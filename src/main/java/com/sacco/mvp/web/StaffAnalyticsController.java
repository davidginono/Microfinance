package com.sacco.mvp.web;

import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.LoanAnalyticsService;
import com.sacco.mvp.service.LoanReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
@PreAuthorize("@authz.staffAnalyticsAccess(principal)")
public class StaffAnalyticsController {
    private final LoanAnalyticsService loanAnalyticsService;
    private final LoanReportService loanReportService;
    private final ObjectMapper objectMapper;
    private final ApplicationClock applicationClock;
    private final LoanProductSettingRepository loanProductSettingRepository;

    @GetMapping("/analytics")
    public String analytics(@AuthenticationPrincipal AppUserPrincipal principal,
                            @RequestParam(required = false) LocalDate fromDate,
                            @RequestParam(required = false) LocalDate toDate,
                            @RequestParam(required = false) java.util.UUID loanProductId,
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
        LoanProductSetting selectedProduct = selectedAnalyticsProduct(principal.getSaccoId(), loanProductId, loanType);
        LoanType resolvedLoanType = selectedProduct == null ? loanType : selectedProduct.getLoanType();
        java.util.UUID resolvedLoanProductId = selectedProduct == null ? null : selectedProduct.getId();
        LoanAnalyticsService.StaffReviewAnalytics staffReviewAnalytics = staffReviewView
            ? loanAnalyticsService.staffReviewAnalytics(principal, resolvedFrom, resolvedTo, resolvedLoanType, resolvedLoanProductId, null)
            : null;

        LoanAnalyticsService.MemberLoanAnalytics analytics = staffReviewView
            ? loanAnalyticsService.forStaff(principal, resolvedFrom, resolvedTo, resolvedLoanType, resolvedLoanProductId, null)
            : stationWideStaffView
                ? loanAnalyticsService.forStation(principal.getSaccoId(), principal.getStationId(), resolvedFrom, resolvedTo, resolvedLoanType, resolvedLoanProductId, null)
            : loanAnalyticsService.forStaff(principal, resolvedFrom, resolvedTo, resolvedLoanType, resolvedLoanProductId, null);
        List<LoanAnalyticsService.MetricTrendSeries> trendSeries = staffReviewView
            ? staffReviewAnalytics.trendSeries()
            : stationWideStaffView
                ? loanAnalyticsService.statusTrendForStation(principal.getSaccoId(), principal.getStationId(), resolvedFrom, resolvedTo, resolvedLoanType, resolvedLoanProductId, null)
            : loanAnalyticsService.statusTrendForStaff(principal, resolvedFrom, resolvedTo, resolvedLoanType, resolvedLoanProductId, null);
        List<LoanAnalyticsService.LoanProductPerformance> productPerformance = staffReviewView
            ? loanAnalyticsService.productPerformanceForStaff(principal, resolvedFrom, resolvedTo, resolvedLoanType, resolvedLoanProductId, null)
            : stationWideStaffView
                ? loanAnalyticsService.productPerformanceForStation(principal.getSaccoId(), principal.getStationId(), resolvedFrom, resolvedTo, resolvedLoanType, resolvedLoanProductId, null)
            : loanAnalyticsService.productPerformanceForStaff(principal, resolvedFrom, resolvedTo, resolvedLoanType, resolvedLoanProductId, null);
        BigDecimal totalInterestAccumulated = BigDecimal.ZERO;
        List<LoanReportService.ProductFinancialBreakdownRow> productFinancialRows = List.of();
        if (stationWideStaffView) {
            LoanReportService.AnalyticsExportReport interestReport =
                loanReportService.staffAnalyticsExportReport(principal, resolvedFrom, resolvedTo, resolvedLoanType, resolvedLoanProductId, selectedView);
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
        model.addAttribute("metricCards", metricCards(analytics));
        model.addAttribute("staffPortfolio", staffReviewView
            ? loanAnalyticsService.staffPortfolio(principal, resolvedFrom, resolvedTo, resolvedLoanType, resolvedLoanProductId, null)
            : stationWideStaffView
                ? loanAnalyticsService.stationPortfolio(principal.getSaccoId(), principal.getStationId(), resolvedFrom, resolvedTo, resolvedLoanType, resolvedLoanProductId, null)
            : loanAnalyticsService.staffPortfolio(principal, resolvedFrom, resolvedTo, resolvedLoanType, resolvedLoanProductId, null));
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
        List<LoanProductSetting> loanProducts = activeLoanProducts(principal.getSaccoId());
        model.addAttribute("selectedLoanProductLabel", selectedLoanProductLabel(selectedProduct, resolvedLoanType, loanProducts));
        model.addAttribute("trendSeriesJson", toJson(trendSeries));
        model.addAttribute("fromDate", StrictAnalyticsLocalDateEditor.format(resolvedFrom));
        model.addAttribute("toDate", StrictAnalyticsLocalDateEditor.format(resolvedTo));
        model.addAttribute("loanProductId", resolvedLoanProductId);
        model.addAttribute("loanType", resolvedLoanType);
        model.addAttribute("loanProducts", loanProducts);
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
                resolvedLoanType,
                resolvedLoanProductId,
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

    private List<LoanProductSetting> activeLoanProducts(String saccoId) {
        if (saccoId == null || saccoId.isBlank()) {
            return List.of();
        }
        return loanProductSettingRepository.findBySaccoIdAndActiveTrue(saccoId).stream()
            .filter(product -> product.getLoanType() != null)
            .filter(LoanProductSetting::isAvailableForApplications)
            .sorted(java.util.Comparator.comparingInt(LoanProductSetting::getResolvedDisplayOrder))
            .toList();
    }

    private LoanProductSetting selectedAnalyticsProduct(String saccoId, java.util.UUID loanProductId, LoanType fallbackLoanType) {
        if (loanProductId != null) {
            return loanProductSettingRepository.findByIdAndSaccoIdAndActiveTrue(loanProductId, saccoId)
                .filter(LoanProductSetting::isAvailableForApplications)
                .orElse(null);
        }
        if (fallbackLoanType == null) {
            return null;
        }
        return activeLoanProducts(saccoId).stream()
            .filter(product -> product.getLoanType() == fallbackLoanType)
            .findFirst()
            .orElse(null);
    }

    private String selectedLoanProductLabel(LoanProductSetting selectedProduct, LoanType loanType, List<LoanProductSetting> loanProducts) {
        if (selectedProduct != null) {
            return selectedProduct.getDisplayName();
        }
        if (loanType == null) {
            return "All Products";
        }
        return loanProducts.stream()
            .filter(product -> product.getLoanType() == loanType)
            .map(LoanProductSetting::getDisplayName)
            .filter(label -> label != null && !label.isBlank())
            .findFirst()
            .orElse("All Products");
    }

    private List<AnalyticsMetricCard> metricCards(LoanAnalyticsService.MemberLoanAnalytics analytics) {
        return List.of(
            new AnalyticsMetricCard("applied", "Applied Loans", analytics.appliedLoans(), "blue", "Applied"),
            new AnalyticsMetricCard("active", "Active Loans", analytics.activeLoans(), "emerald", "Active"),
            new AnalyticsMetricCard("disbursed", "Disbursed Loans", analytics.disbursedLoans(), "violet", "Disbursed"),
            new AnalyticsMetricCard("paid", "Paid Loans", analytics.paidLoans(), "green", "Paid"),
            new AnalyticsMetricCard("defaulted", "Defaulted Loans", analytics.defaultedLoans(), "orange", "Defaulted"),
            new AnalyticsMetricCard("rejected", "Rejected Loans", analytics.rejectedLoans(), "slate", "Rejected")
        );
    }

    private String moneyLabel(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount.setScale(2, java.math.RoundingMode.HALF_UP);
        DecimalFormat format = new DecimalFormat("#,##0.##", new DecimalFormatSymbols(Locale.US));
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

    public record AnalyticsMetricCard(String key, String label, long value, String tone, String sparkName) {
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

    }

    @InitBinder
    void bindAnalyticsDates(WebDataBinder binder) {
        binder.registerCustomEditor(LocalDate.class, new StrictAnalyticsLocalDateEditor());
    }

    private record ProductFinancialTotals(
        BigDecimal totalInterestUnpaid,
        BigDecimal totalLoanAmountPaid,
        BigDecimal totalLoanAmountUnpaid
    ) {}
}
