package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminConfigurationUiContractTest {
    private static final Path JSP_ROOT = Path.of("src/main/webapp/WEB-INF/jsp");

    @Test
    void focusedLoanProductOwnsChangeDrivenWorkflowWarnings() throws Exception {
        String productEdit = read(JSP_ROOT.resolve("admin/loan-product-edit.jsp"));
        String settings = read(JSP_ROOT.resolve("admin/settings-controls.jsp"));
        String workflowScript = read(JSP_ROOT.resolve("fragments/loan-product-workflow-script.jspf"));

        assertThat(productEdit)
            .contains("data-workflow-notifications=\"focused\"")
            .contains("data-workflow-warnings")
            .contains("erp-table-scroll erp-table-scroll-sm")
            .contains("data-board-reviewer-option")
            .contains("data-credit-committee-reviewer-option")
            .contains("<c:if test=\"${not empty error}\">")
            .doesNotContain("openProductModalKey eq productModalKey")
            .doesNotContain("product-config-step-description");
        assertThat(settings).doesNotContain("data-workflow-notifications=\"focused\"");
        assertThat(workflowScript)
            .contains("applyRules({ notify: true })")
            .contains("previousWorkflowWarningKeys")
            .contains("workflowWarningsInitialized")
            .contains("block.dataset.workflowWarningTone")
            .doesNotContain("block.setAttribute('data-toast-message'");
    }

    @Test
    void applicationNoticesExpireOnlyWhenDismissed() throws Exception {
        String shellScript = read(Path.of("src/main/resources/static/js/shell.js"));
        String login = read(JSP_ROOT.resolve("login.jsp"));
        String registration = read(JSP_ROOT.resolve("register-member.jsp"));

        assertThat(shellScript)
            .contains("closeButton?.addEventListener('click', dismiss)")
            .doesNotContain("window.setTimeout(entry.dismiss")
            .doesNotContain("window.setTimeout(dismiss, duration)");
        assertThat(login).doesNotContain("window.setTimeout(dismiss, duration)");
        assertThat(registration).doesNotContain("window.setTimeout(dismiss, duration)");
    }

    @Test
    void smsTablesAndSharedPageHeaderUseAwsConsoleStructure() throws Exception {
        String smsUsage = read(JSP_ROOT.resolve("admin/sms-usage.jsp"));
        String shellCss = read(Path.of("src/main/resources/static/css/shell.css"));

        assertThat(smsUsage)
            .contains("Station SMS Balances")
            .contains("app-table-titlebar")
            .contains("aws-filter-toolbar")
            .contains("erp-table-scroll")
            .contains("class=\"erp-table\"")
            .contains("aws-pagination-chevron")
            .contains("entry.createdAtLabel")
            .doesNotContain("erp-page-subtitle");
        assertThat(shellCss)
            .contains(".erp-page-header[data-aws-page-header]")
            .contains("position: sticky")
            .contains(".shell-page-title-rail")
            .contains("display: none !important")
            .contains(".erp-page-subtitle");
    }

    @Test
    void modalSubmitActionsStayOutOfRegisterToolbarsAndProfileIsCircular() throws Exception {
        String shellScript = read(Path.of("src/main/resources/static/js/shell.js"));
        String users = read(JSP_ROOT.resolve("admin/users.jsp"));
        String shellCss = read(Path.of("src/main/resources/static/css/shell.css"));

        assertThat(shellScript).contains("control.closest('.app-modal-overlay')");
        assertThat(users)
            .containsOnlyOnce("data-user-modal-open=\"create-user\"")
            .contains("data-user-modal=\"create-user\"")
            .contains("type=\"submit\" class=\"app-btn btn-launch\">Create Staff Member");
        assertThat(shellCss)
            .contains(".app-topbar-actions .app-profile-control")
            .contains(".aws-console .app-topbar .profile-icon-btn")
            .contains("border-radius: 50% !important");
    }

    private String read(Path path) throws Exception {
        return Files.readString(path);
    }
}
