package com.sacco.mvp.domain;

public enum LoanType {
    LOAN_ADVANCE,
    EDUCATION_LOAN,
    EMERGENCY_LOAN,
    DEVELOPMENT_LOAN,
    CUSTOMIZED_LOAN;

    public String getDisplayLabel() {
        return switch (this) {
            case LOAN_ADVANCE -> "Loan Advance (Mkopo wa Chapchap)";
            case EDUCATION_LOAN -> "Education Loan (Mkopo wa Elimu)";
            case EMERGENCY_LOAN -> "Emergency Loan (Mkopo wa Dharura)";
            case DEVELOPMENT_LOAN -> "Development Loan (Mkopo wa Biashara)";
            case CUSTOMIZED_LOAN -> "Customized Loan Product";
        };
    }

    public int getDisplayOrder() {
        return switch (this) {
            case LOAN_ADVANCE -> 1;
            case EDUCATION_LOAN -> 2;
            case EMERGENCY_LOAN -> 3;
            case DEVELOPMENT_LOAN -> 4;
            case CUSTOMIZED_LOAN -> 5;
        };
    }
}
