package com.sacco.mvp.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.RepaymentFrequency;
import com.sacco.mvp.domain.UserClaim;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerServiceTest {

    @Test
    void borrowerCannotDisburseTheirOwnLoan() {
        UUID actor = UUID.randomUUID();
        var loan = LoanApplication.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").applicantMemberId(actor).build();
        when(loanApplicationRepository.findForDisbursement(loan.getId())).thenReturn(Optional.of(loan));
        when(memberRepository.findById(actor)).thenReturn(Optional.of(Member.builder().id(actor).saccoId("I1").stationId("B1").status(MemberStatus.ACTIVE).build()));
        when(roleDirectoryService.hasActiveClaimInSacco(actor, "I1", UserClaim.DISBURSEMENT_QUEUE_DISBURSE)).thenReturn(true);
        assertThatThrownBy(() -> managerService.disburseLoan(loan.getId(), actor, LocalDate.now(), null, null, null, null, null, null, null, null))
            .hasMessage("staffClient.error.ownLoan");
        verify(loanApplicationRepository, never()).save(any());
    }

    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private LoanProductSettingRepository loanProductSettingRepository;
    @Mock private ManagerReviewRepository managerReviewRepository;
    @Mock private BoardReviewRepository boardReviewRepository;
    @Mock private GuarantorRequestRepository guarantorRequestRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private SaccoSettingsRepository saccoSettingsRepository;
    @Mock private OutboxService outboxService;
    @Mock private RoleDirectoryService roleDirectoryService;
    @Mock private LoanAttachmentService loanAttachmentService;
    @Mock private WorkflowRoutingService workflowRoutingService;
    @Mock private AuditService auditService;
    @Mock private RepaymentScheduleService repaymentScheduleService;
    @Mock private LoanRepaymentLedgerService loanRepaymentLedgerService;
    @Mock private com.sacco.mvp.accounting.loans.service.LoanPostingGuard accountingGuard;
    @Mock private com.sacco.mvp.accounting.loans.service.LoanDisbursementNumberService disbursementNumbers;
    @Spy private ApplicationClock applicationClock=new ApplicationClock("Africa/Dar_es_Salaam");
    @Spy private ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @InjectMocks
    private ManagerService managerService;

    @org.junit.jupiter.api.BeforeEach void automaticLoanNumber(){
        org.mockito.Mockito.lenient().doAnswer(call->{LoanApplication loan=call.getArgument(0);loan.setLoanId("1");loan.setLoanAccountingPeriodId(UUID.randomUUID());return null;}).when(disbursementNumbers).assign(any(),any(),any());
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

        when(loanApplicationRepository.findForDisbursement(loanId)).thenReturn(Optional.of(app));
        when(memberRepository.findById(officerId)).thenReturn(Optional.of(officer));
        when(roleDirectoryService.hasActiveClaimInSacco(officerId, "SACCO-A", UserClaim.DISBURSEMENT_QUEUE_DISBURSE))
            .thenReturn(true);
        when(loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue("SACCO-A", LoanType.EMERGENCY_LOAN))
            .thenReturn(Optional.of(LoanProductSetting.builder().disbursementProofRequired(true).build()));
        when(loanAttachmentService.store(eq(loanId), any(), eq(null), eq(LoanAttachmentService.CATEGORY_DISBURSEMENT_PROOF)))
            .thenReturn("[]");
        stubRepaymentSchedule(LocalDate.of(2026, 12, 3), new BigDecimal("25000.00"));

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
        org.assertj.core.api.Assertions.assertThat(app.getStatus()).isEqualTo(LoanStatus.DISBURSED);
        org.assertj.core.api.Assertions.assertThat(app.getRepaymentScheduleJson()).isEqualTo("{\"schedule\":[]}");
        org.assertj.core.api.Assertions.assertThat(app.getFirstRepaymentDate()).isEqualTo(LocalDate.of(2026, 7, 3));
        org.assertj.core.api.Assertions.assertThat(app.getFinalDueDate()).isEqualTo(LocalDate.of(2026, 12, 3));
        org.assertj.core.api.Assertions.assertThat(app.getInstallmentAmount()).isEqualByComparingTo("25000.00");
        org.assertj.core.api.Assertions.assertThat(app.getRepaymentFrequency()).isEqualTo(RepaymentFrequency.MONTHLY);
        verify(loanApplicationRepository).save(app);
    }

    @Test
    void disburseTopUpNeverTreatsLegacySnapshotAsVerifiedSettlement() {
        UUID loanId = UUID.randomUUID();
        UUID sourceLoanId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID officerId = UUID.randomUUID();
        LoanApplication sourceLoan = LoanApplication.builder()
            .id(sourceLoanId)
            .saccoId("SACCO-A")
            .stationId("ST-1")
            .applicantMemberId(applicantId)
            .status(LoanStatus.DISBURSED)
            .amount(new BigDecimal("420000.00"))
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .saccoId("SACCO-A")
            .stationId("ST-1")
            .applicantMemberId(applicantId)
            .loanType(LoanType.EMERGENCY_LOAN)
            .amount(new BigDecimal("500000.00"))
            .tenorMonths(6)
            .topUpSourceLoanId(sourceLoanId)
            .financialSnapshot("""
                {
                  "requestedAmount": 80000.00,
                  "topUpRequestedAmount": 80000.00,
                  "topUpSettlementAmount": 420000.00,
                  "principalAmount": 500000.00
                }
                """)
            .status(LoanStatus.READY_FOR_DISBURSEMENT)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        Member officer = activeOfficer(officerId);

        when(loanApplicationRepository.findForDisbursement(loanId)).thenReturn(Optional.of(app));
        when(loanApplicationRepository.findById(sourceLoanId)).thenReturn(Optional.of(sourceLoan));
        when(memberRepository.findById(officerId)).thenReturn(Optional.of(officer));
        when(roleDirectoryService.hasActiveClaimInSacco(officerId, "SACCO-A", UserClaim.DISBURSEMENT_QUEUE_DISBURSE))
            .thenReturn(true);
        when(loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue("SACCO-A", LoanType.EMERGENCY_LOAN))
            .thenReturn(Optional.of(LoanProductSetting.builder().disbursementProofRequired(false).build()));
        stubRepaymentSchedule(LocalDate.of(2026, 12, 3), new BigDecimal("83333.33"));

        Runnable disburse = () -> managerService.disburseLoan(
            loanId,
            officerId,
            LocalDate.of(2026, 6, 3),
            LocalDate.of(2026, 7, 3),
            null,
            null,
            new BigDecimal("75000"),
            "12345",
            null,
            "Top-up release",
            null
        );

        assertThatThrownBy(disburse::run).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("verified source balances");
        assertThat(sourceLoan.getStatus()).isEqualTo(LoanStatus.DISBURSED);
        assertThat(sourceLoan.getPaidAt()).isNull();
        assertThat(app.getStatus()).isEqualTo(LoanStatus.READY_FOR_DISBURSEMENT);
        verify(loanApplicationRepository, never()).save(sourceLoan);
        verify(loanApplicationRepository, never()).save(app);
        verify(loanRepaymentLedgerService, never()).openAtDisbursement(any());
    }

    @Test
    void disburseTopUpRejectsDepositAboveRequestedAmount() {
        UUID loanId = UUID.randomUUID();
        UUID sourceLoanId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID officerId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .saccoId("SACCO-A")
            .stationId("ST-1")
            .applicantMemberId(applicantId)
            .loanType(LoanType.EMERGENCY_LOAN)
            .amount(new BigDecimal("500000.00"))
            .tenorMonths(6)
            .topUpSourceLoanId(sourceLoanId)
            .financialSnapshot("""
                {
                  "requestedAmount": 80000.00,
                  "topUpRequestedAmount": 80000.00,
                  "topUpSettlementAmount": 420000.00,
                  "principalAmount": 500000.00
                }
                """)
            .status(LoanStatus.READY_FOR_DISBURSEMENT)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        Member officer = activeOfficer(officerId);

        when(loanApplicationRepository.findForDisbursement(loanId)).thenReturn(Optional.of(app));
        when(memberRepository.findById(officerId)).thenReturn(Optional.of(officer));
        when(roleDirectoryService.hasActiveClaimInSacco(officerId, "SACCO-A", UserClaim.DISBURSEMENT_QUEUE_DISBURSE))
            .thenReturn(true);

        assertThatThrownBy(() -> managerService.disburseLoan(
            loanId,
            officerId,
            LocalDate.of(2026, 6, 3),
            LocalDate.of(2026, 7, 3),
            null,
            null,
            new BigDecimal("90000"),
            "12345",
            null,
            "Top-up release",
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Deposit amount cannot be greater than the disbursement amount");

        verify(loanApplicationRepository, never()).save(any(LoanApplication.class));
    }

    @Test
    void disburseLoanAllowsMissingProofWhenProductPolicyIsOptional() {
        UUID loanId = UUID.randomUUID();
        UUID officerId = UUID.randomUUID();
        LoanApplication app = readyDisbursementApplication(loanId);
        Member officer = activeOfficer(officerId);

        when(loanApplicationRepository.findForDisbursement(loanId)).thenReturn(Optional.of(app));
        when(memberRepository.findById(officerId)).thenReturn(Optional.of(officer));
        when(roleDirectoryService.hasActiveClaimInSacco(officerId, "SACCO-A", UserClaim.DISBURSEMENT_QUEUE_DISBURSE))
            .thenReturn(true);
        when(loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue("SACCO-A", LoanType.EMERGENCY_LOAN))
            .thenReturn(Optional.of(LoanProductSetting.builder().disbursementProofRequired(false).build()));
        stubRepaymentSchedule(LocalDate.of(2026, 12, 3), new BigDecimal("25000.00"));
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

        org.assertj.core.api.Assertions.assertThat(app.getStatus()).isEqualTo(LoanStatus.DISBURSED);
        org.assertj.core.api.Assertions.assertThat(app.getRepaymentScheduleJson()).isEqualTo("{\"schedule\":[]}");
        verify(loanAttachmentService, never()).store(eq(loanId), any(), any(), eq(LoanAttachmentService.CATEGORY_DISBURSEMENT_PROOF));
        verify(loanApplicationRepository).save(app);
    }

    @Test
    void disburseLoanRequiresProofWhenProductPolicyIsRequired() {
        UUID loanId = UUID.randomUUID();
        UUID officerId = UUID.randomUUID();
        LoanApplication app = readyDisbursementApplication(loanId);
        Member officer = activeOfficer(officerId);

        when(loanApplicationRepository.findForDisbursement(loanId)).thenReturn(Optional.of(app));
        when(memberRepository.findById(officerId)).thenReturn(Optional.of(officer));
        when(roleDirectoryService.hasActiveClaimInSacco(officerId, "SACCO-A", UserClaim.DISBURSEMENT_QUEUE_DISBURSE))
            .thenReturn(true);
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

    private void stubRepaymentSchedule(LocalDate finalDueDate, BigDecimal installmentAmount) {
        when(repaymentScheduleService.buildSchedule(
            any(LoanApplication.class),
            any(LocalDate.class),
            any(LocalDate.class),
            any(RepaymentFrequency.class),
            nullable(BigDecimal.class),
            nullable(String.class),
            nullable(String.class)
        )).thenReturn(new RepaymentScheduleService.ScheduleResult(
            "{\"schedule\":[]}",
            finalDueDate,
            installmentAmount,
            6
        ));
    }

}
