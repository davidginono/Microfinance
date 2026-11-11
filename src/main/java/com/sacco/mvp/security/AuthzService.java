package com.sacco.mvp.security;

import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.service.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("authz")
@RequiredArgsConstructor
public class AuthzService {
    private final LoanApplicationRepository loanApplicationRepository;
    private final AccessControlService access;

    /**
     * Separation of Duties: Super Admins (ADMIN) MUST NOT perform SACCO-workspace
     * operations (loan approvals, settings mutations, guarantor flows, etc.).
     * They register, suspend and audit tenants from the platform layer only.
     */
    public boolean notSuperAdmin(AppUserPrincipal principal) {
        return access.notPlatformIdentity(principal);
    }

    public boolean notAdminClass(AppUserPrincipal principal) {
        return access.notAdminScope(principal);
    }

    public boolean staffAnalyticsAccess(AppUserPrincipal principal) {
        return access.staffAnalyticsAccess(principal);
    }

    public boolean platformAdminIdentity(AppUserPrincipal principal) {
        return access.isPlatformIdentity(principal);
    }

    public boolean workspaceAdminOnly(AppUserPrincipal principal) {
        return principal!=null&&principal.isStaffSession()&&!principal.isPlatformIdentity()
            &&principal.getSaccoId()!=null&&!principal.getSaccoId().isBlank()
            &&principal.getStationId()!=null&&!principal.getStationId().isBlank();
    }

    /**
     * Separation of Duties: SACCOS Admins (MINOR_ADMIN) MUST NOT touch platform-wide
     * controls (registering SACCOs, creating other SACCOS Admins, switching tenant scope).
     */
    public boolean platformAdminOnly(AppUserPrincipal principal) {
        return access.isPlatformIdentity(principal);
    }

    public boolean canViewLoan(UUID loanId, AppUserPrincipal p) {
        if(p==null||!p.isStaffSession()||access.isPlatformIdentity(p)||!access.has(p,UserClaim.LOAN_DOCUMENTS_VIEW)
            || !access.hasAny(p,UserClaim.LOAN_RECORDING_VIEW,UserClaim.DISBURSEMENT_QUEUE_VIEW)
            || p.getSaccoId()==null||p.getStationId()==null||p.getStationId().isBlank())return false;
        return loanApplicationRepository.findAccessRowById(loanId)
            .filter(l->p.getSaccoId().equals(l.getSaccoId())&&p.getStationId().equalsIgnoreCase(l.getStationId())).isPresent();
    }
}
