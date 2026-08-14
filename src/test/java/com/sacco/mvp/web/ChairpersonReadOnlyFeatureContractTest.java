package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ChairpersonReadOnlyFeatureContractTest {
    @Test
    void migrationCopiesClaimsFromExistingQueueHoldersWithoutRoleChecks() throws IOException {
        String migration = Files.readString(Path.of(
            "src/main/resources/db/migration/V25__grant_chairperson_read_only_claims.sql"));

        assertThat(migration).contains("WHERE claim_name = 'CHAIRPERSON_QUEUE_VIEW'")
            .contains("'PROCESSED_LOANS_VIEW'")
            .contains("'SACCO_CONFIGURATIONS_VIEW'")
            .doesNotContain("CHAIRPERSON'")
            .doesNotContain("position")
            .doesNotContain("role");
    }

    @Test
    void securityRegistersDedicatedMatchersBeforeBroadChairpersonMatcher() throws IOException {
        String security = Files.readString(Path.of("src/main/java/com/sacco/mvp/config/SecurityConfig.java"));
        int processed = security.indexOf("/chairperson/processed-loans");
        int configurations = security.indexOf("/chairperson/configurations");
        int broad = security.indexOf("/chairperson/**");

        assertThat(processed).isGreaterThanOrEqualTo(0).isLessThan(broad);
        assertThat(configurations).isGreaterThanOrEqualTo(0).isLessThan(broad);
        assertThat(security.substring(processed, broad))
            .contains("canViewProcessedLoans")
            .contains("canViewSaccoConfigurations")
            .doesNotContain("hasRole")
            .doesNotContain("workspaceAdminOnly");
    }

    @Test
    void pagesAreReadOnlyAndSidebarLinksAreClaimGated() throws IOException {
        String list = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/chairperson/processed-loans.jsp"));
        String detail = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/chairperson/processed-loan-detail.jsp"));
        String configurations = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/chairperson/configurations.jsp"));
        String product = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/chairperson/loan-product-configuration.jsp"));
        String sidebar = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/fragments/sidebar.jspf"));

        assertThat(detail + product).doesNotContain("<form").doesNotContain("method=\"post\"");
        assertThat(detail)
            .contains("include file=\"../fragments/staff-loan-detail-header.jspf\"")
            .contains("include file=\"../fragments/staff-loan-review-summary.jspf\"")
            .contains("include file=\"../fragments/financial-field-sections.jspf\"")
            .contains("include file=\"../fragments/staff-repayment-summary.jspf\"")
            .contains("processedLoanDetail")
            .doesNotContain("reviewAction")
            .doesNotContain("decisionForm");
        assertThat(list + configurations).doesNotContain("method=\"post\"");
        assertThat(list).contains("&middot;")
            .doesNotContain("Â")
            .doesNotContain(" /> · <");
        assertThat(sidebar).contains("@access.canViewProcessedLoans(principal)")
            .contains("@access.canViewSaccoConfigurations(principal)");
    }

    @Test
    void configurationViewsUseLocalizedValuesAndCompactAlignedTables() throws IOException {
        String overview = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/chairperson/configurations.jsp"));
        String product = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/chairperson/loan-product-configuration.jsp"));
        String styles = Files.readString(Path.of("src/main/resources/static/css/console-components.css"));

        assertThat(overview + product)
            .contains("workflow.stage.${stage.stage}")
            .contains("erp-table--left-headings")
            .contains("erp-table-scroll-fit")
            .doesNotContain("â")
            .doesNotContain("<c:out value=\"${product.savingsCheckRequired}\"")
            .doesNotContain("<c:out value=\"${product.status}\"");
        assertThat(overview)
            .contains("General Applicant Qualifications & Guarantor Policies")
            .contains("chairperson.otpConfiguration")
            .doesNotContain("chairperson.saccoDefaults")
            .doesNotContain("configuration.defaults")
            .doesNotContain("chairperson-config-source")
            .doesNotContain("Default approval flow")
            .doesNotContain("chairperson-config-flow-row");
        assertThat(product).contains("chairperson-product-code")
            .contains("chairperson.votingSummary")
            .contains("product.guarantorSavingsCheckRequired");
        assertThat(styles).contains(".aws-console .erp-table-scroll-fit .erp-table")
            .contains(".aws-console .erp-table--left-headings th")
            .contains("overflow-wrap: anywhere");
    }

    @Test
    void processedLoansFiltersDateStatusAndPreservesThemThroughPagination() throws IOException {
        String list = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/chairperson/processed-loans.jsp"));
        String repository = Files.readString(Path.of(
            "src/main/java/com/sacco/mvp/repository/LoanApplicationRepository.java"));

        assertThat(list)
            .contains("name=\"fromDate\"")
            .contains("name=\"toDate\"")
            .contains("name=\"status\"")
            .contains("processedLoans.availableStatuses")
            .contains("<c:param name=\"fromDate\"")
            .contains("<c:param name=\"toDate\"")
            .contains("<c:param name=\"status\"");
        assertThat(repository)
            .contains("l.updatedAt >= :updatedFrom")
            .contains("l.updatedAt < :updatedToExclusive")
            .contains("l.status = :status")
            .contains("Page<LoanApplication> findProcessedLoansPage");
    }
}
