package com.sacco.mvp.accounting.loans.repository;

import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Repository @RequiredArgsConstructor
public class LoanPostingAuthorizationRepository {
    private final JdbcTemplate jdbc;
    public boolean recordedLoan(UUID loan) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from accountant_loan_record where loan_id=?)",Boolean.class,loan));
    }
    public boolean recordedCommand(UUID loan, AppUserPrincipal actor, UUID key, String kind, String reference, BigDecimal amount, LocalDate date) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from accountant_loan_post p join accountant_loan_record r on r.loan_id=p.loan_id
            where p.loan_id=? and p.sacco_id=? and p.station_id=? and p.actor_id=? and p.request_key=? and p.kind=?
            and p.reference=? and p.amount-coalesce((select sum(amount) from loan_charge_event where posting_id=p.id and kind='COLLECT'),0)=? and p.effective_date=? and p.state='POSTING')
            """,Boolean.class,loan,actor.getSaccoId(),actor.getStationId(),actor.getMemberId(),key,kind,reference,amount,date));
    }
    public boolean recordedReversal(UUID loan, AppUserPrincipal actor, UUID key, LocalDate date, UUID original) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from accountant_loan_post p join accountant_loan_post o on o.id=p.reverses_id
            where p.loan_id=? and p.sacco_id=? and p.station_id=? and p.actor_id=? and p.request_key=? and p.kind='REVERSAL'
            and (?::date is null or p.effective_date=?::date) and (?::uuid is null or o.transaction_id=?::uuid) and p.state='POSTING')
            """,Boolean.class,loan,actor.getSaccoId(),actor.getStationId(),actor.getMemberId(),key,date,date,original,original));
    }

    public boolean retainedSourceOwnsLoan(UUID loan) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from accounting_archive.accounting_business_document where loan_id=? and state<>'REJECTED')
              or exists(select 1 from accounting_archive.gl_operational_bridge b join loan_journal_entries e
                on e.voucher_id=b.voucher_id where e.loan_application_id=?)
            """, Boolean.class, loan, loan));
    }
}
