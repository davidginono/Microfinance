package com.sacco.mvp.integration.foresight;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ForesightInvestment(
    LocalDate receiptDate,
    BigDecimal debit,
    BigDecimal credit,
    BigDecimal runningBalance
) {
}
