package com.sacco.mvp.accounting.repayment.service;

import com.sacco.mvp.domain.EarlyPayoffInterest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** Calculates settlement from the retained instalment amounts and dated interest evidence. */
public final class MonthlyInterestCalculator {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private MonthlyInterestCalculator() {}

    public static LocalDate openingDate(LocalDate disbursedOn, LocalDate previousDueDate) {
        return previousDueDate == null ? disbursedOn : previousDueDate.plusDays(1);
    }

    public static Payoff payoff(LocalDate effectiveDate, EarlyPayoffInterest rule, BigDecimal principal,
                                BigDecimal charges, List<Instalment> instalments) {
        BigDecimal dueInterest = ZERO;
        BigDecimal cancelledInterest = ZERO;
        BigDecimal previouslyCollected = ZERO;
        for (Instalment row : instalments) {
            var part = payoffInterest(effectiveDate,rule,row);
            dueInterest = dueInterest.add(part.due());
            previouslyCollected = previouslyCollected.add(part.returned());
            cancelledInterest = cancelledInterest.add(part.cancelled());
        }
        BigDecimal outstanding = principal.add(charges).add(dueInterest);
        BigDecimal appliedCredit = previouslyCollected.min(outstanding);
        return new Payoff(dueInterest, cancelledInterest, previouslyCollected, appliedCredit,
            outstanding.subtract(appliedCredit), previouslyCollected.subtract(appliedCredit));
    }

    public record Instalment(LocalDate dueDate, BigDecimal netScheduledInterest, BigDecimal paidInterest,
                             BigDecimal elapsedInterest) {}

    public static InterestPart payoffInterest(LocalDate date, EarlyPayoffInterest rule, Instalment row) {
        BigDecimal earned = row.netScheduledInterest();
        if (date.isBefore(row.dueDate())) {
            earned = rule == EarlyPayoffInterest.ELAPSED_DAYS
                ? row.elapsedInterest().setScale(2, RoundingMode.HALF_UP).max(ZERO).min(earned)
                : ZERO;
        }
        BigDecimal difference = earned.subtract(row.paidInterest());
        return new InterestPart(difference.max(ZERO),difference.negate().max(ZERO),row.netScheduledInterest().subtract(earned));
    }

    public record InterestPart(BigDecimal due, BigDecimal returned, BigDecimal cancelled) {}

    public record Payoff(BigDecimal interestDue, BigDecimal cancelledInterest, BigDecimal interestToReturn,
                         BigDecimal appliedCredit, BigDecimal cashDue, BigDecimal refundDue) {}
}
