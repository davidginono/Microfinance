package com.sacco.mvp.service;

/** Employer-issued identifier, separate from the institution's staff login number. */
public final class EmployeeNumber {
    private EmployeeNumber() {}
    public static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > 50 || !value.matches("[A-Za-z0-9/._ -]+"))
            throw new IllegalArgumentException("payroll.employeeNumber.invalid");
        return value.trim();
    }
}
