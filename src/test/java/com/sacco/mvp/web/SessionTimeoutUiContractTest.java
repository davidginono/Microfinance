package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTimeoutUiContractTest {

    @Test
    void footerUsesTimeoutPolicyAttributesAndStillThereCopy() throws Exception {
        String footer = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/fragments/footer.jspf"));

        assertThat(footer).contains("data-timeout-ms=\"${sessionTimeoutMs}\"");
        assertThat(footer).contains("data-warning-ms=\"${sessionTimeoutWarningMs}\"");
        assertThat(footer).contains("Are you still there?");
        assertThat(footer).doesNotContain("data-prompt-ms");
        assertThat(footer).doesNotContain("data-grace-ms");
    }

    @Test
    void shellScriptUsesFinalMinuteActivityCheckInsteadOfRecurringPromptTimer() throws Exception {
        String shell = Files.readString(Path.of("src/main/resources/static/js/shell.js"));

        assertThat(shell).contains("data-timeout-ms");
        assertThat(shell).contains("data-warning-ms");
        assertThat(shell).contains("activityInCycle");
        assertThat(shell).contains("runFinalMinuteCheck");
        assertThat(shell).contains("refreshSession(true)");
        assertThat(shell).doesNotContain("schedulePrompt");
    }

    @Test
    void platformSettingsRendersSessionTimeoutCard() throws Exception {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/platform-settings.jsp"));

        assertThat(jsp).contains("/admin/platform-settings/session-timeout");
        assertThat(jsp).contains("name=\"timeoutMinutes\"");
        assertThat(jsp).contains("value=\"${platformSessionSettings.timeoutMinutes}\"");
        assertThat(jsp).contains("Save Timeout");
    }
}
