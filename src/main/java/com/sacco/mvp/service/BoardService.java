package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BoardService {
    private final BoardReviewRepository boardReviewRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final OutboxService outboxService;

    public List<LoanApplication> assignedPending(UUID boardMemberId) {
        List<BoardReview> reviews = boardReviewRepository.findByBoardMemberIdAndDecision(boardMemberId, BoardDecision.PENDING);
        List<LoanApplication> apps = new ArrayList<>();
        for (BoardReview review : reviews) {
            loanApplicationRepository.findById(review.getLoanApplicationId())
                .filter(app -> app.getStatus() == LoanStatus.AWAITING_BOARD)
                .ifPresent(apps::add);
        }
        return apps;
    }

    public List<BoardReview> assignedAll(UUID boardMemberId) {
        return boardReviewRepository.findByBoardMemberIdOrderByCreatedAtDesc(boardMemberId);
    }

    public BoardReview getMyReview(UUID loanId, UUID boardMemberId) {
        return boardReviewRepository.findByLoanApplicationIdAndBoardMemberId(loanId, boardMemberId)
            .orElseThrow(() -> new IllegalArgumentException("Board assignment not found"));
    }

    public List<BoardReview> reviewsForLoan(UUID loanId) {
        return boardReviewRepository.findByLoanApplicationId(loanId);
    }

    @Transactional
    public void decide(UUID loanId, UUID boardMemberId, BoardDecision decision, String comment) {
        decide(loanId, boardMemberId, decision, comment, null, null);
    }

    @Transactional
    public void decide(UUID loanId,
                       UUID boardMemberId,
                       BoardDecision decision,
                       String comment,
                       String signatureText,
                       OffsetDateTime verifiedAt) {
        if (decision == BoardDecision.PENDING) {
            throw new IllegalArgumentException("Decision must be APPROVED or REJECTED");
        }

        BoardReview review = boardReviewRepository.findByLoanApplicationIdAndBoardMemberId(loanId, boardMemberId)
            .orElseThrow(() -> new IllegalArgumentException("Board assignment not found"));
        if (review.getDecision() != BoardDecision.PENDING) {
            throw new IllegalStateException("Board member already decided");
        }

        LoanApplication app = loanApplicationRepository.findById(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        if (app.getStatus() != LoanStatus.AWAITING_BOARD) {
            throw new IllegalStateException("Application not in board stage");
        }

        review.setDecision(decision);
        review.setComment(comment);
        if (decision == BoardDecision.APPROVED) {
            review.setBoardSignatureText(signatureText == null ? null : signatureText.trim());
            review.setBoardSignatureVerifiedAt(verifiedAt);
        } else {
            review.setBoardSignatureText(null);
            review.setBoardSignatureVerifiedAt(null);
        }
        review.setDecidedAt(OffsetDateTime.now());
        boardReviewRepository.save(review);
        evaluateOutcome(app);
    }

    @Transactional
    public void undoDecision(UUID loanId, UUID boardMemberId) {
        throw new IllegalStateException("Board review decisions cannot be reversed.");
    }

    private void evaluateOutcome(LoanApplication app) {
        UUID loanId = app.getId();
        long approvals = boardReviewRepository.countByLoanApplicationIdAndDecision(loanId, BoardDecision.APPROVED);
        long rejections = boardReviewRepository.countByLoanApplicationIdAndDecision(loanId, BoardDecision.REJECTED);
        int quorum = saccoSettingsRepository.findById(app.getSaccoId())
            .map(SaccoSettings::getBoardQuorum)
            .filter(value -> value != null && value > 0)
            .orElse(2);

        LoanStatus nextStatus = LoanStatus.AWAITING_BOARD;
        if (approvals >= quorum) {
            nextStatus = LoanStatus.BOARD_APPROVED;
        } else if (rejections >= quorum) {
            nextStatus = LoanStatus.BOARD_REJECTED;
        }

        if (app.getStatus() == nextStatus) {
            return;
        }

        app.setStatus(nextStatus);
        app.setUpdatedAt(OffsetDateTime.now());
        loanApplicationRepository.save(app);

        if (nextStatus == LoanStatus.BOARD_APPROVED) {
            outboxService.enqueue("LOAN", loanId, "BOARD_APPROVED", app.getApplicantMemberId(),
                Map.of("loanId", loanId.toString()));
        } else if (nextStatus == LoanStatus.BOARD_REJECTED) {
            outboxService.enqueue("LOAN", loanId, "BOARD_REJECTED", app.getApplicantMemberId(),
                Map.of("loanId", loanId.toString()));
        }
    }
}
