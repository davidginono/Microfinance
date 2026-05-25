package com.sacco.mvp.web;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.integration.memberportal.LoanPaymentLookupException;
import com.sacco.mvp.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.EligibilityService;
import com.sacco.mvp.service.FinancialDetailsService;
import com.sacco.mvp.service.FormSchemaService;
import com.sacco.mvp.service.AdminService;
import com.sacco.mvp.service.EmailOtpService;
import com.sacco.mvp.service.ExternalAccountStatusService;
import com.sacco.mvp.service.LoanAttachmentService;
import com.sacco.mvp.service.LoanWorkflowService;
import com.sacco.mvp.service.LoanAnalyticsService;
import com.sacco.mvp.service.LoanPresentationService;
import com.sacco.mvp.service.LoanQualificationPolicyService;
import com.sacco.mvp.service.LoanReportService;
import com.sacco.mvp.service.LoanProductWorkflowService;
import com.sacco.mvp.service.NotificationInboxService;
import com.sacco.mvp.service.ReversalRequestService;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import com.sacco.mvp.service.dto.FormModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/app")
public class AppController {
    private static final long REVERSAL_WINDOW_HOURS = 24L;
    private static final DateTimeFormatter REVERSAL_WINDOW_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);
    private static final String DISMISSED_ACTIVE_LOAN_CHARTS_KEY = "dismissedActiveLoanCharts";
    private final LoanWorkflowService loanWorkflowService;
    private final LoanAnalyticsService loanAnalyticsService;
    private final LoanQualificationPolicyService loanQualificationPolicyService;
    private final FormSchemaService formSchemaService;
    private final EligibilityService eligibilityService;
    private final FinancialDetailsService financialDetailsService;
    private final LoanPresentationService loanPresentationService;
    private final LoanReportService loanReportService;
    private final LoanProductWorkflowService loanProductWorkflowService;
    private final AdminService adminService;
    private final EmailOtpService emailOtpService;
    private final ExternalAccountStatusService externalAccountStatusService;
    private final NotificationInboxService notificationInboxService;
    private final ReversalRequestService reversalRequestService;
    private final LoanAttachmentService loanAttachmentService;
    private final MemberRepository memberRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanProductSettingRepository loanProductSettingRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final ManagerReviewRepository managerReviewRepository;
    private final BoardReviewRepository boardReviewRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final LoanPaymentTransactionRepository loanPaymentTransactionRepository;
    private final ForesightDirectoryService foresightDirectoryService;
    private final ObjectMapper objectMapper;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        List<GuarantorRequest> pendingGuarantees = activeGuarantorRequests(
            visibleGuarantorRequests(loanWorkflowService.myGuarantorRequests(principal.getMemberId())));
        List<LoanApplication> allApplications = loanWorkflowService.myApplications(principal.getMemberId());
        List<LoanApplication> currentApplications = currentApplications(allApplications);
        List<LoanApplication> archivedApplications = archivedApplications(allApplications);
        Map<LoanStatus, Long> statusCounts = currentApplications.stream()
            .filter(app -> isStatusChartIncluded(app.getStatus()))
            .collect(Collectors.groupingBy(LoanApplication::getStatus, LinkedHashMap::new, Collectors.counting()));
        List<LoanApplication> activeLoans = activeRepaymentLoans(allApplications);
        long openApplications = currentApplications.stream()
            .filter(this::isPendingApplication)
            .count();
        long rejectedLoans = archivedApplications.stream()
            .filter(app -> isRejectedStatus(app.getStatus()))
            .count();
        long pendingGuaranteeApprovals = pendingGuarantees.stream()
            .filter(req -> req.getStatus() == GuarantorRequestStatus.PENDING)
            .count();
        long loansAwaitingDecision = currentApplications.stream()
            .filter(this::isAwaitingDecisionStage)
            .count();
        LoanApplication currentWorkflowApplication = currentApplications.stream()
            .sorted(Comparator.comparing(LoanApplication::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(LoanApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .findFirst()
            .orElse(null);
        Set<UUID> dismissedActiveLoanChartIds = dismissedActiveLoanChartIds(principal.getMemberId());
        List<Map<String, Object>> activeLoanChartRows = buildActiveLoanChartRows(activeLoans, dismissedActiveLoanChartIds);
        Member member = memberRepository.findById(principal.getMemberId()).orElse(null);

        model.addAttribute("myApplications", currentApplications);
        model.addAttribute("managerReasons", loanPresentationService.latestManagerReasons(allApplications));
        model.addAttribute("pendingGuarantees", pendingGuarantees);
        model.addAttribute("totalApplications", openApplications);
        model.addAttribute("currentApplicationCount", currentApplications.size());
        model.addAttribute("activeLoanCount", activeLoans.size());
        model.addAttribute("activeLoanChartCount", activeLoanChartRows.size());
        model.addAttribute("rejectedLoanCount", rejectedLoans);
        model.addAttribute("pendingGuaranteeApprovals", pendingGuaranteeApprovals);
        model.addAttribute("loansAwaitingDecision", loansAwaitingDecision);
        model.addAttribute("statusChartRows", buildStatusChartRows(statusCounts));
        model.addAttribute("activeLoanChartRows", activeLoanChartRows);
        model.addAttribute("archivedApplicationCount", archivedApplications.size());
        model.addAttribute("dashboardExternalAccountStatus", externalAccountStatusService.loading("Loading live balances..."));
        model.addAttribute("currentWorkflowApplication", currentWorkflowApplication);
        model.addAttribute("currentWorkflowApplicationNumber",
            currentWorkflowApplication == null || currentWorkflowApplication.getApplicationNumber() == null
                ? "-"
                : String.valueOf(currentWorkflowApplication.getApplicationNumber()));
        model.addAttribute("currentWorkflowAmountLabel",
            currentWorkflowApplication == null
                ? "-"
                : loanPresentationService.formatMoneyDisplay(currentWorkflowApplication.getAmount()));
        model.addAttribute("currentWorkflowStatusLabel",
            currentWorkflowApplication == null
                ? ""
                : dashboardStatusLabel(currentWorkflowApplication.getStatus()));
        model.addAttribute("currentWorkflowUpdatedAtLabel",
            currentWorkflowApplication == null
                ? ""
                : formatDashboardWorkflowTimestamp(currentWorkflowApplication.getUpdatedAt()));
        model.addAttribute("currentWorkflowSteps",
            currentWorkflowApplication == null
                ? List.of()
                : buildDashboardWorkflowSteps(currentWorkflowApplication));
        addGuaranteeContext(pendingGuarantees, model);
        return "app/dashboard";
    }

    @GetMapping("/dashboard/external-account-status")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> dashboardExternalAccountStatus(@AuthenticationPrincipal AppUserPrincipal principal) {
        Member member = memberRepository.findById(principal.getMemberId()).orElse(null);
        return ResponseEntity.ok(externalAccountStatusPayload(externalAccountStatusService.resolve(member)));
    }

    @PostMapping("/dashboard/active-loans/{loanId}/seen")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    public String markExpiredActiveLoanChartSeen(@AuthenticationPrincipal AppUserPrincipal principal,
                                                 @PathVariable UUID loanId,
                                                 RedirectAttributes ra) {
        LoanApplication app = loanApplicationRepository.findByIdAndApplicantMemberId(loanId, principal.getMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Loan not found"));
        if (app.getStatus() != LoanStatus.FINAL_APPROVED && app.getStatus() != LoanStatus.DEFAULTED) {
            ra.addFlashAttribute("error", "Only active or defaulted disbursed loans can be removed from the repayment timeline.");
            return "redirect:/app/dashboard";
        }
        if (!hasRepaymentTimeframeEnded(app)) {
            ra.addFlashAttribute("error", "This repayment timeline is still active.");
            return "redirect:/app/dashboard";
        }
        dismissActiveLoanChart(principal.getMemberId(), loanId);
        return "redirect:/app/dashboard";
    }

    @GetMapping("/loan-products")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    public String products(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        List<LoanProductSetting> products = loanWorkflowService.listProducts(principal.getSaccoId());
        model.addAttribute("products", products);
        Optional<String> applicantPolicyReason = loanQualificationPolicyService.applicantFailureReason(
            principal.getSaccoId(),
            principal.getMemberId()
        );
        model.addAttribute("applicantPolicyEligible", applicantPolicyReason.isEmpty());
        model.addAttribute("applicantPolicyReason", applicantPolicyReason.orElse(""));
        loanWorkflowService.findApplicationInProgress(principal.getMemberId()).ifPresent(app -> {
            model.addAttribute("applicationLockApp", app);
            model.addAttribute("applicationLockStatusLabel", dashboardStatusLabel(app.getStatus()));
        });
        loanWorkflowService.findActiveDisbursedLoan(principal.getMemberId()).ifPresent(app ->
            model.addAttribute("activeDisbursedLoanApp", app)
        );
        return "app/loan-products";
    }

    @GetMapping("/loan-applications")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    public String listMyApps(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        List<LoanApplication> allApplications = loanWorkflowService.myApplications(principal.getMemberId());
        List<LoanApplication> apps = currentApplications(allApplications);
        model.addAttribute("apps", apps);
        model.addAttribute("managerReasons", loanPresentationService.latestManagerReasons(allApplications));
        model.addAttribute("archiveCount", archivedApplications(allApplications).size());
        return "app/loan-applications";
    }

    @GetMapping("/archives")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    public String archives(@AuthenticationPrincipal AppUserPrincipal principal,
                           @RequestParam(required = false, defaultValue = "loans") String section,
                           @RequestParam(required = false) String loanArchiveQuery,
                           @RequestParam(required = false, defaultValue = "ALL") String loanArchiveFilter,
                           @RequestParam(required = false) String guarantorArchiveQuery,
                           @RequestParam(required = false, defaultValue = "ALL") String guarantorArchiveFilter,
                           Model model) {
        List<LoanApplication> allApplications = loanWorkflowService.myApplications(principal.getMemberId());
        List<LoanApplication> allArchives = archivedApplications(allApplications);
        List<GuarantorRequest> allGuarantorArchives = archivedGuarantorRequests(
            visibleGuarantorRequests(loanWorkflowService.myGuarantorRequests(principal.getMemberId())));
        List<LoanApplication> archives = filterLoanArchives(allArchives, loanArchiveQuery, loanArchiveFilter);
        List<GuarantorRequest> guarantorArchives = filterGuarantorArchives(
            allGuarantorArchives, guarantorArchiveQuery, guarantorArchiveFilter);
        model.addAttribute("archives", archives);
        model.addAttribute("guarantorArchives", guarantorArchives);
        model.addAttribute("archiveSection", normalizeArchiveSection(section));
        model.addAttribute("loanArchiveQuery", safeArchiveQuery(loanArchiveQuery));
        model.addAttribute("loanArchiveFilter", safeArchiveFilter(loanArchiveFilter));
        model.addAttribute("guarantorArchiveQuery", safeArchiveQuery(guarantorArchiveQuery));
        model.addAttribute("guarantorArchiveFilter", safeArchiveFilter(guarantorArchiveFilter));
        model.addAttribute("totalArchivedRecordCount", allArchives.size() + allGuarantorArchives.size());
        model.addAttribute("totalLoanArchiveCount", allArchives.size());
        model.addAttribute("totalGuarantorArchiveCount", allGuarantorArchives.size());
        model.addAttribute("disbursedArchiveCount", allArchives.stream()
            .filter(app -> app.getStatus() == LoanStatus.FINAL_APPROVED)
            .count());
        model.addAttribute("managerReasons", loanPresentationService.latestManagerReasons(allApplications));
        addGuaranteeActionContext(allGuarantorArchives, model);
        return "app/archives";
    }

    @GetMapping("/reports")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    public String reports(@AuthenticationPrincipal AppUserPrincipal principal,
                          @RequestParam(required = false) LocalDate fromDate,
                          @RequestParam(required = false) LocalDate toDate,
                          @RequestParam(required = false) LoanType loanType,
                          Model model) {
        LocalDate resolvedTo = toDate == null ? LocalDate.now() : toDate;
        LocalDate resolvedFrom = fromDate == null ? resolvedTo.minusYears(1) : fromDate;
        if (resolvedFrom.isAfter(resolvedTo)) {
            LocalDate swap = resolvedFrom;
            resolvedFrom = resolvedTo;
            resolvedTo = swap;
        }
        ReportsDateRange previousRange = previousRange(resolvedFrom, resolvedTo);
        LoanAnalyticsService.MemberLoanAnalytics analytics =
            loanAnalyticsService.forMember(principal.getMemberId(), resolvedFrom, resolvedTo, loanType, null);
        LoanAnalyticsService.MemberLoanAnalytics previousAnalytics =
            loanAnalyticsService.forMember(principal.getMemberId(), previousRange.fromDate(), previousRange.toDate(), loanType, null);
        List<LoanAnalyticsService.MetricTrendSeries> trendSeries =
            loanAnalyticsService.statusTrendForMember(principal.getMemberId(), resolvedFrom, resolvedTo, loanType, null);
        Map<String, LoanAnalyticsService.MetricDelta> metricDeltas = loanAnalyticsService.metricDeltas(analytics, previousAnalytics)
            .stream()
            .collect(Collectors.toMap(LoanAnalyticsService.MetricDelta::key, java.util.function.Function.identity()));
        LoanQualificationPolicyService.EligibilitySummary eligibilitySummary =
            loanQualificationPolicyService.eligibilitySummary(principal.getSaccoId(), principal.getMemberId());
        model.addAttribute("analytics", analytics);
        model.addAttribute("metricCards", memberMetricCards(analytics, metricDeltas));
        model.addAttribute("metricPeriodLabel", previousPeriodLabel(resolvedFrom, resolvedTo));
        model.addAttribute("fromDate", resolvedFrom);
        model.addAttribute("toDate", resolvedTo);
        model.addAttribute("loanType", loanType);
        model.addAttribute("loanTypes", LoanType.values());
        model.addAttribute("trendSeriesJson", toJson(trendSeries));
        model.addAttribute("eligibilitySummary", eligibilitySummary);
        model.addAttribute("canApply", eligibilitySummary.canApply());
        model.addAttribute("canGuarantee", eligibilitySummary.canGuarantee());
        model.addAttribute("riskNote", eligibilitySummary.reason());
        return "app/reports";
    }

    private ReportsDateRange previousRange(LocalDate fromDate, LocalDate toDate) {
        long days = Math.max(java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate), 0);
        LocalDate previousTo = fromDate.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days);
        return new ReportsDateRange(previousFrom, previousTo);
    }

    private String previousPeriodLabel(LocalDate fromDate, LocalDate toDate) {
        long days = Math.max(java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1, 1);
        if (days >= 60) {
            long months = Math.max(1, Math.round(days / 30.4375d));
            return months == 1 ? "previous month" : "previous " + months + " months";
        }
        return days == 1 ? "previous day" : "previous " + days + " days";
    }

    private List<MemberMetricCard> memberMetricCards(LoanAnalyticsService.MemberLoanAnalytics analytics,
                                                     Map<String, LoanAnalyticsService.MetricDelta> deltas) {
        return List.of(
            memberMetricCard("applied", "Applied Loans", analytics.appliedLoans(), "blue", "Applied", deltas),
            memberMetricCard("active", "Active Loans", analytics.activeLoans(), "emerald", "Applied", deltas),
            memberMetricCard("disbursed", "Disbursed Loans", analytics.disbursedLoans(), "violet", "Disbursed", deltas),
            memberMetricCard("paid", "Paid Loans", analytics.paidLoans(), "green", "Paid", deltas),
            memberMetricCard("defaulted", "Defaulted Loans", analytics.defaultedLoans(), "orange", "Defaulted", deltas),
            memberMetricCard("forfeited", "Forfeited Loan Applications", analytics.forfeitedLoans(), "rose", "Forfeited", deltas),
            memberMetricCard("rejected", "Rejected Loans", analytics.rejectedLoans(), "slate", "Rejected", deltas)
        );
    }

    private MemberMetricCard memberMetricCard(String key,
                                              String label,
                                              long value,
                                              String tone,
                                              String sparkName,
                                              Map<String, LoanAnalyticsService.MetricDelta> deltas) {
        BigDecimal percent = deltas.getOrDefault(key, new LoanAnalyticsService.MetricDelta(key, BigDecimal.ZERO, false)).percent();
        boolean positive = percent.compareTo(BigDecimal.ZERO) >= 0;
        return new MemberMetricCard(key, label, value, tone, sparkName, formatAnalyticsPercent(percent), positive);
    }

    private String formatAnalyticsPercent(BigDecimal value) {
        String sign = value.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        return sign + value.setScale(2, RoundingMode.HALF_UP) + "%";
    }

    public record MemberMetricCard(String key, String label, long value, String tone, String sparkName, String percentLabel, boolean positive) {
        public String getKey() { return key; }
        public String getLabel() { return label; }
        public long getValue() { return value; }
        public String getTone() { return tone; }
        public String getSparkName() { return sparkName; }
        public String getPercentLabel() { return percentLabel; }
        public boolean isPositive() { return positive; }
    }

    private record ReportsDateRange(LocalDate fromDate, LocalDate toDate) {}

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<Map<String, Object>> buildActiveLoanChartRows(List<LoanApplication> activeLoans,
                                                               Set<UUID> dismissedChartIds) {
        LocalDate today = LocalDate.now();
        return activeLoans.stream()
            .sorted(Comparator.comparing(LoanApplication::getFinalDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .filter(app -> !hasRepaymentTimeframeEnded(app) || !dismissedChartIds.contains(app.getId()))
            .map(app -> {
                LoanPresentationService.LoanPaymentSummaryView paymentSummary =
                    loanPresentationService.parseLoanPaymentSummaryView(app.getLoanPaymentSummaryJson());
                Map<String, Object> row = new LinkedHashMap<>();
                long daysLeft = app.getFinalDueDate() == null ? 0 : Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(today, app.getFinalDueDate()));
                long totalDays = 0L;
                if (app.getDisbursementDate() != null && app.getFinalDueDate() != null) {
                    totalDays = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(app.getDisbursementDate(), app.getFinalDueDate()));
                } else if (app.getFirstRepaymentDate() != null && app.getFinalDueDate() != null) {
                    totalDays = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(app.getFirstRepaymentDate(), app.getFinalDueDate()));
                }
                long elapsedDays = Math.max(0, totalDays - daysLeft);
                long remainingPercent = totalDays <= 0
                    ? (daysLeft > 0 ? 100 : 0)
                    : Math.max(0, Math.min(100, Math.round((daysLeft * 100.0d) / totalDays)));

                row.put("fullId", app.getId());
                row.put("loanId", app.getId().toString().substring(0, 8));
                row.put("amountLabel", loanPresentationService.formatMoneyDisplay(app.getAmount()));
                row.put("startDate", resolveRepaymentTimerStartDate(app));
                row.put("daysLeft", daysLeft);
                row.put("elapsedDays", elapsedDays);
                row.put("totalDays", totalDays);
                row.put("remainingPercent", remainingPercent);
                row.put("finalDueDate", app.getFinalDueDate());
                row.put("countdown", loanPresentationService.countdownLabel(app.getFinalDueDate()));
                row.put("canDismiss", hasRepaymentTimeframeEnded(app));
                row.put("repaymentStateLabel", repaymentStateLabel(app, paymentSummary, today));
                row.put("repaymentStateClasses", repaymentStateClasses(app, paymentSummary, today));
                row.put("paymentSummaryAvailable", paymentSummary.available());
                row.put("loanDescription", paymentSummary.loanDescription());
                row.put("lastPaymentDate", paymentSummary.lastPaymentDateLabel());
                row.put("totalOutstanding", paymentSummary.totalOutstandingLabel());
                row.put("outstandingPrincipal", paymentSummary.outstandingPrincipalLabel());
                row.put("outstandingInterest", paymentSummary.outstandingInterestLabel());
                row.put("totalPrincipalPaid", paymentSummary.totalPrincipalPaidLabel());
                row.put("totalInterestPaid", paymentSummary.totalInterestPaidLabel());
                return row;
            })
            .toList();
    }

    private LocalDate resolveRepaymentTimerStartDate(LoanApplication app) {
        if (app == null) {
            return null;
        }
        if (app.getDisbursementDate() != null) {
            return app.getDisbursementDate();
        }
        return app.getFirstRepaymentDate();
    }

    private List<Map<String, Object>> buildStatusChartRows(Map<LoanStatus, Long> statusCounts) {
        List<LoanStatus> statusOrder = List.of(
            LoanStatus.DRAFT,
            LoanStatus.AWAITING_GUARANTORS,
            LoanStatus.ALL_GUARANTORS_APPROVED,
            LoanStatus.READY_FOR_MANAGER,
            LoanStatus.AWAITING_BOARD,
            LoanStatus.FINAL_APPROVED
        );
        long maxCount = statusOrder.stream()
            .map(status -> statusCounts.getOrDefault(status, 0L))
            .max(Long::compareTo)
            .orElse(1L);
        long safeMax = Math.max(1L, maxCount);

        return statusOrder.stream()
            .map(status -> {
                long count = statusCounts.getOrDefault(status, 0L);
                long heightPercent = count <= 0 ? 6L : Math.max(18L, Math.round((count * 100.0d) / safeMax));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", status.name());
                row.put("label", dashboardStatusLabel(status));
                row.put("count", count);
                row.put("heightPercent", heightPercent);
                row.put("color", dashboardStatusColor(status));
                return row;
            })
            .toList();
    }

    private List<Map<String, Object>> buildDashboardWorkflowSteps(LoanApplication app) {
        List<String> labels = List.of(
            "Applicant",
            "Guarantors",
            "Manager",
            "Further Review",
            "Ready for Release",
            "Disbursement"
        );
        List<String> iconKeys = List.of(
            "applicant",
            "guarantors",
            "manager",
            "review",
            "disbursement",
            "bank"
        );
        int currentIndex = dashboardWorkflowStepIndex(app == null ? null : app.getStatus());
        boolean workflowFinished = isDashboardWorkflowFinished(app);
        List<String> stageDates = List.of(
            dashboardApplicantStageDate(app),
            dashboardGuarantorStageDate(app),
            dashboardManagerStageDate(app),
            dashboardFurtherReviewStageDate(app),
            dashboardReadyForDisbursementDate(app),
            dashboardDisbursedDate(app)
        );
        List<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            String stateKey;
            if (i < currentIndex || (workflowFinished && i == currentIndex)) {
                stateKey = "completed";
            } else if (i == currentIndex) {
                stateKey = "current";
            } else {
                stateKey = "pending";
            }
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("stepNumber", i + 1);
            step.put("label", labels.get(i));
            step.put("stateKey", stateKey);
            step.put("stateLabel", dashboardWorkflowStateLabel(i, stateKey, app));
            step.put("metaLabel", dashboardWorkflowMetaLabel(stateKey));
            step.put("dateLabel", "pending".equals(stateKey) ? "" : stageDates.get(i));
            step.put("nodeClasses", workflowNodeClasses(stateKey));
            step.put("textClasses", workflowTextClasses(stateKey));
            step.put("numberClasses", workflowNumberClasses(stateKey));
            step.put("iconClasses", workflowIconClasses(stateKey));
            step.put("connectorClasses", workflowConnectorClasses(i, currentIndex));
            step.put("iconKey", iconKeys.get(i));
            steps.add(step);
        }
        return steps;
    }

    private int dashboardWorkflowStepIndex(LoanStatus status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case DRAFT -> 0;
            case SUBMITTED, AWAITING_GUARANTORS, ALL_GUARANTORS_APPROVED -> 1;
            case READY_FOR_MANAGER, MANAGER_ACCEPTED, MANAGER_REJECTED -> 2;
            case AWAITING_LOAN_OFFICER, LOAN_OFFICER_APPROVED, LOAN_OFFICER_REJECTED,
                AWAITING_BOARD, BOARD_APPROVED, BOARD_REJECTED,
                AWAITING_ACCOUNTANT, ACCOUNTANT_APPROVED, ACCOUNTANT_REJECTED -> 3;
            case READY_FOR_DISBURSEMENT, FORFEITED, FINAL_REJECTED -> 4;
            case FINAL_APPROVED, DEFAULTED, PAID -> 5;
        };
    }

    private String dashboardWorkflowStateLabel(int index, String stateKey, LoanApplication app) {
        if ("completed".equals(stateKey)) {
            return switch (index) {
                case 0 -> "Submitted";
                case 1 -> "Approved";
                case 2, 3 -> "Approved";
                case 4 -> "Ready";
                case 5 -> "Disbursed";
                default -> "Completed";
            };
        }
        if ("current".equals(stateKey)) {
            return switch (index) {
                case 0 -> "Draft Saved";
                case 1 -> "Awaiting Approval";
                case 2, 3 -> "In Review";
                case 4 -> "Pending Release";
                case 5 -> "Disbursed";
                default -> "In Review";
            };
        }
        return "Pending";
    }

    private String dashboardWorkflowMetaLabel(String stateKey) {
        return "current".equals(stateKey) ? "Current Stage" : "";
    }

    private String workflowNodeClasses(String stateKey) {
        return switch (stateKey) {
            case "completed" -> "member-dashboard-flow-node--completed";
            case "current" -> "member-dashboard-flow-node--current";
            default -> "member-dashboard-flow-node--pending";
        };
    }

    private String workflowTextClasses(String stateKey) {
        return switch (stateKey) {
            case "completed" -> "member-dashboard-flow-text--completed";
            case "current" -> "member-dashboard-flow-text--current";
            case "pending" -> "member-dashboard-flow-text--pending";
            default -> "member-dashboard-flow-text--not-started";
        };
    }

    private String workflowNumberClasses(String stateKey) {
        return switch (stateKey) {
            case "completed" -> "member-dashboard-flow-number--completed";
            case "current" -> "member-dashboard-flow-number--current";
            default -> "member-dashboard-flow-number--pending";
        };
    }

    private String workflowIconClasses(String stateKey) {
        return switch (stateKey) {
            case "completed" -> "member-dashboard-flow-icon--completed";
            case "current" -> "member-dashboard-flow-icon--current";
            default -> "member-dashboard-flow-icon--pending";
        };
    }

    private String workflowConnectorClasses(int index, int currentIndex) {
        if (index < currentIndex) {
            return "member-dashboard-flow-connector--completed";
        }
        if (index == currentIndex) {
            return "member-dashboard-flow-connector--current";
        }
        return "member-dashboard-flow-connector--pending";
    }

    private boolean isDashboardWorkflowFinished(LoanApplication app) {
        if (app == null) {
            return false;
        }
        return app.getStatus() == LoanStatus.FINAL_APPROVED
            || app.getStatus() == LoanStatus.DEFAULTED
            || app.getStatus() == LoanStatus.PAID;
    }

    private String dashboardApplicantStageDate(LoanApplication app) {
        if (app == null) {
            return "";
        }
        if (app.getSubmittedAt() != null) {
            return formatDashboardWorkflowTimestamp(app.getSubmittedAt());
        }
        return formatDashboardWorkflowTimestamp(app.getCreatedAt());
    }

    private String formatDashboardWorkflowTimestamp(OffsetDateTime timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH));
    }

    private String formatDashboardWorkflowDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
    }

    private String dashboardGuarantorStageDate(LoanApplication app) {
        if (app == null) {
            return "";
        }
        if (app.getRequiredGuarantors() == null || app.getRequiredGuarantors() <= 0) {
            return formatDashboardWorkflowTimestamp(app.getSubmittedAt());
        }
        return guarantorRequestRepository.findByLoanApplicationId(app.getId()).stream()
            .map(GuarantorRequest::getDecidedAt)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .map(this::formatDashboardWorkflowTimestamp)
            .orElseGet(() -> formatDashboardWorkflowTimestamp(app.getSubmittedAt()));
    }

    private String dashboardManagerStageDate(LoanApplication app) {
        if (app == null) {
            return "";
        }
        return managerReviewRepository.findByLoanApplicationIdAndReviewStageOrderByCreatedAtAsc(app.getId(), ApprovalWorkflowStage.MANAGER).stream()
            .map(ManagerReview::getCreatedAt)
            .filter(Objects::nonNull)
            .findFirst()
            .map(this::formatDashboardWorkflowTimestamp)
            .orElse("");
    }

    private String dashboardFurtherReviewStageDate(LoanApplication app) {
        if (app == null) {
            return "";
        }
        Optional<String> loanOfficerReview = managerReviewRepository
            .findByLoanApplicationIdAndReviewStageOrderByCreatedAtAsc(app.getId(), ApprovalWorkflowStage.LOAN_OFFICER)
            .stream()
            .map(ManagerReview::getCreatedAt)
            .filter(Objects::nonNull)
            .findFirst()
            .map(this::formatDashboardWorkflowTimestamp);
        if (loanOfficerReview.isPresent()) {
            return loanOfficerReview.get();
        }
        Optional<String> boardReview = boardReviewRepository.findByLoanApplicationIdAndReviewStage(app.getId(), ApprovalWorkflowStage.BOARD).stream()
            .map(review -> review.getDecidedAt() == null ? review.getCreatedAt() : review.getDecidedAt())
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder())
            .map(this::formatDashboardWorkflowTimestamp);
        if (boardReview.isPresent()) {
            return boardReview.get();
        }
        return managerReviewRepository.findByLoanApplicationIdAndReviewStageOrderByCreatedAtAsc(app.getId(), ApprovalWorkflowStage.ACCOUNTANT).stream()
            .map(ManagerReview::getCreatedAt)
            .filter(Objects::nonNull)
            .findFirst()
            .map(this::formatDashboardWorkflowTimestamp)
            .orElse("");
    }

    private String dashboardReadyForDisbursementDate(LoanApplication app) {
        if (app == null) {
            return "";
        }
        if (app.getStatus() == LoanStatus.READY_FOR_DISBURSEMENT
            || app.getStatus() == LoanStatus.FINAL_APPROVED
            || app.getStatus() == LoanStatus.DEFAULTED
            || app.getStatus() == LoanStatus.PAID) {
            return formatDashboardWorkflowTimestamp(app.getUpdatedAt());
        }
        return "";
    }

    private String dashboardDisbursedDate(LoanApplication app) {
        if (app == null) {
            return "";
        }
        if (app.getDisbursementDate() != null) {
            return formatDashboardWorkflowDate(app.getDisbursementDate());
        }
        if (app.getStatus() == LoanStatus.FINAL_APPROVED || app.getStatus() == LoanStatus.DEFAULTED || app.getStatus() == LoanStatus.PAID) {
            return formatDashboardWorkflowTimestamp(app.getUpdatedAt());
        }
        return "";
    }

    @GetMapping("/loan-applications/new")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    public String newApp(@AuthenticationPrincipal AppUserPrincipal principal,
                         @RequestParam LoanType loanType,
                         @RequestParam(required = false) UUID topUpLoanId,
                         RedirectAttributes ra,
                         Model model) {
        Optional<LoanApplication> blockingApplication = loanWorkflowService.findApplicationInProgress(principal.getMemberId());
        if (blockingApplication.isPresent()) {
            LoanApplication app = blockingApplication.get();
            ra.addFlashAttribute(
                "error",
                "You already have loan application " + app.getId().toString().substring(0, 8)
                    + " on review (" + dashboardStatusLabel(app.getStatus())
                    + "). Continue it until it is disbursed before applying again."
            );
            return "redirect:/app/loan-applications/" + app.getId();
        }
        LoanProductSetting product = formSchemaService.getSchema(principal.getSaccoId(), loanType);
        try {
            loanWorkflowService.assertCanApplyForProduct(principal.getSaccoId(), principal.getMemberId(), product);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/app/loan-products";
        }
        Map<String, String> formValues = new LinkedHashMap<>();
        if (topUpLoanId != null) {
            try {
                loanWorkflowService.requireAllowedTopUpSourceLoan(principal.getSaccoId(), principal.getMemberId(), topUpLoanId);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                ra.addFlashAttribute("error", ex.getMessage());
                return "redirect:/app/loan-applications";
            }
            formValues.put("topUpLoanId", topUpLoanId.toString());
        }
        return prepareLoanNewModel(principal, loanType, formValues, Collections.emptyList(), Collections.emptyMap(), null, model);
    }

    @GetMapping("/loan-applications/{id}/edit")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#id, principal)")
    public String editDraft(@PathVariable UUID id,
                            @AuthenticationPrincipal AppUserPrincipal principal,
                            Model model,
                            RedirectAttributes ra) {
        LoanApplication app = loanWorkflowService.getMine(id, principal.getMemberId());
        if (app.getStatus() != LoanStatus.DRAFT) {
            ra.addFlashAttribute("error", "Only draft applications can be edited.");
            return "redirect:/app/loan-applications/" + id;
        }

        Map<String, String> formValues = new LinkedHashMap<>();
        formValues.put("applicationId", app.getId().toString());
        formValues.put("amount", app.getAmount() == null ? "" : app.getAmount().toPlainString());
        formValues.put("tenorMonths", app.getTenorMonths() == null ? "" : String.valueOf(app.getTenorMonths()));
        formValues.put("financialSnapshotJson", app.getFinancialSnapshot());
        if (app.getTopUpSourceLoanId() != null) {
            formValues.put("topUpLoanId", app.getTopUpSourceLoanId().toString());
        }
        formValues.putAll(parseJsonAsStringMap(app.getFormData()));

        return prepareLoanNewModel(
            principal,
            app.getLoanType(),
            formValues,
            parseUuidList(app.getSelectedGuarantors()),
            parseSelectedGuarantorCommitments(app.getSelectedGuarantors()),
            app.getRequiredGuarantors(),
            model
        );
    }

    @PostMapping("/loan-applications")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    public String createDraft(@AuthenticationPrincipal AppUserPrincipal principal,
                              @RequestParam LoanType loanType,
                              @RequestParam BigDecimal amount,
                              @RequestParam Integer tenorMonths,
                              @RequestParam(required = false) UUID applicationId,
                              @RequestParam(required = false) String action,
                              @RequestParam(required = false) String applicantSignatureOtpCode,
                              @RequestParam(required = false) List<UUID> guarantorIds,
                              @RequestParam(required = false) String financialSnapshotJson,
                              @RequestParam(required = false) UUID topUpLoanId,
                              @RequestParam Map<String, String> params,
                              @RequestParam(required = false, name = "attachments") List<MultipartFile> attachments,
                              RedirectAttributes ra,
                              Model model) {
        Map<String, String> submittedValues = new LinkedHashMap<>(params);
        Map<String, String> formPayload = new LinkedHashMap<>(params);
        formPayload.remove("loanType");
        formPayload.remove("amount");
        formPayload.remove("tenorMonths");
        formPayload.remove("applicationId");
        formPayload.remove("action");
        formPayload.remove("termsAccepted");
        formPayload.remove("guarantorIds");
        formPayload.remove("financialSnapshotJson");
        formPayload.remove("topUpLoanId");
        formPayload.remove("_csrf");
        formPayload.entrySet().removeIf(entry -> entry.getKey().startsWith("guarantorCommitmentAmount_"));
        Map<UUID, BigDecimal> guarantorCommitments = parseGuarantorCommitments(params);

        try {
            if ("SEND_TO_GUARANTORS".equalsIgnoreCase(action)) {
                if (applicationId == null) {
                    throw new IllegalStateException("Save the application as a draft before submitting it.");
                }
                boolean submitsDirectlyToStaff = requiresApplicantOtpBeforeImmediateSubmission(principal.getSaccoId(), loanType);
                if (submitsDirectlyToStaff) {
                    requireTermsAccepted(params.get("termsAccepted"));
                }
                UUID applicantSignatureOtpTokenId = null;
                if (submitsDirectlyToStaff) {
                    applicantSignatureOtpTokenId = validateApplicantSignatureOtp(principal.getMemberId(), applicantSignatureOtpCode);
                }
                LoanApplication submitted = loanWorkflowService.saveAndSubmit(
                    principal.getSaccoId(),
                    principal.getMemberId(),
                    loanType,
                    amount,
                    tenorMonths,
                    formPayload,
                    applicationId,
                    guarantorIds,
                    guarantorCommitments,
                    financialSnapshotJson,
                    topUpLoanId,
                    attachments
                );
                if (submitted.getStatus() == LoanStatus.AWAITING_GUARANTORS) {
                    ra.addFlashAttribute("message", "Application sent to guarantors successfully. Current status: " + submitted.getStatus());
                } else if (submitted.getRequiredGuarantors() <= 0) {
                    Member signingMember = requireMemberWithSavedSignature(principal.getMemberId());
                    loanWorkflowService.recordApplicantSignature(
                        submitted.getId(),
                        principal.getMemberId(),
                        signingMember.getSignatureText(),
                        OffsetDateTime.now()
                    );
                    if (applicantSignatureOtpTokenId != null) {
                        emailOtpService.consumeOtpById(applicantSignatureOtpTokenId);
                    }
                    ra.addFlashAttribute("message", "Application submitted and sent for manager review. Current status: " + submitted.getStatus());
                } else {
                    ra.addFlashAttribute("message", "Application updated successfully. Current status: " + submitted.getStatus());
                }
                return "redirect:/app/loan-applications";
            }

            LoanApplication app = loanWorkflowService.saveDraft(principal.getSaccoId(), principal.getMemberId(), loanType,
                amount, tenorMonths, formPayload, applicationId, guarantorIds, guarantorCommitments,
                financialSnapshotJson, topUpLoanId, attachments);

            ra.addFlashAttribute("message", "Draft saved successfully. You can continue editing.");
            return "redirect:/app/loan-applications/" + app.getId() + "/edit";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            String errorMessage = humanizeLoanFormError(ex.getMessage(), principal, loanType);
            model.addAttribute("error", errorMessage);
            if (ex instanceof LoanWorkflowService.GuarantorValidationException guarantorEx
                && guarantorEx.getGuarantorId() != null) {
                model.addAttribute("guarantorValidationErrorId", guarantorEx.getGuarantorId().toString());
                model.addAttribute("guarantorValidationErrorMessage", errorMessage);
            }
            Integer requiredGuarantorsOverride = resolveDraftRequiredGuarantors(principal.getMemberId(), applicationId);
            return prepareLoanNewModel(
                principal,
                loanType,
                submittedValues,
                guarantorIds,
                guarantorCommitments,
                requiredGuarantorsOverride,
                model
            );
        }
    }

    @GetMapping("/loan-applications/{id}")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#id, principal)")
    public String viewMine(@PathVariable UUID id, Model model) {
        LoanApplication app = loanApplicationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
        Member applicant = memberRepository.findById(app.getApplicantMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Applicant member not found"));
        List<GuarantorRequest> guarantorRequests = guarantorRequestRepository.findByLoanApplicationId(id);
        Set<UUID> guarantorIds = new HashSet<>();
        for (GuarantorRequest req : guarantorRequests) {
            guarantorIds.add(req.getGuarantorMemberId());
        }
        Map<UUID, String> guarantorNames = new HashMap<>();
        Map<UUID, Member> guarantorMembersById = new HashMap<>();
        if (!guarantorIds.isEmpty()) {
            for (Member member : memberRepository.findAllById(guarantorIds)) {
                guarantorNames.put(member.getId(), member.getFullName());
                guarantorMembersById.put(member.getId(), member);
            }
        }
        model.addAttribute("app", app);
        addMemberLoanViewDisplayAttributes(model, app);
        model.addAttribute("applicantExternalAccountStatus", externalAccountStatusService.resolve(applicant));
        model.addAttribute("topUpSourceLoan",
            app.getTopUpSourceLoanId() == null ? null
                : loanApplicationRepository.findByIdAndApplicantMemberId(app.getTopUpSourceLoanId(), app.getApplicantMemberId()).orElse(null));
        model.addAttribute("canRequestTopUp", loanWorkflowService.canRequestTopUp(app));
        model.addAttribute("canForfeitApplication", loanWorkflowService.canForfeitReviewApplication(app));
        model.addAttribute("forfeitWaitDays",
            loanQualificationPolicyService.applicantForfeitedWaitDays(app.getSaccoId(), app.getApplicantMemberId()).orElse(null));
        model.addAttribute("loanFinalSubmitLabel", finalSubmitLabel(app));
        model.addAttribute("formFields", loanPresentationService.parseFormFields(app.getFormData()));
        model.addAttribute("guarantorRequests", guarantorRequests);
        model.addAttribute("guarantorNames", guarantorNames);
        model.addAttribute("guarantorMembersById", guarantorMembersById);
        Map<UUID, ReversalRequest> pendingGuarantorUndoRequests = new HashMap<>();
        for (GuarantorRequest guarantorRequest : guarantorRequests) {
            ReversalRequest pendingUndo = reversalRequestService.pendingGuarantorUndo(guarantorRequest.getId());
            if (pendingUndo != null) {
                pendingGuarantorUndoRequests.put(guarantorRequest.getId(), pendingUndo);
            }
        }
        model.addAttribute("pendingGuarantorUndoRequests", pendingGuarantorUndoRequests);
        model.addAttribute("pendingManagerStageWithdrawal", reversalRequestService.pendingManagerStageWithdrawal(id));
        OffsetDateTime memberReversalReferenceAt = app.getStatus() == LoanStatus.READY_FOR_MANAGER
            ? app.getUpdatedAt()
            : app.getSubmittedAt();
        model.addAttribute("memberReversalWindowOpen", isWithinReversalWindow(memberReversalReferenceAt));
        model.addAttribute("draftSelectedGuarantors",
            memberRepository.findAllById(parseUuidList(app.getSelectedGuarantors())).stream()
                .map(member -> member.getMemberNo() + " - " + member.getFullName())
                .toList());
        model.addAttribute("managerReason",
            app.getStatus() == LoanStatus.MANAGER_REJECTED ? loanPresentationService.latestManagerReason(id) : "");
        model.addAttribute("financialFields", loanPresentationService.parseFinancialFields(app.getFinancialSnapshot()));
        model.addAttribute("attachments", loanPresentationService.parseApplicationAttachments(app.getAttachmentsJson()));
        model.addAttribute("disbursementProofAttachments", loanPresentationService.parseDisbursementProofAttachments(app.getAttachmentsJson()));
        model.addAttribute("feeInsuranceReceiptAttachments", loanPresentationService.parseFeeInsuranceReceiptAttachments(app.getAttachmentsJson()));
        model.addAttribute("loanFeePaymentSettings", saccoSettingsRepository.findById(app.getSaccoId()).orElse(null));
        model.addAttribute("loanFeeReceiptUploaded", loanWorkflowService.hasLoanFeeReceipt(app));
        model.addAttribute("loanFeeReceiptMissingBlocksDisbursement", loanFeeReceiptMissingBlocksDisbursement(app));
        model.addAttribute("canUploadLoanFeeReceipt", canUploadLoanFeeReceipt(app));
        model.addAttribute("repaymentSummary",
            loanPresentationService.parseRepaymentSummary(app.getRepaymentScheduleJson(), app.getPaidAt()));
        model.addAttribute("repaymentRows", loanPresentationService.parseRepaymentRows(
            app.getRepaymentScheduleJson(),
            loanPaymentTransactionRepository.findByLoanApplicationIdOrderByReceiptDateAsc(app.getId()),
            loanPresentationService.parseLoanPaymentSummaryView(app.getLoanPaymentSummaryJson())));
        model.addAttribute("repaymentCountdown", loanPresentationService.countdownLabel(app.getFinalDueDate()));
        model.addAttribute("repaymentDaysLeft",
            app.getFinalDueDate() == null ? null : java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), app.getFinalDueDate()));
        model.addAttribute("repaymentWeeksLeft",
            app.getFinalDueDate() == null ? null : Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), app.getFinalDueDate()) / 7));
        model.addAttribute("repaymentMonthsLeft",
            app.getFinalDueDate() == null ? null : Math.max(0, java.time.temporal.ChronoUnit.MONTHS.between(java.time.LocalDate.now().withDayOfMonth(1), app.getFinalDueDate().withDayOfMonth(1))));
        model.addAttribute("savedSignatureText", resolveSavedSignatureText(app.getApplicantMemberId()));
        model.addAttribute("canPrint",
            app.getFinancialSnapshot() != null
                && !app.getFinancialSnapshot().isBlank()
                && app.getStatus() != LoanStatus.DRAFT
                && app.getStatus() != LoanStatus.AWAITING_GUARANTORS
                && app.getStatus() != LoanStatus.ALL_GUARANTORS_APPROVED
                && guarantorRequests.stream().filter(req -> req.getStatus() == GuarantorRequestStatus.APPROVED).count() >= app.getRequiredGuarantors());
        model.addAttribute("statusTimeline", List.of(
            LoanStatus.DRAFT, LoanStatus.AWAITING_GUARANTORS, LoanStatus.ALL_GUARANTORS_APPROVED, LoanStatus.READY_FOR_MANAGER,
            LoanStatus.MANAGER_ACCEPTED, LoanStatus.AWAITING_BOARD, LoanStatus.BOARD_APPROVED,
            LoanStatus.READY_FOR_DISBURSEMENT, LoanStatus.FORFEITED, LoanStatus.FINAL_APPROVED, LoanStatus.DEFAULTED, LoanStatus.PAID
        ));
        return "app/loan-view";
    }

    private boolean loanFeeReceiptMissingBlocksDisbursement(LoanApplication app) {
        if (app == null || loanWorkflowService.hasLoanFeeReceipt(app)) {
            return false;
        }
        return canUploadLoanFeeReceipt(app);
    }

    private boolean canUploadLoanFeeReceipt(LoanApplication app) {
        if (app == null) {
            return false;
        }
        return switch (app.getStatus()) {
            case ALL_GUARANTORS_APPROVED,
                 READY_FOR_MANAGER,
                 MANAGER_ACCEPTED,
                 AWAITING_LOAN_OFFICER,
                 LOAN_OFFICER_APPROVED,
                 AWAITING_BOARD,
                 BOARD_APPROVED,
                 AWAITING_ACCOUNTANT,
                 ACCOUNTANT_APPROVED,
                 READY_FOR_DISBURSEMENT -> true;
            default -> false;
        };
    }

    @PostMapping("/loan-applications/{id}/fee-insurance-receipt")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#id, principal)")
    public String uploadFeeInsuranceReceipt(@PathVariable UUID id,
                                            @AuthenticationPrincipal AppUserPrincipal principal,
                                            @RequestParam(required = false) MultipartFile feeInsuranceReceiptFile,
                                            RedirectAttributes ra) {
        try {
            LoanApplication app = loanWorkflowService.getMine(id, principal.getMemberId());
            if (!canUploadLoanFeeReceipt(app)) {
                throw new IllegalStateException("Upload the fees and insurance receipt after all guarantors have approved this application and before disbursement.");
            }
            if (feeInsuranceReceiptFile == null || feeInsuranceReceiptFile.isEmpty()) {
                throw new IllegalArgumentException("Choose the payment receipt file to upload.");
            }
            app.setAttachmentsJson(loanAttachmentService.store(
                app.getId(),
                List.of(feeInsuranceReceiptFile),
                app.getAttachmentsJson(),
                LoanAttachmentService.CATEGORY_FEE_INSURANCE_RECEIPT
            ));
            loanApplicationRepository.save(app);
            ra.addFlashAttribute("message", "Fees and insurance payment receipt uploaded.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/app/loan-applications/" + id;
    }

    private void addMemberLoanViewDisplayAttributes(Model model, LoanApplication app) {
        model.addAttribute("loanIdShort", app.getApplicationNumber() == null ? "" : app.getApplicationNumber().toString());
        model.addAttribute("disbursedLoanId", app.getLoanId());
        model.addAttribute("loanStatusBadgeClass", memberLoanStatusBadgeClass(app.getStatus()));
        model.addAttribute("loanProgressItems", loanPresentationService.buildProgressItems(app));
    }

    private String finalSubmitLabel(LoanApplication app) {
        LoanProductWorkflowService.WorkflowDefinition workflow = loanProductWorkflowService.resolveForApplication(app);
        ApprovalWorkflowStage firstStage = workflow.stages().isEmpty() ? ApprovalWorkflowStage.MANAGER : workflow.stages().getFirst();
        return "Submit to " + workflowStageLabel(firstStage);
    }

    private String workflowStageLabel(ApprovalWorkflowStage stage) {
        if (stage == null) {
            return "Review";
        }
        return switch (stage) {
            case MANAGER -> "Manager";
            case LOAN_OFFICER -> "Loan Officer";
            case BOARD -> "Committee";
            case ACCOUNTANT -> "Accountant";
            case DISBURSEMENT_OFFICER -> "Disbursement Officer";
        };
    }

    private int memberLoanProgressStep(LoanStatus status) {
        if (status == null) {
            return 1;
        }
        return switch (status) {
            case DRAFT -> 1;
            case SUBMITTED, AWAITING_GUARANTORS, ALL_GUARANTORS_APPROVED -> 2;
            case READY_FOR_MANAGER, MANAGER_REJECTED, MANAGER_ACCEPTED -> 3;
            case AWAITING_LOAN_OFFICER, LOAN_OFFICER_REJECTED, LOAN_OFFICER_APPROVED,
                AWAITING_BOARD, BOARD_REJECTED, BOARD_APPROVED,
                AWAITING_ACCOUNTANT, ACCOUNTANT_REJECTED, ACCOUNTANT_APPROVED, READY_FOR_DISBURSEMENT -> 4;
            case FORFEITED, FINAL_REJECTED, FINAL_APPROVED, DEFAULTED, PAID -> 5;
        };
    }

    private int memberLoanProgressPercent(LoanStatus status) {
        if (status == null) {
            return 8;
        }
        return switch (status) {
            case DRAFT -> 10;
            case SUBMITTED, AWAITING_GUARANTORS, ALL_GUARANTORS_APPROVED -> 35;
            case READY_FOR_MANAGER, MANAGER_REJECTED, MANAGER_ACCEPTED -> 58;
            case AWAITING_LOAN_OFFICER, LOAN_OFFICER_REJECTED, LOAN_OFFICER_APPROVED,
                AWAITING_BOARD, BOARD_REJECTED, BOARD_APPROVED,
                AWAITING_ACCOUNTANT, ACCOUNTANT_REJECTED, ACCOUNTANT_APPROVED, READY_FOR_DISBURSEMENT -> 80;
            case FORFEITED, FINAL_REJECTED, FINAL_APPROVED, DEFAULTED, PAID -> 100;
        };
    }

    private String memberLoanStatusBadgeClass(LoanStatus status) {
        if (status == null) {
            return "bg-slate-100 text-slate-700";
        }
        return switch (status) {
            case DRAFT -> "bg-slate-100 text-slate-700";
            case SUBMITTED, AWAITING_GUARANTORS, ALL_GUARANTORS_APPROVED -> "bg-cyan-50 text-cyan-700";
            case READY_FOR_MANAGER -> "bg-amber-50 text-amber-700";
            case MANAGER_REJECTED, LOAN_OFFICER_REJECTED, BOARD_REJECTED, ACCOUNTANT_REJECTED, FORFEITED, DEFAULTED -> "bg-rose-50 text-rose-700";
            case AWAITING_LOAN_OFFICER, AWAITING_BOARD, AWAITING_ACCOUNTANT -> "bg-blue-50 text-blue-700";
            case MANAGER_ACCEPTED, LOAN_OFFICER_APPROVED, BOARD_APPROVED, ACCOUNTANT_APPROVED, READY_FOR_DISBURSEMENT,
                FINAL_APPROVED, PAID -> "bg-emerald-50 text-emerald-700";
            case FINAL_REJECTED -> "bg-rose-50 text-rose-700";
        };
    }

    @PostMapping("/loan-applications/{id}/submit")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#id, principal)")
    public String submit(@PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal principal,
                         @RequestParam(required = false) String applicantSignatureOtpCode,
                         @RequestParam(required = false) String termsAccepted,
                         RedirectAttributes ra) {
        try {
            LoanApplication current = loanWorkflowService.getMine(id, principal.getMemberId());
            if (requiresTermsBeforeStaffSubmission(current)) {
                requireTermsAccepted(termsAccepted);
            }
            UUID applicantSignatureOtpTokenId = validateApplicantSignatureOtpIfRequired(current, principal.getMemberId(), applicantSignatureOtpCode);
            LoanApplication app = loanWorkflowService.submit(id, principal.getMemberId());
            if (app.getStatus() == LoanStatus.AWAITING_GUARANTORS) {
                ra.addFlashAttribute("message", "Application sent to guarantors successfully.");
                return "redirect:/app/loan-applications/" + id;
            }
            if (app.getStatus() == LoanStatus.READY_FOR_MANAGER) {
                Member signingMember = requireMemberWithSavedSignature(principal.getMemberId());
                loanWorkflowService.recordApplicantSignature(
                    app.getId(),
                    principal.getMemberId(),
                    signingMember.getSignatureText(),
                    OffsetDateTime.now()
                );
                if (applicantSignatureOtpTokenId != null) {
                    emailOtpService.consumeOtpById(applicantSignatureOtpTokenId);
                }
                ra.addFlashAttribute("message", "Application submitted and sent for manager review.");
                return "redirect:/app/loan-applications/" + id;
            }
            if (app.getStatus() == LoanStatus.AWAITING_BOARD) {
                ra.addFlashAttribute("message", "Application submitted and sent for committee review.");
                return "redirect:/app/loan-applications/" + id;
            }
            if (app.getStatus() == LoanStatus.MANAGER_ACCEPTED) {
                ra.addFlashAttribute("message", "Application completed the configured review path and is now ready for disbursement.");
                return "redirect:/app/loan-applications/" + id;
            }
            ra.addFlashAttribute("message", "Application status updated successfully.");
            return "redirect:/app/loan-applications/" + id;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/app/loan-applications/" + id;
        }
    }

    private void requireTermsAccepted(String termsAccepted) {
        if (!"true".equalsIgnoreCase(String.valueOf(termsAccepted))) {
            throw new IllegalStateException("Accept the terms and conditions before submitting this loan application.");
        }
    }

    private boolean requiresTermsBeforeStaffSubmission(LoanApplication app) {
        if (app == null) {
            return false;
        }
        return app.getStatus() == LoanStatus.ALL_GUARANTORS_APPROVED
            || (app.getStatus() == LoanStatus.DRAFT && (app.getRequiredGuarantors() == null || app.getRequiredGuarantors() <= 0));
    }

    @PostMapping("/loan-applications/{id}/cancel")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#id, principal)")
    public String cancelSubmission(@PathVariable UUID id,
                                   @AuthenticationPrincipal AppUserPrincipal principal,
                                   RedirectAttributes ra) {
        try {
            LoanApplication app = loanWorkflowService.getMine(id, principal.getMemberId());
            if (app.getStatus() == LoanStatus.READY_FOR_MANAGER) {
                reversalRequestService.requestManagerStageWithdrawal(id, principal.getMemberId());
                ra.addFlashAttribute("message", "Removal request sent to the manager for approval.");
                return "redirect:/app/loan-applications/" + id;
            }
            loanWorkflowService.cancelSubmission(id, principal.getMemberId());
            ra.addFlashAttribute("message", "Application moved back to draft.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/app/loan-applications/" + id;
    }

    @PostMapping("/loan-applications/{id}/forfeit")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#id, principal)")
    public String forfeitApplication(@PathVariable UUID id,
                                     @AuthenticationPrincipal AppUserPrincipal principal,
                                     @RequestParam String forfeitOtpCode,
                                     RedirectAttributes ra) {
        try {
            Member member = requireMemberWithEmail(principal.getMemberId());
            UUID otpTokenId = emailOtpService.validateOtp(member.getEmail(), EmailOtpPurpose.LOAN_APPLICATION_FORFEIT, forfeitOtpCode);
            loanWorkflowService.forfeitReviewApplication(id, principal.getMemberId());
            emailOtpService.consumeOtpById(otpTokenId);
            ra.addFlashAttribute("message", "Application forfeited successfully.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/app/loan-applications/" + id;
    }

    @GetMapping("/loan-applications/{id}/guarantors")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#id, principal)")
    public String guarantorSelection(@PathVariable UUID id,
                                     @AuthenticationPrincipal AppUserPrincipal principal,
                                     @RequestParam(defaultValue = "") String q,
                                     @RequestParam(defaultValue = "0") int page,
                                     RedirectAttributes ra) {
        loanWorkflowService.getMine(id, principal.getMemberId());
        ra.addFlashAttribute("message", "Guarantors are selected directly from the application form before submission.");
        return "redirect:/app/loan-applications/" + id;
    }

    @PostMapping("/loan-applications/{id}/guarantors")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#id, principal)")
    public String saveGuarantors(@PathVariable UUID id,
                                 @AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam(required = false) List<UUID> guarantorIds,
                                 RedirectAttributes ra) {
        loanWorkflowService.selectGuarantors(id, principal.getMemberId(), guarantorIds);
        ra.addFlashAttribute("message", "Guarantors assigned successfully");
        return "redirect:/app/loan-applications/" + id;
    }

    @PostMapping("/loan-applications/{id}/sync-payments")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#id, principal)")
    public String syncLoanPayments(@PathVariable UUID id,
                                   @AuthenticationPrincipal AppUserPrincipal principal,
                                   @RequestParam(name = "monthsBack", defaultValue = "12") int monthsBack,
                                   RedirectAttributes ra) {
        try {
            int inserted = loanWorkflowService.syncLoanPayments(id, principal.getMemberId(), monthsBack);
            ra.addFlashAttribute("message",
                inserted == 0
                    ? "Repayment data refreshed; nothing new was found."
                    : "Repayment data refreshed; " + inserted + " new payment record(s) were added.");
        } catch (LoanPaymentLookupException ex) {
            ra.addFlashAttribute("error", "Repayment data could not be fetched: " + ex.getMessage());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/app/loan-applications/" + id;
    }

    @PostMapping("/loan-applications/{id}/delete")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#id, principal)")
    public String deleteApplication(@PathVariable UUID id,
                                    @AuthenticationPrincipal AppUserPrincipal principal,
                                    RedirectAttributes ra) {
        try {
            loanWorkflowService.deleteApplication(id, principal.getMemberId());
            ra.addFlashAttribute("message", "Application removed successfully.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/app/loan-applications/" + id;
        }
        return "redirect:/app/loan-applications";
    }

    @GetMapping("/guarantors/search")
    @ResponseBody
    public List<Map<String, String>> searchGuarantors(@AuthenticationPrincipal AppUserPrincipal principal,
                                                      @RequestParam(defaultValue = "") String q,
                                                      @RequestParam(required = false) BigDecimal amount) {
        String query = q == null ? "" : q.trim().toUpperCase(Locale.ROOT);
        boolean fourDigitsOrMore = query.matches("\\d{4,20}");
        boolean fullMemberNo = query.matches("[A-Z0-9]{4,20}") && query.chars().anyMatch(Character::isDigit);
        if (!fourDigitsOrMore && !fullMemberNo) {
            return Collections.emptyList();
        }
        return loanWorkflowService.searchGuarantorCandidates(
                principal.getSaccoId(),
                principal.getStationId(),
                principal.getMemberId(),
                query,
                amount,
                0,
                6)
            .stream()
            .map(candidate -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", candidate.id().toString());
                row.put("memberNo", candidate.memberNo());
                row.put("fullName", candidate.fullName());
                row.put("eligible", Boolean.toString(candidate.eligible()));
                row.put("disabledReason", candidate.disabledReason());
                return row;
            })
            .toList();
    }

    @PostMapping("/loan-applications/financial-preview")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> financialPreview(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                @RequestParam LoanType loanType,
                                                                @RequestParam BigDecimal amount,
                                                                @RequestParam Integer tenorMonths,
                                                                @RequestParam(required = false) UUID topUpLoanId) {
        try {
            loanWorkflowService.requireAllowedTopUpSourceLoan(principal.getSaccoId(), principal.getMemberId(), topUpLoanId);
            LoanProductSetting product = formSchemaService.getSchema(principal.getSaccoId(), loanType);
            loanWorkflowService.assertCanApplyForProduct(principal.getSaccoId(), principal.getMemberId(), product);
            Map<String, Object> snapshot = financialDetailsService.generateSnapshot(
                principal.getSaccoId(), principal.getMemberId(), loanType, amount, tenorMonths, topUpLoanId);
            EligibilityService.EligibilityResult eligibility = eligibilityService.check(
                principal.getSaccoId(), principal.getMemberId(), loanType, amount);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("snapshotJson", financialDetailsService.toJson(snapshot));
            response.put("fields", loanPresentationService.parseFinancialFields(financialDetailsService.toJson(snapshot)));
            response.put("message", "Loan details loaded");
            BigDecimal principalPlusInterest = readBigDecimal(snapshot.get("principalPlusInterest"));
            if (principalPlusInterest == null) {
                principalPlusInterest = readBigDecimal(snapshot.get("loanPlusInterest"));
            }
            response.put("principalPlusInterest", principalPlusInterest == null ? "" : principalPlusInterest.setScale(2, RoundingMode.HALF_UP).toPlainString());
            response.put("principalPlusInterestLabel", principalPlusInterest == null ? "" : formatTzs(principalPlusInterest));
            Map<String, Object> eligibilityMap = new LinkedHashMap<>();
            eligibilityMap.put("eligible", eligibility.eligible());
            eligibilityMap.put("savingsLabel", formatTzs(eligibility.savings()));
            eligibilityMap.put("maxAllowedLabel", formatTzs(eligibility.maxAllowed()));
            eligibilityMap.put("ratioPercentLabel",
                eligibility.ratio().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%");
            response.put("eligibility", eligibilityMap);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "message", ex.getMessage()
            ));
        }
    }

    @GetMapping("/loan-applications/external-eligibility-summary")
    @ResponseBody
    public Map<String, Object> externalEligibilitySummary(@AuthenticationPrincipal AppUserPrincipal principal,
                                                          @RequestParam LoanType loanType) {
        Member member = memberRepository.findById(principal.getMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Logged-in member was not found."));
        if (member.getStationId() == null || member.getStationId().isBlank()) {
            throw new IllegalStateException("Station ID is not configured for this member.");
        }
        EligibilityService.EligibilityResult eligibility = eligibilityService.check(
            principal.getSaccoId(),
            principal.getMemberId(),
            loanType,
            BigDecimal.ZERO
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("savingsLabel", formatTzs(eligibility.savings()));
        response.put("ratioPercentLabel", eligibility.ratio().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%");
        response.put("maxAllowedLabel", formatTzs(eligibility.maxAllowed()));
        response.put("exampleAmountLabel", formatTzs(exampleAmount(eligibility.maxAllowed())));
        response.put("memberNumber", member.getMemberNo());
        response.put("stationId", member.getStationId());
        return response;
    }

    @GetMapping("/guarantee-requests")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPROVE_GUARANTOR_REQUESTS')")
    public String myGuarantorRequests(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        List<GuarantorRequest> requests = activeGuarantorRequests(
            visibleGuarantorRequests(loanWorkflowService.myGuarantorRequests(principal.getMemberId())));
        model.addAttribute("requests", requests);
        addGuaranteeActionContext(requests, model);
        model.addAttribute("guarantorSavedSignatureText", resolveSavedSignatureText(principal.getMemberId()));
        return "app/guarantee-requests";
    }

    @GetMapping("/guaranteed-loans")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPROVE_GUARANTOR_REQUESTS')")
    public String guaranteedLoans(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        List<GuarantorRequest> requests = loanWorkflowService.myGuarantorRequests(principal.getMemberId());
        Map<UUID, LoanApplication> loansById = loanApplicationsById(requests);
        List<Map<String, Object>> rows = requests.stream()
            .filter(request -> request.getStatus() == GuarantorRequestStatus.APPROVED)
            .filter(request -> {
                LoanApplication loan = loansById.get(request.getLoanApplicationId());
                return loan != null && isActiveRepaymentLoan(loan);
            })
            .map(request -> {
                LoanApplication loan = loansById.get(request.getLoanApplicationId());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("request", request);
                row.put("loan", loan);
                row.put("statusLabel", loan == null ? "Not available" : loan.getStatus().name().replace('_', ' '));
                row.put("daysLeft", loan == null || loan.getFinalDueDate() == null
                    ? null
                    : Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), loan.getFinalDueDate())));
                return row;
            })
            .toList();
        model.addAttribute("guaranteedLoans", rows);
        return "app/guaranteed-loans";
    }

    @PostMapping("/guarantee-requests/{requestId}/approve")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPROVE_GUARANTOR_REQUESTS') and @authz.isGuarantorAssignee(#requestId, principal)")
    public String approveRequest(@PathVariable UUID requestId,
                                 @AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam(required = false, defaultValue = "false") boolean guarantorDeclarationAccepted,
                                 @RequestParam(required = false) String guarantorSignatureOtpCode,
                                 RedirectAttributes ra) {
        if (!guarantorDeclarationAccepted) {
            ra.addFlashAttribute("error", "Confirm the guarantor declaration before approving the request.");
            return "redirect:/app/guarantee-requests";
        }
        try {
            Member guarantor = requireMemberWithSavedSignature(
                principal.getMemberId(),
                "Add an email address to your member profile before requesting a guarantor OTP.",
                "Register your signature first before approving guarantor requests."
            );
            UUID otpTokenId = emailOtpService.validateOtp(
                guarantor.getEmail(), EmailOtpPurpose.GUARANTOR_SIGNATURE, guarantorSignatureOtpCode);
            loanWorkflowService.approveGuarantorRequest(
                requestId,
                principal.getMemberId(),
                guarantor.getSignatureText(),
                OffsetDateTime.now()
            );
            emailOtpService.consumeOtpById(otpTokenId);
            ra.addFlashAttribute("message", "Guarantee request approved");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/app/guarantee-requests";
    }

    @PostMapping("/guarantee-requests/request-signature-otp")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPROVE_GUARANTOR_REQUESTS')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestGuarantorSignatureOtp(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                            @RequestParam(required = false) UUID requestId) {
        try {
            Member member = requireMemberWithSavedSignature(
                principal.getMemberId(),
                "Add an email address to your member profile before requesting a guarantor OTP.",
                "Register your signature first before approving guarantor requests."
            );
            if (requestId != null) {
                GuarantorRequest request = guarantorRequestRepository.findByIdAndGuarantorMemberId(requestId, principal.getMemberId())
                    .orElseThrow(() -> new IllegalArgumentException("Guarantor request not found"));
                if (request.getStatus() != GuarantorRequestStatus.PENDING) {
                    throw new IllegalStateException("Request already decided");
                }
                LoanApplication application = loanApplicationRepository.findById(request.getLoanApplicationId())
                    .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
                BigDecimal requestedAmount = request.getRequestedAmount();
                if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalStateException("This guarantee request does not have an assigned commitment amount.");
                }
                loanQualificationPolicyService.assertGuarantorCanCommit(application.getSaccoId(), principal.getMemberId(), requestedAmount);
            }
            emailOtpService.issueOtp(
                member.getEmail(),
                EmailOtpPurpose.GUARANTOR_SIGNATURE,
                member.getId(),
                "Your SACCO MVP guarantor confirmation code",
                "Use this OTP code to confirm your guarantor signature and approve the request."
            );
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "We sent a guarantor confirmation code to " + member.getEmail() + "."
            ));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/loan-applications/request-signature-otp")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestApplicantSignatureOtp(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                            @RequestParam UUID applicationId) {
        try {
            LoanApplication application = loanWorkflowService.getMine(applicationId, principal.getMemberId());
            assertApplicantSignatureOtpAllowed(application);
            Member member = requireMemberWithSavedSignature(principal.getMemberId());
            emailOtpService.issueOtp(
                member.getEmail(),
                EmailOtpPurpose.APPLICANT_SIGNATURE,
                member.getId(),
                "Your SACCO MVP submission code",
                "Use this OTP code to confirm your signature and submit your loan application."
            );
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "We sent a submission code to " + member.getEmail() + "."
            ));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/loan-applications/verify-signature-otp")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyApplicantSignatureOtp(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                           @RequestParam UUID applicationId,
                                                                           @RequestParam String otpCode) {
        try {
            LoanApplication application = loanWorkflowService.getMine(applicationId, principal.getMemberId());
            assertApplicantSignatureOtpAllowed(application);
            validateApplicantSignatureOtp(principal.getMemberId(), otpCode);
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "Verified"
            ));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/loan-applications/request-forfeit-otp")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestForfeitOtp(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                 @RequestParam UUID applicationId) {
        try {
            LoanApplication application = loanWorkflowService.getMine(applicationId, principal.getMemberId());
            if (!loanWorkflowService.canForfeitReviewApplication(application)) {
                throw new IllegalStateException("This application cannot be forfeited at its current stage.");
            }
            Member member = requireMemberWithEmail(principal.getMemberId());
            emailOtpService.issueOtp(
                member.getEmail(),
                EmailOtpPurpose.LOAN_APPLICATION_FORFEIT,
                member.getId(),
                "Your loan application forfeit code",
                "Use this OTP code to confirm that you want to forfeit this loan application."
            );
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "We sent a forfeit confirmation code to " + member.getEmail() + "."
            ));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/guarantee-requests/{requestId}/reject")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPROVE_GUARANTOR_REQUESTS') and @authz.isGuarantorAssignee(#requestId, principal)")
    public String rejectRequest(@PathVariable UUID requestId,
                                @AuthenticationPrincipal AppUserPrincipal principal,
                                RedirectAttributes ra) {
        loanWorkflowService.rejectGuarantorRequest(requestId, principal.getMemberId(), "Declined by guarantor");
        ra.addFlashAttribute("message", "Guarantee request rejected");
        return "redirect:/app/guarantee-requests";
    }

    @PostMapping("/guarantee-requests/{requestId}/undo")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPROVE_GUARANTOR_REQUESTS') and @authz.isGuarantorAssignee(#requestId, principal)")
    public String undoGuarantorDecision(@PathVariable UUID requestId,
                                        @AuthenticationPrincipal AppUserPrincipal principal,
                                        RedirectAttributes ra) {
        try {
            reversalRequestService.requestGuarantorUndo(requestId, principal.getMemberId());
            ra.addFlashAttribute("message", "Removal request sent to the applicant for approval.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/app/guarantee-requests";
    }

    @PostMapping("/loan-applications/{loanId}/guarantor-reversal-requests/{requestId}/approve")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#loanId, principal)")
    public String approveGuarantorUndoRequest(@PathVariable UUID loanId,
                                              @PathVariable UUID requestId,
                                              @AuthenticationPrincipal AppUserPrincipal principal,
                                              RedirectAttributes ra) {
        try {
            reversalRequestService.decideGuarantorUndo(requestId, principal.getMemberId(), true);
            ra.addFlashAttribute("message", "Guarantor removed from this application.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/app/loan-applications/" + loanId;
    }

    @PostMapping("/loan-applications/{loanId}/guarantor-reversal-requests/{requestId}/reject")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#loanId, principal)")
    public String rejectGuarantorUndoRequest(@PathVariable UUID loanId,
                                             @PathVariable UUID requestId,
                                             @AuthenticationPrincipal AppUserPrincipal principal,
                                             RedirectAttributes ra) {
        try {
            reversalRequestService.decideGuarantorUndo(requestId, principal.getMemberId(), false);
            ra.addFlashAttribute("message", "Guarantor removal request declined.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/app/loan-applications/" + loanId;
    }

    @GetMapping("/notifications")
    public String notifications(@AuthenticationPrincipal AppUserPrincipal principal,
                                @RequestParam(required = false) UUID highlight,
                                Model model) {
        model.addAttribute("notifications", notificationInboxService.allViews(principal.getMemberId(), principal.getGrantedPositions()));
        model.addAttribute("highlightNotificationId", highlight);
        return "app/notifications";
    }

    @GetMapping("/notifications/{id}/open")
    public String openNotification(@PathVariable UUID id,
                                   @AuthenticationPrincipal AppUserPrincipal principal,
                                   RedirectAttributes ra) {
        try {
            return "redirect:" + notificationInboxService.openForMember(
                id, principal.getMemberId(), principal.getGrantedPositions(), principal.getPosition(), "/app/notifications");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/app/notifications";
        }
    }

    @GetMapping("/support")
    public String support() {
        return "app/support";
    }

    @GetMapping("/support/archive")
    public String supportArchive(@AuthenticationPrincipal AppUserPrincipal principal,
                                 Model model) {
        model.addAttribute("supportArchive", adminService.memberSupportArchive(principal.getMemberId()));
        return "app/support-archive";
    }

    @GetMapping("/support/replies")
    public String supportReplies(@AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam(required = false) UUID highlight,
                                 Model model) {
        model.addAttribute("replies", notificationInboxService.allViewsByType(principal.getMemberId(), "ADMIN_REPLY"));
        model.addAttribute("broadcasts", notificationInboxService.allViewsByType(principal.getMemberId(), "ADMIN_BROADCAST"));
        model.addAttribute("highlightNotificationId", highlight);
        return "app/support-replies";
    }

    @GetMapping("/support/replies/{id}/open")
    public String openSupportReply(@PathVariable UUID id,
                                   @AuthenticationPrincipal AppUserPrincipal principal,
                                   RedirectAttributes ra) {
        try {
            return "redirect:" + notificationInboxService.openForMember(
                id, principal.getMemberId(), principal.getGrantedPositions(), principal.getPosition(), "/app/support/replies");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/app/support/replies";
        }
    }

    @PostMapping("/support/replies/mark-all-read")
    public String markAllSupportRepliesRead(@AuthenticationPrincipal AppUserPrincipal principal,
                                            RedirectAttributes ra) {
        int updated = notificationInboxService.markAllAsReadByTypes(
            principal.getMemberId(),
            List.of("ADMIN_REPLY", "ADMIN_BROADCAST")
        );
        ra.addFlashAttribute("message", updated > 0
            ? "All support replies have been marked as read."
            : "There were no unread support replies.");
        return "redirect:/app/support/replies";
    }

    @GetMapping("/settings")
    @PreAuthorize("hasRole('MEMBER')")
    public String settings(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        UserSettings settings = userSettingsRepository.findById(principal.getMemberId())
            .orElseGet(() -> UserSettings.builder()
                .memberId(principal.getMemberId())
                .language("en")
                .notificationPrefs("{}")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());
        model.addAttribute("memberSettingsLanguage", normalizeMemberLanguage(settings.getLanguage()));
        return "app/settings";
    }

    @PostMapping("/settings/language")
    @PreAuthorize("hasRole('MEMBER')")
    public String updateLanguage(@AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam String language,
                                 RedirectAttributes ra) {
        OffsetDateTime now = OffsetDateTime.now();
        UserSettings settings = userSettingsRepository.findById(principal.getMemberId())
            .orElseGet(() -> UserSettings.builder()
                .memberId(principal.getMemberId())
                .language("en")
                .notificationPrefs("{}")
                .createdAt(now)
                .updatedAt(now)
                .build());
        settings.setLanguage(normalizeMemberLanguage(language));
        if (settings.getNotificationPrefs() == null || settings.getNotificationPrefs().isBlank()) {
            settings.setNotificationPrefs("{}");
        }
        if (settings.getCreatedAt() == null) {
            settings.setCreatedAt(now);
        }
        settings.setUpdatedAt(now);
        userSettingsRepository.save(settings);
        ra.addFlashAttribute("message", "Language preference updated.");
        return "redirect:/app/settings";
    }

    @PostMapping("/notifications/mark-all-read")
    public String markAllNotificationsRead(@AuthenticationPrincipal AppUserPrincipal principal,
                                           RedirectAttributes ra) {
        int updated = notificationInboxService.markAllAsRead(principal.getMemberId());
        if (updated > 0) {
            ra.addFlashAttribute("message", "All notifications have been marked as read.");
        } else {
            ra.addFlashAttribute("message", "There were no unread notifications.");
        }
        return "redirect:/app/notifications";
    }

    @PostMapping("/support")
    public String sendSupport(@AuthenticationPrincipal AppUserPrincipal principal,
                              @RequestParam String subject,
                              @RequestParam String message,
                              RedirectAttributes ra) {
        adminService.submitSupport(principal.getSaccoId(), principal.getMemberId(), subject, message);
        ra.addFlashAttribute("message", "Your message has been sent to the system administrator.");
        return "redirect:/app/support";
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String handleError(RuntimeException ex, RedirectAttributes ra) {
        ra.addFlashAttribute("error", ex.getMessage());
        return "redirect:/app/dashboard";
    }

    private String prepareLoanNewModel(AppUserPrincipal principal,
                                       LoanType loanType,
                                       Map<String, String> formValues,
                                       List<UUID> guarantorIds,
                                       Map<UUID, BigDecimal> selectedGuarantorCommitments,
                                       Integer requiredGuarantorsOverride,
                                       Model model) {
        LoanProductSetting schema = formSchemaService.getSchema(principal.getSaccoId(), loanType);
        FormModel form = formSchemaService.toFormModel(loanType, schema.getFormSchema());
        int requiredGuarantors = requiredGuarantorsOverride == null
            ? schema.getGuarantorsRequired()
            : Math.max(requiredGuarantorsOverride, 0);
        EligibilityService.EligibilityResult eligibility = eligibilityService.check(
            principal.getSaccoId(), principal.getMemberId(), loanType, BigDecimal.ZERO);

        model.addAttribute("formModel", form);
        model.addAttribute("product", schema);
        model.addAttribute("loanType", loanType);
        model.addAttribute("loanProductName", schema.getDisplayName());
        model.addAttribute("loanProductCode", schema.getDisplayCode());
        model.addAttribute("loanProductDescription", schema.getDisplayDescription());
        model.addAttribute("requiredGuarantors", requiredGuarantors);
        model.addAttribute("formValues", formValues == null ? Collections.emptyMap() : formValues);
        model.addAttribute("selectedGuarantorLookup", toLookupMap(guarantorIds));
        Map<UUID, BigDecimal> commitmentValues = selectedGuarantorCommitments == null || selectedGuarantorCommitments.isEmpty()
            ? parseGuarantorCommitments(formValues)
            : selectedGuarantorCommitments;
        model.addAttribute("selectedGuarantorItems", selectedGuarantorItems(guarantorIds, commitmentValues));
        model.addAttribute("savingsLabel", formatTzs(eligibility.savings()));
        model.addAttribute("maxAllowedLabel", formatTzs(eligibility.maxAllowed()));
        model.addAttribute("ratioPercentLabel",
            eligibility.ratio().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%");
        model.addAttribute("exampleAmountLabel", formatTzs(exampleAmount(eligibility.maxAllowed())));
        model.addAttribute("exampleAmountRaw", exampleAmount(eligibility.maxAllowed()).toPlainString());
        model.addAttribute("minimumAmountLabel",
            schema.getMinimumAmount() == null ? "-" : formatTzs(schema.getMinimumAmount()));
        model.addAttribute("maximumAmountLabel",
            schema.getMaximumAmount() == null ? "Not set" : formatTzs(schema.getMaximumAmount()));
        String annualInterestPercentLabel = loanType == LoanType.LOAN_ADVANCE
            ? "0% at 1 month, 12% after"
            : (schema.getInterestRate() == null
                ? BigDecimal.ZERO
                : schema.getInterestRate().multiply(BigDecimal.valueOf(100))).stripTrailingZeros().toPlainString() + "%";
        model.addAttribute("annualInterestPercentLabel", annualInterestPercentLabel);
        model.addAttribute("allowApplicationWithActiveLoan", schema.isApplicationWithActiveLoanAllowed());
        model.addAttribute("financialSnapshotDisplay",
            loanPresentationService.parseFinancialFields(formValues == null ? null : formValues.get("financialSnapshotJson")));
        model.addAttribute("savedSignatureText", resolveSavedSignatureText(principal.getMemberId()));
        model.addAttribute("topUpLoanId", formValues == null ? null : formValues.get("topUpLoanId"));
        model.addAttribute("topUpSourceLoan", resolveTopUpSourceLoan(principal.getMemberId(), formValues == null ? null : formValues.get("topUpLoanId")));
        return "app/loan-new";
    }

    private Integer resolveDraftRequiredGuarantors(UUID memberId, UUID applicationId) {
        if (applicationId == null) {
            return null;
        }
        return loanApplicationRepository.findById(applicationId)
            .filter(app -> app.getApplicantMemberId().equals(memberId))
            .map(LoanApplication::getRequiredGuarantors)
            .orElse(null);
    }

    private Map<UUID, Boolean> toLookupMap(List<UUID> values) {
        Map<UUID, Boolean> lookup = new HashMap<>();
        if (values == null) {
            return lookup;
        }
        for (UUID value : values) {
            lookup.put(value, true);
        }
        return lookup;
    }

    private BigDecimal exampleAmount(BigDecimal maxAllowed) {
        if (maxAllowed == null || maxAllowed.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.DOWN);
        }
        BigDecimal half = maxAllowed.multiply(new BigDecimal("0.5")).setScale(2, RoundingMode.DOWN);
        return half.compareTo(BigDecimal.ZERO) > 0 ? half : maxAllowed.setScale(2, RoundingMode.DOWN);
    }

    private String formatTzs(BigDecimal amount) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.DOWN);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        return "TSh " + format.format(safeAmount);
    }

    private BigDecimal readBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).replace(",", "").trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String humanizeLoanFormError(String message, AppUserPrincipal principal, LoanType loanType) {
        if (message != null && message.contains("Amount exceeds eligibility cap")) {
            EligibilityService.EligibilityResult eligibility = eligibilityService.check(
                principal.getSaccoId(), principal.getMemberId(), loanType, BigDecimal.ZERO);
            return "Amount exceeds your eligibility. Maximum allowed now is " + formatTzs(eligibility.maxAllowed())
                + " (" + eligibility.ratio().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()
                + "% of savings " + formatTzs(eligibility.savings()) + ").";
        }
        if (message != null && message.contains("Load SACCO financial details")) {
            return "Load the loan details first so the application can include the official deductions section.";
        }
        return message == null ? "Unable to process loan form." : message;
    }

    private Member requireMemberWithSavedSignature(UUID memberId) {
        return requireMemberWithSavedSignature(
            memberId,
            "Add an email address to your member profile before requesting a submission OTP.",
            "Register your signature first before submitting a loan application."
        );
    }

    private Member requireMemberWithSavedSignature(UUID memberId,
                                                   String missingEmailMessage,
                                                   String missingSignatureMessage) {
        Member member = requireMemberWithEmail(memberId, missingEmailMessage);
        if (member.getSignatureText() == null || member.getSignatureText().isBlank()) {
            throw new IllegalStateException(missingSignatureMessage);
        }
        return member;
    }

    private Member requireMemberWithEmail(UUID memberId) {
        return requireMemberWithEmail(memberId, "Your member profile needs an email address before requesting an OTP.");
    }

    private Member requireMemberWithEmail(UUID memberId, String missingEmailMessage) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Member account not found."));
        if (member.getEmail() == null || member.getEmail().isBlank()) {
            throw new IllegalStateException(missingEmailMessage);
        }
        return member;
    }

    private UUID validateApplicantSignatureOtpIfRequired(LoanApplication app, UUID memberId, String otpCode) {
        if (app == null) {
            return null;
        }
        boolean requiresOtp = app.getStatus() == LoanStatus.ALL_GUARANTORS_APPROVED
            || (app.getStatus() == LoanStatus.DRAFT && (app.getRequiredGuarantors() == null || app.getRequiredGuarantors() <= 0));
        if (!requiresOtp) {
            return null;
        }
        return validateApplicantSignatureOtp(memberId, otpCode);
    }

    private UUID validateApplicantSignatureOtp(UUID memberId, String otpCode) {
        Member signingMember = requireMemberWithSavedSignature(memberId);
        return emailOtpService.validateOtp(
            signingMember.getEmail(),
            EmailOtpPurpose.APPLICANT_SIGNATURE,
            otpCode
        );
    }

    private boolean requiresApplicantOtpBeforeImmediateSubmission(String saccoId, LoanType loanType) {
        return loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue(saccoId, loanType)
            .map(LoanProductSetting::getGuarantorsRequired)
            .map(required -> required == null || required <= 0)
            .orElse(false);
    }

    private void assertApplicantSignatureOtpAllowed(LoanApplication application) {
        if (application.getRequiredGuarantors() > 0 && application.getStatus() != LoanStatus.ALL_GUARANTORS_APPROVED) {
            throw new IllegalStateException("Request the OTP after all guarantors have approved this application.");
        }
        if (application.getRequiredGuarantors() <= 0 && application.getStatus() != LoanStatus.DRAFT) {
            throw new IllegalStateException("This application is no longer waiting for applicant OTP confirmation.");
        }
    }

    private boolean canRequestApplicantOtp(LoanApplication app) {
        return app != null && (
            (app.getRequiredGuarantors() > 0 && app.getStatus() == LoanStatus.ALL_GUARANTORS_APPROVED)
                || (app.getRequiredGuarantors() <= 0 && app.getStatus() == LoanStatus.DRAFT)
        );
    }

    private String resolveSavedSignatureText(UUID memberId) {
        return memberRepository.findById(memberId)
            .map(Member::getSignatureText)
            .filter(text -> text != null && !text.isBlank())
            .orElse("");
    }

    private List<Map<String, String>> selectedGuarantorItems(List<UUID> guarantorIds,
                                                            Map<UUID, BigDecimal> guarantorCommitments) {
        if (guarantorIds == null || guarantorIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<UUID, Member> membersById = memberRepository.findAllById(guarantorIds).stream()
            .collect(Collectors.toMap(Member::getId, member -> member));
        List<Map<String, String>> items = new ArrayList<>();
        for (UUID guarantorId : guarantorIds) {
            Member member = membersById.get(guarantorId);
            if (member == null) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("id", member.getId().toString());
            item.put("memberNo", member.getMemberNo());
            item.put("fullName", member.getFullName());
            BigDecimal amount = guarantorCommitments == null ? null : guarantorCommitments.get(member.getId());
            item.put("amount", amount == null ? "" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
            items.add(item);
        }
        return items;
    }

    private Map<String, String> parseJsonAsStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                values.put(entry.getKey(), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
            }
            return values;
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private List<UUID> parseUuidList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<?> raw = objectMapper.readValue(json, new TypeReference<List<?>>() {});
            List<UUID> values = new ArrayList<>();
            for (Object item : raw) {
                if (item instanceof String text) {
                    values.add(UUID.fromString(text));
                } else if (item instanceof Map<?, ?> map && map.get("id") != null) {
                    values.add(UUID.fromString(String.valueOf(map.get("id"))));
                }
            }
            return values;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private Map<UUID, BigDecimal> parseSelectedGuarantorCommitments(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            List<?> raw = objectMapper.readValue(json, new TypeReference<List<?>>() {});
            Map<UUID, BigDecimal> commitments = new LinkedHashMap<>();
            for (Object item : raw) {
                if (item instanceof Map<?, ?> map && map.get("id") != null && map.get("amount") != null) {
                    BigDecimal amount = readBigDecimal(map.get("amount"));
                    if (amount != null) {
                        commitments.put(UUID.fromString(String.valueOf(map.get("id"))), amount);
                    }
                }
            }
            return commitments;
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private Map<UUID, BigDecimal> parseGuarantorCommitments(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, BigDecimal> commitments = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!entry.getKey().startsWith("guarantorCommitmentAmount_")) {
                continue;
            }
            BigDecimal amount = readBigDecimal(entry.getValue());
            if (amount == null) {
                continue;
            }
            try {
                UUID guarantorId = UUID.fromString(entry.getKey().substring("guarantorCommitmentAmount_".length()));
                commitments.put(guarantorId, amount);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return commitments;
    }

    private void addGuaranteeContext(List<GuarantorRequest> requests, Model model) {
        Map<UUID, LoanApplication> applicationById = new HashMap<>();
        Set<UUID> applicationIds = new HashSet<>();
        for (GuarantorRequest request : requests) {
            applicationIds.add(request.getLoanApplicationId());
        }
        if (!applicationIds.isEmpty()) {
            for (LoanApplication application : loanApplicationRepository.findAllById(applicationIds)) {
                applicationById.put(application.getId(), application);
            }
        }

        Set<UUID> applicantIds = new HashSet<>();
        for (LoanApplication application : applicationById.values()) {
            applicantIds.add(application.getApplicantMemberId());
        }

        Map<UUID, String> applicantNames = new HashMap<>();
        if (!applicantIds.isEmpty()) {
            for (Member member : memberRepository.findAllById(applicantIds)) {
                applicantNames.put(member.getId(), member.getFullName());
            }
        }

        Map<UUID, String> guaranteeNames = new HashMap<>();
        Map<UUID, LoanType> guaranteeLoanTypes = new HashMap<>();
        Map<UUID, BigDecimal> guaranteeLoanAmounts = new HashMap<>();
        Map<UUID, String> guaranteeCommitmentAmounts = new HashMap<>();
        for (Map.Entry<UUID, LoanApplication> entry : applicationById.entrySet()) {
            LoanApplication application = entry.getValue();
            guaranteeNames.put(entry.getKey(),
                applicantNames.getOrDefault(application.getApplicantMemberId(), "Unknown Member"));
            guaranteeLoanTypes.put(entry.getKey(), application.getLoanType());
            guaranteeLoanAmounts.put(entry.getKey(), application.getAmount());
        }
        for (GuarantorRequest request : requests) {
            guaranteeCommitmentAmounts.put(request.getId(), formatTzs(request.getRequestedAmount()));
        }

        model.addAttribute("guaranteeNames", guaranteeNames);
        model.addAttribute("guaranteeLoanTypes", guaranteeLoanTypes);
        model.addAttribute("guaranteeLoanAmounts", guaranteeLoanAmounts);
        model.addAttribute("guaranteeCommitmentAmounts", guaranteeCommitmentAmounts);
    }

    private void addGuaranteeActionContext(List<GuarantorRequest> requests, Model model) {
        addGuaranteeContext(requests, model);
        Map<UUID, LoanApplication> applicationById = loanApplicationsById(requests);
        Map<UUID, ReversalRequest> pendingRemovalRequests = new HashMap<>();
        Map<UUID, Boolean> removalAllowed = new HashMap<>();
        Map<UUID, String> removalExpiryLabels = new HashMap<>();
        Map<UUID, Boolean> guaranteePolicyEligible = new HashMap<>();
        Map<UUID, String> guaranteePolicyReasons = new HashMap<>();
        for (GuarantorRequest request : requests) {
            ReversalRequest pendingUndo = reversalRequestService.pendingGuarantorUndo(request.getId());
            if (pendingUndo != null) {
                pendingRemovalRequests.put(request.getId(), pendingUndo);
            }
            LoanApplication application = applicationById.get(request.getLoanApplicationId());
            Optional<String> policyReason = application == null
                ? Optional.of("Loan application not found.")
                : loanQualificationPolicyService.guarantorFailureReason(
                    application.getSaccoId(),
                    request.getGuarantorMemberId(),
                    request.getRequestedAmount()
                );
            guaranteePolicyEligible.put(request.getId(), policyReason.isEmpty());
            policyReason.ifPresent(reason -> guaranteePolicyReasons.put(request.getId(), reason));
            boolean stageOpen = isGuarantorRemovalStageOpen(application);
            boolean canRequestRemoval = stageOpen && isWithinReversalWindow(request.getDecidedAt());
            removalAllowed.put(request.getId(), canRequestRemoval);
            if (canRequestRemoval && request.getDecidedAt() != null) {
                removalExpiryLabels.put(request.getId(), formatReversalWindowExpiry(request.getDecidedAt()));
            }
        }
        model.addAttribute("guaranteePendingRemovalRequests", pendingRemovalRequests);
        model.addAttribute("guaranteeRemovalAllowed", removalAllowed);
        model.addAttribute("guaranteeRemovalExpiryLabels", removalExpiryLabels);
        model.addAttribute("guaranteePolicyEligible", guaranteePolicyEligible);
        model.addAttribute("guaranteePolicyReasons", guaranteePolicyReasons);
    }

    private LoanApplication resolveTopUpSourceLoan(UUID memberId, String topUpLoanId) {
        if (topUpLoanId == null || topUpLoanId.isBlank()) {
            return null;
        }
        try {
            UUID loanId = UUID.fromString(topUpLoanId);
            return loanApplicationRepository.findByIdAndApplicantMemberId(loanId, memberId).orElse(null);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private List<LoanApplication> currentApplications(List<LoanApplication> apps) {
        return apps.stream()
            .filter(app -> !isArchived(app))
            .toList();
    }

    private List<LoanApplication> archivedApplications(List<LoanApplication> apps) {
        return apps.stream()
            .filter(this::isArchived)
            .toList();
    }

    private List<LoanApplication> filterLoanArchives(List<LoanApplication> archives,
                                                     String loanArchiveQuery,
                                                     String loanArchiveFilter) {
        String normalizedQuery = safeArchiveQuery(loanArchiveQuery).toLowerCase(Locale.ROOT);
        String normalizedFilter = safeArchiveFilter(loanArchiveFilter);
        return archives.stream()
            .filter(app -> normalizedQuery.isBlank()
                || (app.getLoanId() != null
                    && app.getLoanId().toLowerCase(Locale.ROOT).contains(normalizedQuery)))
            .filter(app -> switch (normalizedFilter) {
                case "DISBURSED" -> app.getStatus() == LoanStatus.FINAL_APPROVED;
                case "DEFAULTED" -> app.getStatus() == LoanStatus.DEFAULTED;
                case "PAID" -> app.getStatus() == LoanStatus.PAID;
                case "FORFEITED" -> app.getStatus() == LoanStatus.FORFEITED;
                case "REJECTED" -> isRejectedStatus(app.getStatus());
                default -> true;
            })
            .toList();
    }

    private List<GuarantorRequest> filterGuarantorArchives(List<GuarantorRequest> archives,
                                                           String guarantorArchiveQuery,
                                                           String guarantorArchiveFilter) {
        String normalizedQuery = safeArchiveQuery(guarantorArchiveQuery).toLowerCase(Locale.ROOT);
        String normalizedFilter = safeArchiveFilter(guarantorArchiveFilter);
        return archives.stream()
            .filter(request -> normalizedQuery.isBlank()
                || request.getLoanApplicationId().toString().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || request.getLoanApplicationId().toString().substring(0, 8).toLowerCase(Locale.ROOT).contains(normalizedQuery))
            .filter(request -> {
                if ("ALL".equals(normalizedFilter)) {
                    return true;
                }
                try {
                    return request.getStatus() == GuarantorRequestStatus.valueOf(normalizedFilter);
                } catch (IllegalArgumentException ex) {
                    return true;
                }
            })
            .toList();
    }

    private List<LoanApplication> activeRepaymentLoans(List<LoanApplication> apps) {
        return apps.stream()
            .filter(this::isActiveRepaymentLoan)
            .toList();
    }

    private boolean isActiveRepaymentLoan(LoanApplication app) {
        return app != null
            && (app.getStatus() == LoanStatus.FINAL_APPROVED || app.getStatus() == LoanStatus.DEFAULTED);
    }

    private String repaymentStateLabel(LoanApplication app,
                                       LoanPresentationService.LoanPaymentSummaryView paymentSummary,
                                       LocalDate today) {
        if (app.getStatus() == LoanStatus.PAID) {
            return "Paid";
        }
        if (app.getStatus() == LoanStatus.DEFAULTED) {
            return "Defaulted";
        }
        if (app.getFinalDueDate() != null && app.getFinalDueDate().isBefore(today)) {
            if (paymentSummary.available()
                && paymentSummary.totalOutstanding() != null
                && paymentSummary.totalOutstanding().compareTo(BigDecimal.ZERO) > 0) {
                return "Defaulted";
            }
            return "Overdue";
        }
        return "Active";
    }

    private String repaymentStateClasses(LoanApplication app,
                                         LoanPresentationService.LoanPaymentSummaryView paymentSummary,
                                         LocalDate today) {
        String state = repaymentStateLabel(app, paymentSummary, today);
        return switch (state) {
            case "Paid" -> "border-emerald-200 bg-emerald-50 text-emerald-700";
            case "Defaulted" -> "border-rose-200 bg-rose-50 text-rose-700";
            case "Overdue" -> "border-amber-200 bg-amber-50 text-amber-700";
            default -> "border-slate-200 bg-slate-50 text-slate-700";
        };
    }

    private Set<UUID> dismissedActiveLoanChartIds(UUID memberId) {
        if (memberId == null) {
            return Collections.emptySet();
        }
        return userSettingsRepository.findById(memberId)
            .map(UserSettings::getNotificationPrefs)
            .map(this::parsePrefs)
            .map(prefs -> parseUuidSet(prefs.get(DISMISSED_ACTIVE_LOAN_CHARTS_KEY)))
            .orElse(Collections.emptySet());
    }

    private void dismissActiveLoanChart(UUID memberId, UUID loanId) {
        OffsetDateTime now = OffsetDateTime.now();
        UserSettings settings = userSettingsRepository.findById(memberId)
            .orElseGet(() -> UserSettings.builder()
                .memberId(memberId)
                .language("en")
                .notificationPrefs("{}")
                .createdAt(now)
                .updatedAt(now)
                .build());
        Map<String, Object> prefs = parsePrefs(settings.getNotificationPrefs());
        LinkedHashSet<String> dismissedCharts = parseUuidSet(prefs.get(DISMISSED_ACTIVE_LOAN_CHARTS_KEY)).stream()
            .map(UUID::toString)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        dismissedCharts.add(loanId.toString());
        prefs.put(DISMISSED_ACTIVE_LOAN_CHARTS_KEY, new ArrayList<>(dismissedCharts));
        settings.setNotificationPrefs(writePrefs(prefs));
        if (settings.getCreatedAt() == null) {
            settings.setCreatedAt(now);
        }
        settings.setUpdatedAt(now);
        userSettingsRepository.save(settings);
    }

    private String normalizeMemberLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "en";
        }
        return switch (language.trim().toLowerCase(Locale.ROOT)) {
            case "sw", "swahili" -> "sw";
            default -> "en";
        };
    }

    private List<GuarantorRequest> activeGuarantorRequests(List<GuarantorRequest> requests) {
        Map<UUID, LoanApplication> applicationById = loanApplicationsById(requests);
        return requests.stream()
            .filter(request -> request.getStatus() == GuarantorRequestStatus.PENDING
                || (request.getStatus() == GuarantorRequestStatus.APPROVED
                    && isGuarantorRemovalStageOpen(applicationById.get(request.getLoanApplicationId()))
                    && isWithinReversalWindow(request.getDecidedAt())))
            .toList();
    }

    private List<GuarantorRequest> visibleGuarantorRequests(List<GuarantorRequest> requests) {
        Set<UUID> loanIds = requests.stream()
            .map(GuarantorRequest::getLoanApplicationId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (loanIds.isEmpty()) {
            return requests;
        }
        Set<UUID> supersededLoanIds = loanApplicationRepository.findByTopUpSourceLoanIdIn(loanIds).stream()
            .filter(app -> app.getStatus() != LoanStatus.DRAFT)
            .map(LoanApplication::getTopUpSourceLoanId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (supersededLoanIds.isEmpty()) {
            return requests;
        }
        return requests.stream()
            .filter(request -> !supersededLoanIds.contains(request.getLoanApplicationId()))
            .toList();
    }

    private List<GuarantorRequest> archivedGuarantorRequests(List<GuarantorRequest> requests) {
        Map<UUID, LoanApplication> applicationById = loanApplicationsById(requests);
        return requests.stream()
            .filter(request -> request.getStatus() == GuarantorRequestStatus.REJECTED
                || request.getStatus() == GuarantorRequestStatus.EXPIRED
                || (request.getStatus() == GuarantorRequestStatus.APPROVED
                    && (!isGuarantorRemovalStageOpen(applicationById.get(request.getLoanApplicationId()))
                        || !isWithinReversalWindow(request.getDecidedAt()))))
            .toList();
    }

    private Map<UUID, LoanApplication> loanApplicationsById(List<GuarantorRequest> requests) {
        Set<UUID> applicationIds = requests.stream()
            .map(GuarantorRequest::getLoanApplicationId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (applicationIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, LoanApplication> applicationById = new HashMap<>();
        for (LoanApplication application : loanApplicationRepository.findAllById(applicationIds)) {
            applicationById.put(application.getId(), application);
        }
        return applicationById;
    }

    private boolean isGuarantorRemovalStageOpen(LoanApplication application) {
        return application != null
            && (application.getStatus() == LoanStatus.AWAITING_GUARANTORS
                || application.getStatus() == LoanStatus.ALL_GUARANTORS_APPROVED);
    }

    private boolean isPendingApplication(LoanApplication app) {
        return !isRejectedStatus(app.getStatus())
            && app.getStatus() != LoanStatus.FORFEITED
            && app.getStatus() != LoanStatus.FINAL_APPROVED
            && app.getStatus() != LoanStatus.DEFAULTED
            && app.getStatus() != LoanStatus.PAID;
    }

    private boolean isAwaitingDecisionStage(LoanApplication app) {
        return app.getStatus() == LoanStatus.AWAITING_GUARANTORS
            || app.getStatus() == LoanStatus.ALL_GUARANTORS_APPROVED
            || app.getStatus() == LoanStatus.READY_FOR_MANAGER
            || app.getStatus() == LoanStatus.AWAITING_BOARD;
    }

    private boolean isArchived(LoanApplication app) {
        return isRejectedStatus(app.getStatus())
            || app.getStatus() == LoanStatus.FORFEITED
            || app.getStatus() == LoanStatus.FINAL_APPROVED
            || app.getStatus() == LoanStatus.DEFAULTED
            || app.getStatus() == LoanStatus.PAID;
    }

    private boolean isWithinReversalWindow(OffsetDateTime referenceAt) {
        return referenceAt != null && referenceAt.plusHours(REVERSAL_WINDOW_HOURS).isAfter(OffsetDateTime.now());
    }

    private String formatReversalWindowExpiry(OffsetDateTime referenceAt) {
        return referenceAt.plusHours(REVERSAL_WINDOW_HOURS).format(REVERSAL_WINDOW_FORMATTER);
    }

    private boolean hasRepaymentTimeframeEnded(LoanApplication app) {
        return app != null && app.getFinalDueDate() != null && !app.getFinalDueDate().isAfter(LocalDate.now());
    }

    private String normalizeArchiveSection(String section) {
        return "guarantors".equalsIgnoreCase(section) ? "guarantors" : "loans";
    }

    private String safeArchiveQuery(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeArchiveFilter(String value) {
        return value == null || value.isBlank() ? "ALL" : value.trim().toUpperCase(Locale.ROOT);
    }

    private Set<UUID> parseUuidSet(Object rawValue) {
        if (!(rawValue instanceof List<?> values)) {
            return Collections.emptySet();
        }
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            try {
                result.add(UUID.fromString(String.valueOf(value)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    private Map<String, Object> parsePrefs(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private String writePrefs(Map<String, Object> prefs) {
        try {
            return objectMapper.writeValueAsString(prefs == null ? Collections.emptyMap() : prefs);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private boolean isRejectedStatus(LoanStatus status) {
        return status == LoanStatus.MANAGER_REJECTED
            || status == LoanStatus.BOARD_REJECTED
            || status == LoanStatus.FINAL_REJECTED;
    }

    private Map<String, Object> externalAccountStatusPayload(ExternalAccountStatusService.ExternalAccountStatusView status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("available", status.isAvailable());
        payload.put("pending", status.isPending());
        payload.put("savingsLabel", status.getSavingsLabel());
        payload.put("sharesLabel", status.getSharesLabel());
        payload.put("statusMessage", status.getStatusMessage());
        return payload;
    }

    private boolean isStatusChartIncluded(LoanStatus status) {
        return status == LoanStatus.DRAFT
            || status == LoanStatus.AWAITING_GUARANTORS
            || status == LoanStatus.ALL_GUARANTORS_APPROVED
            || status == LoanStatus.READY_FOR_MANAGER
            || status == LoanStatus.AWAITING_BOARD
            || status == LoanStatus.FINAL_APPROVED
            || status == LoanStatus.DEFAULTED;
    }

    private String dashboardStatusLabel(LoanStatus status) {
        if (status == LoanStatus.DRAFT) {
            return "Draft";
        }
        if (status == LoanStatus.SUBMITTED) {
            return "Submitted";
        }
        if (status == LoanStatus.AWAITING_GUARANTORS) {
            return "Awaiting Guarantors";
        }
        if (status == LoanStatus.ALL_GUARANTORS_APPROVED) {
            return "All Guarantors Approved";
        }
        if (status == LoanStatus.READY_FOR_MANAGER) {
            return "On Review By Manager";
        }
        if (status == LoanStatus.MANAGER_ACCEPTED) {
            return "Manager Approved";
        }
        if (status == LoanStatus.AWAITING_LOAN_OFFICER) {
            return "On Review By Loan Officer";
        }
        if (status == LoanStatus.LOAN_OFFICER_APPROVED) {
            return "Loan Officer Approved";
        }
        if (status == LoanStatus.LOAN_OFFICER_REJECTED) {
            return "Loan Officer Rejected";
        }
        if (status == LoanStatus.AWAITING_BOARD) {
            return "On Review By Board";
        }
        if (status == LoanStatus.BOARD_APPROVED) {
            return "Board Approved";
        }
        if (status == LoanStatus.BOARD_REJECTED) {
            return "Board Rejected";
        }
        if (status == LoanStatus.AWAITING_ACCOUNTANT) {
            return "On Review By Accountant";
        }
        if (status == LoanStatus.ACCOUNTANT_APPROVED) {
            return "Accountant Approved";
        }
        if (status == LoanStatus.ACCOUNTANT_REJECTED) {
            return "Accountant Rejected";
        }
        if (status == LoanStatus.READY_FOR_DISBURSEMENT) {
            return "Ready for Disbursement";
        }
        if (status == LoanStatus.FINAL_APPROVED) {
            return "Disbursed Loan";
        }
        if (status == LoanStatus.FINAL_REJECTED) {
            return "Final Rejected";
        }
        if (status == LoanStatus.FORFEITED) {
            return "Forfeited";
        }
        if (status == LoanStatus.DEFAULTED) {
            return "Defaulted / Not Paid";
        }
        if (status == LoanStatus.PAID) {
            return "Paid";
        }
        return status.name().replace('_', ' ');
    }

    private String dashboardStatusColor(LoanStatus status) {
        if (status == LoanStatus.DRAFT) {
            return "#60A5FA";
        }
        if (status == LoanStatus.AWAITING_GUARANTORS) {
            return "#F59E0B";
        }
        if (status == LoanStatus.ALL_GUARANTORS_APPROVED) {
            return "#0F766E";
        }
        if (status == LoanStatus.READY_FOR_MANAGER) {
            return "#14B8A6";
        }
        if (status == LoanStatus.AWAITING_BOARD) {
            return "#6366F1";
        }
        if (status == LoanStatus.FINAL_APPROVED) {
            return "#22C55E";
        }
        if (status == LoanStatus.DEFAULTED) {
            return "#DC2626";
        }
        if (status == LoanStatus.PAID) {
            return "#0F766E";
        }
        if (status == LoanStatus.FORFEITED) {
            return "#F43F5E";
        }
        return "#94A3B8";
    }
}
