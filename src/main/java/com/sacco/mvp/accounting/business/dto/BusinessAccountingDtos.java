package com.sacco.mvp.accounting.business.dto;

import com.sacco.mvp.accounting.policy.PostingEvent;
import com.sacco.mvp.domain.RepaymentFrequency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

public final class BusinessAccountingDtos {
    private BusinessAccountingDtos() {}
    public enum Kind {
        LOAN_DISBURSEMENT(PostingEvent.DISBURSEMENT), LOAN_REPAYMENT(PostingEvent.REPAYMENT),
        LOAN_REPAYMENT_REVERSAL(PostingEvent.REPAYMENT), INTEREST_ACCRUAL(PostingEvent.INTEREST_ACCRUAL),
        LOAN_FEE(PostingEvent.FEE), UNMATCHED_RECEIPT(PostingEvent.ADVANCE), LOAN_ADVANCE(PostingEvent.ADVANCE),
        REFUND(PostingEvent.REFUND), ADVANCE_APPLICATION(PostingEvent.REPAYMENT), EARLY_SETTLEMENT(PostingEvent.SETTLEMENT),
        IMPAIRMENT(PostingEvent.PROVISION), IMPAIRMENT_RELEASE(PostingEvent.PROVISION), WRITE_OFF(PostingEvent.WRITE_OFF), RECOVERY(PostingEvent.RECOVERY),
        EXPENSE_INVOICE(PostingEvent.EXPENSE), DIRECT_EXPENSE(PostingEvent.EXPENSE), STAFF_REIMBURSEMENT(PostingEvent.EXPENSE),
        PAYABLE_PAYMENT(PostingEvent.EXPENSE), SUPPLIER_CREDIT(PostingEvent.EXPENSE),
        CAPITAL_RECEIPT(PostingEvent.CAPITAL), OWNER_DISTRIBUTION(PostingEvent.CAPITAL), FUNDING_RECEIPT(PostingEvent.FUNDING),
        FUNDING_PRINCIPAL_PAYMENT(PostingEvent.FUNDING), FUNDING_INTEREST(PostingEvent.FUNDING),
        ASSET_PURCHASE(PostingEvent.EXPENSE), DEPRECIATION(PostingEvent.EXPENSE), ASSET_DISPOSAL(PostingEvent.EXPENSE),
        PREPAYMENT(PostingEvent.EXPENSE), PREPAYMENT_RELEASE(PostingEvent.EXPENSE), ACCRUAL(PostingEvent.EXPENSE), ACCRUAL_PAYMENT(PostingEvent.EXPENSE),
        TAX_LIABILITY(PostingEvent.EXPENSE), TAX_PAYMENT(PostingEvent.EXPENSE), INTERNAL_TRANSFER_OUT(PostingEvent.EXPENSE), INTERNAL_TRANSFER_IN(PostingEvent.EXPENSE),
        BUSINESS_REVERSAL(PostingEvent.EXPENSE);
        private final PostingEvent event;
        Kind(PostingEvent event) {this.event=event;}
        public PostingEvent event(){return event;}
    }
    public record Command(UUID requestKey, Kind kind, LocalDate effectiveDate, BigDecimal amount, UUID loanId,
        UUID relatedDocumentId, UUID supplierId, String description, String evidenceReference,
        String channelReference, String moneyAccountKey, String destinationBranch, String loanNumber,
        LocalDate firstRepaymentDate, RepaymentFrequency frequency, BigDecimal installmentAmount) {}
    public record Document(UUID id, String institutionId, String branchId, UUID makerId, UUID checkerId,
        Command command, String state, UUID journalId, UUID loanTransactionId, String approvalEvidence,
        OffsetDateTime createdAt, OffsetDateTime postedAt, BigDecimal principal, BigDecimal interest, BigDecimal fees) {}
    public record Supplier(UUID id, String name, String evidenceReference, boolean active) {}
    public record Asset(UUID id, String description, LocalDate acquiredOn, BigDecimal cost,
        BigDecimal depreciation, boolean disposed) {}
    public record Page<T>(List<T> rows, int page, boolean hasNext) {}
    public record Control(UUID sourceDocumentId, Kind kind, String description, BigDecimal original, BigDecimal remaining) {}
    public record LoanAmounts(BigDecimal principal, BigDecimal duePrincipal, BigDecimal dueInterest, LocalDate disbursementDate) {}
}
