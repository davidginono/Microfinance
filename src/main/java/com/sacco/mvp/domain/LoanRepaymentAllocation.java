package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "loan_repayment_allocations")
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanRepaymentAllocation {
    @Id private UUID id;
    @Column(name = "transaction_id", nullable = false) private UUID transactionId;
    @Column(name = "installment_id", nullable = false) private UUID installmentId;
    @Column(name = "principal_amount", nullable = false, precision = 18, scale = 2) private BigDecimal principalAmount;
    @Column(name = "interest_amount", nullable = false, precision = 18, scale = 2) private BigDecimal interestAmount;
}
