package com.sacco.mvp.accounting.batch.dto;

import com.sacco.mvp.accounting.dto.VoucherDtos.Form;
import com.sacco.mvp.accounting.repayment.dto.RepaymentPolicyDtos.AllocationOrder;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import static com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ReceiptBatchDtos {
    private ReceiptBatchDtos() {}
    public record Batch(UUID id,String institution,String branch,UUID owner,String title,int version,String state,
                        boolean cancelRequested,UUID workerToken,OffsetDateTime createdAt) {}
    public record Item(UUID id,int lineNo,int version,String state,Form form,String clientLabel,Map<UUID,String> labels,
                       Plan preview,String failureKey,UUID receiptId,@JsonFormat(shape=STRING) BigDecimal total,String failureMessage) {
        public Item withFailureMessage(String message){return new Item(id,lineNo,version,state,form,clientLabel,labels,preview,failureKey,receiptId,total,message);}
    }
    public record SaveItem(int expectedVersion,Form form) {}
    public record LinkAccounts(int expectedVersion) {}
    public record Plan(UUID policyId,int policyVersion,AllocationOrder allocationOrder,String moneyAccount,
                       String interestAccount,List<PlanLine> lines,@JsonFormat(shape=STRING) BigDecimal total,
                       @JsonFormat(shape=STRING) BigDecimal principal,@JsonFormat(shape=STRING) BigDecimal interest) {}
    public record PlanLine(UUID loanId,String loanNumber,@JsonFormat(shape=STRING) BigDecimal amount,
                           @JsonFormat(shape=STRING) BigDecimal outstandingPrincipal,@JsonFormat(shape=STRING) BigDecimal duePrincipal,
                           @JsonFormat(shape=STRING) BigDecimal dueInterest,@JsonFormat(shape=STRING) BigDecimal principal,
                           @JsonFormat(shape=STRING) BigDecimal interest,@JsonFormat(shape=STRING) BigDecimal principalBalance,String principalAccount,UUID principalAccountId,UUID interestAccountId,String interestAccount,String product,
                           LocalDate paymentDate,List<com.sacco.mvp.service.LoanRepaymentLedgerService.PreviewAllocation> servicingAllocations) {
        public PlanLine(UUID id,String number,BigDecimal amount,BigDecimal outstanding,BigDecimal duePrincipal,BigDecimal dueInterest,BigDecimal principal,BigDecimal interest,BigDecimal balance,String account,UUID principalId,UUID interestId,String interestAccount,String product){this(id,number,amount,outstanding,duePrincipal,dueInterest,principal,interest,balance,account,principalId,interestId,interestAccount,product,null,null);}
        public PlanLine(UUID id,String number,BigDecimal amount,BigDecimal outstanding,BigDecimal duePrincipal,BigDecimal dueInterest,BigDecimal principal,BigDecimal interest,BigDecimal balance,String account){this(id,number,amount,outstanding,duePrincipal,dueInterest,principal,interest,balance,account,null,null,null,null);}
    }
    public record Summary(Batch batch,int receipts,int posted,int ready,int failed,@JsonFormat(shape=STRING) BigDecimal total,
                          @JsonFormat(shape=STRING) BigDecimal postedTotal) {
        @JsonProperty @JsonFormat(shape=STRING) public BigDecimal remainingTotal(){return total.subtract(postedTotal);}
    }
    public record Balances(UUID id,String number,@JsonFormat(shape=STRING) BigDecimal outstandingPrincipal,
                           @JsonFormat(shape=STRING) BigDecimal duePrincipal,@JsonFormat(shape=STRING) BigDecimal dueInterest) {}
    public record Page<T>(List<T> rows,int page,boolean hasNext) {}
}
