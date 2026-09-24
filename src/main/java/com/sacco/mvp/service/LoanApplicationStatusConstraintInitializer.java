package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanApplicationStatusConstraintInitializer implements ApplicationRunner {
    private static final String CONSTRAINT_NAME = "loan_applications_status_check";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgres()) {
            return;
        }
        String constraintDefinition = currentConstraintDefinition();
        int legacyStatusCount = legacyStatusCount();
        if (legacyStatusCount == 0 && constraintMatchesCurrentStatuses(constraintDefinition)) {
            return;
        }

        jdbcTemplate.execute("ALTER TABLE loan_applications DROP CONSTRAINT IF EXISTS " + CONSTRAINT_NAME);
        normalizeLegacyLoanStatuses(legacyStatusCount);
        jdbcTemplate.execute("""
            ALTER TABLE loan_applications
            ADD CONSTRAINT loan_applications_status_check
            CHECK (status IN (%s))
            """.formatted(loanStatusSqlValues()));
        log.info("Updated loan_applications status check constraint and normalized legacy loan statuses.");
    }

    private boolean isPostgres() {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName != null && productName.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to inspect database product", ex);
        }
    }

    private String currentConstraintDefinition() {
        return jdbcTemplate.query("""
            select pg_get_constraintdef(c.oid)
            from pg_constraint c
            join pg_class t on t.oid = c.conrelid
            where t.relname = 'loan_applications'
              and c.conname = ?
            """, rs -> rs.next() ? rs.getString(1) : null, CONSTRAINT_NAME);
    }

    private String loanStatusSqlValues() {
        return Arrays.stream(LoanStatus.values())
            .map(status -> "'" + status.name() + "'")
            .collect(Collectors.joining(", "));
    }

    private boolean constraintMatchesCurrentStatuses(String constraintDefinition) {
        if (constraintDefinition == null || constraintDefinition.isBlank()) {
            return false;
        }
        for (LoanStatus status : LoanStatus.values()) {
            if (!constraintDefinition.contains(status.name())) {
                return false;
            }
        }
        return !constraintDefinition.contains("FINAL_APPROVED")
            && !constraintDefinition.contains("FINAL_REJECTED")
            && !constraintDefinition.contains("FORFEITED");
    }

    private int legacyStatusCount() {
        Integer count = jdbcTemplate.queryForObject("""
            select count(*)
            from loan_applications
            where status in ('FINAL_APPROVED', 'FINAL_REJECTED', 'FORFEITED')
            """, Integer.class);
        return count == null ? 0 : count;
    }

    private void normalizeLegacyLoanStatuses(int count) {
        if (count == 0) {
            return;
        }
        jdbcTemplate.update("""
            update loan_applications
            set status = case
                    when status = 'FINAL_APPROVED' then 'DISBURSED'
                    when status = 'FINAL_REJECTED' then 'REJECTED'
                    when exists (
                        select 1 from manager_reviews mr
                        where mr.loan_application_id = loan_applications.id
                    ) then 'MANAGER_REJECTED'
                    when exists (
                        select 1 from board_reviews br
                        where br.loan_application_id = loan_applications.id
                          and br.review_stage = 'CREDIT_COMMITTEE'
                    ) then 'CREDIT_COMMITTEE_REJECTED'
                    when exists (
                        select 1 from board_reviews br
                        where br.loan_application_id = loan_applications.id
                          and br.review_stage = 'BOARD'
                    ) then 'BOARD_REJECTED'
                    else 'MANAGER_REJECTED'
                end,
                updated_at = coalesce(updated_at, now())
            where status in ('FINAL_APPROVED', 'FINAL_REJECTED', 'FORFEITED')
            """);
        log.info("Normalized {} legacy loan application status row(s).", count);
    }
}
