package com.sacco.mvp.service;

import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.domain.SaccoStationPolicy;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LoanPortfolioRiskStatusService {
    private static final List<LoanStatus> CANDIDATE_STATUSES = List.of(
        LoanStatus.DISBURSED,
        LoanStatus.PAR,
        LoanStatus.DEFAULTED
    );

    private final LoanApplicationRepository loanApplicationRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final SaccoStationPolicyRepository saccoStationPolicyRepository;
    private final RepaymentScheduleService repaymentScheduleService;
    private final OutboxService outboxService;
    private final AuditService auditService;
    private final ApplicationClock applicationClock;
    private final ObjectMapper objectMapper;
    private final LoanRepaymentLedgerService loanRepaymentLedgerService;

    @Transactional
    public PortfolioRiskStatusResult reevaluatePortfolioRiskStatuses(int batchSize) {
        int safeBatchSize = batchSize <= 0 ? 100 : Math.min(batchSize, 500);
        Sort riskCandidateSort = Sort.by(Sort.Order.asc("id"));
        PageRequest pageRequest = PageRequest.of(0, safeBatchSize, riskCandidateSort);
        int changed = 0;
        int paid = 0;
        int par = 0;
        int defaulted = 0;
        int reverted = 0;
        int skipped = 0;
        while (true) {
            Page<LoanApplication> page = loanApplicationRepository.findByStatusInAndLoanIdIsNotNull(
                CANDIDATE_STATUSES,
                pageRequest
            );
            int changedOnPage = 0;
            for (LoanApplication loan : page.getContent()) {
                StatusDecision decision = decideStatus(loan, applicationClock.today());
                if (decision.skipReason() != null) {
                    skipped++;
                    continue;
                }
                LoanStatus nextStatus = decision.status();
                if (nextStatus == null || nextStatus == loan.getStatus()) {
                    continue;
                }
                LoanStatus previous = loan.getStatus();
                applyStatus(loan, nextStatus);
                changed++;
                if (nextStatus == LoanStatus.PAID) {
                    paid++;
                } else if (nextStatus == LoanStatus.PAR) {
                    par++;
                } else if (nextStatus == LoanStatus.DEFAULTED) {
                    defaulted++;
                } else if (nextStatus == LoanStatus.DISBURSED && previous == LoanStatus.PAR) {
                    reverted++;
                }
                emitStatusChanged(loan, previous, nextStatus, decision);
                changedOnPage++;
            }
            if (changedOnPage > 0) {
                pageRequest = PageRequest.of(0, safeBatchSize, riskCandidateSort);
                continue;
            }
            if (!page.hasNext()) {
                break;
            }
            pageRequest = PageRequest.of(
                page.nextPageable().getPageNumber(),
                safeBatchSize,
                riskCandidateSort
            );
        }
        return new PortfolioRiskStatusResult(changed, paid, par, defaulted, reverted, skipped);
    }

    StatusDecision decideStatus(LoanApplication loan, LocalDate today) {
        if (loan == null) {
            return StatusDecision.skipped("Loan is missing.");
        }
        Optional<LoanRepaymentLedgerService.RiskBalance> ledgerBalance = loanRepaymentLedgerService.riskBalance(loan.getId(), today);
        if (ledgerBalance.isPresent()) {
            LoanRepaymentLedgerService.RiskBalance balance = ledgerBalance.get();
            int riskDays = resolvedPortfolioAtRiskDays(loan);
            LoanStatus status = balance.contractualRemaining().signum() == 0 ? LoanStatus.PAID
                : loan.getStatus() == LoanStatus.DEFAULTED ? LoanStatus.DEFAULTED
                : balance.oldestOverdue() == null ? LoanStatus.DISBURSED
                : today.isAfter(balance.oldestOverdue().plusDays(riskDays)) ? LoanStatus.DEFAULTED : LoanStatus.PAR;
            return StatusDecision.to(status, balance.oldestOverdue(), balance.currentOutstanding(), riskDays);
        }
        BigDecimal outstanding = syncedOutstandingBalance(loan);
        if (outstanding == null) {
            return StatusDecision.skipped("Synced outstanding balance is missing.");
        }
        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            return StatusDecision.to(LoanStatus.PAID, null, outstanding, resolvedPortfolioAtRiskDays(loan));
        }
        if (loan.getStatus() == LoanStatus.DEFAULTED) {
            return StatusDecision.to(LoanStatus.DEFAULTED, null, outstanding, resolvedPortfolioAtRiskDays(loan));
        }
        LocalDate dueDate = firstUnpaidDueDate(loan, outstanding).orElse(loan.getFinalDueDate());
        if (dueDate == null) {
            return StatusDecision.skipped("Repayment due date is missing.");
        }
        int portfolioAtRiskDays = resolvedPortfolioAtRiskDays(loan);
        if (today.isAfter(dueDate.plusDays(portfolioAtRiskDays))) {
            return StatusDecision.to(LoanStatus.DEFAULTED, dueDate, outstanding, portfolioAtRiskDays);
        }
        if (today.isAfter(dueDate)) {
            return StatusDecision.to(LoanStatus.PAR, dueDate, outstanding, portfolioAtRiskDays);
        }
        return StatusDecision.to(LoanStatus.DISBURSED, dueDate, outstanding, portfolioAtRiskDays);
    }

    private void applyStatus(LoanApplication loan, LoanStatus nextStatus) {
        loan.setStatus(nextStatus);
        if (nextStatus == LoanStatus.PAID && loan.getPaidAt() == null) {
            loan.setPaidAt(applicationClock.now());
        }
        if (nextStatus != LoanStatus.PAID) {
            loan.setPaidAt(null);
        }
        loan.setUpdatedAt(applicationClock.now());
        loanApplicationRepository.save(loan);
    }

    private void emitStatusChanged(LoanApplication loan,
                                   LoanStatus previousStatus,
                                   LoanStatus nextStatus,
                                   StatusDecision decision) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("loanId", safeLoanReference(loan));
        details.put("previousStatus", previousStatus == null ? "" : previousStatus.name());
        details.put("newStatus", nextStatus == null ? "" : nextStatus.name());
        details.put("firstUnpaidDueDate", decision.firstUnpaidDueDate() == null ? "" : decision.firstUnpaidDueDate().toString());
        details.put("portfolioAtRiskDays", decision.portfolioAtRiskDays());
        details.put("totalOutstanding", decision.totalOutstanding());

        outboxService.enqueue(
            "LOAN_APPLICATION",
            loan.getId(),
            "LOAN_STATUS_CHANGED",
            loan.getApplicantMemberId(),
            null,
            loan.getSaccoId(),
            loan.getStationId(),
            details
        );
        auditService.logEvent(
            "LOAN_APPLICATION",
            loan.getId(),
            "LOAN_STATUS_CHANGED",
            null,
            AuditEventStatus.SUCCESS,
            "Loan status updated",
            "LOAN_APPLICATION",
            safeLoanReference(loan),
            loan.getSaccoId(),
            loan.getStationId(),
            details
        );
    }

    private Optional<LocalDate> firstUnpaidDueDate(LoanApplication loan, BigDecimal outstanding) {
        List<Map<String, Object>> rows = repaymentScheduleService.parseRows(loan.getRepaymentScheduleJson());
        if (rows.isEmpty()) {
            return Optional.ofNullable(loan.getFinalDueDate());
        }
        BigDecimal paidTotal = paidTotalFromSnapshot(loan).orElseGet(() -> {
            BigDecimal totalScheduled = rows.stream()
                .map(row -> readBigDecimal(row.get("amount")))
                .map(value -> value == null ? BigDecimal.ZERO : value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return totalScheduled.subtract(outstanding).max(BigDecimal.ZERO);
        });
        BigDecimal runningScheduled = BigDecimal.ZERO;
        LocalDate lastDueDate = null;
        for (Map<String, Object> row : rows) {
            LocalDate dueDate = readDate(row.get("dueDate"));
            if (dueDate != null) {
                lastDueDate = dueDate;
            }
            BigDecimal amount = readBigDecimal(row.get("amount"));
            runningScheduled = runningScheduled.add(amount == null ? BigDecimal.ZERO : amount);
            if (runningScheduled.compareTo(paidTotal) > 0 && dueDate != null) {
                return Optional.of(dueDate);
            }
        }
        return Optional.ofNullable(lastDueDate).or(() -> Optional.ofNullable(loan.getFinalDueDate()));
    }

    private Optional<BigDecimal> paidTotalFromSnapshot(LoanApplication loan) {
        Map<String, Object> snapshot = financialSnapshot(loan);
        BigDecimal principalPaid = readBigDecimal(snapshot.get(LoanFinancialSnapshotKeys.PAYMENT_SUMMARY_TOTAL_PRINCIPAL_PAID));
        BigDecimal interestPaid = readBigDecimal(snapshot.get(LoanFinancialSnapshotKeys.PAYMENT_SUMMARY_TOTAL_INTEREST_PAID));
        if (principalPaid == null && interestPaid == null) {
            return Optional.empty();
        }
        return Optional.of((principalPaid == null ? BigDecimal.ZERO : principalPaid)
            .add(interestPaid == null ? BigDecimal.ZERO : interestPaid)
            .setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal syncedOutstandingBalance(LoanApplication loan) {
        return readBigDecimal(financialSnapshot(loan).get(LoanFinancialSnapshotKeys.PAYMENT_SUMMARY_TOTAL_OUTSTANDING));
    }

    private Map<String, Object> financialSnapshot(LoanApplication loan) {
        if (loan == null || loan.getFinancialSnapshot() == null || loan.getFinancialSnapshot().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(loan.getFinancialSnapshot(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private int resolvedPortfolioAtRiskDays(LoanApplication loan) {
        SaccoSettings settings = loan == null || loan.getSaccoId() == null
            ? null
            : saccoSettingsRepository.findById(loan.getSaccoId()).orElse(null);
        SaccoStationPolicy stationPolicy = settings == null || loan.getStationId() == null || loan.getStationId().isBlank()
            ? null
            : saccoStationPolicyRepository.findBySaccoIdAndStationId(settings.getSaccoId(), loan.getStationId()).orElse(null);
        Integer value = stationPolicy == null || stationPolicy.getPortfolioAtRiskDays() == null
            ? settings == null ? null : settings.getPortfolioAtRiskDays()
            : stationPolicy.getPortfolioAtRiskDays();
        if (value == null) {
            return 30;
        }
        return Math.max(1, Math.min(365, value));
    }

    private BigDecimal readBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate readDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String safeLoanReference(LoanApplication loan) {
        if (loan == null) {
            return "";
        }
        if (loan.getLoanId() != null && !loan.getLoanId().isBlank()) {
            return loan.getLoanId().trim();
        }
        return loan.getApplicationNumber() == null ? loan.getId().toString() : String.valueOf(loan.getApplicationNumber());
    }

    record StatusDecision(
        LoanStatus status,
        LocalDate firstUnpaidDueDate,
        BigDecimal totalOutstanding,
        int portfolioAtRiskDays,
        String skipReason
    ) {
        static StatusDecision to(LoanStatus status,
                                 LocalDate firstUnpaidDueDate,
                                 BigDecimal totalOutstanding,
                                 int portfolioAtRiskDays) {
            return new StatusDecision(status, firstUnpaidDueDate, totalOutstanding, portfolioAtRiskDays, null);
        }

        static StatusDecision skipped(String reason) {
            return new StatusDecision(null, null, null, 30, reason);
        }
    }

    public record PortfolioRiskStatusResult(
        int changed,
        int paid,
        int par,
        int defaulted,
        int reverted,
        int skipped
    ) {
    }
}
