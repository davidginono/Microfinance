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

        assertThat(routes).hasSize(73);
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
                assertThat(view)
                    .as(path.toString())
                    .satisfiesAnyOf(
                        content -> assertThat(content).contains("data-aws-page-header"),
                        content -> assertThat(content).contains("staff-loan-detail-header.jspf")
                    );
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
    void memberDashboardActiveLoansUsesForesightBalanceRefreshControl() throws Exception {
        String dashboard = read(JSP_ROOT.resolve("app/dashboard.jsp"));

        assertThat(dashboard)
            .contains("data-active-loans-refresh")
            .contains("data-refresh-url=\"${pageContext.request.contextPath}/app/dashboard/active-loans/balances/refresh\"")
            .contains("data-aws-no-refresh=\"true\"")
            .contains("M20 11a8.1 8.1 0 0 0-15.5-2M4 4v5h5")
            .contains("data-active-loan-current-balance")
            .contains("data-active-loan-paid-amount")
            .contains("updateActiveLoanBalanceRows(payload.rows)")
            .contains("We could not refresh loan balances right now. Please retry again later.")
            .doesNotContain("data-lucide=\"refresh-cw\"");
    }

    @Test
    void memberDashboardSupportsProgressivePostLoginHydration() throws Exception {
        String dashboard = read(JSP_ROOT.resolve("app/dashboard.jsp"));
        String liveStatusHydration = read(JSP_ROOT.resolve("fragments/live-account-status-hydration.jspf"));
        String workspaceLanding = read(Path.of("src/main/java/com/sacco/mvp/security/WorkspaceLanding.java"));
        String securityConfig = read(Path.of("src/main/java/com/sacco/mvp/config/SecurityConfig.java"));
        String authController = read(Path.of("src/main/java/com/sacco/mvp/web/AuthController.java"));

        assertThat(dashboard)
            .contains("data-member-dashboard-progressive=\"${dashboardProgressive}\"")
            .contains("data-member-dashboard-content-url=\"${pageContext.request.contextPath}/app/dashboard?full=true\"")
            .contains("initProgressiveDashboard()")
            .contains("window.SaccosMemberDashboard")
            .contains("window.SaccosLiveAccountStatus.hydrateAll()")
            .contains("Dashboard content could not load. Refresh this page to try again.");
        assertThat(liveStatusHydration)
            .contains("window.SaccosLiveAccountStatus")
            .contains("hydrateAll: hydrateAllLiveAccountStatuses");
        assertThat(workspaceLanding)
            .contains("memberDashboardAfterLogin()")
            .contains("return memberDashboard() + \"?progressive=true\";");
        assertThat(securityConfig)
            .contains("WorkspaceLanding.memberDashboardAfterLogin()")
            .contains("savedTargetAfterLogin(principal, savedTarget)");
        assertThat(authController)
            .contains("\"redirectUrl\", WorkspaceLanding.memberDashboardAfterLogin()");
    }

    @Test
    void conciseWorkspaceHeadersDoNotExposePageSubtitles() throws Exception {
        for (String workspace : List.of("app", "manager", "board", "loan-officer", "disbursement")) {
            try (Stream<Path> paths = Files.walk(JSP_ROOT.resolve(workspace))) {
                paths.filter(path -> path.toString().endsWith(".jsp"))
                    .forEach(path -> assertThat(read(path))
                        .as(path.toString())
                        .doesNotContain("erp-page-subtitle"));
            }
        }
    }

    @Test
    void roleQueueCurrentFiltersUseCompactAwsSummaryStrips() throws Exception {
        String managerQueue = read(JSP_ROOT.resolve("manager/queue.jsp"));
        List<String> currentFilterViews = List.of(
            managerQueue,
            read(JSP_ROOT.resolve("disbursement/queue.jsp")),
            read(JSP_ROOT.resolve("disbursement/archive.jsp")),
            read(JSP_ROOT.resolve("board/assigned.jsp")),
            read(JSP_ROOT.resolve("accountant/queue.jsp")),
            read(JSP_ROOT.resolve("accountant/archive.jsp"))
        );
        String consoleCss = read(Path.of("src/main/resources/static/css/console-components.css"));

        assertThat(managerQueue)
            .contains("<section class=\"aws-current-filter-toolbar manager-queue-filter-toolbar\" aria-labelledby=\"managerQueueCurrentFilterLabel\">")
            .contains("id=\"managerQueueCurrentFilterLabel\"")
            .contains("manager-queue-filter-summary")
            .contains("class=\"erp-filter-form manager-queue-search-form aws-filter-toolbar\"");
        for (String view : currentFilterViews) {
            assertThat(view)
                .contains("class=\"aws-current-filter-summary")
                .contains("class=\"aws-current-filter-copy")
                .contains("class=\"aws-current-filter-kicker")
                .contains("aws-current-filter-value")
                .contains("<c:out value=\"${currentFilterLabel}\" />")
                .doesNotContain("<div class=\"erp-toolbar\">")
                .doesNotContain("class=\"erp-widget-title\"><spring:message code=\"common.currentFilter\"")
                .doesNotContain("class=\"erp-widget-heading\">${currentFilterLabel}</h2>")
                .doesNotContain("manager-queue-filter-stack");
        }
        assertThat(consoleCss)
            .contains(".aws-current-filter-toolbar")
            .contains(".aws-current-filter-summary")
            .contains("border-left: 3px solid var(--aws-orange, #ec7211)")
            .contains("min-height: 38px")
            .contains(".aws-current-filter-kicker")
            .contains(".aws-current-filter-value")
            .contains(".aws-current-filter-toolbar > .aws-filter-toolbar")
            .contains(".aws-current-filter-toolbar > .aws-current-filter-tabs")
            .doesNotContain("min-height: 46px")
            .doesNotContain(".manager-queue-filter-stack");
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
                        .doesNotContainPattern("(?i)>\\s*info\\s*<")
                        .doesNotContain("data-auto-submit")
                        .doesNotContain("onchange=")
                        .doesNotContain("oninput=")
                        .doesNotContain("Â")
                        .doesNotContain("Ã")
                        .doesNotContain("ï¿½");
                });
        }
    }

    @Test
    void workspaceLabelsAndFiltersUseTheSharedResponsiveLayoutContract() throws Exception {
        for (Path path : routeViews()) {
            String view = read(path);
            if (!view.contains("fragments/header.jspf")) {
                continue;
            }
            assertThat(view)
                .as(path.toString())
                .doesNotContainPattern("class=[\"'][^\"']*\\btruncate\\b[^\"']*[\"']");

            java.util.regex.Matcher getForms = java.util.regex.Pattern
                .compile("<form\\b[^>]*\\bmethod\\s*=\\s*[\"']get[\"'][^>]*>", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(view);
            while (getForms.find()) {
                assertThat(getForms.group())
                    .as(path + " GET form")
                    .contains("data-aws-filter-toolbar");
            }
        }

        String shellCss = read(Path.of("src/main/resources/static/css/shell.css"));
        assertThat(shellCss)
            .contains("Complete Cloudscape workspace layout")
            .contains(".aws-console .aws-filter-toolbar > [class*=\"actions\"]")
            .contains("flex: 1 1 34rem")
            .contains("width: min(100%, 728px)")
            .contains("grid-template-columns: repeat(4, minmax(0, 1fr)) !important")
            .contains(".aws-console .loan-application-steps")
            .contains("overflow-x: auto")
            .contains("white-space: normal");
    }

    @Test
    void authenticatedOperationalTablesUseNestedCloudscapeSurfacesEverywhere() throws Exception {
        try (Stream<Path> paths = Files.walk(JSP_ROOT)) {
            paths.filter(path -> path.toString().endsWith(".jsp") || path.toString().endsWith(".jspf"))
                .forEach(path -> {
                    String view = read(path);
                    assertThat(view)
                        .as(path.toString())
                        .doesNotContainPattern("class=[\"'][^\"']*\\brounded-(?:md|lg|xl|2xl|3xl)\\b[^\"']*[\"']")
                        .doesNotContainPattern("class=[\"'][^\"']*\\bshadow-(?:md|lg|xl|2xl)\\b[^\"']*[\"']")
                        .doesNotContainPattern("class=[\"'][^\"']*\\b(?:overflow-x-auto|min-w-max)\\b[^\"']*[\"']");
                    if (view.contains("fragments/header.jspf")) {
                        assertThat(view)
                            .as(path.toString())
                            .doesNotContain("erp-page-subtitle");
                    }
                    if (!view.contains("<table")) {
                        return;
                    }
                    assertThat(view)
                        .as(path.toString())
                        .contains("erp-table")
                        .contains("erp-table-scroll")
                        .doesNotContain("erp-table-wrap erp-table-scroll");
                });
        }
    }

    @Test
    void guaranteedLoansUsesOneSearchableCloudscapeTableSurface() throws Exception {
        String view = read(JSP_ROOT.resolve("app/guaranteed-loans.jsp"));
        String shellJs = read(Path.of("src/main/resources/static/js/shell.js"));

        assertThat(view)
            .contains("class=\"erp-table-wrap guaranteed-loans-table\"")
            .contains("id=\"guaranteedLoansTableTitle\"")
            .contains("data-aws-client-table")
            .contains("data-aws-table-search")
            .contains("class=\"erp-table-scroll\"")
            .contains("<table class=\"erp-table\">")
            .contains("text=\"Loan ID\"")
            .contains("text=\"Amount\" /> (TZS)")
            .doesNotContain("erp-widget-title")
            .doesNotContain("erp-panel overflow-hidden")
            .doesNotContain("erp-table-wrap erp-table-scroll");
        assertThat(shellJs)
            .contains("const initAwsClientTables")
            .contains("[data-aws-table-search]")
            .contains("row.hidden = !visible")
            .contains("loader.setAttribute('aria-label'")
            .doesNotContain("loader.append(spinner, label)");
    }

    @Test
    void memberLoanTablesAlignActionsWithTheirColumnHeaders() throws Exception {
        String applications = read(JSP_ROOT.resolve("app/loan-applications.jsp"));
        String archives = read(JSP_ROOT.resolve("app/archives.jsp"));
        String shellCss = read(Path.of("src/main/resources/static/css/shell.css"));

        assertThat(applications)
            .contains("<th scope=\"col\" class=\"erp-table-action-column\"")
            .contains("<td class=\"erp-table-action-column\"")
            .contains("<div class=\"erp-table-actions\">");
        assertThat(archives)
            .contains("<th scope=\"col\" class=\"erp-table-action-column\"")
            .contains("<td class=\"erp-table-action-column\"")
            .contains("<div class=\"erp-table-actions\">")
            .doesNotContain("<th></th>");
        assertThat(shellCss)
            .contains(".erp-table th.erp-table-action-column")
            .contains("min-width: 10rem")
            .contains("justify-content: center");
    }

    @Test
    void sharedAssetsProvideAwsShellTableAndResponsiveFilterPrimitives() throws Exception {
        String header = read(JSP_ROOT.resolve("fragments/header.jspf"));
        String footer = read(JSP_ROOT.resolve("fragments/footer.jspf"));
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
            .contains("console-components.css?v=20260824-calculator-detail-v43")
            .contains("shell.css?v=20260824-calculator-detail-v44")
            .contains("shell.js?v=20260824-modal-toast-v19")
            .contains("app-global-logo")
            .contains("app-global-logo-image")
            .contains("activeSaccoLogoUrl")
            .contains("/profile/image/me")
            .contains("profile-avatar-img")
            .contains("aws-console")
            .contains("app-topbar")
            .contains("app-topbar-left")
            .contains("app-topbar-actions")
            .contains("app-notification-control")
            .contains("app-notification-badge")
            .contains("app-account-copy")
            .contains("app-profile-control")
            .contains("shellPageBreadcrumbRailText")
            .contains("pageSubmitPreloader")
            .contains("app-global-search");
        assertThat(footer)
            .contains("item.dataset.sidebarLabel = label")
            .doesNotContain("item.setAttribute(\"title\", label)");
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
            .contains("--sacco-topbar: #3A756B")
            .contains("--sacco-font-family: \"Open Sans\", Helvetica, Arial, sans-serif")
            .contains("--sacco-sidebar: #183038")
            .contains("--sacco-canvas: #F2F3F3")
            .contains("--sacco-control-height: 32px")
            .contains("--aws-orange")
            .contains("--aws-sidebar: #183038")
            .contains("--shell-sidebar-width: 236px")
            .contains("--shell-nav-height: 44px")
            .contains("--sacco-topbar-height: 44px")
            .contains("--shell-topbar-control-height: 28px")
            .contains(".aws-filter-toolbar")
            .contains("flex-wrap: wrap !important")
            .contains("width: calc(100% - var(--shell-sidebar-width)) !important")
            .doesNotContain("padding-left: calc(var(--shell-sidebar-width) + 12px) !important")
            .doesNotContain("padding-left: calc(var(--shell-sidebar-collapsed-width) + 12px) !important")
            .contains("max-width: calc(100vw - 32px) !important")
            .contains(".app-table-titlebar")
            .contains("#appToastContainer .app-toast-close")
            .contains("background: transparent !important")
            .contains(".erp-page-header[data-aws-page-header] .erp-page-title")
            .contains("font-size: 24px !important")
            .contains("line-height: 30px !important")
            .contains(".erp-page-path__link")
            .contains("position: sticky !important")
            .contains("top: var(--shell-nav-height) !important")
            .contains("text-decoration: none")
            .contains(".erp-page-path__link:focus-visible")
            .contains(".aws-console .loan-applicant-photo-frame img")
            .contains("border-radius: 50% !important")
            .contains(".aws-console .workflow-table-head")
            .contains(".aws-console .admin-register-shell .erp-table thead th")
            .contains(".aws-console .member-dashboard-flow-track")
            .contains("transform: translateY(-50%) !important")
            .contains(".shell-sidebar-group > div > a:hover")
            .contains("background: #232e36 !important")
            .contains(".shell-sidebar-group > div > a.shell-nav-active")
            .contains("background: #25313a !important")
            .contains("color: #ffffff !important")
            .contains(".app-global-brand .app-global-logo")
            .contains("width: min(22vw, 320px)")
            .contains("max-width: 320px")
            .contains("margin: 0 8px 0 16px !important")
            .contains("width: min(18vw, 240px)")
            .contains("margin-left: 16px !important")
            .contains("height: var(--shell-topbar-control-height) !important")
            .contains("background: transparent !important")
            .contains("color: rgba(242, 243, 243, 0.55) !important")
            .contains(".app-notification-badge")
            .contains("top: 0;")
            .contains("right: -5px")
            .contains("border-radius: 2px")
            .contains(".app-topbar-actions .app-notification-control")
            .contains(".app-topbar-actions .app-profile-control")
            .contains("flex: 0 0 28px")
            .contains(".app-topbar .profile-icon-btn .profile-avatar-img")
            .contains("Final Cloudscape workspace shell")
            .contains(".shell-sidebar-nav-heading")
            .contains("--shell-sidebar-width: var(--sacco-sidebar-width)")
            .contains("--shell-nav-fixed-height: var(--sacco-topbar-height)")
            .contains("--shell-topbar-control-height: 28px")
            .contains("--shell-sims-topbar: var(--sacco-topbar)")
            .contains("--shell-sims-sidebar: var(--sacco-sidebar)")
            .contains("@media (max-width: 420px)")
            .contains(".app-global-brand > div")
            .contains(".shell-nav-active")
            .contains("border-left: 3px solid var(--aws-orange) !important")
            .doesNotContain("Amazon Ember")
            .doesNotContain("Manrope")
            .doesNotContain("Sora");
        assertThat(authCss)
            .contains("font-family: var(--sacco-font-family) !important")
            .contains(".auth-notification-bar button[type=\"button\"].app-toast-close")
            .contains("background: transparent !important")
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
            .contains("aws-auth.css?v=20260813-ribbonless-v7")
            .contains("href=\"#forgotPasswordModal\" class=\"auth-text-link\"")
            .contains("event.preventDefault()")
            .doesNotContain("data-forgot-password-open=\"member\">Forgot password?</button>")
            .contains("class=\"app-toast-close-icon\"");
        assertThat(registration)
            .contains("aws-auth.css?v=20260813-ribbonless-v7")
            .contains("class=\"app-toast-close-icon\"")
            .doesNotContain("&times;");
        assertThat(authCss)
            .contains("body.auth-shell.aws-auth-shell::before")
            .contains("display: none")
            .contains("body.auth-shell.aws-auth-shell > .min-h-screen")
            .contains("body.auth-shell.aws-auth-shell .auth-notification-rail")
            .contains("inset-block-start: 0");
        assertThat(shellJs)
            .contains("syncActiveSidebarLink")
            .contains("app-toast-close-icon")
            .doesNotContain("&times;")
            .contains("enhanceConsoleTables")
            .contains("showConsoleTableLoading")
            .contains("showPageSubmitPreloader(null)")
            .contains("[data-download-action=\"true\"]")
            .contains("downloadWithPagePreloader")
            .contains("syncConsoleFiltersFromUrl")
            .contains("breadcrumbRouteGroups")
            .contains("'admin tools'")
            .contains("'member workspace'")
            .contains("'staff workspace'")
            .contains("'users & roles': '/admin/users'")
            .contains("resolveBreadcrumbHref")
            .contains("document.createElement(href ? 'a' : 'span')")
            .contains("item.href = href")
            .contains("aws-pagination-chevron")
            .contains("maxVisibleToasts = 3")
            .contains("promoteQueuedToast")
            .doesNotContain("info.textContent = 'Info'")
            .doesNotContain("heading.append(title, info)");
    }

    @Test
    void loginAndLoanJourneysExposeProgressiveAwsInteractions() throws Exception {
        String login = read(JSP_ROOT.resolve("login.jsp"));
        String loanProducts = read(JSP_ROOT.resolve("app/loan-products.jsp"));
        String loanApplication = read(JSP_ROOT.resolve("app/loan-new.jsp"));
        String guarantorSelection = read(JSP_ROOT.resolve("app/guarantor-selection.jsp"));
        String loanDetail = read(JSP_ROOT.resolve("app/loan-view.jsp"));
        String staffLoanDetail = read(JSP_ROOT.resolve("manager/detail.jsp"));
        String staffLoanHeader = read(JSP_ROOT.resolve("fragments/staff-loan-detail-header.jspf"));
        String staffReviewSummary = read(JSP_ROOT.resolve("fragments/staff-loan-review-summary.jspf"));
        String header = read(JSP_ROOT.resolve("fragments/header.jspf"));
        String consoleCss = read(Path.of("src/main/resources/static/css/console-components.css"));
        String shellCss = read(Path.of("src/main/resources/static/css/shell.css"));
        String shellJs = read(Path.of("src/main/resources/static/js/shell.js"));
        String authCss = read(Path.of("src/main/resources/static/css/aws-auth.css"));

        assertThat(login)
            .contains("role=\"tablist\"")
            .contains("aria-selected")
            .contains("panel.hidden = !active")
            .contains("auth-notification-rail");
        assertThat(authCss)
            .contains(".auth-notification-bar")
            .contains(".auth-text-link")
            .contains("color: var(--aws-blue) !important")
            .contains("inset: 44px 0 auto")
            .contains("--sacco-topbar-height: 44px")
            .contains("--sacco-control-height: 32px")
            .contains("--aws-topbar: #3A756B")
            .contains("transform: none !important");
        assertThat(loanProducts)
            .contains("loan-calculator-steps")
            .contains("productsCalculatorResults")
            .contains("productsCalculatorApplyLink")
            .contains("setCalculatorStage");
        assertThat(loanApplication)
            .contains("data-loan-flow-step=\"1\"")
            .contains("data-loan-flow-step=\"5\"")
            .contains("data-loan-step-next")
            .contains("<span>Loan Calculations</span>")
            .contains("<span>Select Guarantor</span>")
            .contains("<span>Attachments</span>")
            .contains("<span>Submit</span>")
            .contains("data-aws-no-refresh=\"true\"")
            .contains("data-aws-no-titlebar=\"true\"")
            .contains("loan-summary-table")
            .contains("Continue to Loan Calculations")
            .contains("Continue to Select Guarantor")
            .contains("Continue to Attachments")
            .contains("Continue to Submit")
            .doesNotContain("Check affordability")
            .doesNotContain("Add guarantors and documents")
            .contains("validateCurrentStep");
        assertThat(guarantorSelection)
            .contains("guarantor-selection-page")
            .contains("loan-guarantor-search-row")
            .contains("loan-selected-guarantors")
            .contains("selected-guarantor-chip")
            .doesNotContain("rounded-full");
        assertThat(loanDetail)
            .contains("loan-application-detail-page")
            .contains("class=\"loan-detail-action-row\"")
            .contains("data-aws-action-pin=\"true\"")
            .contains("app-table-titlebar")
            .contains("erp-table-wrap")
            .contains("class=\"erp-table-scroll")
            .doesNotContain("erp-table-wrap erp-table-scroll")
            .contains("loan-summary-table")
            .contains("loan-detail-disclosure")
            .doesNotContain("loan-staff-kpi-icon");
        assertThat(staffLoanDetail)
            .contains("include file=\"../fragments/staff-loan-detail-header.jspf\"")
            .contains("include file=\"../fragments/staff-loan-review-summary.jspf\"");
        assertThat(staffLoanHeader)
            .contains("class=\"erp-page-header loan-detail-heading-row\" data-aws-page-header")
            .contains("data-aws-page-header")
            .contains("class=\"loan-detail-action-row\"")
            .contains("data-loan-export-url");
        assertThat(staffReviewSummary)
            .contains("loan-applicant-photo-card")
            .contains("loan-applicant-photo-frame")
            .contains("/profile/image/members/${reviewApplicantId}")
            .contains("loan-staff-review-section-title")
            .contains("loan-staff-kpi-icon");
        assertThat(header)
            .contains("aria-controls=\"notificationPanel\"")
            .contains("aria-expanded=\"false\"")
            .contains("z-50\" role=\"region\" aria-label=")
            .doesNotContain("z-50\" role=\"dialog\" aria-label=")
            .doesNotContain("notificationPanelSubtitle")
            .doesNotContain("erp-notification-body space-y-2");
        assertThat(shellCss)
            .contains(".erp-page-header[data-aws-page-header]")
            .contains("position: static !important")
            .contains("font-size: 24px !important")
            .contains(".shell-content-frame")
            .contains("overflow-y: visible !important");
        assertThat(consoleCss)
            .contains("grid-template-columns: repeat(5, minmax(0, 1fr))")
            .containsPattern("\\.loan-application-steps\\s*\\{\\s*position: static;")
            .doesNotContainPattern("\\.loan-application-steps\\s*\\{\\s*position: sticky;")
            .contains(".loan-guarantor-search-row")
            .contains(".loan-summary-table .erp-table")
            .contains(".loan-application-detail-page")
            .contains(".loan-detail-action-row")
            .contains("justify-content: flex-end")
            .contains("border-radius: 2px !important");
        assertThat(shellJs)
            .contains("data-aws-no-titlebar")
            .contains("!suppressTitlebar")
            .contains("control.closest('table')")
            .contains("link.className = 'erp-notification-item'")
            .contains("notificationToggle.setAttribute('aria-expanded', 'true')")
            .doesNotContain("rounded-md border border-slate-300");
        assertThat(shellCss)
            .contains(".erp-notification-item")
            .contains(".erp-notification-meta")
            .contains("background: #f1faff");
    }

    @Test
    void memberMobileAnalyticsAndShellStateUsePinnedResponsiveContracts() throws Exception {
        String reports = read(JSP_ROOT.resolve("app/reports.jsp"));
        String staffAnalytics = read(JSP_ROOT.resolve("staff/analytics.jsp"));
        String sidebar = read(JSP_ROOT.resolve("fragments/sidebar.jspf"));
        String shellCss = read(Path.of("src/main/resources/static/css/shell.css"));
        String consoleCss = read(Path.of("src/main/resources/static/css/console-components.css"));
        String shellJs = read(Path.of("src/main/resources/static/js/shell.js"));

        assertThat(reports)
            .contains("data-aws-filter-pin=\"true\"")
            .contains("data-aws-action-pin=\"true\"")
            .contains("data-download-action=\"true\"")
            .contains("loan-spark-row")
            .contains("renderMetricSparklines")
            .doesNotContain("loan-analytics-command-header")
            .doesNotContain("reports.controls");
        int portfolioPanelIndex = staffAnalytics.indexOf("class=\"erp-panel staff-portfolio-panel\"");
        int trendPanelIndex = staffAnalytics.indexOf("class=\"erp-panel staff-trend-panel\"");
        int productPanelIndex = staffAnalytics.indexOf("class=\"erp-panel staff-product-panel\"");
        assertThat(portfolioPanelIndex).isGreaterThanOrEqualTo(0);
        assertThat(trendPanelIndex).isGreaterThan(portfolioPanelIndex);
        assertThat(productPanelIndex).isGreaterThan(trendPanelIndex);
        assertThat(staffAnalytics)
            .contains("class=\"erp-panel staff-analytics-command-panel\"")
            .contains("class=\"app-table-toolbar staff-analytics-actions\" data-aws-action-pin=\"true\"")
            .contains("data-aws-filter-pin=\"true\"")
            .contains("class=\"erp-breadcrumb\"")
            .contains("staff.analytics.breadcrumb")
            .contains("name=\"fromDate\" type=\"date\"")
            .contains("name=\"toDate\" type=\"date\"")
            .contains("value=\"${fromDateInput}\"")
            .contains("value=\"${toDateInput}\"")
            .contains("dateFilterControls")
            .contains("dateInput.showPicker()")
            .contains("class=\"erp-panel staff-portfolio-panel\"")
            .contains("class=\"erp-panel staff-product-panel\"")
            .contains("class=\"erp-panel staff-trend-panel\"")
            .contains("<div class=\"staff-financial-breakdown-grid\">")
            .contains("<div class=\"staff-interest-summary\">")
            .contains("id=\"staffAnalyticsViewAs\" type=\"hidden\" name=\"viewAs\"")
            .contains("type=\"button\" data-report-view=\"member\"")
            .contains("type=\"button\" data-report-view=\"staff\"")
            .contains("reportScopeForm.requestSubmit()")
            .contains("reportScopeForm.setAttribute('aria-busy', 'true')")
            .contains("candidate.classList.toggle('is-switching', selected)")
            .contains("data-trend-interval=\"monthly\"")
            .contains("data-trend-interval=\"quarterly\"")
            .contains("data-trend-interval=\"yearly\"")
            .contains("trendRangeLimit(interval)")
            .contains("data-download-action=\"true\"")
            .contains("class=\"erp-table-scroll\" data-view-position-key=\"staff-analytics-financial-breakdown\"")
            .contains("staff-metric-meta-value")
            .contains("exactLinePath")
            .doesNotContain("Math.sin")
            .doesNotContain("staffTrendRange")
            .doesNotContain("syncTrendRangeControl")
            .doesNotContain("staff-info-bar")
            .doesNotContain("staff-card-info")
            .doesNotContain("staff-analytics-primary-column");
        assertThat(sidebar)
            .contains("data-view-position-key=\"sidebar-navigation\"")
            .contains("<sec:authorize access=\"@authz.staffAnalyticsAccess(principal)\">")
            .contains("<c:set var=\"showStaffAnalyticsPanel\" value=\"true\" />");
        assertThat(consoleCss)
            .contains(".loan-analytics-command-panel .loan-analytics-actions")
            .contains("height: var(--sacco-control-height)")
            .contains(".staff-analytics-command-panel")
            .contains(".staff-analytics-filter")
            .contains("grid-auto-rows: auto")
            .contains(".staff-metric-grid.is-station")
            .contains(".staff-metric-grid.is-staff")
            .contains(".staff-analytics-layout")
            .contains(".staff-portfolio-panel")
            .contains(".staff-product-panel")
            .contains(".staff-trend-panel")
            .contains("align-items: stretch")
            .contains("height: 100%")
            .contains("flex-wrap: nowrap")
            .contains("text-overflow: clip")
            .contains(".staff-analytics-control.is-date input[type=\"date\"]")
            .contains("grid-template-columns: repeat(2, minmax(0, 1fr))")
            .contains("grid-column: 1 / -1")
            .contains("grid-row: 2")
            .contains("grid-row: 3")
            .contains("position: relative")
            .contains("overflow-x: visible")
            .contains(".staff-trend-period-button")
            .contains("@keyframes staffScopeSelect")
            .contains("@keyframes staffScopeIndicator")
            .contains(".staff-view-switch.is-submitting")
            .contains(".staff-view-option.is-switching")
            .contains(".staff-view-option.is-switching::after")
            .contains("padding-right: 12px")
            .contains("border-radius: 2px")
            .contains(".staff-metric-meta-value")
            .doesNotContain(".staff-card-info")
            .doesNotContain("grid-column: 7 / -1")
            .doesNotContain("grid-column: 1 / span 6")
            .doesNotContain(".loan-analytics-page-header .erp-breadcrumb");
        assertThat(shellCss)
            .contains(".app-table-heading h2")
            .contains("overflow-wrap: anywhere")
            .contains(".shell-sidebar-flyout-link.is-active")
            .contains("background: transparent !important")
            .contains("box-shadow: none !important")
            .contains(".shell-sidebar-collapsed .shell-sidebar-nav-heading")
            .contains("overflow-x: hidden !important")
            .contains("font-size: 0 !important")
            .contains("width: 24px !important")
            .contains("padding-left: 12px !important")
            .contains(".staff-analytics-command-panel .staff-analytics-filter")
            .contains("flex-flow: row nowrap !important")
            .contains("gap: 4px 8px !important")
            .contains("overflow-x: visible !important")
            .contains("white-space: nowrap !important")
            .contains("min-width: 8.15rem")
            .contains("min-width: 10.25rem")
            .contains("min-width: 15.9rem")
            .contains("padding-left: 2px")
            .contains("flex-flow: row wrap !important")
            .contains(".staff-analytics-control.is-view .staff-view-switch")
            .contains("width: fit-content !important")
            .contains("max-width: 100% !important")
            .contains("overflow-x: auto !important");
        assertThat(shellJs)
            .contains("isConsoleDownloadAction")
            .contains("header.querySelector('.erp-breadcrumb')")
            .contains("promotePageHeadersToShell")
            .contains("shellMain.insertBefore(header, contentFrame)")
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
        String adminSettings = read(JSP_ROOT.resolve("admin/settings-controls.jsp"));
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
            .contains("aws-settings-panel aws-settings-panel--compact")
            .contains("aws-settings-control-row")
            .contains("aws-settings-grid")
            .contains("aws-settings-footer")
            .contains("aria-labelledby=\"memberLanguageSettingsTitle\"")
            .contains("for=\"memberSettingsLanguage\"")
            .contains("aria-describedby=\"memberSettingsLanguageHelp\"")
            .doesNotContain("member.settings.language.eyebrow")
            .doesNotContain("member-settings-action-bar");
        assertThat(staffSettings)
            .contains("aws-settings-panel aws-settings-panel--compact")
            .contains("aws-settings-control-row")
            .contains("aws-settings-footer")
            .contains("aria-labelledby=\"staffLanguageSettingsTitle\"")
            .contains("for=\"staffSettingsLanguage\"")
            .doesNotContain("overflow-hidden")
            .doesNotContain("staffSettingsLanguageHelp")
            .doesNotContain("staff.settings.language.help")
            .doesNotContain("member.settings.language.eyebrow")
            .doesNotContain("staff-settings-action-bar");
        assertThat(platformSettings)
            .contains("aws-settings-panel")
            .contains("aws-settings-subsection")
            .doesNotContain("platform-settings-card rounded-md");
        assertThat(adminSettings)
            .contains("aws-settings-panel aws-settings-panel--compact")
            .contains("aria-labelledby=\"adminLanguageSettingsTitle\"")
            .contains("for=\"adminSettingsLanguage\"")
            .contains("aws-settings-control-row")
            .contains("aws-settings-footer")
            .doesNotContain("settings-action-note");
        assertThat(shellCss)
            .contains(".aws-status-summary-grid")
            .contains(".aws-settings-header")
            .contains(".aws-settings-control-row")
            .contains("width: min(100%, 34rem)")
            .contains("flex-flow: column nowrap")
            .contains(".aws-settings-panel--compact")
            .contains("width: min(100%, 36rem)")
            .contains(".aws-console .aws-settings-panel:has(.neo-select--open)")
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
            .contains("Select a loan product")
            .contains("product-catalog-grid")
            .contains("data-product-config-card")
            .contains("data-product-card-toggle")
            .contains("See more")
            .contains("/admin/settings-controls/loan-products/${product.id}/edit")
            .contains("Configure product")
            .doesNotContain("Step 1 of 2")
            .doesNotContain("loan-products-versions")
            .doesNotContain("data-product-modal-open=\"versions-${product.id}\"")
            .doesNotContain("/versions/${version.id}/rollback");
        assertThat(productEdit)
            .contains("Step 2 of 2")
            .contains("data-product-config-workspace")
            .contains("data-product-config-nav=\"identity\"")
            .contains("data-product-config-nav=\"preview\"")
            .contains("data-product-config-step=\"workflow\"")
            .contains("data-product-config-panel hidden")
            .doesNotContain("<span><c:out value=\"${product.displayCode}\" /></span>")
            .doesNotContain("data-product-config-progress")
            .contains("text=\"Included\"")
            .contains("text=\"Order\"")
            .contains("data-toast-message")
            .contains("&middot;")
            .doesNotContain(" · ")
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
            .contains("const workflowScrollTarget = (step) =>")
            .contains("step.querySelector('.product-config-subsection')")
            .contains("const workflowScrollOffset = () =>")
            .contains("workspace.querySelector('.product-config-workspace-nav')")
            .contains("workspaceNav.offsetHeight + 12")
            .contains("const target = workflowScrollTarget(step) || step")
            .contains("window.scrollTo({ top: Math.max(0, targetTop), behavior })")
            .contains("scrollStepIntoView(steps[activeIndex])")
            .contains("window.requestAnimationFrame(() => scrollStepIntoView(steps[activeIndex]))")
            .doesNotContain("if (target !== step && typeof window.scrollTo === 'function')")
            .contains("resolvedInitialKey === 'workflow'")
            .contains("window.showToast('error', message")
            .contains("workflowFormIsActive")
            .contains("warningsRoot.hidden = warnings.length === 0")
            .contains("form.addEventListener('workflow:refresh', applyRules)")
            .contains("expanded ? 'Show less' : 'See more'");
        assertThat(consoleCss)
            .contains(".product-catalog-grid")
            .contains("grid-template-columns: repeat(2, minmax(0, 1fr))")
            .contains(".product-config-workspace")
            .contains("grid-template-columns: repeat(5, minmax(11rem, 1fr))")
            .contains(".product-config-workspace.is-enhanced .product-config-step:not(.is-active)")
            .contains(".product-config-subsection")
            .contains("scroll-margin-top: calc(var(--shell-nav-height, 52px) + var(--shell-context-height, 36px) + 12px)")
            .contains(".product-config-step.is-active")
            .contains(".product-config-actionbar")
            .contains("grid-template-columns: 1.55rem minmax(0, 1fr)")
            .contains("min-height: 3rem")
            .doesNotContain(".product-config-progress");
        assertThat(shellCss)
            .contains(".aws-console .product-config-workspace input:not([type=\"checkbox\"])")
            .contains("min-height: var(--sacco-control-height) !important")
            .contains(".aws-console .product-catalog .app-btn");
    }

    @Test
    void regionalLoadingDoesNotCompeteWithThePagePreloader() throws Exception {
        String shellJs = read(Path.of("src/main/resources/static/js/shell.js"));

        assertThat(shellJs)
            .contains("hidePageSubmitPreloader();")
            .contains("const pagePreloaderRequested")
            .contains("const regionalLoaderActive")
            .contains("if (!pagePreloaderRequested && (regionalLoaderActive || regionalForm))")
            .contains("[data-aws-table-region][aria-busy=\"true\"]");
    }

    @Test
    void analyticsFiltersUseThePagePreloaderInsteadOfAnOffscreenTableLoader() throws Exception {
        String memberAnalytics = read(JSP_ROOT.resolve("app/reports.jsp"));
        String staffAnalytics = read(JSP_ROOT.resolve("staff/analytics.jsp"));
        String shellJs = read(Path.of("src/main/resources/static/js/shell.js"));

        assertThat(memberAnalytics)
            .contains("action=\"/app/reports\" method=\"get\"")
            .contains("data-page-preloader=\"true\"");
        assertThat(staffAnalytics)
            .contains("action=\"/staff/analytics\" method=\"get\"")
            .contains("data-page-preloader=\"true\"");
        assertThat(shellJs)
            .contains("if (form.matches('[data-page-preloader=\"true\"]'))")
            .contains("clearConsoleTableLoading();");
    }

    @Test
    void documentDownloadsUseSelfClearingPagePreloader() throws Exception {
        String shellJs = read(Path.of("src/main/resources/static/js/shell.js"));
        String loanExportModal = read(JSP_ROOT.resolve("fragments/loan-export-modal.jspf"));
        String staffLoanDetailHeader = read(JSP_ROOT.resolve("fragments/staff-loan-detail-header.jspf"));
        String attachmentPreview = read(JSP_ROOT.resolve("documents/attachment-view.jsp"));
        List<String> loanDetails = List.of(
            read(JSP_ROOT.resolve("app/loan-view.jsp")),
            read(JSP_ROOT.resolve("manager/detail.jsp")),
            read(JSP_ROOT.resolve("board/detail.jsp"))
        );

        assertThat(shellJs)
            .contains("downloadWithPagePreloader")
            .contains("const exportDownloadTimeoutMs = 60 * 1000")
            .contains("const response = await window.fetch")
            .contains("triggerBrowserDownload(blob, resolveDownloadFilename(response, target))")
            .contains("controller.abort()")
            .contains("finally {")
            .contains("hidePageSubmitPreloader();")
            .contains("resolveExportFormUrl(form, event.submitter)")
            .contains("showPageSubmitPreloader(null)")
            .contains("isConsoleDownloadAction(link)")
            .contains("[data-download-action=\"true\"]")
            .contains("link.matches('[download], [data-download-action=\"true\"]')")
            .contains("void downloadWithPagePreloader(target.toString())");
        assertThat(loanExportModal)
            .contains("data-loan-export-signed data-download-action=\"true\"")
            .contains("data-loan-export-unsigned data-download-action=\"true\"");
        assertThat(attachmentPreview)
            .contains("previewDownloadHref")
            .contains("data-download-action=\"true\">Download</a>");
        for (String detail : loanDetails) {
            assertThat(detail)
                .contains("/documents/loan-applications/${app.id}/attachments/${file.id}\"")
                .contains("data-download-action=\"true\"><spring:message code=\"common.download\"");
        }
        assertThat(loanDetails.get(0)).contains("data-loan-export-url=");
        assertThat(staffLoanDetailHeader)
            .contains("data-loan-export-url=")
            .contains("/documents/loan-applications/${reviewDocumentLoanId}/print");
    }

    @Test
    void loanDetailsReuseProgressBalanceAndFinancialTableComponents() throws Exception {
        String memberDetail = read(JSP_ROOT.resolve("app/loan-view.jsp"));
        String staffDetail = read(JSP_ROOT.resolve("manager/detail.jsp"));
        String boardDetail = read(JSP_ROOT.resolve("board/detail.jsp"));
        String processedDetail = read(JSP_ROOT.resolve("chairperson/processed-loan-detail.jsp"));
        String staffDetailHeader = read(JSP_ROOT.resolve("fragments/staff-loan-detail-header.jspf"));
        String staffReviewSummary = read(JSP_ROOT.resolve("fragments/staff-loan-review-summary.jspf"));
        String progress = read(JSP_ROOT.resolve("fragments/loan-application-progress.jspf"));
        String liveStatus = read(JSP_ROOT.resolve("fragments/live-account-status-message.jspf"));
        String liveHydration = read(JSP_ROOT.resolve("fragments/live-account-status-hydration.jspf"));
        String financialSections = read(JSP_ROOT.resolve("fragments/financial-field-sections.jspf"));
        String consoleCss = read(Path.of("src/main/resources/static/css/console-components.css"));

        assertThat(memberDetail)
            .contains("include file=\"../fragments/loan-application-progress.jspf\"")
            .contains("include file=\"../fragments/live-account-status-message.jspf\"")
            .doesNotContain("<div class=\"mt-6 loan-simple-progress\"");
        assertThat(staffReviewSummary)
            .contains("include file=\"loan-application-progress.jspf\"")
            .contains("include file=\"live-account-status-message.jspf\"")
            .contains("class=\"loan-view-hero-summary loan-staff-review-template\"")
            .contains("data-staff-review-page=\"true\"");

        for (String detail : List.of(staffDetail, boardDetail)) {
            assertThat(detail)
                .contains("include file=\"../fragments/staff-loan-detail-header.jspf\"")
                .contains("include file=\"../fragments/staff-loan-review-summary.jspf\"")
                .doesNotContain("<div class=\"mt-6 loan-simple-progress\"");

            int headerInclude = detail.indexOf("include file=\"../fragments/staff-loan-detail-header.jspf\"");
            int heroSummary = detail.indexOf("include file=\"../fragments/staff-loan-review-summary.jspf\"");

            assertThat(headerInclude).isGreaterThanOrEqualTo(0);
            assertThat(heroSummary).isGreaterThan(headerInclude);
        }
        assertThat(staffDetailHeader)
            .contains("class=\"erp-page-header loan-detail-heading-row\" data-aws-page-header")
            .contains("data-aws-page-header")
            .contains("class=\"loan-detail-action-row\"")
            .contains("data-aws-action-pin=\"true\"")
            .contains("data-loan-export-url");
        assertThat(staffDetail).contains("reviewStatusBadgeClass");
        assertThat(boardDetail).contains("reviewStatusBadgeClass");
        assertThat(processedDetail)
            .contains("include file=\"../fragments/staff-loan-detail-header.jspf\"")
            .contains("include file=\"../fragments/staff-loan-review-summary.jspf\"")
            .contains("include file=\"../fragments/financial-field-sections.jspf\"")
            .contains("processedLoanDetail")
            .contains("loan.progressItems")
            .contains("loan.repaymentSummaryEstimated")
            .contains("loan.calculatedRepaymentRows");
        assertThat(memberDetail).doesNotContain("data-staff-review-page=\"true\"");
        assertThat(read(Path.of("src/main/resources/static/js/shell.js")))
            .contains("document.querySelector('[data-staff-review-page=\"true\"]')");
        assertThat(progress)
            .contains("is-${item.state}")
            .contains("item.state eq 'rejected'")
            .contains("item.state eq 'closed'")
            .contains("loan.progress.currentStatus");
        assertThat(liveStatus)
            .contains("live-account-status-message")
            .contains("data-live-account-status-message");
        assertThat(liveHydration)
            .contains("setStatusBoxState(box, \"available\", \"\")")
            .contains("LIVE_ACCOUNT_STATUS_TIMEOUT_MS")
            .contains("finish({")
            .contains("window.addEventListener(\"pageshow\"");
        assertThat(financialSections)
            .contains("data-aws-no-titlebar=\"true\"")
            .contains("data-aws-no-refresh=\"true\"")
            .contains("class=\"erp-table-scroll erp-table-scroll-sm\"")
            .contains("code=\"common.attribute\" text=\"Attribute\"")
            .doesNotContain("erp-table-wrap erp-table-scroll");
        assertThat(consoleCss)
            .contains(".loan-simple-progress-item.is-rejected")
            .contains(".loan-simple-progress-item.is-closed")
            .contains(".live-account-status-message[hidden]")
            .contains(".loan-staff-review-template[data-staff-review-page=\"true\"]")
            .contains(".loan-staff-review-section-title")
            .contains("grid-template-columns: repeat(4, minmax(0, 1fr))")
            .contains(".loan-detail-heading-row")
            .contains(".erp-page-header.loan-detail-heading-row > .erp-page-title")
            .contains(".loan-staff-kpi-icon")
            .contains("border-left: 3px solid #ec7211 !important")
            .contains(".loan-financial-section > .erp-table-scroll .erp-table");
    }

    @Test
    void adminRegistersKeepAwsFiltersAndScrollableTablesInOneSurface() throws Exception {
        for (String viewName : List.of("admin/users.jsp", "admin/events.jsp", "admin/outbox.jsp")) {
            String view = read(JSP_ROOT.resolve(viewName));
            assertThat(view)
                .as(viewName)
                .contains("admin-register-shell")
                .contains("app-table-titlebar")
                .contains("data-aws-filter-toolbar")
                .contains("class=\"erp-table-scroll\"")
                .contains("data-aws-table-region");
        }
    }

    @Test
    void otpConfirmationModalUsesSharedCompactContainerLayout() throws Exception {
        String guaranteeRequests = read(JSP_ROOT.resolve("app/guarantee-requests.jsp"));
        String consoleCss = read(Path.of("src/main/resources/static/css/console-components.css"));
        String shellCss = read(Path.of("src/main/resources/static/css/shell.css"));

        assertThat(guaranteeRequests)
            .contains("class=\"app-modal-panel app-modal-panel--compact guarantee-otp-modal-panel\" role=\"dialog\" aria-modal=\"true\" aria-labelledby=\"guaranteeApproveTitle-${req.id}\"")
            .contains("id=\"guaranteeApproveTitle-${req.id}\"")
            .contains("class=\"app-modal-body guarantee-otp-modal-body space-y-3\"")
            .contains("class=\"app-modal-section otp-confirmation-panel\"")
            .contains("class=\"otp-confirmation-header\"")
            .contains("class=\"otp-feedback-message mt-3 hidden border px-3 py-2 text-sm\"")
            .contains("class=\"otp-live-status guarantor-otp-live-status")
            .contains("class=\"app-modal-actions\"")
            .contains("modal.setAttribute(\"aria-hidden\", \"true\")")
            .contains("modal.setAttribute(\"aria-hidden\", \"false\")")
            .doesNotContain("guarantee-modal-subtitle-legacy")
            .doesNotContain("text-3xl font-semibold text-sacco-ink");

        assertThat(consoleCss)
            .contains(".otp-confirmation-panel")
            .contains(".otp-confirmation-header")
            .contains(".guarantee-otp-modal-panel .app-modal-header h2")
            .contains("width: min(100%, 16rem)")
            .contains("border-radius: 2px")
            .containsPattern("(?s)\\.app-modal-section \\{\\s*border: 1px solid #e2e8f0;\\s*border-radius: 2px;\\s*background: #f7f8f8;")
            .containsPattern("(?s)@media \\(max-width: 640px\\) \\{.*?\\.app-modal-section \\{\\s*padding: 0\\.95rem;\\s*border-radius: 2px;")
            .doesNotContain("background: #fafcff;")
            .doesNotContainPattern("(?s)\\.app-modal-section \\{\\s*border: 1px solid #e2e8f0;\\s*border-radius: 0\\.75rem;")
            .doesNotContain(".guarantee-modal-subtitle-legacy");

        assertThat(shellCss)
            .containsPattern("(?s)\\.app-modal-section \\{\\s*border-radius: 2px !important;\\s*\\}");
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
