package com.sacco.mvp.service;

import com.sacco.mvp.domain.EmailOtpPurpose;
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
public class EmailOtpPurposeConstraintInitializer implements ApplicationRunner {
    private static final String CONSTRAINT_NAME = "email_otp_tokens_purpose_check";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgres()) {
            return;
        }
        String constraintDefinition = currentConstraintDefinition();
        if (constraintMatchesCurrentPurposes(constraintDefinition)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE email_otp_tokens DROP CONSTRAINT IF EXISTS " + CONSTRAINT_NAME);
        int deletedLegacyTokens = jdbcTemplate.update("""
            DELETE FROM email_otp_tokens
            WHERE purpose NOT IN (%s)
            """.formatted(emailOtpPurposeSqlValues()));
        jdbcTemplate.execute("""
            ALTER TABLE email_otp_tokens
            ADD CONSTRAINT email_otp_tokens_purpose_check
            CHECK (purpose IN (%s))
            """.formatted(emailOtpPurposeSqlValues()));
        if (deletedLegacyTokens > 0) {
            log.info("Deleted {} obsolete forfeit OTP token(s).", deletedLegacyTokens);
        }
        log.info("Updated email_otp_tokens purpose check constraint to match configured OTP purposes.");
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
            where t.relname = 'email_otp_tokens'
              and c.conname = ?
            """, rs -> rs.next() ? rs.getString(1) : null, CONSTRAINT_NAME);
    }

    private boolean constraintMatchesCurrentPurposes(String constraintDefinition) {
        if (constraintDefinition == null || constraintDefinition.isBlank()) {
            return false;
        }
        for (EmailOtpPurpose purpose : EmailOtpPurpose.values()) {
            if (!constraintDefinition.contains(purpose.name())) {
                return false;
            }
        }
        return !constraintDefinition.contains("LOAN_APPLICATION_FORFEIT");
    }

    private String emailOtpPurposeSqlValues() {
        return Arrays.stream(EmailOtpPurpose.values())
            .map(purpose -> "'" + purpose.name() + "'")
            .collect(Collectors.joining(", "));
    }
}
