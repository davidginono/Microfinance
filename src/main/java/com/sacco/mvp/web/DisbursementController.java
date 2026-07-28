package com.sacco.mvp.web;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.ManagerReview;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
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
import com.sacco.mvp.service.WorkflowStatusPresentationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/disbursement")
@PreAuthorize("@authz.notSuperAdmin(principal) and (@userClaims.has(principal, 'ACCESS_DISBURSEMENT_QUEUE') or @userClaims.has(principal, 'DISBURSE_LOAN'))")
public class DisbursementController {
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
    private final WorkflowStatusPresentationService workflowStatusPresentationService;
    private final EmailOtpService emailOtpService;
    private final PaymentDetailsService paymentDetailsService;
    private final MessageSource messageSource;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        ManagerService.ManagerDashboard dashboard = managerService.dashboard(principal.getSaccoId(), principal.getStationId());
        Map<UUID, String> applicantNames = memberRepository.findAllById(
                dashboard.recentDisbursements().stream()
                    .map(LoanApplication::getApplicantMemberId)
                    .collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));

        model.addAttribute("dashboardBreadcrumb", "Disbursement Panel / Dashboard");
        model.addAttribute("dashboardPageTitle", "Disbursement Dashboard");
        model.addAttribute("dashboardQueueLabel", "Ready for Disbursement");
        model.addAttribute("dashboardQueueValue",
            workflowStatusPresentationService.countFor(dashboard.statusBreakdown(), LoanStatus.READY_FOR_DISBURSEMENT));
        model.addAttribute("dashboardQueueFooterLabel", "Queue waiting");
        model.addAttribute("dashboardDetailBasePath", "/disbursement/loan-applications");
        model.addAttribute("dashboardTotalDisbursedLoans", dashboard.totalDisbursedLoans());
        model.addAttribute("dashboardTrackedApplicationCount", dashboard.totalLoans());
        model.addAttribute("dashboardDisbursementYear", LocalDate.now().getYear());
        model.addAttribute("dashboardActiveDisbursedLoans", dashboard.activeDisbursedLoans());
        model.addAttribute("dashboardDefaultedLoans", dashboard.defaultedLoansCurrentYear());
        model.addAttribute("dashboardChartTitle", "Disbursement Status Chart");
        model.addAttribute("dashboardStatusChartRows",
            workflowStatusPresentationService.buildDisbursementDashboardChartRows(dashboard.statusBreakdown()));
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
        model.addAttribute("queueSearchLabel", "Loan Application ID");
        model.addAttribute("queueSearchPlaceholder", "Search loan application ID");
        return "disbursement/queue";
    }

    @GetMapping("/archive")
    public String archive(@AuthenticationPrincipal AppUserPrincipal principal,
                          @RequestParam(required = false) String filter,
                          @RequestParam(required = false) String searchId,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        ArchiveFilter currentFilter = resolveArchiveFilter(filter);
        String normalizedSearchId = normalizeQueueSearch(searchId);
        List<String> statuses = currentFilter.status() == null
            ? List.of(LoanStatus.DRAFT.name())
            : List.of(currentFilter.status().name());
        org.springframework.data.domain.Page<ManagerReview> archivePage = managerReviewRepository.findLatestArchivePage(
            principal.getMemberId(),
            ApprovalWorkflowStage.DISBURSEMENT_OFFICER.name(),
            principal.getSaccoId(),
            principal.getStationId(),
            false,
            ManagerDecision.ACCEPT.name(),
            currentFilter.status() != null,
            statuses,
            normalizedSearchId,
            true,
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
        Map<com.sacco.mvp.domain.LoanType, String> loanProductNames = loanProductDisplayService.namesForSacco(principal.getSaccoId());

        model.addAttribute("archiveRows", entries.stream()
            .map(entry -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", entry.loan().getId().toString());
                row.put("loanId", entry.loan().getLoanId() == null ? "-" : entry.loan().getLoanId());
                row.put("applicationNumber", entry.loan().getApplicationNumber() == null ? "-" : entry.loan().getApplicationNumber().toString());
                row.put("loanProductName", loanProductDisplayService.displayName(entry.loan(), loanProductNames));
                row.put("applicantName", applicantNames.getOrDefault(entry.loan().getApplicantMemberId(), "-"));
                row.put("amount", entry.loan().getAmount() == null ? "-" : entry.loan().getAmount().toPlainString());
                row.put("disbursedAt", entry.review().getCreatedAt() == null ? "-" : entry.review().getCreatedAt().toLocalDate().toString());
                row.put("currentStatusLabel", workflowStatusPresentationService.dashboardStatusLabel(entry.loan().getStatus()));
                return row;
            })
            .toList());
        model.addAttribute("currentFilterKey", currentFilter.key());
        model.addAttribute("currentFilterLabel", currentFilter.label());
        model.addAttribute("queueSearchValue", normalizedSearchId);
        model.addAttribute("archivePage", archivePage);
        return "disbursement/archive";
    }

    @GetMapping("/reports")
    public String reports(@AuthenticationPrincipal AppUserPrincipal principal,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                          Model model) {
        LocalDate effectiveTo = toDate == null ? LocalDate.now() : toDate;
        LocalDate effectiveFrom = fromDate == null ? effectiveTo.withDayOfMonth(1) : fromDate;
        if (effectiveFrom.isAfter(effectiveTo)) {
            model.addAttribute("error", "From date cannot be after to date.");
            effectiveFrom = effectiveTo.withDayOfMonth(1);
        }
        LoanReportService.DisbursementLoanReport report = loanReportService.disbursementReport(
            principal.getMemberId(), principal.getSaccoId(), principal.getStationId(), effectiveFrom, effectiveTo);
        Map<UUID, String> applicantNames = report.applicantMap().values().stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));

        model.addAttribute("report", report);
        model.addAttribute("applicantNames", applicantNames);
        model.addAttribute("fromDateValue", report.fromDate().toString());
        model.addAttribute("toDateValue", report.toDate().toString());
        model.addAttribute("reportRows", report.entries().stream()
            .map(entry -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", entry.loan().getId().toString());
                row.put("shortId", entry.loan().getApplicationNumber() == null ? "" : entry.loan().getApplicationNumber().toString());
                row.put("loanId", entry.loan().getLoanId() == null ? "" : entry.loan().getLoanId());
                row.put("applicantName", applicantNames.getOrDefault(entry.loan().getApplicantMemberId(), "-"));
                row.put("loanTypeLabel", entry.loan().getLoanType() == null ? "-" : entry.loan().getLoanType().getDisplayLabel());
                row.put("amount", entry.loan().getAmount() == null ? "-" : entry.loan().getAmount().toPlainString());
                row.put("disbursedAt", entry.review().getCreatedAt() == null ? "-" : entry.review().getCreatedAt().toLocalDate().toString());
                row.put("currentStatusLabel", workflowStatusPresentationService.dashboardStatusLabel(entry.loan().getStatus()));
                return row;
            })
            .toList());
        return "disbursement/reports";
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

        model.addAttribute("app", app);
        model.addAttribute("applicant", applicant);
        model.addAttribute("loanProductName", loanProductDisplayService.displayName(app));
        model.addAttribute("paymentDetails", paymentDetailsService.resolveForLoan(app));
        model.addAttribute("applicantExternalAccountStatus", externalAccountStatusService.loading("Loading live balances..."));
        model.addAttribute("formFields", parseJsonObject(app.getFormData()));
        model.addAttribute("financialFields", loanPresentationService.parseFinancialFields(app));
        model.addAttribute("financialFieldSections", loanPresentationService.parseFinancialFieldSections(app));
        model.addAttribute("totalDeductions", loanPresentationService.totalDeductions(app));
        model.addAttribute("deductibleFeeRows", loanPresentationService.deductibleFeeRows(app));
        model.addAttribute("attachments", loanPresentationService.parseApplicationAttachments(app.getAttachmentsJson()));
        model.addAttribute("disbursementProofAttachments", loanPresentationService.parseDisbursementProofAttachments(app.getAttachmentsJson()));
        model.addAttribute("repaymentSummary", loanPresentationService.reviewRepaymentSummary(app));
        model.addAttribute("repaymentSummaryEstimated", loanPresentationService.isEstimatedReviewRepaymentSummary(app));
        model.addAttribute("calculatedRepaymentRows", loanPresentationService.calculatedRepaymentRows(app));
        model.addAttribute("repaymentRows", loanPresentationService.reviewRepaymentRows(app));
        model.addAttribute("repaymentCountdown", loanPresentationService.countdownLabel(app.getFinalDueDate()));
        model.addAttribute("previousApprovedReviews",
            loanPresentationService.previousApprovedReviews(app, ApprovalWorkflowStage.DISBURSEMENT_OFFICER));
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
                row.put("outstandingBalance", loan.getStatus() == LoanStatus.PAID ? formatMoney(BigDecimal.ZERO) : formatMoney(loan.getAmount()));
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
        model.addAttribute("reviewBasePath", "/disbursement");
        model.addAttribute("reviewPanelBreadcrumb", message("review.disbursement.breadcrumb"));
        model.addAttribute("reviewPanelTitle", message("review.disbursement.title"));
        model.addAttribute("reviewPanelSubtitle", message("review.disbursement.subtitle"));
        model.addAttribute("reviewCommentLabel", "Notes");
        model.addAttribute("reviewCommentPlaceholder", "Record any operational notes");
        model.addAttribute("approveActionLabel", "Approve");
        model.addAttribute("rejectActionLabel", "Reject");
        model.addAttribute("showReviewDecisionForm", false);
        model.addAttribute("showManagerReversalRequests", false);
        boolean canDisburseLoan = principal.getClaims().contains("DISBURSE_LOAN");
        model.addAttribute("showDisbursementForm", app.getStatus() == LoanStatus.READY_FOR_DISBURSEMENT && canDisburseLoan);
        model.addAttribute("showDisbursementPermissionMessage", app.getStatus() == LoanStatus.READY_FOR_DISBURSEMENT && !canDisburseLoan);
        model.addAttribute("disbursementNotesLabel", message("loan.disbursement.notes"));
        model.addAttribute("disbursementActionLabel", message("loan.disbursement.action"));
        model.addAttribute("disbursementProofRequired", managerService.isDisbursementProofRequired(app));
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

    @PostMapping("/loan-applications/{id}/finalize")
    @PreAuthorize("@authz.notSuperAdmin(principal) and @userClaims.has(principal, 'DISBURSE_LOAN')")
    public String finalize(@PathVariable UUID id,
                           @AuthenticationPrincipal AppUserPrincipal principal,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate disbursementDate,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate firstRepaymentDate,
                           @RequestParam(required = false) BigDecimal depositAmount,
                           @RequestParam(required = false) String loanId,
                           @RequestParam(required = false) String disbursementReference,
                           @RequestParam(required = false) String disbursementNotes,
                           @RequestParam(required = false) String disbursementOtpCode,
                           @RequestParam(required = false) MultipartFile disbursementProofFile,
                           RedirectAttributes ra) {
        try {
            UUID otpTokenId = validateDisbursementOtp(principal.getMemberId(), disbursementOtpCode);
            managerService.disburseLoan(
                id,
                principal.getMemberId(),
                disbursementDate,
                firstRepaymentDate,
                null,
                null,
                depositAmount,
                loanId,
                disbursementReference,
                disbursementNotes,
                disbursementProofFile
            );
            emailOtpService.consumeOtpById(otpTokenId);
            ra.addFlashAttribute("message", "Loan disbursed successfully.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/disbursement/loan-applications/" + id;
    }

    @PostMapping("/loan-applications/{id}/request-disbursement-otp")
    @PreAuthorize("@authz.notSuperAdmin(principal) and @userClaims.has(principal, 'DISBURSE_LOAN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestDisbursementOtp(@PathVariable UUID id,
                                                                      @AuthenticationPrincipal AppUserPrincipal principal) {
        try {
            LoanApplication app = requireVisibleApplication(id, principal.getSaccoId(), principal.getStationId());
            if (app.getStatus() != LoanStatus.READY_FOR_DISBURSEMENT) {
                throw new IllegalStateException("This loan is no longer ready for disbursement.");
            }
            Member officer = requireMemberWithEmail(principal.getMemberId(), "Add an email address to your member profile before requesting a disbursement OTP.");
            EmailOtpService.OtpIssueResult otp = emailOtpService.issueOtpWithMetadata(
                officer.getEmail(),
                EmailOtpPurpose.BOARD_SIGNATURE,
                officer.getId(),
                "Your SACCO LMS disbursement code",
                "Use this OTP code to confirm the loan disbursement action.",
                app.getSaccoId(),
                app.getStationId(),
                officer.getPhone()
            );
            return ResponseEntity.ok(otpIssueResponse(otp, "We sent a disbursement code using the station OTP delivery policy."));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/loan-applications/{id}/verify-disbursement-otp")
    @PreAuthorize("@authz.notSuperAdmin(principal) and @userClaims.has(principal, 'DISBURSE_LOAN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyDisbursementOtp(@PathVariable UUID id,
                                                                     @AuthenticationPrincipal AppUserPrincipal principal,
                                                                     @RequestParam String otpCode) {
        try {
            LoanApplication app = requireVisibleApplication(id, principal.getSaccoId(), principal.getStationId());
            if (app.getStatus() != LoanStatus.READY_FOR_DISBURSEMENT) {
                throw new IllegalStateException("This loan is no longer ready for disbursement.");
            }
            validateDisbursementOtp(principal.getMemberId(), otpCode);
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
    @PreAuthorize("@authz.notSuperAdmin(principal) and @userClaims.has(principal, 'ACCESS_DISBURSEMENT_QUEUE')")
    public String notifications(@AuthenticationPrincipal AppUserPrincipal principal,
                                @RequestParam(required = false) UUID highlight,
                                Model model) {
        model.addAttribute("notifications", notificationInboxService.allViews(
            principal.getMemberId(), principal.getGrantedPositions()));
        model.addAttribute("highlightNotificationId", highlight);
        return "disbursement/notifications";
    }

    @GetMapping("/notifications/{id}/open")
    @PreAuthorize("@authz.notSuperAdmin(principal) and @userClaims.has(principal, 'ACCESS_DISBURSEMENT_QUEUE')")
    public String openNotification(@PathVariable UUID id,
                                   @AuthenticationPrincipal AppUserPrincipal principal,
                                   RedirectAttributes ra) {
        try {
            return "redirect:" + notificationInboxService.openForMember(
                id, principal.getMemberId(), principal.getGrantedPositions(),
                principal.getPosition(), "/disbursement/notifications");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/disbursement/notifications";
        }
    }

    @PostMapping("/notifications/mark-all-read")
    @PreAuthorize("@authz.notSuperAdmin(principal) and @userClaims.has(principal, 'ACCESS_DISBURSEMENT_QUEUE')")
    public String markAllNotificationsRead(@AuthenticationPrincipal AppUserPrincipal principal,
                                           RedirectAttributes ra) {
        int updated = notificationInboxService.markAllAsRead(principal.getMemberId());
        ra.addFlashAttribute("message", updated > 0
            ? "All notifications have been marked as read."
            : "There were no unread notifications.");
        return "redirect:/disbursement/notifications";
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String handleError(RuntimeException ex, RedirectAttributes ra) {
        ra.addFlashAttribute("error", ex.getMessage());
        return "redirect:/disbursement/loan-applications";
    }

    private LoanApplication requireVisibleApplication(UUID id, String saccoId, String stationId) {
        LoanApplication app = managerService.get(id, saccoId, stationId);
        if (app.getStatus() != LoanStatus.READY_FOR_DISBURSEMENT
            && app.getStatus() != LoanStatus.DISBURSED
            && app.getStatus() != LoanStatus.DEFAULTED
            && app.getStatus() != LoanStatus.PAID) {
            throw new IllegalArgumentException("This loan is not available in the disbursement panel.");
        }
        return app;
    }

    private UUID validateDisbursementOtp(UUID memberId, String otpCode) {
        Member member = requireMemberWithEmail(memberId, "Add an email address to your member profile before confirming disbursement.");
        return emailOtpService.validateOtp(member.getEmail(), EmailOtpPurpose.BOARD_SIGNATURE, otpCode);
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

    private Member requireMemberWithEmail(UUID memberId, String missingEmailMessage) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Member account not found."));
        if (member.getEmail() == null || member.getEmail().isBlank()) {
            throw new IllegalStateException(missingEmailMessage);
        }
        return member;
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
        return new QueueFilter("READY_FOR_DISBURSEMENT", "Ready for Disbursement",
            List.of(LoanStatus.READY_FOR_DISBURSEMENT));
    }

    private ArchiveFilter resolveArchiveFilter(String filter) {
        String key = filter == null || filter.isBlank() ? "ALL" : filter.trim().toUpperCase(Locale.ENGLISH);
        return switch (key) {
            case "PAID" -> new ArchiveFilter("PAID", "Paid Loans", LoanStatus.PAID);
            case "DEFAULTED" -> new ArchiveFilter("DEFAULTED", "Defaulted Loans", LoanStatus.DEFAULTED);
            case "DISBURSED" -> new ArchiveFilter("DISBURSED", "Disbursed Loans", LoanStatus.DISBURSED);
            default -> new ArchiveFilter("ALL", "All Disbursed Loans", null);
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

    private boolean matchesArchiveSearch(LoanApplication app, String searchId) {
        if (searchId == null || searchId.isBlank()) {
            return true;
        }
        String loanId = app.getLoanId();
        return loanId != null && loanId.toLowerCase(Locale.ENGLISH).contains(searchId.toLowerCase(Locale.ENGLISH));
    }

    private String message(String code) {
        return messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
    }

    private record QueueFilter(String key, String label, List<LoanStatus> statuses) {}

    private record ArchiveFilter(String key, String label, LoanStatus status) {
        private boolean matches(LoanApplication loan) {
            return status == null || loan.getStatus() == status;
        }
    }

    private record ArchiveEntry(ManagerReview review, LoanApplication loan) {}
}
