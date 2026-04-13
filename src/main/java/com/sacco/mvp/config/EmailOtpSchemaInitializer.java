package com.sacco.mvp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
                    'LOGIN',
                    'STAFF_LOGIN',
                    'REGISTRATION',
                    'APPLICANT_SIGNATURE',
                    'GUARANTOR_SIGNATURE',
                    'BOARD_SIGNATURE'
                ))
                """);

            log.info("Ensured email_otp_tokens purpose constraint supports signature and staff OTP flows.");
        } catch (Exception ex) {
            log.warn("Unable to refresh email_otp_tokens purpose constraint automatically: {}", ex.getMessage());
        }
    }
}
