package com.sacco.mvp.domain;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public enum Position {
    MEMBER,
    MANAGER,
    BOARD,
    CHAIRPERSON,
    ADMIN;

    public boolean isStaffRole() {
        return this != MEMBER;
    }

    public static List<Position> staffAssignableRoles() {
        return List.of(ADMIN, CHAIRPERSON, MANAGER, BOARD);
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
            case CHAIRPERSON -> 1;
            case MANAGER -> 2;
            case BOARD -> 3;
            case MEMBER -> 4;
        };
    }
}
