package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.grouplending.dto.GroupConsentDtos.Challenge;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.Choice;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.support.ResourceTableSort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class GroupCycleRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private static final RowMapper<Policy> POLICY=(r,n)->new Policy(r.getObject("id",UUID.class),r.getObject("product_id",UUID.class),r.getString("product_name"),
        Model.valueOf(r.getString("model")),r.getInt("min_members"),r.getInt("max_members"),r.getBigDecimal("max_principal"),r.getBigDecimal("max_guarantee"),
        r.getInt("max_active_cycles"),r.getBoolean("own_loan_guarantors"),r.getBoolean("enabled"),r.getString("policy_reference"),r.getString("agreement_reference"),r.getObject("created_at",OffsetDateTime.class),r.getLong("revision"));
    private static final String POLICIES="""
        select p.*,coalesce(l.product_name,l.loan_type) product_name from lending_group_policy p join loan_product_settings l on l.id=p.product_id
        where p.sacco_id=? and p.station_id=?
        """;
    public List<Choice> products(AppUserPrincipal a,String search,int page){return jdbc.query("""
        select id,coalesce(product_name,loan_type) label from loan_product_settings where sacco_id=? and active and coalesce(product_status,'ACTIVE')='ACTIVE'
        and not custom_recording_configuration and strpos(lower(coalesce(product_name,loan_type)),?)>0 order by product_name,id limit 26 offset ?
        """,(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2)),a.getSaccoId(),search.toLowerCase(Locale.ROOT),page*25);}
    public boolean product(AppUserPrincipal a,UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("""
        select exists(select 1 from loan_product_settings where id=? and sacco_id=? and active and coalesce(product_status,'ACTIVE')='ACTIVE'
        and interest_method='REDUCING_BALANCE' and not custom_recording_configuration)
        """,Boolean.class,id,a.getSaccoId()));}
    public Optional<Choice> selectedProduct(AppUserPrincipal a,UUID id){return jdbc.query("select id,coalesce(product_name,loan_type) from loan_product_settings where sacco_id=? and id=?",(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2)),a.getSaccoId(),id).stream().findFirst();}
    public ProductRules rules(AppUserPrincipal a,UUID id){return jdbc.query("""
        select guarantors_required,coalesce(max_repayment_to_disposable_income_ratio,0.4) ratio,coalesce(collateral_required,false) collateral,
        coalesce(min_collateral_coverage_ratio,0) coverage,coalesce(allow_application_with_active_loan,false) allow_active,
        coalesce(maximum_active_loans,1) max_active from loan_product_settings where id=? and sacco_id=? for share
        """,(r,n)->new ProductRules(r.getInt("guarantors_required"),r.getBigDecimal("ratio"),r.getBoolean("collateral"),r.getBigDecimal("coverage"),r.getBoolean("allow_active"),r.getInt("max_active")),id,a.getSaccoId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Product unavailable"));}
    public void lock(AppUserPrincipal a,String kind,UUID key){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))",kind+"/"+a.getSaccoId()+"/"+a.getStationId()+"/"+key);}
    public Optional<UUID> retry(AppUserPrincipal a,UUID key,String hash,boolean cycle){
        String table=cycle?"lending_group_cycle":"lending_group_policy";var rows=jdbc.queryForList("select id,payload_hash,created_by from "+table+" where sacco_id=? and station_id=? and request_key=?",a.getSaccoId(),a.getStationId(),key);
        if(rows.isEmpty())return Optional.empty();var r=rows.getFirst();
        if(!hash.equals(r.get("payload_hash")) || !a.getMemberId().equals(r.get("created_by")))throw new IllegalArgumentException("group.error.retry");return Optional.of((UUID)r.get("id"));
    }
    public UUID policy(AppUserPrincipal a,PolicyForm f,String hash,OffsetDateTime now){UUID id=UUID.randomUUID();jdbc.update("""
        insert into lending_group_policy(id,sacco_id,station_id,product_id,model,min_members,max_members,max_principal,max_guarantee,max_active_cycles,
          own_loan_guarantors,policy_reference,agreement_reference,enabled,request_key,payload_hash,created_by,created_at)
        values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,id,a.getSaccoId(),a.getStationId(),f.getProductId(),f.getModel().name(),f.getMinMembers(),f.getMaxMembers(),f.getMaxPrincipal(),f.getMaxGuarantee(),f.getMaxActiveCycles(),
        f.isOwnLoanGuarantors(),f.getPolicyReference().strip(),f.getAgreementReference().strip(),f.isEnabled(),f.getRequestKey(),hash,a.getMemberId(),now);return id;}
    public Policy policy(AppUserPrincipal a,UUID id){return jdbc.query(POLICIES+" and p.id=?",POLICY,a.getSaccoId(),a.getStationId(),id).stream().findFirst().orElseThrow(()->new AccessDeniedException("Group product unavailable"));}
    public List<Policy> policies(AppUserPrincipal a,int page){return policies(a,"",page);}
    public List<Policy> policies(AppUserPrincipal a,String search,int page){return jdbc.query(POLICIES+" and strpos(lower(coalesce(l.product_name,l.loan_type)),?)>0 and not exists(select 1 from lending_group_policy newer where newer.sacco_id=p.sacco_id and newer.station_id=p.station_id and newer.product_id=p.product_id and newer.model=p.model and newer.revision>p.revision) order by "+
        ResourceTableSort.order("group-product-rules",Map.of("product","product_name","model","p.model","members","row(p.min_members,p.max_members)","principal","p.max_principal","guarantee","p.max_guarantee","agreement","p.agreement_reference","enabled","p.enabled"),"p.id","product_name,p.model,p.id")+" limit 26 offset ?",POLICY,a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);}
    public List<Choice> memberChoices(AppUserPrincipal a,UUID group,String search,int page){return jdbc.query("""
        select m.id,m.member_no||' - '||m.full_name label from lending_group_member gm join members m on m.id=gm.client_id
        where gm.sacco_id=? and gm.station_id=? and gm.group_id=? and gm.ends_on is null and m.status='ACTIVE'
        and strpos(lower(m.member_no||' '||m.full_name),?)>0 order by m.member_no,m.id limit 26 offset ?
        """,(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2)),a.getSaccoId(),a.getStationId(),group,search.toLowerCase(Locale.ROOT),page*25);}
    public Map<UUID,String> memberLabels(AppUserPrincipal a,UUID group,List<UUID> ids){if(ids.isEmpty())return Map.of();
        var args=new ArrayList<Object>(List.of(a.getSaccoId(),a.getStationId(),group));args.addAll(ids);var labels=new HashMap<UUID,String>();
        jdbc.query("select distinct m.id,m.member_no||' - '||m.full_name label from lending_group_member gm join members m on m.id=gm.client_id where gm.sacco_id=? and gm.station_id=? and gm.group_id=? and m.id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",(RowCallbackHandler)r->labels.put(r.getObject(1,UUID.class),r.getString(2)),args.toArray());return labels;
    }
    public boolean current(Policy p,AppUserPrincipal a){return !Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from lending_group_policy where sacco_id=? and station_id=? and product_id=? and model=? and revision>?)",Boolean.class,a.getSaccoId(),a.getStationId(),p.productId(),p.model().name(),p.revision()));}
    public UUID cycle(AppUserPrincipal a,UUID group,Policy p,CycleForm f,String hash,OffsetDateTime now){UUID id=UUID.randomUUID();jdbc.update("""
        insert into lending_group_cycle(id,group_id,sacco_id,station_id,policy_id,product_id,model,form_data,request_key,payload_hash,created_by,created_at)
        values(?,?,?,?,?,?,?,?::jsonb,?,?,?,?)
        """,id,group,a.getSaccoId(),a.getStationId(),p.id(),p.productId(),p.model().name(),json.writeValueAsString(f),f.getRequestKey(),hash,a.getMemberId(),now);return id;}
    private Cycle cycleRow(java.sql.ResultSet r,int n) throws java.sql.SQLException {return new Cycle(r.getObject("id",UUID.class),r.getObject("group_id",UUID.class),r.getString("group_name"),r.getObject("policy_id",UUID.class),
        r.getObject("product_id",UUID.class),Model.valueOf(r.getString("model")),r.getString("state"),r.getInt("version"),json.readValue(r.getString("form_data"),CycleForm.class),r.getObject("submitted_at",OffsetDateTime.class));}
    private static final String CYCLES="select c.*,g.name group_name from lending_group_cycle c join lending_group g on g.id=c.group_id where c.sacco_id=? and c.station_id=?";
    public Cycle cycle(AppUserPrincipal a,UUID id,boolean lock){return jdbc.query(CYCLES+" and c.id=?"+(lock?" for update of c":""),(RowMapper<Cycle>)this::cycleRow,a.getSaccoId(),a.getStationId(),id).stream().findFirst().orElseThrow(()->new AccessDeniedException("Group cycle unavailable"));}
    public List<CycleSummary> cycles(AppUserPrincipal a,UUID group,int page){return jdbc.query("""
        select c.id,(c.form_data->>'applicationDate')::date application_date,c.form_data->>'purpose' purpose,c.model,c.state,c.submitted_at
        from lending_group_cycle c where c.sacco_id=? and c.station_id=? and c.group_id=? order by
        """+ResourceTableSort.order("group-cycles",Map.of("cycle","row(c.form_data->>'applicationDate',c.form_data->>'purpose')","model","c.model","status","c.state","submitted","c.submitted_at"),"c.id","c.created_at desc,c.id desc")+" limit 26 offset ?",
        (r,n)->new CycleSummary(r.getObject("id",UUID.class),r.getObject("application_date",LocalDate.class),r.getString("purpose"),Model.valueOf(r.getString("model")),r.getString("state"),r.getObject("submitted_at",OffsetDateTime.class)),a.getSaccoId(),a.getStationId(),group,page*25);}
    public void save(Cycle c,CycleForm form,boolean submit,OffsetDateTime now){if(jdbc.update("update lending_group_cycle set form_data=?::jsonb,version=version+1,state=?,submitted_at=? where id=? and version=? and state='DRAFT'",
        json.writeValueAsString(form),submit?"SUBMITTED":"DRAFT",submit?now:null,c.id(),form.getExpectedVersion())!=1)throw new IllegalArgumentException("group.error.changed");}
    public Optional<Termination> termination(AppUserPrincipal a,UUID id){return jdbc.query("select t.*,m.full_name actor from lending_group_termination t join members m on m.id=t.actor_id where t.cycle_id=? and t.sacco_id=? and t.station_id=?",(r,n)->new Termination(r.getString("kind"),r.getObject("actor_id",UUID.class),r.getString("actor"),r.getString("reason"),r.getObject("recorded_at",OffsetDateTime.class)),id,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public boolean lockUnfundedLoans(AppUserPrincipal a,UUID cycle){var eligible=jdbc.query("""
        select l.status not in ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF','PAID','DISBURSEMENT_REVERSED')
          and not exists(select 1 from loan_ledgers b where b.loan_application_id=l.id)
          and not exists(select 1 from accountant_loan_post b where b.loan_id=l.id) eligible
        from lending_group_participant p join loan_applications l on l.id=p.loan_id
        where p.cycle_id=? and p.sacco_id=? and p.station_id=? order by l.id limit 51 for update of l
        """,(r,n)->r.getBoolean("eligible"),cycle,a.getSaccoId(),a.getStationId());return eligible.size()<=50 && eligible.stream().allMatch(Boolean.TRUE::equals);}
    public void terminate(AppUserPrincipal a,Cycle c,String kind,String reason,OffsetDateTime now){
        jdbc.update("insert into lending_group_termination(cycle_id,sacco_id,station_id,kind,actor_id,reason,recorded_at) values(?,?,?,?,?,?,?)",c.id(),a.getSaccoId(),a.getStationId(),kind,a.getMemberId(),reason,now);
        jdbc.update("""
            update loan_applications l set status='WITHDRAWN',updated_at=?,version=version+1 where l.sacco_id=? and l.station_id=?
            and exists(select 1 from lending_group_participant p where p.cycle_id=? and p.loan_id=l.id)
            and l.status not in ('MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED')
            """,now,a.getSaccoId(),a.getStationId(),c.id());
        if(jdbc.update("update lending_group_cycle set state='CANCELLED',version=version+1 where id=? and sacco_id=? and station_id=? and version=?",c.id(),a.getSaccoId(),a.getStationId(),c.version())!=1)throw new IllegalArgumentException("group.error.changed");
    }
    public void lockClients(AppUserPrincipal a,List<UUID> clients){
        var args=new ArrayList<Object>();args.add("GROUP_CAPACITY/"+a.getSaccoId()+"/"+a.getStationId()+"/");args.addAll(clients);
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(? || client::text,0)) from (select client from (values "+
            String.join(",",Collections.nCopies(clients.size(),"(?::uuid)"))+") ids(client) order by client) ordered",args.toArray());
    }
    public Map<UUID,UUID> memberships(AppUserPrincipal a,UUID group,List<UUID> ids){
        var args=new ArrayList<Object>(List.of(a.getSaccoId(),a.getStationId(),group));args.addAll(ids);var result=new HashMap<UUID,UUID>();
        jdbc.query("select gm.client_id,gm.id from lending_group_member gm join members m on m.id=gm.client_id where gm.sacco_id=? and gm.station_id=? and gm.group_id=? and gm.ends_on is null and m.status='ACTIVE' and gm.client_id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",(org.springframework.jdbc.core.RowCallbackHandler)r->result.put(r.getObject(1,UUID.class),r.getObject(2,UUID.class)),args.toArray());return result;
    }
    /** One bounded read for every selected participant; never one exposure query per member. */
    public Map<UUID,Exposure> exposures(AppUserPrincipal a,List<UUID> clients){
        if(clients.isEmpty() || clients.size()>50)throw new IllegalArgumentException("group.error.members");
        var args=new ArrayList<Object>(clients);args.add(a.getSaccoId());args.add(a.getStationId());args.add(a.getSaccoId());args.add(a.getStationId());args.addAll(clients);
        var rows=jdbc.query("with selected(client) as (values "+String.join(",",Collections.nCopies(clients.size(),"(?::uuid)"))+") , "+"""
            scoped as not materialized (select id,applicant_member_id,status,repayment_frequency,installment_amount,amount,financial_snapshot from loan_applications
              where sacco_id=? and station_id=?),
            debt as (select l.applicant_member_id client,count(*) active_loans,
              count(*) filter(where l.amount is null or l.amount<=0 or l.installment_amount is null or l.installment_amount<=0 or l.repayment_frequency not in ('MONTHLY','WEEKLY') or l.repayment_frequency is null) missing_debt,
              sum(case when l.repayment_frequency='WEEKLY' then l.installment_amount*52/12 else l.installment_amount end) monthly_debt
              from scoped l join selected s on s.client=l.applicant_member_id where l.status in ('DISBURSED','PAR','DEFAULTED') group by l.applicant_member_id),
            risk as (select l.applicant_member_id client,count(*) risk_loans from scoped l join selected s on s.client=l.applicant_member_id
              where l.status in ('PAR','DEFAULTED','WRITTEN_OFF') group by l.applicant_member_id),
            pending as not materialized (select * from scoped where status not in
              ('WITHDRAWN','PAID','DISBURSEMENT_REVERSED','MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED')),
            cycles as (select x.client_id client,count(distinct x.cycle_id) active_cycles from lending_group_participant x
              join selected s on s.client=x.client_id where exists(select 1 from lending_group_participant peer join pending p on p.id=peer.loan_id where peer.cycle_id=x.cycle_id) group by x.client_id),
            liabilities as (select g.guarantor_id client,g.amount_cap cap from lending_guarantor_commitment g where g.sacco_id=? and g.station_id=? and g.guarantor_id in (
            """+String.join(",",Collections.nCopies(clients.size(),"?"))+"""
            )),
            guarantees as (select client,sum(cap) reserved,count(*) guarantee_count,count(*) filter(where cap is null) missing_cap from liabilities group by client)
            select s.client,coalesce(c.active_cycles,0) active_cycles,coalesce(d.active_loans,0) active_loans,
              coalesce(d.monthly_debt,0) monthly_debt,coalesce(g.reserved,0) reserved,coalesce(g.guarantee_count,0) guarantee_count,
              coalesce(r.risk_loans,0) risk_loans,coalesce(d.missing_debt,0)+coalesce(g.missing_cap,0) missing_history from selected s
              left join debt d on d.client=s.client left join cycles c on c.client=s.client left join guarantees g on g.client=s.client left join risk r on r.client=s.client
            """,(r,n)->Map.entry(r.getObject("client",UUID.class),new Exposure(r.getInt("active_cycles"),r.getInt("active_loans"),r.getInt("risk_loans"),r.getInt("guarantee_count"),r.getBigDecimal("monthly_debt"),r.getBigDecimal("reserved"),r.getInt("missing_history"))),args.toArray());
        return rows.stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));
    }
    public void participant(AppUserPrincipal a,Cycle c,ParticipantForm f,UUID membership,UUID loan,BigDecimal monthly){jdbc.update("""
        insert into lending_group_participant(id,cycle_id,sacco_id,station_id,client_id,membership_id,principal,income,expenses,other_debt,monthly_payment,
          guarantee_capacity,assessment_evidence,capacity_evidence,loan_id) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,UUID.randomUUID(),c.id(),a.getSaccoId(),a.getStationId(),f.getClientId(),membership,f.getPrincipal(),f.getIncome(),f.getExpenses(),f.getOtherDebt(),monthly,
        f.getGuaranteeCapacity(),f.getAssessmentEvidence().strip(),f.getCapacityEvidence().strip(),loan);}
    public void guarantee(AppUserPrincipal a,Cycle c,UUID guarantor,UUID borrower,UUID loan,BigDecimal cap,String reference,OffsetDateTime now){jdbc.update("""
        insert into lending_group_guarantee(id,cycle_id,sacco_id,station_id,guarantor_id,borrower_id,loan_id,amount_cap,terms_reference,created_at) values(?,?,?,?,?,?,?,?,?,?)
        """,UUID.randomUUID(),c.id(),a.getSaccoId(),a.getStationId(),guarantor,borrower,loan,cap,reference,now);}
    public List<Participant> participants(AppUserPrincipal a,UUID cycle){return jdbc.query("""
        select x.*,m.member_no,m.full_name,coalesce(l.loan_id,l.application_number::text) loan_number,l.status loan_status,
        exists(select 1 from lending_group_consent_evidence s where s.cycle_id=x.cycle_id and s.client_id=x.client_id) consented
        from lending_group_participant x join members m on m.id=x.client_id left join loan_applications l on l.id=x.loan_id
        where x.cycle_id=? and x.sacco_id=? and x.station_id=? order by
        """+ResourceTableSort.order("group-cycle-participants",Map.of("client","m.member_no","principal","x.principal","monthly","x.monthly_payment","loan","loan_number","consent","consented","status","l.status"),"x.id","m.member_no,x.id")+" limit 50",(r,n)->new Participant(r.getObject("id",UUID.class),r.getObject("client_id",UUID.class),r.getString("member_no"),r.getString("full_name"),r.getBigDecimal("principal"),r.getBigDecimal("monthly_payment"),r.getBigDecimal("guarantee_capacity"),r.getObject("loan_id",UUID.class),r.getString("loan_number"),r.getString("loan_status"),r.getBoolean("consented")),cycle,a.getSaccoId(),a.getStationId());}
    public List<ClientCycle> clientCycles(AppUserPrincipal a,int page){return jdbc.query("""
        select c.id,g.name,c.submitted_at,c.state,exists(select 1 from lending_group_consent_evidence s where s.cycle_id=c.id and s.client_id=x.client_id) accepted
        from lending_group_participant x join lending_group_cycle c on c.id=x.cycle_id join lending_group g on g.id=c.group_id
        where x.sacco_id=? and x.station_id=? and x.client_id=? and c.state in ('SUBMITTED','CANCELLED') order by
        """+ResourceTableSort.order("client-group-loans",Map.of("group","g.name","date","c.submitted_at","accepted","accepted","state","c.state"),"c.id","c.submitted_at desc,c.id desc")+" limit 26 offset ?",
        (r,n)->new ClientCycle(r.getObject("id",UUID.class),r.getString("name"),r.getObject("submitted_at",OffsetDateTime.class),r.getBoolean("accepted"),r.getString("state")),a.getSaccoId(),a.getStationId(),a.getMemberId(),page*25);}
    public boolean participant(AppUserPrincipal a,UUID cycle){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from lending_group_participant where cycle_id=? and sacco_id=? and station_id=? and client_id=?)",Boolean.class,cycle,a.getSaccoId(),a.getStationId(),a.getMemberId()));}
    public List<Guarantee> guarantees(AppUserPrincipal a,UUID cycle){return jdbc.query("""
        select g.*,m.full_name guarantor,b.full_name borrower,coalesce(l.loan_id,l.application_number::text) loan_number
        from lending_group_guarantee g join members m on m.id=g.guarantor_id join members b on b.id=g.borrower_id join loan_applications l on l.id=g.loan_id
        where g.cycle_id=? and g.sacco_id=? and g.station_id=? and g.guarantor_id=? order by
        """+ResourceTableSort.order("group-personal-guarantees",Map.of("borrower","b.full_name","loan","loan_number","cap","g.amount_cap","agreement","g.terms_reference"),"g.id","b.member_no,g.id")+" limit 49",(r,n)->new Guarantee(r.getObject("id",UUID.class),r.getObject("guarantor_id",UUID.class),r.getString("guarantor"),r.getObject("borrower_id",UUID.class),r.getString("borrower"),r.getString("loan_number"),r.getBigDecimal("amount_cap"),r.getString("terms_reference")),cycle,a.getSaccoId(),a.getStationId(),a.getMemberId());}
    public void consent(AppUserPrincipal a,UUID cycle,UUID policy,String signature,UUID challenge,OffsetDateTime now){
        jdbc.update("insert into lending_group_consent(cycle_id,sacco_id,station_id,client_id,accepted_at) values(?,?,?,?,?) on conflict(cycle_id,client_id) do nothing",cycle,a.getSaccoId(),a.getStationId(),a.getMemberId(),now);
        jdbc.update("insert into lending_group_consent_evidence(cycle_id,sacco_id,station_id,client_id,policy_id,signature_text,signed_at,otp_required,challenge_id) values(?,?,?,?,?,?,?,?,?)",cycle,a.getSaccoId(),a.getStationId(),a.getMemberId(),policy,signature,now,challenge!=null,challenge);
    }
    public List<Challenge> challenges(AppUserPrincipal a,UUID cycle){return jdbc.query("select * from lending_group_consent_challenge where cycle_id=? and sacco_id=? and station_id=? and client_id=? order by issued_at desc,id desc limit 4 for update",
        (r,n)->new Challenge(r.getObject("id",UUID.class),r.getString("code_hash"),r.getObject("issued_at",OffsetDateTime.class),r.getObject("expires_at",OffsetDateTime.class),r.getObject("delivered_at",OffsetDateTime.class),r.getObject("consumed_at",OffsetDateTime.class),r.getInt("attempts")),cycle,a.getSaccoId(),a.getStationId(),a.getMemberId());}
    public void challenge(AppUserPrincipal a,UUID cycle,UUID id,String hash,OffsetDateTime now,OffsetDateTime expires){jdbc.update("insert into lending_group_consent_challenge(id,cycle_id,sacco_id,station_id,client_id,code_hash,issued_at,expires_at) values(?,?,?,?,?,?,?,?)",id,cycle,a.getSaccoId(),a.getStationId(),a.getMemberId(),hash,now,expires);}
    public void challengeDelivered(UUID id,OffsetDateTime now){jdbc.update("update lending_group_consent_challenge set delivered_at=? where id=? and delivered_at is null and consumed_at is null",now,id);}
    public void challengeAttempt(UUID id){jdbc.update("update lending_group_consent_challenge set attempts=attempts+1 where id=? and attempts<5 and consumed_at is null",id);}
    public void challengeConsumed(UUID id,OffsetDateTime now){jdbc.update("update lending_group_consent_challenge set consumed_at=? where id=? and consumed_at is null",now,id);}
    public boolean consented(AppUserPrincipal a,UUID cycle){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from lending_group_consent_evidence where cycle_id=? and sacco_id=? and station_id=? and client_id=?)",Boolean.class,cycle,a.getSaccoId(),a.getStationId(),a.getMemberId()));}
    public Terms ownTerms(AppUserPrincipal a,UUID cycle){return jdbc.query("""
        select l.amount,(l.financial_snapshot->>'interestRate')::numeric rate,(l.financial_snapshot->>'interestAmount')::numeric interest,
        l.installment_amount,l.repayment_frequency,l.financial_snapshot->>'tenureUnit' tenure_unit,(l.financial_snapshot->>'tenureValue')::integer tenure_value,
        l.first_repayment_date,c.form_data->>'purpose' purpose,
        (l.financial_snapshot->>'maximumInstallmentAmount')::numeric maximum_instalment,
        (l.financial_snapshot->>'recordedChargeTotal')::numeric charge_total,
        (l.financial_snapshot->>'recordedChargeDeductions')::numeric deductions,
        (l.financial_snapshot->>'recordedNetCashEstimate')::numeric net_cash,
        (l.financial_snapshot->>'recordedTotalPayable')::numeric total_payable,
        l.financial_snapshot->'recordedCharges' charges from lending_group_participant x join loan_applications l on l.id=x.loan_id
        join lending_group_cycle c on c.id=x.cycle_id where x.cycle_id=? and x.sacco_id=? and x.station_id=? and x.client_id=?
        """,(r,n)->new Terms(r.getBigDecimal("amount"),r.getBigDecimal("rate"),r.getBigDecimal("interest"),r.getBigDecimal("installment_amount"),r.getString("repayment_frequency"),r.getString("tenure_unit"),r.getInt("tenure_value"),r.getObject("first_repayment_date",LocalDate.class),r.getString("purpose"),
            r.getBigDecimal("maximum_instalment"),r.getBigDecimal("charge_total"),r.getBigDecimal("deductions"),r.getBigDecimal("net_cash"),r.getBigDecimal("total_payable"),json.readValue(r.getString("charges"),com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Plan.class)),cycle,a.getSaccoId(),a.getStationId(),a.getMemberId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Own agreement unavailable"));}
}
