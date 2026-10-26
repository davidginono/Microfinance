package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.grouplending.dto.GroupSharedLoanDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.PaymentDetailsService.PaymentDetailsView;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class GroupSharedLoanRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public void lock(AppUserPrincipal a,UUID loan){
        jdbc.queryForList("select c.id from lending_group_shared_facility f join lending_group_cycle c on c.id=f.cycle_id where f.loan_id=? and f.sacco_id=? and f.station_id=? for share of c",loan,a.getSaccoId(),a.getStationId());
        jdbc.queryForList("select g.id from lending_group_shared_facility f join lending_group_cycle c on c.id=f.cycle_id join lending_group g on g.id=c.group_id where f.loan_id=? and f.sacco_id=? and f.station_id=? for share of g",loan,a.getSaccoId(),a.getStationId());
    }
    public boolean shared(AppUserPrincipal a,UUID loan){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from lending_group_shared_facility where loan_id=? and sacco_id=? and station_id=?)",Boolean.class,loan,a.getSaccoId(),a.getStationId()));}
    public Optional<Funding> funding(AppUserPrincipal a,UUID loan){
        var rows=jdbc.query("""
            select f.cycle_id,lending_group_shared_funding_packet(f.cycle_id) packet,l.amount,(f.snapshot->>'net')::numeric net,l.first_repayment_date,
              c.state='SUBMITTED' and g.status='ACTIVE' and l.status='READY_FOR_DISBURSEMENT'
              and d.cycle_id is not null and not exists(select 1 from lending_group_shared_allocation s left join lending_group_shared_payout p
                on p.cycle_id=s.cycle_id and p.client_id=s.client_id join members m on m.id=s.client_id where s.cycle_id=f.cycle_id
                and (p.client_id is null or m.sacco_id<>f.sacco_id or m.station_id<>f.station_id or m.status<>'ACTIVE' or not m.is_member)) ready
            from lending_group_shared_facility f join loan_applications l on l.id=f.loan_id join lending_group_cycle c on c.id=f.cycle_id
            join lending_group g on g.id=c.group_id left join lending_group_shared_document d on d.cycle_id=f.cycle_id
            where f.loan_id=? and f.sacco_id=? and f.station_id=?
            """,(r,n)->new Funding(r.getObject("cycle_id",UUID.class),r.getString("packet"),r.getBigDecimal("amount"),r.getBigDecimal("net"),r.getObject("first_repayment_date",LocalDate.class),r.getBoolean("ready"),List.of()),loan,a.getSaccoId(),a.getStationId());
        if(rows.isEmpty())return Optional.empty();var f=rows.getFirst();
        var people=jdbc.query("""
            select s.client_id,s.client_number,s.client_name,s.net,p.destination from lending_group_shared_allocation s
            left join lending_group_shared_payout p on p.cycle_id=s.cycle_id and p.client_id=s.client_id
            where s.cycle_id=? order by s.ordinal limit 50
            """,(r,n)->new Payout(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getBigDecimal(4),r.getString(5)==null?null:json.readValue(r.getString(5),PaymentDetailsView.class)),f.cycle());
        return Optional.of(new Funding(f.cycle(),f.hash(),f.principal(),f.net(),f.firstPaymentDate(),f.ready(),people));
    }
    public void execute(AppUserPrincipal a,Funding f,UUID loan,UUID posting,UUID journal,List<String> references,OffsetDateTime at){
        jdbc.update("""
            insert into lending_group_shared_funding(cycle_id,loan_id,sacco_id,station_id,posting_id,journal_id,packet_hash,actor_id,actor_name,actor_number,signature_text,signed_at)
            select ?,?,?,?,?,?,?,m.id,m.full_name,coalesce(m.staff_no,m.member_no),m.signature_text,? from members m where m.id=? and m.sacco_id=? and m.station_id=?
            """,f.cycle(),loan,a.getSaccoId(),a.getStationId(),posting,journal,f.hash(),at,a.getMemberId(),a.getSaccoId(),a.getStationId());
        var rows=new ArrayList<Object[]>();for(int i=0;i<f.payouts().size();i++){var p=f.payouts().get(i);rows.add(new Object[]{f.cycle(),p.client(),a.getSaccoId(),a.getStationId(),p.amount(),references.get(i).strip()});}
        jdbc.batchUpdate("insert into lending_group_shared_transfer(cycle_id,client_id,sacco_id,station_id,amount,reference) values(?,?,?,?,?,?)",rows);
    }
    public Set<UUID> sharedLoans(AppUserPrincipal a,List<UUID> ids){if(ids.isEmpty())return Set.of();var args=new ArrayList<Object>(List.of(a.getSaccoId(),a.getStationId()));args.addAll(ids);return new LinkedHashSet<>(jdbc.query("select loan_id from lending_group_shared_facility where sacco_id=? and station_id=? and loan_id in ("+placeholders(ids.size())+") order by loan_id limit 50",(r,n)->r.getObject(1,UUID.class),args.toArray()));}
    public List<Balance> balances(AppUserPrincipal a,UUID loan){return balances(a,List.of(loan)).getOrDefault(loan,List.of());}
    public Map<UUID,List<Balance>> balances(AppUserPrincipal a,List<UUID> ids){if(ids.isEmpty())return Map.of();var args=new ArrayList<Object>(List.of(a.getSaccoId(),a.getStationId()));args.addAll(ids);var flags=new HashMap<UUID,Boolean>();jdbc.query("select loan_id,exists(select 1 from loan_ledgers l where l.loan_application_id=f.loan_id) and exists(select 1 from lending_group_shared_funding e where e.loan_id=f.loan_id) and lending_group_shared_waivers_complete(loan_id) and lending_group_shared_adjustments_complete(loan_id) known from lending_group_shared_facility f where sacco_id=? and station_id=? and loan_id in ("+placeholders(ids.size())+") limit 50",(org.springframework.jdbc.core.RowCallbackHandler)r->flags.put(r.getObject("loan_id",UUID.class),r.getBoolean("known")),args.toArray());var result=new LinkedHashMap<UUID,List<Balance>>();jdbc.query("""
        select f.loan_id,s.client_id,s.client_number,s.client_name,s.principal,s.interest,s.charges-s.deductions charges,l.status='DISBURSEMENT_REVERSED' funding_reversed,coalesce(waived.amount,0) waived,coalesce(written.amount,0) written_off,
          coalesce(sum(case when p.kind='REVERSAL' then -x.principal else x.principal end),0) paid_principal,
          coalesce(sum(case when p.kind='REVERSAL' then -x.interest else x.interest end),0) paid_interest,
          coalesce(sum(case when p.kind='REVERSAL' then -x.charges else x.charges end),0) paid_charges,
          coalesce(sum(case when p.kind='REVERSAL' then -x.excluded_interest else x.excluded_interest end),0) excluded
        from lending_group_shared_facility f join loan_applications l on l.id=f.loan_id join lending_group_shared_allocation s on s.cycle_id=f.cycle_id
        left join lateral(select sum(case when w.kind='REVERSAL' then -v.amount else v.amount end) amount from lending_group_shared_waiver v join loan_interest_waiver w on w.id=v.waiver_id where v.cycle_id=s.cycle_id and v.client_id=s.client_id) waived on true
        left join lateral(select sum(case when q.kind='WRITE_BACK' then -v.amount else v.amount end) amount from lending_group_shared_adjustment v join loan_adjustment_request q on q.id=v.request_id where v.cycle_id=s.cycle_id and v.client_id=s.client_id) written on true
        left join lending_group_shared_movement x on x.cycle_id=f.cycle_id and x.client_id=s.client_id
        left join accountant_loan_post p on p.id=x.posting_id
        where f.sacco_id=? and f.station_id=? and f.loan_id in (%s) group by f.loan_id,s.participant_id,l.id,waived.amount,written.amount order by f.loan_id,s.ordinal limit 2500

        """.formatted(placeholders(ids.size())),(org.springframework.jdbc.core.RowCallbackHandler)r->result.computeIfAbsent(r.getObject("loan_id",UUID.class),k->new ArrayList<>()).add(new Balance(r.getObject("client_id",UUID.class),r.getString("client_number"),r.getString("client_name"),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("charges"),r.getBigDecimal("paid_principal"),r.getBigDecimal("paid_interest"),r.getBigDecimal("paid_charges"),r.getBigDecimal("excluded"),r.getBigDecimal("waived"),r.getBigDecimal("written_off"),r.getBoolean("funding_reversed"),flags.getOrDefault(r.getObject("loan_id",UUID.class),false))),args.toArray());return result;}
    public Map<UUID,List<com.sacco.mvp.accounting.dto.VoucherDtos.SharedReceiptBenefit>> receiptBenefits(AppUserPrincipal a,List<UUID> postings){
        if(postings.isEmpty())return Map.of();var args=new ArrayList<Object>(List.of(a.getSaccoId(),a.getStationId()));args.addAll(postings);var result=new LinkedHashMap<UUID,List<com.sacco.mvp.accounting.dto.VoucherDtos.SharedReceiptBenefit>>();
        jdbc.query("""
            select x.posting_id,s.client_id,s.client_number,s.client_name,x.payer,x.principal,x.interest,x.charges,x.excluded_interest
            from lending_group_shared_movement x join lending_group_shared_allocation s on s.cycle_id=x.cycle_id and s.client_id=x.client_id
            where x.sacco_id=? and x.station_id=? and x.posting_id in (%s) order by x.posting_id,s.ordinal limit 2500
            """.formatted(placeholders(postings.size())),(org.springframework.jdbc.core.RowCallbackHandler)r->result.computeIfAbsent(r.getObject("posting_id",UUID.class),k->new ArrayList<>()).add(new com.sacco.mvp.accounting.dto.VoucherDtos.SharedReceiptBenefit(r.getObject("client_id",UUID.class),r.getString("client_number"),r.getString("client_name"),r.getString("payer"),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("charges"),r.getBigDecimal("excluded_interest"))),args.toArray());return result;
    }
    public boolean waiversComplete(AppUserPrincipal a,UUID loan,UUID skip){return jdbc.query("select lending_group_shared_waivers_complete(loan_id,?) from lending_group_shared_facility where loan_id=? and sacco_id=? and station_id=?",(r,n)->r.getBoolean(1),skip,loan,a.getSaccoId(),a.getStationId()).stream().findFirst().orElse(false);}
    public List<WaiverAllocation> waivers(AppUserPrincipal a,UUID source){return jdbc.query("select * from lending_group_shared_waiver where waiver_id=? and sacco_id=? and station_id=? order by client_id limit 50",(r,n)->new WaiverAllocation(source,r.getObject("client_id",UUID.class),r.getBigDecimal("amount"),r.getObject("reverses_waiver_id",UUID.class)),source,a.getSaccoId(),a.getStationId());}
    public void waivers(AppUserPrincipal a,UUID loan,List<WaiverAllocation> rows){jdbc.batchUpdate("insert into lending_group_shared_waiver(waiver_id,cycle_id,client_id,sacco_id,station_id,amount,reverses_waiver_id) select ?,f.cycle_id,?,?,?,?,? from lending_group_shared_facility f where f.loan_id=? and f.sacco_id=? and f.station_id=?",rows.stream().map(r->new Object[]{r.waiver(),r.client(),a.getSaccoId(),a.getStationId(),r.amount(),r.reverses(),loan,a.getSaccoId(),a.getStationId()}).toList());}
    public boolean accountingComplete(AppUserPrincipal a,UUID loan,UUID skip){return jdbc.query("select lending_group_shared_adjustments_complete(loan_id,?) from lending_group_shared_facility where loan_id=? and sacco_id=? and station_id=?",(r,n)->r.getBoolean(1),skip,loan,a.getSaccoId(),a.getStationId()).stream().findFirst().orElse(false);}
    public UUID originalWriteOff(AppUserPrincipal a,UUID loan,UUID journal){return jdbc.query("select q.id from loan_adjustment_request q join loan_adjustment_decision d on d.request_id=q.id where q.loan_id=? and q.kind='WRITE_OFF' and d.journal_id=? and q.sacco_id=? and q.station_id=?",(r,n)->r.getObject(1,UUID.class),loan,journal,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new IllegalArgumentException("recording.error.sharedAttributionHistory"));}
    public List<AccountingAllocation> accounting(AppUserPrincipal a,UUID source){return jdbc.query("select * from lending_group_shared_adjustment where request_id=? and sacco_id=? and station_id=? order by client_id limit 50",(r,n)->new AccountingAllocation(source,r.getObject("client_id",UUID.class),r.getBigDecimal("amount"),r.getObject("reverses_request_id",UUID.class)),source,a.getSaccoId(),a.getStationId());}
    public void accounting(AppUserPrincipal a,UUID loan,List<AccountingAllocation> rows){jdbc.batchUpdate("insert into lending_group_shared_adjustment(request_id,cycle_id,client_id,sacco_id,station_id,amount,reverses_request_id) select ?,f.cycle_id,?,?,?,?,? from lending_group_shared_facility f where f.loan_id=? and f.sacco_id=? and f.station_id=?",rows.stream().map(r->new Object[]{r.request(),r.client(),a.getSaccoId(),a.getStationId(),r.amount(),r.reverses(),loan,a.getSaccoId(),a.getStationId()}).toList());}
    public List<AccountingBenefit> accountingBenefits(AppUserPrincipal a,UUID source){return jdbc.query("select x.cycle_id,x.client_id,s.client_number,s.client_name,x.amount from lending_group_shared_adjustment x join lending_group_shared_allocation s on s.cycle_id=x.cycle_id and s.client_id=x.client_id where x.request_id=? and x.sacco_id=? and x.station_id=? order by s.ordinal limit 50",(r,n)->new AccountingBenefit(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getBigDecimal(5)),source,a.getSaccoId(),a.getStationId());}
    private static String placeholders(int count){if(count<1||count>50)throw new IllegalArgumentException("group.error.request");return String.join(",",Collections.nCopies(count,"?"));}
    public BigDecimal excluded(UUID posting){return jdbc.queryForObject("select coalesce(t.future_interest_excluded,0) from accountant_loan_post p left join loan_repayment_transactions t on t.id=p.transaction_id where p.id=?",BigDecimal.class,posting);}
    public List<Movement> movements(AppUserPrincipal a,UUID posting){return jdbc.query("select * from lending_group_shared_movement where posting_id=? and sacco_id=? and station_id=? order by client_id limit 50",(r,n)->new Movement(posting,r.getObject("client_id",UUID.class),r.getString("payer"),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("charges"),r.getBigDecimal("excluded_interest"),r.getObject("reverses_posting_id",UUID.class)),posting,a.getSaccoId(),a.getStationId());}
    public void movements(AppUserPrincipal a,UUID loan,List<Movement> rows){jdbc.batchUpdate("""
        insert into lending_group_shared_movement(posting_id,cycle_id,client_id,sacco_id,station_id,payer,principal,interest,charges,excluded_interest,reverses_posting_id)
        select ?,f.cycle_id,?,?,?,?,?,?,?,?,? from lending_group_shared_facility f where f.loan_id=? and f.sacco_id=? and f.station_id=?
        """,rows.stream().map(r->new Object[]{r.posting(),r.client(),a.getSaccoId(),a.getStationId(),r.payer(),r.principal(),r.interest(),r.charges(),r.excluded(),r.reverses(),loan,a.getSaccoId(),a.getStationId()}).toList());}
}
