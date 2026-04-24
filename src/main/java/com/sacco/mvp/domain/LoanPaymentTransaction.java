package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanPaymentTransaction {
    @Id
    private UUID id;

    @Column(name = "loan_application_id", nullable = false)
    private UUID loanApplicationId;

    @Column(name = "sacco_id", nullable = false, length = 64)
    private String saccoId;

    @Column(name = "external_loan_id", nullable = false, length = 20)
    private String externalLoanId;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(name = "principal_paid", nullable = false, precision = 18, scale = 2)
    private BigDecimal principalPaid;

    @Column(name = "interest_paid", nullable = false, precision = 18, scale = 2)
    private BigDecimal interestPaid;

    @Column(name = "total_paid", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalPaid;

    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt;
}
