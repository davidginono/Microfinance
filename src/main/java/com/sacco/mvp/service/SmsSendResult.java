package com.sacco.mvp.service;

public record SmsSendResult(SmsSendOutcome outcome, String providerReference, String message) {
    public static SmsSendResult sent(String providerReference) {
        return new SmsSendResult(SmsSendOutcome.ACCEPTED, providerReference, "Sent");
    }

    public static SmsSendResult rejected(String message) {
        return new SmsSendResult(SmsSendOutcome.REJECTED, null, message);
    }

    public static SmsSendResult skipped(String message) {
        return new SmsSendResult(SmsSendOutcome.SKIPPED, null, message);
    }

    public static SmsSendResult acceptanceUnknown(String message) {
        return new SmsSendResult(SmsSendOutcome.ACCEPTANCE_UNKNOWN, null, message);
    }

    public boolean sent() {
        return outcome == SmsSendOutcome.ACCEPTED;
    }

    public boolean consumesUnit() {
        return outcome == SmsSendOutcome.ACCEPTED || outcome == SmsSendOutcome.ACCEPTANCE_UNKNOWN;
    }
}
