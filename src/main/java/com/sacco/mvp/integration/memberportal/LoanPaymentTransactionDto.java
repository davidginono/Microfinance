package com.sacco.mvp.integration.memberportal;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LoanPaymentTransactionDto(
    Long loanId,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate receiptDate,
    BigDecimal principalPaid,
    BigDecimal interestPaid,
    BigDecimal totalPaid
) {}
