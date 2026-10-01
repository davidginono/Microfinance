package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "loan_ledger_installments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanLedgerInstallment {
    @Id private UUID id;
    @Column(name = "loan_application_id", nullable = false) private UUID loanApplicationId;
    @Column(name = "installment_number", nullable = false) private int installmentNumber;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal principal;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal interest;
    @Column(name = "principal_paid", nullable = false, precision = 18, scale = 2) private BigDecimal principalPaid;
    @Column(name = "interest_paid", nullable = false, precision = 18, scale = 2) private BigDecimal interestPaid;
}
