package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "guarantor_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuarantorRequest {
    @Id
    private UUID id;

    @Column(name = "loan_application_id", nullable = false)
    private UUID loanApplicationId;

    @Column(name = "guarantor_member_id")
    private UUID guarantorMemberId;

    @Column(name = "external_guarantor_registry_id")
    private UUID externalGuarantorRegistryId;

    @Builder.Default
    @Column(name = "guarantor_source", nullable = false)
    private String guarantorSource = "LMS";

    @Column(name = "external_member_no")
    private String externalMemberNo;

    @Column(name = "external_station_id")
    private String externalStationId;

    @Column(name = "external_full_name")
    private String externalFullName;

    @Column(name = "external_email")
    private String externalEmail;

    @Column(name = "external_phone")
    private String externalPhone;

    @Column(name = "external_financial_snapshot", columnDefinition = "jsonb")
    private String externalFinancialSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GuarantorRequestStatus status;

    @Column(name = "requested_amount", precision = 18, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "committed_amount", precision = 18, scale = 2)
    private BigDecimal committedAmount;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "guarantor_signature_text")
    private String guarantorSignatureText;

    @Column(name = "guarantor_signature_verified_at")
    private OffsetDateTime guarantorSignatureVerifiedAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Version
    @Column(nullable = false)
    private Integer version;
}
