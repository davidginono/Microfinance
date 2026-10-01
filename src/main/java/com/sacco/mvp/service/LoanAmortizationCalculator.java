package com.sacco.mvp.service;

import com.sacco.mvp.domain.InterestMethod;
import com.sacco.mvp.domain.RepaymentFrequency;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Fixed-period estimates; dated contracts and posted payments remain separate records. */
public final class LoanAmortizationCalculator {
    public static final String VERSION = "DECIMAL_PERIODIC_V1";
    public static final int MAX_PAYMENTS = 600;
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private LoanAmortizationCalculator() {}

    public static Result estimate(BigDecimal principal, int months, Map<String, Object> snapshot) {
        boolean current = VERSION.equals(snapshot.get("calculationVersion"));
        RepaymentFrequency frequency = current
            ? RepaymentFrequency.valueOf(String.valueOf(snapshot.get("repaymentFrequency"))) : RepaymentFrequency.MONTHLY;
        InterestMethod method = snapshot.get("interestMethod") == null ? InterestMethod.FLAT_RATE
            : InterestMethod.valueOf(String.valueOf(snapshot.get("interestMethod")));
        BigDecimal rate = decimal(snapshot.get("interestRate"), BigDecimal.ZERO);
        BigDecimal flat = decimal(snapshot.get("interestAmount"), flatInterest(principal, rate, months));
        BigDecimal legacyPayment = !current && method == InterestMethod.REDUCING_BALANCE
            ? decimal(snapshot.get("monthlyRepaymentAmount"), null) : null;
        return calculate(principal, numberOfPayments(months, frequency), rate, method, frequency, legacyPayment, flat);
    }

    private static BigDecimal decimal(Object value, BigDecimal fallback) {
        return value == null ? fallback : new BigDecimal(String.valueOf(value));
    }

    public static int numberOfPayments(int months, RepaymentFrequency frequency) {
        if (months < 1 || months > MAX_PAYMENTS || frequency == null) {
            throw new IllegalArgumentException("Select a valid repayment period and frequency.");
        }
        int payments = frequency == RepaymentFrequency.WEEKLY
            ? (months * 52 + 11) / 12
            : months;
        validatePayments(payments);
        return payments;
    }

    public static BigDecimal monthlyEquivalent(BigDecimal installment, RepaymentFrequency frequency) {
        if (installment == null || installment.signum() < 0 || frequency == null) {
            throw new IllegalArgumentException("A valid instalment and repayment frequency are required.");
        }
        return frequency == RepaymentFrequency.WEEKLY
            ? installment.multiply(BigDecimal.valueOf(52)).divide(BigDecimal.valueOf(12), 2, RoundingMode.UP)
            : installment.setScale(2, RoundingMode.UP);
    }

    public static BigDecimal flatInterest(BigDecimal principal, BigDecimal annualRate, int months) {
        return principal.multiply(annualRate).multiply(BigDecimal.valueOf(months))
            .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal annuity(BigDecimal principal, BigDecimal periodicRate, int payments) {
        validatePayments(payments);
        if (periodicRate.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(payments), 2, RoundingMode.HALF_UP);
        }
        BigDecimal factor = BigDecimal.ONE.add(periodicRate, PRECISION).pow(payments, PRECISION);
        return principal.multiply(periodicRate, PRECISION).multiply(factor, PRECISION)
            .divide(factor.subtract(BigDecimal.ONE, PRECISION), 2, RoundingMode.HALF_UP);
    }

    public static Result calculate(BigDecimal principal, int payments, BigDecimal annualRate,
                                   InterestMethod method, RepaymentFrequency frequency,
                                   BigDecimal installmentOverride, BigDecimal flatInterest) {
        validatePayments(payments);
        principal = exactMoney(principal, "Loan amount");
        if (principal.signum() <= 0 || principal.precision() > 19) {
            throw new IllegalArgumentException("Loan amount must be positive and within the supported range.");
        }
        if (annualRate == null || annualRate.signum() < 0 || annualRate.compareTo(new BigDecimal("100")) > 0
            || method == null || frequency == null) {
            throw new IllegalArgumentException("Valid interest terms and repayment frequency are required.");
        }
        BigDecimal rate = annualRate.divide(BigDecimal.valueOf(frequency == RepaymentFrequency.WEEKLY ? 52 : 12), PRECISION);
        BigDecimal totalFlatInterest = method == InterestMethod.FLAT_RATE ? exactMoney(flatInterest, "Flat interest") : ZERO;
        if (totalFlatInterest.signum() < 0) {
            throw new IllegalArgumentException("Interest cannot be negative.");
        }
        BigDecimal base = installmentOverride == null
            ? (method == InterestMethod.REDUCING_BALANCE ? annuity(principal, rate, payments)
                : principal.add(totalFlatInterest).divide(BigDecimal.valueOf(payments), 2, RoundingMode.HALF_UP))
            : exactMoney(installmentOverride, "Instalment");
        if (base.signum() <= 0) {
            throw new IllegalArgumentException("Loan amount is too small for the selected number of payments.");
        }

        BigDecimal remaining = principal;
        BigDecimal interestAllocated = ZERO;
        BigDecimal flatBase = totalFlatInterest.divide(BigDecimal.valueOf(payments), 2, RoundingMode.DOWN);
        BigDecimal totalInterest = ZERO;
        BigDecimal totalRepayment = ZERO;
        BigDecimal maximumPayment = ZERO;
        List<Installment> rows = new ArrayList<>(payments);
        for (int number = 1; number <= payments; number++) {
            boolean last = number == payments;
            BigDecimal interest = method == InterestMethod.REDUCING_BALANCE
                ? remaining.multiply(rate).setScale(2, RoundingMode.HALF_UP)
                : (last ? totalFlatInterest.subtract(interestAllocated) : flatBase);
            BigDecimal capital = last ? remaining : base.subtract(interest);
            if (capital.signum() <= 0 || (!last && capital.compareTo(remaining) >= 0)) {
                throw new IllegalArgumentException("Instalments must repay principal without settling before the final payment. Review the amount and period.");
            }
            BigDecimal payment = capital.add(interest);
            remaining = remaining.subtract(capital);
            interestAllocated = interestAllocated.add(interest);
            totalInterest = totalInterest.add(interest);
            totalRepayment = totalRepayment.add(payment);
            maximumPayment = maximumPayment.max(payment);
            rows.add(new Installment(number, payment, capital, interest, remaining));
        }
        return new Result(base, maximumPayment, totalInterest, totalRepayment, List.copyOf(rows));
    }

    private static BigDecimal exactMoney(BigDecimal amount, String label) {
        if (amount == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(label + " must have at most two decimal places.");
        }
    }

    private static void validatePayments(int payments) {
        if (payments < 1 || payments > MAX_PAYMENTS) {
            throw new IllegalArgumentException("The repayment schedule must contain 1 to 600 payments.");
        }
    }

    public record Installment(int number, BigDecimal amount, BigDecimal principal, BigDecimal interest,
                              BigDecimal remainingPrincipal) {}

    public record Result(BigDecimal installment, BigDecimal maximumInstallment, BigDecimal totalInterest,
                         BigDecimal totalRepayment, List<Installment> rows) {}
}
