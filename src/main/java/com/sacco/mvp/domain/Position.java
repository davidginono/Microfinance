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
    ADMIN,
    FINANCE_MANAGER, ICT_MANAGER, CREDIT_OFFICER, ACCOUNTS_CLERK, CUSTOMER_CARE, ACCOUNTS_CLERK_2;

    public boolean isStaffRole() {
        return this != MEMBER;
    }

    /** Historical identifiers remain valid for frozen approval evidence. */
    public Position userGroup() {
        return switch(this) {
            case MINOR_ADMIN -> ICT_MANAGER;
            case CHAIRPERSON -> FINANCE_MANAGER;
            case BOARD, CREDIT_COMMITTEE -> CREDIT_OFFICER;
            case DISBURSEMENT_OFFICER -> ACCOUNTS_CLERK;
            default -> this;
        };
    }

    public boolean isAdminRole() {
        return this == ADMIN || this == MINOR_ADMIN || this == ICT_MANAGER;
    }

    public boolean isSuperAdminRole() {
        return this == ADMIN;
    }

    public String getDisplayName() {
        return switch (this) {
            case MEMBER -> "Client";
            case MINOR_ADMIN -> "Institution Admin";
            case MANAGER -> "General Manager";
            case ACCOUNTANT -> "Chief Accountant";
            case DISBURSEMENT_OFFICER -> "Disbursement/Teller Officer";
            case CHAIRPERSON -> "Chairperson";
            case BOARD -> "Board Committee";
            case CREDIT_COMMITTEE -> "Credit Committee";
            case LOAN_OFFICER -> "Loan Officer";
            case ADMIN -> "Super Administrator";
            case FINANCE_MANAGER -> "Finance Manager";
            case ICT_MANAGER -> "ICT Manager";
            case CREDIT_OFFICER -> "Credit Officer";
            case ACCOUNTS_CLERK -> "Accounts Clerk";
            case CUSTOMER_CARE -> "Customer Care";
            case ACCOUNTS_CLERK_2 -> "Accounts Clerk 2";
        };
    }

    public static List<Position> staffAssignableRoles() {
        return List.of(ADMIN, LOAN_OFFICER, MANAGER, FINANCE_MANAGER, ACCOUNTANT, ICT_MANAGER, CREDIT_OFFICER, ACCOUNTS_CLERK, CUSTOMER_CARE, ACCOUNTS_CLERK_2);
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
            case FINANCE_MANAGER -> 4;
            case ICT_MANAGER -> 6;
            case CREDIT_OFFICER -> 7;
            case ACCOUNTS_CLERK -> 8;
            case CUSTOMER_CARE -> 9;
            case ACCOUNTS_CLERK_2 -> 10;
            case MEMBER -> 11;
        };
    }
}
