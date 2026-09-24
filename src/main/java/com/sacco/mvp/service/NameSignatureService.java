package com.sacco.mvp.service;

import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class NameSignatureService {
    public String requireFullName(String value, String blankMessage) {
        String normalized = normalizeSpaces(value);
        if (normalized.isBlank()) {
            throw new IllegalStateException(blankMessage);
        }
        if (normalized.split(" ").length < 2) {
            throw new IllegalStateException("Enter at least two names.");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    public String signatureFromFullName(String fullName) {
        String normalized = requireFullName(fullName, "Enter the user's full name.");
        String[] parts = normalized.split(" ");
        StringBuilder signature = new StringBuilder(toNameCase(parts[0]));
        for (int i = 1; i < parts.length; i++) {
            if (i == parts.length - 1 && parts.length > 2) {
                signature.append(' ').append(toNameCase(parts[i]));
                continue;
            }
            signature.append(' ').append(parts[i].substring(0, 1).toUpperCase());
        }
        return normalizeSignatureText(signature.toString());
    }

    private String normalizeSignatureText(String value) {
        String normalized = normalizeSpaces(value);
        if (normalized.length() > 120) {
            normalized = normalized.substring(0, 120).trim();
        }
        return normalized;
    }

    private String toNameCase(String value) {
        String normalized = normalizeSpaces(value);
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() == 1) {
            return normalized.toUpperCase();
        }
        return normalized.substring(0, 1).toUpperCase() + normalized.substring(1).toLowerCase();
    }

    private String normalizeSpaces(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
