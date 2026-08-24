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
            .contains("data-active-loans-body")
            .contains("data-member-active-loans-count")
            .contains("renderActiveLoanPayload")
            .contains("scheduleAvailable")
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
            .contains("\"Accept\": \"application/json\"")
            .contains("data-staff-active-loans-body");
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
            .contains("data-staff-active-loans-body")
            .contains("data-staff-active-loans-count")
            .contains("staff-active-loans-hydration.jspf");
    }
}
