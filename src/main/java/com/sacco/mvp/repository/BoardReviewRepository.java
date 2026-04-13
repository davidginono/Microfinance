package com.sacco.mvp.repository;

import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardReviewRepository extends JpaRepository<BoardReview, UUID> {
    List<BoardReview> findByLoanApplicationId(UUID loanApplicationId);

    void deleteByLoanApplicationId(UUID loanApplicationId);

    long countByLoanApplicationIdAndDecision(UUID loanApplicationId, BoardDecision decision);

    List<BoardReview> findByBoardMemberIdAndDecision(UUID boardMemberId, BoardDecision decision);

    List<BoardReview> findByBoardMemberIdOrderByCreatedAtDesc(UUID boardMemberId);

    Optional<BoardReview> findByLoanApplicationIdAndBoardMemberId(UUID loanApplicationId, UUID boardMemberId);
}
