package com.sacco.mvp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MICROFINANCE_RECORDING_TEST_URL",
    matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
class OtpDefaultsPostgresTest {
    @Test
    void migrationChangesOnlyDefaultsAndKeepsExistingChoices() throws Exception {
        try (var connection = DriverManager.getConnection(
            System.getenv("MICROFINANCE_RECORDING_TEST_URL"), "microfinance_test", "")) {
            connection.setAutoCommit(false);
            try (var sql = connection.createStatement()) {
                // Temporary tables shadow the real tables on this connection only.
                sql.execute("CREATE TEMP TABLE user_settings (member_id integer, login_otp_enabled boolean DEFAULT true, approval_otp_enabled boolean DEFAULT false)");
                sql.execute("CREATE TEMP TABLE sacco_stations (station_id integer, user_otp_selection_policy varchar(32) DEFAULT 'AT_LEAST_ONE')");
                sql.execute("INSERT INTO user_settings VALUES (1, true, true), (2, false, false)");
                sql.execute("INSERT INTO sacco_stations VALUES (1, 'BOTH'), (2, 'NONE')");

                sql.execute(Files.readString(Path.of("src/main/resources/db/migration/V151__default_optional_otp_off.sql")));
                sql.execute("INSERT INTO user_settings (member_id) VALUES (3)");
                sql.execute("INSERT INTO sacco_stations (station_id) VALUES (3)");

                try (var users = sql.executeQuery("SELECT login_otp_enabled, approval_otp_enabled FROM user_settings ORDER BY member_id")) {
                    assertThat(users.next()).isTrue();
                    assertThat(users.getBoolean(1)).isTrue();
                    assertThat(users.getBoolean(2)).isTrue();
                    for (int i = 0; i < 2; i++) {
                        assertThat(users.next()).isTrue();
                        assertThat(users.getBoolean(1)).isFalse();
                        assertThat(users.getBoolean(2)).isFalse();
                    }
                }
                try (var branches = sql.executeQuery("SELECT user_otp_selection_policy FROM sacco_stations ORDER BY station_id")) {
                    for (String expected : new String[]{"BOTH", "NONE", "NONE"}) {
                        assertThat(branches.next()).isTrue();
                        assertThat(branches.getString(1)).isEqualTo(expected);
                    }
                }
            } finally {
                connection.rollback();
            }
        }
    }
}
