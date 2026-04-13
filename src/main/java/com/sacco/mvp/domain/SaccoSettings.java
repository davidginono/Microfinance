package com.sacco.mvp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "sacco_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaccoSettings {
    @Id
    @Column(name = "sacco_id")
    private String saccoId;

    @Column(name = "external_station_id", nullable = false)
    private String externalStationId;

    @Column(name = "external_sacco_name")
    private String externalSaccoName;

    @Column(name = "required_guarantors", nullable = false)
    private Integer requiredGuarantors;

    @Column(name = "board_size", nullable = false)
    private Integer boardSize;

    @Column(name = "board_quorum", nullable = false)
    private Integer boardQuorum;

    @Column(name = "max_loan_savings_ratio", nullable = false, precision = 6, scale = 4)
    private BigDecimal maxLoanSavingsRatio;

    @Column(name = "default_language", nullable = false)
    private String defaultLanguage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

