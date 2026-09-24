package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.integration.foresight.ForesightAccountSummary;
import com.sacco.mvp.integration.foresight.ForesightActiveLoan;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import com.sacco.mvp.integration.foresight.ForesightInvestment;
import com.sacco.mvp.integration.foresight.ForesightLoanPaymentSummary;
import com.sacco.mvp.integration.foresight.ForesightLoanPaymentTransaction;
import com.sacco.mvp.integration.foresight.ForesightMemberProfile;
import com.sacco.mvp.integration.foresight.UpstreamAvailabilityException;
import com.sacco.mvp.repository.*;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LoanWorkflowService {
    private static final long REVERSAL_WINDOW_HOURS = 24L;
    private static final String GUARANTOR_APPROVAL_MODE_FIELD = "guarantorApprovalMode";
    private static final String GUARANTOR_APPROVAL_MODE_DIRECT_OTP = "DIRECT_OTP";
    private static final String GUARANTOR_APPROVAL_MODE_LOGIN = "LOGIN";
    private static final String GUARANTOR_SOURCE_LMS = "LMS";
    private static final String GUARANTOR_SOURCE_FORESIGHT = "FORESIGHT";
    private static final String DIRECT_OTP_SEARCH_PHONE = "phone";
    private static final String DIRECT_OTP_SEARCH_EMAIL = "email";
    private static final int INVESTMENT_CODE_SAVINGS = 98;
    private static final int INVESTMENT_CODE_SHARES = 97;
    private static final int INVESTMENT_CODE_DEPOSITS = 96;
    private static final List<LoanStatus> APPLICATION_IN_PROGRESS_LOCK_STATUSES = List.of(
        LoanStatus.DRAFT,
        LoanStatus.SUBMITTED,
        LoanStatus.AWAITING_GUARANTORS,
        LoanStatus.ALL_GUARANTORS_APPROVED,
        LoanStatus.READY_FOR_MANAGER,
        LoanStatus.MANAGER_ACCEPTED,
        LoanStatus.AWAITING_LOAN_OFFICER,
        LoanStatus.LOAN_OFFICER_APPROVED,
        LoanStatus.AWAITING_CHAIRPERSON,
        LoanStatus.CHAIRPERSON_APPROVED,
        LoanStatus.AWAITING_BOARD,
        LoanStatus.AWAITING_CREDIT_COMMITTEE,
        LoanStatus.BOARD_APPROVED,
        LoanStatus.CREDIT_COMMITTEE_APPROVED,
        LoanStatus.AWAITING_ACCOUNTANT,
        LoanStatus.ACCOUNTANT_APPROVED,
        LoanStatus.READY_FOR_DISBURSEMENT
    );
    private static final List<LoanStatus> ACTIVE_LOAN_LOCK_STATUSES = List.of(
        LoanStatus.DISBURSED,
        LoanStatus.PAR,
        LoanStatus.DEFAULTED
    );
    private static final List<LoanStatus> REJECTED_ACKNOWLEDGEMENT_STATUSES = List.of(
        LoanStatus.MANAGER_REJECTED,
        LoanStatus.LOAN_OFFICER_REJECTED,
        LoanStatus.CHAIRPERSON_REJECTED,
        LoanStatus.BOARD_REJECTED,
        LoanStatus.CREDIT_COMMITTEE_REJECTED,
        LoanStatus.ACCOUNTANT_REJECTED,
        LoanStatus.REJECTED
    );
    private static final List<LoanStatus> TOP_UP_BLOCKING_STATUSES = List.of(
        LoanStatus.DRAFT,
        LoanStatus.SUBMITTED,
        LoanStatus.AWAITING_GUARANTORS,
        LoanStatus.ALL_GUARANTORS_APPROVED,
        LoanStatus.READY_FOR_MANAGER,
        LoanStatus.MANAGER_ACCEPTED,
        LoanStatus.AWAITING_LOAN_OFFICER,
        LoanStatus.LOAN_OFFICER_APPROVED,
        LoanStatus.AWAITING_CHAIRPERSON,
        LoanStatus.CHAIRPERSON_APPROVED,
        LoanStatus.AWAITING_BOARD,
        LoanStatus.AWAITING_CREDIT_COMMITTEE,
        LoanStatus.BOARD_APPROVED,
        LoanStatus.CREDIT_COMMITTEE_APPROVED,
        LoanStatus.AWAITING_ACCOUNTANT,
        LoanStatus.ACCOUNTANT_APPROVED,
        LoanStatus.READY_FOR_DISBURSEMENT,
        LoanStatus.DISBURSED,
        LoanStatus.PAR,
        LoanStatus.DEFAULTED,
        LoanStatus.PAID
    );
    private final LoanProductSettingRepository loanProductSettingRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final ExternalGuarantorRegistryRepository externalGuarantorRegistryRepository;
    private final BoardReviewRepository boardReviewRepository;
    private final ManagerReviewRepository managerReviewRepository;
    private final MemberRepository memberRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final FormSchemaService formSchemaService;
    private final EligibilityService eligibilityService;
    private final OutboxService outboxService;
    private final LoanAttachmentService loanAttachmentService;
    private final LoanProductRequiredAttachmentService requiredAttachmentService;
    private final FinancialDetailsService financialDetailsService;
    private final ObjectMapper objectMapper;
    private final ForesightDirectoryService foresightDirectoryService;
    private final ApplicationNumberService applicationNumberService;
    private final RoleDirectoryService roleDirectoryService;
    private final LoanProductWorkflowService loanProductWorkflowService;
    private final WorkflowRoutingService workflowRoutingService;
    private final LoanQualificationPolicyService loanQualificationPolicyService;
    private final PaymentDetailsService paymentDetailsService;
    private final ReversalRequestRepository reversalRequestRepository;
    private final AuditService auditService;
    private final PlatformTransactionManager transactionManager;
    private final ApplicationClock applicationClock;

    public List<LoanProductSetting> listProducts(String saccoId) {
        List<LoanProductSetting> products = loanProductSettingRepository.findBySaccoIdAndActiveTrue(saccoId);
        if (!products.isEmpty()) {
            return products.stream()
                .filter(LoanProductSetting::isAvailableForApplications)
                .sorted(java.util.Comparator.comparingInt(LoanProductSetting::getResolvedDisplayOrder))
                .toList();
        }
        return List.of();
    }

    public MemberDashboardData memberDashboard(UUID memberId) {
        Map<LoanStatus, Long> statusCounts = new EnumMap<>(LoanStatus.class);
        loanApplicationRepository.countByStatusForApplicant(memberId)
            .forEach(row -> statusCounts.put(row.getStatus(), row.getTotal()));
        List<LoanApplication> activeLoans = loanApplicationRepository
            .findByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(memberId, ACTIVE_LOAN_LOCK_STATUSES);
        LoanApplication latestCurrentApplication = loanApplicationRepository
            .findLatestVisibleCurrentForApplicant(memberId, APPLICATION_IN_PROGRESS_LOCK_STATUSES, REJECTED_ACKNOWLEDGEMENT_STATUSES, PageRequest.of(0, 1))
            .stream()
            .findFirst()
            .orElse(null);
        long unacknowledgedDisbursedApplicationCount = loanApplicationRepository
            .countByApplicantMemberIdAndStatusAndApplicantDisbursementAcknowledgedAtIsNull(memberId, LoanStatus.DISBURSED);
        long unacknowledgedRejectedApplicationCount = loanApplicationRepository
            .countByApplicantMemberIdAndStatusInAndApplicantRejectionAcknowledgedAtIsNull(memberId, REJECTED_ACKNOWLEDGEMENT_STATUSES);
        long pendingGuaranteeCount = guarantorRequestRepository.countVisiblePendingByGuarantorMemberId(memberId);
        return new MemberDashboardData(
            statusCounts,
            activeLoans,
            latestCurrentApplication,
            unacknowledgedDisbursedApplicationCount,
            unacknowledgedRejectedApplicationCount,
            pendingGuaranteeCount
        );
    }

    public MemberApplicationListData memberApplicationList(UUID memberId) {
        List<LoanApplication> currentApplications = loanApplicationRepository
            .findVisibleCurrentForApplicant(memberId, APPLICATION_IN_PROGRESS_LOCK_STATUSES, REJECTED_ACKNOWLEDGEMENT_STATUSES);
        LoanApplication latestCurrentApplication = loanApplicationRepository
            .findLatestVisibleCurrentForApplicant(memberId, APPLICATION_IN_PROGRESS_LOCK_STATUSES, REJECTED_ACKNOWLEDGEMENT_STATUSES, PageRequest.of(0, 1))
            .stream()
            .findFirst()
            .orElse(null);
        long archiveCount = loanApplicationRepository.countByStatusForApplicant(memberId).stream()
            .filter(row -> !APPLICATION_IN_PROGRESS_LOCK_STATUSES.contains(row.getStatus()))
            .mapToLong(LoanApplicationRepository.StatusCountProjection::getTotal)
            .sum();
        return new MemberApplicationListData(currentApplications, latestCurrentApplication, archiveCount);
    }

    public LoanApplication getMine(UUID appId, UUID memberId) {
        return loanApplicationRepository.findByIdAndApplicantMemberId(appId, memberId)
            .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
    }

    public Optional<LoanApplication> findMine(UUID appId, UUID memberId) {
        return appId == null || memberId == null
            ? Optional.empty()
            : loanApplicationRepository.findByIdAndApplicantMemberId(appId, memberId);
    }

    public Optional<LoanApplication> findApplication(UUID appId) {
        return appId == null ? Optional.empty() : loanApplicationRepository.findById(appId);
    }

    public List<LoanApplication> findApplications(Collection<UUID> appIds) {
        return appIds == null || appIds.isEmpty()
            ? List.of()
            : loanApplicationRepository.findAllById(appIds);
    }

    public Page<LoanApplication> memberArchivePage(UUID applicantMemberId,
                                                   Collection<LoanStatus> statuses,
                                                   String loanIdQuery,
                                                   OffsetDateTime updatedFrom,
                                                   OffsetDateTime updatedToExclusive,
                                                   PageRequest pageRequest) {
        return loanApplicationRepository.findMemberArchivePage(
            applicantMemberId, statuses, loanIdQuery, updatedFrom, updatedToExclusive, pageRequest);
    }

    public List<GuarantorRequest> guarantorRequests(UUID loanApplicationId) {
        return guarantorRequestRepository.findByLoanApplicationId(loanApplicationId);
    }

    public Optional<GuarantorRequest> findGuarantorRequest(UUID requestId) {
        return requestId == null ? Optional.empty() : guarantorRequestRepository.findById(requestId);
    }

    public Optional<GuarantorRequest> findGuarantorRequestForGuarantor(UUID requestId, UUID guarantorMemberId) {
        return requestId == null || guarantorMemberId == null
            ? Optional.empty()
            : guarantorRequestRepository.findByIdAndGuarantorMemberId(requestId, guarantorMemberId);
    }

    public Page<GuarantorRequest> guarantorArchivePage(UUID guarantorMemberId,
                                                       OffsetDateTime removalCutoff,
                                                       GuarantorRequestStatus status,
                                                       String loanIdQuery,
                                                       OffsetDateTime reviewedFrom,
                                                       OffsetDateTime reviewedToExclusive,
                                                       PageRequest pageRequest) {
        return guarantorRequestRepository.findArchivePageByGuarantorMemberId(
            guarantorMemberId, removalCutoff, status, loanIdQuery, reviewedFrom, reviewedToExclusive, pageRequest);
    }

    public List<BoardReview> boardReviewsForStage(UUID loanApplicationId, ApprovalWorkflowStage stage) {
        return boardReviewRepository.findByLoanApplicationIdAndReviewStage(loanApplicationId, stage);
    }

    public List<ManagerReview> staffReviewsForStage(UUID loanApplicationId, ApprovalWorkflowStage stage) {
        return managerReviewRepository.findByLoanApplicationIdAndReviewStageOrderByCreatedAtAsc(loanApplicationId, stage);
    }

    @Transactional
    public void acknowledgeDisbursement(UUID appId, UUID memberId) {
        LoanApplication app = getMine(appId, memberId);
        if (app.getStatus() != LoanStatus.DISBURSED) {
            throw new IllegalStateException("Only disbursed loans can be acknowledged.");
        }
        if (app.getApplicantDisbursementAcknowledgedAt() == null) {
            OffsetDateTime now = OffsetDateTime.now();
            app.setApplicantDisbursementAcknowledgedAt(now);
            app.setUpdatedAt(now);
            loanApplicationRepository.save(app);
        }
    }

    @Transactional
    public void acknowledgeRejection(UUID appId, UUID memberId) {
        LoanApplication app = getMine(appId, memberId);
        if (!REJECTED_ACKNOWLEDGEMENT_STATUSES.contains(app.getStatus())) {
            throw new IllegalStateException("Only rejected loan applications can be acknowledged.");
        }
        if (app.getApplicantRejectionAcknowledgedAt() == null) {
            OffsetDateTime now = OffsetDateTime.now();
            app.setApplicantRejectionAcknowledgedAt(now);
            app.setUpdatedAt(now);
            loanApplicationRepository.save(app);
        }
    }

    public Optional<LoanApplication> findApplicationInProgress(UUID memberId) {
        return loanApplicationRepository.findFirstByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(
            memberId,
            APPLICATION_IN_PROGRESS_LOCK_STATUSES
        );
    }

    private Optional<LoanApplication> findBlockingApplicationInProgress(UUID memberId, UUID allowedApplicationId) {
        return loanApplicationRepository.findByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(memberId, APPLICATION_IN_PROGRESS_LOCK_STATUSES)
            .stream()
            .filter(application -> allowedApplicationId == null || !Objects.equals(application.getId(), allowedApplicationId))
            .findFirst();
    }

    public Optional<LoanApplication> findActiveDisbursedLoan(UUID memberId) {
        return findActiveDisbursedLoans(memberId).stream().findFirst();
    }

    public List<LoanApplication> findActiveDisbursedLoans(UUID memberId) {
        return loanApplicationRepository.findByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(
            memberId,
            ACTIVE_LOAN_LOCK_STATUSES
        );
    }

    public void assertCanApplyForProduct(String saccoId, UUID memberId, LoanProductSetting product) {
        assertCanApplyForProduct(saccoId, memberId, product, null);
    }

    public void assertCanApplyForProduct(String saccoId, UUID memberId, LoanProductSetting product, UUID allowedApplicationId) {
        if (product == null) {
            throw new IllegalArgumentException("Loan product not found");
        }
        if (!Objects.equals(product.getSaccoId(), saccoId)) {
            throw new IllegalArgumentException("Loan product not found");
        }
        if (!product.isAvailableForApplications()) {
            throw new IllegalStateException("This loan product is not currently available for new applications.");
        }
        loanQualificationPolicyService.assertApplicantEligible(saccoId, memberId);
        findBlockingApplicationInProgress(memberId, allowedApplicationId)
            .ifPresent(application -> {
                throw new IllegalStateException(
                    "You already have ongoing loan application " + loanReference(application)
                        + " (" + humanizeApplicationLockStatus(application.getStatus()) + "). Continue or complete it before applying again."
                );
            });
        List<LoanApplication> activeLoans = findActiveDisbursedLoans(memberId);
        if (!activeLoans.isEmpty()) {
            if (!product.isApplicationWithActiveLoanAllowed()) {
                throw new IllegalStateException(activeLoanAwarenessMessage(activeLoans));
            }
        }
    }

    public boolean canRequestTopUp(LoanApplication app) {
        if (app == null) {
            return false;
        }
        return isLoanTopUpEnabled(app.getSaccoId())
            && isAllowedTopUpSource(app)
            && countBlockingTopUpApplications(app.getId(), null) == 0;
    }

    public boolean isLoanTopUpEnabled(String saccoId) {
        if (saccoId == null || saccoId.isBlank()) {
            return false;
        }
        return saccoSettingsRepository.findById(saccoId)
            .map(SaccoSettings::isLoanTopUpEnabled)
            .orElse(true);
    }

    public LoanApplication requireAllowedTopUpSourceLoan(String saccoId, UUID applicantId, UUID topUpSourceLoanId) {
        return requireAllowedTopUpSourceLoan(saccoId, applicantId, topUpSourceLoanId, null);
    }

    public LoanApplication requireAllowedTopUpSourceLoan(String saccoId, UUID applicantId, UUID topUpSourceLoanId,
                                                         UUID excludedApplicationId) {
        if (topUpSourceLoanId == null) {
            return null;
        }
        if (!isLoanTopUpEnabled(saccoId)) {
            throw new IllegalStateException("Loan top-up is disabled for this SACCO.");
        }
        LoanApplication sourceLoan = loanApplicationRepository.findByIdAndApplicantMemberId(topUpSourceLoanId, applicantId)
            .orElseThrow(() -> new IllegalArgumentException("Selected top-up source loan was not found."));
        if (!Objects.equals(sourceLoan.getSaccoId(), saccoId)) {
            throw new IllegalArgumentException("Selected top-up source loan was not found.");
        }
        if (sourceLoan.getStatus() != LoanStatus.DISBURSED) {
            throw new IllegalStateException("Only active disbursed loans can be topped up.");
        }
        if (sourceLoan.getFinalDueDate() != null && sourceLoan.getFinalDueDate().isBefore(applicationClock.today())) {
            throw new IllegalStateException("This loan has already reached its final due date.");
        }
        if (countBlockingTopUpApplications(sourceLoan.getId(), excludedApplicationId) > 0) {
            throw new IllegalStateException("This loan already has an open top-up application.");
        }
        return sourceLoan;
    }

    public LoanApplication saveDraft(String saccoId, UUID applicantId, LoanType loanType, BigDecimal amount,
                                     Integer tenorMonths, Map<String, String> requestParams, UUID existingId,
                                     List<?> guarantorSelections,
                                     String financialSnapshotJson, UUID topUpSourceLoanId, List<MultipartFile> attachments,
                                     Map<UUID, List<MultipartFile>> requiredAttachments) {
        return saveDraft(saccoId, applicantId, null, loanType, amount, tenorMonths, requestParams, existingId, guarantorSelections,
            financialSnapshotJson, topUpSourceLoanId, attachments, requiredAttachments);
    }

    public LoanApplication saveDraft(String saccoId, UUID applicantId, UUID loanProductId, LoanType loanType, BigDecimal amount,
                                     Integer tenorMonths, Map<String, String> requestParams, UUID existingId,
                                     List<?> guarantorSelections,
                                     String financialSnapshotJson, UUID topUpSourceLoanId, List<MultipartFile> attachments,
                                     Map<UUID, List<MultipartFile>> requiredAttachments) {
        LoanApplication existingDraft = existingId == null
            ? null
            : loanApplicationRepository.findByIdAndApplicantMemberId(existingId, applicantId)
                .map(existing -> {
                    if (existing.getStatus() != LoanStatus.DRAFT
                        && existing.getStatus() != LoanStatus.ALL_GUARANTORS_APPROVED) {
                        throw new IllegalStateException("Only DRAFT applications or applications approved by all guarantors can be edited.");
                    }
                    return existing;
                })
                .orElseThrow(() -> new IllegalArgumentException("Loan draft not found"));
        UUID effectiveTopUpSourceLoanId = topUpSourceLoanId == null && existingDraft != null
            ? existingDraft.getTopUpSourceLoanId()
            : topUpSourceLoanId;
        LoanApplication topUpSourceLoan = requireAllowedTopUpSourceLoan(
            saccoId,
            applicantId,
            effectiveTopUpSourceLoanId,
            existingDraft == null ? null : existingDraft.getId()
        );
        boolean reEditingAfterGuarantorApproval = existingDraft != null
            && existingDraft.getStatus() == LoanStatus.ALL_GUARANTORS_APPROVED;
        String applicantStationId = existingDraft != null
            ? coalesceStationId(existingDraft.getStationId(), resolveMemberStationId(applicantId))
            : requireMemberStationId(applicantId);
        LoanProductSetting product = loanProductId == null
            ? formSchemaService.getSchema(saccoId, loanType)
            : formSchemaService.getSchema(saccoId, loanProductId, loanType);
        LoanType resolvedLoanType = product.getLoanType();
        assertCanApplyForProduct(saccoId, applicantId, product, existingDraft == null ? null : existingDraft.getId());
        validateRepaymentPeriod(product, tenorMonths);
        Map<String, Object> generatedTopUpSnapshot = topUpSourceLoan == null
            ? null
            : financialDetailsService.generateSnapshot(saccoId, applicantId, product, amount, tenorMonths, topUpSourceLoan.getId());
        BigDecimal applicationAmount = topUpSourceLoan == null
            ? amount
            : requireFinancialSnapshotAmount(generatedTopUpSnapshot, "principalAmount");
        validateRequestedAmount(product, applicationAmount);
        requireLoadedFinancialDataForDraft(product, financialSnapshotJson);
        Map<String, Object> formData = formSchemaService.extractFormData(requestParams, product.getFormSchema());
        String guarantorApprovalMode = normalizeGuarantorApprovalMode(requestParams.get(GUARANTOR_APPROVAL_MODE_FIELD));
        formData.put(GUARANTOR_APPROVAL_MODE_FIELD, guarantorApprovalMode);
        formSchemaService.validateAgainstSchema(product.getFormSchema(), formData);
        appendLoanPurpose(formData, requestParams.get("purpose"));
        List<LoanProductRequiredAttachment> requiredAttachmentDefinitions = validateApplicantAttachmentRequirement(
            product,
            existingDraft,
            attachments,
            requiredAttachments
        );
        List<?> effectiveGuarantorSelections = preserveSavedGuarantorSelections(existingDraft, guarantorSelections);
        List<GuarantorSelection> normalizedGuarantors = validateGuarantorSelections(
            saccoId,
            applicantStationId,
            applicantId,
            product.getGuarantorsRequired(),
            applicationAmount,
            effectiveGuarantorSelections,
            product,
            guarantorApprovalMode
        );

        EligibilityService.EligibilityResult eligibility = loanProductId == null
            ? checkApplicantSavingsEligibility(saccoId, applicantId, resolvedLoanType, applicationAmount)
            : checkApplicantSavingsEligibility(saccoId, applicantId, product, applicationAmount);
        String snapshot = eligibilityService.policySnapshotJson(
            eligibility,
            product.getGuarantorsRequired(),
            loanProductWorkflowService.snapshotData(
                loanProductWorkflowService.resolveForProduct(saccoId, product)
            )
        );

        LoanApplication application = existingDraft == null
            ? LoanApplication.builder()
                .id(UUID.randomUUID())
                .applicationNumber(applicationNumberService.nextFor(saccoId))
                .createdAt(OffsetDateTime.now())
                .build()
            : existingDraft;

        application.setSaccoId(saccoId);
        application.setStationId(applicantStationId);
        application.setApplicantMemberId(applicantId);
        application.setTopUpSourceLoanId(topUpSourceLoan == null ? null : topUpSourceLoan.getId());
        application.setLoanType(resolvedLoanType);
        application.setLoanProductSettingId(product.getId());
        application.setAmount(applicationAmount);
        application.setTenorMonths(tenorMonths);
        application.setStatus(LoanStatus.DRAFT);
        application.setFormData(formSchemaService.toJson(formData));
        application.setRequiredGuarantors(product.getGuarantorsRequired());
        application.setPolicySnapshot(snapshot);
        application.setSelectedGuarantors(toGuarantorSelectionJson(normalizedGuarantors));
        application.setFinancialSnapshot(financialSnapshotForDraft(generatedTopUpSnapshot, financialSnapshotJson));
        if (application.getAttachmentsJson() == null) {
            application.setAttachmentsJson("[]");
        }
        application.setApplicantSignatureText(null);
        application.setApplicantSignatureVerifiedAt(null);
        application.setUpdatedAt(OffsetDateTime.now());
        return inTransaction(() -> {
            LoanApplication persisted = loanApplicationRepository.save(application);
            if (reEditingAfterGuarantorApproval) {
                expireGuarantorApprovalsForApplicantEdit(persisted.getId());
            }
            persisted.setAttachmentsJson(loanAttachmentService.store(persisted.getId(), attachments, persisted.getAttachmentsJson()));
            persisted.setAttachmentsJson(loanAttachmentService.storeRequired(
                persisted.getId(),
                requiredAttachmentDefinitions.stream()
                    .map(requirement -> new LoanAttachmentService.RequiredAttachmentUpload(
                        requirement.getId(),
                        requirement.getAttachmentName(),
                        requiredAttachments == null ? List.of() : requiredAttachments.getOrDefault(requirement.getId(), List.of())
                    ))
                    .toList(),
                persisted.getAttachmentsJson()
            ));
            LoanApplication finalSaved = loanApplicationRepository.save(persisted);
            auditLoan(finalSaved, applicantId, "LOAN_APPLICATION_DRAFT", "Loan application draft");
            return finalSaved;
        });
    }

    private void expireGuarantorApprovalsForApplicantEdit(UUID appId) {
        List<GuarantorRequest> requests = guarantorRequestRepository.findByLoanApplicationId(appId);
        if (requests.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (GuarantorRequest request : requests) {
            request.setStatus(GuarantorRequestStatus.EXPIRED);
            request.setDecisionReason("Applicant reopened application for editing");
            request.setDecidedAt(now);
            request.setGuarantorSignatureText(null);
            request.setGuarantorSignatureVerifiedAt(null);
        }
        guarantorRequestRepository.saveAll(requests);
    }

    private List<LoanProductRequiredAttachment> validateApplicantAttachmentRequirement(LoanProductSetting product,
                                                                                       LoanApplication existingDraft,
                                                                                       List<MultipartFile> attachments,
                                                                                       Map<UUID, List<MultipartFile>> requiredAttachments) {
        if (product == null || !product.isApplicantAttachmentRequired()) {
            return List.of();
        }
        List<LoanProductRequiredAttachment> requirements = requiredAttachmentService.activeForProduct(product.getId());
        if (!requirements.isEmpty()) {
            for (LoanProductRequiredAttachment requirement : requirements) {
                List<MultipartFile> files = requiredAttachments == null ? List.of() : requiredAttachments.getOrDefault(requirement.getId(), List.of());
                validateRequiredAttachmentSize(requirement, files);
                if (!hasNewAttachment(files) && !hasExistingRequiredApplicationAttachment(existingDraft, requirement.getId())) {
                    throw new IllegalArgumentException("Upload " + requirement.getAttachmentName() + " before saving this loan application.");
                }
            }
            return requirements;
        }
        if (hasNewAttachment(attachments) || hasExistingApplicationAttachment(existingDraft)) {
            return List.of();
        }
        throw new IllegalArgumentException("Upload at least one applicant attachment before saving this loan application.");
    }

    private void validateRequiredAttachmentSize(LoanProductRequiredAttachment requirement, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        long maxBytes = requiredAttachmentService.maxSizeBytes(requirement);
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty() && file.getSize() > maxBytes) {
                throw new IllegalArgumentException(requirement.getAttachmentName() + " must be "
                    + requirement.getMaxSizeMb().stripTrailingZeros().toPlainString() + " MB or smaller.");
            }
        }
    }

    private boolean hasNewAttachment(List<MultipartFile> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return false;
        }
        return attachments.stream().anyMatch(file -> file != null && !file.isEmpty());
    }

    private boolean hasExistingApplicationAttachment(LoanApplication existingDraft) {
        if (existingDraft == null) {
            return false;
        }
        return loanAttachmentService.parse(existingDraft.getAttachmentsJson()).stream()
            .anyMatch(item -> LoanAttachmentService.CATEGORY_APPLICATION_ATTACHMENT.equals(String.valueOf(item.get("attachmentCategory"))));
    }

    private boolean hasExistingRequiredApplicationAttachment(LoanApplication existingDraft, UUID requirementId) {
        if (existingDraft == null || requirementId == null) {
            return false;
        }
        return loanAttachmentService.parse(existingDraft.getAttachmentsJson()).stream()
            .anyMatch(item -> LoanAttachmentService.CATEGORY_APPLICATION_ATTACHMENT.equals(String.valueOf(item.get("attachmentCategory")))
                && requirementId.toString().equals(String.valueOf(item.get("requiredAttachmentId"))));
    }

    private void appendLoanPurpose(Map<String, Object> formData, String purpose) {
        if (purpose == null) {
            return;
        }
        String normalizedPurpose = purpose.trim().replaceAll("\\s+", " ");
        if (normalizedPurpose.isBlank()) {
            return;
        }
        if (!normalizedPurpose.equals(normalizedPurpose.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Loan purpose must be written in capital letters.");
        }
        if (normalizedPurpose.split("\\s+").length > 10) {
            throw new IllegalArgumentException("Loan purpose must be 10 words or fewer.");
        }
        formData.put("purpose", normalizedPurpose);
    }

    private boolean isAllowedTopUpSource(LoanApplication app) {
        return app != null
            && app.getStatus() == LoanStatus.DISBURSED
            && (app.getFinalDueDate() == null || !app.getFinalDueDate().isBefore(applicationClock.today()));
    }

    private long countBlockingTopUpApplications(UUID sourceLoanId, UUID excludedApplicationId) {
        if (sourceLoanId == null) {
            return 0L;
        }
        return excludedApplicationId == null
            ? loanApplicationRepository.countByTopUpSourceLoanIdAndStatusIn(sourceLoanId, TOP_UP_BLOCKING_STATUSES)
            : loanApplicationRepository.countByTopUpSourceLoanIdAndStatusInAndIdNot(sourceLoanId, TOP_UP_BLOCKING_STATUSES, excludedApplicationId);
    }

    public LoanApplication saveAndSubmit(String saccoId, UUID applicantId, LoanType loanType, BigDecimal amount,
                                         Integer tenorMonths, Map<String, String> requestParams, UUID existingId,
                                         List<?> guarantorSelections,
                                         String financialSnapshotJson, UUID topUpSourceLoanId, List<MultipartFile> attachments,
                                         Map<UUID, List<MultipartFile>> requiredAttachments) {
        LoanApplication saved = saveDraft(
            saccoId, applicantId, loanType, amount, tenorMonths, requestParams, existingId, guarantorSelections,
            financialSnapshotJson, topUpSourceLoanId, attachments, requiredAttachments);
        LoanApplication submitted = submit(saved.getId(), applicantId);
        // Return reloaded row to guarantee caller sees persisted status/form data.
        return getMine(submitted.getId(), applicantId);
    }

    public LoanApplication saveAndSubmit(String saccoId, UUID applicantId, UUID loanProductId, LoanType loanType, BigDecimal amount,
                                         Integer tenorMonths, Map<String, String> requestParams, UUID existingId,
                                         List<?> guarantorSelections,
                                         String financialSnapshotJson, UUID topUpSourceLoanId, List<MultipartFile> attachments,
                                         Map<UUID, List<MultipartFile>> requiredAttachments) {
        LoanApplication saved = saveDraft(
            saccoId, applicantId, loanProductId, loanType, amount, tenorMonths, requestParams, existingId, guarantorSelections,
            financialSnapshotJson, topUpSourceLoanId, attachments, requiredAttachments);
        LoanApplication submitted = submit(saved.getId(), applicantId);
        return getMine(submitted.getId(), applicantId);
    }

    public LoanApplication removeApplicationAttachment(UUID appId, UUID memberId, String attachmentId) {
        LoanApplication app = getMine(appId, memberId);
        if (app.getStatus() != LoanStatus.DRAFT && app.getStatus() != LoanStatus.ALL_GUARANTORS_APPROVED) {
            throw new IllegalStateException("Only draft applications or applications approved by all guarantors can be edited.");
        }
        return inTransaction(() -> {
            LoanAttachmentService.AttachmentRemoval removal = loanAttachmentService.removeApplicationAttachment(
                app.getId(),
                attachmentId,
                app.getAttachmentsJson()
            );
            boolean reEditingAfterGuarantorApproval = app.getStatus() == LoanStatus.ALL_GUARANTORS_APPROVED;
            app.setAttachmentsJson(removal.attachmentsJson());
            if (reEditingAfterGuarantorApproval) {
                app.setStatus(LoanStatus.DRAFT);
                app.setApplicantSignatureText(null);
                app.setApplicantSignatureVerifiedAt(null);
                expireGuarantorApprovalsForApplicantEdit(app.getId());
            }
            app.setUpdatedAt(OffsetDateTime.now());
            LoanApplication saved = loanApplicationRepository.save(app);
            auditLoan(saved, memberId, "LOAN_APPLICATION_ATTACHMENT_REMOVED", "Loan application attachment removed", Map.of(
                "attachmentId", attachmentId,
                "attachmentName", removal.originalName()
            ));
            return saved;
        });
    }

    public LoanApplication submit(UUID appId, UUID memberId) {
        LoanApplication app = getMine(appId, memberId);
        if (app.getStatus() == LoanStatus.ALL_GUARANTORS_APPROVED) {
            return submitToManager(appId, memberId);
        }
        if (app.getStatus() != LoanStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT applications can be sent to guarantors");
        }
        if (app.getFinancialSnapshot() == null || app.getFinancialSnapshot().isBlank()) {
            throw new IllegalStateException("Load SACCO financial details before submitting the application");
        }
        LoanProductSetting product = resolveWorkflowProduct(app);
        validateTopUpSourceForApplication(app);
        syncFinancialSnapshotToCurrentProduct(app);
        refreshFinancialSnapshotIfRequired(app, product);
        loanQualificationPolicyService.assertApplicantEligible(app.getSaccoId(), app.getApplicantMemberId());

        EligibilityService.EligibilityResult result = checkApplicantSavingsEligibility(app, product);
        app.setPolicySnapshot(eligibilityService.policySnapshotJson(
            result,
            app.getRequiredGuarantors() == null ? 0 : Math.max(app.getRequiredGuarantors(), 0),
            loanProductWorkflowService.snapshotData(
                loanProductWorkflowService.resolveForProduct(app.getSaccoId(), product)
            )
        ));
        capturePaymentDetailsIfMissing(app);
        List<GuarantorSelection> selectedGuarantors = app.getRequiredGuarantors() == null || app.getRequiredGuarantors() <= 0
            ? List.of()
            : validateGuarantorSelections(
                app.getSaccoId(),
                coalesceStationId(app.getStationId(), requireMemberStationId(memberId)),
                memberId,
                app.getRequiredGuarantors(),
                app.getAmount(),
                parseSelectedGuarantorSelections(app.getSelectedGuarantors()),
                product,
                guarantorApprovalMode(app)
            );
        return inTransaction(() -> persistSubmittedApplication(app, memberId, selectedGuarantors));
    }

    private LoanApplication persistSubmittedApplication(LoanApplication app,
                                                       UUID memberId,
                                                       List<GuarantorSelection> selectedGuarantors) {
        app.setSubmittedAt(OffsetDateTime.now());

        if (app.getRequiredGuarantors() == 0) {
            moveIntoConfiguredReviewStage(app, memberId);
        } else {
            if (selectedGuarantors.size() != app.getRequiredGuarantors()) {
                throw new IllegalStateException("Select exactly " + app.getRequiredGuarantors() + " guarantors before submitting.");
            }
            app.setSelectedGuarantors(toGuarantorSelectionJson(selectedGuarantors));
            app.setStatus(LoanStatus.AWAITING_GUARANTORS);
        }
        app.setUpdatedAt(OffsetDateTime.now());
        LoanApplication submitted = loanApplicationRepository.save(app);
        auditLoan(submitted, memberId, "LOAN_APPLICATION_SUBMITTED", "Loan application submitted");
        if (submitted.getRequiredGuarantors() > 0) {
            refreshGuarantorRequestsForSubmission(
                submitted,
                memberId,
                selectedGuarantors
            );
            LoanApplication reloaded = getMine(submitted.getId(), memberId);
            auditLoan(reloaded, memberId, "LOAN_SENT_TO_GUARANTORS", "Application sent to guarantors");
            return reloaded;
        }
        auditLoan(submitted, memberId, "LOAN_SENT_TO_STAFF", "Application sent to staff");
        return submitted;
    }

    public LoanApplication submitToManager(UUID appId, UUID memberId) {
        LoanApplication app = getMine(appId, memberId);
        if (app.getStatus() != LoanStatus.ALL_GUARANTORS_APPROVED) {
            throw new IllegalStateException("Only applications with all guarantors approved can be submitted to manager");
        }
        if (hasPendingGuarantorRemovalRequest(app.getId())) {
            throw new IllegalStateException("Approve the pending guarantor removal request before submitting this application.");
        }
        if (app.getFinancialSnapshot() == null || app.getFinancialSnapshot().isBlank()) {
            throw new IllegalStateException("Load SACCO financial details before submitting the application");
        }
        LoanProductSetting product = resolveWorkflowProduct(app);
        validateTopUpSourceForApplication(app);
        syncFinancialSnapshotToCurrentProduct(app);
        refreshFinancialSnapshotIfRequired(app, product);
        loanQualificationPolicyService.assertApplicantEligible(app.getSaccoId(), app.getApplicantMemberId());
        EligibilityService.EligibilityResult result = checkApplicantSavingsEligibility(app, product);
        app.setPolicySnapshot(eligibilityService.policySnapshotJson(
            result,
            app.getRequiredGuarantors() == null ? 0 : Math.max(app.getRequiredGuarantors(), 0),
            loanProductWorkflowService.snapshotData(
                loanProductWorkflowService.resolveForProduct(app.getSaccoId(), product)
            )
        ));

        capturePaymentDetailsIfMissing(app);
        return inTransaction(() -> {
            moveIntoConfiguredReviewStage(app, memberId);
            LoanApplication saved = loanApplicationRepository.save(app);
            auditLoan(saved, memberId, "LOAN_SENT_TO_STAFF", "Application sent to staff");
            return saved;
        });
    }

    private void capturePaymentDetailsIfMissing(LoanApplication app) {
        if (app.getPaymentDetailsSnapshot() == null || app.getPaymentDetailsSnapshot().isBlank()) {
            app.setPaymentDetailsSnapshot(paymentDetailsService.snapshotJsonForMember(app.getApplicantMemberId()));
        }
    }

    private void validateTopUpSourceForApplication(LoanApplication app) {
        if (app != null && app.getTopUpSourceLoanId() != null) {
            requireAllowedTopUpSourceLoan(app.getSaccoId(), app.getApplicantMemberId(), app.getTopUpSourceLoanId(), app.getId());
        }
    }

    @Transactional(readOnly = true)
    public boolean hasPendingGuarantorRemovalRequest(UUID appId) {
        return !reversalRequestRepository.findByLoanApplicationIdAndTypeAndStatusOrderByCreatedAtDesc(
            appId,
            ReversalRequestType.GUARANTOR_DECISION_UNDO,
            ReversalRequestStatus.PENDING
        ).isEmpty();
    }

    private void assertApplicantCanAdvanceToManagerReview(UUID applicantId, UUID currentApplicationId) {
        findBlockingApplicationInProgress(applicantId, currentApplicationId)
            .ifPresent(existing -> {
                throw new IllegalStateException(
                    "You already have loan application " + existing.getId().toString().substring(0, 8)
                        + " on review (" + humanizeApplicationLockStatus(existing.getStatus())
                        + "). Wait until it is disbursed before sending another application forward."
                );
            });
    }

    private String humanizeApplicationLockStatus(LoanStatus status) {
        return switch (status) {
            case READY_FOR_MANAGER -> "On Review By Manager";
            case AWAITING_LOAN_OFFICER -> "On Review By Loan Officer";
            case AWAITING_BOARD, BOARD_APPROVED -> "On Review By Board";
            case AWAITING_CREDIT_COMMITTEE -> "On Review By Credit Committee";
            case AWAITING_ACCOUNTANT, ACCOUNTANT_APPROVED -> "On Review By Accountant";
            case READY_FOR_DISBURSEMENT -> "Ready for Disbursement";
            default -> status.name().replace('_', ' ');
        };
    }

    @Transactional
    public void recordApplicantSignature(UUID appId, UUID applicantId, String signatureText, OffsetDateTime verifiedAt) {
        LoanApplication app = getMine(appId, applicantId);
        app.setApplicantSignatureText(signatureText == null ? null : signatureText.trim());
        app.setApplicantSignatureVerifiedAt(verifiedAt);
        app.setUpdatedAt(OffsetDateTime.now());
        loanApplicationRepository.save(app);
    }

    public Page<Member> searchGuarantors(String saccoId, String stationId, UUID applicantId, String q, int page, int size) {
        return searchGuarantors(saccoId, stationId, applicantId, q, "number", page, size);
    }

    public Page<Member> searchGuarantors(String saccoId, String stationId, UUID applicantId, String q, String searchBy, int page, int size) {
        String query = q == null ? "" : q.trim();
        if (query.isBlank()) {
            return Page.empty(PageRequest.of(page, size));
        }

        String normalizedStationId = normalizeOptional(stationId);
        if ("name".equalsIgnoreCase(searchBy)) {
            String lowercaseQuery = query.toLowerCase(Locale.ROOT);
            if (lowercaseQuery.length() < 2) {
                return Page.empty(PageRequest.of(page, size));
            }
            return memberRepository.findGuarantorCandidatesByName(
                saccoId,
                normalizedStationId,
                applicantId,
                lowercaseQuery,
                PageRequest.of(page, size)
            );
        }

        Optional<Member> exactMemberNo = memberRepository.findBySaccoIdAndStatusAndMemberNoIgnoreCase(
            saccoId, MemberStatus.ACTIVE, query);
        if (exactMemberNo.isPresent()
            && !exactMemberNo.get().getId().equals(applicantId)
            && exactMemberNo.get().isMemberAccess()
            && matchesStation(exactMemberNo.get(), normalizedStationId)) {
            return new PageImpl<>(List.of(exactMemberNo.get()), PageRequest.of(page, size), 1);
        }

        return Page.empty(PageRequest.of(page, size));
    }

    public List<GuarantorCandidate> searchGuarantorCandidates(String saccoId,
                                                              String stationId,
                                                              UUID applicantId,
                                                              String q,
                                                              LoanType loanType,
                                                              int page,
                                                              int size) {
        return searchGuarantorCandidates(saccoId, stationId, applicantId, q, "number", loanType, page, size);
    }

    public List<GuarantorCandidate> searchGuarantorCandidates(String saccoId,
                                                              String stationId,
                                                              UUID applicantId,
                                                              String q,
                                                              String searchBy,
                                                              LoanType loanType,
                                                              int page,
                                                              int size) {
        LoanProductSetting product = loanType == null ? null : formSchemaService.getSchema(saccoId, loanType);
        return searchGuarantorCandidates(saccoId, stationId, applicantId, q, searchBy, product, page, size);
    }

    public List<GuarantorCandidate> searchGuarantorCandidates(String saccoId,
                                                              String stationId,
                                                              UUID applicantId,
                                                              String q,
                                                              String searchBy,
                                                              LoanProductSetting product,
                                                              int page,
                                                              int size) {
        return searchGuarantors(saccoId, stationId, applicantId, q, searchBy, page, size).getContent().stream()
            .map(member -> {
                String reason = Optional.ofNullable(loanQualificationPolicyService.guarantorFailureReason(saccoId, member.getId(), null, product))
                    .orElse(Optional.empty())
                    .orElse("");
                return new GuarantorCandidate(
                    member.getId(),
                    member.getMemberNo(),
                    member.getFullName(),
                    reason.isBlank(),
                    reason
                );
            })
            .toList();
    }

    public List<DirectOtpGuarantorCandidate> searchDirectOtpGuarantorCandidates(String saccoId,
                                                                                String stationId,
                                                                                UUID applicantId,
                                                                                String q,
                                                                                String searchBy,
                                                                                LoanProductSetting product) {
        String mode = normalizeDirectOtpSearchBy(searchBy);
        String lookupValue = normalizeDirectOtpLookupValue(q, mode);
        if (lookupValue == null) {
            return List.of();
        }
        ForesightDirectoryService.MemberProfileLookupResult lookup = lookupDirectOtpProfile(mode, lookupValue);
        if (lookup.isNotFound()) {
            return List.of();
        }
        if (!lookup.isFound()) {
            throw new UpstreamAvailabilityException("Member directory is unavailable right now. Please try again later.", null);
        }
        ForesightMemberProfile profile = requireProfileIdentity(lookup.profile());
        DirectOtpFinancialSnapshot snapshot = loadDirectOtpFinancialSnapshot(
            profile,
            loanQualificationPolicyService.externalDefaultedRiskCheckRequired(saccoId, stationId),
            loanQualificationPolicyService.resolvedPortfolioAtRiskDays(saccoId, stationId)
        );
        GuarantorSelection selection = selectionFromProfile(profile, mode, lookupValue, snapshot);
        CandidateResolution resolution = resolveDirectOtpCandidate(saccoId, stationId, applicantId, selection);
        GuarantorSelection resolvedSelection = resolution.selection();
        String disabledReason = resolution.disabledReason();
        if (disabledReason == null || disabledReason.isBlank()) {
            disabledReason = resolvedSelection.localMemberId() == null
                ? Optional.ofNullable(loanQualificationPolicyService.guarantorFailureReasonForExternal(
                    saccoId,
                    stationId,
                    resolvedSelection.externalMemberNo(),
                    resolvedSelection.externalStationId(),
                    snapshot.savingsBalance(),
                    snapshot.activeLoanCount(),
                    snapshot.defaultedRiskLoanCount(),
                    null,
                    product
                )).orElse(Optional.empty()).orElse("")
                : Optional.ofNullable(loanQualificationPolicyService.guarantorFailureReason(
                    saccoId,
                    resolvedSelection.localMemberId(),
                    null,
                    product
                )).orElse(Optional.empty()).orElse("");
        }
        return List.of(new DirectOtpGuarantorCandidate(
            resolvedSelection.source(),
            resolvedSelection.localMemberId(),
            resolvedSelection.identityKey(),
            selectionToken(resolvedSelection),
            resolvedSelection.externalMemberNo(),
            resolvedSelection.externalStationId(),
            profile.saccoName(),
            resolvedSelection.displayName(),
            resolvedSelection.email(),
            resolvedSelection.phone(),
            snapshot.savingsBalance(),
            snapshot.sharesBalance(),
            snapshot.depositsBalance(),
            snapshot.activeLoanCount(),
            snapshot.paidLoanCount(),
            snapshot.activeLoans().stream().map(this::loanRow).toList(),
            snapshot.paidLoans().stream().map(this::loanRow).toList(),
            disabledReason == null || disabledReason.isBlank(),
            disabledReason == null ? "" : disabledReason,
            mode,
            lookupValue
        ));
    }

    public DirectOtpLoanDetails directOtpLoanPaymentDetails(String q, String searchBy, String loanId) {
        String mode = normalizeDirectOtpSearchBy(searchBy);
        String lookupValue = normalizeDirectOtpLookupValue(q, mode);
        String normalizedLoanId = normalizeOptional(loanId);
        if (lookupValue == null || normalizedLoanId == null) {
            throw new IllegalArgumentException("Select a valid loan before loading payment details.");
        }
        ForesightDirectoryService.MemberProfileLookupResult lookup = lookupDirectOtpProfile(mode, lookupValue);
        if (!lookup.isFound()) {
            throw new UpstreamAvailabilityException("Member directory is unavailable right now. Please try again later.", null);
        }
        ForesightMemberProfile profile = requireProfileIdentity(lookup.profile());
        List<ForesightLoanPaymentSummary> summaries = foresightDirectoryService.fetchLoanPaymentSummary(
            profile.memberNo(),
            profile.stationId(),
            normalizedLoanId
        );
        List<ForesightLoanPaymentTransaction> transactions = foresightDirectoryService.fetchLoanPaymentTransactions(
            profile.memberNo(),
            profile.stationId(),
            normalizedLoanId
        );
        return new DirectOtpLoanDetails(
            summaries.stream().map(this::paymentSummaryRow).toList(),
            transactions.stream().map(this::paymentTransactionRow).toList()
        );
    }

    private GuarantorSelection normalizeDirectOtpSelection(String saccoId,
                                                          String stationId,
                                                          UUID applicantId,
                                                          BigDecimal pendingLoanAmount,
                                                          LoanProductSetting product,
                                                          GuarantorSelection submittedSelection) {
        if (submittedSelection == null) {
            throw new IllegalArgumentException("Guarantor not found");
        }
        String lookupBy = submittedSelection.lookupBy();
        String lookupValue = normalizeOptional(submittedSelection.lookupValue());
        if (lookupBy == null && submittedSelection.phone() != null) {
            lookupBy = DIRECT_OTP_SEARCH_PHONE;
            lookupValue = submittedSelection.phone();
        }
        if (lookupBy == null && submittedSelection.email() != null) {
            lookupBy = DIRECT_OTP_SEARCH_EMAIL;
            lookupValue = submittedSelection.email();
        }
        if (lookupBy == null || lookupValue == null) {
            return submittedSelection;
        }

        lookupValue = normalizeDirectOtpLookupValue(lookupValue, lookupBy);
        if (lookupValue == null) {
            throw new IllegalArgumentException("Enter a valid phone number or email address for Direct OTP guarantor search.");
        }
        ForesightDirectoryService.MemberProfileLookupResult lookup = lookupDirectOtpProfile(lookupBy, lookupValue);
        if (lookup.isNotFound()) {
            throw new IllegalArgumentException("Foresight member profile was not found.");
        }
        if (!lookup.isFound()) {
            throw new UpstreamAvailabilityException("Member directory is unavailable right now. Please try again later.", null);
        }
        ForesightMemberProfile profile = requireProfileIdentity(lookup.profile());
        DirectOtpFinancialSnapshot snapshot = loadDirectOtpFinancialSnapshot(
            profile,
            loanQualificationPolicyService.externalDefaultedRiskCheckRequired(saccoId, stationId),
            loanQualificationPolicyService.resolvedPortfolioAtRiskDays(saccoId, stationId)
        );
        GuarantorSelection refreshedSelection = selectionFromProfile(profile, lookupBy, lookupValue, snapshot);
        CandidateResolution resolution = resolveDirectOtpCandidate(saccoId, stationId, applicantId, refreshedSelection);
        if (resolution.disabledReason() != null && !resolution.disabledReason().isBlank()) {
            throw new IllegalArgumentException(resolution.disabledReason());
        }
        if (submittedSelection.localMemberId() != null
            && resolution.selection().localMemberId() != null
            && !submittedSelection.localMemberId().equals(resolution.selection().localMemberId())) {
            throw new IllegalArgumentException("Selected guarantor profile no longer matches the LMS member.");
        }
        if (resolution.selection().localMemberId() != null) {
            validateLocalGuarantor(
                saccoId,
                stationId,
                applicantId,
                pendingLoanAmount,
                product,
                resolution.selection().localMemberId()
            );
        }
        return resolution.selection();
    }

    private ForesightDirectoryService.MemberProfileLookupResult lookupDirectOtpProfile(String mode, String lookupValue) {
        return DIRECT_OTP_SEARCH_EMAIL.equals(mode)
            ? foresightDirectoryService.lookupMemberProfileByEmail(lookupValue)
            : foresightDirectoryService.lookupMemberProfileByPhone(lookupValue);
    }

    private ForesightMemberProfile requireProfileIdentity(ForesightMemberProfile profile) {
        if (profile == null || normalizeOptional(profile.memberNo()) == null || normalizeOptional(profile.stationId()) == null) {
            throw new IllegalArgumentException("Foresight member profile is missing member number or station.");
        }
        return profile;
    }

    private GuarantorSelection selectionFromProfile(ForesightMemberProfile profile,
                                                    String lookupBy,
                                                    String lookupValue,
                                                    DirectOtpFinancialSnapshot snapshot) {
        return new GuarantorSelection(
            GUARANTOR_SOURCE_FORESIGHT,
            null,
            normalizeOptional(profile.memberNo()),
            normalizeOptional(profile.stationId()),
            profileFullName(profile),
            normalizeOptional(profile.email()),
            normalizeOptional(profile.phoneNumber()),
            lookupBy,
            lookupValue,
            snapshot
        );
    }

    private CandidateResolution resolveDirectOtpCandidate(String saccoId,
                                                          String stationId,
                                                          UUID applicantId,
                                                          GuarantorSelection selection) {
        if (selection.externalMemberNo() == null || selection.externalStationId() == null) {
            return new CandidateResolution(selection, "Foresight member profile is missing member number or station.");
        }
        if (!sameStation(selection.externalStationId(), stationId)) {
            return new CandidateResolution(selection, "Guarantor must be in the same station");
        }
        Optional<Member> localMember = memberRepository.findByMemberNoIgnoreCase(selection.externalMemberNo())
            .filter(member -> saccoId.equals(member.getSaccoId()));
        if (localMember.isEmpty()) {
            return new CandidateResolution(selection, "");
        }
        Member member = localMember.get();
        if (member.getId().equals(applicantId)) {
            return new CandidateResolution(selection, "You cannot select yourself as a guarantor");
        }
        if (member.getStatus() != MemberStatus.ACTIVE || !member.isMemberAccess()) {
            return new CandidateResolution(selection, "Guarantor must be active and in same SACCO");
        }
        if (!matchesStation(member, stationId)) {
            return new CandidateResolution(selection, "Guarantor must be in the same station");
        }
        return new CandidateResolution(selection.asLocal(member), "");
    }

    private DirectOtpFinancialSnapshot loadDirectOtpFinancialSnapshot(ForesightMemberProfile profile,
                                                                     boolean defaultedRiskCheckRequired,
                                                                     int portfolioAtRiskDays) {
        ForesightAccountSummary summary = foresightDirectoryService.fetchAccountSummary(profile.memberNo(), profile.stationId());
        List<ForesightInvestment> savingsInvestments = foresightDirectoryService.fetchInvestments(
            profile.memberNo(), profile.stationId(), INVESTMENT_CODE_SAVINGS);
        List<ForesightInvestment> sharesInvestments = foresightDirectoryService.fetchInvestments(
            profile.memberNo(), profile.stationId(), INVESTMENT_CODE_SHARES);
        List<ForesightInvestment> depositsInvestments = foresightDirectoryService.fetchInvestments(
            profile.memberNo(), profile.stationId(), INVESTMENT_CODE_DEPOSITS);
        List<ForesightActiveLoan> activeLoans = foresightDirectoryService.fetchActiveLoans(profile.memberNo(), profile.stationId());
        List<ForesightActiveLoan> paidLoans = foresightDirectoryService.fetchPaidLoans(profile.memberNo(), profile.stationId());
        List<ForesightActiveLoan> normalizedActiveLoans = activeLoans == null ? List.of() : activeLoans;
        int defaultedRiskLoanCount = defaultedRiskCheckRequired
            ? foresightDefaultedRiskLoanCount(profile, normalizedActiveLoans, portfolioAtRiskDays)
            : 0;
        return new DirectOtpFinancialSnapshot(
            nullToZero(summary == null ? null : summary.savingsBalance()),
            nullToZero(summary == null ? null : summary.sharesBalance()),
            nullToZero(summary == null ? null : summary.depositsBalance()),
            normalizedActiveLoans,
            paidLoans == null ? List.of() : paidLoans,
            savingsInvestments == null ? 0 : savingsInvestments.size(),
            sharesInvestments == null ? 0 : sharesInvestments.size(),
            depositsInvestments == null ? 0 : depositsInvestments.size(),
            defaultedRiskLoanCount
        );
    }

    private int foresightDefaultedRiskLoanCount(ForesightMemberProfile profile,
                                                List<ForesightActiveLoan> activeLoans,
                                                int portfolioAtRiskDays) {
        if (profile == null || activeLoans == null || activeLoans.isEmpty()) {
            return 0;
        }
        LocalDate today = applicationClock.today();
        int days = Math.max(1, Math.min(365, portfolioAtRiskDays));
        int riskCount = 0;
        for (ForesightActiveLoan loan : activeLoans) {
            String loanId = loan == null ? null : normalizeOptional(loan.loanIdText());
            if (loanId == null) {
                continue;
            }
            List<ForesightLoanPaymentSummary> summaries = foresightDirectoryService.fetchLoanPaymentSummary(
                profile.memberNo(),
                profile.stationId(),
                loanId
            );
            if (selectUsablePaymentSummary(summaries, loanId)
                .filter(summary -> isForesightParEquivalentRisk(summary, days, today))
                .isPresent()) {
                riskCount++;
            }
        }
        return riskCount;
    }

    private Optional<ForesightLoanPaymentSummary> selectUsablePaymentSummary(List<ForesightLoanPaymentSummary> summaries,
                                                                            String loanId) {
        if (summaries == null || summaries.isEmpty()) {
            return Optional.empty();
        }
        List<ForesightLoanPaymentSummary> usable = summaries.stream()
            .filter(Objects::nonNull)
            .filter(summary -> summary.totalOutstanding() != null)
            .toList();
        if (usable.isEmpty()) {
            return Optional.empty();
        }
        return usable.stream()
            .filter(summary -> summary.loanIdText().equalsIgnoreCase(loanId))
            .findFirst()
            .or(() -> usable.size() == 1 ? Optional.of(usable.getFirst()) : Optional.empty());
    }

    private boolean isForesightParEquivalentRisk(ForesightLoanPaymentSummary summary,
                                                 int portfolioAtRiskDays,
                                                 LocalDate today) {
        if (summary == null || nullToZero(summary.totalOutstanding()).compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        LocalDate lastPaymentDate = summary.lastPaymentDate();
        return lastPaymentDate == null || lastPaymentDate.plusDays(portfolioAtRiskDays).isBefore(today);
    }

    private DirectOtpLoanRow loanRow(ForesightActiveLoan loan) {
        return new DirectOtpLoanRow(
            loan.loanIdText(),
            firstNonBlank(loan.loanDescription(), "-"),
            loan.disbursedDate() == null ? "-" : loan.disbursedDate().toString(),
            loan.requestedAmount(),
            loan.disbursedAmount(),
            loan.totalInterest()
        );
    }

    private DirectOtpPaymentSummaryRow paymentSummaryRow(ForesightLoanPaymentSummary summary) {
        return new DirectOtpPaymentSummaryRow(
            summary.loanIdText(),
            firstNonBlank(summary.loanDescription(), "-"),
            summary.requestedAmount(),
            summary.disbursedAmount(),
            summary.totalPrincipalPaid(),
            summary.totalInterestPaid(),
            summary.outstandingPrincipal(),
            summary.outstandingInterest(),
            summary.totalOutstanding(),
            summary.lastPaymentDate() == null ? "-" : summary.lastPaymentDate().toString()
        );
    }

    private DirectOtpPaymentTransactionRow paymentTransactionRow(ForesightLoanPaymentTransaction transaction) {
        return new DirectOtpPaymentTransactionRow(
            transaction.receiptDate() == null ? "-" : transaction.receiptDate().toString(),
            transaction.principalPaid(),
            transaction.interestPaid(),
            transaction.totalPaid()
        );
    }

    @Transactional
    public void selectGuarantors(UUID appId, UUID applicantId, List<UUID> guarantorIds) {
        LoanApplication app = getMine(appId, applicantId);
        if (app.getRequiredGuarantors() == 0) {
            return;
        }
        LoanProductSetting product = resolveWorkflowProduct(app);
        List<UUID> uniqueGuarantors = validateGuarantorSelection(
            app.getSaccoId(),
            coalesceStationId(app.getStationId(), requireMemberStationId(applicantId)),
            applicantId,
            app.getRequiredGuarantors(),
            app.getAmount(),
            guarantorIds,
            product
        );

        app.setSelectedGuarantors(toJson(uniqueGuarantors));
        app.setUpdatedAt(OffsetDateTime.now());

        if (app.getStatus() == LoanStatus.DRAFT) {
            loanApplicationRepository.save(app);
            return;
        }
        throw new IllegalStateException("Guarantor selection is locked after the application is submitted.");
    }

    public List<GuarantorRequest> myActiveGuarantorRequests(UUID memberId) {
        return guarantorRequestRepository.findActiveVisibleByGuarantorMemberId(
            memberId,
            OffsetDateTime.now().minusHours(REVERSAL_WINDOW_HOURS)
        );
    }

    public List<GuarantorRequest> myActiveGuaranteedLoans(UUID memberId) {
        return guarantorRequestRepository.findActiveGuaranteedLoansByGuarantorMemberId(memberId);
    }

    @Transactional
    public void approveGuarantorRequest(UUID requestId, UUID guarantorId) {
        approveGuarantorRequestInternal(requestId, guarantorId, null, null);
    }

    @Transactional
    public void approveGuarantorRequest(UUID requestId,
                                        UUID guarantorId,
                                        String signatureText,
                                        OffsetDateTime verifiedAt) {
        approveGuarantorRequestInternal(requestId, guarantorId, signatureText, verifiedAt);
    }

    private void approveGuarantorRequestInternal(UUID requestId,
                                                 UUID guarantorId,
                                                 String signatureText,
                                                 OffsetDateTime verifiedAt) {
        GuarantorRequest request = guarantorRequestRepository.findByIdAndGuarantorMemberId(requestId, guarantorId)
            .orElseThrow(() -> new IllegalArgumentException("Guarantor request not found"));
        if (request.getStatus() != GuarantorRequestStatus.PENDING) {
            throw new IllegalStateException("Request already decided");
        }
        request.setStatus(GuarantorRequestStatus.APPROVED);
        request.setCommittedAmount(null);
        request.setGuarantorSignatureText(signatureText == null ? null : signatureText.trim());
        request.setGuarantorSignatureVerifiedAt(verifiedAt);
        request.setDecidedAt(OffsetDateTime.now());
        guarantorRequestRepository.save(request);
        evaluateReadiness(request.getLoanApplicationId());
        loanApplicationRepository.findById(request.getLoanApplicationId())
            .ifPresent(app -> {
                outboxService.enqueue(
                    "GUARANTOR_REQUEST",
                    request.getId(),
                    "GUARANTOR_REQUEST_APPROVED",
                    app.getApplicantMemberId(),
                    guarantorId,
                    app.getSaccoId(),
                    app.getStationId(),
                    Map.of(
                        "loanId", app.getId().toString(),
                        "guarantorRequestId", request.getId().toString(),
                        "guarantorId", guarantorId.toString()
                    )
                );
                auditLoan(app, guarantorId, "GUARANTOR_REQUEST_APPROVED", "Guarantor request approved");
            });
    }

    @Transactional
    public void approveDirectOtpGuarantorRequest(UUID requestId,
                                                 UUID applicantId,
                                                 String signatureText,
                                                 OffsetDateTime verifiedAt) {
        GuarantorRequest request = guarantorRequestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Guarantor request not found"));
        LoanApplication app = loanApplicationRepository.findById(request.getLoanApplicationId())
            .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
        if (!Objects.equals(app.getApplicantMemberId(), applicantId)) {
            throw new IllegalArgumentException("Guarantor request not found");
        }
        if (!isDirectOtpGuarantorApproval(app)) {
            throw new IllegalStateException("Direct OTP approval is not enabled for this application.");
        }
        if (request.getStatus() != GuarantorRequestStatus.PENDING) {
            throw new IllegalStateException("Request already decided");
        }
        request.setStatus(GuarantorRequestStatus.APPROVED);
        request.setCommittedAmount(null);
        request.setGuarantorSignatureText(signatureText == null ? null : signatureText.trim());
        request.setGuarantorSignatureVerifiedAt(verifiedAt);
        request.setDecidedAt(OffsetDateTime.now());
        if (request.getGuarantorMemberId() == null) {
            ExternalGuarantorRegistry registry = upsertExternalGuarantorRegistry(app, request);
            request.setExternalGuarantorRegistryId(registry.getId());
        }
        guarantorRequestRepository.save(request);
        evaluateReadiness(request.getLoanApplicationId());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("loanId", app.getId().toString());
        details.put("guarantorRequestId", request.getId().toString());
        if (request.getGuarantorMemberId() != null) {
            details.put("guarantorId", request.getGuarantorMemberId().toString());
        }
        if (request.getExternalMemberNo() != null) {
            details.put("externalMemberNo", request.getExternalMemberNo());
        }
        if (request.getExternalStationId() != null) {
            details.put("externalStationId", request.getExternalStationId());
        }
        if (request.getExternalGuarantorRegistryId() != null) {
            details.put("externalGuarantorRegistryId", request.getExternalGuarantorRegistryId().toString());
        }
        outboxService.enqueue(
            "GUARANTOR_REQUEST",
            request.getId(),
            "GUARANTOR_REQUEST_APPROVED",
            app.getApplicantMemberId(),
            request.getGuarantorMemberId() == null ? applicantId : request.getGuarantorMemberId(),
            app.getSaccoId(),
            app.getStationId(),
            details
        );
        auditLoan(
            app,
            request.getGuarantorMemberId() == null ? applicantId : request.getGuarantorMemberId(),
            "GUARANTOR_REQUEST_APPROVED",
            "Guarantor request approved"
        );
    }

    private ExternalGuarantorRegistry upsertExternalGuarantorRegistry(LoanApplication app, GuarantorRequest request) {
        String externalMemberNo = normalizeOptional(request.getExternalMemberNo());
        String externalStationId = normalizeStationId(request.getExternalStationId());
        if (externalMemberNo == null || externalStationId == null) {
            throw new IllegalStateException("Foresight guarantor profile is missing member number or station.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        ExternalGuarantorRegistry registry = externalGuarantorRegistryRepository
            .findBySaccoIdAndExternalStationIdIgnoreCaseAndExternalMemberNoIgnoreCase(
                app.getSaccoId(),
                externalStationId,
                externalMemberNo
            )
            .orElseGet(() -> ExternalGuarantorRegistry.builder()
                .id(UUID.randomUUID())
                .saccoId(app.getSaccoId())
                .externalStationId(externalStationId)
                .externalMemberNo(externalMemberNo)
                .createdAt(now)
                .version(0)
                .build());
        registry.setStationId(app.getStationId());
        registry.setFullName(normalizeOptional(request.getExternalFullName()));
        registry.setEmail(normalizeOptional(request.getExternalEmail()));
        registry.setPhone(normalizeOptional(request.getExternalPhone()));
        registry.setLatestFinancialSnapshot(normalizeJson(
            request.getExternalFinancialSnapshot(),
            "Failed to save Foresight guarantor financial snapshot"
        ));
        registry.setLastApprovedAt(now);
        registry.setUpdatedAt(now);
        return externalGuarantorRegistryRepository.save(registry);
    }

    @Transactional
    public void rejectGuarantorRequest(UUID requestId, UUID guarantorId, String reason) {
        GuarantorRequest request = guarantorRequestRepository.findByIdAndGuarantorMemberId(requestId, guarantorId)
            .orElseThrow(() -> new IllegalArgumentException("Guarantor request not found"));
        if (request.getStatus() != GuarantorRequestStatus.PENDING) {
            throw new IllegalStateException("Request already decided");
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isBlank()) {
            throw new IllegalStateException("Enter a reason before rejecting this guarantee request.");
        }
        request.setStatus(GuarantorRequestStatus.REJECTED);
        request.setDecisionReason(normalizedReason);
        request.setGuarantorSignatureText(null);
        request.setGuarantorSignatureVerifiedAt(null);
        request.setDecidedAt(OffsetDateTime.now());
        guarantorRequestRepository.save(request);
        evaluateReadiness(request.getLoanApplicationId());
        loanApplicationRepository.findById(request.getLoanApplicationId())
            .ifPresent(app -> {
                outboxService.enqueue(
                    "GUARANTOR_REQUEST",
                    request.getId(),
                    "GUARANTOR_REQUEST_REJECTED",
                    app.getApplicantMemberId(),
                    guarantorId,
                    app.getSaccoId(),
                    app.getStationId(),
                    Map.of(
                        "loanId", app.getId().toString(),
                        "guarantorRequestId", request.getId().toString(),
                        "guarantorId", guarantorId.toString(),
                        "reasons", normalizedReason
                    )
                );
                auditLoan(app, guarantorId, "GUARANTOR_REQUEST_REJECTED", "Guarantor request rejected");
            });
    }

    @Transactional
    public void removeGuarantorFromLoan(UUID requestId, UUID guarantorId) {
        GuarantorRequest request = guarantorRequestRepository.findByIdAndGuarantorMemberId(requestId, guarantorId)
            .orElseThrow(() -> new IllegalArgumentException("Guarantor request not found"));
        if (request.getStatus() == GuarantorRequestStatus.PENDING) {
            throw new IllegalStateException("Only approved or rejected guarantor decisions can be removed.");
        }

        LoanApplication app = loanApplicationRepository.findById(request.getLoanApplicationId())
            .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
        if (app.getStatus() != LoanStatus.AWAITING_GUARANTORS
            && app.getStatus() != LoanStatus.ALL_GUARANTORS_APPROVED) {
            throw new IllegalStateException("This guarantor can no longer be removed from the application.");
        }

        List<UUID> selectedGuarantors = new ArrayList<>(parseSelectedGuarantors(app.getSelectedGuarantors()));
        selectedGuarantors.removeIf(guarantorId::equals);
        app.setSelectedGuarantors(toJson(selectedGuarantors));
        app.setUpdatedAt(OffsetDateTime.now());
        loanApplicationRepository.save(app);

        guarantorRequestRepository.delete(request);
        evaluateReadiness(request.getLoanApplicationId());
    }

    @Transactional
    public void cancelSubmission(UUID appId, UUID applicantId) {
        LoanApplication app = getMine(appId, applicantId);
        if (app.getStatus() != LoanStatus.AWAITING_GUARANTORS
            && app.getStatus() != LoanStatus.ALL_GUARANTORS_APPROVED) {
            throw new IllegalStateException("This application cannot be canceled at its current stage");
        }
        if (!isWithinReversalWindow(app.getSubmittedAt())) {
            throw new IllegalStateException("The 24-hour reversal window for this application has already closed.");
        }

        List<GuarantorRequest> requests = guarantorRequestRepository.findByLoanApplicationId(appId);
        for (GuarantorRequest request : requests) {
            request.setStatus(GuarantorRequestStatus.EXPIRED);
            request.setDecisionReason("Applicant canceled submission");
            request.setDecidedAt(OffsetDateTime.now());
        }
        guarantorRequestRepository.saveAll(requests);

        app.setStatus(LoanStatus.DRAFT);
        app.setSubmittedAt(null);
        app.setApplicantSignatureText(null);
        app.setApplicantSignatureVerifiedAt(null);
        app.setUpdatedAt(OffsetDateTime.now());
        loanApplicationRepository.save(app);
        auditLoan(app, applicantId, "LOAN_SUBMISSION_CANCELLED", "Loan submission moved back to draft");
    }

    @Transactional
    public void deleteApplication(UUID appId, UUID applicantId) {
        LoanApplication app = getMine(appId, applicantId);
        if (app.getStatus() != LoanStatus.DRAFT
            && app.getStatus() != LoanStatus.AWAITING_GUARANTORS) {
            throw new IllegalStateException("This application can no longer be deleted.");
        }

        deleteApplicationRecords(app);
        auditLoan(app, applicantId, "LOAN_APPLICATION_DELETED", "Loan application removed");
    }

    @Transactional
    public void removeApplicationAtManagerStage(UUID appId, UUID applicantId) {
        LoanApplication app = getMine(appId, applicantId);
        if (app.getStatus() != LoanStatus.READY_FOR_MANAGER) {
            throw new IllegalStateException("Only applications still on review by manager can be removed.");
        }
        deleteApplicationRecords(app);
    }

    @Transactional
    public void evaluateReadiness(UUID loanApplicationId) {
        LoanApplication app = loanApplicationRepository.findById(loanApplicationId)
            .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
        if (app.getStatus() != LoanStatus.AWAITING_GUARANTORS
            && app.getStatus() != LoanStatus.SUBMITTED
            && app.getStatus() != LoanStatus.ALL_GUARANTORS_APPROVED
            && app.getStatus() != LoanStatus.READY_FOR_MANAGER) {
            return;
        }

        long approvals = guarantorRequestRepository.countByLoanApplicationIdAndStatus(loanApplicationId,
            GuarantorRequestStatus.APPROVED);
        LoanProductSetting product = resolveWorkflowProduct(app);
        EligibilityService.EligibilityResult eligibility = app.getLoanProductSettingId() == null
            ? eligibilityService.check(app.getSaccoId(), app.getApplicantMemberId(), app.getLoanType(), app.getAmount())
            : eligibilityService.check(app.getSaccoId(), app.getApplicantMemberId(), product, app.getAmount());

        long approvalsNeeded = app.getRequiredGuarantors() == null ? 0 : Math.max(app.getRequiredGuarantors(), 0);
        boolean allGuarantorsApproved = approvals >= approvalsNeeded && eligibility.eligible();
        if (allGuarantorsApproved) {
            if (app.getStatus() == LoanStatus.ALL_GUARANTORS_APPROVED || app.getStatus() == LoanStatus.READY_FOR_MANAGER) {
                return;
            }
            app.setStatus(LoanStatus.ALL_GUARANTORS_APPROVED);
            app.setUpdatedAt(OffsetDateTime.now());
            loanApplicationRepository.save(app);
            outboxService.enqueue("LOAN", app.getId(), "LOAN_GUARANTORS_APPROVED", app.getApplicantMemberId(),
                app.getSaccoId(), app.getStationId(),
                Map.of("loanId", app.getId().toString()));
            return;
        }

        if (approvalsNeeded > 0 && app.getStatus() == LoanStatus.ALL_GUARANTORS_APPROVED) {
            app.setStatus(LoanStatus.AWAITING_GUARANTORS);
            app.setUpdatedAt(OffsetDateTime.now());
            loanApplicationRepository.save(app);
        }
    }

    private String toJson(List<UUID> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to save selected guarantors", e);
        }
    }

    private String normalizeGuarantorApprovalMode(String value) {
        if (GUARANTOR_APPROVAL_MODE_DIRECT_OTP.equalsIgnoreCase(String.valueOf(value).trim())) {
            return GUARANTOR_APPROVAL_MODE_DIRECT_OTP;
        }
        return GUARANTOR_APPROVAL_MODE_LOGIN;
    }

    private boolean isDirectOtpGuarantorApproval(LoanApplication app) {
        return GUARANTOR_APPROVAL_MODE_DIRECT_OTP.equals(guarantorApprovalMode(app));
    }

    private String guarantorApprovalMode(LoanApplication app) {
        if (app == null || app.getFormData() == null || app.getFormData().isBlank()) {
            return GUARANTOR_APPROVAL_MODE_LOGIN;
        }
        try {
            Map<String, Object> formData = objectMapper.readValue(app.getFormData(), new TypeReference<Map<String, Object>>() {});
            return normalizeGuarantorApprovalMode(String.valueOf(formData.get(GUARANTOR_APPROVAL_MODE_FIELD)));
        } catch (Exception ex) {
            return GUARANTOR_APPROVAL_MODE_LOGIN;
        }
    }

    private String toGuarantorSelectionJson(List<GuarantorSelection> selections) {
        List<GuarantorSelection> uniqueSelections = selections == null ? Collections.emptyList() : selections;
        if (uniqueSelections.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(uniqueSelections.stream()
                .map(this::selectionPayload)
                .toList());
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to save selected guarantors", e);
        }
    }

    private Map<String, String> selectionPayload(GuarantorSelection selection) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("source", selection.source());
        if (selection.localMemberId() != null) {
            row.put("id", selection.localMemberId().toString());
        }
        putIfPresent(row, "memberNo", selection.externalMemberNo());
        putIfPresent(row, "stationId", selection.externalStationId());
        putIfPresent(row, "fullName", selection.fullName());
        putIfPresent(row, "email", selection.email());
        putIfPresent(row, "phone", selection.phone());
        putIfPresent(row, "lookupBy", selection.lookupBy());
        putIfPresent(row, "lookupValue", selection.lookupValue());
        return row;
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private void deleteApplicationRecords(LoanApplication app) {
        UUID appId = app.getId();
        guarantorRequestRepository.deleteByLoanApplicationId(appId);
        boardReviewRepository.deleteByLoanApplicationId(appId);
        managerReviewRepository.deleteByLoanApplicationId(appId);
        loanApplicationRepository.delete(app);
        loanAttachmentService.deleteAll(appId);
    }

    private boolean isWithinReversalWindow(OffsetDateTime referenceAt) {
        return referenceAt != null && referenceAt.plusHours(REVERSAL_WINDOW_HOURS).isAfter(OffsetDateTime.now());
    }

    private List<UUID> parseSelectedGuarantors(String json) {
        return parseSelectedGuarantorSelections(json).stream()
            .map(GuarantorSelection::localMemberId)
            .filter(Objects::nonNull)
            .toList();
    }

    private List<GuarantorSelection> parseSelectedGuarantorSelections(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            return parseSubmittedGuarantorSelections(raw);
        } catch (Exception ignored) {
            try {
                List<String> raw = objectMapper.readValue(json, new TypeReference<List<String>>() {});
                return parseSubmittedGuarantorSelections(raw);
            } catch (Exception ex) {
                return Collections.emptyList();
            }
        }
    }

    private List<GuarantorSelection> parseSubmittedGuarantorSelections(List<?> rawSelections) {
        if (rawSelections == null || rawSelections.isEmpty()) {
            return Collections.emptyList();
        }
        List<GuarantorSelection> selections = new ArrayList<>();
        for (Object rawSelection : rawSelections) {
            parseSubmittedGuarantorSelection(rawSelection).ifPresent(selections::add);
        }
        return selections;
    }

    private List<?> preserveSavedGuarantorSelections(LoanApplication existingDraft, List<?> rawSelections) {
        if (hasSubmittedGuarantorSelectionPayload(rawSelections)) {
            return rawSelections;
        }
        if (existingDraft == null) {
            return rawSelections;
        }
        List<GuarantorSelection> savedSelections = parseSelectedGuarantorSelections(existingDraft.getSelectedGuarantors());
        return savedSelections.isEmpty() ? rawSelections : savedSelections;
    }

    private boolean hasSubmittedGuarantorSelectionPayload(List<?> rawSelections) {
        if (rawSelections == null || rawSelections.isEmpty()) {
            return false;
        }
        for (Object rawSelection : rawSelections) {
            if (rawSelection == null) {
                continue;
            }
            if (rawSelection instanceof Map<?, ?> map && map.isEmpty()) {
                continue;
            }
            if (rawSelection instanceof String text && normalizeOptional(text) == null) {
                continue;
            }
            return true;
        }
        return false;
    }

    private Optional<GuarantorSelection> parseSubmittedGuarantorSelection(Object rawSelection) {
        if (rawSelection == null) {
            return Optional.empty();
        }
        if (rawSelection instanceof UUID id) {
            return Optional.of(localSelection(id));
        }
        if (rawSelection instanceof GuarantorSelection selection) {
            return Optional.of(selection);
        }
        if (rawSelection instanceof Map<?, ?> map) {
            return Optional.ofNullable(selectionFromMap(map));
        }
        String text = normalizeOptional(rawString(rawSelection));
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        UUID localId = parseUuid(text);
        if (localId != null) {
            return Optional.of(localSelection(localId));
        }
        try {
            Map<String, Object> map = objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
            return Optional.ofNullable(selectionFromMap(map));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private GuarantorSelection selectionFromMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        UUID localId = parseUuid(rawString(map.get("id")));
        String source = GUARANTOR_SOURCE_FORESIGHT.equalsIgnoreCase(rawString(map.get("source")))
            ? GUARANTOR_SOURCE_FORESIGHT
            : GUARANTOR_SOURCE_LMS;
        String lookupBy = normalizeDirectOtpSearchBy(rawString(map.get("lookupBy")));
        String lookupValue = normalizeOptional(rawString(map.get("lookupValue")));
        if (localId != null) {
            return new GuarantorSelection(
                GUARANTOR_SOURCE_LMS,
                localId,
                normalizeOptional(rawString(map.get("memberNo"))),
                normalizeOptional(rawString(map.get("stationId"))),
                normalizeOptional(rawString(map.get("fullName"))),
                normalizeOptional(rawString(map.get("email"))),
                normalizeOptional(rawString(map.get("phone"))),
                lookupBy,
                lookupValue,
                null
            );
        }
        String memberNo = normalizeOptional(rawString(map.get("memberNo")));
        String stationId = normalizeOptional(rawString(map.get("stationId")));
        if (!GUARANTOR_SOURCE_FORESIGHT.equals(source) && memberNo == null) {
            return null;
        }
        return new GuarantorSelection(
            GUARANTOR_SOURCE_FORESIGHT,
            null,
            memberNo,
            stationId,
            normalizeOptional(rawString(map.get("fullName"))),
            normalizeOptional(rawString(map.get("email"))),
            normalizeOptional(rawString(map.get("phone"))),
            lookupBy,
            lookupValue,
            null
        );
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String rawString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode node) {
            if (node.isNull()) {
                return null;
            }
            return node.isValueNode() ? node.asString() : node.toString();
        }
        return String.valueOf(value);
    }

    private GuarantorSelection localSelection(UUID localMemberId) {
        return new GuarantorSelection(GUARANTOR_SOURCE_LMS, localMemberId, null, null, null, null, null, null, null, null);
    }

    public List<Map<String, String>> selectedGuarantorDisplayItems(String selectedGuarantorsJson) {
        return selectedGuarantorDisplayItemsFromSelections(parseSelectedGuarantorSelections(selectedGuarantorsJson));
    }

    public List<Map<String, String>> selectedGuarantorDisplayItems(List<?> rawSelections) {
        return selectedGuarantorDisplayItemsFromSelections(parseSubmittedGuarantorSelections(rawSelections));
    }

    private List<Map<String, String>> selectedGuarantorDisplayItemsFromSelections(List<GuarantorSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            return Collections.emptyList();
        }
        Set<UUID> localIds = new LinkedHashSet<>();
        for (GuarantorSelection selection : selections) {
            if (selection.localMemberId() != null) {
                localIds.add(selection.localMemberId());
            }
        }
        Map<UUID, Member> membersById = new LinkedHashMap<>();
        if (!localIds.isEmpty()) {
            for (Member member : memberRepository.findAllById(localIds)) {
                membersById.put(member.getId(), member);
            }
        }
        List<Map<String, String>> items = new ArrayList<>();
        for (GuarantorSelection selection : selections) {
            Member member = selection.localMemberId() == null ? null : membersById.get(selection.localMemberId());
            String memberNo = firstNonBlank(member == null ? null : member.getMemberNo(), selection.externalMemberNo(), "-");
            String fullName = firstNonBlank(member == null ? null : member.getFullName(), selection.fullName(), "Guarantor");
            Map<String, String> item = new LinkedHashMap<>();
            item.put("id", selection.identityKey());
            item.put("selectionKey", selection.identityKey());
            item.put("selectionToken", selectionToken(selection));
            item.put("memberNo", memberNo);
            item.put("fullName", fullName);
            item.put("source", selection.source());
            item.put("stationId", firstNonBlank(member == null ? null : member.getStationId(), selection.externalStationId(), ""));
            item.put("email", firstNonBlank(selection.email(), member == null ? null : member.getEmail(), ""));
            item.put("phone", firstNonBlank(selection.phone(), member == null ? null : member.getPhone(), ""));
            item.put("lookupBy", firstNonBlank(selection.lookupBy(), ""));
            item.put("lookupValue", firstNonBlank(selection.lookupValue(), ""));
            if (selection.localMemberId() != null) {
                item.put("localMemberId", selection.localMemberId().toString());
            }
            items.add(item);
        }
        return items;
    }

    private List<UUID> normalizeGuarantorSelection(LoanApplication app, UUID applicantId, List<UUID> guarantorIds) {
        LoanProductSetting product = resolveWorkflowProduct(app);
        return validateGuarantorSelections(
            app.getSaccoId(),
            coalesceStationId(app.getStationId(), requireMemberStationId(applicantId)),
            applicantId,
            app.getRequiredGuarantors(),
            app.getAmount(),
            guarantorIds,
            product,
            guarantorApprovalMode(app)
        ).stream()
            .map(GuarantorSelection::localMemberId)
            .filter(Objects::nonNull)
            .toList();
    }

    private List<GuarantorSelection> validateGuarantorSelections(String saccoId,
                                                                 String stationId,
                                                                 UUID applicantId,
                                                                 Integer requiredGuarantors,
                                                                 BigDecimal pendingLoanAmount,
                                                                 List<?> rawSelections,
                                                                 LoanProductSetting product,
                                                                 String approvalMode) {
        if (requiredGuarantors == null || requiredGuarantors <= 0) {
            return Collections.emptyList();
        }
        List<GuarantorSelection> submittedSelections = parseSubmittedGuarantorSelections(rawSelections);
        if (submittedSelections.size() != requiredGuarantors) {
            throw new IllegalArgumentException("Select exactly " + requiredGuarantors + " guarantors");
        }

        List<GuarantorSelection> normalizedSelections = new ArrayList<>();
        Set<String> uniqueKeys = new LinkedHashSet<>();
        boolean directOtp = GUARANTOR_APPROVAL_MODE_DIRECT_OTP.equals(approvalMode);
        for (GuarantorSelection submittedSelection : submittedSelections) {
            GuarantorSelection selection = directOtp
                ? normalizeDirectOtpSelection(saccoId, stationId, applicantId, pendingLoanAmount, product, submittedSelection)
                : submittedSelection;
            if (!directOtp && selection.localMemberId() == null) {
                throw new IllegalArgumentException("Login approval guarantors must be LMS members.");
            }
            if (selection.localMemberId() != null) {
                Member guarantor = validateLocalGuarantor(
                    saccoId,
                    stationId,
                    applicantId,
                    pendingLoanAmount,
                    product,
                    selection.localMemberId()
                );
                selection = selection.withMember(guarantor);
            } else {
                validateExternalGuarantor(saccoId, stationId, pendingLoanAmount, product, selection);
            }
            if (!uniqueKeys.add(selection.identityKey())) {
                throw new IllegalArgumentException("Select exactly " + requiredGuarantors + " different guarantors");
            }
            normalizedSelections.add(selection);
        }

        if (normalizedSelections.size() != requiredGuarantors) {
            throw new IllegalArgumentException("Select exactly " + requiredGuarantors + " guarantors");
        }
        return normalizedSelections;
    }

    private List<UUID> validateGuarantorSelection(String saccoId,
                                                  String stationId,
                                                  UUID applicantId,
                                                  Integer requiredGuarantors,
                                                  BigDecimal pendingLoanAmount,
                                                  List<UUID> guarantorIds,
                                                  LoanProductSetting product) {
        if (requiredGuarantors == null || requiredGuarantors <= 0) {
            return Collections.emptyList();
        }
        if (guarantorIds == null || guarantorIds.size() != requiredGuarantors) {
            throw new IllegalArgumentException("Select exactly " + requiredGuarantors + " guarantors");
        }

        List<UUID> uniqueGuarantors = new ArrayList<>(new LinkedHashSet<>(guarantorIds));
        if (uniqueGuarantors.size() != requiredGuarantors) {
            throw new IllegalArgumentException("Select exactly " + requiredGuarantors + " different guarantors");
        }

        for (UUID guarantorId : uniqueGuarantors) {
            validateLocalGuarantor(saccoId, stationId, applicantId, pendingLoanAmount, product, guarantorId);
        }

        return uniqueGuarantors;
    }

    private Member validateLocalGuarantor(String saccoId,
                                          String stationId,
                                          UUID applicantId,
                                          BigDecimal pendingLoanAmount,
                                          LoanProductSetting product,
                                          UUID guarantorId) {
        if (guarantorId == null) {
            throw new IllegalArgumentException("Guarantor not found");
        }
        if (guarantorId.equals(applicantId)) {
            throw new IllegalArgumentException("You cannot select yourself as a guarantor");
        }
        Member guarantor = null;
        Iterable<Member> matches = memberRepository.findAllById(List.of(guarantorId));
        if (matches != null) {
            for (Member match : matches) {
                if (match != null && guarantorId.equals(match.getId())) {
                    guarantor = match;
                    break;
                }
            }
        }
        if (guarantor == null) {
            guarantor = memberRepository.findById(guarantorId)
                .orElseThrow(() -> new IllegalArgumentException("Guarantor not found"));
        }
        if (!saccoId.equals(guarantor.getSaccoId()) || guarantor.getStatus() != MemberStatus.ACTIVE) {
            throw new IllegalArgumentException("Guarantor must be active and in same SACCO");
        }
        if (!guarantor.isMemberAccess()) {
            throw new IllegalArgumentException("Guarantor must have member access");
        }
        if (!matchesStation(guarantor, stationId)) {
            throw new IllegalArgumentException("Guarantor must be in the same station");
        }
        Optional<String> policyFailure = Optional.ofNullable(loanQualificationPolicyService.guarantorFailureReason(
            saccoId,
            guarantorId,
            pendingLoanAmount,
            product
        )).orElse(Optional.empty());
        if (policyFailure.isPresent()) {
            throw guarantorValidationException(guarantorId, guarantor, policyFailure.get());
        }
        return guarantor;
    }

    private void validateExternalGuarantor(String saccoId,
                                           String stationId,
                                           BigDecimal pendingLoanAmount,
                                           LoanProductSetting product,
                                           GuarantorSelection selection) {
        if (selection.externalMemberNo() == null || selection.externalStationId() == null) {
            throw new IllegalArgumentException("Foresight member profile is missing member number or station.");
        }
        if (!sameStation(selection.externalStationId(), stationId)) {
            throw new IllegalArgumentException("Guarantor must be in the same station");
        }
        DirectOtpFinancialSnapshot snapshot = selection.financialSnapshot();
        BigDecimal savings = snapshot == null ? BigDecimal.ZERO : snapshot.savingsBalance();
        int activeLoanCount = snapshot == null ? 0 : snapshot.activeLoanCount();
        int defaultedRiskLoanCount = snapshot == null ? 0 : snapshot.defaultedRiskLoanCount();
        Optional<String> policyFailure = Optional.ofNullable(loanQualificationPolicyService.guarantorFailureReasonForExternal(
            saccoId,
            stationId,
            selection.externalMemberNo(),
            selection.externalStationId(),
            savings,
            activeLoanCount,
            defaultedRiskLoanCount,
            pendingLoanAmount,
            product
        )).orElse(Optional.empty());
        if (policyFailure.isPresent()) {
            throw new GuarantorValidationException(null, selection.displayName() + ": " + policyFailure.get());
        }
    }

    private GuarantorValidationException guarantorValidationException(Member guarantor, RuntimeException ex) {
        UUID guarantorId = guarantor == null ? null : guarantor.getId();
        return guarantorValidationException(guarantorId, guarantor, ex.getMessage());
    }

    private GuarantorValidationException guarantorValidationException(UUID guarantorId, Member guarantor, String message) {
        String label = guarantor == null
            ? "This guarantor"
            : guarantor.getFullName() + " (" + guarantor.getMemberNo() + ")";
        String detail = message == null || message.isBlank() ? "This guarantor cannot be used for this loan." : message;
        return new GuarantorValidationException(guarantorId, label + ": " + detail);
    }

    public static class GuarantorValidationException extends IllegalArgumentException {
        private final UUID guarantorId;

        public GuarantorValidationException(UUID guarantorId, String message) {
            super(message);
            this.guarantorId = guarantorId;
        }

        public UUID getGuarantorId() {
            return guarantorId;
        }
    }

    private BigDecimal readBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).replace(",", "").trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveMemberStationId(UUID memberId) {
        if (memberId == null) {
            return null;
        }
        return memberRepository.findById(memberId)
            .map(Member::getStationId)
            .map(this::normalizeStationId)
            .orElse(null);
    }

    private String requireMemberStationId(UUID memberId) {
        String stationId = resolveMemberStationId(memberId);
        if (stationId == null) {
            throw new IllegalStateException("Applicant station is not configured.");
        }
        return stationId;
    }

    private boolean matchesStation(Member member, String stationId) {
        String normalizedStationId = normalizeStationId(stationId);
        if (normalizedStationId == null) {
            return true;
        }
        return member != null
            && member.getStationId() != null
            && normalizedStationId.equalsIgnoreCase(member.getStationId());
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeDirectOtpSearchBy(String searchBy) {
        return DIRECT_OTP_SEARCH_EMAIL.equalsIgnoreCase(String.valueOf(searchBy).trim())
            ? DIRECT_OTP_SEARCH_EMAIL
            : DIRECT_OTP_SEARCH_PHONE;
    }

    private String normalizeDirectOtpLookupValue(String value, String mode) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        if (DIRECT_OTP_SEARCH_EMAIL.equals(mode)) {
            String email = normalized.toLowerCase(Locale.ROOT);
            return email.contains("@") ? email : null;
        }
        String phone = TanzaniaPhoneNumber.normalizeOptional(normalized);
        return phone == null ? null : "+" + phone;
    }

    private String selectionToken(GuarantorSelection selection) {
        if (selection.localMemberId() != null
            && selection.lookupBy() == null
            && selection.lookupValue() == null
            && selection.externalMemberNo() == null
            && selection.email() == null
            && selection.phone() == null) {
            return selection.localMemberId().toString();
        }
        try {
            return objectMapper.writeValueAsString(selectionPayload(selection));
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Failed to save selected guarantor", ex);
        }
    }

    private String financialSnapshotJson(DirectOtpFinancialSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("savingsBalance", snapshot.savingsBalance());
        data.put("sharesBalance", snapshot.sharesBalance());
        data.put("depositsBalance", snapshot.depositsBalance());
        data.put("activeLoanCount", snapshot.activeLoanCount());
        data.put("paidLoanCount", snapshot.paidLoanCount());
        data.put("savingsInvestmentCount", snapshot.savingsInvestmentCount());
        data.put("sharesInvestmentCount", snapshot.sharesInvestmentCount());
        data.put("depositsInvestmentCount", snapshot.depositsInvestmentCount());
        data.put("defaultedRiskLoanCount", snapshot.defaultedRiskLoanCount());
        data.put("fetchedAt", OffsetDateTime.now().toString());
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Failed to save guarantor financial snapshot", ex);
        }
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean sameStation(String left, String right) {
        String normalizedLeft = normalizeStationId(left);
        String normalizedRight = normalizeStationId(right);
        return normalizedLeft == null || normalizedRight == null || normalizedLeft.equalsIgnoreCase(normalizedRight);
    }

    private String profileFullName(ForesightMemberProfile profile) {
        if (profile == null) {
            return "Guarantor";
        }
        String otherName = normalizeOptional(profile.otherName());
        String surname = normalizeOptional(profile.surname());
        String joined = List.of(otherName, surname).stream()
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.joining(" "));
        return firstNonBlank(joined, profile.memberNo(), "Guarantor");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String coalesceStationId(String primaryStationId, String fallbackStationId) {
        String normalizedPrimary = normalizeStationId(primaryStationId);
        return normalizedPrimary != null ? normalizedPrimary : normalizeStationId(fallbackStationId);
    }

    private String normalizeStationId(String stationId) {
        if (stationId == null) {
            return null;
        }
        String normalized = stationId.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private void refreshGuarantorRequestsForSubmission(LoanApplication app,
                                                       UUID applicantId,
                                                       List<GuarantorSelection> guarantors) {
        List<GuarantorSelection> uniqueGuarantors = guarantors == null ? List.of() : guarantors;
        guarantorRequestRepository.deleteByLoanApplicationId(app.getId());
        guarantorRequestRepository.flush();

        for (GuarantorSelection selection : uniqueGuarantors) {
            UUID guarantorId = selection.localMemberId();
            GuarantorRequest request = GuarantorRequest.builder()
                .id(UUID.randomUUID())
                .loanApplicationId(app.getId())
                .guarantorMemberId(guarantorId)
                .createdAt(OffsetDateTime.now())
                .build();

            request.setGuarantorSource(selection.source());
            request.setExternalMemberNo(selection.externalMemberNo());
            request.setExternalStationId(selection.externalStationId());
            request.setExternalFullName(selection.fullName());
            request.setExternalEmail(selection.email());
            request.setExternalPhone(selection.phone());
            request.setExternalFinancialSnapshot(financialSnapshotJson(selection.financialSnapshot()));
            request.setExternalGuarantorRegistryId(null);
            request.setStatus(GuarantorRequestStatus.PENDING);
            request.setRequestedAmount(null);
            request.setCommittedAmount(null);
            request.setDecisionReason(null);
            request.setGuarantorSignatureText(null);
            request.setGuarantorSignatureVerifiedAt(null);
            request.setDecidedAt(null);
            guarantorRequestRepository.save(request);

            if (guarantorId != null && !isDirectOtpGuarantorApproval(app)) {
                outboxService.enqueue("GUARANTOR_REQUEST", request.getId(), "GUARANTOR_REQUEST_ASSIGNED", guarantorId,
                    applicantId, app.getSaccoId(), app.getStationId(),
                    Map.of(
                        "loanId", app.getId().toString(),
                        "guarantorRequestId", request.getId().toString()
                    ));
            }
        }
    }

    private String normalizeJson(String rawJson, String messageOnFailure) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(rawJson));
        } catch (Exception ex) {
            throw new IllegalArgumentException(messageOnFailure, ex);
        }
    }

    private String financialSnapshotForDraft(Map<String, Object> generatedSnapshot, String submittedFinancialSnapshotJson) {
        if (generatedSnapshot == null) {
            return normalizeJson(submittedFinancialSnapshotJson, "Load SACCO financial details again before saving the draft.");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>(generatedSnapshot);
        snapshot.putAll(extractLiveFinancialValues(submittedFinancialSnapshotJson));
        return writeJson(snapshot, "Failed to prepare top-up financial details.");
    }

    private BigDecimal requireFinancialSnapshotAmount(Map<String, Object> snapshot, String key) {
        BigDecimal amount = snapshot == null ? null : readBigDecimal(snapshot.get(key));
        if (amount == null) {
            throw new IllegalStateException("Load SACCO financial details again before saving the draft.");
        }
        return amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal topUpRequestedAmount(LoanApplication app) {
        if (app == null || app.getTopUpSourceLoanId() == null) {
            return app == null ? null : app.getAmount();
        }
        Map<String, Object> snapshot = parseFinancialSnapshot(app.getFinancialSnapshot());
        BigDecimal topUpAmount = readBigDecimal(snapshot.get("topUpRequestedAmount"));
        if (topUpAmount == null) {
            topUpAmount = readBigDecimal(snapshot.get("requestedAmount"));
        }
        return topUpAmount == null ? app.getAmount() : topUpAmount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private Map<String, Object> parseFinancialSnapshot(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private void validateRequestedAmount(LoanProductSetting product, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Loan amount must be greater than zero.");
        }
        if (product.getMinimumAmount() != null && amount.compareTo(product.getMinimumAmount()) < 0) {
            throw new IllegalArgumentException(
                "Loan amount cannot be below " + product.getMinimumAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                    + " for this loan product"
            );
        }
        if (product.getMaximumAmount() != null && amount.compareTo(product.getMaximumAmount()) > 0) {
            throw new IllegalArgumentException(
                "Loan amount cannot exceed " + product.getMaximumAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                    + " for this loan product"
            );
        }
    }

    private EligibilityService.EligibilityResult checkApplicantSavingsEligibility(String saccoId,
                                                                                  UUID applicantId,
                                                                                  LoanType loanType,
                                                                                  BigDecimal amount) {
        EligibilityService.EligibilityResult result = eligibilityService.check(saccoId, applicantId, loanType, amount);
        return requireSavingsEligibility(result, amount);
    }

    private EligibilityService.EligibilityResult checkApplicantSavingsEligibility(String saccoId,
                                                                                  UUID applicantId,
                                                                                  LoanProductSetting product,
                                                                                  BigDecimal amount) {
        EligibilityService.EligibilityResult result = eligibilityService.check(saccoId, applicantId, product, amount);
        return requireSavingsEligibility(result, amount);
    }

    private EligibilityService.EligibilityResult checkApplicantSavingsEligibility(LoanApplication app,
                                                                                  LoanProductSetting product) {
        return app.getLoanProductSettingId() == null
            ? checkApplicantSavingsEligibility(app.getSaccoId(), app.getApplicantMemberId(), app.getLoanType(), app.getAmount())
            : checkApplicantSavingsEligibility(app.getSaccoId(), app.getApplicantMemberId(), product, app.getAmount());
    }

    private EligibilityService.EligibilityResult requireSavingsEligibility(EligibilityService.EligibilityResult result,
                                                                          BigDecimal amount) {
        if (!result.eligible()) {
            throw new IllegalStateException(
                "Loan amount exceeds the applicant savings limit for this product. Requested "
                    + formatAmount(amount)
                    + ", maximum allowed "
                    + formatAmount(result.maxAllowed())
                    + " based on savings "
                    + formatAmount(result.savings())
                    + " at "
                    + result.ratio().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                    + " x of savings."
            );
        }
        return result;
    }

    private String formatAmount(BigDecimal amount) {
        return amount == null ? "0.00" : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private void validateRepaymentPeriod(LoanProductSetting product, Integer tenorMonths) {
        if (tenorMonths == null || tenorMonths <= 0) {
            throw new IllegalArgumentException("Repayment period must be at least 1 month");
        }
        int minimumMonths = product.getMinimumRepaymentMonths();
        if (tenorMonths < minimumMonths) {
            throw new IllegalArgumentException("Repayment period cannot be below " + minimumMonths + " month(s) for this loan product");
        }
        if (product.getMaxRepaymentMonths() != null
            && product.getMaxRepaymentMonths() > 0
            && tenorMonths > product.getMaxRepaymentMonths()) {
            throw new IllegalArgumentException("Repayment period cannot exceed " + product.getMaxRepaymentMonths() + " month(s) for this loan product");
        }
    }

    private void requireLoadedFinancialDataForDraft(LoanProductSetting product, String financialSnapshotJson) {
        if (product == null || !product.isFreshFinancialDataRequired()) {
            return;
        }
        if (financialSnapshotJson == null || financialSnapshotJson.isBlank()) {
            throw new IllegalStateException("Load loan calculations before saving this draft. This loan product requires loaded financial data.");
        }
    }

    private void moveIntoConfiguredReviewStage(LoanApplication app, UUID actorMemberId) {
        assertApplicantCanAdvanceToManagerReview(app.getApplicantMemberId(), app.getId());
        workflowRoutingService.moveToFirstReviewStage(app, actorMemberId);
        app.setUpdatedAt(OffsetDateTime.now());
    }

    private LoanProductSetting resolveWorkflowProduct(LoanApplication app) {
        if (app == null || app.getSaccoId() == null) {
            return null;
        }
        if (app.getLoanProductSettingId() != null) {
            return loanProductSettingRepository.findByIdAndSaccoId(app.getLoanProductSettingId(), app.getSaccoId())
                .orElse(null);
        }
        if (app.getLoanType() == null) {
            return null;
        }
        return loanProductSettingRepository.findBySaccoIdAndLoanType(app.getSaccoId(), app.getLoanType())
            .orElse(null);
    }

    private void syncFinancialSnapshotToCurrentProduct(LoanApplication app) {
        if (app == null) {
            return;
        }
        BigDecimal requestedAmount = topUpRequestedAmount(app);
        Map<String, Object> generatedSnapshot = app.getLoanProductSettingId() == null
            ? financialDetailsService.generateSnapshot(
                app.getSaccoId(), app.getApplicantMemberId(), app.getLoanType(), requestedAmount,
                app.getTenorMonths(), app.getTopUpSourceLoanId())
            : financialDetailsService.generateSnapshot(
                app.getSaccoId(), app.getApplicantMemberId(), resolveWorkflowProduct(app), requestedAmount,
                app.getTenorMonths(), app.getTopUpSourceLoanId());
        if (app.getTopUpSourceLoanId() != null) {
            app.setAmount(requireFinancialSnapshotAmount(generatedSnapshot, "principalAmount"));
        }
        Map<String, Object> latestSnapshot = new LinkedHashMap<>(generatedSnapshot);
        latestSnapshot.putAll(extractLiveFinancialValues(app.getFinancialSnapshot()));
        app.setFinancialSnapshot(writeJson(latestSnapshot, "Failed to refresh financial snapshot."));
    }

    private void refreshFinancialSnapshotIfRequired(LoanApplication app, LoanProductSetting product) {
        if (app == null || product == null || !product.isFreshFinancialDataRequired()) {
            return;
        }
        Member member = memberRepository.findById(app.getApplicantMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Applicant account not found."));
        if (member.getMemberNo() == null || member.getMemberNo().isBlank()) {
            throw new IllegalStateException("Fresh financial data is required for this loan product, but the applicant member number is missing.");
        }
        String stationId = normalizeStationId(app.getStationId());
        if (stationId == null) {
            throw new IllegalStateException("Fresh financial data is required for this loan product, but the applicant station ID is missing.");
        }
        try {
            ForesightAccountSummary summary = foresightDirectoryService.fetchAccountSummary(member.getMemberNo(), stationId);
            app.setFinancialSnapshot(mergeFreshFinancialData(app.getFinancialSnapshot(), summary));
        } catch (UpstreamAvailabilityException ex) {
            throw new IllegalStateException(
                "Fresh financial data is required for this loan product, but the upstream financial service is unavailable right now. Try again later.",
                ex
            );
        } catch (IllegalStateException ex) {
            throw new IllegalStateException(
                "Fresh financial data is required for this loan product, but the live financial balances could not be refreshed right now.",
                ex
            );
        }
    }

    private String mergeFreshFinancialData(String rawJson, ForesightAccountSummary summary) {
        try {
            Map<String, Object> snapshot = rawJson == null || rawJson.isBlank()
                ? new LinkedHashMap<>()
                : objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {});
            snapshot.put("liveSavingsBalance", summary == null || summary.savingsBalance() == null
                ? BigDecimal.ZERO
                : summary.savingsBalance());
            snapshot.put("liveSharesBalance", summary == null || summary.sharesBalance() == null
                ? BigDecimal.ZERO
                : summary.sharesBalance());
            snapshot.put("liveDepositsBalance", summary == null || summary.depositsBalance() == null
                ? BigDecimal.ZERO
                : summary.depositsBalance());
            snapshot.put("liveFinancialDataFetchedAt", OffsetDateTime.now().toString());
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to refresh the stored financial snapshot.", ex);
        }
    }

    private Map<String, Object> extractLiveFinancialValues(String rawJson) {
        Map<String, Object> liveValues = new LinkedHashMap<>();
        if (rawJson == null || rawJson.isBlank()) {
            return liveValues;
        }
        try {
            Map<String, Object> snapshot = objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {});
            for (String key : List.of(
                "liveSavingsBalance",
                "liveSharesBalance",
                "liveDepositsBalance",
                "liveFinancialDataFetchedAt"
            )) {
                if (snapshot.containsKey(key)) {
                    liveValues.put(key, snapshot.get(key));
                }
            }
        } catch (Exception ignored) {
            return liveValues;
        }
        return liveValues;
    }

    private String writeJson(Map<String, Object> payload, String messageOnFailure) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException(messageOnFailure, ex);
        }
    }

    private String loanReference(LoanApplication app) {
        if (app.getApplicationNumber() != null) {
            return app.getApplicationNumber().toString();
        }
        return app.getId().toString().substring(0, 8);
    }

    public String activeLoanAwarenessMessage(List<LoanApplication> activeLoans) {
        List<String> loanIds = activeLoans.stream()
            .map(this::loanIdReference)
            .filter(reference -> reference != null && !reference.isBlank())
            .toList();
        if (loanIds.isEmpty()) {
            return "";
        }
        if (loanIds.size() == 1) {
            return "Be aware that you have an active loan with the Loan ID " + loanIds.get(0) + ".";
        }
        return "Be aware that you have active loans with the Loan IDs " + joinLoanIds(loanIds) + ".";
    }

    private String loanIdReference(LoanApplication app) {
        if (app.getLoanId() != null && !app.getLoanId().isBlank()) {
            return app.getLoanId();
        }
        return loanReference(app);
    }

    private String joinLoanIds(List<String> loanIds) {
        if (loanIds.size() <= 1) {
            return loanIds.isEmpty() ? "" : loanIds.get(0);
        }
        if (loanIds.size() == 2) {
            return loanIds.get(0) + " and " + loanIds.get(1);
        }
        return String.join(", ", loanIds.subList(0, loanIds.size() - 1))
            + " and "
            + loanIds.get(loanIds.size() - 1);
    }

    private void auditLoan(LoanApplication app, UUID actorId, String action, String description) {
        auditLoan(app, actorId, action, description, Map.of());
    }

    private void auditLoan(LoanApplication app,
                           UUID actorId,
                           String action,
                           String description,
                           Map<String, Object> extraDetails) {
        if (app == null) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("result", "SUCCESS");
        details.put("saccoId", app.getSaccoId());
        details.put("stationId", app.getStationId());
        details.put("applicationNumber", app.getApplicationNumber());
        details.put("loanId", app.getLoanId());
        details.put("workflowStatus", app.getStatus() == null ? null : app.getStatus().name());
        if (extraDetails != null) {
            details.putAll(extraDetails);
        }
        String referenceValue = app.getLoanId() != null && !app.getLoanId().isBlank() && "LOAN_DISBURSED".equals(action)
            ? "Loan ID " + app.getLoanId()
            : "Loan Application #" + app.getApplicationNumber();
        auditService.logEvent(
            "LOAN_APPLICATION",
            app.getId(),
            action,
            actorId,
            AuditEventStatus.SUCCESS,
            description,
            "LOAN_APPLICATION",
            referenceValue,
            app.getSaccoId(),
            app.getStationId(),
            details
        );
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> work.get());
    }

    public record MemberDashboardData(
        Map<LoanStatus, Long> statusCounts,
        List<LoanApplication> activeLoans,
        LoanApplication latestCurrentApplication,
        long unacknowledgedDisbursedApplicationCount,
        long unacknowledgedRejectedApplicationCount,
        long pendingGuaranteeCount
    ) {
    }

    public record MemberApplicationListData(
        List<LoanApplication> currentApplications,
        LoanApplication latestCurrentApplication,
        long archiveCount
    ) {
    }

    public record GuarantorCandidate(
        UUID id,
        String memberNo,
        String fullName,
        boolean eligible,
        String disabledReason
    ) {
    }

    public record DirectOtpGuarantorCandidate(
        String source,
        UUID localMemberId,
        String selectionKey,
        String selectionToken,
        String memberNo,
        String stationId,
        String saccoName,
        String fullName,
        String email,
        String phone,
        BigDecimal savingsBalance,
        BigDecimal sharesBalance,
        BigDecimal depositsBalance,
        int activeLoanCount,
        int paidLoanCount,
        List<DirectOtpLoanRow> activeLoans,
        List<DirectOtpLoanRow> paidLoans,
        boolean eligible,
        String disabledReason,
        String lookupBy,
        String lookupValue
    ) {
    }

    public record DirectOtpLoanRow(
        String loanId,
        String description,
        String disbursedDate,
        BigDecimal requestedAmount,
        BigDecimal disbursedAmount,
        BigDecimal totalInterest
    ) {
    }

    public record DirectOtpLoanDetails(
        List<DirectOtpPaymentSummaryRow> summaries,
        List<DirectOtpPaymentTransactionRow> transactions
    ) {
    }

    public record DirectOtpPaymentSummaryRow(
        String loanId,
        String description,
        BigDecimal requestedAmount,
        BigDecimal disbursedAmount,
        BigDecimal totalPrincipalPaid,
        BigDecimal totalInterestPaid,
        BigDecimal outstandingPrincipal,
        BigDecimal outstandingInterest,
        BigDecimal totalOutstanding,
        String lastPaymentDate
    ) {
    }

    public record DirectOtpPaymentTransactionRow(
        String receiptDate,
        BigDecimal principalPaid,
        BigDecimal interestPaid,
        BigDecimal totalPaid
    ) {
    }

    private record CandidateResolution(GuarantorSelection selection, String disabledReason) {
    }

    private record DirectOtpFinancialSnapshot(
        BigDecimal savingsBalance,
        BigDecimal sharesBalance,
        BigDecimal depositsBalance,
        List<ForesightActiveLoan> activeLoans,
        List<ForesightActiveLoan> paidLoans,
        int savingsInvestmentCount,
        int sharesInvestmentCount,
        int depositsInvestmentCount,
        int defaultedRiskLoanCount
    ) {
        private DirectOtpFinancialSnapshot {
            activeLoans = activeLoans == null ? List.of() : activeLoans;
            paidLoans = paidLoans == null ? List.of() : paidLoans;
        }

        int activeLoanCount() {
            return activeLoans.size();
        }

        int paidLoanCount() {
            return paidLoans.size();
        }
    }

    private record GuarantorSelection(
        String source,
        UUID localMemberId,
        String externalMemberNo,
        String externalStationId,
        String fullName,
        String email,
        String phone,
        String lookupBy,
        String lookupValue,
        DirectOtpFinancialSnapshot financialSnapshot
    ) {
        private GuarantorSelection {
            source = GUARANTOR_SOURCE_FORESIGHT.equalsIgnoreCase(String.valueOf(source))
                ? GUARANTOR_SOURCE_FORESIGHT
                : GUARANTOR_SOURCE_LMS;
        }

        String identityKey() {
            if (localMemberId != null) {
                return GUARANTOR_SOURCE_LMS + ":" + localMemberId;
            }
            return GUARANTOR_SOURCE_FORESIGHT + ":"
                + safeLower(externalStationId)
                + ":"
                + safeLower(externalMemberNo);
        }

        String displayName() {
            return first(fullName, externalMemberNo, "Guarantor");
        }

        GuarantorSelection withMember(Member member) {
            if (member == null) {
                return this;
            }
            return new GuarantorSelection(
                source,
                member.getId(),
                first(externalMemberNo, member.getMemberNo()),
                first(externalStationId, member.getStationId()),
                first(fullName, member.getFullName()),
                email,
                phone,
                lookupBy,
                lookupValue,
                financialSnapshot
            );
        }

        GuarantorSelection asLocal(Member member) {
            if (member == null) {
                return this;
            }
            return new GuarantorSelection(
                GUARANTOR_SOURCE_LMS,
                member.getId(),
                first(externalMemberNo, member.getMemberNo()),
                first(externalStationId, member.getStationId()),
                first(fullName, member.getFullName()),
                email,
                phone,
                lookupBy,
                lookupValue,
                financialSnapshot
            );
        }

        private static String safeLower(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }

        private static String first(String... values) {
            if (values == null) {
                return "";
            }
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
            return "";
        }
    }
}
