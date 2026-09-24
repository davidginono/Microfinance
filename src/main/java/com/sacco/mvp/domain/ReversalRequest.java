package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reversal_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReversalRequest {
    @Id
    private UUID id;

    @Column(name = "sacco_id", nullable = false)
    private String saccoId;

    @Column(name = "loan_application_id", nullable = false)
    private UUID loanApplicationId;

    @Column(name = "guarantor_request_id")
    private UUID guarantorRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReversalRequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReversalRequestStatus status;

    @Column(name = "requester_member_id", nullable = false)
    private UUID requesterMemberId;

    @Column(name = "approver_member_id")
    private UUID approverMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_role")
    private Position approverRole;

    @Column(name = "request_reason")
    private String requestReason;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "decided_by_member_id")
    private UUID decidedByMemberId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Version
    @Column(nullable = false)
    private Integer version;
}
