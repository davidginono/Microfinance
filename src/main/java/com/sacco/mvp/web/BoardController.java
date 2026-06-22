package com.sacco.mvp.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.BoardService;
import com.sacco.mvp.service.EmailOtpService;
import com.sacco.mvp.service.ExternalAccountStatusService;
import com.sacco.mvp.service.LoanPresentationService;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/board")
@PreAuthorize("hasRole('BOARD') and @userClaims.has(principal, 'REVIEW_BOARD_QUEUE')")
public class BoardController {
    private final BoardService boardService;
    private final LoanApplicationRepository loanApplicationRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final LoanPresentationService loanPresentationService;
    private final ExternalAccountStatusService externalAccountStatusService;
    private final EmailOtpService emailOtpService;
    private final NotificationInboxService notificationInboxService;
    private final ManagerService managerService;
    private final WorkflowStatusPresentationService workflowStatusPresentationService;
    private final PaymentDetailsService paymentDetailsService;
    private final MessageSource messageSource;
    private final LoanReportService loanReportService;
    private final ApplicationClock applicationClock;

    @GetMapping("/assigned")
    public String assigned() {
        return "redirect:/board/queue";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        ManagerService.ManagerDashboard dashboard = managerService.dashboard(principal.getSaccoId(), principal.getStationId());
        Map<UUID, String> applicantNames = memberRepository.findAllById(
                dashboard.recentDisbursements().stream()
                    .map(LoanApplication::getApplicantMemberId)
                    .collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));

        model.addAttribute("dashboardBreadcrumb", "Board Panel / Dashboard");
        model.addAttribute("dashboardPageTitle", "Board Dashboard");
        model.addAttribute("dashboardSubtitle", "Track board review decisions and keep an eye on loans waiting for board action.");
        model.addAttribute("dashboardQueueLabel", "On Review By Board");
        model.addAttribute("dashboardQueueValue",
            workflowStatusPresentationService.countFor(dashboard.statusBreakdown(), com.sacco.mvp.domain.LoanStatus.AWAITING_BOARD));
        model.addAttribute("dashboardQueueMeta", "Applications currently waiting for your action.");
        model.addAttribute("dashboardQueueFooterLabel", "Queue waiting");
        model.addAttribute("dashboardQueueIcon", "B");
        model.addAttribute("dashboardDetailBasePath", "/board/loan-applications");
        model.addAttribute("dashboardTotalDisbursedLoans", dashboard.totalDisbursedLoans());
        model.addAttribute("dashboardTrackedApplicationCount", dashboard.totalLoans());
        model.addAttribute("dashboardDisbursementYear", LocalDate.now().getYear());
        model.addAttribute("dashboardActiveDisbursedLoans", dashboard.activeDisbursedLoans());
        model.addAttribute("dashboardDefaultedLoans", dashboard.defaultedLoansCurrentYear());
        model.addAttribute("dashboardChartTitle", "Board Decision Chart");
        model.addAttribute("dashboardChartHelp", "A board-focused view of applications waiting for board review, approved by board, and rejected by board.");
        model.addAttribute("dashboardStatusChartRows",
            workflowStatusPresentationService.buildBoardDashboardChartRows(
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

    @GetMapping("/queue")
    public String queue(@AuthenticationPrincipal AppUserPrincipal principal,
                        @RequestParam(required = false) String searchId,
                        Model model) {
        applyBoardUi(model);
        List<BoardReview> myReviews = boardService.assignedAll(principal.getMemberId()).stream()
            .filter(review -> review.getDecision() == BoardDecision.PENDING)
            .toList();
        return populateListing(
            principal,
            model,
            myReviews,
            true,
            "Board Panel / Queue",
            "Board Queue",
            "No applications are currently waiting in your board queue.",
            "/board/queue",
            searchId
        );
    }

    @GetMapping("/archive")
    public String archive(@AuthenticationPrincipal AppUserPrincipal principal,
                          @RequestParam(required = false) String searchId,
                          Model model) {
        applyBoardUi(model);
        List<BoardReview> myReviews = boardService.assignedAll(principal.getMemberId()).stream()
            .filter(review -> review.getDecision() != BoardDecision.PENDING)
            .toList();
        return populateListing(
            principal,
            model,
            myReviews,
            false,
            "Board Panel / Archive",
            "Board Archive",
            "No reviewed applications are available in your archive yet.",
            "/board/archive",
            searchId
        );
    }

    @GetMapping("/reports")
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
            ApprovalWorkflowStage.BOARD, effectiveFrom, effectiveTo, decisionFilter);
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
                row.put("decisionLabel", entry.review().getDecision() == BoardDecision.APPROVED ? message("review.approved") : message("review.rejected"));
                OffsetDateTime reviewedAt = entry.review().getDecidedAt() == null ? entry.review().getCreatedAt() : entry.review().getDecidedAt();
                row.put("reviewedAt", reviewedAt == null ? "-" : reviewedAt.toLocalDate().toString());
                row.put("currentStatusLabel", workflowStatusPresentationService.dashboardStatusLabel(entry.loan().getStatus()));
                return row;
            })
            .toList());
        return "board/reports";
    }

    @GetMapping("/loan-applications/{id}")
    @PreAuthorize("hasRole('BOARD') and @authz.isBoardAssignee(#id, principal)")
    public String detail(@PathVariable UUID id,
                         @AuthenticationPrincipal AppUserPrincipal principal,
                         Model model) {
        applyBoardUi(model);
        LoanApplication app = loanApplicationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        Member applicant = memberRepository.findById(app.getApplicantMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Applicant not found"));
        BoardReview myReview = boardService.getMyReview(id, principal.getMemberId());
        List<BoardReview> boardReviews = boardService.reviewsForLoan(id);
        List<GuarantorRequest> guarantorRequests = guarantorRequestRepository.findByLoanApplicationId(id);
        List<Member> guarantorMembers = memberRepository.findAllById(
            guarantorRequests.stream().map(GuarantorRequest::getGuarantorMemberId).collect(Collectors.toSet()));
        Map<UUID, String> guarantorNames = guarantorMembers.stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));
        Map<UUID, Member> guarantorMembersById = guarantorMembers.stream()
            .collect(Collectors.toMap(Member::getId, member -> member));
        Map<UUID, Member> boardMembers = memberRepository.findAllById(
                boardReviews.stream().map(BoardReview::getBoardMemberId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, member -> member));
        model.addAttribute("app", app);
        model.addAttribute("applicant", applicant);
        model.addAttribute("paymentDetails", paymentDetailsService.resolveForLoan(app));
        model.addAttribute("applicantExternalAccountStatus", externalAccountStatusService.loading("Loading live balances..."));
        model.addAttribute("myReview", myReview);
        model.addAttribute("formFields", parseFormData(app.getFormData()));
        model.addAttribute("financialFields", loanPresentationService.parseFinancialFields(app));
        model.addAttribute("financialFieldSections", loanPresentationService.parseFinancialFieldSections(app));
        model.addAttribute("repaymentSummary", loanPresentationService.reviewRepaymentSummary(app));
        model.addAttribute("repaymentSummaryEstimated", loanPresentationService.isEstimatedReviewRepaymentSummary(app));
        model.addAttribute("repaymentRows", loanPresentationService.reviewRepaymentRows(app));
        model.addAttribute("repaymentCountdown", loanPresentationService.countdownLabel(app.getFinalDueDate()));
        model.addAttribute("attachments", loanPresentationService.parseApplicationAttachments(app.getAttachmentsJson()));
        model.addAttribute("disbursementProofAttachments", loanPresentationService.parseDisbursementProofAttachments(app.getAttachmentsJson()));
        model.addAttribute("guarantorRequests", guarantorRequests);
        model.addAttribute("guarantorNames", guarantorNames);
        model.addAttribute("guarantorMembersById", guarantorMembersById);
        List<LoanApplication> activeApplicantLoans = managerService.activeApplicantLoans(
            app.getApplicantMemberId(), app.getId(), principal.getSaccoId());
        Map<UUID, LoanPresentationService.LoanPaymentSummaryView> activeLoanSummaries = activeApplicantLoans.stream()
            .collect(Collectors.toMap(
                LoanApplication::getId,
                this::storedActiveLoanPaymentSummary,
                (left, right) -> left,
                LinkedHashMap::new
            ));
        model.addAttribute("activeApplicantLoans", activeApplicantLoans.stream()
            .map(loan -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", loan.getId().toString());
                row.put("shortId", loan.getApplicationNumber() == null ? "" : loan.getApplicationNumber().toString());
                row.put("loanId", loan.getLoanId() == null ? "" : loan.getLoanId());
                row.put("loanTypeLabel", loanTypeLabel(loan.getLoanType()));
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
        model.addAttribute("managerReason", loanPresentationService.latestManagerReason(id));
        model.addAttribute("loanIdShort", app.getApplicationNumber() == null ? "" : app.getApplicationNumber().toString());
        model.addAttribute("disbursedLoanId", app.getLoanId());
        model.addAttribute("loanProgressItems", loanPresentationService.buildProgressItems(app));
        model.addAttribute("boardStatusBadgeClass", boardLoanStatusBadgeClass(app));
        model.addAttribute("boardSavedSignatureText", resolveSavedSignatureText(principal.getMemberId()));
        model.addAttribute("boardAssessors", boardReviews.stream()
            .map(review -> {
                Map<String, Object> row = new LinkedHashMap<>();
                Member boardMember = boardMembers.get(review.getBoardMemberId());
                row.put("name", boardMember == null ? shortMemberId(review.getBoardMemberId()) : boardMember.getFullName());
                row.put("memberNo", boardMember == null ? "-" : boardMember.getMemberNo());
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
    @PreAuthorize("hasRole('BOARD') and @authz.isBoardAssignee(#loanId, principal)")
    public ResponseEntity<Map<String, Object>> guarantorFinancialStatus(@PathVariable UUID loanId,
                                                                        @PathVariable UUID guarantorId,
                                                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        boardService.getMyReview(loanId, principal.getMemberId());
        boolean guarantorAssigned = guarantorRequestRepository.findByLoanApplicationId(loanId).stream()
            .anyMatch(request -> guarantorId.equals(request.getGuarantorMemberId()));
        if (!guarantorAssigned) {
            return ResponseEntity.badRequest().body(Map.of("message", "Guarantor request was not found for this loan."));
        }
        Member guarantor = memberRepository.findById(guarantorId)
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
    @PreAuthorize("hasRole('BOARD') and @authz.isBoardAssignee(#id, principal)")
    public ResponseEntity<Map<String, Object>> applicantFinancialStatus(@PathVariable UUID id,
                                                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        boardService.getMyReview(id, principal.getMemberId());
        LoanApplication app = loanApplicationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        Member applicant = memberRepository.findById(app.getApplicantMemberId()).orElse(null);
        return ResponseEntity.ok(externalAccountStatusPayload(externalAccountStatusService.resolve(applicant)));
    }

    @GetMapping("/loan-applications/{id}/active-loans/outstanding-balances")
    @ResponseBody
    @PreAuthorize("hasRole('BOARD') and @authz.isBoardAssignee(#id, principal)")
    public ResponseEntity<Map<String, Object>> activeLoanOutstandingBalances(@PathVariable UUID id,
                                                                             @AuthenticationPrincipal AppUserPrincipal principal) {
        boardService.getMyReview(id, principal.getMemberId());
        LoanApplication app = loanApplicationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
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

    @PostMapping("/loan-applications/{id}/request-signature-otp")
    @PreAuthorize("hasRole('BOARD') and @authz.isBoardAssignee(#id, principal)")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestBoardSignatureOtp(@PathVariable UUID id,
                                                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        try {
            LoanApplication app = loanApplicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
            if (app.getStatus() != com.sacco.mvp.domain.LoanStatus.AWAITING_BOARD) {
                throw new IllegalStateException("This application is no longer waiting for board approval.");
            }
            BoardReview myReview = boardService.getMyReview(id, principal.getMemberId());
            if (myReview.getDecision() != BoardDecision.PENDING) {
                throw new IllegalStateException("You have already submitted your board decision.");
            }
            Member boardMember = requireMemberWithEmail(principal.getMemberId());
            EmailOtpService.OtpIssueResult otp = emailOtpService.issueOtpWithMetadata(
                boardMember.getEmail(),
                EmailOtpPurpose.BOARD_SIGNATURE,
                boardMember.getId(),
                "Your SACCO MVP board decision code",
                "Use this OTP code to confirm your assigned board review decision.",
                app.getSaccoId(),
                app.getStationId(),
                boardMember.getPhone()
            );
            return ResponseEntity.ok(otpIssueResponse(otp, "We sent a board decision code using the station OTP delivery policy."));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/loan-applications/{id}/verify-signature-otp")
    @PreAuthorize("hasRole('BOARD') and @authz.isBoardAssignee(#id, principal)")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyBoardSignatureOtp(@PathVariable UUID id,
                                                                       @AuthenticationPrincipal AppUserPrincipal principal,
                                                                       @RequestParam String otpCode) {
        try {
            LoanApplication app = loanApplicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
            if (app.getStatus() != com.sacco.mvp.domain.LoanStatus.AWAITING_BOARD) {
                throw new IllegalStateException("This application is no longer waiting for board approval.");
            }
            BoardReview myReview = boardService.getMyReview(id, principal.getMemberId());
            if (myReview.getDecision() != BoardDecision.PENDING) {
                throw new IllegalStateException("You have already submitted your board decision.");
            }
            Member boardMember = requireMemberWithEmail(principal.getMemberId());
            emailOtpService.validateOtp(boardMember.getEmail(), EmailOtpPurpose.BOARD_SIGNATURE, otpCode);
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
    @PreAuthorize("hasRole('BOARD') and @authz.isBoardAssignee(#id, principal)")
    public String decide(@PathVariable UUID id,
                         @AuthenticationPrincipal AppUserPrincipal principal,
                         @RequestParam BoardDecision decision,
                         @RequestParam(required = false) String comment,
                         @RequestParam(required = false) String boardSignatureOtpCode,
                         RedirectAttributes ra) {
        try {
            Member boardMember = requireMemberWithEmail(principal.getMemberId());
            UUID otpTokenId = emailOtpService.validateOtp(
                boardMember.getEmail(), EmailOtpPurpose.BOARD_SIGNATURE, boardSignatureOtpCode);
            if (decision == BoardDecision.APPROVED) {
                requireSavedSignature(boardMember);
                boardService.decide(
                    id,
                    principal.getMemberId(),
                    com.sacco.mvp.domain.ApprovalWorkflowStage.BOARD,
                    decision,
                    comment,
                    boardMember.getSignatureText(),
                    OffsetDateTime.now()
                );
            } else {
                boardService.decide(id, principal.getMemberId(), decision, comment);
            }
            emailOtpService.consumeOtpById(otpTokenId);
            ra.addFlashAttribute("message", "Board decision submitted");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/board/loan-applications/" + id;
    }

    @PostMapping("/loan-applications/{id}/undo")
    @PreAuthorize("hasRole('BOARD') and @authz.isBoardAssignee(#id, principal)")
    public String undo(@PathVariable UUID id,
                       @AuthenticationPrincipal AppUserPrincipal principal,
                       RedirectAttributes ra) {
        try {
            boardService.undoDecision(id, principal.getMemberId());
            ra.addFlashAttribute("message", "Board decision reversed");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/board/loan-applications/" + id;
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasRole('BOARD')")
    public String notifications(@AuthenticationPrincipal AppUserPrincipal principal,
                                @RequestParam(required = false) UUID highlight,
                                Model model) {
        applyBoardUi(model);
        model.addAttribute("notificationBreadcrumb", "Board Panel / Notifications");
        model.addAttribute("notificationSubtitle", "Workflow updates and alerts for the board queue in one place.");
        model.addAttribute("notifications", notificationInboxService.allViews(
            principal.getMemberId(), principal.getGrantedPositions()));
        model.addAttribute("highlightNotificationId", highlight);
        return "board/notifications";
    }

    @GetMapping("/notifications/{id}/open")
    @PreAuthorize("hasRole('BOARD')")
    public String openNotification(@PathVariable UUID id,
                                   @AuthenticationPrincipal AppUserPrincipal principal,
                                   RedirectAttributes ra) {
        try {
            return "redirect:" + notificationInboxService.openForMember(
                id, principal.getMemberId(), principal.getGrantedPositions(),
                principal.getPosition(), "/board/notifications");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/board/notifications";
        }
    }

    @PostMapping("/notifications/mark-all-read")
    @PreAuthorize("hasRole('BOARD')")
    public String markAllNotificationsRead(@AuthenticationPrincipal AppUserPrincipal principal,
                                           RedirectAttributes ra) {
        int updated = notificationInboxService.markAllAsRead(principal.getMemberId());
        if (updated > 0) {
            ra.addFlashAttribute("message", "All notifications have been marked as read.");
        } else {
            ra.addFlashAttribute("message", "There were no unread notifications.");
        }
        return "redirect:/board/notifications";
    }

    private void applyBoardUi(Model model) {
        model.addAttribute("reviewBasePath", "/board");
        model.addAttribute("reviewRoleLabel", message("review.board.role"));
        model.addAttribute("reviewRoleLabelLower", message("review.board.roleLower"));
        model.addAttribute("reviewPanelBreadcrumb", message("review.board.breadcrumb"));
        model.addAttribute("reviewPanelTitle", message("review.board.title"));
        model.addAttribute("reviewPanelSubtitle", message("review.board.subtitle"));
        model.addAttribute("reviewDecisionLabel", message("review.board.decision"));
        model.addAttribute("reviewAssessorTitle", message("review.board.assessors"));
        model.addAttribute("reviewAssessorDescription", message("review.board.assessorsHelp"));
        model.addAttribute("reviewApprovalOtpEnabled", true);
        model.addAttribute("reviewAwaitingStatus", "AWAITING_BOARD");
    }

    private String message(String code) {
        return messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
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

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String handleError(RuntimeException ex, RedirectAttributes ra) {
        ra.addFlashAttribute("error", ex.getMessage());
        return "redirect:/board/queue";
    }

    private String populateListing(AppUserPrincipal principal,
                                   Model model,
                                   List<BoardReview> reviews,
                                   boolean awaitingBoardOnly,
                                   String breadcrumb,
                                   String pageTitle,
                                   String emptyState,
                                   String listRoute,
                                   String searchId) {
        List<LoanApplication> apps = new java.util.ArrayList<>();
        Map<UUID, BoardDecision> myDecisions = new java.util.LinkedHashMap<>();
        Map<UUID, java.time.OffsetDateTime> myDecisionDates = new java.util.LinkedHashMap<>();
        String normalizedSearchId = normalizeBoardSearch(searchId);
        for (BoardReview review : reviews) {
            loanApplicationRepository.findById(review.getLoanApplicationId())
                .filter(app -> principal.getSaccoId().equals(app.getSaccoId()))
                .filter(app -> matchesApplicantStation(app, principal.getStationId()))
                .filter(app -> !awaitingBoardOnly || app.getStatus() == com.sacco.mvp.domain.LoanStatus.AWAITING_BOARD)
                .filter(app -> matchesBoardSearch(app, normalizedSearchId))
                .ifPresent(app -> {
                    apps.add(app);
                    myDecisions.put(app.getId(), review.getDecision());
                    myDecisionDates.put(app.getId(), review.getDecidedAt() == null ? review.getCreatedAt() : review.getDecidedAt());
                });
        }
        Map<UUID, String> applicantNames = memberRepository.findAllById(
                apps.stream().map(LoanApplication::getApplicantMemberId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));

        model.addAttribute("apps", apps);
        model.addAttribute("applicantNames", applicantNames);
        model.addAttribute("myDecisions", myDecisions);
        model.addAttribute("myDecisionDates", myDecisionDates);
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

    private String normalizeBoardSearch(String searchId) {
        return searchId == null ? "" : searchId.trim();
    }

    private boolean matchesBoardSearch(LoanApplication app, String searchId) {
        if (searchId == null || searchId.isBlank()) {
            return true;
        }
        Long applicationNumber = app.getApplicationNumber();
        return applicationNumber != null && String.valueOf(applicationNumber).contains(searchId);
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
            "#,##0.00", new java.text.DecimalFormatSymbols(java.util.Locale.US));
        return "TSh " + format.format(amount);
    }

    private String humanizeEnum(String value) {
        return value == null ? "-" : value.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
    }

    private LoanPresentationService.LoanPaymentSummaryView storedActiveLoanPaymentSummary(LoanApplication loan) {
        return loanPresentationService.parseLoanPaymentSummaryView(loan.getLoanPaymentSummaryJson());
    }

    private Member requireMemberWithEmail(UUID memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Member account not found."));
        if (member.getEmail() == null || member.getEmail().isBlank()) {
            throw new IllegalStateException("Add an email address to your member profile before requesting a board OTP.");
        }
        return member;
    }

    private void requireSavedSignature(Member member) {
        if (member.getSignatureText() == null || member.getSignatureText().isBlank()) {
            throw new IllegalStateException("Register your signature first before approving board reviews.");
        }
    }

    private String resolveSavedSignatureText(UUID memberId) {
        return memberRepository.findById(memberId)
            .map(Member::getSignatureText)
            .filter(text -> text != null && !text.isBlank())
            .orElse("");
    }

    private String shortMemberId(UUID memberId) {
        return memberId == null ? "-" : "#" + memberId.toString().substring(0, 8);
    }

    private String boardLoanStatusBadgeClass(LoanApplication app) {
        return switch (app.getStatus()) {
            case READY_FOR_MANAGER -> "bg-amber-50 text-amber-700";
            case MANAGER_ACCEPTED, AWAITING_BOARD -> "bg-blue-50 text-blue-700";
            case BOARD_APPROVED, FINAL_APPROVED, PAID -> "bg-emerald-50 text-emerald-700";
            case DEFAULTED -> "bg-rose-50 text-rose-700";
            case MANAGER_REJECTED, BOARD_REJECTED, FINAL_REJECTED -> "bg-rose-50 text-rose-700";
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
