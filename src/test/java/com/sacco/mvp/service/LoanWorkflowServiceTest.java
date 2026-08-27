package com.sacco.mvp.service;

import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.ExternalGuarantorRegistry;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.GuarantorRequestStatus;
import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.ReversalRequest;
import com.sacco.mvp.domain.ReversalRequestStatus;
import com.sacco.mvp.domain.ReversalRequestType;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.integration.foresight.ForesightAccountSummary;
import com.sacco.mvp.integration.foresight.ForesightActiveLoan;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import com.sacco.mvp.integration.foresight.ForesightMemberProfile;
import com.sacco.mvp.integration.foresight.UpstreamAvailabilityException;
import com.sacco.mvp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    @Mock private ExternalGuarantorRegistryRepository externalGuarantorRegistryRepository;
    @Mock private BoardReviewRepository boardReviewRepository;
    @Mock private ManagerReviewRepository managerReviewRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private SavingsAccountRepository savingsAccountRepository;
    @Mock private SaccoSettingsRepository saccoSettingsRepository;
    @Mock private FormSchemaService formSchemaService;
    @Mock private EligibilityService eligibilityService;
    @Mock private OutboxService outboxService;
    @Mock private LoanAttachmentService loanAttachmentService;
    @Mock private LoanProductRequiredAttachmentService requiredAttachmentService;
    @Mock private FinancialDetailsService financialDetailsService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private ForesightDirectoryService foresightDirectoryService;
    @Mock private SaccoConfigurationService saccoConfigurationService;
    @Mock private ApplicationNumberService applicationNumberService;
    @Mock private RoleDirectoryService roleDirectoryService;
    @Mock private LoanProductWorkflowService loanProductWorkflowService;
    @Mock private WorkflowRoutingService workflowRoutingService;
    @Mock private LoanQualificationPolicyService loanQualificationPolicyService;
    @Mock private PaymentDetailsService paymentDetailsService;
    @Mock private ReversalRequestRepository reversalRequestRepository;
    @Mock private AuditService auditService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private ApplicationClock applicationClock;

    @InjectMocks
    private LoanWorkflowService loanWorkflowService;

    @BeforeEach
    void stubTransactions() {
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        lenient().doNothing().when(transactionManager).commit(any());
        lenient().doNothing().when(transactionManager).rollback(any());
        lenient().when(applicationClock.today()).thenReturn(LocalDate.of(2026, 8, 24));
        lenient().when(applicationClock.now()).thenReturn(OffsetDateTime.parse("2026-08-24T13:00:00+03:00"));
    }

    @Test
    void memberDashboardUsesFocusedAggregateAndCurrentLoanQueries() {
        UUID memberId = UUID.randomUUID();
        LoanApplication activeLoan = LoanApplication.builder()
            .id(UUID.randomUUID())
            .applicantMemberId(memberId)
            .status(LoanStatus.DISBURSED)
            .build();
        LoanApplication currentLoan = LoanApplication.builder()
            .id(UUID.randomUUID())
            .applicantMemberId(memberId)
            .status(LoanStatus.READY_FOR_MANAGER)
            .build();
        LoanApplicationRepository.StatusCountProjection currentCount = mock(LoanApplicationRepository.StatusCountProjection.class);
        LoanApplicationRepository.StatusCountProjection paidCount = mock(LoanApplicationRepository.StatusCountProjection.class);
        when(currentCount.getStatus()).thenReturn(LoanStatus.READY_FOR_MANAGER);
        when(currentCount.getTotal()).thenReturn(2L);
        when(paidCount.getStatus()).thenReturn(LoanStatus.PAID);
        when(paidCount.getTotal()).thenReturn(3L);
        when(loanApplicationRepository.countByStatusForApplicant(memberId)).thenReturn(List.of(currentCount, paidCount));
        when(loanApplicationRepository.findByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(eq(memberId), any()))
            .thenReturn(List.of(activeLoan));
        when(loanApplicationRepository.findLatestVisibleCurrentForApplicant(eq(memberId), any(), any(), any()))
            .thenReturn(List.of(currentLoan));
        when(loanApplicationRepository.countByApplicantMemberIdAndStatusInAndApplicantRejectionAcknowledgedAtIsNull(eq(memberId), any()))
            .thenReturn(1L);
        when(guarantorRequestRepository.countVisiblePendingByGuarantorMemberId(memberId)).thenReturn(4L);

        LoanWorkflowService.MemberDashboardData dashboard = loanWorkflowService.memberDashboard(memberId);

        assertThat(dashboard.statusCounts())
            .containsEntry(LoanStatus.READY_FOR_MANAGER, 2L)
            .containsEntry(LoanStatus.PAID, 3L);
        assertThat(dashboard.activeLoans()).containsExactly(activeLoan);
        assertThat(dashboard.latestCurrentApplication()).isEqualTo(currentLoan);
        assertThat(dashboard.unacknowledgedRejectedApplicationCount()).isEqualTo(1L);
        assertThat(dashboard.pendingGuaranteeCount()).isEqualTo(4L);
        verify(loanApplicationRepository, never()).findByApplicantMemberIdOrderByCreatedAtDesc(memberId);
        verify(guarantorRequestRepository, never()).findByGuarantorMemberIdOrderByCreatedAtDesc(memberId);
    }

    @Test
    void memberApplicationListLoadsOnlyCurrentApplicationsAndAggregatesArchiveCount() {
        UUID memberId = UUID.randomUUID();
        LoanApplication currentLoan = LoanApplication.builder()
            .id(UUID.randomUUID())
            .applicantMemberId(memberId)
            .status(LoanStatus.READY_FOR_MANAGER)
            .build();
        LoanApplicationRepository.StatusCountProjection currentCount = mock(LoanApplicationRepository.StatusCountProjection.class);
        LoanApplicationRepository.StatusCountProjection paidCount = mock(LoanApplicationRepository.StatusCountProjection.class);
        LoanApplicationRepository.StatusCountProjection rejectedCount = mock(LoanApplicationRepository.StatusCountProjection.class);
        when(currentCount.getStatus()).thenReturn(LoanStatus.READY_FOR_MANAGER);
        when(paidCount.getStatus()).thenReturn(LoanStatus.PAID);
        when(paidCount.getTotal()).thenReturn(3L);
        when(rejectedCount.getStatus()).thenReturn(LoanStatus.REJECTED);
        when(rejectedCount.getTotal()).thenReturn(2L);
        when(loanApplicationRepository.findVisibleCurrentForApplicant(eq(memberId), any(), any()))
            .thenReturn(List.of(currentLoan));
        when(loanApplicationRepository.countByStatusForApplicant(memberId))
            .thenReturn(List.of(currentCount, paidCount, rejectedCount));

        LoanWorkflowService.MemberApplicationListData applications = loanWorkflowService.memberApplicationList(memberId);

        assertThat(applications.currentApplications()).containsExactly(currentLoan);
        assertThat(applications.archiveCount()).isEqualTo(5L);
        verify(loanApplicationRepository, never()).findByApplicantMemberIdOrderByCreatedAtDesc(memberId);
    }

    @Test
    void acknowledgeDisbursementMarksFinalApprovedLoanAsSeenByApplicant() {
        UUID memberId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
            .id(appId)
            .applicantMemberId(memberId)
            .status(LoanStatus.DISBURSED)
            .updatedAt(OffsetDateTime.now().minusDays(1))
            .build();
        when(loanApplicationRepository.findByIdAndApplicantMemberId(appId, memberId)).thenReturn(Optional.of(app));
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        loanWorkflowService.acknowledgeDisbursement(appId, memberId);

        assertThat(app.getApplicantDisbursementAcknowledgedAt()).isNotNull();
        assertThat(app.getUpdatedAt()).isEqualTo(app.getApplicantDisbursementAcknowledgedAt());
        verify(loanApplicationRepository).save(app);
    }

    @Test
    void acknowledgeDisbursementRejectsNonDisbursedLoan() {
        UUID memberId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
            .id(appId)
            .applicantMemberId(memberId)
            .status(LoanStatus.READY_FOR_DISBURSEMENT)
            .build();
        when(loanApplicationRepository.findByIdAndApplicantMemberId(appId, memberId)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> loanWorkflowService.acknowledgeDisbursement(appId, memberId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Only disbursed loans can be acknowledged.");
        verify(loanApplicationRepository, never()).save(any());
    }

    @Test
    void acknowledgeRejectionMarksRejectedApplicationAsSeenByApplicant() {
        UUID memberId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
            .id(appId)
            .applicantMemberId(memberId)
            .status(LoanStatus.CREDIT_COMMITTEE_REJECTED)
            .updatedAt(OffsetDateTime.now().minusDays(1))
            .build();
        when(loanApplicationRepository.findByIdAndApplicantMemberId(appId, memberId)).thenReturn(Optional.of(app));
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        loanWorkflowService.acknowledgeRejection(appId, memberId);

        assertThat(app.getApplicantRejectionAcknowledgedAt()).isNotNull();
        assertThat(app.getUpdatedAt()).isEqualTo(app.getApplicantRejectionAcknowledgedAt());
        verify(loanApplicationRepository).save(app);
    }

    @Test
    void acknowledgeRejectionRejectsNonRejectedLoan() {
        UUID memberId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
            .id(appId)
            .applicantMemberId(memberId)
            .status(LoanStatus.DISBURSED)
            .build();
        when(loanApplicationRepository.findByIdAndApplicantMemberId(appId, memberId)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> loanWorkflowService.acknowledgeRejection(appId, memberId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Only rejected loan applications can be acknowledged.");
        verify(loanApplicationRepository, never()).save(any());
    }

    @Test
    void latestMemberLoanLookupsUseBoundedRepositoryQueries() {
        UUID memberId = UUID.randomUUID();
        LoanApplication currentLoan = LoanApplication.builder()
            .id(UUID.randomUUID())
            .status(LoanStatus.READY_FOR_MANAGER)
            .build();
        LoanApplication activeLoan = LoanApplication.builder()
            .id(UUID.randomUUID())
            .status(LoanStatus.DISBURSED)
            .build();
        when(loanApplicationRepository.findFirstByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(eq(memberId), any()))
            .thenReturn(Optional.of(currentLoan));
        when(loanApplicationRepository.findByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(eq(memberId), any()))
            .thenReturn(List.of(activeLoan));

        assertThat(loanWorkflowService.findApplicationInProgress(memberId)).contains(currentLoan);
        assertThat(loanWorkflowService.findActiveDisbursedLoan(memberId)).contains(activeLoan);

        verify(loanApplicationRepository)
            .findFirstByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(eq(memberId), any());
        verify(loanApplicationRepository)
            .findByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(eq(memberId), any());
    }

    @Test
    void guarantorWorkspaceListsUseFocusedRepositoryQueries() {
        UUID memberId = UUID.randomUUID();
        GuarantorRequest request = GuarantorRequest.builder().id(UUID.randomUUID()).build();
        when(guarantorRequestRepository.findActiveVisibleByGuarantorMemberId(eq(memberId), any()))
            .thenReturn(List.of(request));
        when(guarantorRequestRepository.findActiveGuaranteedLoansByGuarantorMemberId(memberId))
            .thenReturn(List.of(request));

        assertThat(loanWorkflowService.myActiveGuarantorRequests(memberId)).containsExactly(request);
        assertThat(loanWorkflowService.myActiveGuaranteedLoans(memberId)).containsExactly(request);

        verify(guarantorRequestRepository, never()).findByGuarantorMemberIdOrderByCreatedAtDesc(memberId);
    }

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
        when(reversalRequestRepository.findByLoanApplicationIdAndTypeAndStatusOrderByCreatedAtDesc(
            appId, ReversalRequestType.GUARANTOR_DECISION_UNDO, ReversalRequestStatus.PENDING
        )).thenReturn(List.of());
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
    void submitToManagerRequiresPendingGuarantorRemovalApproval() {
        UUID appId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
            .id(appId)
            .applicantMemberId(memberId)
            .saccoId("CIRCLE-1001")
            .status(LoanStatus.ALL_GUARANTORS_APPROVED)
            .requiredGuarantors(1)
            .financialSnapshot("{\"principalPlusInterest\":120000.00}")
            .build();
        ReversalRequest pendingRemoval = ReversalRequest.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(appId)
            .type(ReversalRequestType.GUARANTOR_DECISION_UNDO)
            .status(ReversalRequestStatus.PENDING)
            .build();

        when(loanApplicationRepository.findByIdAndApplicantMemberId(appId, memberId)).thenReturn(Optional.of(app));
        when(reversalRequestRepository.findByLoanApplicationIdAndTypeAndStatusOrderByCreatedAtDesc(
            appId, ReversalRequestType.GUARANTOR_DECISION_UNDO, ReversalRequestStatus.PENDING
        )).thenReturn(List.of(pendingRemoval));

        assertThatThrownBy(() -> loanWorkflowService.submitToManager(appId, memberId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Approve the pending guarantor removal request before submitting this application.");

        verifyNoInteractions(workflowRoutingService);
        verify(loanApplicationRepository, never()).save(any(LoanApplication.class));
    }

    @Test
    void removingAttachmentFromApprovedApplicationReopensDraftAndExpiresGuarantors() {
        UUID appId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String attachmentId = UUID.randomUUID().toString();
        LoanApplication app = LoanApplication.builder()
            .id(appId)
            .applicantMemberId(memberId)
            .saccoId("CIRCLE-1001")
            .stationId("ST01")
            .status(LoanStatus.ALL_GUARANTORS_APPROVED)
            .attachmentsJson("[{\"id\":\"" + attachmentId + "\"}]")
            .applicantSignatureText("SIGNED")
            .applicantSignatureVerifiedAt(OffsetDateTime.now().minusHours(1))
            .build();
        GuarantorRequest request = GuarantorRequest.builder()
            .id(UUID.randomUUID())
            .status(GuarantorRequestStatus.APPROVED)
            .build();

        when(loanApplicationRepository.findByIdAndApplicantMemberId(appId, memberId)).thenReturn(Optional.of(app));
        when(loanAttachmentService.removeApplicationAttachment(appId, attachmentId, app.getAttachmentsJson()))
            .thenReturn(new LoanAttachmentService.AttachmentRemoval("[]", "idp.pdf"));
        when(guarantorRequestRepository.findByLoanApplicationId(appId)).thenReturn(List.of(request));
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoanApplication updated = loanWorkflowService.removeApplicationAttachment(appId, memberId, attachmentId);

        assertThat(updated.getAttachmentsJson()).isEqualTo("[]");
        assertThat(updated.getStatus()).isEqualTo(LoanStatus.DRAFT);
        assertThat(updated.getApplicantSignatureText()).isNull();
        assertThat(updated.getApplicantSignatureVerifiedAt()).isNull();
        assertThat(request.getStatus()).isEqualTo(GuarantorRequestStatus.EXPIRED);
        assertThat(request.getDecisionReason()).isEqualTo("Applicant reopened application for editing");
        verify(guarantorRequestRepository).saveAll(List.of(request));
        verify(auditService).logEvent(eq("LOAN_APPLICATION"), eq(appId), eq("LOAN_APPLICATION_ATTACHMENT_REMOVED"),
            eq(memberId), any(), anyString(), eq("LOAN_APPLICATION"), anyString(), eq("CIRCLE-1001"), eq("ST01"), anyMap());
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
        stubActiveMemberBatchLookup(saccoId);
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
            List.of(guarantorOne.toString(), guarantorTwo.toString()),
            "{\"principalPlusInterest\":120000.00}",
            null,
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
        when(memberRepository.findAllById(any())).thenReturn(List.of(staffOnlyMember(staffOnlyGuarantor, saccoId, "ST01")));

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
        var result = loanWorkflowService.searchGuarantors(saccoId, "ST01", applicantId, "0101", 0, 10);

        assertThat(result.getContent()).isEmpty();
        verify(memberRepository, never()).findGuarantorCandidatesByNumberSuffix(anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    void searchGuarantorsUsesExactMemberNumberWithoutMinimumLength() {
        UUID applicantId = UUID.randomUUID();
        UUID guarantorId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";
        Member guarantor = activeMember(guarantorId, saccoId, "ST01");
        guarantor.setMemberNo("00");

        when(memberRepository.findBySaccoIdAndStatusAndMemberNoIgnoreCase(saccoId, MemberStatus.ACTIVE, "00"))
            .thenReturn(Optional.of(guarantor));

        var result = loanWorkflowService.searchGuarantors(saccoId, "ST01", applicantId, "00", "number", 0, 10);

        assertThat(result.getContent()).containsExactly(guarantor);
        verify(memberRepository, never()).findGuarantorCandidatesByNumberSuffix(anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    void searchGuarantorsDoesNotFallbackToPartialNumberSuffix() {
        UUID applicantId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";

        when(memberRepository.findBySaccoIdAndStatusAndMemberNoIgnoreCase(saccoId, MemberStatus.ACTIVE, "00"))
            .thenReturn(Optional.empty());

        var result = loanWorkflowService.searchGuarantors(saccoId, "ST01", applicantId, "00", "number", 0, 10);

        assertThat(result.getContent()).isEmpty();
        verify(memberRepository, never()).findGuarantorCandidatesByNumberSuffix(anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    void searchGuarantorsFindsCandidatesByLowercaseName() {
        UUID applicantId = UUID.randomUUID();
        UUID guarantorId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";
        Member guarantor = activeMember(guarantorId, saccoId, "ST01");
        guarantor.setFullName("ALEX JUMAPILI");
        guarantor.setMemberNo("0100");

        when(memberRepository.findGuarantorCandidatesByName(
            eq(saccoId),
            eq("ST01"),
            eq(applicantId),
            eq("alex"),
            eq(PageRequest.of(0, 10))
        )).thenReturn(new PageImpl<>(List.of(guarantor), PageRequest.of(0, 10), 1));

        var result = loanWorkflowService.searchGuarantors(saccoId, "ST01", applicantId, "ALEX", "name", 0, 10);

        assertThat(result.getContent()).containsExactly(guarantor);
        verify(memberRepository, never()).findBySaccoIdAndStatusAndMemberNoIgnoreCase(anyString(), any(), anyString());
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
    void directOtpSearchReturnsLmsCandidateWhenForesightMemberNumberMatchesLocalMember() {
        UUID applicantId = UUID.randomUUID();
        UUID guarantorId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";
        LoanProductSetting product = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .formSchema("{}")
            .active(true)
            .build();
        ForesightMemberProfile profile = new ForesightMemberProfile(
            "Mushi",
            "Jane",
            "0101",
            "ST01",
            "Demo SACCO",
            "+255676423992",
            "jane@example.com"
        );
        Member localGuarantor = activeMember(guarantorId, saccoId, "ST01");
        localGuarantor.setMemberNo("0101");
        localGuarantor.setFullName("Jane Mushi");

        when(foresightDirectoryService.lookupMemberProfileByPhone("+255676423992"))
            .thenReturn(ForesightDirectoryService.MemberProfileLookupResult.found(profile));
        stubDirectOtpFinancialProfile("0101", "ST01", new BigDecimal("250000.00"), List.of(), List.of(
            new ForesightActiveLoan("2001", LocalDate.of(2023, 2, 15), "Paid Loan",
                new BigDecimal("400000.00"), new BigDecimal("420000.00"), BigDecimal.TEN, new BigDecimal("20000.00"))
        ));
        when(memberRepository.findByMemberNoIgnoreCase("0101")).thenReturn(Optional.of(localGuarantor));
        when(loanQualificationPolicyService.guarantorFailureReason(saccoId, guarantorId, null, product))
            .thenReturn(Optional.empty());

        List<LoanWorkflowService.DirectOtpGuarantorCandidate> result =
            loanWorkflowService.searchDirectOtpGuarantorCandidates(
                saccoId,
                "ST01",
                applicantId,
                "0676423992",
                "phone",
                product
            );

        assertThat(result).hasSize(1);
        LoanWorkflowService.DirectOtpGuarantorCandidate candidate = result.getFirst();
        assertThat(candidate.source()).isEqualTo("LMS");
        assertThat(candidate.localMemberId()).isEqualTo(guarantorId);
        assertThat(candidate.memberNo()).isEqualTo("0101");
        assertThat(candidate.fullName()).isEqualTo("Jane Mushi");
        assertThat(candidate.paidLoanCount()).isEqualTo(1);
        assertThat(candidate.selectionToken()).contains("\"id\":\"" + guarantorId + "\"");
    }

    @Test
    void directOtpSearchHandlesMissingAndInvalidProfileResponses() {
        String saccoId = "CIRCLE-1001";
        UUID applicantId = UUID.randomUUID();
        LoanProductSetting product = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .active(true)
            .build();

        when(foresightDirectoryService.lookupMemberProfileByEmail("missing@example.com"))
            .thenReturn(ForesightDirectoryService.MemberProfileLookupResult.notFound());
        assertThat(loanWorkflowService.searchDirectOtpGuarantorCandidates(
            saccoId, "ST01", applicantId, "missing@example.com", "email", product)).isEmpty();

        when(foresightDirectoryService.lookupMemberProfileByEmail("broken@example.com"))
            .thenReturn(ForesightDirectoryService.MemberProfileLookupResult.found(
                new ForesightMemberProfile("Missing", "Station", "0101", "", "Demo", null, "broken@example.com")
            ));
        assertThatThrownBy(() -> loanWorkflowService.searchDirectOtpGuarantorCandidates(
            saccoId, "ST01", applicantId, "broken@example.com", "email", product))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing member number or station");

        when(foresightDirectoryService.lookupMemberProfileByEmail("down@example.com"))
            .thenReturn(ForesightDirectoryService.MemberProfileLookupResult.unavailable());
        assertThatThrownBy(() -> loanWorkflowService.searchDirectOtpGuarantorCandidates(
            saccoId, "ST01", applicantId, "down@example.com", "email", product))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unavailable");
    }

    @Test
    void submitCreatesForesightOnlyDirectOtpGuarantorRequest() {
        UUID appId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";
        LoanApplication app = directOtpDraft(appId, applicantId, saccoId,
            "[{\"source\":\"FORESIGHT\",\"lookupBy\":\"phone\",\"lookupValue\":\"0676423992\"}]");
        LoanProductSetting product = directOtpProduct(saccoId);
        ForesightMemberProfile profile = new ForesightMemberProfile(
            "Mtei",
            "Asha",
            "EXT-77",
            "ST01",
            "Demo SACCO",
            "+255676423992",
            "asha@example.com"
        );

        when(loanApplicationRepository.findByIdAndApplicantMemberId(appId, applicantId)).thenReturn(Optional.of(app));
        when(loanProductSettingRepository.findBySaccoIdAndLoanType(saccoId, LoanType.DEVELOPMENT_LOAN)).thenReturn(Optional.of(product));
        when(memberRepository.findById(applicantId)).thenReturn(Optional.of(activeMember(applicantId, saccoId, "ST01")));
        when(financialDetailsService.generateSnapshot(saccoId, applicantId, LoanType.DEVELOPMENT_LOAN, new BigDecimal("100000"), 6, null))
            .thenReturn(Map.of("principalPlusInterest", new BigDecimal("120000.00")));
        when(eligibilityService.check(saccoId, applicantId, LoanType.DEVELOPMENT_LOAN, new BigDecimal("100000")))
            .thenReturn(new EligibilityService.EligibilityResult(true, BigDecimal.ONE, new BigDecimal("500000"), new BigDecimal("500000")));
        when(foresightDirectoryService.lookupMemberProfileByPhone("+255676423992"))
            .thenReturn(ForesightDirectoryService.MemberProfileLookupResult.found(profile));
        stubDirectOtpFinancialProfile("EXT-77", "ST01", new BigDecimal("300000.00"), List.of(), List.of());
        when(memberRepository.findByMemberNoIgnoreCase("EXT-77")).thenReturn(Optional.empty());
        when(loanQualificationPolicyService.guarantorFailureReasonForExternal(
            eq(saccoId), eq("ST01"), eq("EXT-77"), eq("ST01"), any(BigDecimal.class), eq(0), eq(0), eq(new BigDecimal("100000")), eq(product)))
            .thenReturn(Optional.empty());
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        loanWorkflowService.submit(appId, applicantId);

        verify(guarantorRequestRepository).save(argThat(request ->
            request.getGuarantorMemberId() == null
                && "FORESIGHT".equals(request.getGuarantorSource())
                && "EXT-77".equals(request.getExternalMemberNo())
                && "ST01".equals(request.getExternalStationId())
                && "Asha Mtei".equals(request.getExternalFullName())
                && request.getExternalFinancialSnapshot() != null
                && request.getExternalFinancialSnapshot().contains("300000.00")
        ));
    }

    @Test
    void submitRejectsForesightOnlyDirectOtpGuarantorWhenExternalPolicyFails() {
        UUID appId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";
        LoanApplication app = directOtpDraft(appId, applicantId, saccoId,
            "[{\"source\":\"FORESIGHT\",\"lookupBy\":\"email\",\"lookupValue\":\"guarantor@example.com\"}]");
        LoanProductSetting product = directOtpProduct(saccoId);
        ForesightMemberProfile profile = new ForesightMemberProfile(
            "Mtei",
            "Asha",
            "EXT-77",
            "ST01",
            "Demo SACCO",
            "+255676423992",
            "guarantor@example.com"
        );
        ForesightActiveLoan activeLoan = new ForesightActiveLoan("3001", LocalDate.now(), "Active Loan",
            new BigDecimal("100000.00"), new BigDecimal("110000.00"), BigDecimal.TEN, new BigDecimal("10000.00"));

        when(loanApplicationRepository.findByIdAndApplicantMemberId(appId, applicantId)).thenReturn(Optional.of(app));
        when(loanProductSettingRepository.findBySaccoIdAndLoanType(saccoId, LoanType.DEVELOPMENT_LOAN)).thenReturn(Optional.of(product));
        when(memberRepository.findById(applicantId)).thenReturn(Optional.of(activeMember(applicantId, saccoId, "ST01")));
        when(financialDetailsService.generateSnapshot(saccoId, applicantId, LoanType.DEVELOPMENT_LOAN, new BigDecimal("100000"), 6, null))
            .thenReturn(Map.of("principalPlusInterest", new BigDecimal("120000.00")));
        when(eligibilityService.check(saccoId, applicantId, LoanType.DEVELOPMENT_LOAN, new BigDecimal("100000")))
            .thenReturn(new EligibilityService.EligibilityResult(true, BigDecimal.ONE, new BigDecimal("500000"), new BigDecimal("500000")));
        when(foresightDirectoryService.lookupMemberProfileByEmail("guarantor@example.com"))
            .thenReturn(ForesightDirectoryService.MemberProfileLookupResult.found(profile));
        stubDirectOtpFinancialProfile("EXT-77", "ST01", new BigDecimal("300000.00"), List.of(activeLoan), List.of());
        when(memberRepository.findByMemberNoIgnoreCase("EXT-77")).thenReturn(Optional.empty());
        when(loanQualificationPolicyService.guarantorFailureReasonForExternal(
            eq(saccoId), eq("ST01"), eq("EXT-77"), eq("ST01"), any(BigDecimal.class), eq(1), eq(0), eq(new BigDecimal("100000")), eq(product)))
            .thenReturn(Optional.of("Disabled: active loans are not allowed for guarantors under the station policy."));

        assertThatThrownBy(() -> loanWorkflowService.submit(appId, applicantId))
            .isInstanceOf(LoanWorkflowService.GuarantorValidationException.class)
            .hasMessageContaining("active loans are not allowed");

        verify(guarantorRequestRepository, never()).save(any());
    }

    @Test
    void directOtpApprovalUpsertsRegistryForForesightOnlyGuarantor() {
        UUID appId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";
        LoanApplication app = directOtpDraft(appId, applicantId, saccoId,
            "[{\"source\":\"FORESIGHT\",\"lookupBy\":\"phone\",\"lookupValue\":\"+255676423992\"}]");
        app.setStatus(LoanStatus.AWAITING_GUARANTORS);
        GuarantorRequest request = GuarantorRequest.builder()
            .id(requestId)
            .loanApplicationId(appId)
            .guarantorMemberId(null)
            .status(GuarantorRequestStatus.PENDING)
            .createdAt(OffsetDateTime.now())
            .build();
        request.setGuarantorSource("FORESIGHT");
        request.setExternalMemberNo("EXT-77");
        request.setExternalStationId("ST01");
        request.setExternalFullName("Asha Mtei");
        request.setExternalEmail("asha@example.com");
        request.setExternalPhone("+255676423992");
        request.setExternalFinancialSnapshot("{\"savingsBalance\":\"300000.00\"}");
        LoanProductSetting product = directOtpProduct(saccoId);

        when(guarantorRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(loanApplicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(externalGuarantorRegistryRepository.findBySaccoIdAndExternalStationIdIgnoreCaseAndExternalMemberNoIgnoreCase(
            saccoId, "ST01", "EXT-77"))
            .thenReturn(Optional.empty());
        when(externalGuarantorRegistryRepository.save(any(ExternalGuarantorRegistry.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(guarantorRequestRepository.save(any(GuarantorRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loanProductSettingRepository.findBySaccoIdAndLoanType(saccoId, LoanType.DEVELOPMENT_LOAN)).thenReturn(Optional.of(product));
        when(guarantorRequestRepository.countByLoanApplicationIdAndStatus(appId, GuarantorRequestStatus.APPROVED)).thenReturn(1L);
        when(eligibilityService.check(saccoId, applicantId, LoanType.DEVELOPMENT_LOAN, new BigDecimal("100000")))
            .thenReturn(new EligibilityService.EligibilityResult(true, BigDecimal.ONE, new BigDecimal("500000"), new BigDecimal("500000")));
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        loanWorkflowService.approveDirectOtpGuarantorRequest(
            requestId,
            applicantId,
            "Asha Mtei",
            OffsetDateTime.parse("2026-08-24T12:00:00+03:00")
        );

        verify(externalGuarantorRegistryRepository).save(argThat(registry ->
            saccoId.equals(registry.getSaccoId())
                && "ST01".equals(registry.getStationId())
                && "ST01".equals(registry.getExternalStationId())
                && "EXT-77".equals(registry.getExternalMemberNo())
                && "Asha Mtei".equals(registry.getFullName())
                && registry.getLastApprovedAt() != null
        ));
        verify(guarantorRequestRepository).save(argThat(saved ->
            saved.getExternalGuarantorRegistryId() != null
                && saved.getStatus() == GuarantorRequestStatus.APPROVED
        ));
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
        when(memberRepository.findAllById(any())).thenReturn(List.of(activeMember(guarantorId, saccoId, "ST01")));
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
        stubActiveMemberBatchLookup(saccoId);

        loanWorkflowService.submit(appId, applicantId);

        verify(guarantorRequestRepository).deleteByLoanApplicationId(appId);
        verify(guarantorRequestRepository, times(2)).save(argThat(request ->
            request.getStatus() == GuarantorRequestStatus.PENDING
                && request.getCommittedAmount() == null
                && request.getRequestedAmount() == null
        ));
    }

    @Test
    void saveAndSubmitPreservesSavedGuarantorsWhenReloadSubmitOmitsInputs() {
        UUID appId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID guarantorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";

        LoanApplication existing = LoanApplication.builder()
            .id(appId)
            .applicantMemberId(applicantId)
            .saccoId(saccoId)
            .stationId("ST01")
            .loanProductSettingId(productId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .amount(new BigDecimal("100000"))
            .tenorMonths(6)
            .status(LoanStatus.DRAFT)
            .requiredGuarantors(1)
            .selectedGuarantors("[{\"id\":\"" + guarantorId + "\"}]")
            .financialSnapshot("{\"principalPlusInterest\":120000.00}")
            .attachmentsJson("[]")
            .formData("{\"purpose\":\"WORKING CAPITAL\"}")
            .policySnapshot("{}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .version(0)
            .build();
        LoanProductSetting product = LoanProductSetting.builder()
            .id(productId)
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .guarantorsRequired(1)
            .freshFinancialDataRequired(false)
            .allowApplicationWithActiveLoan(true)
            .minimumAmount(BigDecimal.ZERO)
            .maximumAmount(new BigDecimal("1000000.00"))
            .maxRepaymentMonths(12)
            .formSchema("{}")
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(loanApplicationRepository.findByIdAndApplicantMemberId(appId, applicantId)).thenReturn(Optional.of(existing));
        when(formSchemaService.getSchema(saccoId, productId, LoanType.DEVELOPMENT_LOAN)).thenReturn(product);
        when(formSchemaService.extractFormData(anyMap(), eq("{}"))).thenReturn(new LinkedHashMap<>(Map.of("purpose", "WORKING CAPITAL")));
        when(loanApplicationRepository.findByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(eq(applicantId), any()))
            .thenReturn(List.of(existing))
            .thenReturn(List.of());
        when(loanProductSettingRepository.findByIdAndSaccoId(productId, saccoId)).thenReturn(Optional.of(product));
        when(memberRepository.findById(applicantId)).thenReturn(Optional.of(activeMember(applicantId, saccoId, "ST01")));
        when(financialDetailsService.generateSnapshot(saccoId, applicantId, product, new BigDecimal("100000"), 6, null))
            .thenReturn(Map.of("principalPlusInterest", new BigDecimal("120000.00")));
        when(eligibilityService.check(saccoId, applicantId, product, new BigDecimal("100000")))
            .thenReturn(new EligibilityService.EligibilityResult(true, new BigDecimal("0.3333"), new BigDecimal("500000"),
                new BigDecimal("166650.00")));
        when(eligibilityService.policySnapshotJson(any(), anyInt(), any())).thenReturn("{}");
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(loanAttachmentService.store(any(), any(), anyString())).thenAnswer(invocation -> invocation.getArgument(2));
        when(loanAttachmentService.storeRequired(any(), any(), anyString())).thenAnswer(invocation -> invocation.getArgument(2));
        when(paymentDetailsService.snapshotJsonForMember(applicantId)).thenReturn("{}");
        stubActiveMemberBatchLookup(saccoId);

        LoanApplication submitted = loanWorkflowService.saveAndSubmit(
            saccoId,
            applicantId,
            productId,
            LoanType.DEVELOPMENT_LOAN,
            new BigDecimal("100000"),
            6,
            Map.of("purpose", "WORKING CAPITAL"),
            appId,
            List.of(),
            "{\"principalPlusInterest\":120000.00}",
            null,
            null,
            null
        );

        assertThat(submitted.getStatus()).isEqualTo(LoanStatus.AWAITING_GUARANTORS);
        assertThat(submitted.getSelectedGuarantors()).contains(guarantorId.toString());
        verify(guarantorRequestRepository).save(argThat(request -> guarantorId.equals(request.getGuarantorMemberId())));
    }

    @Test
    void saveAndSubmitTopUpPreservesSavedSourceLoanAndGuarantorsWhenReloadSubmitOmitsInputs() {
        UUID appId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID guarantorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID sourceLoanId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";

        LoanApplication existing = LoanApplication.builder()
            .id(appId)
            .applicantMemberId(applicantId)
            .saccoId(saccoId)
            .stationId("ST01")
            .topUpSourceLoanId(sourceLoanId)
            .loanProductSettingId(productId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .amount(new BigDecimal("150000.00"))
            .tenorMonths(6)
            .status(LoanStatus.DRAFT)
            .requiredGuarantors(1)
            .selectedGuarantors("[{\"id\":\"" + guarantorId + "\"}]")
            .financialSnapshot("{\"topUpRequestedAmount\":100000.00,\"topUpSettlementAmount\":50000.00,\"principalAmount\":150000.00}")
            .attachmentsJson("[]")
            .formData("{\"purpose\":\"WORKING CAPITAL\"}")
            .policySnapshot("{}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .version(0)
            .build();
        LoanApplication sourceLoan = LoanApplication.builder()
            .id(sourceLoanId)
            .applicantMemberId(applicantId)
            .saccoId(saccoId)
            .stationId("ST01")
            .status(LoanStatus.DISBURSED)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        LoanProductSetting product = LoanProductSetting.builder()
            .id(productId)
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .guarantorsRequired(1)
            .freshFinancialDataRequired(false)
            .allowApplicationWithActiveLoan(true)
            .minimumAmount(BigDecimal.ZERO)
            .maximumAmount(new BigDecimal("1000000.00"))
            .maxRepaymentMonths(12)
            .formSchema("{}")
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        Map<String, Object> topUpSnapshot = new LinkedHashMap<>(Map.of(
            "requestedAmount", new BigDecimal("100000.00"),
            "topUpRequestedAmount", new BigDecimal("100000.00"),
            "topUpSettlementAmount", new BigDecimal("50000.00"),
            "principalAmount", new BigDecimal("150000.00"),
            "principalPlusInterest", new BigDecimal("180000.00")
        ));

        when(loanApplicationRepository.findByIdAndApplicantMemberId(appId, applicantId)).thenReturn(Optional.of(existing));
        when(loanApplicationRepository.findByIdAndApplicantMemberId(sourceLoanId, applicantId)).thenReturn(Optional.of(sourceLoan));
        when(formSchemaService.getSchema(saccoId, productId, LoanType.DEVELOPMENT_LOAN)).thenReturn(product);
        when(formSchemaService.extractFormData(anyMap(), eq("{}"))).thenReturn(new LinkedHashMap<>(Map.of("purpose", "WORKING CAPITAL")));
        when(loanApplicationRepository.findByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(eq(applicantId), any()))
            .thenReturn(List.of(existing))
            .thenReturn(List.of(sourceLoan));
        when(loanProductSettingRepository.findByIdAndSaccoId(productId, saccoId)).thenReturn(Optional.of(product));
        when(memberRepository.findById(applicantId)).thenReturn(Optional.of(activeMember(applicantId, saccoId, "ST01")));
        when(financialDetailsService.generateSnapshot(eq(saccoId), eq(applicantId), eq(product), any(BigDecimal.class), eq(6), eq(sourceLoanId)))
            .thenReturn(topUpSnapshot);
        when(eligibilityService.check(eq(saccoId), eq(applicantId), eq(product), any(BigDecimal.class)))
            .thenReturn(new EligibilityService.EligibilityResult(true, new BigDecimal("0.3333"), new BigDecimal("500000"),
                new BigDecimal("166650.00")));
        when(eligibilityService.policySnapshotJson(any(), anyInt(), any())).thenReturn("{}");
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(loanAttachmentService.store(any(), any(), anyString())).thenAnswer(invocation -> invocation.getArgument(2));
        when(loanAttachmentService.storeRequired(any(), any(), anyString())).thenAnswer(invocation -> invocation.getArgument(2));
        when(paymentDetailsService.snapshotJsonForMember(applicantId)).thenReturn("{}");
        stubActiveMemberBatchLookup(saccoId);

        LoanApplication submitted = loanWorkflowService.saveAndSubmit(
            saccoId,
            applicantId,
            productId,
            LoanType.DEVELOPMENT_LOAN,
            new BigDecimal("100000"),
            6,
            Map.of("purpose", "WORKING CAPITAL"),
            appId,
            List.of(),
            "{\"principalPlusInterest\":180000.00}",
            null,
            null,
            null
        );

        assertThat(submitted.getStatus()).isEqualTo(LoanStatus.AWAITING_GUARANTORS);
        assertThat(submitted.getTopUpSourceLoanId()).isEqualTo(sourceLoanId);
        assertThat(submitted.getAmount()).isEqualByComparingTo("150000.00");
        assertThat(submitted.getSelectedGuarantors()).contains(guarantorId.toString());
        verify(guarantorRequestRepository).save(argThat(request -> guarantorId.equals(request.getGuarantorMemberId())));
    }

    @Test
    void approveGuarantorRequestNotifiesApplicant() {
        UUID requestId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID guarantorId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";
        GuarantorRequest request = GuarantorRequest.builder()
            .id(requestId)
            .loanApplicationId(loanId)
            .guarantorMemberId(guarantorId)
            .status(GuarantorRequestStatus.PENDING)
            .createdAt(OffsetDateTime.now())
            .build();
        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .applicantMemberId(applicantId)
            .saccoId(saccoId)
            .stationId("ST01")
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .amount(new BigDecimal("100000"))
            .requiredGuarantors(2)
            .status(LoanStatus.AWAITING_GUARANTORS)
            .build();

        when(guarantorRequestRepository.findByIdAndGuarantorMemberId(requestId, guarantorId)).thenReturn(Optional.of(request));
        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
        when(guarantorRequestRepository.countByLoanApplicationIdAndStatus(loanId, GuarantorRequestStatus.APPROVED)).thenReturn(1L);
        when(eligibilityService.check(saccoId, applicantId, LoanType.DEVELOPMENT_LOAN, new BigDecimal("100000")))
            .thenReturn(new EligibilityService.EligibilityResult(true, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN));

        loanWorkflowService.approveGuarantorRequest(requestId, guarantorId);

        verify(outboxService).enqueue(
            eq("GUARANTOR_REQUEST"),
            eq(requestId),
            eq("GUARANTOR_REQUEST_APPROVED"),
            eq(applicantId),
            eq(guarantorId),
            eq(saccoId),
            eq("ST01"),
            argThat(details -> loanId.toString().equals(details.get("loanId"))
                && requestId.toString().equals(details.get("guarantorRequestId"))
                && guarantorId.toString().equals(details.get("guarantorId")))
        );
    }

    @Test
    void rejectGuarantorRequestNotifiesApplicantWithReason() {
        UUID requestId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID guarantorId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";
        GuarantorRequest request = GuarantorRequest.builder()
            .id(requestId)
            .loanApplicationId(loanId)
            .guarantorMemberId(guarantorId)
            .status(GuarantorRequestStatus.PENDING)
            .createdAt(OffsetDateTime.now())
            .build();
        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .applicantMemberId(applicantId)
            .saccoId(saccoId)
            .stationId("ST01")
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .amount(new BigDecimal("100000"))
            .requiredGuarantors(2)
            .status(LoanStatus.AWAITING_GUARANTORS)
            .build();

        when(guarantorRequestRepository.findByIdAndGuarantorMemberId(requestId, guarantorId)).thenReturn(Optional.of(request));
        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
        when(guarantorRequestRepository.countByLoanApplicationIdAndStatus(loanId, GuarantorRequestStatus.APPROVED)).thenReturn(0L);
        when(eligibilityService.check(saccoId, applicantId, LoanType.DEVELOPMENT_LOAN, new BigDecimal("100000")))
            .thenReturn(new EligibilityService.EligibilityResult(true, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN));

        loanWorkflowService.rejectGuarantorRequest(requestId, guarantorId, "Savings committed elsewhere");

        verify(outboxService).enqueue(
            eq("GUARANTOR_REQUEST"),
            eq(requestId),
            eq("GUARANTOR_REQUEST_REJECTED"),
            eq(applicantId),
            eq(guarantorId),
            eq(saccoId),
            eq("ST01"),
            argThat(details -> loanId.toString().equals(details.get("loanId"))
                && requestId.toString().equals(details.get("guarantorRequestId"))
                && guarantorId.toString().equals(details.get("guarantorId"))
                && "Savings committed elsewhere".equals(details.get("reasons")))
        );
    }

    private LoanApplication directOtpDraft(UUID appId, UUID applicantId, String saccoId, String selectedGuarantors) {
        return LoanApplication.builder()
            .id(appId)
            .applicantMemberId(applicantId)
            .saccoId(saccoId)
            .stationId("ST01")
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .amount(new BigDecimal("100000"))
            .tenorMonths(6)
            .status(LoanStatus.DRAFT)
            .requiredGuarantors(1)
            .selectedGuarantors(selectedGuarantors)
            .financialSnapshot("{\"principalPlusInterest\":120000.00}")
            .formData("{\"guarantorApprovalMode\":\"DIRECT_OTP\"}")
            .policySnapshot("{}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .version(0)
            .build();
    }

    private LoanProductSetting directOtpProduct(String saccoId) {
        return LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .guarantorsRequired(1)
            .freshFinancialDataRequired(false)
            .managerReviewRequired(true)
            .committeeReviewRequired(false)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private void stubDirectOtpFinancialProfile(String memberNo,
                                               String stationId,
                                               BigDecimal savingsBalance,
                                               List<ForesightActiveLoan> activeLoans,
                                               List<ForesightActiveLoan> paidLoans) {
        when(foresightDirectoryService.fetchAccountSummary(memberNo, stationId))
            .thenReturn(new ForesightAccountSummary(
                savingsBalance,
                new BigDecimal("50000.00"),
                new BigDecimal("125000.00"),
                List.of()
            ));
        when(foresightDirectoryService.fetchInvestments(eq(memberNo), eq(stationId), anyInt()))
            .thenReturn(List.of());
        when(foresightDirectoryService.fetchActiveLoans(memberNo, stationId))
            .thenReturn(activeLoans);
        when(foresightDirectoryService.fetchPaidLoans(memberNo, stationId))
            .thenReturn(paidLoans);
    }

    private void stubActiveMemberBatchLookup(String saccoId) {
        when(memberRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<UUID> ids = invocation.getArgument(0);
            List<Member> members = new java.util.ArrayList<>();
            ids.forEach(id -> members.add(activeMember(id, saccoId, "ST01")));
            return members;
        });
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
            null,
            null
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Only DRAFT applications or applications approved by all guarantors can be edited.");

        verify(loanApplicationRepository, never()).save(any(LoanApplication.class));
        verify(loanAttachmentService, never()).store(any(), any(), anyString());
    }

    @Test
    void saveDraftStoresTopUpWithConsolidatedPrincipal() {
        UUID memberId = UUID.randomUUID();
        UUID sourceLoanId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";

        LoanProductSetting product = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .guarantorsRequired(0)
            .allowApplicationWithActiveLoan(true)
            .minimumAmount(BigDecimal.ZERO)
            .maximumAmount(new BigDecimal("1000000.00"))
            .maxRepaymentMonths(12)
            .formSchema("{}")
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        LoanApplication sourceLoan = LoanApplication.builder()
            .id(sourceLoanId)
            .saccoId(saccoId)
            .stationId("ST01")
            .applicantMemberId(memberId)
            .status(LoanStatus.DISBURSED)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(loanApplicationRepository.findByIdAndApplicantMemberId(sourceLoanId, memberId)).thenReturn(Optional.of(sourceLoan));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(activeMember(memberId, saccoId, "ST01")));
        when(formSchemaService.getSchema(saccoId, product.getId(), LoanType.DEVELOPMENT_LOAN)).thenReturn(product);
        when(formSchemaService.extractFormData(anyMap(), eq("{}"))).thenReturn(new LinkedHashMap<>(Map.of("purpose", "WORKING CAPITAL")));
        when(loanApplicationRepository.findByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(eq(memberId), any()))
            .thenReturn(List.of())
            .thenReturn(List.of(sourceLoan));
        when(financialDetailsService.generateSnapshot(saccoId, memberId, product, new BigDecimal("1000"), 6, sourceLoanId))
            .thenReturn(new LinkedHashMap<>(Map.of(
                "requestedAmount", new BigDecimal("1000.00"),
                "topUpRequestedAmount", new BigDecimal("1000.00"),
                "topUpSettlementAmount", new BigDecimal("50000.00"),
                "principalAmount", new BigDecimal("51000.00"),
                "principalPlusInterest", new BigDecimal("56100.00")
            )));
        when(eligibilityService.check(saccoId, memberId, product, new BigDecimal("51000.00")))
            .thenReturn(new EligibilityService.EligibilityResult(true, new BigDecimal("0.3333"), new BigDecimal("200000"),
                new BigDecimal("66660.00")));
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(loanAttachmentService.store(any(), any(), anyString())).thenAnswer(invocation -> invocation.getArgument(2));
        when(loanAttachmentService.storeRequired(any(), any(), anyString())).thenAnswer(invocation -> invocation.getArgument(2));

        LoanApplication saved = loanWorkflowService.saveDraft(
            saccoId,
            memberId,
            product.getId(),
            LoanType.DEVELOPMENT_LOAN,
            new BigDecimal("1000"),
            6,
            Map.of("purpose", "WORKING CAPITAL"),
            null,
            List.of(),
            "{\"balance\":1000}",
            sourceLoanId,
            null,
            null
        );

        assertThat(saved.getTopUpSourceLoanId()).isEqualTo(sourceLoanId);
        assertThat(saved.getAmount()).isEqualByComparingTo("51000.00");
        assertThat(saved.getFinancialSnapshot()).contains("topUpRequestedAmount", "topUpSettlementAmount", "principalAmount");
        verify(eligibilityService).check(saccoId, memberId, product, new BigDecimal("51000.00"));
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
        when(paymentDetailsService.snapshotJsonForMember(memberId))
            .thenReturn("{\"available\":true,\"provider\":\"M-Pesa\",\"accountIdentifier\":\"255700000001\"}");
        doAnswer(invocation -> {
            LoanApplication target = invocation.getArgument(0);
            target.setStatus(LoanStatus.READY_FOR_MANAGER);
            return LoanStatus.READY_FOR_MANAGER;
        }).when(workflowRoutingService).moveToFirstReviewStage(any(LoanApplication.class), eq(memberId));
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        LoanApplication submitted = loanWorkflowService.submit(appId, memberId);

        assertThat(submitted.getStatus()).isEqualTo(LoanStatus.READY_FOR_MANAGER);
        assertThat(submitted.getPaymentDetailsSnapshot()).contains("255700000001");
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
        verify(outboxService).enqueue(
            eq("LOAN"),
            eq(appId),
            eq("LOAN_GUARANTORS_APPROVED"),
            eq(memberId),
            eq(saccoId),
            eq((String) null),
            any()
        );
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
    void rejectGuarantorRequestRequiresReason() {
        UUID requestId = UUID.randomUUID();
        UUID guarantorId = UUID.randomUUID();
        GuarantorRequest request = GuarantorRequest.builder()
            .id(requestId)
            .guarantorMemberId(guarantorId)
            .status(GuarantorRequestStatus.PENDING)
            .build();

        when(guarantorRequestRepository.findByIdAndGuarantorMemberId(requestId, guarantorId))
            .thenReturn(Optional.of(request));

        assertThatThrownBy(() -> loanWorkflowService.rejectGuarantorRequest(requestId, guarantorId, " "))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Enter a reason before rejecting this guarantee request.");

        verify(guarantorRequestRepository, never()).save(any());
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
