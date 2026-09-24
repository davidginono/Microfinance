package com.sacco.mvp.service;

public record SessionTimeoutPolicy(int timeoutMinutes, long timeoutMs, long warningMs) {
    public int timeoutSeconds() {
        return Math.toIntExact(timeoutMs / 1000L);
    }
}
