package com.sacco.mvp.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.sacco.mvp.service.BoardService;
import com.sacco.mvp.service.EmailOtpService;
import com.sacco.mvp.service.ExternalAccountStatusService;
import com.sacco.mvp.service.LoanPresentationService;
import com.sacco.mvp.service.NotificationInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/board")
@PreAuthorize("(hasRole('BOARD') and @userClaims.has(principal, 'REVIEW_BOARD_QUEUE')) or hasRole('ADMIN')")
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

    @GetMapping("/assigned")
    public String assigned() {
        return "redirect:/board/queue";
    }

    @GetMapping("/queue")
    public String queue(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        List<BoardReview> myReviews = boardService.assignedAll(principal.getMemberId()).stream()
            .filter(review -> review.getDecision() == BoardDecision.PENDING)
            .toList();
        return populateListing(
            model,
            myReviews,
            true,
            "Board Panel / Queue",
            "Board Queue",
            "No applications are currently waiting in your board queue."
        );
    }

    @GetMapping("/archive")
    public String archive(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        List<BoardReview> myReviews = boardService.assignedAll(principal.getMemberId()).stream()
            .filter(review -> review.getDecision() != BoardDecision.PENDING)
            .toList();
        return populateListing(
            model,
            myReviews,
            false,
            "Board Panel / Archive",
            "Board Archive",
            "No reviewed applications are available in your archive yet."
        );
    }

    @GetMapping("/loan-applications/{id}")
    @PreAuthorize("hasAnyRole('BOARD','ADMIN') and @authz.isBoardAssignee(#id, principal)")
    public String detail(@PathVariable UUID id,
                         @AuthenticationPrincipal AppUserPrincipal principal,
                         Model model) {
        LoanApplication app = loanApplicationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        Member applicant = memberRepository.findById(app.getApplicantMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Applicant not found"));
        BoardReview myReview = boardService.getMyReview(id, principal.getMemberId());
        List<BoardReview> boardReviews = boardService.reviewsForLoan(id);
        List<GuarantorRequest> guarantorRequests = guarantorRequestRepository.findByLoanApplicationId(id);
        Map<UUID, String> guarantorNames = memberRepository.findAllById(
                guarantorRequests.stream().map(GuarantorRequest::getGuarantorMemberId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));
        Map<UUID, Member> boardMembers = memberRepository.findAllById(
                boardReviews.stream().map(BoardReview::getBoardMemberId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, member -> member));
        model.addAttribute("app", app);
        model.addAttribute("applicant", applicant);
        model.addAttribute("myReview", myReview);
        model.addAttribute("formFields", parseFormData(app.getFormData()));
        model.addAttribute("financialFields", loanPresentationService.parseFinancialFields(app.getFinancialSnapshot()));
        model.addAttribute("attachments", loanPresentationService.parseAttachments(app.getAttachmentsJson()));
        model.addAttribute("guarantorRequests", guarantorRequests);
        model.addAttribute("guarantorNames", guarantorNames);
        model.addAttribute("managerReason", loanPresentationService.latestManagerReason(id));
        model.addAttribute("applicantExternalAccountStatus", externalAccountStatusService.resolve(applicant));
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
    @PreAuthorize("hasAnyRole('BOARD','ADMIN') and @authz.isBoardAssignee(#loanId, principal)")
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
            Member boardMember = requireMemberWithSavedSignature(principal.getMemberId());
            emailOtpService.issueOtp(
                boardMember.getEmail(),
                EmailOtpPurpose.BOARD_SIGNATURE,
                boardMember.getId(),
                "Your SACCO MVP board approval code",
                "Use this OTP code to confirm your signature and approve the assigned board review."
            );
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "We sent a board approval code to " + boardMember.getEmail() + "."
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
            if (decision == BoardDecision.APPROVED) {
                Member boardMember = requireMemberWithSavedSignature(principal.getMemberId());
                UUID otpTokenId = emailOtpService.validateOtp(
                    boardMember.getEmail(), EmailOtpPurpose.BOARD_SIGNATURE, boardSignatureOtpCode);
                boardService.decide(
                    id,
                    principal.getMemberId(),
                    decision,
                    comment,
                    boardMember.getSignatureText(),
                    OffsetDateTime.now()
                );
                emailOtpService.consumeOtpById(otpTokenId);
            } else {
                boardService.decide(id, principal.getMemberId(), decision, comment);
            }
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
    @PreAuthorize("hasRole('BOARD') or hasRole('ADMIN')")
    public String notifications(@AuthenticationPrincipal AppUserPrincipal principal,
                                @RequestParam(required = false) UUID highlight,
                                Model model) {
        model.addAttribute("notifications", notificationInboxService.allViews(
            principal.getMemberId(), principal.getGrantedPositions()));
        model.addAttribute("highlightNotificationId", highlight);
        return "board/notifications";
    }

    @GetMapping("/notifications/{id}/open")
    @PreAuthorize("hasRole('BOARD') or hasRole('ADMIN')")
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

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String handleError(RuntimeException ex, RedirectAttributes ra) {
        ra.addFlashAttribute("error", ex.getMessage());
        return "redirect:/board/queue";
    }

    private String populateListing(Model model,
                                   List<BoardReview> reviews,
                                   boolean awaitingBoardOnly,
                                   String breadcrumb,
                                   String pageTitle,
                                   String emptyState) {
        List<LoanApplication> apps = new java.util.ArrayList<>();
        Map<UUID, BoardDecision> myDecisions = new java.util.LinkedHashMap<>();
        Map<UUID, java.time.OffsetDateTime> myDecisionDates = new java.util.LinkedHashMap<>();
        for (BoardReview review : reviews) {
            loanApplicationRepository.findById(review.getLoanApplicationId())
                .filter(app -> !awaitingBoardOnly || app.getStatus() == com.sacco.mvp.domain.LoanStatus.AWAITING_BOARD)
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
        return "board/assigned";
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
            || normalized.equals("purpose")
            || normalized.equals("additionalnotes");
    }

    private String humanizeFieldLabel(String key) {
        return switch (key) {
            case "nationalId" -> "National ID";
            case "employerName" -> "Employer Name";
            case "additionalNotes" -> "Additional Notes";
            case "hasExistingLoan" -> "Existing Loan";
            default -> key.replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .trim();
        };
    }

    private Member requireMemberWithSavedSignature(UUID memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Member account not found."));
        if (member.getEmail() == null || member.getEmail().isBlank()) {
            throw new IllegalStateException("Add an email address to your member profile before requesting a board OTP.");
        }
        if (member.getSignatureText() == null || member.getSignatureText().isBlank()) {
            throw new IllegalStateException("Register your signature first before approving board reviews.");
        }
        return member;
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
}
