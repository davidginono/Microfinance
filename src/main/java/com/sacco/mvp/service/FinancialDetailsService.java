package com.sacco.mvp.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.InterestMethod;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.RepaymentFrequency;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
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

    private final LoanProductSettingRepository loanProductSettingRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final ObjectMapper objectMapper;

    public Map<String, Object> generateSnapshot(String saccoId, UUID memberId, LoanType loanType, BigDecimal amount,
                                                Integer tenorMonths, UUID topUpSourceLoanId) {
        LoanProductSetting product = loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue(saccoId, loanType)
            .orElseThrow(() -> new IllegalArgumentException("Loan product settings not found"));
        return generateSnapshot(saccoId, memberId, product, amount, tenorMonths, topUpSourceLoanId);
    }

    public Map<String, Object> generateSnapshot(String saccoId, UUID memberId, LoanProductSetting product, BigDecimal amount,
                                                Integer tenorMonths, UUID topUpSourceLoanId) {
        if (product == null) {
            throw new IllegalArgumentException("Loan product settings not found");
        }
        BigDecimal safeAmount;
        try {
            safeAmount = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Loan amount must have at most two decimal places.");
        }
        if (tenorMonths == null || tenorMonths <= 0) {
            throw new IllegalArgumentException("Select a valid repayment period.");
        }
        int safeTenor = tenorMonths;
        validatePositiveAmount(safeAmount);
        validateRepaymentPeriod(product, safeTenor);

        BigDecimal insuranceRate = product.getInsuranceRate() == null ? DEFAULT_INSURANCE_RATE : product.getInsuranceRate();
        BigDecimal fallbackApplicationFee = saccoSettingsRepository.findById(saccoId)
            .map(SaccoSettings::getResolvedApplicationFee)
            .orElse(APPLICATION_FEE);
        BigDecimal applicationFee = product.getResolvedApplicationFee(fallbackApplicationFee);
        BigDecimal processingFeeRate = product.getResolvedProcessingFeeRate();
        BigDecimal interestRate = product.getInterestRate() == null ? DEFAULT_INTEREST_RATE : product.getInterestRate();

        BigDecimal loanBalance = outstandingLoanBalance(memberId, topUpSourceLoanId);
        BigDecimal principalAmount = safeAmount
            .add(loanBalance)
            .setScale(2, RoundingMode.HALF_UP);
        validateRequestedAmount(product, principalAmount);

        BigDecimal insuranceFee = safeAmount.multiply(insuranceRate)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal processingFee = safeAmount.multiply(processingFeeRate)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDeductions = applicationFee.add(insuranceFee).add(processingFee)
            .setScale(2, RoundingMode.HALF_UP);

        InterestMethod method = product.getInterestMethod() == null ? InterestMethod.REDUCING_BALANCE : product.getInterestMethod();
        RepaymentFrequency frequency = product.getResolvedRepaymentFrequency();
        int payments = LoanAmortizationCalculator.numberOfPayments(safeTenor, frequency);
        LoanAmortizationCalculator.Result amortization = LoanAmortizationCalculator.calculate(
            principalAmount, payments, interestRate, method, frequency, null,
            LoanAmortizationCalculator.flatInterest(principalAmount, interestRate, safeTenor));
        BigDecimal interestAmount = amortization.totalInterest();
        BigDecimal principalPlusInterest = amortization.totalRepayment()
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal periodicRepaymentAmount = amortization.installment();

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("requestedAmount", safeAmount);
        snapshot.put("applicationFee", applicationFee);
        snapshot.put("insuranceFee", insuranceFee);
        snapshot.put("processingFee", processingFee);
        snapshot.put("totalDeductions", totalDeductions);
        snapshot.put("loanBalance", loanBalance);
        snapshot.put("principalAmount", principalAmount);
        snapshot.put("loanToBePaid", principalAmount);
        snapshot.put("principalPlusInterest", principalPlusInterest);
        snapshot.put("loanPlusInterest", principalPlusInterest);
        snapshot.put("interestAmount", interestAmount);
        snapshot.put("monthlyRepaymentAmount", LoanAmortizationCalculator.monthlyEquivalent(amortization.maximumInstallment(), frequency));
        snapshot.put("periodicRepaymentAmount", periodicRepaymentAmount);
        snapshot.put("maximumInstallmentAmount", amortization.maximumInstallment());
        snapshot.put("repaymentFrequency", frequency.name());
        snapshot.put("calculationVersion", LoanAmortizationCalculator.VERSION);
        snapshot.put("applicationFeeRate", applicationFee);
        snapshot.put("insuranceRate", insuranceRate);
        snapshot.put("processingFeeRate", processingFeeRate);
        snapshot.put("interestRate", interestRate);
        snapshot.put("tenorMonths", safeTenor);
        snapshot.put("numberOfPayments", payments);
        snapshot.put("interestMethod", method.name());
        snapshot.put("topUpSourceLoanId", topUpSourceLoanId == null ? "" : topUpSourceLoanId.toString());
        if (topUpSourceLoanId != null) {
            snapshot.put("topUpRequestedAmount", safeAmount);
            snapshot.put("topUpSettlementAmount", loanBalance);
            snapshot.put("topUpAmountBeforeFees", safeAmount);
            snapshot.put("topUpAmountAfterFees", safeAmount.subtract(totalDeductions).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        }
        return snapshot;
    }

    public String toJson(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to prepare financial details", e);
        }
    }

    private BigDecimal outstandingLoanBalance(UUID memberId, UUID topUpSourceLoanId) {
        if (topUpSourceLoanId == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        LoanApplication sourceLoan = loanApplicationRepository.findById(topUpSourceLoanId)
            .filter(loan -> memberId.equals(loan.getApplicantMemberId()))
            .filter(loan -> loan.getStatus() == LoanStatus.DISBURSED)
            .orElse(null);
        if (sourceLoan == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal syncedOutstanding = financialSnapshotAmount(sourceLoan, LoanFinancialSnapshotKeys.PAYMENT_SUMMARY_TOTAL_OUTSTANDING);
        if (syncedOutstanding != null) {
            return syncedOutstanding.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal principalPlusInterest = principalPlusInterest(sourceLoan);
        if (principalPlusInterest != null) {
            return principalPlusInterest.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal outstanding = sourceLoan.getInstallmentAmount() != null
            ? sourceLoan.getInstallmentAmount().multiply(BigDecimal.valueOf(Math.max(sourceLoan.getTenorMonths(), 1)))
            : sourceLoan.getAmount();
        return outstanding.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal principalPlusInterest(LoanApplication loan) {
        BigDecimal principal = loan.getAmount() == null ? BigDecimal.ZERO : loan.getAmount();
        Map<String, Object> snapshot = financialSnapshot(loan);
        BigDecimal principalPlusInterest = readBigDecimal(snapshot.get("principalPlusInterest"));
        if (principalPlusInterest != null) {
            return principalPlusInterest;
        }
        BigDecimal loanPlusInterest = readBigDecimal(snapshot.get("loanPlusInterest"));
        if (loanPlusInterest != null) {
            return loanPlusInterest;
        }
        BigDecimal interest = readBigDecimal(snapshot.get("interestAmount"));
        return interest == null ? null : principal.add(interest);
    }

    private BigDecimal financialSnapshotAmount(LoanApplication loan, String key) {
        return readBigDecimal(financialSnapshot(loan).get(key));
    }

    private Map<String, Object> financialSnapshot(LoanApplication loan) {
        if (loan == null || loan.getFinancialSnapshot() == null || loan.getFinancialSnapshot().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(loan.getFinancialSnapshot(), new tools.jackson.core.type.TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private BigDecimal readBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Loan amount must be greater than zero.");
        }
    }

    private void validateRequestedAmount(LoanProductSetting product, BigDecimal amount) {
        validatePositiveAmount(amount);
        if (product.getMinimumAmount() != null && amount.compareTo(product.getMinimumAmount()) < 0) {
            throw new IllegalArgumentException(
                "Loan amount cannot be below " + product.getMinimumAmount().setScale(2, RoundingMode.HALF_UP).toPlainString()
                    + " for this loan product."
            );
        }
        if (product.getMaximumAmount() != null && amount.compareTo(product.getMaximumAmount()) > 0) {
            throw new IllegalArgumentException(
                "Loan amount cannot exceed " + product.getMaximumAmount().setScale(2, RoundingMode.HALF_UP).toPlainString()
                    + " for this loan product."
            );
        }
    }

    private void validateRepaymentPeriod(LoanProductSetting product, int tenorMonths) {
        int minimumMonths = product.getMinimumRepaymentMonths();
        if (tenorMonths < minimumMonths) {
            throw new IllegalArgumentException(
                "Total months to repay cannot be below " + minimumMonths + " month(s) configured for this loan product."
            );
        }
        if (product.getMaxRepaymentMonths() != null
            && product.getMaxRepaymentMonths() > 0
            && tenorMonths > product.getMaxRepaymentMonths()) {
            throw new IllegalArgumentException(
                "Total months to repay cannot exceed " + product.getMaxRepaymentMonths() + " month(s) configured for this loan product."
            );
        }
    }

}
