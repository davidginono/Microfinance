package com.sacco.mvp.accounting.adjustments.repository;

import com.sacco.mvp.accounting.adjustments.dto.LoanAdjustmentDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class LoanAdjustmentRepository {
    private final JdbcTemplate jdbc;
    private static final String SELECT="""
        select q.*,a.loan_id number,m.full_name client,u.full_name requester,
        coalesce(d.decision,'PENDING') state,d.actor_id decided_by,v.full_name reviewer,d.reason decision_reason,
        d.recorded_at decided_at,d.journal_id,dr.name debit_name,cr.name credit_name,
        i.sacco_name institution,coalesce(nullif(s.address_location,''),s.station_id) branch
        from loan_adjustment_request q join loan_applications a on a.id=q.loan_id
        join members m on m.id=a.applicant_member_id join members u on u.id=q.actor_id
        join registered_saccos i on i.sacco_id=q.sacco_id
        join sacco_stations s on s.sacco_id=q.sacco_id and s.station_id=q.station_id
        join gl_account dr on dr.id=q.debit_account_id join gl_account cr on cr.id=q.credit_account_id
        left join loan_adjustment_decision d on d.request_id=q.id left join members v on v.id=d.actor_id
        """;
    public void lockKey(AppUserPrincipal a,UUID key){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","LOAN_ADJUSTMENT/"+a.getSaccoId()+"/"+a.getStationId()+"/"+key);}
    public Context context(AppUserPrincipal a,UUID id,boolean lock){
        // Same lock order as receipt posting: recorded source, then repayment ledger.
        if(lock){if(jdbc.queryForList("select loan_id from accountant_loan_record where loan_id=? and sacco_id=? and station_id=? for update",id,a.getSaccoId(),a.getStationId()).isEmpty())throw new AccessDeniedException("Loan unavailable");
            jdbc.queryForList("select loan_application_id from loan_ledgers where loan_application_id=? for update",id);}
        return jdbc.query("""
            select a.id,a.loan_id,m.full_name,a.status,l.disbursement_date,l.principal,l.principal-l.principal_paid outstanding,l.next_sequence,
            b.allowance,b.written_off,b.last_adjustment,greatest(b.last_date,l.last_payment_date) last_date,b.allowance_account,b.write_off_journal,
            p.principal_account_id,p.money_account_id,p.journal_id,p.actor_id,(select maker_id from gl_journal where id=b.write_off_journal) write_off_posted_by
            from accountant_loan_record r join loan_applications a on a.id=r.loan_id
            join loan_ledgers l on l.loan_application_id=a.id join members m on m.id=a.applicant_member_id
            join accountant_loan_post p on p.loan_id=a.id and p.kind='DISBURSEMENT' and p.state='POSTED'
            join loan_adjustment_balance b on b.loan_id=a.id
            where r.loan_id=? and r.sacco_id=? and r.station_id=? and a.sacco_id=r.sacco_id and a.station_id=r.station_id
            """,(r,n)->new Context(r.getObject("id",UUID.class),r.getString("loan_id"),r.getString("full_name"),r.getString("status"),r.getObject("disbursement_date",LocalDate.class),r.getBigDecimal("principal"),r.getBigDecimal("outstanding"),r.getBigDecimal("allowance"),r.getBigDecimal("written_off"),r.getLong("next_sequence"),r.getObject("last_adjustment",UUID.class),r.getObject("last_date",LocalDate.class),r.getObject("principal_account_id",UUID.class),r.getObject("money_account_id",UUID.class),r.getObject("journal_id",UUID.class),r.getObject("actor_id",UUID.class),r.getObject("allowance_account",UUID.class),r.getObject("write_off_journal",UUID.class),r.getObject("write_off_posted_by",UUID.class)),id,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Verified recorded disbursement required"));
    }
    public List<Choice> loans(AppUserPrincipal a,String search,int page){return jdbc.query("""
        select a.id,a.loan_id||' - '||m.full_name label from accountant_loan_record r
        join loan_applications a on a.id=r.loan_id join members m on m.id=a.applicant_member_id
        join loan_ledgers l on l.loan_application_id=a.id
        where r.sacco_id=? and r.station_id=? and a.status in ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF','PAID')
        and exists(select 1 from accountant_loan_post p where p.loan_id=a.id and p.kind='DISBURSEMENT' and p.state='POSTED')
        and strpos(lower(a.loan_id||' '||m.full_name||' '||m.member_no),?)>0 order by a.loan_id,a.id limit 26 offset ?
        """,(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2)),a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);}
    public List<Choice> accounts(AppUserPrincipal a,String type,String purpose,String search,int page){return jdbc.query("select id,name from gl_account where sacco_id=? and active and (kind='POSTING' or (purpose='ALLOWANCE' and kind='CONTROL')) and type=? and purpose=? and strpos(lower(code||' '||name),?)>0 order by code,id limit 26 offset ?",(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2)),a.getSaccoId(),type,purpose,search.toLowerCase(Locale.ROOT),page*25);}
    public boolean account(AppUserPrincipal a,UUID id,String type,String purpose){return !jdbc.queryForList("select id from gl_account where id=? and sacco_id=? and active and (kind='POSTING' or (purpose='ALLOWANCE' and kind='CONTROL')) and type=? and purpose=? for share",id,a.getSaccoId(),type,purpose).isEmpty();}
    public Choice selectedAccount(AppUserPrincipal a,UUID id){return id==null?null:jdbc.query("select id,name from gl_account where id=? and sacco_id=? and purpose in ('ALLOWANCE','EXPENSE')",(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2)),id,a.getSaccoId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Account unavailable"));}
    public Optional<Request> retry(AppUserPrincipal a,UUID key,String hash){var found=jdbc.queryForList("select id,payload_hash,actor_id from loan_adjustment_request where sacco_id=? and station_id=? and request_key=?",a.getSaccoId(),a.getStationId(),key);if(found.isEmpty())return Optional.empty();var r=found.getFirst();if(!hash.equals(r.get("payload_hash")) || !a.getMemberId().equals(r.get("actor_id")))throw new IllegalArgumentException("adjustment.error.retry");return Optional.of(find(a,(UUID)r.get("id")));}
    public boolean pending(UUID loan){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from loan_adjustment_request q where loan_id=? and not exists(select 1 from loan_adjustment_decision d where d.request_id=q.id))",Boolean.class,loan));}
    public Request find(AppUserPrincipal a,UUID id){return jdbc.query(SELECT+" where q.id=? and q.sacco_id=? and q.station_id=?",this::map,id,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Adjustment unavailable"));}
    public List<Request> list(AppUserPrincipal a,String state,String search,int page,String sort,String direction){String column=Map.of("number","a.loan_id","client","m.full_name","kind","q.kind","date","q.effective_date","amount","q.amount","state","coalesce(d.decision,'PENDING')").get(sort);if(column==null || !Set.of("asc","desc").contains(direction))throw new IllegalArgumentException("adjustment.error.parameters");return jdbc.query(SELECT+" where q.sacco_id=? and q.station_id=? and (?='' or coalesce(d.decision,'PENDING')=?) and strpos(lower(a.loan_id||' '||m.full_name),?)>0 order by "+column+" "+direction+",q.id limit 26 offset ?",this::map,a.getSaccoId(),a.getStationId(),state,state,search.toLowerCase(Locale.ROOT),page*25);}
    public void insert(AppUserPrincipal a,UUID id,Form f,Context c,String hash,BigDecimal amount,BigDecimal allowanceChange,BigDecimal principalChange,UUID debit,UUID credit,UUID reverses,OffsetDateTime now){jdbc.update("""
        insert into loan_adjustment_request(id,loan_id,sacco_id,station_id,request_key,payload_hash,kind,effective_date,amount,allowance_change,principal_change,
        debit_account_id,credit_account_id,outstanding_before,allowance_before,written_off_before,repayment_sequence,last_adjustment,reason,evidence,actor_id,recorded_at,reverses_journal_id)
        values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,id,c.id(),a.getSaccoId(),a.getStationId(),f.getRequestKey(),hash,f.getKind().name(),f.getDate(),amount,allowanceChange,principalChange,debit,credit,c.outstanding(),c.allowance(),c.writtenOff(),c.sequence(),c.lastAdjustment(),f.getReason().strip(),f.getEvidence().strip(),a.getMemberId(),now,reverses);}
    public void decide(AppUserPrincipal a,UUID id,UUID key,boolean approve,LocalDate date,String reason,UUID journal,OffsetDateTime now){jdbc.update("insert into loan_adjustment_decision(request_id,sacco_id,station_id,decision,actor_id,request_key,effective_date,reason,journal_id,recorded_at) values(?,?,?,?,?,?,?,?,?,?)",id,a.getSaccoId(),a.getStationId(),approve?"APPROVED":"REJECTED",a.getMemberId(),key,date,reason,journal,now);}
    public UUID post(AppUserPrincipal a,Request q,UUID key,UUID journal,UUID period,OffsetDateTime now){
        beginPosting(a,q,key,journal,period,now);
        postingLines(a,q,journal,null);finishPosting(journal,now);return journal;
    }
    public void beginPosting(AppUserPrincipal a,Request q,UUID key,UUID journal,UUID period,OffsetDateTime now){
        jdbc.update("""
            insert into gl_journal(id,sacco_id,station_id,policy_version,period_id,source_type,source_reference,request_key,payload_hash,currency,state,evidence_reference,reason,effective_date,maker_id,recorded_at,reverses_id,direct_post)
            select ?,sacco_id,station_id,1,?,'LOAN_ADJUSTMENT',id::text,?,payload_hash,'TZS','DRAFT',evidence,reason,effective_date,?,?,reverses_journal_id,true from loan_adjustment_request where id=?
            """,journal,period,key,a.getMemberId(),now,q.id());
        jdbc.update("insert into accounting_outbox(id,journal_id,sacco_id,station_id,event_type,created_at) values(?,?,?,?,'LOAN_ADJUSTMENT_POSTED',?)",UUID.randomUUID(),journal,a.getSaccoId(),a.getStationId(),now);
    }
    public void postingLines(AppUserPrincipal a,Request q,UUID journal,List<Object[]> reversal){if(reversal==null){jdbc.update("insert into gl_journal_line(id,journal_id,sacco_id,station_id,account_id,debit,credit) values(?,?,?,?,?,?,0),(?,?,?,?,?,0,?)",UUID.randomUUID(),journal,a.getSaccoId(),a.getStationId(),q.debitId(),q.amount(),UUID.randomUUID(),journal,a.getSaccoId(),a.getStationId(),q.creditId(),q.amount());}else for(var line:reversal)jdbc.update("insert into gl_journal_line(id,journal_id,sacco_id,station_id,account_id,debit,credit) values(?,?,?,?,?,?,?)",UUID.randomUUID(),journal,a.getSaccoId(),a.getStationId(),line[0],line[1],line[2]);}
    public void finishPosting(UUID journal,OffsetDateTime now){jdbc.update("update gl_journal set state='POSTED',posted_at=? where id=?",now,journal);}
    public void status(UUID loan,String status,OffsetDateTime now){jdbc.update("""
        update loan_applications set status=?,updated_at=?,version=version+1,financial_snapshot=case when ?='DISBURSEMENT_REVERSED'
        then financial_snapshot::jsonb||'{"paymentSummaryOutstandingPrincipal":0,"paymentSummaryOutstandingInterest":0,"paymentSummaryTotalOutstanding":0,"ledgerFutureScheduledInterest":0}'::jsonb
        else financial_snapshot end where id=?
        """,status,now,status,loan);}
    public boolean decisionRetry(AppUserPrincipal a,UUID id,UUID key,boolean approve,String reason){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from loan_adjustment_decision where request_id=? and actor_id=? and request_key=? and decision=? and reason=?)",Boolean.class,id,a.getMemberId(),key,approve?"APPROVED":"REJECTED",reason));}
    private Request map(ResultSet r,int n)throws SQLException{return new Request(r.getObject("id",UUID.class),r.getObject("loan_id",UUID.class),r.getString("number"),r.getString("client"),Kind.valueOf(r.getString("kind")),r.getObject("effective_date",LocalDate.class),r.getBigDecimal("amount"),r.getBigDecimal("allowance_change"),r.getBigDecimal("principal_change"),r.getObject("debit_account_id",UUID.class),r.getObject("credit_account_id",UUID.class),r.getString("debit_name"),r.getString("credit_name"),r.getBigDecimal("outstanding_before"),r.getBigDecimal("allowance_before"),r.getBigDecimal("written_off_before"),r.getLong("repayment_sequence"),r.getObject("last_adjustment",UUID.class),r.getString("reason"),r.getString("evidence"),r.getObject("actor_id",UUID.class),r.getString("requester"),r.getObject("recorded_at",OffsetDateTime.class),r.getString("state"),r.getObject("decided_by",UUID.class),r.getString("reviewer"),r.getString("decision_reason"),r.getObject("decided_at",OffsetDateTime.class),r.getObject("journal_id",UUID.class),r.getObject("reverses_journal_id",UUID.class),r.getString("institution"),r.getString("branch"));}
}
