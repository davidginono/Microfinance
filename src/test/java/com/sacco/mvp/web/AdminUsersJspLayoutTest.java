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
        assertThat(editJsp).contains("disabled");
        assertThat(editJsp).contains("No supported claim");
        assertThat(editJsp).contains("input.checked = defaults.has(input.value)");
    }
}
