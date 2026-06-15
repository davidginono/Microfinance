package com.sacco.mvp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "sacco_stations",
    uniqueConstraints = @UniqueConstraint(name = "uk_sacco_station", columnNames = {"sacco_id", "station_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaccoStation {
    @Id
    private UUID id;

    @Column(name = "sacco_id", nullable = false)
    private String saccoId;

    @Column(name = "station_id", nullable = false)
    private String stationId;

    @Column(name = "address_location")
    private String addressLocation;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "otp_delivery_channel", nullable = false)
    @Builder.Default
    private OtpDeliveryChannel otpDeliveryChannel = OtpDeliveryChannel.SMS_WITH_EMAIL_FALLBACK;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_status")
    @Builder.Default
    private SaccoAccessStatus accessStatus = SaccoAccessStatus.ACTIVE;

    @Column(name = "payment_due_date")
    private java.time.LocalDate paymentDueDate;

    @Column(name = "access_suspended_at")
    private OffsetDateTime accessSuspendedAt;

    @Column(name = "access_suspended_by_member_id")
    private UUID accessSuspendedByMemberId;

    @Column(name = "access_restriction_reason", length = 500)
    private String accessRestrictionReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public SaccoAccessStatus getResolvedAccessStatus() {
        return accessStatus == null ? SaccoAccessStatus.ACTIVE : accessStatus;
    }

    public boolean isAccessSuspended() {
        return getResolvedAccessStatus() == SaccoAccessStatus.SUSPENDED;
    }

    public OtpDeliveryChannel getResolvedOtpDeliveryChannel() {
        return otpDeliveryChannel == null ? OtpDeliveryChannel.SMS_WITH_EMAIL_FALLBACK : otpDeliveryChannel;
    }
}
