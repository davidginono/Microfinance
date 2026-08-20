package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SidebarWorkspaceFilterTest {
    private static final Path SIDEBAR_FRAGMENT = Path.of("src/main/webapp/WEB-INF/jsp/fragments/sidebar.jspf");

    @Test
    void sidebarDefinesWorkspaceFlagsFromCurrentLoginSession() throws IOException {
        String sidebar = sidebarFragment();

        assertThat(sidebar).contains(
            "<c:set var=\"memberWorkspace\" value=\"${not empty currentMember and currentMember.memberAccess and not currentMember.staffSession}\" />",
            "<c:set var=\"staffWorkspace\" value=\"${not empty currentMember and currentMember.staffSession}\" />"
        );
    }

    @Test
    void memberMenuIsLimitedToMemberWorkspace() throws IOException {
        String sidebar = sidebarFragment();

        assertGateBefore(sidebar, "<c:if test=\"${memberWorkspace}\">", "<a href=\"/app/dashboard\"");
        assertThat(sidebar).contains("<a href=\"/app/dashboard\"");
        assertThat(sidebar).contains("<a href=\"/app/support\"");
    }

    @Test
    void staffMenusAreLimitedToStaffWorkspace() throws IOException {
        String sidebar = sidebarFragment();

        assertGateBefore(sidebar, "<c:if test=\"${staffWorkspace}\">", "<a href=\"/admin/dashboard\"");
        assertGateBefore(sidebar, "<c:if test=\"${staffWorkspace}\">", "<c:set var=\"showManagerPanel\"");
        assertThat(sidebar).contains(
            "@access.canAccessManagerArea(principal)",
            "@access.canAccessLoanOfficerArea(principal)",
            "@access.canAccessAccountantArea(principal)",
            "@access.canAccessDisbursementArea(principal)"
        );
        assertThat(sidebar).contains(
            "id=\"staffSettingsPanel\"",
            "href=\"/staff/settings\"",
            "shell-sidebar-subitem",
            "shell-sidebar-subitem-label",
            "menu.settings.language",
            "Language &amp; Preferences"
        );
        assertThat(sidebar).contains("/staff/settings' ? 'shell-nav-active");
    }

    private String sidebarFragment() throws IOException {
        return Files.readString(SIDEBAR_FRAGMENT)
            .replace("\r\n", "\n");
    }

    private void assertGateBefore(String source, String gate, String marker) {
        int markerIndex = source.indexOf(marker);
        assertThat(markerIndex).isNotNegative();
        assertThat(source.lastIndexOf(gate, markerIndex)).isNotNegative();
    }
}
