package com.sacco.mvp.service;

import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowRoutingServiceTest {

    @Mock private SaccoSettingsRepository saccoSettingsRepository;
    @Spy private ApprovalFlowService approvalFlowService = new ApprovalFlowService();
    @Mock private BoardReviewRepository boardReviewRepository;
    @Mock private RoleDirectoryService roleDirectoryService;
    @Mock private LoanProductWorkflowService loanProductWorkflowService;
    @Mock private OutboxService outboxService;

    @InjectMocks
    private WorkflowRoutingService workflowRoutingService;

    @Test
    void moveToFirstReviewStageUsesProductConfiguredLoanOfficerStart() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID loanOfficerId = UUID.randomUUID();

        LoanApplication app = LoanApplication.builder()
            .id(appId)
            .saccoId("SACCO-1")
            .status(LoanStatus.DRAFT)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(loanProductWorkflowService.resolveForApplication(app)).thenReturn(
            new LoanProductWorkflowService.WorkflowDefinition(
                List.of(ApprovalWorkflowStage.LOAN_OFFICER, ApprovalWorkflowStage.MANAGER, ApprovalWorkflowStage.DISBURSEMENT_OFFICER),
                ApprovalWorkflowStage.LOAN_OFFICER,
                true,
                true,
                false,
                3,
                0,
                0,
                false,
                4
            )
        );
        when(roleDirectoryService.activeByRoleInStation("SACCO-1", null, Position.LOAN_OFFICER)).thenReturn(List.of(
            RoleDirectoryService.RoleAccountRef.builder()
                .id(loanOfficerId)
                .saccoId("SACCO-1")
                .identifier("LO-1")
                .fullName("Loan Officer")
                .email("loan.officer@example.com")
                .memberBased(true)
                .build()
        ));

        workflowRoutingService.moveToFirstReviewStage(app, actorId);

        assertThat(app.getStatus()).isEqualTo(LoanStatus.AWAITING_LOAN_OFFICER);
        ArgumentCaptor<com.sacco.mvp.domain.BoardReview> captor = ArgumentCaptor.forClass(com.sacco.mvp.domain.BoardReview.class);
        verify(boardReviewRepository).save(captor.capture());
        assertThat(captor.getValue().getReviewStage()).isEqualTo(ApprovalWorkflowStage.LOAN_OFFICER);
        assertThat(captor.getValue().getDecision()).isEqualTo(BoardDecision.PENDING);
        verify(outboxService).enqueue(
            eq("LOAN"),
            eq(appId),
            eq("LOAN_OFFICER_REVIEW_ASSIGNED"),
            eq(actorId),
            eq("SACCO-1"),
            eq((String) null),
            any()
        );
    }
}
