package com.sacco.mvp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "station_sms_accounts",
    uniqueConstraints = @UniqueConstraint(name = "uk_station_sms_account", columnNames = {"sacco_id", "station_id"}),
    indexes = {
        @Index(name = "idx_station_sms_account_status", columnList = "status"),
        @Index(name = "idx_station_sms_account_scope", columnList = "sacco_id, station_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StationSmsAccount {
    @Id
    private UUID id;

    @Column(name = "sacco_id", nullable = false)
    private String saccoId;

    @Column(name = "station_id", nullable = false)
    private String stationId;

    @Column(name = "available_units", nullable = false)
    private long availableUnits;

    @Column(name = "alert_reserved_units", nullable = false)
    private long alertReservedUnits;

    @Column(name = "warning_baseline", nullable = false)
    private long warningBaseline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SmsUnitStatus status;

    @Column(name = "low_alert_sent", nullable = false)
    private boolean lowAlertSent;

    @Column(name = "critical_alert_sent", nullable = false)
    private boolean criticalAlertSent;

    @Column(name = "depleted_alert_sent", nullable = false)
    private boolean depletedAlertSent;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;
}
