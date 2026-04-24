package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {
    List<LoanApplication> findByApplicantMemberIdOrderByCreatedAtDesc(UUID applicantMemberId);

    List<LoanApplication> findBySaccoIdOrderByCreatedAtDesc(String saccoId);
    List<LoanApplication> findBySaccoIdAndStatusOrderByCreatedAtAsc(String saccoId, LoanStatus status);
    List<LoanApplication> findBySaccoIdAndStatusInOrderByCreatedAtAsc(String saccoId, List<LoanStatus> statuses);
    List<LoanApplication> findByStatusAndFinalDueDateIsNotNull(LoanStatus status);
    List<LoanApplication> findByApplicantMemberIdAndStatusOrderByCreatedAtDesc(UUID applicantMemberId, LoanStatus status);
    List<LoanApplication> findByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(UUID applicantMemberId, List<LoanStatus> statuses);
    List<LoanApplication> findByTopUpSourceLoanIdIn(Collection<UUID> topUpSourceLoanIds);

    Optional<LoanApplication> findByIdAndApplicantMemberId(UUID id, UUID applicantMemberId);

    boolean existsBySaccoIdAndLoanId(String saccoId, String loanId);

    Optional<LoanApplication> findBySaccoIdAndApplicationNumber(String saccoId, Long applicationNumber);

    List<LoanApplication> findByStatusInAndLoanIdIsNotNull(Collection<LoanStatus> statuses);
}

