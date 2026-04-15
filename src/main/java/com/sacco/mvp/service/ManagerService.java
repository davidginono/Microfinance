package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ManagerService {
    private static final long REVERSAL_WINDOW_HOURS = 24L;
    private final LoanApplicationRepository loanApplicationRepository;
    private final ManagerReviewRepository managerReviewRepository;
    private final BoardReviewRepository boardReviewRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final MemberRepository memberRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final OutboxService outboxService;
    private final RepaymentScheduleService repaymentScheduleService;
    private final RoleDirectoryService roleDirectoryService;

    public ManagerDashboard dashboard(String saccoId) {
        List<LoanApplication> loans = loanApplicationRepository.findBySaccoIdOrderByCreatedAtDesc(saccoId);
        LocalDate today = LocalDate.now();
        LocalDate currentYearStart = today.withDayOfYear(1);
        LocalDate recentCutoff = today.minusDays(30);
        Map<LoanStatus, Long> counts = new EnumMap<>(LoanStatus.class);
        for (LoanStatus status : LoanStatus.values()) {
            counts.put(status, 0L);
        }
        for (LoanApplication loan : loans) {
            counts.computeIfPresent(loan.getStatus(), (ignored, count) -> count + 1);
        }

        long totalDisbursed = loans.stream()
            .filter(this::isDisbursedLoan)
            .map(this::dashboardDisbursementDate)
            .filter(java.util.Objects::nonNull)
            .filter(date -> !date.isBefore(currentYearStart))
            .count();

        List<StatusCount> breakdown = new ArrayList<>();
        for (LoanStatus status : List.of(
            LoanStatus.DRAFT,
            LoanStatus.AWAITING_GUARANTORS,
            LoanStatus.ALL_GUARANTORS_APPROVED,
            LoanStatus.READY_FOR_MANAGER,
            LoanStatus.AWAITING_BOARD,
            LoanStatus.BOARD_APPROVED,
            LoanStatus.MANAGER_REJECTED,
            LoanStatus.BOARD_REJECTED,
            LoanStatus.FINAL_REJECTED,
            LoanStatus.FINAL_APPROVED,
            LoanStatus.PAID
        )) {
            breakdown.add(new StatusCount(status, counts.getOrDefault(status, 0L)));
        }

        List<LoanApplication> recentDisbursements = loans.stream()
            .filter(this::isDisbursedLoan)
            .filter(loan -> {
                LocalDate effectiveDate = dashboardDisbursementDate(loan);
                return effectiveDate != null && !effectiveDate.isBefore(recentCutoff);
            })
            .sorted(Comparator.comparing(
                this::dashboardDisbursementDate
            ).reversed())
            .limit(5)
            .toList();

        return new ManagerDashboard(
            loans.size(),
            totalDisbursed,
            counts.getOrDefault(LoanStatus.FINAL_APPROVED, 0L),
            counts.getOrDefault(LoanStatus.PAID, 0L),
            counts.getOrDefault(LoanStatus.READY_FOR_MANAGER, 0L),
            counts.getOrDefault(LoanStatus.AWAITING_BOARD, 0L),
            breakdown,
            recentDisbursements
        );
    }

    private boolean isDisbursedLoan(LoanApplication loan) {
        return loan.getStatus() == LoanStatus.FINAL_APPROVED || loan.getStatus() == LoanStatus.PAID;
    }

    private LocalDate dashboardDisbursementDate(LoanApplication loan) {
        if (loan.getDisbursementDate() != null) {
            return loan.getDisbursementDate();
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

    public LoanApplication get(UUID id, String saccoId) {
        LoanApplication app = loanApplicationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        if (!app.getSaccoId().equals(saccoId)) {
            throw new IllegalArgumentException("Forbidden");
        }
        return app;
    }

    public List<LoanApplication> activeApplicantLoans(UUID applicantMemberId, UUID excludeLoanId, String saccoId) {
        return loanApplicationRepository.findByApplicantMemberIdAndStatusOrderByCreatedAtDesc(
                applicantMemberId, LoanStatus.FINAL_APPROVED).stream()
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
            .decision(decision)
            .reasons(reasons)
            .createdAt(OffsetDateTime.now())
            .build());

        if (decision == ManagerDecision.REJECT) {
            app.setStatus(LoanStatus.MANAGER_REJECTED);
            app.setUpdatedAt(OffsetDateTime.now());
            loanApplicationRepository.save(app);
            outboxService.enqueue("LOAN", loanId, "MANAGER_REJECTED", app.getApplicantMemberId(),
                Map.of("reasons", reasons == null ? "" : reasons));
            return;
        }

        app.setStatus(LoanStatus.MANAGER_ACCEPTED);
        loanApplicationRepository.save(app);

        SaccoSettings settings = saccoSettingsRepository.findById(app.getSaccoId())
            .orElseThrow(() -> new IllegalArgumentException("SACCO settings missing"));
        int requiredBoardReviewers = settings.getBoardQuorum() == null || settings.getBoardQuorum() <= 0
            ? Math.max(settings.getBoardSize() == null ? 0 : settings.getBoardSize(), 1)
            : settings.getBoardQuorum();

        List<RoleDirectoryService.RoleAccountRef> boardMembers = roleDirectoryService.activeByRole(app.getSaccoId(), Position.BOARD);
        if (boardMembers.size() < requiredBoardReviewers) {
            throw new IllegalStateException("Not enough board members");
        }

        // If the workflow was returned to manager review after an earlier board assignment,
        // stale board review rows may still exist. Reset them before creating the fresh queue.
        if (!boardReviewRepository.findByLoanApplicationId(loanId).isEmpty()) {
            boardReviewRepository.deleteByLoanApplicationId(loanId);
        }

        boardMembers.stream().limit(requiredBoardReviewers).forEach(board -> {
            boardReviewRepository.save(BoardReview.builder()
                .id(UUID.randomUUID())
                .loanApplicationId(loanId)
                .boardMemberId(board.getId())
                .decision(BoardDecision.PENDING)
                .createdAt(OffsetDateTime.now())
                .build());

            outboxService.enqueue("BOARD", loanId, "BOARD_REVIEW_ASSIGNED", board.getId(),
                Map.of("loanId", loanId.toString()));
        });

        app.setStatus(LoanStatus.AWAITING_BOARD);
        app.setUpdatedAt(OffsetDateTime.now());
        loanApplicationRepository.save(app);
    }

    @Transactional
    public void finalizeDecision(UUID loanId,
                                 String decision,
                                 UUID managerId,
                                 LocalDate disbursementDate,
                                 LocalDate firstRepaymentDate,
                                 RepaymentFrequency repaymentFrequency,
                                 BigDecimal installmentAmount,
                                 String disbursementReference,
                                 String disbursementNotes) {
        LoanApplication app = getManagedApplication(loanId, managerId);

        if ("FINAL_APPROVE".equals(decision) && app.getStatus() == LoanStatus.BOARD_APPROVED) {
            RepaymentFrequency effectiveFrequency = repaymentFrequency == null ? RepaymentFrequency.MONTHLY : repaymentFrequency;
            validateDisbursement(disbursementDate, firstRepaymentDate);
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
            app.setDisbursementReference(blankToNull(disbursementReference));
            app.setDisbursementNotes(blankToNull(disbursementNotes));
            app.setRepaymentScheduleJson(schedule.scheduleJson());
            app.setStatus(LoanStatus.FINAL_APPROVED);
        } else if ("FINAL_REJECT".equals(decision) && app.getStatus() == LoanStatus.BOARD_REJECTED) {
            app.setStatus(LoanStatus.FINAL_REJECTED);
        } else {
            throw new IllegalStateException("Invalid finalization state");
        }

        app.setUpdatedAt(OffsetDateTime.now());
        loanApplicationRepository.save(app);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("managerId", managerId.toString());
        if (app.getStatus() == LoanStatus.FINAL_APPROVED) {
            details.put("finalDueDate", String.valueOf(app.getFinalDueDate()));
            details.put("firstRepaymentDate", String.valueOf(app.getFirstRepaymentDate()));
            details.put("repaymentFrequency", app.getRepaymentFrequency() == null ? "" : app.getRepaymentFrequency().name());
            details.put("installmentAmount", app.getInstallmentAmount());
        }
        outboxService.enqueue("LOAN", loanId, app.getStatus().name(), app.getApplicantMemberId(),
            details);
    }

    @Transactional
    public void markPaid(UUID loanId, UUID managerId, boolean paid) {
        LoanApplication app = getManagedApplication(loanId, managerId);

        if (paid) {
            if (app.getStatus() != LoanStatus.FINAL_APPROVED) {
                throw new IllegalStateException("Only disbursed loans can be marked as paid");
            }
            app.setStatus(LoanStatus.PAID);
            app.setPaidAt(OffsetDateTime.now());
            app.setPaidMarkedByManagerId(managerId);
            app.setUpdatedAt(OffsetDateTime.now());
            loanApplicationRepository.save(app);
            outboxService.enqueue("LOAN", loanId, "PAID", app.getApplicantMemberId(),
                Map.of("managerId", managerId.toString(), "paidAt", app.getPaidAt().toString()));
            return;
        }

        if (app.getStatus() != LoanStatus.PAID) {
            throw new IllegalStateException("Only paid loans can be moved back to disbursed");
        }
        if (!isWithinReversalWindow(app.getPaidAt())) {
            throw new IllegalStateException("The 24-hour reversal window for this paid status has already closed.");
        }
        app.setStatus(LoanStatus.FINAL_APPROVED);
        app.setPaidAt(null);
        app.setPaidMarkedByManagerId(null);
        app.setUpdatedAt(OffsetDateTime.now());
        loanApplicationRepository.save(app);
    }

    @Transactional
    public void undoDecision(UUID loanId, UUID managerId) {
        LoanApplication app = getManagedApplication(loanId, managerId);
        ManagerReview latestReview = managerReviewRepository.findFirstByLoanApplicationIdOrderByCreatedAtDesc(loanId)
            .orElse(null);

        if (app.getStatus() == LoanStatus.MANAGER_REJECTED) {
            if (latestReview == null || !isWithinReversalWindow(latestReview.getCreatedAt())) {
                throw new IllegalStateException("The 24-hour reversal window for this manager action has already closed.");
            }
            app.setStatus(LoanStatus.READY_FOR_MANAGER);
            app.setUpdatedAt(OffsetDateTime.now());
            loanApplicationRepository.save(app);
            return;
        }

        throw new IllegalStateException("Manager actions cannot be reversed after the application leaves manager review.");
    }

    private LoanApplication getManagedApplication(UUID loanId, UUID managerId) {
        LoanApplication app = loanApplicationRepository.findById(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        if (!roleDirectoryService.hasActiveRoleInSacco(managerId, app.getSaccoId(), Position.MANAGER)) {
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isWithinReversalWindow(OffsetDateTime referenceAt) {
        return referenceAt != null && referenceAt.plusHours(REVERSAL_WINDOW_HOURS).isAfter(OffsetDateTime.now());
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
        long paidLoans,
        long onReviewByManagerLoans,
        long onReviewByBoardLoans,
        List<StatusCount> statusBreakdown,
        List<LoanApplication> recentDisbursements
    ) {}
}

