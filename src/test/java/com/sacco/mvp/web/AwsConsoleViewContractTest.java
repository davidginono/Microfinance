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
        String shellCss = read(Path.of("src/main/resources/static/css/shell.css"));
        String shellJs = read(Path.of("src/main/resources/static/js/shell.js"));

        assertThat(header)
            .contains("console-components.css")
            .contains("aws-console")
            .contains("app-topbar")
            .contains("app-global-search");
        assertThat(shellCss)
            .contains("--aws-orange")
            .contains(".aws-filter-toolbar")
            .contains("flex-wrap: wrap !important")
            .contains("width: calc(100% - var(--shell-sidebar-width)) !important")
            .contains("max-width: calc(100vw - 32px) !important")
            .contains(".app-table-titlebar");
        assertThat(shellJs)
            .contains("enhanceConsoleTables")
            .contains("showConsoleTableLoading")
            .contains("syncConsoleFiltersFromUrl")
            .contains("aws-pagination-chevron");
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
            .contains("inset: 38px 0 auto")
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
            .contains("height: 31px !important")
            .contains("font-size: 14px")
            .contains("border-radius: 2px");
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
