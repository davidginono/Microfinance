package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "guarantor_requests", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"loan_application_id", "guarantor_member_id"})
})
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

    @Column(name = "guarantor_member_id", nullable = false)
    private UUID guarantorMemberId;

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
