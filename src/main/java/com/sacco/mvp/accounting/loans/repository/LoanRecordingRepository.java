package com.sacco.mvp.accounting.loans.repository;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.dto.RegisterSort;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

import static com.sacco.mvp.accounting.batch.repository.ReceiptBatchReferenceSql.POST_ITEM_ID;

@Repository @RequiredArgsConstructor
public class LoanRecordingRepository {
    public boolean editable(AppUserPrincipal a,UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from accountant_loan_record r where r.loan_id=? and r.sacco_id=? and r.station_id=? and r.actor_id=? and loan_record_editable(r.loan_id))",Boolean.class,id,a.getSaccoId(),a.getStationId(),a.getMemberId()));}
    public Optional<UUID> editRetry(AppUserPrincipal a,UUID id,UUID key,String hash){
        var rows=jdbc.queryForList("select loan_id,actor_id,payload_hash from loan_record_edit where sacco_id=? and station_id=? and request_key=?",a.getSaccoId(),a.getStationId(),key);
        if(rows.isEmpty())return Optional.empty();var row=rows.getFirst();
        if(!id.equals(row.get("loan_id"))||!a.getMemberId().equals(row.get("actor_id"))||!hash.equals(row.get("payload_hash")))throw new IllegalArgumentException("recording.error.retry");
        return Optional.of(id);
    }
    public void beginEdit(AppUserPrincipal a,UUID id,RecordForm f,String hash){
        jdbc.update("insert into loan_record_edit(loan_id,sacco_id,station_id,actor_id,request_key,payload_hash,expected_version) values(?,?,?,?,?,?,?)",id,a.getSaccoId(),a.getStationId(),a.getMemberId(),f.getRequestKey(),hash,f.getExpectedVersion());
    }
    public void updateRecord(AppUserPrincipal a,UUID id,RecordForm f,String product,String metadata){
        jdbc.update("update accountant_loan_record set application_date=?,requested_principal=?,first_payment_date=?,product_name=?,metadata=?::jsonb where loan_id=? and sacco_id=? and station_id=?",f.getApplicationDate(),f.getRequestedPrincipal(),f.getFirstPaymentDate(),product,metadata,id,a.getSaccoId(),a.getStationId());
    }
    public Map<com.sacco.mvp.domain.Position,List<UUID>> groupCandidates(AppUserPrincipal a,GroupLoan loan,String groups) {
        var candidates=new LinkedHashMap<com.sacco.mvp.domain.Position,List<UUID>>();
        jdbc.query("""
            select g.value,m.id from jsonb_array_elements_text(?::jsonb) g cross join lateral(
              select u.id from members u where u.sacco_id=? and u.station_id=? and u.status='ACTIVE'
              and u.id is distinct from ?::uuid and u.id is distinct from ?::uuid and u.id is distinct from ?::uuid
               and not loan_group_related(?::uuid,u.id)
              and (u.staff_access_status='ACTIVE' or (u.staff_access_status='NONE' and coalesce(u.staff_no,'')='' and u.staff_access_assigned_at is null and u.staff_access_activated_at is null))
              and (u.position=g.value or exists(select 1 from member_staff_roles r where r.member_id=u.id and r.role_name=g.value))
              and exists(select 1 from member_access_claims c where c.member_id=u.id and c.claim_name='LOAN_RECORDING_VIEW')
              and exists(select 1 from member_access_claims c where c.member_id=u.id and c.claim_name='LOAN_RECORDING_APPROVE')
              and u.position<>'ADMIN' and not exists(select 1 from member_staff_roles r where r.member_id=u.id and r.role_name='ADMIN')
              order by u.id limit 51) m
            """,(org.springframework.jdbc.core.RowCallbackHandler) r->candidates.computeIfAbsent(com.sacco.mvp.domain.Position.valueOf(r.getString(1)),k->new ArrayList<>()).add(r.getObject(2,UUID.class)),
            groups,a.getSaccoId(),a.getStationId(),a.getMemberId(),loan.client(),loan.recorder(),loan.id());
        return candidates;
    }
    public int draftVersion(AppUserPrincipal a,UUID id){return jdbc.queryForObject("select coalesce(max(version),0) from client_application_command where application_id=? and sacco_id=? and station_id=?",Integer.class,id,a.getSaccoId(),a.getStationId());}
    public boolean retiredGroup(AppUserPrincipal a,UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("select retired_group_application(id) from loan_applications where id=? and sacco_id=? and station_id=?",Boolean.class,id,a.getSaccoId(),a.getStationId()));}
    public boolean groupWithdrawalAvailable(AppUserPrincipal a,UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("select (l.applicant_member_id is null or exists(select 1 from lending_group_participant where loan_id=l.id)) and l.status not in('DISBURSED','PAR','DEFAULTED','PAID','WRITTEN_OFF','DISBURSEMENT_REVERSED','WITHDRAWN') and not exists(select 1 from loan_ledgers where loan_application_id=l.id) from loan_applications l where l.id=? and l.sacco_id=? and l.station_id=?",Boolean.class,id,a.getSaccoId(),a.getStationId()));}
    public void withdrawGroup(AppUserPrincipal a,GroupLoan loan,UUID key,String reason,java.time.OffsetDateTime at){
        var previous=jdbc.queryForList("select * from staff_group_withdrawal where loan_id=? and sacco_id=? and station_id=?",loan.id(),a.getSaccoId(),a.getStationId());
        if(!previous.isEmpty()){var p=previous.getFirst();if(!key.equals(p.get("request_key"))||!a.getMemberId().equals(p.get("actor_id"))||!reason.strip().equals(p.get("reason")))throw new IllegalArgumentException("simpleGroup.error.changed");return;}
        jdbc.update("insert into staff_group_withdrawal(loan_id,request_key,sacco_id,station_id,actor_id,previous_status,terms_hash,reason,recorded_at) values(?,?,?,?,?,?,?,?,?)",loan.id(),key,a.getSaccoId(),a.getStationId(),a.getMemberId(),loan.status(),loan.termsHash(),reason.strip(),at);
        jdbc.update("update loan_applications set status='WITHDRAWN',updated_at=?,version=version+1 where id=? and sacco_id=? and station_id=?",at,loan.id(),a.getSaccoId(),a.getStationId());
    }
    public GroupLoan groupLoan(AppUserPrincipal a,UUID id,boolean lock) {
        return jdbc.query("""
            select l.id,l.loan_product_setting_id,l.applicant_member_id,r.actor_id,l.status,
            loan_group_terms(l),
            coalesce(l.loan_id,l.application_number::text),b.full_name,l.amount,loan_group_related(l.id,?::uuid)
            from loan_applications l left join accountant_loan_record r on r.loan_id=l.id
            join loan_borrower_directory b on b.party_id=l.borrower_party_id and b.sacco_id=l.sacco_id
            where l.id=? and l.sacco_id=? and l.station_id=?
            """+(lock?" for update of l":""),(r,n)->new GroupLoan(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getObject(4,UUID.class),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getBigDecimal(9),r.getBoolean(10)),
            a.getMemberId(),id,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Loan unavailable"));
    }

    public Optional<GroupRule> groupRule(AppUserPrincipal a,UUID product) {
        return jdbc.query("select id,product_id,revision,steps::text from loan_group_rule where sacco_id=? and station_id=? and product_id=? order by revision desc limit 1",
            (r,n)->new GroupRule(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getInt(3),r.getString(4)),a.getSaccoId(),a.getStationId(),product).stream().findFirst();
    }
    public void groupRule(AppUserPrincipal a,GroupRule rule,OffsetDateTime at) {
        jdbc.update("insert into loan_group_rule(id,sacco_id,station_id,product_id,revision,steps,actor_id,recorded_at) values(?,?,?,?,?,?::jsonb,?,?)",
            rule.id(),a.getSaccoId(),a.getStationId(),rule.product(),rule.revision(),rule.steps(),a.getMemberId(),at);
    }
    public void lockGroupRule(AppUserPrincipal a,UUID product) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","LOAN_GROUP_RULE/"+a.getSaccoId()+"/"+a.getStationId()+"/"+product);
    }
    public Optional<GroupRequest> groupRequest(AppUserPrincipal a,UUID loan) {
        return jdbc.query("select id,loan_id,rule_id,maker_id,round,terms_hash,previous_status from loan_group_request where loan_id=? and sacco_id=? and station_id=? order by round desc limit 1",
            (r,n)->new GroupRequest(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getObject(4,UUID.class),r.getInt(5),r.getString(6),r.getString(7)),loan,a.getSaccoId(),a.getStationId()).stream().findFirst();
    }
    public String groupSteps(AppUserPrincipal a,UUID rule) {
        return jdbc.queryForObject("select steps::text from loan_group_rule where id=? and sacco_id=? and station_id=?",String.class,rule,a.getSaccoId(),a.getStationId());
    }
    public List<GroupDecision> groupDecisions(GroupRequest request) {
        return jdbc.query("""
            select d.id,d.step,d.actor_id,m.full_name,d.decision,d.reason,d.evidence,d.recorded_at
            from loan_group_decision d join members m on m.id=d.actor_id where d.request_id=? order by d.recorded_at,d.id limit 101
            """,(r,n)->new GroupDecision(r.getObject(1,UUID.class),r.getInt(2),r.getObject(3,UUID.class),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getObject(8,OffsetDateTime.class)),request.id());
    }
    public void groupRequest(AppUserPrincipal a,GroupRequest r,OffsetDateTime at) {
        groupRequest(a,r,at,"{}");
    }
    public void groupRequest(AppUserPrincipal a,GroupRequest r,OffsetDateTime at,String documents) {
        jdbc.update("insert into loan_group_request(id,loan_id,sacco_id,station_id,round,rule_id,terms_hash,previous_status,maker_id,recorded_at,document_policy) values(?,?,?,?,?,?,?,?,?,?,coalesce(?::jsonb,'{}'::jsonb))",
            r.id(),r.loan(),a.getSaccoId(),a.getStationId(),r.round(),r.rule(),r.termsHash(),r.previousStatus(),r.maker(),at,documents);
    }
    public Optional<String> reviewDocumentPolicy(AppUserPrincipal a,UUID loan){
        return jdbc.query("select document_policy::text from loan_group_request where loan_id=? and sacco_id=? and station_id=? order by round desc limit 1",(r,n)->r.getString(1),loan,a.getSaccoId(),a.getStationId()).stream().findFirst();
    }
    public List<com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.Requirement> reviewDocumentDefinitions(AppUserPrincipal a,UUID product){
        return jdbc.query("select d.id,d.attachment_name,coalesce(d.max_size_mb,5.00) from loan_product_required_attachments d join loan_product_settings p on p.id=d.loan_product_setting_id where p.id=? and p.sacco_id=? and d.active=true order by d.display_order,d.created_at limit 11",(r,n)->new com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.Requirement(r.getObject(1,UUID.class),r.getString(2),r.getBigDecimal(3)),product,a.getSaccoId());
    }
    public void groupDecision(GroupRequest r,GroupDecision d) {
        jdbc.update("insert into loan_group_decision(id,request_id,step,actor_id,decision,reason,evidence,recorded_at) values(?,?,?,?,?,?,?,?)",
            d.id(),r.id(),d.step(),d.actor(),d.decision(),d.reason(),d.evidence(),d.at());
    }
    public void groupStatus(AppUserPrincipal a,UUID loan,String status,OffsetDateTime at) {
        jdbc.update("update loan_applications set status=?,updated_at=?,version=version+1 where id=? and sacco_id=? and station_id=?",status,at,loan,a.getSaccoId(),a.getStationId());
    }
    public boolean signedGroupApplication(AppUserPrincipal a,UUID loan){return Boolean.TRUE.equals(jdbc.queryForObject("""
        select exists(select 1 from lending_group_org_agreement p join lending_group_authority h on h.id=p.authority_id
          where p.loan_id=? and p.sacco_id=? and p.station_id=? and (select count(*) from lending_group_org_signature s where s.cycle_id=p.cycle_id)>=h.required_signatures)
        or exists(select 1 from lending_group_cycle c where c.sacco_id=? and c.station_id=?
          and (exists(select 1 from lending_group_participant x where x.cycle_id=c.id and x.loan_id=?)
            or exists(select 1 from lending_group_shared_facility x where x.cycle_id=c.id and x.loan_id=?))
          and exists(select 1 from lending_group_participant x where x.cycle_id=c.id)
          and not exists(select 1 from lending_group_participant x where x.cycle_id=c.id and not exists(select 1 from lending_group_consent_evidence e where e.cycle_id=c.id and e.client_id=x.client_id)))
        """,Boolean.class,loan,a.getSaccoId(),a.getStationId(),a.getSaccoId(),a.getStationId(),loan,loan));}
    public boolean personalGuaranteesReady(AppUserPrincipal a,UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("select coalesce(lending_personal_guarantees_ready(id),false) from loan_applications where id=? and sacco_id=? and station_id=?",Boolean.class,id,a.getSaccoId(),a.getStationId()));}
    public boolean recordedGuarantees(AppUserPrincipal a,UUID id,int required){return Boolean.TRUE.equals(jdbc.queryForObject("select count(distinct g.guarantor_member_id)>=? from guarantor_requests g join loan_applications l on l.id=g.loan_application_id where l.id=? and l.sacco_id=? and l.station_id=? and g.status='APPROVED'",Boolean.class,required,id,a.getSaccoId(),a.getStationId()));}

    public String clientKycError(AppUserPrincipal actor,UUID client,LocalDate day) {
        return jdbc.queryForObject("""
            select case when (select kyc_required from lending_credit_policy where sacco_id=? and station_id=?
                order by revision desc limit 1) then client_kyc_error(?,?,?,?) end
            """,String.class,actor.getSaccoId(),actor.getStationId(),client,actor.getSaccoId(),actor.getStationId(),day);
    }
    private final JdbcTemplate jdbc;

    public List<com.sacco.mvp.staffoffice.dto.OfficeWork.Task> officeReviews(AppUserPrincipal a,com.sacco.mvp.domain.Position group,String search,String sort,int page) {
        String order=switch(sort) {case "oldest"->"l.created_at,l.id";case "number"->"coalesce(l.loan_id,l.application_number::text),l.id";case "number_desc"->"coalesce(l.loan_id,l.application_number::text) desc,l.id desc";default->"l.created_at desc,l.id desc";};
        return jdbc.query("""
            select l.id,coalesce(l.loan_id,l.application_number::text) number,b.full_name,l.amount
            from loan_applications l
            join loan_borrower_directory b on b.party_id=l.borrower_party_id and b.sacco_id=l.sacco_id
            left join accountant_loan_record recorded on recorded.loan_id=l.id
            join lateral(select * from loan_group_request r where r.loan_id=l.id and r.sacco_id=l.sacco_id
                and r.station_id=l.station_id order by r.round desc limit 1) request on true
            join loan_group_rule rule on rule.id=request.rule_id and rule.sacco_id=l.sacco_id and rule.station_id=l.station_id
            join lateral(select step.value from jsonb_array_elements(rule.steps) with ordinality step(value,n)
                where (select count(*) from loan_group_decision d where d.request_id=request.id and d.step=step.n-1
                    and d.decision='APPROVE') < (step.value->>'approvals')::int
                order by step.n limit 1) current_step on true
            where l.sacco_id=? and l.station_id=? and l.status='SUBMITTED'
                and request.terms_hash=loan_group_terms(l) and not retired_group_application(l.id)
                and current_step.value->>'group'=?
                and l.applicant_member_id is distinct from ?::uuid and recorded.actor_id is distinct from ?::uuid
                and request.maker_id<>?::uuid and not loan_group_related(l.id,?::uuid)
                and not exists(select 1 from loan_group_decision d where d.request_id=request.id
                    and (d.decision='REJECT' or d.actor_id=?::uuid))
                and (strpos(lower(coalesce(l.loan_id,l.application_number::text)),?)>0 or strpos(lower(b.full_name),?)>0)
            """+" order by "+order+" limit 26 offset ?",
            (r,n)->new com.sacco.mvp.staffoffice.dto.OfficeWork.Task(r.getString("number"),r.getString("full_name"),
                "office.readyReview",r.getBigDecimal("amount"),"/finance/loan-records/"+r.getObject("id",UUID.class)),
            a.getSaccoId(),a.getStationId(),group.name(),a.getMemberId(),a.getMemberId(),a.getMemberId(),a.getMemberId(),a.getMemberId(),
            search.toLowerCase(Locale.ROOT),search.toLowerCase(Locale.ROOT),page*25);
    }

    private static final String BRANCH_LOANS = """
        select l.id,coalesce(l.loan_id,l.application_number::text) number,b.full_name,
          coalesce(r.product_name,l.loan_type) product,l.status,l.amount,l.disbursement_date
        from loan_applications l
        join loan_borrower_directory b on b.party_id=l.borrower_party_id and b.sacco_id=l.sacco_id
        left join accountant_loan_record r on r.loan_id=l.id and r.sacco_id=l.sacco_id and r.station_id=l.station_id
        where l.sacco_id=? and l.station_id=?
        """;

    private final org.springframework.jdbc.core.RowMapper<BranchLoan> branchLoanMapper=(row,n) -> new BranchLoan(
        row.getObject("id",UUID.class),row.getString("number"),row.getString("full_name"),row.getString("product"),
        row.getString("status"),row.getBigDecimal("amount"),row.getObject("disbursement_date",LocalDate.class));

    public List<BranchLoan> branchLoans(String institution,String branch,String search,String sort,int page) {
        String order=switch(sort) {
            case "number_desc" -> "number desc";
            case "name_asc" -> "b.full_name";
            case "name_desc" -> "b.full_name desc";
            case "status_asc" -> "l.status";
            case "status_desc" -> "l.status desc";
            case "amount_asc" -> "l.amount";
            case "amount_desc" -> "l.amount desc";
            case "number_asc" -> "number";
            default -> throw new IllegalArgumentException("branches.error.filter");
        };
        return jdbc.query(BRANCH_LOANS + """
            and (strpos(lower(coalesce(l.loan_id,l.application_number::text)),?)>0 or strpos(lower(b.full_name),?)>0)
            """ + " order by " + order + ", l.id limit 26 offset ?",branchLoanMapper,institution,branch,search,search,page*25);
    }

    public Optional<BranchLoan> branchLoan(String institution,String branch,UUID id) {
        return jdbc.query(BRANCH_LOANS+" and l.id=?",branchLoanMapper,institution,branch,id).stream().findFirst();
    }

    public List<ReviewedLoan> reviewedLoans(AppUserPrincipal actor, String search, String sort, int page) {
        String order = switch(sort) {
            case "oldest" -> "l.disbursement_date, l.id";
            case "number" -> "number, l.id";
            case "number_desc" -> "number desc, l.id";
            case "client" -> "b.full_name, l.id";
            case "client_desc" -> "b.full_name desc, l.id";
            case "status" -> "l.status, l.id";
            case "status_desc" -> "l.status desc, l.id";
            default -> "l.disbursement_date desc, l.id desc";
        };
        return jdbc.query("""
            select l.id, coalesce(l.loan_id,l.application_number::text) number, b.full_name, l.status, l.disbursement_date
            """ + REVIEW_ARCHIVE + " order by " + order + " limit 26 offset ?",
            (r,n) -> new ReviewedLoan(r.getObject("id",UUID.class),r.getString("number"),r.getString("full_name"),
                r.getString("status"),r.getObject("disbursement_date",java.time.LocalDate.class)),
            actor.getMemberId(),actor.getSaccoId(),actor.getStationId(), search,search,page*25);
    }

    public long reviewedLoanCount(AppUserPrincipal actor, String search) {
        return jdbc.queryForObject("select count(*) " + REVIEW_ARCHIVE,Long.class,
            actor.getMemberId(),actor.getSaccoId(),actor.getStationId(),search,search);
    }

    private static final String REVIEW_ARCHIVE = """
        from (select distinct r.loan_id,r.sacco_id,r.station_id
            from loan_group_decision d join loan_group_request r on r.id=d.request_id
            where d.actor_id=? and r.sacco_id=? and r.station_id=?) reviewed
        join loan_applications l on l.id=reviewed.loan_id and l.sacco_id=reviewed.sacco_id and l.station_id=reviewed.station_id
        join loan_borrower_directory b on b.party_id=l.borrower_party_id and b.sacco_id=l.sacco_id
        where l.disbursement_date is not null and l.status in ('DISBURSED','PAID','PAR','DEFAULTED','WRITTEN_OFF')
          and exists(select 1 from accountant_loan_post p where p.loan_id=l.id and p.sacco_id=l.sacco_id
            and p.station_id=l.station_id and p.kind='DISBURSEMENT' and p.state='POSTED')
          and (strpos(lower(coalesce(l.loan_id,l.application_number::text)),?)>0 or strpos(lower(b.full_name),?)>0)
        """;

    private static final String LOANS="""
        select (l.loan_application_id is not null) has_ledger,coalesce(c.outstanding,0) charges,(loan_cancellation_error(a.id) is null) cancel_eligible,a.id,coalesce(a.loan_id,a.application_number::text) loan_id,m.party_id client_id,m.full_name,m.member_no,case when m.kind='CLIENT' then 'INDIVIDUAL' else 'GROUP' end loan_for,r.product_name,r.application_date,r.requested_principal,
        a.amount,a.status,(m.kind='CLIENT' and a.top_up_source_loan_id is null and a.status='DISBURSED' and coalesce(l.principal-l.principal_paid,0)>0 and coalesce((select loan_top_up_enabled from sacco_settings where sacco_id=a.sacco_id),true) and not exists(select 1 from loan_applications child where child.top_up_source_loan_id=a.id and child.status not in ('WITHDRAWN','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED','DISBURSEMENT_REVERSED','PAID'))) topup_eligible,case when a.status='DISBURSEMENT_REVERSED' then 0 else coalesce(l.principal-l.principal_paid,0) end outstanding,coalesce(l.principal_paid,0) principal_paid,
        coalesce(l.interest_paid,0) interest_paid,case when a.status='DISBURSEMENT_REVERSED' or exists(select 1 from accountant_loan_topup ts where ts.source_id=a.id and ts.reverses_id is null and not exists(select 1 from accountant_loan_topup tr where tr.reverses_id=ts.id)) then 0 else coalesce(d.interest,0) end due_interest,case when a.status='DISBURSEMENT_REVERSED' or exists(select 1 from accountant_loan_topup ts where ts.source_id=a.id and ts.reverses_id is null and not exists(select 1 from accountant_loan_topup tr where tr.reverses_id=ts.id)) then 0 else coalesce(d.arrears,0) end arrears,case when a.status='DISBURSEMENT_REVERSED' or exists(select 1 from accountant_loan_topup ts where ts.source_id=a.id and ts.reverses_id is null and not exists(select 1 from accountant_loan_topup tr where tr.reverses_id=ts.id)) then 0 else coalesce(d.future,0) end future_interest
        from accountant_loan_record r join loan_applications a on a.id=r.loan_id join loan_borrower_directory m on m.party_id=a.borrower_party_id and m.sacco_id=a.sacco_id
        left join loan_charge_balance c on c.loan_id=a.id and c.sacco_id=r.sacco_id and c.station_id=r.station_id
        left join loan_ledgers l on l.loan_application_id=a.id
        left join lateral (select sum(loan_interest_due(id,?)) interest,
        sum(case when due_date<? then principal-principal_paid+interest-interest_paid-interest_excluded-interest_waived else 0 end) arrears,
        sum(greatest(0,interest-interest_paid-interest_excluded-interest_waived-loan_interest_due(id,?))) future
        from loan_ledger_installments where loan_application_id=a.id) d on true
        """;
    private static final String APPLICATION_LOANS=LOANS.replace("from accountant_loan_record r join loan_applications a", """
        from (select loan_id,sacco_id,station_id,product_name,application_date,requested_principal from accountant_loan_record
          union all
          select a.id,a.sacco_id,a.station_id,coalesce(nullif(p.product_name,''),p.loan_type),
            coalesce(nullif(a.form_data->'recordingDetails'->>'applicationDate','')::date,a.created_at::date),a.amount
          from loan_applications a join members borrower on borrower.id=a.applicant_member_id
          left join loan_product_settings p on p.id=a.loan_product_setting_id and p.sacco_id=a.sacco_id
          where true
            and not exists(select 1 from accountant_loan_record recorded where recorded.loan_id=a.id)
        ) r join loan_applications a
        """);
    public Set<UUID> intakeIds(AppUserPrincipal actor,List<UUID> ids) {
        if(ids.isEmpty())return Set.of();
        if(ids.size()>26)throw new IllegalArgumentException("recording.error.filter");
        var found=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).query("""
            select a.id from loan_applications a where a.id in(:ids) and a.sacco_id=:institution and a.station_id=:branch
            and not exists(select 1 from accountant_loan_record r where r.loan_id=a.id)
            """,Map.of("ids",ids,"institution",actor.getSaccoId(),"branch",actor.getStationId()),(row,n)->row.getObject(1,UUID.class));
        return new HashSet<>(found);
    }
    public boolean intakeRequest(AppUserPrincipal actor,UUID key) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from client_application_command where sacco_id=? and station_id=? and request_key=?)",
            Boolean.class,actor.getSaccoId(),actor.getStationId(),key));
    }
    public boolean directRequest(AppUserPrincipal actor,UUID key) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from accountant_loan_record where sacco_id=? and station_id=? and request_key=?)",
            Boolean.class,actor.getSaccoId(),actor.getStationId(),key));
    }
    private static final String POST_REFERENCE = """
        case when %s is null then p.reference
             else coalesce(batch.title||' / '||item.line_no||' / '||receipt_row.line_no,receipt.number,a.loan_id,'') end
        """.formatted(POST_ITEM_ID);
    private static final String POST_FROM="""
        from accountant_loan_movement p join loan_applications a on a.id=p.loan_id
        join accountant_loan_record r on r.loan_id=p.loan_id and r.sacco_id=p.sacco_id and r.station_id=p.station_id
        join loan_borrower_directory m on m.party_id=a.borrower_party_id and m.sacco_id=a.sacco_id join members u on u.id=p.actor_id
        left join accountant_loan_receipt_row receipt_row on receipt_row.posting_id=p.id
            and receipt_row.sacco_id=p.sacco_id and receipt_row.station_id=p.station_id and %s is not null
        left join accountant_loan_receipt receipt on receipt.id=receipt_row.receipt_id
            and receipt.sacco_id=p.sacco_id and receipt.station_id=p.station_id
        left join receipt_batch_item item on item.id=%s and item.receipt_id=coalesce(receipt.reverses_id,receipt.id)
            and item.sacco_id=p.sacco_id and item.station_id=p.station_id
        left join receipt_batch batch on batch.id=item.batch_id and batch.sacco_id=p.sacco_id and batch.station_id=p.station_id
        """.formatted(POST_ITEM_ID,POST_ITEM_ID);
    private static final String POSTS="""
        select p.*,%s display_reference,coalesce((select cash from accountant_loan_topup where id=p.id),p.amount-case when p.kind in ('DISBURSEMENT','FUNDING_UNDO') then coalesce((select sum(amount) from loan_charge_event where journal_id=p.journal_id and kind in ('DEDUCT','DEDUCT_UNDO')),0) else 0 end) cash_amount,a.loan_id loan_number,m.full_name,m.member_no,case when m.kind='CLIENT' then 'INDIVIDUAL' else 'GROUP' end loan_for,u.full_name actor_name,r.product_name,
        coalesce((select x.id from accountant_loan_movement x where x.reverses_id=p.id),(select x.id from accountant_loan_topup x where x.reverses_id=p.id)) reversed_by
        """.formatted(POST_REFERENCE)+POST_FROM;
    public void lockRequest(AppUserPrincipal a,UUID key){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","LOAN_RECORD/"+a.getSaccoId()+"/"+a.getStationId()+"/"+key);}
    public UUID disbursementCorrection(AppUserPrincipal a,UUID posting){return jdbc.query("select q.id from loan_adjustment_request q join loan_adjustment_decision d on d.request_id=q.id join gl_journal j on j.id=d.journal_id and j.state='POSTED' join accountant_loan_post p on p.journal_id=q.reverses_journal_id where p.id=? and q.sacco_id=? and q.station_id=? and q.kind='DISBURSEMENT_REVERSAL'",(r,n)->r.getObject(1,UUID.class),posting,a.getSaccoId(),a.getStationId()).stream().findFirst().orElse(null);}
    public Optional<UUID> receiptForPosting(AppUserPrincipal a,UUID id){return jdbc.query("select receipt_id from accountant_loan_receipt_row where posting_id=? and sacco_id=? and station_id=? union all select receipt_id from lending_group_receipt_loan where posting_id=? and sacco_id=? and station_id=?",(r,n)->r.getObject(1,UUID.class),id,a.getSaccoId(),a.getStationId(),id,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public List<LoanRow> repaymentLoans(AppUserPrincipal a,UUID client,String search,int page,LocalDate today){
        return jdbc.query(LOANS+" where r.sacco_id=? and r.station_id=? and m.party_id=? and a.status in ('DISBURSED','PAR','DEFAULTED') and l.loan_application_id is not null and strpos(lower(a.loan_id),?)>0 order by a.loan_id,a.id limit 26 offset ?",(org.springframework.jdbc.core.RowMapper<LoanRow>)this::loan,today,today,today,a.getSaccoId(),a.getStationId(),client,search.toLowerCase(Locale.ROOT),page*25);
    }
    public List<UUID> lockReceiptLoans(AppUserPrincipal a,List<UUID> ids,UUID client){
        var args=new ArrayList<Object>();args.add(a.getSaccoId());args.add(a.getStationId());args.add(client);args.add(client);args.addAll(ids);
        return jdbc.query("select r.loan_id from accountant_loan_record r join loan_applications l on l.id=r.loan_id where r.sacco_id=? and r.station_id=? and (?::uuid is null or l.borrower_party_id=?::uuid) and r.loan_id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") order by r.loan_id for update of r",(r,n)->r.getObject(1,UUID.class),args.toArray());
    }
    public List<ReceiptEntryLoan> receiptEntryLoans(AppUserPrincipal actor, String search, List<String> numbers) {
        var parameters = new ArrayList<Object>();
        parameters.add(actor.getSaccoId());
        parameters.add(actor.getStationId());
        String filter = "true";
        if (!numbers.isEmpty()) {
            filter = "loan_id in (" + String.join(",", Collections.nCopies(numbers.size(), "?")) + ")";
            parameters.addAll(numbers);
        } else if (!search.isEmpty()) {
            filter = """
                party_id in (
                    select party_id from scoped_loans
                    where strpos(lower(concat_ws(' ',loan_id,full_name,member_no)), ?) > 0
                )
                """;
            parameters.add(search.toLowerCase(Locale.ROOT));
        }
        return jdbc.query("""
            with scoped_loans as (
                select l.id, m.party_id, l.loan_id, m.full_name, m.member_no, r.product_name, b.disbursement_date
                from accountant_loan_record r
                join loan_applications l on l.id=r.loan_id and l.sacco_id=r.sacco_id and l.station_id=r.station_id
                join loan_borrower_directory m on m.party_id=l.borrower_party_id and m.sacco_id=l.sacco_id and m.kind='CLIENT'
                join loan_ledgers b on b.loan_application_id=l.id and b.sacco_id=r.sacco_id and b.station_id=r.station_id
                where r.sacco_id=? and r.station_id=? and l.status in ('DISBURSED','PAR','DEFAULTED')
                  and b.disbursement_date is not null and l.loan_id is not null
            )
            select eligible.*, i.due_date, i.amount
            from (
                select * from scoped_loans where
            """ + filter + """
                order by loan_id,id limit 1001
            ) eligible
            left join lateral (
                select due_date, principal-principal_paid+interest-interest_paid-interest_excluded-interest_waived amount
                from loan_ledger_installments
                where loan_application_id=eligible.id
                  and (principal>principal_paid or interest>interest_paid+interest_excluded+interest_waived)
                order by due_date,installment_number limit 1
            ) i on true
            order by eligible.loan_id,eligible.id
            """, (row, index) -> new ReceiptEntryLoan(row.getObject("id", UUID.class),
                row.getObject("party_id", UUID.class), row.getString("loan_id"), row.getString("full_name"),
                row.getString("member_no"), row.getString("product_name"), row.getObject("disbursement_date", LocalDate.class),
                row.getObject("due_date", LocalDate.class), row.getBigDecimal("amount")), parameters.toArray());
    }

    public List<ReceiptLoan> receiptLoans(AppUserPrincipal a,List<UUID> ids,LocalDate date){
        var args=new ArrayList<Object>();args.add(date);args.add(date);args.add(a.getSaccoId());args.add(a.getStationId());args.addAll(ids);
        return jdbc.query("""
            select l.id,l.borrower_party_id applicant_member_id,l.loan_id,m.full_name,m.member_no,l.status,b.disbursement_date,greatest(b.last_payment_date,c.last_date,(select max(effective_date) from loan_interest_waiver where loan_id=l.id)) last_payment_date,
            b.principal-b.principal_paid outstanding,coalesce(d.principal,0) due_principal,coalesce(d.interest,0) due_interest,coalesce(c.outstanding,0) charges,
            r.product_name,ab.principal_account_id,ab.interest_account_id,pa.code||' - '||pa.name principal_account,ia.code||' - '||ia.name interest_account
            from accountant_loan_record r join loan_applications l on l.id=r.loan_id join loan_borrower_directory m on m.party_id=l.borrower_party_id and m.sacco_id=l.sacco_id
            left join loan_ledgers b on b.loan_application_id=l.id
            left join loan_charge_balance c on c.loan_id=l.id
            left join loan_product_account_binding ab on ab.loan_id=l.id and ab.sacco_id=r.sacco_id and ab.station_id=r.station_id
            left join gl_account pa on pa.id=ab.principal_account_id and pa.sacco_id=r.sacco_id and pa.active and pa.kind in ('POSTING','CONTROL')
            left join gl_account ia on ia.id=ab.interest_account_id and ia.sacco_id=r.sacco_id and ia.active and ia.kind in ('POSTING','CONTROL')
            left join lateral(select sum(case when due_date<=? then principal-principal_paid else 0 end) principal,sum(loan_interest_due(id,?)) interest
              from loan_ledger_installments where loan_application_id=l.id) d on true
            where r.sacco_id=? and r.station_id=? and l.id in (
            """+String.join(",",Collections.nCopies(ids.size(),"?"))+") order by l.id",(r,n)->new ReceiptLoan(r.getObject("id",UUID.class),r.getObject("applicant_member_id",UUID.class),r.getString("loan_id"),r.getString("full_name"),r.getString("member_no"),r.getBigDecimal("due_principal"),r.getBigDecimal("due_interest"),r.getBigDecimal("outstanding"),r.getObject("disbursement_date",LocalDate.class),r.getObject("last_payment_date",LocalDate.class),r.getString("status"),r.getBigDecimal("charges"),r.getString("product_name"),r.getObject("principal_account_id",UUID.class),r.getString("principal_account"),r.getObject("interest_account_id",UUID.class),r.getString("interest_account")),args.toArray());
    }
    public Optional<UUID> recordRequest(AppUserPrincipal a,UUID key,String hash){
        var rows=jdbc.queryForList("select loan_id,payload_hash,actor_id from accountant_loan_record where sacco_id=? and station_id=? and request_key=?",a.getSaccoId(),a.getStationId(),key);
        if(rows.isEmpty())return Optional.empty();var row=rows.getFirst();
        if(!hash.equals(row.get("payload_hash")) || !a.getMemberId().equals(row.get("actor_id")))throw new IllegalArgumentException("recording.error.retry");
        return Optional.of((UUID)row.get("loan_id"));
    }
    public void insertRecord(AppUserPrincipal a,UUID id,RecordForm f,String hash,String product,String metadata,OffsetDateTime now){
        jdbc.update("""
            insert into accountant_loan_record(loan_id,sacco_id,station_id,request_key,payload_hash,actor_id,recorded_at,application_date,
            requested_principal,first_payment_date,product_name,metadata) values(?,?,?,?,?,?,?,?,?,?,?,?::jsonb)
            """,id,a.getSaccoId(),a.getStationId(),f.getRequestKey(),hash,a.getMemberId(),now,f.getApplicationDate(),f.getRequestedPrincipal(),f.getFirstPaymentDate(),product,metadata);
    }
    public Map<String,Object> record(AppUserPrincipal a,UUID id,boolean lock){
        var rows=jdbc.queryForList("""
            select r.*,i.sacco_name,coalesce(nullif(s.address_location,''),r.station_id) branch_name,m.full_name actor_name
            from accountant_loan_record r join registered_saccos i on i.sacco_id=r.sacco_id
            join sacco_stations s on s.sacco_id=r.sacco_id and s.station_id=r.station_id join members m on m.id=r.actor_id
            where r.loan_id=? and r.sacco_id=? and r.station_id=?
            """+(lock?" for update of r":""),id,a.getSaccoId(),a.getStationId());
        if(rows.isEmpty())throw new org.springframework.security.access.AccessDeniedException("Loan unavailable");return rows.getFirst();
    }
    public LoanRow loan(AppUserPrincipal a,UUID id,LocalDate today){return jdbc.query(LOANS+" where a.id=? and r.sacco_id=? and r.station_id=?",(org.springframework.jdbc.core.RowMapper<LoanRow>) this::loan,today,today,today,id,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Loan unavailable"));}
    private String loanWhere(){return """
        where r.sacco_id=? and r.station_id=? and (?='' or strpos(lower(coalesce(a.loan_id,a.application_number::text)||' '||m.full_name||' '||m.member_no),?)>0)
        and (?::date is null or r.application_date>=?::date) and (?::date is null or r.application_date<=?::date)
        and (?='' or a.status=?) and (?::uuid is null or m.party_id=?::uuid)
        and (?::uuid is null or a.loan_product_setting_id=?::uuid)
        """;}
    private Object[] loanArgs(AppUserPrincipal a,Filter f,LocalDate today){return new Object[]{today,today,today,a.getSaccoId(),a.getStationId(),f.search().toLowerCase(Locale.ROOT),f.search().toLowerCase(Locale.ROOT),f.from(),f.from(),f.through(),f.through(),f.status(),f.status(),f.clientId(),f.clientId(),f.productId(),f.productId()};}
    public List<LoanRow> loans(AppUserPrincipal a,Filter f,LocalDate today,int limit,int offset){
        return queryLoans(a,f,today,limit,offset,LOANS,false);
    }
    public List<LoanRow> registerLoans(AppUserPrincipal a,Filter f,LocalDate today,int limit,int offset) {
        return queryLoans(a,f,today,limit,offset,a.getClaims().contains("CLIENT_ONBOARDING_VIEW")?APPLICATION_LOANS:LOANS,true);
    }
    private List<LoanRow> queryLoans(AppUserPrincipal a,Filter f,LocalDate today,int limit,int offset,String source,boolean working) {
        var args=new ArrayList<>(Arrays.asList(loanArgs(a,f,today)));args.add(limit);args.add(offset);
        String order=RegisterSort.order(f.sort(),Map.of("number","coalesce(a.loan_id,a.application_number::text)","product","r.product_name","client","m.full_name","loanFor","loan_for","date","r.application_date","amount","a.amount","balance","coalesce(l.principal-l.principal_paid,0)","status","a.status"),"a.id");
        order=com.sacco.mvp.support.ResourceTableSort.order("client-statement-loans",Map.of("loan","coalesce(a.loan_id,a.application_number::text)","product","r.product_name","status","a.status","principal","outstanding","interest","due_interest","charges","charges","arrears","arrears"),"a.id",order);
        String lifecycle=working && f.status().isBlank()?" and a.status not in ('WITHDRAWN','DISBURSEMENT_REVERSED')":"";
        return jdbc.query(source+loanWhere()+lifecycle+" order by "+order+" limit ? offset ?",(org.springframework.jdbc.core.RowMapper<LoanRow>) this::loan,args.toArray());
    }
    public List<Choice> clients(AppUserPrincipal a,String search,int page){return jdbc.query("""
        select id,member_no||' - '||full_name label,case when client_portal_enabled then 'BORROWER' else 'PROVISIONAL' end client_kind from members where sacco_id=? and station_id=? and status='ACTIVE' and is_member
        and strpos(lower(member_no||' '||full_name),?)>0 order by member_no,id limit 26 offset ?
        """,(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),r.getString("client_kind")),a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);}
    public Map<UUID,String> evidenceClients(AppUserPrincipal actor, Collection<UUID> ids) {
        if(ids.isEmpty())return Map.of();
        if(ids.size()>20)throw new IllegalArgumentException("applications.error.guarantors");
        var found=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).query("""
            select id,member_no||' - '||full_name from members where id in(:ids) and sacco_id=:institution
            and station_id=:branch and status='ACTIVE' and is_member=true
            """,Map.of("ids",ids,"institution",actor.getSaccoId(),"branch",actor.getStationId()),
            (r,n)->Map.entry(r.getObject(1,UUID.class),r.getString(2)));
        var labels=new LinkedHashMap<UUID,String>();found.forEach(e->labels.put(e.getKey(),e.getValue()));return labels;
    }
    public Optional<Choice> client(AppUserPrincipal a,UUID id){return jdbc.query("select id,member_no||' - '||full_name label,case when client_portal_enabled then 'BORROWER' else 'PROVISIONAL' end client_kind from members where id=? and sacco_id=? and station_id=? and status='ACTIVE' and is_member",(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),r.getString("client_kind")),id,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    // clientId is the retained UI parameter; an organization selection carries its typed borrower party ID.
    private static final String LEDGER_BORROWERS="""
        select m.id,m.member_no,m.full_name,'CLIENT' kind from members m where m.sacco_id=?
          and ((m.station_id=? and m.is_member) or exists(select 1 from accountant_loan_record r
            join loan_applications l on l.id=r.loan_id where l.applicant_member_id=m.id and r.sacco_id=? and r.station_id=?))
        union all
        select b.party_id,b.member_no,b.full_name,b.kind from loan_borrower_directory b where b.sacco_id=? and b.group_station_id=? and b.kind in ('ORGANIZATION','JOINT_GROUP')
          and exists(select 1 from accountant_loan_record r join loan_applications l on l.id=r.loan_id
            where l.borrower_party_id=b.party_id and r.sacco_id=b.sacco_id and r.station_id=b.group_station_id)
        """;
    public List<Choice> ledgerClients(AppUserPrincipal a,String search,int page){return jdbc.query("select m.id,m.member_no||' - '||m.full_name label,m.kind from ("+LEDGER_BORROWERS+") m where strpos(lower(m.member_no||' '||m.full_name),?)>0 order by "+com.sacco.mvp.support.ResourceTableSort.order("accounting-loans-clients-1",Map.of("client","m.member_no||' - '||m.full_name"),"m.id","m.member_no,m.id")+" limit 26 offset ?",(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),r.getString("kind")),a.getSaccoId(),a.getStationId(),a.getSaccoId(),a.getStationId(),a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);}
    public Optional<Choice> ledgerClient(AppUserPrincipal a, UUID id) {
        return statementClient(a, id).map(client -> new Choice(id, client.number()+" - "+client.name(), client.kind()));
    }

    public Optional<StatementClient> statementClient(AppUserPrincipal a, UUID id) {
        return jdbc.query("select m.member_no,m.full_name,m.kind from ("+LEDGER_BORROWERS+") m where m.id=?",
            (row,n) -> new StatementClient(row.getString(1),row.getString(2),row.getString(3)),
            a.getSaccoId(),a.getStationId(),a.getSaccoId(),a.getStationId(),a.getSaccoId(),a.getStationId(),id).stream().findFirst();
    }

    public List<StatementBalance> statementBalances(AppUserPrincipal a, UUID client, LocalDate today, int limit) {
        return jdbc.query(LOANS+"""
            where a.sacco_id=? and a.station_id=? and r.sacco_id=a.sacco_id and r.station_id=a.station_id
              and a.borrower_party_id=?
            order by r.application_date,a.id limit ?
            """, (row,n) -> new StatementBalance(loan(row,n),row.getBoolean("has_ledger")),
            today,today,today,a.getSaccoId(),a.getStationId(),client,limit);
    }

    public List<Choice> statementMovementChoices(AppUserPrincipal a, UUID client, boolean products, String search, int limit, int offset, UUID selected) {
        String source = products
            ? "select a.loan_product_setting_id id,min(r.product_name) label"
            : "select a.id,coalesce(a.loan_id,a.application_number::text) label";
        String group = products ? "group by a.loan_product_setting_id" : "";
        return jdbc.query("""
            select choices.id,choices.label from (
            """+source+"""
              from loan_applications a join accountant_loan_record r on r.loan_id=a.id
                and r.sacco_id=a.sacco_id and r.station_id=a.station_id
              where a.sacco_id=? and a.station_id=? and a.borrower_party_id=?
            """+group+"""
            ) choices
            where choices.id is not null and (?::uuid is null or choices.id=?)
              and strpos(lower(choices.label),?)>0
            order by choices.label,choices.id limit ? offset ?
            """, (row,n) -> new Choice(row.getObject(1,UUID.class),row.getString(2),""),
            a.getSaccoId(),a.getStationId(),client,selected,selected,search,limit,offset);
    }
    public List<StatementTerms> statementTerms(AppUserPrincipal a,UUID client,UUID loan){return jdbc.query("""
        select coalesce((a.financial_snapshot->'recordedCharges')::text,'') charge_snapshot,a.id,a.tenor_months,coalesce(a.first_repayment_date,r.first_payment_date) first_payment_date,a.final_due_date,
        coalesce(a.financial_snapshot->>'tenureUnit','MONTHS') tenure_unit,
        coalesce((a.financial_snapshot->>'tenureValue')::integer,a.tenor_months) tenure_value,
        case when a.financial_snapshot->>'loanMode'='CUSTOM' then case when a.financial_snapshot->>'interestEntry'='AMOUNT' then 'AMOUNT' else coalesce(a.financial_snapshot->>'interestBasis','WHOLE_LOAN') end else 'ANNUAL' end rate_basis,
        (a.financial_snapshot::jsonb->>'interestRate')::numeric rate,
        case when a.financial_snapshot->>'loanMode'='CUSTOM' and a.financial_snapshot->>'interestEntry'='AMOUNT'
            then (a.financial_snapshot->>'interestAmount')::numeric end fixed_interest,
        a.financial_snapshot::jsonb->>'repaymentFrequency' frequency,
        (a.financial_snapshot::jsonb->>'numberOfPayments')::integer installments,
        (a.financial_snapshot::jsonb->>'periodicRepaymentAmount')::numeric installment,
        i.sacco_name,coalesce(nullif(s.address_location,''),r.station_id) branch_name
        from accountant_loan_record r join loan_applications a on a.id=r.loan_id
        join registered_saccos i on i.sacco_id=r.sacco_id
        join sacco_stations s on s.sacco_id=r.sacco_id and s.station_id=r.station_id
        where a.borrower_party_id=? and r.sacco_id=? and r.station_id=? and (?::uuid is null or a.id=?)
        order by r.application_date,a.id limit 501
        """,(r,n)->new StatementTerms(r.getObject("id",UUID.class),r.getBigDecimal("rate"),r.getString("frequency"),
            r.getInt("tenor_months"),r.getInt("installments"),r.getObject("first_payment_date",LocalDate.class),
            r.getBigDecimal("installment"),r.getString("sacco_name"),r.getString("branch_name"),r.getString("rate_basis"),r.getString("tenure_unit"),r.getInt("tenure_value"),r.getObject("final_due_date",LocalDate.class),r.getString("charge_snapshot"),r.getBigDecimal("fixed_interest")),client,a.getSaccoId(),a.getStationId(),loan,loan);}
    public List<Choice> products(AppUserPrincipal a,String search,int page){return jdbc.query("""
        select id,coalesce(product_name,loan_type) label,coalesce(tenure_unit,'MONTHS') tenure_unit,minimum_amount,maximum_amount,
        coalesce(min_tenure,case when min_repayment_months>0 then min_repayment_months else 1 end) minimum_tenure,
        coalesce(max_tenure,max_repayment_months,600) maximum_tenure from loan_product_settings where sacco_id=? and active and not custom_recording_configuration
        and coalesce(product_status,'ACTIVE')='ACTIVE' and strpos(lower(coalesce(product_name,loan_type)),?)>0
        order by coalesce(display_order,2147483647),id limit 26 offset ?
        """,(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),"",r.getString("tenure_unit"),r.getString("minimum_amount"),r.getString("maximum_amount"),r.getInt("minimum_tenure"),r.getInt("maximum_tenure")),a.getSaccoId(),search.toLowerCase(Locale.ROOT),page*25);}
    public List<Choice> filterProducts(AppUserPrincipal a,String search,int page){return jdbc.query("""
        select id,coalesce(product_name,loan_type) label from loan_product_settings where sacco_id=?
        and strpos(lower(coalesce(product_name,loan_type)),?)>0
        order by coalesce(display_order,2147483647),id limit 26 offset ?
        """,(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),""),a.getSaccoId(),search.toLowerCase(Locale.ROOT),page*25);}
    public Optional<Choice> filterProduct(AppUserPrincipal a,UUID id){return jdbc.query("""
        select id,coalesce(product_name,loan_type) label,coalesce(tenure_unit,'MONTHS') tenure_unit,minimum_amount,maximum_amount,
        coalesce(min_tenure,case when min_repayment_months>0 then min_repayment_months else 1 end) minimum_tenure,
        coalesce(max_tenure,max_repayment_months,600) maximum_tenure from loan_product_settings where sacco_id=? and id=?
        """,(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),"",r.getString("tenure_unit"),r.getString("minimum_amount"),r.getString("maximum_amount"),r.getInt("minimum_tenure"),r.getInt("maximum_tenure")),a.getSaccoId(),id).stream().findFirst();}
    public List<Choice> accounts(AppUserPrincipal a,String purpose,String search,int page){return jdbc.query("""
        select id,code||' - '||name label,purpose from gl_account where sacco_id=? and active and
        ((? in ('FUNDING_MONEY','MONEY') and gl_loan_disbursement_account(id,sacco_id))
        or (?='FEE_MONEY' and type='ASSET' and kind='POSTING' and purpose in ('CASH','BANK'))
        or (?='PRINCIPAL' and kind in ('POSTING','CONTROL'))
        or (?='INTEREST' and kind in ('POSTING','CONTROL')))
        and strpos(lower(code||' '||name),?)>0 order by code,id limit 26 offset ?
        """,(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),r.getString("purpose")),a.getSaccoId(),purpose,purpose,purpose,purpose,search.toLowerCase(Locale.ROOT),page*25);}
    public Choice account(AppUserPrincipal a,UUID id,String category){
        return account(a,id,category,true);
    }
    public Choice account(AppUserPrincipal a,UUID id,String category,boolean lock){
        var rows=jdbc.queryForList("select code,name,purpose,kind,case when ? in ('FUNDING_MONEY','MONEY') then gl_loan_disbursement_account(id,sacco_id) else false end money_eligible from gl_account where sacco_id=? and id=? and active"+(lock?" for share":""),category,a.getSaccoId(),id);
        if(rows.isEmpty())throw new IllegalArgumentException("recording.error.account");var r=rows.getFirst();String purpose=(String)r.get("purpose");
        boolean valid=switch(category){case "FUNDING_MONEY","MONEY"->Boolean.TRUE.equals(r.get("money_eligible"));
            case "PRINCIPAL","INTEREST"->Set.of("POSTING","CONTROL").contains(r.get("kind"));
            default->false;};
        if(!valid)throw new IllegalArgumentException("recording.error.account");return new Choice(id,r.get("code")+" - "+r.get("name"),purpose);
    }
    public Optional<Posting> postRequest(AppUserPrincipal a,UUID key,String hash){
        var row=jdbc.queryForList("select id,payload_hash,actor_id from accountant_loan_post where sacco_id=? and station_id=? and request_key=?",a.getSaccoId(),a.getStationId(),key);
        if(row.isEmpty())return Optional.empty();var r=row.getFirst();if(!hash.equals(r.get("payload_hash")) || !a.getMemberId().equals(r.get("actor_id")))throw new IllegalArgumentException("recording.error.retry");
        return Optional.of(posting(a,(UUID)r.get("id")));
    }
    public void ticket(AppUserPrincipal a,UUID id,String kind,PostForm f,String hash,UUID journal,UUID reverses,Choice money,Choice principal,Choice interest,OffsetDateTime now){
        jdbc.update("""
            insert into accountant_loan_post(id,loan_id,sacco_id,station_id,kind,state,request_key,payload_hash,effective_date,reference,amount,
            money_account_id,principal_account_id,interest_account_id,money_account_name,principal_account_name,interest_account_name,evidence,notes,actor_id,recorded_at,journal_id,reverses_id)
            values(?,?,?,?,?,'POSTING',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,f.getLoanId(),a.getSaccoId(),a.getStationId(),kind,f.getRequestKey(),hash,f.getEffectiveDate(),f.getReference().strip(),f.getAmount(),
            money.id(),principal.id(),interest==null?null:interest.id(),money.label(),principal.label(),interest==null?"":interest.label(),f.getEvidence().strip(),f.getNotes().strip(),a.getMemberId(),now,journal,reverses);
    }
    public void finish(UUID id,UUID transaction,BigDecimal principal,BigDecimal interest,BigDecimal balance){finish(id,transaction,principal,interest,balance,BigDecimal.ZERO);}
    public void finish(UUID id,UUID transaction,BigDecimal principal,BigDecimal interest,BigDecimal balance,BigDecimal charges){jdbc.update("update accountant_loan_post set transaction_id=?,principal_amount=?,interest_amount=?,principal_balance=?,charge_amount=?,state='POSTED' where id=? and state='POSTING'",transaction,principal,interest,balance,charges,id);}
    public Posting posting(AppUserPrincipal a,UUID id){return posting(a,id,"reference");}
    public Posting displayedPosting(AppUserPrincipal a,UUID id){return posting(a,id,"display_reference");}
    private Posting posting(AppUserPrincipal a,UUID id,String referenceColumn){
        return jdbc.query(POSTS+" where p.id=? and p.sacco_id=? and p.station_id=?",
            (r,n)->posting(r,referenceColumn),id,a.getSaccoId(),a.getStationId()).stream().findFirst()
            .orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Posting unavailable"));
    }
    public Optional<Posting> loanOpening(AppUserPrincipal a,UUID loan){return jdbc.query(POSTS+" where p.loan_id=? and p.sacco_id=? and p.station_id=? and p.kind='DISBURSEMENT' and p.state='POSTED' order by p.recorded_at,p.id limit 1",(org.springframework.jdbc.core.RowMapper<Posting>) this::posting,loan,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public List<Posting> loanPosts(AppUserPrincipal a,UUID loan,int limit,int offset){return jdbc.query(POSTS+" where p.loan_id=? and p.sacco_id=? and p.station_id=? and p.state='POSTED' order by "+historyOrder()+" limit ? offset ?",(org.springframework.jdbc.core.RowMapper<Posting>) this::posting,loan,a.getSaccoId(),a.getStationId(),limit,offset);}
    public List<StatementMovement> statementPosts(AppUserPrincipal actor, UUID loan, int limit, int offset) {
        var parameters = Map.of("loan",loan,"institution",actor.getSaccoId(),"branch",actor.getStationId(),
            "limit",limit,"offset",offset);
        return new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).query("""
            with scoped_ledger as (
                select loan_application_id,sacco_id,station_id from loan_ledgers
                where loan_application_id=:loan and sacco_id=:institution and station_id=:branch
            ), contract as (
                select sum(i.interest) as opening_interest from scoped_ledger l
                join loan_ledger_installments i on i.loan_application_id=l.loan_application_id
            ), interest_movements as (
                select t.id as transaction_id,t.sequence,
                    (case when t.kind in ('PAYMENT','SETTLEMENT') then 1 else -1 end)
                      *(t.interest_amount+t.future_interest_excluded+coalesce(u.excluded_interest,0)) as reduction
                from scoped_ledger l join loan_repayment_transactions t on t.loan_application_id=l.loan_application_id
                    and t.sacco_id=l.sacco_id and t.station_id=l.station_id
                left join accountant_loan_topup u on u.transaction_id=t.id and u.source_id=t.loan_application_id
                    and u.sacco_id=t.sacco_id and u.station_id=t.station_id
                union all
                select null::uuid,w.sequence,case when w.kind='WAIVER' then w.amount else -w.amount end
                from scoped_ledger l join loan_interest_waiver w on w.loan_id=l.loan_application_id
                    and w.sacco_id=l.sacco_id and w.station_id=l.station_id
            ), running as (
                select transaction_id,sum(reduction) over (order by sequence rows unbounded preceding) as interest_reduced
                from interest_movements
            ), page as (
                select m.*,row_number() over (order by m.recorded_at,
                    case m.kind when 'CHARGE_ASSESS' then 0 when 'DISBURSEMENT' then 1 else 2 end,
                    t.sequence nulls last,m.id) as statement_order
                from (
            """+POSTS+"""
                where p.loan_id=:loan and p.sacco_id=:institution and p.station_id=:branch and p.state='POSTED'
                    and a.sacco_id=p.sacco_id and a.station_id=p.station_id
                ) m left join loan_repayment_transactions t on t.id=m.transaction_id and t.loan_application_id=m.loan_id
                    and t.sacco_id=m.sacco_id and t.station_id=m.station_id
                order by statement_order limit :limit offset :offset
            )
            select p.*, case
                when c.opening_interest is null then null
                when p.kind='DISBURSEMENT' then c.opening_interest
                when p.kind='FUNDING_UNDO' then 0
                when b.interest_reduced between 0 and c.opening_interest then c.opening_interest-b.interest_reduced
            end as outstanding_interest
            from page p cross join contract c left join running b on b.transaction_id=p.transaction_id
            order by p.statement_order
            """,parameters,(row,n)->new StatementMovement(posting(row,n),row.getBigDecimal("outstanding_interest")));
    }
    public List<Posting> clientPosts(AppUserPrincipal a, UUID client, MovementFilter filter, int limit, int offset) {
        return jdbc.query(POSTS+"""
            where a.borrower_party_id=? and p.sacco_id=? and p.station_id=? and p.state='POSTED'
              and a.sacco_id=p.sacco_id and a.station_id=p.station_id
              and (?::date is null or p.effective_date>=?) and (?::date is null or p.effective_date<=?)
              and (?::uuid is null or a.id=?) and (?::uuid is null or a.loan_product_setting_id=?)
            order by """+" "+historyOrder()+" limit ? offset ?", this::posting,
            client,a.getSaccoId(),a.getStationId(),filter.from(),filter.from(),filter.through(),filter.through(),
            filter.loanId(),filter.loanId(),filter.productId(),filter.productId(),limit,offset);
    }
    private String historyOrder() {
        var columns=Map.of("date","p.effective_date","loan","a.loan_id","product","r.product_name","reference","display_reference","kind","p.kind","amount","cash_amount","principal","p.principal_amount","interest","p.interest_amount","balance","p.principal_balance");
        String order=com.sacco.mvp.support.ResourceTableSort.order("accounting-loans-details-1",columns,"p.id","p.recorded_at,p.id");
        return com.sacco.mvp.support.ResourceTableSort.order("client-statement-movements",columns,"p.id",order);
    }
    private String postWhere(){return """
        where p.sacco_id=? and p.station_id=? and (p.kind=? or (?='PAYMENT' and p.kind='REVERSAL') or (?='DISBURSEMENT' and p.kind='FUNDING_UNDO')) and p.state='POSTED'
        and (?='' or strpos(lower(coalesce(a.loan_id,a.application_number::text)||' '||m.full_name||' '||m.member_no||' '||(%s)),?)>0)
        and (?::date is null or p.effective_date>=?::date) and (?::date is null or p.effective_date<=?::date)
        and (?='' or (case when p.reverses_id is not null then 'REVERSAL' when (exists(select 1 from accountant_loan_post x where x.reverses_id=p.id) or exists(select 1 from accountant_loan_topup x where x.reverses_id=p.id)) then 'REVERSED' else 'POSTED' end)=?)
        and (?::uuid is null or a.loan_product_setting_id=?::uuid)
        """.formatted(POST_REFERENCE);}
    private Object[] postArgs(AppUserPrincipal a,String kind,Filter f){return new Object[]{a.getSaccoId(),a.getStationId(),kind,kind,kind,f.search().toLowerCase(Locale.ROOT),f.search().toLowerCase(Locale.ROOT),f.from(),f.from(),f.through(),f.through(),f.status(),f.status(),f.productId(),f.productId()};}
    public List<Posting> posts(AppUserPrincipal a,String kind,Filter f,int limit,int offset){
        var args=new ArrayList<>(Arrays.asList(postArgs(a,kind,f)));args.add(limit);args.add(offset);
        String order=RegisterSort.order(f.sort(),Map.of("number","a.loan_id","product","r.product_name","client","m.full_name","loanFor","loan_for","date","p.effective_date","amount","case when p.kind in ('REVERSAL','FUNDING_UNDO') then -p.amount else coalesce((select cash from accountant_loan_topup where id=p.id),p.amount-case when p.kind in ('DISBURSEMENT','FUNDING_UNDO') then coalesce((select sum(amount) from loan_charge_event where journal_id=p.journal_id and kind in ('DEDUCT','DEDUCT_UNDO')),0) else 0 end) end","reference","display_reference","status","case when p.reverses_id is not null then 'REVERSAL' when (exists(select 1 from accountant_loan_post x where x.reverses_id=p.id) or exists(select 1 from accountant_loan_topup x where x.reverses_id=p.id)) then 'REVERSED' else 'POSTED' end"),"p.id");
        return jdbc.query(POSTS+postWhere()+" order by "+order+" limit ? offset ?",(org.springframework.jdbc.core.RowMapper<Posting>) this::posting,args.toArray());
    }
    public Totals totals(AppUserPrincipal a,String kind,Filter f){return jdbc.query( "select count(*) n,coalesce(sum(case when p.kind in ('REVERSAL','FUNDING_UNDO') then -p.amount else coalesce((select cash from accountant_loan_topup where id=p.id),p.amount-case when p.kind in ('DISBURSEMENT','FUNDING_UNDO') then coalesce((select sum(amount) from loan_charge_event where journal_id=p.journal_id and kind in ('DEDUCT','DEDUCT_UNDO')),0) else 0 end) end),0) amount,coalesce(sum(case when p.kind in ('REVERSAL','FUNDING_UNDO') then -p.principal_amount else p.principal_amount end),0) principal,coalesce(sum(case when p.kind in ('REVERSAL','FUNDING_UNDO') then -p.interest_amount else p.interest_amount end),0) interest "+POST_FROM+postWhere(),(r,n)->new Totals(r.getLong("n"),r.getBigDecimal("amount"),r.getBigDecimal("principal"),r.getBigDecimal("interest")),postArgs(a,kind,f)).getFirst();}
    public Map<String,Object> clientTotals(AppUserPrincipal a,Filter f,LocalDate today){return jdbc.queryForMap("select count(*) loans,coalesce(sum(outstanding),0) principal,coalesce(sum(due_interest),0) interest,coalesce(sum(arrears),0) arrears,coalesce(sum(future_interest),0) future_interest,coalesce(sum(charges),0) charges from ("+LOANS+loanWhere()+") q",loanArgs(a,f,today));}
    public void journal(AppUserPrincipal a,UUID journal,UUID source,PostForm f,String hash,UUID period,String kind,UUID reverses,OffsetDateTime now){
        jdbc.update("""
            insert into gl_journal(id,sacco_id,station_id,policy_version,period_id,source_type,source_reference,request_key,payload_hash,currency,state,evidence_reference,reason,effective_date,maker_id,recorded_at,reverses_id,direct_post)
            values(?,?,?,1,?,?,?,?,?,'TZS','DRAFT',?,?,?,?,?,?,true)
            """,journal,a.getSaccoId(),a.getStationId(),period,kind.startsWith("LOAN_CHARGE_")?kind:"LOAN_RECORD_"+kind,source.toString(),f.getRequestKey(),hash,f.getEvidence().strip(),f.getNotes().strip(),f.getEffectiveDate(),a.getMemberId(),now,reverses);
    }
    public void lines(AppUserPrincipal a,UUID journal,List<Object[]> amounts){var rows=new ArrayList<Object[]>();for(var line:amounts)rows.add(new Object[]{UUID.randomUUID(),journal,a.getSaccoId(),a.getStationId(),line[0],line[1],line[2]});jdbc.batchUpdate("insert into gl_journal_line(id,journal_id,sacco_id,station_id,account_id,debit,credit) values(?,?,?,?,?,?,?)",rows);}
    public void postJournal(AppUserPrincipal a,UUID journal,OffsetDateTime now){jdbc.update("insert into accounting_outbox(id,journal_id,sacco_id,station_id,event_type,created_at) values(?,?,?,?,?,?)",UUID.randomUUID(),journal,a.getSaccoId(),a.getStationId(),"LOAN_RECORD_POSTED",now);jdbc.update("update gl_journal set state='POSTED',posted_at=? where id=?",now,journal);}
    public List<JournalLine> journalLines(AppUserPrincipal a,UUID journal){return jdbc.query("select g.code||' - '||g.name account,z.debit,z.credit from gl_journal_line z join gl_account g on g.id=z.account_id where z.journal_id=? and z.sacco_id=? and z.station_id=? order by z.debit desc,z.id limit 10",(r,n)->new JournalLine(r.getString(1),r.getBigDecimal(2),r.getBigDecimal(3)),journal,a.getSaccoId(),a.getStationId());}
    public List<Object[]> reversedLines(UUID journal){return jdbc.query("select account_id,credit,debit from loan_base_journal_line where journal_id=? order by account_id",(r,n)->new Object[]{r.getObject(1,UUID.class),r.getBigDecimal(2),r.getBigDecimal(3)},journal);}
    public UUID postingInterestAccount(UUID id){return jdbc.queryForObject("select interest_account_id from accountant_loan_post where id=?",UUID.class,id);}
    public UUID principalAccount(UUID loan){return jdbc.queryForObject("select principal_account_id from accountant_loan_post where loan_id=? and kind='DISBURSEMENT'",UUID.class,loan);}
    private LoanRow loan(ResultSet r,int n)throws SQLException{return new LoanRow(r.getObject("id",UUID.class),r.getString("loan_id"),r.getObject("client_id",UUID.class),r.getString("full_name"),r.getString("member_no"),r.getString("product_name"),r.getObject("application_date",LocalDate.class),r.getBigDecimal("requested_principal"),r.getBigDecimal("amount"),r.getString("status"),r.getBigDecimal("outstanding"),r.getBigDecimal("due_interest"),r.getBigDecimal("arrears"),r.getBigDecimal("future_interest"),r.getBigDecimal("principal_paid"),r.getBigDecimal("interest_paid"),r.getBoolean("topup_eligible"),r.getBigDecimal("charges"),r.getBoolean("cancel_eligible"),r.getString("loan_for"));}
    private Posting posting(ResultSet r,int n)throws SQLException{return posting(r,"display_reference");}
    private Posting posting(ResultSet r,String referenceColumn)throws SQLException{return new Posting(r.getObject("id",UUID.class),r.getObject("loan_id",UUID.class),r.getString("loan_number"),r.getString("full_name"),r.getString("member_no"),r.getString("kind"),r.getObject("effective_date",LocalDate.class),r.getString(referenceColumn),r.getBigDecimal("cash_amount"),r.getBigDecimal("principal_amount"),r.getBigDecimal("interest_amount"),r.getString("money_account_name"),r.getString("principal_account_name"),r.getString("interest_account_name"),r.getString("evidence"),r.getString("notes"),r.getObject("actor_id",UUID.class),r.getString("actor_name"),r.getObject("recorded_at",OffsetDateTime.class),r.getObject("journal_id",UUID.class),r.getObject("transaction_id",UUID.class),r.getObject("reverses_id",UUID.class),r.getObject("reversed_by",UUID.class),r.getBigDecimal("principal_balance"),r.getString("product_name"),r.getString("loan_for"));}

    public boolean recorded(AppUserPrincipal actor,UUID loan) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from accountant_loan_record where loan_id=? and sacco_id=? and station_id=?)",Boolean.class,loan,actor.getSaccoId(),actor.getStationId()));
    }
    public boolean availableTeller(AppUserPrincipal actor,List<UUID> ids) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from members where id=any(string_to_array(?,',')::uuid[]) and sacco_id=? and station_id=? and status='ACTIVE' and coalesce(staff_access_status,'ACTIVE')='ACTIVE')",Boolean.class,ids.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(",")),actor.getSaccoId(),actor.getStationId()));
    }

    public TopUpQuote topUpQuote(AppUserPrincipal a,UUID source,LocalDate date) {
        return jdbc.query("""
            select l.loan_application_id,l.loan_id,l.applicant_member_id,m.full_name,l.principal-l.principal_paid principal,l.next_sequence,
              coalesce(sum(loan_interest_due(i.id,?)),0) interest,
              coalesce(sum(greatest(0,i.interest-i.interest_paid-i.interest_excluded-i.interest_waived-loan_interest_due(i.id,?))),0) excluded
            from accountant_loan_record r join loan_applications b on b.id=r.loan_id
            join loan_ledgers l on l.loan_application_id=b.id join members m on m.id=b.applicant_member_id
            join loan_ledger_installments i on i.loan_application_id=b.id
            join accountant_loan_post p on p.loan_id=b.id and p.kind='DISBURSEMENT' and p.state='POSTED'
            join gl_journal j on j.id=p.journal_id and j.state='POSTED'
            join loan_adjustment_balance ab on ab.loan_id=b.id
            where b.id=? and r.sacco_id=? and r.station_id=? and b.sacco_id=r.sacco_id and b.station_id=r.station_id
             and l.sacco_id=r.sacco_id and l.station_id=r.station_id and b.status='DISBURSED'
             and l.disbursement_date<=? and (l.last_payment_date is null or l.last_payment_date<=?)
             and ab.allowance=0 and ab.written_off=0 and (ab.last_date is null or ab.last_date<=?)
             and (coalesce((b.policy_snapshot->>'applicationFee')::numeric,0)=0 or exists(select 1 from loan_charge_contract where loan_id=b.id))
            group by l.loan_application_id,m.full_name
            """,(r,n)->new TopUpQuote(source,r.getString("loan_id"),r.getObject("applicant_member_id",UUID.class),r.getString("full_name"),date,
                r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("excluded"),r.getLong("next_sequence")),
            date,date,source,a.getSaccoId(),a.getStationId(),date,date,date).stream().findFirst().orElseThrow(()->new IllegalArgumentException("recording.error.topUpUnavailable"));
    }
    public boolean topUpEnabled(String institution){return !Boolean.FALSE.equals(jdbc.query("select loan_top_up_enabled from sacco_settings where sacco_id=?",(r,n)->r.getBoolean(1),institution).stream().findFirst().orElse(true));}
    public boolean pendingTopUp(UUID source,UUID excluded){return Boolean.TRUE.equals(jdbc.queryForObject("""
        select exists(select 1 from loan_applications where top_up_source_loan_id=? and (?::uuid is null or id<>?)
        and status not in ('WITHDRAWN','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED','DISBURSEMENT_REVERSED','PAID'))
        """,Boolean.class,source,excluded,excluded));}
    public void lockTopUpPair(AppUserPrincipal a,UUID source,UUID loan){
        var ids=List.of(source,loan).stream().sorted().toList();
        if(lockReceiptLoans(a,ids,null).size()!=2)throw new org.springframework.security.access.AccessDeniedException("Loan unavailable");
        jdbc.queryForList("select loan_application_id from loan_ledgers where loan_application_id in (?,?) order by loan_application_id for update",ids.get(0),ids.get(1));
    }
    public Optional<UUID> topUpSource(AppUserPrincipal a,UUID loan){return jdbc.query("select a.top_up_source_loan_id from accountant_loan_record r join loan_applications a on a.id=r.loan_id where r.loan_id=? and r.sacco_id=? and r.station_id=? and a.top_up_source_loan_id is not null",(r,n)->r.getObject(1,UUID.class),loan,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public Optional<TopUpPosting> topUp(AppUserPrincipal a,UUID loan){return topUpRows(a,"t.loan_id=? and t.reverses_id is null",loan).stream().findFirst();}
    public Optional<TopUpPosting> sourceTopUp(AppUserPrincipal a,UUID loan){return topUpRows(a,"t.source_id=? and t.reverses_id is null and not exists(select 1 from accountant_loan_topup z where z.reverses_id=t.id)",loan).stream().findFirst();}
    public Optional<TopUpPosting> transactionTopUp(AppUserPrincipal a,UUID transaction){return topUpRows(a,"t.id=(select coalesce(e.reverses_id,e.id) from accountant_loan_topup e where e.transaction_id=?)",transaction).stream().findFirst();}
    public Optional<TopUpPosting> topUpRequest(AppUserPrincipal a,UUID key,String hash){
        var rows=jdbc.queryForList("select loan_id,payload_hash,actor_id from accountant_loan_topup where sacco_id=? and station_id=? and request_key=?",a.getSaccoId(),a.getStationId(),key);
        if(rows.isEmpty())return Optional.empty();var r=rows.getFirst();if(!hash.equals(r.get("payload_hash")) || !a.getMemberId().equals(r.get("actor_id")))throw new IllegalArgumentException("recording.error.retry");
        return topUpRows(a,"t.request_key=?",key).stream().findFirst();
    }
    private List<TopUpPosting> topUpRows(AppUserPrincipal a,String condition,UUID id){return jdbc.query("""
        select t.*,n.loan_id new_number,o.loan_id source_number,(select z.id from accountant_loan_topup z where z.reverses_id=t.id) reversed_by
        from accountant_loan_topup t join loan_applications n on n.id=t.loan_id join loan_applications o on o.id=t.source_id
        where t.sacco_id=? and t.station_id=? and
        """+condition+" order by t.recorded_at,t.id limit 1",(r,n)->new TopUpPosting(r.getObject("id",UUID.class),r.getObject("loan_id",UUID.class),r.getObject("source_id",UUID.class),r.getString("new_number"),r.getString("source_number"),r.getObject("effective_date",LocalDate.class),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("excluded_interest"),r.getBigDecimal("cash"),r.getObject("journal_id",UUID.class),r.getObject("transaction_id",UUID.class),r.getObject("reverses_id",UUID.class),r.getObject("reversed_by",UUID.class),r.getObject("actor_id",UUID.class),r.getString("evidence"),r.getString("reason"),r.getBigDecimal("settled_charges"),r.getBigDecimal("deducted_charges")),a.getSaccoId(),a.getStationId(),id);}
    public void insertTopUp(AppUserPrincipal a,UUID id,UUID source,UUID posting,UUID transaction,UUID journal,PostForm f,String hash,TopUpQuote q,UUID reverses,OffsetDateTime now){
        insertTopUp(a,id,source,posting,transaction,journal,f,hash,q,reverses,now,new BigDecimal("0.00"));
    }
    public void insertTopUp(AppUserPrincipal a,UUID id,UUID source,UUID posting,UUID transaction,UUID journal,PostForm f,String hash,TopUpQuote q,UUID reverses,OffsetDateTime now,BigDecimal deductions){
        jdbc.update("""
            insert into accountant_loan_topup(id,loan_id,source_id,posting_id,sacco_id,station_id,request_key,payload_hash,effective_date,
             principal,interest,excluded_interest,cash,source_sequence,transaction_id,journal_id,reverses_id,actor_id,evidence,reason,recorded_at,settled_charges,deducted_charges)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,f.getLoanId(),source,posting,a.getSaccoId(),a.getStationId(),f.getRequestKey(),hash,f.getEffectiveDate(),q.principal(),q.interest(),q.excludedInterest(),f.getAmount().subtract(q.total()).subtract(deductions),q.sequence(),transaction,journal,reverses,a.getMemberId(),f.getEvidence(),f.getNotes(),now,q.charges(),deductions);
    }
    public void topUpJournal(AppUserPrincipal a,UUID id,UUID journal,PostForm f,String hash,UUID period,UUID reverses,OffsetDateTime now){
        jdbc.update("""
            insert into gl_journal(id,sacco_id,station_id,policy_version,period_id,source_type,source_reference,request_key,payload_hash,currency,state,evidence_reference,reason,effective_date,maker_id,recorded_at,reverses_id,direct_post)
            values(?,?,?,1,?,?,?,?,?,'TZS','DRAFT',?,?,?,?,?,?,true)
            """,journal,a.getSaccoId(),a.getStationId(),period,reverses==null?"LOAN_TOPUP":"LOAN_TOPUP_UNDO",id.toString(),f.getRequestKey(),hash,f.getEvidence(),f.getNotes(),f.getEffectiveDate(),a.getMemberId(),now,reverses);
    }
    public long topUpCorrectionSequence(AppUserPrincipal a,TopUpPosting original){
        var rows=jdbc.queryForList("""
            select s.next_sequence from loan_ledgers s join loan_ledgers n on n.loan_application_id=?
            join loan_repayment_transactions t on t.id=?
            join loan_applications l on l.id=n.loan_application_id
            where s.loan_application_id=? and s.sacco_id=? and s.station_id=? and s.next_sequence=t.sequence+1
             and n.next_sequence=1 and n.principal_paid=0 and n.interest_paid=0 and l.status='DISBURSED'
             and not exists(select 1 from loan_adjustment_request where loan_id in (?,?))
            """,original.loanId(),original.transactionId(),original.sourceId(),a.getSaccoId(),a.getStationId(),original.loanId(),original.sourceId());
        if(rows.isEmpty() || pendingTopUp(original.loanId(),null))throw new IllegalArgumentException("recording.error.topUpDependencies");
        return ((Number)rows.getFirst().get("next_sequence")).longValue();
    }
}
