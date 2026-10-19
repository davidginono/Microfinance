package com.sacco.mvp.domain;

public enum AccessFeature {
    PAYROLL_DEDUCTIONS("Payroll deductions"),
    VOUCHER_WORKSPACE("Approval Voucher Workspace"),
    VOUCHER_APPROVAL("Voucher approval actions"),
    VOUCHER_RULES("Voucher approval configuration"),
    VOUCHER_DOCUMENTS("Voucher supporting documents"),
    ACCESS_MATRIX("Access Matrix"),
    USER_ACCESS("User Access"),
    ADMIN_DASHBOARD("Admin Dashboard"),
    SACCO_REGISTRY("SACCO Registry"),
    PLATFORM_SETTINGS("Platform Settings"),
    WORKSPACE_SETTINGS("Workspace Settings"),
    LOAN_PRODUCTS("Loan Products"),
    APPROVAL_FLOW("Approval Flow"),
    SMS_USAGE("SMS Usage"),
    OUTBOX("Outbox"),
    MEMBER_SETTINGS("Member Settings"),
    PAYMENT_DETAILS("Payment Details"),
    LOAN_REPAYMENTS("Repayments"),
    LOAN_RECORDING("Accountant loan recording"),
    LOAN_ADJUSTMENTS("Loan provisions and corrections"),
    ACCOUNTING_ACCOUNTS("Chart of accounts"),
    ACCOUNTING_ACCOUNT_APPROVAL("Institution account version approvals"),
    ACCOUNTING_ACCOUNT_RULES("Institution account approval configuration"),
    ACCOUNTING_JOURNALS("Journal vouchers"),
    ACCOUNTING_OPENINGS("Opening balances"),
    ACCOUNTING_PERIODS("Accounting periods"),
    ACCOUNTING_POLICIES("Accounting policies"),
    ACCOUNTING_BUSINESS("Receipt and payment vouchers"),
    MEMBER_LOANS("Member Loans"),
    GUARANTOR_REQUESTS("Guarantor Requests"),
    LOAN_DOCUMENTS("Loan Documents"),
    STAFF_ANALYTICS("Staff Analytics"),
    LOAN_OFFICER_QUEUE("Loan Officer Queue"),
    MANAGER_QUEUE("Manager Queue"),
    ACCOUNTANT_QUEUE("Accountant Queue"),
    BOARD_QUEUE("Board Queue"),
    CHAIRPERSON_QUEUE("Chairperson Queue"),
    PROCESSED_LOANS("Processed Loans"),
    SACCO_CONFIGURATIONS("SACCO Configurations"),
    CREDIT_COMMITTEE_QUEUE("Credit Committee Queue"),
    DISBURSEMENT_QUEUE("Disbursement Queue"),
    LOAN_REPORTS("Loan Reports"),
    NOTIFICATIONS("Notifications"),
    SUPPORT("Support"),
    REPORT_BUILDER("Operational Report Builder"),
    FINANCIAL_REPORTS("Financial and management reports"),
    ACCOUNTING_CASH_FLOW("Cash flow classification"),
    ACCOUNTING_RECONCILIATION("Reconciliation"),
    ACCOUNTING_CLOSING("Period Closing"),
    REPORT_EXECUTION("Report execution and approval"),
    FINANCIAL_STATEMENTS("Financial statement designer"),
    REGULATORY_STATEMENTS("Regulatory submissions"),
    ACCOUNTING_RELEASE("Accounting release"),
    ACCOUNTING_COMPLIANCE_RELEASE("Accounting compliance release"),
    ACCOUNTING_STAFF_ACCEPTANCE("Accounting staff acceptance");

    private final String displayName;

    AccessFeature(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
