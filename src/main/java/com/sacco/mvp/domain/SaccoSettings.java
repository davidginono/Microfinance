package com.sacco.mvp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "sacco_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaccoSettings {
    private static final BigDecimal DEFAULT_APPLICATION_FEE = new BigDecimal("15000.00");

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

    @Column(name = "loan_officer_review_required")
    private Boolean loanOfficerReviewRequired;

    @Column(name = "board_review_required")
    private Boolean boardReviewRequired;

    @Column(name = "max_loan_savings_ratio", nullable = false, precision = 6, scale = 4)
    private BigDecimal maxLoanSavingsRatio;

    @Column(name = "application_fee", precision = 18, scale = 2)
    private BigDecimal applicationFee;

    @Column(name = "default_language", nullable = false)
    private String defaultLanguage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public BigDecimal getResolvedApplicationFee() {
        return applicationFee == null ? DEFAULT_APPLICATION_FEE : applicationFee.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public boolean isLoanOfficerReviewRequired() {
        return Boolean.TRUE.equals(loanOfficerReviewRequired);
    }

    public boolean isBoardReviewRequired() {
        return boardReviewRequired == null || boardReviewRequired;
    }

    public List<ApprovalWorkflowStage> resolvedApprovalFlow() {
        java.util.ArrayList<ApprovalWorkflowStage> flow = new java.util.ArrayList<>();
        flow.add(ApprovalWorkflowStage.MANAGER);
        if (isLoanOfficerReviewRequired()) {
            flow.add(ApprovalWorkflowStage.LOAN_OFFICER);
        }
        if (isBoardReviewRequired()) {
            flow.add(ApprovalWorkflowStage.BOARD);
        }
        flow.add(ApprovalWorkflowStage.ACCOUNTANT);
        flow.add(ApprovalWorkflowStage.DISBURSEMENT_OFFICER);
        return java.util.List.copyOf(flow);
    }
}

