package com.sacco.mvp.lending.controls.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CreditControlDtos {
    private CreditControlDtos() {}
    public record Page<T>(List<T> rows,int page,boolean hasNext) {}
    public record Choice(UUID id,String label) {}
    public record Policy(UUID id,int revision,boolean capacityRequired,boolean collateralRequired,String reference,
                         boolean remindersEnabled,Integer reminderDaysBefore,Integer reminderEveryDays) {}
    public record Loan(UUID id,String number,String borrower,String status,BigDecimal principal,BigDecimal dueInterest,
                       BigDecimal futureInterest,BigDecimal charges,BigDecimal arrears,LocalDate oldestDue,LocalDate nextDue,boolean tracked) {}
    public record Guarantee(UUID id,UUID loanId,String loanNumber,String borrower,String guarantor,String status,
                            String subject,BigDecimal liability,String capacityError) {}
    public record Assessment(UUID id,BigDecimal income,BigDecimal expenses,BigDecimal otherDebt,BigDecimal capacity,
                             LocalDate assessedOn,LocalDate validThrough,String evidence,String policyReference,String actor,OffsetDateTime recordedAt) {}
    public record Asset(UUID id,String code,String description,String owner,String ownerEvidence,BigDecimal value,
                        BigDecimal encumbrance,LocalDate validThrough,BigDecimal pledged,int sequence) {}
    public record CollateralEvent(UUID id,String kind,UUID loanId,String loanNumber,BigDecimal amount,BigDecimal encumbrance,
                                  LocalDate validThrough,LocalDate effectiveDate,String evidence,String reason,String actor,OffsetDateTime recordedAt) {}
    public record Pledge(UUID loanId,String number,BigDecimal amount) {}
    public record Case(UUID id,UUID loanId,String loanNumber,String borrower,String state,UUID assignedTo,String officer,
                       LocalDate nextAction,BigDecimal arrears,LocalDate oldestDue,int sequence,String reason) {}
    public record CollectionEvent(UUID id,String kind,String officer,LocalDate nextAction,BigDecimal amount,LocalDate promiseOn,
                                  LocalDate effectiveDate,String evidence,String actor,OffsetDateTime recordedAt) {}
    @Data public static class PolicyForm {
        private UUID requestKey=UUID.randomUUID();private int expectedRevision;
        private boolean capacityRequired,collateralRequired,verified,remindersEnabled;private String reference="";
        private Integer reminderDaysBefore,reminderEveryDays;
    }
    @Data public static class AssessmentForm {
        private UUID requestKey=UUID.randomUUID();private BigDecimal income,expenses,otherDebt,capacity;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate assessedOn,validThrough;
        private String evidence="",policyReference="";private boolean verified;
    }
    @Data public static class AssetForm {
        private UUID requestKey=UUID.randomUUID();private String code="",description="",owner="",ownerEvidence="";private boolean verified;
    }
    @Data public static class CollateralForm {
        private UUID requestKey=UUID.randomUUID();private int expectedSequence;private String kind="VALUATION";
        private UUID loanId;private BigDecimal amount,encumbrance;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate effectiveDate,validThrough;
        private String evidence="",reason="";private boolean verified;
    }
    @Data public static class CaseForm {
        private UUID requestKey=UUID.randomUUID();private UUID loanId,assignedTo;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate nextAction;
        private String reason="";
    }
    @Data public static class CollectionForm {
        private UUID requestKey=UUID.randomUUID();private int expectedSequence;private String kind="CONTACT";
        private UUID assignedTo;private BigDecimal amount;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate nextAction,promiseOn,effectiveDate;
        private String evidence="";
    }
}
