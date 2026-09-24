package com.sacco.mvp.domain;

public enum ApprovalWorkflowStage {
    MANAGER,
    LOAN_OFFICER,
    CHAIRPERSON,
    BOARD,
    CREDIT_COMMITTEE,
    ACCOUNTANT,
    DISBURSEMENT_OFFICER;

    public String getDisplayLabel() {
        return switch (this) {
            case MANAGER -> "Branch Manager";
            case LOAN_OFFICER -> "Loan Officer";
            case CHAIRPERSON -> "Chairperson";
            case BOARD -> "Board Member";
            case CREDIT_COMMITTEE -> "Credit Committee";
            case ACCOUNTANT -> "Accountant";
            case DISBURSEMENT_OFFICER -> "Disbursement/Teller Officer";
        };
    }
}
