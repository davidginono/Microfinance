package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OtpPreferenceMigrationContractTest {
    @Test
    void usersOwnOtpRequirementsWhileAdminsRetainDeliveryConfiguration() throws Exception {
        String security = read("src/main/webapp/WEB-INF/jsp/account/security.jsp");
        String adminSettings = read("src/main/webapp/WEB-INF/jsp/admin/settings-controls.jsp");
        String sidebar = read("src/main/webapp/WEB-INF/jsp/fragments/sidebar.jspf");

        assertThat(security)
            .contains("name=\"loginOtpEnabled\"")
            .contains("name=\"approvalOtpEnabled\"")
            .contains("action=\"/account/security/preferences\"")
            .contains("name=\"${_csrf.parameterName}\"");
        assertThat(sidebar).contains("href=\"/account/security\"");
        assertThat(adminSettings)
            .contains("name=\"otpDeliveryChannel\"")
            .contains("name=\"otpSelectionPolicy\"")
            .contains("value=\"AT_LEAST_ONE\"")
            .contains("value=\"BOTH\"")
            .doesNotContain("name=\"otpRequirementMode\"")
            .doesNotContain("stationOtpRequirementMode");
    }

    @Test
    void stationPolicyMigrationRequiresAtLeastOneOptionByDefault() throws Exception {
        String migration = read("src/main/resources/db/migration/V36__add_user_otp_selection_policy.sql");

        assertThat(migration)
            .contains("user_otp_selection_policy varchar(32) NOT NULL DEFAULT 'AT_LEAST_ONE'")
            .contains("CHECK (user_otp_selection_policy IN ('AT_LEAST_ONE', 'BOTH'))");
    }

    @Test
    void migrationBackfillsUserPreferencesFromLegacyStationModes() throws Exception {
        String migration = read("src/main/resources/db/migration/V34__move_otp_requirements_to_user_settings.sql");
        String correction = read("src/main/resources/db/migration/V35__preserve_login_otp_for_missing_station_modes.sql");

        assertThat(migration)
            .contains("ADD COLUMN login_otp_enabled boolean")
            .contains("ADD COLUMN approval_otp_enabled boolean")
            .contains("LEFT JOIN sacco_stations station")
            .contains("ON CONFLICT (member_id) DO UPDATE")
            .contains("ALTER COLUMN login_otp_enabled SET NOT NULL")
            .contains("ALTER COLUMN approval_otp_enabled SET NOT NULL");
        assertThat(correction)
            .contains("SET login_otp_enabled = true")
            .contains("member.station_id IS NOT NULL")
            .contains("station.otp_requirement_mode IS NULL");
    }

    private String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
