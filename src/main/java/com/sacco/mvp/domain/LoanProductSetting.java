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
    @UniqueConstraint(columnNames = {"sacco_id", "loan_type"})
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

    @Column(name = "guarantors_required", nullable = false)
    private Integer guarantorsRequired;

    @Column(name = "max_loan_savings_ratio", precision = 6, scale = 4)
    private BigDecimal maxLoanSavingsRatio;

    @Column(name = "insurance_rate", precision = 6, scale = 4)
    private BigDecimal insuranceRate;

    @Column(name = "interest_rate", precision = 6, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "max_repayment_months")
    private Integer maxRepaymentMonths;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_schema", nullable = false, columnDefinition = "jsonb")
    private String formSchema;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

