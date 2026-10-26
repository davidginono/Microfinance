package com.sacco.mvp.grouplending.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class GroupCycleDtos {
    private GroupCycleDtos() {}
    public enum Model { INDIVIDUAL, SHARED, ORGANIZATION }
    public enum SharedLiability { PROPORTIONAL, JOINT_AND_SEVERAL }
    @Data public static class PolicyForm {
        private UUID requestKey=UUID.randomUUID();
        private UUID productId;
        private Model model;
        private Integer minMembers;
        private Integer maxMembers;
        private BigDecimal maxPrincipal;
        private BigDecimal maxGuarantee;
        private Integer maxActiveCycles;
        private boolean ownLoanGuarantors;
        private boolean enabled;
        private String policyReference="";
        private String agreementReference="";
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private SharedLiability sharedLiability;
    }
    public record Policy(UUID id,UUID productId,String product,Model model,int minMembers,int maxMembers,
        BigDecimal maxPrincipal,BigDecimal maxGuarantee,int maxActiveCycles,boolean ownLoanGuarantors,
        boolean enabled,String policyReference,String agreementReference,OffsetDateTime createdAt,long revision) {}
    @Data public static class CycleForm {
        private UUID requestKey=UUID.randomUUID();
        private UUID policyId;
        private int expectedVersion;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate applicationDate;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate firstPaymentDate;
        private String tenureUnit="MONTHS";
        private Integer tenureValue;
        private String purpose="";
        private List<ParticipantForm> participants=new ArrayList<>();
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private OrganizationForm organization;
    }
    @Data public static class OrganizationForm {
        private UUID authorityId;
        private UUID representativeId;
        private BigDecimal principal,income,expenses,otherDebt,collateralValue,insurancePremium;
        private String business="",assessmentEvidence="",collateral="",insuranceEvidence="";
    }
    public record OrganizationLoan(UUID loanId,String number,String status,UUID authorityId,UUID representativeId,
        String representative,BigDecimal principal,BigDecimal monthlyPayment) {}
    @Data public static class ParticipantForm {
        private UUID clientId;
        private BigDecimal principal;
        private BigDecimal income;
        private BigDecimal expenses;
        private BigDecimal otherDebt;
        private BigDecimal guaranteeCapacity;
        private String assessmentEvidence="";
        private String capacityEvidence="";
        private String collateral="";
        private BigDecimal collateralValue;
        private BigDecimal insurancePremium;
        private String insuranceEvidence="";
    }
    public record Cycle(UUID id,UUID groupId,String groupName,UUID policyId,UUID productId,Model model,
        String state,int version,CycleForm form,OffsetDateTime submittedAt) {}
    public record TerminationNotice(String kind,OffsetDateTime recordedAt) {}
    public record Termination(String kind,UUID actorId,String actor,String reason,OffsetDateTime recordedAt) {}
    @Data public static class TerminationForm {
        private int expectedVersion;
        private String reason="";
    }
    public record CycleSummary(UUID id,LocalDate date,String purpose,Model model,String state,OffsetDateTime submittedAt) {}
    public record ClientCycle(UUID id,String group,OffsetDateTime submittedAt,boolean accepted,String state,Model model,boolean payoutOnly) {
        public ClientCycle(UUID id,String group,OffsetDateTime submittedAt,boolean accepted,String state,Model model){this(id,group,submittedAt,accepted,state,model,false);}
        public ClientCycle(UUID id,String group,OffsetDateTime submittedAt,boolean accepted,String state){this(id,group,submittedAt,accepted,state,Model.INDIVIDUAL,false);}
    }
    public record Participant(UUID id,UUID clientId,String clientNumber,String name,BigDecimal principal,
        BigDecimal monthlyPayment,BigDecimal guaranteeCapacity,UUID loanId,String loanNumber,String loanStatus,boolean consented) {}
    public record Guarantee(UUID id,UUID guarantorId,String guarantor,UUID borrowerId,String borrower,String loanNumber,
        BigDecimal cap,String agreementReference) {}
    public record ProductRules(int guarantors,BigDecimal affordabilityRatio,boolean collateralRequired,BigDecimal collateralRatio,
        boolean activeLoanAllowed,int maximumActiveLoans) {}
    public record SharedShare(UUID clientId,BigDecimal principal,BigDecimal interest,BigDecimal charges,
        BigDecimal deductions,BigDecimal net,BigDecimal monthlyPayment) {}
    public record SharedQuote(BigDecimal principal,BigDecimal interest,BigDecimal charges,BigDecimal deductions,
        BigDecimal net,BigDecimal totalPayable,BigDecimal monthlyPayment,String frequency,List<SharedShare> shares,
        List<com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ScheduleRow> schedule,List<SharedDue> dueShares) {}
    public record SharedDue(UUID clientId,int number,LocalDate date,BigDecimal principal,BigDecimal interest) {}
    public record SharedFacility(UUID loanId,String number,String status,SharedLiability liability,SharedQuote quote) {}
    public record SharedObligation(SharedLiability liability,BigDecimal cap,SharedShare allocation) {}
    public record Exposure(int activeCycles,int activeLoans,int riskLoans,int guaranteeCount,BigDecimal monthlyDebt,BigDecimal reserved,int missingHistory,int pendingApplications) {
        public Exposure(int activeCycles,int activeLoans,int riskLoans,int guaranteeCount,BigDecimal monthlyDebt,BigDecimal reserved,int missingHistory){this(activeCycles,activeLoans,riskLoans,guaranteeCount,monthlyDebt,reserved,missingHistory,0);}
    }
    public record Terms(BigDecimal principal,BigDecimal annualRate,BigDecimal contractualInterest,BigDecimal instalment,
        String frequency,String tenureUnit,int tenureValue,LocalDate firstPaymentDate,String purpose,
        BigDecimal maximumInstalment,BigDecimal chargeTotal,BigDecimal deductions,BigDecimal netCash,BigDecimal totalPayable,
        com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Plan charges) {}
}
