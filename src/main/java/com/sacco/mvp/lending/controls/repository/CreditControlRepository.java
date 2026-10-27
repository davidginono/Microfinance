package com.sacco.mvp.lending.controls.repository;

import com.sacco.mvp.lending.controls.dto.CreditControlDtos.*;
import com.sacco.mvp.lending.controls.dto.CreditControlDtos.Case;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import com.sacco.mvp.support.ResourceTableSort;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class CreditControlRepository {
    private final JdbcTemplate jdbc;
    public record Reminder(UUID installment,UUID loan,UUID client,String institution,String branch,String number,LocalDate due,BigDecimal amount,String language) {}
    public List<Reminder> reminders(LocalDate day,int limit){return jdbc.query("""
        select i.id installment,l.id loan,l.applicant_member_id,l.sacco_id,l.station_id,l.loan_id,i.due_date,
          i.principal-i.principal_paid+i.interest-i.interest_paid-i.interest_excluded-i.interest_waived amount,coalesce(u.language,'en') language
        from loan_ledger_installments i join loan_applications l on l.id=i.loan_application_id
        join loan_ledgers b on b.loan_application_id=l.id and b.sacco_id=l.sacco_id and b.station_id=l.station_id
        join members m on m.id=l.applicant_member_id and m.sacco_id=l.sacco_id and m.status='ACTIVE'
        left join user_settings u on u.member_id=m.id
        join lateral(select * from lending_credit_policy p where p.sacco_id=l.sacco_id and p.station_id=l.station_id order by revision desc limit 1) policy on policy.reminders_enabled
        where l.status in ('DISBURSED','PAR','DEFAULTED') and i.due_date<=?::date+policy.reminder_days_before
          and (i.principal>i.principal_paid or i.interest>i.interest_paid+i.interest_excluded+i.interest_waived)
          and not exists(select 1 from loan_ledger_installments earlier where earlier.loan_application_id=l.id and earlier.due_date<i.due_date
            and earlier.principal-earlier.principal_paid+earlier.interest-earlier.interest_paid-earlier.interest_excluded-earlier.interest_waived>0)
          and not exists(select 1 from lending_instalment_reminder sent join loan_ledger_installments previous on previous.id=sent.installment_id where previous.loan_application_id=l.id and sent.reminder_on>?::date-policy.reminder_every_days)
          and not exists(select 1 from lending_collection_case c where c.loan_id=l.id and (select e.kind from lending_collection_event e where e.case_id=c.id and e.kind in ('DISPUTE','RESOLVE','CLOSE','REOPEN') order by sequence desc limit 1)='DISPUTE')
        order by i.due_date,l.id limit ? for update of i skip locked
        """,(r,n)->new Reminder(r.getObject("installment",UUID.class),r.getObject("loan",UUID.class),r.getObject("applicant_member_id",UUID.class),r.getString("sacco_id"),r.getString("station_id"),r.getString("loan_id"),r.getObject("due_date",LocalDate.class),r.getBigDecimal("amount"),r.getString("language")),day,day,limit);}
    public void reminder(Reminder r,UUID id,LocalDate day,String payload,OffsetDateTime now){
        jdbc.update("insert into outbox_events(id,aggregate_type,aggregate_id,event_type,payload,status,created_at) values(?,'INSTALMENT',?,'REPAYMENT_REMINDER',?::jsonb,'NEW',?)",id,r.installment(),payload,now);
        jdbc.update("insert into lending_instalment_reminder values(?,?,?,?)",UUID.randomUUID(),r.installment(),day,id);
    }
    private static <T> T one(List<T> rows){return rows.stream().findFirst().orElseThrow(()->new AccessDeniedException("Credit record unavailable"));}
    public void lock(String key){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))",key);}
    public Optional<UUID> retry(AppUserPrincipal a,String table,UUID key,String hash){
        if(!Set.of("lending_credit_policy","lending_guarantor_assessment","lending_collateral_asset","lending_collateral_event","lending_collection_case","lending_collection_event").contains(table))throw new IllegalArgumentException();
        return jdbc.query("select id,payload_hash from "+table+" where sacco_id=? and station_id=? and request_key=?",(r,n)->{if(!hash.equals(r.getString(2)))throw new IllegalArgumentException("credit.error.retry");return r.getObject(1,UUID.class);},a.getSaccoId(),a.getStationId(),key).stream().findFirst();
    }
    public Optional<Policy> policy(AppUserPrincipal a){return jdbc.query("select * from lending_credit_policy where sacco_id=? and station_id=? order by revision desc limit 1",(r,n)->new Policy(r.getObject("id",UUID.class),r.getInt("revision"),r.getBoolean("capacity_required"),r.getBoolean("collateral_required"),r.getString("reference"),r.getBoolean("reminders_enabled"),r.getObject("reminder_days_before",Integer.class),r.getObject("reminder_every_days",Integer.class)),a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public void policy(AppUserPrincipal a,UUID id,PolicyForm f,String hash,OffsetDateTime now){jdbc.update("insert into lending_credit_policy values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,a.getSaccoId(),a.getStationId(),f.getExpectedRevision()+1,f.isCapacityRequired(),f.isCollateralRequired(),f.getReference().strip(),f.getRequestKey(),hash,a.getMemberId(),now,f.isRemindersEnabled(),f.getReminderDaysBefore(),f.getReminderEveryDays());}
    public List<Choice> officers(AppUserPrincipal a,String search,int page){return jdbc.query("select id,coalesce(staff_no,member_no)||' · '||full_name label from members where sacco_id=? and station_id=? and status='ACTIVE' and staff_access_status='ACTIVE' and position<>'ADMIN' and (lower(full_name) like ? or lower(coalesce(staff_no,member_no)) like ?) order by full_name,id limit 26 offset ?",(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2)),a.getSaccoId(),a.getStationId(),like(search),like(search),page*25);}
    public boolean officer(AppUserPrincipal a,UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from members where id=? and sacco_id=? and station_id=? and status='ACTIVE' and staff_access_status='ACTIVE' and position<>'ADMIN')",Boolean.class,id,a.getSaccoId(),a.getStationId()));}
    public Optional<Choice> officerChoice(AppUserPrincipal a,UUID id){return jdbc.query("select id,coalesce(staff_no,member_no)||' · '||full_name from members where id=? and sacco_id=? and station_id=?",(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2)),id,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public List<Choice> loanChoices(AppUserPrincipal a,String search,int page){return jdbc.query("""
        select l.id,coalesce(l.loan_id,l.application_number::text,l.id::text)||' · '||coalesce(b.full_name,'') label
        from loan_applications l join loan_borrower_directory b on b.party_id=l.borrower_party_id and b.sacco_id=l.sacco_id
        where l.sacco_id=? and l.station_id=? and (lower(coalesce(l.loan_id,l.application_number::text,'')) like ? or lower(coalesce(b.full_name,'')) like ?)
        order by l.created_at desc,l.id limit 26 offset ?
        """,(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2)),a.getSaccoId(),a.getStationId(),like(search),like(search),page*25);}
    private static String like(String s){return "%"+s.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}
    private static final String GUARANTEES="""
        select g.id,l.id loan_id,coalesce(l.loan_id,l.application_number::text,l.id::text) loan_number,
          coalesce(m.full_name,'') borrower,coalesce(p.full_name,g.external_full_name,'') guarantor,g.status,lending_capacity_subject(g.id) subject,
          case when jsonb_typeof(l.financial_snapshot->'interestAmount')='number' then l.amount+(l.financial_snapshot->>'interestAmount')::numeric+coalesce((l.financial_snapshot->>'recordedChargeTotal')::numeric,0) end liability,
          null::text capacity_error
        from guarantor_requests g join loan_applications l on l.id=g.loan_application_id
        left join members m on m.id=l.applicant_member_id and m.sacco_id=l.sacco_id left join members p on p.id=g.guarantor_member_id and p.sacco_id=l.sacco_id
        where l.sacco_id=? and l.station_id=?
        """;
    private static final RowMapper<Guarantee> GUARANTEE=(r,n)->new Guarantee(r.getObject("id",UUID.class),r.getObject("loan_id",UUID.class),r.getString("loan_number"),r.getString("borrower"),r.getString("guarantor"),r.getString("status"),r.getString("subject"),r.getBigDecimal("liability"),r.getString("capacity_error"));
    public List<Guarantee> guarantees(AppUserPrincipal a,String search,int page,String order,LocalDate today){return jdbc.query(GUARANTEES+" and (lower(coalesce(p.full_name,g.external_full_name,'')) like ? or lower(coalesce(l.loan_id,l.application_number::text,'')) like ?) order by "+order+",g.id limit 26 offset ?",GUARANTEE,a.getSaccoId(),a.getStationId(),like(search),like(search),page*25);}
    public Guarantee guarantee(AppUserPrincipal a,UUID id,LocalDate today,boolean lock){var row=one(jdbc.query(GUARANTEES+" and g.id=?",GUARANTEE,a.getSaccoId(),a.getStationId(),id));if(lock&&!Boolean.TRUE.equals(jdbc.queryForObject("select lending_capacity_lock(?,?,?)",Boolean.class,id,a.getSaccoId(),a.getStationId())))throw new AccessDeniedException("Guarantor unavailable");String error=jdbc.queryForObject("select lending_capacity_error(?,?,?,?)",String.class,id,a.getSaccoId(),a.getStationId(),today);return new Guarantee(row.id(),row.loanId(),row.loanNumber(),row.borrower(),row.guarantor(),row.status(),row.subject(),row.liability(),error);}
    public List<Assessment> assessments(AppUserPrincipal a,UUID request,int page){return jdbc.query("""
        select x.*,m.full_name actor from lending_guarantor_assessment x join members m on m.id=x.actor_id
        where x.sacco_id=? and x.station_id=? and x.subject_key=lending_capacity_subject(?) order by x.revision desc limit 26 offset ?
        """,(r,n)->new Assessment(r.getObject("id",UUID.class),r.getBigDecimal("income"),r.getBigDecimal("expenses"),r.getBigDecimal("other_debt"),r.getBigDecimal("capacity"),r.getObject("assessed_on",LocalDate.class),r.getObject("valid_through",LocalDate.class),r.getString("evidence"),r.getString("policy_reference"),r.getString("actor"),r.getObject("recorded_at",OffsetDateTime.class)),a.getSaccoId(),a.getStationId(),request,page*25);}
    public void assessment(AppUserPrincipal a,UUID id,Guarantee g,AssessmentForm f,String hash,OffsetDateTime now){jdbc.update("insert into lending_guarantor_assessment values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,g.id(),a.getSaccoId(),a.getStationId(),g.subject(),f.getIncome(),f.getExpenses(),f.getOtherDebt(),f.getCapacity(),f.getAssessedOn(),f.getValidThrough(),f.getEvidence().strip(),f.getPolicyReference().strip(),f.getRequestKey(),hash,a.getMemberId(),now,jdbc.queryForObject("select coalesce(max(revision),0)+1 from lending_guarantor_assessment where sacco_id=? and station_id=? and subject_key=?",Integer.class,a.getSaccoId(),a.getStationId(),g.subject()));}
    private static final String ASSETS="""
        select a.*,v.amount value,v.encumbrance,v.valid_through,
          coalesce((select sum(p.amount) from lending_collateral_pledge p where p.asset_id=a.id and p.kind='PLEDGE'),0) pledged,
          coalesce((select max(e.sequence) from lending_collateral_event e where e.asset_id=a.id),0) sequence
        from lending_collateral_asset a left join lending_collateral_valuation v on v.asset_id=a.id where a.sacco_id=? and a.station_id=?
        """;
    private static final RowMapper<Asset> ASSET=(r,n)->new Asset(r.getObject("id",UUID.class),r.getString("code"),r.getString("description"),r.getString("owner_name"),r.getString("owner_evidence"),r.getBigDecimal("value"),r.getBigDecimal("encumbrance"),r.getObject("valid_through",LocalDate.class),r.getBigDecimal("pledged"),r.getInt("sequence"));
    public List<Asset> assets(AppUserPrincipal a,String search,int page,String order){return jdbc.query(ASSETS+" and (lower(a.code) like ? or lower(a.description) like ? or lower(a.owner_name) like ?) order by "+order+",a.id limit 26 offset ?",ASSET,a.getSaccoId(),a.getStationId(),like(search),like(search),like(search),page*25);}
    public Asset asset(AppUserPrincipal a,UUID id,boolean lock){if(lock)one(jdbc.query("select id from lending_collateral_asset where id=? and sacco_id=? and station_id=? for update",(r,n)->r.getObject(1,UUID.class),id,a.getSaccoId(),a.getStationId()));return one(jdbc.query(ASSETS+" and a.id=?",ASSET,a.getSaccoId(),a.getStationId(),id));}
    public void asset(AppUserPrincipal a,UUID id,AssetForm f,String hash,OffsetDateTime now){jdbc.update("insert into lending_collateral_asset values(?,?,?,?,?,?,?,?,?,?,?)",id,a.getSaccoId(),a.getStationId(),f.getCode().strip().toUpperCase(Locale.ROOT),f.getDescription().strip(),f.getOwner().strip(),f.getOwnerEvidence().strip(),f.getRequestKey(),hash,a.getMemberId(),now);}
    public List<Pledge> pledges(AppUserPrincipal a,UUID asset,int page){return jdbc.query("select p.loan_id,coalesce(l.loan_id,l.application_number::text,l.id::text),p.amount from lending_collateral_pledge p join loan_applications l on l.id=p.loan_id where p.asset_id=? and p.sacco_id=? and p.station_id=? and p.kind='PLEDGE' order by "+ResourceTableSort.order("credit-pledges",Map.of("loan","l.loan_id","amount","p.amount"),"p.loan_id","p.loan_id")+" limit 26 offset ?",(r,n)->new Pledge(r.getObject(1,UUID.class),r.getString(2),r.getBigDecimal(3)),asset,a.getSaccoId(),a.getStationId(),page*25);}
    public boolean pledged(UUID asset,UUID loan){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from lending_collateral_pledge where asset_id=? and loan_id=? and kind='PLEDGE')",Boolean.class,asset,loan));}
    public void collateral(AppUserPrincipal a,UUID id,UUID asset,CollateralForm f,String hash,OffsetDateTime now){jdbc.update("insert into lending_collateral_event values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,asset,a.getSaccoId(),a.getStationId(),f.getExpectedSequence()+1,f.getKind(),f.getLoanId(),f.getAmount(),f.getEncumbrance(),f.getValidThrough(),f.getEffectiveDate(),f.getEvidence().strip(),f.getReason().strip(),f.getRequestKey(),hash,a.getMemberId(),now);}
    public List<CollateralEvent> collateralHistory(AppUserPrincipal a,UUID asset,int page){return jdbc.query("select e.*,m.full_name actor,coalesce(l.loan_id,l.application_number::text,l.id::text) loan_number from lending_collateral_event e join members m on m.id=e.actor_id left join loan_applications l on l.id=e.loan_id where e.asset_id=? and e.sacco_id=? and e.station_id=? order by e.sequence desc limit 26 offset ?",(r,n)->new CollateralEvent(r.getObject("id",UUID.class),r.getString("kind"),r.getObject("loan_id",UUID.class),r.getString("loan_number"),r.getBigDecimal("amount"),r.getBigDecimal("encumbrance"),r.getObject("valid_through",LocalDate.class),r.getObject("effective_date",LocalDate.class),r.getString("evidence"),r.getString("reason"),r.getString("actor"),r.getObject("recorded_at",OffsetDateTime.class)),asset,a.getSaccoId(),a.getStationId(),page*25);}
    public LocalDate assetDate(UUID id){return jdbc.queryForObject("select coalesce((select max(e.effective_date) from lending_collateral_event e where e.asset_id=a.id),date '1901-01-01') from lending_collateral_asset a where id=?",LocalDate.class,id);}
    public UUID lastPledgeActor(UUID asset,UUID loan){return jdbc.queryForObject("select actor_id from lending_collateral_event where asset_id=? and loan_id=? and kind='PLEDGE' order by sequence desc limit 1",UUID.class,asset,loan);}
    public LocalDate caseDate(UUID id){return jdbc.queryForObject("select greatest(c.recorded_at::date,coalesce((select max(e.effective_date) from lending_collection_event e where e.case_id=c.id),c.recorded_at::date)) from lending_collection_case c where id=?",LocalDate.class,id);}
    public boolean otherOpenCase(UUID loan,UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from lending_collection_case c where loan_id=? and id<>? and coalesce((select e.kind from lending_collection_event e where e.case_id=c.id and e.kind in ('CLOSE','REOPEN') order by sequence desc limit 1),'OPEN')<>'CLOSE')",Boolean.class,loan,id));}
    private static final String LOANS="""
        select l.id,coalesce(l.loan_id,l.application_number::text,l.id::text) number,
          coalesce(m.full_name,party.full_name,'') borrower,l.status,b.loan_application_id is not null and s.installments>0 tracked,
          b.principal-b.principal_paid principal,s.due_interest,s.future_interest,coalesce(c.outstanding,0) charges,s.arrears,s.oldest_due,s.next_due
        from loan_applications l left join members m on m.id=l.applicant_member_id and m.sacco_id=l.sacco_id left join loan_borrower_directory party on party.party_id=l.borrower_party_id and party.sacco_id=l.sacco_id
        left join loan_ledgers b on b.loan_application_id=l.id and b.sacco_id=l.sacco_id and b.station_id=l.station_id
        left join lateral(select count(*) installments,coalesce(sum(loan_interest_due(i.id,?::date)),0) due_interest,
          coalesce(sum(greatest(0,i.interest-i.interest_paid-i.interest_excluded-i.interest_waived-loan_interest_due(i.id,?::date))),0) future_interest,
          coalesce(sum(i.principal-i.principal_paid+i.interest-i.interest_paid-i.interest_excluded-i.interest_waived) filter(where i.due_date<?::date),0) arrears,
          min(i.due_date) filter(where i.due_date<?::date and (i.principal>i.principal_paid or i.interest>i.interest_paid+i.interest_excluded+i.interest_waived)) oldest_due,
          min(i.due_date) filter(where i.principal-i.principal_paid+i.interest-i.interest_paid-i.interest_excluded-i.interest_waived>0) next_due
          from loan_ledger_installments i where i.loan_application_id=l.id) s on true
        left join loan_charge_balance c on c.loan_id=l.id where l.sacco_id=? and l.station_id=?
        """;
    private static final RowMapper<Loan> LOAN=(r,n)->new Loan(r.getObject("id",UUID.class),r.getString("number"),r.getString("borrower"),r.getString("status"),r.getBigDecimal("principal"),r.getBigDecimal("due_interest"),r.getBigDecimal("future_interest"),r.getBigDecimal("charges"),r.getBigDecimal("arrears"),r.getObject("oldest_due",LocalDate.class),r.getObject("next_due",LocalDate.class),r.getBoolean("tracked"));
    public Loan loan(AppUserPrincipal a,UUID id,LocalDate day,boolean lock){if(lock)one(jdbc.query("select id from loan_applications where id=? and sacco_id=? and station_id=? for update",(r,n)->r.getObject(1,UUID.class),id,a.getSaccoId(),a.getStationId()));return one(jdbc.query(LOANS+" and l.id=?",LOAN,day,day,day,day,a.getSaccoId(),a.getStationId(),id));}
    public List<Loan> loans(AppUserPrincipal a,String search,int page,LocalDate day,boolean overdue){return jdbc.query(LOANS+" and l.status not in ('WITHDRAWN','DISBURSEMENT_REVERSED') and (lower(coalesce(l.loan_id,l.application_number::text,'')) like ? or lower(coalesce(m.full_name,party.full_name,'')) like ?)"+(overdue?" and b.loan_application_id is not null and s.arrears>0":"")+" order by "+ResourceTableSort.order("credit-loan-picker",Map.of("loan","number","borrower","borrower","arrears","s.arrears"),"l.id","l.created_at desc,l.id")+" limit 26 offset ?",LOAN,day,day,day,day,a.getSaccoId(),a.getStationId(),like(search),like(search),page*25);}
    private static final String CASES="""
        select c.*,coalesce(l.loan_id,l.application_number::text,l.id::text) loan_number,coalesce(m.full_name,party.full_name,'') borrower,
          case when lifecycle.kind in ('CLOSE','DISPUTE') then lifecycle.kind else 'OPEN' end state,coalesce(assignment.assigned_to,c.assigned_to) current_officer,
          officer.full_name officer,case when lifecycle.kind='CLOSE' then null else coalesce(last.next_action,c.next_action) end action_on,coalesce(last.sequence,0) sequence,
          lending_coverage_due(l.id,?::date) arrears,
          (select min(i.due_date) from loan_ledger_installments i where i.loan_application_id=l.id and i.due_date<?::date and (i.principal>i.principal_paid or i.interest>i.interest_paid+i.interest_excluded+i.interest_waived)) oldest_due
        from lending_collection_case c join loan_applications l on l.id=c.loan_id left join members m on m.id=l.applicant_member_id and m.sacco_id=l.sacco_id
        left join loan_borrower_directory party on party.party_id=l.borrower_party_id and party.sacco_id=l.sacco_id
        left join lateral(select * from lending_collection_event e where e.case_id=c.id order by sequence desc limit 1) last on true
        left join lateral(select kind from lending_collection_event e where e.case_id=c.id and kind in ('DISPUTE','RESOLVE','CLOSE','REOPEN') order by sequence desc limit 1) lifecycle on true
        left join lateral(select assigned_to from lending_collection_event e where e.case_id=c.id and kind='ASSIGN' order by sequence desc limit 1) assignment on true
        join members officer on officer.id=coalesce(assignment.assigned_to,c.assigned_to)
        where c.sacco_id=? and c.station_id=?
        """;
    private static final RowMapper<Case> CASE=(r,n)->new Case(r.getObject("id",UUID.class),r.getObject("loan_id",UUID.class),r.getString("loan_number"),r.getString("borrower"),r.getString("state"),r.getObject("current_officer",UUID.class),r.getString("officer"),r.getObject("action_on",LocalDate.class),r.getBigDecimal("arrears"),r.getObject("oldest_due",LocalDate.class),r.getInt("sequence"),r.getString("reason"));
    public List<Case> cases(AppUserPrincipal a,String search,int page,String order,LocalDate day){return jdbc.query(CASES+" and (lower(coalesce(l.loan_id,l.application_number::text,'')) like ? or lower(coalesce(m.full_name,party.full_name,'')) like ?) order by "+order+",c.id limit 26 offset ?",CASE,day,day,a.getSaccoId(),a.getStationId(),like(search),like(search),page*25);}
    public Case collection(AppUserPrincipal a,UUID id,LocalDate day,boolean lock){if(lock)one(jdbc.query("select id from lending_collection_case where id=? and sacco_id=? and station_id=? for update",(r,n)->r.getObject(1,UUID.class),id,a.getSaccoId(),a.getStationId()));return one(jdbc.query(CASES+" and c.id=?",CASE,day,day,a.getSaccoId(),a.getStationId(),id));}
    public boolean openCase(UUID loan){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from lending_collection_case c where loan_id=? and coalesce((select e.kind from lending_collection_event e where e.case_id=c.id order by sequence desc limit 1),'OPEN')<>'CLOSE')",Boolean.class,loan));}
    public void collection(AppUserPrincipal a,UUID id,CaseForm f,String hash,OffsetDateTime now){jdbc.update("insert into lending_collection_case values(?,?,?,?,?,?,?,?,?,?,?)",id,f.getLoanId(),a.getSaccoId(),a.getStationId(),f.getAssignedTo(),f.getNextAction(),f.getReason().strip(),f.getRequestKey(),hash,a.getMemberId(),now);}
    public void collectionEvent(AppUserPrincipal a,UUID id,UUID caseId,CollectionForm f,String hash,OffsetDateTime now){jdbc.update("insert into lending_collection_event values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,caseId,a.getSaccoId(),a.getStationId(),f.getExpectedSequence()+1,f.getKind(),f.getAssignedTo(),f.getNextAction(),f.getAmount(),f.getPromiseOn(),f.getEvidence().strip(),f.getEffectiveDate(),f.getRequestKey(),hash,a.getMemberId(),now);}
    public List<CollectionEvent> collectionHistory(AppUserPrincipal a,UUID id,int page){return jdbc.query("select e.*,m.full_name actor,o.full_name officer from lending_collection_event e join members m on m.id=e.actor_id left join members o on o.id=e.assigned_to where e.case_id=? and e.sacco_id=? and e.station_id=? order by sequence desc limit 26 offset ?",(r,n)->new CollectionEvent(r.getObject("id",UUID.class),r.getString("kind"),r.getString("officer"),r.getObject("next_action",LocalDate.class),r.getBigDecimal("amount"),r.getObject("promise_on",LocalDate.class),r.getObject("effective_date",LocalDate.class),r.getString("evidence"),r.getString("actor"),r.getObject("recorded_at",OffsetDateTime.class)),id,a.getSaccoId(),a.getStationId(),page*25);}
}
