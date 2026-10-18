package com.sacco.mvp.accounting.charges.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class LoanChargeDtos {
    private LoanChargeDtos() {}
    public enum Timing { SEPARATE, DEDUCT, FINANCED }
    @Data public static class PolicyForm {
        private UUID requestKey=UUID.randomUUID(),expectedVersion,productId,receivableAccountId,managementAccountId,insuranceAccountId;
        @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
        private LocalDate effectiveFrom;
        private Timing managementTiming=Timing.SEPARATE,insuranceTiming=Timing.SEPARATE;
        private String policyReference="",agreementReference="",insurer="",insurerVerification="";
        private boolean enabled,allowCustom;
        private BigDecimal customManagementLimit;
    }
    public record Policy(UUID id,UUID productId,LocalDate effectiveFrom,boolean enabled,Timing managementTiming,Timing insuranceTiming,
        UUID receivableAccountId,UUID managementAccountId,UUID insuranceAccountId,String policyReference,String agreementReference,
        String insurer,String insurerVerification,boolean allowCustom,BigDecimal customManagementLimit) {}
    public record Term(UUID id,String kind,String basis,BigDecimal basisValue,BigDecimal amount,Timing timing,
        UUID receivableAccountId,UUID liabilityAccountId,String evidence,String provider,String verification) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Plan(UUID policyId,String policyReference,String agreementReference,List<Term> terms,boolean supported,String error) {
        public BigDecimal total(){return terms.stream().map(Term::amount).reduce(new BigDecimal("0.00"),BigDecimal::add);}
        public BigDecimal managementTotal(){return terms.stream().filter(t->!t.kind().equals("INSURANCE")).map(Term::amount).reduce(new BigDecimal("0.00"),BigDecimal::add);}
        public BigDecimal getManagementTotal(){return managementTotal();}
        public BigDecimal deductions(){return terms.stream().filter(t->t.timing()==Timing.DEDUCT).map(Term::amount).reduce(new BigDecimal("0.00"),BigDecimal::add);}
        public BigDecimal separate(){return total().subtract(deductions());}
        public BigDecimal getTotal(){return total();} public BigDecimal getDeductions(){return deductions();} public BigDecimal getSeparate(){return separate();}
    }
    public record Balance(UUID loanId,long sequence,BigDecimal assessed,BigDecimal collected,BigDecimal outstanding,LocalDate lastDate) {}
    public record Movement(UUID id,UUID loanId,UUID termId,String kind,String chargeKind,BigDecimal amount,LocalDate date,UUID journalId,
        UUID postingId,UUID reversesId,UUID actorId,String evidence,String reason) {}
    public record Summary(Plan plan,Balance balance,List<Movement> history,int page,boolean hasNext,UUID assessmentJournal,UUID assessmentActor,String loanNumber,String institution,String branch) {}
    @Data public static class AssessmentForm {
        private UUID requestKey=UUID.randomUUID(); private LocalDate date; private String evidence="",reason="";
    }
}
