package com.sacco.mvp.service;

import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.ReversalRequest;
import com.sacco.mvp.domain.ReversalRequestStatus;
import com.sacco.mvp.domain.ReversalRequestType;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.ReversalRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReversalRequestServiceTest {

    @Mock private ReversalRequestRepository reversalRequestRepository;
    @Mock private GuarantorRequestRepository guarantorRequestRepository;
    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private LoanWorkflowService loanWorkflowService;
    @Mock private RoleDirectoryService roleDirectoryService;
    @Mock private OutboxService outboxService;

    @InjectMocks
    private ReversalRequestService reversalRequestService;

    @Test
    void decideManagerStageWithdrawalRejectsManagerFromAnotherSacco() {
        UUID requestId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        ReversalRequest request = ReversalRequest.builder()
            .id(requestId)
            .saccoId("SACCO-A")
            .loanApplicationId(UUID.randomUUID())
            .type(ReversalRequestType.MANAGER_STAGE_WITHDRAWAL)
            .status(ReversalRequestStatus.PENDING)
            .requesterMemberId(UUID.randomUUID())
            .approverRole(Position.MANAGER)
            .createdAt(OffsetDateTime.now())
            .build();

        when(reversalRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(roleDirectoryService.hasActiveRoleInSacco(managerId, "SACCO-A", Position.MANAGER)).thenReturn(false);

        assertThatThrownBy(() -> reversalRequestService.decideManagerStageWithdrawal(requestId, managerId, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Forbidden");

        verify(loanApplicationRepository, never()).findById(org.mockito.ArgumentMatchers.any());
        verify(loanWorkflowService, never()).removeApplicationAtManagerStage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void decideManagerStageWithdrawalRejectRequiresReason() {
        UUID requestId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        ReversalRequest request = ReversalRequest.builder()
            .id(requestId)
            .saccoId("SACCO-A")
            .loanApplicationId(loanId)
            .type(ReversalRequestType.MANAGER_STAGE_WITHDRAWAL)
            .status(ReversalRequestStatus.PENDING)
            .requesterMemberId(UUID.randomUUID())
            .approverRole(Position.MANAGER)
            .createdAt(OffsetDateTime.now())
            .build();
        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .saccoId("SACCO-A")
            .build();

        when(reversalRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(roleDirectoryService.hasActiveRoleInSacco(managerId, "SACCO-A", Position.MANAGER)).thenReturn(true);
        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> reversalRequestService.decideManagerStageWithdrawal(requestId, managerId, false, " "))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Enter a reason before declining this request.");

        verify(reversalRequestRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(loanWorkflowService, never()).removeApplicationAtManagerStage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void decideGuarantorUndoCannotBeDeclinedByApplicant() {
        UUID requestId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        ReversalRequest request = ReversalRequest.builder()
            .id(requestId)
            .saccoId("SACCO-A")
            .loanApplicationId(loanId)
            .guarantorRequestId(UUID.randomUUID())
            .type(ReversalRequestType.GUARANTOR_DECISION_UNDO)
            .status(ReversalRequestStatus.PENDING)
            .requesterMemberId(UUID.randomUUID())
            .approverMemberId(applicantId)
            .createdAt(OffsetDateTime.now())
            .build();
        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .saccoId("SACCO-A")
            .build();

        when(reversalRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> reversalRequestService.decideGuarantorUndo(requestId, applicantId, false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Guarantor removal requests can only be approved by the applicant.");

        verify(reversalRequestRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(loanWorkflowService, never()).removeGuarantorFromLoan(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
