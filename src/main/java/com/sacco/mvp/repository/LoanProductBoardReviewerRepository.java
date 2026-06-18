package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanProductBoardReviewer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LoanProductBoardReviewerRepository extends JpaRepository<LoanProductBoardReviewer, UUID> {
    List<LoanProductBoardReviewer> findByLoanProductSettingIdOrderByCreatedAtAsc(UUID loanProductSettingId);

    List<LoanProductBoardReviewer> findByLoanProductSettingIdInOrderByCreatedAtAsc(Collection<UUID> loanProductSettingIds);

    void deleteByLoanProductSettingId(UUID loanProductSettingId);
}
