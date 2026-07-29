package com.sacco.mvp.web;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.config.MemberLocaleInterceptor;
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
import com.sacco.mvp.service.LoanProductDisplayService;
import com.sacco.mvp.service.LoanQualificationPolicyService;
import com.sacco.mvp.service.LoanReportService;
import com.sacco.mvp.service.LoanProductRequiredAttachmentService;
import com.sacco.mvp.service.LoanProductWorkflowService;
import com.sacco.mvp.service.NotificationInboxService;
import com.sacco.mvp.service.PaymentDetailsService;
import com.sacco.mvp.service.ReversalRequestService;
import com.sacco.mvp.service.StationOtpSettingsService;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import com.sacco.mvp.service.dto.FormModel;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
    private static final int MEMBER_ARCHIVE_PAGE_SIZE = 50;
    private static final List<LoanStatus> ARCHIVED_LOAN_STATUSES = List.of(
        LoanStatus.MANAGER_REJECTED,
        LoanStatus.LOAN_OFFICER_REJECTED,
        LoanStatus.CHAIRPERSON_REJECTED,
        LoanStatus.BOARD_REJECTED,
        LoanStatus.CREDIT_COMMITTEE_REJECTED,
        LoanStatus.ACCOUNTANT_REJECTED,
        LoanStatus.REJECTED,
        LoanStatus.DISBURSED,
        LoanStatus.DEFAULTED,
        LoanStatus.PAID
    );
    private static final DateTimeFormatter REVERSAL_WINDOW_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);
    private static final String DISMISSED_ACTIVE_LOAN_CHARTS_KEY = "dismissedActiveLoanCharts";
    private static final String GUARANTOR_APPROVAL_MODE_FIELD = "guarantorApprovalMode";
    private static final String GUARANTOR_APPROVAL_MODE_LOGIN = "LOGIN";
    private static final String GUARANTOR_APPROVAL_MODE_DIRECT_OTP = "DIRECT_OTP";
    private final LoanWorkflowService loanWorkflowService;
    private final LoanAnalyticsService loanAnalyticsService;
    private final LoanQualificationPolicyService loanQualificationPolicyService;
    private final FormSchemaService formSchemaService;
    private final EligibilityService eligibilityService;
    private final FinancialDetailsService financialDetailsService;
    private final LoanPresentationService loanPresentationService;
    private final LoanProductDisplayService loanProductDisplayService;
    private final LoanReportService loanReportService;
    private final LoanProductRequiredAttachmentService requiredAttachmentService;
    private final LoanProductWorkflowService loanProductWorkflowService;
    private final AdminService adminService;
    private final EmailOtpService emailOtpService;
    private final ExternalAccountStatusService externalAccountStatusService;
    private final NotificationInboxService notificationInboxService;
    private final ReversalRequestService reversalRequestService;
    private final LoanAttachmentService loanAttachmentService;
    private final PaymentDetailsService paymentDetailsService;
    private final MemberRepository memberRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanProductSettingRepository loanProductSettingRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final ManagerReviewRepository managerReviewRepository;
    private final BoardReviewRepository boardReviewRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final ForesightDirectoryService foresightDirectoryService;
    private final ObjectMapper objectMapper;
    private final MemberLocaleInterceptor memberLocaleInterceptor;
    private final MessageSource messageSource;
    private final StationOtpSettingsService stationOtpSettingsService;

    @GetMapping("/dashboard")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_VIEW')")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        LoanWorkflowService.MemberDashboardData dashboard = loanWorkflowService.memberDashboard(principal.getMemberId());
        Map<LoanStatus, Long> statusCounts = dashboard.statusCounts();
        List<LoanApplication> activeLoans = dashboard.activeLoans();
        long archivedApplicationCount = statusCounts.entrySet().stream()
            .filter(entry -> isArchivedStatus(entry.getKey()))
            .mapToLong(Map.Entry::getValue)
            .sum();
        long currentApplicationCount = statusCounts.values().stream().mapToLong(Long::longValue).sum()
            - archivedApplicationCount
            + dashboard.unacknowledgedDisbursedApplicationCount()
            + dashboard.unacknowledgedRejectedApplicationCount();
        long rejectedLoans = statusCounts.entrySet().stream()
            .filter(entry -> isRejectedStatus(entry.getKey()))
            .mapToLong(Map.Entry::getValue)
            .sum();
        long loansAwaitingDecision = statusCounts.entrySet().stream()
            .filter(entry -> isAwaitingDecisionStatus(entry.getKey()))
            .mapToLong(Map.Entry::getValue)
            .sum();
        Map<LoanStatus, Long> statusChartCounts = statusCounts.entrySet().stream()
            .filter(entry -> !isArchivedStatus(entry.getKey()) && isStatusChartIncluded(entry.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        LoanApplication currentWorkflowApplication = dashboard.latestCurrentApplication();
        Set<UUID> dismissedActiveLoanChartIds = dismissedActiveLoanChartIds(principal.getMemberId());
        Map<LoanType, String> loanProductNames = loanProductNames(principal.getSaccoId());
        List<Map<String, Object>> activeLoanChartRows = buildActiveLoanChartRows(activeLoans, dismissedActiveLoanChartIds, loanProductNames);

        model.addAttribute("totalApplications", currentApplicationCount);
        model.addAttribute("currentApplicationCount", currentApplicationCount);
        model.addAttribute("activeLoanCount", activeLoans.size());
        model.addAttribute("activeLoanChartCount", activeLoanChartRows.size());
        model.addAttribute("rejectedLoanCount", rejectedLoans);
        model.addAttribute("pendingGuaranteeApprovals", dashboard.pendingGuaranteeCount());
        model.addAttribute("loansAwaitingDecision", loansAwaitingDecision);
        model.addAttribute("statusChartRows", buildStatusChartRows(statusChartCounts));
        model.addAttribute("activeLoanChartRows", activeLoanChartRows);
        model.addAttribute("archivedApplicationCount", archivedApplicationCount);
        model.addAttribute("dashboardExternalAccountStatus", externalAccountStatusService.loading(message("loan.loadingLiveBalances")));
        model.addAttribute("currentWorkflowApplication", currentWorkflowApplication);
        model.addAttribute("currentWorkflowRejectionAcknowledgementRequired",
            requiresRejectionAcknowledgement(currentWorkflowApplication));
        model.addAttribute("currentWorkflowApplicationNumber",
            currentWorkflowApplication == null || currentWorkflowApplication.getApplicationNumber() == null
                ? "-"
                : String.valueOf(currentWorkflowApplication.getApplicationNumber()));
        model.addAttribute("currentWorkflowAmountLabel",
            currentWorkflowApplication == null
                ? "-"
                : loanPresentationService.formatMoneyDisplay(currentWorkflowApplication.getAmount()));
        model.addAttribute("currentWorkflowProductName",
            currentWorkflowApplication == null
                ? "-"
                : loanProductName(currentWorkflowApplication, loanProductNames));
        model.addAttribute("currentWorkflowApplicantReason",
            currentWorkflowApplication == null
                ? ""
                : applicantReason(currentWorkflowApplication));
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
        return "app/dashboard";
    }

    @GetMapping("/dashboard/external-account-status")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_VIEW')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> dashboardExternalAccountStatus(@AuthenticationPrincipal AppUserPrincipal principal) {
        Member member = memberRepository.findById(principal.getMemberId()).orElse(null);
        return ResponseEntity.ok(externalAccountStatusPayload(externalAccountStatusService.resolve(member)));
    }

    @PostMapping("/dashboard/active-loans/{loanId}/seen")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_UPDATE') and @authz.isLoanOwner(#loanId, principal)")
    public String markExpiredActiveLoanChartSeen(@AuthenticationPrincipal AppUserPrincipal principal,
                                                 @PathVariable UUID loanId,
                                                 RedirectAttributes ra) {
        LoanApplication app = loanApplicationRepository.findByIdAndApplicantMemberId(loanId, principal.getMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Loan not found"));
        if (app.getStatus() != LoanStatus.DISBURSED && app.getStatus() != LoanStatus.DEFAULTED) {
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
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_CREATE')")
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
        List<LoanApplication> activeDisbursedLoans = loanWorkflowService.findActiveDisbursedLoans(principal.getMemberId());
        if (!activeDisbursedLoans.isEmpty()) {
            model.addAttribute("activeDisbursedLoanApp", activeDisbursedLoans.get(0));
            model.addAttribute("activeLoanAwarenessMessage", loanWorkflowService.activeLoanAwarenessMessage(activeDisbursedLoans));
        }
        return "app/loan-products";
    }

    @GetMapping("/loan-applications")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_VIEW')")
    public String listMyApps(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        LoanWorkflowService.MemberApplicationListData applications = loanWorkflowService.memberApplicationList(principal.getMemberId());
        List<LoanApplication> apps = applications.currentApplications();
        LoanApplication currentWorkflowApplication = applications.latestCurrentApplication();
        model.addAttribute("apps", apps);
        model.addAttribute("loanProductNames", loanProductDisplayService.namesForSacco(principal.getSaccoId()));
        model.addAttribute("loanProductNamesById", loanProductNamesById(principal.getSaccoId()));
        model.addAttribute("currentWorkflowApplication", currentWorkflowApplication);
        model.addAttribute("currentWorkflowSteps", currentWorkflowApplication == null
            ? List.of()
            : buildDashboardWorkflowSteps(currentWorkflowApplication));
        model.addAttribute("managerReasons", loanPresentationService.rejectionFeedbackReasons(apps));
        model.addAttribute("rejectionAcknowledgementRequiredById", rejectionAcknowledgementRequiredById(apps));
        model.addAttribute("archiveCount", applications.archiveCount());
        return "app/loan-applications";
    }

    @PostMapping("/loan-applications/{id}/acknowledge-disbursement")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_UPDATE') and @authz.isLoanOwner(#id, principal)")
    public String acknowledgeDisbursement(@AuthenticationPrincipal AppUserPrincipal principal,
                                          @PathVariable UUID id,
                                          @RequestParam(defaultValue = "applications") String returnTo,
                                          RedirectAttributes ra) {
        try {
            loanWorkflowService.acknowledgeDisbursement(id, principal.getMemberId());
            ra.addFlashAttribute("message", "Disbursement update acknowledged.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return switch (returnTo) {
            case "dashboard" -> "redirect:/app/dashboard";
            case "detail" -> "redirect:/app/loan-applications/" + id;
            default -> "redirect:/app/loan-applications";
        };
    }

    @PostMapping("/loan-applications/{id}/acknowledge-rejection")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_UPDATE') and @authz.isLoanOwner(#id, principal)")
    public String acknowledgeRejection(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @PathVariable UUID id,
                                       @RequestParam(defaultValue = "applications") String returnTo,
                                       RedirectAttributes ra) {
        try {
            loanWorkflowService.acknowledgeRejection(id, principal.getMemberId());
            ra.addFlashAttribute("message", "Rejection decision acknowledged.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return switch (returnTo) {
            case "dashboard" -> "redirect:/app/dashboard";
            case "detail" -> "redirect:/app/loan-applications/" + id;
            default -> "redirect:/app/loan-applications";
        };
    }

    @GetMapping("/archives")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.hasAny(principal, 'MEMBER_LOANS_VIEW', 'GUARANTOR_REQUESTS_VIEW')")
    public String archives(@AuthenticationPrincipal AppUserPrincipal principal,
                           @RequestParam(required = false, defaultValue = "loans") String section,
                           @RequestParam(required = false) String loanArchiveQuery,
                           @RequestParam(required = false, defaultValue = "ALL") String loanArchiveFilter,
                           @RequestParam(required = false) String guarantorArchiveQuery,
                           @RequestParam(required = false, defaultValue = "ALL") String guarantorArchiveFilter,
                           @RequestParam(required = false, defaultValue = "0") int page,
                           Model model) {
        String archiveSection = normalizeArchiveSection(section);
        int safePage = Math.max(page, 0);
        PageRequest pageRequest = PageRequest.of(safePage, MEMBER_ARCHIVE_PAGE_SIZE);
        Page<LoanApplication> loanArchivePage = Page.empty(pageRequest);
        Page<GuarantorRequest> guarantorArchivePage = Page.empty(pageRequest);
        if ("guarantors".equals(archiveSection)) {
            guarantorArchivePage = guarantorRequestRepository.findArchivePageByGuarantorMemberId(
                principal.getMemberId(),
                OffsetDateTime.now().minusHours(REVERSAL_WINDOW_HOURS),
                safeGuarantorArchiveStatus(guarantorArchiveFilter),
                normalizedArchiveQuery(guarantorArchiveQuery),
                pageRequest
            );
        } else {
            loanArchivePage = loanApplicationRepository.findMemberArchivePage(
                principal.getMemberId(),
                loanArchiveStatuses(loanArchiveFilter),
                normalizedArchiveQuery(loanArchiveQuery),
                pageRequest
            );
        }
        List<LoanApplication> archives = loanArchivePage.getContent();
        List<GuarantorRequest> guarantorArchives = guarantorArchivePage.getContent();
        model.addAttribute("archives", archives);
        model.addAttribute("loanProductNames", loanProductDisplayService.namesForSacco(principal.getSaccoId()));
        model.addAttribute("loanProductNamesById", loanProductNamesById(principal.getSaccoId()));
        model.addAttribute("guarantorArchives", guarantorArchives);
        model.addAttribute("archiveSection", archiveSection);
        model.addAttribute("archivePage", "guarantors".equals(archiveSection) ? guarantorArchivePage : loanArchivePage);
        model.addAttribute("loanArchiveQuery", safeArchiveQuery(loanArchiveQuery));
        model.addAttribute("loanArchiveFilter", safeArchiveFilter(loanArchiveFilter));
        model.addAttribute("guarantorArchiveQuery", safeArchiveQuery(guarantorArchiveQuery));
        model.addAttribute("guarantorArchiveFilter", safeArchiveFilter(guarantorArchiveFilter));
        addGuaranteeContext(guarantorArchives, model);
        return "app/archives";
    }

    @GetMapping("/reports")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'LOAN_REPORTS_VIEW')")
    public String reports(@AuthenticationPrincipal AppUserPrincipal principal,
                          @RequestParam(required = false) LocalDate fromDate,
                          @RequestParam(required = false) LocalDate toDate,
                          @RequestParam(required = false) UUID loanProductId,
                          @RequestParam(required = false) LoanType loanType,
                          Model model) {
        LocalDate resolvedTo = toDate == null ? LocalDate.now() : toDate;
        LocalDate resolvedFrom = fromDate == null ? resolvedTo.minusYears(1) : fromDate;
        if (resolvedFrom.isAfter(resolvedTo)) {
            LocalDate swap = resolvedFrom;
            resolvedFrom = resolvedTo;
            resolvedTo = swap;
        }
        LoanProductSetting selectedProduct = selectedAnalyticsProduct(principal.getSaccoId(), loanProductId, loanType);
        LoanType resolvedLoanType = selectedProduct == null ? loanType : selectedProduct.getLoanType();
        UUID resolvedLoanProductId = selectedProduct == null ? null : selectedProduct.getId();
        LoanAnalyticsService.MemberLoanAnalytics analytics =
            loanAnalyticsService.forMember(principal.getMemberId(), resolvedFrom, resolvedTo, resolvedLoanType, resolvedLoanProductId, null);
        List<LoanAnalyticsService.MetricTrendSeries> trendSeries =
            loanAnalyticsService.statusTrendForMember(principal.getMemberId(), resolvedFrom, resolvedTo, resolvedLoanType, resolvedLoanProductId, null);
        LoanQualificationPolicyService.EligibilitySummary eligibilitySummary =
            loanQualificationPolicyService.eligibilitySummary(principal.getSaccoId(), principal.getMemberId());
        model.addAttribute("analytics", analytics);
        model.addAttribute("metricCards", memberMetricCards(analytics));
        model.addAttribute("fromDate", StrictAnalyticsLocalDateEditor.format(resolvedFrom));
        model.addAttribute("toDate", StrictAnalyticsLocalDateEditor.format(resolvedTo));
        model.addAttribute("loanProductId", resolvedLoanProductId);
        model.addAttribute("loanType", resolvedLoanType);
        model.addAttribute("loanProducts", loanWorkflowService.listProducts(principal.getSaccoId()).stream()
            .filter(product -> product.getLoanType() != null)
            .toList());
        model.addAttribute("trendSeriesJson", toJson(trendSeries));
        model.addAttribute("activeLoanDetails", loanReportService.memberActiveLoanDetails(
            principal.getMemberId(), resolvedFrom, resolvedTo, resolvedLoanType));
        model.addAttribute("eligibilitySummary", eligibilitySummary);
        model.addAttribute("canApply", eligibilitySummary.canApply());
        model.addAttribute("canGuarantee", eligibilitySummary.canGuarantee());
        model.addAttribute("riskNote", eligibilitySummary.reason());
        return "app/reports";
    }

    private LoanProductSetting selectedAnalyticsProduct(String saccoId, UUID loanProductId, LoanType fallbackLoanType) {
        if (loanProductId != null) {
            return loanProductSettingRepository.findByIdAndSaccoIdAndActiveTrue(loanProductId, saccoId)
                .filter(LoanProductSetting::isAvailableForApplications)
                .orElse(null);
        }
        if (fallbackLoanType == null) {
            return null;
        }
        return loanProductSettingRepository.findBySaccoIdAndActiveTrue(saccoId).stream()
            .filter(product -> product.getLoanType() == fallbackLoanType)
            .filter(LoanProductSetting::isAvailableForApplications)
            .sorted(Comparator.comparingInt(LoanProductSetting::getResolvedDisplayOrder))
            .findFirst()
            .orElse(null);
    }

    private List<MemberMetricCard> memberMetricCards(LoanAnalyticsService.MemberLoanAnalytics analytics) {
        return List.of(
            new MemberMetricCard("applied", "Applied Loans", analytics.appliedLoans(), "blue", "Applied"),
            new MemberMetricCard("active", "Active Loans", analytics.activeLoans(), "emerald", "Applied"),
            new MemberMetricCard("disbursed", "Disbursed Loans", analytics.disbursedLoans(), "violet", "Disbursed"),
            new MemberMetricCard("paid", "Paid Loans", analytics.paidLoans(), "green", "Paid"),
            new MemberMetricCard("defaulted", "Defaulted Loans", analytics.defaultedLoans(), "orange", "Defaulted"),
            new MemberMetricCard("rejected", "Rejected Loans", analytics.rejectedLoans(), "slate", "Rejected")
        );
    }

    public record MemberMetricCard(String key, String label, long value, String tone, String sparkName) {
        public String getKey() { return key; }
        public String getLabel() { return label; }
        public long getValue() { return value; }
        public String getTone() { return tone; }
        public String getSparkName() { return sparkName; }
    }

    @InitBinder
    void bindAnalyticsDates(WebDataBinder binder) {
        binder.registerCustomEditor(LocalDate.class, new StrictAnalyticsLocalDateEditor());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<Map<String, Object>> buildActiveLoanChartRows(List<LoanApplication> activeLoans,
                                                               Set<UUID> dismissedChartIds,
                                                               Map<LoanType, String> loanProductNames) {
        LocalDate today = LocalDate.now();
        return activeLoans.stream()
            .sorted(Comparator.comparing(LoanApplication::getFinalDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .filter(app -> !hasRepaymentTimeframeEnded(app) || !dismissedChartIds.contains(app.getId()))
            .map(app -> {
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
                row.put("loanId", app.getLoanId() == null || app.getLoanId().isBlank() ? "-" : app.getLoanId());
                row.put("loanProductName", loanProductName(app, loanProductNames));
                row.put("applicantReason", applicantReason(app));
                row.put("amountLabel", loanPresentationService.formatMoneyDisplay(app.getAmount()));
                row.put("disbursementDate", app.getDisbursementDate() == null ? "-" : app.getDisbursementDate());
                row.put("startDate", resolveRepaymentTimerStartDate(app));
                row.put("daysLeft", daysLeft);
                row.put("elapsedDays", elapsedDays);
                row.put("totalDays", totalDays);
                row.put("remainingPercent", remainingPercent);
                row.put("finalDueDate", app.getFinalDueDate());
                row.put("countdown", loanPresentationService.countdownLabel(app.getFinalDueDate()));
                row.put("canDismiss", hasRepaymentTimeframeEnded(app));
                row.put("repaymentStateCode", app.getStatus().name());
                row.put("repaymentStateLabel", repaymentStateLabel(app, today));
                row.put("repaymentStateClasses", repaymentStateClasses(app, today));
                row.put("loanDescription", loanProductName(app, loanProductNames));
                row.put("lastPaymentDate", "-");
                row.put("totalOutstanding", app.getStatus() == LoanStatus.PAID ? formatTzs(BigDecimal.ZERO) : loanPresentationService.formatMoneyDisplay(app.getAmount()));
                row.put("paidAmount", app.getStatus() == LoanStatus.PAID ? loanPresentationService.formatMoneyDisplay(app.getAmount()) : formatTzs(BigDecimal.ZERO));
                row.put("currentBalance", app.getStatus() == LoanStatus.PAID ? formatTzs(BigDecimal.ZERO) : loanPresentationService.formatMoneyDisplay(app.getAmount()));
                row.put("outstandingPrincipal", app.getStatus() == LoanStatus.PAID ? formatTzs(BigDecimal.ZERO) : loanPresentationService.formatMoneyDisplay(app.getAmount()));
                row.put("outstandingInterest", "-");
                row.put("totalPrincipalPaid", app.getStatus() == LoanStatus.PAID ? loanPresentationService.formatMoneyDisplay(app.getAmount()) : formatTzs(BigDecimal.ZERO));
                row.put("totalInterestPaid", "-");
                return row;
            })
            .toList();
    }

    private Map<LoanType, String> loanProductNames(String saccoId) {
        return loanProductSettingRepository.findBySaccoIdAndActiveTrue(saccoId).stream()
            .filter(LoanProductSetting::isAvailableForApplications)
            .sorted(Comparator.comparingInt(LoanProductSetting::getResolvedDisplayOrder))
            .collect(Collectors.toMap(
                LoanProductSetting::getLoanType,
                LoanProductSetting::getDisplayName,
                (first, ignored) -> first,
                LinkedHashMap::new
            ));
    }

    private Map<UUID, String> loanProductNamesById(String saccoId) {
        return loanProductSettingRepository.findBySaccoIdAndActiveTrue(saccoId).stream()
            .filter(LoanProductSetting::isAvailableForApplications)
            .sorted(Comparator.comparingInt(LoanProductSetting::getResolvedDisplayOrder))
            .collect(Collectors.toMap(
                LoanProductSetting::getId,
                LoanProductSetting::getDisplayName,
                (first, ignored) -> first,
                LinkedHashMap::new
            ));
    }

    private String loanProductName(LoanApplication app, Map<LoanType, String> loanProductNames) {
        if (app == null) {
            return "-";
        }
        if (app.getLoanProductSettingId() != null) {
            String productName = loanProductSettingRepository.findByIdAndSaccoIdAndActiveTrue(app.getLoanProductSettingId(), app.getSaccoId())
                .filter(LoanProductSetting::isAvailableForApplications)
                .map(LoanProductSetting::getDisplayName)
                .orElse(null);
            if (productName != null && !productName.isBlank()) {
                return productName;
            }
        }
        LoanType loanType = app.getLoanType();
        if (loanType == null) {
            return "-";
        }
        String configuredName = loanProductNames.get(loanType);
        if (configuredName != null && !configuredName.isBlank()) {
            return configuredName;
        }
        return loanType.getDisplayLabel();
    }

    private String applicantReason(LoanApplication app) {
        if (app == null || app.getFormData() == null || app.getFormData().isBlank()) {
            return "";
        }
        try {
            Map<String, Object> formData = objectMapper.readValue(app.getFormData(), new TypeReference<>() {});
            Object purpose = formData.get("purpose");
            return purpose == null ? "" : String.valueOf(purpose).trim();
        } catch (Exception ex) {
            return "";
        }
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
            LoanStatus.AWAITING_CREDIT_COMMITTEE,
            LoanStatus.DISBURSED
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
        List<ApprovalWorkflowStage> reviewStages = dashboardReviewStages(app);
        ApprovalWorkflowStage firstReviewStage = reviewStages.isEmpty()
            ? ApprovalWorkflowStage.MANAGER
            : reviewStages.get(0);
        String furtherReviewDetail = dashboardFurtherReviewDetail(reviewStages);
        List<String> labels = List.of(
            "Applicant",
            "Guarantors",
            dashboardReviewerLabel(firstReviewStage),
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
        int currentIndex = dashboardWorkflowStepIndex(app, reviewStages);
        boolean workflowFinished = isDashboardWorkflowFinished(app);
        List<String> stageDates = List.of(
            dashboardApplicantStageDate(app),
            dashboardGuarantorStageDate(app),
            dashboardReviewStageDate(app, firstReviewStage),
            dashboardFurtherReviewStageDate(app, firstReviewStage),
            dashboardReadyForDisbursementDate(app),
            dashboardDisbursedDate(app)
        );
        List<Map<String, Object>> steps = new ArrayList<>();
        boolean workflowRejected = app != null && isRejectedStatus(app.getStatus());
        for (int i = 0; i < labels.size(); i++) {
            String stateKey;
            if (workflowRejected && i == currentIndex) {
                stateKey = "rejected";
            } else if (workflowRejected && i > currentIndex) {
                stateKey = "closed";
            } else if (i < currentIndex || (workflowFinished && i == currentIndex)) {
                stateKey = "completed";
            } else if (i == currentIndex) {
                stateKey = "current";
            } else {
                stateKey = "pending";
            }
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("stepNumber", i + 1);
            step.put("label", labels.get(i));
            step.put("detailLabel", i == 3 ? furtherReviewDetail : "");
            step.put("stateKey", stateKey);
            step.put("stateLabel", dashboardWorkflowStateLabel(i, stateKey, app));
            step.put("metaLabel", dashboardWorkflowMetaLabel(stateKey));
            step.put("metaClasses", workflowMetaClasses(stateKey));
            String dateLabel = "pending".equals(stateKey) || "closed".equals(stateKey) ? "" : stageDates.get(i);
            if ("rejected".equals(stateKey) && (dateLabel == null || dateLabel.isBlank())) {
                dateLabel = formatDashboardWorkflowTimestamp(app.getUpdatedAt());
            }
            step.put("dateLabel", dateLabel);
            step.put("nodeClasses", workflowNodeClasses(stateKey));
            step.put("textClasses", workflowTextClasses(stateKey));
            step.put("numberClasses", workflowNumberClasses(stateKey));
            step.put("iconClasses", workflowIconClasses(stateKey));
            step.put("connectorClasses", workflowConnectorClasses(i, currentIndex, workflowRejected));
            step.put("iconKey", iconKeys.get(i));
            steps.add(step);
        }
        return steps;
    }

    private int dashboardWorkflowStepIndex(LoanApplication app, List<ApprovalWorkflowStage> reviewStages) {
        LoanStatus status = app == null ? null : app.getStatus();
        if (status == null) {
            return 0;
        }
        ApprovalWorkflowStage activeReviewStage = dashboardActiveReviewStage(status);
        if (activeReviewStage != null) {
            return dashboardReviewStepIndex(activeReviewStage, reviewStages);
        }
        ApprovalWorkflowStage approvedReviewStage = dashboardApprovedReviewStage(status);
        if (approvedReviewStage != null) {
            return dashboardStepAfterReviewStage(approvedReviewStage, reviewStages);
        }
        return switch (status) {
            case DRAFT -> 0;
            case SUBMITTED, AWAITING_GUARANTORS -> 1;
            case ALL_GUARANTORS_APPROVED -> 2;
            case READY_FOR_MANAGER, MANAGER_REJECTED,
                AWAITING_LOAN_OFFICER, LOAN_OFFICER_REJECTED,
                AWAITING_CHAIRPERSON, CHAIRPERSON_REJECTED,
                AWAITING_BOARD, BOARD_REJECTED,
                AWAITING_CREDIT_COMMITTEE, CREDIT_COMMITTEE_REJECTED,
                AWAITING_ACCOUNTANT, ACCOUNTANT_REJECTED -> 3;
            case MANAGER_ACCEPTED, LOAN_OFFICER_APPROVED, CHAIRPERSON_APPROVED, BOARD_APPROVED, CREDIT_COMMITTEE_APPROVED, ACCOUNTANT_APPROVED -> 4;
            case READY_FOR_DISBURSEMENT, REJECTED -> 4;
            case DISBURSED, DEFAULTED, PAID -> 5;
        };
    }

    private ApprovalWorkflowStage dashboardActiveReviewStage(LoanStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case READY_FOR_MANAGER, MANAGER_REJECTED -> ApprovalWorkflowStage.MANAGER;
            case AWAITING_LOAN_OFFICER, LOAN_OFFICER_REJECTED -> ApprovalWorkflowStage.LOAN_OFFICER;
            case AWAITING_CHAIRPERSON, CHAIRPERSON_REJECTED -> ApprovalWorkflowStage.CHAIRPERSON;
            case AWAITING_BOARD, BOARD_REJECTED -> ApprovalWorkflowStage.BOARD;
            case AWAITING_CREDIT_COMMITTEE, CREDIT_COMMITTEE_REJECTED -> ApprovalWorkflowStage.CREDIT_COMMITTEE;
            case AWAITING_ACCOUNTANT, ACCOUNTANT_REJECTED -> ApprovalWorkflowStage.ACCOUNTANT;
            default -> null;
        };
    }

    private ApprovalWorkflowStage dashboardApprovedReviewStage(LoanStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case MANAGER_ACCEPTED -> ApprovalWorkflowStage.MANAGER;
            case LOAN_OFFICER_APPROVED -> ApprovalWorkflowStage.LOAN_OFFICER;
            case CHAIRPERSON_APPROVED -> ApprovalWorkflowStage.CHAIRPERSON;
            case BOARD_APPROVED -> ApprovalWorkflowStage.BOARD;
            case CREDIT_COMMITTEE_APPROVED -> ApprovalWorkflowStage.CREDIT_COMMITTEE;
            case ACCOUNTANT_APPROVED -> ApprovalWorkflowStage.ACCOUNTANT;
            default -> null;
        };
    }

    private int dashboardReviewStepIndex(ApprovalWorkflowStage stage, List<ApprovalWorkflowStage> reviewStages) {
        if (stage == null) {
            return 2;
        }
        ApprovalWorkflowStage firstReviewStage = reviewStages == null || reviewStages.isEmpty()
            ? ApprovalWorkflowStage.MANAGER
            : reviewStages.get(0);
        return stage == firstReviewStage ? 2 : 3;
    }

    private int dashboardStepAfterReviewStage(ApprovalWorkflowStage stage, List<ApprovalWorkflowStage> reviewStages) {
        if (reviewStages == null || reviewStages.isEmpty()) {
            return 4;
        }
        int stageIndex = reviewStages.indexOf(stage);
        if (stageIndex < 0) {
            return dashboardReviewStepIndex(stage, reviewStages);
        }
        if (stageIndex + 1 < reviewStages.size()) {
            return dashboardReviewStepIndex(reviewStages.get(stageIndex + 1), reviewStages);
        }
        return 4;
    }

    private List<ApprovalWorkflowStage> dashboardReviewStages(LoanApplication app) {
        LoanProductWorkflowService.WorkflowDefinition workflow = loanProductWorkflowService.resolveForApplication(app);
        return workflow.stages().stream()
            .filter(stage -> stage != ApprovalWorkflowStage.DISBURSEMENT_OFFICER)
            .toList();
    }

    private String dashboardReviewerLabel(ApprovalWorkflowStage stage) {
        return switch (stage) {
            case MANAGER -> "Manager";
            case LOAN_OFFICER -> "Loan Officer";
            case CHAIRPERSON -> "Chairperson";
            case BOARD -> "Board Member";
            case CREDIT_COMMITTEE -> "Credit Committee";
            case ACCOUNTANT -> "Accountant";
            case DISBURSEMENT_OFFICER -> "Disbursement/Teller Officer";
        };
    }

    private String dashboardFurtherReviewDetail(List<ApprovalWorkflowStage> reviewStages) {
        if (reviewStages == null || reviewStages.size() <= 1) {
            return "";
        }
        return reviewStages.stream()
            .skip(1)
            .map(this::dashboardReviewerLabel)
            .distinct()
            .collect(Collectors.joining(", "));
    }

    private String dashboardWorkflowStateLabel(int index, String stateKey, LoanApplication app) {
        if ("rejected".equals(stateKey)) {
            return "Rejected";
        }
        if ("closed".equals(stateKey)) {
            return "Closed";
        }
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
            if (app != null && app.getStatus() == LoanStatus.ALL_GUARANTORS_APPROVED && index == 2) {
                return "Ready for Review";
            }
            if (app != null && dashboardApprovedReviewStage(app.getStatus()) != null && (index == 2 || index == 3)) {
                return "Ready for Review";
            }
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
        if ("rejected".equals(stateKey)) {
            return "Rejected Stage";
        }
        return "current".equals(stateKey) ? "Current Stage" : "";
    }

    private String workflowMetaClasses(String stateKey) {
        return switch (stateKey) {
            case "rejected" -> "member-dashboard-flow-meta--rejected";
            case "current" -> "member-dashboard-flow-meta--current";
            default -> "";
        };
    }

    private String workflowNodeClasses(String stateKey) {
        return switch (stateKey) {
            case "completed" -> "member-dashboard-flow-node--completed";
            case "current" -> "member-dashboard-flow-node--current";
            case "rejected" -> "member-dashboard-flow-node--rejected";
            case "closed" -> "member-dashboard-flow-node--closed";
            default -> "member-dashboard-flow-node--pending";
        };
    }

    private String workflowTextClasses(String stateKey) {
        return switch (stateKey) {
            case "completed" -> "member-dashboard-flow-text--completed";
            case "current" -> "member-dashboard-flow-text--current";
            case "rejected" -> "member-dashboard-flow-text--rejected";
            case "closed" -> "member-dashboard-flow-text--closed";
            case "pending" -> "member-dashboard-flow-text--pending";
            default -> "member-dashboard-flow-text--not-started";
        };
    }

    private String workflowNumberClasses(String stateKey) {
        return switch (stateKey) {
            case "completed" -> "member-dashboard-flow-number--completed";
            case "current" -> "member-dashboard-flow-number--current";
            case "rejected" -> "member-dashboard-flow-number--rejected";
            case "closed" -> "member-dashboard-flow-number--closed";
            default -> "member-dashboard-flow-number--pending";
        };
    }

    private String workflowIconClasses(String stateKey) {
        return switch (stateKey) {
            case "completed" -> "member-dashboard-flow-icon--completed";
            case "current" -> "member-dashboard-flow-icon--current";
            case "rejected" -> "member-dashboard-flow-icon--rejected";
            case "closed" -> "member-dashboard-flow-icon--closed";
            default -> "member-dashboard-flow-icon--pending";
        };
    }

    private String workflowConnectorClasses(int index, int currentIndex, boolean workflowRejected) {
        if (index < currentIndex) {
            return "member-dashboard-flow-connector--completed";
        }
        if (workflowRejected && index >= currentIndex) {
            return "member-dashboard-flow-connector--pending";
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
        return app.getStatus() == LoanStatus.DISBURSED
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

    private String dashboardReviewStageDate(LoanApplication app, ApprovalWorkflowStage stage) {
        if (app == null || stage == null) {
            return "";
        }
        if (stage == ApprovalWorkflowStage.BOARD || stage == ApprovalWorkflowStage.CREDIT_COMMITTEE) {
            return boardReviewRepository.findByLoanApplicationIdAndReviewStage(app.getId(), stage).stream()
                .map(review -> review.getDecidedAt() == null ? review.getCreatedAt() : review.getDecidedAt())
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .map(this::formatDashboardWorkflowTimestamp)
                .orElse("");
        }
        if (stage == ApprovalWorkflowStage.MANAGER
            || stage == ApprovalWorkflowStage.LOAN_OFFICER
            || stage == ApprovalWorkflowStage.ACCOUNTANT) {
            return managerReviewRepository.findByLoanApplicationIdAndReviewStageOrderByCreatedAtAsc(app.getId(), stage).stream()
                .map(ManagerReview::getCreatedAt)
                .filter(Objects::nonNull)
                .findFirst()
                .map(this::formatDashboardWorkflowTimestamp)
                .orElse("");
        }
        return "";
    }

    private String dashboardFurtherReviewStageDate(LoanApplication app, ApprovalWorkflowStage firstReviewStage) {
        if (app == null) {
            return "";
        }
        for (ApprovalWorkflowStage stage : dashboardReviewStages(app)) {
            if (stage == firstReviewStage) {
                continue;
            }
            String dateLabel = dashboardReviewStageDate(app, stage);
            if (dateLabel != null && !dateLabel.isBlank()) {
                return dateLabel;
            }
        }
        return "";
    }

    private String dashboardReadyForDisbursementDate(LoanApplication app) {
        if (app == null) {
            return "";
        }
        if (app.getStatus() == LoanStatus.READY_FOR_DISBURSEMENT
            || app.getStatus() == LoanStatus.DISBURSED
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
        if (app.getStatus() == LoanStatus.DISBURSED || app.getStatus() == LoanStatus.DEFAULTED || app.getStatus() == LoanStatus.PAID) {
            return formatDashboardWorkflowTimestamp(app.getUpdatedAt());
        }
        return "";
    }

    @GetMapping("/loan-applications/new")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_CREATE')")
    public String newApp(@AuthenticationPrincipal AppUserPrincipal principal,
                         @RequestParam(required = false) UUID loanProductId,
                         @RequestParam(required = false) LoanType loanType,
                         @RequestParam(required = false) UUID topUpLoanId,
                         RedirectAttributes ra,
                         Model model) {
        Optional<LoanApplication> blockingApplication = loanWorkflowService.findApplicationInProgress(principal.getMemberId());
        if (blockingApplication.isPresent()) {
            LoanApplication app = blockingApplication.get();
            ra.addFlashAttribute(
                "error",
                "You already have loan application " + app.getId().toString().substring(0, 8)
                    + " in progress (" + dashboardStatusLabel(app.getStatus())
                    + "). Continue or complete it before applying again."
            );
            return "redirect:/app/loan-applications/" + app.getId();
        }
        LoanProductSetting product = resolveApplicationProduct(principal.getSaccoId(), loanProductId, loanType);
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
        return prepareLoanNewModel(principal, product, formValues, Collections.emptyList(), null, model);
    }

    @GetMapping("/loan-applications/{id}/edit")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_UPDATE') and @authz.isLoanOwner(#id, principal)")
    public String editDraft(@PathVariable UUID id,
                            @AuthenticationPrincipal AppUserPrincipal principal,
                            Model model,
                            RedirectAttributes ra) {
        LoanApplication app = loanWorkflowService.getMine(id, principal.getMemberId());
        if (app.getStatus() != LoanStatus.DRAFT && app.getStatus() != LoanStatus.ALL_GUARANTORS_APPROVED) {
            ra.addFlashAttribute("error", "Only draft applications or applications approved by all guarantors can be edited.");
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
            resolveApplicationProduct(principal.getSaccoId(), app.getLoanProductSettingId(), app.getLoanType()),
            formValues,
            parseUuidList(app.getSelectedGuarantors()),
            app.getRequiredGuarantors(),
            model
        );
    }

    @PostMapping("/loan-applications")
    @PreAuthorize("@access.canAccessMemberArea(principal) and ((#applicationId == null and @access.has(principal, 'MEMBER_LOANS_CREATE')) or (#applicationId != null and @access.has(principal, 'MEMBER_LOANS_UPDATE')))")
    public String createDraft(@AuthenticationPrincipal AppUserPrincipal principal,
                              @RequestParam(required = false) UUID loanProductId,
                              @RequestParam(required = false) LoanType loanType,
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
                              HttpServletRequest request,
                              RedirectAttributes ra,
                              Model model) {
        Map<String, String> submittedValues = new LinkedHashMap<>(params);
        Map<String, String> formPayload = new LinkedHashMap<>(params);
        formPayload.remove("loanProductId");
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
        Map<UUID, List<MultipartFile>> requiredAttachmentFiles = requiredAttachmentFiles(request);
        LoanProductSetting product = resolveApplicationProduct(principal.getSaccoId(), loanProductId, loanType);
        LoanType resolvedLoanType = product.getLoanType();

        try {
            if ("SEND_TO_GUARANTORS".equalsIgnoreCase(action)) {
                if (applicationId == null) {
                    throw new IllegalStateException("Save the application as a draft before submitting it.");
                }
                boolean submitsDirectlyToStaff = requiresApplicantOtpBeforeImmediateSubmission(principal.getSaccoId(), product);
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
                    product.getId(),
                    resolvedLoanType,
                    amount,
                    tenorMonths,
                    formPayload,
                    applicationId,
                    guarantorIds,
                    financialSnapshotJson,
                    topUpLoanId,
                    attachments,
                    requiredAttachmentFiles
                );
                if (submitted.getStatus() == LoanStatus.AWAITING_GUARANTORS) {
                    if (isDirectOtpGuarantorApproval(submitted)) {
                        addDirectGuarantorOtpFlash(ra, issueApplicantGuarantorConfirmationOtps(submitted));
                    } else {
                        ra.addFlashAttribute("message", "Application sent to guarantors successfully. Current status: " + submitted.getStatus());
                    }
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
                return "redirect:/app/loan-applications/" + submitted.getId();
            }

            LoanApplication app = loanWorkflowService.saveDraft(principal.getSaccoId(), principal.getMemberId(), product.getId(), resolvedLoanType,
                amount, tenorMonths, formPayload, applicationId, guarantorIds,
                financialSnapshotJson, topUpLoanId, attachments, requiredAttachmentFiles);

            ra.addFlashAttribute("message", "Draft saved successfully. You can continue editing.");
            return "redirect:/app/loan-applications/" + app.getId() + "/edit";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            String errorMessage = humanizeLoanFormError(ex.getMessage(), principal, product);
            model.addAttribute("error", errorMessage);
            if (ex instanceof LoanWorkflowService.GuarantorValidationException guarantorEx
                && guarantorEx.getGuarantorId() != null) {
                model.addAttribute("guarantorValidationErrorId", guarantorEx.getGuarantorId().toString());
                model.addAttribute("guarantorValidationErrorMessage", errorMessage);
            }
            Integer requiredGuarantorsOverride = resolveDraftRequiredGuarantors(principal.getMemberId(), applicationId);
            return prepareLoanNewModel(
                principal,
                product,
                submittedValues,
                guarantorIds,
                requiredGuarantorsOverride,
                model
            );
        }
    }

    @GetMapping("/loan-applications/{id}")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_VIEW') and @authz.isLoanOwner(#id, principal)")
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
        model.addAttribute("applicantExternalAccountStatus", externalAccountStatusService.loading(message("loan.loadingLiveBalances")));
        model.addAttribute("topUpSourceLoan",
            app.getTopUpSourceLoanId() == null ? null
                : loanApplicationRepository.findByIdAndApplicantMemberId(app.getTopUpSourceLoanId(), app.getApplicantMemberId()).orElse(null));
        model.addAttribute("canRequestTopUp", loanWorkflowService.canRequestTopUp(app));
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
        model.addAttribute("pendingGuarantorRemovalRequest", !pendingGuarantorUndoRequests.isEmpty());
        String guarantorApprovalMode = guarantorApprovalMode(app);
        List<GuarantorRequest> pendingDirectOtpGuarantorRequests = guarantorRequests.stream()
            .filter(request -> request.getStatus() == GuarantorRequestStatus.PENDING)
            .toList();
        model.addAttribute("guarantorApprovalMode", guarantorApprovalMode);
        model.addAttribute("directOtpGuarantorApproval", GUARANTOR_APPROVAL_MODE_DIRECT_OTP.equals(guarantorApprovalMode));
        model.addAttribute("directOtpGuarantorRequests", pendingDirectOtpGuarantorRequests);
        model.addAttribute("directOtpGuarantorPanelVisible",
            GUARANTOR_APPROVAL_MODE_DIRECT_OTP.equals(guarantorApprovalMode)
                && app.getStatus() == LoanStatus.AWAITING_GUARANTORS
                && !pendingDirectOtpGuarantorRequests.isEmpty());
        model.addAttribute("pendingManagerStageWithdrawal", reversalRequestService.pendingManagerStageWithdrawal(id));
        OffsetDateTime memberReversalReferenceAt = app.getStatus() == LoanStatus.READY_FOR_MANAGER
            ? app.getUpdatedAt()
            : app.getSubmittedAt();
        model.addAttribute("memberReversalWindowOpen", isWithinReversalWindow(memberReversalReferenceAt));
        model.addAttribute("draftSelectedGuarantors", selectedGuarantorItems(parseUuidList(app.getSelectedGuarantors())));
        model.addAttribute("managerReason",
            app.getStatus() == LoanStatus.MANAGER_REJECTED ? loanPresentationService.latestManagerReason(id) : "");
        model.addAttribute("financialFields", loanPresentationService.parseFinancialFields(app));
        model.addAttribute("financialFieldSections", loanPresentationService.parseFinancialFieldSections(app));
        model.addAttribute("decisionFeedback", isRejectedStatus(app.getStatus()) ? loanPresentationService.rejectionFeedback(id) : List.of());
        model.addAttribute("calculatedRepaymentRows", loanPresentationService.calculatedRepaymentRows(app));
        model.addAttribute("attachments", loanPresentationService.parseApplicationAttachments(app.getAttachmentsJson()));
        model.addAttribute("disbursementProofAttachments", loanPresentationService.parseDisbursementProofAttachments(app.getAttachmentsJson()));
        model.addAttribute("repaymentSummary", loanPresentationService.reviewRepaymentSummary(app));
        model.addAttribute("repaymentRows", loanPresentationService.parseRepaymentRows(app.getRepaymentScheduleJson()));
        model.addAttribute("rejectionAcknowledgementRequired", requiresRejectionAcknowledgement(app));
        model.addAttribute("repaymentCountdown", loanPresentationService.countdownLabel(app.getFinalDueDate()));
        model.addAttribute("repaymentDaysLeft",
            app.getFinalDueDate() == null ? null : java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), app.getFinalDueDate()));
        model.addAttribute("repaymentWeeksLeft",
            app.getFinalDueDate() == null ? null : Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), app.getFinalDueDate()) / 7));
        model.addAttribute("repaymentMonthsLeft",
            app.getFinalDueDate() == null ? null : Math.max(0, java.time.temporal.ChronoUnit.MONTHS.between(java.time.LocalDate.now().withDayOfMonth(1), app.getFinalDueDate().withDayOfMonth(1))));
        model.addAttribute("savedSignatureText", resolveSavedSignatureText(app.getApplicantMemberId()));
        model.addAttribute("applicantApprovalOtpEnabled",
            stationOtpSettingsService.requiresApprovalOtp(app.getSaccoId(), app.getStationId()));
        model.addAttribute("canPrint",
            app.getFinancialSnapshot() != null
                && !app.getFinancialSnapshot().isBlank()
                && app.getStatus() != LoanStatus.DRAFT
                && app.getStatus() != LoanStatus.AWAITING_GUARANTORS
                && app.getStatus() != LoanStatus.ALL_GUARANTORS_APPROVED
                && guarantorRequests.stream().filter(req -> req.getStatus() == GuarantorRequestStatus.APPROVED).count() >= app.getRequiredGuarantors());
        model.addAttribute("statusTimeline", List.of(
            LoanStatus.DRAFT, LoanStatus.AWAITING_GUARANTORS, LoanStatus.ALL_GUARANTORS_APPROVED, LoanStatus.READY_FOR_MANAGER,
            LoanStatus.MANAGER_ACCEPTED, LoanStatus.AWAITING_BOARD, LoanStatus.AWAITING_CREDIT_COMMITTEE, LoanStatus.BOARD_APPROVED,
            LoanStatus.CREDIT_COMMITTEE_APPROVED, LoanStatus.READY_FOR_DISBURSEMENT, LoanStatus.DISBURSED, LoanStatus.DEFAULTED, LoanStatus.PAID
        ));
        return "app/loan-view";
    }

    @GetMapping("/loan-applications/{id}/applicant-financial-status")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_VIEW') and @authz.isLoanOwner(#id, principal)")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> applicantFinancialStatus(@PathVariable UUID id,
                                                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        LoanApplication app = loanApplicationRepository.findByIdAndApplicantMemberId(id, principal.getMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
        Member applicant = memberRepository.findById(app.getApplicantMemberId()).orElse(null);
        return ResponseEntity.ok(externalAccountStatusPayload(externalAccountStatusService.resolve(applicant)));
    }

    private void addMemberLoanViewDisplayAttributes(Model model, LoanApplication app) {
        model.addAttribute("loanIdShort", app.getApplicationNumber() == null ? "" : app.getApplicationNumber().toString());
        model.addAttribute("disbursedLoanId", app.getLoanId());
        model.addAttribute("loanProductName", loanProductDisplayService.displayName(app));
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
            case CHAIRPERSON -> "Chairperson";
            case BOARD -> "Board Member";
            case CREDIT_COMMITTEE -> "Credit Committee";
            case ACCOUNTANT -> "Accountant";
            case DISBURSEMENT_OFFICER -> "Disbursement/Teller Officer";
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
            case MANAGER_REJECTED, LOAN_OFFICER_REJECTED, CHAIRPERSON_REJECTED, BOARD_REJECTED, CREDIT_COMMITTEE_REJECTED, ACCOUNTANT_REJECTED, DEFAULTED -> "bg-rose-50 text-rose-700";
            case AWAITING_LOAN_OFFICER, AWAITING_CHAIRPERSON, AWAITING_BOARD, AWAITING_CREDIT_COMMITTEE, AWAITING_ACCOUNTANT -> "bg-blue-50 text-blue-700";
            case MANAGER_ACCEPTED, LOAN_OFFICER_APPROVED, CHAIRPERSON_APPROVED, BOARD_APPROVED, CREDIT_COMMITTEE_APPROVED, ACCOUNTANT_APPROVED, READY_FOR_DISBURSEMENT,
                DISBURSED, PAID -> "bg-emerald-50 text-emerald-700";
            case REJECTED -> "bg-rose-50 text-rose-700";
        };
    }

    @PostMapping("/loan-applications/{id}/submit")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_UPDATE') and @authz.isLoanOwner(#id, principal)")
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
                if (isDirectOtpGuarantorApproval(app)) {
                    addDirectGuarantorOtpFlash(ra, issueApplicantGuarantorConfirmationOtps(app));
                } else {
                    ra.addFlashAttribute("message", "Application sent to guarantors successfully.");
                }
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
            if (app.getStatus() == LoanStatus.AWAITING_BOARD || app.getStatus() == LoanStatus.AWAITING_CREDIT_COMMITTEE) {
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
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_UPDATE') and @authz.isLoanOwner(#id, principal)")
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

    @GetMapping("/loan-applications/{id}/guarantors")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_ASSIGN') and @authz.isLoanOwner(#id, principal)")
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
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_ASSIGN') and @authz.isLoanOwner(#id, principal)")
    public String saveGuarantors(@PathVariable UUID id,
                                 @AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam(required = false) List<UUID> guarantorIds,
                                 RedirectAttributes ra) {
        loanWorkflowService.selectGuarantors(id, principal.getMemberId(), guarantorIds);
        ra.addFlashAttribute("message", "Guarantors assigned successfully");
        return "redirect:/app/loan-applications/" + id;
    }

    @PostMapping("/loan-applications/{id}/delete")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_DELETE') and @authz.isLoanOwner(#id, principal)")
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
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.hasAny(principal, 'MEMBER_LOANS_CREATE', 'MEMBER_LOANS_UPDATE', 'MEMBER_LOANS_ASSIGN')")
    @ResponseBody
    public List<Map<String, String>> searchGuarantors(@AuthenticationPrincipal AppUserPrincipal principal,
                                                      @RequestParam(defaultValue = "") String q,
                                                      @RequestParam(required = false) String searchBy,
                                                      @RequestParam(required = false) UUID loanProductId,
                                                      @RequestParam(required = false) LoanType loanType) {
        String mode = resolveGuarantorSearchMode(searchBy, q);
        String query = q == null ? "" : q.trim();
        if ("name".equals(mode)) {
            query = query.toLowerCase(Locale.ROOT);
            if (query.length() < 2) {
                return Collections.emptyList();
            }
        } else {
            query = query.toUpperCase(Locale.ROOT);
            boolean fourDigitsOrMore = query.matches("\\d{4,20}");
            boolean fullMemberNo = query.matches("[A-Z0-9]{4,20}") && query.chars().anyMatch(Character::isDigit);
            if (!fourDigitsOrMore && !fullMemberNo) {
                return Collections.emptyList();
            }
        }
        if (query.isBlank()) {
            return Collections.emptyList();
        }
        LoanProductSetting product = resolveApplicationProduct(principal.getSaccoId(), loanProductId, loanType);
        return loanWorkflowService.searchGuarantorCandidates(
                principal.getSaccoId(),
                principal.getStationId(),
                principal.getMemberId(),
                query,
                mode,
                product,
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

    private String resolveGuarantorSearchMode(String searchBy, String q) {
        if ("name".equalsIgnoreCase(searchBy)) {
            return "name";
        }
        if ("number".equalsIgnoreCase(searchBy)) {
            return "number";
        }
        String query = q == null ? "" : q.trim();
        boolean containsLetter = query.chars().anyMatch(Character::isLetter);
        boolean containsDigit = query.chars().anyMatch(Character::isDigit);
        return containsLetter && !containsDigit ? "name" : "number";
    }

    @PostMapping("/loan-applications/financial-preview")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.hasAny(principal, 'MEMBER_LOANS_CREATE', 'MEMBER_LOANS_UPDATE')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> financialPreview(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                @RequestParam(required = false) UUID loanProductId,
                                                                @RequestParam(required = false) LoanType loanType,
                                                                @RequestParam BigDecimal amount,
                                                                @RequestParam Integer tenorMonths,
                                                                @RequestParam(required = false) UUID applicationId,
                                                                @RequestParam(required = false) UUID topUpLoanId) {
        try {
            loanWorkflowService.requireAllowedTopUpSourceLoan(principal.getSaccoId(), principal.getMemberId(), topUpLoanId);
            LoanProductSetting product = resolveApplicationProduct(principal.getSaccoId(), loanProductId, loanType);
            Map<String, Object> snapshot = financialDetailsService.generateSnapshot(
                principal.getSaccoId(), principal.getMemberId(), product, amount, tenorMonths, topUpLoanId);
            EligibilityService.EligibilityResult eligibility = eligibilityService.check(
                principal.getSaccoId(), principal.getMemberId(), product, amount);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("snapshotJson", financialDetailsService.toJson(snapshot));
            response.put("fields", loanPresentationService.parseFinancialFields(financialDetailsService.toJson(snapshot)));
            response.put("fieldSections", loanPresentationService.parseFinancialFieldSections(financialDetailsService.toJson(snapshot)));
            response.put("message", "Loan calculations loaded");
            BigDecimal principalPlusInterest = readBigDecimal(snapshot.get("principalPlusInterest"));
            if (principalPlusInterest == null) {
                principalPlusInterest = readBigDecimal(snapshot.get("loanPlusInterest"));
            }
            response.put("principalPlusInterest", principalPlusInterest == null ? "" : principalPlusInterest.setScale(2, RoundingMode.HALF_UP).toPlainString());
            response.put("principalPlusInterestLabel", principalPlusInterest == null ? "" : formatTzs(principalPlusInterest));
            response.put("repaymentSchedule", previewRepaymentSchedule(amount, tenorMonths, snapshot));
            Map<String, Object> eligibilityMap = new LinkedHashMap<>();
            eligibilityMap.put("eligible", eligibility.eligible());
            eligibilityMap.put("savingsLabel", formatTzs(eligibility.savings()));
            eligibilityMap.put("maxAllowedLabel", formatTzs(eligibility.maxAllowed()));
            eligibilityMap.put("savingsLimitCheckRequired", eligibility.savingsLimitCheckRequired());
            eligibilityMap.put("savingsMultipleLabel", formatSavingsMultiple(eligibility.ratio()));
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

    ResponseEntity<Map<String, Object>> financialPreview(AppUserPrincipal principal,
                                                         LoanType loanType,
                                                         BigDecimal amount,
                                                         Integer tenorMonths,
                                                         UUID applicationId,
                                                         UUID topUpLoanId) {
        return financialPreview(principal, null, loanType, amount, tenorMonths, applicationId, topUpLoanId);
    }

    @GetMapping("/loan-applications/external-eligibility-summary")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_CREATE')")
    @ResponseBody
    public Map<String, Object> externalEligibilitySummary(@AuthenticationPrincipal AppUserPrincipal principal,
                                                          @RequestParam(required = false) UUID loanProductId,
                                                          @RequestParam(required = false) LoanType loanType) {
        Member member = memberRepository.findById(principal.getMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Logged-in member was not found."));
        if (member.getStationId() == null || member.getStationId().isBlank()) {
            throw new IllegalStateException("Station ID is not configured for this member.");
        }
        LoanProductSetting product = resolveApplicationProduct(principal.getSaccoId(), loanProductId, loanType);
        EligibilityService.EligibilityResult eligibility = eligibilityService.check(
            principal.getSaccoId(),
            principal.getMemberId(),
            product,
            BigDecimal.ZERO
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("savingsLabel", formatTzs(eligibility.savings()));
        response.put("savingsMultipleLabel", formatSavingsMultiple(eligibility.ratio()));
        response.put("ratioPercentLabel", eligibility.ratio().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%");
        response.put("maxAllowedLabel", formatTzs(eligibility.maxAllowed()));
        response.put("savingsLimitCheckRequired", eligibility.savingsLimitCheckRequired());
        response.put("exampleAmountLabel", formatTzs(exampleAmount(eligibility.maxAllowed())));
        response.put("memberNumber", member.getMemberNo());
        response.put("stationId", member.getStationId());
        return response;
    }

    @GetMapping("/guarantee-requests")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'GUARANTOR_REQUESTS_VIEW')")
    public String myGuarantorRequests(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        List<GuarantorRequest> requests = loanWorkflowService.myActiveGuarantorRequests(principal.getMemberId());
        model.addAttribute("requests", requests);
        addGuaranteeActionContext(requests, model);
        model.addAttribute("guarantorRequestOtpEnabled", guarantorRequestOtpEnabled(requests));
        model.addAttribute("guarantorSavedSignatureText", resolveSavedSignatureText(principal.getMemberId()));
        return "app/guarantee-requests";
    }

    private Map<UUID, Boolean> guarantorRequestOtpEnabled(List<GuarantorRequest> requests) {
        Map<UUID, Boolean> enabled = new LinkedHashMap<>();
        if (requests == null || requests.isEmpty()) {
            return enabled;
        }
        Set<UUID> loanIds = requests.stream()
            .map(GuarantorRequest::getLoanApplicationId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<UUID, LoanApplication> loans = loanApplicationRepository.findAllById(loanIds).stream()
            .collect(Collectors.toMap(LoanApplication::getId, loan -> loan));
        for (GuarantorRequest request : requests) {
            LoanApplication loan = loans.get(request.getLoanApplicationId());
            enabled.put(request.getId(), loan == null
                || stationOtpSettingsService.requiresApprovalOtp(loan.getSaccoId(), loan.getStationId()));
        }
        return enabled;
    }

    @GetMapping("/guaranteed-loans")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'GUARANTOR_REQUESTS_VIEW')")
    public String guaranteedLoans(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        List<GuarantorRequest> requests = loanWorkflowService.myActiveGuaranteedLoans(principal.getMemberId());
        Map<UUID, LoanApplication> loansById = loanApplicationsById(requests);
        List<Map<String, Object>> rows = requests.stream()
            .map(request -> {
                LoanApplication loan = loansById.get(request.getLoanApplicationId());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("request", request);
                row.put("loan", loan);
                row.put("statusLabel", loan == null ? message("common.notAvailable") : dashboardStatusLabel(loan.getStatus()));
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
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'GUARANTOR_REQUESTS_APPROVE') and @authz.isGuarantorAssignee(#requestId, principal)")
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
            GuarantorRequest request = guarantorRequestRepository.findByIdAndGuarantorMemberId(requestId, principal.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("Guarantor request not found"));
            LoanApplication application = loanApplicationRepository.findById(request.getLoanApplicationId())
                .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
            UUID otpTokenId = stationOtpSettingsService.requiresApprovalOtp(application.getSaccoId(), application.getStationId())
                ? emailOtpService.validateOtp(guarantor.getEmail(), EmailOtpPurpose.GUARANTOR_SIGNATURE, guarantorSignatureOtpCode)
                : null;
            loanWorkflowService.approveGuarantorRequest(
                requestId,
                principal.getMemberId(),
                guarantor.getSignatureText(),
                OffsetDateTime.now()
            );
            if (otpTokenId != null) {
                emailOtpService.consumeOtpById(otpTokenId);
            }
            ra.addFlashAttribute("message", "Guarantee request approved");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/app/guarantee-requests";
    }

    @PostMapping("/guarantee-requests/request-signature-otp")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'GUARANTOR_REQUESTS_APPROVE')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestGuarantorSignatureOtp(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                            @RequestParam(required = false) UUID requestId) {
        try {
            Member member = requireMemberWithSavedSignature(
                principal.getMemberId(),
                "Add an email address to your member profile before requesting a guarantor OTP.",
                "Register your signature first before approving guarantor requests."
            );
            LoanApplication workflowApplication = null;
            if (requestId != null) {
                GuarantorRequest request = guarantorRequestRepository.findByIdAndGuarantorMemberId(requestId, principal.getMemberId())
                    .orElseThrow(() -> new IllegalArgumentException("Guarantor request not found"));
                if (request.getStatus() != GuarantorRequestStatus.PENDING) {
                    throw new IllegalStateException("Request already decided");
                }
                workflowApplication = loanApplicationRepository.findById(request.getLoanApplicationId())
                    .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
            }
            String otpSaccoId = workflowApplication == null ? member.getSaccoId() : workflowApplication.getSaccoId();
            String otpStationId = workflowApplication == null ? member.getStationId() : workflowApplication.getStationId();
            if (!stationOtpSettingsService.requiresApprovalOtp(otpSaccoId, otpStationId)) {
                throw new IllegalStateException("OTP verification is disabled for approval actions at this station.");
            }
            EmailOtpService.OtpIssueResult otp = emailOtpService.issueOtpWithMetadata(
                member.getEmail(),
                EmailOtpPurpose.GUARANTOR_SIGNATURE,
                member.getId(),
                "Your Loan Application Portal guarantor confirmation code",
                "Use this OTP code to confirm your guarantor signature and approve the request.",
                otpSaccoId,
                otpStationId,
                member.getPhone()
            );
            return ResponseEntity.ok(otpIssueResponse(otp, "We sent a guarantor confirmation code using the station OTP delivery policy."));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/guarantee-requests/verify-signature-otp")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'GUARANTOR_REQUESTS_APPROVE')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyGuarantorSignatureOtp(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                           @RequestParam UUID requestId,
                                                                           @RequestParam String otpCode) {
        try {
            GuarantorRequest request = guarantorRequestRepository.findByIdAndGuarantorMemberId(requestId, principal.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("Guarantor request not found"));
            if (request.getStatus() != GuarantorRequestStatus.PENDING) {
                throw new IllegalStateException("Request already decided");
            }
            LoanApplication application = loanApplicationRepository.findById(request.getLoanApplicationId())
                .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
            if (!stationOtpSettingsService.requiresApprovalOtp(application.getSaccoId(), application.getStationId())) {
                return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "message", "OTP verification is disabled for approval actions at this station."
                ));
            }
            Member guarantor = requireMemberWithSavedSignature(
                principal.getMemberId(),
                "Add an email address to your member profile before verifying a guarantor OTP.",
                "Register your signature first before approving guarantor requests."
            );
            emailOtpService.validateOtp(
                guarantor.getEmail(), EmailOtpPurpose.GUARANTOR_SIGNATURE, guarantor.getId(), otpCode);
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "OTP code verified."
            ));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/loan-applications/request-signature-otp")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_UPDATE')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestApplicantSignatureOtp(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                            @RequestParam UUID applicationId) {
        try {
            LoanApplication application = loanWorkflowService.getMine(applicationId, principal.getMemberId());
            assertApplicantSignatureOtpAllowed(application);
            if (!stationOtpSettingsService.requiresApprovalOtp(application.getSaccoId(), application.getStationId())) {
                throw new IllegalStateException("OTP verification is disabled for approval actions at this station.");
            }
            Member member = requireMemberWithSavedSignature(principal.getMemberId());
            EmailOtpService.OtpIssueResult otp = emailOtpService.issueOtpWithMetadata(
                member.getEmail(),
                EmailOtpPurpose.APPLICANT_SIGNATURE,
                member.getId(),
                "Your Loan Application Portal submission code",
                "Use this OTP code to confirm your signature and submit your loan application.",
                application.getSaccoId(),
                application.getStationId(),
                member.getPhone()
            );
            return ResponseEntity.ok(otpIssueResponse(otp, "We sent a submission code using the station OTP delivery policy."));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/loan-applications/verify-signature-otp")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_UPDATE')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyApplicantSignatureOtp(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                           @RequestParam UUID applicationId,
                                                                           @RequestParam String otpCode) {
        try {
            LoanApplication application = loanWorkflowService.getMine(applicationId, principal.getMemberId());
            assertApplicantSignatureOtpAllowed(application);
            if (!stationOtpSettingsService.requiresApprovalOtp(application.getSaccoId(), application.getStationId())) {
                return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "message", "OTP verification is disabled for approval actions at this station."
                ));
            }
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

    @PostMapping("/loan-applications/{loanId}/guarantors/{requestId}/request-confirmation-otp")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_ASSIGN') and @authz.isLoanOwner(#loanId, principal)")
    public String requestApplicantGuarantorConfirmationOtp(@PathVariable UUID loanId,
                                                           @PathVariable UUID requestId,
                                                           @AuthenticationPrincipal AppUserPrincipal principal,
                                                           RedirectAttributes ra) {
        try {
            LoanApplication application = loanWorkflowService.getMine(loanId, principal.getMemberId());
            assertDirectOtpGuarantorApproval(application);
            GuarantorRequest request = guarantorRequestRepository.findById(requestId)
                .filter(item -> loanId.equals(item.getLoanApplicationId()))
                .orElseThrow(() -> new IllegalArgumentException("Guarantor request not found"));
            Member guarantor = requireMemberWithEmail(
                request.getGuarantorMemberId(),
                "The selected guarantor must have an email address before receiving a confirmation OTP."
            );
            issueApplicantGuarantorConfirmationOtp(application, request, guarantor);
            ra.addFlashAttribute("message", "Guarantor OTP sent to " + guarantor.getFullName() + ".");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/app/loan-applications/" + loanId;
    }

    @PostMapping(value = "/loan-applications/{loanId}/guarantors/{requestId}/request-confirmation-otp-json", produces = "application/json")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_ASSIGN') and @authz.isLoanOwner(#loanId, principal)")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestApplicantGuarantorConfirmationOtpJson(@PathVariable UUID loanId,
                                                                                             @PathVariable UUID requestId,
                                                                                             @AuthenticationPrincipal AppUserPrincipal principal) {
        try {
            LoanApplication application = loanWorkflowService.getMine(loanId, principal.getMemberId());
            assertDirectOtpGuarantorApproval(application);
            GuarantorRequest request = guarantorRequestRepository.findById(requestId)
                .filter(item -> loanId.equals(item.getLoanApplicationId()))
                .orElseThrow(() -> new IllegalArgumentException("Guarantor request not found"));
            Member guarantor = requireMemberWithEmail(
                request.getGuarantorMemberId(),
                "The selected guarantor must have an email address before receiving a confirmation OTP."
            );
            EmailOtpService.OtpIssueResult otp = issueApplicantGuarantorConfirmationOtp(application, request, guarantor);
            return ResponseEntity.ok(otpIssueResponse(otp, "Guarantor OTP sent to " + guarantor.getFullName() + "."));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping(value = "/loan-applications/{loanId}/guarantors/{requestId}/verify-confirmation-otp", produces = "application/json")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_ASSIGN') and @authz.isLoanOwner(#loanId, principal)")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyApplicantGuarantorConfirmationOtp(@PathVariable UUID loanId,
                                                                                       @PathVariable UUID requestId,
                                                                                       @AuthenticationPrincipal AppUserPrincipal principal,
                                                                                       @RequestParam String otpCode) {
        try {
            LoanApplication application = loanWorkflowService.getMine(loanId, principal.getMemberId());
            assertDirectOtpGuarantorApproval(application);
            GuarantorRequest request = guarantorRequestRepository.findById(requestId)
                .filter(item -> loanId.equals(item.getLoanApplicationId()))
                .orElseThrow(() -> new IllegalArgumentException("Guarantor request not found"));
            if (request.getStatus() != GuarantorRequestStatus.PENDING) {
                throw new IllegalStateException("This guarantor request has already been decided.");
            }
            Member guarantor = requireMemberWithEmail(
                request.getGuarantorMemberId(),
                "The selected guarantor must have an email address before confirming by OTP."
            );
            emailOtpService.validateOtp(
                guarantor.getEmail(),
                EmailOtpPurpose.GUARANTOR_APPLICANT_CONFIRMATION,
                guarantor.getId(),
                otpCode
            );
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

    @PostMapping("/loan-applications/{loanId}/guarantors/{requestId}/confirm-with-otp")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_ASSIGN') and @authz.isLoanOwner(#loanId, principal)")
    public String confirmApplicantGuarantorOtp(@PathVariable UUID loanId,
                                               @PathVariable UUID requestId,
                                               @AuthenticationPrincipal AppUserPrincipal principal,
                                               @RequestParam String otpCode,
                                               RedirectAttributes ra) {
        try {
            LoanApplication application = loanWorkflowService.getMine(loanId, principal.getMemberId());
            assertDirectOtpGuarantorApproval(application);
            GuarantorRequest request = guarantorRequestRepository.findById(requestId)
                .filter(item -> loanId.equals(item.getLoanApplicationId()))
                .orElseThrow(() -> new IllegalArgumentException("Guarantor request not found"));
            if (request.getStatus() != GuarantorRequestStatus.PENDING) {
                throw new IllegalStateException("This guarantor request has already been decided.");
            }
            Member guarantor = requireMemberWithEmail(
                request.getGuarantorMemberId(),
                "The selected guarantor must have an email address before confirming by OTP."
            );
            UUID otpTokenId = emailOtpService.validateOtp(
                guarantor.getEmail(),
                EmailOtpPurpose.GUARANTOR_APPLICANT_CONFIRMATION,
                guarantor.getId(),
                otpCode
            );
            loanWorkflowService.approveGuarantorRequest(
                requestId,
                guarantor.getId(),
                "Approved by guarantor OTP",
                OffsetDateTime.now()
            );
            emailOtpService.consumeOtpById(otpTokenId);
            ra.addFlashAttribute("message", "Guarantor approval confirmed.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/app/loan-applications/" + loanId;
    }

    @PostMapping("/guarantee-requests/{requestId}/reject")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'GUARANTOR_REQUESTS_REJECT') and @authz.isGuarantorAssignee(#requestId, principal)")
    public String rejectRequest(@PathVariable UUID requestId,
                                @AuthenticationPrincipal AppUserPrincipal principal,
                                @RequestParam(required = false) String reason,
                                RedirectAttributes ra) {
        try {
            loanWorkflowService.rejectGuarantorRequest(requestId, principal.getMemberId(), reason);
            ra.addFlashAttribute("message", "Guarantee request rejected");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/app/guarantee-requests";
    }

    @PostMapping("/guarantee-requests/{requestId}/undo")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'GUARANTOR_REQUESTS_UPDATE') and @authz.isGuarantorAssignee(#requestId, principal)")
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
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_LOANS_ASSIGN') and @authz.isLoanOwner(#loanId, principal)")
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

    @GetMapping("/notifications")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'NOTIFICATIONS_VIEW')")
    public String notifications(@AuthenticationPrincipal AppUserPrincipal principal,
                                @RequestParam(required = false) UUID highlight,
                                Model model) {
        model.addAttribute("notifications", notificationInboxService.allViews(principal));
        model.addAttribute("highlightNotificationId", highlight);
        return "app/notifications";
    }

    @GetMapping("/notifications/{id}/open")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'NOTIFICATIONS_VIEW')")
    public String openNotification(@PathVariable UUID id,
                                   @AuthenticationPrincipal AppUserPrincipal principal,
                                   RedirectAttributes ra) {
        try {
            return "redirect:" + notificationInboxService.openForMember(id, principal, "/app/notifications");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/app/notifications";
        }
    }

    @GetMapping("/support")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'SUPPORT_VIEW')")
    public String support() {
        return "app/support";
    }

    @GetMapping("/support/archive")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'SUPPORT_VIEW')")
    public String supportArchive(@AuthenticationPrincipal AppUserPrincipal principal,
                                 Model model) {
        model.addAttribute("supportArchive", adminService.memberSupportArchive(principal.getMemberId()));
        return "app/support-archive";
    }

    @GetMapping("/support/replies")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'SUPPORT_VIEW')")
    public String supportReplies(@AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam(required = false) UUID highlight,
                                 Model model) {
        model.addAttribute("replies", notificationInboxService.allViewsByType(principal.getMemberId(), "ADMIN_REPLY"));
        model.addAttribute("broadcasts", notificationInboxService.allViewsByType(principal.getMemberId(), "ADMIN_BROADCAST"));
        model.addAttribute("highlightNotificationId", highlight);
        return "app/support-replies";
    }

    @GetMapping("/support/replies/{id}/open")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'SUPPORT_VIEW')")
    public String openSupportReply(@PathVariable UUID id,
                                   @AuthenticationPrincipal AppUserPrincipal principal,
                                   RedirectAttributes ra) {
        try {
            return "redirect:" + notificationInboxService.openForMember(id, principal, "/app/support/replies");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/app/support/replies";
        }
    }

    @PostMapping("/support/replies/mark-all-read")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'NOTIFICATIONS_UPDATE') and @access.has(principal, 'SUPPORT_VIEW')")
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
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.hasAny(principal, 'MEMBER_SETTINGS_VIEW', 'PAYMENT_DETAILS_VIEW')")
    public String settings(@AuthenticationPrincipal AppUserPrincipal principal,
                           @RequestParam(required = false) String section,
                           Model model) {
        boolean paymentSection = "payment-details".equals(section);
        model.addAttribute("settingsSection", paymentSection ? "payment-details" : "language");
        if (paymentSection) {
            PaymentDetailsService.PaymentDetailsView paymentDetails =
                paymentDetailsService.currentForMember(principal.getMemberId());
            model.addAttribute("paymentDetails", paymentDetails);
            model.addAttribute(
                "paymentDestinationType",
                paymentDetails.destinationType() == null ? "" : paymentDetails.destinationType().name()
            );
        } else {
            UserSettings settings = userSettingsRepository.findById(principal.getMemberId())
                .orElseGet(() -> UserSettings.builder()
                    .memberId(principal.getMemberId())
                    .language("en")
                    .notificationPrefs("{}")
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build());
            model.addAttribute("memberSettingsLanguage", normalizeMemberLanguage(settings.getLanguage()));
        }
        return "app/settings";
    }

    @PostMapping("/settings/language")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'MEMBER_SETTINGS_UPDATE')")
    public String updateLanguage(@AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam String language,
                                 HttpServletRequest request,
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
        memberLocaleInterceptor.cacheUserLocale(request, principal.getMemberId(), settings.getLanguage());
        ra.addFlashAttribute("message", "Language preference updated.");
        return "redirect:/app/settings";
    }

    @PostMapping("/settings/payment-details")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'PAYMENT_DETAILS_UPDATE')")
    public String updatePaymentDetails(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @RequestParam PaymentDestinationType destinationType,
                                       @RequestParam String provider,
                                       @RequestParam String accountHolderName,
                                       @RequestParam String accountIdentifier,
                                       @RequestParam String otpCode,
                                       RedirectAttributes ra) {
        try {
            Member member = requireMemberWithEmail(
                principal.getMemberId(),
                "Add an email address to your member profile before changing financial details for the disbursement deposit."
            );
            UUID otpTokenId = emailOtpService.validateOtp(
                member.getEmail(),
                EmailOtpPurpose.PAYMENT_DETAILS_CHANGE,
                member.getId(),
                otpCode
            );
            paymentDetailsService.update(
                principal.getMemberId(),
                destinationType,
                provider,
                accountHolderName,
                accountIdentifier
            );
            emailOtpService.consumeOtpById(otpTokenId);
            ra.addFlashAttribute("message", "Financial details for the disbursement deposit updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/app/settings?section=payment-details#payment-details";
    }

    @PostMapping("/settings/payment-details/request-otp")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'PAYMENT_DETAILS_UPDATE')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestPaymentDetailsOtp(
        @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        try {
            Member member = requireMemberWithEmail(
                principal.getMemberId(),
                "Add an email address to your member profile before changing financial details for the disbursement deposit."
            );
            EmailOtpService.OtpIssueResult otp = emailOtpService.issueOtpWithMetadata(
                member.getEmail(),
                EmailOtpPurpose.PAYMENT_DETAILS_CHANGE,
                member.getId(),
                "Your disbursement deposit details change code",
                "Use this OTP code to confirm the change to your financial details for the disbursement deposit.",
                member.getSaccoId(),
                member.getStationId(),
                member.getPhone()
            );
            return ResponseEntity.ok(otpIssueResponse(otp, "We sent an OTP code to your registered email."));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/notifications/mark-all-read")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'NOTIFICATIONS_UPDATE')")
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
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'SUPPORT_CREATE')")
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

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public String handleMultipartError(RuntimeException ex, RedirectAttributes ra) {
        ra.addFlashAttribute("error", "The selected attachment upload is too large. Reduce the file size and try again.");
        return "redirect:/app/loan-applications";
    }

    private String prepareLoanNewModel(AppUserPrincipal principal,
                                       LoanProductSetting schema,
                                       Map<String, String> formValues,
                                       List<UUID> guarantorIds,
                                       Integer requiredGuarantorsOverride,
                                       Model model) {
        LoanType loanType = schema.getLoanType();
        FormModel form = formSchemaService.toFormModel(loanType, schema.getFormSchema());
        int requiredGuarantors = requiredGuarantorsOverride == null
            ? schema.getGuarantorsRequired()
            : Math.max(requiredGuarantorsOverride, 0);
        EligibilityService.EligibilityResult eligibility = eligibilityService.check(
            principal.getSaccoId(), principal.getMemberId(), schema, BigDecimal.ZERO);

        model.addAttribute("formModel", form);
        model.addAttribute("product", schema);
        model.addAttribute("requiredAttachmentDefinitions", requiredAttachmentService.activeForProduct(schema.getId()));
        model.addAttribute("existingRequiredAttachmentIds", existingRequiredAttachmentIds(formValues));
        model.addAttribute("existingRequiredAttachmentNames", existingRequiredAttachmentNames(formValues));
        model.addAttribute("loanType", loanType);
        model.addAttribute("loanProductId", schema.getId());
        model.addAttribute("loanProductName", schema.getDisplayName());
        model.addAttribute("loanProductCode", schema.getDisplayCode());
        model.addAttribute("loanProductDescription", schema.getDisplayDescription());
        model.addAttribute("requiredGuarantors", requiredGuarantors);
        model.addAttribute("formValues", formValues == null ? Collections.emptyMap() : formValues);
        model.addAttribute("guarantorApprovalMode", normalizeGuarantorApprovalMode(
            formValues == null ? null : formValues.get(GUARANTOR_APPROVAL_MODE_FIELD)
        ));
        model.addAttribute("selectedGuarantorLookup", toLookupMap(guarantorIds));
        model.addAttribute("selectedGuarantorItems", selectedGuarantorItems(guarantorIds));
        model.addAttribute("savingsLabel", formatTzs(eligibility.savings()));
        model.addAttribute("maxAllowedLabel", formatTzs(eligibility.maxAllowed()));
        model.addAttribute("savingsLimitCheckRequired", eligibility.savingsLimitCheckRequired());
        model.addAttribute("savingsMultipleLabel", formatSavingsMultiple(eligibility.ratio()));
        model.addAttribute("ratioPercentLabel",
            eligibility.ratio().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%");
        model.addAttribute("exampleAmountLabel", formatTzs(exampleAmount(eligibility.maxAllowed())));
        model.addAttribute("exampleAmountRaw", exampleAmount(eligibility.maxAllowed()).toPlainString());
        model.addAttribute("minimumAmountLabel",
            schema.getMinimumAmount() == null ? "-" : formatTzs(schema.getMinimumAmount()));
        model.addAttribute("maximumAmountLabel",
            schema.getMaximumAmount() == null ? "Not set" : formatTzs(schema.getMaximumAmount()));
        String annualInterestPercentLabel = (schema.getInterestRate() == null
            ? BigDecimal.ZERO
            : schema.getInterestRate().multiply(BigDecimal.valueOf(100))).stripTrailingZeros().toPlainString() + "%";
        model.addAttribute("annualInterestPercentLabel", annualInterestPercentLabel);
        model.addAttribute("allowApplicationWithActiveLoan", schema.isApplicationWithActiveLoanAllowed());
        model.addAttribute("financialSnapshotDisplay",
            loanPresentationService.parseFinancialFields(formValues == null ? null : formValues.get("financialSnapshotJson")));
        model.addAttribute("financialSnapshotSections",
            loanPresentationService.parseFinancialFieldSections(formValues == null ? null : formValues.get("financialSnapshotJson")));
        model.addAttribute("repaymentSchedulePreviewRows", draftRepaymentSchedulePreview(formValues));
        model.addAttribute("savedSignatureText", resolveSavedSignatureText(principal.getMemberId()));
        model.addAttribute("topUpLoanId", formValues == null ? null : formValues.get("topUpLoanId"));
        model.addAttribute("topUpSourceLoan", resolveTopUpSourceLoan(principal.getMemberId(), formValues == null ? null : formValues.get("topUpLoanId")));
        return "app/loan-new";
    }

    private Map<UUID, List<MultipartFile>> requiredAttachmentFiles(HttpServletRequest request) {
        if (!(request instanceof MultipartHttpServletRequest multipartRequest)) {
            return Map.of();
        }
        Map<UUID, List<MultipartFile>> filesByRequirement = new LinkedHashMap<>();
        multipartRequest.getMultiFileMap().forEach((name, files) -> {
            if (name == null || !name.startsWith("requiredAttachmentFiles_")) {
                return;
            }
            try {
                UUID requirementId = UUID.fromString(name.substring("requiredAttachmentFiles_".length()));
                List<MultipartFile> nonEmptyFiles = files == null
                    ? List.of()
                    : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
                if (!nonEmptyFiles.isEmpty()) {
                    filesByRequirement.put(requirementId, nonEmptyFiles);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed field names; server-side validation will still require configured slots.
            }
        });
        return filesByRequirement;
    }

    private Set<String> existingRequiredAttachmentIds(Map<String, String> formValues) {
        return existingRequiredAttachmentNames(formValues).keySet().stream()
            .map(UUID::toString)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<UUID, String> existingRequiredAttachmentNames(Map<String, String> formValues) {
        if (formValues == null) {
            return Map.of();
        }
        String applicationId = formValues.get("applicationId");
        if (applicationId == null || applicationId.isBlank()) {
            return Map.of();
        }
        try {
            UUID appId = UUID.fromString(applicationId);
            return loanApplicationRepository.findById(appId)
                .map(app -> {
                    Map<UUID, List<String>> namesByRequirement = new LinkedHashMap<>();
                    loanAttachmentService.parse(app.getAttachmentsJson()).stream()
                        .filter(item -> LoanAttachmentService.CATEGORY_APPLICATION_ATTACHMENT.equals(String.valueOf(item.get("attachmentCategory"))))
                        .forEach(item -> {
                            String requirementId = String.valueOf(item.getOrDefault("requiredAttachmentId", ""));
                            if (requirementId == null || requirementId.isBlank()) {
                                return;
                            }
                            try {
                                UUID id = UUID.fromString(requirementId);
                                String name = String.valueOf(item.getOrDefault("originalName", ""));
                                if (name != null && !name.isBlank()) {
                                    namesByRequirement.computeIfAbsent(id, ignored -> new ArrayList<>()).add(name);
                                }
                            } catch (IllegalArgumentException ignored) {
                                // Ignore malformed historical metadata.
                            }
                        });
                    Map<UUID, String> result = new LinkedHashMap<>();
                    namesByRequirement.forEach((id, names) -> result.put(id, String.join(", ", names)));
                    return result;
                })
                .orElseGet(LinkedHashMap::new);
        } catch (IllegalArgumentException ex) {
            return Map.of();
        }
    }

    private List<Map<String, String>> draftRepaymentSchedulePreview(Map<String, String> formValues) {
        if (formValues == null || formValues.get("financialSnapshotJson") == null || formValues.get("financialSnapshotJson").isBlank()) {
            return Collections.emptyList();
        }
        try {
            BigDecimal amount = readBigDecimal(formValues.get("amount"));
            Integer tenorMonths = formValues.get("tenorMonths") == null || formValues.get("tenorMonths").isBlank()
                ? null
                : Integer.valueOf(formValues.get("tenorMonths"));
            Map<String, Object> snapshot = objectMapper.readValue(
                formValues.get("financialSnapshotJson"),
                new TypeReference<Map<String, Object>>() {}
            );
            return previewRepaymentSchedule(amount, tenorMonths, snapshot);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
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

    private List<Map<String, String>> previewRepaymentSchedule(BigDecimal amount,
                                                               Integer tenorMonths,
                                                               Map<String, Object> snapshot) {
        BigDecimal principal = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        int months = tenorMonths == null || tenorMonths <= 0 ? 1 : tenorMonths;
        BigDecimal annualRate = Optional.ofNullable(readBigDecimal(snapshot.get("interestRate")))
            .orElse(BigDecimal.ZERO);
        InterestMethod interestMethod = InterestMethod.FLAT_RATE;
        Object rawMethod = snapshot.get("interestMethod");
        if (rawMethod != null) {
            try {
                interestMethod = InterestMethod.valueOf(String.valueOf(rawMethod));
            } catch (IllegalArgumentException ignored) {
                interestMethod = InterestMethod.FLAT_RATE;
            }
        }

        List<Map<String, String>> rows = new ArrayList<>();
        BigDecimal runningPrincipal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal runningInterest = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingPrincipal = principal;
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);
        BigDecimal flatTotalInterest = Optional.ofNullable(readBigDecimal(snapshot.get("interestAmount")))
            .orElseGet(() -> principal.multiply(annualRate)
                .multiply(BigDecimal.valueOf(months))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP));
        BigDecimal flatPrincipalBase = principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        BigDecimal flatInterestBase = flatTotalInterest.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        BigDecimal reducingInstallment = reducingInstallment(principal, monthlyRate, months);

        for (int month = 1; month <= months; month++) {
            BigDecimal principalComponent;
            BigDecimal interestComponent;
            BigDecimal installmentAmount;
            BigDecimal beginningBalance = remainingPrincipal;
            if (interestMethod == InterestMethod.REDUCING_BALANCE) {
                interestComponent = remainingPrincipal.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
                principalComponent = reducingInstallment.subtract(interestComponent).setScale(2, RoundingMode.HALF_UP);
                if (principalComponent.compareTo(BigDecimal.ZERO) < 0) {
                    principalComponent = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }
                if (month == months) {
                    principalComponent = principal.subtract(runningPrincipal).setScale(2, RoundingMode.HALF_UP);
                    installmentAmount = principalComponent.add(interestComponent).setScale(2, RoundingMode.HALF_UP);
                } else {
                    installmentAmount = reducingInstallment;
                }
            } else {
                principalComponent = month == months
                    ? principal.subtract(runningPrincipal).setScale(2, RoundingMode.HALF_UP)
                    : flatPrincipalBase;
                interestComponent = month == months
                    ? flatTotalInterest.subtract(runningInterest).setScale(2, RoundingMode.HALF_UP)
                    : flatInterestBase;
                installmentAmount = principalComponent.add(interestComponent).setScale(2, RoundingMode.HALF_UP);
            }

            runningPrincipal = runningPrincipal.add(principalComponent).setScale(2, RoundingMode.HALF_UP);
            runningInterest = runningInterest.add(interestComponent).setScale(2, RoundingMode.HALF_UP);
            remainingPrincipal = principal.subtract(runningPrincipal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

            Map<String, String> row = new LinkedHashMap<>();
            row.put("pmtNo", String.valueOf(month));
            row.put("month", "Month " + month);
            row.put("beginningBalance", formatTzs(beginningBalance));
            row.put("payment", formatTzs(installmentAmount));
            row.put("loanAmount", formatTzs(principalComponent));
            row.put("interest", formatTzs(interestComponent));
            row.put("endingBalance", formatTzs(remainingPrincipal));
            row.put("installment", formatTzs(installmentAmount));
            row.put("principal", formatTzs(principalComponent));
            row.put("outstandingBalance", formatTzs(remainingPrincipal));
            rows.add(row);
        }
        return rows;
    }

    private BigDecimal reducingInstallment(BigDecimal principal, BigDecimal monthlyRate, int months) {
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (monthlyRate == null || monthlyRate.compareTo(BigDecimal.ZERO) <= 0) {
            return principal.divide(BigDecimal.valueOf(Math.max(months, 1)), 2, RoundingMode.HALF_UP);
        }
        double rate = monthlyRate.doubleValue();
        double factor = 1d - Math.pow(1d + rate, -Math.max(months, 1));
        return BigDecimal.valueOf(principal.doubleValue() * rate / factor)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private String formatTzs(BigDecimal amount) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.DOWN);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat format = new DecimalFormat("#,##0.##", symbols);
        return "TSh " + format.format(safeAmount);
    }

    private String formatSavingsMultiple(BigDecimal ratio) {
        BigDecimal safeRatio = ratio == null ? BigDecimal.ZERO : ratio.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
        return safeRatio.toPlainString() + "x your savings";
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

    private LoanProductSetting resolveApplicationProduct(String saccoId, UUID loanProductId, LoanType fallbackLoanType) {
        return loanProductId == null
            ? formSchemaService.getSchema(saccoId, fallbackLoanType)
            : formSchemaService.getSchema(saccoId, loanProductId, fallbackLoanType);
    }

    private LoanProductSetting resolveExistingApplicationProduct(LoanApplication application) {
        if (application == null || application.getSaccoId() == null) {
            return null;
        }
        if (application.getLoanProductSettingId() != null) {
            return loanProductSettingRepository.findByIdAndSaccoId(
                application.getLoanProductSettingId(), application.getSaccoId()).orElse(null);
        }
        return loanProductSettingRepository.findBySaccoIdAndActiveTrue(application.getSaccoId()).stream()
            .filter(product -> product.getLoanType() == application.getLoanType())
            .sorted(Comparator.comparing(LoanProductSetting::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .findFirst()
            .orElse(null);
    }

    private String humanizeLoanFormError(String message, AppUserPrincipal principal, LoanProductSetting product) {
        if (message != null
            && (message.contains("Amount exceeds eligibility cap")
                || message.contains("Loan amount exceeds the applicant savings limit"))) {
            EligibilityService.EligibilityResult eligibility = eligibilityService.check(
                principal.getSaccoId(), principal.getMemberId(), product, BigDecimal.ZERO);
            return "Amount exceeds your eligibility. Maximum allowed now is " + formatTzs(eligibility.maxAllowed())
                + " (" + eligibility.ratio().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()
                + "% of savings " + formatTzs(eligibility.savings()) + ").";
        }
        if (message != null && message.contains("Load SACCO financial details")) {
            return "Load the loan calculations first so the application can include the official deductions section.";
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
        requiresOtp = requiresOtp && stationOtpSettingsService.requiresApprovalOtp(app.getSaccoId(), app.getStationId());
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

    private Map<String, Object> otpIssueResponse(EmailOtpService.OtpIssueResult otp, String fallbackMessage) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("valid", true);
        response.put("message", otp.messageOrDefault(fallbackMessage));
        response.put("expiresAt", otp.expiresAt());
        response.put("secondsUntilExpiry", otp.secondsUntilExpiry());
        response.put("resendAvailableAt", otp.resendAvailableAt());
        response.put("resendCount", otp.resendCount());
        response.put("maxResends", otp.maxResends());
        response.put("resendAttemptsRemaining", otp.resendAttemptsRemaining());
        return response;
    }

    private DirectGuarantorOtpIssueSummary issueApplicantGuarantorConfirmationOtps(LoanApplication application) {
        List<GuarantorRequest> pendingRequests = guarantorRequestRepository.findByLoanApplicationId(application.getId()).stream()
            .filter(request -> request.getStatus() == GuarantorRequestStatus.PENDING)
            .toList();
        int sent = 0;
        List<String> warnings = new ArrayList<>();
        for (GuarantorRequest request : pendingRequests) {
            try {
                Member guarantor = requireMemberWithEmail(
                    request.getGuarantorMemberId(),
                    "The selected guarantor must have an email address before receiving a confirmation OTP."
                );
                issueApplicantGuarantorConfirmationOtp(application, request, guarantor);
                sent += 1;
            } catch (IllegalArgumentException | IllegalStateException ex) {
                warnings.add(ex.getMessage());
            }
        }
        return new DirectGuarantorOtpIssueSummary(sent, warnings);
    }

    private EmailOtpService.OtpIssueResult issueApplicantGuarantorConfirmationOtp(LoanApplication application,
                                                                                  GuarantorRequest request,
                                                                                  Member guarantor) {
        if (request.getStatus() != GuarantorRequestStatus.PENDING) {
            throw new IllegalStateException("This guarantor request has already been decided.");
        }
        return emailOtpService.issueOtpWithMetadata(
            guarantor.getEmail(),
            EmailOtpPurpose.GUARANTOR_APPLICANT_CONFIRMATION,
            guarantor.getId(),
            "Your SACCO guarantor confirmation code",
            directGuarantorOtpIntro(application),
            application.getSaccoId(),
            application.getStationId(),
            guarantor.getPhone()
        );
    }

    private String directGuarantorOtpIntro(LoanApplication application) {
        String applicantName = directOtpApplicantName(application);
        String loanProductName = directOtpLoanProductName(application);
        List<String> summary = new ArrayList<>();
        if (applicantName != null) {
            summary.add("Applicant: " + applicantName);
        }
        if (application.getApplicationNumber() != null) {
            summary.add("Application #" + application.getApplicationNumber());
        }
        if (application.getAmount() != null) {
            summary.add("Amount: " + formatTzs(application.getAmount()));
        }
        if (application.getTenorMonths() != null) {
            summary.add("Tenure: " + application.getTenorMonths() + " month(s)");
        }
        if (loanProductName != null) {
            summary.add("Loan Product: " + loanProductName);
        }
        String requestSummary = summary.isEmpty()
            ? "Loan request details were not available."
            : String.join("; ", summary) + ".";
        return "Share this OTP with " + (applicantName == null ? "the applicant" : applicantName)
            + " only if you approve being listed as guarantor for this loan application."
            + System.lineSeparator()
            + "Request summary: "
            + requestSummary;
    }

    private String directOtpApplicantName(LoanApplication application) {
        if (application == null || application.getApplicantMemberId() == null) {
            return null;
        }
        return memberRepository.findById(application.getApplicantMemberId())
            .map(Member::getFullName)
            .map(String::trim)
            .filter(name -> !name.isBlank())
            .orElse(null);
    }

    private String directOtpLoanProductName(LoanApplication application) {
        if (application == null) {
            return null;
        }
        String productName = loanProductDisplayService.displayName(application);
        if (productName != null && !productName.isBlank() && !"-".equals(productName.trim())) {
            return productName.trim();
        }
        return application.getLoanType() == null ? null : application.getLoanType().getDisplayLabel();
    }

    private void addDirectGuarantorOtpFlash(RedirectAttributes ra, DirectGuarantorOtpIssueSummary summary) {
        if (summary.sent() > 0) {
            ra.addFlashAttribute("message", "Application sent to guarantors. Direct OTP codes sent to " + summary.sent() + " guarantor(s).");
        } else {
            ra.addFlashAttribute("message", "Application sent to guarantors.");
        }
        if (!summary.warnings().isEmpty()) {
            ra.addFlashAttribute("error", String.join(" ", summary.warnings()));
        }
    }

    private boolean isDirectOtpGuarantorApproval(LoanApplication application) {
        return GUARANTOR_APPROVAL_MODE_DIRECT_OTP.equals(guarantorApprovalMode(application));
    }

    private void assertDirectOtpGuarantorApproval(LoanApplication application) {
        if (!isDirectOtpGuarantorApproval(application)) {
            throw new IllegalStateException("Direct OTP approval is not enabled for this application.");
        }
    }

    private String guarantorApprovalMode(LoanApplication application) {
        if (application == null) {
            return GUARANTOR_APPROVAL_MODE_LOGIN;
        }
        return normalizeGuarantorApprovalMode(parseJsonAsStringMap(application.getFormData()).get(GUARANTOR_APPROVAL_MODE_FIELD));
    }

    private String normalizeGuarantorApprovalMode(String value) {
        if (GUARANTOR_APPROVAL_MODE_DIRECT_OTP.equalsIgnoreCase(String.valueOf(value).trim())) {
            return GUARANTOR_APPROVAL_MODE_DIRECT_OTP;
        }
        return GUARANTOR_APPROVAL_MODE_LOGIN;
    }

    private record DirectGuarantorOtpIssueSummary(int sent, List<String> warnings) {
    }

    private boolean requiresApplicantOtpBeforeImmediateSubmission(String saccoId, LoanProductSetting product) {
        return product != null
            && saccoId.equals(product.getSaccoId())
            && (product.getGuarantorsRequired() == null || product.getGuarantorsRequired() <= 0);
    }

    private void assertApplicantSignatureOtpAllowed(LoanApplication application) {
        if (application.getRequiredGuarantors() > 0 && application.getStatus() != LoanStatus.ALL_GUARANTORS_APPROVED) {
            throw new IllegalStateException("Request the OTP after all guarantors have approved this application.");
        }
        if (application.getRequiredGuarantors() <= 0 && application.getStatus() != LoanStatus.DRAFT) {
            throw new IllegalStateException("This application is no longer waiting for applicant OTP confirmation.");
        }
    }

    private String resolveSavedSignatureText(UUID memberId) {
        return memberRepository.findById(memberId)
            .map(Member::getSignatureText)
            .filter(text -> text != null && !text.isBlank())
            .orElse("");
    }

    private List<Map<String, String>> selectedGuarantorItems(List<UUID> guarantorIds) {
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
        Map<UUID, String> guaranteeLoanProductNames = new HashMap<>();
        Map<LoanType, String> loanProductNames = loanProductDisplayService.namesForSacco(
            applicationById.values().stream()
                .findFirst()
                .map(LoanApplication::getSaccoId)
                .orElse("")
        );
        Map<UUID, BigDecimal> guaranteeLoanAmounts = new HashMap<>();
        Map<UUID, String> guaranteeLoanAmountLabels = new HashMap<>();
        for (Map.Entry<UUID, LoanApplication> entry : applicationById.entrySet()) {
            LoanApplication application = entry.getValue();
            guaranteeNames.put(entry.getKey(),
                applicantNames.getOrDefault(application.getApplicantMemberId(), message("member.unknown")));
            guaranteeLoanTypes.put(entry.getKey(), application.getLoanType());
            guaranteeLoanProductNames.put(entry.getKey(), loanProductDisplayService.displayName(application, loanProductNames));
            guaranteeLoanAmounts.put(entry.getKey(), application.getAmount());
            guaranteeLoanAmountLabels.put(entry.getKey(), formatTzs(application.getAmount()));
        }

        model.addAttribute("guaranteeNames", guaranteeNames);
        model.addAttribute("guaranteeLoanTypes", guaranteeLoanTypes);
        model.addAttribute("guaranteeLoanProductNames", guaranteeLoanProductNames);
        model.addAttribute("guaranteeLoanAmounts", guaranteeLoanAmounts);
        model.addAttribute("guaranteeLoanAmountLabels", guaranteeLoanAmountLabels);
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
                    request.getRequestedAmount(),
                    resolveExistingApplicationProduct(application)
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

    private List<LoanStatus> loanArchiveStatuses(String filter) {
        return switch (safeArchiveFilter(filter)) {
            case "DISBURSED" -> List.of(LoanStatus.DISBURSED);
            case "DEFAULTED" -> List.of(LoanStatus.DEFAULTED);
            case "PAID" -> List.of(LoanStatus.PAID);
            case "REJECTED" -> List.of(
                LoanStatus.MANAGER_REJECTED,
                LoanStatus.LOAN_OFFICER_REJECTED,
                LoanStatus.CHAIRPERSON_REJECTED,
                LoanStatus.BOARD_REJECTED,
                LoanStatus.CREDIT_COMMITTEE_REJECTED,
                LoanStatus.ACCOUNTANT_REJECTED,
                LoanStatus.REJECTED
            );
            default -> ARCHIVED_LOAN_STATUSES;
        };
    }

    private GuarantorRequestStatus safeGuarantorArchiveStatus(String filter) {
        String normalized = safeArchiveFilter(filter);
        if ("ALL".equals(normalized)) {
            return null;
        }
        try {
            return GuarantorRequestStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizedArchiveQuery(String query) {
        String normalized = safeArchiveQuery(query).toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String repaymentStateLabel(LoanApplication app, LocalDate today) {
        if (app.getStatus() == LoanStatus.PAID) {
            return message("analytics.paid");
        }
        if (app.getStatus() == LoanStatus.DEFAULTED) {
            return message("analytics.defaulted");
        }
        if (app.getFinalDueDate() != null && app.getFinalDueDate().isBefore(today)) {
            return message("loan.repayment.overdue");
        }
        return message("analytics.active");
    }

    private String repaymentStateClasses(LoanApplication app, LocalDate today) {
        if (app.getStatus() == LoanStatus.PAID) {
            return "border-emerald-200 bg-emerald-50 text-emerald-700";
        }
        if (app.getStatus() == LoanStatus.DEFAULTED) {
            return "border-rose-200 bg-rose-50 text-rose-700";
        }
        if (app.getFinalDueDate() != null && app.getFinalDueDate().isBefore(today)) {
            return "border-amber-200 bg-amber-50 text-amber-700";
        }
        return "border-slate-200 bg-slate-50 text-slate-700";
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

    private boolean isAwaitingDecisionStatus(LoanStatus status) {
        return status == LoanStatus.AWAITING_GUARANTORS
            || status == LoanStatus.ALL_GUARANTORS_APPROVED
            || status == LoanStatus.READY_FOR_MANAGER
            || status == LoanStatus.AWAITING_BOARD
            || status == LoanStatus.AWAITING_CREDIT_COMMITTEE;
    }

    private boolean isArchivedStatus(LoanStatus status) {
        return isRejectedStatus(status)
            || status == LoanStatus.DISBURSED
            || status == LoanStatus.DEFAULTED
            || status == LoanStatus.PAID;
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
            || status == LoanStatus.LOAN_OFFICER_REJECTED
            || status == LoanStatus.CHAIRPERSON_REJECTED
            || status == LoanStatus.BOARD_REJECTED
            || status == LoanStatus.CREDIT_COMMITTEE_REJECTED
            || status == LoanStatus.ACCOUNTANT_REJECTED
            || status == LoanStatus.REJECTED;
    }

    private boolean requiresRejectionAcknowledgement(LoanApplication app) {
        return app != null
            && isRejectedStatus(app.getStatus())
            && app.getApplicantRejectionAcknowledgedAt() == null;
    }

    private Map<UUID, Boolean> rejectionAcknowledgementRequiredById(List<LoanApplication> apps) {
        if (apps == null || apps.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, Boolean> requiredById = new HashMap<>();
        for (LoanApplication app : apps) {
            requiredById.put(app.getId(), requiresRejectionAcknowledgement(app));
        }
        return requiredById;
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
            || status == LoanStatus.AWAITING_CREDIT_COMMITTEE
            || status == LoanStatus.DISBURSED
            || status == LoanStatus.DEFAULTED;
    }

    private String dashboardStatusLabel(LoanStatus status) {
        return message("loan.status." + status.name());
    }

    private String message(String code) {
        return messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
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
        if (status == LoanStatus.AWAITING_CREDIT_COMMITTEE) {
            return "#8B5CF6";
        }
        if (status == LoanStatus.DISBURSED) {
            return "#22C55E";
        }
        if (status == LoanStatus.DEFAULTED) {
            return "#DC2626";
        }
        if (status == LoanStatus.PAID) {
            return "#0F766E";
        }
        return "#94A3B8";
    }
}
