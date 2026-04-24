package com.sacco.mvp.config;

import com.sacco.mvp.domain.EmailOtpPurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailOtpSchemaInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            Integer tableExists = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'email_otp_tokens'
                """, Integer.class);
            if (tableExists == null || tableExists == 0) {
                return;
            }

            String allowedPurposes = Arrays.stream(EmailOtpPurpose.values())
                .map(Enum::name)
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(",\n                    "));

            jdbcTemplate.update("""
                UPDATE email_otp_tokens
                SET purpose = 'STAFF_LOGIN'
                WHERE purpose = 'NON_MEMBER_LOGIN'
                """);

            jdbcTemplate.execute("""
                ALTER TABLE email_otp_tokens
                DROP CONSTRAINT IF EXISTS email_otp_tokens_purpose_check
                """);

            jdbcTemplate.execute("""
                ALTER TABLE email_otp_tokens
                ADD CONSTRAINT email_otp_tokens_purpose_check
                CHECK (purpose IN (
                    %s
                ))
                """.formatted(allowedPurposes));

            log.info("Ensured email_otp_tokens purpose constraint matches current OTP purposes.");
        } catch (Exception ex) {
            log.warn("Unable to refresh email_otp_tokens purpose constraint automatically: {}", ex.getMessage());
        }
    }
}
