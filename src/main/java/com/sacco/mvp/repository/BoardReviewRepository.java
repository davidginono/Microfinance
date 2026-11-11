package com.sacco.mvp.repository;

import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardReviewRepository extends JpaRepository<BoardReview, UUID> {

    List<BoardReview> findByLoanApplicationId(UUID loanApplicationId);

    List<BoardReview> findByLoanApplicationIdIn(Collection<UUID> loanApplicationIds);

    List<BoardReview> findByLoanApplicationIdInOrderByCreatedAtDesc(Collection<UUID> loanApplicationIds);

    void deleteByLoanApplicationId(UUID loanApplicationId);

    long countByLoanApplicationIdAndDecision(UUID loanApplicationId, BoardDecision decision);

    List<BoardReview> findByBoardMemberIdAndDecision(UUID boardMemberId, BoardDecision decision);

    List<BoardReview> findByBoardMemberIdAndReviewStageAndDecision(UUID boardMemberId,
                                                                   ApprovalWorkflowStage reviewStage,
                                                                   BoardDecision decision);

    List<BoardReview> findByBoardMemberIdAndReviewStageOrderByCreatedAtDesc(UUID boardMemberId, ApprovalWorkflowStage reviewStage);

    Optional<BoardReview> findByLoanApplicationIdAndBoardMemberId(UUID loanApplicationId, UUID boardMemberId);

    @Query("""
        select r
        from BoardReview r, LoanApplication l
        where r.loanApplicationId = l.id
          and r.boardMemberId = :reviewerId
          and r.reviewStage = :reviewStage
          and r.decision <> com.sacco.mvp.domain.BoardDecision.PENDING
          and l.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
          and (cast(:decision as string) is null or r.decision = :decision)
          and coalesce(r.decidedAt, r.createdAt) >= :reviewedFrom
          and coalesce(r.decidedAt, r.createdAt) < :reviewedToExclusive
        order by coalesce(r.decidedAt, r.createdAt) desc
        """)
    List<BoardReview> findForWorkflowReport(@Param("reviewerId") UUID reviewerId,
                                            @Param("reviewStage") ApprovalWorkflowStage reviewStage,
                                            @Param("saccoId") String saccoId,
                                            @Param("stationId") String stationId,
                                            @Param("decision") BoardDecision decision,
                                            @Param("reviewedFrom") OffsetDateTime reviewedFrom,
                                            @Param("reviewedToExclusive") OffsetDateTime reviewedToExclusive);

    @Query("""
        select r
        from BoardReview r, LoanApplication l
        where r.loanApplicationId = l.id
          and r.boardMemberId = :boardMemberId
          and r.reviewStage = :reviewStage
          and l.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
          and (cast(:createdFrom as timestamp) is null or coalesce(r.decidedAt, r.createdAt) >= :createdFrom)
          and (cast(:createdToExclusive as timestamp) is null or coalesce(r.decidedAt, r.createdAt) < :createdToExclusive)
        order by coalesce(r.decidedAt, r.createdAt) desc
        """)
    List<BoardReview> findForAnalytics(@Param("boardMemberId") UUID boardMemberId,
                                       @Param("reviewStage") ApprovalWorkflowStage reviewStage,
                                       @Param("saccoId") String saccoId,
                                       @Param("stationId") String stationId,
                                       @Param("createdFrom") OffsetDateTime createdFrom,
                                       @Param("createdToExclusive") OffsetDateTime createdToExclusive);
}
