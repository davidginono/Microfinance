package com.sacco.mvp.web;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.ActiveLoanDisplayService;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.ArchiveDateRange;
import com.sacco.mvp.service.BoardService;
import com.sacco.mvp.service.EmailOtpService;
import com.sacco.mvp.service.ExternalAccountStatusService;
import com.sacco.mvp.service.ForesightRepaymentScheduleService;
import com.sacco.mvp.service.LoanPresentationService;
import com.sacco.mvp.service.LoanProductDisplayService;
import com.sacco.mvp.service.LoanReportService;
import com.sacco.mvp.service.ManagerService;
import com.sacco.mvp.service.MemberDirectoryService;
import com.sacco.mvp.service.NotificationInboxService;
import com.sacco.mvp.service.PaymentDetailsService;
import com.sacco.mvp.service.StationOtpSettingsService;
import com.sacco.mvp.service.WorkflowStatusPresentationService;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/loan-officer")
@PreAuthorize("@access.canAccessLoanOfficerArea(principal)")
public class LoanOfficerController {
    private static final ApprovalWorkflowStage STAGE = ApprovalWorkflowStage.LOAN_OFFICER;

    private final BoardService boardService;
    private final MemberDirectoryService memberDirectoryService;
    private final ObjectMapper objectMapper;
    private final LoanPresentationService loanPresentationService;
    private final ActiveLoanDisplayService activeLoanDisplayService;
    private final LoanProductDisplayService loanProductDisplayService;
    private final ExternalAccountStatusService externalAccountStatusService;
    private final EmailOtpService emailOtpService;
    private final NotificationInboxService notificationInboxService;
    private final ManagerService managerService;
    private final WorkflowStatusPresentationService workflowStatusPresentationService;
    private final PaymentDetailsService paymentDetailsService;
    private final LoanReportService loanReportService;
    private final ApplicationClock applicationClock;
    private final StationOtpSettingsService stationOtpSettingsService;
    private final AccessControlService access;
    private final ForesightRepaymentScheduleService foresightRepaymentScheduleService;

    @GetMapping("/assigned")
    public String assigned() {
        return "redirect:/loan-officer/queue";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        ManagerService.ManagerDashboard dashboard = managerService.dashboard(principal.getSaccoId(), principal.getStationId());
        Map<UUID, String> applicantNames = memberDirectoryService.fullNames(
            dashboard.recentDisbursements().stream()
                .map(LoanApplication::getApplicantMemberId)
                .collect(Collectors.toSet()));

        model.addAttribute("dashboardBreadcrumb", "Loan Officer Panel / Dashboard");
        model.addAttribute("dashboardPageTitle", "Loan Officer Dashboard");
        model.addAttribute("dashboardQueueLabel", "On Review By Loan Officer");
        model.addAttribute("dashboardQueueValue",
            workflowStatusPresentationService.countFor(dashboard.statusBreakdown(), LoanStatus.AWAITING_LOAN_OFFICER));
        model.addAttribute("dashboardQueueFooterLabel", "Queue waiting");
        model.addAttribute("dashboardDetailBasePath", "/loan-officer/loan-applications");
        model.addAttribute("dashboardTotalDisbursedLoans", dashboard.totalDisbursedLoans());
        model.addAttribute("dashboardTrackedApplicationCount", dashboard.totalLoans());
        model.addAttribute("dashboardDisbursementYear", java.time.LocalDate.now().getYear());
        model.addAttribute("dashboardActiveDisbursedLoans", dashboard.activeDisbursedLoans());
        model.addAttribute("dashboardDefaultedLoans", dashboard.defaultedLoansCurrentYear());
        model.addAttribute("dashboardChartTitle", "Loan Officer Review Chart");
        model.addAttribute("dashboardStatusChartRows",
            workflowStatusPresentationService.buildLoanOfficerDashboardChartRows(
                dashboard.statusBreakdown(),
                access.canAccessDisbursementArea(principal)
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

    @GetMapping("/queue")
    public String queue(@AuthenticationPrincipal AppUserPrincipal principal,
                        @RequestParam(required = false) String searchId,
                        Model model) {
        applyLoanOfficerUi(model);
        List<BoardReview> myReviews = boardService.assignedAll(principal.getMemberId(), STAGE).stream()
            .filter(review -> review.getDecision() == BoardDecision.PENDING)
            .toList();
        return populateListing(
            principal,
            model,
            myReviews,
            true,
            "Loan Officer Panel / Queue",
            "Loan Officer Queue",
            "No applications are currently waiting in your loan officer queue.",
            "/loan-officer/queue",
            searchId
        );
    }

    @GetMapping("/archive")
    public String archive(@AuthenticationPrincipal AppUserPrincipal principal,
                          @RequestParam(required = false) String searchId,
                          @RequestParam(required = false) String filter,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        applyLoanOfficerUi(model);
        ArchiveFilter currentFilter = resolveArchiveFilter(filter);
        ArchiveDateRange dateRange = ArchiveDateRange.inclusive(fromDate, toDate, applicationClock);
        List<String> statuses = currentFilter.statuses().isEmpty()
            ? List.of(com.sacco.mvp.domain.LoanStatus.DRAFT.name())
            : currentFilter.statuses().stream().map(Enum::name).toList();
        org.springframework.data.domain.Page<BoardReview> archivePage = boardService.archivePage(
            principal.getMemberId(),
            List.of(STAGE.name()),
            principal.getSaccoId(),
            principal.getStationId(),
            dateRange.fromInclusive(),
            dateRange.toExclusive(),
            currentFilter.decision() != null,
            currentFilter.decision() == null ? BoardDecision.APPROVED.name() : currentFilter.decision().name(),
            !currentFilter.statuses().isEmpty(),
            statuses,
            normalizeSearch(searchId),
            org.springframework.data.domain.PageRequest.of(Math.max(page, 0), 50)
        );
        String view = populateListing(
            principal,
            model,
            archivePage.getContent(),
            false,
            "Loan Officer Panel / Archive",
            "Loan Officer Archive",
            "No reviewed applications are available in your archive yet.",
            "/loan-officer/archive",
            searchId
        );
        model.addAttribute("archiveView", true);
        model.addAttribute("archivePage", archivePage);
        model.addAttribute("currentFilterKey", currentFilter.key());
        model.addAttribute("currentFilterLabel", currentFilter.label());
        model.addAttribute("fromDate", dateRange.fromDate());
        model.addAttribute("toDate", dateRange.toDate());
        return view;
    }

    @GetMapping("/reports")
    @PreAuthorize("@access.canAccessLoanOfficerArea(principal) and @access.has(principal, 'LOAN_OFFICER_QUEUE_EXPORT')")
    public String reports(@AuthenticationPrincipal AppUserPrincipal principal,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                          @RequestParam(required = false) String decisionFilter,
                          Model model) {
        LocalDate effectiveTo = toDate == null ? applicationClock.today() : toDate;
        LocalDate effectiveFrom = fromDate == null ? effectiveTo.withDayOfMonth(1) : fromDate;
        if (effectiveFrom.isAfter(effectiveTo)) {
            model.addAttribute("error", "From date cannot be after to date.");
            effectiveFrom = effectiveTo.withDayOfMonth(1);
        }
        LoanReportService.BoardWorkflowReport report = loanReportService.boardWorkflowReport(
            principal.getMemberId(), principal.getSaccoId(), principal.getStationId(),
            STAGE, effectiveFrom, effectiveTo, decisionFilter);
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
                row.put("decisionLabel", entry.review().getDecision() == BoardDecision.APPROVED ? "Approved" : "Rejected");
                OffsetDateTime reviewedAt = entry.review().getDecidedAt() == null ? entry.review().getCreatedAt() : entry.review().getDecidedAt();
                row.put("reviewedAt", reviewedAt == null ? "-" : reviewedAt.toLocalDate().toString());
                row.put("currentStatusLabel", workflowStatusPresentationService.dashboardStatusLabel(entry.loan().getStatus()));
                return row;
            })
            .toList());
        return "loan-officer/reports";
    }

    @GetMapping("/loan-applications/{id}")
    @PreAuthorize("@access.canAccessLoanOfficerArea(principal) and @authz.isLoanOfficerAssignee(#id, principal)")
    public String detail(@PathVariable UUID id,
                         @AuthenticationPrincipal AppUserPrincipal principal,
                         Model model) {
        applyLoanOfficerUi(model);
        LoanApplication app = boardService.findLoan(id)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        Member applicant = memberDirectoryService.find(app.getApplicantMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Applicant not found"));
        BoardReview myReview = boardService.getMyReview(id, principal.getMemberId(), STAGE);
        List<BoardReview> stageReviews = boardService.reviewsForLoan(id, STAGE);
        List<GuarantorRequest> guarantorRequests = loanPresentationService.guarantorRequests(id);
        List<Member> guarantorMembers = memberDirectoryService.findAll(
            guarantorRequests.stream().map(GuarantorRequest::getGuarantorMemberId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, String> guarantorNames = guarantorMembers.stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));
        Map<UUID, Member> guarantorMembersById = guarantorMembers.stream()
            .collect(Collectors.toMap(Member::getId, member -> member));
        Map<UUID, Member> reviewers = memberDirectoryService.membersById(
                stageReviews.stream().map(BoardReview::getBoardMemberId).collect(Collectors.toSet()));
        model.addAttribute("app", app);
        model.addAttribute("applicant", applicant);
        model.addAttribute("loanProductName", loanProductDisplayService.displayName(app));
        model.addAttribute("paymentDetails", paymentDetailsService.resolveForLoan(app));
        model.addAttribute("applicantExternalAccountStatus", externalAccountStatusService.loading("Loading live balances..."));
        model.addAttribute("myReview", myReview);
        model.addAttribute("formFields", parseFormData(app.getFormData()));
        model.addAttribute("financialFields", loanPresentationService.parseFinancialFields(app));
        model.addAttribute("financialFieldSections", loanPresentationService.parseFinancialFieldSections(app));
        boolean actualRepaymentScheduleEnabled = isActualRepaymentStatus(app.getStatus());
        model.addAttribute("actualRepaymentScheduleEnabled", actualRepaymentScheduleEnabled);
        model.addAttribute("repaymentSchedulePath", actualRepaymentScheduleEnabled
            ? "/loan-officer/loan-applications/" + app.getId() + "/repayment-schedule"
            : "");
        model.addAttribute("repaymentSummary", loanPresentationService.reviewRepaymentSummary(app));
        model.addAttribute("repaymentSummaryEstimated", loanPresentationService.isEstimatedReviewRepaymentSummary(app));
        model.addAttribute("calculatedRepaymentRows", actualRepaymentScheduleEnabled
            ? List.of()
            : loanPresentationService.calculatedRepaymentRows(app));
        model.addAttribute("repaymentRows", loanPresentationService.reviewRepaymentRows(app));
        model.addAttribute("repaymentCountdown", loanPresentationService.countdownLabel(app.getFinalDueDate()));
        model.addAttribute("previousApprovedReviews", loanPresentationService.previousApprovedReviews(app, STAGE));
        model.addAttribute("attachments", loanPresentationService.parseApplicationAttachments(app.getAttachmentsJson()));
        model.addAttribute("disbursementProofAttachments", loanPresentationService.parseDisbursementProofAttachments(app.getAttachmentsJson()));
        model.addAttribute("guarantorRequests", guarantorRequests);
        model.addAttribute("guarantorNames", guarantorNames);
        model.addAttribute("guarantorMembersById", guarantorMembersById);
        List<LoanApplication> activeApplicantLoans = managerService.activeApplicantLoans(
            app.getApplicantMemberId(), app.getId(), principal.getSaccoId());
        ActiveLoanDisplayService.ActiveLoanDisplay activeLoanDisplay =
            activeLoanDisplayService.localStaffReviewRows(principal.getSaccoId(), app, activeApplicantLoans);
        model.addAttribute("activeApplicantLoans", activeLoanDisplay.rows());
        model.addAttribute("activeApplicantLoanCount", activeLoanDisplay.count());
        model.addAttribute("activeApplicantLoanTotalAmount", activeLoanDisplay.totalExposure());
        model.addAttribute("activeApplicantLoansForesightEnabled", true);
        model.addAttribute("managerReason", loanPresentationService.latestManagerReason(id));
        model.addAttribute("loanIdShort", app.getApplicationNumber() == null ? "" : app.getApplicationNumber().toString());
        model.addAttribute("disbursedLoanId", app.getLoanId());
        model.addAttribute("loanProgressItems", loanPresentationService.buildProgressItems(app));
        model.addAttribute("boardStatusBadgeClass", boardLoanStatusBadgeClass(app));
        model.addAttribute("boardSavedSignatureText", resolveSavedSignatureText(principal.getMemberId()));
        model.addAttribute("reviewApprovalOtpEnabled",
            stationOtpSettingsService.requiresApprovalOtp(
                principal.getMemberId(), principal.getSaccoId(), principal.getStationId()));
        model.addAttribute("boardAssessors", stageReviews.stream()
            .map(review -> {
                Map<String, Object> row = new LinkedHashMap<>();
                Member reviewer = reviewers.get(review.getBoardMemberId());
                row.put("name", reviewer == null ? shortMemberId(review.getBoardMemberId()) : reviewer.getFullName());
                row.put("memberNo", reviewer == null ? "-" : reviewer.getMemberNo());
                row.put("decision", review.getDecision() == null ? "PENDING" : review.getDecision().name());
                row.put("comment", review.getComment());
                row.put("decidedAt", review.getDecidedAt() == null ? "" : review.getDecidedAt().toString());
                row.put("signatureText", review.getBoardSignatureText());
                row.put("signatureVerifiedAt",
                    review.getBoardSignatureVerifiedAt() == null ? "" : review.getBoardSignatureVerifiedAt().toString());
                row.put("isMine", review.getBoardMemberId() != null && review.getBoardMemberId().equals(principal.getMemberId()));
                return row;
            })
            .toList());
        return "board/detail";
    }

    @GetMapping("/loan-applications/{loanId}/guarantors/{guarantorId}/financial-status")
    @ResponseBody
    @PreAuthorize("@access.canAccessLoanOfficerArea(principal) and @authz.isLoanOfficerAssignee(#loanId, principal)")
    public ResponseEntity<Map<String, Object>> guarantorFinancialStatus(@PathVariable UUID loanId,
                                                                        @PathVariable UUID guarantorId,
                                                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        boardService.getMyReview(loanId, principal.getMemberId(), STAGE);
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

    @GetMapping("/loan-applications/{id}/applicant-financial-status")
    @ResponseBody
    @PreAuthorize("@access.canAccessLoanOfficerArea(principal) and @authz.isLoanOfficerAssignee(#id, principal)")
    public ResponseEntity<Map<String, Object>> applicantFinancialStatus(@PathVariable UUID id,
                                                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        boardService.getMyReview(id, principal.getMemberId(), STAGE);
        LoanApplication app = boardService.findLoan(id)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        Member applicant = memberDirectoryService.find(app.getApplicantMemberId()).orElse(null);
        return ResponseEntity.ok(externalAccountStatusPayload(externalAccountStatusService.resolve(applicant)));
    }

    @GetMapping("/loan-applications/{id}/applicant-active-loans")
    @ResponseBody
    @PreAuthorize("@access.canAccessLoanOfficerArea(principal) and @authz.isLoanOfficerAssignee(#id, principal)")
    public ResponseEntity<Map<String, Object>> applicantActiveLoans(@PathVariable UUID id,
                                                                    @AuthenticationPrincipal AppUserPrincipal principal) {
        boardService.getMyReview(id, principal.getMemberId(), STAGE);
        LoanApplication app = boardService.findLoan(id)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
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
    @PreAuthorize("@access.canAccessLoanOfficerArea(principal) and @authz.isLoanOfficerAssignee(#id, principal)")
    public ResponseEntity<Map<String, Object>> repaymentSchedule(@PathVariable UUID id,
                                                                 @AuthenticationPrincipal AppUserPrincipal principal) {
        boardService.getMyReview(id, principal.getMemberId(), STAGE);
        LoanApplication app = boardService.findLoan(id)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        Member applicant = memberDirectoryService.find(app.getApplicantMemberId()).orElse(null);
        return ResponseEntity.ok(foresightRepaymentScheduleService.loadLocalLoanSchedule(app, applicant).toPayload());
    }

    @GetMapping("/loan-applications/{id}/applicant-active-loans/{loanId}/repayment-schedule")
    @ResponseBody
    @PreAuthorize("@access.canAccessLoanOfficerArea(principal) and @authz.isLoanOfficerAssignee(#id, principal)")
    public ResponseEntity<Map<String, Object>> applicantActiveLoanRepaymentSchedule(@PathVariable UUID id,
                                                                                   @PathVariable String loanId,
                                                                                   @AuthenticationPrincipal AppUserPrincipal principal) {
        boardService.getMyReview(id, principal.getMemberId(), STAGE);
        LoanApplication app = boardService.findLoan(id)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        Member applicant = memberDirectoryService.find(app.getApplicantMemberId()).orElse(null);
        return ResponseEntity.ok(foresightRepaymentScheduleService
            .loadExternalLoanSchedule(applicant, app.getStationId(), loanId)
            .toPayload());
    }

    @PostMapping("/loan-applications/{id}/request-signature-otp")
    @PreAuthorize("@access.canAccessLoanOfficerArea(principal) and @access.hasAny(principal, 'LOAN_OFFICER_QUEUE_APPROVE', 'LOAN_OFFICER_QUEUE_REJECT') and @authz.isLoanOfficerAssignee(#id, principal)")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestSignatureOtp(@PathVariable UUID id,
                                                                   @AuthenticationPrincipal AppUserPrincipal principal) {
        try {
            LoanApplication app = boardService.findLoan(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
            if (app.getStatus() != LoanStatus.AWAITING_LOAN_OFFICER) {
                throw new IllegalStateException("This application is no longer waiting for loan officer approval.");
            }
            BoardReview myReview = boardService.getMyReview(id, principal.getMemberId(), STAGE);
            if (myReview.getDecision() != BoardDecision.PENDING) {
                throw new IllegalStateException("You have already submitted your loan officer decision.");
            }
            if (!stationOtpSettingsService.requiresApprovalOtp(
                principal.getMemberId(), principal.getSaccoId(), principal.getStationId())) {
                throw new IllegalStateException("OTP verification is disabled for approval actions on your account.");
            }
            Member reviewer = requireMemberWithEmail(principal.getMemberId());
            EmailOtpService.OtpIssueResult otp = emailOtpService.issueLoanOtpWithMetadata(
                reviewer.getEmail(),
                EmailOtpPurpose.BOARD_SIGNATURE,
                reviewer.getId(),
                "Your Loan Application Portal loan officer decision code",
                "Use this OTP code to confirm your assigned loan officer review decision.",
                app.getSaccoId(),
                app.getStationId(),
                reviewer.getPhone(),
                app.getId(),
                app.getApplicantMemberId()
            );
            return ResponseEntity.ok(otpIssueResponse(otp, "We sent a loan officer decision code using the station OTP delivery policy."));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/loan-applications/{id}/verify-signature-otp")
    @PreAuthorize("@access.canAccessLoanOfficerArea(principal) and @access.hasAny(principal, 'LOAN_OFFICER_QUEUE_APPROVE', 'LOAN_OFFICER_QUEUE_REJECT') and @authz.isLoanOfficerAssignee(#id, principal)")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifySignatureOtp(@PathVariable UUID id,
                                                                  @AuthenticationPrincipal AppUserPrincipal principal,
                                                                  @RequestParam String otpCode) {
        try {
            LoanApplication app = boardService.findLoan(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
            if (app.getStatus() != LoanStatus.AWAITING_LOAN_OFFICER) {
                throw new IllegalStateException("This application is no longer waiting for loan officer approval.");
            }
            BoardReview myReview = boardService.getMyReview(id, principal.getMemberId(), STAGE);
            if (myReview.getDecision() != BoardDecision.PENDING) {
                throw new IllegalStateException("You have already submitted your loan officer decision.");
            }
            if (!stationOtpSettingsService.requiresApprovalOtp(
                principal.getMemberId(), principal.getSaccoId(), principal.getStationId())) {
                return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "message", "OTP verification is disabled for approval actions on your account."
                ));
            }
            Member reviewer = requireMemberWithEmail(principal.getMemberId());
            emailOtpService.validateOtp(reviewer.getEmail(), EmailOtpPurpose.BOARD_SIGNATURE, otpCode);
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

    @PostMapping("/loan-applications/{id}/decision")
    @PreAuthorize("@access.canAccessLoanOfficerArea(principal) and @access.canDecide(principal, #decision, 'LOAN_OFFICER_QUEUE_APPROVE', 'LOAN_OFFICER_QUEUE_REJECT') and @authz.isLoanOfficerAssignee(#id, principal)")
    public String decide(@PathVariable UUID id,
                         @AuthenticationPrincipal AppUserPrincipal principal,
                         @RequestParam BoardDecision decision,
                         @RequestParam(required = false) String comment,
                         @RequestParam(required = false) String boardSignatureOtpCode,
                         RedirectAttributes ra) {
        try {
            LoanApplication app = boardService.findLoan(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
            Member reviewer = requireMemberWithEmail(principal.getMemberId());
            UUID otpTokenId = stationOtpSettingsService.requiresApprovalOtp(
                principal.getMemberId(), principal.getSaccoId(), principal.getStationId())
                ? emailOtpService.validateOtp(reviewer.getEmail(), EmailOtpPurpose.BOARD_SIGNATURE, boardSignatureOtpCode)
                : null;
            requireSavedSignature(reviewer);
            boardService.decide(
                id,
                principal.getMemberId(),
                STAGE,
                decision,
                comment,
                reviewer.getSignatureText(),
                OffsetDateTime.now()
            );
            if (otpTokenId != null) {
                emailOtpService.consumeOtpById(otpTokenId);
            }
            ra.addFlashAttribute("message", "Loan officer decision submitted");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/loan-officer/loan-applications/" + id;
    }

    @GetMapping("/notifications")
    @PreAuthorize("@access.canAccessLoanOfficerArea(principal) and @access.has(principal, 'NOTIFICATIONS_VIEW')")
    public String notifications(@AuthenticationPrincipal AppUserPrincipal principal,
                                @RequestParam(required = false) UUID highlight,
                                Model model) {
        applyLoanOfficerUi(model);
        model.addAttribute("notificationBreadcrumb", "Loan Officer Panel / Notifications");
        model.addAttribute("notificationSubtitle", "Workflow updates and alerts for the loan officer queue in one place.");
        model.addAttribute("notifications", notificationInboxService.allViews(principal));
        model.addAttribute("highlightNotificationId", highlight);
        return "board/notifications";
    }

    @GetMapping("/notifications/{id}/open")
    @PreAuthorize("@access.canAccessLoanOfficerArea(principal) and @access.has(principal, 'NOTIFICATIONS_VIEW')")
    public String openNotification(@PathVariable UUID id,
                                   @AuthenticationPrincipal AppUserPrincipal principal,
                                   RedirectAttributes ra) {
        try {
            return "redirect:" + notificationInboxService.openForMember(id, principal, "/loan-officer/notifications");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/loan-officer/notifications";
        }
    }

    @PostMapping("/notifications/mark-all-read")
    @PreAuthorize("@access.canAccessLoanOfficerArea(principal) and @access.has(principal, 'NOTIFICATIONS_UPDATE')")
    public String markAllNotificationsRead(@AuthenticationPrincipal AppUserPrincipal principal,
                                           RedirectAttributes ra) {
        int updated = notificationInboxService.markAllAsRead(principal.getMemberId());
        if (updated > 0) {
            ra.addFlashAttribute("message", "All notifications have been marked as read.");
        } else {
            ra.addFlashAttribute("message", "There were no unread notifications.");
        }
        return "redirect:/loan-officer/notifications";
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String handleError(RuntimeException ex, RedirectAttributes ra) {
        ra.addFlashAttribute("error", ex.getMessage());
        return "redirect:/loan-officer/queue";
    }

    private void applyLoanOfficerUi(Model model) {
        model.addAttribute("reviewBasePath", "/loan-officer");
        model.addAttribute("reviewRoleLabel", "Loan Officer");
        model.addAttribute("reviewRoleLabelLower", "loan officer");
        model.addAttribute("reviewPanelBreadcrumb", "Loan Officer Panel / Review Detail");
        model.addAttribute("reviewPanelTitle", "Loan Officer Review Detail");
        model.addAttribute("reviewPanelSubtitle", "Review the loan package and record the loan officer decision.");
        model.addAttribute("reviewDecisionLabel", "Loan Officer Decision");
        model.addAttribute("reviewAssessorTitle", "Loan Officer Review");
        model.addAttribute("reviewAssessorDescription", "The assigned loan officer decision recorded for this application.");
        model.addAttribute("reviewApprovalOtpEnabled", true);
        model.addAttribute("reviewAwaitingStatus", "AWAITING_LOAN_OFFICER");
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

    private ArchiveFilter resolveArchiveFilter(String filter) {
        String key = filter == null || filter.isBlank() ? "ALL" : filter.trim().toUpperCase(java.util.Locale.ENGLISH);
        return switch (key) {
            case "APPROVED" -> new ArchiveFilter("APPROVED", "Approved Loans", BoardDecision.APPROVED, List.of());
            case "REJECTED" -> new ArchiveFilter("REJECTED", "Rejected Loans", BoardDecision.REJECTED, List.of());
            case "DISBURSED", "APPROVED_FOR_DISBURSEMENT" -> new ArchiveFilter("DISBURSED", "Disbursed Loans", null,
                List.of(com.sacco.mvp.domain.LoanStatus.DISBURSED, com.sacco.mvp.domain.LoanStatus.PAR, com.sacco.mvp.domain.LoanStatus.DEFAULTED, com.sacco.mvp.domain.LoanStatus.PAID));
            default -> new ArchiveFilter("ALL", "All Reviewed Loans", null, List.of());
        };
    }

    private record ArchiveFilter(String key,
                                 String label,
                                 BoardDecision decision,
                                 List<com.sacco.mvp.domain.LoanStatus> statuses) {
    }

    private String populateListing(AppUserPrincipal principal,
                                   Model model,
                                   List<BoardReview> reviews,
                                   boolean awaitingOnly,
                                   String breadcrumb,
                                   String pageTitle,
                                   String emptyState,
                                   String listRoute,
                                   String searchId) {
        List<LoanApplication> apps = new java.util.ArrayList<>();
        Map<UUID, BoardDecision> myDecisions = new java.util.LinkedHashMap<>();
        Map<UUID, java.time.OffsetDateTime> myDecisionDates = new java.util.LinkedHashMap<>();
        Map<UUID, String> myDecisionReasons = new java.util.LinkedHashMap<>();
        String normalizedSearchId = normalizeSearch(searchId);
        Map<UUID, LoanApplication> loans = loanPresentationService.loansById(
            reviews.stream().map(BoardReview::getLoanApplicationId).toList());
        for (BoardReview review : reviews) {
            java.util.Optional.ofNullable(loans.get(review.getLoanApplicationId()))
                .filter(app -> principal.getSaccoId().equals(app.getSaccoId()))
                .filter(app -> matchesApplicantStation(app, principal.getStationId()))
                .filter(app -> !awaitingOnly || app.getStatus() == LoanStatus.AWAITING_LOAN_OFFICER)
                .filter(app -> matchesSearch(app, normalizedSearchId))
                .ifPresent(app -> {
                    apps.add(app);
                    myDecisions.put(app.getId(), review.getDecision());
                    myDecisionDates.put(app.getId(), review.getDecidedAt() == null ? review.getCreatedAt() : review.getDecidedAt());
                    myDecisionReasons.put(app.getId(), review.getDecision() == BoardDecision.REJECTED
                        && review.getComment() != null
                        && !review.getComment().isBlank()
                        ? review.getComment()
                        : "-");
                });
        }
        Map<UUID, String> applicantNames = memberDirectoryService.fullNames(
            apps.stream().map(LoanApplication::getApplicantMemberId).collect(Collectors.toSet()));

        model.addAttribute("apps", apps);
        model.addAttribute("applicantNames", applicantNames);
        model.addAttribute("applicationProductNames", loanProductDisplayService.namesForApplications(principal.getSaccoId(), apps));
        model.addAttribute("myDecisions", myDecisions);
        model.addAttribute("myDecisionDates", myDecisionDates);
        model.addAttribute("myDecisionReasons", myDecisionReasons);
        model.addAttribute("boardListBreadcrumb", breadcrumb);
        model.addAttribute("boardListTitle", pageTitle);
        model.addAttribute("boardListEmptyState", emptyState);
        model.addAttribute("boardListRoute", listRoute);
        model.addAttribute("boardSearchValue", normalizedSearchId);
        return "board/assigned";
    }

    private boolean matchesApplicantStation(LoanApplication app, String stationId) {
        if (stationId == null || stationId.isBlank()) {
            return true;
        }
        return app.getStationId() != null && stationId.equalsIgnoreCase(app.getStationId());
    }

    private String normalizeSearch(String searchId) {
        return searchId == null ? "" : searchId.trim();
    }

    private boolean matchesSearch(LoanApplication app, String searchId) {
        if (searchId == null || searchId.isBlank()) {
            return true;
        }
        Long applicationNumber = app.getApplicationNumber();
        return applicationNumber != null && String.valueOf(applicationNumber).contains(searchId);
    }

    private boolean isActualRepaymentStatus(LoanStatus status) {
        return status == LoanStatus.DISBURSED || status == LoanStatus.DEFAULTED || status == LoanStatus.PAID;
    }

    private Map<String, Object> parseFormData(String formDataJson) {
        if (formDataJson == null || formDataJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(formDataJson, new TypeReference<>() {});
            Map<String, Object> cleaned = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isBlank() || shouldHideField(key)) {
                    continue;
                }
                cleaned.put(humanizeFieldLabel(key), entry.getValue());
            }
            return cleaned;
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private boolean shouldHideField(String key) {
        String normalized = key.trim().toLowerCase(java.util.Locale.ROOT);
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

    private Member requireMemberWithEmail(UUID memberId) {
        Member member = memberDirectoryService.find(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Member account not found."));
        if (member.getEmail() == null || member.getEmail().isBlank()) {
            throw new IllegalStateException("Add an email address to your member profile before requesting an approval OTP.");
        }
        return member;
    }

    private void requireSavedSignature(Member member) {
        if (member.getSignatureText() == null || member.getSignatureText().isBlank()) {
            throw new IllegalStateException("Register your signature first before recording loan officer reviews.");
        }
    }

    private String resolveSavedSignatureText(UUID memberId) {
        return memberDirectoryService.savedSignatureText(memberId);
    }

    private String shortMemberId(UUID memberId) {
        return memberId == null ? "-" : "#" + memberId.toString().substring(0, 8);
    }

    private String boardLoanStatusBadgeClass(LoanApplication app) {
        return switch (app.getStatus()) {
            case READY_FOR_MANAGER -> "bg-amber-50 text-amber-700";
            case MANAGER_ACCEPTED, AWAITING_LOAN_OFFICER, AWAITING_BOARD, AWAITING_CREDIT_COMMITTEE, AWAITING_ACCOUNTANT -> "bg-blue-50 text-blue-700";
            case LOAN_OFFICER_APPROVED, BOARD_APPROVED, ACCOUNTANT_APPROVED, READY_FOR_DISBURSEMENT, DISBURSED, PAID -> "bg-emerald-50 text-emerald-700";
            case PAR -> "bg-amber-50 text-amber-700";
            case DEFAULTED -> "bg-rose-50 text-rose-700";
            case MANAGER_REJECTED, LOAN_OFFICER_REJECTED, BOARD_REJECTED, ACCOUNTANT_REJECTED, REJECTED -> "bg-rose-50 text-rose-700";
            default -> "bg-slate-100 text-slate-700";
        };
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
}
