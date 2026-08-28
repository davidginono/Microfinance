package com.sacco.mvp.web;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.ManagerReview;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.LoanProductDisplayService;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.ActiveLoanDisplayService;
import com.sacco.mvp.service.ArchiveDateRange;
import com.sacco.mvp.service.ForesightRepaymentScheduleService;
import com.sacco.mvp.service.LoanPresentationService;
import com.sacco.mvp.service.LoanReportService;
import com.sacco.mvp.service.ManagerService;
import com.sacco.mvp.service.MemberDirectoryService;
import com.sacco.mvp.service.NotificationInboxService;
import com.sacco.mvp.service.PaymentDetailsService;
import com.sacco.mvp.service.ReversalRequestService;
import com.sacco.mvp.service.StationOtpSettingsService;
import com.sacco.mvp.service.WorkflowStatusPresentationService;
import com.sacco.mvp.service.ExternalAccountStatusService;
import com.sacco.mvp.service.EmailOtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/manager")
@PreAuthorize("@access.canAccessManagerArea(principal)")
public class ManagerController {
    private static final DateTimeFormatter REPORT_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final ManagerService managerService;
    private final MemberDirectoryService memberDirectoryService;
    private final ObjectMapper objectMapper;
    private final LoanPresentationService loanPresentationService;
    private final ActiveLoanDisplayService activeLoanDisplayService;
    private final ForesightRepaymentScheduleService foresightRepaymentScheduleService;
    private final LoanProductDisplayService loanProductDisplayService;
    private final LoanReportService loanReportService;
    private final ReversalRequestService reversalRequestService;
    private final ExternalAccountStatusService externalAccountStatusService;
    private final NotificationInboxService notificationInboxService;
    private final WorkflowStatusPresentationService workflowStatusPresentationService;
    private final EmailOtpService emailOtpService;
    private final PaymentDetailsService paymentDetailsService;
    private final MessageSource messageSource;
    private final StationOtpSettingsService stationOtpSettingsService;
    private final ApplicationClock applicationClock;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        ManagerService.ManagerDashboard dashboard = managerService.dashboard(principal.getSaccoId(), principal.getStationId());
        Map<UUID, String> applicantNames = memberDirectoryService.fullNames(
            dashboard.recentDisbursements().stream()
                .map(com.sacco.mvp.domain.LoanApplication::getApplicantMemberId)
                .collect(Collectors.toSet()));

        model.addAttribute("dashboardTotalDisbursedLoans", dashboard.totalDisbursedLoans());
        model.addAttribute("dashboardTrackedApplicationCount", dashboard.totalLoans());
        model.addAttribute("dashboardDisbursementYear", LocalDate.now().getYear());
        model.addAttribute("dashboardActiveDisbursedLoans", dashboard.activeDisbursedLoans());
        model.addAttribute("dashboardOnReviewByManagerLoans", dashboard.onReviewByManagerLoans());
        model.addAttribute("dashboardQueueValue", dashboard.onReviewByManagerLoans());
        model.addAttribute("dashboardDefaultedLoans", dashboard.defaultedLoansCurrentYear());
        model.addAttribute("dashboardChartTitle", "Station Loan Status Chart");
        model.addAttribute("dashboardStatusChartRows",
            workflowStatusPresentationService.buildManagerDashboardChartRows(dashboard.statusBreakdown()));
        model.addAttribute("dashboardDisbursementRows", dashboard.recentDisbursements().stream()
            .map(loan -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", loan.getId().toString());
                row.put("applicantName", applicantNames.getOrDefault(loan.getApplicantMemberId(), "-"));
                row.put("loanTypeLabel", loan.getLoanType().getDisplayLabel());
                row.put("statusLabel", switch (loan.getStatus()) {
                    case PAID -> message("loan.status.PAID");
                    case PAR -> message("loan.status.PAR");
                    case DEFAULTED -> message("loan.status.DEFAULTED");
                    default -> message("manager.dashboard.disbursedLoan");
                });
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
                        @RequestParam(required = false) LoanStatus status,
                        Model model) {
        QueueFilter currentFilter = new QueueFilter(
            "READY_FOR_MANAGER",
            "On Review By Manager",
            List.of(LoanStatus.READY_FOR_MANAGER)
        );
        boolean loanIdSearch = false;
        String normalizedSearchId = normalizeQueueSearch(searchId);
        List<com.sacco.mvp.domain.LoanApplication> apps = managerService.queue(
            principal.getSaccoId(),
            currentFilter.statuses(),
            normalizedSearchId,
            loanIdSearch,
            principal.getStationId()
        );
        Map<UUID, String> applicantNames = memberDirectoryService.fullNames(
            apps.stream().map(com.sacco.mvp.domain.LoanApplication::getApplicantMemberId).collect(Collectors.toSet()));

        model.addAttribute("apps", apps);
        model.addAttribute("applicantNames", applicantNames);
        model.addAttribute("loanProductNames", loanProductDisplayService.namesForSacco(principal.getSaccoId()));
        model.addAttribute("currentFilterKey", currentFilter.key());
        model.addAttribute("currentFilterLabel", currentFilter.label());
        model.addAttribute("queueSearchValue", normalizedSearchId);
        model.addAttribute("queueSearchLabel", loanIdSearch ? message("loan.loanId") : message("loan.applicationId"));
        model.addAttribute("queueSearchPlaceholder", loanIdSearch ? "Search loan ID" : "Search loan application ID");
        return "manager/queue";
    }

    @GetMapping("/archive")
    public String archive(@AuthenticationPrincipal AppUserPrincipal principal,
                          @RequestParam(required = false) String filter,
                          @RequestParam(required = false) String searchId,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        ArchiveFilter currentFilter = resolveArchiveFilter(filter);
        ArchiveDateRange dateRange = ArchiveDateRange.inclusive(fromDate, toDate, applicationClock);
        String normalizedSearchId = normalizeQueueSearch(searchId);
        boolean loanIdSearch = currentFilter.usesLoanId();
        List<String> statuses = currentFilter.statuses().isEmpty()
            ? List.of(LoanStatus.DRAFT.name())
            : currentFilter.statuses().stream().map(Enum::name).toList();
        org.springframework.data.domain.Page<ManagerReview> archivePage = managerService.archivePage(
            principal.getMemberId(),
            ApprovalWorkflowStage.MANAGER.name(),
            principal.getSaccoId(),
            principal.getStationId(),
            dateRange.fromInclusive(),
            dateRange.toExclusive(),
            currentFilter.decision() != null,
            currentFilter.decision() == null ? ManagerDecision.ACCEPT.name() : currentFilter.decision().name(),
            !currentFilter.statuses().isEmpty(),
            statuses,
            normalizedSearchId,
            loanIdSearch,
            org.springframework.data.domain.PageRequest.of(Math.max(page, 0), 50)
        );
        List<ManagerReview> latestReviews = archivePage.getContent();
        Map<UUID, com.sacco.mvp.domain.LoanApplication> loanMap = loanPresentationService.loansById(
            latestReviews.stream().map(ManagerReview::getLoanApplicationId).collect(Collectors.toSet()));
        List<ArchiveEntry> archiveEntries = latestReviews.stream()
            .map(review -> {
                com.sacco.mvp.domain.LoanApplication loan = loanMap.get(review.getLoanApplicationId());
                return loan == null ? null : new ArchiveEntry(review, loan, dashboardStatusLabel(loan.getStatus()));
            })
            .filter(Objects::nonNull)
            .toList();
        Map<UUID, String> applicantNames = memberDirectoryService.fullNames(
            archiveEntries.stream().map(entry -> entry.loan().getApplicantMemberId()).collect(Collectors.toSet()));
        Map<com.sacco.mvp.domain.LoanType, String> loanProductNames = loanProductDisplayService.namesForSacco(principal.getSaccoId());

        model.addAttribute("archiveRows", archiveEntries.stream()
            .map(entry -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", entry.loan().getId().toString());
                row.put("applicationNumber", entry.loan().getApplicationNumber() == null ? "-" : entry.loan().getApplicationNumber().toString());
                row.put("loanId", entry.loan().getLoanId() == null || entry.loan().getLoanId().isBlank() ? "-" : entry.loan().getLoanId());
                row.put("loanProductName", loanProductDisplayService.displayName(entry.loan(), loanProductNames));
                row.put("applicantName", applicantNames.getOrDefault(entry.loan().getApplicantMemberId(), shortLoanId(entry.loan().getApplicantMemberId())));
                row.put("amount", entry.loan().getAmount() == null ? "-" : entry.loan().getAmount().toPlainString());
                row.put("decisionLabel", entry.review().getDecision() == ManagerDecision.ACCEPT ? message("review.approved") : message("review.rejected"));
                row.put("reason", entry.review().getDecision() == ManagerDecision.REJECT
                    && entry.review().getReasons() != null
                    && !entry.review().getReasons().isBlank()
                    ? entry.review().getReasons()
                    : "-");
                row.put("reviewedAt", entry.review().getCreatedAt() == null ? "-" : REPORT_DATE_TIME.format(entry.review().getCreatedAt()));
                row.put("currentStatusLabel", entry.currentStatusLabel());
                return row;
            })
            .toList());
        model.addAttribute("currentFilterKey", currentFilter.key());
        model.addAttribute("currentFilterLabel", currentFilter.label());
        model.addAttribute("queueSearchValue", normalizedSearchId);
        model.addAttribute("archiveSearchLabel", loanIdSearch ? message("loan.loanId") : message("loan.applicationId"));
        model.addAttribute("archiveSearchPlaceholder", loanIdSearch ? "Search loan ID" : "Search loan application ID");
        model.addAttribute("archivePage", archivePage);
        model.addAttribute("fromDate", dateRange.fromDate());
        model.addAttribute("toDate", dateRange.toDate());
        return "manager/archive";
    }

    @GetMapping("/loan-applications/{id}")
    public String detail(@PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        var app = managerService.get(id, principal.getSaccoId(), principal.getStationId());
        Member applicant = memberDirectoryService.find(app.getApplicantMemberId()).orElse(null);
        List<com.sacco.mvp.domain.GuarantorRequest> guarantorRequests = loanPresentationService.guarantorRequests(id);
        List<Member> guarantorMembers = memberDirectoryService.findAll(
            guarantorRequests.stream().map(com.sacco.mvp.domain.GuarantorRequest::getGuarantorMemberId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<UUID, String> guarantorNames = guarantorMembers.stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));
        Map<UUID, Member> guarantorMembersById = guarantorMembers.stream()
            .collect(Collectors.toMap(Member::getId, member -> member));
        String managerReason =
            app.getStatus() == LoanStatus.MANAGER_REJECTED ? loanPresentationService.latestManagerReason(id) : "";
        List<com.sacco.mvp.domain.LoanApplication> activeApplicantLoans = managerService.activeApplicantLoans(
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
        model.addAttribute("attachments", loanPresentationService.parseApplicationAttachments(app.getAttachmentsJson()));
        model.addAttribute("disbursementProofAttachments", loanPresentationService.parseDisbursementProofAttachments(app.getAttachmentsJson()));
        boolean actualRepaymentScheduleEnabled = isActualRepaymentStatus(app.getStatus());
        model.addAttribute("actualRepaymentScheduleEnabled", actualRepaymentScheduleEnabled);
        model.addAttribute("repaymentSchedulePath", actualRepaymentScheduleEnabled
            ? "/manager/loan-applications/" + app.getId() + "/repayment-schedule"
            : "");
        model.addAttribute("repaymentSummary", loanPresentationService.reviewRepaymentSummary(app));
        model.addAttribute("repaymentSummaryEstimated", loanPresentationService.isEstimatedReviewRepaymentSummary(app));
        model.addAttribute("calculatedRepaymentRows", actualRepaymentScheduleEnabled
            ? List.of()
            : loanPresentationService.calculatedRepaymentRows(app));
        model.addAttribute("repaymentRows", loanPresentationService.reviewRepaymentRows(app));
        model.addAttribute("repaymentCountdown", loanPresentationService.countdownLabel(app.getFinalDueDate()));
        model.addAttribute("previousApprovedReviews",
            loanPresentationService.previousApprovedReviews(app, ApprovalWorkflowStage.MANAGER));
        model.addAttribute("managerReason", managerReason);
        model.addAttribute("guarantorRequests", guarantorRequests);
        model.addAttribute("guarantorNames", guarantorNames);
        model.addAttribute("guarantorMembersById", guarantorMembersById);
        model.addAttribute("pendingManagerStageWithdrawal", reversalRequestService.pendingManagerStageWithdrawal(id));
        ActiveLoanDisplayService.ActiveLoanDisplay activeLoanDisplay =
            activeLoanDisplayService.localStaffReviewRows(principal.getSaccoId(), app, activeApplicantLoans);
        model.addAttribute("activeApplicantLoans", activeLoanDisplay.rows());
        model.addAttribute("activeApplicantLoanCount", activeLoanDisplay.count());
        model.addAttribute("activeApplicantLoanTotalAmount", activeLoanDisplay.totalExposure());
        model.addAttribute("activeApplicantLoansForesightEnabled", true);
        model.addAttribute("reviewBasePath", "/manager");
        model.addAttribute("reviewPanelBreadcrumb", message("review.manager.breadcrumb"));
        model.addAttribute("reviewPanelTitle", message("review.manager.title"));
        model.addAttribute("reviewPanelSubtitle", message("review.manager.subtitle"));
        model.addAttribute("reviewCommentLabel", message("review.manager.commentLabel"));
        model.addAttribute("reviewCommentPlaceholder", message("review.manager.commentPlaceholder"));
        model.addAttribute("approveActionLabel", message("review.manager.approveLoan"));
        model.addAttribute("rejectActionLabel", message("review.manager.rejectLoan"));
        model.addAttribute("showReviewDecisionForm", app.getStatus() == LoanStatus.READY_FOR_MANAGER);
        model.addAttribute("staffDecisionOtpEnabled", stationOtpSettingsService.requiresApprovalOtp(principal.getMemberId()));
        model.addAttribute("showManagerReversalRequests", true);
        model.addAttribute("showDisbursementForm", false);
        model.addAttribute("disbursementNotesLabel", message("review.manager.notes"));
        model.addAttribute("disbursementActionLabel", message("loan.disbursement.action"));
        model.addAttribute("disbursementProofRequired", managerService.isDisbursementProofRequired(app));
        model.addAttribute("allowPaymentSync", true);
        model.addAttribute("allowDefaultedPaymentRecheck", false);
        addReviewDisplayAttributes(model, app, managerReason);
        return "manager/detail";
    }

    @GetMapping("/loan-applications/{id}/applicant-financial-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> applicantFinancialStatus(@PathVariable UUID id,
                                                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        var app = managerService.get(id, principal.getSaccoId(), principal.getStationId());
        Member applicant = memberDirectoryService.find(app.getApplicantMemberId()).orElse(null);
        return ResponseEntity.ok(externalAccountStatusPayload(externalAccountStatusService.resolve(applicant)));
    }

    @GetMapping("/loan-applications/{id}/applicant-active-loans")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> applicantActiveLoans(@PathVariable UUID id,
                                                                    @AuthenticationPrincipal AppUserPrincipal principal) {
        var app = managerService.get(id, principal.getSaccoId(), principal.getStationId());
        Member applicant = memberDirectoryService.find(app.getApplicantMemberId()).orElse(null);
        List<LoanApplication> activeApplicantLoans = managerService.activeApplicantLoans(
            app.getApplicantMemberId(), app.getId(), principal.getSaccoId());
        return ResponseEntity.ok(activeLoanDisplayService.staffReviewRows(
            applicant,
            principal.getSaccoId(),
            app,
            activeApplicantLoans
        ).toPayload());
    }

    @GetMapping("/loan-applications/{id}/repayment-schedule")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> repaymentSchedule(@PathVariable UUID id,
                                                                 @AuthenticationPrincipal AppUserPrincipal principal) {
        var app = managerService.get(id, principal.getSaccoId(), principal.getStationId());
        Member applicant = memberDirectoryService.find(app.getApplicantMemberId()).orElse(null);
        return ResponseEntity.ok(foresightRepaymentScheduleService.loadLocalLoanSchedule(app, applicant).toPayload());
    }

    @GetMapping("/loan-applications/{id}/applicant-active-loans/{loanId}/repayment-schedule")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> applicantActiveLoanRepaymentSchedule(@PathVariable UUID id,
                                                                                   @PathVariable String loanId,
                                                                                   @AuthenticationPrincipal AppUserPrincipal principal) {
        var app = managerService.get(id, principal.getSaccoId(), principal.getStationId());
        Member applicant = memberDirectoryService.find(app.getApplicantMemberId()).orElse(null);
        return ResponseEntity.ok(foresightRepaymentScheduleService
            .loadExternalLoanSchedule(applicant, app.getStationId(), loanId)
            .toPayload());
    }

    @GetMapping("/loan-applications/{loanId}/guarantors/{guarantorId}/financial-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> guarantorFinancialStatus(@PathVariable UUID loanId,
                                                                        @PathVariable UUID guarantorId,
                                                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        managerService.get(loanId, principal.getSaccoId(), principal.getStationId());
        boolean guarantorAssigned = loanPresentationService.guarantorRequests(loanId).stream()
            .anyMatch(request -> guarantorId.equals(request.getGuarantorMemberId()));
        if (!guarantorAssigned) {
            return ResponseEntity.badRequest().body(Map.of("message", "Guarantor request was not found for this loan."));
        }
        Member guarantor = memberDirectoryService.find(guarantorId)
            .orElseThrow(() -> new IllegalArgumentException("Guarantor not found"));
        ExternalAccountStatusService.ExternalAccountStatusView status = externalAccountStatusService.resolve(guarantor);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("available", status.isAvailable());
        payload.put("savingsLabel", status.getSavingsLabel());
        payload.put("sharesLabel", status.getSharesLabel());
        payload.put("statusMessage", status.getStatusMessage());
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/loan-applications/{id}/decision")
    @PreAuthorize("@access.canAccessManagerArea(principal) and @access.canDecide(principal, #decision, 'MANAGER_QUEUE_APPROVE', 'MANAGER_QUEUE_REJECT')")
    public String decide(@PathVariable UUID id,
                         @AuthenticationPrincipal AppUserPrincipal principal,
                         @RequestParam ManagerDecision decision,
                         @RequestParam(required = false) String reasons,
                         @RequestParam(required = false) String managerDecisionOtpCode,
                         RedirectAttributes ra) {
        try {
            LoanApplication app = managerService.get(id, principal.getSaccoId(), principal.getStationId());
            UUID otpTokenId = stationOtpSettingsService.requiresApprovalOtp(principal.getMemberId())
                ? validateStaffDecisionOtp(principal.getMemberId(), managerDecisionOtpCode)
                : null;
            Member manager = requireMemberWithSavedSignature(
                principal.getMemberId(),
                "Save your staff signature before recording this decision.");
            managerService.decide(id, principal.getMemberId(), decision, reasons, manager.getSignatureText(), OffsetDateTime.now());
            if (otpTokenId != null) {
                emailOtpService.consumeOtpById(otpTokenId);
            }
            if (decision == ManagerDecision.ACCEPT) {
                LoanStatus updatedStatus = managerService.get(id, principal.getSaccoId(), principal.getStationId()).getStatus();
                String message = switch (updatedStatus) {
                    case AWAITING_LOAN_OFFICER -> "Manager approved. Status moved to ON REVIEW BY LOAN OFFICER.";
                    case AWAITING_BOARD -> "Manager approved. Status moved to ON REVIEW BY BOARD.";
                    case AWAITING_CREDIT_COMMITTEE -> "Manager approved. Status moved to ON REVIEW BY CREDIT COMMITTEE.";
                    case AWAITING_ACCOUNTANT -> "Manager approved. Status moved to ON REVIEW BY ACCOUNTANT.";
                    case READY_FOR_DISBURSEMENT -> "Manager approved. Loan is now READY FOR DISBURSEMENT.";
                    default -> "Manager approved. The application moved to the next configured stage.";
                };
                ra.addFlashAttribute("message", message);
            } else {
                ra.addFlashAttribute("message", "Manager rejected application.");
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/loan-applications/" + id;
    }

    @PostMapping("/loan-applications/{id}/request-decision-otp")
    @PreAuthorize("@access.canAccessManagerArea(principal) and @access.hasAny(principal, 'MANAGER_QUEUE_APPROVE', 'MANAGER_QUEUE_REJECT')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestDecisionOtp(@PathVariable UUID id,
                                                                  @AuthenticationPrincipal AppUserPrincipal principal) {
        try {
            LoanApplication application = managerService.get(id, principal.getSaccoId(), principal.getStationId());
            if (application.getStatus() != LoanStatus.READY_FOR_MANAGER) {
                throw new IllegalStateException("This application is no longer waiting for manager review.");
            }
            if (!stationOtpSettingsService.requiresApprovalOtp(principal.getMemberId())) {
                throw new IllegalStateException("OTP verification is disabled for approval actions on your account.");
            }
            Member manager = requireMemberWithEmail(principal.getMemberId(), "Add an email address to your member profile before requesting a manager decision OTP.");
            EmailOtpService.OtpIssueResult otp = emailOtpService.issueOtpWithMetadata(
                manager.getEmail(),
                EmailOtpPurpose.BOARD_SIGNATURE,
                manager.getId(),
                "Your SACCO LMS manager decision code",
                "Use this OTP code to confirm your manager decision on the loan application.",
                application.getSaccoId(),
                application.getStationId(),
                manager.getPhone()
            );
            return ResponseEntity.ok(otpIssueResponse(otp, "We sent a manager decision code using the station OTP delivery policy."));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/loan-applications/{id}/verify-decision-otp")
    @PreAuthorize("@access.canAccessManagerArea(principal) and @access.hasAny(principal, 'MANAGER_QUEUE_APPROVE', 'MANAGER_QUEUE_REJECT')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyDecisionOtp(@PathVariable UUID id,
                                                                 @AuthenticationPrincipal AppUserPrincipal principal,
                                                                 @RequestParam String otpCode) {
        try {
            LoanApplication application = managerService.get(id, principal.getSaccoId(), principal.getStationId());
            if (application.getStatus() != LoanStatus.READY_FOR_MANAGER) {
                throw new IllegalStateException("This application is no longer waiting for manager review.");
            }
            if (!stationOtpSettingsService.requiresApprovalOtp(principal.getMemberId())) {
                return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "message", "OTP verification is disabled for approval actions on your account."
                ));
            }
            validateStaffDecisionOtp(principal.getMemberId(), otpCode);
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

    @PostMapping("/loan-applications/{loanId}/reversal-requests/{requestId}/approve")
    @PreAuthorize("@access.canAccessManagerArea(principal) and @access.has(principal, 'MANAGER_QUEUE_APPROVE')")
    public String approveReversalRequest(@PathVariable UUID loanId,
                                         @PathVariable UUID requestId,
                                         @AuthenticationPrincipal AppUserPrincipal principal,
                                         RedirectAttributes ra) {
        try {
            reversalRequestService.decideManagerStageWithdrawal(requestId, principal.getMemberId(), true);
            ra.addFlashAttribute("message", "Removal request approved. The application was removed from manager review.");
            return "redirect:/manager/loan-applications";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/manager/loan-applications/" + loanId;
        }
    }

    @PostMapping("/loan-applications/{loanId}/reversal-requests/{requestId}/reject")
    @PreAuthorize("@access.canAccessManagerArea(principal) and @access.has(principal, 'MANAGER_QUEUE_REJECT')")
    public String rejectReversalRequest(@PathVariable UUID loanId,
                                        @PathVariable UUID requestId,
                                        @AuthenticationPrincipal AppUserPrincipal principal,
                                        @RequestParam(required = false) String decisionReason,
                                        RedirectAttributes ra) {
        try {
            reversalRequestService.decideManagerStageWithdrawal(requestId, principal.getMemberId(), false, decisionReason);
            ra.addFlashAttribute("message", "Removal request declined.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/loan-applications/" + loanId;
    }

    @PostMapping("/loan-applications/{id}/finalize")
    public String finalize(@PathVariable UUID id,
                           @AuthenticationPrincipal AppUserPrincipal principal,
                           @RequestParam String decision,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate disbursementDate,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate firstRepaymentDate,
                           @RequestParam(required = false) String loanId,
                           @RequestParam(required = false) String disbursementReference,
                           @RequestParam(required = false) String disbursementNotes,
                           @RequestParam(required = false) MultipartFile disbursementProofFile,
                           RedirectAttributes ra) {
        ra.addFlashAttribute("error", "Managers can no longer disburse loans. Use the Disbursement/Teller Officer queue instead.");
        return "redirect:/manager/loan-applications/" + id;
    }

    @GetMapping("/reports")
    @PreAuthorize("@access.canAccessManagerArea(principal) and @access.has(principal, 'MANAGER_QUEUE_EXPORT')")
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
        LoanReportService.ManagerWorkflowReport report = loanReportService.managerWorkflowReport(
            principal.getMemberId(), principal.getSaccoId(), principal.getStationId(), effectiveFrom, effectiveTo, decisionFilter);
        Map<UUID, String> applicantNames = report.applicantMap().values().stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));
        model.addAttribute("report", report);
        model.addAttribute("applicantNames", applicantNames);
        model.addAttribute("fromDateValue", report.fromDate().toString());
        model.addAttribute("toDateValue", report.toDate().toString());
        model.addAttribute("decisionFilterValue", report.decisionFilter());
        model.addAttribute("reportRows", report.entries().stream()
            .map(entry -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", entry.loan().getId().toString());
                row.put("shortId", entry.loan().getApplicationNumber() == null ? "" : entry.loan().getApplicationNumber().toString());
                row.put("loanId", entry.loan().getLoanId() == null ? "" : entry.loan().getLoanId());
                row.put("applicantName", applicantNames.getOrDefault(entry.loan().getApplicantMemberId(), "-"));
                row.put("loanTypeLabel", entry.loan().getLoanType() == null ? "-" : entry.loan().getLoanType().getDisplayLabel());
                row.put("amount", entry.loan().getAmount() == null ? "-" : entry.loan().getAmount().toPlainString());
                row.put("managerDecisionLabel", entry.review().getDecision() == ManagerDecision.ACCEPT ? message("review.approved") : message("review.rejected"));
                row.put("reviewedAt", entry.review().getCreatedAt() == null ? "-" : entry.review().getCreatedAt().toLocalDate().toString());
                row.put("currentStatusLabel", dashboardStatusLabel(entry.loan().getStatus()));
                return row;
            })
            .toList());
        return "manager/reports";
    }

    @GetMapping("/settings")
    @PreAuthorize("@access.canAccessManagerArea(principal) and @access.has(principal, 'WORKSPACE_SETTINGS_VIEW')")
    public String settings(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("settings", managerService.getSettings(principal.getSaccoId()));
        return "manager/settings";
    }

    @PostMapping("/settings")
    @PreAuthorize("@access.canAccessManagerArea(principal) and @access.has(principal, 'WORKSPACE_SETTINGS_UPDATE')")
    public String updateSettings(@AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam Integer requiredGuarantors,
                                 @RequestParam Integer boardSize,
                                 @RequestParam Integer boardQuorum,
                                 @RequestParam String defaultLanguage,
                                 RedirectAttributes ra) {
        SaccoSettings settings = managerService.updateSettings(principal.getSaccoId(), requiredGuarantors,
            boardSize, boardQuorum, defaultLanguage);
        ra.addFlashAttribute("message", "Settings updated to quorum=" + settings.getBoardQuorum());
        return "redirect:/manager/settings";
    }

    @GetMapping("/notifications")
    @PreAuthorize("@access.canAccessManagerArea(principal) and @access.has(principal, 'NOTIFICATIONS_VIEW')")
    public String notifications(@AuthenticationPrincipal AppUserPrincipal principal,
                                @RequestParam(required = false) UUID highlight,
                                Model model) {
        model.addAttribute("notifications", notificationInboxService.allViews(principal));
        model.addAttribute("highlightNotificationId", highlight);
        return "manager/notifications";
    }

    @GetMapping("/notifications/{id}/open")
    @PreAuthorize("@access.canAccessManagerArea(principal) and @access.has(principal, 'NOTIFICATIONS_VIEW')")
    public String openNotification(@PathVariable UUID id,
                                   @AuthenticationPrincipal AppUserPrincipal principal,
                                   RedirectAttributes ra) {
        try {
            return "redirect:" + notificationInboxService.openForMember(id, principal, "/manager/notifications");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/manager/notifications";
        }
    }

    @PostMapping("/notifications/mark-all-read")
    @PreAuthorize("@access.canAccessManagerArea(principal) and @access.has(principal, 'NOTIFICATIONS_UPDATE')")
    public String markAllNotificationsRead(@AuthenticationPrincipal AppUserPrincipal principal,
                                           RedirectAttributes ra) {
        int updated = notificationInboxService.markAllAsRead(principal.getMemberId());
        if (updated > 0) {
            ra.addFlashAttribute("message", "All notifications have been marked as read.");
        } else {
            ra.addFlashAttribute("message", "There were no unread notifications.");
        }
        return "redirect:/manager/notifications";
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String handleError(RuntimeException ex, RedirectAttributes ra) {
        ra.addFlashAttribute("error", ex.getMessage());
        return "redirect:/manager/loan-applications";
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
        if (loanType == null) {
            return "-";
        }
        return loanType.getDisplayLabel();
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        java.text.DecimalFormat format = new java.text.DecimalFormat(
            "#,##0.##", new java.text.DecimalFormatSymbols(Locale.US));
        return "TSh " + format.format(amount);
    }

    private String shortLoanId(UUID id) {
        return id == null ? "-" : id.toString().substring(0, 8);
    }

    private String humanizeEnum(String value) {
        return value == null ? "-" : value.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private String dashboardStatusLabel(LoanStatus status) {
        return workflowStatusPresentationService.dashboardStatusLabel(status);
    }

    private String dashboardStatusColor(LoanStatus status) {
        return workflowStatusPresentationService.dashboardStatusColor(status);
    }

    private boolean isActualRepaymentStatus(LoanStatus status) {
        return status == LoanStatus.DISBURSED || status == LoanStatus.DEFAULTED || status == LoanStatus.PAID;
    }

    private void addReviewDisplayAttributes(Model model,
                                            com.sacco.mvp.domain.LoanApplication app,
                                            String managerReason) {
        boolean showReviewSidebar = (managerReason != null && !managerReason.isBlank())
            || app.getStatus() == LoanStatus.AWAITING_BOARD
            || app.getStatus() == LoanStatus.AWAITING_CREDIT_COMMITTEE
            || app.getDisbursementDate() != null;

        model.addAttribute("loanIdShort", app.getApplicationNumber() == null ? "" : app.getApplicationNumber().toString());
        model.addAttribute("disbursedLoanId", app.getLoanId());
        model.addAttribute("showReviewSidebar", showReviewSidebar);
        model.addAttribute("managerReviewLayoutClass", showReviewSidebar ? "xl:grid-cols-[1.45fr_0.55fr]" : "");
        model.addAttribute("applicantDetailsGridClass", showReviewSidebar ? "sm:grid-cols-2" : "sm:grid-cols-2 xl:grid-cols-3");
        model.addAttribute("loanProgressItems", loanPresentationService.buildProgressItems(app));
        model.addAttribute("managerStatusBadgeClass", switch (app.getStatus()) {
            case READY_FOR_MANAGER -> "bg-amber-50 text-amber-700";
            case AWAITING_BOARD, AWAITING_CREDIT_COMMITTEE -> "bg-blue-50 text-blue-700";
            case MANAGER_ACCEPTED, BOARD_APPROVED, DISBURSED, PAID -> "bg-emerald-50 text-emerald-700";
            case PAR -> "bg-amber-50 text-amber-700";
            case DEFAULTED -> "bg-rose-50 text-rose-700";
            case MANAGER_REJECTED, BOARD_REJECTED, REJECTED -> "bg-rose-50 text-rose-700";
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

    private QueueFilter resolveQueueFilter(String filter, LoanStatus status) {
        String key = normalizeQueueFilterKey(filter, status);
        return switch (key) {
            case "AWAITING_BOARD" -> new QueueFilter(
                "AWAITING_BOARD",
                "On Review By Board",
                List.of(LoanStatus.AWAITING_BOARD)
            );
            case "DISBURSED" -> new QueueFilter(
                "DISBURSED",
                "Disbursed Loans",
                List.of(LoanStatus.DISBURSED, LoanStatus.PAR, LoanStatus.DEFAULTED, LoanStatus.PAID)
            );
            default -> new QueueFilter(
                "READY_FOR_MANAGER",
                "On Review By Manager",
                List.of(LoanStatus.READY_FOR_MANAGER)
            );
        };
    }

    private String normalizeQueueFilterKey(String filter, LoanStatus status) {
        if (filter != null && !filter.isBlank()) {
            return filter.trim().toUpperCase(Locale.ENGLISH);
        }
        if (status == null) {
            return "READY_FOR_MANAGER";
        }
        return switch (status) {
            case READY_FOR_MANAGER -> "READY_FOR_MANAGER";
            case AWAITING_BOARD -> "AWAITING_BOARD";
            case DISBURSED, PAR, DEFAULTED, PAID -> "DISBURSED";
            default -> "READY_FOR_MANAGER";
        };
    }

    private ArchiveFilter resolveArchiveFilter(String filter) {
        String key = filter == null || filter.isBlank() ? "ALL" : filter.trim().toUpperCase(Locale.ENGLISH);
        return switch (key) {
            case "APPROVED" -> new ArchiveFilter("APPROVED", "Approved Loans", ManagerDecision.ACCEPT, List.of(), false);
            case "REJECTED" -> new ArchiveFilter("REJECTED", "Rejected Loans", ManagerDecision.REJECT, List.of(), false);
            case "DISBURSED", "APPROVED_FOR_DISBURSEMENT" -> new ArchiveFilter("DISBURSED", "Disbursed Loans", null,
                List.of(LoanStatus.DISBURSED, LoanStatus.PAR, LoanStatus.DEFAULTED, LoanStatus.PAID), true);
            default -> new ArchiveFilter("ALL", "All Reviewed Loans", null, List.of(), false);
        };
    }

    private String normalizeQueueSearch(String searchId) {
        return searchId == null ? "" : searchId.trim();
    }

    private UUID validateStaffDecisionOtp(UUID memberId, String otpCode) {
        Member member = requireMemberWithEmail(memberId, "Add an email address to your member profile before confirming this decision.");
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
        Member member = memberDirectoryService.find(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Member account not found."));
        if (member.getEmail() == null || member.getEmail().isBlank()) {
            throw new IllegalStateException(missingEmailMessage);
        }
        return member;
    }

    private Member requireMemberWithSavedSignature(UUID memberId, String missingSignatureMessage) {
        Member member = memberDirectoryService.find(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Member account not found."));
        if (member.getSignatureText() == null || member.getSignatureText().isBlank()) {
            throw new IllegalStateException(missingSignatureMessage);
        }
        return member;
    }

    private String message(String code) {
        return messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
    }

    private record QueueFilter(String key, String label, List<LoanStatus> statuses) {}

    private record ArchiveFilter(String key,
                                 String label,
                                 ManagerDecision decision,
                                 List<LoanStatus> statuses,
                                 boolean usesLoanId) {
        private boolean matches(ManagerReview review, com.sacco.mvp.domain.LoanApplication loan) {
            boolean decisionMatches = decision == null || review.getDecision() == decision;
            boolean statusMatches = statuses == null || statuses.isEmpty() || statuses.contains(loan.getStatus());
            return decisionMatches && statusMatches;
        }
    }

    private record ArchiveEntry(ManagerReview review,
                                com.sacco.mvp.domain.LoanApplication loan,
                                String currentStatusLabel) {}
}
