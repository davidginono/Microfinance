package com.sacco.mvp.repository;

import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.ManagerReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

public interface ManagerReviewRepository extends JpaRepository<ManagerReview, UUID> {
    List<ManagerReview> findByLoanApplicationIdOrderByCreatedAtAsc(UUID loanApplicationId);

    @Query("""
        select count(r)
        from ManagerReview r, LoanApplication l
        where r.loanApplicationId = l.id
          and l.saccoId = :saccoId
          and (cast(:status as string) is null or l.status = :status)
          and (cast(:loanType as string) is null or l.loanType = :loanType)
          and (cast(:createdFrom as timestamp) is null or l.createdAt >= :createdFrom)
          and (cast(:createdToExclusive as timestamp) is null or l.createdAt < :createdToExclusive)
        """)
    long countDecisionsForReport(@Param("saccoId") String saccoId,
                                 @Param("status") LoanStatus status,
                                 @Param("loanType") LoanType loanType,
                                 @Param("createdFrom") OffsetDateTime createdFrom,
                                 @Param("createdToExclusive") OffsetDateTime createdToExclusive);

    List<ManagerReview> findByLoanApplicationIdAndReviewStageOrderByCreatedAtAsc(UUID loanApplicationId,
                                                                                  ApprovalWorkflowStage reviewStage);

    void deleteByLoanApplicationId(UUID loanApplicationId);

    List<ManagerReview> findByLoanApplicationIdInOrderByCreatedAtDesc(Collection<UUID> loanApplicationIds);

    Optional<ManagerReview> findFirstByLoanApplicationIdOrderByCreatedAtDesc(UUID loanApplicationId);

    Optional<ManagerReview> findFirstByLoanApplicationIdAndReviewStageOrderByCreatedAtDesc(UUID loanApplicationId,
                                                                                            ApprovalWorkflowStage reviewStage);

    List<ManagerReview> findByManagerMemberIdAndReviewStageOrderByCreatedAtDesc(UUID managerMemberId,
                                                                                 ApprovalWorkflowStage reviewStage);

    List<ManagerReview> findByManagerMemberIdAndReviewStageAndCreatedAtBetweenOrderByCreatedAtDesc(UUID managerMemberId,
                                                                                                     ApprovalWorkflowStage reviewStage,
                                                                                                     OffsetDateTime from,
                                                                                                     OffsetDateTime to);

    @Query("""
        select r
        from ManagerReview r, LoanApplication l
        where r.loanApplicationId = l.id
          and r.managerMemberId = :reviewerId
          and r.reviewStage = :reviewStage
          and l.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
          and (cast(:decision as string) is null or r.decision = :decision)
          and r.createdAt >= :reviewedFrom
          and r.createdAt <= :reviewedTo
        order by r.createdAt desc
        """)
    List<ManagerReview> findForWorkflowReport(@Param("reviewerId") UUID reviewerId,
                                              @Param("reviewStage") ApprovalWorkflowStage reviewStage,
                                              @Param("saccoId") String saccoId,
                                              @Param("stationId") String stationId,
                                              @Param("decision") ManagerDecision decision,
                                              @Param("reviewedFrom") OffsetDateTime reviewedFrom,
                                              @Param("reviewedTo") OffsetDateTime reviewedTo);

    @Query("""
        select r
        from ManagerReview r, LoanApplication l
        where r.loanApplicationId = l.id
          and r.managerMemberId = :managerMemberId
          and r.reviewStage = :reviewStage
          and l.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
          and (cast(:createdFrom as timestamp) is null or r.createdAt >= :createdFrom)
          and (cast(:createdToExclusive as timestamp) is null or r.createdAt < :createdToExclusive)
        order by r.createdAt desc
        """)
    List<ManagerReview> findForAnalytics(@Param("managerMemberId") UUID managerMemberId,
                                         @Param("reviewStage") ApprovalWorkflowStage reviewStage,
                                         @Param("saccoId") String saccoId,
                                         @Param("stationId") String stationId,
                                         @Param("createdFrom") OffsetDateTime createdFrom,
                                         @Param("createdToExclusive") OffsetDateTime createdToExclusive);

    @Query(
        value = """
            select r.*
            from manager_reviews r
            join loan_applications l on l.id = r.loan_application_id
            where r.manager_member_id = :reviewerId
              and r.review_stage = :reviewStage
              and l.sacco_id = :saccoId
              and lower(l.station_id) = lower(:stationId)
              and (cast(:reviewedFrom as timestamp) is null or r.created_at >= :reviewedFrom)
              and (cast(:reviewedToExclusive as timestamp) is null or r.created_at < :reviewedToExclusive)
              and r.id = (
                select r2.id
                from manager_reviews r2
                where r2.manager_member_id = :reviewerId
                  and r2.review_stage = :reviewStage
                  and r2.loan_application_id = r.loan_application_id
                order by r2.created_at desc
                limit 1
              )
              and (:filterDecision = false or r.decision = :decision)
              and (:filterStatuses = false or l.status in (:statuses))
              and (
                :searchTerm = ''
                or (:searchLoanId = true and lower(coalesce(l.loan_id, '')) like concat('%', lower(:searchTerm), '%'))
                or (:searchLoanId = false and cast(l.application_number as text) like concat('%', :searchTerm, '%'))
              )
            order by r.created_at desc
            """,
        countQuery = """
            select count(*)
            from manager_reviews r
            join loan_applications l on l.id = r.loan_application_id
            where r.manager_member_id = :reviewerId
              and r.review_stage = :reviewStage
              and l.sacco_id = :saccoId
              and lower(l.station_id) = lower(:stationId)
              and (cast(:reviewedFrom as timestamp) is null or r.created_at >= :reviewedFrom)
              and (cast(:reviewedToExclusive as timestamp) is null or r.created_at < :reviewedToExclusive)
              and r.id = (
                select r2.id
                from manager_reviews r2
                where r2.manager_member_id = :reviewerId
                  and r2.review_stage = :reviewStage
                  and r2.loan_application_id = r.loan_application_id
                order by r2.created_at desc
                limit 1
              )
              and (:filterDecision = false or r.decision = :decision)
              and (:filterStatuses = false or l.status in (:statuses))
              and (
                :searchTerm = ''
                or (:searchLoanId = true and lower(coalesce(l.loan_id, '')) like concat('%', lower(:searchTerm), '%'))
                or (:searchLoanId = false and cast(l.application_number as text) like concat('%', :searchTerm, '%'))
              )
            """,
        nativeQuery = true
    )
    Page<ManagerReview> findLatestArchivePage(@Param("reviewerId") UUID reviewerId,
                                              @Param("reviewStage") String reviewStage,
                                              @Param("saccoId") String saccoId,
                                              @Param("stationId") String stationId,
                                              @Param("reviewedFrom") OffsetDateTime reviewedFrom,
                                              @Param("reviewedToExclusive") OffsetDateTime reviewedToExclusive,
                                              @Param("filterDecision") boolean filterDecision,
                                              @Param("decision") String decision,
                                              @Param("filterStatuses") boolean filterStatuses,
                                              @Param("statuses") Collection<String> statuses,
                                              @Param("searchTerm") String searchTerm,
                                              @Param("searchLoanId") boolean searchLoanId,
                                              Pageable pageable);
}
