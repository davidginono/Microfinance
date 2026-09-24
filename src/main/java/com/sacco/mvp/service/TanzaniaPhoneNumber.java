package com.sacco.mvp.service;

public final class TanzaniaPhoneNumber {
    private TanzaniaPhoneNumber() {
    }

    public static String normalizeRequired(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalStateException("Enter a valid phone number in the format 255XXXXXXXXX.");
        }
        return normalized;
    }

    public static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.matches("0[0-9]{9}")) {
            digits = "255" + digits.substring(1);
        }
        return digits.matches("255[0-9]{9}") ? digits : null;
    }
}
