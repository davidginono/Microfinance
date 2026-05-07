package com.sacco.mvp.service;

import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.SaccoSettings;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ApprovalFlowService {
    public List<ApprovalWorkflowStage> flowFor(SaccoSettings settings) {
        return settings == null
            ? List.of(
                ApprovalWorkflowStage.MANAGER,
                ApprovalWorkflowStage.BOARD,
                ApprovalWorkflowStage.ACCOUNTANT,
                ApprovalWorkflowStage.DISBURSEMENT_OFFICER
            )
            : settings.resolvedApprovalFlow();
    }

    public ApprovalWorkflowStage nextStageAfter(SaccoSettings settings, ApprovalWorkflowStage currentStage) {
        return nextStageAfter(flowFor(settings), currentStage);
    }

    public ApprovalWorkflowStage nextStageAfter(List<ApprovalWorkflowStage> flow, ApprovalWorkflowStage currentStage) {
        int currentIndex = flow.indexOf(currentStage);
        if (currentIndex < 0 || currentIndex + 1 >= flow.size()) {
            return null;
        }
        return flow.get(currentIndex + 1);
    }

    public LoanStatus pendingStatusFor(ApprovalWorkflowStage stage) {
        if (stage == null) {
            return LoanStatus.FINAL_APPROVED;
        }
        return switch (stage) {
            case MANAGER -> LoanStatus.READY_FOR_MANAGER;
            case LOAN_OFFICER -> LoanStatus.AWAITING_LOAN_OFFICER;
            case BOARD -> LoanStatus.AWAITING_BOARD;
            case ACCOUNTANT -> LoanStatus.AWAITING_ACCOUNTANT;
            case DISBURSEMENT_OFFICER -> LoanStatus.READY_FOR_DISBURSEMENT;
        };
    }

    public LoanStatus rejectionStatusFor(ApprovalWorkflowStage stage) {
        return switch (stage) {
            case MANAGER -> LoanStatus.MANAGER_REJECTED;
            case LOAN_OFFICER -> LoanStatus.LOAN_OFFICER_REJECTED;
            case BOARD -> LoanStatus.BOARD_REJECTED;
            case ACCOUNTANT -> LoanStatus.ACCOUNTANT_REJECTED;
            case DISBURSEMENT_OFFICER -> LoanStatus.FINAL_REJECTED;
        };
    }

    public String reviewAssignedEventType(ApprovalWorkflowStage stage) {
        return switch (stage) {
            case MANAGER -> "LOAN_READY_FOR_MANAGER";
            case LOAN_OFFICER -> "LOAN_OFFICER_REVIEW_ASSIGNED";
            case BOARD -> "BOARD_REVIEW_ASSIGNED";
            case ACCOUNTANT -> "LOAN_READY_FOR_ACCOUNTANT";
            case DISBURSEMENT_OFFICER -> "LOAN_READY_FOR_DISBURSEMENT";
        };
    }
}
