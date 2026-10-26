package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.grouplending.dto.GroupCycleStatementDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class GroupCycleStatementRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String SOURCE="""
        with cycle_scope as materialized(select c.id,c.sacco_id,c.station_id,c.model from lending_group_cycle c where c.id=:cycle and c.sacco_id=:institution and c.station_id=:branch),
        linked as materialized(
          select p.loan_id from cycle_scope c join lending_group_participant p on p.cycle_id=c.id and p.sacco_id=c.sacco_id and p.station_id=c.station_id where c.model='INDIVIDUAL' and p.loan_id is not null
          union select f.loan_id from cycle_scope c join lending_group_shared_facility f on f.cycle_id=c.id and f.sacco_id=c.sacco_id and f.station_id=c.station_id where c.model='SHARED'
          union select f.loan_id from cycle_scope c join lending_group_facility f on f.cycle_id=c.id and f.sacco_id=c.sacco_id and f.station_id=c.station_id where c.model='ORGANIZATION'),
        loans as materialized(select a.id,a.loan_id,a.application_number,a.borrower_party_id,a.sacco_id,a.station_id,a.status,a.amount from linked x join loan_applications a on a.id=x.loan_id and a.sacco_id=:institution and a.station_id=:branch)
        """;
    private MapSqlParameterSource parameters(AppUserPrincipal a,UUID cycle){return new MapSqlParameterSource("institution",a.getSaccoId()).addValue("branch",a.getStationId()).addValue("cycle",cycle);}
    public Context context(AppUserPrincipal a,UUID cycle){return jdbc.query("""
        select c.id,c.group_id,c.model,c.state,g.code,g.name,i.sacco_name,coalesce(nullif(b.address_location,''),b.station_id) branch
        from lending_group_cycle c join lending_group g on g.id=c.group_id and g.sacco_id=c.sacco_id and g.station_id=c.station_id
        join registered_saccos i on i.sacco_id=c.sacco_id join sacco_stations b on b.sacco_id=c.sacco_id and b.station_id=c.station_id
        where c.id=:cycle and c.sacco_id=:institution and c.station_id=:branch
        """,parameters(a,cycle),(r,n)->new Context(cycle,r.getObject("group_id",UUID.class),r.getString("sacco_name"),r.getString("branch"),r.getString("code"),r.getString("name"),r.getString("model"),r.getString("state"))).stream().findFirst().orElseThrow(()->new AccessDeniedException("Cycle statement unavailable"));}
    public List<Loan> loans(AppUserPrincipal a,UUID cycle,LocalDate today){return jdbc.query(SOURCE+"""
        select a.id,coalesce(a.loan_id,a.application_number::text) number,coalesce(b.member_no,'') borrower_number,coalesce(b.full_name,'') borrower,a.status,a.amount,
          (l.loan_application_id is not null or a.status in ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF','PAID','DISBURSEMENT_REVERSED')) funded,
          l.loan_application_id is not null and d.rows>0 and b.party_id is not null and exists(select 1 from accountant_loan_post p where p.loan_id=a.id and p.sacco_id=:institution and p.station_id=:branch and p.kind='DISBURSEMENT' and p.state='POSTED')
            and (b.kind<>'ORGANIZATION' or exists(select 1 from lending_group_org_funding f where f.loan_id=a.id and f.sacco_id=:institution and f.station_id=:branch))
            and (b.kind<>'JOINT_GROUP' or exists(select 1 from lending_group_shared_funding f where f.loan_id=a.id and f.sacco_id=:institution and f.station_id=:branch)) known,
          case when a.status='DISBURSEMENT_REVERSED' then 0 else l.principal-l.principal_paid end principal,
          case when closed.value then 0 else d.interest end due_interest,case when closed.value then 0 else d.future end future_interest,
          case when a.status='DISBURSEMENT_REVERSED' then 0 else coalesce(charges.outstanding,0) end charges,
          case when closed.value then 0 else d.arrears end arrears,case when closed.value then null else d.next_due end next_due
        from loans a left join loan_borrower_directory b on b.party_id=a.borrower_party_id and b.sacco_id=a.sacco_id
        left join loan_ledgers l on l.loan_application_id=a.id and l.sacco_id=a.sacco_id and l.station_id=a.station_id
        left join lateral(select count(*) rows,sum(loan_interest_due(id,:today)) interest,
          sum(greatest(0,interest-interest_paid-interest_excluded-interest_waived-loan_interest_due(id,:today))) future,
          sum(case when due_date<:today then principal-principal_paid+interest-interest_paid-interest_excluded-interest_waived else 0 end) arrears,
          min(due_date) filter(where due_date>=:today and principal-principal_paid+interest-interest_paid-interest_excluded-interest_waived>0) next_due
          from loan_ledger_installments where loan_application_id=a.id) d on true
        left join lateral(select outstanding from loan_charge_balance where loan_id=a.id and sacco_id=:institution and station_id=:branch offset 0) charges on true
        cross join lateral(select a.status='DISBURSEMENT_REVERSED' or exists(select 1 from accountant_loan_topup t where t.source_id=a.id and t.reverses_id is null and not exists(select 1 from accountant_loan_topup r where r.reverses_id=t.id)) value) closed
        order by a.loan_id,a.id limit 51
        """,parameters(a,cycle).addValue("today",today),(r,n)->new Loan(r.getObject("id",UUID.class),r.getString("number"),r.getString("borrower_number"),r.getString("borrower"),r.getString("status"),r.getBigDecimal("amount"),r.getBigDecimal("principal"),r.getBigDecimal("due_interest"),r.getBigDecimal("future_interest"),r.getBigDecimal("charges"),r.getBigDecimal("arrears"),r.getObject("next_due",LocalDate.class),r.getBoolean("funded"),r.getBoolean("known")));}
    public List<Movement> movements(AppUserPrincipal a,UUID cycle,LocalDate from,LocalDate through,int limit,int offset){return jdbc.query(SOURCE+"""
        , events as(
          select p.id,p.loan_id,a.loan_id loan_number,coalesce(r.id,g.id,case when p.kind in ('PAYMENT','REVERSAL') then p.id end) receipt_id,coalesce(r.number,g.number,t.receipt_reference,p.reference) reference,p.effective_date date,p.kind,
            coalesce(nullif(r.payer_name,''),g.remitter,(select min(x.payer) from lending_group_shared_movement x where x.posting_id=p.id and x.sacco_id=:institution and x.station_id=:branch),'') payer,
            case when p.kind in ('CHARGE_ASSESS','CHARGE_UNDO','SETTLEMENT','SETTLEMENT_UNDO') then 0 else coalesce((select cash from accountant_loan_topup where id=p.id),p.amount-case when p.kind in ('DISBURSEMENT','FUNDING_UNDO') then coalesce((select sum(amount) from loan_charge_event where journal_id=p.journal_id and kind in ('DEDUCT','DEDUCT_UNDO')),0) else 0 end) end cash,
            p.principal_amount principal,p.interest_amount interest,coalesce(actual.charge_amount,0) charges,coalesce(t.future_interest_excluded,0) excluded,
            case when p.kind in ('CHARGE_ASSESS','CHARGE_UNDO','SETTLEMENT','SETTLEMENT_UNDO') then p.amount else 0 end non_cash,coalesce(original.reference,'') reverses,p.recorded_at
          from accountant_loan_movement p join loans a on a.id=p.loan_id
          left join accountant_loan_post actual on actual.id=p.id and actual.sacco_id=:institution and actual.station_id=:branch
          left join loan_repayment_transactions t on t.id=p.transaction_id and t.loan_application_id=p.loan_id and t.sacco_id=:institution and t.station_id=:branch
          left join accountant_loan_receipt_row rr on rr.posting_id=p.id and rr.sacco_id=:institution and rr.station_id=:branch
          left join accountant_loan_receipt r on r.id=rr.receipt_id and r.sacco_id=:institution and r.station_id=:branch
          left join lending_group_receipt_loan gr on gr.posting_id=p.id and gr.sacco_id=:institution and gr.station_id=:branch
          left join lending_group_receipt g on g.id=gr.receipt_id and g.sacco_id=:institution and g.station_id=:branch
          left join accountant_loan_movement original on original.id=p.reverses_id and original.loan_id=p.loan_id and original.sacco_id=:institution and original.station_id=:branch
          where p.sacco_id=:institution and p.station_id=:branch and p.state='POSTED'
          union all
          select w.id,w.loan_id,a.loan_id,null::uuid,'W-'||w.sequence,w.effective_date,case when w.kind='REVERSAL' then 'WAIVER_REVERSAL' else 'INTEREST_WAIVER' end,'',0,0,0,0,0,w.amount,coalesce('W-'||original.sequence,''),w.created_at
          from loan_interest_waiver w join loans a on a.id=w.loan_id left join loan_interest_waiver original on original.id=w.reverses_id and original.loan_id=w.loan_id
          where w.sacco_id=:institution and w.station_id=:branch
          union all
          select q.id,q.loan_id,a.loan_id,null::uuid,'A-'||d.event_sequence,q.effective_date,q.kind,'',0,0,0,0,0,case when q.kind='PROVISION' then q.allowance_change else q.amount end,coalesce('A-'||previous.event_sequence,''),d.recorded_at
          from loan_adjustment_request q join loans a on a.id=q.loan_id join loan_adjustment_decision d on d.request_id=q.id and d.decision='APPROVED' and d.sacco_id=:institution and d.station_id=:branch
          join gl_journal j on j.id=d.journal_id and j.sacco_id=:institution and j.station_id=:branch and j.state='POSTED'
          left join loan_adjustment_decision previous on previous.journal_id=q.reverses_journal_id and previous.decision='APPROVED' and previous.sacco_id=:institution and previous.station_id=:branch
          where q.sacco_id=:institution and q.station_id=:branch and q.kind in ('PROVISION','WRITE_OFF','WRITE_BACK')
        ) select * from events where (:from::date is null or date>=:from) and date<=:through order by date desc,recorded_at desc,id desc limit :limit offset :offset
        """,parameters(a,cycle).addValue("from",from).addValue("through",through).addValue("limit",limit).addValue("offset",offset),(r,n)->new Movement(r.getObject("id",UUID.class),r.getObject("loan_id",UUID.class),r.getString("loan_number"),r.getObject("receipt_id",UUID.class),r.getString("reference"),r.getObject("date",LocalDate.class),r.getString("kind"),r.getString("payer"),r.getBigDecimal("cash"),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("charges"),r.getBigDecimal("excluded"),r.getBigDecimal("non_cash"),r.getString("reverses")));}
    public List<Contribution> contributions(AppUserPrincipal a,UUID cycle,LocalDate from,LocalDate through,int limit,int offset){return jdbc.query(SOURCE+"""
        select c.id,c.receipt_id,r.number,a.loan_id,c.payer_number||' - '||c.payer_name payer,c.beneficiary_number||' - '||c.beneficiary_name beneficiary,
          r.effective_date,c.amount,c.principal,c.interest,c.charges,r.reverses_id is not null reversal
        from lending_group_contribution c join lending_group_receipt r on r.id=c.receipt_id and r.sacco_id=:institution and r.station_id=:branch and r.cycle_id=:cycle
        join loans a on a.id=c.loan_id where c.sacco_id=:institution and c.station_id=:branch
          and (:from::date is null or r.effective_date>=:from) and r.effective_date<=:through
        order by r.effective_date desc,r.posted_at desc,r.id desc,c.line_no,c.id limit :limit offset :offset
        """,parameters(a,cycle).addValue("from",from).addValue("through",through).addValue("limit",limit).addValue("offset",offset),(r,n)->new Contribution(r.getObject("id",UUID.class),r.getObject("receipt_id",UUID.class),r.getString("number"),r.getString("loan_id"),r.getString("payer"),r.getString("beneficiary"),r.getObject("effective_date",LocalDate.class),r.getBigDecimal("amount"),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("charges"),r.getBoolean("reversal")));}
}
