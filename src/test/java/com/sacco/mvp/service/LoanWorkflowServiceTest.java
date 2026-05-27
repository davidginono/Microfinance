package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.GuarantorRequestStatus;
import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.SaccoSettings;
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
import java.util.LinkedHashMap;
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
    @Mock private LoanQualificationPolicyService loanQualificationPolicyService;

    @InjectMocks
    private LoanWorkflowService loanWorkflowService;

    @Test
    void submitToManagerDoesNotRequireLegacyFeeReceipt() {
        // Scenario: the removed fee receipt gate must not block manager handoff.
        // Given guarantors have approved and the loan has only normal application attachments
        // When the applicant submits the loan to manager review
        // Then the workflow advances without checking for a fee receipt.
        UUID appId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";
        LoanApplication app = LoanApplication.builder()
            .id(appId)
            .applicantMemberId(memberId)
            .saccoId(saccoId)
            .stationId("ST01")
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .amount(new BigDecimal("100000"))
            .tenorMonths(6)
            .status(LoanStatus.ALL_GUARANTORS_APPROVED)
            .requiredGuarantors(1)
            .financialSnapshot("{\"principalPlusInterest\":120000.00}")
            .attachmentsJson("[{\"attachmentCategory\":\"APPLICATION_ATTACHMENT\"}]")
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
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(loanApplicationRepository.findByIdAndApplicantMemberId(appId, memberId)).thenReturn(Optional.of(app));
        when(loanProductSettingRepository.findBySaccoIdAndLoanType(saccoId, LoanType.DEVELOPMENT_LOAN)).thenReturn(Optional.of(product));
        when(financialDetailsService.generateSnapshot(saccoId, memberId, LoanType.DEVELOPMENT_LOAN, new BigDecimal("100000"), 6, null))
            .thenReturn(Map.of("principalPlusInterest", new BigDecimal("120000.00")));
        when(eligibilityService.check(saccoId, memberId, LoanType.DEVELOPMENT_LOAN, new BigDecimal("100000")))
            .thenReturn(new EligibilityService.EligibilityResult(true, new BigDecimal("0.3333"), new BigDecimal("500000"),
                new BigDecimal("166650.00")));
        when(eligibilityService.policySnapshotJson(any(), anyInt(), any())).thenReturn("{}");
        doAnswer(invocation -> {
            LoanApplication target = invocation.getArgument(0);
            target.setStatus(LoanStatus.READY_FOR_MANAGER);
            return LoanStatus.READY_FOR_MANAGER;
        }).when(workflowRoutingService).moveToFirstReviewStage(any(LoanApplication.class), eq(memberId));
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoanApplication submitted = loanWorkflowService.submitToManager(appId, memberId);

        assertThat(submitted.getStatus()).isEqualTo(LoanStatus.READY_FOR_MANAGER);
        verifyNoInteractions(loanAttachmentService, saccoSettingsRepository);
    }

    @Test
    void saveDraftStoresSelectedGuarantorsWithoutCommitmentAmounts() {
        // Scenario: applicants choose guarantors only; no commitment split is stored for new drafts.
        UUID applicantId = UUID.randomUUID();
        UUID guarantorOne = UUID.randomUUID();
        UUID guarantorTwo = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";

        LoanProductSetting product = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .guarantorsRequired(2)
            .guarantorMinSavingsCheckRequired(false)
            .maxRepaymentMonths(12)
            .formSchema("{}")
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(formSchemaService.getSchema(saccoId, LoanType.DEVELOPMENT_LOAN)).thenReturn(product);
        when(formSchemaService.extractFormData(anyMap(), eq("{}"))).thenReturn(new LinkedHashMap<>(Map.of("purpose", "WORKING CAPITAL")));
        when(memberRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID memberId = invocation.getArgument(0);
            return Optional.of(activeMember(memberId, saccoId, "ST01"));
        });
        when(eligibilityService.check(saccoId, applicantId, LoanType.DEVELOPMENT_LOAN, new BigDecimal("100000")))
            .thenReturn(new EligibilityService.EligibilityResult(true, new BigDecimal("0.3333"), new BigDecimal("500000"),
                new BigDecimal("166650.00")));
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(loanAttachmentService.store(any(), any(), anyString())).thenAnswer(invocation -> invocation.getArgument(2));

        LoanApplication saved = loanWorkflowService.saveDraft(
            saccoId,
            applicantId,
            LoanType.DEVELOPMENT_LOAN,
            new BigDecimal("100000"),
            6,
            Map.of("purpose", "WORKING CAPITAL"),
            null,
            List.of(guarantorOne, guarantorTwo),
            "{\"principalPlusInterest\":120000.00}",
            null,
            null
        );

        assertThat(saved.getSelectedGuarantors()).contains(guarantorOne.toString(), guarantorTwo.toString());
        assertThat(saved.getSelectedGuarantors()).doesNotContain("amount");
    }

    @Test
    void saveDraftRejectsStaffOnlyGuarantorSelection() {
        // Scenario: staff-only accounts cannot be used as guarantors because they do not have member access.
        // Given an applicant selects a staff-only account as guarantor
        // When the applicant saves the draft
        // Then the draft is rejected before persistence.
        UUID applicantId = UUID.randomUUID();
        UUID staffOnlyGuarantor = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";

        LoanProductSetting product = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .guarantorsRequired(1)
            .maxRepaymentMonths(12)
            .formSchema("{}")
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(formSchemaService.getSchema(saccoId, LoanType.DEVELOPMENT_LOAN)).thenReturn(product);
        when(formSchemaService.extractFormData(anyMap(), eq("{}"))).thenReturn(new LinkedHashMap<>(Map.of("purpose", "WORKING CAPITAL")));
        when(memberRepository.findById(applicantId)).thenReturn(Optional.of(activeMember(applicantId, saccoId, "ST01")));
        when(memberRepository.findById(staffOnlyGuarantor)).thenReturn(Optional.of(staffOnlyMember(staffOnlyGuarantor, saccoId, "ST01")));

        assertThatThrownBy(() -> loanWorkflowService.saveDraft(
            saccoId,
            applicantId,
            LoanType.DEVELOPMENT_LOAN,
            new BigDecimal("100000"),
            6,
            Map.of("purpose", "WORKING CAPITAL"),
            null,
            List.of(staffOnlyGuarantor),
            "{\"principalPlusInterest\":120000.00}",
            null,
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Guarantor must have member access");

        verify(loanApplicationRepository, never()).save(any(LoanApplication.class));
    }

    @Test
    void searchGuarantorsExcludesStaffOnlyAccounts() {
        UUID applicantId = UUID.randomUUID();
        UUID staffOnlyId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";
        Member staffOnly = staffOnlyMember(staffOnlyId, saccoId, "ST01");
        staffOnly.setMemberNo("0101");

        when(memberRepository.findBySaccoIdAndStatusAndMemberNoIgnoreCase(saccoId, MemberStatus.ACTIVE, "0101"))
            .thenReturn(Optional.of(staffOnly));
        when(memberRepository.findBySaccoIdAndStatusOrderByFullNameAsc(saccoId, MemberStatus.ACTIVE))
            .thenReturn(List.of(staffOnly));

        var result = loanWorkflowService.searchGuarantors(saccoId, "ST01", applicantId, "0101", 0, 10);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void searchGuarantorCandidatesUsesProductMinimumSavingsToggle() {
        UUID applicantId = UUID.randomUUID();
        UUID guarantorId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";
        Member guarantor = activeMember(guarantorId, saccoId, "ST01");
        guarantor.setMemberNo("0101");
        LoanProductSetting product = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .guarantorMinSavingsCheckRequired(true)
            .formSchema("{}")
            .active(true)
            .build();

        when(formSchemaService.getSchema(saccoId, LoanType.DEVELOPMENT_LOAN)).thenReturn(product);
        when(memberRepository.findBySaccoIdAndStatusAndMemberNoIgnoreCase(saccoId, MemberStatus.ACTIVE, "0101"))
            .thenReturn(Optional.of(guarantor));
        when(loanQualificationPolicyService.guarantorFailureReason(saccoId, guarantorId, null, product))
            .thenReturn(Optional.of("Disabled: savings are below this loan product's guarantor minimum."));

        var result = loanWorkflowService.searchGuarantorCandidates(saccoId, "ST01", applicantId, "0101", LoanType.DEVELOPMENT_LOAN, 0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().eligible()).isFalse();
        assertThat(result.getFirst().disabledReason()).contains("minimum");
    }

    @Test
    void searchGuarantorCandidatesUsesStationActiveLoanPolicy() {
        UUID applicantId = UUID.randomUUID();
        UUID guarantorId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";
        Member guarantor = activeMember(guarantorId, saccoId, "ST01");
        guarantor.setMemberNo("0101");
        LoanProductSetting product = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .guarantorMinSavingsCheckRequired(false)
            .formSchema("{}")
            .active(true)
            .build();

        when(formSchemaService.getSchema(saccoId, LoanType.DEVELOPMENT_LOAN)).thenReturn(product);
        when(memberRepository.findBySaccoIdAndStatusAndMemberNoIgnoreCase(saccoId, MemberStatus.ACTIVE, "0101"))
            .thenReturn(Optional.of(guarantor));
        when(loanQualificationPolicyService.guarantorFailureReason(saccoId, guarantorId, null, product))
            .thenReturn(Optional.of("Disabled: active loans are not allowed for guarantors under the station policy."));

        var result = loanWorkflowService.searchGuarantorCandidates(saccoId, "ST01", applicantId, "0101", LoanType.DEVELOPMENT_LOAN, 0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().eligible()).isFalse();
        assertThat(result.getFirst().disabledReason()).contains("active loans");
    }

    @Test
    void saveDraftRejectsGuarantorWithActiveLoanWhenStationPolicyBlocksIt() {
        UUID applicantId = UUID.randomUUID();
        UUID guarantorId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";

        LoanProductSetting product = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .guarantorsRequired(1)
            .maxRepaymentMonths(12)
            .formSchema("{}")
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(formSchemaService.getSchema(saccoId, LoanType.DEVELOPMENT_LOAN)).thenReturn(product);
        when(formSchemaService.extractFormData(anyMap(), eq("{}"))).thenReturn(new LinkedHashMap<>(Map.of("purpose", "WORKING CAPITAL")));
        when(memberRepository.findById(applicantId)).thenReturn(Optional.of(activeMember(applicantId, saccoId, "ST01")));
        when(memberRepository.findById(guarantorId)).thenReturn(Optional.of(activeMember(guarantorId, saccoId, "ST01")));
        when(loanQualificationPolicyService.guarantorFailureReason(saccoId, guarantorId, new BigDecimal("100000"), product))
            .thenReturn(Optional.of("Disabled: active loans are not allowed for guarantors under the station policy."));

        assertThatThrownBy(() -> loanWorkflowService.saveDraft(
            saccoId,
            applicantId,
            LoanType.DEVELOPMENT_LOAN,
            new BigDecimal("100000"),
            6,
            Map.of("purpose", "WORKING CAPITAL"),
            null,
            List.of(guarantorId),
            "{\"principalPlusInterest\":120000.00}",
            null,
            null
        ))
            .isInstanceOf(LoanWorkflowService.GuarantorValidationException.class)
            .hasMessageContaining("active loans are not allowed");

        verify(loanApplicationRepository, never()).save(any(LoanApplication.class));
    }

    @Test
    void submitCreatesGuarantorRequestsWithoutApplicantAssignedCommitments() {
        // Scenario: guarantor requests preserve the selected guarantors only.
        // Given a draft loan with two selected guarantors
        // When the applicant submits the loan
        // Then pending guarantor requests are recreated without requested or committed amounts.
        UUID appId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID guarantorOne = UUID.randomUUID();
        UUID guarantorTwo = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";

        LoanApplication app = LoanApplication.builder()
            .id(appId)
            .applicantMemberId(applicantId)
            .saccoId(saccoId)
            .stationId("ST01")
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .amount(new BigDecimal("100000"))
            .tenorMonths(6)
            .status(LoanStatus.DRAFT)
            .requiredGuarantors(2)
            .selectedGuarantors("[{\"id\":\"" + guarantorOne + "\"},{\"id\":\"" + guarantorTwo + "\"}]")
            .financialSnapshot("{\"principalPlusInterest\":120000.00}")
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
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(loanApplicationRepository.findByIdAndApplicantMemberId(appId, applicantId)).thenReturn(Optional.of(app));
        when(loanProductSettingRepository.findBySaccoIdAndLoanType(saccoId, LoanType.DEVELOPMENT_LOAN)).thenReturn(Optional.of(product));
        when(financialDetailsService.generateSnapshot(saccoId, applicantId, LoanType.DEVELOPMENT_LOAN, new BigDecimal("100000"), 6, null))
            .thenReturn(Map.of("principalPlusInterest", new BigDecimal("120000.00")));
        when(eligibilityService.check(saccoId, applicantId, LoanType.DEVELOPMENT_LOAN, new BigDecimal("100000")))
            .thenReturn(new EligibilityService.EligibilityResult(true, new BigDecimal("0.3333"), new BigDecimal("500000"),
                new BigDecimal("166650.00")));
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));
        when(memberRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID memberId = invocation.getArgument(0);
            return Optional.of(activeMember(memberId, saccoId, "ST01"));
        });

        loanWorkflowService.submit(appId, applicantId);

        verify(guarantorRequestRepository).deleteByLoanApplicationId(appId);
        verify(guarantorRequestRepository, times(2)).save(argThat(request ->
            request.getStatus() == GuarantorRequestStatus.PENDING
                && request.getCommittedAmount() == null
                && request.getRequestedAmount() == null
        ));
    }

    private Member activeMember(UUID id, String saccoId, String stationId) {
        return Member.builder()
            .id(id)
            .saccoId(saccoId)
            .stationId(stationId)
            .memberNo(id.toString().substring(0, 8))
            .fullName("Member " + id.toString().substring(0, 8))
            .status(MemberStatus.ACTIVE)
            .memberAccount(true)
            .build();
    }

    private Member staffOnlyMember(UUID id, String saccoId, String stationId) {
        Member member = activeMember(id, saccoId, stationId);
        member.setMemberAccount(false);
        return member;
    }

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
        // Scenario: loan advances are configured without guarantors and should enter staff review immediately.
        // Given a draft loan advance with zero required guarantors
        // When the applicant submits it
        // Then no guarantor request is created and the workflow moves to the first review stage.
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
        // Scenario: a loan cannot leave the guarantor stage until every configured guarantor approval exists.
        // Given a loan waiting for three approvals with only two approved guarantor requests
        // When readiness is evaluated
        // Then the workflow remains open and no approval event is emitted.
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
        // Scenario: the guarantor stage closes only after the configured approval count is met.
        // Given a loan waiting for three approvals with all three guarantor requests approved
        // When readiness is evaluated
        // Then the loan moves to All Guarantors Approved and emits the handoff event.
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
        // Scenario: an individual guarantor signature is stored even when the overall guarantor stage remains open.
        // Given one guarantor approves a loan that still needs another approval
        // When the approval is recorded
        // Then the signature is saved but the loan stays Awaiting Guarantors.
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
        // Scenario: products that require fresh upstream financial data must fail closed when the provider is unavailable.
        // Given a draft loan whose product requires a live financial refresh
        // When the upstream account service cannot respond
        // Then submission is blocked and the stale draft is not saved.
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
        // Scenario: submission recalculates financial terms from the current product setup, not the stale draft snapshot.
        // Given a draft created with an older interest method
        // When the applicant submits after product settings change
        // Then the saved snapshot reflects the latest configured financial terms.
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
