package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "manager_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagerReview {
    @Id
    private UUID id;

    @Column(name = "loan_application_id", nullable = false)
    private UUID loanApplicationId;

    @Column(name = "manager_member_id", nullable = false)
    private UUID managerMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_stage", nullable = false)
    private ApprovalWorkflowStage reviewStage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ManagerDecision decision;

    private String reasons;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
