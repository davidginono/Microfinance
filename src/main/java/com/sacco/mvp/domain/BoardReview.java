package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "board_reviews", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"loan_application_id", "board_member_id", "review_stage"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardReview {
    @Id
    private UUID id;

    @Column(name = "loan_application_id", nullable = false)
    private UUID loanApplicationId;

    @Column(name = "board_member_id", nullable = false)
    private UUID boardMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_stage", nullable = false)
    private ApprovalWorkflowStage reviewStage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoardDecision decision;

    private String comment;

    @Column(name = "board_signature_text")
    private String boardSignatureText;

    @Column(name = "board_signature_verified_at")
    private OffsetDateTime boardSignatureVerifiedAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
