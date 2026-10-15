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
        private String loanMode = "CONFIGURED";
        private String tenureUnit = "MONTHS";
        private Integer tenureValue;
        private String interestEntry = "PERCENTAGE";
        private String interestBasis = "WHOLE_LOAN";
        private BigDecimal interestPercent;
        private BigDecimal interestAmount;
        private RepaymentFrequency customFrequency = RepaymentFrequency.MONTHLY;
        private InterestMethod customMethod = InterestMethod.FLAT_RATE;
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
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate firstPaymentDate;
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
    public record ReceiptLoan(UUID id,UUID clientId,String number,String client,String clientNumber,BigDecimal duePrincipal,BigDecimal dueInterest,BigDecimal outstandingPrincipal,LocalDate disbursed,LocalDate lastPayment,String status) {}
    public record Filter(String search, LocalDate from, LocalDate through, String status, String sort, int page, UUID clientId, UUID productId) {
        public Filter(String search, LocalDate from, LocalDate through, String status, String sort, int page, UUID clientId) {
            this(search, from, through, status, sort, page, clientId, null);
        }
    }
    public record Page<T>(List<T> rows, int page, boolean hasNext) {}
    public record LoanRow(UUID id, String number, UUID clientId, String client, String clientNumber,
        String product, LocalDate applicationDate, BigDecimal requestedPrincipal, BigDecimal principal,
        String status, BigDecimal outstandingPrincipal, BigDecimal dueInterest, BigDecimal arrears,
        BigDecimal futureInterest, BigDecimal principalPaid, BigDecimal interestPaid) {}
    public record Posting(UUID id, UUID loanId, String loanNumber, String client, String clientNumber,
        String kind, LocalDate date, String reference, BigDecimal amount, BigDecimal principal, BigDecimal interest,
        String moneyAccount, String principalAccount, String interestAccount, String evidence, String notes,
        UUID actorId, String actor, OffsetDateTime recordedAt, UUID journalId, UUID transactionId,
        UUID reversesId, UUID reversedBy, BigDecimal principalBalance, String product) {}
    public record Detail(LoanRow loan, String institution, String branch, String recordedBy, OffsetDateTime recordedAt,
        String purpose, String business, BigDecimal income, BigDecimal expenses, BigDecimal otherDebt,
        String informationSource, String guarantors, String collateral, String evidence, String notes,
        BigDecimal rate, String method, String frequency, int months, LocalDate firstPaymentDate,
        BigDecimal installment, BigDecimal contractualInterest, List<ScheduleRow> schedule,
        String tenureUnit,int tenureValue,String interestBasis,String loanMode,String interestEntry) {
        public Detail(LoanRow loan,String institution,String branch,String recordedBy,OffsetDateTime recordedAt,String purpose,String business,BigDecimal income,BigDecimal expenses,BigDecimal otherDebt,String informationSource,String guarantors,String collateral,String evidence,String notes,BigDecimal rate,String method,String frequency,int months,LocalDate firstPaymentDate,BigDecimal installment,BigDecimal contractualInterest,List<ScheduleRow> schedule){this(loan,institution,branch,recordedBy,recordedAt,purpose,business,income,expenses,otherDebt,informationSource,guarantors,collateral,evidence,notes,rate,method,frequency,months,firstPaymentDate,installment,contractualInterest,schedule,"MONTHS",months,"ANNUAL","CONFIGURED","PERCENTAGE");}
    }
    public record ScheduleRow(int number, LocalDate date, BigDecimal principal, BigDecimal interest, BigDecimal amount) {}
    public record Statement(String client, String clientNumber, BigDecimal principal, BigDecimal interest, BigDecimal arrears,
        BigDecimal futureInterest, long loans, Page<LoanRow> page) {}
    public record Totals(long count, BigDecimal amount, BigDecimal principal, BigDecimal interest) {}
    public record StatementTerms(UUID loanId, BigDecimal rate, String frequency, int months, int installments,
        LocalDate firstPaymentDate, BigDecimal installment, String institution, String branch, String rateBasis, String tenureUnit, int tenureValue, LocalDate maturityDate) {
        public StatementTerms(UUID loanId,BigDecimal rate,String frequency,int months,int installments,LocalDate firstPaymentDate,BigDecimal installment,String institution,String branch){this(loanId,rate,frequency,months,installments,firstPaymentDate,installment,institution,branch,"ANNUAL","MONTHS",months,null);}
        public LocalDate lastPaymentDate() {
            if(maturityDate!=null)return maturityDate;
            if (firstPaymentDate == null || installments < 1 || !("WEEKLY".equals(frequency) || "MONTHLY".equals(frequency))) return null;
            return "WEEKLY".equals(frequency) ? firstPaymentDate.plusWeeks(installments - 1L)
                : firstPaymentDate.plusMonths(installments - 1L);
        }
    }
}
