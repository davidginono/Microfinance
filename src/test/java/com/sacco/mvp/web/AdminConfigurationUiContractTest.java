package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminConfigurationUiContractTest {
    private static final Path JSP_ROOT = Path.of("src/main/webapp/WEB-INF/jsp");

    @Test
    void focusedLoanProductOwnsChangeDrivenWorkflowWarnings() throws Exception {
        String productEdit = read(JSP_ROOT.resolve("admin/loan-product-edit.jsp"));
        String settings = read(JSP_ROOT.resolve("admin/settings-controls.jsp"));
        String workflowScript = read(JSP_ROOT.resolve("fragments/loan-product-workflow-script.jspf"));

        assertThat(productEdit)
            .contains("data-workflow-notifications=\"focused\"")
            .contains("data-workflow-warnings")
            .contains("erp-table-scroll erp-table-scroll-sm")
            .contains("data-board-reviewer-option")
            .contains("data-credit-committee-reviewer-option")
            .contains("<c:if test=\"${not empty error}\">")
            .doesNotContain("openProductModalKey eq productModalKey")
            .doesNotContain("product-config-step-description")
            .doesNotContain("data-product-config-toggle")
            .doesNotContain("product-config-step-action")
            .doesNotContain("product-config-progress");
        assertThat(settings).doesNotContain("data-workflow-notifications=\"focused\"");
        assertThat(workflowScript)
            .contains("applyRules({ notify: true })")
            .contains("previousWorkflowWarningKeys")
            .contains("workflowWarningsInitialized")
            .contains("block.dataset.workflowWarningTone")
            .doesNotContain("block.setAttribute('data-toast-message'")
            .doesNotContain("progressMeter")
            .doesNotContain("data-product-config-progress");
    }

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
            .contains("class=\"aws-settings-commandbar\"")
            .contains("class=\"app-btn btn-primary aws-settings-save-action\"")
            .contains("class=\"aws-settings-grid aws-qualification-grid\"")
            .contains("class=\"aws-settings-group\"")
            .contains("class=\"aws-settings-group-header\"")
            .contains("class=\"aws-settings-checkbox-row\"")
            .contains("class=\"aws-settings-field aws-settings-field--compact\"")
            .contains("class=\"aws-control\"")
            .contains("name=\"applicantMaxDefaultedLoans\"")
            .contains("name=\"guarantorWithActiveLoanAllowed\"")
            .contains("name=\"guarantorMaxGuaranteedLoanAmount\"")
            .contains("name=\"guarantorMaxDefaultedLoans\"")
            .doesNotContain("settings-action-bar settings-action-bar--split");
        assertThat(consoleCss)
            .contains(".aws-qualification-settings .aws-settings-commandbar")
            .contains("grid-template-columns: minmax(0, 1fr) auto")
            .contains(".aws-qualification-settings .aws-qualification-grid")
            .contains("max-width: none !important")
            .contains(".aws-settings-checkbox-row")
            .contains("grid-template-columns: 16px minmax(0, 1fr)")
            .contains(".aws-settings-policy-grid")
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
        String registration = read(JSP_ROOT.resolve("register-member.jsp"));

        assertThat(shellScript)
            .contains("closeButton?.addEventListener('click', function (event)")
            .contains("event.preventDefault()")
            .contains("event.stopPropagation()")
            .doesNotContain("window.setTimeout(entry.dismiss")
            .doesNotContain("window.setTimeout(dismiss, duration)");
        assertThat(login).doesNotContain("window.setTimeout(dismiss, duration)");
        assertThat(registration).doesNotContain("window.setTimeout(dismiss, duration)");
    }

    @Test
    void smsTablesAndSharedPageHeaderUseAwsConsoleStructure() throws Exception {
        String smsUsage = read(JSP_ROOT.resolve("admin/sms-usage.jsp"));
        String shellCss = read(Path.of("src/main/resources/static/css/shell.css"));
        String shellScript = read(Path.of("src/main/resources/static/js/shell.js"));

        assertThat(smsUsage)
            .contains("Station SMS Balances")
            .contains("app-table-titlebar")
            .contains("aws-filter-toolbar")
            .contains("erp-table-scroll")
            .contains("class=\"erp-table\"")
            .contains("aws-pagination-chevron")
            .contains("entry.createdAtLabel")
            .doesNotContain("erp-page-subtitle");
        assertThat(shellCss)
            .contains(".erp-page-header[data-aws-page-header]")
            .contains("position: sticky")
            .contains(".shell-page-title-rail")
            .contains("display: none !important")
            .contains(".erp-page-path")
            .contains("padding-top: 0 !important")
            .contains("overflow-x: clip !important")
            .contains("overflow-y: visible !important")
            .contains(".erp-page-subtitle");
        assertThat(shellScript)
            .contains("enhancePageBreadcrumbs")
            .contains("separator.textContent = '>'")
            .contains("breadcrumb.remove()");
    }

    @Test
    void sidebarShowsOneActiveStaffRailAndUsesOrangeEmblemWithExistingWordmark() throws Exception {
        String sidebar = read(JSP_ROOT.resolve("fragments/sidebar.jspf"));
        String supportContact = read(JSP_ROOT.resolve("fragments/platform-support-contact-sidebar.jspf"));
        String shellCss = read(Path.of("src/main/resources/static/css/shell.css"));

        assertThat(sidebar)
            .contains("shell-sidebar-group shell-sidebar-support-group space-y-1")
            .contains("staffDashboardActive ? 'shell-nav-active")
            .contains("staffReportsActive ? 'shell-nav-active")
            .contains("/staff/settings' ? 'shell-nav-active")
            .contains("shell-sidebar-subitem-label")
            .contains("menu.settings.language")
            .contains("Language &amp; Preferences")
            .contains("/images/computer-resources-mark-orange.png")
            .contains("Computer Resources")
            .contains("(T) Limited")
            .contains("shell-powered-by-copy")
            .contains("shell-powered-by-wordmark");
        assertThat(supportContact)
            .contains("shell-support-contact")
            .contains("shell-support-contact__link")
            .contains("platformSupportContact.officeHours ne '-'")
            .contains("platformSupportContact.supportNote ne '-'");
        assertThat(shellCss)
            .contains(".shell-review-panel-link.shell-nav-active")
            .contains(".shell-sidebar-group > div > a.shell-nav-active")
            .contains(".shell-sidebar-group > div > a:hover")
            .contains(".shell-sidebar-subitem-label")
            .contains("border-left-color: #ec7211 !important")
            .contains("color: #c4ced4 !important")
            .contains("max-width: calc(100% - 16px)")
            .contains("overflow-wrap: anywhere")
            .contains(".shell-support-contact__link")
            .contains("grid-template-columns: max-content minmax(0, 1fr)")
            .containsPattern("(?s)\\.shell-support-contact__label\\s*\\{[^}]*white-space:\\s*nowrap;")
            .contains("width: 42px !important")
            .contains("width: 22px !important")
            .containsPattern("(?s)\\.shell-sidebar-toggle-row\\s*\\{[^}]*padding:\\s*0 !important;")
            .containsPattern("(?s)\\.shell-sidebar-toggle-label\\s*\\{[^}]*padding-left:\\s*12px !important;")
            .contains(".shell-powered-by-copy")
            .doesNotContain(".shell-sidebar-group > div a[class*=\"bg-white\"]")
            .doesNotContain(".shell-sidebar-scroll .shell-review-panel-link[class*=\"bg-white\"]")
            .contains("background: #111d2c !important");
    }

    @Test
    void incidentAndManagerArchiveFiltersLeaveNoEmptyToolbarBars() throws Exception {
        String incidents = read(JSP_ROOT.resolve("admin/incidents.jsp"));
        String managerArchive = read(JSP_ROOT.resolve("manager/archive.jsp"));
        String shellCss = read(Path.of("src/main/resources/static/css/shell.css"));

        assertThat(incidents)
            .contains("class=\"admin-filter-form admin-filter-bar aws-filter-toolbar\"")
            .containsPattern("(?s)<section class=\"erp-table-wrap\"[^>]*>\\s*<form action=\"/admin/incidents\"")
            .doesNotContain("/admin/notifications/mark-all-read")
            .doesNotContain("<section class=\"erp-form-wrap\">\n<form action=\"/admin/incidents\"");
        assertThat(managerArchive)
            .contains("<div class=\"erp-filter-row\">")
            .contains("class=\"erp-filter-form manager-archive-search-form aws-filter-toolbar\"")
            .doesNotContain("common.currentFilter")
            .doesNotContain("<div class=\"erp-toolbar\">");
        assertThat(shellCss)
            .contains(".aws-console .aws-filter-toolbar label > .neo-select")
            .contains("height: var(--sacco-control-height) !important")
            .contains("max-height: var(--sacco-control-height) !important")
            .contains("padding-top: 0 !important")
            .contains("margin-top: 4px !important");
    }

    @Test
    void staffArchiveChromeStaysOutsideTheTableScrollViewport() throws Exception {
        for (String relativePath : new String[] {
            "manager/archive.jsp",
            "accountant/archive.jsp",
            "disbursement/archive.jsp"
        }) {
            String archive = read(JSP_ROOT.resolve(relativePath));

            assertThat(archive)
                .contains("class=\"erp-table-wrap\"")
                .contains("class=\"erp-table-scroll\" data-view-position-key=")
                .contains("class=\"erp-table\"")
                .doesNotContain("erp-table-wrap erp-table-scroll");
        }
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
            .contains("type=\"submit\" class=\"app-btn btn-launch\">Create Staff Member");
        assertThat(shellCss)
            .contains(".app-topbar-actions .app-profile-control")
            .contains(".aws-console .app-topbar .profile-icon-btn")
            .contains("border-radius: 50% !important");
    }

    private String read(Path path) throws Exception {
        return Files.readString(path);
    }
}
