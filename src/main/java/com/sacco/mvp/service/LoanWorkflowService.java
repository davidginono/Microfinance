package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
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
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LoanWorkflowService {
    private static final long REVERSAL_WINDOW_HOURS = 24L;
    private static final List<LoanStatus> REVIEW_ONWARD_LOCK_STATUSES = List.of(
        LoanStatus.READY_FOR_MANAGER,
        LoanStatus.MANAGER_ACCEPTED,
        LoanStatus.AWAITING_BOARD,
        LoanStatus.BOARD_APPROVED
    );
    private final LoanProductSettingRepository loanProductSettingRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final BoardReviewRepository boardReviewRepository;
    private final ManagerReviewRepository managerReviewRepository;
    private final MemberRepository memberRepository;
    private final SavingsAccountRepository savingsAccountRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final FormSchemaService formSchemaService;
    private final EligibilityService eligibilityService;
    private final OutboxService outboxService;
    private final LoanAttachmentService loanAttachmentService;
    private final ObjectMapper objectMapper;
    private final SaccoConfigurationService saccoConfigurationService;

    public List<LoanProductSetting> listProducts(String saccoId) {
        List<LoanProductSetting> products = loanProductSettingRepository.findBySaccoIdAndActiveTrue(saccoId);
        if (!products.isEmpty()) {
            return products;
        }
        saccoConfigurationService.ensureDefaultLoanProducts(saccoId);
        return loanProductSettingRepository.findBySaccoIdAndActiveTrue(saccoId);
    }

    public List<LoanApplication> myApplications(UUID memberId) {
        return loanApplicationRepository.findByApplicantMemberIdOrderByCreatedAtDesc(memberId);
    }

    public LoanApplication getMine(UUID appId, UUID memberId) {
        return loanApplicationRepository.findByIdAndApplicantMemberId(appId, memberId)
            .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
    }

    public Optional<LoanApplication> findApplicationInProgress(UUID memberId) {
        return loanApplicationRepository.findByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(memberId, REVIEW_ONWARD_LOCK_STATUSES)
            .stream()
            .findFirst();
    }

    @Transactional
    public LoanApplication saveDraft(String saccoId, UUID applicantId, LoanType loanType, BigDecimal amount,
                                     Integer tenorMonths, Map<String, String> requestParams, UUID existingId,
                                     List<UUID> guarantorIds, String financialSnapshotJson, UUID topUpSourceLoanId,
                                     List<MultipartFile> attachments) {
        LoanProductSetting product = formSchemaService.getSchema(saccoId, loanType);
        validateRepaymentPeriod(product, tenorMonths);
        Map<String, Object> formData = formSchemaService.extractFormData(requestParams, product.getFormSchema());
        formSchemaService.validateAgainstSchema(product.getFormSchema(), formData);

        EligibilityService.EligibilityResult eligibility = eligibilityService.check(saccoId, applicantId, loanType, amount);
        String snapshot = eligibilityService.policySnapshotJson(eligibility, product.getGuarantorsRequired());

        LoanApplication application = existingId == null
            ? LoanApplication.builder().id(UUID.randomUUID()).createdAt(OffsetDateTime.now()).build()
            : loanApplicationRepository.findByIdAndApplicantMemberId(existingId, applicantId)
                .orElseThrow(() -> new IllegalArgumentException("Loan draft not found"));

        application.setSaccoId(saccoId);
        application.setApplicantMemberId(applicantId);
        application.setTopUpSourceLoanId(topUpSourceLoanId);
        application.setLoanType(loanType);
        application.setAmount(amount);
        application.setTenorMonths(tenorMonths);
        application.setStatus(LoanStatus.DRAFT);
        application.setFormData(formSchemaService.toJson(formData));
        application.setRequiredGuarantors(product.getGuarantorsRequired());
        application.setPolicySnapshot(snapshot);
        application.setSelectedGuarantors(toJson(guarantorIds == null ? Collections.emptyList() : new ArrayList<>(new LinkedHashSet<>(guarantorIds))));
        application.setFinancialSnapshot(normalizeJson(financialSnapshotJson, "Load SACCO financial details again before saving the draft."));
        if (application.getAttachmentsJson() == null) {
            application.setAttachmentsJson("[]");
        }
        application.setApplicantSignatureText(null);
        application.setApplicantSignatureVerifiedAt(null);
        application.setUpdatedAt(OffsetDateTime.now());
        LoanApplication saved = loanApplicationRepository.save(application);
        saved.setAttachmentsJson(loanAttachmentService.store(saved.getId(), attachments, saved.getAttachmentsJson()));
        return loanApplicationRepository.save(saved);
    }

    @Transactional
    public LoanApplication saveAndSubmit(String saccoId, UUID applicantId, LoanType loanType, BigDecimal amount,
                                         Integer tenorMonths, Map<String, String> requestParams, UUID existingId,
                                         List<UUID> guarantorIds, String financialSnapshotJson, UUID topUpSourceLoanId,
                                         List<MultipartFile> attachments) {
        LoanApplication saved = saveDraft(
            saccoId, applicantId, loanType, amount, tenorMonths, requestParams, existingId, guarantorIds, financialSnapshotJson, topUpSourceLoanId, attachments);
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

        EligibilityService.EligibilityResult result = eligibilityService.check(app.getSaccoId(), app.getApplicantMemberId(),
            app.getLoanType(), app.getAmount());
        if (!result.eligible()) {
            throw new IllegalStateException("Amount exceeds eligibility cap");
        }

        app.setSubmittedAt(OffsetDateTime.now());

        if (app.getRequiredGuarantors() == 0) {
            assertApplicantCanAdvanceToManagerReview(memberId);
            app.setStatus(LoanStatus.READY_FOR_MANAGER);
            outboxService.enqueue("LOAN", app.getId(), "LOAN_READY_FOR_MANAGER", memberId,
                Map.of("loanId", app.getId().toString()));
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
            refreshGuarantorRequestsForSubmission(submitted, memberId, parseSelectedGuarantors(submitted.getSelectedGuarantors()));
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

        assertApplicantCanAdvanceToManagerReview(memberId);
        app.setStatus(LoanStatus.READY_FOR_MANAGER);
        app.setUpdatedAt(OffsetDateTime.now());
        LoanApplication submitted = loanApplicationRepository.save(app);
        outboxService.enqueue("LOAN", app.getId(), "LOAN_READY_FOR_MANAGER", memberId,
            Map.of("loanId", app.getId().toString()));
        return submitted;
    }

    private void assertApplicantCanAdvanceToManagerReview(UUID applicantId) {
        findApplicationInProgress(applicantId).ifPresent(existing -> {
            throw new IllegalStateException(
                "You already have loan application " + existing.getId().toString().substring(0, 8)
                    + " on review (" + humanizeApplicationLockStatus(existing.getStatus())
                    + "). Wait until it is disbursed before sending another application forward."
            );
        });
    }

    private String humanizeApplicationLockStatus(LoanStatus status) {
        return switch (status) {
            case READY_FOR_MANAGER, MANAGER_ACCEPTED -> "On Review By Manager";
            case AWAITING_BOARD, BOARD_APPROVED -> "On Review By Board";
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

    public Page<Member> searchGuarantors(String saccoId, UUID applicantId, String q, int page, int size) {
        String query = q == null ? "" : q.trim();
        if (query.isBlank()) {
            return Page.empty(PageRequest.of(page, size));
        }

        Optional<Member> exactMemberNo = memberRepository.findBySaccoIdAndStatusAndMemberNoIgnoreCase(
            saccoId, MemberStatus.ACTIVE, query);
        if (exactMemberNo.isPresent() && !exactMemberNo.get().getId().equals(applicantId)) {
            return new PageImpl<>(List.of(exactMemberNo.get()), PageRequest.of(page, size), 1);
        }

        if (!query.matches("\\d{6}")) {
            return Page.empty(PageRequest.of(page, size));
        }

        List<Member> matches = memberRepository.findBySaccoIdAndStatusOrderByFullNameAsc(saccoId, MemberStatus.ACTIVE).stream()
            .filter(member -> !member.getId().equals(applicantId))
            .filter(member -> {
                String memberNo = member.getMemberNo() == null ? "" : member.getMemberNo();
                String digits = memberNo.replaceAll("\\D", "");
                return !digits.isBlank() && digits.endsWith(query);
            })
            .limit(size)
            .toList();

        return new PageImpl<>(matches, PageRequest.of(page, size), matches.size());
    }

    @Transactional
    public void selectGuarantors(UUID appId, UUID applicantId, List<UUID> guarantorIds) {
        LoanApplication app = getMine(appId, applicantId);
        if (app.getRequiredGuarantors() == 0) {
            return;
        }
        List<UUID> uniqueGuarantors = normalizeGuarantorSelection(app, applicantId, guarantorIds);

        app.setSelectedGuarantors(toJson(uniqueGuarantors));
        app.setUpdatedAt(OffsetDateTime.now());

        if (app.getStatus() == LoanStatus.DRAFT) {
            loanApplicationRepository.save(app);
            return;
        }
        if (app.getStatus() != LoanStatus.AWAITING_GUARANTORS) {
            throw new IllegalStateException("Guarantors must be selected before submitting the application");
        }

        BigDecimal requestedSplit = app.getAmount().divide(BigDecimal.valueOf(app.getRequiredGuarantors()), 2,
            java.math.RoundingMode.UP);
        List<GuarantorRequest> existingRequests = guarantorRequestRepository.findByLoanApplicationId(appId);
        Map<UUID, GuarantorRequest> existingByGuarantor = new LinkedHashMap<>();
        for (GuarantorRequest existingRequest : existingRequests) {
            if (existingRequest.getGuarantorMemberId() != null) {
                existingByGuarantor.putIfAbsent(existingRequest.getGuarantorMemberId(), existingRequest);
            }
        }

        for (GuarantorRequest existingRequest : existingRequests) {
            if (!uniqueGuarantors.contains(existingRequest.getGuarantorMemberId())) {
                guarantorRequestRepository.delete(existingRequest);
            }
        }
        guarantorRequestRepository.flush();

        for (UUID guarantorId : uniqueGuarantors) {
            GuarantorRequest request = existingByGuarantor.get(guarantorId);
            boolean newlyAssigned = request == null;
            if (request == null) {
                request = GuarantorRequest.builder()
                    .id(UUID.randomUUID())
                    .loanApplicationId(appId)
                    .guarantorMemberId(guarantorId)
                    .createdAt(OffsetDateTime.now())
                    .build();
            }

            request.setStatus(GuarantorRequestStatus.PENDING);
            request.setRequestedAmount(requestedSplit);
            request.setCommittedAmount(null);
            request.setDecisionReason(null);
            request.setGuarantorSignatureText(null);
            request.setGuarantorSignatureVerifiedAt(null);
            request.setDecidedAt(null);
            guarantorRequestRepository.save(request);

            if (newlyAssigned) {
                outboxService.enqueue("GUARANTOR_REQUEST", appId, "GUARANTOR_REQUEST_ASSIGNED", guarantorId,
                    Map.of("loanId", appId.toString()));
            }
        }

        app.setStatus(LoanStatus.AWAITING_GUARANTORS);
        loanApplicationRepository.save(app);
    }

    public List<GuarantorRequest> myPendingGuarantorRequests(UUID memberId) {
        return guarantorRequestRepository.findByGuarantorMemberIdAndStatusOrderByCreatedAtDesc(memberId,
            GuarantorRequestStatus.PENDING);
    }

    public List<GuarantorRequest> myGuarantorRequests(UUID memberId) {
        return guarantorRequestRepository.findByGuarantorMemberIdOrderByCreatedAtDesc(memberId);
    }

    @Transactional
    public void approveGuarantorRequest(UUID requestId, UUID guarantorId) {
        GuarantorRequest request = guarantorRequestRepository.findByIdAndGuarantorMemberId(requestId, guarantorId)
            .orElseThrow(() -> new IllegalArgumentException("Guarantor request not found"));
        approveGuarantorRequest(requestId, guarantorId, request.getRequestedAmount(), null, null);
    }

    @Transactional
    public void approveGuarantorRequest(UUID requestId,
                                        UUID guarantorId,
                                        String signatureText,
                                        OffsetDateTime verifiedAt) {
        GuarantorRequest request = guarantorRequestRepository.findByIdAndGuarantorMemberId(requestId, guarantorId)
            .orElseThrow(() -> new IllegalArgumentException("Guarantor request not found"));
        approveGuarantorRequest(requestId, guarantorId, request.getRequestedAmount(), signatureText, verifiedAt);
    }

    @Transactional
    public void approveGuarantorRequest(UUID requestId, UUID guarantorId, BigDecimal committedAmount) {
        approveGuarantorRequest(requestId, guarantorId, committedAmount, null, null);
    }

    @Transactional
    public void approveGuarantorRequest(UUID requestId,
                                        UUID guarantorId,
                                        BigDecimal committedAmount,
                                        String signatureText,
                                        OffsetDateTime verifiedAt) {
        GuarantorRequest request = guarantorRequestRepository.findByIdAndGuarantorMemberId(requestId, guarantorId)
            .orElseThrow(() -> new IllegalArgumentException("Guarantor request not found"));
        if (request.getStatus() != GuarantorRequestStatus.PENDING) {
            throw new IllegalStateException("Request already decided");
        }

        BigDecimal available = savingsAccountRepository.findByMemberId(guarantorId)
            .orElseThrow(() -> new IllegalArgumentException("Savings account missing"))
            .getAvailableBalance();
        if (committedAmount.compareTo(available) > 0) {
            throw new IllegalStateException("Committed amount exceeds guarantor balance");
        }

        request.setStatus(GuarantorRequestStatus.APPROVED);
        request.setCommittedAmount(committedAmount);
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
        request.setStatus(GuarantorRequestStatus.REJECTED);
        request.setDecisionReason(reason);
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
            return objectMapper.readValue(json, new TypeReference<List<UUID>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<UUID> normalizeGuarantorSelection(LoanApplication app, UUID applicantId, List<UUID> guarantorIds) {
        if (guarantorIds == null || guarantorIds.size() != app.getRequiredGuarantors()) {
            throw new IllegalArgumentException("Select exactly " + app.getRequiredGuarantors() + " guarantors");
        }

        List<UUID> uniqueGuarantors = new ArrayList<>(new LinkedHashSet<>(guarantorIds));
        if (uniqueGuarantors.size() != app.getRequiredGuarantors()) {
            throw new IllegalArgumentException("Select exactly " + app.getRequiredGuarantors() + " different guarantors");
        }

        for (UUID guarantorId : uniqueGuarantors) {
            if (guarantorId.equals(applicantId)) {
                throw new IllegalArgumentException("You cannot select yourself as a guarantor");
            }
            Member guarantor = memberRepository.findById(guarantorId)
                .orElseThrow(() -> new IllegalArgumentException("Guarantor not found"));
            if (!guarantor.getSaccoId().equals(app.getSaccoId()) || guarantor.getStatus() != MemberStatus.ACTIVE) {
                throw new IllegalArgumentException("Guarantor must be active and in same SACCO");
            }
        }

        return uniqueGuarantors;
    }

    private void refreshGuarantorRequestsForSubmission(LoanApplication app, UUID applicantId, List<UUID> guarantorIds) {
        List<UUID> uniqueGuarantors = normalizeGuarantorSelection(app, applicantId, guarantorIds);
        BigDecimal requestedSplit = app.getAmount().divide(BigDecimal.valueOf(app.getRequiredGuarantors()), 2,
            java.math.RoundingMode.UP);

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
            request.setRequestedAmount(requestedSplit);
            request.setCommittedAmount(null);
            request.setDecisionReason(null);
            request.setGuarantorSignatureText(null);
            request.setGuarantorSignatureVerifiedAt(null);
            request.setDecidedAt(null);
            guarantorRequestRepository.save(request);

            outboxService.enqueue("GUARANTOR_REQUEST", app.getId(), "GUARANTOR_REQUEST_ASSIGNED", guarantorId,
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

    private void validateRepaymentPeriod(LoanProductSetting product, Integer tenorMonths) {
        if (tenorMonths == null || tenorMonths <= 0) {
            throw new IllegalArgumentException("Repayment period must be at least 1 month");
        }
        if (product.getMaxRepaymentMonths() != null
            && product.getMaxRepaymentMonths() > 0
            && tenorMonths > product.getMaxRepaymentMonths()) {
            throw new IllegalArgumentException("Repayment period cannot exceed " + product.getMaxRepaymentMonths() + " month(s) for this loan product");
        }
    }
}

