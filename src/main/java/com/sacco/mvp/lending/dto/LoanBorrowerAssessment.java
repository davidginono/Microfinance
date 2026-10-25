package com.sacco.mvp.lending.dto;

import java.math.BigDecimal;

/** Read-only assessment from the staff-recorded application, with exact decimal amounts. */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
public record LoanBorrowerAssessment(String business,BigDecimal income,BigDecimal expenses,BigDecimal otherDebt,
    String informationSource,String evidence) {}
