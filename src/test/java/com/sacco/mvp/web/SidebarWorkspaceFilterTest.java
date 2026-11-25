package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SidebarWorkspaceFilterTest {
    private static final Path SIDEBAR_FRAGMENT = Path.of("src/main/webapp/WEB-INF/jsp/fragments/sidebar.jspf");

    @Test void loanRecordingExistsOnlyInAccountantNavigation() throws IOException {
        String sidebar=sidebarFragment();
        String finance=Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/fragments/finance-navigation.jspf"));
        assertThat(sidebar).doesNotContain("value='/finance/loan-records'","value='/finance/client-applications'");
        assertThat(finance).contains("@access.has(principal, 'LOAN_RECORDING_VIEW')");
        assertThat(java.util.regex.Pattern.compile("value='/finance/loan-records'").matcher(finance).results().count()).isEqualTo(1);
        String form=Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/accounting/loans/record-form.jsp"));
        assertThat(form).doesNotContain("applicationForm","data-application-tab","application-form.jspf");
    }
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
        assertThat(sidebarFragment()).contains("currentMember.staffSession").doesNotContain("memberWorkspace", "memberApplyLoanActive");
    }

    @Test
    void memberMenuIsLimitedToMemberWorkspace() throws IOException {
        assertThat(sidebarFragment()).doesNotContain("/app/", "showManagerPanel", "showBoardPanel", "showChairpersonPanel");
    }

    @Test
    void staffMenusAreLimitedToStaffWorkspace() throws IOException {
        String sidebar=sidebarFragment();
        assertThat(sidebar).contains("${staffWorkspace}", "staff-offices.jspf", "staffSettingsPanel", "/account/security", "finance-navigation.jspf");
        assertThat(sidebar).doesNotContain("canAccessManagerArea", "canAccessAccountantArea", "canAccessBoardArea");
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
