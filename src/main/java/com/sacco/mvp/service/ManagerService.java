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
    private final RepaymentScheduleService repaymentScheduleService;
    private final RoleDirectoryService roleDirectoryService;
    private final LoanPaymentTransactionSyncService loanPaymentTransactionSyncService;
    private final LoanAttachmentService loanAttachmentService;
    private final WorkflowRoutingService workflowRoutingService;

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
            List.of(LoanStatus.DISBURSED, LoanStatus.DEFAULTED, LoanStatus.PAID),
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
            LoanStatus.DEFAULTED,
            LoanStatus.PAID
        )) {
            breakdown.add(new StatusCount(status, counts.getOrDefault(status, 0L)));
        }

        List<LoanApplication> recentDisbursements = loanApplicationRepository.findRecentDisbursementsForScope(
            saccoId,
            blankToNull(stationId),
            List.of(LoanStatus.DISBURSED, LoanStatus.DEFAULTED, LoanStatus.PAID),
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

    private boolean isDisbursedLoan(LoanApplication loan) {
        return loan.getStatus() == LoanStatus.DISBURSED
            || loan.getStatus() == LoanStatus.DEFAULTED
            || loan.getStatus() == LoanStatus.PAID;
    }

    private LocalDate dashboardDisbursementDate(LoanApplication loan) {
        if (loan.getDisbursementDate() != null) {
            return loan.getDisbursementDate();
        }
        return loan.getCreatedAt() == null ? null : loan.getCreatedAt().toLocalDate();
    }

    private LocalDate dashboardDefaultedDate(LoanApplication loan) {
        if (loan.getUpdatedAt() != null) {
            return loan.getUpdatedAt().toLocalDate();
        }
        if (loan.getFinalDueDate() != null) {
            return loan.getFinalDueDate();
        }
        return loan.getCreatedAt() == null ? null : loan.getCreatedAt().toLocalDate();
    }

    public List<LoanApplication> queue(String saccoId, LoanStatus status) {
        if (status == null) {
            return loanApplicationRepository.findBySaccoIdAndStatusInOrderByCreatedAtAsc(
                saccoId, List.of(LoanStatus.READY_FOR_MANAGER));
        }
        return loanApplicationRepository.findBySaccoIdAndStatusOrderByCreatedAtAsc(saccoId, status);
    }

    public List<LoanApplication> queue(String saccoId, List<LoanStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return queue(saccoId, LoanStatus.READY_FOR_MANAGER);
        }
        if (statuses.size() == 1) {
            return queue(saccoId, statuses.get(0));
        }
        return loanApplicationRepository.findBySaccoIdAndStatusInOrderByCreatedAtAsc(saccoId, statuses);
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
            return loanApplicationRepository.findQueuePageForScope(
                saccoId,
                blankToNull(stationId),
                resolvedStatuses,
                PageRequest.of(0, MAX_QUEUE_ROWS)
            ).getContent();
        }
        return loanApplicationRepository.findQueuePage(
            saccoId,
            blankToNull(stationId),
            resolvedStatuses,
            normalizedSearchTerm == null ? null : normalizedSearchTerm.toLowerCase(Locale.ENGLISH),
            searchByLoanId,
            PageRequest.of(0, MAX_QUEUE_ROWS)
        ).getContent();
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
                applicantMemberId, List.of(LoanStatus.DISBURSED, LoanStatus.DEFAULTED)).stream()
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

    @Transactional
    public void decide(UUID loanId, UUID managerId, ManagerDecision decision, String reasons) {
        LoanApplication app = getManagedApplication(loanId, managerId);
        if (app.getStatus() != LoanStatus.READY_FOR_MANAGER) {
            throw new IllegalStateException("Application is not ready for manager review");
        }
        String normalizedReasons = normalizeDecisionReasons(reasons);
        if (decision == ManagerDecision.REJECT && normalizedReasons.isBlank()) {
            throw new IllegalStateException("Add a reason before rejecting this loan application.");
        }
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
            .createdAt(OffsetDateTime.now())
            .build());

        if (decision == ManagerDecision.REJECT) {
            app.setStatus(LoanStatus.MANAGER_REJECTED);
            app.setUpdatedAt(OffsetDateTime.now());
            loanApplicationRepository.save(app);
            outboxService.enqueue("LOAN", loanId, "MANAGER_REJECTED", app.getApplicantMemberId(),
                app.getSaccoId(), app.getStationId(),
                Map.of("reasons", normalizedReasons));
            return;
        }

        workflowRoutingService.advanceAfterApproval(app, ApprovalWorkflowStage.MANAGER, managerId);
        loanApplicationRepository.save(app);
    }

    @Transactional
    public void decideAccountant(UUID loanId, UUID accountantId, ManagerDecision decision, String reasons) {
        LoanApplication app = getAccountantApplication(loanId, accountantId);
        if (app.getStatus() != LoanStatus.AWAITING_ACCOUNTANT) {
            throw new IllegalStateException("Application is not ready for accountant review");
        }
        String normalizedReasons = normalizeDecisionReasons(reasons);
        if (decision == ManagerDecision.REJECT && normalizedReasons.isBlank()) {
            throw new IllegalStateException("Add a reason before rejecting this loan application.");
        }
        managerReviewRepository.save(ManagerReview.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(loanId)
            .managerMemberId(accountantId)
            .reviewStage(ApprovalWorkflowStage.ACCOUNTANT)
            .decision(decision)
            .reasons(normalizedReasons)
            .createdAt(OffsetDateTime.now())
            .build());
        if (decision == ManagerDecision.REJECT) {
            app.setStatus(LoanStatus.ACCOUNTANT_REJECTED);
            app.setUpdatedAt(OffsetDateTime.now());
            loanApplicationRepository.save(app);
            outboxService.enqueue("LOAN", loanId, "ACCOUNTANT_REJECTED", app.getApplicantMemberId(),
                app.getSaccoId(), app.getStationId(),
                Map.of("reasons", normalizedReasons));
            return;
        }
        workflowRoutingService.advanceAfterApproval(app, ApprovalWorkflowStage.ACCOUNTANT, accountantId);
        loanApplicationRepository.save(app);
    }

    private String normalizeDecisionReasons(String reasons) {
        return reasons == null ? "" : reasons.trim();
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
        RepaymentFrequency effectiveFrequency = repaymentFrequency == null ? RepaymentFrequency.MONTHLY : repaymentFrequency;
        validateDisbursement(disbursementDate, firstRepaymentDate);
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
        RepaymentScheduleService.ScheduleResult schedule = repaymentScheduleService.buildSchedule(
            app,
            disbursementDate,
            firstRepaymentDate,
            effectiveFrequency,
            installmentAmount,
            disbursementReference,
            disbursementNotes
        );
        app.setDisbursementDate(disbursementDate);
        app.setFirstRepaymentDate(firstRepaymentDate);
        app.setRepaymentFrequency(effectiveFrequency);
        app.setInstallmentAmount(schedule.installmentAmount());
        app.setFinalDueDate(schedule.finalDueDate());
        app.setLoanId(normalisedLoanId);
        app.setDisbursementReference(blankToNull(disbursementReference));
        app.setDisbursementNotes(blankToNull(disbursementNotes));
        app.setRepaymentScheduleJson(schedule.scheduleJson());
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
            app.getSaccoId(), app.getStationId(),
            details);
    }

    /**
     * Manager-triggered on-demand refresh of payment transactions for the last
     * {@code monthsBack} calendar months. Returns the number of newly inserted
     * rows so the caller can surface it as a flash message.
     */
    public int syncLoanPayments(UUID applicationId, UUID managerId, int monthsBack) {
        LoanApplication app = getManagedApplication(applicationId, managerId);
        if (app.getLoanId() == null || app.getLoanId().isBlank()) {
            throw new IllegalStateException("This loan has not been disbursed yet.");
        }
        return loanPaymentTransactionSyncService.syncRecentAndRefreshSummary(app, monthsBack);
    }

    public DefaultedLoanRecheckResult recheckDefaultedLoanPaymentStatus(UUID applicationId, UUID managerId, int monthsBack) {
        LoanApplication app = getManagedApplication(applicationId, managerId);
        if (app.getStatus() != LoanStatus.DEFAULTED) {
            throw new IllegalStateException("Only defaulted loans can be rechecked with this action.");
        }
        if (app.getLoanId() == null || app.getLoanId().isBlank()) {
            throw new IllegalStateException("This defaulted loan does not have a loan ID to verify.");
        }
        int inserted = loanPaymentTransactionSyncService.syncRecentAndRefreshSummary(app, monthsBack);
        return new DefaultedLoanRecheckResult(inserted, app.getStatus() == LoanStatus.PAID, app.getStatus());
    }

    public record DefaultedLoanRecheckResult(int insertedTransactions, boolean paid, LoanStatus status) {
    }

    public boolean matchesApplicantStation(LoanApplication loan, String stationId) {
        if (loan == null || stationId == null || stationId.isBlank()) {
            return true;
        }
        String loanStationId = blankToNull(loan.getStationId());
        return loanStationId != null && loanStationId.equalsIgnoreCase(stationId);
    }

    public boolean isDisbursementProofRequired(LoanApplication app) {
        if (app == null || app.getLoanType() == null) {
            return true;
        }
        return loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue(app.getSaccoId(), app.getLoanType())
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
        LoanApplication app = loanApplicationRepository.findById(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        if (!roleDirectoryService.hasActiveRoleInSacco(managerId, app.getSaccoId(), Position.MANAGER)) {
            throw new IllegalArgumentException("Forbidden");
        }
        return app;
    }

    private LoanApplication getAccountantApplication(UUID loanId, UUID accountantId) {
        LoanApplication app = loanApplicationRepository.findById(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        if (!roleDirectoryService.hasActiveRoleInSacco(accountantId, app.getSaccoId(), Position.ACCOUNTANT)) {
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
            || !matchesApplicantStation(app, actor.getStationId())) {
            throw new IllegalArgumentException("Forbidden");
        }
        return app;
    }

    private void validateDisbursement(LocalDate disbursementDate,
                                      LocalDate firstRepaymentDate) {
        if (disbursementDate == null) {
            throw new IllegalArgumentException("Disbursement date is required for final approval");
        }
        if (firstRepaymentDate == null) {
            throw new IllegalArgumentException("First repayment date is required for final approval");
        }
        if (firstRepaymentDate.isBefore(disbursementDate)) {
            throw new IllegalArgumentException("First repayment date cannot be before disbursement date");
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

    private boolean matchesQueueSearch(LoanApplication app, String lookup, boolean searchByLoanId) {
        if (searchByLoanId) {
            String loanId = blankToNull(app.getLoanId());
            return loanId != null && loanId.toLowerCase(Locale.ENGLISH).contains(lookup);
        }
        Long applicationNumber = app.getApplicationNumber();
        return applicationNumber != null && String.valueOf(applicationNumber).contains(lookup);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<LoanApplication> filterLoansByApplicantStation(List<LoanApplication> loans, String stationId) {
        if (stationId == null || stationId.isBlank() || loans.isEmpty()) {
            return loans;
        }
        return loans.stream()
            .filter(loan -> stationId.equalsIgnoreCase(blankToNull(loan.getStationId())))
            .toList();
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
