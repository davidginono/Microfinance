package com.sacco.mvp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanPaymentTransaction;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.integration.memberportal.LoanPaymentLookupException;
import com.sacco.mvp.integration.memberportal.LoanPaymentSummaryClient;
import com.sacco.mvp.integration.memberportal.LoanPaymentSummaryDto;
import com.sacco.mvp.integration.memberportal.LoanPaymentTransactionClient;
import com.sacco.mvp.integration.memberportal.LoanPaymentTransactionDto;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanPaymentTransactionRepository;
import com.sacco.mvp.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Reusable loan-payment sync logic. Called by both the monthly scheduler
 * (with a single target month) and by the manager-triggered manual refresh
 * (with a window spanning several past months).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanPaymentTransactionSyncService {

    private final LoanPaymentTransactionClient client;
    private final LoanPaymentSummaryClient loanPaymentSummaryClient;
    private final LoanPaymentTransactionRepository transactionRepository;
    private final MemberRepository memberRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    /**
     * Fetches every transaction the memberportal has for the loan and
     * upserts those whose {@code receiptDate} falls within {@code target}.
     *
     * @return number of rows inserted (existing duplicates are skipped).
     */
    @Transactional
    public int syncMonth(LoanApplication loan, YearMonth target) {
        return syncInternal(loan, transaction -> target.equals(YearMonth.from(transaction.receiptDate())));
    }

    /**
     * Upserts every transaction whose {@code receiptDate} is within the last
     * {@code monthsBack} calendar months (inclusive of the current month).
     * Used by the on-demand manager action to backfill history.
     */
    @Transactional
    public int syncRecent(LoanApplication loan, int monthsBack) {
        YearMonth cutoff = YearMonth.now().minusMonths(Math.max(monthsBack - 1, 0));
        return syncInternal(loan, transaction -> !YearMonth.from(transaction.receiptDate()).isBefore(cutoff));
    }

    private int syncInternal(LoanApplication loan, java.util.function.Predicate<LoanPaymentTransactionDto> filter) {
        if (loan.getLoanId() == null || loan.getLoanId().isBlank()) {
            log.debug("Skipping payment sync for application {} without a loan ID", loan.getId());
            return 0;
        }
        Optional<Member> applicantOpt = memberRepository.findById(loan.getApplicantMemberId());
        if (applicantOpt.isEmpty()) {
            log.warn("Skipping payment sync for application {}: applicant member {} not found",
                loan.getId(), loan.getApplicantMemberId());
            return 0;
        }
        Member applicant = applicantOpt.get();
        String loanStationId = normalizeStationId(loan.getStationId());
        if (applicant.getMemberNo() == null || applicant.getMemberNo().isBlank()
            || loanStationId == null) {
            log.warn("Skipping payment sync for application {}: member missing memberNo or stationId",
                loan.getId());
            return 0;
        }

        List<LoanPaymentTransactionDto> transactions = client.fetchTransactions(
            applicant.getMemberNo(), loanStationId, loan.getLoanId());

        int inserted = 0;
        OffsetDateTime fetchedAt = OffsetDateTime.now();
        boolean matchedTransactions = false;
        boolean matchedFinalInstallmentMonth = false;
        for (LoanPaymentTransactionDto dto : transactions) {
            if (dto == null || dto.receiptDate() == null || dto.totalPaid() == null) {
                continue;
            }
            if (dto.totalPaid().signum() <= 0) {
                continue;
            }
            if (!filter.test(dto)) {
                continue;
            }
            matchedTransactions = true;
            if (isFinalInstallmentMonth(loan, dto.receiptDate())) {
                matchedFinalInstallmentMonth = true;
            }
            boolean exists = transactionRepository
                .existsByLoanApplicationIdAndReceiptDateAndPrincipalPaidAndInterestPaidAndTotalPaid(
                    loan.getId(),
                    dto.receiptDate(),
                    dto.principalPaid(),
                    dto.interestPaid(),
                    dto.totalPaid());
            if (exists) {
                continue;
            }
            transactionRepository.save(LoanPaymentTransaction.builder()
                .id(UUID.randomUUID())
                .loanApplicationId(loan.getId())
                .saccoId(loan.getSaccoId())
                .externalLoanId(loan.getLoanId())
                .receiptDate(dto.receiptDate())
                .principalPaid(dto.principalPaid())
                .interestPaid(dto.interestPaid())
                .totalPaid(dto.totalPaid())
                .fetchedAt(fetchedAt)
                .build());
            inserted++;
        }
        if (matchedTransactions) {
            refreshLoanPaymentSummary(loan, applicant, fetchedAt, matchedFinalInstallmentMonth);
        }
        return inserted;
    }

    private boolean isFinalInstallmentMonth(LoanApplication loan, LocalDate receiptDate) {
        return loan.getFinalDueDate() != null
            && receiptDate != null
            && YearMonth.from(loan.getFinalDueDate()).equals(YearMonth.from(receiptDate));
    }

    private void refreshLoanPaymentSummary(LoanApplication loan,
                                           Member applicant,
                                           OffsetDateTime fetchedAt,
                                           boolean finalInstallmentMonthMatched) {
        try {
            Optional<LoanPaymentSummaryDto> summaryOpt = loanPaymentSummaryClient.fetchSummary(
                applicant.getMemberNo(), normalizeStationId(loan.getStationId()), loan.getLoanId());
            if (summaryOpt.isEmpty()) {
                return;
            }
            applySummary(loan, summaryOpt.get(), fetchedAt, finalInstallmentMonthMatched);
        } catch (LoanPaymentLookupException ex) {
            log.warn("Loan payment summary sync failed for applicationNumber={} loanId={}: {}",
                loan.getApplicationNumber(), loan.getLoanId(), ex.getMessage());
        }
    }

    private void applySummary(LoanApplication loan,
                              LoanPaymentSummaryDto summary,
                              OffsetDateTime fetchedAt,
                              boolean finalInstallmentMonthMatched) {
        loan.setLoanPaymentSummaryJson(writeSummaryJson(summary));
        loan.setLoanPaymentSummaryFetchedAt(fetchedAt);
        LocalDate today = fetchedAt.toLocalDate();

        if (shouldMarkPaid(loan, summary, finalInstallmentMonthMatched, today)) {
            loan.setStatus(LoanStatus.PAID);
            loan.setPaidAt(fetchedAt);
            loan.setPaidMarkedByManagerId(null);
            loan.setUpdatedAt(fetchedAt);
            loanApplicationRepository.save(loan);
            outboxService.enqueue("LOAN", loan.getId(), "PAID", loan.getApplicantMemberId(),
                Map.of(
                    "source", "SYNC",
                    "loanId", Objects.toString(loan.getLoanId(), ""),
                    "paidAt", fetchedAt.toString(),
                    "lastPaymentDate", Objects.toString(summary.lastPaymentDate(), "")
            ));
            return;
        }

        if (shouldMarkDefaulted(loan, summary, today)) {
            loan.setStatus(LoanStatus.DEFAULTED);
            loan.setUpdatedAt(fetchedAt);
            loanApplicationRepository.save(loan);
            outboxService.enqueue("LOAN", loan.getId(), "DEFAULTED", loan.getApplicantMemberId(),
                Map.of(
                    "source", "SYNC",
                    "loanId", Objects.toString(loan.getLoanId(), ""),
                    "finalDueDate", Objects.toString(loan.getFinalDueDate(), ""),
                    "totalOutstanding", Objects.toString(summary.totalOutstanding(), "")
                ));
            return;
        }

        loanApplicationRepository.save(loan);
    }

    private boolean shouldMarkPaid(LoanApplication loan,
                                   LoanPaymentSummaryDto summary,
                                   boolean finalInstallmentMonthMatched,
                                   LocalDate today) {
        if (loan.getStatus() == LoanStatus.PAID || !hasZeroOutstanding(summary.totalOutstanding())) {
            return false;
        }
        if (loan.getStatus() == LoanStatus.DEFAULTED) {
            return true;
        }
        return finalInstallmentMonthMatched || isPastFinalDueDate(loan, today);
    }

    private boolean shouldMarkDefaulted(LoanApplication loan,
                                        LoanPaymentSummaryDto summary,
                                        LocalDate today) {
        return loan.getStatus() == LoanStatus.FINAL_APPROVED
            && hasOutstanding(summary.totalOutstanding())
            && isPastFinalDueDate(loan, today);
    }

    private boolean isPastFinalDueDate(LoanApplication loan, LocalDate today) {
        return loan.getFinalDueDate() != null && loan.getFinalDueDate().isBefore(today);
    }

    private boolean hasZeroOutstanding(BigDecimal totalOutstanding) {
        return totalOutstanding != null && totalOutstanding.compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean hasOutstanding(BigDecimal totalOutstanding) {
        return totalOutstanding != null && totalOutstanding.compareTo(BigDecimal.ZERO) > 0;
    }

    private String writeSummaryJson(LoanPaymentSummaryDto summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to persist loan payment summary.", ex);
        }
    }

    /** Type alias for callers that don't want to import the exception package. */
    public static Class<LoanPaymentLookupException> lookupFailureType() {
        return LoanPaymentLookupException.class;
    }

    private String normalizeStationId(String stationId) {
        if (stationId == null) {
            return null;
        }
        String normalized = stationId.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
