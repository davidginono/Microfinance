package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.GuarantorRequestStatus;
import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import com.sacco.mvp.integration.foresight.UpstreamAvailabilityException;
import com.sacco.mvp.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanWorkflowServiceTest {

    @Mock private LoanProductSettingRepository loanProductSettingRepository;
    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private GuarantorRequestRepository guarantorRequestRepository;
    @Mock private BoardReviewRepository boardReviewRepository;
    @Mock private ManagerReviewRepository managerReviewRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private SavingsAccountRepository savingsAccountRepository;
    @Mock private SaccoSettingsRepository saccoSettingsRepository;
    @Mock private FormSchemaService formSchemaService;
    @Mock private EligibilityService eligibilityService;
    @Mock private OutboxService outboxService;
    @Mock private LoanAttachmentService loanAttachmentService;
    @Mock private FinancialDetailsService financialDetailsService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private ForesightDirectoryService foresightDirectoryService;
    @Mock private SaccoConfigurationService saccoConfigurationService;
    @Mock private ApplicationNumberService applicationNumberService;
    @Mock private RoleDirectoryService roleDirectoryService;
    @Mock private LoanProductWorkflowService loanProductWorkflowService;
    @Mock private WorkflowRoutingService workflowRoutingService;

    @InjectMocks
    private LoanWorkflowService loanWorkflowService;

    @Test
    void saveDraftRejectsEditingApplicationOutsideDraftStatus() {
        UUID appId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";

        LoanProductSetting product = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .guarantorsRequired(2)
            .maxRepaymentMonths(12)
            .formSchema("{}")
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        LoanApplication existing = LoanApplication.builder()
            .id(appId)
            .saccoId(saccoId)
            .applicantMemberId(memberId)
            .status(LoanStatus.READY_FOR_MANAGER)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(loanApplicationRepository.findByIdAndApplicantMemberId(appId, memberId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> loanWorkflowService.saveDraft(
            saccoId,
            memberId,
            LoanType.DEVELOPMENT_LOAN,
            new BigDecimal("1000"),
            6,
            Map.of("purpose", "Working capital"),
            appId,
            List.of(UUID.randomUUID(), UUID.randomUUID()),
            "{\"balance\":1000}",
            null,
            null
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Only DRAFT applications can be edited.");

        verify(loanApplicationRepository, never()).save(any(LoanApplication.class));
        verify(loanAttachmentService, never()).store(any(), any(), anyString());
    }

    @Test
    void saveDraftRejectsTopUpWhenSourceLoanIsAlreadyDisbursed() {
        UUID memberId = UUID.randomUUID();
        UUID sourceLoanId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";

        LoanApplication sourceLoan = LoanApplication.builder()
            .id(sourceLoanId)
            .saccoId(saccoId)
            .applicantMemberId(memberId)
            .status(LoanStatus.FINAL_APPROVED)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(loanApplicationRepository.findByIdAndApplicantMemberId(sourceLoanId, memberId)).thenReturn(Optional.of(sourceLoan));

        assertThatThrownBy(() -> loanWorkflowService.saveDraft(
            saccoId,
            memberId,
            LoanType.DEVELOPMENT_LOAN,
            new BigDecimal("1000"),
            6,
            Map.of("purpose", "Working capital"),
            null,
            List.of(),
            "{\"balance\":1000}",
            sourceLoanId,
            null
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Disbursed loans cannot be topped up.");

        verify(loanApplicationRepository, never()).save(any(LoanApplication.class));
        verify(loanAttachmentService, never()).store(any(), any(), anyString());
        verifyNoInteractions(formSchemaService, eligibilityService, applicationNumberService);
    }

    @Test
    void loanAdvanceSkipsGuarantorStageOnSubmit() {
        UUID appId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";

        LoanApplication app = LoanApplication.builder()
            .id(appId)
            .applicantMemberId(memberId)
            .saccoId(saccoId)
            .loanType(LoanType.LOAN_ADVANCE)
            .amount(new BigDecimal("1000"))
            .tenorMonths(3)
            .status(LoanStatus.DRAFT)
            .requiredGuarantors(0)
            .financialSnapshot("{}")
            .formData("{}")
            .policySnapshot("{}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .version(0)
            .build();

        when(loanApplicationRepository.findByIdAndApplicantMemberId(appId, memberId)).thenReturn(Optional.of(app));
        when(eligibilityService.check(saccoId, memberId, LoanType.LOAN_ADVANCE, new BigDecimal("1000")))
            .thenReturn(new EligibilityService.EligibilityResult(true, new BigDecimal("0.3333"), new BigDecimal("9000"),
                new BigDecimal("2999.70")));
        when(financialDetailsService.generateSnapshot(saccoId, memberId, LoanType.LOAN_ADVANCE, new BigDecimal("1000"), 3, null))
            .thenReturn(Map.of("interestMethod", "FLAT_RATE", "interestRate", BigDecimal.ZERO));
        doAnswer(invocation -> {
            LoanApplication target = invocation.getArgument(0);
            target.setStatus(LoanStatus.READY_FOR_MANAGER);
            return LoanStatus.READY_FOR_MANAGER;
        }).when(workflowRoutingService).moveToFirstReviewStage(any(LoanApplication.class), eq(memberId));
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        LoanApplication submitted = loanWorkflowService.submit(appId, memberId);

        assertThat(submitted.getStatus()).isEqualTo(LoanStatus.READY_FOR_MANAGER);
        verify(guarantorRequestRepository, never()).save(any());
    }

    @Test
    void evaluateReadinessRequiresAllConfiguredGuarantorApprovals() {
        UUID appId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";

        LoanApplication app = LoanApplication.builder()
            .id(appId)
            .applicantMemberId(memberId)
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .amount(new BigDecimal("90000"))
            .tenorMonths(12)
            .status(LoanStatus.AWAITING_GUARANTORS)
            .requiredGuarantors(3)
            .formData("{}")
            .policySnapshot("{}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .version(0)
            .build();

        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(guarantorRequestRepository.countByLoanApplicationIdAndStatus(appId, GuarantorRequestStatus.APPROVED))
            .thenReturn(2L);
        when(eligibilityService.check(saccoId, memberId, LoanType.DEVELOPMENT_LOAN, new BigDecimal("90000")))
            .thenReturn(new EligibilityService.EligibilityResult(true, new BigDecimal("0.3333"), new BigDecimal("300000"),
                new BigDecimal("99990.00")));

        loanWorkflowService.evaluateReadiness(appId);

        verify(loanApplicationRepository, never()).save(any(LoanApplication.class));
        verify(outboxService, never()).enqueue(any(), any(), any(), any(), any());
    }

    @Test
    void evaluateReadinessMovesToReadyWhenAllRequiredGuarantorsApproved() {
        UUID appId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";

        LoanApplication app = LoanApplication.builder()
            .id(appId)
            .applicantMemberId(memberId)
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .amount(new BigDecimal("90000"))
            .tenorMonths(12)
            .status(LoanStatus.AWAITING_GUARANTORS)
            .requiredGuarantors(3)
            .formData("{}")
            .policySnapshot("{}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .version(0)
            .build();

        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(guarantorRequestRepository.countByLoanApplicationIdAndStatus(appId, GuarantorRequestStatus.APPROVED))
            .thenReturn(3L);
        when(eligibilityService.check(saccoId, memberId, LoanType.DEVELOPMENT_LOAN, new BigDecimal("90000")))
            .thenReturn(new EligibilityService.EligibilityResult(true, new BigDecimal("0.3333"), new BigDecimal("300000"),
                new BigDecimal("99990.00")));
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        loanWorkflowService.evaluateReadiness(appId);

        assertThat(app.getStatus()).isEqualTo(LoanStatus.ALL_GUARANTORS_APPROVED);
        verify(loanApplicationRepository).save(app);
        verify(outboxService).enqueue(eq("LOAN"), eq(appId), eq("LOAN_GUARANTORS_APPROVED"), eq(memberId), any());
    }

    @Test
    void approveGuarantorRequestStoresSignatureAndKeepsStageOpenUntilAllApprovalsExist() {
        UUID requestId = UUID.randomUUID();
        UUID guarantorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        OffsetDateTime verifiedAt = OffsetDateTime.now();

        GuarantorRequest request = GuarantorRequest.builder()
            .id(requestId)
            .loanApplicationId(appId)
            .guarantorMemberId(guarantorId)
            .status(GuarantorRequestStatus.PENDING)
            .requestedAmount(new BigDecimal("15000"))
            .createdAt(OffsetDateTime.now())
            .build();

        LoanApplication app = LoanApplication.builder()
            .id(appId)
            .applicantMemberId(UUID.randomUUID())
            .saccoId("CIRCLE-1001")
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .amount(new BigDecimal("90000"))
            .tenorMonths(12)
            .status(LoanStatus.AWAITING_GUARANTORS)
            .requiredGuarantors(2)
            .formData("{}")
            .policySnapshot("{}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .version(0)
            .build();

        when(guarantorRequestRepository.findByIdAndGuarantorMemberId(requestId, guarantorId)).thenReturn(Optional.of(request));
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(guarantorRequestRepository.countByLoanApplicationIdAndStatus(appId, GuarantorRequestStatus.APPROVED))
            .thenReturn(1L);

        loanWorkflowService.approveGuarantorRequest(requestId, guarantorId, "Guarantor Signer", verifiedAt);

        assertThat(request.getStatus()).isEqualTo(GuarantorRequestStatus.APPROVED);
        assertThat(request.getGuarantorSignatureText()).isEqualTo("Guarantor Signer");
        assertThat(request.getGuarantorSignatureVerifiedAt()).isEqualTo(verifiedAt);
        assertThat(app.getStatus()).isEqualTo(LoanStatus.AWAITING_GUARANTORS);
        verify(outboxService, never()).enqueue(eq("LOAN"), eq(appId), eq("LOAN_GUARANTORS_APPROVED"), any(), any());
    }

    @Test
    void submitBlocksWhenFreshFinancialDataIsRequiredAndUpstreamIsUnavailable() {
        UUID appId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";

        LoanApplication app = LoanApplication.builder()
            .id(appId)
            .applicantMemberId(memberId)
            .saccoId(saccoId)
            .stationId("ST01")
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .amount(new BigDecimal("150000"))
            .tenorMonths(6)
            .status(LoanStatus.DRAFT)
            .requiredGuarantors(0)
            .financialSnapshot("{\"requestedAmount\":150000}")
            .formData("{}")
            .policySnapshot("{}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .version(0)
            .build();
        LoanProductSetting product = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .freshFinancialDataRequired(true)
            .managerReviewRequired(true)
            .committeeReviewRequired(false)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        Member member = Member.builder()
            .id(memberId)
            .memberNo("MEM001")
            .stationId("ST01")
            .build();

        when(loanApplicationRepository.findByIdAndApplicantMemberId(appId, memberId)).thenReturn(Optional.of(app));
        when(loanProductSettingRepository.findBySaccoIdAndLoanType(saccoId, LoanType.DEVELOPMENT_LOAN)).thenReturn(Optional.of(product));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(financialDetailsService.generateSnapshot(saccoId, memberId, LoanType.DEVELOPMENT_LOAN, new BigDecimal("150000"), 6, null))
            .thenReturn(Map.of("interestMethod", "FLAT_RATE", "interestRate", new BigDecimal("0.1000")));
        when(foresightDirectoryService.fetchAccountSummary("MEM001", "ST01"))
            .thenThrow(new UpstreamAvailabilityException("down", null));

        assertThatThrownBy(() -> loanWorkflowService.submit(appId, memberId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Fresh financial data is required for this loan product, but the upstream financial service is unavailable right now. Try again later.");

        verify(loanApplicationRepository, never()).save(any(LoanApplication.class));
    }

    @Test
    void submitRefreshesFinancialSnapshotFromCurrentProductConfiguration() {
        UUID appId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";

        LoanApplication app = LoanApplication.builder()
            .id(appId)
            .applicantMemberId(memberId)
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .amount(new BigDecimal("150000"))
            .tenorMonths(6)
            .status(LoanStatus.DRAFT)
            .requiredGuarantors(0)
            .financialSnapshot("{\"interestMethod\":\"FLAT_RATE\",\"interestRate\":0.1000}")
            .formData("{}")
            .policySnapshot("{}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .version(0)
            .build();
        LoanProductSetting product = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .freshFinancialDataRequired(false)
            .managerReviewRequired(true)
            .committeeReviewRequired(false)
            .interestMethod(com.sacco.mvp.domain.InterestMethod.REDUCING_BALANCE)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(loanApplicationRepository.findByIdAndApplicantMemberId(appId, memberId)).thenReturn(Optional.of(app));
        when(loanProductSettingRepository.findBySaccoIdAndLoanType(saccoId, LoanType.DEVELOPMENT_LOAN)).thenReturn(Optional.of(product));
        when(financialDetailsService.generateSnapshot(saccoId, memberId, LoanType.DEVELOPMENT_LOAN, new BigDecimal("150000"), 6, null))
            .thenReturn(Map.of("interestMethod", "REDUCING_BALANCE", "interestRate", new BigDecimal("0.1200")));
        when(eligibilityService.check(saccoId, memberId, LoanType.DEVELOPMENT_LOAN, new BigDecimal("150000")))
            .thenReturn(new EligibilityService.EligibilityResult(true, new BigDecimal("0.3333"), new BigDecimal("600000"),
                new BigDecimal("199980.00")));
        doAnswer(invocation -> {
            LoanApplication target = invocation.getArgument(0);
            target.setStatus(LoanStatus.READY_FOR_MANAGER);
            return LoanStatus.READY_FOR_MANAGER;
        }).when(workflowRoutingService).moveToFirstReviewStage(any(LoanApplication.class), eq(memberId));
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        LoanApplication submitted = loanWorkflowService.submit(appId, memberId);

        assertThat(submitted.getStatus()).isEqualTo(LoanStatus.READY_FOR_MANAGER);
        assertThat(submitted.getFinancialSnapshot()).contains("REDUCING_BALANCE");
        assertThat(submitted.getFinancialSnapshot()).contains("0.1200");
    }
}
