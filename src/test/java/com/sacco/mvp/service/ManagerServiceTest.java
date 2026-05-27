package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerServiceTest {

    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private ManagerReviewRepository managerReviewRepository;
    @Mock private BoardReviewRepository boardReviewRepository;
    @Mock private GuarantorRequestRepository guarantorRequestRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private SaccoSettingsRepository saccoSettingsRepository;
    @Mock private OutboxService outboxService;
    @Mock private RepaymentScheduleService repaymentScheduleService;
    @Mock private RoleDirectoryService roleDirectoryService;
    @Mock private LoanPaymentTransactionSyncService loanPaymentTransactionSyncService;
    @Mock private LoanAttachmentService loanAttachmentService;
    @Mock private WorkflowRoutingService workflowRoutingService;

    @InjectMocks
    private ManagerService managerService;

    @Test
    void decideRejectsManagerFromAnotherSacco() {
        UUID loanId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .saccoId("SACCO-A")
            .applicantMemberId(UUID.randomUUID())
            .status(LoanStatus.READY_FOR_MANAGER)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
        when(roleDirectoryService.hasActiveRoleInSacco(managerId, "SACCO-A", Position.MANAGER)).thenReturn(false);

        assertThatThrownBy(() -> managerService.decide(loanId, managerId, ManagerDecision.REJECT, "No"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Forbidden");

        verify(managerReviewRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(loanApplicationRepository, never()).save(org.mockito.ArgumentMatchers.any(LoanApplication.class));
    }

}
