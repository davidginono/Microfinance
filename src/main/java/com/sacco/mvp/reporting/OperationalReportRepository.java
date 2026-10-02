package com.sacco.mvp.reporting;

import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import static com.sacco.mvp.reporting.OperationalReportDefinition.*;

@Repository
@RequiredArgsConstructor
public class OperationalReportRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public OperationalReportService.Result execute(OperationalReportDefinition definition, AppUserPrincipal actor, LocalDate from,
            LocalDate through, OffsetDateTime cutoff, int page, int pageSize) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("institution", actor.getSaccoId()).addValue("branch", actor.getStationId())
            .addValue("from", from).addValue("through", through).addValue("cutoff", cutoff)
            .addValue("limit", pageSize).addValue("offset", Math.multiplyExact(page, pageSize));
        String source = source(definition.dataset());
        StringBuilder filterSql = new StringBuilder(" WHERE 1=1");
        int n = 0;
        for (Filter filter : definition.filters()) {
            String parameter = "f" + n++;
            filterSql.append(" AND ").append(column(filter.field())).append(switch (filter.operator()) {
                case EQ -> " = :"; case GE -> " >= :"; case LE -> " <= :";
            }).append(parameter);
            parameters.addValue(parameter, value(filter));
        }
        String filtered = "WITH source AS (" + source + "), filtered AS (SELECT * FROM source" + filterSql + ") ";
        List<Column> visible = definition.columns().stream().filter(Column::visible).toList();
        String group = definition.groups().isEmpty() ? "" : " GROUP BY " + joinFields(definition.groups());
        String selection = visible.stream().map(c -> definition.groups().isEmpty() || definition.groups().contains(c.field())
            ? column(c.field()) : "SUM(" + column(c.field()) + ") AS " + column(c.field())).reduce((a,b) -> a + "," + b).orElseThrow();
        String order = definition.sorts().stream().map(s -> column(s.field()) + (s.descending() ? " DESC" : " ASC"))
            .reduce((a,b) -> a + "," + b).orElse("");
        // Stable ID tie-break prevents duplicates across pages; grouped rows use stable dimensions.
        order += (order.isEmpty() ? "" : ",") + (definition.groups().isEmpty() ? "source_id" : joinFields(definition.groups()));
        String resultQuery = "SELECT " + selection + " FROM filtered" + group;
        Long count = jdbc.queryForObject(filtered + "SELECT COUNT(*) FROM (" + resultQuery + ") counted", parameters, Long.class);
        List<Map<String, Object>> rows = jdbc.query(filtered + resultQuery + " ORDER BY " + order + " LIMIT :limit OFFSET :offset", parameters,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                for (Column c : visible) row.put(c.field().name(), switch (c.field().getType()) {
                    case MONEY -> rs.getBigDecimal(column(c.field()));
                    case DATE -> rs.getObject(column(c.field()), LocalDate.class);
                    case TEXT -> rs.getString(column(c.field()));
                });
                return Collections.unmodifiableMap(row);
            });
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        if (!definition.totals().isEmpty()) {
            String aggregate = definition.totals().stream().map(f -> "COALESCE(SUM(" + column(f) + "),0) AS " + column(f))
                .reduce((a,b) -> a + "," + b).orElseThrow();
            jdbc.query(filtered + "SELECT " + aggregate + " FROM filtered", parameters, rs -> {
                for (Field field : definition.totals()) totals.put(field.name(), rs.getBigDecimal(column(field)));
            });
        }
        Long untracked = jdbc.queryForObject("SELECT COUNT(*) FROM loan_applications a WHERE a.sacco_id=:institution AND a.station_id=:branch "
            + "AND a.disbursement_date <= :through AND a.created_at <= :cutoff "
            + "AND NOT EXISTS (SELECT 1 FROM loan_ledgers l WHERE l.loan_application_id=a.id AND l.created_at<=:cutoff)", parameters, Long.class);
        return new OperationalReportService.Result(definition, actor.getSaccoId(), actor.getStationId(), from, through, cutoff, count == null ? 0 : count,
            untracked == null ? 0 : untracked, List.copyOf(rows), Collections.unmodifiableMap(totals), page, pageSize,
            (long) (page + 1) * pageSize < (count == null ? 0 : count), "report.coverage." + definition.dataset().name());
    }

    private static String column(Field field) { return field.name().toLowerCase(Locale.ROOT); }
    private static String joinFields(List<Field> fields) { return fields.stream().map(OperationalReportRepository::column).reduce((a,b) -> a + "," + b).orElseThrow(); }
    private static String source(Dataset dataset) {
        String scope = " l.sacco_id=:institution AND l.station_id=:branch AND l.created_at<=:cutoff AND l.disbursement_date<=:through";
        return switch (dataset) {
            case COLLECTIONS -> "SELECT t.id AS source_id,l.loan_id,t.payment_date AS effective_date,t.receipt_reference AS receipt,t.channel,t.kind,"
                + "CASE WHEN t.kind='REVERSAL' THEN -t.amount ELSE t.amount END AS amount,"
                + "CASE WHEN t.kind='REVERSAL' THEN -t.principal_amount ELSE t.principal_amount END AS principal,"
                + "CASE WHEN t.kind='REVERSAL' THEN -t.interest_amount ELSE t.interest_amount END AS interest "
                + "FROM loan_repayment_transactions t JOIN loan_ledgers l ON l.loan_application_id=t.loan_application_id WHERE" + scope
                + " AND t.sacco_id=:institution AND t.station_id=:branch AND t.payment_date BETWEEN :from AND :through AND t.posted_at<=:cutoff";
            case DISBURSEMENTS -> "SELECT l.loan_application_id AS source_id,l.loan_id,l.disbursement_date AS effective_date,l.principal FROM loan_ledgers l WHERE" + scope
                + " AND l.disbursement_date>=:from";
            case LOAN_PORTFOLIO -> "SELECT l.loan_application_id AS source_id,l.loan_id,l.disbursement_date AS effective_date,l.principal,"
                + "l.principal-COALESCE(p.paid,0) AS outstanding_principal FROM loan_ledgers l LEFT JOIN ("
                + "SELECT loan_application_id,SUM(CASE WHEN kind='REVERSAL' THEN -principal_amount ELSE principal_amount END) AS paid "
                + "FROM loan_repayment_transactions WHERE sacco_id=:institution AND station_id=:branch AND payment_date<=:through AND posted_at<=:cutoff "
                + "GROUP BY loan_application_id) p ON p.loan_application_id=l.loan_application_id WHERE" + scope;
        };
    }
}

