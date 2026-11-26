package com.sacco.mvp.accounting.charges.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class LoanChargeDtos {
    private LoanChargeDtos() {}
    public enum Timing { SEPARATE, DEDUCT, FINANCED }
    @Data public static class PolicyForm {
        private UUID requestKey=UUID.randomUUID(),expectedVersion,productId,receivableAccountId,managementAccountId,insuranceAccountId,applicationAccountId,processingAccountId;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private UUID feeRecoveryAccountId;
        @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
        private LocalDate effectiveFrom;
        private Timing managementTiming=Timing.SEPARATE,insuranceTiming=Timing.SEPARATE;
        private String policyReference="",agreementReference="",insurer="",insurerVerification="";
        private boolean enabled,allowCustom;
        private BigDecimal customManagementLimit;
    }
    public record Policy(UUID id,UUID productId,LocalDate effectiveFrom,boolean enabled,Timing managementTiming,Timing insuranceTiming,
        UUID receivableAccountId,UUID managementAccountId,UUID insuranceAccountId,String policyReference,String agreementReference,
        String insurer,String insurerVerification,boolean allowCustom,BigDecimal customManagementLimit,
        int accountingVersion,UUID applicationAccountId,UUID processingAccountId,UUID feeRecoveryAccountId) {
        public Policy(UUID id,UUID productId,LocalDate effectiveFrom,boolean enabled,Timing managementTiming,Timing insuranceTiming,
                UUID receivableAccountId,UUID managementAccountId,UUID insuranceAccountId,String policyReference,String agreementReference,
                String insurer,String insurerVerification,boolean allowCustom,BigDecimal customManagementLimit,int accountingVersion,
                UUID applicationAccountId,UUID processingAccountId) {
            this(id,productId,effectiveFrom,enabled,managementTiming,insuranceTiming,receivableAccountId,managementAccountId,insuranceAccountId,
                policyReference,agreementReference,insurer,insurerVerification,allowCustom,customManagementLimit,accountingVersion,applicationAccountId,processingAccountId,null);
        }
        public Policy(UUID id,UUID productId,LocalDate effectiveFrom,boolean enabled,Timing managementTiming,Timing insuranceTiming,
                UUID receivableAccountId,UUID managementAccountId,UUID insuranceAccountId,String policyReference,String agreementReference,
                String insurer,String insurerVerification,boolean allowCustom,BigDecimal customManagementLimit) {
            this(id,productId,effectiveFrom,enabled,managementTiming,insuranceTiming,receivableAccountId,managementAccountId,insuranceAccountId,
                policyReference,agreementReference,insurer,insurerVerification,allowCustom,customManagementLimit,1,null,null);
        }
        public int getAccountingVersion(){return accountingVersion;}
        public String getPolicyReference(){return policyReference;}
        public UUID account(String kind) {
            return switch(kind) {case "INSURANCE" -> insuranceAccountId;case "APPLICATION" -> accountingVersion==1?managementAccountId:applicationAccountId;
                case "PROCESSING" -> accountingVersion==1?managementAccountId:processingAccountId;default -> throw new IllegalArgumentException("charges.error.account");};
        }
        public String accountKind(String kind) {return kind.equals("INSURANCE")?"INSURANCE":accountingVersion==1?"MANAGEMENT":kind;}
    }
    public record GeneratedAccount(String component,UUID accountId,String label,String state) {
        public String getComponent(){return component;} public UUID getAccountId(){return accountId;}
        public String getLabel(){return label;} public String getState(){return state;}
    }
    public record AccountSummary(Policy current,List<GeneratedAccount> generated,Map<String,String> currentMappings) {}
    public record Term(UUID id,String kind,String basis,BigDecimal basisValue,BigDecimal amount,Timing timing,
        UUID receivableAccountId,UUID liabilityAccountId,String evidence,String provider,String verification) {
        @com.fasterxml.jackson.annotation.JsonIgnore
        public boolean isPercentageRate(){return Set.of("PRINCIPAL_RATE","TOPUP_ADDITIONAL_RATE","TOPUP_COMBINED_RATE").contains(basis);}
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Plan(UUID policyId,String policyReference,String agreementReference,List<Term> terms,boolean supported,String error) {
        public BigDecimal total(){return terms.stream().map(Term::amount).reduce(new BigDecimal("0.00"),BigDecimal::add);}
        public BigDecimal managementTotal(){return terms.stream().filter(t->!t.kind().equals("INSURANCE")).map(Term::amount).reduce(new BigDecimal("0.00"),BigDecimal::add);}
        public BigDecimal getManagementTotal(){return managementTotal();}
        public BigDecimal deductions(){return terms.stream().filter(t->t.timing()==Timing.DEDUCT).map(Term::amount).reduce(new BigDecimal("0.00"),BigDecimal::add);}
        public BigDecimal separate(){return total().subtract(deductions());}
        public BigDecimal getTotal(){return total();} public BigDecimal getDeductions(){return deductions();} public BigDecimal getSeparate(){return separate();}
    }
    public record FeeBalance(UUID termId,UUID accountId,@com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal outstanding) {}
    public record FeePayment(UUID termId,UUID accountId,@com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal amount) {}
    public record RecoveryBalance(UUID termId,String kind,UUID accountId,UUID holdingAccountId,
        @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal outstanding,
        LocalDate startsOn) {}
    public record RecoveryPayment(UUID termId,UUID accountId,UUID holdingAccountId,
        @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) BigDecimal amount) {}
    public record RecoveryEvent(UUID id,UUID termId,UUID accountId,UUID holdingAccountId,String kind,BigDecimal amount) {}
    public record RecoveryReview(UUID id,UUID loanId,UUID makerId,UUID holdingAccountId,BigDecimal principalSnapshot,
        long postingSnapshot,Map<String,BigDecimal> amounts,String evidence,UUID journalId) {
        public boolean approved(){return journalId!=null;}
    }
    public record RecoverySnapshot(BigDecimal principal,long sequence,LocalDate latestDate) {}
    @Data public static class RecoveryReviewForm {
        private UUID requestKey=UUID.randomUUID(),holdingAccountId;
        private BigDecimal application,processing;
        private String evidence="";
    }
    public record Balance(UUID loanId,long sequence,BigDecimal assessed,BigDecimal collected,BigDecimal outstanding,LocalDate lastDate) {}
    public record Movement(UUID id,UUID loanId,UUID termId,String kind,BigDecimal amount,UUID journalId,UUID actorId) {}
    public record Summary(Plan plan,Balance balance,UUID assessmentJournal,UUID assessmentActor,String loanNumber,String institution,String branch) {}
    @Data public static class AssessmentForm {
        private UUID requestKey=UUID.randomUUID(); private LocalDate date; private String evidence="",reason="";
    }
}
