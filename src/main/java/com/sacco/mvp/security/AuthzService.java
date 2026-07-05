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

    public boolean notAdminClass(AppUserPrincipal principal) {
        return principal != null
            && !principal.hasRole(Position.ADMIN)
            && !principal.hasRole(Position.MINOR_ADMIN);
    }

    public boolean staffAnalyticsAccess(AppUserPrincipal principal) {
        return notSuperAdmin(principal)
            && (principal.hasRole(Position.LOAN_OFFICER)
                || principal.hasRole(Position.MANAGER)
                || principal.hasRole(Position.ACCOUNTANT)
                || principal.hasRole(Position.DISBURSEMENT_OFFICER)
                || principal.hasRole(Position.CHAIRPERSON)
                || principal.hasRole(Position.BOARD)
                || principal.hasRole(Position.CREDIT_COMMITTEE));
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
     * Separation of Duties: SACCOS Admins (MINOR_ADMIN) MUST NOT touch platform-wide
     * controls (registering SACCOs, creating other SACCOS Admins, switching tenant scope).
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
        if (principal == null) {
            return false;
        }
        return (principal.hasRole(Position.CHAIRPERSON)
                && principal.getClaims().contains("REVIEW_CHAIRPERSON_QUEUE")
                && hasAssignedReviewInScope(loanId, principal, com.sacco.mvp.domain.ApprovalWorkflowStage.CHAIRPERSON))
            || (principal.hasRole(Position.BOARD)
                && principal.getClaims().contains("REVIEW_BOARD_QUEUE")
                && hasAssignedReviewInScope(loanId, principal, com.sacco.mvp.domain.ApprovalWorkflowStage.BOARD))
            || (principal.hasRole(Position.CREDIT_COMMITTEE)
                && principal.getClaims().contains("REVIEW_CREDIT_COMMITTEE_QUEUE")
                && hasAssignedReviewInScope(loanId, principal, com.sacco.mvp.domain.ApprovalWorkflowStage.CREDIT_COMMITTEE));
    }

    public boolean isBoardReviewer(AppUserPrincipal principal) {
        return principal != null
            && ((principal.hasRole(Position.CHAIRPERSON) && principal.getClaims().contains("REVIEW_CHAIRPERSON_QUEUE"))
                || (principal.hasRole(Position.BOARD) && principal.getClaims().contains("REVIEW_BOARD_QUEUE"))
                || (principal.hasRole(Position.CREDIT_COMMITTEE)
                    && principal.getClaims().contains("REVIEW_CREDIT_COMMITTEE_QUEUE")));
    }

    public boolean isLoanOfficerAssignee(UUID loanId, AppUserPrincipal principal) {
        return hasAssignedReviewInScope(loanId, principal, com.sacco.mvp.domain.ApprovalWorkflowStage.LOAN_OFFICER);
    }

    public boolean canViewLoan(UUID loanId, AppUserPrincipal principal) {
        return loanApplicationRepository.findById(loanId)
            .map(app -> {
                if (app.getApplicantMemberId().equals(principal.getMemberId())) {
                    return true;
                }
                if (principal.hasRole(com.sacco.mvp.domain.Position.MINOR_ADMIN)
                    && app.getSaccoId().equals(principal.getSaccoId())
                    && sameStationScope(app, principal)) {
                    return true;
                }
                if ((principal.hasRole(com.sacco.mvp.domain.Position.MANAGER)
                    || principal.hasRole(com.sacco.mvp.domain.Position.ACCOUNTANT)
                    || principal.hasRole(com.sacco.mvp.domain.Position.DISBURSEMENT_OFFICER)
                    || (!principal.hasRole(com.sacco.mvp.domain.Position.MINOR_ADMIN)
                        && principal.getClaims().contains("ACCESS_DISBURSEMENT_QUEUE")))
                    && app.getSaccoId().equals(principal.getSaccoId())
                    && sameStationScope(app, principal)) {
                    return true;
                }
                if (principal.hasRole(com.sacco.mvp.domain.Position.LOAN_OFFICER)) {
                    return hasAssignedReviewInScope(loanId, principal, com.sacco.mvp.domain.ApprovalWorkflowStage.LOAN_OFFICER);
                }
                if (isBoardAssignee(loanId, principal)) {
                    return true;
                }
                return false;
            })
            .orElse(false);
    }

    private boolean hasAssignedReviewInScope(UUID loanId,
                                             AppUserPrincipal principal,
                                             com.sacco.mvp.domain.ApprovalWorkflowStage stage) {
        if (principal == null) {
            return false;
        }
        return boardReviewRepository.findByLoanApplicationIdAndBoardMemberIdAndReviewStage(
                loanId, principal.getMemberId(), stage)
            .flatMap(review -> loanApplicationRepository.findById(loanId))
            .filter(app -> app.getSaccoId().equals(principal.getSaccoId()))
            .filter(app -> sameStationScope(app, principal))
            .isPresent();
    }

    private boolean sameStationScope(com.sacco.mvp.domain.LoanApplication app, AppUserPrincipal principal) {
        if (principal == null || principal.getStationId() == null || principal.getStationId().isBlank()) {
            return true;
        }
        return app != null
            && app.getStationId() != null
            && principal.getStationId().equalsIgnoreCase(app.getStationId());
    }
}
