package com.sacco.mvp.accounting.repayment.service;

import com.sacco.mvp.domain.EarlyPayoffInterest;
import com.sacco.mvp.domain.InterestMethod;
import com.sacco.mvp.domain.RepaymentFrequency;
import com.sacco.mvp.service.LoanAmortizationCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyInterestCalculatorTest {
    private static final LocalDate RELEASED = LocalDate.of(2026, 11, 18);
    private static final LocalDate FIRST_DUE = LocalDate.of(2026, 12, 18);

    @Test
    void interestOpensAtReleaseAndTheDayAfterEachPriorDueDate() {
        var first = MonthlyInterestCalculator.openingDate(RELEASED, null);
        var second = MonthlyInterestCalculator.openingDate(RELEASED, FIRST_DUE);
        assertThat(first).isEqualTo(RELEASED);
        assertThat(second).isEqualTo(LocalDate.of(2026, 12, 19));
    }

    @Test
    void referenceExamplesUseTheFrozenFlatTotalOrTheScheduledReducingBalance() {
        BigDecimal principal = money("1000000.00");
        var flat = LoanAmortizationCalculator.calculate(principal, 10, money("0.12"), InterestMethod.FLAT_RATE,
            RepaymentFrequency.MONTHLY, null, money("120000.00"));
        assertThat(flat.totalInterest()).isEqualByComparingTo("120000.00");
        assertThat(flat.rows()).allSatisfy(row -> assertThat(row.interest()).isEqualByComparingTo("12000.00"));

        var reducing = LoanAmortizationCalculator.calculate(principal, 10, money("0.12"), InterestMethod.REDUCING_BALANCE,
            RepaymentFrequency.MONTHLY, null, money("0.00"));
        assertThat(reducing.rows().getFirst().interest()).isEqualByComparingTo("10000.00");
        assertThat(reducing.rows().getLast().amount()).isEqualByComparingTo("105582.04");
        assertThat(reducing.totalInterest()).isEqualByComparingTo("55820.76");
    }

    @Test
    void usesCollectedInterestAsCreditAndShowsCashRefundForExcess() {
        var row = new MonthlyInterestCalculator.Instalment(FIRST_DUE, money("1000.00"), money("1000.00"), money("400.00"));
        var payoff = MonthlyInterestCalculator.payoff(FIRST_DUE.minusDays(5), EarlyPayoffInterest.ELAPSED_DAYS,
            money("500.00"), money("0.00"), List.of(row));
        assertThat(payoff.interestDue()).isZero();
        assertThat(payoff.cancelledInterest()).isEqualByComparingTo("600.00");
        assertThat(payoff.interestToReturn()).isEqualByComparingTo("600.00");
        assertThat(payoff.appliedCredit()).isEqualByComparingTo("500.00");
        assertThat(payoff.cashDue()).isZero();
        assertThat(payoff.refundDue()).isEqualByComparingTo("100.00");
    }

    @Test
    void excludesAnOpenPeriodAndKeepsTheNetInterestOfCompletedPeriods() {
        var date = FIRST_DUE.plusDays(1);
        var completed = new MonthlyInterestCalculator.Instalment(FIRST_DUE, money("200.00"), money("125.00"), money("200.00"));
        var open = new MonthlyInterestCalculator.Instalment(FIRST_DUE.plusMonths(1), money("150.00"), money("0.00"), money("5.00"));
        var payoff = MonthlyInterestCalculator.payoff(date, EarlyPayoffInterest.EXCLUDE_OPEN_PERIOD,
            money("500.00"), money("10.00"), List.of(completed, open));
        assertThat(payoff.interestDue()).isEqualByComparingTo("75.00");
        assertThat(payoff.cancelledInterest()).isEqualByComparingTo("150.00");
        assertThat(payoff.interestToReturn()).isZero();
        assertThat(payoff.cashDue()).isEqualByComparingTo("585.00");
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
