package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTimeoutUiContractTest {

    @Test
    void footerUsesTimeoutPolicyAttributesAndVerificationCopy() throws Exception {
        String footer = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/fragments/footer.jspf"));

        assertThat(footer).contains("data-timeout-ms=\"${sessionTimeoutMs}\"");
        assertThat(footer).contains("data-warning-ms=\"${sessionTimeoutWarningMs}\"");
        assertThat(footer).contains("data-warning-enabled=\"${sessionTimeoutWarningEnabled}\"");
        assertThat(footer).doesNotContain("data-unfinished-work-message");
        assertThat(footer).contains("Verify to continue");
        assertThat(footer).contains("id=\"sessionInactivityPassword\"");
        assertThat(footer).contains("Verify &amp; Stay Logged In");
        assertThat(footer).doesNotContain("data-prompt-ms");
        assertThat(footer).doesNotContain("data-grace-ms");
    }

    @Test
    void shellScriptSeparatesPasswordConfirmationFromActivityRenewal() throws Exception {
        String shell = Files.readString(Path.of("src/main/resources/static/js/shell.js"));

        assertThat(shell).contains("data-timeout-ms");
        assertThat(shell).contains("data-warning-ms");
        assertThat(shell).contains("sessionInactivityPassword");
        assertThat(shell).contains("runFinalMinuteCheck");
        assertThat(shell).contains("body.set('password', password)");
        assertThat(shell).contains("payload.refreshed !== true");
        assertThat(shell).contains("!activity && !password.trim()", "!event.isTrusted", "scheduleActivityRenewal", "'/activity'");
        assertThat(shell).doesNotContain("activityInCycle");
        assertThat(shell).doesNotContain("refreshSession(true)");
        assertThat(shell).doesNotContain("schedulePrompt");
    }

    @Test
    void platformSettingsRendersSessionTimeoutCard() throws Exception {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/platform-settings.jsp"));
        String editor = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/platform-settings-edit.jsp"));

        assertThat(jsp).contains("/admin/platform-settings/session-timeout");
        assertThat(jsp).contains("${platformSessionSettings.timeoutMinutes}");
        assertThat(jsp).contains("platformSessionSettings.warningEnabled");
        assertThat(editor).contains("name=\"timeoutMinutes\"", "name=\"warningEnabled\"", "platform.settings.sessionWarning.help");
    }

    @Test
    void platformSettingsRendersEmailAndSmsGatewayCards() throws Exception {
        String overview=Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/platform-settings.jsp"));
        String editor=Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/platform-settings-edit.jsp"));
        assertThat(overview).contains("/admin/platform-settings/email/edit", "/admin/platform-settings/sms-gateway/edit", "../fragments/alerts.jspf");
        assertThat(editor).contains("_csrf", "apiKey", "password").doesNotContain("name=\"password\" value=", "name=\"apiKey\" value=");
    }

    @Test
    void platformSettingsRendersSaccoLoanTopUpFeatureToggle() throws Exception {
        String overview=Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/platform-settings.jsp"));
        String editor=Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/platform-settings-top-up.jsp"));
        assertThat(overview).contains("/admin/platform-settings/loan-top-up");
        assertThat(editor).contains("institution.loanTopUpEnabled", "data-platform-topup-row");
        assertThat(Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/platform-settings-edit.jsp"))).contains("loanTopUpEnabled", "_csrf");
    }
}
