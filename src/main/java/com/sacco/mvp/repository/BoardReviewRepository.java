package com.sacco.mvp.repository;

import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    List<BoardReview> findByLoanApplicationIdAndReviewStage(UUID loanApplicationId, ApprovalWorkflowStage reviewStage);

    long countByLoanApplicationIdAndReviewStage(UUID loanApplicationId, ApprovalWorkflowStage reviewStage);

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

    @Query(
        value = """
            select r.*
            from board_reviews r
            join loan_applications l on l.id = r.loan_application_id
            where r.board_member_id = :reviewerId
              and r.review_stage = :reviewStage
              and r.decision <> 'PENDING'
              and l.sacco_id = :saccoId
              and (cast(:stationId as text) is null or lower(l.station_id) = lower(cast(:stationId as text)))
              and (:filterDecision = false or r.decision = :decision)
              and (:filterStatuses = false or l.status in (:statuses))
              and (
                :searchTerm = ''
                or cast(l.application_number as text) like concat('%', :searchTerm, '%')
                or lower(coalesce(l.loan_id, '')) like concat('%', lower(:searchTerm), '%')
              )
            order by coalesce(r.decided_at, r.created_at) desc
            """,
        countQuery = """
            select count(*)
            from board_reviews r
            join loan_applications l on l.id = r.loan_application_id
            where r.board_member_id = :reviewerId
              and r.review_stage = :reviewStage
              and r.decision <> 'PENDING'
              and l.sacco_id = :saccoId
              and (cast(:stationId as text) is null or lower(l.station_id) = lower(cast(:stationId as text)))
              and (:filterDecision = false or r.decision = :decision)
              and (:filterStatuses = false or l.status in (:statuses))
              and (
                :searchTerm = ''
                or cast(l.application_number as text) like concat('%', :searchTerm, '%')
                or lower(coalesce(l.loan_id, '')) like concat('%', lower(:searchTerm), '%')
              )
            """,
        nativeQuery = true
    )
    Page<BoardReview> findArchivePage(@Param("reviewerId") UUID reviewerId,
                                      @Param("reviewStage") String reviewStage,
                                      @Param("saccoId") String saccoId,
                                      @Param("stationId") String stationId,
                                      @Param("filterDecision") boolean filterDecision,
                                      @Param("decision") String decision,
                                      @Param("filterStatuses") boolean filterStatuses,
                                      @Param("statuses") Collection<String> statuses,
                                      @Param("searchTerm") String searchTerm,
                                      Pageable pageable);

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
