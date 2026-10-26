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
    public List<Balance> balances(AppUserPrincipal a,UUID loan){return jdbc.query("""
        select s.client_id,s.client_number,s.client_name,s.principal,s.interest,s.charges-s.deductions charges,l.status='DISBURSEMENT_REVERSED' funding_reversed,
          coalesce(sum(case when p.kind='REVERSAL' then -x.principal else x.principal end),0) paid_principal,
          coalesce(sum(case when p.kind='REVERSAL' then -x.interest else x.interest end),0) paid_interest,
          coalesce(sum(case when p.kind='REVERSAL' then -x.charges else x.charges end),0) paid_charges,
          coalesce(sum(case when p.kind='REVERSAL' then -x.excluded_interest else x.excluded_interest end),0) excluded
        from lending_group_shared_facility f join loan_applications l on l.id=f.loan_id join lending_group_shared_allocation s on s.cycle_id=f.cycle_id
        left join lending_group_shared_movement x on x.cycle_id=f.cycle_id and x.client_id=s.client_id
        left join accountant_loan_post p on p.id=x.posting_id
        where f.loan_id=? and f.sacco_id=? and f.station_id=? group by s.participant_id,l.id order by s.ordinal limit 50
        """,(r,n)->new Balance(r.getObject("client_id",UUID.class),r.getString("client_number"),r.getString("client_name"),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("charges"),r.getBigDecimal("paid_principal"),r.getBigDecimal("paid_interest"),r.getBigDecimal("paid_charges"),r.getBigDecimal("excluded"),r.getBoolean("funding_reversed")),loan,a.getSaccoId(),a.getStationId());}
    public BigDecimal excluded(UUID posting){return jdbc.queryForObject("select coalesce(t.future_interest_excluded,0) from accountant_loan_post p left join loan_repayment_transactions t on t.id=p.transaction_id where p.id=?",BigDecimal.class,posting);}
    public List<Movement> movements(AppUserPrincipal a,UUID posting){return jdbc.query("select * from lending_group_shared_movement where posting_id=? and sacco_id=? and station_id=? order by client_id limit 50",(r,n)->new Movement(posting,r.getObject("client_id",UUID.class),r.getString("payer"),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("charges"),r.getBigDecimal("excluded_interest"),r.getObject("reverses_posting_id",UUID.class)),posting,a.getSaccoId(),a.getStationId());}
    public void movements(AppUserPrincipal a,UUID loan,List<Movement> rows){jdbc.batchUpdate("""
        insert into lending_group_shared_movement(posting_id,cycle_id,client_id,sacco_id,station_id,payer,principal,interest,charges,excluded_interest,reverses_posting_id)
        select ?,f.cycle_id,?,?,?,?,?,?,?,?,? from lending_group_shared_facility f where f.loan_id=? and f.sacco_id=? and f.station_id=?
        """,rows.stream().map(r->new Object[]{r.posting(),r.client(),a.getSaccoId(),a.getStationId(),r.payer(),r.principal(),r.interest(),r.charges(),r.excluded(),r.reverses(),loan,a.getSaccoId(),a.getStationId()}).toList());}
}
