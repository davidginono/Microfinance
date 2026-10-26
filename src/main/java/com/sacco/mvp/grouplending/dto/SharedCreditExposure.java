package com.sacco.mvp.grouplending.dto;

import java.math.BigDecimal;

/** Minimal underwriting facts; never a list of another branch's loans or a portfolio asset total. */
public record SharedCreditExposure(long active,long pending,long risk,long missing,BigDecimal liability,BigDecimal monthlyPayment) {
    public static final SharedCreditExposure NONE=new SharedCreditExposure(0,0,0,0,new BigDecimal("0.00"),new BigDecimal("0.00"));
    public boolean known(){return missing==0;}
}
