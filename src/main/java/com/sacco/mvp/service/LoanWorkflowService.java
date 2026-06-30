package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.integration.foresight.ForesightAccountSummary;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import com.sacco.mvp.integration.foresight.UpstreamAvailabilityException;
import com.sacco.mvp.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LoanWorkflowService {
    private static final long REVERSAL_WINDOW_HOURS = 24L;
    private static final List<LoanStatus> APPLICATION_IN_PROGRESS_LOCK_STATUSES = List.of(
        LoanStatus.DRAFT,
        LoanStatus.SUBMITTED,
        LoanStatus.AWAITING_GUARANTORS,
        LoanStatus.ALL_GUARANTORS_APPROVED,
        LoanStatus.READY_FOR_MANAGER,
        LoanStatus.MANAGER_ACCEPTED,
        LoanStatus.AWAITING_LOAN_OFFICER,
        LoanStatus.LOAN_OFFICER_APPROVED,
        LoanStatus.AWAITING_BOARD,
        LoanStatus.AWAITING_CREDIT_COMMITTEE,
        LoanStatus.BOARD_APPROVED,
        LoanStatus.AWAITING_ACCOUNTANT,
        LoanStatus.ACCOUNTANT_APPROVED,
        LoanStatus.READY_FOR_DISBURSEMENT
    );
    private static final List<LoanStatus> ACTIVE_LOAN_LOCK_STATUSES = List.of(
        LoanStatus.FINAL_APPROVED,
        LoanStatus.DEFAULTED
    );
    private static final Set<LoanStatus> MEMBER_FORFEITABLE_REVIEW_STATUSES = EnumSet.of(
        LoanStatus.READY_FOR_MANAGER,
        LoanStatus.MANAGER_ACCEPTED,
        LoanStatus.AWAITING_LOAN_OFFICER,
        LoanStatus.LOAN_OFFICER_APPROVED,
        LoanStatus.AWAITING_BOARD,
        LoanStatus.AWAITING_CREDIT_COMMITTEE,
        LoanStatus.BOARD_APPROVED,
        LoanStatus.AWAITING_ACCOUNTANT,
        LoanStatus.ACCOUNTANT_APPROVED,
        LoanStatus.READY_FOR_DISBURSEMENT
    );
    private final LoanProductSettingRepository loanProductSettingRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final BoardReviewRepository boardReviewRepository;
    private final ManagerReviewRepository managerReviewRepository;
    private final MemberRepository memberRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final FormSchemaService formSchemaService;
    private final EligibilityService eligibilityService;
    private final OutboxService outboxService;
    private final LoanAttachmentService loanAttachmentService;
    private final LoanProductRequiredAttachmentService requiredAttachmentService;
    private final LoanPaymentTransactionSyncService loanPaymentTransactionSyncService;
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

    public List<LoanProductSetting> listProducts(String saccoId) {
        List<LoanProductSetting> products = loanProductSettingRepository.findBySaccoIdAndActiveTrue(saccoId);
        if (!products.isEmpty()) {
            return products.stream()
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
            .findLatestVisibleCurrentForApplicant(memberId, APPLICATION_IN_PROGRESS_LOCK_STATUSES, PageRequest.of(0, 1))
            .stream()
            .findFirst()
            .orElse(null);
        long unacknowledgedDisbursedApplicationCount = loanApplicationRepository
            .countByApplicantMemberIdAndStatusAndApplicantDisbursementAcknowledgedAtIsNull(memberId, LoanStatus.FINAL_APPROVED);
        long pendingGuaranteeCount = guarantorRequestRepository.countVisiblePendingByGuarantorMemberId(memberId);
        return new MemberDashboardData(
            statusCounts,
            activeLoans,
            latestCurrentApplication,
            unacknowledgedDisbursedApplicationCount,
            pendingGuaranteeCount
        );
    }

    public MemberApplicationListData memberApplicationList(UUID memberId) {
        List<LoanApplication> currentApplications = loanApplicationRepository
            .findVisibleCurrentForApplicant(memberId, APPLICATION_IN_PROGRESS_LOCK_STATUSES);
        long archiveCount = loanApplicationRepository.countByStatusForApplicant(memberId).stream()
            .filter(row -> !APPLICATION_IN_PROGRESS_LOCK_STATUSES.contains(row.getStatus()))
            .mapToLong(LoanApplicationRepository.StatusCountProjection::getTotal)
            .sum();
        return new MemberApplicationListData(currentApplications, archiveCount);
    }

    public LoanApplication getMine(UUID appId, UUID memberId) {
        return loanApplicationRepository.findByIdAndApplicantMemberId(appId, memberId)
            .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
    }

    @Transactional
    public void acknowledgeDisbursement(UUID appId, UUID memberId) {
        LoanApplication app = getMine(appId, memberId);
        if (app.getStatus() != LoanStatus.FINAL_APPROVED) {
            throw new IllegalStateException("Only disbursed loans can be acknowledged.");
        }
        if (app.getApplicantDisbursementAcknowledgedAt() == null) {
            OffsetDateTime now = OffsetDateTime.now();
            app.setApplicantDisbursementAcknowledgedAt(now);
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
        return !isTopUpBlockedFor(app)
            && app.getFinalDueDate() != null
            && !app.getFinalDueDate().isBefore(LocalDate.now());
    }

    public boolean canForfeitReviewApplication(LoanApplication app) {
        return app != null && MEMBER_FORFEITABLE_REVIEW_STATUSES.contains(app.getStatus());
    }

    public LoanApplication requireAllowedTopUpSourceLoan(String saccoId, UUID applicantId, UUID topUpSourceLoanId) {
        if (topUpSourceLoanId == null) {
            return null;
        }
        LoanApplication sourceLoan = loanApplicationRepository.findByIdAndApplicantMemberId(topUpSourceLoanId, applicantId)
            .orElseThrow(() -> new IllegalArgumentException("Selected top-up source loan was not found."));
        if (!Objects.equals(sourceLoan.getSaccoId(), saccoId)) {
            throw new IllegalArgumentException("Selected top-up source loan was not found.");
        }
        if (isTopUpBlockedFor(sourceLoan)) {
            throw new IllegalStateException("Disbursed loans cannot be topped up.");
        }
        return sourceLoan;
    }

    @Transactional
    public LoanApplication saveDraft(String saccoId, UUID applicantId, LoanType loanType, BigDecimal amount,
                                     Integer tenorMonths, Map<String, String> requestParams, UUID existingId,
                                     List<UUID> guarantorIds,
                                     String financialSnapshotJson, UUID topUpSourceLoanId, List<MultipartFile> attachments,
                                     Map<UUID, List<MultipartFile>> requiredAttachments) {
        LoanApplication topUpSourceLoan = requireAllowedTopUpSourceLoan(saccoId, applicantId, topUpSourceLoanId);
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
        boolean reEditingAfterGuarantorApproval = existingDraft != null
            && existingDraft.getStatus() == LoanStatus.ALL_GUARANTORS_APPROVED;
        String applicantStationId = existingDraft != null
            ? coalesceStationId(existingDraft.getStationId(), resolveMemberStationId(applicantId))
            : requireMemberStationId(applicantId);
        LoanProductSetting product = formSchemaService.getSchema(saccoId, loanType);
        assertCanApplyForProduct(saccoId, applicantId, product, existingDraft == null ? null : existingDraft.getId());
        validateRequestedAmount(product, amount);
        validateRepaymentPeriod(product, tenorMonths);
        requireLoadedFinancialDataForDraft(product, financialSnapshotJson);
        Map<String, Object> formData = formSchemaService.extractFormData(requestParams, product.getFormSchema());
        formSchemaService.validateAgainstSchema(product.getFormSchema(), formData);
        appendLoanPurpose(formData, requestParams.get("purpose"));
        List<LoanProductRequiredAttachment> requiredAttachmentDefinitions = validateApplicantAttachmentRequirement(
            product,
            existingDraft,
            attachments,
            requiredAttachments
        );
        validateGuarantorSelection(
            saccoId,
            applicantStationId,
            applicantId,
            product.getGuarantorsRequired(),
            amount,
            guarantorIds,
            product
        );

        EligibilityService.EligibilityResult eligibility = checkApplicantSavingsEligibility(saccoId, applicantId, loanType, amount);
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
        application.setLoanType(loanType);
        application.setAmount(amount);
        application.setTenorMonths(tenorMonths);
        application.setStatus(LoanStatus.DRAFT);
        application.setFormData(formSchemaService.toJson(formData));
        application.setRequiredGuarantors(product.getGuarantorsRequired());
        application.setPolicySnapshot(snapshot);
        application.setSelectedGuarantors(toGuarantorSelectionJson(guarantorIds));
        application.setFinancialSnapshot(normalizeJson(financialSnapshotJson, "Load SACCO financial details again before saving the draft."));
        if (application.getAttachmentsJson() == null) {
            application.setAttachmentsJson("[]");
        }
        application.setApplicantSignatureText(null);
        application.setApplicantSignatureVerifiedAt(null);
        application.setUpdatedAt(OffsetDateTime.now());
        LoanApplication saved = loanApplicationRepository.save(application);
        if (reEditingAfterGuarantorApproval) {
            expireGuarantorApprovalsForApplicantEdit(saved.getId());
        }
        saved.setAttachmentsJson(loanAttachmentService.store(saved.getId(), attachments, saved.getAttachmentsJson()));
        saved.setAttachmentsJson(loanAttachmentService.storeRequired(
            saved.getId(),
            requiredAttachmentDefinitions.stream()
                .map(requirement -> new LoanAttachmentService.RequiredAttachmentUpload(
                    requirement.getId(),
                    requirement.getAttachmentName(),
                    requiredAttachments == null ? List.of() : requiredAttachments.getOrDefault(requirement.getId(), List.of())
                ))
                .toList(),
            saved.getAttachmentsJson()
        ));
        return loanApplicationRepository.save(saved);
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

    private boolean isTopUpBlockedFor(LoanApplication app) {
        LoanStatus status = app.getStatus();
        return status == LoanStatus.FINAL_APPROVED || status == LoanStatus.DEFAULTED || status == LoanStatus.PAID;
    }

    @Transactional
    public LoanApplication saveAndSubmit(String saccoId, UUID applicantId, LoanType loanType, BigDecimal amount,
                                         Integer tenorMonths, Map<String, String> requestParams, UUID existingId,
                                         List<UUID> guarantorIds,
                                         String financialSnapshotJson, UUID topUpSourceLoanId, List<MultipartFile> attachments,
                                         Map<UUID, List<MultipartFile>> requiredAttachments) {
        LoanApplication saved = saveDraft(
            saccoId, applicantId, loanType, amount, tenorMonths, requestParams, existingId, guarantorIds,
            financialSnapshotJson, topUpSourceLoanId, attachments, requiredAttachments);
        LoanApplication submitted = submit(saved.getId(), applicantId);
        // Return reloaded row to guarantee caller sees persisted status/form data.
        return getMine(submitted.getId(), applicantId);
    }

    @Transactional
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
        syncFinancialSnapshotToCurrentProduct(app);
        refreshFinancialSnapshotIfRequired(app, product);
        loanQualificationPolicyService.assertApplicantEligible(app.getSaccoId(), app.getApplicantMemberId());

        EligibilityService.EligibilityResult result = checkApplicantSavingsEligibility(
            app.getSaccoId(), app.getApplicantMemberId(), app.getLoanType(), app.getAmount());
        app.setPolicySnapshot(eligibilityService.policySnapshotJson(
            result,
            app.getRequiredGuarantors() == null ? 0 : Math.max(app.getRequiredGuarantors(), 0),
            loanProductWorkflowService.snapshotData(
                loanProductWorkflowService.resolveForProduct(app.getSaccoId(), product)
            )
        ));

        app.setSubmittedAt(OffsetDateTime.now());
        capturePaymentDetailsIfMissing(app);

        if (app.getRequiredGuarantors() == 0) {
            moveIntoConfiguredReviewStage(app, memberId);
        } else {
            List<UUID> selectedGuarantors = parseSelectedGuarantors(app.getSelectedGuarantors());
            if (selectedGuarantors.size() != app.getRequiredGuarantors()) {
                throw new IllegalStateException("Select exactly " + app.getRequiredGuarantors() + " guarantors before submitting.");
            }
            app.setStatus(LoanStatus.AWAITING_GUARANTORS);
        }
        app.setUpdatedAt(OffsetDateTime.now());
        LoanApplication submitted = loanApplicationRepository.save(app);
        if (submitted.getRequiredGuarantors() > 0) {
            refreshGuarantorRequestsForSubmission(
                submitted,
                memberId,
                parseSelectedGuarantors(submitted.getSelectedGuarantors())
            );
            return getMine(submitted.getId(), memberId);
        }
        return submitted;
    }

    @Transactional
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
        syncFinancialSnapshotToCurrentProduct(app);
        refreshFinancialSnapshotIfRequired(app, product);
        loanQualificationPolicyService.assertApplicantEligible(app.getSaccoId(), app.getApplicantMemberId());
        EligibilityService.EligibilityResult result = checkApplicantSavingsEligibility(
            app.getSaccoId(), app.getApplicantMemberId(), app.getLoanType(), app.getAmount()
        );
        app.setPolicySnapshot(eligibilityService.policySnapshotJson(
            result,
            app.getRequiredGuarantors() == null ? 0 : Math.max(app.getRequiredGuarantors(), 0),
            loanProductWorkflowService.snapshotData(
                loanProductWorkflowService.resolveForProduct(app.getSaccoId(), product)
            )
        ));

        capturePaymentDetailsIfMissing(app);
        moveIntoConfiguredReviewStage(app, memberId);
        return loanApplicationRepository.save(app);
    }

    private void capturePaymentDetailsIfMissing(LoanApplication app) {
        if (app.getPaymentDetailsSnapshot() == null || app.getPaymentDetailsSnapshot().isBlank()) {
            app.setPaymentDetailsSnapshot(paymentDetailsService.snapshotJsonForMember(app.getApplicantMemberId()));
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

    @Transactional
    public int syncLoanPayments(UUID appId, UUID memberId, int monthsBack) {
        LoanApplication app = getMine(appId, memberId);
        if (app.getLoanId() == null || app.getLoanId().isBlank()) {
            throw new IllegalStateException("This loan has not been disbursed yet.");
        }
        return loanPaymentTransactionSyncService.syncRecent(app, monthsBack);
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

        if (!query.matches("\\d{4,20}")) {
            return Page.empty(PageRequest.of(page, size));
        }

        return memberRepository.findGuarantorCandidatesByNumberSuffix(
            saccoId,
            normalizedStationId,
            applicantId,
            query,
            PageRequest.of(page, size)
        );
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

        app.setSelectedGuarantors(toGuarantorSelectionJson(uniqueGuarantors));
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
    }

    @Transactional
    public void forfeitReviewApplication(UUID appId, UUID applicantId) {
        LoanApplication app = getMine(appId, applicantId);
        if (!canForfeitReviewApplication(app)) {
            throw new IllegalStateException("This application cannot be forfeited at its current stage.");
        }
        app.setStatus(LoanStatus.FORFEITED);
        app.setUpdatedAt(OffsetDateTime.now());
        loanApplicationRepository.save(app);
        outboxService.enqueue("LOAN", appId, "LOAN_FORFEITED", app.getApplicantMemberId(),
            app.getSaccoId(), app.getStationId(),
            Map.of("loanId", app.getId().toString()));
    }

    @Transactional
    public void deleteApplication(UUID appId, UUID applicantId) {
        LoanApplication app = getMine(appId, applicantId);
        if (app.getStatus() != LoanStatus.DRAFT
            && app.getStatus() != LoanStatus.AWAITING_GUARANTORS) {
            throw new IllegalStateException("This application can no longer be deleted.");
        }

        deleteApplicationRecords(app);
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
        EligibilityService.EligibilityResult eligibility = eligibilityService.check(app.getSaccoId(), app.getApplicantMemberId(),
            app.getLoanType(), app.getAmount());

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
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to save selected guarantors", e);
        }
    }

    private String toGuarantorSelectionJson(List<UUID> guarantorIds) {
        List<UUID> uniqueGuarantors = guarantorIds == null ? Collections.emptyList() : new ArrayList<>(new LinkedHashSet<>(guarantorIds));
        if (uniqueGuarantors.isEmpty()) {
            return "[]";
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (UUID guarantorId : uniqueGuarantors) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", guarantorId.toString());
            rows.add(row);
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to save selected guarantors", e);
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
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<?> raw = objectMapper.readValue(json, new TypeReference<List<?>>() {});
            List<UUID> values = new ArrayList<>();
            for (Object item : raw) {
                if (item instanceof String text) {
                    values.add(UUID.fromString(text));
                } else if (item instanceof Map<?, ?> map && map.get("id") != null) {
                    values.add(UUID.fromString(String.valueOf(map.get("id"))));
                }
            }
            return values;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<UUID> normalizeGuarantorSelection(LoanApplication app, UUID applicantId, List<UUID> guarantorIds) {
        LoanProductSetting product = resolveWorkflowProduct(app);
        return validateGuarantorSelection(
            app.getSaccoId(),
            coalesceStationId(app.getStationId(), requireMemberStationId(applicantId)),
            applicantId,
            app.getRequiredGuarantors(),
            app.getAmount(),
            guarantorIds,
            product
        );
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
            if (guarantorId.equals(applicantId)) {
                throw new IllegalArgumentException("You cannot select yourself as a guarantor");
            }
            Member guarantor = memberRepository.findById(guarantorId)
                .orElseThrow(() -> new IllegalArgumentException("Guarantor not found"));
            if (!guarantor.getSaccoId().equals(saccoId) || guarantor.getStatus() != MemberStatus.ACTIVE) {
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
        }

        return uniqueGuarantors;
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
                                                       List<UUID> guarantorIds) {
        List<UUID> uniqueGuarantors = normalizeGuarantorSelection(app, applicantId, guarantorIds);

        guarantorRequestRepository.deleteByLoanApplicationId(app.getId());
        guarantorRequestRepository.flush();

        for (UUID guarantorId : uniqueGuarantors) {
            GuarantorRequest request = guarantorRequestRepository.findByLoanApplicationIdAndGuarantorMemberId(app.getId(), guarantorId)
                .orElseGet(() -> GuarantorRequest.builder()
                    .id(UUID.randomUUID())
                    .loanApplicationId(app.getId())
                    .guarantorMemberId(guarantorId)
                    .createdAt(OffsetDateTime.now())
                    .build());

            request.setStatus(GuarantorRequestStatus.PENDING);
            request.setRequestedAmount(null);
            request.setCommittedAmount(null);
            request.setDecisionReason(null);
            request.setGuarantorSignatureText(null);
            request.setGuarantorSignatureVerifiedAt(null);
            request.setDecidedAt(null);
            guarantorRequestRepository.save(request);

            outboxService.enqueue("GUARANTOR_REQUEST", app.getId(), "GUARANTOR_REQUEST_ASSIGNED", guarantorId,
                app.getSaccoId(), app.getStationId(),
                Map.of("loanId", app.getId().toString()));
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
        if (app == null || app.getSaccoId() == null || app.getLoanType() == null) {
            return null;
        }
        return loanProductSettingRepository.findBySaccoIdAndLoanType(app.getSaccoId(), app.getLoanType())
            .orElse(null);
    }

    private void syncFinancialSnapshotToCurrentProduct(LoanApplication app) {
        if (app == null) {
            return;
        }
        Map<String, Object> latestSnapshot = new LinkedHashMap<>(financialDetailsService.generateSnapshot(
            app.getSaccoId(),
            app.getApplicantMemberId(),
            app.getLoanType(),
            app.getAmount(),
            app.getTenorMonths(),
            app.getTopUpSourceLoanId()
        ));
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

    public record MemberDashboardData(
        Map<LoanStatus, Long> statusCounts,
        List<LoanApplication> activeLoans,
        LoanApplication latestCurrentApplication,
        long unacknowledgedDisbursedApplicationCount,
        long pendingGuaranteeCount
    ) {
    }

    public record MemberApplicationListData(
        List<LoanApplication> currentApplications,
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
}
