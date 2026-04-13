package com.sacco.mvp.service;

import com.sacco.mvp.domain.Position;
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
}
