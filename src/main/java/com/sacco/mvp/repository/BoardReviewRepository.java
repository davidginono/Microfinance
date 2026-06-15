package com.sacco.mvp.repository;

import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardReviewRepository extends JpaRepository<BoardReview, UUID> {
    List<BoardReview> findByLoanApplicationId(UUID loanApplicationId);

    List<BoardReview> findByLoanApplicationIdAndReviewStage(UUID loanApplicationId, ApprovalWorkflowStage reviewStage);

    void deleteByLoanApplicationId(UUID loanApplicationId);

    void deleteByLoanApplicationIdAndReviewStage(UUID loanApplicationId, ApprovalWorkflowStage reviewStage);

    long countByLoanApplicationIdAndDecision(UUID loanApplicationId, BoardDecision decision);

    long countByLoanApplicationIdAndReviewStageAndDecision(UUID loanApplicationId, ApprovalWorkflowStage reviewStage, BoardDecision decision);

    List<BoardReview> findByBoardMemberIdAndDecision(UUID boardMemberId, BoardDecision decision);

    List<BoardReview> findByBoardMemberIdAndReviewStageAndDecision(UUID boardMemberId,
                                                                   ApprovalWorkflowStage reviewStage,
                                                                   BoardDecision decision);

    List<BoardReview> findTop100ByBoardMemberIdAndReviewStageAndDecisionOrderByCreatedAtDesc(UUID boardMemberId,
                                                                                              ApprovalWorkflowStage reviewStage,
                                                                                              BoardDecision decision);

    List<BoardReview> findByBoardMemberIdOrderByCreatedAtDesc(UUID boardMemberId);

    List<BoardReview> findByBoardMemberIdAndReviewStageOrderByCreatedAtDesc(UUID boardMemberId, ApprovalWorkflowStage reviewStage);

    List<BoardReview> findTop100ByBoardMemberIdAndReviewStageOrderByCreatedAtDesc(UUID boardMemberId, ApprovalWorkflowStage reviewStage);

    Optional<BoardReview> findByLoanApplicationIdAndBoardMemberId(UUID loanApplicationId, UUID boardMemberId);

    Optional<BoardReview> findByLoanApplicationIdAndBoardMemberIdAndReviewStage(UUID loanApplicationId,
                                                                                 UUID boardMemberId,
                                                                                 ApprovalWorkflowStage reviewStage);

    @Query("""
        select r
        from BoardReview r
        where r.boardMemberId = :boardMemberId
          and r.reviewStage = :reviewStage
          and (cast(:createdFrom as timestamp) is null or coalesce(r.decidedAt, r.createdAt) >= :createdFrom)
          and (cast(:createdToExclusive as timestamp) is null or coalesce(r.decidedAt, r.createdAt) < :createdToExclusive)
        order by coalesce(r.decidedAt, r.createdAt) desc
        """)
    List<BoardReview> findForAnalytics(@Param("boardMemberId") UUID boardMemberId,
                                       @Param("reviewStage") ApprovalWorkflowStage reviewStage,
                                       @Param("createdFrom") OffsetDateTime createdFrom,
                                       @Param("createdToExclusive") OffsetDateTime createdToExclusive);
}
