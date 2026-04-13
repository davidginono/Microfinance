package com.sacco.mvp.repository;

import com.sacco.mvp.domain.ManagerReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManagerReviewRepository extends JpaRepository<ManagerReview, UUID> {
    List<ManagerReview> findByLoanApplicationIdOrderByCreatedAtAsc(UUID loanApplicationId);

    void deleteByLoanApplicationId(UUID loanApplicationId);

    List<ManagerReview> findByLoanApplicationIdInOrderByCreatedAtDesc(Collection<UUID> loanApplicationIds);

    Optional<ManagerReview> findFirstByLoanApplicationIdOrderByCreatedAtDesc(UUID loanApplicationId);

    List<ManagerReview> findAllByOrderByCreatedAtDesc();
}
