package com.sacco.mvp.domain;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public enum Position {
    MEMBER,
    MINOR_ADMIN,
    MANAGER,
    ACCOUNTANT,
    DISBURSEMENT_OFFICER,
    CHAIRPERSON,
    BOARD,
    CREDIT_COMMITTEE,
    LOAN_OFFICER,
    ADMIN;

    public boolean isStaffRole() {
        return this != MEMBER;
    }

    public boolean isAdminRole() {
        return this == ADMIN || this == MINOR_ADMIN;
    }

    public boolean isSuperAdminRole() {
        return this == ADMIN;
    }

    public String getDisplayName() {
        return switch (this) {
            case MEMBER -> "Client";
            case MINOR_ADMIN -> "Institution Admin";
            case MANAGER -> "Manager";
            case ACCOUNTANT -> "Accountant";
            case DISBURSEMENT_OFFICER -> "Disbursement/Teller Officer";
            case CHAIRPERSON -> "Chairperson";
            case BOARD -> "Board";
            case CREDIT_COMMITTEE -> "Credit Committee";
            case LOAN_OFFICER -> "Loan Officer";
            case ADMIN -> "Super Admin";
        };
    }

    public static List<Position> staffAssignableRoles() {
        return List.of(ADMIN, MINOR_ADMIN, LOAN_OFFICER, MANAGER, ACCOUNTANT, DISBURSEMENT_OFFICER, CHAIRPERSON, BOARD, CREDIT_COMMITTEE);
    }

    public static boolean containsAdminRole(Collection<Position> roles) {
        return roles != null && roles.stream().anyMatch(Position::isAdminRole);
    }

    public static boolean containsSuperAdminRole(Collection<Position> roles) {
        return roles != null && roles.stream().anyMatch(Position::isSuperAdminRole);
    }

    public static LinkedHashSet<Position> normalizeStaffRoles(Collection<Position> roles) {
        LinkedHashSet<Position> normalized = new LinkedHashSet<>();
        if (roles == null) {
            return normalized;
        }
        roles.stream()
            .filter(position -> position != null && position.isStaffRole())
            .sorted(Comparator.comparingInt(Position::priority))
            .forEach(normalized::add);
        return normalized;
    }

    public static Position primaryRole(Collection<Position> staffRoles, boolean memberAccess) {
        Set<Position> normalized = normalizeStaffRoles(staffRoles);
        if (!normalized.isEmpty()) {
            return normalized.iterator().next();
        }
        return memberAccess ? MEMBER : null;
    }

    private int priority() {
        return switch (this) {
            case ADMIN -> 0;
            case MINOR_ADMIN -> 1;
            case LOAN_OFFICER -> 2;
            case MANAGER -> 3;
            case ACCOUNTANT -> 4;
            case DISBURSEMENT_OFFICER -> 5;
            case CHAIRPERSON -> 6;
            case BOARD -> 7;
            case CREDIT_COMMITTEE -> 8;
            case MEMBER -> 9;
        };
    }
}
