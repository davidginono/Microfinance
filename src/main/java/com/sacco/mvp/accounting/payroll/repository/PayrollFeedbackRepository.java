package com.sacco.mvp.accounting.payroll.repository;

import com.sacco.mvp.accounting.payroll.dto.PayrollFeedbackDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class PayrollFeedbackRepository {
    private final JdbcTemplate jdbc;
    private static final String HEADER="select f.id,f.batch_id,f.filename,f.checksum,f.evidence,f.actor_name,f.created_at,f.row_count,f.total,m.receipt_batch_id from payroll_feedback f left join payroll_remittance m on m.feedback_id=f.id ";
    public Optional<UUID> retry(UUID batch,String checksum,String semantic) {return jdbc.query("select id from payroll_feedback where batch_id=? and (checksum=? or semantic_hash=?)",(r,n)->r.getObject(1,UUID.class),batch,checksum,semantic).stream().findFirst();}
    public Set<UUID> successes(UUID batch){return new HashSet<>(jdbc.query("select client_id from payroll_feedback_row where batch_id=? and status='SUCCESS' limit 1000",(r,n)->r.getObject(1,UUID.class),batch));}
    public UUID insert(AppUserPrincipal a,UUID batch,String filename,byte[] content,String checksum,String semantic,String evidence,List<Matched> rows,OffsetDateTime now) {
        UUID id=UUID.randomUUID();var total=rows.stream().map(r->r.input().amount()).reduce(new BigDecimal("0.00"),BigDecimal::add);
        jdbc.update("insert into payroll_feedback(id,batch_id,sacco_id,station_id,filename,checksum,semantic_hash,content,evidence,actor_id,actor_name,created_at,row_count,total) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,batch,a.getSaccoId(),a.getStationId(),filename,checksum,semantic,content,evidence,a.getMemberId(),a.getFullName(),now,rows.size(),total);
        jdbc.batchUpdate("insert into payroll_feedback_row(feedback_id,batch_id,line_number,client_id,client_name,employee_number,deduction_reference,status,amount,requested,deduction_date,employer_reference) values(?,?,?,?,?,?,?,?,?,?,?,?)",rows,100,(p,r)->{var i=r.input();p.setObject(1,id);p.setObject(2,batch);p.setInt(3,i.line());p.setObject(4,r.clientId());p.setString(5,r.clientName());p.setString(6,i.employee());p.setObject(7,UUID.fromString(i.reference()));p.setString(8,i.status());p.setBigDecimal(9,i.amount());p.setBigDecimal(10,r.requested());p.setObject(11,i.date());p.setString(12,i.employerReference());});return id;
    }
    public Feedback get(AppUserPrincipal a,UUID batch,UUID id) {return jdbc.query(HEADER+"where f.id=? and f.batch_id=? and f.sacco_id=? and f.station_id=?",this::header,id,batch,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Payroll feedback unavailable"));}
    public List<Feedback> list(AppUserPrincipal a,UUID batch,int page,String sort,String direction){
        var columns=Map.of("file","f.filename","date","f.created_at","actor","f.actor_name","rows","f.row_count","total","f.total");
        if(!columns.containsKey(sort) || !Set.of("asc","desc").contains(direction))throw new IllegalArgumentException("payroll.feedback.error.input");
        return jdbc.query(HEADER+"where f.batch_id=? and f.sacco_id=? and f.station_id=? order by "+columns.get(sort)+" "+direction+",f.id limit 26 offset ?",this::header,batch,a.getSaccoId(),a.getStationId(),page*25);
    }
    public List<Result> rows(UUID feedback,int offset,int limit) {
        return jdbc.query("""
            select r.*,i.state item_state,i.receipt_id,
              exists(select 1 from accountant_loan_receipt c where c.reverses_id=i.receipt_id) reversed
            from payroll_feedback_row r left join payroll_feedback_receipt l using(feedback_id,line_number)
            left join receipt_batch_item i on i.id=l.item_id
            where r.feedback_id=? order by r.line_number limit ? offset ?
            """,(r,n)->new Result(r.getInt("line_number"),r.getObject("client_id",UUID.class),r.getString("client_name"),r.getString("employee_number"),r.getObject("deduction_reference",UUID.class).toString().toUpperCase(Locale.ROOT),r.getString("status"),r.getBigDecimal("amount"),r.getBigDecimal("requested"),r.getObject("deduction_date",LocalDate.class),r.getString("employer_reference"),r.getBoolean("reversed")?"REVERSED":r.getString("item_state")==null?"UNPOSTED":r.getString("item_state"),r.getObject("receipt_id",UUID.class)),feedback,limit,offset);
    }
    public void link(UUID feedback,int line,UUID item){jdbc.update("insert into payroll_feedback_receipt(feedback_id,line_number,item_id) values(?,?,?)",feedback,line,item);}
    public Optional<Remittance> remittance(UUID id){return jdbc.query("select m.*,(select i.loan_labels->>(m.money_account_id::text) from receipt_batch_item i where i.batch_id=m.receipt_batch_id order by i.line_no limit 1) account_name from payroll_remittance m where m.feedback_id=?",(r,n)->new Remittance(r.getString("account_name"),r.getObject("received_date",LocalDate.class),r.getBigDecimal("received_amount"),r.getString("reference"),r.getString("evidence")),id).stream().findFirst();}
    public boolean sameRemittance(AppUserPrincipal a,UUID id,RemittanceForm f){return f!=null && f.isVerified() && Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from payroll_remittance where feedback_id=? and actor_id=? and money_account_id=? and received_date=? and received_amount=? and reference=? and evidence=?)",Boolean.class,id,a.getMemberId(),f.getMoneyAccountId(),f.getReceivedDate(),f.getReceivedAmount(),f.getReference()==null?null:f.getReference().strip(),f.getEvidence()==null?null:f.getEvidence().strip()));}
    public void remit(AppUserPrincipal a,UUID feedback,UUID batch,RemittanceForm f,OffsetDateTime now){jdbc.update("insert into payroll_remittance(feedback_id,receipt_batch_id,sacco_id,station_id,money_account_id,received_date,received_amount,reference,evidence,actor_id,created_at) values(?,?,?,?,?,?,?,?,?,?,?)",feedback,batch,a.getSaccoId(),a.getStationId(),f.getMoneyAccountId(),f.getReceivedDate(),f.getReceivedAmount(),f.getReference().strip(),f.getEvidence().strip(),a.getMemberId(),now);}
    private Feedback header(ResultSet r,int n)throws SQLException{return new Feedback(r.getObject("id",UUID.class),r.getObject("batch_id",UUID.class),r.getString("filename"),r.getString("checksum"),r.getString("evidence"),r.getString("actor_name"),r.getObject("created_at",OffsetDateTime.class),r.getInt("row_count"),r.getBigDecimal("total"),r.getObject("receipt_batch_id",UUID.class));}
}
