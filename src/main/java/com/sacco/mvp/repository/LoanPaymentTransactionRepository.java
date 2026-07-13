package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanPaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LoanPaymentTransactionRepository extends JpaRepository<LoanPaymentTransaction, UUID> {

    List<LoanPaymentTransaction> findByLoanApplicationIdOrderByReceiptDateAscProviderOrderDesc(UUID loanApplicationId);

    List<LoanPaymentTransaction> findByLoanApplicationIdInAndReceiptDateBetweenOrderByReceiptDateAsc(
        Collection<UUID> loanApplicationIds,
        LocalDate fromDate,
        LocalDate toDate);

}
