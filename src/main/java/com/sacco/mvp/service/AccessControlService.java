package com.sacco.mvp.service;

import com.sacco.mvp.domain.AccessAction;
import com.sacco.mvp.domain.AccessFeature;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.security.AppUserPrincipal;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

@Service("access")
public class AccessControlService {
    public boolean has(AppUserPrincipal principal, String claimName) {
        if (principal == null || claimName == null || claimName.isBlank()) {
            return false;
        }
        Set<String> principalClaims = principal.getClaims();
        return UserClaim.fromStoredName(claimName).stream()
            .map(UserClaim::name)
            .anyMatch(principalClaims::contains);
    }

    public boolean has(AppUserPrincipal principal, UserClaim claim) {
        return claim != null && has(principal, claim.name());
    }

    public boolean hasAny(AppUserPrincipal principal, UserClaim... claims) {
        if (principal == null || claims == null || claims.length == 0) {
            return false;
        }
        return Arrays.stream(claims).anyMatch(claim -> has(principal, claim));
    }

    public boolean hasAny(AppUserPrincipal principal, Collection<UserClaim> claims) {
        if (principal == null || claims == null || claims.isEmpty()) {
            return false;
        }
        return claims.stream().anyMatch(claim -> has(principal, claim));
    }

    public boolean hasAny(AppUserPrincipal principal, String... claimNames) {
        if (principal == null || claimNames == null || claimNames.length == 0) {
            return false;
        }
        return Arrays.stream(claimNames).anyMatch(claimName -> has(principal, claimName));
    }

    public boolean canDecide(AppUserPrincipal principal, Object decision, String approveClaimName, String rejectClaimName) {
        if (decision == null) {
            return false;
        }
        String normalizedDecision = decision.toString().trim().toUpperCase(Locale.ROOT);
        return switch (normalizedDecision) {
            case "ACCEPT", "APPROVE", "APPROVED" -> has(principal, approveClaimName);
            case "REJECT", "REJECTED", "DECLINE", "DECLINED" -> has(principal, rejectClaimName);
            default -> false;
        };
    }

    public boolean can(AppUserPrincipal principal, AccessFeature feature, AccessAction action) {
        return UserClaim.forFeatureAction(feature, action)
            .map(claim -> has(principal, claim))
            .orElse(false);
    }

    public boolean can(AppUserPrincipal principal, String feature, String action) {
        if (feature == null || action == null) {
            return false;
        }
        try {
            return can(principal, AccessFeature.valueOf(feature), AccessAction.valueOf(action));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isPlatformIdentity(AppUserPrincipal principal) {
        return principal != null && principal.hasMetadataRole(Position.ADMIN);
    }

    public boolean isWorkspaceAdminScope(AppUserPrincipal principal) {
        return principal != null
            && (principal.hasMetadataRole(Position.MINOR_ADMIN) || principal.hasMetadataRole(Position.ICT_MANAGER))
            && !isPlatformIdentity(principal);
    }

    public boolean isAdminScope(AppUserPrincipal principal) {
        return isPlatformIdentity(principal) || isWorkspaceAdminScope(principal);
    }

    public boolean notPlatformIdentity(AppUserPrincipal principal) {
        return principal != null && !isPlatformIdentity(principal);
    }

    public boolean notAdminScope(AppUserPrincipal principal) {
        return principal != null && !isAdminScope(principal);
    }

    public boolean canAccessAdminArea(AppUserPrincipal principal) {
        return isStaffSession(principal) && (isPlatformIdentity(principal)
            || principal.getSaccoId()!=null&&!principal.getSaccoId().isBlank()&&principal.getStationId()!=null&&!principal.getStationId().isBlank()) && hasAny(principal,
            UserClaim.ADMIN_DASHBOARD_VIEW,
            UserClaim.BRANCHES_VIEW,
            UserClaim.USER_ACCESS_VIEW,
            UserClaim.WORKSPACE_SETTINGS_VIEW,
            UserClaim.WORKSPACE_SETTINGS_UPDATE,
            UserClaim.WORKSPACE_SETTINGS_CONFIGURE,
            UserClaim.LOAN_PRODUCTS_VIEW,
            UserClaim.LOAN_PRODUCTS_CREATE,
            UserClaim.LOAN_PRODUCTS_UPDATE,
            UserClaim.LOAN_PRODUCTS_CONFIGURE,
            UserClaim.LOAN_PRODUCTS_DELETE,
            UserClaim.APPROVAL_FLOW_VIEW,
            UserClaim.APPROVAL_FLOW_CONFIGURE,
            UserClaim.SACCO_REGISTRY_VIEW,
            UserClaim.SACCO_REGISTRY_CREATE,
            UserClaim.SACCO_REGISTRY_UPDATE,
            UserClaim.SACCO_REGISTRY_DELETE,
            UserClaim.PLATFORM_SETTINGS_VIEW,
            UserClaim.PLATFORM_SETTINGS_UPDATE,
            UserClaim.SMS_USAGE_VIEW,
            UserClaim.SMS_USAGE_ADD,
            UserClaim.SMS_USAGE_CONFIGURE,
            UserClaim.OUTBOX_VIEW,
            UserClaim.OUTBOX_UPDATE,
            UserClaim.SUPPORT_VIEW,
            UserClaim.SUPPORT_CREATE,
            UserClaim.SUPPORT_UPDATE,
            UserClaim.NOTIFICATIONS_VIEW,
            UserClaim.NOTIFICATIONS_UPDATE);
    }

    public boolean staffDashboardAccess(AppUserPrincipal principal) {
        return isStaffSession(principal) && notPlatformIdentity(principal)
            && principal.getSaccoId()!=null && !principal.getSaccoId().isBlank()
            && principal.getStationId()!=null && !principal.getStationId().isBlank()
            && !com.sacco.mvp.staffoffice.model.StaffOffice.assigned(principal.getStaffRoles()).isEmpty();
    }

    public boolean canAccessDisbursementArea(AppUserPrincipal principal) {
        return isStaffSession(principal) && notPlatformIdentity(principal) && hasAny(principal,
            UserClaim.DISBURSEMENT_QUEUE_VIEW,
            UserClaim.DISBURSEMENT_QUEUE_DISBURSE);
    }

    public boolean staffAnalyticsAccess(AppUserPrincipal principal) {
        return isStaffSession(principal) && notPlatformIdentity(principal) && has(principal, UserClaim.STAFF_ANALYTICS_VIEW);
    }

    private boolean isStaffSession(AppUserPrincipal principal) {
        return principal != null && principal.isStaffSession();
    }

}
