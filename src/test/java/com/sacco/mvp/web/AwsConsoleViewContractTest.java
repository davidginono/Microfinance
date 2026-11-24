package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AwsConsoleViewContractTest {
    private static final Path JSP_ROOT = Path.of("src/main/webapp/WEB-INF/jsp");

    @Test
    void repaymentPagesHaveLiteralMessagesInBothLanguages() throws Exception {
        Pattern message = Pattern.compile("<spring:message\\s+code=[\"']([^\"'$]+)[\"']");
        for (String language : List.of("en", "sw")) {
            Properties bundle = new Properties();
            try (var reader = Files.newBufferedReader(Path.of("src/main/resources/messages_" + language + ".properties"))) {
                bundle.load(reader);
            }
            for (String page : List.of("index", "loan", "receipt")) {
                var matches = message.matcher(read(JSP_ROOT.resolve("repayments/" + page + ".jsp")));
                while (matches.find()) {
                    assertThat(bundle).as(language + " repayment " + page).containsKey(matches.group(1));
                }
            }
        }
    }

    @Test
    void applicationTimeTagMatchesItsRegisteredUri() throws Exception {
        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var descriptor = factory.newDocumentBuilder().parse(JSP_ROOT.getParent().resolve("application-time.tld").toFile());
        String uri = descriptor.getElementsByTagName("uri").item(0).getTextContent();
        assertThat(read(JSP_ROOT.resolve("fragments/header.jspf"))).contains("uri=\"" + uri + "\"");
    }

    @Test
    void everyRouteUsesTheAwsConsoleAuthOrStandalonePrintShell() throws Exception {
        List<Path> routes = routeViews();

        assertThat(routes).isNotEmpty();
        assertThat(routes).allSatisfy(path -> {
            String view = read(path);
            if (isStandalonePrintView(path)) {
                boolean statement = path.getParent().getFileName().toString().equals("statements");
                boolean reconciliation = path.getParent().getFileName().toString().equals("reconciliation");
                boolean management = path.getParent().getFileName().toString().equals("management");
                String prefix = management ? "management" : reconciliation ? "reconciliation" : statement ? "financial-statement" : path.getFileName().toString().equals("statement-print.jsp") ? "client-statement"
                    : path.getParent().getFileName().toString().equals("vouchers") ? "voucher" : "loan";
                assertThat(view).as(path.toString())
                    .containsIgnoringCase("<!doctype html>")
                    .contains("name=\"viewport\"", prefix + "-print")
                    .doesNotContain("fragments/header.jspf", "fragments/sidebar.jspf");
                return;
            }
            boolean publicLanding = path.equals(JSP_ROOT.resolve("landing.jsp")) && view.contains("landing.css");
            boolean publicPortal = path.getParent().equals(JSP_ROOT.resolve("portal"))
                && view.contains("file=\"head.jspf\"")
                && read(path.getParent().resolve("head.jspf")).contains("public-access.css");
            assertThat(usesConsoleShell(path, new HashSet<>()) || view.contains("aws-auth.css") || publicLanding || publicPortal)
                .as("Console or authentication shell for %s", path)
                .isTrue();
        });
    }

    @Test
    void authenticatedPagesExposeAwsHeadersAndRegisterContracts() throws Exception {
        for (Path path : routeViews()) {
            String view = read(path);
            if (usesConsoleShell(path, new HashSet<>()) && !path.getFileName().toString().equals("error.jsp")) {
                assertThat(hasWorkspaceHeader(path,new HashSet<>()))
                    .as("Workspace header for %s",path).isTrue();
            }
            if (view.contains("<table") && !isStandalonePrintView(path) && !isStatementDocumentPreview(path)) {
                assertThat(view)
                    .as(path.toString())
                    .satisfiesAnyOf(
                        content -> assertThat(content).contains("erp-table"),
                        content -> assertThat(content).contains("data-chart-source")
                    )
                    .satisfiesAnyOf(
                        content -> assertThat(content).contains("data-aws-table-region"),
                        content -> assertThat(content).contains("data-resource-table"),
                        content -> assertThat(content).contains("data-chart-source"),
                        // Bounded detail/estimate tables use the shared local sorting adapter without register commands.
                        content -> assertThat(content).contains("data-table-sort-mode=\"local\""),
                        content -> assertThat(content).contains("data-table-sort-mode=\"none\"")
                    );
            }
            if (view.matches("(?s).*method=[\"']get[\"'].*") && !isStatementDocumentPreview(path)) {
                assertThat(view)
                    .as(path.toString())
                    .satisfiesAnyOf(
                        content -> assertThat(content).contains("data-aws-filter-toolbar"),
                        content -> assertThat(content).contains("erp-table-toolbar"),
                        content -> assertThat(content).contains("voucher-filters"),
                        content -> assertThat(content).contains("data-table-filter-for="),
                        content -> assertThat(content).contains("approval-type-filter"),
                        content -> assertThat(content).containsPattern("class=\"[^\"]*\\b(?:accounting-create-form|approval-config-context|approval-filters|management-parameters)\\b[^\"]*\"")
                    );
            }
        }
    }

    @Test
    void conciseWorkspaceHeadersDoNotExposePageSubtitles() throws Exception {
        for (String workspace : List.of("staff", "disbursement")) {
            try (Stream<Path> paths = Files.walk(JSP_ROOT.resolve(workspace))) {
                paths.filter(path -> path.toString().endsWith(".jsp"))
                    .forEach(path -> assertThat(read(path))
                        .as(path.toString())
                        .doesNotContain("erp-page-subtitle"));
            }
        }
    }

    @Test
    void jspMarkupContainsNoLocalStylesOrAutomaticFilterSubmission() throws Exception {
        try (Stream<Path> paths = Files.walk(JSP_ROOT)) {
            paths.filter(path -> path.toString().endsWith(".jsp") || path.toString().endsWith(".jspf"))
                .forEach(path -> {
                    if (isStandalonePrintView(path) || isStatementDocumentPreview(path)) return;
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
            if (!usesConsoleShell(path, new HashSet<>())) {
                continue;
            }
            assertThat(view)
                .as(path.toString())
                .doesNotContainPattern("class=[\"'][^\"']*\\btruncate\\b[^\"']*[\"']");

            java.util.regex.Matcher getForms = java.util.regex.Pattern
                .compile("<form\\b(?:[^>\"']|\"[^\"]*\"|'[^']*')*>", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(view);
            while (getForms.find()) {
                if (!getForms.group().matches("(?is).*\\bmethod\\s*=\\s*[\"']get[\"'].*")) {
                    continue;
                }
                // Spreadsheet templates are immutable downloads, not table filters.
                if(path.equals(JSP_ROOT.resolve("accounting/batches/spreadsheet.jsp")) && getForms.group().contains("/template")) continue;
                // These GET forms download immutable documents; they are not search/filter forms.
                if (isStatementDocumentPreview(path) && getForms.group().contains("/export")) continue;
                if (getForms.group().contains("data-table-export-for=")
                    && getForms.group().contains("/documents/reports/")) continue;
                // Report parameters are a focused creation form, not controls for a registry table.
                if(path.equals(JSP_ROOT.resolve("accounting/management/report.jsp")) && getForms.group().contains("management-report-form")){
                    assertThat(view).contains("management-parameters","management-filter-grid");continue;
                }
                // Selection-dependent GET commands open version proposals; they are not filter toolbars.
                if (getForms.group().contains("data-table-command")
                    || getForms.group().contains("accounting-create-form")) continue;
                assertThat(getForms.group())
                    .as(path + " GET form")
                    .satisfiesAnyOf(
                        content -> assertThat(content).contains("data-aws-filter-toolbar"),
                        content -> assertThat(content).contains("data-table-filter-for="),
                        content -> assertThat(content).containsPattern("class=[\"'][^\"']*\\b(?:aws-filter-toolbar|erp-table-toolbar|voucher-filters|approval-config-context|approval-type-filter|approval-filters|management-parameters)\\b[^\"']*[\"']")
                    );
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
        assertThat(read(Path.of("src/main/resources/static/css/console-resource-details.css")))
            .contains(".aws-console .institution-details-controls {display:flex;flex-flow:row wrap;")
            .contains(".institution-details-controls > label {display:grid;")
            .contains(".institution-details-controls .aws-control {width:100%;min-width:0;}");
    }

    @Test
    void authenticatedOperationalTablesUseNestedCloudscapeSurfacesEverywhere() throws Exception {
        try (Stream<Path> paths = Files.walk(JSP_ROOT)) {
            paths.filter(path -> path.toString().endsWith(".jsp") || path.toString().endsWith(".jspf"))
                .forEach(path -> {
                    if (isStandalonePrintView(path) || isStatementDocumentPreview(path)
                        || path.equals(JSP_ROOT.resolve("accounting/management/document.jspf"))
                        || path.equals(JSP_ROOT.resolve("accounting/loans/statement-document.jspf"))) return;
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
                    if (!view.contains("<table") || view.contains("data-chart-source")) {
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
    void sharedAssetsProvideAwsShellTableAndResponsiveFilterPrimitives() throws Exception {
        String header=read(JSP_ROOT.resolve("fragments/header.jspf"));
        String login=read(JSP_ROOT.resolve("login.jsp"));
        String css=read(Path.of("src/main/resources/static/css/shell.css"));
        String js=read(Path.of("src/main/resources/static/js/shell.js"));
        assertThat(header).contains("amazon-ember.css", "console-components.css", "shell.css", "shell.js", "app-topbar", "consoleNavigationSearch");
        assertThat(login).contains("data-login-password-form", "password-visibility", "forgotPasswordModal").doesNotContain("memberLoginPanel", "/register/member", "/client-portal");
        assertThat(css).contains("#232f3e", ".erp-table-scroll", "@media", ".shell-nav-active");
        assertThat(js).contains("enhanceConsoleTables", "syncActiveSidebarLink", "downloadWithLocalLoading", "WorkspaceNavigation").doesNotContain("'member workspace'");
    }

    @Test
    void financialStatusAndSettingsUseSharedAwsContainerPrimitives() throws Exception {
        assertThat(JSP_ROOT.resolve("staff/settings.jsp")).doesNotExist();
        assertThat(read(JSP_ROOT.resolve("fragments/header.jspf"))).contains("staff/settings/language", "data-shell-language");
        assertThat(read(JSP_ROOT.resolve("fragments/sidebar.jspf"))).doesNotContain("/staff/settings", "section=language");
        assertThat(read(Path.of("src/main/resources/static/css/shell.css"))).contains(".aws-settings-control-row", ".aws-settings-panel--compact");
    }

    @Test
    void regionalLoadingAndWorkspaceActionsNeverCreateAPageOverlay() throws Exception {
        String shellJs = read(Path.of("src/main/resources/static/js/shell.js"));
        String loading = read(Path.of("src/main/resources/static/js/ui-loading.js"));
        assertThat(shellJs)
            .contains("finish?.();")
            .doesNotContain("showPageSubmitPreloader", "pageSubmitPreloader")
            .contains("if (!event.defaultPrevented)")
            .contains("window.AppLoading?.end(region)");
        assertThat(loading)
            .contains("window.AppLoading={begin,end,status,clear,fail,action}")
            .contains("element.setAttribute('aria-label'")
            .contains("state.loader.remove()");
    }

    @Test
    void analyticsFiltersRespectInterceptedRequestsAndKeepNativeSubmissionFeedback() throws Exception {
        String staffAnalytics = read(JSP_ROOT.resolve("staff/analytics.jsp"));
        String shellJs = read(Path.of("src/main/resources/static/js/shell.js"));

        assertThat(staffAnalytics)
            .contains("action=\"/staff/analytics\" method=\"get\"")
            .contains("data-page-preloader=\"true\"");
        assertThat(shellJs)
            .contains("if (!event.defaultPrevented)")
            .contains("showSubmissionLoading(form,event.submitter)")
            .contains("clearConsoleTableLoading();");
    }

    @Test
    void documentDownloadsUseSelfClearingLocalActionSpinners() throws Exception {
        String js=read(Path.of("src/main/resources/static/js/shell.js"));
        assertThat(js).contains("downloadWithLocalLoading", "exportDownloadTimeoutMs", "controller.abort()", "resolveDownloadFilename", "finish?.();");
        String details=read(JSP_ROOT.resolve("fragments/staff-loan-detail-header.jspf"));
        assertThat(details).contains("data-loan-export-url", "data-print-action", "loan-document-action-icon");
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

    private static List<Path> routeViews() throws IOException {
        try (Stream<Path> paths = Files.walk(JSP_ROOT)) {
            return paths
                .filter(path -> path.toString().endsWith(".jsp"))
                // This MVC view is a scoped table-row response, not a complete document.
                .filter(path -> !path.equals(JSP_ROOT.resolve("accounting/account-rows.jsp")))
                .sorted()
                .toList();
        }
    }

    private static boolean hasWorkspaceHeader(Path path,Set<Path> visited) {
        path=path.normalize();
        if(!path.startsWith(JSP_ROOT.normalize()) || !visited.add(path)
            || Set.of("header.jspf","sidebar.jspf","footer.jspf").contains(path.getFileName().toString()))return false;
        String view=read(path);
        if(view.contains("data-aws-page-header") || Pattern.compile("<h1\\b").matcher(view).find()
            || Pattern.compile("(?s)data-resource-table=[\"'][^\"']+[\"'].*?<h2\\b").matcher(view).find())return true;
        var includes=Pattern.compile("<%@\\s*include\\s+file=[\"']([^\"']+)[\"']\\s*%>").matcher(view);
        while(includes.find())if(hasWorkspaceHeader(path.getParent().resolve(includes.group(1)),visited))return true;
        return false;
    }

    private static boolean usesConsoleShell(Path path, Set<Path> visited) {
        path = path.normalize();
        if (!visited.add(path)) {
            return false;
        }
        String view = read(path);
        if (view.contains("fragments/header.jspf")) {
            return true;
        }
        var includes = Pattern.compile("<%@\\s*include\\s+file=[\"']([^\"']+)[\"']\\s*%>").matcher(view);
        while (includes.find()) {
            if (usesConsoleShell(path.getParent().resolve(includes.group(1)), visited)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStatementDocumentPreview(Path path) {
        // Reference financial stationery intentionally retains its own typography and columns.
        return path.normalize().equals(JSP_ROOT.resolve("accounting/statements/preview.jsp").normalize())
            || path.normalize().equals(JSP_ROOT.resolve("accounting/management/source-tables.jspf").normalize());
    }

    private static boolean isStandalonePrintView(Path path) {
        return path.equals(JSP_ROOT.resolve("accounting/vouchers/print.jsp"))
            || path.equals(JSP_ROOT.resolve("accounting/loans/print.jsp"))
            || path.equals(JSP_ROOT.resolve("accounting/loans/statement-print.jsp"))
            || path.equals(JSP_ROOT.resolve("accounting/statements/print.jsp"))
            || path.equals(JSP_ROOT.resolve("accounting/reconciliation/print.jsp"))
            || path.equals(JSP_ROOT.resolve("accounting/management/print.jsp"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }
}
