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

    public String defaultProductCode() {
        return switch (this) {
            case LOAN_ADVANCE -> "ADV_LOAN";
            case EDUCATION_LOAN -> "EDU_LOAN";
            case EMERGENCY_LOAN -> "EMERGENCY_LOAN";
            case DEVELOPMENT_LOAN -> "DEV_LOAN";
            case CUSTOMIZED_LOAN -> "CUSTOM_LOAN";
        };
    }

    public String defaultDescription() {
        return switch (this) {
            case LOAN_ADVANCE -> "Short-cycle advance for urgent member needs.";
            case EDUCATION_LOAN -> "Supports school fees and related education expenses.";
            case EMERGENCY_LOAN -> "Supports time-sensitive personal and family emergencies.";
            case DEVELOPMENT_LOAN -> "Supports business growth and long-term development plans.";
            case CUSTOMIZED_LOAN -> "A SACCO-defined loan product with custom rules.";
        };
    }
}
