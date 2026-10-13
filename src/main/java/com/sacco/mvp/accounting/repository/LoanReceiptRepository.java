package com.sacco.mvp.accounting.repository;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class LoanReceiptRepository {
    private final JdbcTemplate jdbc;
    private static final String HEAD="""
        select r.*,null::uuid journal_id,'RECEIPT' type,i.sacco_name institution_name,
        coalesce(nullif(s.address_location,''),r.station_id) branch_name,
        (select x.id from accountant_loan_receipt x where x.reverses_id=r.id) reversed_by
        from accountant_loan_receipt r join registered_saccos i on i.sacco_id=r.sacco_id
        join sacco_stations s on s.sacco_id=r.sacco_id and s.station_id=r.station_id
        """;
    private static final String LEGACY="""
        select p.id,p.sacco_id,p.station_id,t.receipt_reference number,'RECEIPT' type,p.effective_date,
        m.full_name party,m.member_no client_number,p.reference,p.notes description,p.evidence,p.money_account_id,
        p.amount total,1 transaction_count,null::uuid journal_id,p.actor_id posted_by,u.full_name posted_by_name,
        p.recorded_at posted_at,p.reverses_id,(select x.id from accountant_loan_post x where x.reverses_id=p.id) reversed_by,
        i.sacco_name institution_name,coalesce(nullif(s.address_location,''),p.station_id) branch_name,
        p.id posting_id,p.loan_id,a.loan_id loan_number,p.amount,p.principal_amount,p.interest_amount,p.principal_balance,
        p.money_account_name,p.principal_account_name,p.interest_account_name,1 line_no
        from accountant_loan_post p join loan_repayment_transactions t on t.id=p.transaction_id
        join loan_applications a on a.id=p.loan_id join members m on m.id=a.applicant_member_id join members u on u.id=p.actor_id
        join registered_saccos i on i.sacco_id=p.sacco_id join sacco_stations s on s.sacco_id=p.sacco_id and s.station_id=p.station_id
        where p.sacco_id=? and p.station_id=? and p.id=? and p.kind in ('PAYMENT','REVERSAL')
        and not exists(select 1 from accountant_loan_receipt_row x where x.posting_id=p.id)
        """;
    public Optional<LoanReceipt> legacy(AppUserPrincipal a,UUID id){return jdbc.query(LEGACY,(r,n)->new LoanReceipt(header(r),r.getString("client_number"),List.of(row(r))),a.getSaccoId(),a.getStationId(),id).stream().findFirst();}
    public Optional<LoanReceipt> find(AppUserPrincipal a,UUID id,boolean lock){
        return jdbc.query(HEAD+" where r.sacco_id=? and r.station_id=? and r.id=?"+(lock?" for update of r":""),
            (r,n)->new LoanReceipt(header(r),r.getString("client_number"),List.of()),a.getSaccoId(),a.getStationId(),id).stream().findFirst();
    }
    public Optional<UUID> retry(AppUserPrincipal a,UUID key,String hash){
        var rows=jdbc.queryForList("select id,payload_hash,posted_by from accountant_loan_receipt where sacco_id=? and station_id=? and request_key=?",a.getSaccoId(),a.getStationId(),key);
        if(rows.isEmpty())return Optional.empty();var r=rows.getFirst();
        if(!hash.equals(r.get("payload_hash")) || !a.getMemberId().equals(r.get("posted_by")))throw new Invalid("","retry");
        return Optional.of((UUID)r.get("id"));
    }
    public List<LoanReceiptRow> rows(AppUserPrincipal a,UUID id){return jdbc.query("""
        select x.line_no,x.description,p.*,p.id posting_id,l.loan_id loan_number from accountant_loan_receipt_row x
        join accountant_loan_post p on p.id=x.posting_id join loan_applications l on l.id=p.loan_id
        where x.receipt_id=? and x.sacco_id=? and x.station_id=? order by x.line_no limit 50
        """,(r,n)->row(r),id,a.getSaccoId(),a.getStationId());}
    public void insert(AppUserPrincipal a,Voucher v,UUID requestKey,UUID client,String clientNumber,String hash,List<LoanReceiptRow> rows){
        jdbc.update("""
            insert into accountant_loan_receipt(id,sacco_id,station_id,number,request_key,payload_hash,effective_date,client_id,party,client_number,
            reference,description,evidence,money_account_id,total,transaction_count,posted_by,posted_by_name,posted_at,reverses_id)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,v.id(),a.getSaccoId(),a.getStationId(),v.number(),requestKey,hash,v.effectiveDate(),client,v.party(),clientNumber,v.reference(),v.description(),v.evidence(),v.moneyAccountId(),v.total(),rows.size(),a.getMemberId(),v.postedByName(),v.postedAt(),v.reversesId());
        jdbc.batchUpdate("insert into accountant_loan_receipt_row(receipt_id,sacco_id,station_id,line_no,posting_id,description) values(?,?,?,?,?,?)",
            rows.stream().map(r->new Object[]{v.id(),a.getSaccoId(),a.getStationId(),r.lineNo(),r.postingId(),r.description()}).toList());
    }
    public List<Voucher> list(AppUserPrincipal a,Filter f,boolean includeLoans){
        String order=switch(f.sort()){case "oldest"->"v.effective_date,v.id";case "amount"->"v.total desc,v.id desc";default->"v.effective_date desc,v.id desc";};
        return jdbc.query("""
            with receipts as not materialized (
              select id,sacco_id,station_id,number,request_key,effective_date,party,reference,description,evidence,money_account_id,
              total,transaction_count,journal_id,posted_by,posted_at,reverses_id from accounting_voucher where type='RECEIPT' and sacco_id=? and station_id=?
              union all
              select id,sacco_id,station_id,number,request_key,effective_date,party,reference,description,evidence,money_account_id,
              total,transaction_count,null::uuid,posted_by,posted_at,reverses_id from accountant_loan_receipt where sacco_id=? and station_id=? and ?
              union all
              select p.id,p.sacco_id,p.station_id,t.receipt_reference,p.request_key,p.effective_date,m.full_name,p.reference,p.notes,p.evidence,p.money_account_id,
              p.amount,1,null::uuid,p.actor_id,p.recorded_at,p.reverses_id from accountant_loan_post p
              join loan_repayment_transactions t on t.id=p.transaction_id join loan_applications l on l.id=p.loan_id join members m on m.id=l.applicant_member_id
              where p.sacco_id=? and p.station_id=? and ? and p.kind in ('PAYMENT','REVERSAL')
              and not exists(select 1 from accountant_loan_receipt_row x where x.posting_id=p.id)
            ), all_receipts as (
              select v.*,m.full_name posted_by_name,i.sacco_name institution_name,
              coalesce(nullif(s.address_location,''),v.station_id) branch_name,
              (select r.id from receipts r where r.reverses_id=v.id) reversed_by
              from receipts v join members m on m.id=v.posted_by join registered_saccos i on i.sacco_id=v.sacco_id
              join sacco_stations s on s.sacco_id=v.sacco_id and s.station_id=v.station_id
            ) select v.* from all_receipts v
            where (?='' or strpos(lower(v.number||' '||v.party||' '||v.reference),?)>0)
            and (?::date is null or v.effective_date>=?::date) and (?::date is null or v.effective_date<=?::date)
            and (?='' or (case when v.reverses_id is not null then 'REVERSAL' when v.reversed_by is not null then 'REVERSED' else 'POSTED' end)=?)
            """+" order by "+order+" limit ? offset ?",(r,n)->header(r),a.getSaccoId(),a.getStationId(),a.getSaccoId(),a.getStationId(),includeLoans,a.getSaccoId(),a.getStationId(),includeLoans,f.search().toLowerCase(Locale.ROOT),f.search().toLowerCase(Locale.ROOT),f.from(),f.from(),f.through(),f.through(),f.status(),f.status(),f.size()+1,f.page()*f.size());
    }
    private LoanReceiptRow row(ResultSet r)throws SQLException{return new LoanReceiptRow(r.getInt("line_no"),r.getObject("posting_id",UUID.class),r.getObject("loan_id",UUID.class),r.getString("loan_number"),r.getString("description"),r.getBigDecimal("amount"),r.getBigDecimal("principal_amount"),r.getBigDecimal("interest_amount"),r.getBigDecimal("principal_balance"),r.getString("money_account_name"),r.getString("principal_account_name"),r.getString("interest_account_name"),r.getString("reference"));}
    private Voucher header(ResultSet r)throws SQLException{return new Voucher(r.getObject("id",UUID.class),r.getString("number"),Type.RECEIPT,r.getObject("effective_date",LocalDate.class),r.getString("party"),r.getString("reference"),r.getString("description"),r.getString("evidence"),r.getObject("money_account_id",UUID.class),r.getBigDecimal("total"),r.getInt("transaction_count"),r.getObject("journal_id",UUID.class),r.getObject("posted_by",UUID.class),r.getString("posted_by_name"),r.getObject("posted_at",OffsetDateTime.class),r.getObject("reverses_id",UUID.class),r.getObject("reversed_by",UUID.class),r.getString("institution_name"),r.getString("branch_name"),List.of());}
}
