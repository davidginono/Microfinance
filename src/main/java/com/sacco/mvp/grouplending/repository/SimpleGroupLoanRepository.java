package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.grouplending.dto.SimpleGroupLoanDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class SimpleGroupLoanRepository {
    private final JdbcTemplate jdbc;
    public boolean active(AppUserPrincipal a,UUID group){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from lending_group where id=? and sacco_id=? and station_id=? and status='ACTIVE')",Boolean.class,group,a.getSaccoId(),a.getStationId()));}
    public boolean mayFund(AppUserPrincipal a,UUID loan,LocalDate day){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from staff_group_loan c join lending_group g on g.id=c.group_id where c.loan_id=? and c.sacco_id=? and c.station_id=? and g.status='ACTIVE' and not exists(select 1 from staff_group_borrower b join members m on m.id=b.client_id where b.loan_id=c.loan_id and (b.signed_on>? or m.status<>'ACTIVE' or m.sacco_id<>c.sacco_id or m.station_id<>c.station_id)) and not exists(select 1 from staff_group_guarantor where loan_id=c.loan_id and signed_on>?))",Boolean.class,loan,a.getSaccoId(),a.getStationId(),day,day));}
    private static final String HISTORY="""
        with scoped as (select id,sacco_id,station_id from loan_applications where id=? and sacco_id=? and station_id=?),
        cycles as (select cycle_id from lending_group_participant where loan_id in(select id from scoped)
         union select cycle_id from lending_group_facility where loan_id in(select id from scoped)
         union select cycle_id from lending_group_shared_facility where loan_id in(select id from scoped)),
        documents as not materialized (
         select id,'PAPER' kind,filename,recorded_at,reference,body from lending_group_signed_paper where cycle_id in(select cycle_id from cycles) and sacco_id=(select sacco_id from scoped) and station_id=(select station_id from scoped)
         union all select id,'ORGANIZATION',filename,recorded_at,reference,body from lending_group_org_terms where cycle_id in(select cycle_id from cycles) and sacco_id=(select sacco_id from scoped) and station_id=(select station_id from scoped)
         union all select id,'SHARED',filename,recorded_at,''::varchar,body from lending_group_shared_document where cycle_id in(select cycle_id from cycles) and sacco_id=(select sacco_id from scoped) and station_id=(select station_id from scoped)
         union all select id,'GUARANTEE',filename,recorded_at,capacity_evidence,body from lending_group_personal_guarantee where loan_id in(select id from scoped)
         union all select id,'COVERAGE',filename,recorded_at,terms_reference,notice_pdf from lending_group_coverage_case where loan_id in(select id from scoped) and sacco_id=(select sacco_id from scoped) and station_id=(select station_id from scoped)
        )
        """;
    public List<HistoricalDocument> history(AppUserPrincipal a,UUID loan,int page,boolean coverage){return jdbc.query(HISTORY+"select id,kind,filename,recorded_at,reference from documents where (? or kind<>'COVERAGE') order by recorded_at desc,id limit 26 offset ?",(r,n)->new HistoricalDocument(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getObject(4,OffsetDateTime.class),r.getString(5)),loan,a.getSaccoId(),a.getStationId(),coverage,page*25);}
    public boolean hasHistory(AppUserPrincipal a,UUID loan){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from loan_applications l where l.id=? and l.sacco_id=? and l.station_id=? and (exists(select 1 from lending_group_participant where loan_id=l.id) or exists(select 1 from lending_group_facility where loan_id=l.id) or exists(select 1 from lending_group_shared_facility where loan_id=l.id)))",Boolean.class,loan,a.getSaccoId(),a.getStationId()));}
    public byte[] historicalDocument(AppUserPrincipal a,UUID loan,String kind,UUID id){return jdbc.query(HISTORY+"select body from documents where kind=? and id=? limit 1",(r,n)->r.getBytes(1),loan,a.getSaccoId(),a.getStationId(),kind,id).stream().findFirst().orElseThrow(()->new AccessDeniedException("Document unavailable"));}
    public Setup setup(AppUserPrincipal a,UUID group,boolean lock) {
        var rows=jdbc.query("""
            select g.*,m.full_name leader from lending_group g left join members m on m.id=g.leader_id
            where g.id=? and g.sacco_id=? and g.station_id=?
            """+(lock?" for update of g":""),(r,n)->new Setup(group,r.getInt("version"),r.getString("name"),
            r.getObject("leader_id",UUID.class),r.getString("leader"),r.getString("payout_type"),r.getString("payout_provider"),
            r.getString("payout_holder"),r.getString("payout_identifier"),r.getString("payout_evidence"),List.of()),group,a.getSaccoId(),a.getStationId());
        if(rows.isEmpty())throw new AccessDeniedException("Group unavailable");
        var s=rows.getFirst();var people=jdbc.query("""
            select m.id,m.member_no,m.full_name from lending_group_member gm join members m on m.id=gm.client_id
            where gm.group_id=? and gm.sacco_id=? and gm.station_id=? and gm.ends_on is null
            and gm.starts_on<=current_date and m.sacco_id=gm.sacco_id and m.station_id=gm.station_id
            and m.status='ACTIVE' and m.is_member order by m.id limit 51
            """,(r,n)->new Person(r.getObject(1,UUID.class),r.getString(2),r.getString(3)),group,a.getSaccoId(),a.getStationId());
        return new Setup(group,s.version(),s.name(),s.leaderId(),s.leader(),s.destinationType(),s.provider(),s.holder(),s.identifier(),s.evidence(),people);
    }
    public Map<UUID,Exposure> exposures(AppUserPrincipal a,List<UUID> clients){
        if(clients.isEmpty() || clients.size()>50)throw new IllegalArgumentException("group.error.members");
        var args=new ArrayList<Object>(clients);args.add(a.getSaccoId());args.add(a.getStationId());args.add(a.getSaccoId());args.add(a.getStationId());args.add(a.getSaccoId());args.add(a.getStationId());args.addAll(clients);
        var rows=jdbc.query("with selected(client) as (values "+String.join(",",Collections.nCopies(clients.size(),"(?::uuid)"))+") , "+"""
            scoped as not materialized (select id,applicant_member_id,status,repayment_frequency,installment_amount,amount,financial_snapshot from loan_applications
              where sacco_id=? and station_id=?),
            debt as (select l.applicant_member_id client,count(*) active_loans,
              count(*) filter(where l.amount is null or l.amount<=0 or l.installment_amount is null or l.installment_amount<=0 or l.repayment_frequency not in ('MONTHLY','WEEKLY') or l.repayment_frequency is null) missing_debt,
              sum(case when l.repayment_frequency='WEEKLY' then ceil(greatest(l.installment_amount,coalesce((l.financial_snapshot->>'maximumInstallmentAmount')::numeric,l.installment_amount))*5200/12)/100 else greatest(l.installment_amount,coalesce((l.financial_snapshot->>'maximumInstallmentAmount')::numeric,l.installment_amount)) end) monthly_debt
              from scoped l join selected s on s.client=l.applicant_member_id where l.status in ('DISBURSED','PAR','DEFAULTED') group by l.applicant_member_id),
            risk as (select l.applicant_member_id client,count(*) risk_loans from scoped l join selected s on s.client=l.applicant_member_id
              where l.status in ('PAR','DEFAULTED','WRITTEN_OFF') group by l.applicant_member_id),
            pending as not materialized (select * from scoped where status not in
              ('WITHDRAWN','PAID','DISBURSEMENT_REVERSED','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED')),
            personal_pending as (select l.applicant_member_id client,count(*) applications from pending l join selected s on s.client=l.applicant_member_id
              where l.status not in ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF') group by l.applicant_member_id),
            shared as (select o.client_id client,count(*) filter(where o.funded and (o.maximum_liability>0 or o.missing_history)) active_loans,
              count(*) filter(where o.pending) applications,count(*) filter(where o.status in ('PAR','DEFAULTED','WRITTEN_OFF')) risk_loans,
              count(*) filter(where o.missing_history) missing_history,coalesce(sum(o.monthly_payment) filter(where o.funded),0) monthly_debt,
              coalesce(sum(greatest(0,o.maximum_liability-o.allocated_obligation)),0) contingent
              from lending_shared_credit_obligation o join scoped l on l.id=o.loan_id join selected s on s.client=o.client_id group by o.client_id),
            off_branch_shared as (select o.client,count(*) missing from (select client_id client,sacco_id,station_id from lending_shared_credit_obligation union all select client_id,sacco_id,station_id from lending_group_personal_commitment union all select applicant_member_id,sacco_id,station_id from loan_applications where applicant_member_id is not null and status in('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF','RECORDED','SUBMITTED','READY_FOR_DISBURSEMENT')) o join selected s on s.client=o.client
              where o.sacco_id=? and o.station_id<>? group by o.client),
            liabilities as (select g.guarantor_id client,g.amount_cap cap from lending_guarantor_commitment g where g.sacco_id=? and g.station_id=? and g.guarantor_id in (
            """+String.join(",",Collections.nCopies(clients.size(),"?"))+"""
            )),
            guarantees as (select client,sum(cap) reserved,count(*) guarantee_count,count(*) filter(where cap is null) missing_cap from liabilities group by client)
            select s.client,coalesce(d.active_loans,0)+coalesce(h.active_loans,0) active_loans,
              coalesce(d.monthly_debt,0)+coalesce(h.monthly_debt,0) monthly_debt,coalesce(g.reserved,0)+coalesce(h.contingent,0) reserved,coalesce(g.guarantee_count,0) guarantee_count,
              coalesce(r.risk_loans,0)+coalesce(h.risk_loans,0) risk_loans,coalesce(d.missing_debt,0)+coalesce(g.missing_cap,0)+coalesce(h.missing_history,0)+coalesce(o.missing,0) missing_history,
              coalesce(p.applications,0)+coalesce(h.applications,0) applications from selected s
              left join debt d on d.client=s.client left join guarantees g on g.client=s.client left join risk r on r.client=s.client
              left join shared h on h.client=s.client left join personal_pending p on p.client=s.client left join off_branch_shared o on o.client=s.client
            """,(r,n)->Map.entry(r.getObject("client",UUID.class),new Exposure(r.getInt("active_loans"),r.getInt("risk_loans"),r.getInt("guarantee_count"),r.getBigDecimal("monthly_debt"),r.getBigDecimal("reserved"),r.getInt("missing_history"),r.getInt("applications"))),args.toArray());
        return rows.stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));
    }
    public boolean saveSetup(AppUserPrincipal a,UUID group,SetupForm f) {
        return jdbc.update("""
            update lending_group set leader_id=?,payout_type=?,payout_provider=?,payout_holder=?,payout_identifier=?,payout_evidence=?,version=version+1
            where id=? and sacco_id=? and station_id=? and version=? and status='ACTIVE'
            """,f.getLeaderId(),f.getDestinationType(),f.getProvider().strip(),f.getHolder().strip(),f.getIdentifier().strip(),
            f.getEvidence().strip(),group,a.getSaccoId(),a.getStationId(),f.getExpectedVersion())==1;
    }
    public UUID party(AppUserPrincipal a,UUID group) {
        jdbc.update("""
            insert into loan_borrower_party(id,sacco_id,kind,group_id,group_station_id) values(?,?,'JOINT_GROUP',?,?)
            on conflict(sacco_id,group_id,kind) do nothing
            """,UUID.randomUUID(),a.getSaccoId(),group,a.getStationId());
        return jdbc.queryForObject("select id from loan_borrower_party where sacco_id=? and group_id=? and group_station_id=? and kind='JOINT_GROUP'",UUID.class,a.getSaccoId(),group,a.getStationId());
    }
    public List<Map<String,Object>> profiles(AppUserPrincipal a,UUID group,LocalDate day) {
        return jdbc.queryForList("""
            select m.id,m.member_no,m.full_name,r.id revision,r.profile,case when coalesce((select kyc_required from lending_credit_policy where sacco_id=m.sacco_id and station_id=m.station_id order by revision desc limit 1),false) then client_kyc_error(m.id,?,?,?) end kyc
            from lending_group_member gm join members m on m.id=gm.client_id
            left join lateral(select id,profile from client_onboarding_revision where client_id=m.id and sacco_id=m.sacco_id
              and station_id=m.station_id and mode<>'DRAFT' order by sequence desc limit 1) r on true
            where gm.group_id=? and gm.sacco_id=? and gm.station_id=? and gm.ends_on is null and gm.starts_on<=?
            order by m.id limit 51
            """,a.getSaccoId(),a.getStationId(),day,group,a.getSaccoId(),a.getStationId(),day);
    }
    public void lockClients(AppUserPrincipal a,List<UUID> clients){if(clients.isEmpty()||clients.size()>70)throw new IllegalArgumentException("simpleGroup.error.members");var args=new ArrayList<Object>(List.of(a.getSaccoId(),a.getStationId()));args.addAll(clients);jdbc.queryForList("select lending_guarantor_capacity_lock(x.id,?,?) from (values "+String.join(",",Collections.nCopies(clients.size(),"(?::uuid)"))+")x(id) order by x.id",args.toArray());}
    public java.math.BigDecimal monthlyDebt(AppUserPrincipal a,UUID group){
        return jdbc.queryForObject("""
            with selected as (select client_id from lending_group_member where group_id=? and sacco_id=? and station_id=? and ends_on is null),
            debts as (select l.id,case when l.repayment_frequency='WEEKLY' then ceil(greatest(l.installment_amount,coalesce((l.financial_snapshot->>'maximumInstallmentAmount')::numeric,l.installment_amount))*5200/12)/100 else greatest(l.installment_amount,coalesce((l.financial_snapshot->>'maximumInstallmentAmount')::numeric,l.installment_amount)) end monthly
             from loan_applications l where l.sacco_id=? and l.station_id=? and l.status in('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF')
             and (l.applicant_member_id in(select client_id from selected) or exists(select 1 from lending_shared_credit_obligation o
               join selected s on s.client_id=o.client_id where o.loan_id=l.id and o.funded)))
            select coalesce(sum(monthly),0) from debts
            """,java.math.BigDecimal.class,group,a.getSaccoId(),a.getStationId(),a.getSaccoId(),a.getStationId());
    }
    public void contract(AppUserPrincipal a,UUID loan,Prepared p,ApplicationForm f,String requestHash,String snapshot,byte[] pdf,String filename,OffsetDateTime now) {
        jdbc.update("""
            insert into staff_group_loan(loan_id,group_id,sacco_id,station_id,leader_id,group_version,snapshot,reference,language,filename,body,recorded_by,recorded_at,terms_hash,request_hash)
            values(?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,(select loan_group_terms(l) from loan_applications l where id=?),?)
            """,loan,p.setup().groupId(),a.getSaccoId(),a.getStationId(),p.setup().leaderId(),p.setup().version(),snapshot,
            f.getReference().strip(),f.getLanguage(),filename,pdf,a.getMemberId(),now,loan,requestHash);
        var signed=new HashMap<UUID,LocalDate>();f.getSigners().forEach(s->signed.put(s.getClientId(),s.getSignedOn()));
        var rows=new ArrayList<Object[]>();for(var person:p.assessments())rows.add(new Object[]{loan,person.clientId(),person.number(),person.name(),signed.get(person.clientId()),person.income(),person.expenses(),person.debt(),person.source(),person.evidence(),person.profileRevision()});
        jdbc.batchUpdate("insert into staff_group_borrower(loan_id,client_id,client_number,client_name,signed_on,income,expenses,debt,income_source,evidence,profile_revision) values(?,?,?,?,?,?,?,?,?,?,?)",rows);
    }
    public boolean partyContract(AppUserPrincipal a,UUID party){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from staff_group_loan c join loan_applications l on l.id=c.loan_id where l.borrower_party_id=? and c.sacco_id=? and c.station_id=? and l.sacco_id=c.sacco_id and l.station_id=c.station_id)",Boolean.class,party,a.getSaccoId(),a.getStationId()));}
    public boolean simple(AppUserPrincipal a,UUID loan) {return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from staff_group_loan where loan_id=? and sacco_id=? and station_id=?)",Boolean.class,loan,a.getSaccoId(),a.getStationId()));}
    public String fingerprint(String input){return jdbc.queryForObject("select encode(sha256(convert_to(loan_group_canonical(?::jsonb)::text,'UTF8')),'hex')",String.class,input);}
    public Optional<UUID> retry(AppUserPrincipal a,UUID key,String fingerprint){var rows=jdbc.queryForList("select g.loan_id,g.request_hash,r.actor_id from accountant_loan_record r join staff_group_loan g on g.loan_id=r.loan_id where r.sacco_id=? and r.station_id=? and r.request_key=?",a.getSaccoId(),a.getStationId(),key);if(rows.isEmpty())return Optional.empty();var row=rows.getFirst();if(!a.getMemberId().equals(row.get("actor_id"))||!fingerprint.equals(row.get("request_hash")))throw new IllegalArgumentException("simpleGroup.error.changed");return Optional.of((UUID)row.get("loan_id"));}
    public boolean related(AppUserPrincipal a,UUID loan){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from staff_group_borrower b join staff_group_loan g on g.loan_id=b.loan_id where b.loan_id=? and b.client_id=? and g.sacco_id=? and g.station_id=?)",Boolean.class,loan,a.getMemberId(),a.getSaccoId(),a.getStationId()));}
    public boolean sameContract(AppUserPrincipal a,UUID loan,String snapshot,byte[] pdf){return Boolean.TRUE.equals(jdbc.queryForObject("select coalesce((select snapshot=?::jsonb and body=? and recorded_by=? from staff_group_loan where loan_id=? and sacco_id=? and station_id=?),false)",Boolean.class,snapshot,pdf,a.getMemberId(),loan,a.getSaccoId(),a.getStationId()));}
    public Optional<Contract> contract(AppUserPrincipal a,UUID loan) {
        return jdbc.query("select * from staff_group_loan where loan_id=? and sacco_id=? and station_id=?",(r,n)->new Contract(loan,r.getObject("group_id",UUID.class),r.getString("snapshot"),"",r.getString("snapshot"),r.getString("reference"),r.getString("language"),List.of()),loan,a.getSaccoId(),a.getStationId()).stream().findFirst();
    }
    public byte[] document(AppUserPrincipal a,UUID loan) {return jdbc.query("select body from staff_group_loan where loan_id=? and sacco_id=? and station_id=?",(r,n)->r.getBytes(1),loan,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Document unavailable"));}
    public List<Borrower> borrowers(AppUserPrincipal a,UUID loan){return jdbc.query("select b.* from staff_group_borrower b join staff_group_loan g on g.loan_id=b.loan_id where b.loan_id=? and g.sacco_id=? and g.station_id=? order by b.client_number,b.client_id limit 50",(r,n)->new Borrower(r.getObject("client_id",UUID.class),r.getString("client_number"),r.getString("client_name"),r.getObject("signed_on",LocalDate.class),r.getBigDecimal("income"),r.getBigDecimal("expenses"),r.getBigDecimal("debt"),r.getString("evidence")),loan,a.getSaccoId(),a.getStationId());}
    public void funding(AppUserPrincipal a,UUID loan,UUID posting,UUID journal,OffsetDateTime at) {jdbc.update("insert into staff_group_funding(loan_id,posting_id,journal_id,actor_id,recorded_at) values(?,?,?,?,?)",loan,posting,journal,a.getMemberId(),at);}
    public java.math.BigDecimal totalPayable(UUID loan){return jdbc.queryForObject("select (financial_snapshot::jsonb->>'recordedTotalPayable')::numeric from loan_applications where id=?",java.math.BigDecimal.class,loan);}
    public void lockGuarantor(AppUserPrincipal a,UUID client){jdbc.queryForList("select lending_guarantor_capacity_lock(?,?,?)",client,a.getSaccoId(),a.getStationId());}
    public Map<String,Person> guarantorPeople(AppUserPrincipal a,List<String> references){
        if(references.isEmpty())return Map.of();var args=new ArrayList<Object>(List.of(a.getSaccoId(),a.getStationId()));args.addAll(references);
        var people=jdbc.query("select id,member_no,full_name from members where sacco_id=? and station_id=? and status='ACTIVE' and is_member and (member_no in ("+String.join(",",Collections.nCopies(references.size(),"?"))+") or id::text in ("+String.join(",",Collections.nCopies(references.size(),"?"))+")) limit 21",(r,n)->new Person(r.getObject(1,UUID.class),r.getString(2),r.getString(3)),java.util.stream.Stream.concat(args.stream(),references.stream()).toArray());
        var result=new HashMap<String,Person>();for(var person:people){result.put(person.id().toString(),person);result.put(person.number(),person);}return result;
    }
    public void guarantor(UUID loan,com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.Guarantor g,java.math.BigDecimal cap){jdbc.update("insert into staff_group_guarantor(loan_id,client_id,amount_cap,budget,evidence,signed_on) values(?,?,?,?,?,?) on conflict(loan_id,client_id) do nothing",loan,g.getClientId(),cap,g.getGuaranteeBudget(),g.getGuaranteeEvidence().strip(),g.getSignedOn());}
    public boolean guaranteesReady(AppUserPrincipal a,UUID loan){return Boolean.TRUE.equals(jdbc.queryForObject("select staff_group_guarantees_ready(loan_id) from staff_group_loan where loan_id=? and sacco_id=? and station_id=?",Boolean.class,loan,a.getSaccoId(),a.getStationId()));}
    public Draft draft(AppUserPrincipal a,UUID id,boolean lock){return jdbc.query("select id,version,form_data::text from staff_group_draft where id=? and sacco_id=? and station_id=?"+(lock?" for update":""),(r,n)->new Draft(r.getObject(1,UUID.class),r.getInt(2),r.getString(3)),id,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Draft unavailable"));}
    public List<DraftSummary> drafts(AppUserPrincipal a,UUID group,int page){return jdbc.query("select d.id,m.full_name,d.updated_at,(d.form_data->>'principal')::numeric amount from staff_group_draft d join members m on m.id=d.actor_id where d.group_id=? and d.sacco_id=? and d.station_id=? order by d.updated_at desc,d.id limit 26 offset ?",(r,n)->new DraftSummary(r.getObject(1,UUID.class),r.getString(2),r.getObject(3,OffsetDateTime.class),r.getBigDecimal(4)),group,a.getSaccoId(),a.getStationId(),page*25);}
    public List<Map<String,Object>> draftDocuments(AppUserPrincipal a,UUID id){return jdbc.queryForList("select ordinal-1 id,upload->>'filename' as \"originalName\",case when coalesce((upload->>'agreement')::boolean,false) then upload->>'filename' else upload->>'title' end as \"displayName\",upload->>'purpose' as \"documentPurpose\",true as \"draftGroupFile\",coalesce((upload->>'agreement')::boolean,false) agreement from staff_group_draft d cross join lateral jsonb_array_elements(d.uploads) with ordinality items(upload,ordinal) where d.id=? and d.sacco_id=? and d.station_id=? order by ordinal limit 11",id,a.getSaccoId(),a.getStationId());}
    public DraftFile draftFile(AppUserPrincipal a,UUID id,int index,tools.jackson.databind.ObjectMapper json){return jdbc.query("select (uploads->?::int)::text from staff_group_draft where id=? and sacco_id=? and station_id=?",(r,n)->{String value=r.getString(1);if(value==null)throw new IllegalArgumentException("simpleGroup.error.document");return json.readValue(value,DraftFile.class);},index,id,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Draft unavailable"));}
    public String draftFiles(AppUserPrincipal a,UUID id){return jdbc.query("select uploads::text from staff_group_draft where id=? and sacco_id=? and station_id=?",(r,n)->r.getString(1),id,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Draft unavailable"));}
    public void draftFiles(AppUserPrincipal a,UUID id,String files){jdbc.update("update staff_group_draft set uploads=?::jsonb where id=? and sacco_id=? and station_id=?",files,id,a.getSaccoId(),a.getStationId());}
    public UUID draft(AppUserPrincipal a,UUID id,UUID key,int version,UUID group,String data,OffsetDateTime now){
        if(id==null){
            id=key;jdbc.update("insert into staff_group_draft(id,sacco_id,station_id,actor_id,group_id,form_data,updated_at) values(?,?,?,?,?,?::jsonb,?) on conflict(id) do nothing",id,a.getSaccoId(),a.getStationId(),a.getMemberId(),group,data,now);
            if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from staff_group_draft where id=? and sacco_id=? and station_id=? and actor_id=? and group_id=? and form_data=?::jsonb)",Boolean.class,id,a.getSaccoId(),a.getStationId(),a.getMemberId(),group,data)))throw new IllegalArgumentException("simpleGroup.error.changed");
        }else if(jdbc.update("update staff_group_draft set group_id=?,form_data=?::jsonb,actor_id=?,version=version+1,updated_at=? where id=? and sacco_id=? and station_id=? and version=?",group,data,a.getMemberId(),now,id,a.getSaccoId(),a.getStationId(),version)!=1
          && !Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from staff_group_draft where id=? and sacco_id=? and station_id=? and form_data=?::jsonb)",Boolean.class,id,a.getSaccoId(),a.getStationId(),data)))throw new IllegalArgumentException("simpleGroup.error.changed");
        return id;
    }
    public void consumeDraft(AppUserPrincipal a,UUID id,int version){if(jdbc.update("delete from staff_group_draft where id=? and sacco_id=? and station_id=? and version=?",id,a.getSaccoId(),a.getStationId(),version)!=1)throw new IllegalArgumentException("simpleGroup.error.changed");}
    public List<com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice> choices(AppUserPrincipal a,String search,int page) {
        return jdbc.query("select id,code||' - '||name from lending_group where sacco_id=? and station_id=? and status='ACTIVE' and strpos(lower(code||' '||name),?)>0 order by name,id limit 26 offset ?",(r,n)->new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice(r.getObject(1,UUID.class),r.getString(2),"GROUP"),a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);
    }
}
