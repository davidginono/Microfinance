package com.sacco.mvp.accounting.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.Category;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.Classification;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.MovementSelection;

public final class VoucherDtos {
    private VoucherDtos() {}
    public static final int MAX_ACTIVITIES = 50;
    public enum Type { RECEIPT, PAYMENT, JOURNAL }
    @Data public static class Form {
        private UUID requestKey=UUID.randomUUID();
        private LocalDate effectiveDate;
        private String party="",reference="",description="",evidence="";
        private UUID moneyAccountId;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
        private boolean pettyCash;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private UUID activityId;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
        private List<UUID> activityIds = new ArrayList<>();

        public List<UUID> selectedActivityIds() {
            if (activityIds != null && !activityIds.isEmpty()) return activityIds;
            return activityId == null ? List.of() : List.of(activityId);
        }
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
        private boolean equity;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private Category equityMovement;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private MovementSelection equityRow;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
        private String equityReason="";
        private String receiptPurpose="OTHER";
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private String loanPayer;
        private UUID clientId,interestAccountId;
        private List<RowForm> rows=new ArrayList<>(List.of(new RowForm()));

        @com.fasterxml.jackson.annotation.JsonIgnore
        public String getEquityChoice(){
            return equityRow!=null?equityRow.value():equityMovement==null?"":equityMovement.name();
        }
        @com.fasterxml.jackson.annotation.JsonIgnore
        public void setEquityChoice(String choice){
            if(choice==null||choice.isEmpty())return;
            if(choice.indexOf(':')>=0)equityRow=MovementSelection.parse(choice);
            else equityMovement=Category.valueOf(choice);
        }
    }
    @Data public static class RowForm {
        private UUID transactionId,templateKey,debitAccountId,creditAccountId;
        private UUID loanId;
        private String component="TOTAL",description="";
        @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
        private BigDecimal amount;
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
        private List<EntryForm> entries=new ArrayList<>();
    }
    @Data public static class EntryForm {
        private String side;
        private UUID accountId;
        @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
        private BigDecimal amount;
    }
    public record Entry(String side,Account account,BigDecimal amount) {
        public Entry reversed(){return new Entry("DEBIT".equals(side)?"CREDIT":"DEBIT",account,amount);}
    }
    public static List<Category> equityTypes(Type type){return Arrays.stream(Category.values()).filter(c->c!=Category.UNCLASSIFIED && (type==Type.JOURNAL || c!=Category.TRANSFER && c!=Category.EARNINGS_TRANSFER)).toList();}
    public record Account(UUID id,String code,String name,String purpose,boolean moneyEligible,String type) {
        public Account(UUID id,String code,String name,String purpose,boolean moneyEligible){this(id,code,name,purpose,moneyEligible,null);}
        public Account(UUID id,String code,String name,String purpose){this(id,code,name,purpose,Set.of("CASH","BANK","MOBILE_MONEY").contains(purpose));}
    }
    public record Client(UUID id,String number,String name) {}
    public record Activity(UUID id,String name,String nameSw) {}
    public record NumberReservation(String number) {}
    public record Mapping(UUID transactionId,UUID templateKey,String transactionName,String component,Account debit,Account credit,
                          Activity activity) {}
    public record ReviewLines(Map<String,Account> accounts,Map<Integer,Mapping> mappings) {}
    public record Transaction(UUID id,int lineNo,UUID transactionId,UUID templateKey,String transactionName,String component,
                              String description,BigDecimal amount,Account debit,Account credit,List<Entry> entries) {
        public Transaction(UUID id,int lineNo,UUID transactionId,UUID templateKey,String transactionName,String component,
                           String description,BigDecimal amount,Account debit,Account credit) {
            this(id,lineNo,transactionId,templateKey,transactionName,component,description,amount,debit,credit,List.of());
        }
        public List<Entry> postings(){return entries==null || entries.isEmpty()?List.of(new Entry("DEBIT",debit,amount),new Entry("CREDIT",credit,amount)):entries;}
        public boolean split(){return entries!=null && !entries.isEmpty();}
    }
    public record Voucher(UUID id,String number,Type type,LocalDate effectiveDate,String party,String reference,String description,
                          String evidence,UUID moneyAccountId,BigDecimal total,int transactionCount,UUID journalId,UUID postedBy,
                          String postedByName,OffsetDateTime postedAt,UUID reversesId,UUID reversedBy,String institutionName,
                          String branchName,List<Transaction> transactions,UUID clientId,String clientNumber,Category equityMovement) {
        public Voucher(UUID id,String number,Type type,LocalDate effectiveDate,String party,String reference,String description,
                       String evidence,UUID moneyAccountId,BigDecimal total,int transactionCount,UUID journalId,UUID postedBy,
                       String postedByName,OffsetDateTime postedAt,UUID reversesId,UUID reversedBy,String institutionName,
                       String branchName,List<Transaction> transactions,UUID clientId,String clientNumber) {
            this(id,number,type,effectiveDate,party,reference,description,evidence,moneyAccountId,total,transactionCount,journalId,postedBy,postedByName,postedAt,reversesId,reversedBy,institutionName,branchName,transactions,clientId,clientNumber,null);
        }
        public Voucher(UUID id,String number,Type type,LocalDate effectiveDate,String party,String reference,String description,
                       String evidence,UUID moneyAccountId,BigDecimal total,int transactionCount,UUID journalId,UUID postedBy,
                       String postedByName,OffsetDateTime postedAt,UUID reversesId,UUID reversedBy,String institutionName,
                       String branchName,List<Transaction> transactions) {
            this(id,number,type,effectiveDate,party,reference,description,evidence,moneyAccountId,total,transactionCount,journalId,postedBy,postedByName,postedAt,reversesId,reversedBy,institutionName,branchName,transactions,null,null);
        }
        public String status(){return reversesId!=null?"REVERSAL":reversedBy!=null?"REVERSED":"POSTED";}
    }
    public record Filter(String search,LocalDate from,LocalDate through,String status,String sort,int page,int size) {
        public Filter(String search,LocalDate from,LocalDate through,String status,String sort,int page) {this(search,from,through,status,sort,page,25);}
    }
    public record Page(List<Voucher> rows,int page,boolean hasNext) {}
    public record Preview(List<Transaction> rows,BigDecimal total,Classification equityClassification) {
        public Preview(List<Transaction> rows,BigDecimal total){this(rows,total,null);}
    }
    public record LoanReceipt(Voucher header,String clientNumber,List<LoanReceiptRow> rows,GroupReceipt group,String payer) {
        public boolean hasSharedBenefits(){return rows.stream().anyMatch(r->!r.sharedBenefits().isEmpty());}
        public LoanReceipt(Voucher header,String clientNumber,List<LoanReceiptRow> rows){this(header,clientNumber,rows,null,"");}
        public LoanReceipt(Voucher header,String clientNumber,List<LoanReceiptRow> rows,GroupReceipt group){this(header,clientNumber,rows,group,"");}
    }
    public record GroupReceipt(UUID cycleId,String code,String name,List<ReceiptContribution> contributions,String reversesNumber,String reversedByNumber) {
        public GroupReceipt(UUID cycleId,String code,String name,List<ReceiptContribution> contributions){this(cycleId,code,name,contributions,"","");}
    }
    public record ReceiptContribution(UUID id,int lineNo,UUID loanId,String loanNumber,UUID payerId,String payerNumber,String payer,
        UUID beneficiaryId,String beneficiaryNumber,String beneficiary,String description,BigDecimal amount,BigDecimal principal,
        BigDecimal interest,BigDecimal charges,UUID reversesId,Integer sourceLineNo) {
        public ReceiptContribution(UUID id,int lineNo,UUID loanId,String loanNumber,UUID payerId,String payerNumber,String payer,UUID beneficiaryId,String beneficiaryNumber,String beneficiary,String description,BigDecimal amount,BigDecimal principal,BigDecimal interest,BigDecimal charges,UUID reversesId){this(id,lineNo,loanId,loanNumber,payerId,payerNumber,payer,beneficiaryId,beneficiaryNumber,beneficiary,description,amount,principal,interest,charges,reversesId,null);}
    }
    public record LoanReceiptRow(int lineNo,UUID postingId,UUID loanId,String loanNumber,String description,
                                 BigDecimal amount,BigDecimal principal,BigDecimal interest,BigDecimal principalBalance,
                                 String moneyAccount,String principalAccount,String interestAccount,String reference,String chargeAccount,List<SharedReceiptBenefit> sharedBenefits,BigDecimal feesRecovered) {
        public LoanReceiptRow(int lineNo,UUID postingId,UUID loanId,String loanNumber,String description,BigDecimal amount,BigDecimal principal,
                              BigDecimal interest,BigDecimal principalBalance,String moneyAccount,String principalAccount,String interestAccount,
                              String reference,String chargeAccount,List<SharedReceiptBenefit> sharedBenefits){
            this(lineNo,postingId,loanId,loanNumber,description,amount,principal,interest,principalBalance,moneyAccount,principalAccount,interestAccount,reference,chargeAccount,sharedBenefits,BigDecimal.ZERO);
        }
        public LoanReceiptRow withRecoveredFees(BigDecimal value){
            return new LoanReceiptRow(lineNo,postingId,loanId,loanNumber,description,amount,principal,interest,principalBalance,moneyAccount,principalAccount,interestAccount,reference,chargeAccount,sharedBenefits,value);
        }
        public LoanReceiptRow(int line,UUID posting,UUID loan,String number,String description,BigDecimal amount,BigDecimal principal,BigDecimal interest,BigDecimal balance,String money,String principalAccount,String interestAccount,String reference,String chargeAccount){this(line,posting,loan,number,description,amount,principal,interest,balance,money,principalAccount,interestAccount,reference,chargeAccount,List.of());}
        public LoanReceiptRow withSharedBenefits(List<SharedReceiptBenefit> benefits){return new LoanReceiptRow(lineNo,postingId,loanId,loanNumber,description,amount,principal,interest,principalBalance,moneyAccount,principalAccount,interestAccount,reference,chargeAccount,List.copyOf(benefits),feesRecovered);}
        public LoanReceiptRow(int line,UUID posting,UUID loan,String number,String description,BigDecimal amount,BigDecimal principal,BigDecimal interest,BigDecimal balance,String money,String principalAccount,String interestAccount,String reference){this(line,posting,loan,number,description,amount,principal,interest,balance,money,principalAccount,interestAccount,reference,"");}
        public BigDecimal charges(){return amount.subtract(principal).subtract(interest);}
        public BigDecimal getCharges(){return charges();}
    }
    public record SharedReceiptBenefit(UUID clientId,String clientNumber,String clientName,String payer,BigDecimal principal,BigDecimal interest,BigDecimal charges,BigDecimal excludedInterest) {
        public BigDecimal amount(){return principal.add(interest).add(charges);}
    }
    public static class Invalid extends IllegalArgumentException {
        private final String field;
        public Invalid(String field,String code){super("voucher.error."+code);this.field=field;}
        public String field(){return field;}
    }
}
