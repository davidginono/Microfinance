package com.sacco.mvp.domain;

public enum ApprovalWorkflowStage {
    MANAGER,
    LOAN_OFFICER,
    BOARD,
    ACCOUNTANT,
    DISBURSEMENT_OFFICER;

    public String getDisplayLabel() {
        return switch (this) {
            case MANAGER -> "Branch Manager";
            case LOAN_OFFICER -> "Loan Officer";
            case BOARD -> "Board Committee";
            case ACCOUNTANT -> "Accountant";
            case DISBURSEMENT_OFFICER -> "Disbursement Officer";
        };
    }
}
