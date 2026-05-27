package com.sacco.mvp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "sacco_station_policies",
    uniqueConstraints = @UniqueConstraint(name = "uk_sacco_station_policy", columnNames = {"sacco_id", "station_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaccoStationPolicy {
    @Id
    private UUID id;

    @Column(name = "sacco_id", nullable = false)
    private String saccoId;

    @Column(name = "station_id", nullable = false)
    private String stationId;

    @Column(name = "applicant_max_defaulted_loans")
    private Integer applicantMaxDefaultedLoans;

    @Column(name = "applicant_max_forfeited_loans")
    private Integer applicantMaxForfeitedLoans;

    @Column(name = "applicant_forfeited_lookback_days")
    private Integer applicantForfeitedLookbackDays;

    @Column(name = "applicant_forfeited_wait_days")
    private Integer applicantForfeitedWaitDays;

    @Column(name = "guarantor_with_active_loan_allowed")
    private Boolean guarantorWithActiveLoanAllowed;

    @Column(name = "guarantor_max_guaranteed_loan_amount", precision = 18, scale = 2)
    private BigDecimal guarantorMaxGuaranteedLoanAmount;

    @Column(name = "guarantor_max_defaulted_loans")
    private Integer guarantorMaxDefaultedLoans;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
