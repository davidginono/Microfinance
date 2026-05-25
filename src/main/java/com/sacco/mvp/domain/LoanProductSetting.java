package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_product_settings", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"sacco_id", "loan_type"}),
    @UniqueConstraint(columnNames = {"sacco_id", "product_code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanProductSetting {
    @Id
    private UUID id;

    @Column(name = "sacco_id", nullable = false)
    private String saccoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false)
    private LoanType loanType;

    @Column(name = "product_code", length = 64)
    private String productCode;

    @Column(name = "product_name", length = 120)
    private String productName;

    @Column(name = "product_description", length = 500)
    private String productDescription;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "minimum_amount", precision = 18, scale = 2)
    private BigDecimal minimumAmount;

    @Column(name = "maximum_amount", precision = 18, scale = 2)
    private BigDecimal maximumAmount;

    @Column(name = "guarantors_required", nullable = false)
    private Integer guarantorsRequired;

    @Column(name = "max_loan_savings_ratio", precision = 6, scale = 4)
    private BigDecimal maxLoanSavingsRatio;

    @Column(name = "insurance_rate", precision = 6, scale = 4)
    private BigDecimal insuranceRate;

    @Column(name = "interest_rate", precision = 6, scale = 4)
    private BigDecimal interestRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_method", length = 32)
    private InterestMethod interestMethod;

    @Column(name = "min_repayment_months")
    private Integer minRepaymentMonths;

    @Column(name = "max_repayment_months")
    private Integer maxRepaymentMonths;

    @Column(name = "allow_application_with_active_loan")
    private Boolean allowApplicationWithActiveLoan;

    @Column(name = "fresh_financial_data_required")
    private Boolean freshFinancialDataRequired;

    @Column(name = "manager_review_required")
    private Boolean managerReviewRequired;

    @Column(name = "loan_officer_review_required")
    private Boolean loanOfficerReviewRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_start_stage", length = 32)
    private ApprovalWorkflowStage workflowStartStage;

    @Column(name = "manager_priority")
    private Integer managerPriority;

    @Column(name = "loan_officer_priority")
    private Integer loanOfficerPriority;

    @Column(name = "committee_review_required")
    private Boolean committeeReviewRequired;

    @Column(name = "committee_priority")
    private Integer committeePriority;

    @Column(name = "committee_minimum_votes")
    private Integer committeeMinimumVotes;

    @Column(name = "committee_approval_threshold")
    private Integer committeeApprovalThreshold;

    @Column(name = "accountant_review_required")
    private Boolean accountantReviewRequired;

    @Column(name = "accountant_priority")
    private Integer accountantPriority;

    @Column(name = "disbursement_officer_required")
    private Boolean disbursementOfficerRequired;

    @Column(name = "guarantor_commitment_required")
    private Boolean guarantorCommitmentRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "guarantor_commitment_stage", length = 32)
    private ApprovalWorkflowStage guarantorCommitmentStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_status", length = 32)
    private LoanProductStatus productStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_schema", nullable = false, columnDefinition = "jsonb")
    private String formSchema;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public String getDisplayName() {
        if (productName != null && !productName.isBlank()) {
            return productName;
        }
        return loanType == null ? "" : loanType.getDisplayLabel();
    }

    public String getDisplayCode() {
        if (productCode != null && !productCode.isBlank()) {
            return productCode;
        }
        return loanType == null ? "" : loanType.defaultProductCode();
    }

    public String getDisplayDescription() {
        if (productDescription != null && !productDescription.isBlank()) {
            return productDescription;
        }
        return loanType == null ? "" : loanType.defaultDescription();
    }

    public int getResolvedDisplayOrder() {
        if (displayOrder != null && displayOrder > 0) {
            return displayOrder;
        }
        return loanType == null ? Integer.MAX_VALUE : loanType.getDisplayOrder();
    }

    public Integer getMinimumRepaymentMonths() {
        return minRepaymentMonths == null || minRepaymentMonths <= 0 ? 1 : minRepaymentMonths;
    }

    public boolean isApplicationWithActiveLoanAllowed() {
        return Boolean.TRUE.equals(allowApplicationWithActiveLoan);
    }

    public boolean isFreshFinancialDataRequired() {
        return Boolean.TRUE.equals(freshFinancialDataRequired);
    }

    public boolean isManagerReviewRequired() {
        return managerReviewRequired == null || managerReviewRequired;
    }

    public boolean isCommitteeReviewRequired() {
        return committeeReviewRequired == null || committeeReviewRequired;
    }

    public ApprovalWorkflowStage getResolvedWorkflowStartStage() {
        return workflowStartStage == ApprovalWorkflowStage.LOAN_OFFICER
            ? ApprovalWorkflowStage.LOAN_OFFICER
            : ApprovalWorkflowStage.MANAGER;
    }

    public int getResolvedManagerPriority() {
        if (managerPriority != null && managerPriority > 0) {
            return managerPriority;
        }
        return getResolvedWorkflowStartStage() == ApprovalWorkflowStage.LOAN_OFFICER ? 2 : 1;
    }

    public int getResolvedLoanOfficerPriority() {
        if (loanOfficerPriority != null && loanOfficerPriority > 0) {
            return loanOfficerPriority;
        }
        return getResolvedWorkflowStartStage() == ApprovalWorkflowStage.LOAN_OFFICER ? 1 : 2;
    }

    public int getResolvedCommitteePriority() {
        return committeePriority != null && committeePriority >= 1 && committeePriority <= 4
            ? committeePriority
            : 3;
    }

    public int getResolvedCommitteeMinimumVotes() {
        return committeeMinimumVotes == null || committeeMinimumVotes <= 0 ? 1 : committeeMinimumVotes;
    }

    public int getResolvedCommitteeApprovalThreshold() {
        return committeeApprovalThreshold == null || committeeApprovalThreshold <= 0
            ? getResolvedCommitteeMinimumVotes()
            : committeeApprovalThreshold;
    }

    public boolean isAccountantReviewRequired() {
        return accountantReviewRequired == null || accountantReviewRequired;
    }

    public int getResolvedAccountantPriority() {
        return accountantPriority != null && accountantPriority >= 1 && accountantPriority <= 4
            ? accountantPriority
            : 4;
    }

    public boolean isDisbursementOfficerRequired() {
        return disbursementOfficerRequired == null || disbursementOfficerRequired;
    }

    public boolean isGuarantorCommitmentRequired() {
        return Boolean.TRUE.equals(guarantorCommitmentRequired);
    }

    public ApprovalWorkflowStage getResolvedGuarantorCommitmentStage() {
        return guarantorCommitmentStage == null ? ApprovalWorkflowStage.MANAGER : guarantorCommitmentStage;
    }

    public LoanProductStatus getStatus() {
        if (productStatus != null) {
            return productStatus;
        }
        return Boolean.TRUE.equals(active) ? LoanProductStatus.ACTIVE : LoanProductStatus.SUSPENDED;
    }

    public boolean isAvailableForApplications() {
        return getStatus() == LoanProductStatus.ACTIVE;
    }
}

