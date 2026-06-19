package com.sacco.mvp.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.LoanAnalyticsService;
import com.sacco.mvp.service.LoanReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
@PreAuthorize("@authz.staffAnalyticsAccess(principal)")
public class StaffAnalyticsController {
    private final LoanAnalyticsService loanAnalyticsService;
    private final LoanReportService loanReportService;
    private final ObjectMapper objectMapper;
    private final ApplicationClock applicationClock;

    @GetMapping("/analytics")
    public String analytics(@AuthenticationPrincipal AppUserPrincipal principal,
                            @RequestParam(required = false) LocalDate fromDate,
                            @RequestParam(required = false) LocalDate toDate,
                            @RequestParam(required = false) LoanType loanType,
                            @RequestParam(required = false, defaultValue = "staff") String viewAs,
                            Model model) {
        LocalDate resolvedTo = toDate == null ? applicationClock.today() : toDate;
        LocalDate resolvedFrom = fromDate == null ? resolvedTo.minusYears(1) : fromDate;
        if (resolvedFrom.isAfter(resolvedTo)) {
            LocalDate swap = resolvedFrom;
            resolvedFrom = resolvedTo;
            resolvedTo = swap;
        }

        boolean canViewStationAnalytics = true;
        String selectedView = "staff".equalsIgnoreCase(viewAs) && canViewStationAnalytics ? "staff" : "member";
        boolean stationWideStaffView = "staff".equals(selectedView);
        boolean staffReviewView = !stationWideStaffView;
        DateRange previousRange = previousRange(resolvedFrom, resolvedTo);
        LoanAnalyticsService.StaffReviewAnalytics staffReviewAnalytics = staffReviewView
            ? loanAnalyticsService.staffReviewAnalytics(principal, resolvedFrom, resolvedTo, loanType, null)
            : null;

        LoanAnalyticsService.MemberLoanAnalytics analytics = staffReviewView
            ? loanAnalyticsService.forStaff(principal, resolvedFrom, resolvedTo, loanType, null)
            : stationWideStaffView
                ? loanAnalyticsService.forStation(principal.getSaccoId(), principal.getStationId(), resolvedFrom, resolvedTo, loanType, null)
            : loanAnalyticsService.forStaff(principal, resolvedFrom, resolvedTo, loanType, null);
        LoanAnalyticsService.MemberLoanAnalytics previousAnalytics = staffReviewView
            ? loanAnalyticsService.forStaff(principal, previousRange.fromDate(), previousRange.toDate(), loanType, null)
            : stationWideStaffView
                ? loanAnalyticsService.forStation(principal.getSaccoId(), principal.getStationId(), previousRange.fromDate(), previousRange.toDate(), loanType, null)
            : loanAnalyticsService.forStaff(principal, previousRange.fromDate(), previousRange.toDate(), loanType, null);
        List<LoanAnalyticsService.MetricTrendSeries> trendSeries = staffReviewView
            ? staffReviewAnalytics.trendSeries()
            : stationWideStaffView
                ? loanAnalyticsService.statusTrendForStation(principal.getSaccoId(), principal.getStationId(), resolvedFrom, resolvedTo, loanType, null)
            : loanAnalyticsService.statusTrendForStaff(principal, resolvedFrom, resolvedTo, loanType, null);
        List<LoanAnalyticsService.LoanProductPerformance> productPerformance = staffReviewView
            ? loanAnalyticsService.productPerformanceForStaff(principal, resolvedFrom, resolvedTo, loanType, null)
            : stationWideStaffView
                ? loanAnalyticsService.productPerformanceForStation(principal.getSaccoId(), principal.getStationId(), resolvedFrom, resolvedTo, loanType, null)
            : loanAnalyticsService.productPerformanceForStaff(principal, resolvedFrom, resolvedTo, loanType, null);
        Map<String, LoanAnalyticsService.MetricDelta> metricDeltas = loanAnalyticsService.metricDeltas(analytics, previousAnalytics)
            .stream()
            .collect(Collectors.toMap(LoanAnalyticsService.MetricDelta::key, Function.identity()));
        BigDecimal totalInterestAccumulated = BigDecimal.ZERO;
        List<LoanReportService.ProductFinancialBreakdownRow> productFinancialRows = List.of();
        if (stationWideStaffView) {
            LoanReportService.AnalyticsExportReport interestReport =
                loanReportService.staffAnalyticsExportReport(principal, resolvedFrom, resolvedTo, loanType, selectedView);
            if (interestReport != null) {
                totalInterestAccumulated = interestReport.productRows()
                    .stream()
                    .map(LoanReportService.ProductPerformanceRow::interestPaid)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                productFinancialRows = interestReport.productFinancialRows() == null ? List.of() : interestReport.productFinancialRows();
            }
        }
        ProductFinancialTotals productFinancialTotals = productFinancialTotals(productFinancialRows);

        model.addAttribute("analytics", analytics);
        model.addAttribute("metricCards", metricCards(analytics, metricDeltas));
        model.addAttribute("metricPeriodLabel", previousPeriodLabel(resolvedFrom, resolvedTo));
        model.addAttribute("metricComparisonLabel", comparisonLabel(resolvedFrom, resolvedTo, previousRange));
        model.addAttribute("staffPortfolio", staffReviewView
            ? loanAnalyticsService.staffPortfolio(principal, resolvedFrom, resolvedTo, loanType, null)
            : stationWideStaffView
                ? loanAnalyticsService.stationPortfolio(principal.getSaccoId(), principal.getStationId(), resolvedFrom, resolvedTo, loanType, null)
            : loanAnalyticsService.staffPortfolio(principal, resolvedFrom, resolvedTo, loanType, null));
        model.addAttribute("productPerformance", productPerformance);
        model.addAttribute("staffReviewAnalytics", staffReviewAnalytics);
        model.addAttribute("productPerformanceJson", toJson(staffReviewView
            ? loanAnalyticsService.staffReviewProductChartSeries(staffReviewAnalytics.productRows())
            : loanAnalyticsService.productChartSeries(productPerformance)));
        model.addAttribute("totalInterestAccumulated", totalInterestAccumulated);
        model.addAttribute("totalInterestAccumulatedLabel", moneyLabel(totalInterestAccumulated));
        model.addAttribute("totalInterestUnpaidLabel", moneyLabel(productFinancialTotals.totalInterestUnpaid()));
        model.addAttribute("totalLoanAmountPaidLabel", moneyLabel(productFinancialTotals.totalLoanAmountPaid()));
        model.addAttribute("totalLoanAmountUnpaidLabel", moneyLabel(productFinancialTotals.totalLoanAmountUnpaid()));
        model.addAttribute("productFinancialRows", productFinancialRows);
        model.addAttribute("selectedLoanProductLabel", loanType == null ? "All Products" : loanType.getDisplayLabel());
        model.addAttribute("trendSeriesJson", toJson(trendSeries));
        model.addAttribute("fromDate", resolvedFrom);
        model.addAttribute("toDate", resolvedTo);
        model.addAttribute("loanType", loanType);
        model.addAttribute("loanTypes", LoanType.values());
        model.addAttribute("viewAs", selectedView);
        model.addAttribute("staffReviewView", staffReviewView);
        model.addAttribute("canViewStationAnalytics", canViewStationAnalytics);
        model.addAttribute("stationWideStaffView", stationWideStaffView);
        model.addAttribute("staffAnalyticsTitle", stationWideStaffView ? "Station Loan Status" : "Staff Loan Review Analytics");
        model.addAttribute("staffAnalyticsSubtitle", stationWideStaffView
            ? "View paid, disbursed, active, defaulted, rejected, and other loan status metrics for all members in your station."
            : "View review activity, decisions, and staff portfolio performance within a selected period.");
        if (stationWideStaffView) {
            LoanReportService.StationAnalyticsExportReport stationReport = loanReportService.stationAnalyticsReport(
                principal.getSaccoId(),
                principal.getStationId(),
                resolvedFrom,
                resolvedTo,
                loanType,
                principal.getFullName(),
                roleLabel(principal.getPosition())
            );
            model.addAttribute("stationParticipation", stationReport.participation());
            model.addAttribute("stationTotalMembers", stationReport.participation().activeStationMembers());
            model.addAttribute("stationTotalApplicants", stationReport.participation().uniqueApplicants());
        }
        model.addAttribute("staffName", principal.getFullName());
        return "staff/analytics";
    }

    private DateRange previousRange(LocalDate fromDate, LocalDate toDate) {
        long days = Math.max(ChronoUnit.DAYS.between(fromDate, toDate), 0);
        LocalDate previousTo = fromDate.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days);
        return new DateRange(previousFrom, previousTo);
    }

    private String previousPeriodLabel(LocalDate fromDate, LocalDate toDate) {
        long days = Math.max(ChronoUnit.DAYS.between(fromDate, toDate) + 1, 1);
        if (days >= 60) {
            long months = Math.max(1, Math.round(days / 30.4375d));
            return months == 1 ? "previous month" : "previous " + months + " months";
        }
        return days == 1 ? "previous day" : "previous " + days + " days";
    }

    private String comparisonLabel(LocalDate fromDate, LocalDate toDate, DateRange previousRange) {
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH);
        return "All metrics compare the selected period (" + formatter.format(fromDate) + " - " + formatter.format(toDate)
            + ") with the " + previousPeriodLabel(fromDate, toDate) + " (" + formatter.format(previousRange.fromDate())
            + " - " + formatter.format(previousRange.toDate()) + ").";
    }

    private List<AnalyticsMetricCard> metricCards(LoanAnalyticsService.MemberLoanAnalytics analytics,
                                                  Map<String, LoanAnalyticsService.MetricDelta> deltas) {
        return List.of(
            metricCard("applied", "Applied Loans", analytics.appliedLoans(), "blue", "Applied", deltas),
            metricCard("active", "Active Loans", analytics.activeLoans(), "emerald", "Active", deltas),
            metricCard("disbursed", "Disbursed Loans", analytics.disbursedLoans(), "violet", "Disbursed", deltas),
            metricCard("paid", "Paid Loans", analytics.paidLoans(), "green", "Paid", deltas),
            metricCard("defaulted", "Defaulted Loans", analytics.defaultedLoans(), "orange", "Defaulted", deltas),
            metricCard("forfeited", "Forfeited Loan Applications", analytics.forfeitedLoans(), "rose", "Forfeited", deltas),
            metricCard("rejected", "Rejected Loans", analytics.rejectedLoans(), "slate", "Rejected", deltas)
        );
    }

    private AnalyticsMetricCard metricCard(String key,
                                           String label,
                                           long value,
                                           String tone,
                                           String sparkName,
                                           Map<String, LoanAnalyticsService.MetricDelta> deltas) {
        BigDecimal percent = deltas.getOrDefault(key, new LoanAnalyticsService.MetricDelta(key, BigDecimal.ZERO, false)).percent();
        boolean positive = percent.compareTo(BigDecimal.ZERO) >= 0;
        return new AnalyticsMetricCard(key, label, value, tone, sparkName, formatPercent(percent), positive);
    }

    private String formatPercent(BigDecimal value) {
        String sign = value.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        return sign + value.setScale(2, java.math.RoundingMode.HALF_UP) + "%";
    }

    private String moneyLabel(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount.setScale(2, java.math.RoundingMode.HALF_UP);
        DecimalFormat format = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.US));
        return format.format(safe);
    }

    private ProductFinancialTotals productFinancialTotals(List<LoanReportService.ProductFinancialBreakdownRow> rows) {
        BigDecimal interestUnpaid = BigDecimal.ZERO;
        BigDecimal loanAmountPaid = BigDecimal.ZERO;
        BigDecimal loanAmountUnpaid = BigDecimal.ZERO;
        for (LoanReportService.ProductFinancialBreakdownRow row : rows) {
            interestUnpaid = interestUnpaid.add(row.totalInterestUnpaid() == null ? BigDecimal.ZERO : row.totalInterestUnpaid());
            loanAmountPaid = loanAmountPaid.add(row.totalLoanAmountPaid() == null ? BigDecimal.ZERO : row.totalLoanAmountPaid());
            loanAmountUnpaid = loanAmountUnpaid.add(row.totalLoanAmountUnpaid() == null ? BigDecimal.ZERO : row.totalLoanAmountUnpaid());
        }
        return new ProductFinancialTotals(interestUnpaid, loanAmountPaid, loanAmountUnpaid);
    }

    private String roleLabel(Position position) {
        if (position == null) {
            return "Staff";
        }
        String lower = position.name().toLowerCase(java.util.Locale.ENGLISH).replace('_', ' ');
        StringBuilder label = new StringBuilder();
        for (String part : lower.split(" ")) {
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return label.toString();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private LoanAnalyticsService.StaffPortfolioSummary memberPortfolio(LoanAnalyticsService.MemberLoanAnalytics analytics) {
        BigDecimal defaultedRate = analytics.disbursedLoans() == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(analytics.defaultedLoans())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(analytics.disbursedLoans()), 2, java.math.RoundingMode.HALF_UP);
        String riskLevel = defaultedRate.compareTo(BigDecimal.valueOf(10)) >= 0
            ? "High"
            : defaultedRate.compareTo(BigDecimal.valueOf(5)) >= 0 ? "Moderate" : "Low";
        return new LoanAnalyticsService.StaffPortfolioSummary(
            analytics.appliedLoans(),
            analytics.disbursedLoans(),
            analytics.rejectedLoans(),
            analytics.disbursedLoans(),
            analytics.defaultedLoans(),
            defaultedRate,
            riskLevel
        );
    }

    public record AnalyticsMetricCard(String key, String label, long value, String tone, String sparkName, String percentLabel, boolean positive) {
        public String getKey() {
            return key;
        }

        public String getLabel() {
            return label;
        }

        public long getValue() {
            return value;
        }

        public String getTone() {
            return tone;
        }

        public String getSparkName() {
            return sparkName;
        }

        public String getPercentLabel() {
            return percentLabel;
        }

        public boolean isPositive() {
            return positive;
        }
    }

    private record DateRange(LocalDate fromDate, LocalDate toDate) {}

    private record ProductFinancialTotals(
        BigDecimal totalInterestUnpaid,
        BigDecimal totalLoanAmountPaid,
        BigDecimal totalLoanAmountUnpaid
    ) {}
}
