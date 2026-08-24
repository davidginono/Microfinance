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
        Object loanId,
        String loanDescription,
        String loanName,
        BigDecimal outstandingBalance,
        BigDecimal outstandingPrincipal,
        BigDecimal outstandingInterest
    ) {
        public String loanIdText() {
            if (loanId == null) {
                return "";
            }
            if (loanId instanceof Number) {
                return new BigDecimal(String.valueOf(loanId)).stripTrailingZeros().toPlainString();
            }
            return String.valueOf(loanId).trim();
        }

        public BigDecimal balanceIncludingInterest() {
            if (outstandingBalance != null) {
                return outstandingBalance;
            }
            if (outstandingPrincipal == null || outstandingInterest == null) {
                return null;
            }
            return outstandingPrincipal.add(outstandingInterest);
        }
    }
}
