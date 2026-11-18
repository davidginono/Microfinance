package com.sacco.mvp.accounting.loans.repository;

import com.sacco.mvp.accounting.loans.dto.LoanCancellationDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.*;

@Repository @RequiredArgsConstructor
public class LoanCancellationRepository {
    private final JdbcTemplate jdbc;

    public void lockRequest(AppUserPrincipal actor,UUID key) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","LOAN_ADJUSTMENT/"+actor.getSaccoId()+"/"+actor.getStationId()+"/"+key);
    }

    public View view(AppUserPrincipal actor,UUID id) {
        return jdbc.query("""
            select l.id,coalesce(l.loan_id,l.application_number::text) number,b.full_name,l.status,loan_group_terms(l) terms,l.amount,
              exists(select 1 from loan_ledgers where loan_application_id=l.id) funded,loan_cancellation_error(l.id) blocked,
              q.id adjustment_id,q.state adjustment_state,w.reason,u.full_name cancelled_by,w.recorded_at
            from loan_applications l join accountant_loan_record r on r.loan_id=l.id and r.sacco_id=l.sacco_id and r.station_id=l.station_id
            join loan_borrower_directory b on b.party_id=l.borrower_party_id and b.sacco_id=l.sacco_id
            left join staff_group_withdrawal w on w.loan_id=l.id left join members u on u.id=w.actor_id
            left join lateral(select q.id,coalesce(d.decision,'PENDING') state from loan_adjustment_request q
              left join loan_adjustment_decision d on d.request_id=q.id where q.loan_id=l.id and q.kind='DISBURSEMENT_REVERSAL'
              order by (d.decision is null) desc,(d.decision='APPROVED') desc,q.recorded_at desc,q.id desc limit 1) q on true
            where l.id=? and l.sacco_id=? and l.station_id=?
            """,(r,n)->new View(r.getObject("id",UUID.class),r.getString("number"),r.getString("full_name"),r.getString("status"),
                r.getString("terms"),r.getBigDecimal("amount"),r.getBoolean("funded"),r.getString("blocked"),
                r.getObject("adjustment_id",UUID.class),r.getString("adjustment_state"),r.getString("reason"),
                r.getString("cancelled_by"),r.getObject("recorded_at",OffsetDateTime.class)),
            id,actor.getSaccoId(),actor.getStationId()).stream().findFirst().orElseThrow(()->new AccessDeniedException("Loan unavailable"));
    }

    public void lock(AppUserPrincipal actor,UUID id) {
        // Match funding/receipt/adjustment order; review decisions lock the loan row.
        if(jdbc.queryForList("select loan_id from accountant_loan_record where loan_id=? and sacco_id=? and station_id=? for update",
            id,actor.getSaccoId(),actor.getStationId()).isEmpty())throw new AccessDeniedException("Loan unavailable");
        jdbc.queryForList("select loan_application_id from loan_ledgers where loan_application_id=? for update",id);
        jdbc.queryForList("select id from loan_applications where id=? and sacco_id=? and station_id=? for update",id,actor.getSaccoId(),actor.getStationId());
    }

    public boolean withdrawalRetry(AppUserPrincipal actor,UUID id,Form form) {
        var rows=jdbc.queryForList("select * from staff_group_withdrawal where sacco_id=? and station_id=? and (loan_id=? or request_key=?)",
            actor.getSaccoId(),actor.getStationId(),id,form.getRequestKey());
        if(rows.isEmpty())return false;
        if(rows.size()!=1)throw new IllegalArgumentException("cancellation.error.retry");
        var row=rows.getFirst();
        if(!id.equals(row.get("loan_id")) || !form.getRequestKey().equals(row.get("request_key")) || !actor.getMemberId().equals(row.get("actor_id"))
            || !form.getState().equals(row.get("previous_status")) || !form.getTerms().equals(row.get("terms_hash")) || !form.getReason().strip().equals(row.get("reason")))
            throw new IllegalArgumentException("cancellation.error.retry");
        return true;
    }

    public boolean adjustmentRetry(AppUserPrincipal actor,UUID key) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from loan_adjustment_request where sacco_id=? and station_id=? and request_key=?)",
            Boolean.class,actor.getSaccoId(),actor.getStationId(),key));
    }
}
