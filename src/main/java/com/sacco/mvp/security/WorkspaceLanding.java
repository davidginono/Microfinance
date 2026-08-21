package com.sacco.mvp.security;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.UserClaim;

public final class WorkspaceLanding {
    private WorkspaceLanding() {
    }

    public static String memberDashboard() {
        return "/app/dashboard";
    }

    public static String memberDashboardAfterLogin() {
        return memberDashboard() + "?progressive=true";
    }

    public static String staffDashboard(Member member) {
        if (member == null) {
            return memberDashboard();
        }
        return staffDashboard(UserClaim.defaultClaims(member.getActiveStaffRolesResolved(), member.isMemberAccess()));
    }

    public static String staffDashboard(AppUserPrincipal principal) {
        if (principal == null) {
            return memberDashboard();
        }
        if (hasClaim(principal, UserClaim.ADMIN_DASHBOARD_VIEW)) {
            return "/admin/dashboard";
        }
        String landing = staffDashboard(principal.getClaims());
        if (landing != null) {
            return landing;
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
        if (principal.isStaffSession()) {
            return staffDashboard(principal);
        }
        return "/login";
    }

    private static String staffDashboard(java.util.Collection<?> claims) {
        if (hasClaim(claims, UserClaim.LOAN_OFFICER_QUEUE_VIEW)) {
            return "/loan-officer/dashboard";
        }
        if (hasClaim(claims, UserClaim.MANAGER_QUEUE_VIEW)) {
            return "/manager/dashboard";
        }
        if (hasClaim(claims, UserClaim.ACCOUNTANT_QUEUE_VIEW)) {
            return "/accountant/dashboard";
        }
        if (hasClaim(claims, UserClaim.DISBURSEMENT_QUEUE_VIEW) || hasClaim(claims, UserClaim.DISBURSEMENT_QUEUE_DISBURSE)) {
            return "/disbursement/dashboard";
        }
        if (hasClaim(claims, UserClaim.CHAIRPERSON_QUEUE_VIEW)) {
            return "/chairperson/dashboard";
        }
        if (hasClaim(claims, UserClaim.BOARD_QUEUE_VIEW)) {
            return "/board/dashboard";
        }
        if (hasClaim(claims, UserClaim.CREDIT_COMMITTEE_QUEUE_VIEW)) {
            return "/credit-committee/dashboard";
        }
        return null;
    }

    private static boolean hasClaim(AppUserPrincipal principal, UserClaim claim) {
        return principal.getClaims() != null && principal.getClaims().contains(claim.name());
    }

    private static boolean hasClaim(java.util.Collection<?> claims, UserClaim claim) {
        if (claims == null || claim == null) {
            return false;
        }
        return claims.contains(claim) || claims.contains(claim.name());
    }
}
