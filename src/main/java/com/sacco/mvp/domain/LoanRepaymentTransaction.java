package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_repayment_transactions")
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanRepaymentTransaction {
    public enum Kind { PAYMENT, REVERSAL }
    public enum Channel { CASH, BANK, MOBILE_MONEY }
    @Id private UUID id;
    @Column(name = "loan_application_id", nullable = false) private UUID loanApplicationId;
    @Column(name = "sacco_id", nullable = false) private String saccoId;
    @Column(name = "station_id", nullable = false) private String stationId;
    @Column(nullable = false) private long sequence;
    @Column(name = "receipt_reference", nullable = false, length = 64) private String receiptReference;
    @Column(name = "request_key", nullable = false) private UUID requestKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Kind kind;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private Channel channel;
    @Column(name = "channel_reference", nullable = false, length = 100) private String channelReference;
    @Column(name = "payment_date", nullable = false) private LocalDate paymentDate;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(name = "principal_amount", nullable = false, precision = 18, scale = 2) private BigDecimal principalAmount;
    @Column(name = "interest_amount", nullable = false, precision = 18, scale = 2) private BigDecimal interestAmount;
    @Column(name = "actor_member_id", nullable = false) private UUID actorMemberId;
    @Column(name = "reverses_transaction_id") private UUID reversesTransactionId;
    @Column(length = 500) private String reason;
    @Enumerated(EnumType.STRING) @Column(name = "loan_status_before", nullable = false, length = 40) private LoanStatus loanStatusBefore;
    @Column(name = "posted_at", nullable = false) private OffsetDateTime postedAt;
}
