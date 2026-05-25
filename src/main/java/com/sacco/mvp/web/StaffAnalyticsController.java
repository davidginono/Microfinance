package com.sacco.mvp.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.LoanAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
@PreAuthorize("@authz.staffAnalyticsAccess(principal)")
public class StaffAnalyticsController {
    private final LoanAnalyticsService loanAnalyticsService;
    private final ObjectMapper objectMapper;

    @GetMapping("/analytics")
    public String analytics(@AuthenticationPrincipal AppUserPrincipal principal,
                            @RequestParam(required = false) LocalDate fromDate,
                            @RequestParam(required = false) LocalDate toDate,
                            @RequestParam(required = false) LoanType loanType,
                            @RequestParam(required = false, defaultValue = "staff") String viewAs,
                            Model model) {
        LocalDate resolvedTo = toDate == null ? LocalDate.now() : toDate;
        LocalDate resolvedFrom = fromDate == null ? resolvedTo.minusYears(1) : fromDate;
        if (resolvedFrom.isAfter(resolvedTo)) {
            LocalDate swap = resolvedFrom;
            resolvedFrom = resolvedTo;
            resolvedTo = swap;
        }

        String selectedView = "member".equalsIgnoreCase(viewAs) && principal.isMemberAccess() ? "member" : "staff";
        DateRange previousRange = previousRange(resolvedFrom, resolvedTo);

        LoanAnalyticsService.MemberLoanAnalytics analytics = "member".equals(selectedView)
            ? loanAnalyticsService.forMember(principal.getMemberId(), resolvedFrom, resolvedTo, loanType, null)
            : loanAnalyticsService.forStaff(principal, resolvedFrom, resolvedTo, loanType, null);
        LoanAnalyticsService.MemberLoanAnalytics previousAnalytics = "member".equals(selectedView)
            ? loanAnalyticsService.forMember(principal.getMemberId(), previousRange.fromDate(), previousRange.toDate(), loanType, null)
            : loanAnalyticsService.forStaff(principal, previousRange.fromDate(), previousRange.toDate(), loanType, null);
        List<LoanAnalyticsService.MetricTrendSeries> trendSeries = "member".equals(selectedView)
            ? loanAnalyticsService.statusTrendForMember(principal.getMemberId(), resolvedFrom, resolvedTo, loanType, null)
            : loanAnalyticsService.statusTrendForStaff(principal, resolvedFrom, resolvedTo, loanType, null);
        List<LoanAnalyticsService.LoanProductPerformance> productPerformance = "member".equals(selectedView)
            ? loanAnalyticsService.productPerformanceForMember(principal.getSaccoId(), principal.getStationId(), principal.getMemberId(), resolvedFrom, resolvedTo, null)
            : loanAnalyticsService.productPerformanceForStaff(principal, resolvedFrom, resolvedTo, null);
        Map<String, LoanAnalyticsService.MetricDelta> metricDeltas = loanAnalyticsService.metricDeltas(analytics, previousAnalytics)
            .stream()
            .collect(Collectors.toMap(LoanAnalyticsService.MetricDelta::key, Function.identity()));

        model.addAttribute("analytics", analytics);
        model.addAttribute("metricCards", metricCards(analytics, metricDeltas));
        model.addAttribute("metricPeriodLabel", previousPeriodLabel(resolvedFrom, resolvedTo));
        model.addAttribute("metricComparisonLabel", comparisonLabel(resolvedFrom, resolvedTo, previousRange));
        model.addAttribute("staffPortfolio", "member".equals(selectedView)
            ? memberPortfolio(analytics)
            : loanAnalyticsService.staffPortfolio(principal, resolvedFrom, resolvedTo, loanType, null));
        model.addAttribute("productPerformance", productPerformance);
        model.addAttribute("productPerformanceJson", toJson(loanAnalyticsService.productChartSeries(productPerformance)));
        model.addAttribute("trendSeriesJson", toJson(trendSeries));
        model.addAttribute("fromDate", resolvedFrom);
        model.addAttribute("toDate", resolvedTo);
        model.addAttribute("loanType", loanType);
        model.addAttribute("loanTypes", LoanType.values());
        model.addAttribute("viewAs", selectedView);
        model.addAttribute("canViewMemberAnalytics", principal.isMemberAccess());
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
}
