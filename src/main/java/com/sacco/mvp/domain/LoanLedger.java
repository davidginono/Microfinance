package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_ledgers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanLedger {
    @Id @Column(name = "loan_application_id") private UUID loanApplicationId;
    @Column(name = "sacco_id", nullable = false) private String saccoId;
    @Column(name = "station_id", nullable = false) private String stationId;
    @Column(name = "loan_id", nullable = false, length = 20) private String loanId;
    @Column(name = "applicant_member_id", nullable = false) private UUID applicantMemberId;
    @Column(name = "disbursement_date", nullable = false) private LocalDate disbursementDate;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal principal;
    @Column(name = "principal_paid", nullable = false, precision = 18, scale = 2) private BigDecimal principalPaid;
    @Column(name = "interest_paid", nullable = false, precision = 18, scale = 2) private BigDecimal interestPaid;
    @Column(name = "last_payment_date") private LocalDate lastPaymentDate;
    @Column(name = "next_sequence", nullable = false) private long nextSequence;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Version private Integer version;
}
