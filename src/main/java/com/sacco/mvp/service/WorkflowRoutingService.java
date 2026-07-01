package com.sacco.mvp.service;

import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.LoanProductBoardReviewerRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
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
    private final LoanProductBoardReviewerRepository loanProductBoardReviewerRepository;
    private final LoanProductSettingRepository loanProductSettingRepository;
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
        java.util.List<UUID> assignedBoardReviewers = java.util.List.of();
        if (firstStage == ApprovalWorkflowStage.LOAN_OFFICER || isAssignedReviewerStage(firstStage)) {
            assignedBoardReviewers = assignReviewStage(app, firstStage, workflow);
        }
        app.setStatus(nextStatus);
        app.setUpdatedAt(OffsetDateTime.now());
        enqueueReviewAssignedEvents(app, firstStage, actorMemberId, assignedBoardReviewers);
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
        java.util.List<UUID> assignedBoardReviewers = java.util.List.of();
        if (nextStage == ApprovalWorkflowStage.LOAN_OFFICER || isAssignedReviewerStage(nextStage)) {
            assignedBoardReviewers = assignReviewStage(app, nextStage, workflow);
        }
        app.setStatus(nextStatus);
        app.setUpdatedAt(OffsetDateTime.now());
        enqueueReviewAssignedEvents(app, nextStage, actorMemberId, assignedBoardReviewers);
        return nextStatus;
    }

    private java.util.List<UUID> assignReviewStage(LoanApplication app,
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
            return java.util.List.of();
        }

        java.util.List<UUID> boardMemberIds = productReviewerIds(app, stage);
        if (boardMemberIds.isEmpty()) {
            throw new IllegalStateException("No reviewers are assigned to this loan product stage.");
        }
        boardMemberIds.forEach(boardMemberId -> boardReviewRepository.save(BoardReview.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(app.getId())
            .boardMemberId(boardMemberId)
            .reviewStage(stage)
            .decision(BoardDecision.PENDING)
            .createdAt(OffsetDateTime.now())
            .build()));
        return boardMemberIds;
    }

    private java.util.List<UUID> productReviewerIds(LoanApplication app, ApprovalWorkflowStage stage) {
        Position requiredRole = switch (stage) {
            case CREDIT_COMMITTEE -> Position.CREDIT_COMMITTEE;
            case CHAIRPERSON -> Position.CHAIRPERSON;
            default -> Position.BOARD;
        };
        return loanProductSettingRepository.findBySaccoIdAndLoanType(app.getSaccoId(), app.getLoanType())
            .map(product -> loanProductBoardReviewerRepository.findByLoanProductSettingIdAndReviewStageOrderByCreatedAtAsc(product.getId(), stage).stream()
                .map(com.sacco.mvp.domain.LoanProductBoardReviewer::getBoardMemberId)
                .filter(boardMemberId -> roleDirectoryService.hasActiveRoleInSacco(boardMemberId, app.getSaccoId(), requiredRole))
                .toList())
            .orElse(java.util.List.of());
    }

    private void enqueueReviewAssignedEvents(LoanApplication app,
                                             ApprovalWorkflowStage stage,
                                             UUID actorMemberId,
                                             java.util.List<UUID> assignedBoardReviewers) {
        String eventType = approvalFlowService.reviewAssignedEventType(stage);
        if (isAssignedReviewerStage(stage) && assignedBoardReviewers != null && !assignedBoardReviewers.isEmpty()) {
            assignedBoardReviewers.forEach(reviewerId -> outboxService.enqueue("LOAN", app.getId(), eventType, reviewerId,
                app.getSaccoId(), app.getStationId(),
                Map.of("loanId", app.getId().toString(), "boardMemberId", reviewerId.toString())));
            return;
        }
        outboxService.enqueue("LOAN", app.getId(), eventType, actorMemberId,
            app.getSaccoId(), app.getStationId(),
            Map.of("loanId", app.getId().toString()));
    }

    private boolean isAssignedReviewerStage(ApprovalWorkflowStage stage) {
        return stage == ApprovalWorkflowStage.CHAIRPERSON
            || stage == ApprovalWorkflowStage.BOARD
            || stage == ApprovalWorkflowStage.CREDIT_COMMITTEE;
    }
}
