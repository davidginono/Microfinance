package com.sacco.mvp.repository;

import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.ManagerReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

public interface ManagerReviewRepository extends JpaRepository<ManagerReview, UUID> {
    List<ManagerReview> findByLoanApplicationIdOrderByCreatedAtAsc(UUID loanApplicationId);

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
