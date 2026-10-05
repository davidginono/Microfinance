package com.sacco.mvp.accounting.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Immutable money and scope boundaries. No public command accepts institution or branch fields. */
public final class LedgerDtos {
    private LedgerDtos() {}
    public enum AccountKind { ASSET, LIABILITY, EQUITY, INCOME, EXPENSE }
    public enum NormalBalance { DEBIT, CREDIT }
    public enum AccountUsage { HEADING, POSTING, CONTROL }
    public enum AccountCategory {
        CASH, BANK, MOBILE_MONEY, CLEARING, SUSPENSE, LOAN_PRINCIPAL, INTEREST_RECEIVABLE,
        FEE_RECEIVABLE, ALLOWANCE, PAYABLE, FUNDING, CAPITAL, INCOME, EXPENSE, FIXED_ASSET,
        ACCUMULATED_DEPRECIATION, TAX, PREPAYMENT, EXPENSE_ACCRUAL, CUSTOMER_ADVANCE, INTERNAL_TRANSFER, RETAINED_EARNINGS, OTHER
    }
    public record AccountCommand(String code, String name, AccountKind kind, NormalBalance normalBalance,
                                 UUID parentId, AccountUsage usage, AccountCategory category) {}
    public record AccountView(UUID id, String code, String name, String kind, String normalBalance,
                              UUID parentId, String usage, String category, boolean active) {
        public UUID getId() { return id; }
        public String getCode() { return code; }
        public String getName() { return name; }
        public String getKind() { return kind; }
        public String getNormalBalance() { return normalBalance; }
        public String getUsage() { return usage; }
        public String getCategory() { return category; }
        public boolean isActive() { return active; }
    }
    public record LineCommand(String accountCode, BigDecimal debit, BigDecimal credit, String memo) {}
    public record JournalCommand(UUID requestKey, LocalDate effectiveDate, String description,
                                 String evidenceReference, List<LineCommand> lines) {}
    /** Source commands are internal to validated source use cases, never bound by an HTTP controller. */
    public record SourceJournalCommand(UUID requestKey, LocalDate effectiveDate, String eventType,
                                       String sourceKind, String sourceReference, String description,
                                       String evidenceReference, UUID makerId, UUID checkerId,
                                       List<LineCommand> lines) {}
    public record JournalSummary(UUID id, LocalDate effectiveDate, OffsetDateTime recordedAt,
                                 String description, String state, String sourceKind, BigDecimal totalDebit) {
        public UUID getId() { return id; }
        public LocalDate getEffectiveDate() { return effectiveDate; }
        public String getDescription() { return description; }
        public String getState() { return state; }
        public String getSourceKind() { return sourceKind; }
        public BigDecimal getTotalDebit() { return totalDebit; }
    }
    public record LineView(int lineNumber, UUID accountId, String accountCode, String accountName,
                           BigDecimal debit, BigDecimal credit, String memo) {
        public String getAccountCode() { return accountCode; }
        public String getAccountName() { return accountName; }
        public BigDecimal getDebit() { return debit; }
        public BigDecimal getCredit() { return credit; }
        public String getMemo() { return memo; }
    }
    public record JournalView(UUID id, String institution, String branch, UUID periodId, LocalDate effectiveDate,
                              OffsetDateTime recordedAt, String currency, UUID policyId, int policyVersion,
                              String policyHash, String eventType, String sourceKind, String sourceReference,
                              UUID requestKey, String payloadHash, String description, String evidenceReference,
                              String state, UUID makerId, UUID approvedBy, OffsetDateTime approvedAt,
                              UUID postedBy, OffsetDateTime postedAt, UUID reversesJournalId,
                              String reversalReason, List<LineView> lines) {
        public UUID getId() { return id; }
        public LocalDate getEffectiveDate() { return effectiveDate; }
        public String getState() { return state; }
        public String getDescription() { return description; }
        public String getEvidenceReference() { return evidenceReference; }
        public String getCurrency() { return currency; }
        public int getPolicyVersion() { return policyVersion; }
        public String getSourceKind() { return sourceKind; }
        public String getSourceReference() { return sourceReference; }
        public UUID getReversesJournalId() { return reversesJournalId; }
        public String getReversalReason() { return reversalReason; }
        public List<LineView> getLines() { return lines; }
    }
    public record OpeningCommand(UUID requestKey, LocalDate cutoff, String sourceEvidence, List<LineCommand> lines) {}
    public record OpeningView(UUID id, LocalDate cutoff, UUID journalId, String state, String sourceEvidence,
                              String reconciliationEvidence, UUID makerId, UUID reviewedBy, String kind,
                              UUID policyId, int policyVersion, String policyHash, UUID requestKey, String payloadHash) {
        public UUID getId() { return id; }
        public LocalDate getCutoff() { return cutoff; }
        public UUID getJournalId() { return journalId; }
        public String getState() { return state; }
        public String getSourceEvidence() { return sourceEvidence; }
        public String getKind() { return kind; }
    }
    public record Coverage(LocalDate cutoff, boolean reviewedOpening, long unknownLoans,
                           long unbridgedVouchers, String status) {
        public LocalDate getCutoff() { return cutoff; }
        public boolean isReviewedOpening() { return reviewedOpening; }
        public long getUnknownLoans() { return unknownLoans; }
        public long getUnbridgedVouchers() { return unbridgedVouchers; }
        public String getStatus() { return status; }
    }
}
