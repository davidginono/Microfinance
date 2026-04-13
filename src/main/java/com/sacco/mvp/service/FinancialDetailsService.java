package com.sacco.mvp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinancialDetailsService {
    private static final BigDecimal APPLICATION_FEE = new BigDecimal("15000.00");
    private static final BigDecimal DEFAULT_INSURANCE_RATE = new BigDecimal("0.0150");
    private static final BigDecimal DEFAULT_INTEREST_RATE = new BigDecimal("0.1000");
    private static final BigDecimal CHAPCHAP_EXTENDED_INTEREST_RATE = new BigDecimal("0.1200");

    private final LoanProductSettingRepository loanProductSettingRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final ObjectMapper objectMapper;

    public Map<String, Object> generateSnapshot(String saccoId, UUID memberId, LoanType loanType, BigDecimal amount,
                                                Integer tenorMonths, UUID topUpSourceLoanId) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        int safeTenor = tenorMonths == null || tenorMonths <= 0 ? 1 : tenorMonths;
        LoanProductSetting product = loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue(saccoId, loanType)
            .orElseThrow(() -> new IllegalArgumentException("Loan product settings not found"));
        validateRepaymentPeriod(product, safeTenor);

        BigDecimal insuranceRate = product.getInsuranceRate() == null ? DEFAULT_INSURANCE_RATE : product.getInsuranceRate();
        BigDecimal interestRate = effectiveInterestRate(product, loanType, safeTenor);

        BigDecimal insuranceFee = safeAmount.multiply(insuranceRate)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal loanBalance = outstandingLoanBalance(memberId, topUpSourceLoanId);
        BigDecimal loanToBePaid = safeAmount
            .add(APPLICATION_FEE)
            .add(insuranceFee)
            .add(loanBalance)
            .setScale(2, RoundingMode.HALF_UP);

        BigDecimal interestAmount = safeAmount.multiply(interestRate)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal loanPlusInterest = loanToBePaid.add(interestAmount)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal monthlyRepaymentAmount = loanPlusInterest.divide(
            BigDecimal.valueOf(safeTenor), 2, RoundingMode.HALF_UP);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("applicationFee", APPLICATION_FEE);
        snapshot.put("insuranceFee", insuranceFee);
        snapshot.put("loanBalance", loanBalance);
        snapshot.put("loanToBePaid", loanToBePaid);
        snapshot.put("loanPlusInterest", loanPlusInterest);
        snapshot.put("interestAmount", interestAmount);
        snapshot.put("monthlyRepaymentAmount", monthlyRepaymentAmount);
        snapshot.put("applicationFeeRate", APPLICATION_FEE);
        snapshot.put("insuranceRate", insuranceRate);
        snapshot.put("interestRate", interestRate);
        snapshot.put("topUpSourceLoanId", topUpSourceLoanId == null ? "" : topUpSourceLoanId.toString());
        return snapshot;
    }

    public String toJson(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to prepare financial details", e);
        }
    }

    private BigDecimal outstandingLoanBalance(UUID memberId, UUID topUpSourceLoanId) {
        LoanApplication sourceLoan = null;
        if (topUpSourceLoanId != null) {
            sourceLoan = loanApplicationRepository.findById(topUpSourceLoanId)
                .filter(loan -> memberId.equals(loan.getApplicantMemberId()))
                .filter(loan -> loan.getStatus() == LoanStatus.FINAL_APPROVED)
                .orElse(null);
        }
        if (sourceLoan == null) {
            LocalDate today = LocalDate.now();
            sourceLoan = loanApplicationRepository.findByApplicantMemberIdAndStatusOrderByCreatedAtDesc(memberId, LoanStatus.FINAL_APPROVED)
                .stream()
                .filter(loan -> loan.getFinalDueDate() == null || !loan.getFinalDueDate().isBefore(today))
                .findFirst()
                .orElse(null);
        }
        if (sourceLoan == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal outstanding = sourceLoan.getInstallmentAmount() != null
            ? sourceLoan.getInstallmentAmount().multiply(BigDecimal.valueOf(Math.max(sourceLoan.getTenorMonths(), 1)))
            : sourceLoan.getAmount();
        return outstanding.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateRepaymentPeriod(LoanProductSetting product, int tenorMonths) {
        if (product.getMaxRepaymentMonths() != null
            && product.getMaxRepaymentMonths() > 0
            && tenorMonths > product.getMaxRepaymentMonths()) {
            throw new IllegalArgumentException("Repayment period cannot exceed " + product.getMaxRepaymentMonths() + " month(s) for this loan product");
        }
    }

    private BigDecimal effectiveInterestRate(LoanProductSetting product, LoanType loanType, int tenorMonths) {
        if (loanType == LoanType.LOAN_ADVANCE) {
            return tenorMonths <= 1 ? BigDecimal.ZERO : CHAPCHAP_EXTENDED_INTEREST_RATE;
        }
        return product.getInterestRate() == null ? DEFAULT_INTEREST_RATE : product.getInterestRate();
    }
}

