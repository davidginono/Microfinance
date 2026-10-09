package com.sacco.mvp.accounting.loans.dto;

import com.sacco.mvp.domain.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;

public final class LoanRecordingDtos {
    private LoanRecordingDtos() {}
    @Data public static class RecordForm {
        private UUID requestKey = UUID.randomUUID();
        private UUID clientId;
        private UUID productId;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate applicationDate;
        private BigDecimal requestedPrincipal;
        private BigDecimal principal;
        private Integer months;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate firstPaymentDate;
        private String purpose = "";
        private String business = "";
        private BigDecimal income;
        private BigDecimal expenses;
        private BigDecimal otherDebt;
        private String informationSource = "DECLARED";
        private String guarantors = "";
        private String collateral = "";
        private String evidence = "";
        private String notes = "";
    }
    @Data public static class PostForm {
        private UUID requestKey = UUID.randomUUID();
        private UUID loanId;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate effectiveDate;
        private BigDecimal amount;
        private UUID moneyAccountId;
        private UUID principalAccountId;
        private UUID interestAccountId;
        private String reference = "";
        private String evidence = "";
        private String notes = "";
    }
    public record Choice(UUID id, String label, String purpose) {}
    public record Filter(String search, LocalDate from, LocalDate through, String status, String sort, int page, UUID clientId) {}
    public record Page<T>(List<T> rows, int page, boolean hasNext) {}
    public record LoanRow(UUID id, String number, UUID clientId, String client, String clientNumber,
        String product, LocalDate applicationDate, BigDecimal requestedPrincipal, BigDecimal principal,
        String status, BigDecimal outstandingPrincipal, BigDecimal dueInterest, BigDecimal arrears,
        BigDecimal futureInterest, BigDecimal principalPaid, BigDecimal interestPaid) {}
    public record Posting(UUID id, UUID loanId, String loanNumber, String client, String clientNumber,
        String kind, LocalDate date, String reference, BigDecimal amount, BigDecimal principal, BigDecimal interest,
        String moneyAccount, String principalAccount, String interestAccount, String evidence, String notes,
        UUID actorId, String actor, OffsetDateTime recordedAt, UUID journalId, UUID transactionId,
        UUID reversesId, UUID reversedBy, BigDecimal principalBalance) {}
    public record Detail(LoanRow loan, String institution, String branch, String recordedBy, OffsetDateTime recordedAt,
        String purpose, String business, BigDecimal income, BigDecimal expenses, BigDecimal otherDebt,
        String informationSource, String guarantors, String collateral, String evidence, String notes,
        BigDecimal rate, String method, String frequency, int months, LocalDate firstPaymentDate,
        BigDecimal installment, BigDecimal contractualInterest, List<ScheduleRow> schedule) {}
    public record ScheduleRow(int number, LocalDate date, BigDecimal principal, BigDecimal interest, BigDecimal amount) {}
    public record Statement(String client, String clientNumber, BigDecimal principal, BigDecimal interest, BigDecimal arrears,
        BigDecimal futureInterest, long loans, Page<LoanRow> page) {}
    public record Totals(long count, BigDecimal amount, BigDecimal principal, BigDecimal interest) {}
}
