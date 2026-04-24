package com.sacco.mvp.integration.memberportal;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanPaymentTransactionDto(
    Long loanId,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate receiptDate,
    BigDecimal principalPaid,
    BigDecimal interestPaid,
    BigDecimal totalPaid
) {}
