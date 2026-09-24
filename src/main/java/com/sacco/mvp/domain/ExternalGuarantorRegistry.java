package com.sacco.mvp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "external_guarantor_registry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalGuarantorRegistry {
    @Id
    private UUID id;

    @Column(name = "sacco_id", nullable = false)
    private String saccoId;

    @Column(name = "station_id")
    private String stationId;

    @Column(name = "external_station_id", nullable = false)
    private String externalStationId;

    @Column(name = "external_member_no", nullable = false)
    private String externalMemberNo;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "latest_financial_snapshot", columnDefinition = "jsonb")
    private String latestFinancialSnapshot;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "last_approved_at")
    private OffsetDateTime lastApprovedAt;

    @Version
    @Column(nullable = false)
    private Integer version;
}
