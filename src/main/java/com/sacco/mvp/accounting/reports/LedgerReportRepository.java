package com.sacco.mvp.accounting.reports;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import static com.sacco.mvp.accounting.reports.LedgerReportService.*;

@Repository
@RequiredArgsConstructor
public class LedgerReportRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public record PolicyLineage(UUID id,int version,LocalDate openingDate,String ledger) {}
    /** Select the policy actually approved at the recorded cutoff, including external-ledger decisions. */
    public Optional<PolicyLineage> policyAt(Scope scope,Parameters p) {
        return jdbc.query("""
            SELECT p.id,p.policy_version,p.opening_date,p.authoritative_ledger
            FROM accounting_policies p JOIN accounting_policy_approvals a
              ON (a.policy_id,a.sacco_id,a.policy_version,a.effective_from)=(p.id,p.sacco_id,p.policy_version,p.effective_from)
            WHERE p.sacco_id=:institution AND p.effective_from<=:through AND a.decision='APPROVED'
              AND a.decided_at<=:cutoff AND p.created_at<=:cutoff
            ORDER BY p.effective_from DESC,p.policy_version DESC LIMIT 1
            """,parameters(scope,p),(r,n)->new PolicyLineage(r.getObject("id",UUID.class),r.getInt("policy_version"),r.getObject("opening_date",LocalDate.class),r.getString("authoritative_ledger"))).stream().findFirst();
    }
    private static final String BALANCES="""
        WITH movements AS (
          SELECT l.account_id,
            SUM(CASE WHEN j.source_type='OPENING' OR j.effective_date<:from THEN l.debit-l.credit ELSE 0 END) opening,
            SUM(CASE WHEN j.source_type<>'OPENING' AND j.effective_date>=:from THEN l.debit ELSE 0 END) debit,
            SUM(CASE WHEN j.source_type<>'OPENING' AND j.effective_date>=:from THEN l.credit ELSE 0 END) credit
          FROM gl_journal j JOIN gl_journal_line l ON (l.journal_id,l.sacco_id,l.station_id)=(j.id,j.sacco_id,j.station_id)
          WHERE j.sacco_id=:institution AND (:wide OR j.station_id=:branch) AND j.state='POSTED'
            AND j.effective_date<=:through AND j.posted_at<=:cutoff AND j.recorded_at<=:cutoff
          GROUP BY l.account_id
        ), balances AS (
          SELECT a.id,a.code,a.name,a.type,a.purpose,COALESCE(m.opening,0) opening,
            COALESCE(m.debit,0) debit,COALESCE(m.credit,0) credit,
            COALESCE(m.opening,0)+COALESCE(m.debit,0)-COALESCE(m.credit,0) closing
          FROM gl_account a LEFT JOIN movements m ON m.account_id=a.id
          WHERE a.sacco_id=:institution AND a.kind<>'HEADING' AND a.created_at<=:cutoff
        )
        """;
    private MapSqlParameterSource parameters(Scope scope,Parameters p) {
        return new MapSqlParameterSource().addValue("institution",scope.institution()).addValue("branch",scope.branch())
            .addValue("wide",scope.institutionWide()).addValue("from",p.from()).addValue("through",p.through())
            .addValue("cutoff",p.recordedCutoff()).addValue("offset",Math.multiplyExact(p.page(),25));
    }
    public List<AccountBalance> balances(Scope scope,Parameters p,boolean known) {
        return jdbc.query(BALANCES+"SELECT * FROM balances ORDER BY code,id LIMIT 26 OFFSET :offset",parameters(scope,p),(r,n)->balance(r,known));
    }
    public List<AccountBalance> exportBalances(Scope scope,Parameters p,boolean known) {
        return jdbc.query(BALANCES+"SELECT * FROM balances ORDER BY code,id LIMIT 2001",parameters(scope,p),(r,n)->balance(r,known));
    }
    public Optional<AccountBalance> balance(Scope scope,Parameters p,UUID account,boolean known) {
        return jdbc.query(BALANCES+"SELECT * FROM balances WHERE id=:account",parameters(scope,p).addValue("account",account),(r,n)->balance(r,known)).stream().findFirst();
    }
    public Totals totals(Scope scope,Parameters p,boolean known) {
        return jdbc.queryForObject(BALANCES+"""
            SELECT COALESCE(SUM(GREATEST(opening,0)),0) opening_debit,COALESCE(SUM(GREATEST(-opening,0)),0) opening_credit,
              COALESCE(SUM(debit),0) movement_debit,COALESCE(SUM(credit),0) movement_credit,
              COALESCE(SUM(GREATEST(closing,0)),0) closing_debit,COALESCE(SUM(GREATEST(-closing,0)),0) closing_credit FROM balances
            """,parameters(scope,p),(r,n)->new Totals(known?r.getBigDecimal("opening_debit"):null,known?r.getBigDecimal("opening_credit"):null,
                r.getBigDecimal("movement_debit"),r.getBigDecimal("movement_credit"),known?r.getBigDecimal("closing_debit"):null,known?r.getBigDecimal("closing_credit"):null));
    }
    public List<Activity> activity(Scope scope,Parameters p,UUID account) {
        return activity(scope,p,account,26,Math.multiplyExact(p.page(),25));
    }
    public List<Activity> exportActivity(Scope scope,Parameters p,UUID account) {
        return activity(scope,p,account,2001,0);
    }
    private List<Activity> activity(Scope scope,Parameters p,UUID account,int limit,int offset) {
        return jdbc.query("""
            SELECT j.id,j.effective_date,j.recorded_at,j.posted_at,j.source_type,j.source_reference,j.evidence_reference,j.reverses_id,
              SUM(l.debit) debit,SUM(l.credit) credit
            FROM gl_journal j JOIN gl_journal_line l ON (l.journal_id,l.sacco_id,l.station_id)=(j.id,j.sacco_id,j.station_id)
            WHERE j.sacco_id=:institution AND (:wide OR j.station_id=:branch) AND l.account_id=:account AND j.state='POSTED'
              AND j.source_type<>'OPENING' AND j.effective_date BETWEEN :from AND :through AND j.posted_at<=:cutoff AND j.recorded_at<=:cutoff
            GROUP BY j.id ORDER BY j.effective_date,j.posted_at,j.id LIMIT :rowLimit OFFSET :offset
            """,parameters(scope,p).addValue("account",account).addValue("rowLimit",limit).addValue("offset",offset),(r,n)->new Activity(r.getObject("id",UUID.class),r.getObject("effective_date",LocalDate.class),
                r.getObject("recorded_at",OffsetDateTime.class),r.getObject("posted_at",OffsetDateTime.class),r.getString("source_type"),r.getString("source_reference"),
                r.getString("evidence_reference"),r.getObject("reverses_id",UUID.class),r.getBigDecimal("debit"),r.getBigDecimal("credit")));
    }
    public Coverage coverage(Scope scope,Parameters p) {
        return jdbc.queryForObject("""
            SELECT
              (SELECT COUNT(*) FROM sacco_stations s WHERE s.sacco_id=:institution AND (:wide OR s.station_id=:branch)
                AND NOT EXISTS(SELECT 1 FROM gl_cutover_coverage c JOIN gl_journal j ON j.id=c.opening_journal_id
                  WHERE c.sacco_id=s.sacco_id AND c.station_id=s.station_id AND c.complete AND c.recorded_at<=:cutoff
                    AND c.reconciled_through<=:through AND j.state='POSTED' AND j.posted_at<=:cutoff)) missing,
              (SELECT COUNT(DISTINCT e.voucher_id) FROM loan_journal_entries e JOIN loan_ledgers l ON l.loan_application_id=e.loan_application_id
                WHERE l.sacco_id=:institution AND (:wide OR l.station_id=:branch)
                AND e.effective_date<=:through AND e.posted_at<=:cutoff
                AND NOT EXISTS(SELECT 1 FROM gl_operational_bridge b JOIN gl_journal j ON j.id=b.journal_id
                  WHERE b.voucher_id=e.voucher_id AND b.sacco_id=l.sacco_id AND b.station_id=l.station_id AND j.state='POSTED' AND j.posted_at<=:cutoff)
                AND NOT EXISTS(SELECT 1 FROM gl_cutover_coverage c JOIN gl_journal j ON j.id=c.opening_journal_id
                  WHERE c.sacco_id=l.sacco_id AND c.station_id=l.station_id AND c.complete AND c.recorded_at<=:cutoff
                    AND c.reconciled_through>=e.effective_date AND j.state='POSTED' AND j.posted_at<=:cutoff)) unbridged,
              (SELECT COUNT(*) FROM loan_applications a WHERE a.sacco_id=:institution AND (:wide OR a.station_id=:branch)
                AND (a.disbursement_date<=:through OR (a.disbursement_date IS NULL AND a.status IN ('DISBURSED','PAID','DEFAULTED')))
                AND a.created_at<=:cutoff
                AND NOT EXISTS(SELECT 1 FROM loan_ledgers l WHERE l.loan_application_id=a.id AND l.created_at<=:cutoff)
                AND NOT EXISTS(SELECT 1 FROM gl_cutover_coverage c JOIN gl_journal j ON j.id=c.opening_journal_id
                  WHERE c.sacco_id=a.sacco_id AND c.station_id=a.station_id AND c.complete AND c.recorded_at<=:cutoff
                    AND a.disbursement_date IS NOT NULL AND c.reconciled_through>=a.disbursement_date AND j.state='POSTED' AND j.posted_at<=:cutoff)) uncovered
            """,parameters(scope,p),(r,n)->new Coverage(r.getLong("missing"),r.getLong("unbridged"),r.getLong("uncovered")));
    }
    private AccountBalance balance(ResultSet r,boolean known)throws SQLException {
        return new AccountBalance(r.getObject("id",UUID.class),r.getString("code"),r.getString("name"),r.getString("type"),r.getString("purpose"),
            known?r.getBigDecimal("opening"):null,r.getBigDecimal("debit"),r.getBigDecimal("credit"),known?r.getBigDecimal("closing"):null);
    }
}
