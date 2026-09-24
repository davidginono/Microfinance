package com.sacco.mvp.web;

import com.sacco.mvp.domain.IncidentStatus;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.OutboxStatus;
import com.sacco.mvp.domain.SaccoAccessStatus;
import com.sacco.mvp.domain.SmsUnitStatus;
import com.sacco.mvp.service.AdminService;
import com.sacco.mvp.service.LoanReportService;
import com.sacco.mvp.service.NotificationViewService;
import com.sacco.mvp.service.PlatformAdminService;
import com.sacco.mvp.service.SaccoRegistryService;
import jakarta.el.ExpressionFactory;
import jakarta.el.StandardELContext;
import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JspRecordAccessorCompatibilityTest {
    private static final Pattern JSP_PROPERTY = Pattern.compile("\\.([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern RECORD_DECLARATION = Pattern.compile(
        "\\brecord\\s+([A-Za-z0-9_]+)\\s*\\((.*?)\\)\\s*\\{",
        Pattern.DOTALL
    );
    private static final Pattern PUBLIC_ZERO_ARG_METHOD = Pattern.compile(
        "public\\s+[A-Za-z0-9_.$<>?,\\[\\]\\s]+\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(\\s*\\)\\s*\\{"
    );
    private static final Pattern JAVABEAN_GETTER = Pattern.compile(
        "public\\s+[A-Za-z0-9_.$<>?,\\[\\]\\s]+\\s+(get[A-Z][A-Za-z0-9_]*|is[A-Z][A-Za-z0-9_]*)\\s*\\(\\s*\\)\\s*\\{"
    );
    private static final Pattern ANNOTATION = Pattern.compile("@[A-Za-z0-9_.$]+(?:\\([^)]*\\))?\\s*");
    private static final Pattern COMPONENT_NAME = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)$");

    @Test
    void jakartaElResolvesDerivedRecordPropertiesUsedByJsps() {
        PlatformAdminService.PlatformDashboard dashboard = new PlatformAdminService.PlatformDashboard(
            List.of(),
            1,
            4,
            new BigDecimal("1250000"),
            1,
            0,
            0,
            0,
            List.of()
        );
        assertElProperty("dashboard", dashboard, "totalDisbursedPrincipalLabel",
            dashboard.getTotalDisbursedPrincipalLabel());

        PlatformAdminService.SaccoDetailView detail = new PlatformAdminService.SaccoDetailView(
            "1001",
            summary(),
            List.of(new PlatformAdminService.LoanStatusCount(LoanStatus.READY_FOR_MANAGER, "On Review By Manager", 2)),
            2,
            List.of(),
            List.of("STN001"),
            "STN001"
        );
        assertElProperty("detail", detail, "stationScoped", detail.isStationScoped());
        assertElProperty("detail", detail, "totalLoanApplicationCount",
            detail.getTotalLoanApplicationCount());
        assertElProperty("statusCount", detail.getLoanStatusCounts().get(0), "statusLabel",
            detail.getLoanStatusCounts().get(0).getStatusLabel());

        PlatformAdminService.SaccoSummary summary = summary();
        assertElProperty("summary", summary, "logoFallbackText", summary.getLogoFallbackText());
        assertElProperty("summary", summary, "toneCardClass", summary.getToneCardClass());
        assertElProperty("summary", summary, "accessStatusLabel", summary.getAccessStatusLabel());
        assertElProperty("summary", summary, "accessSuspended", summary.isAccessSuspended());
        assertElProperty("summary", summary, "stationListLabel", summary.getStationListLabel());
        assertElProperty("summary", summary, "totalDisbursedPrincipalFullLabel",
            summary.getTotalDisbursedPrincipalFullLabel());

        SaccoRegistryService.StationView station = new SaccoRegistryService.StationView("STN001", null);
        assertElProperty("station", station, "addressLocationLabel", station.getAddressLocationLabel());

        SaccoRegistryService.RegisteredSaccoView sacco = new SaccoRegistryService.RegisteredSaccoView(
            "1001",
            "Demo SACCO",
            List.of(station),
            false,
            null,
            SaccoAccessStatus.PAYMENT_DUE,
            true
        );
        assertElProperty("sacco", sacco, "accessBadgeClass", sacco.getAccessBadgeClass());
        assertElProperty("sacco", sacco, "accessStatusLabel", sacco.getAccessStatusLabel());
        assertElProperty("sacco", sacco, "stationIdsText", sacco.getStationIdsText());
        assertElProperty("sacco", sacco, "loanTopUpEnabled", sacco.isLoanTopUpEnabled());

        AdminService.AdminDashboard adminDashboard = new AdminService.AdminDashboard(
            memberCounts(),
            Map.of(),
            Map.of(),
            List.of(),
            List.of(),
            List.of(),
            new AdminService.SmsBalanceSummary("STN001", 20, SmsUnitStatus.LOW),
            true,
            2,
            0
        );
        assertElProperty("adminDashboard", adminDashboard, "activeMemberCount",
            adminDashboard.getActiveMemberCount());
        assertElProperty("adminDashboard", adminDashboard, "inactiveMemberCount",
            adminDashboard.getInactiveMemberCount());
        assertElProperty("sms", adminDashboard.smsBalance(), "statusLabel",
            adminDashboard.smsBalance().getStatusLabel());

        AdminService.SupportArchiveView support = new AdminService.SupportArchiveView(
            UUID.randomUUID(),
            "Subject",
            "Message",
            IncidentStatus.OPEN,
            OffsetDateTime.parse("2026-07-21T09:00:00+03:00"),
            false
        );
        assertElProperty("support", support, "readLabel", support.getReadLabel());

        LoanReportService.ProductFinancialBreakdownRow breakdown =
            new LoanReportService.ProductFinancialBreakdownRow(
                "Emergency Loan",
                new BigDecimal("10"),
                new BigDecimal("20"),
                new BigDecimal("30"),
                new BigDecimal("40")
            );
        assertElProperty("breakdown", breakdown, "totalInterestPaidLabel",
            breakdown.getTotalInterestPaidLabel());
        assertElProperty("breakdown", breakdown, "totalLoanAmountUnpaidLabel",
            breakdown.getTotalLoanAmountUnpaidLabel());

        NotificationViewService.NotificationView notification =
            new NotificationViewService.NotificationView(
                UUID.randomUUID(),
                "SYSTEM",
                "System",
                "Subject",
                "One | Two",
                "System",
                null,
                null,
                null,
                "One | Two",
                "NEW",
                "2026-07-21",
                "21 Jul 2026",
                true
            );
        assertElProperty("notification", notification, "detailItems", notification.getDetailItems());
    }

    @Test
    void jspUsedDerivedRecordGettersExposeRecordStyleAccessors() throws IOException {
        Path projectRoot = Path.of("").toAbsolutePath();
        Set<String> jspProperties = jspProperties(projectRoot.resolve("src/main/webapp/WEB-INF/jsp"));
        List<String> missingAccessors = new ArrayList<>();

        try (Stream<Path> javaFiles = Files.walk(projectRoot.resolve("src/main/java"))) {
            for (Path javaFile : javaFiles.filter(path -> path.toString().endsWith(".java")).toList()) {
                collectMissingAccessors(projectRoot, javaFile, jspProperties, missingAccessors);
            }
        }

        assertTrue(missingAccessors.isEmpty(),
            "JSP-used derived record getters need record-style accessors for Tomcat/Jakarta EL: "
                + missingAccessors);
    }

    private static Set<String> jspProperties(Path jspRoot) throws IOException {
        Set<String> properties = new HashSet<>();
        try (Stream<Path> jspFiles = Files.walk(jspRoot)) {
            for (Path jspFile : jspFiles
                .filter(path -> path.toString().endsWith(".jsp") || path.toString().endsWith(".jspf"))
                .toList()) {
                Matcher matcher = JSP_PROPERTY.matcher(Files.readString(jspFile));
                while (matcher.find()) {
                    properties.add(matcher.group(1));
                }
            }
        }
        return properties;
    }

    private static void collectMissingAccessors(
        Path projectRoot,
        Path javaFile,
        Set<String> jspProperties,
        List<String> missingAccessors
    ) throws IOException {
        String source = Files.readString(javaFile);
        Matcher records = RECORD_DECLARATION.matcher(source);
        while (records.find()) {
            String recordName = records.group(1);
            Set<String> components = componentNames(records.group(2));
            int bodyOpenIndex = records.start() + records.group(0).length() - 1;
            int bodyCloseIndex = matchingBraceIndex(source, bodyOpenIndex);
            if (bodyCloseIndex < 0) {
                continue;
            }
            String body = source.substring(bodyOpenIndex + 1, bodyCloseIndex);
            Set<String> zeroArgMethods = zeroArgMethods(body);
            Matcher getters = JAVABEAN_GETTER.matcher(body);
            while (getters.find()) {
                String getterName = getters.group(1);
                String propertyName = propertyNameForGetter(getterName);
                if (!components.contains(propertyName)
                    && !zeroArgMethods.contains(propertyName)
                    && jspProperties.contains(propertyName)) {
                    missingAccessors.add(projectRoot.relativize(javaFile)
                        + " " + recordName + "." + propertyName + "()");
                }
            }
        }
    }

    private static Set<String> componentNames(String recordParameters) {
        Set<String> names = new HashSet<>();
        for (String parameter : splitTopLevelCommas(recordParameters)) {
            String cleaned = ANNOTATION.matcher(parameter).replaceAll("").trim();
            Matcher matcher = COMPONENT_NAME.matcher(cleaned);
            if (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    private static Set<String> zeroArgMethods(String body) {
        Set<String> methods = new HashSet<>();
        Matcher matcher = PUBLIC_ZERO_ARG_METHOD.matcher(body);
        while (matcher.find()) {
            methods.add(matcher.group(1));
        }
        return methods;
    }

    private static String propertyNameForGetter(String getterName) {
        if (getterName.startsWith("get")) {
            return Introspector.decapitalize(getterName.substring(3));
        }
        return Introspector.decapitalize(getterName.substring(2));
    }

    private static List<String> splitTopLevelCommas(String value) {
        List<String> parts = new ArrayList<>();
        int angleDepth = 0;
        int parenthesisDepth = 0;
        int bracketDepth = 0;
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '<' -> angleDepth++;
                case '>' -> angleDepth = Math.max(0, angleDepth - 1);
                case '(' -> parenthesisDepth++;
                case ')' -> parenthesisDepth = Math.max(0, parenthesisDepth - 1);
                case '[' -> bracketDepth++;
                case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
                case ',' -> {
                    if (angleDepth == 0 && parenthesisDepth == 0 && bracketDepth == 0) {
                        parts.add(value.substring(start, i).trim());
                        start = i + 1;
                    }
                }
                default -> {
                }
            }
        }
        String tail = value.substring(start).trim();
        if (!tail.isEmpty()) {
            parts.add(tail);
        }
        return parts;
    }

    private static int matchingBraceIndex(String source, int openingBraceIndex) {
        int depth = 0;
        for (int i = openingBraceIndex; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static PlatformAdminService.SaccoSummary summary() {
        return new PlatformAdminService.SaccoSummary(
            "1001",
            "Demo SACCO",
            List.of("STN001", "STN002"),
            false,
            null,
            12,
            10,
            2,
            3,
            new BigDecimal("1500000"),
            new BigDecimal("3000000"),
            new BigDecimal("750000"),
            new BigDecimal("82.5"),
            new BigDecimal("4.5"),
            new BigDecimal("1.25"),
            "Healthy",
            "green",
            "Healthy portfolio.",
            false,
            SaccoAccessStatus.SUSPENDED,
            LocalDate.of(2026, 7, 31),
            OffsetDateTime.parse("2026-07-21T09:00:00+03:00"),
            "Payment overdue",
            "en"
        );
    }

    private static Map<MemberStatus, Long> memberCounts() {
        EnumMap<MemberStatus, Long> counts = new EnumMap<>(MemberStatus.class);
        counts.put(MemberStatus.ACTIVE, 2L);
        counts.put(MemberStatus.INACTIVE, 1L);
        return counts;
    }

    private static void assertElProperty(
        String variableName,
        Object value,
        String propertyName,
        Object expected
    ) {
        ExpressionFactory expressionFactory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(expressionFactory);
        context.getVariableMapper().setVariable(variableName,
            expressionFactory.createValueExpression(value, value.getClass()));
        Object actual = expressionFactory
            .createValueExpression(context, "${" + variableName + "." + propertyName + "}", Object.class)
            .getValue(context);
        assertTrue(expected.equals(actual), "Expected EL property " + propertyName + " to resolve to " + expected
            + " but got " + actual);
    }
}
