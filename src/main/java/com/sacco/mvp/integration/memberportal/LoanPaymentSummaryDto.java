package com.sacco.mvp.integration.memberportal;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LoanPaymentSummaryDto(
    Long loanId,
    String loanDescription,
    BigDecimal requestedAmount,
    BigDecimal disbursedAmount,
    BigDecimal interestRate,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate effectiveDate,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate lastPaymentDate,
    BigDecimal principalAmount,
    BigDecimal interestAmount,
    BigDecimal totalPrincipalPaid,
    BigDecimal totalInterestPaid,
    BigDecimal outstandingPrincipal,
    BigDecimal outstandingInterest,
    BigDecimal totalOutstanding
) {}
