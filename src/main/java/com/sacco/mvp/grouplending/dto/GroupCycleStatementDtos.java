package com.sacco.mvp.grouplending.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class GroupCycleStatementDtos {
    private GroupCycleStatementDtos() {}
    public record Context(UUID cycle,UUID group,String institution,String branch,String code,String name,String model,String state) {}
    public record Loan(UUID id,String number,String borrowerNumber,String borrower,String status,BigDecimal original,
        BigDecimal principal,BigDecimal dueInterest,BigDecimal futureInterest,BigDecimal charges,BigDecimal arrears,
        LocalDate nextDue,boolean funded,boolean known) {public BigDecimal outstanding(){return known?principal.add(dueInterest).add(charges):null;}}
    public record Totals(int loans,int funded,int unknown,BigDecimal agreed,BigDecimal fundedPrincipal,
        BigDecimal principal,BigDecimal dueInterest,BigDecimal futureInterest,BigDecimal charges,BigDecimal arrears,LocalDate nextDue) {
        public boolean known(){return unknown==0;}
        public BigDecimal outstanding(){return known()?principal.add(dueInterest).add(charges):null;}
    }
    public record Movement(UUID id,UUID loanId,String loanNumber,UUID receiptId,String reference,LocalDate date,String kind,String payer,
        BigDecimal cash,BigDecimal principal,BigDecimal interest,BigDecimal charges,BigDecimal excluded,BigDecimal nonCash,String reverses) {}
    public record Contribution(UUID id,UUID receiptId,String receipt,String loan,String payer,String beneficiary,LocalDate date,
        BigDecimal amount,BigDecimal principal,BigDecimal interest,BigDecimal charges,boolean reversal) {}
    public record Statement(Context context,LocalDate asOf,OffsetDateTime generatedAt,LocalDate from,LocalDate through,Totals totals,
        List<Loan> loans,List<GroupSharedLoanDtos.Balance> allocations,List<Movement> movements,List<Contribution> contributions,int loanPage,boolean moreLoans,int allocationPage,boolean moreAllocations,int page,boolean moreMovements,
        int contributionPage,boolean moreContributions,boolean loanLinks,boolean receiptLinks) {}
}
