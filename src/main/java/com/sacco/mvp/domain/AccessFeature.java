package com.sacco.mvp.domain;

public enum AccessFeature {
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
    ACCOUNTING_ACCOUNTS("Chart of accounts"),
    ACCOUNTING_JOURNALS("Accounting journals"),
    ACCOUNTING_OPENINGS("Opening balances"),
    ACCOUNTING_PERIODS("Accounting periods"),
    ACCOUNTING_POLICIES("Accounting policies"),
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
    ACCOUNTING_RECONCILIATION("Reconciliation"),
    ACCOUNTING_CLOSING("Period Closing"),
    REPORT_EXECUTION("Report execution and approval");


    private final String displayName;

    AccessFeature(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
