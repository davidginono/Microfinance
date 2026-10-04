package com.sacco.mvp.accounting.policy.model;

import java.util.Set;

public enum AccountingEvent {
    OPENING_BALANCE, MANUAL_JOURNAL, CLEARING_BRIDGE, ORDINARY_DISBURSEMENT, REPAYMENT,
    INTEREST_ACCRUAL, FEE_RECOGNITION, UNMATCHED_RECEIPT, ADVANCE_PAYMENT,
    REFUND, EARLY_SETTLEMENT, TOP_UP_SETTLEMENT, RESTRUCTURING, IMPAIRMENT,
    WRITE_OFF, RECOVERY, EXPENSE_INVOICE, EXPENSE_PAYMENT, CAPITAL,
    FUNDING_RECEIPT, FUNDING_REPAYMENT, FUNDING_INTEREST, ASSET_PURCHASE,
    DEPRECIATION, ASSET_DISPOSAL, TAX, BRANCH_TRANSFER, REVERSAL;

    /** Minimum roles only. Posting services validate actual permitted debit/credit lines. */
    public Set<AccountRole> requiredRoles() {
        return switch (this) {
            case ORDINARY_DISBURSEMENT, REPAYMENT -> Set.of(AccountRole.CASH_BANK, AccountRole.LOAN_PRINCIPAL);
            case INTEREST_ACCRUAL -> Set.of(AccountRole.INTEREST_RECEIVABLE, AccountRole.INTEREST_INCOME);
            case FEE_RECOGNITION -> Set.of(AccountRole.FEE_RECEIVABLE, AccountRole.FEE_INCOME);
            case UNMATCHED_RECEIPT -> Set.of(AccountRole.CASH_BANK, AccountRole.SUSPENSE);
            case ADVANCE_PAYMENT, REFUND -> Set.of(AccountRole.CASH_BANK, AccountRole.CUSTOMER_ADVANCE);
            case EARLY_SETTLEMENT, TOP_UP_SETTLEMENT, RESTRUCTURING -> Set.of(AccountRole.LOAN_PRINCIPAL);
            case IMPAIRMENT -> Set.of(AccountRole.ALLOWANCE, AccountRole.IMPAIRMENT_EXPENSE);
            case WRITE_OFF -> Set.of(AccountRole.ALLOWANCE, AccountRole.LOAN_PRINCIPAL);
            case RECOVERY -> Set.of(AccountRole.CASH_BANK, AccountRole.RECOVERY_INCOME);
            case EXPENSE_INVOICE -> Set.of(AccountRole.EXPENSE, AccountRole.PAYABLE);
            case EXPENSE_PAYMENT -> Set.of(AccountRole.CASH_BANK, AccountRole.PAYABLE);
            case CAPITAL -> Set.of(AccountRole.CASH_BANK, AccountRole.CAPITAL);
            case FUNDING_RECEIPT, FUNDING_REPAYMENT -> Set.of(AccountRole.CASH_BANK, AccountRole.FUNDING_PRINCIPAL);
            case FUNDING_INTEREST -> Set.of(AccountRole.CASH_BANK, AccountRole.FUNDING_INTEREST);
            case ASSET_PURCHASE -> Set.of(AccountRole.ASSET, AccountRole.CASH_BANK);
            case DEPRECIATION -> Set.of(AccountRole.DEPRECIATION, AccountRole.ACCUMULATED_DEPRECIATION);
            case ASSET_DISPOSAL -> Set.of(AccountRole.ASSET, AccountRole.ACCUMULATED_DEPRECIATION);
            case TAX -> Set.of(AccountRole.TAX_PAYABLE);
            case BRANCH_TRANSFER -> Set.of(AccountRole.INTERNAL_TRANSFER, AccountRole.CASH_BANK);
            default -> Set.of();
        };
    }
}
