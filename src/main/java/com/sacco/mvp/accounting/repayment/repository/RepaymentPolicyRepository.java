package com.sacco.mvp.accounting.repayment.repository;

import com.sacco.mvp.accounting.repayment.dto.RepaymentPolicyDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class RepaymentPolicyRepository {
    private final JdbcTemplate jdbc;
    public void lock(String institution,boolean exclusive){jdbc.queryForList("select "+(exclusive?"pg_advisory_xact_lock":"pg_advisory_xact_lock_shared")+"(hashtextextended(?,0))","REPAYMENT_POLICY/"+institution);}
    public Optional<Policy> effective(String institution,LocalDate date){
        return jdbc.query("select p.*,coalesce(a.code||' - '||a.name,'') account from repayment_accounting_policy p left join gl_account a on a.id=p.interest_account_id and a.sacco_id=p.sacco_id where p.sacco_id=? and p.effective_from<=? order by p.effective_from desc limit 1",
            (r,n)->new Policy(r.getObject("id",UUID.class),r.getInt("version"),r.getObject("effective_from",LocalDate.class),AllocationOrder.valueOf(r.getString("allocation_order")),r.getObject("interest_account_id",UUID.class),r.getString("account"),r.getString("reason"),r.getObject("created_at",OffsetDateTime.class)),institution,date).stream().findFirst();
    }
    public Optional<Policy> latest(String institution){return effective(institution,LocalDate.of(9999,12,31));}
    public boolean standards(String institution){return Integer.valueOf(2).equals(jdbc.queryForObject("select count(*) from gl_transaction_code t join gl_activity a on a.id=t.activity_id and a.sacco_id=t.sacco_id where a.sacco_id=? and a.system_key='LOAN_REPAYMENT' and a.active and t.active and t.source_event='REPAYMENT' and t.system_key in('REPAYMENT_PRINCIPAL','REPAYMENT_INTEREST')",Integer.class,institution));}
    public boolean account(String institution,UUID id){return !jdbc.queryForList("select id from gl_account where sacco_id=? and id=? and active and kind='POSTING' and purpose='CLEARING' for share",institution,id).isEmpty();}
    public List<com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice> accounts(String institution,String search,int page){return jdbc.query("select id,code||' - '||name label from gl_account where sacco_id=? and active and kind='POSTING' and purpose='CLEARING' and strpos(lower(code||' '||name),?)>0 order by code,id limit 26 offset ?",(r,n)->new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice(r.getObject("id",UUID.class),r.getString("label"),"CLEARING"),institution,search.toLowerCase(Locale.ROOT),page*25);}
    public LocalDate lastPayment(String institution){return jdbc.queryForObject("select max(payment_date) from loan_repayment_transactions where sacco_id=?",LocalDate.class,institution);}
    public UUID insert(String institution,UUID actor,PolicyForm f,int version,OffsetDateTime now){
        UUID id=UUID.randomUUID();jdbc.update("insert into repayment_accounting_policy(id,sacco_id,version,effective_from,allocation_order,interest_account_id,reason,request_key,created_by,created_at) values(?,?,?,?,?,?,?,?,?,?)",id,institution,version,f.effectiveFrom(),f.allocationOrder().name(),f.interestAccountId(),f.reason().strip(),f.requestKey(),actor,now);return id;
    }
    public Optional<UUID> retry(String institution,UUID actor,PolicyForm f){
        var rows=jdbc.queryForList("select * from repayment_accounting_policy where sacco_id=? and request_key=?",institution,f.requestKey());
        if(rows.isEmpty())return Optional.empty();var r=rows.getFirst();
        if(!actor.equals(r.get("created_by")) || !f.effectiveFrom().equals(((java.sql.Date)r.get("effective_from")).toLocalDate()) || !f.allocationOrder().name().equals(r.get("allocation_order")) || !Objects.equals(f.interestAccountId(),r.get("interest_account_id")) || !f.reason().strip().equals(r.get("reason")))throw new IllegalArgumentException("batch.error.changed");
        return Optional.of((UUID)r.get("id"));
    }
}
