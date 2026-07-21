package com.sacco.mvp.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.AuditEventStatus;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Reconciles the complete immutable payment history returned by memberportal.
 * The summary endpoint supplies the latest balances, which are rolled backward
 * across the stored payment records.
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
    private final AuditService auditService;

    @Transactional
    public int syncAllAndRefreshSummary(LoanApplication loan) {
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

        OffsetDateTime fetchedAt = OffsetDateTime.now();
        List<LoanPaymentTransaction> storedTransactions = new ArrayList<>(transactionRepository
            .findByLoanApplicationIdOrderByReceiptDateAscProviderOrderDesc(loan.getId()));
        Map<PaymentIdentity, LoanPaymentTransaction> storedByIdentity = new HashMap<>();
        for (LoanPaymentTransaction stored : storedTransactions) {
            storedByIdentity.put(paymentIdentity(stored), stored);
        }

        Map<PaymentSignature, Integer> occurrences = new LinkedHashMap<>();
        List<LoanPaymentTransaction> reconciledTransactions = new ArrayList<>();
        int inserted = 0;
        int providerOrder = 0;
        boolean matchedFinalInstallmentMonth = false;
        for (LoanPaymentTransactionDto dto : transactions) {
            if (!isValidPayment(dto)) {
                continue;
            }
            PaymentSignature signature = paymentSignature(dto);
            int duplicateOccurrence = occurrences.getOrDefault(signature, 0);
            occurrences.put(signature, duplicateOccurrence + 1);
            PaymentIdentity identity = new PaymentIdentity(signature, duplicateOccurrence);
            LoanPaymentTransaction transaction = storedByIdentity.remove(identity);
            if (transaction == null) {
                transaction = LoanPaymentTransaction.builder()
                    .id(UUID.randomUUID())
                    .loanApplicationId(loan.getId())
                    .saccoId(loan.getSaccoId())
                    .externalLoanId(loan.getLoanId())
                    .receiptDate(dto.receiptDate())
                    .principalPaid(dto.principalPaid())
                    .interestPaid(dto.interestPaid())
                    .totalPaid(dto.totalPaid())
                    .duplicateOccurrence(duplicateOccurrence)
                    .build();
                storedTransactions.add(transaction);
                inserted++;
            }
            transaction.setSaccoId(loan.getSaccoId());
            transaction.setExternalLoanId(loan.getLoanId());
            transaction.setProviderOrder(providerOrder++);
            transaction.setFetchedAt(fetchedAt);
            reconciledTransactions.add(transaction);
            if (isFinalInstallmentMonth(loan, dto.receiptDate())) {
                matchedFinalInstallmentMonth = true;
            }
        }
        if (!reconciledTransactions.isEmpty()) {
            transactionRepository.saveAll(reconciledTransactions);
        }
        List<LoanPaymentTransaction> removedTransactions = new ArrayList<>(storedByIdentity.values());
        if (!removedTransactions.isEmpty()) {
            transactionRepository.deleteAllInBatch(removedTransactions);
            storedTransactions.removeAll(removedTransactions);
        }

        Optional<LoanPaymentSummaryDto> paymentSummary = fetchLoanPaymentSummary(loan, applicant);
        if (paymentSummary.isPresent()) {
            applyOutstandingBalances(storedTransactions, paymentSummary.get(), fetchedAt);
            if (!storedTransactions.isEmpty()) {
                transactionRepository.saveAll(storedTransactions);
            }
            applySummary(loan, paymentSummary.get(), fetchedAt, matchedFinalInstallmentMonth);
        }
        return inserted + removedTransactions.size();
    }

    private boolean isFinalInstallmentMonth(LoanApplication loan, LocalDate receiptDate) {
        return loan.getFinalDueDate() != null
            && receiptDate != null
            && YearMonth.from(loan.getFinalDueDate()).equals(YearMonth.from(receiptDate));
    }

    private Optional<LoanPaymentSummaryDto> fetchLoanPaymentSummary(LoanApplication loan, Member applicant) {
        try {
            return loanPaymentSummaryClient.fetchSummary(
                applicant.getMemberNo(), normalizeStationId(loan.getStationId()), loan.getLoanId());
        } catch (LoanPaymentLookupException ex) {
            log.warn("Skipping payment summary refresh for application {}: {}", loan.getId(), ex.getMessage());
            return Optional.empty();
        }
    }

    private void applyOutstandingBalances(List<LoanPaymentTransaction> transactions,
                                          LoanPaymentSummaryDto summary,
                                          OffsetDateTime fetchedAt) {
        List<LoanPaymentTransaction> newestFirst = transactions.stream()
            .filter(transaction -> transaction.getReceiptDate() != null)
            .sorted(Comparator.comparing(LoanPaymentTransaction::getReceiptDate).reversed()
                .thenComparingInt(LoanPaymentTransaction::getProviderOrder)
                .thenComparingInt(LoanPaymentTransaction::getDuplicateOccurrence)
                .thenComparing(LoanPaymentTransaction::getId))
            .toList();
        BigDecimal outstandingTotal = summary.totalOutstanding();
        BigDecimal outstandingPrincipal = summary.outstandingPrincipal();
        BigDecimal outstandingInterest = summary.outstandingInterest();
        for (LoanPaymentTransaction transaction : newestFirst) {
            transaction.setOutstandingBalance(outstandingTotal);
            transaction.setOutstandingPrincipal(outstandingPrincipal);
            transaction.setOutstandingInterest(outstandingInterest);
            transaction.setFetchedAt(fetchedAt);
            outstandingTotal = addNullable(outstandingTotal, transaction.getTotalPaid());
            outstandingPrincipal = addNullable(outstandingPrincipal, transaction.getPrincipalPaid());
            outstandingInterest = addNullable(outstandingInterest, transaction.getInterestPaid());
        }
    }

    private BigDecimal addNullable(BigDecimal balance, BigDecimal paid) {
        return balance == null || paid == null ? null : balance.add(paid);
    }

    private boolean isValidPayment(LoanPaymentTransactionDto dto) {
        return dto != null
            && dto.receiptDate() != null
            && dto.principalPaid() != null
            && dto.interestPaid() != null
            && dto.totalPaid() != null
            && dto.principalPaid().signum() >= 0
            && dto.interestPaid().signum() >= 0
            && dto.totalPaid().signum() > 0;
    }

    private PaymentIdentity paymentIdentity(LoanPaymentTransaction transaction) {
        return new PaymentIdentity(new PaymentSignature(
            transaction.getReceiptDate(),
            canonicalMoney(transaction.getPrincipalPaid()),
            canonicalMoney(transaction.getInterestPaid()),
            canonicalMoney(transaction.getTotalPaid())
        ), transaction.getDuplicateOccurrence());
    }

    private PaymentSignature paymentSignature(LoanPaymentTransactionDto dto) {
        return new PaymentSignature(
            dto.receiptDate(),
            canonicalMoney(dto.principalPaid()),
            canonicalMoney(dto.interestPaid()),
            canonicalMoney(dto.totalPaid())
        );
    }

    private String canonicalMoney(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private record PaymentSignature(LocalDate receiptDate, String principalPaid, String interestPaid, String totalPaid) {}

    private record PaymentIdentity(PaymentSignature signature, int duplicateOccurrence) {}

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
                loan.getSaccoId(), loan.getStationId(),
                Map.of(
                    "source", "SYNC",
                    "loanId", Objects.toString(loan.getLoanId(), ""),
                    "paidAt", fetchedAt.toString(),
                    "lastPaymentDate", Objects.toString(summary.lastPaymentDate(), "")
            ));
            auditLoanStatus(loan, "LOAN_MARKED_PAID", "Loan marked paid");
            return;
        }

        if (shouldMarkDefaulted(loan, summary, today)) {
            loan.setStatus(LoanStatus.DEFAULTED);
            loan.setUpdatedAt(fetchedAt);
            loanApplicationRepository.save(loan);
            outboxService.enqueue("LOAN", loan.getId(), "DEFAULTED", loan.getApplicantMemberId(),
                loan.getSaccoId(), loan.getStationId(),
                Map.of(
                    "source", "SYNC",
                    "loanId", Objects.toString(loan.getLoanId(), ""),
                    "finalDueDate", Objects.toString(loan.getFinalDueDate(), ""),
                    "totalOutstanding", Objects.toString(summary.totalOutstanding(), "")
                ));
            auditLoanStatus(loan, "LOAN_MARKED_DEFAULTED", "Loan marked defaulted");
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
        return loan.getStatus() == LoanStatus.DISBURSED
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

    private void auditLoanStatus(LoanApplication loan, String action, String description) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("result", "SUCCESS");
        details.put("saccoId", loan.getSaccoId());
        details.put("stationId", loan.getStationId());
        details.put("applicationNumber", loan.getApplicationNumber());
        details.put("loanId", loan.getLoanId());
        details.put("workflowStatus", loan.getStatus() == null ? null : loan.getStatus().name());
        auditService.logEvent(
            "LOAN_APPLICATION",
            loan.getId(),
            action,
            null,
            AuditEventStatus.SUCCESS,
            description,
            "LOAN_APPLICATION",
            loan.getLoanId() == null || loan.getLoanId().isBlank()
                ? "Loan Application #" + loan.getApplicationNumber()
                : "Loan ID " + loan.getLoanId(),
            loan.getSaccoId(),
            loan.getStationId(),
            details
        );
    }

    private String writeSummaryJson(LoanPaymentSummaryDto summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JacksonException ex) {
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
