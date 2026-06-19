package com.sacco.mvp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
    name = "app_usage_events",
    indexes = {
        @Index(name = "idx_app_usage_events_occurred_at", columnList = "occurred_at"),
        @Index(name = "idx_app_usage_events_scope_time", columnList = "sacco_id, station_id, occurred_at"),
        @Index(name = "idx_app_usage_events_type_time", columnList = "event_type, occurred_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUsageEvent {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private AppUsageEventType eventType;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "username", length = 120)
    private String username;

    @Column(name = "display_name", length = 180)
    private String displayName;

    @Column(name = "roles", length = 240)
    private String roles;

    @Column(name = "sacco_id", length = 80)
    private String saccoId;

    @Column(name = "station_id", length = 80)
    private String stationId;

    @Column(name = "page_path", length = 240)
    private String pagePath;

    @Column(name = "device_type", length = 40)
    private String deviceType;

    @Column(name = "browser_family", length = 40)
    private String browserFamily;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;
}
