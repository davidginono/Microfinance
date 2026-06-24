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
    private static final BigDecimal DEFAULT_APPLICATION_FEE = new BigDecimal("15000.00");
    private static final BigDecimal DEFAULT_INSURANCE_RATE = new BigDecimal("0.0150");
    private static final BigDecimal DEFAULT_PROCESSING_FEE_RATE = BigDecimal.ZERO.setScale(4);
    private static final BigDecimal DEFAULT_INTEREST_RATE = new BigDecimal("0.1000");
    private static final int DEFAULT_MIN_REPAYMENT_MONTHS = 1;

    private final LoanProductSettingRepository loanProductSettingRepository;

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
                                                BigDecimal applicationFee,
                                                BigDecimal insuranceRate,
                                                BigDecimal processingFeeRate,
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
                                                boolean boardReviewRequired,
                                                Integer boardPriority,
                                                boolean committeeReviewRequired,
                                                Integer committeePriority,
                                                Integer committeeMinimumVotes,
                                                Integer committeeApprovalThreshold,
                                                boolean accountantReviewRequired,
                                              Integer accountantPriority,
                                              boolean disbursementOfficerRequired,
                                              boolean disbursementProofRequired,
                                              boolean applicantAttachmentRequired,
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
            applicationFee == null ? DEFAULT_APPLICATION_FEE : applicationFee,
            insuranceRate == null ? DEFAULT_INSURANCE_RATE : insuranceRate,
            processingFeeRate == null ? DEFAULT_PROCESSING_FEE_RATE : processingFeeRate,
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
            boardReviewRequired,
            boardPriority,
            committeeReviewRequired,
            committeePriority,
            committeeMinimumVotes,
            committeeApprovalThreshold,
            accountantReviewRequired,
            accountantPriority,
            disbursementOfficerRequired,
            disbursementProofRequired,
            applicantAttachmentRequired,
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
                                              BigDecimal applicationFee,
                                              BigDecimal insuranceRate,
                                              BigDecimal processingFeeRate,
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
                                              boolean boardReviewRequired,
                                              Integer boardPriority,
                                              boolean committeeReviewRequired,
                                              Integer committeePriority,
                                              Integer committeeMinimumVotes,
                                              Integer committeeApprovalThreshold,
                                              boolean accountantReviewRequired,
                                              Integer accountantPriority,
                                              boolean disbursementOfficerRequired,
                                              boolean disbursementProofRequired,
                                              boolean applicantAttachmentRequired,
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
            .applicationFee(applicationFee == null ? DEFAULT_APPLICATION_FEE : applicationFee)
            .insuranceRate(insuranceRate)
            .processingFeeRate(processingFeeRate == null ? DEFAULT_PROCESSING_FEE_RATE : processingFeeRate)
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
            .boardReviewRequired(boardReviewRequired)
            .boardPriority(boardPriority)
            .committeeReviewRequired(committeeReviewRequired)
            .committeePriority(committeePriority)
            .committeeMinimumVotes(committeeMinimumVotes)
            .committeeApprovalThreshold(committeeApprovalThreshold)
            .accountantReviewRequired(accountantReviewRequired)
            .accountantPriority(accountantPriority)
            .disbursementOfficerRequired(disbursementOfficerRequired)
            .disbursementProofRequired(disbursementProofRequired)
            .applicantAttachmentRequired(applicantAttachmentRequired)
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
