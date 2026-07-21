package com.sacco.mvp.web;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.ManagerReview;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanPaymentTransactionRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ExternalAccountStatusService;
import com.sacco.mvp.service.EmailOtpService;
import com.sacco.mvp.service.LoanPresentationService;
import com.sacco.mvp.service.LoanProductDisplayService;
import com.sacco.mvp.service.LoanReportService;
import com.sacco.mvp.service.ManagerService;
import com.sacco.mvp.service.NotificationInboxService;
import com.sacco.mvp.service.PaymentDetailsService;
import com.sacco.mvp.service.StationOtpSettingsService;
import com.sacco.mvp.service.WorkflowStatusPresentationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/accountant")
@PreAuthorize("hasRole('ACCOUNTANT') and @userClaims.has(principal, 'REVIEW_ACCOUNTANT_QUEUE')")
public class AccountantController {
    private final ManagerService managerService;
    private final ManagerReviewRepository managerReviewRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;
    private final LoanPresentationService loanPresentationService;
    private final LoanProductDisplayService loanProductDisplayService;
    private final LoanReportService loanReportService;
    private final ExternalAccountStatusService externalAccountStatusService;
    private final NotificationInboxService notificationInboxService;
    private final LoanPaymentTransactionRepository loanPaymentTransactionRepository;
    private final WorkflowStatusPresentationService workflowStatusPresentationService;
    private final PaymentDetailsService paymentDetailsService;
    private final MessageSource messageSource;
    private final EmailOtpService emailOtpService;
    private final StationOtpSettingsService stationOtpSettingsService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        ManagerService.ManagerDashboard dashboard = managerService.dashboard(principal.getSaccoId(), principal.getStationId());
        Map<UUID, String> applicantNames = memberRepository.findAllById(
                dashboard.recentDisbursements().stream()
                    .map(LoanApplication::getApplicantMemberId)
                    .collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));

        model.addAttribute("dashboardBreadcrumb", "Accountant Panel / Dashboard");
        model.addAttribute("dashboardPageTitle", "Accountant Dashboard");
        model.addAttribute("dashboardQueueLabel", "On Review By Accountant");
        model.addAttribute("dashboardQueueValue",
            workflowStatusPresentationService.countFor(dashboard.statusBreakdown(), LoanStatus.AWAITING_ACCOUNTANT));
        model.addAttribute("dashboardQueueFooterLabel", "Queue waiting");
        model.addAttribute("dashboardDetailBasePath", "/accountant/loan-applications");
        model.addAttribute("dashboardTotalDisbursedLoans", dashboard.totalDisbursedLoans());
        model.addAttribute("dashboardTrackedApplicationCount", dashboard.totalLoans());
        model.addAttribute("dashboardDisbursementYear", LocalDate.now().getYear());
        model.addAttribute("dashboardActiveDisbursedLoans", dashboard.activeDisbursedLoans());
        model.addAttribute("dashboardDefaultedLoans", dashboard.defaultedLoansCurrentYear());
        model.addAttribute("dashboardChartTitle", "Accountant Review Chart");
        model.addAttribute("dashboardStatusChartRows",
            workflowStatusPresentationService.buildAccountantDashboardChartRows(
                dashboard.statusBreakdown(),
                principal.getClaims().contains("ACCESS_DISBURSEMENT_QUEUE")
            ));
        model.addAttribute("dashboardDisbursementRows", dashboard.recentDisbursements().stream()
            .map(loan -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", loan.getId().toString());
                row.put("applicantName", applicantNames.getOrDefault(loan.getApplicantMemberId(), "-"));
                row.put("loanTypeLabel", loan.getLoanType().getDisplayLabel());
                row.put("statusLabel", workflowStatusPresentationService.dashboardStatusLabel(loan.getStatus()));
                row.put("shortId", loan.getApplicationNumber() == null ? "" : loan.getApplicationNumber().toString());
                row.put("loanId", loan.getLoanId() == null ? "" : loan.getLoanId());
                row.put("amount", loan.getAmount() == null ? "-" : loan.getAmount().toPlainString());
                row.put("disbursementDate", loan.getDisbursementDate() == null ? "-" : loan.getDisbursementDate().toString());
                return row;
            })
            .toList());
        model.addAttribute("dashboardRecentDisbursementDays", 30);
        return "manager/dashboard";
    }

    @GetMapping("/loan-applications")
    public String queue(@AuthenticationPrincipal AppUserPrincipal principal,
                        @RequestParam(required = false) String filter,
                        @RequestParam(required = false) String searchId,
                        Model model) {
        QueueFilter currentFilter = resolveQueueFilter(filter);
        String normalizedSearchId = normalizeQueueSearch(searchId);
        List<LoanApplication> apps = managerService.queue(
            principal.getSaccoId(),
            currentFilter.statuses(),
            normalizedSearchId,
            false,
            principal.getStationId()
        );
        Map<UUID, String> applicantNames = memberRepository.findAllById(
                apps.stream().map(LoanApplication::getApplicantMemberId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));

        model.addAttribute("apps", apps);
        model.addAttribute("applicantNames", applicantNames);
        model.addAttribute("loanProductNames", loanProductDisplayService.namesForSacco(principal.getSaccoId()));
        model.addAttribute("currentFilterKey", currentFilter.key());
        model.addAttribute("currentFilterLabel", currentFilter.label());
        model.addAttribute("queueSearchValue", normalizedSearchId);
        return "accountant/queue";
    }

    @GetMapping("/archive")
    public String archive(@AuthenticationPrincipal AppUserPrincipal principal,
                          @RequestParam(required = false) String filter,
                          @RequestParam(required = false) String searchId,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        ArchiveFilter currentFilter = resolveArchiveFilter(filter);
        String normalizedSearchId = normalizeQueueSearch(searchId);
        boolean loanIdSearch = currentFilter.usesLoanId();
        org.springframework.data.domain.Page<ManagerReview> archivePage = managerReviewRepository.findLatestArchivePage(
            principal.getMemberId(),
            ApprovalWorkflowStage.ACCOUNTANT.name(),
            principal.getSaccoId(),
            principal.getStationId(),
            currentFilter.decision() != null,
            currentFilter.decision() == null ? ManagerDecision.ACCEPT.name() : currentFilter.decision().name(),
            false,
            List.of(LoanStatus.DRAFT.name()),
            normalizedSearchId,
            loanIdSearch,
            org.springframework.data.domain.PageRequest.of(Math.max(page, 0), 50)
        );
        List<ManagerReview> latestReviews = archivePage.getContent();
        Map<UUID, LoanApplication> loanMap = loadLoansById(latestReviews.stream()
            .map(ManagerReview::getLoanApplicationId)
            .toList());
        List<ArchiveEntry> entries = latestReviews.stream()
            .map(review -> {
                LoanApplication loan = loanMap.get(review.getLoanApplicationId());
                return loan == null ? null : new ArchiveEntry(review, loan);
            })
            .filter(Objects::nonNull)
            .toList();
        Map<UUID, String> applicantNames = loadApplicantNames(entries.stream()
            .map(entry -> entry.loan().getApplicantMemberId())
            .toList());

        model.addAttribute("archiveEntries", entries);
        model.addAttribute("applicantNames", applicantNames);
        model.addAttribute("loanProductNames", loanProductDisplayService.namesForSacco(principal.getSaccoId()));
        model.addAttribute("currentFilterKey", currentFilter.key());
        model.addAttribute("currentFilterLabel", currentFilter.label());
        model.addAttribute("queueSearchValue", normalizedSearchId);
        model.addAttribute("archivePrimaryColumnLabel", loanIdSearch ? message("loan.loanId") : message("loan.applicationId"));
        model.addAttribute("archiveSearchLabel", loanIdSearch ? message("loan.loanId") : message("loan.applicationId"));
        model.addAttribute("archiveSearchPlaceholder", loanIdSearch ? "Search loan ID" : "Search loan application ID");
        model.addAttribute("archiveLoanIdMode", loanIdSearch);
        model.addAttribute("archivePage", archivePage);
        return "accountant/archive";
    }

    @GetMapping("/reports")
    public String reports(@AuthenticationPrincipal AppUserPrincipal principal,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                          @RequestParam(required = false) String decisionFilter,
                          Model model) {
        LocalDate effectiveTo = toDate == null ? LocalDate.now() : toDate;
        LocalDate effectiveFrom = fromDate == null ? effectiveTo.withDayOfMonth(1) : fromDate;
        if (effectiveFrom.isAfter(effectiveTo)) {
            model.addAttribute("error", "From date cannot be after to date.");
            effectiveFrom = effectiveTo.withDayOfMonth(1);
        }
        LoanReportService.AccountantLoanReport report = loanReportService.accountantReport(
            principal.getMemberId(), principal.getSaccoId(), principal.getStationId(), effectiveFrom, effectiveTo, decisionFilter);
        Map<UUID, String> applicantNames = report.applicantMap().values().stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));

        model.addAttribute("report", report);
        model.addAttribute("applicantNames", applicantNames);
        model.addAttribute("fromDateValue", report.fromDate().toString());
        model.addAttribute("toDateValue", report.toDate().toString());
        model.addAttribute("decisionFilterValue", report.decisionFilter());
        model.addAttribute("reportReviewedCount", report.summary().reviewedCount());
        model.addAttribute("reportApprovedCount", report.summary().approvedCount());
        model.addAttribute("reportRejectedCount", report.summary().rejectedCount());
        model.addAttribute("reportReviewedAmountLabel", report.summary().getReviewedAmountLabel());
        model.addAttribute("reportRows", report.entries().stream()
            .map(entry -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", entry.loan().getId().toString());
                row.put("shortId", entry.loan().getApplicationNumber() == null ? "" : entry.loan().getApplicationNumber().toString());
                row.put("loanId", entry.loan().getLoanId() == null ? "" : entry.loan().getLoanId());
                row.put("applicantName", applicantNames.getOrDefault(entry.loan().getApplicantMemberId(), "-"));
                row.put("loanTypeLabel", entry.loan().getLoanType() == null ? "-" : entry.loan().getLoanType().getDisplayLabel());
                row.put("amount", entry.loan().getAmount() == null ? "-" : entry.loan().getAmount().toPlainString());
                row.put("decisionLabel", entry.review().getDecision() == ManagerDecision.ACCEPT
                    ? message("loan.status.READY_FOR_DISBURSEMENT")
                    : message("review.rejected"));
                row.put("reviewedAt", entry.review().getCreatedAt() == null
                    ? "-"
                    : entry.review().getCreatedAt().toLocalDate().toString());
                row.put("currentStatusLabel", humanizeEnum(entry.loan().getStatus().name()));
                return row;
            })
            .toList());
        return "accountant/reports";
    }

    @GetMapping("/loan-applications/{id}")
    public String detail(@PathVariable UUID id,
                         @AuthenticationPrincipal AppUserPrincipal principal,
                         Model model) {
        LoanApplication app = requireVisibleApplication(id, principal.getSaccoId(), principal.getStationId());
        Member applicant = memberRepository.findById(app.getApplicantMemberId()).orElse(null);
        List<GuarantorRequest> guarantorRequests = guarantorRequestRepository.findByLoanApplicationId(id);
        List<Member> guarantorMembers = memberRepository.findAllById(
            guarantorRequests.stream().map(GuarantorRequest::getGuarantorMemberId).collect(Collectors.toSet()));
        Map<UUID, String> guarantorNames = guarantorMembers.stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));
        Map<UUID, Member> guarantorMembersById = guarantorMembers.stream()
            .collect(Collectors.toMap(Member::getId, member -> member));
        List<LoanApplication> activeApplicantLoans = managerService.activeApplicantLoans(
            app.getApplicantMemberId(), app.getId(), principal.getSaccoId());
        Map<UUID, LoanPresentationService.LoanPaymentSummaryView> activeLoanSummaries = activeApplicantLoans.stream()
            .collect(Collectors.toMap(
                LoanApplication::getId,
                this::storedActiveLoanPaymentSummary,
                (left, right) -> left,
                LinkedHashMap::new
            ));

        model.addAttribute("app", app);
        model.addAttribute("applicant", applicant);
        model.addAttribute("loanProductName", loanProductDisplayService.displayName(app));
        model.addAttribute("paymentDetails", paymentDetailsService.resolveForLoan(app));
        model.addAttribute("applicantExternalAccountStatus", externalAccountStatusService.loading("Loading live balances..."));
        model.addAttribute("formFields", parseJsonObject(app.getFormData()));
        model.addAttribute("financialFields", loanPresentationService.parseFinancialFields(app));
        model.addAttribute("financialFieldSections", loanPresentationService.parseFinancialFieldSections(app));
        model.addAttribute("attachments", loanPresentationService.parseApplicationAttachments(app.getAttachmentsJson()));
        model.addAttribute("disbursementProofAttachments", loanPresentationService.parseDisbursementProofAttachments(app.getAttachmentsJson()));
        model.addAttribute("repaymentSummary", loanPresentationService.reviewRepaymentSummary(app));
        model.addAttribute("repaymentSummaryEstimated", loanPresentationService.isEstimatedReviewRepaymentSummary(app));
        var repaymentTransactions = loanPaymentTransactionRepository.findByLoanApplicationIdOrderByReceiptDateAscProviderOrderDesc(app.getId());
        var paymentSummary = loanPresentationService.parseLoanPaymentSummaryView(app.getLoanPaymentSummaryJson());
        boolean hasPaymentRecords = !repaymentTransactions.isEmpty();
        model.addAttribute("generatedRepaymentRows", loanPresentationService.generatedRepaymentRows(app, repaymentTransactions, paymentSummary));
        model.addAttribute("generatedRepaymentRowsArePaymentRecords", hasPaymentRecords);
        model.addAttribute("calculatedRepaymentRows", loanPresentationService.calculatedRepaymentRows(app));
        model.addAttribute("repaymentRows", loanPresentationService.reviewRepaymentRows(
            app,
            repaymentTransactions,
            paymentSummary));
        model.addAttribute("repaymentCountdown", loanPresentationService.countdownLabel(app.getFinalDueDate()));
        model.addAttribute("previousApprovedReviews",
            loanPresentationService.previousApprovedReviews(app, ApprovalWorkflowStage.ACCOUNTANT));
        model.addAttribute("managerReason", loanPresentationService.latestManagerReason(id));
        model.addAttribute("guarantorRequests", guarantorRequests);
        model.addAttribute("guarantorNames", guarantorNames);
        model.addAttribute("guarantorMembersById", guarantorMembersById);
        Map<com.sacco.mvp.domain.LoanType, String> activeLoanProductNames = loanProductDisplayService.namesForSacco(principal.getSaccoId());
        model.addAttribute("activeApplicantLoans", activeApplicantLoans.stream()
            .map(loan -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", loan.getId().toString());
                row.put("shortId", loan.getApplicationNumber() == null ? "" : loan.getApplicationNumber().toString());
                row.put("loanId", loan.getLoanId() == null ? "" : loan.getLoanId());
                row.put("loanTypeLabel", loanProductDisplayService.displayName(loan, activeLoanProductNames));
                row.put("amount", formatMoney(loan.getAmount()));
                row.put("disbursedAt", loan.getDisbursementDate() == null ? "-" : loan.getDisbursementDate().toString());
                row.put("finalDueDate", loan.getFinalDueDate() == null ? "-" : loan.getFinalDueDate().toString());
                row.put("installmentAmount", formatMoney(loan.getInstallmentAmount()));
                row.put("outstandingBalance", activeLoanSummaries.getOrDefault(
                    loan.getId(),
                    LoanPresentationService.LoanPaymentSummaryView.empty()
                ).totalOutstandingLabel());
                row.put("repaymentFrequency", loan.getRepaymentFrequency() == null
                    ? "Standard schedule"
                    : humanizeEnum(loan.getRepaymentFrequency().name()));
                row.put("countdown", loanPresentationService.countdownLabel(loan.getFinalDueDate()));
                row.put("isTopUpSource", String.valueOf(
                    app.getTopUpSourceLoanId() != null && app.getTopUpSourceLoanId().equals(loan.getId())));
                return row;
            })
            .toList());
        model.addAttribute("activeApplicantLoanCount", activeApplicantLoans.size());
        model.addAttribute("activeApplicantLoanTotalAmount", formatMoney(
            activeApplicantLoans.stream()
                .map(LoanApplication::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
        model.addAttribute("reviewBasePath", "/accountant");
        model.addAttribute("reviewPanelBreadcrumb", message("review.accountant.breadcrumb"));
        model.addAttribute("reviewPanelTitle", message("review.accountant.title"));
        model.addAttribute("reviewPanelSubtitle", message("review.accountant.subtitle"));
        model.addAttribute("reviewCommentLabel", message("review.accountant.notes"));
        model.addAttribute("reviewCommentPlaceholder", message("review.accountant.commentPlaceholder"));
        model.addAttribute("approveActionLabel", message("review.accountant.approveForDisbursement"));
        model.addAttribute("rejectActionLabel", message("review.manager.rejectLoan"));
        model.addAttribute("showReviewDecisionForm", app.getStatus() == LoanStatus.AWAITING_ACCOUNTANT);
        model.addAttribute("staffDecisionOtpEnabled", stationOtpSettingsService.requiresApprovalOtp(app.getSaccoId(), app.getStationId()));
        model.addAttribute("showManagerReversalRequests", false);
        model.addAttribute("showDisbursementForm", false);
        model.addAttribute("disbursementNotesLabel", message("loan.disbursement.notes"));
        model.addAttribute("disbursementActionLabel", message("loan.disbursement.action"));
        model.addAttribute("allowPaymentSync", false);
        model.addAttribute("allowDefaultedPaymentRecheck", false);
        addReviewDisplayAttributes(model, app);
        return "manager/detail";
    }

    @GetMapping("/loan-applications/{id}/applicant-financial-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> applicantFinancialStatus(@PathVariable UUID id,
                                                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        LoanApplication app = requireVisibleApplication(id, principal.getSaccoId(), principal.getStationId());
        Member applicant = memberRepository.findById(app.getApplicantMemberId()).orElse(null);
        return ResponseEntity.ok(externalAccountStatusPayload(externalAccountStatusService.resolve(applicant)));
    }

    @GetMapping("/loan-applications/{id}/active-loans/outstanding-balances")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> activeLoanOutstandingBalances(@PathVariable UUID id,
                                                                             @AuthenticationPrincipal AppUserPrincipal principal) {
        LoanApplication app = requireVisibleApplication(id, principal.getSaccoId(), principal.getStationId());
        List<LoanApplication> activeApplicantLoans = managerService.activeApplicantLoans(
            app.getApplicantMemberId(), app.getId(), principal.getSaccoId());
        List<Map<String, String>> rows = activeApplicantLoans.stream()
            .map(loan -> {
                LoanPresentationService.LoanPaymentSummaryView summary = storedActiveLoanPaymentSummary(loan);
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", loan.getId().toString());
                row.put("outstandingBalance", summary.totalOutstandingLabel());
                return row;
            })
            .toList();
        return ResponseEntity.ok(Map.of("rows", rows));
    }

    @GetMapping("/loan-applications/{loanId}/guarantors/{guarantorId}/financial-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> guarantorFinancialStatus(@PathVariable UUID loanId,
                                                                        @PathVariable UUID guarantorId,
                                                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        requireVisibleApplication(loanId, principal.getSaccoId(), principal.getStationId());
        boolean guarantorAssigned = guarantorRequestRepository.findByLoanApplicationId(loanId).stream()
            .anyMatch(request -> guarantorId.equals(request.getGuarantorMemberId()));
        if (!guarantorAssigned) {
            return ResponseEntity.badRequest().body(Map.of("message", "Guarantor request was not found for this loan."));
        }
        Member guarantor = memberRepository.findById(guarantorId)
            .orElseThrow(() -> new IllegalArgumentException("Guarantor not found"));
        return ResponseEntity.ok(externalAccountStatusPayload(externalAccountStatusService.resolve(guarantor)));
    }

    @PostMapping("/loan-applications/{id}/decision")
    public String decide(@PathVariable UUID id,
                         @AuthenticationPrincipal AppUserPrincipal principal,
                         @RequestParam ManagerDecision decision,
                         @RequestParam(required = false) String reasons,
                         @RequestParam(required = false) String managerDecisionOtpCode,
                         RedirectAttributes ra) {
        try {
            LoanApplication app = requireVisibleApplication(id, principal.getSaccoId(), principal.getStationId());
            UUID otpTokenId = stationOtpSettingsService.requiresApprovalOtp(app.getSaccoId(), app.getStationId())
                ? validateAccountantDecisionOtp(principal.getMemberId(), managerDecisionOtpCode)
                : null;
            managerService.decideAccountant(id, principal.getMemberId(), decision, reasons);
            if (otpTokenId != null) {
                emailOtpService.consumeOtpById(otpTokenId);
            }
            ra.addFlashAttribute("message", decision == ManagerDecision.ACCEPT
                ? "Accountant approved the loan for disbursement."
                : "Accountant rejected the loan.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/accountant/loan-applications/" + id;
    }

    @PostMapping("/loan-applications/{id}/request-decision-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestDecisionOtp(@PathVariable UUID id,
                                                                  @AuthenticationPrincipal AppUserPrincipal principal) {
        try {
            LoanApplication application = requireVisibleApplication(id, principal.getSaccoId(), principal.getStationId());
            if (application.getStatus() != LoanStatus.AWAITING_ACCOUNTANT) {
                throw new IllegalStateException("This application is no longer waiting for accountant review.");
            }
            if (!stationOtpSettingsService.requiresApprovalOtp(application.getSaccoId(), application.getStationId())) {
                throw new IllegalStateException("OTP verification is disabled for approval actions at this station.");
            }
            Member accountant = requireMemberWithEmail(principal.getMemberId(), "Add an email address to your member profile before requesting an accountant decision OTP.");
            EmailOtpService.OtpIssueResult otp = emailOtpService.issueOtpWithMetadata(
                accountant.getEmail(),
                EmailOtpPurpose.BOARD_SIGNATURE,
                accountant.getId(),
                "Your SACCO LMS accountant decision code",
                "Use this OTP code to confirm your accountant decision on the loan application.",
                application.getSaccoId(),
                application.getStationId(),
                accountant.getPhone()
            );
            return ResponseEntity.ok(otpIssueResponse(otp, "We sent an accountant decision code using the station OTP delivery policy."));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/loan-applications/{id}/verify-decision-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyDecisionOtp(@PathVariable UUID id,
                                                                 @AuthenticationPrincipal AppUserPrincipal principal,
                                                                 @RequestParam String otpCode) {
        try {
            LoanApplication application = requireVisibleApplication(id, principal.getSaccoId(), principal.getStationId());
            if (application.getStatus() != LoanStatus.AWAITING_ACCOUNTANT) {
                throw new IllegalStateException("This application is no longer waiting for accountant review.");
            }
            if (!stationOtpSettingsService.requiresApprovalOtp(application.getSaccoId(), application.getStationId())) {
                return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "message", "OTP verification is disabled for approval actions at this station."
                ));
            }
            validateAccountantDecisionOtp(principal.getMemberId(), otpCode);
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

    @GetMapping("/notifications")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public String notifications(@AuthenticationPrincipal AppUserPrincipal principal,
                                @RequestParam(required = false) UUID highlight,
                                Model model) {
        model.addAttribute("notifications", notificationInboxService.allViews(
            principal.getMemberId(), principal.getGrantedPositions()));
        model.addAttribute("highlightNotificationId", highlight);
        return "accountant/notifications";
    }

    @GetMapping("/notifications/{id}/open")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public String openNotification(@PathVariable UUID id,
                                   @AuthenticationPrincipal AppUserPrincipal principal,
                                   RedirectAttributes ra) {
        try {
            return "redirect:" + notificationInboxService.openForMember(
                id, principal.getMemberId(), principal.getGrantedPositions(),
                principal.getPosition(), "/accountant/notifications");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/accountant/notifications";
        }
    }

    @PostMapping("/notifications/mark-all-read")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public String markAllNotificationsRead(@AuthenticationPrincipal AppUserPrincipal principal,
                                           RedirectAttributes ra) {
        int updated = notificationInboxService.markAllAsRead(principal.getMemberId());
        ra.addFlashAttribute("message", updated > 0
            ? "All notifications have been marked as read."
            : "There were no unread notifications.");
        return "redirect:/accountant/notifications";
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String handleError(RuntimeException ex, RedirectAttributes ra) {
        ra.addFlashAttribute("error", ex.getMessage());
        return "redirect:/accountant/loan-applications";
    }

    private LoanApplication requireVisibleApplication(UUID id, String saccoId, String stationId) {
        LoanApplication app = managerService.get(id, saccoId, stationId);
        if (app.getStatus() != LoanStatus.AWAITING_ACCOUNTANT
            && app.getStatus() != LoanStatus.ACCOUNTANT_REJECTED
            && app.getStatus() != LoanStatus.READY_FOR_DISBURSEMENT
            && app.getStatus() != LoanStatus.DISBURSED
            && app.getStatus() != LoanStatus.DEFAULTED
            && app.getStatus() != LoanStatus.PAID) {
            throw new IllegalArgumentException("This loan is not available in the accountant panel.");
        }
        return app;
    }

    private Map<String, Object> parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Object> cleaned = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isBlank() || shouldHideField(key)) {
                    continue;
                }
                cleaned.put(humanizeFieldLabel(key), entry.getValue());
            }
            return cleaned;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private boolean shouldHideField(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("_csrf")
            || normalized.equals("additionalnotes");
    }

    private String humanizeFieldLabel(String key) {
        return switch (key) {
            case "purpose" -> "Loan Purpose";
            case "nationalId" -> "National ID";
            case "employerName" -> "Employer Name";
            case "additionalNotes" -> "Additional Notes";
            case "hasExistingLoan" -> "Existing Loan";
            default -> key.replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .trim();
        };
    }

    private String loanTypeLabel(com.sacco.mvp.domain.LoanType loanType) {
        return loanType == null ? "-" : loanType.getDisplayLabel();
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        java.text.DecimalFormat format = new java.text.DecimalFormat(
            "#,##0.##", new java.text.DecimalFormatSymbols(Locale.US));
        return "TSh " + format.format(amount);
    }

    private String humanizeEnum(String value) {
        return value == null ? "-" : value.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private LoanPresentationService.LoanPaymentSummaryView storedActiveLoanPaymentSummary(LoanApplication loan) {
        return loanPresentationService.parseLoanPaymentSummaryView(loan.getLoanPaymentSummaryJson());
    }

    private UUID validateAccountantDecisionOtp(UUID memberId, String otpCode) {
        Member member = requireMemberWithEmail(memberId, "Add an email address to your member profile before confirming this decision.");
        return emailOtpService.validateOtp(member.getEmail(), EmailOtpPurpose.BOARD_SIGNATURE, otpCode);
    }

    private Member requireMemberWithEmail(UUID memberId, String message) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Member account not found."));
        if (member.getEmail() == null || member.getEmail().isBlank()) {
            throw new IllegalStateException(message);
        }
        return member;
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

    private void addReviewDisplayAttributes(Model model, LoanApplication app) {
        model.addAttribute("loanIdShort", app.getApplicationNumber() == null ? "" : app.getApplicationNumber().toString());
        model.addAttribute("disbursedLoanId", app.getLoanId());
        model.addAttribute("showReviewSidebar", app.getDisbursementDate() != null);
        model.addAttribute("managerReviewLayoutClass", app.getDisbursementDate() != null ? "xl:grid-cols-[1.45fr_0.55fr]" : "");
        model.addAttribute("applicantDetailsGridClass", app.getDisbursementDate() != null ? "sm:grid-cols-2" : "sm:grid-cols-2 xl:grid-cols-3");
        model.addAttribute("loanProgressItems", loanPresentationService.buildProgressItems(app));
        model.addAttribute("managerStatusBadgeClass", switch (app.getStatus()) {
            case READY_FOR_MANAGER -> "bg-amber-50 text-amber-700";
            case AWAITING_LOAN_OFFICER, AWAITING_BOARD, AWAITING_CREDIT_COMMITTEE, AWAITING_ACCOUNTANT -> "bg-blue-50 text-blue-700";
            case MANAGER_ACCEPTED, LOAN_OFFICER_APPROVED, BOARD_APPROVED, ACCOUNTANT_APPROVED,
                READY_FOR_DISBURSEMENT, DISBURSED, PAID -> "bg-emerald-50 text-emerald-700";
            case DEFAULTED -> "bg-rose-50 text-rose-700";
            case MANAGER_REJECTED, LOAN_OFFICER_REJECTED, BOARD_REJECTED, ACCOUNTANT_REJECTED, REJECTED -> "bg-rose-50 text-rose-700";
            default -> "bg-slate-100 text-slate-700";
        });
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

    private QueueFilter resolveQueueFilter(String filter) {
        return new QueueFilter("AWAITING_ACCOUNTANT", "On Review By Accountant",
            List.of(LoanStatus.AWAITING_ACCOUNTANT));
    }

    private ArchiveFilter resolveArchiveFilter(String filter) {
        String key = filter == null || filter.isBlank() ? "ALL" : filter.trim().toUpperCase(Locale.ENGLISH);
        return switch (key) {
            case "APPROVED" -> new ArchiveFilter("APPROVED", "Ready for Disbursement", ManagerDecision.ACCEPT, true);
            case "REJECTED" -> new ArchiveFilter("REJECTED", "Rejected", ManagerDecision.REJECT, false);
            default -> new ArchiveFilter("ALL", "All Reviewed Loans", null, false);
        };
    }

    private String normalizeQueueSearch(String searchId) {
        return searchId == null ? "" : searchId.trim();
    }

    private Map<UUID, LoanApplication> loadLoansById(List<UUID> loanIds) {
        return loanIds.isEmpty()
            ? Collections.emptyMap()
            : loanApplicationRepository.findAllById(loanIds).stream()
                .collect(Collectors.toMap(LoanApplication::getId, loan -> loan, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<UUID, String> loadApplicantNames(List<UUID> applicantIds) {
        return applicantIds.isEmpty()
            ? Collections.emptyMap()
            : memberRepository.findAllById(applicantIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getFullName, (left, right) -> left, LinkedHashMap::new));
    }

    private boolean matchesArchiveSearch(LoanApplication app, String searchId, boolean loanIdSearch) {
        if (searchId == null || searchId.isBlank()) {
            return true;
        }
        if (loanIdSearch) {
            String loanId = app.getLoanId();
            return loanId != null && loanId.toLowerCase(Locale.ENGLISH).contains(searchId.toLowerCase(Locale.ENGLISH));
        }
        Long applicationNumber = app.getApplicationNumber();
        return applicationNumber != null && String.valueOf(applicationNumber).contains(searchId);
    }

    private String message(String code) {
        return messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
    }

    private record QueueFilter(String key, String label, List<LoanStatus> statuses) {}

    private record ArchiveFilter(String key, String label, ManagerDecision decision, boolean usesLoanId) {
        private boolean matches(ManagerReview review) {
            return decision == null || review.getDecision() == decision;
        }
    }

    private record ArchiveEntry(ManagerReview review, LoanApplication loan) {}
}
