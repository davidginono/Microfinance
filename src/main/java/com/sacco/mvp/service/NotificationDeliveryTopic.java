package com.sacco.mvp.service;

public enum NotificationDeliveryTopic {
    LOAN_STATUS("Loan status updates"),
    GUARANTEE_REQUEST("Guarantee requests"),
    REPAYMENT_REMINDER("Repayment reminders");

    private final String label;

    NotificationDeliveryTopic(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
