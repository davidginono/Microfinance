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
    private static final String REQUIRED_STATUS = "AWAITING_CREDIT_COMMITTEE";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgres()) {
            return;
        }
        String constraintDefinition = currentConstraintDefinition();
        if (constraintDefinition != null && constraintDefinition.contains(REQUIRED_STATUS)) {
            return;
        }

        jdbcTemplate.execute("ALTER TABLE loan_applications DROP CONSTRAINT IF EXISTS " + CONSTRAINT_NAME);
        jdbcTemplate.execute("""
            ALTER TABLE loan_applications
            ADD CONSTRAINT loan_applications_status_check
            CHECK (status IN (%s))
            """.formatted(loanStatusSqlValues()));
        log.info("Updated loan_applications status check constraint to include credit committee review status.");
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
}
