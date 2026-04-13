package com.sacco.mvp.repository;

import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.ReversalRequest;
import com.sacco.mvp.domain.ReversalRequestStatus;
import com.sacco.mvp.domain.ReversalRequestType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReversalRequestRepository extends JpaRepository<ReversalRequest, UUID> {
    Optional<ReversalRequest> findTopByGuarantorRequestIdAndTypeAndStatusOrderByCreatedAtDesc(
        UUID guarantorRequestId, ReversalRequestType type, ReversalRequestStatus status);

    Optional<ReversalRequest> findTopByLoanApplicationIdAndTypeAndStatusOrderByCreatedAtDesc(
        UUID loanApplicationId, ReversalRequestType type, ReversalRequestStatus status);

    List<ReversalRequest> findByLoanApplicationIdAndTypeAndStatusOrderByCreatedAtDesc(
        UUID loanApplicationId, ReversalRequestType type, ReversalRequestStatus status);

    List<ReversalRequest> findBySaccoIdAndApproverRoleAndStatusOrderByCreatedAtDesc(
        String saccoId, Position approverRole, ReversalRequestStatus status);
}
