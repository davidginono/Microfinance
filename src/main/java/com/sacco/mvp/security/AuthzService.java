package com.sacco.mvp.security;

import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.ChairpersonProcessedLoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component("authz")
@RequiredArgsConstructor
public class AuthzService {
    private final LoanApplicationRepository loanApplicationRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final BoardReviewRepository boardReviewRepository;
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
        return access.isWorkspaceAdminScope(principal);
    }

    /**
     * Separation of Duties: SACCOS Admins (MINOR_ADMIN) MUST NOT touch platform-wide
     * controls (registering SACCOs, creating other SACCOS Admins, switching tenant scope).
     */
    public boolean platformAdminOnly(AppUserPrincipal principal) {
        return access.isPlatformIdentity(principal);
    }

    public boolean isLoanOwner(UUID loanId, AppUserPrincipal principal) {
        if (principal == null || !access.has(principal, UserClaim.MEMBER_LOANS_VIEW)) {
            return false;
        }
        return loanApplicationRepository.findById(loanId)
            .map(app -> app.getApplicantMemberId().equals(principal.getMemberId()))
            .orElse(false);
    }

    public boolean isGuarantorAssignee(UUID requestId, AppUserPrincipal principal) {
        if (principal == null || !access.has(principal, UserClaim.GUARANTOR_REQUESTS_VIEW)) {
            return false;
        }
        return guarantorRequestRepository.findByIdAndGuarantorMemberId(requestId, principal.getMemberId())
            .map(req -> true)
            .orElse(false);
    }

    public boolean isBoardAssignee(UUID loanId, AppUserPrincipal principal) {
        return assignedReviewStage(loanId, principal).isPresent();
    }

    public boolean isBoardReviewer(AppUserPrincipal principal) {
        return access.canAccessChairpersonArea(principal)
            || access.canAccessBoardArea(principal)
            || access.canAccessCreditCommitteeArea(principal);
    }

    public boolean canPrepareBoardDecision(UUID loanId, AppUserPrincipal principal) {
        return assignedReviewStage(loanId, principal)
            .map(stage -> access.hasAny(principal, boardApproveClaim(stage), boardRejectClaim(stage)))
            .orElse(false);
    }

    public boolean canSubmitBoardDecision(UUID loanId, AppUserPrincipal principal, Object decision) {
        return assignedReviewStage(loanId, principal)
            .map(stage -> access.canDecide(principal, decision, boardApproveClaim(stage), boardRejectClaim(stage)))
            .orElse(false);
    }

    public boolean isLoanOfficerAssignee(UUID loanId, AppUserPrincipal principal) {
        return access.canAccessLoanOfficerArea(principal)
            && hasAssignedReviewInScope(loanId, principal, ApprovalWorkflowStage.LOAN_OFFICER);
    }

    public boolean canViewLoan(UUID loanId, AppUserPrincipal principal) {
        if (principal == null) {
            return false;
        }
        return loanApplicationRepository.findById(loanId)
            .map(app -> {
                if (app.getApplicantMemberId().equals(principal.getMemberId())
                    && access.has(principal, UserClaim.LOAN_DOCUMENTS_VIEW)) {
                    return true;
                }
                if (access.isWorkspaceAdminScope(principal)
                    && access.has(principal, UserClaim.LOAN_DOCUMENTS_VIEW)
                    && app.getSaccoId().equals(principal.getSaccoId())
                    && sameStationScope(app, principal)) {
                    return true;
                }
                if (access.has(principal, UserClaim.LOAN_DOCUMENTS_VIEW)
                    && (access.canAccessManagerArea(principal)
                    || access.canAccessAccountantArea(principal)
                    || access.canAccessDisbursementArea(principal))
                    && app.getSaccoId().equals(principal.getSaccoId())
                    && sameStationScope(app, principal)) {
                    return true;
                }
                if (access.has(principal, UserClaim.LOAN_DOCUMENTS_VIEW)
                    && access.canAccessLoanOfficerArea(principal)) {
                    return hasAssignedReviewInScope(loanId, principal, ApprovalWorkflowStage.LOAN_OFFICER);
                }
                if (access.has(principal, UserClaim.LOAN_DOCUMENTS_VIEW)
                    && isBoardAssignee(loanId, principal)) {
                    return true;
                }
                if (access.has(principal, UserClaim.LOAN_DOCUMENTS_VIEW)
                    && access.canViewProcessedLoans(principal)
                    && ChairpersonProcessedLoanService.isProcessedStatus(app.getStatus())
                    && app.getSaccoId().equals(principal.getSaccoId())
                    && sameStationScope(app, principal)) {
                    return true;
                }
                return false;
            })
            .orElse(false);
    }

    private boolean hasAssignedReviewInScope(UUID loanId,
                                             AppUserPrincipal principal,
                                             ApprovalWorkflowStage stage) {
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

    private Optional<ApprovalWorkflowStage> assignedReviewStage(UUID loanId, AppUserPrincipal principal) {
        if (principal == null) {
            return Optional.empty();
        }
        if (access.canAccessChairpersonArea(principal)
            && hasAssignedReviewInScope(loanId, principal, ApprovalWorkflowStage.CHAIRPERSON)) {
            return Optional.of(ApprovalWorkflowStage.CHAIRPERSON);
        }
        if (access.canAccessBoardArea(principal)
            && hasAssignedReviewInScope(loanId, principal, ApprovalWorkflowStage.BOARD)) {
            return Optional.of(ApprovalWorkflowStage.BOARD);
        }
        if (access.canAccessCreditCommitteeArea(principal)
            && hasAssignedReviewInScope(loanId, principal, ApprovalWorkflowStage.CREDIT_COMMITTEE)) {
            return Optional.of(ApprovalWorkflowStage.CREDIT_COMMITTEE);
        }
        return Optional.empty();
    }

    private String boardApproveClaim(ApprovalWorkflowStage stage) {
        return switch (stage) {
            case CHAIRPERSON -> UserClaim.CHAIRPERSON_QUEUE_APPROVE.name();
            case BOARD -> UserClaim.BOARD_QUEUE_APPROVE.name();
            case CREDIT_COMMITTEE -> UserClaim.CREDIT_COMMITTEE_QUEUE_APPROVE.name();
            default -> "";
        };
    }

    private String boardRejectClaim(ApprovalWorkflowStage stage) {
        return switch (stage) {
            case CHAIRPERSON -> UserClaim.CHAIRPERSON_QUEUE_REJECT.name();
            case BOARD -> UserClaim.BOARD_QUEUE_REJECT.name();
            case CREDIT_COMMITTEE -> UserClaim.CREDIT_COMMITTEE_QUEUE_REJECT.name();
            default -> "";
        };
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
