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
    ACCOUNTING("Finance and Accounting"),
    ACCOUNTING_POLICY("Accounting Policies"),
    ACCOUNTING_ACCOUNTS("Chart of Accounts"),
    ACCOUNTING_JOURNAL("Accounting Journals"),
    ACCOUNTING_OPENING("Opening Balances"),
    ACCOUNTING_CUTOVER("Accounting Cutover"),
    ACCOUNTING_PERIOD("Accounting Periods"),
    ACCOUNTING_BUSINESS("Loan and Business Accounting"),
    FINANCIAL_REPORTS("Financial Reports"),
    FINANCIAL_MAPPINGS("Financial Statement Mappings"),
    FINANCIAL_BUDGETS("Financial Budgets"),
    REPORT_TEMPLATES("Operational Report Templates"),
    REPORTS("Report Execution"),
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
    SUPPORT("Support");

    private final String displayName;

    AccessFeature(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
