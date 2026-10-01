package com.sacco.mvp.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.ManagerReview;
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
    @Spy private ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

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
        when(roleDirectoryService.hasActiveClaimInSacco(managerId, "SACCO-A", UserClaim.MANAGER_QUEUE_REJECT)).thenReturn(false);

        assertThatThrownBy(() -> managerService.decide(
            loanId,
            managerId,
            ManagerDecision.REJECT,
            "No",
            "M. Manager",
            OffsetDateTime.parse("2026-07-01T09:00:00Z")
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Forbidden");

        verify(managerReviewRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(loanApplicationRepository, never()).save(org.mockito.ArgumentMatchers.any(LoanApplication.class));
    }

    @Test
    void rejectStoresManagerSignatureSnapshotAndClearsApplicantAcknowledgement() {
        UUID loanId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        OffsetDateTime acknowledgedAt = OffsetDateTime.parse("2026-06-30T09:00:00Z");
        OffsetDateTime signatureVerifiedAt = OffsetDateTime.parse("2026-07-01T09:00:00Z");
        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .saccoId("SACCO-A")
            .stationId("ST-1")
            .applicantMemberId(UUID.randomUUID())
            .status(LoanStatus.READY_FOR_MANAGER)
            .requiredGuarantors(0)
            .applicantRejectionAcknowledgedAt(acknowledgedAt)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
        when(roleDirectoryService.hasActiveClaimInSacco(managerId, "SACCO-A", UserClaim.MANAGER_QUEUE_REJECT)).thenReturn(true);

        managerService.decide(
            loanId,
            managerId,
            ManagerDecision.REJECT,
            "  Insufficient capacity  ",
            "  M. Manager  ",
            signatureVerifiedAt
        );

        org.mockito.ArgumentCaptor<ManagerReview> reviewCaptor = org.mockito.ArgumentCaptor.forClass(ManagerReview.class);
        verify(managerReviewRepository).save(reviewCaptor.capture());
        ManagerReview review = reviewCaptor.getValue();
        assertThat(review.getReviewStage()).isEqualTo(ApprovalWorkflowStage.MANAGER);
        assertThat(review.getDecision()).isEqualTo(ManagerDecision.REJECT);
        assertThat(review.getReasons()).isEqualTo("Insufficient capacity");
        assertThat(review.getManagerSignatureText()).isEqualTo("M. Manager");
        assertThat(review.getManagerSignatureVerifiedAt()).isEqualTo(signatureVerifiedAt);
        assertThat(app.getStatus()).isEqualTo(LoanStatus.MANAGER_REJECTED);
        assertThat(app.getApplicantRejectionAcknowledgedAt()).isNull();
        verify(loanApplicationRepository).save(app);
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
        when(roleDirectoryService.hasActiveClaimInSacco(officerId, "SACCO-A", UserClaim.DISBURSEMENT_QUEUE_DISBURSE))
            .thenReturn(true);
        when(loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue("SACCO-A", LoanType.EMERGENCY_LOAN))
            .thenReturn(Optional.of(LoanProductSetting.builder().disbursementProofRequired(true).build()));
        when(loanApplicationRepository.existsBySaccoIdAndLoanId("SACCO-A", "12345")).thenReturn(false);
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void disburseTopUpSettlesOnlyLegacySources(boolean sourceTracked) {
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

        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
        when(loanApplicationRepository.findById(sourceLoanId)).thenReturn(Optional.of(sourceLoan));
        when(memberRepository.findById(officerId)).thenReturn(Optional.of(officer));
        when(roleDirectoryService.hasActiveClaimInSacco(officerId, "SACCO-A", UserClaim.DISBURSEMENT_QUEUE_DISBURSE))
            .thenReturn(true);
        when(loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue("SACCO-A", LoanType.EMERGENCY_LOAN))
            .thenReturn(Optional.of(LoanProductSetting.builder().disbursementProofRequired(false).build()));
        when(loanRepaymentLedgerService.hasLedger(sourceLoanId)).thenReturn(sourceTracked);
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

        if (sourceTracked) {
            assertThatThrownBy(disburse::run).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verified settlement transaction");
            assertThat(sourceLoan.getStatus()).isEqualTo(LoanStatus.DISBURSED);
            verify(loanApplicationRepository, never()).save(sourceLoan);
            verify(loanRepaymentLedgerService, never()).openAtDisbursement(any());
            return;
        }
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        disburse.run();

        assertThat(app.getStatus()).isEqualTo(LoanStatus.DISBURSED);
        assertThat(app.getDepositAmount()).isEqualByComparingTo("75000.00");
        assertThat(sourceLoan.getStatus()).isEqualTo(LoanStatus.PAID);
        assertThat(sourceLoan.getPaidAt()).isNotNull();
        assertThat(sourceLoan.getPaidMarkedByManagerId()).isEqualTo(officerId);
        verify(loanApplicationRepository).save(sourceLoan);
        verify(loanApplicationRepository).save(app);
        verify(loanRepaymentLedgerService).openAtDisbursement(app);
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

        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
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

        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
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

        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
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
