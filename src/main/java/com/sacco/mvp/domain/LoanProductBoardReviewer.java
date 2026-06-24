package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "loan_product_board_reviewers",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"loan_product_setting_id", "review_stage", "board_member_id"})
    },
    indexes = {
        @Index(name = "idx_loan_product_board_reviewers_product", columnList = "loan_product_setting_id"),
        @Index(name = "idx_loan_product_board_reviewers_product_stage", columnList = "loan_product_setting_id, review_stage"),
        @Index(name = "idx_loan_product_board_reviewers_member", columnList = "board_member_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanProductBoardReviewer {
    @Id
    private UUID id;

    @Column(name = "loan_product_setting_id", nullable = false)
    private UUID loanProductSettingId;

    @Column(name = "sacco_id", nullable = false)
    private String saccoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_stage", nullable = false, length = 32)
    private ApprovalWorkflowStage reviewStage;

    @Column(name = "board_member_id", nullable = false)
    private UUID boardMemberId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
