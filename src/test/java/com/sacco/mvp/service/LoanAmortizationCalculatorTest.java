package com.sacco.mvp.service;

import com.sacco.mvp.domain.InterestMethod;
import com.sacco.mvp.domain.RepaymentFrequency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoanAmortizationCalculatorTest {
    @Test
    void reducingBalanceUsesDecimalAnnuityAndReconcilesFinalPrincipal() {
        var result = calculate("120000.00", 12, "0.12", InterestMethod.REDUCING_BALANCE, null);
        assertThat(result.installment()).isEqualByComparingTo("10661.85");
        assertThat(result.rows().getFirst().interest()).isEqualByComparingTo("1200.00");
        assertThat(result.rows().getLast().remainingPrincipal()).isEqualByComparingTo("0.00");
        assertReconciles(result, new BigDecimal("120000.00"));
    }

    @Test
    void zeroInterestFinalCentIsIncludedInAffordabilityMaximum() {
        var result = calculate("100.00", 3, "0", InterestMethod.REDUCING_BALANCE, null);
        assertThat(result.installment()).isEqualByComparingTo("33.33");
        assertThat(result.rows().getLast().amount()).isEqualByComparingTo("33.34");
        assertThat(result.maximumInstallment()).isEqualByComparingTo("33.34");
        assertThat(result.totalRepayment()).isEqualByComparingTo("100.00");
        assertThat(result.totalInterest()).isEqualByComparingTo("0.00");
    }

    @Test
    void largePrincipalDoesNotLoseCentsThroughDoubleConversion() {
        var result = calculate("9007199254740993.01", 3, "0", InterestMethod.REDUCING_BALANCE, null);
        assertThat(result.totalRepayment()).isEqualByComparingTo("9007199254740993.01");
        assertReconciles(result, new BigDecimal("9007199254740993.01"));
    }

    @Test
    void annualFlatInterestIsProportionalToTenure() {
        assertThat(LoanAmortizationCalculator.flatInterest(new BigDecimal("120000"), new BigDecimal("0.12"), 6))
            .isEqualByComparingTo("7200.00");
        var result = LoanAmortizationCalculator.calculate(new BigDecimal("120000.00"), 6,
            new BigDecimal("0.12"), InterestMethod.FLAT_RATE, RepaymentFrequency.MONTHLY, null, new BigDecimal("7200.00"));
        assertThat(result.totalRepayment()).isEqualByComparingTo("127200.00");
        assertReconciles(result, new BigDecimal("120000.00"));
    }

    @Test
    void weeklyPeriodsUse52PerYearAndNormalizeUpForAffordability() {
        assertThat(LoanAmortizationCalculator.numberOfPayments(12, RepaymentFrequency.WEEKLY)).isEqualTo(52);
        assertThat(LoanAmortizationCalculator.numberOfPayments(1, RepaymentFrequency.WEEKLY)).isEqualTo(5);
        assertThat(LoanAmortizationCalculator.monthlyEquivalent(new BigDecimal("1000.00"), RepaymentFrequency.WEEKLY))
            .isEqualByComparingTo("4333.34");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 601, Integer.MAX_VALUE})
    void rejectsUnboundedPaymentCounts(int payments) {
        assertThatThrownBy(() -> calculate("120000.00", payments, "0.12", InterestMethod.REDUCING_BALANCE, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "100001", "10"})
    void rejectsInvalidOrNonAmortizingOverrides(String override) {
        assertThatThrownBy(() -> calculate("100000.00", 12, "0.12", InterestMethod.REDUCING_BALANCE, new BigDecimal(override)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeRatesAndFractionalCents() {
        assertThatThrownBy(() -> calculate("100.00", 3, "-0.12", InterestMethod.REDUCING_BALANCE, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculate("100.001", 3, "0.12", InterestMethod.REDUCING_BALANCE, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void representativeAmountsRatesAndPeriodsAlwaysReconcile() {
        for (String principal : new String[] {"100.01", "120000.00", "1000000.99"}) {
            for (String rate : new String[] {"0", "0.0001", "0.12", "0.36"}) {
                for (int periods : new int[] {1, 3, 12, 52, 120}) {
                    var result = calculate(principal, periods, rate, InterestMethod.REDUCING_BALANCE, null);
                    assertReconciles(result, new BigDecimal(principal));
                }
            }
        }
    }

    private LoanAmortizationCalculator.Result calculate(String principal, int periods, String rate,
                                                         InterestMethod method, BigDecimal override) {
        return LoanAmortizationCalculator.calculate(new BigDecimal(principal), periods, new BigDecimal(rate),
            method, RepaymentFrequency.MONTHLY, override, BigDecimal.ZERO);
    }

    private void assertReconciles(LoanAmortizationCalculator.Result result, BigDecimal principal) {
        assertThat(result.rows().stream().map(LoanAmortizationCalculator.Installment::principal)
            .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(principal);
        assertThat(result.totalRepayment()).isEqualByComparingTo(principal.add(result.totalInterest()));
        result.rows().forEach(row -> {
            assertThat(row.principal()).isPositive();
            assertThat(row.interest()).isNotNegative();
            assertThat(row.remainingPrincipal()).isNotNegative();
            assertThat(row.amount()).isEqualByComparingTo(row.principal().add(row.interest()));
        });
    }
}
