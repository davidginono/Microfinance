package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveLoanHydrationViewContractTest {

    @Test
    void memberDashboardAlwaysProvidesHydratableActiveLoansTable() throws Exception {
        String dashboard = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/app/dashboard.jsp"));

        assertThat(dashboard)
            .contains("data-active-loans-url=\"${pageContext.request.contextPath}/app/dashboard/active-loans\"")
            .contains("data-loading-label=\"Loading active loans...\"")
            .contains("data-active-loans-body")
            .contains("data-member-active-loans-count")
            .contains("showActiveLoansTableLoading(region)")
            .contains("showActiveLoansTableLoading(region, loadingLabel)")
            .contains("hideActiveLoansTableLoading(region)")
            .contains("data-aws-async-table-loading")
            .contains("aws-table-loader__spinner")
            .contains("renderActiveLoanPayload")
            .contains("scheduleAvailable")
            .contains("scheduleDataUrl")
            .contains("data-open-repayment-schedule")
            .contains("repayment-schedule-hydration.jspf")
            .doesNotContain("<c:when test=\"${not empty activeLoanChartRows}\">\r\n                        <div class=\"erp-table-wrap\"");
    }

    @Test
    void staffReviewPagesUseSharedActiveLoanHydrationFragment() throws Exception {
        String manager = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/manager/detail.jsp"));
        String board = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/board/detail.jsp"));
        String fragment = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/fragments/staff-active-loans-hydration.jspf"));

        assertStaffHydrationMarkup(manager);
        assertStaffHydrationMarkup(board);
        assertThat(fragment)
            .contains("data-staff-active-loans-section")
            .contains("data-staff-active-loans-refresh")
            .contains("showLoading(section")
            .contains("hideLoading(section)")
            .contains("data-aws-async-table-loading")
            .contains("aws-table-loader__spinner")
            .contains("\"Accept\": \"application/json\"")
            .contains("data-open-repayment-schedule")
            .contains("encodeURIComponent(loanId)")
            .contains("colspan=\\\"8\\\"")
            .contains("data-staff-active-loans-body");
        assertThat(manager)
            .contains("data-open-repayment-schedule")
            .contains("repayment-schedule-hydration.jspf");
        assertThat(board)
            .contains("data-open-repayment-schedule")
            .contains("repayment-schedule-hydration.jspf");
    }

    @Test
    void actualRepaymentScheduleViewsExposeForesightHydrationHooks() throws Exception {
        String memberDetail = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/app/loan-view.jsp"));
        String staffSummary = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/fragments/staff-repayment-summary.jspf"));

        assertThat(memberDetail)
            .contains("data-repayment-schedule-section=\"true\"")
            .contains("data-repayment-schedule-url=\"${pageContext.request.contextPath}${repaymentSchedulePath}\"")
            .contains("data-repayment-schedule-body")
            .contains("repayment-schedule-hydration.jspf");
        assertThat(staffSummary)
            .contains("actualRepaymentScheduleEnabled or not empty repaymentSummary")
            .contains("data-repayment-schedule-section=\"true\"")
            .contains("data-repayment-schedule-url=\"${repaymentSchedulePath}\"")
            .contains("data-repayment-schedule-body")
            .contains("Repayment schedule will appear when Foresight is available.");
    }

    @Test
    void disbursementOwnedViewsDoNotDeclareActiveLoanHydrationEndpoint() throws Exception {
        for (String file : Files.list(Path.of("src/main/webapp/WEB-INF/jsp/disbursement")).map(Path::toString).toList()) {
            String source = Files.readString(Path.of(file));
            assertThat(source).doesNotContain("applicant-active-loans");
            assertThat(source).doesNotContain("data-staff-active-loans-section");
        }
    }

    private void assertStaffHydrationMarkup(String source) {
        assertThat(source)
            .contains("data-staff-active-loans-section=\"true\"")
            .contains("data-active-loans-url=\"${reviewBasePath}/loan-applications/${app.id}/applicant-active-loans\"")
            .contains("data-staff-active-loans-refresh")
            .contains("data-loading-label=\"Loading active loans...\"")
            .contains("aria-label=\"Refresh applicant active loans\"")
            .contains("data-staff-active-loans-body")
            .contains("data-staff-active-loans-count")
            .contains("staff-active-loans-hydration.jspf");
    }
}
