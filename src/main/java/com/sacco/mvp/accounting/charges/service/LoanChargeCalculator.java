package com.sacco.mvp.accounting.charges.service;

import java.math.*;

/** Charge amounts are rounded individually once; percentages are stored as decimal rates. */
public final class LoanChargeCalculator {
    private LoanChargeCalculator() {}
    public static BigDecimal amount(BigDecimal value) {
        if(value==null)return new BigDecimal("0.00");
        if(value.signum()<0 || value.precision()-value.scale()>16)throw new IllegalArgumentException("charges.error.amount");
        var rounded=value.setScale(2,RoundingMode.HALF_UP);
        if(rounded.precision()-rounded.scale()>16)throw new IllegalArgumentException("charges.error.amount");
        return rounded;
    }
    public static BigDecimal percent(BigDecimal principal,BigDecimal rate) {
        if(rate==null)return new BigDecimal("0.00");
        if(rate.signum()<0 || rate.compareTo(BigDecimal.ONE)>0 || rate.scale()>4)throw new IllegalArgumentException("charges.error.rate");
        return amount(principal.multiply(rate));
    }
}
