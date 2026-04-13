package com.sacco.mvp.repository;

import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.GuarantorRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuarantorRequestRepository extends JpaRepository<GuarantorRequest, UUID> {
    List<GuarantorRequest> findByLoanApplicationId(UUID loanApplicationId);

    void deleteByLoanApplicationId(UUID loanApplicationId);

    long countByLoanApplicationIdAndStatus(UUID loanApplicationId, GuarantorRequestStatus status);

    List<GuarantorRequest> findByGuarantorMemberIdAndStatusOrderByCreatedAtDesc(UUID guarantorMemberId,
                                                                                GuarantorRequestStatus status);

    List<GuarantorRequest> findByGuarantorMemberIdOrderByCreatedAtDesc(UUID guarantorMemberId);

    Optional<GuarantorRequest> findByIdAndGuarantorMemberId(UUID id, UUID guarantorMemberId);

    Optional<GuarantorRequest> findByLoanApplicationIdAndGuarantorMemberId(UUID loanApplicationId, UUID guarantorMemberId);
}
