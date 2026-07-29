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
    void editPageRendersDisabledUnsupportedMatrixCells() throws Exception {
        String editJsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/user-edit.jsp"));

        assertThat(editJsp).contains("data-restore-default-claims");
        assertThat(editJsp).contains("admin-access-matrix-restore");
        assertThat(editJsp).contains("width: auto");
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
