package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.RepaymentFrequency;
import com.sacco.mvp.domain.SaccoSettings;
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
import java.time.LocalDate;
import java.util.List;
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

    @Test
    void disburseLoanRequiresApplicantFeeReceiptWhenPaymentInstructionsAreConfigured() {
        UUID loanId = UUID.randomUUID();
        UUID officerId = UUID.randomUUID();
        String saccoId = "SACCO-A";
        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .saccoId(saccoId)
            .stationId("ST01")
            .applicantMemberId(UUID.randomUUID())
            .status(LoanStatus.READY_FOR_DISBURSEMENT)
            .attachmentsJson("[]")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        Member officer = Member.builder()
            .id(officerId)
            .saccoId(saccoId)
            .stationId("ST01")
            .status(MemberStatus.ACTIVE)
            .build();
        SaccoSettings settings = SaccoSettings.builder()
            .saccoId(saccoId)
            .loanFeePaymentAccount("123456")
            .build();

        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
        when(memberRepository.findById(officerId)).thenReturn(Optional.of(officer));
        when(saccoSettingsRepository.findById(saccoId)).thenReturn(Optional.of(settings));
        when(loanAttachmentService.parse("[]")).thenReturn(List.of());

        assertThatThrownBy(() -> managerService.disburseLoan(
            loanId,
            officerId,
            LocalDate.now(),
            LocalDate.now().plusMonths(1),
            RepaymentFrequency.MONTHLY,
            null,
            "12345",
            "REF-1",
            "",
            null
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("The applicant must upload the insurance and application fees payment receipt before disbursement.");

        verify(repaymentScheduleService, never()).buildSchedule(any(), any(), any(), any(), any(), any(), any());
        verify(loanApplicationRepository, never()).save(any(LoanApplication.class));
    }
}
