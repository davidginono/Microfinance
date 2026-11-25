package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminConfigurationUiContractTest {
    private static final Path JSP_ROOT = Path.of("src/main/webapp/WEB-INF/jsp");

    @Test
    void loanProductDeletionRequiresTypedConfirmation() throws Exception {
        String productEdit = read(JSP_ROOT.resolve("admin/loan-product-edit.jsp"));
        String confirmModal = read(JSP_ROOT.resolve("fragments/confirm-modal.jspf"));

        assertThat(productEdit)
            .contains("data-confirm-text=\"Delete product\"")
            .contains("data-confirm-proceed=\"Delete Product\"");
        assertThat(confirmModal)
            .contains("id=\"appConfirmTextInput\"")
            .contains("Type <strong id=\"appConfirmRequiredText\"></strong> to confirm.")
            .contains("textInput.value.trim() === requiredText")
            .contains("proceed.disabled = !confirmed")
            .contains("(requiredText ? textInput : proceed).focus");
    }

    @Test
    void loanProductFormsRestoreSubmittedDraftAfterValidationError() throws Exception {
        String productEdit = read(JSP_ROOT.resolve("admin/loan-product-edit.jsp"));
        String settings = read(JSP_ROOT.resolve("admin/settings-controls.jsp"));
        String workflowScript = read(JSP_ROOT.resolve("fragments/loan-product-editor-script.jspf"));
        String controller = read(Path.of("src/main/java/com/sacco/mvp/web/AdminController.java"));

        assertThat(productEdit)
            .contains("data-loan-product-form-draft")
            .contains("data-loan-product-form-value=\"${fn:escapeXml(draftField.key)}\"");
        assertThat(settings)
            .contains("openProductModalKey eq 'create-product' and not empty loanProductFormDraft")
            .contains("data-loan-product-form-draft")
            .contains("data-loan-product-form-value=\"${fn:escapeXml(draftField.key)}\"");
        assertThat(workflowScript)
            .contains("restoreLoanProductFormDraft(form)")
            .contains("restoreLoanProductAttachmentRows(form, valuesByName)")
            .contains("const checkboxes = fields.filter((field) => field.type === 'checkbox')")
            .contains("field.checked = values.includes(field.value)");
        assertThat(controller)
            .contains("LOAN_PRODUCT_BOOLEAN_FIELDS")
            .contains("\"committeeReviewRequired\"")
            .contains("attachLoanProductFormDraft(ra, request)");
    }

    @Test
    void createdLoanProductReturnsToCatalogAndFocusesItsRow() throws Exception {
        String settings = read(JSP_ROOT.resolve("admin/settings-controls.jsp"));
        String workflowScript = read(JSP_ROOT.resolve("fragments/loan-product-editor-script.jspf"));
        String controller = read(Path.of("src/main/java/com/sacco/mvp/web/AdminController.java"));

        assertThat(controller)
            .contains("ra.addFlashAttribute(\"createdLoanProductId\", product.getId())")
            .contains("return \"redirect:/admin/settings-controls?section=loan\"");
        assertThat(settings)
            .contains("id=\"loan-product-${product.id}\"")
            .contains("data-created-loan-product-target=\"${createdLoanProductId eq product.id}\"");
        assertThat(workflowScript)
            .contains("closeAllProductModals()")
            .contains("closeAllProductModals()")
            .contains("createdLoanProductTarget.scrollIntoView")
            .contains("createdLoanProductTarget.focus({ preventScroll: true })");
    }

    @Test
    void loanProductVersionHistoryIsRemovedAcrossUiApplicationAndSchema() throws Exception {
        String settings = read(JSP_ROOT.resolve("admin/settings-controls.jsp"));
        String controller = read(Path.of("src/main/java/com/sacco/mvp/web/AdminController.java"));
        String service = read(Path.of("src/main/java/com/sacco/mvp/service/AdminService.java"));
        String migration = read(Path.of("src/main/resources/db/migration/V23__remove_loan_product_versions.sql"));

        assertThat(settings)
            .doesNotContain("Step 1 of 2")
            .doesNotContain("Loan Products Versions")
            .doesNotContain("Product Versions")
            .doesNotContain("/versions/");
        assertThat(controller)
            .doesNotContain("rollbackLoanProductVersion")
            .doesNotContain("loanProductsVersionHistory")
            .doesNotContain("productVersionsByProductId");
        assertThat(service)
            .doesNotContain("LoanProductVersion")
            .doesNotContain("LoanProductsVersion")
            .doesNotContain("saveLoanProductSnapshot");
        assertThat(migration)
            .contains("DROP TABLE IF EXISTS public.loan_product_versions")
            .contains("DROP TABLE IF EXISTS public.loan_products_versions");
    }

    @Test
    void applicantAndGuarantorSettingsUseCloudscapeSettingsSurface() throws Exception {
        String settings = read(JSP_ROOT.resolve("admin/settings-controls.jsp"));
        String consoleCss = read(Path.of("src/main/resources/static/css/console-components.css"));

        assertThat(settings)
            .contains("class=\"erp-panel aws-settings-panel aws-qualification-settings mb-4\"")
            .contains("aria-labelledby=\"qualification-settings-title\"")
            .contains("id=\"qualification-settings-title\" class=\"aws-settings-title\"")
            .contains("class=\"aws-settings-form aws-qualification-form\"")
            .doesNotContain("class=\"aws-settings-commandbar\"")
            .contains("class=\"aws-settings-footer\"")
            .contains("class=\"app-btn btn-launch aws-settings-save-action\"")
            .contains("class=\"aws-settings-grid aws-qualification-grid\"")
            .contains("class=\"aws-settings-group aws-qualification-applicants-group\"")
            .contains("class=\"aws-settings-group-header\"")
            .contains("class=\"aws-settings-checkbox-row\"")
            .contains("class=\"aws-settings-field aws-settings-field--compact\"")
            .contains("class=\"aws-control\"")
            .contains("name=\"applicantMaxDefaultedLoans\"")
            .contains("name=\"guarantorWithActiveLoanAllowed\"")
            .contains("name=\"guarantorMaxGuaranteedLoanAmount\"")
            .contains("name=\"guarantorMaxDefaultedLoans\"")
            .contains("id=\"portfolio-risk-policy-title\"")
            .contains("class=\"aws-settings-group aws-qualification-risk-group\"")
            .contains("name=\"portfolioAtRiskDays\"")
            .doesNotContain("settings-action-bar settings-action-bar--split");
        int guarantorHeaderIndex = settings.indexOf("id=\"guarantor-policy-title\"");
        int portfolioHeaderIndex = settings.indexOf("id=\"portfolio-risk-policy-title\"");
        int portfolioInputIndex = settings.indexOf("name=\"portfolioAtRiskDays\"");
        assertThat(guarantorHeaderIndex).isGreaterThanOrEqualTo(0);
        assertThat(portfolioHeaderIndex).isGreaterThan(guarantorHeaderIndex);
        assertThat(portfolioInputIndex).isGreaterThan(portfolioHeaderIndex);
        assertThat(settings.substring(guarantorHeaderIndex, portfolioHeaderIndex))
            .doesNotContain("name=\"portfolioAtRiskDays\"");
        assertThat(consoleCss)
            .doesNotContain(".aws-qualification-settings .aws-settings-commandbar")
            .contains(".aws-qualification-settings .aws-qualification-grid")
            .contains("max-width: none !important")
            .contains(".aws-settings-checkbox-row")
            .contains("grid-template-columns: 16px minmax(0, 1fr)")
            .contains(".aws-settings-policy-grid")
            .contains(".aws-qualification-risk-group")
            .contains("grid-column: 1 / -1")
            .contains(".aws-settings-risk-body")
            .contains(".aws-settings-checkbox-row:focus-within")
            .contains("outline: none")
            .contains(".aws-settings-checkbox-row:has(.aws-settings-checkbox:focus-visible)")
            .contains(".aws-settings-checkbox:focus-visible")
            .contains("accent-color: var(--aws-blue, #0972d3)")
            .contains("@media (max-width: 900px)")
            .contains("@media (max-width: 639px)");
    }

    @Test
    void applicationNoticesExpireOnlyWhenDismissed() throws Exception {
        String shellScript = read(Path.of("src/main/resources/static/js/shell.js"));
        String login = read(JSP_ROOT.resolve("login.jsp"));

        assertThat(shellScript)
            .contains("closeButton?.addEventListener('click', function (event)")
            .contains("event.preventDefault()")
            .contains("event.stopPropagation()")
            .doesNotContain("window.setTimeout(entry.dismiss")
            .doesNotContain("window.setTimeout(dismiss, duration)");
        assertThat(login).doesNotContain("window.setTimeout(dismiss, duration)");
    }

    @Test
    void smsTablesAndSharedPageHeaderUseAwsConsoleStructure() throws Exception {
        String jsp=read(JSP_ROOT.resolve("admin/sms-usage.jsp"));
        assertThat(jsp).contains("app-table-titlebar", "aws-filter-toolbar", "erp-table-scroll", "data-sms-applicant-filter", "/documents/reports/sms-usage.pdf").doesNotContain("sms-usage.xlsx", "sms-usage.csv");
        assertThat(read(Path.of("src/main/resources/static/js/shell.js"))).contains("enhancePageBreadcrumbs");
    }

    @Test
    void systemAdminInstitutionLoanPortfolioUsesLocalizedScopedSummary() throws Exception {
        String saccoDetail = read(JSP_ROOT.resolve("admin/sacco-detail.jsp"));

        assertThat(saccoDetail)
            .contains("selectedSection eq 'loans'")
            .contains("code=\"admin.institutionDetails.loanPortfolio\"")
            .contains("code=\"admin.institutionDetails.applications\"")
            .contains("code=\"admin.institutionDetails.disbursed\"")
            .contains("code=\"admin.institutionDetails.loanStatuses\"")
            .contains("code=\"admin.institutionDetails.noLoans\"")
            .contains("${saccoDetail.totalLoanApplicationCount}")
            .contains("${saccoDetail.summary.totalDisbursedPrincipalFullLabel}")
            .contains("${saccoDetail.loanStatusCounts}")
            .contains("data-institution-resource-link", "${saccoDetail.stationOptions}")
            .doesNotContain("data-institution-scope", "data-institution-reset", "admin.institutionDetails.branchScope")
            .contains("name=\"stationId\"")
            .doesNotContain("Paid Loans")
            .doesNotContain("Overdue Unpaid")
            .doesNotContain("Recent Loan Activity")
            .doesNotContain("saccoDetail.recentLoans")
            .doesNotContain("saccoDetail.paidLoanCount")
            .doesNotContain("saccoDetail.overdueLoanCount");
        for (String language : java.util.List.of("en", "sw")) {
            var messages = new java.util.Properties();
            try (var reader = Files.newBufferedReader(Path.of("src/main/resources/messages_" + language + ".properties"))) {
                messages.load(reader);
            }
            for (String key : java.util.List.of("loanPortfolio", "applications", "disbursed", "loanStatuses", "noLoans")) {
                assertThat(messages.getProperty("admin.institutionDetails." + key)).as(language + " " + key).isNotBlank();
            }
        }
    }

    @Test
    void smsUsageLoanReportQueryCastsNullableFiltersForPostgresql() throws Exception {
        String repository = read(Path.of("src/main/java/com/sacco/mvp/repository/SmsUsageLedgerRepository.java"));

        assertThat(repository)
            .contains("cast(:fromAt as timestamp) is null")
            .contains("cast(:toAt as timestamp) is null")
            .contains("cast(:loanStatus as string) is null")
            .doesNotContain(":fromAt is null")
            .doesNotContain(":toAt is null")
            .doesNotContain(":loanStatus is null");
    }

    @Test
    void sidebarShowsOneActiveStaffRailAndUsesOrangeEmblemWithExistingWordmark() throws Exception {
        String sidebar=read(JSP_ROOT.resolve("fragments/sidebar.jspf"));
        assertThat(sidebar).contains("staff-offices.jspf", "/account/security", "shell-sidebar-subitem-label", "shell-powered-by-wordmark", "finance-navigation.jspf");
        assertThat(sidebar).doesNotContain("/app/", "showManagerPanel", "showBoardPanel");
        assertThat(read(Path.of("src/main/resources/static/css/shell.css"))).contains("--sacco-topbar: #232f3e", "--sacco-sidebar: #ffffff", "#0073bb", ".shell-support-contact__link");
    }

    @Test
    void shellRestoresEveryWorkspaceAndRepairsLegacyTableScrollRegions() throws Exception {
        String shellScript = read(Path.of("src/main/resources/static/js/shell.js"));

        assertThat(shellScript)
            .contains("window.history.scrollRestoration = 'manual'")
            .contains("scrollRestoreStorageKeyPrefix")
            .contains("currentScrollRestoreStorageKey()")
            .contains(".shell-sidebar-scroll, .shell-main")
            .contains("window.addEventListener('scroll', scheduleScrollStateSave")
            .contains("ensureConsoleTableScroller")
            .contains("scroller.classList.add(modifier)")
            .contains("region.classList.remove('erp-table-scroll')")
            .doesNotContain("isAdminWorkspace");
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
            .contains("type=\"submit\" class=\"app-btn btn-launch\"><spring:message code=\"admin.workspace.createStaff\"");
        assertThat(shellCss)
            .contains(".app-topbar-actions .app-profile-control")
            .contains(".aws-console .app-topbar .profile-icon-btn")
            .contains("border-radius: 50% !important");
    }

    private String read(Path path) throws Exception {
        return Files.readString(path);
    }
}
