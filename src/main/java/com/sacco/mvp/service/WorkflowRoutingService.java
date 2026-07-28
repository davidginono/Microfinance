package com.sacco.mvp.service;

import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.LoanProductBoardReviewerRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
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
        java.util.List<UUID> assignedReviewers = java.util.List.of();
        if (requiresReviewAssignment(firstStage)) {
            assignedReviewers = assignReviewStage(app, firstStage, workflow);
        }
        app.setStatus(nextStatus);
        app.setUpdatedAt(OffsetDateTime.now());
        enqueueReviewAssignedEvents(app, firstStage, assignedReviewers, actorMemberId);
        enqueueApplicantStatusChangedEvent(app, nextStatus, actorMemberId);
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
        java.util.List<UUID> assignedReviewers = java.util.List.of();
        if (requiresReviewAssignment(nextStage)) {
            assignedReviewers = assignReviewStage(app, nextStage, workflow);
        }
        app.setStatus(nextStatus);
        app.setUpdatedAt(OffsetDateTime.now());
        enqueueReviewAssignedEvents(app, nextStage, assignedReviewers, actorMemberId);
        enqueueApplicantStatusChangedEvent(app, nextStatus, actorMemberId);
        return nextStatus;
    }

    private java.util.List<UUID> assignReviewStage(LoanApplication app,
                                                   ApprovalWorkflowStage stage,
                                                   LoanProductWorkflowService.WorkflowDefinition workflow) {
        boardReviewRepository.deleteByLoanApplicationIdAndReviewStage(app.getId(), stage);
        boardReviewRepository.flush();
        if (stage == ApprovalWorkflowStage.LOAN_OFFICER) {
            RoleDirectoryService.RoleAccountRef loanOfficer = roleDirectoryService
                .activeByAnyClaimInStation(app.getSaccoId(), app.getStationId(), java.util.List.of(
                    UserClaim.LOAN_OFFICER_QUEUE_ASSIGN,
                    UserClaim.LOAN_OFFICER_QUEUE_APPROVE
                )).stream()
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
            return java.util.List.of(loanOfficer.getId());
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
        UserClaim requiredClaim = reviewerClaimFor(stage);
        java.util.Optional<com.sacco.mvp.domain.LoanProductSetting> selectedProduct = app.getLoanProductSettingId() == null
            ? loanProductSettingRepository.findBySaccoIdAndLoanType(app.getSaccoId(), app.getLoanType())
            : loanProductSettingRepository.findByIdAndSaccoId(app.getLoanProductSettingId(), app.getSaccoId());
        return selectedProduct
            .map(productSetting -> loanProductBoardReviewerRepository.findByLoanProductSettingIdAndReviewStageOrderByCreatedAtAsc(productSetting.getId(), stage).stream()
                .map(com.sacco.mvp.domain.LoanProductBoardReviewer::getBoardMemberId)
                .filter(boardMemberId -> roleDirectoryService.hasActiveClaimInSacco(boardMemberId, app.getSaccoId(), requiredClaim))
                .toList())
            .orElse(java.util.List.of());
    }

    private void enqueueReviewAssignedEvents(LoanApplication app,
                                             ApprovalWorkflowStage stage,
                                             java.util.List<UUID> assignedReviewers,
                                             UUID actorMemberId) {
        if (stage == null) {
            return;
        }
        String eventType = approvalFlowService.reviewAssignedEventType(stage);
        reviewNotificationRecipients(app, stage, assignedReviewers)
            .forEach(reviewerId -> outboxService.enqueue("LOAN", app.getId(), eventType, reviewerId,
                actorMemberId, app.getSaccoId(), app.getStationId(),
                Map.of(
                    "loanId", app.getId().toString(),
                    "reviewStage", stage.name(),
                    "reviewerMemberId", reviewerId.toString()
                )));
    }

    private void enqueueApplicantStatusChangedEvent(LoanApplication app, LoanStatus status, UUID actorMemberId) {
        if (app.getApplicantMemberId() == null || status == null) {
            return;
        }
        outboxService.enqueue("LOAN", app.getId(), "LOAN_STATUS_" + status.name(), app.getApplicantMemberId(),
            actorMemberId, app.getSaccoId(), app.getStationId(),
            Map.of(
                "loanId", app.getId().toString(),
                "status", status.name()
            ));
    }

    private java.util.List<UUID> reviewNotificationRecipients(LoanApplication app,
                                                              ApprovalWorkflowStage stage,
                                                              java.util.List<UUID> assignedReviewers) {
        if (assignedReviewers != null && !assignedReviewers.isEmpty()) {
            return assignedReviewers.stream().distinct().toList();
        }
        UserClaim recipientClaim = reviewerClaimFor(stage);
        if (recipientClaim == null) {
            return java.util.List.of();
        }
        LinkedHashSet<UUID> recipients = new LinkedHashSet<>();
        roleDirectoryService.activeByClaimInStation(app.getSaccoId(), app.getStationId(), recipientClaim).stream()
            .map(RoleDirectoryService.RoleAccountRef::getId)
            .forEach(recipients::add);
        if (stage == ApprovalWorkflowStage.DISBURSEMENT_OFFICER) {
            roleDirectoryService.activeByClaimInStation(app.getSaccoId(), app.getStationId(), UserClaim.DISBURSEMENT_QUEUE_VIEW).stream()
                .map(RoleDirectoryService.RoleAccountRef::getId)
                .forEach(recipients::add);
            roleDirectoryService.activeByClaimInStation(app.getSaccoId(), app.getStationId(), UserClaim.DISBURSEMENT_QUEUE_DISBURSE).stream()
                .map(RoleDirectoryService.RoleAccountRef::getId)
                .forEach(recipients::add);
        }
        return java.util.List.copyOf(recipients);
    }

    private UserClaim reviewerClaimFor(ApprovalWorkflowStage stage) {
        if (stage == null) {
            return null;
        }
        return switch (stage) {
            case MANAGER -> UserClaim.MANAGER_QUEUE_APPROVE;
            case LOAN_OFFICER -> UserClaim.LOAN_OFFICER_QUEUE_APPROVE;
            case CHAIRPERSON -> UserClaim.CHAIRPERSON_QUEUE_APPROVE;
            case BOARD -> UserClaim.BOARD_QUEUE_APPROVE;
            case CREDIT_COMMITTEE -> UserClaim.CREDIT_COMMITTEE_QUEUE_APPROVE;
            case ACCOUNTANT -> UserClaim.ACCOUNTANT_QUEUE_APPROVE;
            case DISBURSEMENT_OFFICER -> UserClaim.DISBURSEMENT_QUEUE_VIEW;
        };
    }

    private boolean requiresReviewAssignment(ApprovalWorkflowStage stage) {
        return stage == ApprovalWorkflowStage.LOAN_OFFICER || isAssignedReviewerStage(stage);
    }

    private boolean isAssignedReviewerStage(ApprovalWorkflowStage stage) {
        return stage == ApprovalWorkflowStage.CHAIRPERSON
            || stage == ApprovalWorkflowStage.BOARD
            || stage == ApprovalWorkflowStage.CREDIT_COMMITTEE;
    }
}
