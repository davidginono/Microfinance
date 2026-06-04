package com.sacco.mvp.service;

public record SmsSendResult(boolean sent, String providerReference, String message) {
    public static SmsSendResult sent(String providerReference) {
        return new SmsSendResult(true, providerReference, "Sent");
    }

    public static SmsSendResult skipped(String message) {
        return new SmsSendResult(false, null, message);
    }
}
