package com.sacco.mvp.reporting.operational.repository;

import com.sacco.mvp.reporting.operational.dto.ReportDefinition;
import com.sacco.mvp.reporting.operational.dto.ReportDefinition.*;
import com.sacco.mvp.reporting.operational.dto.OperationalReportResult.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.OffsetDateTime;
import java.util.*;

/** SQL structure comes exclusively from enum values; all user values are bound. */
@Repository @RequiredArgsConstructor
public class OperationalReportRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public record QueryResult(List<Map<String, Object>> rows, long count, Map<String, Total> totals,
                              List<Map<String, Object>> groups, Coverage coverage) { }
    public void event(UUID template, String institution, UUID actor, String action, String definition, OffsetDateTime now) {
        jdbc.update("INSERT INTO operational_report_template_events(id,template_id,sacco_id,actor_id,action,definition_json,recorded_at)"
            + " VALUES(:id,:template,:institution,:actor,:action,:definition,:now)", new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID()).addValue("template", template).addValue("institution", institution)
            .addValue("actor", actor).addValue("action", action).addValue("definition", definition).addValue("now", now));
    }
    public QueryResult query(String institution, String branch, ReportDefinition d, OffsetDateTime cutoff, int page, int size) {
        var p = new MapSqlParameterSource().addValue("institution", institution).addValue("branch", branch)
            .addValue("from", Date.valueOf(d.dateFrom())).addValue("to", Date.valueOf(d.dateTo()))
            .addValue("cutoff", cutoff).addValue("limit", size).addValue("offset", (long) page * size)
            .addValue("loanId", d.loanId()).addValue("channel", d.channel()).addValue("status", d.status());
        String source = source(d.dataset());
        String where = d.dataset() == Dataset.LOAN_PORTFOLIO ? " WHERE event_date<=:to" : " WHERE event_date BETWEEN :from AND :to";
        if (!d.loanId().isEmpty()) where += " AND loan_id = :loanId";
        if (!d.channel().isEmpty()) where += " AND channel = :channel";
        if (!d.status().isEmpty()) where += " AND status = :status";
        String cte = "WITH dataset AS (" + source + "), filtered AS (SELECT * FROM dataset" + where + ") ";
        String fields = d.columns().stream().map(c -> key(c.field())).reduce((a, b) -> a + ", " + b).orElseThrow();
        var rows = jdbc.queryForList(cte + "SELECT " + fields + " FROM filtered ORDER BY " + key(d.sortBy())
            + " " + d.direction().name() + " NULLS LAST, row_key ASC LIMIT :limit OFFSET :offset", p);
        String totalSql = "SELECT COUNT(*) AS row_count";
        for (var field : d.totals()) {
            String key = key(field);
            totalSql += ", SUM(" + key + ") AS " + key + ", COUNT(" + key + ") AS " + key + "_available";
        }
        var summary = jdbc.queryForMap(cte + totalSql + " FROM filtered", p);
        long count = ((Number) summary.get("row_count")).longValue();
        Map<String, Total> totals = new LinkedHashMap<>();
        for (var field : d.totals()) {
            String key = key(field);
            long available = ((Number) summary.get(key + "_available")).longValue();
            totals.put(key, new Total((BigDecimal) summary.get(key), available, count - available));
        }
        List<Map<String, Object>> groups = List.of();
        if (!d.groupBy().isEmpty()) {
            String groupKeys = d.groupBy().stream().map(OperationalReportRepository::key).reduce((a, b) -> a + ", " + b).orElseThrow();
            String aggregates = ", COUNT(*) AS row_count";
            for (var field : d.totals()) {
                String key = key(field);
                aggregates += ", SUM(" + key + ") AS " + key + ", COUNT(*) - COUNT(" + key + ") AS " + key + "_unavailable";
            }
            groups = jdbc.queryForList(cte + "SELECT " + groupKeys + aggregates + " FROM filtered GROUP BY "
                + groupKeys + " ORDER BY " + groupKeys + " LIMIT 101", p);
            if (groups.size() > 100) throw new IllegalArgumentException("opreport.error.groups");
        }
        var coverage = jdbc.queryForMap("SELECT COUNT(l.loan_application_id) FILTER (WHERE a.disbursement_date IS NOT NULL) AS tracked, "
            + "COUNT(*) FILTER (WHERE a.disbursement_date IS NOT NULL AND l.loan_application_id IS NULL) AS untracked, "
            + "COUNT(*) FILTER (WHERE a.disbursement_date IS NULL) AS unknown_date"
            + " FROM loan_applications a LEFT JOIN loan_ledgers l ON l.loan_application_id=a.id AND l.sacco_id=a.sacco_id AND l.station_id=a.station_id AND l.created_at<=:cutoff"
            + " WHERE a.sacco_id=:institution AND a.station_id=:branch AND a.created_at<=:cutoff AND "
            + "(" + (d.dataset() == Dataset.DISBURSEMENTS ? "a.disbursement_date BETWEEN :from AND :to" : "a.disbursement_date<=:to")
            + " OR (a.disbursement_date IS NULL AND a.status IN ('DISBURSED','PAR','DEFAULTED','PAID')))"
            + (d.loanId().isEmpty() ? "" : " AND a.loan_id=:loanId")
            + (d.status().isEmpty() ? "" : " AND a.status=:status"), p);
        return new QueryResult(immutableRows(rows), count, Collections.unmodifiableMap(totals), immutableRows(groups),
            new Coverage(((Number) coverage.get("tracked")).longValue(), ((Number) coverage.get("untracked")).longValue(),
                ((Number) coverage.get("unknown_date")).longValue(),
                "opreport.coverage." + d.dataset().name()));
    }
    private List<Map<String, Object>> immutableRows(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> {
            Map<String, Object> scalar = new LinkedHashMap<>();
            row.forEach((key, value) -> scalar.put(key, value instanceof Date date ? date.toLocalDate() : value));
            return Collections.unmodifiableMap(scalar); // Map.copyOf would reject intentionally unavailable cells.
        }).toList();
    }
    public static String key(Field field) { return field.name().toLowerCase(Locale.ROOT); }
    private String source(Dataset dataset) {
        return switch (dataset) {
            case COLLECTIONS -> """
                SELECT t.id AS row_key, t.payment_date AS event_date, l.loan_id,
                  t.payment_date, t.receipt_reference AS receipt, t.channel, t.kind,
                  CASE WHEN t.kind='REVERSAL' THEN -t.principal_amount ELSE t.principal_amount END AS principal,
                  CASE WHEN t.kind='REVERSAL' THEN -t.interest_amount ELSE t.interest_amount END AS interest,
                  CASE WHEN t.kind='REVERSAL' THEN -t.amount ELSE t.amount END AS amount
                FROM loan_repayment_transactions t
                JOIN loan_ledgers l ON l.loan_application_id=t.loan_application_id AND l.sacco_id=t.sacco_id AND l.station_id=t.station_id
                JOIN loan_applications a ON a.id=l.loan_application_id AND a.sacco_id=l.sacco_id AND a.station_id=l.station_id
                WHERE t.sacco_id=:institution AND t.station_id=:branch AND t.posted_at<=:cutoff
                """;
            case DISBURSEMENTS -> """
                SELECT l.loan_application_id AS row_key, l.disbursement_date AS event_date,
                  l.loan_id, l.disbursement_date, l.principal
                FROM loan_ledgers l JOIN loan_applications a ON a.id=l.loan_application_id
                  AND a.sacco_id=l.sacco_id AND a.station_id=l.station_id
                WHERE l.sacco_id=:institution AND l.station_id=:branch AND l.created_at<=:cutoff
                """;
            case LOAN_PORTFOLIO -> """
                SELECT a.id AS row_key, a.disbursement_date AS event_date, a.loan_id, a.disbursement_date, a.status,
                  CASE WHEN l.loan_application_id IS NULL THEN 'UNAVAILABLE' ELSE 'LOCAL_LEDGER' END AS coverage,
                  l.principal, l.principal-COALESCE(t.principal_paid,0) AS outstanding_principal,
                  CASE WHEN l.loan_application_id IS NULL THEN NULL ELSE COALESCE(i.interest_due,0)-COALESCE(t.interest_paid,0) END AS due_interest
                FROM loan_applications a
                LEFT JOIN loan_ledgers l ON l.loan_application_id=a.id AND l.sacco_id=a.sacco_id
                  AND l.station_id=a.station_id AND l.created_at<=:cutoff
                LEFT JOIN (
                  SELECT loan_application_id,
                    SUM(CASE WHEN kind='REVERSAL' THEN -principal_amount ELSE principal_amount END) AS principal_paid,
                    SUM(CASE WHEN kind='REVERSAL' THEN -interest_amount ELSE interest_amount END) AS interest_paid
                  FROM loan_repayment_transactions
                  WHERE sacco_id=:institution AND station_id=:branch AND payment_date<=:to AND posted_at<=:cutoff
                  GROUP BY loan_application_id
                ) t ON t.loan_application_id=l.loan_application_id
                LEFT JOIN (
                  SELECT i.loan_application_id, SUM(i.interest) AS interest_due
                  FROM loan_ledger_installments i JOIN loan_ledgers ll ON ll.loan_application_id=i.loan_application_id
                  WHERE ll.sacco_id=:institution AND ll.station_id=:branch AND i.due_date<=:to AND ll.created_at<=:cutoff
                  GROUP BY i.loan_application_id
                ) i ON i.loan_application_id=l.loan_application_id
                WHERE a.sacco_id=:institution AND a.station_id=:branch AND a.disbursement_date IS NOT NULL AND a.created_at<=:cutoff
                """;
        };
    }
}
