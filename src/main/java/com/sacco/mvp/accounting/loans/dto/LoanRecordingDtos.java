package com.sacco.mvp.accounting.loans.dto;

import com.sacco.mvp.domain.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;

public final class LoanRecordingDtos {
    private LoanRecordingDtos() {}
    public record GroupStep(Position group,int approvals) {}
    @Data public static class GroupRuleForm {
        private UUID productId;
        private int expectedRevision;
        private List<Position> groups=new ArrayList<>();
        private List<Integer> approvals=new ArrayList<>();
    }
    public record GroupRule(UUID id,UUID product,int revision,String steps) {}
    public record GroupLoan(UUID id,UUID product,UUID client,UUID recorder,String status,String termsHash,String number,String clientName,BigDecimal amount,boolean relatedActor) {}
    public record GroupRequest(UUID id,UUID loan,UUID rule,UUID maker,int round,String termsHash,String previousStatus) {}
    public record GroupDecision(UUID id,int step,UUID actor,String name,String decision,String reason,String evidence,OffsetDateTime at) {}
    public record ReviewDocuments(boolean required,boolean guarantorConsent,List<com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.Requirement> documents) {}
    public record GroupReview(GroupRequest request,List<GroupStep> steps,List<GroupDecision> decisions,int step,boolean canApprove,boolean canReject,boolean canSubmit) {}
    public record ChargeField(String code,String percentage,String amount) {}
    public record ChargeQuote(com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Plan plan,List<ChargeField> fields) {}
    @Data public static class RecordForm {
        private String loanFor="INDIVIDUAL";
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private String groupAgreementChecksum;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private String groupRequestHash;
        private com.sacco.mvp.grouplending.dto.SimpleGroupLoanDtos.ApplicationForm groupApplication=new com.sacco.mvp.grouplending.dto.SimpleGroupLoanDtos.ApplicationForm();
        /** Internal group-origination evidence; deliberately excluded from every browser binder. */
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private com.sacco.mvp.grouplending.dto.GroupDisclosureDtos.Snapshot groupDisclosure;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private ApplicationDetails applicationDetails;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private UUID applicationId;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
        private int expectedVersion;
        private UUID requestKey = UUID.randomUUID();
        private UUID clientId;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private UUID topUpSourceLoanId;
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
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) private BigDecimal customApplicationCharge;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) private BigDecimal customProcessingCharge;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) private BigDecimal customProcessingPercent;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) private BigDecimal customInsurancePercent;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) private String customProcessingBasis;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) private String customInsuranceBasis;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) private BigDecimal insurancePremium;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) private String insuranceEvidence;
    }
    @Data public static class ApplicationDetails {
        /** Server-only source values for validating each joint borrower against the product schema. */
        @com.fasterxml.jackson.annotation.JsonIgnore
        private List<Map<String,String>> groupClientValues=new ArrayList<>();
        /** Frozen source revision; never accepted from the recording form. */
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.LoanProfile clientProfile;
        private UUID loanOfficerId;
        private String loanOfficerLabel = "";
        private Map<String,String> fieldLabels = new LinkedHashMap<>();
        private String useOfFunds = "";
        private List<com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.Guarantor> guarantors = new ArrayList<>();
        private List<com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.Collateral> collateral = new ArrayList<>();
        private Map<String,String> fields = new LinkedHashMap<>();
        private List<String> documentNames = new ArrayList<>(), documentTypes = new ArrayList<>(), requiredAttachmentIds = new ArrayList<>();
        private boolean declarationAccepted;
    }
    @Data public static class PostForm {
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
        private List<UUID> deductChargeIds=new ArrayList<>();
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private BigDecimal actualCashPaid;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private UUID feeReceivingAccountId;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
        private String feePaymentReference="";
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) private String sharedPayer;
        private UUID requestKey = UUID.randomUUID();
        private UUID loanId;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate firstPaymentDate;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate effectiveDate;
        private BigDecimal amount;
        private UUID moneyAccountId;
        private UUID principalAccountId;
        private UUID interestAccountId;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) private Long settlementSequence;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) private BigDecimal settlementPrincipal;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) private BigDecimal settlementInterest;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) private BigDecimal settlementCharges;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) private Long settlementChargeSequence;
        private String reference = "";
        private String evidence = "";
        private String notes = "";
    }
    public record FundingQuote(BigDecimal cash,BigDecimal deductions,BigDecimal separate,TopUpQuote settlement,
                               BigDecimal recoveryFees,BigDecimal reducedCash) {
        public FundingQuote(BigDecimal cash,BigDecimal deductions,BigDecimal separate,TopUpQuote settlement){
            this(cash,deductions,separate,settlement,BigDecimal.ZERO,null);
        }
    }
    public record Choice(UUID id, String label, String purpose, String tenureUnit,
        String minimumAmount, String maximumAmount, Integer minimumTenure, Integer maximumTenure) {
        public Choice(UUID id,String label,String purpose,String tenureUnit){this(id,label,purpose,tenureUnit,null,null,null,null);}
        public Choice(UUID id,String label,String purpose){this(id,label,purpose,null);}
    }
    public record ReceiptEntryLoan(UUID id, UUID clientId, String number, String client, String clientNumber,
                                   String product, LocalDate disbursed, LocalDate instalmentDate, BigDecimal instalmentAmount) {}
    public record ReceiptLoan(UUID id,UUID clientId,String number,String client,String clientNumber,BigDecimal duePrincipal,BigDecimal dueInterest,BigDecimal outstandingPrincipal,LocalDate disbursed,LocalDate lastPayment,String status,BigDecimal charges,String product,UUID principalAccountId,String principalAccount,UUID interestAccountId,String interestAccount) {
        public ReceiptLoan(UUID id,UUID client,String number,String name,String clientNo,BigDecimal principal,BigDecimal interest,BigDecimal balance,LocalDate disbursed,LocalDate last,String status){this(id,client,number,name,clientNo,principal,interest,balance,disbursed,last,status,new BigDecimal("0.00"),"",null,null,null,null);}
    }
    public record TopUpQuote(UUID sourceId,String sourceNumber,UUID clientId,String client,LocalDate date,
        BigDecimal principal,BigDecimal interest,BigDecimal excludedInterest,long sequence,BigDecimal charges,long chargeSequence) {
        public TopUpQuote(UUID id,String number,UUID client,String name,LocalDate date,BigDecimal principal,BigDecimal interest,BigDecimal excluded,long sequence){this(id,number,client,name,date,principal,interest,excluded,sequence,new BigDecimal("0.00"),0);}
        public BigDecimal total(){return principal.add(interest).add(charges);}
        public BigDecimal getTotal(){return total();}
    }
    public record TopUpPosting(UUID id,UUID loanId,UUID sourceId,String loanNumber,String sourceNumber,
        LocalDate date,BigDecimal principal,BigDecimal interest,BigDecimal excludedInterest,BigDecimal cash,
        UUID journalId,UUID transactionId,UUID reversesId,UUID reversedBy,UUID actorId,String evidence,String reason,BigDecimal sourceCharges,BigDecimal newDeductions) {
        public TopUpPosting(UUID id,UUID loan,UUID source,String number,String sourceNumber,LocalDate date,BigDecimal principal,BigDecimal interest,BigDecimal excluded,BigDecimal cash,UUID journal,UUID transaction,UUID reverses,UUID reversed,UUID actor,String evidence,String reason){this(id,loan,source,number,sourceNumber,date,principal,interest,excluded,cash,journal,transaction,reverses,reversed,actor,evidence,reason,new BigDecimal("0.00"),new BigDecimal("0.00"));}
    }
    public record Filter(String search, LocalDate from, LocalDate through, String status, String sort, int page, UUID clientId, UUID productId) {
        public Filter(String search, LocalDate from, LocalDate through, String status, String sort, int page, UUID clientId) {
            this(search, from, through, status, sort, page, clientId, null);
        }
    }
    public record Page<T>(List<T> rows, int page, boolean hasNext) {}
    public record LoanRow(UUID id, String number, UUID clientId, String client, String clientNumber,
        String product, LocalDate applicationDate, BigDecimal requestedPrincipal, BigDecimal principal,
        String status, BigDecimal outstandingPrincipal, BigDecimal dueInterest, BigDecimal arrears,
        BigDecimal futureInterest, BigDecimal principalPaid, BigDecimal interestPaid, boolean topUpEligible,BigDecimal charges,boolean cancelEligible,String loanFor) {
        public LoanRow(UUID id,String number,UUID clientId,String client,String clientNumber,String product,LocalDate applicationDate,BigDecimal requestedPrincipal,BigDecimal principal,String status,BigDecimal outstandingPrincipal,BigDecimal dueInterest,BigDecimal arrears,BigDecimal futureInterest,BigDecimal principalPaid,BigDecimal interestPaid,boolean topUpEligible,BigDecimal charges,boolean cancelEligible){this(id,number,clientId,client,clientNumber,product,applicationDate,requestedPrincipal,principal,status,outstandingPrincipal,dueInterest,arrears,futureInterest,principalPaid,interestPaid,topUpEligible,charges,cancelEligible,null);}
        public LoanRow(UUID id,String number,UUID clientId,String client,String clientNumber,String product,LocalDate applicationDate,BigDecimal requestedPrincipal,BigDecimal principal,String status,BigDecimal outstandingPrincipal,BigDecimal dueInterest,BigDecimal arrears,BigDecimal futureInterest,BigDecimal principalPaid,BigDecimal interestPaid,boolean topUpEligible,BigDecimal charges){this(id,number,clientId,client,clientNumber,product,applicationDate,requestedPrincipal,principal,status,outstandingPrincipal,dueInterest,arrears,futureInterest,principalPaid,interestPaid,topUpEligible,charges,false);}
        public LoanRow(UUID id,String number,UUID clientId,String client,String clientNumber,String product,LocalDate applicationDate,BigDecimal requestedPrincipal,BigDecimal principal,String status,BigDecimal outstandingPrincipal,BigDecimal dueInterest,BigDecimal arrears,BigDecimal futureInterest,BigDecimal principalPaid,BigDecimal interestPaid,boolean topUpEligible){this(id,number,clientId,client,clientNumber,product,applicationDate,requestedPrincipal,principal,status,outstandingPrincipal,dueInterest,arrears,futureInterest,principalPaid,interestPaid,topUpEligible,BigDecimal.ZERO);}
        public LoanRow(UUID id,String number,UUID clientId,String client,String clientNumber,String product,LocalDate applicationDate,
            BigDecimal requestedPrincipal,BigDecimal principal,String status,BigDecimal outstandingPrincipal,BigDecimal dueInterest,
            BigDecimal arrears,BigDecimal futureInterest,BigDecimal principalPaid,BigDecimal interestPaid) {
            this(id,number,clientId,client,clientNumber,product,applicationDate,requestedPrincipal,principal,status,outstandingPrincipal,dueInterest,arrears,futureInterest,principalPaid,interestPaid,false);
        }
    }
    public record JournalLine(String account,BigDecimal debit,BigDecimal credit) {}
    public record Posting(UUID id, UUID loanId, String loanNumber, String client, String clientNumber,
        String kind, LocalDate date, String reference, BigDecimal amount, BigDecimal principal, BigDecimal interest,
        String moneyAccount, String principalAccount, String interestAccount, String evidence, String notes,
        UUID actorId, String actor, OffsetDateTime recordedAt, UUID journalId, UUID transactionId,
        UUID reversesId, UUID reversedBy, BigDecimal principalBalance, String product, String loanFor) {
        public Posting(UUID id,UUID loanId,String loanNumber,String client,String clientNumber,String kind,LocalDate date,String reference,BigDecimal amount,BigDecimal principal,BigDecimal interest,String moneyAccount,String principalAccount,String interestAccount,String evidence,String notes,UUID actorId,String actor,OffsetDateTime recordedAt,UUID journalId,UUID transactionId,UUID reversesId,UUID reversedBy,BigDecimal principalBalance,String product){this(id,loanId,loanNumber,client,clientNumber,kind,date,reference,amount,principal,interest,moneyAccount,principalAccount,interestAccount,evidence,notes,actorId,actor,recordedAt,journalId,transactionId,reversesId,reversedBy,principalBalance,product,null);}
        public BigDecimal charges(){return Set.of("PAYMENT","REVERSAL").contains(kind)?amount.subtract(principal).subtract(interest):new BigDecimal("0.00");}
        public BigDecimal getCharges(){return charges();}
    }
    public record StatementMovement(Posting posting, BigDecimal outstandingInterest) {}
    public record Detail(LoanRow loan, String institution, String branch, String recordedBy, OffsetDateTime recordedAt,
        String purpose, String business, BigDecimal income, BigDecimal expenses, BigDecimal otherDebt,
        String informationSource, String guarantors, String collateral, String evidence, String notes,
        BigDecimal rate, String method, String frequency, int months, LocalDate firstPaymentDate,
        BigDecimal installment, BigDecimal contractualInterest, List<ScheduleRow> schedule,
        String tenureUnit,int tenureValue,String interestBasis,String loanMode,String interestEntry) {
        public Detail(LoanRow loan,String institution,String branch,String recordedBy,OffsetDateTime recordedAt,String purpose,String business,BigDecimal income,BigDecimal expenses,BigDecimal otherDebt,String informationSource,String guarantors,String collateral,String evidence,String notes,BigDecimal rate,String method,String frequency,int months,LocalDate firstPaymentDate,BigDecimal installment,BigDecimal contractualInterest,List<ScheduleRow> schedule){this(loan,institution,branch,recordedBy,recordedAt,purpose,business,income,expenses,otherDebt,informationSource,guarantors,collateral,evidence,notes,rate,method,frequency,months,firstPaymentDate,installment,contractualInterest,schedule,"MONTHS",months,"ANNUAL","CONFIGURED","PERCENTAGE");}
    }
    public record ScheduleRow(int number, LocalDate date, BigDecimal principal, BigDecimal interest, BigDecimal amount,
                              BigDecimal beginningBalance, BigDecimal endingBalance) {
        public ScheduleRow(int number,LocalDate date,BigDecimal principal,BigDecimal interest,BigDecimal amount){this(number,date,principal,interest,amount,null,null);}
    }
    public record CollectionAmount(UUID loanId,UUID clientId,BigDecimal amount) {}
    public record AllocationPreview(BigDecimal principal,BigDecimal interest,BigDecimal principalBalance,BigDecimal charges,BigDecimal feesRecovered) {
        public AllocationPreview(BigDecimal principal,BigDecimal interest,BigDecimal balance,BigDecimal charges){this(principal,interest,balance,charges,new BigDecimal("0.00"));}
        public AllocationPreview(BigDecimal principal,BigDecimal interest,BigDecimal balance){this(principal,interest,balance,new BigDecimal("0.00"));}
    }
    public record StatementClient(String number, String name, String kind) {
        public String titleName() {
            if (!"CLIENT".equals(kind) || name == null || name.isBlank()) return name;
            String first = name.strip().split("\\s+", 2)[0];
            if (!first.equals(first.toUpperCase(Locale.ROOT))) return first;
            int firstLetter = first.offsetByCodePoints(0, 1);
            return first.substring(0, firstLetter) + first.substring(firstLetter).toLowerCase(Locale.ROOT);
        }
    }
    public record Statement(StatementClient client, BigDecimal principal, BigDecimal interest, BigDecimal arrears,
        BigDecimal futureInterest, long loans, Page<LoanRow> page, BigDecimal charges) {}
    public record StatementBalance(LoanRow loan, boolean hasLedger) {}
    public record BalanceExport(StatementClient client, List<StatementBalance> loans, LocalDate asOf) {}
    public record MovementFilter(LocalDate from, LocalDate through, UUID loanId, UUID productId) {}
    public record StatementMovements(Page<Posting> page, Choice loan, Choice product) {}
    public record Totals(long count, BigDecimal amount, BigDecimal principal, BigDecimal interest) {}
    public record StatementTerms(UUID loanId, BigDecimal rate, String frequency, int months, int installments,
        LocalDate firstPaymentDate, BigDecimal installment, String institution, String branch, String rateBasis, String tenureUnit, int tenureValue, LocalDate maturityDate,String chargeSnapshot,BigDecimal fixedInterest) {
        public StatementTerms(UUID loanId,BigDecimal rate,String frequency,int months,int installments,LocalDate firstPaymentDate,BigDecimal installment,String institution,String branch,String rateBasis,String tenureUnit,int tenureValue,LocalDate maturityDate){this(loanId,rate,frequency,months,installments,firstPaymentDate,installment,institution,branch,rateBasis,tenureUnit,tenureValue,maturityDate,"",null);}
        public StatementTerms(UUID loanId,BigDecimal rate,String frequency,int months,int installments,LocalDate firstPaymentDate,BigDecimal installment,String institution,String branch){this(loanId,rate,frequency,months,installments,firstPaymentDate,installment,institution,branch,"ANNUAL","MONTHS",months,null);}
        public LocalDate lastPaymentDate() {
            if(maturityDate!=null)return maturityDate;
            if (firstPaymentDate == null || installments < 1 || !("WEEKLY".equals(frequency) || "MONTHLY".equals(frequency))) return null;
            return "WEEKLY".equals(frequency) ? firstPaymentDate.plusWeeks(installments - 1L)
                : firstPaymentDate.plusMonths(installments - 1L);
        }
    }

    public record BranchLoan(UUID id,String number,String client,String product,String status,BigDecimal principal,LocalDate disbursed) {}
    public record ReviewedLoan(UUID id, String number, String client, String status, java.time.LocalDate disbursed) {}
    public record ReviewArchive(java.util.List<ReviewedLoan> rows, long count, int page, boolean hasNext) {}
}
