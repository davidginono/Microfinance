package com.sacco.mvp.grouplending.repository;
import com.sacco.mvp.grouplending.dto.GroupCoverageDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class GroupCoverageRepository {
 private final JdbcTemplate jdbc;
 public UUID product(AppUserPrincipal a,UUID cycle){return jdbc.query("select product_id from lending_group_cycle where id=? and sacco_id=? and station_id=?",(r,n)->r.getObject(1,UUID.class),cycle,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Coverage unavailable"));}
 public void policyLock(AppUserPrincipal a,UUID product){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","COVERAGE_POLICY/"+a.getSaccoId()+"/"+a.getStationId()+"/"+product);}
 public Optional<Policy> policy(AppUserPrincipal a,UUID product){return jdbc.query("select id,revision,notice_days,case_days,promise_days,reference,enabled from lending_group_coverage_policy where sacco_id=? and station_id=? and product_id=? order by revision desc limit 1",(r,n)->new Policy(r.getObject(1,UUID.class),r.getInt(2),r.getInt(3),r.getInt(4),r.getInt(5),r.getString(6),r.getBoolean(7)),a.getSaccoId(),a.getStationId(),product).stream().findFirst();}
 public void requestLock(AppUserPrincipal a,UUID key){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","COVERAGE/"+a.getSaccoId()+"/"+a.getStationId()+"/"+key);}
 public Optional<UUID> retry(AppUserPrincipal a,String table,UUID key,String hash){if(!Set.of("lending_group_coverage_policy","lending_group_coverage_case").contains(table))throw new IllegalArgumentException();return jdbc.query("select id,payload_hash,recorded_by from "+table+" where sacco_id=? and station_id=? and request_key=?",(r,n)->{if(!hash.equals(r.getString(2))||!a.getMemberId().equals(r.getObject(3,UUID.class)))throw new IllegalArgumentException("group.error.retry");return r.getObject(1,UUID.class);},a.getSaccoId(),a.getStationId(),key).stream().findFirst();}
 public UUID recordPolicy(AppUserPrincipal a,UUID product,PolicyForm f,Policy prior,String hash,OffsetDateTime at){UUID id=UUID.randomUUID();jdbc.update("insert into lending_group_coverage_policy(id,sacco_id,station_id,product_id,revision,previous_id,request_key,payload_hash,notice_days,case_days,promise_days,reference,enabled,recorded_by,recorded_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,a.getSaccoId(),a.getStationId(),product,prior==null?1:prior.revision()+1,prior==null?null:prior.id(),f.getRequestKey(),hash,f.getNoticeDays(),f.getCaseDays(),f.getPromiseDays(),f.getReference().strip(),f.isEnabled(),a.getMemberId(),at);return id;}
 private static final String SOURCES="""
 select s.*,m.member_no,m.full_name,b.full_name borrower,coalesce(l.loan_id,l.application_number::text) loan_number,l.loan_product_setting_id,l.status,
  lending_coverage_due(l.id,?::date) arrears,(select min(due_date) from loan_ledger_installments where loan_application_id=l.id and due_date<?::date and principal-principal_paid+interest-interest_paid-interest_excluded-interest_waived>0) oldest_due,
  exists(select 1 from loan_ledgers where loan_application_id=l.id and sacco_id=s.sacco_id and station_id=s.station_id)
   and exists(select 1 from accountant_loan_post where loan_id=l.id and kind='DISBURSEMENT' and state='POSTED') known
 from lending_group_coverage_source s join loan_applications l on l.id=s.loan_id and l.sacco_id=s.sacco_id and l.station_id=s.station_id
 join loan_borrower_directory b on b.party_id=l.borrower_party_id and b.sacco_id=l.sacco_id
 join members m on m.id=s.client_id and m.sacco_id=s.sacco_id and m.station_id=s.station_id and m.is_member and m.status='ACTIVE'
 where s.cycle_id=? and s.sacco_id=? and s.station_id=?
 """;
 private static final RowMapper<Source> SOURCE=(r,n)->new Source(r.getObject("source_id",UUID.class),r.getString("source_kind"),r.getObject("cycle_id",UUID.class),r.getObject("loan_id",UUID.class),r.getObject("client_id",UUID.class),r.getString("member_no"),r.getString("full_name"),r.getString("borrower"),r.getString("loan_number"),r.getObject("loan_product_setting_id",UUID.class),r.getBigDecimal("cap"),r.getString("terms_reference"),r.getBigDecimal("arrears"),r.getObject("oldest_due",LocalDate.class),r.getString("status"),r.getBoolean("known"));
 public List<Source> sources(AppUserPrincipal a,UUID cycle,LocalDate date,int page){return jdbc.query(SOURCES+" and l.status in ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') order by loan_number,s.client_id,s.source_kind,s.source_id limit 26 offset ?",SOURCE,date,date,cycle,a.getSaccoId(),a.getStationId(),page*25);}
 public Source source(AppUserPrincipal a,UUID cycle,UUID id,String kind,LocalDate date,boolean lock){return jdbc.query(SOURCES+" and s.source_id=? and s.source_kind=?"+(lock?" for update of l":""),SOURCE,date,date,cycle,a.getSaccoId(),a.getStationId(),id,kind).stream().findFirst().orElseThrow(()->new AccessDeniedException("Coverage obligation unavailable"));}
 public UUID record(AppUserPrincipal a,Source s,Policy p,CallForm f,String hash,String filename,String checksum,int pages,byte[] bytes,LocalDate date,OffsetDateTime at){UUID id=UUID.randomUUID();jdbc.update("""
 insert into lending_group_coverage_case(id,cycle_id,loan_id,source_id,source_kind,sacco_id,station_id,client_id,client_number,client_name,borrower_name,loan_number,policy_id,request_key,payload_hash,call_date,expires_on,oldest_due,requested,source_cap,arrears_snapshot,borrower_notice_date,person_notice_date,borrower_notice_evidence,person_notice_evidence,notice_language,filename,notice_pdf,notice_sha256,notice_pages,terms_reference,recorded_by,recorded_at)
 values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
 """,id,s.cycle(),s.loan(),s.id(),s.kind(),a.getSaccoId(),a.getStationId(),s.client(),s.number(),s.name(),s.borrower(),s.loanNumber(),p.id(),f.getRequestKey(),hash,date,date.plusDays(p.caseDays()),s.oldestDue(),f.getAmount(),s.cap(),s.arrears(),f.getBorrowerNoticeDate(),f.getPersonNoticeDate(),f.getBorrowerNoticeEvidence().strip(),f.getPersonNoticeEvidence().strip(),f.getNoticeLanguage(),filename,bytes,checksum,pages,s.terms(),a.getMemberId(),at);return id;}
 private static final String CASES="""
 select c.*,b.remaining_due,b.unpaid,b.received,coalesce(e.sequence,0) sequence,
  case when b.closure is not null then b.closure when b.remaining_due is null then 'UNAVAILABLE' when b.received>=c.requested then 'RECEIVED'
   when b.received>0 then 'PART_RECEIVED' when b.remaining_due=0 then 'CURED_ELSEWHERE' when c.expires_on<?::date then 'EXPIRED' else coalesce(e.kind,case when c.source_kind='VOLUNTARY' then 'REQUESTED' else 'CALLED' end) end case_state
 from lending_group_coverage_case c join lending_group_coverage_balance b on b.id=c.id
 left join lateral(select kind,sequence from lending_group_coverage_event where case_id=c.id order by sequence desc limit 1) e on true
 where c.sacco_id=? and c.station_id=?
 """;
 private static final RowMapper<Case> CASE=(r,n)->new Case(r.getObject("id",UUID.class),r.getObject("cycle_id",UUID.class),r.getObject("loan_id",UUID.class),r.getObject("source_id",UUID.class),r.getString("source_kind"),r.getObject("client_id",UUID.class),r.getString("client_number"),r.getString("client_name"),r.getString("borrower_name"),r.getString("loan_number"),r.getObject("policy_id",UUID.class),r.getObject("call_date",LocalDate.class),r.getObject("expires_on",LocalDate.class),r.getObject("oldest_due",LocalDate.class),r.getBigDecimal("requested"),r.getBigDecimal("source_cap"),r.getBigDecimal("arrears_snapshot"),r.getBigDecimal("remaining_due"),r.getBigDecimal("unpaid"),r.getBigDecimal("received"),r.getString("case_state"),r.getInt("sequence"),r.getString("terms_reference"),r.getString("notice_language"),r.getString("notice_sha256"),r.getInt("notice_pages"),r.getObject("recorded_at",OffsetDateTime.class));
 public List<Case> cases(AppUserPrincipal a,UUID cycle,LocalDate date,int page,boolean own){return jdbc.query(CASES+(own?" and c.client_id=?":" and c.cycle_id=?")+" order by c.recorded_at desc,c.id desc limit 26 offset ?",CASE,date,a.getSaccoId(),a.getStationId(),own?a.getMemberId():cycle,page*25);}
 public List<Case> trace(AppUserPrincipal a,UUID cycle,UUID client,LocalDate today,LocalDate from,LocalDate through,int offset,int limit){
  return jdbc.query(CASES+" and c.cycle_id=? and (?::uuid is null or c.client_id=?) and (?::date is null or c.call_date>=?) and c.call_date<=? order by c.recorded_at desc,c.id desc limit ? offset ?",CASE,today,a.getSaccoId(),a.getStationId(),cycle,client,client,from,from,through,limit,offset);
 }
 public Case detail(AppUserPrincipal a,UUID id,LocalDate date,boolean own){return jdbc.query(CASES+" and c.id=?"+(own?" and c.client_id=?":""),CASE,own?new Object[]{date,a.getSaccoId(),a.getStationId(),id,a.getMemberId()}:new Object[]{date,a.getSaccoId(),a.getStationId(),id}).stream().findFirst().orElseThrow(()->new AccessDeniedException("Coverage case unavailable"));}
 public void lock(AppUserPrincipal a,Case c){jdbc.queryForList("select id from loan_applications where id=? and sacco_id=? and station_id=? for update",c.loan(),a.getSaccoId(),a.getStationId());jdbc.queryForList("select id from lending_group_coverage_case where id=? and sacco_id=? and station_id=? for update",c.id(),a.getSaccoId(),a.getStationId());}
 public Optional<UUID> eventRetry(Case c,UUID key,String hash,UUID actor){return jdbc.query("select id,payload_hash,actor_id from lending_group_coverage_event where case_id=? and request_key=?",(r,n)->{if(!hash.equals(r.getString(2))||!actor.equals(r.getObject(3,UUID.class)))throw new IllegalArgumentException("group.error.retry");return r.getObject(1,UUID.class);},c.id(),key).stream().findFirst();}
 public Policy frozenPolicy(Case c){return jdbc.queryForObject("select id,revision,notice_days,case_days,promise_days,reference,enabled from lending_group_coverage_policy where id=?",(r,n)->new Policy(r.getObject(1,UUID.class),r.getInt(2),r.getInt(3),r.getInt(4),r.getInt(5),r.getString(6),r.getBoolean(7)),c.policy());}
 public UUID event(AppUserPrincipal a,Case c,ResponseForm f,String hash,OffsetDateTime at){UUID id=UUID.randomUUID();jdbc.update("insert into lending_group_coverage_event(id,case_id,sequence,kind,amount,promise_on,reason,request_key,payload_hash,actor_id,recorded_at) values(?,?,?,?,?,?,?,?,?,?,?)",id,c.id(),c.sequence()+1,f.getKind(),f.getAmount(),f.getPromiseOn(),f.getReason().strip(),f.getRequestKey(),hash,a.getMemberId(),at);return id;}
 public List<Event> events(Case c,int page){return events(c,page,26);}
 public List<Event> events(Case c,int page,int limit){return jdbc.query("select id,sequence,kind,amount,promise_on,reason,recorded_at from lending_group_coverage_event where case_id=? order by sequence desc limit ? offset ?",(r,n)->new Event(r.getObject(1,UUID.class),r.getInt(2),r.getString(3),r.getBigDecimal(4),r.getObject(5,LocalDate.class),r.getString(6),r.getObject(7,OffsetDateTime.class)),c.id(),limit,page*25);}
 public byte[] notice(Case c){return jdbc.queryForObject("select notice_pdf from lending_group_coverage_case where id=?",byte[].class,c.id());}
 public List<Payment> payments(Case c,int page){return payments(c,page,26);}
 public List<Payment> payments(Case c,int page,int limit){return jdbc.query("""
 select x.*,coalesce(r.id,g.id,p.id) receipt,coalesce(r.number,g.number,p.reference) number
 from lending_group_coverage_cash x join accountant_loan_post p on p.id=x.posting_id
 left join accountant_loan_receipt_row rr on rr.posting_id=p.id left join accountant_loan_receipt r on r.id=rr.receipt_id
 left join lending_group_receipt_loan gr on gr.posting_id=p.id left join lending_group_receipt g on g.id=gr.receipt_id
 where x.case_id=? order by x.recorded_at desc,x.id desc limit ? offset ?
 """,(r,n)->new Payment(r.getObject("id",UUID.class),r.getObject("posting_id",UUID.class),r.getObject("receipt",UUID.class),r.getString("number"),r.getObject("effective_date",LocalDate.class),r.getString("payer_text"),r.getBigDecimal("amount"),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("charges"),r.getBigDecimal("net_amount"),r.getObject("reversed_by",UUID.class),r.getObject("corrected_by",UUID.class),r.getString("evidence"),r.getObject("recorded_at",OffsetDateTime.class)),c.id(),limit,page*25);}
 public List<PaymentSource> paymentChoices(AppUserPrincipal a,Case c,int page){return jdbc.query("""
 select p.id posting,x.id contribution,coalesce(r.id,g.id,p.id) receipt,coalesce(r.number,g.number,p.reference) reference,
 p.effective_date,p.recorded_at,x.payer_id,
 case when x.id is not null then x.payer_number||' - '||x.payer_name else
 coalesce(nullif(r.payer_name,''),(select min(payer) from lending_group_shared_movement where posting_id=p.id),p.notes,'') end payer,
 case when x.id is not null then x.amount else p.amount end amount,
 case when x.id is not null then x.principal else p.principal_amount end principal,
 case when x.id is not null then x.interest else p.interest_amount end interest,
 case when x.id is not null then x.charges else p.charge_amount end charges
 from accountant_loan_post p
 left join accountant_loan_receipt_row rr on rr.posting_id=p.id left join accountant_loan_receipt r on r.id=rr.receipt_id
 left join lending_group_receipt_loan gr on gr.posting_id=p.id left join lending_group_receipt g on g.id=gr.receipt_id
 left join lending_group_contribution x on x.receipt_id=g.id and x.loan_id=p.loan_id and x.payer_id=?
 where p.loan_id=? and p.sacco_id=? and p.station_id=? and p.kind='PAYMENT' and p.state='POSTED'
 and p.effective_date>=? and p.recorded_at>=? and (g.id is null or x.id is not null)
 and not exists(select 1 from accountant_loan_post where reverses_id=p.id)
 and not exists(select 1 from lending_group_coverage_payment z where z.posting_id=p.id and z.contribution_id is not distinct from x.id
 and not exists(select 1 from lending_group_coverage_payment_correction where payment_id=z.id))
 order by p.recorded_at desc,p.id desc,x.id limit 26 offset ?
 """,(r,n)->new PaymentSource(r.getObject("posting",UUID.class),r.getObject("contribution",UUID.class),r.getObject("receipt",UUID.class),r.getString("reference"),r.getObject("effective_date",LocalDate.class),r.getString("payer"),r.getObject("payer_id",UUID.class),r.getBigDecimal("amount"),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("charges"),r.getObject("recorded_at",OffsetDateTime.class)),c.client(),c.loan(),a.getSaccoId(),a.getStationId(),c.callDate(),c.recordedAt(),page*25);}
 public PaymentSource paymentSource(AppUserPrincipal a,Case c,UUID posting,UUID contribution){return jdbc.query("""
 select p.*,x.id contribution,x.payer_id,x.payer_number||' - '||x.payer_name contribution_payer,x.amount contribution_amount,x.principal contribution_principal,x.interest contribution_interest,x.charges contribution_charges,
 g.id group_receipt,coalesce(r.id,g.id,p.id) receipt,coalesce(r.number,g.number,p.reference) number,
 coalesce(nullif(r.payer_name,''),g.remitter,(select min(payer) from lending_group_shared_movement where posting_id=p.id),p.notes,'') payer
 from accountant_loan_post p left join accountant_loan_receipt_row rr on rr.posting_id=p.id left join accountant_loan_receipt r on r.id=rr.receipt_id
 left join lending_group_receipt_loan gr on gr.posting_id=p.id left join lending_group_receipt g on g.id=gr.receipt_id
 left join lending_group_contribution x on x.id=? and x.receipt_id=g.id and x.loan_id=p.loan_id
 where p.id=? and p.loan_id=? and p.sacco_id=? and p.station_id=? and p.kind='PAYMENT' and p.state='POSTED'
 and not exists(select 1 from accountant_loan_post where reverses_id=p.id)
 """,(r,n)->{boolean split=r.getObject("contribution")!=null;if(!Objects.equals(contribution,r.getObject("contribution",UUID.class))||!split&&r.getObject("group_receipt")!=null)throw new IllegalArgumentException("coverage.error.payment");return new PaymentSource(posting,r.getObject("contribution",UUID.class),r.getObject("receipt",UUID.class),r.getString("number"),r.getObject("effective_date",LocalDate.class),r.getString(split?"contribution_payer":"payer"),r.getObject("payer_id",UUID.class),r.getBigDecimal(split?"contribution_amount":"amount"),r.getBigDecimal(split?"contribution_principal":"principal_amount"),r.getBigDecimal(split?"contribution_interest":"interest_amount"),r.getBigDecimal(split?"contribution_charges":"charge_amount"),r.getObject("recorded_at",OffsetDateTime.class));},contribution,posting,c.loan(),a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new IllegalArgumentException("coverage.error.payment"));}
 public Optional<UUID> paymentRetry(Case c,UUID key,String hash,UUID actor){return jdbc.query("select id,payload_hash,actor_id from lending_group_coverage_payment where case_id=? and request_key=?",(r,n)->{if(!hash.equals(r.getString(2))||!actor.equals(r.getObject(3,UUID.class)))throw new IllegalArgumentException("group.error.retry");return r.getObject(1,UUID.class);},c.id(),key).stream().findFirst();}
 public java.math.BigDecimal reserved(Source s,LocalDate date){return jdbc.queryForObject("select coalesce(sum(unpaid),0) from lending_group_coverage_balance where loan_id=? and closure is null and expires_on>=?",java.math.BigDecimal.class,s.loan(),date);}
 public java.math.BigDecimal received(UUID loan,UUID source,String kind){return jdbc.queryForObject("select coalesce(sum(received),0) from lending_group_coverage_balance where loan_id=? and source_id=? and source_kind=?",java.math.BigDecimal.class,loan,source,kind);}
 public boolean linked(PaymentSource p){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from lending_group_coverage_payment x where posting_id=? and contribution_id is not distinct from ?::uuid and not exists(select 1 from lending_group_coverage_payment_correction where payment_id=x.id))",Boolean.class,p.posting(),p.contribution()));}
 public UUID link(AppUserPrincipal a,Case c,PaymentSource p,LinkForm f,String hash,OffsetDateTime at){UUID id=UUID.randomUUID();jdbc.update("insert into lending_group_coverage_payment(id,case_id,posting_id,contribution_id,amount,principal,interest,charges,payer_text,evidence,request_key,payload_hash,actor_id,recorded_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,c.id(),p.posting(),p.contribution(),p.amount(),p.principal(),p.interest(),p.charges(),p.payer(),f.getPayerEvidence().strip(),f.getRequestKey(),hash,a.getMemberId(),at);return id;}
 public UUID correct(AppUserPrincipal a,Case c,UUID payment,UUID key,String reason,OffsetDateTime at){var row=jdbc.queryForList("select x.actor_id,c.id,c.actor_id correction_actor,c.request_key,c.reason from lending_group_coverage_payment x left join lending_group_coverage_payment_correction c on c.payment_id=x.id where x.id=? and x.case_id=?",payment,c.id());if(row.isEmpty())throw new AccessDeniedException("Coverage link unavailable");var r=row.getFirst();if(r.get("id")!=null){if(!key.equals(r.get("request_key"))||!reason.equals(r.get("reason"))||!a.getMemberId().equals(r.get("correction_actor")))throw new IllegalArgumentException("group.error.retry");return (UUID)r.get("id");}if(a.getMemberId().equals(r.get("actor_id")))throw new IllegalArgumentException("coverage.error.independent");UUID id=UUID.randomUUID();jdbc.update("insert into lending_group_coverage_payment_correction(id,payment_id,reason,request_key,actor_id,recorded_at) values(?,?,?,?,?,?)",id,payment,reason,key,a.getMemberId(),at);return id;}
}
