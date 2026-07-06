package com.sacco.mvp.security;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.Position;

public final class WorkspaceLanding {
    private static final String MANAGER_REVIEW_CLAIM = "REVIEW_MANAGER_QUEUE";
    private static final String ACCOUNTANT_REVIEW_CLAIM = "REVIEW_ACCOUNTANT_QUEUE";
    private static final String BOARD_REVIEW_CLAIM = "REVIEW_BOARD_QUEUE";
    private static final String CREDIT_COMMITTEE_REVIEW_CLAIM = "REVIEW_CREDIT_COMMITTEE_QUEUE";
    private static final String CHAIRPERSON_REVIEW_CLAIM = "REVIEW_CHAIRPERSON_QUEUE";
    private static final String LOAN_OFFICER_REVIEW_CLAIM = "REVIEW_LOAN_OFFICER_QUEUE";
    private static final String DISBURSEMENT_ACCESS_CLAIM = "ACCESS_DISBURSEMENT_QUEUE";

    private WorkspaceLanding() {
    }

    public static String memberDashboard() {
        return "/app/dashboard";
    }

    public static String staffDashboard(Member member) {
        if (member == null) {
            return memberDashboard();
        }
        return staffDashboard(Position.primaryRole(member.getStaffRolesResolved(), false), false);
    }

    public static String staffDashboard(AppUserPrincipal principal) {
        if (principal == null) {
            return memberDashboard();
        }
        if (principal.hasRole(Position.ADMIN) || principal.hasRole(Position.MINOR_ADMIN)) {
            return "/admin/dashboard";
        }
        Position primaryRole = Position.primaryRole(principal.getStaffRoles(), false);
        String primaryLanding = staffDashboardForRole(primaryRole, principal);
        if (primaryLanding != null) {
            return primaryLanding;
        }
        for (Position role : principal.getStaffRoles()) {
            String landing = staffDashboardForRole(role, principal);
            if (landing != null) {
                return landing;
            }
        }
        return principal.isMemberAccess() ? memberDashboard() : "/login";
    }

    public static String authenticatedDefault(AppUserPrincipal principal) {
        if (principal == null) {
            return "/login";
        }
        if (principal.isMemberAccess()) {
            return memberDashboard();
        }
        if (principal.getStaffRoles() != null && !principal.getStaffRoles().isEmpty()) {
            return staffDashboard(principal);
        }
        return "/login";
    }

    private static String staffDashboard(Position role, boolean hasDisbursementAccess) {
        if (role == null) {
            return hasDisbursementAccess ? "/disbursement/dashboard" : memberDashboard();
        }
        return switch (role) {
            case ADMIN, MINOR_ADMIN -> "/admin/dashboard";
            case LOAN_OFFICER -> "/loan-officer/dashboard";
            case MANAGER -> "/manager/dashboard";
            case ACCOUNTANT -> "/accountant/dashboard";
            case DISBURSEMENT_OFFICER -> "/disbursement/dashboard";
            case CHAIRPERSON -> "/chairperson/dashboard";
            case BOARD -> "/board/dashboard";
            case CREDIT_COMMITTEE -> "/credit-committee/dashboard";
            case MEMBER -> memberDashboard();
        };
    }

    private static String staffDashboardForRole(Position role, AppUserPrincipal principal) {
        if (role == null || principal == null) {
            return null;
        }
        return switch (role) {
            case ADMIN, MINOR_ADMIN -> "/admin/dashboard";
            case LOAN_OFFICER -> hasClaim(principal, LOAN_OFFICER_REVIEW_CLAIM) ? "/loan-officer/dashboard" : null;
            case MANAGER -> hasClaim(principal, MANAGER_REVIEW_CLAIM) ? "/manager/dashboard" : null;
            case ACCOUNTANT -> hasClaim(principal, ACCOUNTANT_REVIEW_CLAIM) ? "/accountant/dashboard" : null;
            case DISBURSEMENT_OFFICER -> hasClaim(principal, DISBURSEMENT_ACCESS_CLAIM) ? "/disbursement/dashboard" : null;
            case CHAIRPERSON -> hasClaim(principal, CHAIRPERSON_REVIEW_CLAIM) ? "/chairperson/dashboard" : null;
            case BOARD -> hasClaim(principal, BOARD_REVIEW_CLAIM) ? "/board/dashboard" : null;
            case CREDIT_COMMITTEE -> hasClaim(principal, CREDIT_COMMITTEE_REVIEW_CLAIM) ? "/credit-committee/dashboard" : null;
            case MEMBER -> memberDashboard();
        };
    }

    private static boolean hasClaim(AppUserPrincipal principal, String claim) {
        return principal.getClaims() != null && principal.getClaims().contains(claim);
    }
}
