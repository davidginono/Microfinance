package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanApplication {
    @Id
    private UUID id;

    @Column(name = "sacco_id", nullable = false)
    private String saccoId;

    @Column(name = "applicant_member_id", nullable = false)
    private UUID applicantMemberId;

    @Column(name = "top_up_source_loan_id")
    private UUID topUpSourceLoanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false)
    private LoanType loanType;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "tenor_months", nullable = false)
    private Integer tenorMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_data", nullable = false, columnDefinition = "jsonb")
    private String formData;

    @Column(name = "required_guarantors", nullable = false)
    private Integer requiredGuarantors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_snapshot", nullable = false, columnDefinition = "jsonb")
    private String policySnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_guarantors", columnDefinition = "jsonb")
    private String selectedGuarantors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "financial_snapshot", columnDefinition = "jsonb")
    private String financialSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachments_json", columnDefinition = "jsonb")
    private String attachmentsJson;

    @Column(name = "applicant_signature_text")
    private String applicantSignatureText;

    @Column(name = "applicant_signature_verified_at")
    private OffsetDateTime applicantSignatureVerifiedAt;

    @Column(name = "disbursement_date")
    private LocalDate disbursementDate;

    @Column(name = "first_repayment_date")
    private LocalDate firstRepaymentDate;

    @Column(name = "final_due_date")
    private LocalDate finalDueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency")
    private RepaymentFrequency repaymentFrequency;

    @Column(name = "installment_amount", precision = 18, scale = 2)
    private BigDecimal installmentAmount;

    @Column(name = "disbursement_reference")
    private String disbursementReference;

    @Column(name = "disbursement_notes", length = 4000)
    private String disbursementNotes;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "paid_marked_by_manager_id")
    private UUID paidMarkedByManagerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "repayment_schedule_json", columnDefinition = "jsonb")
    private String repaymentScheduleJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Integer version;
}

