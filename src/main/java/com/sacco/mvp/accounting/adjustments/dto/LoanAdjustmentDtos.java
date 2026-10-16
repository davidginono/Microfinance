package com.sacco.mvp.accounting.adjustments.dto;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class LoanAdjustmentDtos {
    private LoanAdjustmentDtos() { }
    public enum Kind { PROVISION, WRITE_OFF, WRITE_BACK, DISBURSEMENT_REVERSAL }
    @Getter @Setter
    public static class Form {
        private UUID requestKey=UUID.randomUUID(), loanId, allowanceAccountId, expenseAccountId;
        private Kind kind=Kind.PROVISION;
        @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
        private LocalDate date;
        private BigDecimal targetAllowance;
        private String reason="", evidence="";
    }
    public record Choice(UUID id,String label) { }
    public record Context(UUID id,String number,String client,String status,LocalDate disbursed,
            BigDecimal principal,BigDecimal outstanding,BigDecimal allowance,BigDecimal writtenOff,
            long sequence,UUID lastAdjustment,LocalDate lastDate,UUID principalAccount,UUID moneyAccount,
            UUID disbursementJournal,UUID disbursedBy,UUID allowanceAccount,UUID writeOffJournal,UUID writeOffPostedBy) {
        public BigDecimal bookPrincipal(){return status.equals("DISBURSEMENT_REVERSED")?BigDecimal.ZERO:outstanding.subtract(writtenOff);}
    }
    public record Request(UUID id,UUID loanId,String number,String client,Kind kind,LocalDate date,
            BigDecimal amount,BigDecimal allowanceChange,BigDecimal principalChange,UUID debitId,UUID creditId,
            String debitName,String creditName,BigDecimal outstanding,BigDecimal allowance,BigDecimal writtenOff,
            long sequence,UUID lastAdjustment,String reason,String evidence,UUID requestedBy,String requester,
            OffsetDateTime requestedAt,String state,UUID decidedBy,String reviewer,String decisionReason,
            OffsetDateTime decidedAt,UUID journalId,UUID reversesJournalId,String institution,String branch) { }
    public record Page(List<Request> rows,int page,boolean hasNext) { }
}
