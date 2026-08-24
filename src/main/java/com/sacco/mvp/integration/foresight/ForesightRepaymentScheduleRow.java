package com.sacco.mvp.integration.foresight;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ForesightRepaymentScheduleRow(
    Integer paymentNumber,
    LocalDate month,
    BigDecimal beginningBalance,
    BigDecimal amountToPay,
    BigDecimal principal,
    BigDecimal interest,
    BigDecimal endingBalance
) {
}
