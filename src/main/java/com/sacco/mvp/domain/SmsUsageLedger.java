package com.sacco.mvp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "sms_usage_ledger",
    indexes = {
        @Index(name = "idx_sms_usage_ledger_scope_created", columnList = "sacco_id, station_id, created_at"),
        @Index(name = "idx_sms_usage_ledger_account_created", columnList = "account_id, created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsUsageLedger {
    @Id
    private UUID id;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "sacco_id")
    private String saccoId;

    @Column(name = "station_id")
    private String stationId;

    @Column(name = "notification_id")
    private UUID notificationId;

    @Column(name = "loan_application_id")
    private UUID loanApplicationId;

    @Column(name = "applicant_member_id")
    private UUID applicantMemberId;

    @Column(name = "recipient_member_id")
    private UUID recipientMemberId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "unit_change", nullable = false)
    private long unitChange;

    @Builder.Default
    @Column(name = "event_count", nullable = false)
    private long eventCount = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SmsUsageOutcome outcome;

    @Column(name = "provider_reference", length = 500)
    private String providerReference;

    @Column(name = "actor_member_id")
    private UUID actorMemberId;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_occurred_at", nullable = false)
    private OffsetDateTime lastOccurredAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void applyDefaults() {
        if (eventCount < 1) {
            eventCount = 1;
        }
        if (lastOccurredAt == null) {
            lastOccurredAt = createdAt == null ? OffsetDateTime.now() : createdAt;
        }
    }
}
