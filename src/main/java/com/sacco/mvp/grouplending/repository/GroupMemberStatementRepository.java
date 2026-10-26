package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.grouplending.dto.GroupMemberStatementDtos.*;
import com.sacco.mvp.grouplending.dto.GroupSharedLoanDtos.Balance;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Repository @RequiredArgsConstructor
public class GroupMemberStatementRepository {
    private final JdbcTemplate jdbc;
    // Keep charge aggregation parameterized by this loan rather than grouping every institution.
    public Source source(AppUserPrincipal a,UUID cycle,UUID client,LocalDate asOf){return jdbc.query("""
        select f.cycle_id,f.loan_id,l.loan_id loan_number,i.sacco_name,coalesce(nullif(b.address_location,''),f.station_id) branch_name,g.code,g.name,
          l.status,f.liability_rule,s.*,s.charges-s.deductions collectible_charges,coalesce(paid.principal,0) paid_principal,
          coalesce(paid.interest,0) paid_interest,coalesce(waived.amount,0) waived,coalesce(written.amount,0) written_off,coalesce(paid.charges,0) paid_charges,coalesce(paid.excluded,0) excluded,
          (execution.loan_id is not null or l.status in ('DISBURSED','PAR','DEFAULTED','WRITTEN_OFF','PAID','DISBURSEMENT_REVERSED')) funded,
          case when l.status='DISBURSEMENT_REVERSED' then 0 else schedule.due_principal end due_principal,
          case when l.status='DISBURSEMENT_REVERSED' then 0 else schedule.due_interest end due_interest,
          case when l.status='DISBURSEMENT_REVERSED' then 0 else schedule.arrears end arrears,
          case when l.status='DISBURSEMENT_REVERSED' then 0 else coalesce(charges.outstanding,0) end loan_charges,
          execution.loan_id is not null and ledger.loan_application_id is not null and coalesce(schedule.rows,0)>0 and lending_group_shared_waivers_complete(f.loan_id) and lending_group_shared_adjustments_complete(f.loan_id)
            and s.principal>=coalesce(paid.principal,0) and s.interest>=coalesce(paid.interest,0)+coalesce(paid.excluded,0)+coalesce(waived.amount,0)
            and s.charges-s.deductions>=coalesce(paid.charges,0) known,
          case when l.status='DISBURSEMENT_REVERSED' then 0
            when f.liability_rule='JOINT_AND_SEVERAL' then least(s.liability_cap,greatest(0,l.amount-ledger.principal_paid)+coalesce(schedule.interest,0)+coalesce(charges.outstanding,0))
            else s.principal-coalesce(paid.principal,0)+s.interest-coalesce(paid.interest,0)-coalesce(paid.excluded,0)-coalesce(waived.amount,0)+s.charges-s.deductions-coalesce(paid.charges,0) end maximum_liability
        from lending_group_shared_facility f join loan_applications l on l.id=f.loan_id and l.sacco_id=f.sacco_id and l.station_id=f.station_id
        join lending_group_shared_allocation s on s.cycle_id=f.cycle_id and s.client_id=? join lending_group_cycle c on c.id=f.cycle_id
        join lending_group g on g.id=c.group_id join registered_saccos i on i.sacco_id=f.sacco_id
        join sacco_stations b on b.sacco_id=f.sacco_id and b.station_id=f.station_id
        left join lending_group_shared_funding execution on execution.loan_id=f.loan_id
        left join loan_ledgers ledger on ledger.loan_application_id=f.loan_id left join lateral(select outstanding from loan_charge_balance where loan_id=f.loan_id and sacco_id=f.sacco_id and station_id=f.station_id offset 0) charges on true
        left join lateral(select sum(case when p.kind='REVERSAL' then -x.principal else x.principal end) principal,
          sum(case when p.kind='REVERSAL' then -x.interest else x.interest end) interest,
          sum(case when p.kind='REVERSAL' then -x.charges else x.charges end) charges,
          sum(case when p.kind='REVERSAL' then -x.excluded_interest else x.excluded_interest end) excluded
          from lending_group_shared_movement x join accountant_loan_post p on p.id=x.posting_id
          where x.cycle_id=f.cycle_id and x.client_id=s.client_id) paid on true
        left join lateral(select sum(case when w.kind='REVERSAL' then -v.amount else v.amount end) amount from lending_group_shared_waiver v join loan_interest_waiver w on w.id=v.waiver_id where v.cycle_id=f.cycle_id and v.client_id=s.client_id) waived on true
        left join lateral(select sum(case when q.kind='WRITE_BACK' then -v.amount else v.amount end) amount from lending_group_shared_adjustment v join loan_adjustment_request q on q.id=v.request_id where v.cycle_id=f.cycle_id and v.client_id=s.client_id) written on true
        left join lateral(select count(*) rows,sum(interest-interest_paid-interest_excluded-interest_waived) interest,sum(interest_waived) waived,
          sum(case when due_date<=? then principal-principal_paid else 0 end) due_principal,
          sum(loan_interest_due(id,?)) due_interest,
          sum(case when due_date<? then principal-principal_paid+interest-interest_paid-interest_excluded-interest_waived else 0 end) arrears
          from loan_ledger_installments where loan_application_id=f.loan_id) schedule on true
        where f.cycle_id=? and f.sacco_id=? and f.station_id=?
        """,(r,n)->{boolean known=r.getBoolean("known");return new Source(cycle,r.getObject("loan_id",UUID.class),r.getString("loan_number"),r.getString("sacco_name"),r.getString("branch_name"),r.getString("code"),r.getString("name"),r.getString("status"),r.getString("liability_rule"),r.getBigDecimal("liability_cap"),r.getBigDecimal("deductions"),r.getBigDecimal("net"),known?r.getBigDecimal("maximum_liability"):null,new Balance(client,r.getString("client_number"),r.getString("client_name"),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("collectible_charges"),r.getBigDecimal("paid_principal"),r.getBigDecimal("paid_interest"),r.getBigDecimal("paid_charges"),r.getBigDecimal("excluded"),r.getBigDecimal("waived"),r.getBigDecimal("written_off"),r.getString("status").equals("DISBURSEMENT_REVERSED"),known),r.getBigDecimal("due_principal"),r.getBigDecimal("due_interest"),r.getBigDecimal("arrears"),r.getBigDecimal("loan_charges"),r.getBoolean("funded"),known);},client,asOf,asOf,asOf,cycle,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Personal shared statement unavailable"));}
    public List<Due> schedule(AppUserPrincipal a,UUID cycle,UUID client,int limit,int offset){return jdbc.query("""
        select (d->>'number')::integer number,(d->>'date')::date date,(d->>'principal')::numeric principal,(d->>'interest')::numeric interest
        from lending_group_shared_facility f cross join lateral jsonb_array_elements(f.snapshot->'dueShares') d
        where f.cycle_id=? and f.sacco_id=? and f.station_id=? and d->>'clientId'=? order by (d->>'number')::integer limit ? offset ?
        """,(r,n)->new Due(r.getInt("number"),r.getObject("date",LocalDate.class),r.getBigDecimal("principal"),r.getBigDecimal("interest")),cycle,a.getSaccoId(),a.getStationId(),client.toString(),limit,offset);}
    public List<Credit> credits(AppUserPrincipal a,UUID cycle,UUID client,LocalDate from,LocalDate through,int limit,int offset){return jdbc.query("""
        with activity as(
         select p.id,coalesce(r.id,g.id,p.id) receipt_id,coalesce(r.number,g.number,t.receipt_reference,p.reference) number,p.effective_date,p.kind,x.payer,
          x.principal,x.interest,x.charges,x.excluded_interest,0::numeric waived_interest,0::numeric written_off,
          coalesce(original_r.number,original_g.number,original_t.receipt_reference,original.reference,'') reverses,
          coalesce(reversed_r.number,reversed_g.number,reversed_t.receipt_reference,reversed.reference,'') reversed_by,p.recorded_at
         from lending_group_shared_movement x join lending_group_shared_facility f on f.cycle_id=x.cycle_id
         join accountant_loan_post p on p.id=x.posting_id
         left join loan_repayment_transactions t on t.id=p.transaction_id
         left join accountant_loan_receipt_row rr on rr.posting_id=p.id left join accountant_loan_receipt r on r.id=rr.receipt_id
         left join lending_group_receipt_loan gr on gr.posting_id=p.id left join lending_group_receipt g on g.id=gr.receipt_id
         left join accountant_loan_post original on original.id=p.reverses_id left join loan_repayment_transactions original_t on original_t.id=original.transaction_id
         left join accountant_loan_receipt_row orr on orr.posting_id=original.id left join accountant_loan_receipt original_r on original_r.id=orr.receipt_id
         left join lending_group_receipt_loan ogr on ogr.posting_id=original.id left join lending_group_receipt original_g on original_g.id=ogr.receipt_id
         left join accountant_loan_post reversed on reversed.reverses_id=p.id left join loan_repayment_transactions reversed_t on reversed_t.id=reversed.transaction_id
         left join accountant_loan_receipt_row rrr on rrr.posting_id=reversed.id left join accountant_loan_receipt reversed_r on reversed_r.id=rrr.receipt_id
         left join lending_group_receipt_loan rgr on rgr.posting_id=reversed.id left join lending_group_receipt reversed_g on reversed_g.id=rgr.receipt_id
         where x.cycle_id=? and x.client_id=? and x.sacco_id=? and x.station_id=? and f.sacco_id=x.sacco_id and f.station_id=x.station_id
           and (x.principal+x.interest+x.charges+x.excluded_interest)>0
         union all
         select w.id,null::uuid,'W-'||w.sequence,w.effective_date,case when w.kind='REVERSAL' then 'WAIVER_REVERSAL' else 'INTEREST_WAIVER' end,'',
          0::numeric,0::numeric,0::numeric,0::numeric,x.amount,0::numeric,
          case when original.id is null then '' else 'W-'||original.sequence end,
          case when reversed.id is null then '' else 'W-'||reversed.sequence end,w.created_at
         from lending_group_shared_waiver x join lending_group_shared_facility f on f.cycle_id=x.cycle_id
         join loan_interest_waiver w on w.id=x.waiver_id and w.loan_id=f.loan_id
         left join loan_interest_waiver original on original.id=w.reverses_id left join loan_interest_waiver reversed on reversed.reverses_id=w.id
         where x.cycle_id=? and x.client_id=? and x.sacco_id=? and x.station_id=? and f.sacco_id=x.sacco_id and f.station_id=x.station_id and x.amount>0
         union all
         select q.id,null::uuid,'A-'||d.event_sequence,q.effective_date,q.kind,'',0::numeric,0::numeric,0::numeric,0::numeric,0::numeric,x.amount,
          case when original.request_id is null then '' else 'A-'||original.event_sequence end,
          case when reversed.request_id is null then '' else 'A-'||reversed.event_sequence end,d.recorded_at
         from lending_group_shared_adjustment x join lending_group_shared_facility f on f.cycle_id=x.cycle_id
         join loan_adjustment_request q on q.id=x.request_id and q.loan_id=f.loan_id
         join loan_adjustment_decision d on d.request_id=q.id and d.decision='APPROVED' join gl_journal j on j.id=d.journal_id and j.state='POSTED'
         left join loan_adjustment_decision original on original.journal_id=q.reverses_journal_id
         left join lateral(select rd.request_id,rd.event_sequence from loan_adjustment_request b join loan_adjustment_decision rd on rd.request_id=b.id and rd.decision='APPROVED' join gl_journal rj on rj.id=rd.journal_id and rj.state='POSTED' where b.reverses_journal_id=d.journal_id and b.kind='WRITE_BACK' and b.loan_id=q.loan_id order by rd.event_sequence limit 1) reversed on true
         where x.cycle_id=? and x.client_id=? and x.sacco_id=? and x.station_id=? and f.sacco_id=x.sacco_id and f.station_id=x.station_id and x.amount>0
        ) select * from activity where (?::date is null or effective_date>=?::date) and effective_date<=?
        order by effective_date desc,recorded_at desc,id desc limit ? offset ?
        """,(r,n)->new Credit(r.getObject("id",UUID.class),r.getObject("receipt_id",UUID.class),r.getString("number"),r.getObject("effective_date",LocalDate.class),r.getString("kind"),r.getString("payer"),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("charges"),r.getBigDecimal("excluded_interest"),r.getBigDecimal("waived_interest"),r.getBigDecimal("written_off"),r.getString("reverses"),r.getString("reversed_by")),cycle,client,a.getSaccoId(),a.getStationId(),cycle,client,a.getSaccoId(),a.getStationId(),cycle,client,a.getSaccoId(),a.getStationId(),from,from,through,limit,offset);}
}
