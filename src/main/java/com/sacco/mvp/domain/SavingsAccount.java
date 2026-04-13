package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounts_savings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsAccount {
    @Id
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "available_balance", nullable = false, precision = 18, scale = 2)
    private BigDecimal availableBalance;

    @Column(name = "shares_balance", nullable = false, precision = 18, scale = 2)
    private BigDecimal sharesBalance;

    @Column(name = "deposits_balance", nullable = false, precision = 18, scale = 2)
    private BigDecimal depositsBalance;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "summary_last_synced_at")
    private OffsetDateTime summaryLastSyncedAt;
}
