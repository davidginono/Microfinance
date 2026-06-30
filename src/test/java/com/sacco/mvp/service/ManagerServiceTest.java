package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.RepaymentFrequency;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerServiceTest {

    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private LoanProductSettingRepository loanProductSettingRepository;
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
    void disburseLoanStoresDepositAmountSeparatelyFromApprovedPrincipal() {
        UUID loanId = UUID.randomUUID();
        UUID officerId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .saccoId("SACCO-A")
            .stationId("ST-1")
            .applicantMemberId(UUID.randomUUID())
            .loanType(LoanType.EMERGENCY_LOAN)
            .amount(new BigDecimal("150000.00"))
            .tenorMonths(6)
            .status(LoanStatus.READY_FOR_DISBURSEMENT)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        Member officer = Member.builder()
            .id(officerId)
            .saccoId("SACCO-A")
            .stationId("ST-1")
            .status(MemberStatus.ACTIVE)
            .build();
        MockMultipartFile proof = new MockMultipartFile(
            "disbursementProofFile",
            "proof.pdf",
            "application/pdf",
            "proof".getBytes()
        );

        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
        when(memberRepository.findById(officerId)).thenReturn(Optional.of(officer));
        when(loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue("SACCO-A", LoanType.EMERGENCY_LOAN))
            .thenReturn(Optional.of(LoanProductSetting.builder().disbursementProofRequired(true).build()));
        when(loanApplicationRepository.existsBySaccoIdAndLoanId("SACCO-A", "12345")).thenReturn(false);
        when(repaymentScheduleService.buildSchedule(
            eq(app),
            eq(LocalDate.of(2026, 6, 3)),
            eq(LocalDate.of(2026, 7, 3)),
            eq(RepaymentFrequency.MONTHLY),
            eq(null),
            eq(null),
            eq("Release notes")
        )).thenAnswer(invocation -> {
            org.assertj.core.api.Assertions.assertThat(app.getAmount()).isEqualByComparingTo("150000.00");
            org.assertj.core.api.Assertions.assertThat(app.getDepositAmount()).isEqualByComparingTo("125000.00");
            return new RepaymentScheduleService.ScheduleResult(
                "{\"disbursedPrincipal\":150000.00,\"schedule\":[]}",
                LocalDate.of(2026, 12, 3),
                new BigDecimal("20833.33"),
                6
            );
        });
        when(loanAttachmentService.store(eq(loanId), any(), eq(null), eq(LoanAttachmentService.CATEGORY_DISBURSEMENT_PROOF)))
            .thenReturn("[]");

        managerService.disburseLoan(
            loanId,
            officerId,
            LocalDate.of(2026, 6, 3),
            LocalDate.of(2026, 7, 3),
            null,
            null,
            new BigDecimal("125000"),
            "12345",
            null,
            "Release notes",
            proof
        );

        org.assertj.core.api.Assertions.assertThat(app.getAmount()).isEqualByComparingTo("150000.00");
        org.assertj.core.api.Assertions.assertThat(app.getDepositAmount()).isEqualByComparingTo("125000.00");
        org.assertj.core.api.Assertions.assertThat(app.getStatus()).isEqualTo(LoanStatus.FINAL_APPROVED);
        org.assertj.core.api.Assertions.assertThat(app.getRepaymentScheduleJson()).contains("150000.00");
        verify(loanApplicationRepository).save(app);
    }

    @Test
    void disburseLoanAllowsMissingProofWhenProductPolicyIsOptional() {
        UUID loanId = UUID.randomUUID();
        UUID officerId = UUID.randomUUID();
        LoanApplication app = readyDisbursementApplication(loanId);
        Member officer = activeOfficer(officerId);

        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
        when(memberRepository.findById(officerId)).thenReturn(Optional.of(officer));
        when(loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue("SACCO-A", LoanType.EMERGENCY_LOAN))
            .thenReturn(Optional.of(LoanProductSetting.builder().disbursementProofRequired(false).build()));
        when(repaymentScheduleService.buildSchedule(
            eq(app),
            eq(LocalDate.of(2026, 6, 3)),
            eq(LocalDate.of(2026, 7, 3)),
            eq(RepaymentFrequency.MONTHLY),
            eq(null),
            eq(null),
            eq(null)
        )).thenReturn(new RepaymentScheduleService.ScheduleResult(
            "{\"disbursedPrincipal\":125000.00,\"schedule\":[]}",
            LocalDate.of(2026, 12, 3),
            new BigDecimal("20833.33"),
            6
        ));

        managerService.disburseLoan(
            loanId,
            officerId,
            LocalDate.of(2026, 6, 3),
            LocalDate.of(2026, 7, 3),
            null,
            null,
            new BigDecimal("125000"),
            "12345",
            null,
            null,
            null
        );

        org.assertj.core.api.Assertions.assertThat(app.getStatus()).isEqualTo(LoanStatus.FINAL_APPROVED);
        verify(loanAttachmentService, never()).store(eq(loanId), any(), any(), eq(LoanAttachmentService.CATEGORY_DISBURSEMENT_PROOF));
        verify(loanApplicationRepository).save(app);
    }

    @Test
    void disburseLoanRequiresProofWhenProductPolicyIsRequired() {
        UUID loanId = UUID.randomUUID();
        UUID officerId = UUID.randomUUID();
        LoanApplication app = readyDisbursementApplication(loanId);
        Member officer = activeOfficer(officerId);

        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
        when(memberRepository.findById(officerId)).thenReturn(Optional.of(officer));
        when(loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue("SACCO-A", LoanType.EMERGENCY_LOAN))
            .thenReturn(Optional.of(LoanProductSetting.builder().disbursementProofRequired(true).build()));

        assertThatThrownBy(() -> managerService.disburseLoan(
            loanId,
            officerId,
            LocalDate.of(2026, 6, 3),
            LocalDate.of(2026, 7, 3),
            null,
            null,
            new BigDecimal("125000"),
            "12345",
            null,
            null,
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Disbursement proof file is required to disburse this loan");

        verify(repaymentScheduleService, never()).buildSchedule(any(), any(), any(), any(), any(), any(), any());
        verify(loanApplicationRepository, never()).save(app);
    }

    private LoanApplication readyDisbursementApplication(UUID loanId) {
        return LoanApplication.builder()
            .id(loanId)
            .saccoId("SACCO-A")
            .stationId("ST-1")
            .applicantMemberId(UUID.randomUUID())
            .loanType(LoanType.EMERGENCY_LOAN)
            .amount(new BigDecimal("150000.00"))
            .tenorMonths(6)
            .status(LoanStatus.READY_FOR_DISBURSEMENT)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private Member activeOfficer(UUID officerId) {
        return Member.builder()
            .id(officerId)
            .saccoId("SACCO-A")
            .stationId("ST-1")
            .status(MemberStatus.ACTIVE)
            .build();
    }

}
