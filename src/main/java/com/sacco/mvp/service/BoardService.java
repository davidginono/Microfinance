package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BoardService {
    private final BoardReviewRepository boardReviewRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final OutboxService outboxService;
    private final LoanProductWorkflowService loanProductWorkflowService;
    private final WorkflowRoutingService workflowRoutingService;
    private final LoanProductSettingRepository loanProductSettingRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final AuditService auditService;

    public List<LoanApplication> assignedPending(UUID boardMemberId) {
        return assignedPending(boardMemberId, ApprovalWorkflowStage.BOARD);
    }

    public List<LoanApplication> assignedPending(UUID boardMemberId, ApprovalWorkflowStage stage) {
        List<BoardReview> reviews = boardReviewRepository.findTop100ByBoardMemberIdAndReviewStageAndDecisionOrderByCreatedAtDesc(
            boardMemberId, stage, BoardDecision.PENDING);
        List<LoanApplication> apps = new ArrayList<>();
        for (BoardReview review : reviews) {
            loanApplicationRepository.findById(review.getLoanApplicationId())
                .filter(app -> app.getStatus() == pendingStatusFor(stage))
                .ifPresent(apps::add);
        }
        return apps;
    }

    public List<BoardReview> assignedAll(UUID boardMemberId) {
        return assignedAll(boardMemberId, ApprovalWorkflowStage.BOARD);
    }

    public List<BoardReview> assignedAll(UUID boardMemberId, ApprovalWorkflowStage stage) {
        return boardReviewRepository.findTop100ByBoardMemberIdAndReviewStageOrderByCreatedAtDesc(boardMemberId, stage);
    }

    public BoardReview getMyReview(UUID loanId, UUID boardMemberId) {
        return getMyReview(loanId, boardMemberId, ApprovalWorkflowStage.BOARD);
    }

    public BoardReview getMyReview(UUID loanId, UUID boardMemberId, ApprovalWorkflowStage stage) {
        return boardReviewRepository.findByLoanApplicationIdAndBoardMemberIdAndReviewStage(loanId, boardMemberId, stage)
            .orElseThrow(() -> new IllegalArgumentException("Board assignment not found"));
    }

    public List<BoardReview> reviewsForLoan(UUID loanId) {
        return reviewsForLoan(loanId, ApprovalWorkflowStage.BOARD);
    }

    public List<BoardReview> reviewsForLoan(UUID loanId, ApprovalWorkflowStage stage) {
        return boardReviewRepository.findByLoanApplicationIdAndReviewStage(loanId, stage);
    }

    @Transactional
    public void decide(UUID loanId, UUID boardMemberId, BoardDecision decision, String comment) {
        decide(loanId, boardMemberId, ApprovalWorkflowStage.BOARD, decision, comment, null, null);
    }

    public void undoDecision(UUID loanId, UUID boardMemberId) {
        throw new IllegalStateException("Board review decisions cannot be reversed.");
    }

    @Transactional
    public void decide(UUID loanId,
                       UUID boardMemberId,
                       BoardDecision decision,
                       String comment,
                       String signatureText,
                       OffsetDateTime verifiedAt) {
        decide(loanId, boardMemberId, ApprovalWorkflowStage.BOARD, decision, comment, signatureText, verifiedAt);
    }

    @Transactional
    public void decide(UUID loanId,
                       UUID boardMemberId,
                       ApprovalWorkflowStage stage,
                       BoardDecision decision,
                       String comment,
                       String signatureText,
                       OffsetDateTime verifiedAt) {
        if (decision == BoardDecision.PENDING) {
            throw new IllegalArgumentException("Decision must be APPROVED or REJECTED");
        }

        BoardReview review = boardReviewRepository.findByLoanApplicationIdAndBoardMemberIdAndReviewStage(loanId, boardMemberId, stage)
            .orElseThrow(() -> new IllegalArgumentException("Board assignment not found"));
        if (review.getDecision() != BoardDecision.PENDING) {
            throw new IllegalStateException("Board member already decided");
        }

        LoanApplication app = loanApplicationRepository.findById(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        LoanStatus expectedStatus = pendingStatusFor(stage);
        if (app.getStatus() != expectedStatus) {
            throw new IllegalStateException("Application not in " + stage.getDisplayLabel().toLowerCase() + " stage");
        }

        String normalizedComment = normalizeComment(comment);
        if (decision == BoardDecision.REJECTED && normalizedComment == null) {
            throw new IllegalArgumentException("Add a comment before rejecting this review.");
        }
        String normalizedSignature = normalizeSignature(signatureText, verifiedAt);
        review.setDecision(decision);
        review.setComment(normalizedComment);
        review.setBoardSignatureText(normalizedSignature);
        review.setBoardSignatureVerifiedAt(verifiedAt);
        review.setDecidedAt(OffsetDateTime.now());
        boardReviewRepository.save(review);
        evaluateOutcome(app, stage, boardMemberId);
        auditBoardReview(app, boardMemberId, stage, decision);
    }

    private void evaluateOutcome(LoanApplication app,
                                 ApprovalWorkflowStage stage,
                                 UUID actorMemberId) {
        UUID loanId = app.getId();
        long approvals = boardReviewRepository.countByLoanApplicationIdAndReviewStageAndDecision(
            loanId, stage, BoardDecision.APPROVED);
        long rejections = boardReviewRepository.countByLoanApplicationIdAndReviewStageAndDecision(
            loanId, stage, BoardDecision.REJECTED);
        LoanProductWorkflowService.WorkflowDefinition workflow = loanProductWorkflowService.resolveForApplication(app);
        long assignedReviewers = boardReviewRepository.countByLoanApplicationIdAndReviewStage(loanId, stage);
        int minimumVotes;
        int approvalThreshold;
        if (stage == ApprovalWorkflowStage.LOAN_OFFICER || stage == ApprovalWorkflowStage.CHAIRPERSON) {
            minimumVotes = 1;
            approvalThreshold = 1;
        } else if (stage == ApprovalWorkflowStage.BOARD) {
            int requiredBoardApprovals = Math.max(1, Math.toIntExact(Math.max(assignedReviewers, 1L)));
            minimumVotes = requiredBoardApprovals;
            approvalThreshold = requiredBoardApprovals;
        } else {
            minimumVotes = Math.max(workflow.committeeMinimumVotes(), 1);
            approvalThreshold = Math.max(workflow.committeeApprovalThreshold(), 1);
        }
        long totalDecisions = approvals + rejections;
        long rejectionThreshold = Math.max(1, minimumVotes - approvalThreshold + 1L);

        if (approvals >= approvalThreshold) {
            workflowRoutingService.advanceAfterApproval(app, stage, actorMemberId);
            loanApplicationRepository.save(app);
            return;
        }

        LoanStatus nextStatus = pendingStatusFor(stage);
        if (rejections >= rejectionThreshold || (totalDecisions >= minimumVotes && approvals < approvalThreshold)) {
            nextStatus = rejectionStatusFor(stage);
        }

        if (app.getStatus() == nextStatus) {
            return;
        } else if (rejections >= rejectionThreshold || (totalDecisions >= minimumVotes && approvals < approvalThreshold)) {
            app.setStatus(nextStatus);
            app.setApplicantRejectionAcknowledgedAt(null);
            app.setUpdatedAt(OffsetDateTime.now());
            loanApplicationRepository.save(app);
            outboxService.enqueue("LOAN", loanId, nextStatus.name(), app.getApplicantMemberId(),
                actorMemberId, app.getSaccoId(), app.getStationId(),
                Map.of("loanId", loanId.toString()));
        }
    }

    private void auditBoardReview(LoanApplication app,
                                  UUID actorId,
                                  ApprovalWorkflowStage stage,
                                  BoardDecision decision) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("result", "SUCCESS");
        details.put("saccoId", app.getSaccoId());
        details.put("stationId", app.getStationId());
        details.put("applicationNumber", app.getApplicationNumber());
        details.put("loanId", app.getLoanId());
        details.put("reviewStage", stage == null ? null : stage.name());
        details.put("decision", decision == null ? null : decision.name());
        details.put("workflowStatus", app.getStatus() == null ? null : app.getStatus().name());
        auditService.logEvent(
            "LOAN_APPLICATION",
            app.getId(),
            "BOARD_REVIEWED_LOAN_APPLICATION",
            actorId,
            AuditEventStatus.SUCCESS,
            "Board reviewed loan application",
            "LOAN_APPLICATION",
            "Loan Application #" + app.getApplicationNumber(),
            app.getSaccoId(),
            app.getStationId(),
            details
        );
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String normalized = comment.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeSignature(String signatureText, OffsetDateTime verifiedAt) {
        String normalizedSignature = signatureText == null ? "" : signatureText.trim();
        if (normalizedSignature.isBlank() || verifiedAt == null) {
            throw new IllegalStateException("Save and verify your staff signature before recording this decision.");
        }
        return normalizedSignature;
    }

    private LoanStatus pendingStatusFor(ApprovalWorkflowStage stage) {
        return switch (stage) {
            case LOAN_OFFICER -> LoanStatus.AWAITING_LOAN_OFFICER;
            case CHAIRPERSON -> LoanStatus.AWAITING_CHAIRPERSON;
            case CREDIT_COMMITTEE -> LoanStatus.AWAITING_CREDIT_COMMITTEE;
            case BOARD -> LoanStatus.AWAITING_BOARD;
            case ACCOUNTANT -> LoanStatus.AWAITING_ACCOUNTANT;
            case MANAGER -> LoanStatus.READY_FOR_MANAGER;
            case DISBURSEMENT_OFFICER -> LoanStatus.READY_FOR_DISBURSEMENT;
        };
    }

    private LoanStatus rejectionStatusFor(ApprovalWorkflowStage stage) {
        return switch (stage) {
            case LOAN_OFFICER -> LoanStatus.LOAN_OFFICER_REJECTED;
            case CHAIRPERSON -> LoanStatus.CHAIRPERSON_REJECTED;
            case ACCOUNTANT -> LoanStatus.ACCOUNTANT_REJECTED;
            case DISBURSEMENT_OFFICER -> LoanStatus.REJECTED;
            case MANAGER -> LoanStatus.MANAGER_REJECTED;
            case BOARD -> LoanStatus.BOARD_REJECTED;
            case CREDIT_COMMITTEE -> LoanStatus.CREDIT_COMMITTEE_REJECTED;
        };
    }
}
