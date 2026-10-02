package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.RepaymentFrequency;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EligibilityService {
    private static final BigDecimal DEFAULT_RATIO = new BigDecimal("0.4000");

    private final LoanProductSettingRepository loanProductSettingRepository;
    private final LoanAnalyticsService loanAnalyticsService;
    private final ObjectMapper objectMapper;

    public EligibilityResult check(String saccoId, UUID memberId, LoanType loanType, BigDecimal amount) {
        return check(saccoId, memberId, resolveProduct(saccoId, loanType), amount, Map.of(), Map.of());
    }

    public EligibilityResult check(String saccoId,
                                   UUID memberId,
                                   LoanType loanType,
                                   BigDecimal amount,
                                   Map<String, ?> formData,
                                   Map<String, ?> financialSnapshot) {
        return check(saccoId, memberId, resolveProduct(saccoId, loanType), amount, formData, financialSnapshot);
    }

    public EligibilityResult check(String saccoId, UUID memberId, LoanProductSetting product, BigDecimal amount) {
        return check(saccoId, memberId, product, amount, Map.of(), Map.of());
    }

    public EligibilityResult check(String saccoId,
                                   UUID memberId,
                                   LoanProductSetting product,
                                   BigDecimal amount,
                                   String formDataJson,
                                   String financialSnapshotJson) {
        return check(
            saccoId,
            memberId,
            product,
            amount,
            parseJson(formDataJson),
            parseJson(financialSnapshotJson)
        );
    }

    public EligibilityResult check(String saccoId,
                                   UUID memberId,
                                   LoanProductSetting product,
                                   BigDecimal amount,
                                   Map<String, ?> formData,
                                   Map<String, ?> financialSnapshot) {
        if (product == null) {
            throw new IllegalArgumentException("Loan product not found");
        }
        BigDecimal safeAmount = money(amount);
        Map<String, ?> safeFormData = formData == null ? Map.of() : formData;
        Map<String, ?> safeFinancialSnapshot = financialSnapshot == null ? Map.of() : financialSnapshot;

        BigDecimal incomeInput = readMoney(safeFormData, "monthlyIncome", "income", "declaredMonthlyIncome");
        BigDecimal expensesInput = readMoney(safeFormData, "monthlyExpenses", "expenses", "declaredMonthlyExpenses");
        BigDecimal debtInput = readMoney(safeFormData, "otherDebtRepayments", "monthlyDebtRepayments", "debtRepayments");
        BigDecimal monthlyIncome = money(incomeInput);
        BigDecimal monthlyExpenses = money(expensesInput);
        BigDecimal otherDebtRepayments = money(debtInput);
        BigDecimal collateralValue = readMoney(safeFormData, "collateralEstimatedValue", "collateralValue");
        String collateralDescription = readText(safeFormData, "collateralDescription", "securityDescription");
        BigDecimal repaymentInput = monthlyRepayment(safeFinancialSnapshot);
        BigDecimal repayment = money(repaymentInput);
        BigDecimal disposableIncome = monthlyIncome.subtract(monthlyExpenses).subtract(otherDebtRepayments)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal ratio = product.getResolvedMaxRepaymentToDisposableIncomeRatio();
        if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
            ratio = DEFAULT_RATIO;
        }
        BigDecimal maxAffordableRepayment = disposableIncome.multiply(ratio).setScale(2, RoundingMode.DOWN);
        BigDecimal activeExposure = loanAnalyticsService.activeLoanAmount(memberId, saccoId, null);
        BigDecimal repaymentBurdenRatio = disposableIncome.compareTo(BigDecimal.ZERO) <= 0
            ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
            : repayment.divide(disposableIncome, 4, RoundingMode.HALF_UP);

        boolean amountProvided = safeAmount.compareTo(BigDecimal.ZERO) > 0;
        boolean cashFlowValid = incomeInput != null && incomeInput.signum() > 0
            && expensesInput != null && expensesInput.signum() >= 0
            && debtInput != null && debtInput.signum() >= 0;
        boolean affordabilityOk = !product.isAffordabilityCheckRequired()
            || (cashFlowValid && repaymentInput != null && repaymentInput.signum() > 0
                && disposableIncome.compareTo(BigDecimal.ZERO) > 0
                && repayment.compareTo(maxAffordableRepayment) <= 0);
        boolean collateralOk = !product.isCollateralRequired()
            || (!collateralDescription.isBlank()
                && collateralValue != null && collateralValue.signum() >= 0
                && collateralValue.compareTo(minCollateralValue(product, safeAmount)) >= 0);
        boolean eligible = amountProvided && affordabilityOk && collateralOk;
        String reason = reason(product, amountProvided, cashFlowValid, repaymentInput, monthlyIncome, disposableIncome, repayment,
            maxAffordableRepayment, collateralDescription, collateralValue, safeAmount);

        return new EligibilityResult(
            eligible,
            ratio,
            disposableIncome,
            maxAffordableRepayment,
            product.isAffordabilityCheckRequired(),
            monthlyIncome,
            monthlyExpenses,
            otherDebtRepayments,
            repayment,
            repaymentBurdenRatio,
            activeExposure == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : activeExposure,
            money(collateralValue),
            product.isCollateralRequired(),
            reason
        );
    }

    public String policySnapshotJson(EligibilityResult result, int guarantorsRequired, Map<String, Object> extraData) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("assessmentType", "LOCAL_CREDIT_POLICY");
        snapshot.put("verificationStatus", "DECLARED_NOT_VERIFIED");
        snapshot.put("repaymentAmountBasis", "MONTHLY_EQUIVALENT_MAX_INSTALLMENT");
        snapshot.put("assessedAt", OffsetDateTime.now().toString());
        snapshot.put("eligible", result.eligible());
        snapshot.put("reason", result.reason());
        snapshot.put("monthlyIncome", result.monthlyIncome());
        snapshot.put("monthlyExpenses", result.monthlyExpenses());
        snapshot.put("otherDebtRepayments", result.otherDebtRepayments());
        snapshot.put("disposableIncome", result.disposableIncome());
        snapshot.put("repaymentAmount", result.repaymentAmount());
        snapshot.put("maxAffordableRepayment", result.maxAffordableRepayment());
        snapshot.put("repaymentBurdenRatio", result.repaymentBurdenRatio());
        snapshot.put("affordabilityRatio", result.ratio());
        snapshot.put("activeExposure", result.activeExposure());
        snapshot.put("collateralRequired", result.collateralRequired());
        snapshot.put("collateralValue", result.collateralValue());
        snapshot.put("guarantorsRequired", guarantorsRequired);
        if (extraData != null && !extraData.isEmpty()) {
            extraData.forEach(snapshot::putIfAbsent);
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to save policy snapshot", e);
        }
    }

    private LoanProductSetting resolveProduct(String saccoId, LoanType loanType) {
        return loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue(saccoId, loanType)
            .orElseThrow(() -> new IllegalArgumentException("Loan product not found"));
    }

    private Map<String, Object> parseJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String reason(LoanProductSetting product,
                          boolean amountProvided,
                          boolean cashFlowValid,
                          BigDecimal repaymentInput,
                          BigDecimal monthlyIncome,
                          BigDecimal disposableIncome,
                          BigDecimal repayment,
                          BigDecimal maxAffordableRepayment,
                          String collateralDescription,
                          BigDecimal collateralValue,
                          BigDecimal requestedAmount) {
        if (!amountProvided) {
            return "Enter a positive loan amount before calculating the credit assessment.";
        }
        if (product.isAffordabilityCheckRequired() && !cashFlowValid) {
            return "Enter valid monthly income, expenses, and debt repayments. Expenses and debts must be zero or greater.";
        }
        if (product.isAffordabilityCheckRequired() && (repaymentInput == null || repaymentInput.signum() <= 0)) {
            return "Calculate a positive repayment amount with a valid repayment frequency before assessing affordability.";
        }
        if (product.isAffordabilityCheckRequired() && amountProvided && monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return "Enter monthly income before calculating the credit assessment.";
        }
        if (product.isAffordabilityCheckRequired() && amountProvided && disposableIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return "Disposable income is not enough for this loan.";
        }
        if (product.isAffordabilityCheckRequired() && amountProvided && repayment.compareTo(maxAffordableRepayment) > 0) {
            return "Repayment is above the affordable limit for the declared income and expenses.";
        }
        if (product.isCollateralRequired() && collateralDescription.isBlank()) {
            return "Collateral details are required for this loan product.";
        }
        BigDecimal minimumCollateral = minCollateralValue(product, requestedAmount);
        if (product.isCollateralRequired() && (collateralValue == null || collateralValue.signum() < 0 || collateralValue.compareTo(minimumCollateral) < 0)) {
            return "Collateral value is below the minimum coverage required for this loan product.";
        }
        return "Credit assessment requirements are met.";
    }

    private BigDecimal minCollateralValue(LoanProductSetting product, BigDecimal amount) {
        BigDecimal ratio = product.getResolvedMinCollateralCoverageRatio();
        if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0 || amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal readMoney(Map<String, ?> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            return parseMoney(value);
        }
        return null;
    }

    private BigDecimal monthlyRepayment(Map<String, ?> snapshot) {
        if (snapshot.containsKey("monthlyRepaymentAmount")) {
            return readMoney(snapshot, "monthlyRepaymentAmount");
        }
        BigDecimal periodic = readMoney(snapshot, "periodicRepaymentAmount", "installmentAmount");
        if (periodic == null || periodic.signum() <= 0) {
            return null;
        }
        try {
            RepaymentFrequency frequency = RepaymentFrequency.valueOf(String.valueOf(snapshot.get("repaymentFrequency")));
            return LoanAmortizationCalculator.monthlyEquivalent(periodic, frequency);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String readText(Map<String, ?> source, String... keys) {
        if (source == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private BigDecimal parseMoney(Object value) {
        try {
            return new BigDecimal(String.valueOf(value).replace(",", "").trim()).setScale(2, RoundingMode.UNNECESSARY);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    public record EligibilityResult(
        boolean eligible,
        BigDecimal ratio,
        BigDecimal savings,
        BigDecimal maxAllowed,
        boolean savingsLimitCheckRequired,
        BigDecimal monthlyIncome,
        BigDecimal monthlyExpenses,
        BigDecimal otherDebtRepayments,
        BigDecimal repaymentAmount,
        BigDecimal repaymentBurdenRatio,
        BigDecimal activeExposure,
        BigDecimal collateralValue,
        boolean collateralRequired,
        String reason
    ) {
        public EligibilityResult(boolean eligible, BigDecimal ratio, BigDecimal savings, BigDecimal maxAllowed) {
            this(
                eligible,
                ratio,
                savings,
                maxAllowed,
                true,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                false,
                eligible ? "Credit assessment requirements are met." : "Credit assessment requirements are not met."
            );
        }

        public BigDecimal disposableIncome() {
            return savings;
        }

        public BigDecimal maxAffordableRepayment() {
            return maxAllowed;
        }

        public boolean affordabilityCheckRequired() {
            return savingsLimitCheckRequired;
        }
    }
}
