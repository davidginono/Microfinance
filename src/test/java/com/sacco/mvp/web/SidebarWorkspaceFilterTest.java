package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SidebarWorkspaceFilterTest {
    private static final Path SIDEBAR_FRAGMENT = Path.of("src/main/webapp/WEB-INF/jsp/fragments/sidebar.jspf");

    @Test void institutionConfigurationBelongsToItsClaimGatedDomainAndNeverPersonalSettings() throws IOException {
        String sidebar=sidebarFragment();
        String finance=Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/fragments/finance-navigation.jspf"));
        assertThat(sidebar).doesNotContain("value='/finance/approval/configuration'","value='/finance/accounts/approval-configuration'");
        String vouchers=finance.substring(finance.indexOf("id=\"financeVouchersPanel\""),finance.indexOf("accountingSetupActive"));
        String accounts=finance.substring(finance.indexOf("id=\"accountingSetupPanel\""),finance.indexOf("financeReportsActive"));
        assertThat(vouchers).contains("'VOUCHER_RULE_VIEW'","value='/finance/approval/configuration'");
        assertThat(accounts).contains("'ACCOUNTING_ACCOUNT_RULES_VIEW'","value='/finance/accounts/approval-configuration'");
        assertThat(finance).contains("'ACCOUNTING_JOURNALS_VIEW', 'VOUCHER_RULE_VIEW'","'ACCOUNTING_ACCOUNTS_VIEW', 'ACCOUNTING_ACCOUNT_RULES_VIEW'");
    }

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
    void memberLoanFormRoutesHighlightApplyForLoanInsteadOfApplicationProgress() throws IOException {
        String sidebar = sidebarFragment();

        assertThat(sidebar).contains(
            "<c:set var=\"memberApplyLoanActive\" value=\"${requestUri eq '/app/loan-products' or requestUri eq '/app/loan-applications/new' or (fn:startsWith(requestUri, '/app/loan-applications/') and fn:endsWith(requestUri, '/edit'))}\" />",
            "<c:set var=\"memberApplicationProgressActive\" value=\"${fn:startsWith(requestUri, '/app/loan-applications') and not memberApplyLoanActive}\" />",
            "${memberApplyLoanActive ? 'border-cyan-200 bg-cyan-50 text-cyan-800 shadow-sm'",
            "${memberApplicationProgressActive ? 'border-cyan-200 bg-cyan-50 text-cyan-800 shadow-sm'"
        );
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
