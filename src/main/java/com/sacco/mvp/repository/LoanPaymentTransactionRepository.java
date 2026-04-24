package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanPaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LoanPaymentTransactionRepository extends JpaRepository<LoanPaymentTransaction, UUID> {

    List<LoanPaymentTransaction> findByLoanApplicationIdOrderByReceiptDateAsc(UUID loanApplicationId);

    boolean existsByLoanApplicationIdAndReceiptDateAndPrincipalPaidAndInterestPaidAndTotalPaid(
        UUID loanApplicationId,
        LocalDate receiptDate,
        BigDecimal principalPaid,
        BigDecimal interestPaid,
        BigDecimal totalPaid);
}
