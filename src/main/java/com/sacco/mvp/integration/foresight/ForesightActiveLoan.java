package com.sacco.mvp.integration.foresight;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ForesightActiveLoan(
    Object loanId,
    LocalDate disbursedDate,
    String loanDescription,
    BigDecimal requestedAmount,
    BigDecimal disbursedAmount,
    BigDecimal interestRate,
    BigDecimal totalInterest
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
}
