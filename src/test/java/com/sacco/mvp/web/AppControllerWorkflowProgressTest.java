package com.sacco.mvp.web;

import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import com.sacco.mvp.service.LoanProductWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppControllerWorkflowProgressTest {
    @Mock private LoanProductWorkflowService loanProductWorkflowService;
    @Mock private ManagerReviewRepository managerReviewRepository;
    @Mock private BoardReviewRepository boardReviewRepository;

    @InjectMocks private AppController controller;

    @Test
    void reviewStageRejectionShowsRejectedStepInsteadOfCurrentTick() {
        LoanApplication app = loanWithStatus(LoanStatus.CREDIT_COMMITTEE_REJECTED);
        when(loanProductWorkflowService.resolveForApplication(app)).thenReturn(workflow(
            List.of(
                ApprovalWorkflowStage.MANAGER,
                ApprovalWorkflowStage.CREDIT_COMMITTEE,
                ApprovalWorkflowStage.ACCOUNTANT,
                ApprovalWorkflowStage.DISBURSEMENT_OFFICER
            )
        ));
        when(managerReviewRepository.findByLoanApplicationIdAndReviewStageOrderByCreatedAtAsc(any(), any()))
            .thenReturn(List.of());
        when(boardReviewRepository.findByLoanApplicationIdAndReviewStage(any(), any()))
            .thenReturn(List.of());

        List<Map<String, Object>> steps = dashboardWorkflowSteps(app);

        assertThat(steps.get(3))
            .containsEntry("label", "Further Review")
            .containsEntry("stateKey", "rejected")
            .containsEntry("stateLabel", "Rejected")
            .containsEntry("metaLabel", "Rejected Stage")
            .containsEntry("nodeClasses", "member-dashboard-flow-node--rejected")
            .containsEntry("textClasses", "member-dashboard-flow-text--rejected")
            .containsEntry("numberClasses", "member-dashboard-flow-number--rejected")
            .containsEntry("connectorClasses", "member-dashboard-flow-connector--pending")
            .containsEntry("dateLabel", "29 Jul 2026, 07:05");
        assertThat(steps.get(2)).containsEntry("connectorClasses", "member-dashboard-flow-connector--completed");
        assertThat(steps.get(4))
            .containsEntry("stateKey", "closed")
            .containsEntry("stateLabel", "Closed")
            .containsEntry("nodeClasses", "member-dashboard-flow-node--closed")
            .containsEntry("textClasses", "member-dashboard-flow-text--closed")
            .containsEntry("dateLabel", "");
        assertThat(steps.get(5)).containsEntry("stateKey", "closed");
    }

    @Test
    void finalRejectedStatusMarksReadyForReleaseAsRejected() {
        LoanApplication app = loanWithStatus(LoanStatus.REJECTED);
        when(loanProductWorkflowService.resolveForApplication(app)).thenReturn(workflow(
            List.of(ApprovalWorkflowStage.MANAGER, ApprovalWorkflowStage.DISBURSEMENT_OFFICER)
        ));
        when(managerReviewRepository.findByLoanApplicationIdAndReviewStageOrderByCreatedAtAsc(any(), any()))
            .thenReturn(List.of());

        List<Map<String, Object>> steps = dashboardWorkflowSteps(app);

        assertThat(steps.get(4))
            .containsEntry("label", "Ready for Release")
            .containsEntry("stateKey", "rejected")
            .containsEntry("stateLabel", "Rejected")
            .containsEntry("nodeClasses", "member-dashboard-flow-node--rejected")
            .containsEntry("connectorClasses", "member-dashboard-flow-connector--pending")
            .containsEntry("dateLabel", "29 Jul 2026, 07:05");
        assertThat(steps.get(5))
            .containsEntry("stateKey", "closed")
            .containsEntry("stateLabel", "Closed")
            .containsEntry("nodeClasses", "member-dashboard-flow-node--closed");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> dashboardWorkflowSteps(LoanApplication app) {
        return (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
            controller,
            "buildDashboardWorkflowSteps",
            app
        );
    }

    private LoanApplication loanWithStatus(LoanStatus status) {
        return LoanApplication.builder()
            .id(UUID.randomUUID())
            .status(status)
            .requiredGuarantors(0)
            .createdAt(OffsetDateTime.parse("2026-07-14T10:29:00+03:00"))
            .submittedAt(OffsetDateTime.parse("2026-07-14T10:29:00+03:00"))
            .updatedAt(OffsetDateTime.parse("2026-07-29T07:05:00+03:00"))
            .build();
    }

    private LoanProductWorkflowService.WorkflowDefinition workflow(List<ApprovalWorkflowStage> stages) {
        return new LoanProductWorkflowService.WorkflowDefinition(
            stages,
            stages.get(0),
            stages.contains(ApprovalWorkflowStage.MANAGER),
            1,
            stages.contains(ApprovalWorkflowStage.LOAN_OFFICER),
            2,
            stages.contains(ApprovalWorkflowStage.CHAIRPERSON),
            3,
            stages.contains(ApprovalWorkflowStage.BOARD),
            4,
            stages.contains(ApprovalWorkflowStage.CREDIT_COMMITTEE),
            5,
            1,
            1,
            stages.contains(ApprovalWorkflowStage.ACCOUNTANT),
            6,
            stages.contains(ApprovalWorkflowStage.DISBURSEMENT_OFFICER)
        );
    }
}
