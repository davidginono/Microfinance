package com.sacco.mvp.service;

import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanProductStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.InterestMethod;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SaccoConfigurationService {
    private static final BigDecimal DEFAULT_MINIMUM_AMOUNT = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal DEFAULT_RATIO = new BigDecimal("0.3333");
    private static final BigDecimal DEFAULT_INSURANCE_RATE = new BigDecimal("0.0150");
    private static final BigDecimal DEFAULT_INTEREST_RATE = new BigDecimal("0.1000");
    private static final int DEFAULT_MIN_REPAYMENT_MONTHS = 1;

    private final LoanProductSettingRepository loanProductSettingRepository;

    @Transactional
    public void ensureDefaultLoanProducts(String saccoId) {
        if (saccoId == null || saccoId.isBlank() || loanProductSettingRepository.existsBySaccoId(saccoId)) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        seedDefaultProduct(saccoId, LoanType.LOAN_ADVANCE, 0, 3, now);
        seedDefaultProduct(saccoId, LoanType.EDUCATION_LOAN, 3, 12, now);
        seedDefaultProduct(saccoId, LoanType.EMERGENCY_LOAN, 3, 12, now);
        seedDefaultProduct(saccoId, LoanType.DEVELOPMENT_LOAN, 3, 12, now);
    }

    private void seedDefaultProduct(String saccoId,
                                    LoanType loanType,
                                    int guarantorsRequired,
                                    int maxRepaymentMonths,
                                    OffsetDateTime now) {
        loanProductSettingRepository.save(newLoanProduct(
            saccoId,
            loanType,
            loanType.defaultProductCode(),
            null,
            loanType.defaultDescription(),
            loanType.getDisplayOrder(),
            DEFAULT_MINIMUM_AMOUNT,
            null,
            guarantorsRequired,
            DEFAULT_RATIO,
            DEFAULT_INSURANCE_RATE,
            DEFAULT_INTEREST_RATE,
            InterestMethod.FLAT_RATE,
            DEFAULT_MIN_REPAYMENT_MONTHS,
            maxRepaymentMonths,
            false,
            false,
            true,
            null,
            ApprovalWorkflowStage.MANAGER,
            1,
            2,
            true,
            3,
            2,
            2,
            true,
            4,
            true,
            false,
            BigDecimal.ZERO,
            LoanProductStatus.ACTIVE,
            true,
            now
        ));
    }

    @Transactional
    public LoanProductSetting createLoanProduct(String saccoId,
                                                LoanType loanType,
                                                String productCode,
                                                String productName,
                                                String productDescription,
                                                Integer displayOrder,
                                                BigDecimal minimumAmount,
                                                BigDecimal maximumAmount,
                                                Integer guarantorsRequired,
                                                BigDecimal maxLoanSavingsRatio,
                                                BigDecimal insuranceRate,
                                                BigDecimal interestRate,
                                                InterestMethod interestMethod,
                                                Integer minRepaymentMonths,
                                                Integer maxRepaymentMonths,
                                                boolean allowApplicationWithActiveLoan,
                                                boolean freshFinancialDataRequired,
                                                boolean managerReviewRequired,
                                                Boolean loanOfficerReviewRequired,
                                                ApprovalWorkflowStage workflowStartStage,
                                                Integer managerPriority,
                                                Integer loanOfficerPriority,
                                                boolean committeeReviewRequired,
                                                Integer committeePriority,
                                                Integer committeeMinimumVotes,
                                                Integer committeeApprovalThreshold,
                                                boolean accountantReviewRequired,
                                              Integer accountantPriority,
                                              boolean disbursementOfficerRequired,
                                              boolean guarantorMinSavingsCheckRequired,
                                              BigDecimal guarantorMinimumSavings,
                                              LoanProductStatus productStatus,
                                              boolean active) {
        if (loanProductSettingRepository.existsBySaccoIdAndLoanType(saccoId, loanType)) {
            throw new IllegalStateException("That loan product already exists for this SACCO.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        return loanProductSettingRepository.save(newLoanProduct(
            saccoId,
            loanType,
            productCode,
            productName,
            productDescription,
            displayOrder,
            minimumAmount,
            maximumAmount,
            guarantorsRequired,
            maxLoanSavingsRatio == null ? DEFAULT_RATIO : maxLoanSavingsRatio,
            insuranceRate == null ? DEFAULT_INSURANCE_RATE : insuranceRate,
            interestRate == null ? DEFAULT_INTEREST_RATE : interestRate,
            interestMethod == null ? InterestMethod.FLAT_RATE : interestMethod,
            minRepaymentMonths == null ? DEFAULT_MIN_REPAYMENT_MONTHS : minRepaymentMonths,
            maxRepaymentMonths,
            allowApplicationWithActiveLoan,
            freshFinancialDataRequired,
            managerReviewRequired,
            loanOfficerReviewRequired,
            workflowStartStage,
            managerPriority,
            loanOfficerPriority,
            committeeReviewRequired,
            committeePriority,
            committeeMinimumVotes,
            committeeApprovalThreshold,
            accountantReviewRequired,
            accountantPriority,
            disbursementOfficerRequired,
            guarantorMinSavingsCheckRequired,
            guarantorMinimumSavings,
            productStatus == null ? (active ? LoanProductStatus.ACTIVE : LoanProductStatus.SUSPENDED) : productStatus,
            active,
            now
        ));
    }

    private LoanProductSetting newLoanProduct(String saccoId,
                                              LoanType loanType,
                                              String productCode,
                                              String productName,
                                              String productDescription,
                                              Integer displayOrder,
                                              BigDecimal minimumAmount,
                                              BigDecimal maximumAmount,
                                              Integer guarantorsRequired,
                                              BigDecimal maxLoanSavingsRatio,
                                              BigDecimal insuranceRate,
                                              BigDecimal interestRate,
                                              InterestMethod interestMethod,
                                              Integer minRepaymentMonths,
                                              Integer maxRepaymentMonths,
                                              boolean allowApplicationWithActiveLoan,
                                              boolean freshFinancialDataRequired,
                                              boolean managerReviewRequired,
                                              Boolean loanOfficerReviewRequired,
                                              ApprovalWorkflowStage workflowStartStage,
                                              Integer managerPriority,
                                              Integer loanOfficerPriority,
                                              boolean committeeReviewRequired,
                                              Integer committeePriority,
                                              Integer committeeMinimumVotes,
                                              Integer committeeApprovalThreshold,
                                              boolean accountantReviewRequired,
                                              Integer accountantPriority,
                                              boolean disbursementOfficerRequired,
                                              boolean guarantorMinSavingsCheckRequired,
                                              BigDecimal guarantorMinimumSavings,
                                              LoanProductStatus productStatus,
                                              boolean active,
                                              OffsetDateTime now) {
        return LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(loanType)
            .productCode(productCode)
            .productName(productName)
            .productDescription(productDescription)
            .displayOrder(displayOrder)
            .minimumAmount(minimumAmount == null ? DEFAULT_MINIMUM_AMOUNT : minimumAmount)
            .maximumAmount(maximumAmount)
            .guarantorsRequired(guarantorsRequired)
            .maxLoanSavingsRatio(maxLoanSavingsRatio)
            .insuranceRate(insuranceRate)
            .interestRate(interestRate)
            .interestMethod(interestMethod == null ? InterestMethod.FLAT_RATE : interestMethod)
            .minRepaymentMonths(minRepaymentMonths == null ? DEFAULT_MIN_REPAYMENT_MONTHS : minRepaymentMonths)
            .maxRepaymentMonths(maxRepaymentMonths)
            .allowApplicationWithActiveLoan(allowApplicationWithActiveLoan)
            .freshFinancialDataRequired(freshFinancialDataRequired)
            .managerReviewRequired(managerReviewRequired)
            .managerPriority(managerPriority == null ? 1 : managerPriority)
            .loanOfficerReviewRequired(loanOfficerReviewRequired)
            .loanOfficerPriority(loanOfficerPriority == null ? 2 : loanOfficerPriority)
            .workflowStartStage(workflowStartStage == null ? ApprovalWorkflowStage.MANAGER : workflowStartStage)
            .committeeReviewRequired(committeeReviewRequired)
            .committeePriority(committeePriority)
            .committeeMinimumVotes(committeeMinimumVotes)
            .committeeApprovalThreshold(committeeApprovalThreshold)
            .accountantReviewRequired(accountantReviewRequired)
            .accountantPriority(accountantPriority)
            .disbursementOfficerRequired(disbursementOfficerRequired)
            .guarantorMinSavingsCheckRequired(guarantorMinSavingsCheckRequired)
            .guarantorMinimumSavings(guarantorMinimumSavings == null ? BigDecimal.ZERO : guarantorMinimumSavings)
            .productStatus(productStatus == null ? (active ? LoanProductStatus.ACTIVE : LoanProductStatus.SUSPENDED) : productStatus)
            .formSchema(defaultLoanFormSchema())
            .active(active && (productStatus == null || productStatus == LoanProductStatus.ACTIVE))
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    private String defaultLoanFormSchema() {
        return """
            {
              "type": "object",
              "properties": {}
            }
            """;
    }
}
