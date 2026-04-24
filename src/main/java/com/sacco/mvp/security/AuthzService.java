package com.sacco.mvp.security;

import com.sacco.mvp.domain.Position;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("authz")
@RequiredArgsConstructor
public class AuthzService {
    private final LoanApplicationRepository loanApplicationRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final BoardReviewRepository boardReviewRepository;

    /**
     * Separation of Duties: Super Admins (ADMIN) MUST NOT perform SACCO-workspace
     * operations (loan approvals, settings mutations, guarantor flows, etc.).
     * They register, suspend and audit tenants from the platform layer only.
     */
    public boolean notSuperAdmin(AppUserPrincipal principal) {
        return principal != null && !principal.hasRole(Position.ADMIN);
    }

    public boolean platformAdminIdentity(AppUserPrincipal principal) {
        return principal != null && principal.hasRole(Position.ADMIN);
    }

    public boolean workspaceAdminOnly(AppUserPrincipal principal) {
        return principal != null
            && principal.hasRole(Position.MINOR_ADMIN)
            && !principal.hasRole(Position.ADMIN);
    }

    /**
     * Separation of Duties: Minor Admins (MINOR_ADMIN) MUST NOT touch platform-wide
     * controls (registering SACCOs, creating other Minor Admins, switching tenant scope).
     */
    public boolean platformAdminOnly(AppUserPrincipal principal) {
        return principal != null
            && principal.hasRole(Position.ADMIN);
    }

    public boolean isLoanOwner(UUID loanId, AppUserPrincipal principal) {
        return loanApplicationRepository.findById(loanId)
            .map(app -> app.getApplicantMemberId().equals(principal.getMemberId()))
            .orElse(false);
    }

    public boolean isGuarantorAssignee(UUID requestId, AppUserPrincipal principal) {
        return guarantorRequestRepository.findByIdAndGuarantorMemberId(requestId, principal.getMemberId())
            .map(req -> true)
            .orElse(false);
    }

    public boolean isBoardAssignee(UUID loanId, AppUserPrincipal principal) {
        return boardReviewRepository.findByLoanApplicationIdAndBoardMemberId(loanId, principal.getMemberId())
            .map(review -> true)
            .orElse(false);
    }

    public boolean canViewLoan(UUID loanId, AppUserPrincipal principal) {
        return loanApplicationRepository.findById(loanId)
            .map(app -> {
                if (app.getApplicantMemberId().equals(principal.getMemberId())) {
                    return true;
                }
                if (principal.hasRole(com.sacco.mvp.domain.Position.ADMIN)) {
                    return true;
                }
                if (principal.hasRole(com.sacco.mvp.domain.Position.MINOR_ADMIN)
                    && app.getSaccoId().equals(principal.getSaccoId())) {
                    return true;
                }
                if ((principal.hasRole(com.sacco.mvp.domain.Position.MANAGER) || principal.hasRole(com.sacco.mvp.domain.Position.CHAIRPERSON))
                    && app.getSaccoId().equals(principal.getSaccoId())) {
                    return true;
                }
                if (principal.hasRole(com.sacco.mvp.domain.Position.BOARD)) {
                    return boardReviewRepository.findByLoanApplicationIdAndBoardMemberId(loanId, principal.getMemberId())
                        .isPresent();
                }
                return false;
            })
            .orElse(false);
    }
}
