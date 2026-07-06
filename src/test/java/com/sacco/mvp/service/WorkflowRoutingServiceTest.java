package com.sacco.mvp.service;

import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductBoardReviewer;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.LoanProductBoardReviewerRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowRoutingServiceTest {

    @Mock private SaccoSettingsRepository saccoSettingsRepository;
    @Spy private ApprovalFlowService approvalFlowService = new ApprovalFlowService();
    @Mock private BoardReviewRepository boardReviewRepository;
    @Mock private LoanProductBoardReviewerRepository loanProductBoardReviewerRepository;
    @Mock private LoanProductSettingRepository loanProductSettingRepository;
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
            eq(loanOfficerId),
            eq("SACCO-1"),
            eq((String) null),
            any()
        );
    }

    @Test
    void moveToCreditCommitteeStageUsesProductAssignedReviewers() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID reviewerOne = UUID.randomUUID();
        UUID reviewerTwo = UUID.randomUUID();

        LoanApplication app = LoanApplication.builder()
            .id(appId)
            .saccoId("SACCO-1")
            .stationId("AR704")
            .loanType(LoanType.LOAN_ADVANCE)
            .status(LoanStatus.DRAFT)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(loanProductWorkflowService.resolveForApplication(app)).thenReturn(
            new LoanProductWorkflowService.WorkflowDefinition(
                List.of(ApprovalWorkflowStage.CREDIT_COMMITTEE, ApprovalWorkflowStage.DISBURSEMENT_OFFICER),
                ApprovalWorkflowStage.CREDIT_COMMITTEE,
                false,
                false,
                true,
                3,
                2,
                2,
                false,
                4
            )
        );
        when(loanProductSettingRepository.findBySaccoIdAndLoanType("SACCO-1", LoanType.LOAN_ADVANCE))
            .thenReturn(Optional.of(LoanProductSetting.builder()
                .id(productId)
                .saccoId("SACCO-1")
                .loanType(LoanType.LOAN_ADVANCE)
                .build()));
        when(loanProductBoardReviewerRepository.findByLoanProductSettingIdAndReviewStageOrderByCreatedAtAsc(productId, ApprovalWorkflowStage.CREDIT_COMMITTEE))
            .thenReturn(List.of(
                LoanProductBoardReviewer.builder().boardMemberId(reviewerOne).build(),
                LoanProductBoardReviewer.builder().boardMemberId(reviewerTwo).build()
            ));
        when(roleDirectoryService.hasActiveRoleInSacco(reviewerOne, "SACCO-1", Position.CREDIT_COMMITTEE)).thenReturn(true);
        when(roleDirectoryService.hasActiveRoleInSacco(reviewerTwo, "SACCO-1", Position.CREDIT_COMMITTEE)).thenReturn(true);

        workflowRoutingService.moveToFirstReviewStage(app, actorId);

        assertThat(app.getStatus()).isEqualTo(LoanStatus.AWAITING_CREDIT_COMMITTEE);
        ArgumentCaptor<com.sacco.mvp.domain.BoardReview> captor = ArgumentCaptor.forClass(com.sacco.mvp.domain.BoardReview.class);
        verify(boardReviewRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(com.sacco.mvp.domain.BoardReview::getBoardMemberId)
            .containsExactly(reviewerOne, reviewerTwo);
        assertThat(captor.getAllValues())
            .extracting(com.sacco.mvp.domain.BoardReview::getReviewStage)
            .containsOnly(ApprovalWorkflowStage.CREDIT_COMMITTEE);
        verify(outboxService).enqueue(eq("LOAN"), eq(appId), eq("CREDIT_COMMITTEE_REVIEW_ASSIGNED"), eq(reviewerOne), eq("SACCO-1"), eq("AR704"), any());
        verify(outboxService).enqueue(eq("LOAN"), eq(appId), eq("CREDIT_COMMITTEE_REVIEW_ASSIGNED"), eq(reviewerTwo), eq("SACCO-1"), eq("AR704"), any());
    }
}
