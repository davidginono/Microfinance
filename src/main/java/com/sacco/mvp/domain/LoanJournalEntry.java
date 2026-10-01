package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_journal_entries")
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanJournalEntry {
    @Id private UUID id;
    @Column(name = "loan_application_id", nullable = false) private UUID loanApplicationId;
    @Column(name = "transaction_id") private UUID transactionId;
    @Column(name = "voucher_id", nullable = false) private UUID voucherId;
    @Column(name = "account_code", nullable = false, length = 40) private String accountCode;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal debit;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal credit;
    @Column(name = "effective_date", nullable = false) private LocalDate effectiveDate;
    @Column(name = "posted_at", nullable = false) private OffsetDateTime postedAt;
}
