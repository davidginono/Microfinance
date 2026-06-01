package com.sacco.mvp.repository;

import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.ManagerReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
          and (:status is null or l.status = :status)
          and (:loanType is null or l.loanType = :loanType)
          and (:createdFrom is null or l.createdAt >= :createdFrom)
          and (:createdToExclusive is null or l.createdAt < :createdToExclusive)
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

    List<ManagerReview> findAllByOrderByCreatedAtDesc();

    List<ManagerReview> findByManagerMemberIdAndReviewStageOrderByCreatedAtDesc(UUID managerMemberId,
                                                                                 ApprovalWorkflowStage reviewStage);

    List<ManagerReview> findByManagerMemberIdAndReviewStageAndCreatedAtBetweenOrderByCreatedAtDesc(UUID managerMemberId,
                                                                                                     ApprovalWorkflowStage reviewStage,
                                                                                                     OffsetDateTime from,
                                                                                                     OffsetDateTime to);
}
