package com.sacco.mvp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.InterestMethod;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
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
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        int safeTenor = tenorMonths == null || tenorMonths <= 0 ? 1 : tenorMonths;
        LoanProductSetting product = loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue(saccoId, loanType)
            .orElseThrow(() -> new IllegalArgumentException("Loan product settings not found"));
        validateRequestedAmount(product, safeAmount);
        validateRepaymentPeriod(product, safeTenor);

        BigDecimal insuranceRate = product.getInsuranceRate() == null ? DEFAULT_INSURANCE_RATE : product.getInsuranceRate();
        BigDecimal applicationFee = saccoSettingsRepository.findById(saccoId)
            .map(SaccoSettings::getResolvedApplicationFee)
            .orElse(APPLICATION_FEE);
        BigDecimal interestRate = product.getInterestRate() == null ? DEFAULT_INTEREST_RATE : product.getInterestRate();

        BigDecimal insuranceFee = safeAmount.multiply(insuranceRate)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal loanBalance = outstandingLoanBalance(memberId, topUpSourceLoanId);
        BigDecimal principalAmount = safeAmount
            .add(loanBalance)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDeductions = applicationFee.add(insuranceFee)
            .setScale(2, RoundingMode.HALF_UP);

        AmortizationResult amortization = amortize(safeAmount, safeTenor, interestRate, product.getInterestMethod());
        BigDecimal interestAmount = amortization.totalInterest();
        BigDecimal principalPlusInterest = amortization.totalRepayment()
            .add(loanBalance)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal periodicRepaymentAmount = amortization.periodicPayment();

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("requestedAmount", safeAmount);
        snapshot.put("applicationFee", applicationFee);
        snapshot.put("insuranceFee", insuranceFee);
        snapshot.put("totalDeductions", totalDeductions);
        snapshot.put("loanBalance", loanBalance);
        snapshot.put("principalAmount", principalAmount);
        snapshot.put("loanToBePaid", principalAmount);
        snapshot.put("principalPlusInterest", principalPlusInterest);
        snapshot.put("loanPlusInterest", principalPlusInterest);
        snapshot.put("interestAmount", interestAmount);
        snapshot.put("monthlyRepaymentAmount", periodicRepaymentAmount);
        snapshot.put("applicationFeeRate", applicationFee);
        snapshot.put("insuranceRate", insuranceRate);
        snapshot.put("interestRate", interestRate);
        snapshot.put("interestMethod", product.getInterestMethod() == null ? InterestMethod.FLAT_RATE.name() : product.getInterestMethod().name());
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
        if (topUpSourceLoanId == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        LoanApplication sourceLoan = loanApplicationRepository.findById(topUpSourceLoanId)
            .filter(loan -> memberId.equals(loan.getApplicantMemberId()))
            .filter(loan -> loan.getStatus() == LoanStatus.FINAL_APPROVED || loan.getStatus() == LoanStatus.DEFAULTED)
            .orElse(null);
        if (sourceLoan == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal outstanding = sourceLoan.getInstallmentAmount() != null
            ? sourceLoan.getInstallmentAmount().multiply(BigDecimal.valueOf(Math.max(sourceLoan.getTenorMonths(), 1)))
            : sourceLoan.getAmount();
        return outstanding.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateRequestedAmount(LoanProductSetting product, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Loan amount must be greater than zero.");
        }
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

    private AmortizationResult amortize(BigDecimal principal,
                                        int tenorMonths,
                                        BigDecimal annualRate,
                                        InterestMethod interestMethod) {
        InterestMethod method = interestMethod == null ? InterestMethod.FLAT_RATE : interestMethod;
        if (method == InterestMethod.REDUCING_BALANCE) {
            BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);
            if (monthlyRate.compareTo(BigDecimal.ZERO) <= 0) {
                BigDecimal payment = principal.divide(BigDecimal.valueOf(tenorMonths), 2, RoundingMode.HALF_UP);
                return new AmortizationResult(
                    payment,
                    payment.multiply(BigDecimal.valueOf(tenorMonths)).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                );
            }
            double monthlyRateDouble = monthlyRate.doubleValue();
            double factor = 1d - Math.pow(1d + monthlyRateDouble, -tenorMonths);
            BigDecimal payment = BigDecimal.valueOf(principal.doubleValue() * monthlyRateDouble / factor)
                .setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalRepayment = payment.multiply(BigDecimal.valueOf(tenorMonths)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalInterest = totalRepayment.subtract(principal).setScale(2, RoundingMode.HALF_UP);
            return new AmortizationResult(payment, totalRepayment, totalInterest);
        }

        BigDecimal totalInterest = principal.multiply(annualRate)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRepayment = principal.add(totalInterest)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal payment = totalRepayment.divide(BigDecimal.valueOf(tenorMonths), 2, RoundingMode.HALF_UP);
        return new AmortizationResult(payment, totalRepayment, totalInterest);
    }

    private record AmortizationResult(
        BigDecimal periodicPayment,
        BigDecimal totalRepayment,
        BigDecimal totalInterest
    ) {
    }
}
