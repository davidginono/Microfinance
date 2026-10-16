package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanLedgerInstallment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LoanLedgerInstallmentRepository extends JpaRepository<LoanLedgerInstallment, UUID> {
    List<LoanLedgerInstallment> findByLoanApplicationIdOrderByInstallmentNumber(UUID id, Pageable pageable);
    Page<LoanLedgerInstallment> findAllByLoanApplicationIdOrderByInstallmentNumber(UUID id, Pageable pageable);
    @Query("select coalesce(sum(i.principal),0) from LoanLedgerInstallment i where i.loanApplicationId=:loanId and i.installmentNumber<:number")
    BigDecimal principalBefore(UUID loanId,int number);
}
