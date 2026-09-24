package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanProductBoardReviewer;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LoanProductBoardReviewerRepository extends JpaRepository<LoanProductBoardReviewer, UUID> {
    List<LoanProductBoardReviewer> findByLoanProductSettingIdOrderByCreatedAtAsc(UUID loanProductSettingId);

    List<LoanProductBoardReviewer> findByLoanProductSettingIdAndReviewStageOrderByCreatedAtAsc(UUID loanProductSettingId,
                                                                                                ApprovalWorkflowStage reviewStage);

    List<LoanProductBoardReviewer> findByLoanProductSettingIdInOrderByCreatedAtAsc(Collection<UUID> loanProductSettingIds);

    List<LoanProductBoardReviewer> findByLoanProductSettingIdInAndReviewStageOrderByCreatedAtAsc(Collection<UUID> loanProductSettingIds,
                                                                                                  ApprovalWorkflowStage reviewStage);

    void deleteByLoanProductSettingId(UUID loanProductSettingId);

    void deleteByLoanProductSettingIdAndReviewStage(UUID loanProductSettingId, ApprovalWorkflowStage reviewStage);
}
