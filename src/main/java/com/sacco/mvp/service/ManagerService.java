package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ManagerService {
    private static final int MAX_QUEUE_ROWS = 100;
    private static final java.util.regex.Pattern LOAN_ID_PATTERN = java.util.regex.Pattern.compile("^[0-9]{4,20}$");
    private final LoanApplicationRepository loanApplicationRepository;
    private final ManagerReviewRepository managerReviewRepository;
    private final BoardReviewRepository boardReviewRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final MemberRepository memberRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final LoanProductSettingRepository loanProductSettingRepository;
    private final OutboxService outboxService;
    private final RoleDirectoryService roleDirectoryService;
    private final LoanAttachmentService loanAttachmentService;
    private final WorkflowRoutingService workflowRoutingService;
    private final AuditService auditService;

    public ManagerDashboard dashboard(String saccoId) {
        return dashboard(saccoId, null);
    }

    public ManagerDashboard dashboard(String saccoId, String stationId) {
        LocalDate today = LocalDate.now();
        LocalDate currentYearStart = today.withDayOfYear(1);
        LocalDate recentCutoff = today.minusDays(30);
        Map<LoanStatus, Long> counts = new EnumMap<>(LoanStatus.class);
        for (LoanStatus status : LoanStatus.values()) {
            counts.put(status, 0L);
        }
        loanApplicationRepository.countByStatusForScope(saccoId, blankToNull(stationId))
            .forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        OffsetDateTime currentYearStartAt = currentYearStart.atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        long totalLoans = loanApplicationRepository.countForScope(saccoId, blankToNull(stationId));
        long totalDisbursed = loanApplicationRepository.countDisbursedInYearForScope(
            saccoId,
            blankToNull(stationId),
            List.of(LoanStatus.DISBURSED, LoanStatus.PAR, LoanStatus.DEFAULTED, LoanStatus.PAID),
            currentYearStart,
            currentYearStartAt
        );
        long defaultedLoansCurrentYear = loanApplicationRepository.countDefaultedInYearForScope(
            saccoId,
            blankToNull(stationId),
            currentYearStart,
            currentYearStartAt
        );

        List<StatusCount> breakdown = new ArrayList<>();
        for (LoanStatus status : List.of(
            LoanStatus.DRAFT,
            LoanStatus.SUBMITTED,
            LoanStatus.AWAITING_GUARANTORS,
            LoanStatus.ALL_GUARANTORS_APPROVED,
            LoanStatus.READY_FOR_MANAGER,
            LoanStatus.MANAGER_ACCEPTED,
            LoanStatus.AWAITING_LOAN_OFFICER,
            LoanStatus.LOAN_OFFICER_APPROVED,
            LoanStatus.LOAN_OFFICER_REJECTED,
            LoanStatus.AWAITING_BOARD,
            LoanStatus.AWAITING_CREDIT_COMMITTEE,
            LoanStatus.BOARD_APPROVED,
            LoanStatus.AWAITING_ACCOUNTANT,
            LoanStatus.ACCOUNTANT_APPROVED,
            LoanStatus.ACCOUNTANT_REJECTED,
            LoanStatus.READY_FOR_DISBURSEMENT,
            LoanStatus.MANAGER_REJECTED,
            LoanStatus.BOARD_REJECTED,
            LoanStatus.REJECTED,
            LoanStatus.DISBURSED,
            LoanStatus.PAR,
            LoanStatus.DEFAULTED,
            LoanStatus.PAID
        )) {
            breakdown.add(new StatusCount(status, counts.getOrDefault(status, 0L)));
        }

        List<LoanApplication> recentDisbursements = loanApplicationRepository.findRecentDisbursementsForScope(
            saccoId,
            blankToNull(stationId),
            List.of(LoanStatus.DISBURSED, LoanStatus.PAR, LoanStatus.DEFAULTED, LoanStatus.PAID),
            recentCutoff,
            recentCutoff.atStartOfDay().atOffset(OffsetDateTime.now().getOffset()),
            PageRequest.of(0, 5)
        );

        return new ManagerDashboard(
            totalLoans,
            totalDisbursed,
            counts.getOrDefault(LoanStatus.DISBURSED, 0L),
            defaultedLoansCurrentYear,
            counts.getOrDefault(LoanStatus.PAID, 0L),
            counts.getOrDefault(LoanStatus.READY_FOR_MANAGER, 0L),
            counts.getOrDefault(LoanStatus.AWAITING_BOARD, 0L),
            breakdown,
            recentDisbursements
        );
    }

    public List<LoanApplication> queue(String saccoId, LoanStatus status) {
        return queue(saccoId, status == null ? List.of(LoanStatus.READY_FOR_MANAGER) : List.of(status), null, false, null);
    }

    public List<LoanApplication> queue(String saccoId, List<LoanStatus> statuses) {
        return queue(saccoId, statuses, null, false, null);
    }

    public List<LoanApplication> queue(String saccoId,
                                       List<LoanStatus> statuses,
                                       String searchTerm,
                                       boolean searchByLoanId) {
        return queue(saccoId, statuses, searchTerm, searchByLoanId, null);
    }

    public List<LoanApplication> queue(String saccoId,
                                       List<LoanStatus> statuses,
                                       String searchTerm,
                                       boolean searchByLoanId,
                                       String stationId) {
        String normalizedSearchTerm = blankToNull(searchTerm);
        List<LoanStatus> resolvedStatuses = statuses == null || statuses.isEmpty()
            ? List.of(LoanStatus.READY_FOR_MANAGER)
            : statuses;
        if (normalizedSearchTerm == null) {
            return hydrateQueueRows(loanApplicationRepository.findQueuePageForScope(
                saccoId,
                blankToNull(stationId),
                resolvedStatuses,
                PageRequest.of(0, MAX_QUEUE_ROWS)
            ).getContent());
        }
        return hydrateQueueRows(loanApplicationRepository.findQueuePage(
            saccoId,
            blankToNull(stationId),
            resolvedStatuses,
            normalizedSearchTerm.toLowerCase(Locale.ENGLISH),
            searchByLoanId,
            PageRequest.of(0, MAX_QUEUE_ROWS)
        ).getContent());
    }

    private List<LoanApplication> hydrateQueueRows(List<LoanApplicationRepository.QueueLoanRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(this::toQueueLoan).toList();
    }

    private LoanApplication toQueueLoan(LoanApplicationRepository.QueueLoanRow row) {
        LoanApplication loan = new LoanApplication();
        loan.setId(row.getId());
        loan.setApplicationNumber(row.getApplicationNumber());
        loan.setLoanId(row.getLoanId());
        loan.setLoanType(row.getLoanType());
        loan.setLoanProductSettingId(row.getLoanProductSettingId());
        loan.setApplicantMemberId(row.getApplicantMemberId());
        loan.setAmount(row.getAmount());
        loan.setStatus(row.getStatus());
        loan.setCreatedAt(row.getCreatedAt());
        loan.setDisbursementDate(row.getDisbursementDate());
        loan.setFinalDueDate(row.getFinalDueDate());
        loan.setSaccoId(row.getSaccoId());
        loan.setStationId(row.getStationId());
        return loan;
    }

    public LoanApplication get(UUID id, String saccoId) {
        return get(id, saccoId, null);
    }

    public LoanApplication get(UUID id, String saccoId, String stationId) {
        LoanApplication app = loanApplicationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        if (!app.getSaccoId().equals(saccoId)) {
            throw new IllegalArgumentException("Forbidden");
        }
        if (!matchesApplicantStation(app, stationId)) {
            throw new IllegalArgumentException("Forbidden");
        }
        return app;
    }

    public List<LoanApplication> activeApplicantLoans(UUID applicantMemberId, UUID excludeLoanId, String saccoId) {
        return loanApplicationRepository.findByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(
                applicantMemberId, List.of(LoanStatus.DISBURSED, LoanStatus.PAR, LoanStatus.DEFAULTED)).stream()
            .filter(loan -> loan.getSaccoId().equals(saccoId))
            .filter(loan -> excludeLoanId == null || !loan.getId().equals(excludeLoanId))
            .sorted(Comparator.comparing(
                LoanApplication::getDisbursementDate,
                Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(LoanApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
    }

    public SaccoSettings getSettings(String saccoId) {
        return saccoSettingsRepository.findById(saccoId)
            .orElseThrow(() -> new IllegalArgumentException("Settings missing"));
    }

    public List<ManagerReview> reviewsForLoan(UUID loanId) {
        return managerReviewRepository.findByLoanApplicationIdOrderByCreatedAtAsc(loanId);
    }

    public List<ManagerReview> reviewsForLoan(UUID loanId, ApprovalWorkflowStage stage) {
        return managerReviewRepository.findByLoanApplicationIdAndReviewStageOrderByCreatedAtAsc(loanId, stage);
    }

    public org.springframework.data.domain.Page<ManagerReview> archivePage(UUID reviewerId,
                                                                           String reviewStage,
                                                                           String saccoId,
                                                                           String stationId,
                                                                           OffsetDateTime reviewedFrom,
                                                                           OffsetDateTime reviewedToExclusive,
                                                                           boolean filterDecision,
                                                                           String decision,
                                                                           boolean filterStatuses,
                                                                           java.util.Collection<String> statuses,
                                                                           String searchTerm,
                                                                           boolean searchLoanId,
                                                                           org.springframework.data.domain.Pageable pageable) {
        return managerReviewRepository.findLatestArchivePage(
            reviewerId,
            reviewStage,
            saccoId,
            stationId,
            reviewedFrom,
            reviewedToExclusive,
            filterDecision,
            decision,
            filterStatuses,
            statuses,
            searchTerm,
            searchLoanId,
            pageable
        );
    }

    @Transactional
    public void decide(UUID loanId,
                       UUID managerId,
                       ManagerDecision decision,
                       String reasons,
                       String signatureText,
                       OffsetDateTime signatureVerifiedAt) {
        LoanApplication app = getManagedApplication(loanId, managerId, managerDecisionClaim(decision));
        if (app.getStatus() != LoanStatus.READY_FOR_MANAGER) {
            throw new IllegalStateException("Application is not ready for manager review");
        }
        String normalizedReasons = normalizeDecisionReasons(reasons);
        if (decision == ManagerDecision.REJECT && normalizedReasons.isBlank()) {
            throw new IllegalStateException("Add a reason before rejecting this loan application.");
        }
        String normalizedSignature = normalizeSignature(signatureText, signatureVerifiedAt);
        OffsetDateTime now = OffsetDateTime.now();
        int requiredGuarantors = app.getRequiredGuarantors() == null ? 0 : Math.max(app.getRequiredGuarantors(), 0);
        if (requiredGuarantors > 0) {
            long approvals = guarantorRequestRepository.countByLoanApplicationIdAndStatus(
                loanId, GuarantorRequestStatus.APPROVED);
            if (approvals < requiredGuarantors) {
                throw new IllegalStateException(
                    "Application requires " + requiredGuarantors + " approved guarantors before manager review");
            }
        }

        managerReviewRepository.save(ManagerReview.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(loanId)
            .managerMemberId(managerId)
            .reviewStage(ApprovalWorkflowStage.MANAGER)
            .decision(decision)
            .reasons(normalizedReasons)
            .managerSignatureText(normalizedSignature)
            .managerSignatureVerifiedAt(signatureVerifiedAt)
            .createdAt(now)
            .build());

        if (decision == ManagerDecision.REJECT) {
            app.setStatus(LoanStatus.MANAGER_REJECTED);
            app.setApplicantRejectionAcknowledgedAt(null);
            app.setUpdatedAt(now);
            loanApplicationRepository.save(app);
            outboxService.enqueue("LOAN", loanId, "MANAGER_REJECTED", app.getApplicantMemberId(),
                managerId, app.getSaccoId(), app.getStationId(),
                Map.of("reasons", normalizedReasons));
            auditLoanReview(app, managerId, ApprovalWorkflowStage.MANAGER, decision);
            return;
        }

        workflowRoutingService.advanceAfterApproval(app, ApprovalWorkflowStage.MANAGER, managerId);
        loanApplicationRepository.save(app);
        auditLoanReview(app, managerId, ApprovalWorkflowStage.MANAGER, decision);
    }

    @Transactional
    public void decideAccountant(UUID loanId,
                                 UUID accountantId,
                                 ManagerDecision decision,
                                 String reasons,
                                 String signatureText,
                                 OffsetDateTime signatureVerifiedAt) {
        LoanApplication app = getAccountantApplication(loanId, accountantId, accountantDecisionClaim(decision));
        if (app.getStatus() != LoanStatus.AWAITING_ACCOUNTANT) {
            throw new IllegalStateException("Application is not ready for accountant review");
        }
        String normalizedReasons = normalizeDecisionReasons(reasons);
        if (decision == ManagerDecision.REJECT && normalizedReasons.isBlank()) {
            throw new IllegalStateException("Add a reason before rejecting this loan application.");
        }
        String normalizedSignature = normalizeSignature(signatureText, signatureVerifiedAt);
        OffsetDateTime now = OffsetDateTime.now();
        managerReviewRepository.save(ManagerReview.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(loanId)
            .managerMemberId(accountantId)
            .reviewStage(ApprovalWorkflowStage.ACCOUNTANT)
            .decision(decision)
            .reasons(normalizedReasons)
            .managerSignatureText(normalizedSignature)
            .managerSignatureVerifiedAt(signatureVerifiedAt)
            .createdAt(now)
            .build());
        if (decision == ManagerDecision.REJECT) {
            app.setStatus(LoanStatus.ACCOUNTANT_REJECTED);
            app.setApplicantRejectionAcknowledgedAt(null);
            app.setUpdatedAt(now);
            loanApplicationRepository.save(app);
            outboxService.enqueue("LOAN", loanId, "ACCOUNTANT_REJECTED", app.getApplicantMemberId(),
                accountantId, app.getSaccoId(), app.getStationId(),
                Map.of("reasons", normalizedReasons));
            auditLoanReview(app, accountantId, ApprovalWorkflowStage.ACCOUNTANT, decision);
            return;
        }
        workflowRoutingService.advanceAfterApproval(app, ApprovalWorkflowStage.ACCOUNTANT, accountantId);
        loanApplicationRepository.save(app);
        auditLoanReview(app, accountantId, ApprovalWorkflowStage.ACCOUNTANT, decision);
    }

    private String normalizeDecisionReasons(String reasons) {
        return reasons == null ? "" : reasons.trim();
    }

    private String normalizeSignature(String signatureText, OffsetDateTime signatureVerifiedAt) {
        String normalizedSignature = signatureText == null ? "" : signatureText.trim();
        if (normalizedSignature.isBlank() || signatureVerifiedAt == null) {
            throw new IllegalStateException("Save and verify your staff signature before recording this decision.");
        }
        return normalizedSignature;
    }

    @Transactional
    public void disburseLoan(UUID applicationId,
                             UUID disbursementOfficerId,
                             LocalDate disbursementDate,
                             LocalDate firstRepaymentDate,
                             RepaymentFrequency repaymentFrequency,
                             BigDecimal installmentAmount,
                             BigDecimal depositAmount,
                             String loanId,
                             String disbursementReference,
                             String disbursementNotes,
                             MultipartFile disbursementProofFile) {
        LoanApplication app = getDisbursementApplication(applicationId, disbursementOfficerId);
        if (app.getStatus() != LoanStatus.READY_FOR_DISBURSEMENT) {
            throw new IllegalStateException("Application is not ready for disbursement");
        }
        validateDisbursement(disbursementDate);
        BigDecimal effectiveDepositAmount = validateDepositAmount(depositAmount, app.getAmount());
        boolean hasUploadedProof = disbursementProofFile != null && !disbursementProofFile.isEmpty();
        if (isDisbursementProofRequired(app) && !hasUploadedProof && !hasDisbursementProofAttachment(app)) {
            throw new IllegalArgumentException("Disbursement proof file is required to disburse this loan");
        }
        String normalisedLoanId = blankToNull(loanId);
        if (normalisedLoanId == null) {
            throw new IllegalArgumentException("Loan ID is required to disburse this loan");
        }
        if (!LOAN_ID_PATTERN.matcher(normalisedLoanId).matches()) {
            throw new IllegalArgumentException("Loan ID must be 4-20 digits (numbers only)");
        }
        if (!normalisedLoanId.equals(app.getLoanId())
            && loanApplicationRepository.existsBySaccoIdAndLoanId(app.getSaccoId(), normalisedLoanId)) {
            throw new IllegalArgumentException("Loan ID is already used in this SACCO");
        }
        app.setDepositAmount(effectiveDepositAmount);
        app.setDisbursementDate(disbursementDate);
        app.setFirstRepaymentDate(null);
        app.setRepaymentFrequency(null);
        app.setInstallmentAmount(null);
        app.setFinalDueDate(null);
        app.setLoanId(normalisedLoanId);
        app.setDisbursementReference(blankToNull(disbursementReference));
        app.setDisbursementNotes(blankToNull(disbursementNotes));
        app.setRepaymentScheduleJson(null);
        if (hasUploadedProof) {
            app.setAttachmentsJson(loanAttachmentService.store(
                app.getId(),
                List.of(disbursementProofFile),
                app.getAttachmentsJson(),
                LoanAttachmentService.CATEGORY_DISBURSEMENT_PROOF
            ));
        }
        managerReviewRepository.save(ManagerReview.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(applicationId)
            .managerMemberId(disbursementOfficerId)
            .reviewStage(ApprovalWorkflowStage.DISBURSEMENT_OFFICER)
            .decision(ManagerDecision.ACCEPT)
            .reasons(blankToNull(disbursementNotes))
            .createdAt(OffsetDateTime.now())
            .build());
        app.setStatus(LoanStatus.DISBURSED);
        app.setUpdatedAt(OffsetDateTime.now());
        loanApplicationRepository.save(app);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("managerId", disbursementOfficerId.toString());
        details.put("finalDueDate", String.valueOf(app.getFinalDueDate()));
        details.put("firstRepaymentDate", String.valueOf(app.getFirstRepaymentDate()));
        details.put("repaymentFrequency", app.getRepaymentFrequency() == null ? "" : app.getRepaymentFrequency().name());
        details.put("installmentAmount", app.getInstallmentAmount());
        details.put("disbursementAmount", app.getAmount());
        details.put("depositAmount", app.getDepositAmount());
        details.put("applicationId", applicationId.toString());
        details.put("loanId", app.getLoanId());
        outboxService.enqueue("LOAN", applicationId, app.getStatus().name(), app.getApplicantMemberId(),
            disbursementOfficerId, app.getSaccoId(), app.getStationId(),
            details);
        auditLoan(app, disbursementOfficerId, "LOAN_DISBURSED", "Loan disbursed",
            Map.of("loanId", app.getLoanId(), "disbursementDate", String.valueOf(app.getDisbursementDate())));
    }

    public boolean matchesApplicantStation(LoanApplication loan, String stationId) {
        if (loan == null || stationId == null || stationId.isBlank()) {
            return true;
        }
        String loanStationId = blankToNull(loan.getStationId());
        return loanStationId != null && loanStationId.equalsIgnoreCase(stationId);
    }

    private void auditLoanReview(LoanApplication app,
                                 UUID actorId,
                                 ApprovalWorkflowStage stage,
                                 ManagerDecision decision) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reviewStage", stage == null ? null : stage.name());
        details.put("decision", decision == null ? null : decision.name());
        auditLoan(app, actorId, "STAFF_REVIEWED_LOAN_APPLICATION", "Staff reviewed loan application", details);
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

    public boolean isDisbursementProofRequired(LoanApplication app) {
        if (app == null || app.getLoanType() == null) {
            return true;
        }
        java.util.Optional<LoanProductSetting> product = app.getLoanProductSettingId() == null
            ? loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue(app.getSaccoId(), app.getLoanType())
            : loanProductSettingRepository.findByIdAndSaccoId(app.getLoanProductSettingId(), app.getSaccoId());
        return product
            .map(LoanProductSetting::isDisbursementProofRequired)
            .orElse(true);
    }

    private boolean hasDisbursementProofAttachment(LoanApplication app) {
        List<Map<String, Object>> attachments = loanAttachmentService.parse(app.getAttachmentsJson());
        if (attachments == null || attachments.isEmpty()) {
            return false;
        }
        return attachments.stream()
            .anyMatch(item -> LoanAttachmentService.CATEGORY_DISBURSEMENT_PROOF.equals(String.valueOf(item.get("attachmentCategory"))));
    }

    private LoanApplication getManagedApplication(UUID loanId, UUID managerId) {
        return getManagedApplication(loanId, managerId, UserClaim.MANAGER_QUEUE_VIEW);
    }

    private LoanApplication getManagedApplication(UUID loanId, UUID managerId, UserClaim requiredClaim) {
        LoanApplication app = loanApplicationRepository.findById(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        if (!roleDirectoryService.hasActiveClaimInSacco(managerId, app.getSaccoId(), requiredClaim)) {
            throw new IllegalArgumentException("Forbidden");
        }
        return app;
    }

    private LoanApplication getAccountantApplication(UUID loanId, UUID accountantId) {
        return getAccountantApplication(loanId, accountantId, UserClaim.ACCOUNTANT_QUEUE_VIEW);
    }

    private LoanApplication getAccountantApplication(UUID loanId, UUID accountantId, UserClaim requiredClaim) {
        LoanApplication app = loanApplicationRepository.findById(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        if (!roleDirectoryService.hasActiveClaimInSacco(accountantId, app.getSaccoId(), requiredClaim)) {
            throw new IllegalArgumentException("Forbidden");
        }
        return app;
    }

    private LoanApplication getDisbursementApplication(UUID loanId, UUID disbursementActorId) {
        LoanApplication app = loanApplicationRepository.findById(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        Member actor = memberRepository.findById(disbursementActorId)
            .orElseThrow(() -> new IllegalArgumentException("Forbidden"));
        if (actor.getStatus() != MemberStatus.ACTIVE
            || !app.getSaccoId().equals(actor.getSaccoId())
            || !matchesApplicantStation(app, actor.getStationId())
            || !roleDirectoryService.hasActiveClaimInSacco(disbursementActorId, app.getSaccoId(), UserClaim.DISBURSEMENT_QUEUE_DISBURSE)) {
            throw new IllegalArgumentException("Forbidden");
        }
        return app;
    }

    private UserClaim managerDecisionClaim(ManagerDecision decision) {
        return decision == ManagerDecision.REJECT
            ? UserClaim.MANAGER_QUEUE_REJECT
            : UserClaim.MANAGER_QUEUE_APPROVE;
    }

    private UserClaim accountantDecisionClaim(ManagerDecision decision) {
        return decision == ManagerDecision.REJECT
            ? UserClaim.ACCOUNTANT_QUEUE_REJECT
            : UserClaim.ACCOUNTANT_QUEUE_APPROVE;
    }

    private void validateDisbursement(LocalDate disbursementDate) {
        if (disbursementDate == null) {
            throw new IllegalArgumentException("Disbursement date is required for final approval");
        }
    }

    private BigDecimal validateDepositAmount(BigDecimal depositAmount, BigDecimal disbursementAmount) {
        BigDecimal principal = disbursementAmount == null ? BigDecimal.ZERO : disbursementAmount.setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal normalized = depositAmount == null ? principal : depositAmount.setScale(2, java.math.RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Deposit amount cannot be negative");
        }
        if (principal.compareTo(BigDecimal.ZERO) > 0 && normalized.compareTo(principal) > 0) {
            throw new IllegalArgumentException("Deposit amount cannot be greater than the approved loan amount");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional
    public SaccoSettings updateSettings(String saccoId, Integer requiredGuarantors, Integer boardSize,
                                        Integer boardQuorum, String language) {
        SaccoSettings settings = saccoSettingsRepository.findById(saccoId)
            .orElseThrow(() -> new IllegalArgumentException("Settings missing"));

        settings.setRequiredGuarantors(requiredGuarantors);
        settings.setBoardSize(boardSize);
        settings.setBoardQuorum(boardQuorum);
        settings.setDefaultLanguage(language);
        settings.setUpdatedAt(OffsetDateTime.now());
        return saccoSettingsRepository.save(settings);
    }

    public record StatusCount(LoanStatus status, long count) {}

    public record ManagerDashboard(
        long totalLoans,
        long totalDisbursedLoans,
        long activeDisbursedLoans,
        long defaultedLoansCurrentYear,
        long paidLoans,
        long onReviewByManagerLoans,
        long onReviewByBoardLoans,
        List<StatusCount> statusBreakdown,
        List<LoanApplication> recentDisbursements
    ) {}
}
