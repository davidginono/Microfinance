package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AwsConsoleViewContractTest {
    private static final Path JSP_ROOT = Path.of("src/main/webapp/WEB-INF/jsp");

    @Test
    void everyRouteUsesTheAwsConsoleOrAuthShell() throws Exception {
        List<Path> routes = routeViews();

        assertThat(routes).hasSize(69);
        assertThat(routes).allSatisfy(path -> {
            String view = read(path);
            assertThat(view)
                .as(path.toString())
                .satisfiesAnyOf(
                    content -> assertThat(content).contains("fragments/header.jspf"),
                    content -> assertThat(content).contains("aws-auth.css")
                );
        });
    }

    @Test
    void authenticatedPagesExposeAwsHeadersAndRegisterContracts() throws Exception {
        for (Path path : routeViews()) {
            String view = read(path);
            if (view.contains("fragments/header.jspf")) {
                assertThat(view).as(path.toString()).contains("data-aws-page-header");
            }
            if (view.contains("<table")) {
                assertThat(view)
                    .as(path.toString())
                    .contains("erp-table")
                    .contains("data-aws-table-region");
            }
            if (view.matches("(?s).*method=[\"']get[\"'].*")) {
                assertThat(view)
                    .as(path.toString())
                    .contains("data-aws-filter-toolbar");
            }
        }
    }

    @Test
    void jspMarkupContainsNoLocalStylesOrAutomaticFilterSubmission() throws Exception {
        try (Stream<Path> paths = Files.walk(JSP_ROOT)) {
            paths.filter(path -> path.toString().endsWith(".jsp") || path.toString().endsWith(".jspf"))
                .forEach(path -> {
                    String view = read(path);
                    assertThat(view)
                        .as(path.toString())
                        .doesNotContain("<style")
                        .doesNotContain("style=")
                        .doesNotContain("data-auto-submit")
                        .doesNotContain("onchange=")
                        .doesNotContain("oninput=");
                });
        }
    }

    @Test
    void sharedAssetsProvideAwsShellTableAndResponsiveFilterPrimitives() throws Exception {
        String header = read(JSP_ROOT.resolve("fragments/header.jspf"));
        String login = read(JSP_ROOT.resolve("login.jsp"));
        String registration = read(JSP_ROOT.resolve("register-member.jsp"));
        String fontCss = read(Path.of("src/main/resources/static/css/open-sans.css"));
        String shellCss = read(Path.of("src/main/resources/static/css/shell.css"));
        String authCss = read(Path.of("src/main/resources/static/css/aws-auth.css"));
        String consoleCss = read(Path.of("src/main/resources/static/css/console-components.css"));
        String securityConfig = read(Path.of("src/main/java/com/sacco/mvp/config/SecurityConfig.java"));
        String shellJs = read(Path.of("src/main/resources/static/js/shell.js"));

        assertThat(header)
            .contains("open-sans-400.woff2")
            .contains("open-sans-700.woff2")
            .contains("open-sans.css?v=20260805-cloudscape-type-v2")
            .contains("console-components.css")
            .contains("shell.css?v=20260805-nav-toast-v3")
            .contains("shell.js?v=20260805-nav-toast-v3")
            .contains("app-global-logo")
            .contains("app-global-logo-image")
            .contains("activeSaccoLogoUrl")
            .contains("/profile/image/me")
            .contains("profile-avatar-img")
            .contains("aws-console")
            .contains("app-topbar")
            .contains("app-global-search");
        assertThat(fontCss)
            .contains("font-family: \"Open Sans\"")
            .contains("open-sans-300.woff2")
            .contains("open-sans-400.woff2")
            .contains("open-sans-700.woff2")
            .contains("open-sans-800.woff2")
            .contains("font-display: swap");
        assertThat(securityConfig)
            .contains("\"/fonts/**\"")
            .contains(".permitAll()");
        assertThat(shellCss)
            .contains("--sacco-topbar: #101820")
            .contains("--sacco-font-family: \"Open Sans\", Helvetica, Arial, sans-serif")
            .contains("--sacco-sidebar: #183038")
            .contains("--sacco-canvas: #eaeded")
            .contains("--sacco-control-height: 32px")
            .contains("--aws-orange")
            .contains("--aws-sidebar: #183038")
            .contains("--shell-sidebar-width: 236px")
            .contains("--shell-nav-height: 52px")
            .contains(".aws-filter-toolbar")
            .contains("flex-wrap: wrap !important")
            .contains("width: calc(100% - var(--shell-sidebar-width)) !important")
            .contains("max-width: calc(100vw - 32px) !important")
            .contains(".app-table-titlebar")
            .contains("#appToastContainer .app-toast-close")
            .contains("transform: translateY(-50%) !important")
            .contains(".app-global-brand .app-global-logo")
            .contains(".app-topbar .profile-icon-btn .profile-avatar-img")
            .doesNotContain("Amazon Ember")
            .doesNotContain("Manrope")
            .doesNotContain("Sora");
        assertThat(authCss)
            .contains("font-family: var(--sacco-font-family) !important")
            .contains(".auth-notification-bar button[type=\"button\"].app-toast-close")
            .contains("width: 24px !important")
            .contains("transform: translateY(-50%) !important")
            .doesNotContain("Amazon Ember")
            .doesNotContain("Manrope")
            .doesNotContain("Sora");
        assertThat(consoleCss)
            .contains("font-family: \"Open Sans\", Helvetica, Arial, sans-serif")
            .doesNotContain("Amazon Ember")
            .doesNotContain("Manrope")
            .doesNotContain("Sora");
        assertThat(login)
            .contains("aws-auth.css?v=20260805-nav-toast-v3")
            .contains("class=\"app-toast-close-icon\"");
        assertThat(registration)
            .contains("aws-auth.css?v=20260805-nav-toast-v3")
            .contains("class=\"app-toast-close-icon\"")
            .doesNotContain("&times;");
        assertThat(shellJs)
            .contains("app-toast-close-icon")
            .doesNotContain("&times;")
            .contains("enhanceConsoleTables")
            .contains("showConsoleTableLoading")
            .contains("syncConsoleFiltersFromUrl")
            .contains("aws-pagination-chevron")
            .contains("maxVisibleToasts = 3")
            .contains("promoteQueuedToast");
    }

    @Test
    void loginAndLoanJourneysExposeProgressiveAwsInteractions() throws Exception {
        String login = read(JSP_ROOT.resolve("login.jsp"));
        String loanProducts = read(JSP_ROOT.resolve("app/loan-products.jsp"));
        String loanApplication = read(JSP_ROOT.resolve("app/loan-new.jsp"));
        String authCss = read(Path.of("src/main/resources/static/css/aws-auth.css"));

        assertThat(login)
            .contains("role=\"tablist\"")
            .contains("aria-selected")
            .contains("panel.hidden = !active")
            .contains("auth-notification-rail");
        assertThat(authCss)
            .contains(".auth-notification-bar")
            .contains("inset: 52px 0 auto")
            .contains("--sacco-topbar-height: 52px")
            .contains("--sacco-control-height: 32px")
            .contains("--aws-topbar: #101820")
            .contains("transform: none !important");
        assertThat(loanProducts)
            .contains("loan-calculator-steps")
            .contains("productsCalculatorResults")
            .contains("productsCalculatorApplyLink")
            .contains("setCalculatorStage");
        assertThat(loanApplication)
            .contains("data-loan-flow-step=\"1\"")
            .contains("data-loan-flow-step=\"4\"")
            .contains("data-loan-step-next")
            .contains("data-aws-no-refresh=\"true\"")
            .contains("Continue to Guarantees")
            .contains("Continue to Save&amp;Send")
            .doesNotContain("Continue to Documents")
            .doesNotContain("Continue to Review")
            .contains("validateCurrentStep");
    }

    @Test
    void memberMobileAnalyticsAndShellStateUsePinnedResponsiveContracts() throws Exception {
        String reports = read(JSP_ROOT.resolve("app/reports.jsp"));
        String sidebar = read(JSP_ROOT.resolve("fragments/sidebar.jspf"));
        String shellCss = read(Path.of("src/main/resources/static/css/shell.css"));
        String consoleCss = read(Path.of("src/main/resources/static/css/console-components.css"));
        String shellJs = read(Path.of("src/main/resources/static/js/shell.js"));

        assertThat(reports)
            .contains("data-aws-filter-pin=\"true\"")
            .contains("data-aws-action-pin=\"true\"")
            .contains("data-download-action=\"true\"");
        assertThat(sidebar).contains("data-view-position-key=\"sidebar-navigation\"");
        assertThat(consoleCss)
            .contains(".loan-analytics-command-panel .loan-analytics-actions")
            .contains("grid-template-columns: repeat(2, minmax(0, 1fr))");
        assertThat(shellCss)
            .contains(".app-table-heading h2")
            .contains("overflow-wrap: anywhere")
            .contains(".shell-sidebar-flyout-link.is-active")
            .contains("background: transparent !important")
            .contains("box-shadow: none !important");
        assertThat(shellJs)
            .contains("isConsoleDownloadAction")
            .contains("data-aws-no-refresh")
            .contains("dataset.awsFilterPin")
            .contains("data-aws-action-pin")
            .contains("restoreScrollablePositions")
            .contains("clearConsoleTableLoading");
    }

    @Test
    void financialStatusAndSettingsUseSharedAwsContainerPrimitives() throws Exception {
        String dashboard = read(JSP_ROOT.resolve("app/dashboard.jsp"));
        String memberSettings = read(JSP_ROOT.resolve("app/settings.jsp"));
        String staffSettings = read(JSP_ROOT.resolve("staff/settings.jsp"));
        String platformSettings = read(JSP_ROOT.resolve("admin/platform-settings.jsp"));
        String shellCss = read(Path.of("src/main/resources/static/css/shell.css"));

        assertThat(dashboard)
            .contains("aws-status-summary-grid")
            .contains("aws-status-summary-item")
            .contains("aws-status-summary-label")
            .contains("aws-status-summary-value")
            .contains("aws-disclosure-button")
            .contains("aws-dashboard-detail-panel")
            .contains("aws-detail-grid")
            .contains("aws-table-pagination-footer");
        assertThat(memberSettings)
            .contains("aws-settings-panel")
            .contains("aws-settings-control-row")
            .contains("aws-settings-grid")
            .contains("aws-settings-footer")
            .doesNotContain("member-settings-action-bar");
        assertThat(staffSettings)
            .contains("aws-settings-panel")
            .contains("aws-settings-control-row")
            .contains("aws-settings-footer")
            .doesNotContain("staff-settings-action-bar");
        assertThat(platformSettings)
            .contains("aws-settings-panel")
            .contains("aws-settings-subsection")
            .doesNotContain("platform-settings-card rounded-md");
        assertThat(shellCss)
            .contains(".aws-status-summary-grid")
            .contains(".aws-settings-header")
            .contains(".aws-settings-control-row")
            .contains(".aws-dashboard-detail-panel")
            .contains(".aws-disclosure-button")
            .contains(".shell-page-title-rail__inner")
            .contains("backdrop-filter: none !important")
            .contains("height: 32px !important")
            .contains("font-size: 14px")
            .contains("border-radius: 2px");
    }

    @Test
    void adminLoanProductsUseCatalogSelectionAndProgressiveConfiguration() throws Exception {
        String settings = read(JSP_ROOT.resolve("admin/settings-controls.jsp"));
        String productEdit = read(JSP_ROOT.resolve("admin/loan-product-edit.jsp"));
        String workflowScript = read(JSP_ROOT.resolve("fragments/loan-product-workflow-script.jspf"));
        String consoleCss = read(Path.of("src/main/resources/static/css/console-components.css"));
        String shellCss = read(Path.of("src/main/resources/static/css/shell.css"));

        assertThat(settings)
            .contains("Step 1 of 2")
            .contains("Select a loan product")
            .contains("product-catalog-grid")
            .contains("data-product-config-card")
            .contains("data-product-card-toggle")
            .contains("See more")
            .contains("/admin/settings-controls/loan-products/${product.id}/edit")
            .contains("Configure product");
        assertThat(productEdit)
            .contains("Step 2 of 2")
            .contains("data-product-config-workspace")
            .contains("data-product-config-nav=\"identity\"")
            .contains("data-product-config-nav=\"preview\"")
            .contains("data-product-config-step=\"workflow\"")
            .contains("data-product-config-panel hidden")
            .contains("data-product-config-progress-meter")
            .contains("details class=\"workflow-subsection product-config-subsection\"")
            .contains("data-product-config-previous")
            .contains("data-product-config-next")
            .contains("data-product-config-save-state")
            .contains("Save product")
            .contains("action=\"/admin/settings-controls/${product.id}\"");
        assertThat(workflowScript)
            .contains("activateStep")
            .contains("workspace.classList.add('is-enhanced')")
            .contains("'Continue to ' + stepLabel")
            .contains("form.addEventListener('invalid'")
            .contains("disclosure.open = true")
            .contains("saveState.textContent = 'Unsaved changes'")
            .contains("window.sessionStorage.setItem(storageKey")
            .contains("expanded ? 'Show less' : 'See more'");
        assertThat(consoleCss)
            .contains(".product-catalog-grid")
            .contains("grid-template-columns: repeat(2, minmax(0, 1fr))")
            .contains(".product-config-workspace")
            .contains(".product-config-workspace.is-enhanced .product-config-step:not(.is-active)")
            .contains(".product-config-subsection")
            .contains(".product-config-step.is-active")
            .contains(".product-config-actionbar");
        assertThat(shellCss)
            .contains(".aws-console .product-config-workspace input:not([type=\"checkbox\"])")
            .contains("min-height: var(--sacco-control-height) !important")
            .contains(".aws-console .product-catalog .app-btn");
    }

    private static List<Path> routeViews() throws IOException {
        try (Stream<Path> paths = Files.walk(JSP_ROOT)) {
            return paths
                .filter(path -> path.toString().endsWith(".jsp"))
                .sorted()
                .toList();
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }
}
