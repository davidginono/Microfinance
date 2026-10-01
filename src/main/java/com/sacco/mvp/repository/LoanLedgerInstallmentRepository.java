package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanLedgerInstallment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LoanLedgerInstallmentRepository extends JpaRepository<LoanLedgerInstallment, UUID> {
    List<LoanLedgerInstallment> findByLoanApplicationIdOrderByInstallmentNumber(UUID id, Pageable pageable);
}
