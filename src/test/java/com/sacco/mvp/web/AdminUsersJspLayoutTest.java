package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUsersJspLayoutTest {
    @Test
    void usersListUsesEditPageLinksInsteadOfPerRowEditModals() throws Exception {
        String usersJsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/users.jsp"));

        assertThat(usersJsp).contains("/admin/users/${user.accountId}/edit");
        assertThat(usersJsp).doesNotContain("data-user-modal=\"user-${user.accountId}\"");
        assertThat(usersJsp).doesNotContain("data-access-column-toggle");
    }

    @Test
    void usersAndRolesFiltersUseCompactAwsToolbarWithoutVerticalTableScroll() throws Exception {
        String usersJsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/users.jsp"));
        String componentCss = Files.readString(Path.of("src/main/resources/static/css/console-components.css"));

        assertThat(usersJsp)
            .contains("class=\"erp-table-wrap admin-register-shell admin-users-register\"")
            .contains("class=\"admin-filter-form aws-filter-toolbar admin-users-filter-form\"")
            .contains("class=\"admin-users-filter-field admin-users-filter-field--type\"")
            .contains("class=\"admin-users-filter-field admin-users-filter-field--query\"")
            .contains("class=\"admin-users-filter-field admin-users-filter-field--rows\"")
            .contains("class=\"admin-users-filter-actions\"")
            .contains("<span class=\"sr-only\">Search By</span>")
            .contains("<span class=\"sr-only\">Search User</span>")
            .contains("<span class=\"sr-only\">Rows Per Page</span>")
            .contains("type=\"search\"")
            .contains("class=\"fcms-control w-full\"")
            .doesNotContain("tracking-[0.22em] text-slate-500\">Search By")
            .doesNotContain("tracking-[0.22em] text-slate-500\">Rows Per Page");
        assertThat(componentCss)
            .contains(".admin-users-filter-form")
            .contains("overflow-y: hidden !important")
            .contains(".admin-users-filter-field--query")
            .contains("flex: 1 1 25rem !important")
            .contains(".admin-users-filter-actions")
            .contains(".aws-console .admin-users-register.admin-register-shell > .erp-table-scroll")
            .contains("max-height: none !important")
            .contains("overflow-y: visible !important")
            .contains("@media (max-width: 767px)");
    }

    @Test
    void editPageRendersDisabledUnsupportedMatrixCells() throws Exception {
        String editJsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/user-edit.jsp"));
        String componentCss = Files.readString(Path.of("src/main/resources/static/css/console-components.css"));

        assertThat(editJsp).contains("data-restore-default-claims");
        assertThat(editJsp).contains("admin-access-matrix-restore");
        assertThat(editJsp)
            .contains("admin-user-edit-context-bar")
            .contains("admin-user-summary-header")
            .contains("admin-user-detail-grid")
            .contains("admin-role-option")
            .contains("admin-cloud-checkbox")
            .contains("admin-user-edit-actions")
            .contains("class=\"fcms-control\"")
            .doesNotContain("erp-page-subtitle")
            .doesNotContain("class=\"erp-toolbar\"");
        assertThat(componentCss).contains(".admin-access-matrix-restore");
        assertThat(componentCss).contains("width: auto");
        assertThat(componentCss)
            .contains(".admin-user-edit-context-bar")
            .contains("border-radius: 2px")
            .contains(".admin-user-section-header")
            .contains(".admin-role-grid")
            .contains(".admin-role-option")
            .contains(".admin-status-callout")
            .contains(".admin-delete-callout");
        assertThat(editJsp).contains("disabled");
        assertThat(editJsp).contains("No supported claim");
        assertThat(editJsp).contains("input.checked = defaults.has(input.value)");
    }

    @Test
    void loanProductWorkflowScriptIsSharedBetweenAdminPages() throws Exception {
        String settingsJsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/settings-controls.jsp"));
        String productEditJsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/loan-product-edit.jsp"));
        String sharedScript = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/fragments/loan-product-workflow-script.jspf"));

        assertThat(settingsJsp).contains("../fragments/loan-product-workflow-script.jspf");
        assertThat(productEditJsp).contains("../fragments/loan-product-workflow-script.jspf");
        assertThat(settingsJsp).doesNotContain("function validateBeforeSubmit()");
        assertThat(productEditJsp).doesNotContain("function validateBeforeSubmit()");
        assertThat(sharedScript).contains("function validateBeforeSubmit()");
        assertThat(sharedScript).contains("data-required-attachments-list");
        assertThat(sharedScript).contains("data-required-attachment-add");
    }
}
