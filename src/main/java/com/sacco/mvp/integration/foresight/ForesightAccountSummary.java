package com.sacco.mvp.integration.foresight;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ForesightAccountSummary(
    BigDecimal savingsBalance,
    BigDecimal sharesBalance,
    BigDecimal depositsBalance,
    List<ForesightOutstandingLoan> outstandingLoans
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForesightOutstandingLoan(
        String loanName,
        BigDecimal outstandingBalance
    ) {
    }
}
