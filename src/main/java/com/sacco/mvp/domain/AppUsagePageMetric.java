package com.sacco.mvp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
    name = "app_usage_page_metrics",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_app_usage_page_metrics_bucket_scope_page_device",
            columnNames = {"bucket_start", "sacco_id", "station_id", "page_path", "device_type", "browser_family"}
        )
    },
    indexes = {
        @Index(name = "idx_app_usage_page_metrics_bucket", columnList = "bucket_start"),
        @Index(name = "idx_app_usage_page_metrics_scope_bucket", columnList = "sacco_id, station_id, bucket_start")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUsagePageMetric {
    @Id
    private UUID id;

    @Column(name = "bucket_start", nullable = false)
    private OffsetDateTime bucketStart;

    @Column(name = "sacco_id", nullable = false, length = 80)
    private String saccoId;

    @Column(name = "station_id", nullable = false, length = 80)
    private String stationId;

    @Column(name = "page_path", nullable = false, length = 240)
    private String pagePath;

    @Column(name = "device_type", nullable = false, length = 40)
    private String deviceType;

    @Column(name = "browser_family", nullable = false, length = 40)
    private String browserFamily;

    @Column(name = "hit_count", nullable = false)
    private long hitCount;

    @Column(name = "last_recorded_at", nullable = false)
    private OffsetDateTime lastRecordedAt;
}
