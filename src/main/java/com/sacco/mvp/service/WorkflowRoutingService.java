package com.sacco.mvp.service;

import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowRoutingService {
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final ApprovalFlowService approvalFlowService;
    private final BoardReviewRepository boardReviewRepository;
    private final RoleDirectoryService roleDirectoryService;
    private final LoanProductWorkflowService loanProductWorkflowService;
    private final OutboxService outboxService;

    public SaccoSettings settings(String saccoId) {
        return saccoSettingsRepository.findById(saccoId)
            .orElseThrow(() -> new IllegalArgumentException("Settings missing"));
    }

    @Transactional
    public LoanStatus moveToFirstReviewStage(LoanApplication app, UUID actorMemberId) {
        if (app == null) {
            throw new IllegalArgumentException("Loan application not found");
        }
        LoanProductWorkflowService.WorkflowDefinition workflow = loanProductWorkflowService.resolveForApplication(app);
        ApprovalWorkflowStage firstStage = workflow.stages().isEmpty() ? null : workflow.stages().getFirst();
        LoanStatus nextStatus = approvalFlowService.pendingStatusFor(firstStage);
        if (firstStage == ApprovalWorkflowStage.LOAN_OFFICER || firstStage == ApprovalWorkflowStage.BOARD) {
            assignReviewStage(app, firstStage, workflow);
        }
        app.setStatus(nextStatus);
        app.setUpdatedAt(OffsetDateTime.now());
        outboxService.enqueue("LOAN", app.getId(), approvalFlowService.reviewAssignedEventType(firstStage), actorMemberId,
            Map.of("loanId", app.getId().toString()));
        return nextStatus;
    }

    @Transactional
    public LoanStatus advanceAfterApproval(LoanApplication app,
                                           ApprovalWorkflowStage currentStage,
                                           UUID actorMemberId) {
        if (app == null) {
            throw new IllegalArgumentException("Loan application not found");
        }
        LoanProductWorkflowService.WorkflowDefinition workflow = loanProductWorkflowService.resolveForApplication(app);
        ApprovalWorkflowStage nextStage = approvalFlowService.nextStageAfter(workflow.stages(), currentStage);
        LoanStatus nextStatus = approvalFlowService.pendingStatusFor(nextStage);
        if (nextStage == ApprovalWorkflowStage.LOAN_OFFICER || nextStage == ApprovalWorkflowStage.BOARD) {
            assignReviewStage(app, nextStage, workflow);
        }
        app.setStatus(nextStatus);
        app.setUpdatedAt(OffsetDateTime.now());
        outboxService.enqueue("LOAN", app.getId(), approvalFlowService.reviewAssignedEventType(nextStage), actorMemberId,
            Map.of("loanId", app.getId().toString()));
        return nextStatus;
    }

    private void assignReviewStage(LoanApplication app,
                                   ApprovalWorkflowStage stage,
                                   LoanProductWorkflowService.WorkflowDefinition workflow) {
        boardReviewRepository.deleteByLoanApplicationIdAndReviewStage(app.getId(), stage);
        boardReviewRepository.flush();
        if (stage == ApprovalWorkflowStage.LOAN_OFFICER) {
            RoleDirectoryService.RoleAccountRef loanOfficer = roleDirectoryService
                .activeByRoleInStation(app.getSaccoId(), app.getStationId(), Position.LOAN_OFFICER).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No active loan officer is configured for this station."));
            boardReviewRepository.save(BoardReview.builder()
                .id(UUID.randomUUID())
                .loanApplicationId(app.getId())
                .boardMemberId(loanOfficer.getId())
                .reviewStage(stage)
                .decision(BoardDecision.PENDING)
                .createdAt(OffsetDateTime.now())
                .build());
            return;
        }

        int requiredBoardReviewers = Math.max(workflow.committeeMinimumVotes(), 1);
        java.util.List<RoleDirectoryService.RoleAccountRef> boardMembers = roleDirectoryService
            .activeByRoleInStation(app.getSaccoId(), app.getStationId(), Position.BOARD);
        if (boardMembers.size() < requiredBoardReviewers) {
            throw new IllegalStateException("Not enough board members for the configured committee review requirement.");
        }
        boardMembers.stream().limit(requiredBoardReviewers).forEach(board -> boardReviewRepository.save(BoardReview.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(app.getId())
            .boardMemberId(board.getId())
            .reviewStage(stage)
            .decision(BoardDecision.PENDING)
            .createdAt(OffsetDateTime.now())
            .build()));
    }
}
