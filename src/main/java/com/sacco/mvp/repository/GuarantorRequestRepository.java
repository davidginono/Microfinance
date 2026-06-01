package com.sacco.mvp.repository;

import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.GuarantorRequestStatus;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuarantorRequestRepository extends JpaRepository<GuarantorRequest, UUID> {
    List<GuarantorRequest> findByLoanApplicationId(UUID loanApplicationId);

    @Query("""
        select count(g)
        from GuarantorRequest g, LoanApplication l
        where g.loanApplicationId = l.id
          and g.status = com.sacco.mvp.domain.GuarantorRequestStatus.PENDING
          and l.saccoId = :saccoId
          and (:status is null or l.status = :status)
          and (:loanType is null or l.loanType = :loanType)
          and (:createdFrom is null or l.createdAt >= :createdFrom)
          and (:createdToExclusive is null or l.createdAt < :createdToExclusive)
        """)
    long countPendingForReport(@Param("saccoId") String saccoId,
                               @Param("status") LoanStatus status,
                               @Param("loanType") LoanType loanType,
                               @Param("createdFrom") OffsetDateTime createdFrom,
                               @Param("createdToExclusive") OffsetDateTime createdToExclusive);

    void deleteByLoanApplicationId(UUID loanApplicationId);

    long countByLoanApplicationIdAndStatus(UUID loanApplicationId, GuarantorRequestStatus status);

    List<GuarantorRequest> findByGuarantorMemberIdAndStatusOrderByCreatedAtDesc(UUID guarantorMemberId,
                                                                                GuarantorRequestStatus status);

    List<GuarantorRequest> findByGuarantorMemberIdOrderByCreatedAtDesc(UUID guarantorMemberId);

    Optional<GuarantorRequest> findByIdAndGuarantorMemberId(UUID id, UUID guarantorMemberId);

    Optional<GuarantorRequest> findByLoanApplicationIdAndGuarantorMemberId(UUID loanApplicationId, UUID guarantorMemberId);
}
