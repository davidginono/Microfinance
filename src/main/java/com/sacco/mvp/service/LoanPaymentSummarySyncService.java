package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import com.sacco.mvp.integration.foresight.ForesightLoanPaymentSummary;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanPaymentSummarySyncService {
    public static final String COMMUNICATION_ERROR_MESSAGE =
        "We could not refresh loan balances right now. Please retry again later.";
    private static final String UPDATED_MESSAGE = "Loan balances updated.";
    private static final String NO_DATA_MESSAGE = "No updated balance was returned. Original outstanding balance remains.";
    private static final String PARTIAL_MESSAGE = "Some loan balances were updated.";
    private static final int DEFAULT_MANUAL_REFRESH_LIMIT = 50;
    private static final List<LoanStatus> SYNCABLE_STATUSES = List.of(
        LoanStatus.DISBURSED,
        LoanStatus.DEFAULTED
    );

    private final LoanApplicationRepository loanApplicationRepository;
    private final MemberRepository memberRepository;
    private final ForesightDirectoryService foresightDirectoryService;
    private final ObjectMapper objectMapper;

    public MemberRefreshResult refreshMemberActiveLoanPaymentSummaries(UUID memberId, int limit) {
        if (memberId == null) {
            return new MemberRefreshResult(RefreshStatus.NO_DATA, NO_DATA_MESSAGE, List.of());
        }
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) {
            return new MemberRefreshResult(RefreshStatus.NO_DATA, NO_DATA_MESSAGE, List.of());
        }
        int pageSize = limit <= 0 ? DEFAULT_MANUAL_REFRESH_LIMIT : Math.min(limit, DEFAULT_MANUAL_REFRESH_LIMIT);
        Page<LoanApplication> page = loanApplicationRepository.findByApplicantMemberIdAndStatusInAndLoanIdIsNotNull(
            memberId,
            SYNCABLE_STATUSES,
            PageRequest.of(0, pageSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")))
        );
        List<LoanRefreshResult> results = page.getContent().stream()
            .map(loan -> refreshLoanPaymentSummary(loan, member))
            .toList();
        return aggregate(results);
    }

    public LoanRefreshResult refreshLoanPaymentSummary(UUID loanApplicationId) {
        if (loanApplicationId == null) {
            return LoanRefreshResult.skipped(null, "", "Loan application ID is missing.");
        }
        LoanApplication loan = loanApplicationRepository.findById(loanApplicationId).orElse(null);
        if (loan == null) {
            return LoanRefreshResult.skipped(loanApplicationId, "", "Loan application was not found.");
        }
        Member member = memberRepository.findById(loan.getApplicantMemberId()).orElse(null);
        return refreshLoanPaymentSummary(loan, member);
    }

    public ScheduledSyncResult syncActiveLoanPaymentSummaries(int batchSize) {
        int safeBatchSize = batchSize <= 0 ? 100 : Math.min(batchSize, 500);
        int updated = 0;
        int noData = 0;
        int errors = 0;
        int skipped = 0;
        PageRequest pageRequest = PageRequest.of(
            0,
            safeBatchSize,
            Sort.by(Sort.Order.asc("id"))
        );
        while (true) {
            Page<LoanApplication> page = loanApplicationRepository.findByStatusInAndLoanIdIsNotNull(
                SYNCABLE_STATUSES,
                pageRequest
            );
            for (LoanApplication loan : page.getContent()) {
                LoanRefreshResult result = refreshLoanPaymentSummary(loan.getId());
                switch (result.status()) {
                    case UPDATED -> updated++;
                    case NO_DATA -> noData++;
                    case ERROR -> errors++;
                    case SKIPPED -> skipped++;
                    case PARTIAL -> {
                    }
                }
            }
            if (!page.hasNext()) {
                break;
            }
            pageRequest = PageRequest.of(
                page.nextPageable().getPageNumber(),
                safeBatchSize,
                Sort.by(Sort.Order.asc("id"))
            );
        }
        return new ScheduledSyncResult(updated, noData, errors, skipped);
    }

    private LoanRefreshResult refreshLoanPaymentSummary(LoanApplication loan, Member member) {
        if (!isSyncable(loan)) {
            return LoanRefreshResult.skipped(
                loan == null ? null : loan.getId(),
                loan == null ? "" : safeLoanId(loan),
                "Loan is not active or has no LMS loan ID."
            );
        }
        if (member == null || !hasText(member.getMemberNo())) {
            return LoanRefreshResult.skipped(loan.getId(), safeLoanId(loan), "Applicant member number is missing.");
        }
        String stationId = normalizeStationId(loan.getStationId(), member.getStationId());
        if (!hasText(stationId)) {
            return LoanRefreshResult.skipped(loan.getId(), safeLoanId(loan), "Applicant station ID is missing.");
        }
        try {
            List<ForesightLoanPaymentSummary> summaries = foresightDirectoryService.fetchLoanPaymentSummary(
                member.getMemberNo().trim(),
                stationId,
                safeLoanId(loan)
            );
            Optional<ForesightLoanPaymentSummary> summary = selectUsableSummary(summaries, safeLoanId(loan));
            if (summary.isEmpty()) {
                return new LoanRefreshResult(loan.getId(), safeLoanId(loan), RefreshStatus.NO_DATA, NO_DATA_MESSAGE);
            }
            loan.setFinancialSnapshot(mergePaymentSummary(loan.getFinancialSnapshot(), summary.get()));
            loanApplicationRepository.save(loan);
            return new LoanRefreshResult(loan.getId(), safeLoanId(loan), RefreshStatus.UPDATED, UPDATED_MESSAGE);
        } catch (RuntimeException ex) {
            log.warn("Unable to refresh Foresight loan payment summary for application {}: {}",
                loan.getId(), ex.getMessage());
            return new LoanRefreshResult(loan.getId(), safeLoanId(loan), RefreshStatus.ERROR, COMMUNICATION_ERROR_MESSAGE);
        }
    }

    private Optional<ForesightLoanPaymentSummary> selectUsableSummary(List<ForesightLoanPaymentSummary> summaries, String loanId) {
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

    private String mergePaymentSummary(String rawJson, ForesightLoanPaymentSummary summary) {
        try {
            Map<String, Object> snapshot = rawJson == null || rawJson.isBlank()
                ? new LinkedHashMap<>()
                : objectMapper.readValue(rawJson, new TypeReference<LinkedHashMap<String, Object>>() {});
            putOrRemove(snapshot, LoanFinancialSnapshotKeys.FORESIGHT_PAYMENT_SUMMARY_LOAN_ID, summary.loanIdText());
            putOrRemove(snapshot, LoanFinancialSnapshotKeys.FORESIGHT_LOAN_DESCRIPTION, summary.loanDescription());
            putOrRemove(snapshot, LoanFinancialSnapshotKeys.FORESIGHT_REQUESTED_AMOUNT, summary.requestedAmount());
            putOrRemove(snapshot, LoanFinancialSnapshotKeys.FORESIGHT_DISBURSED_AMOUNT, summary.disbursedAmount());
            putOrRemove(snapshot, LoanFinancialSnapshotKeys.FORESIGHT_INTEREST_RATE, summary.interestRate());
            putOrRemove(snapshot, LoanFinancialSnapshotKeys.FORESIGHT_EFFECTIVE_DATE,
                summary.effectiveDate() == null ? null : summary.effectiveDate().toString());
            putOrRemove(snapshot, LoanFinancialSnapshotKeys.FORESIGHT_LAST_PAYMENT_DATE,
                summary.lastPaymentDate() == null ? null : summary.lastPaymentDate().toString());
            putOrRemove(snapshot, LoanFinancialSnapshotKeys.FORESIGHT_PRINCIPAL_AMOUNT, summary.principalAmount());
            putOrRemove(snapshot, LoanFinancialSnapshotKeys.FORESIGHT_INTEREST_AMOUNT, summary.interestAmount());
            putOrRemove(snapshot, LoanFinancialSnapshotKeys.FORESIGHT_TOTAL_PRINCIPAL_PAID, summary.totalPrincipalPaid());
            putOrRemove(snapshot, LoanFinancialSnapshotKeys.FORESIGHT_TOTAL_INTEREST_PAID, summary.totalInterestPaid());
            putOrRemove(snapshot, LoanFinancialSnapshotKeys.FORESIGHT_OUTSTANDING_PRINCIPAL, summary.outstandingPrincipal());
            putOrRemove(snapshot, LoanFinancialSnapshotKeys.FORESIGHT_OUTSTANDING_INTEREST, summary.outstandingInterest());
            snapshot.put(LoanFinancialSnapshotKeys.FORESIGHT_TOTAL_OUTSTANDING, summary.totalOutstanding());
            snapshot.put(LoanFinancialSnapshotKeys.FORESIGHT_PAYMENT_SUMMARY_FETCHED_AT, OffsetDateTime.now().toString());
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to merge the Foresight loan payment summary.", ex);
        }
    }

    private void putOrRemove(Map<String, Object> snapshot, String key, Object value) {
        if (value == null) {
            snapshot.remove(key);
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            snapshot.remove(key);
            return;
        }
        snapshot.put(key, value);
    }

    private MemberRefreshResult aggregate(List<LoanRefreshResult> results) {
        if (results == null || results.isEmpty()) {
            return new MemberRefreshResult(RefreshStatus.NO_DATA, NO_DATA_MESSAGE, List.of());
        }
        long updated = results.stream().filter(result -> result.status() == RefreshStatus.UPDATED).count();
        long errors = results.stream().filter(result -> result.status() == RefreshStatus.ERROR).count();
        long noData = results.stream().filter(result -> result.status() == RefreshStatus.NO_DATA).count();
        long skipped = results.stream().filter(result -> result.status() == RefreshStatus.SKIPPED).count();
        if (errors == results.size()) {
            return new MemberRefreshResult(RefreshStatus.ERROR, COMMUNICATION_ERROR_MESSAGE, results);
        }
        if (updated > 0 && (errors > 0 || noData > 0 || skipped > 0)) {
            return new MemberRefreshResult(RefreshStatus.PARTIAL, PARTIAL_MESSAGE, results);
        }
        if (updated > 0) {
            return new MemberRefreshResult(RefreshStatus.UPDATED, UPDATED_MESSAGE, results);
        }
        if (errors > 0) {
            return new MemberRefreshResult(RefreshStatus.ERROR, COMMUNICATION_ERROR_MESSAGE, results);
        }
        return new MemberRefreshResult(RefreshStatus.NO_DATA, NO_DATA_MESSAGE, results);
    }

    private boolean isSyncable(LoanApplication loan) {
        return loan != null
            && SYNCABLE_STATUSES.contains(loan.getStatus())
            && hasText(loan.getLoanId());
    }

    private String safeLoanId(LoanApplication loan) {
        return loan == null || loan.getLoanId() == null ? "" : loan.getLoanId().trim();
    }

    private String normalizeStationId(String loanStationId, String memberStationId) {
        if (hasText(loanStationId)) {
            return loanStationId.trim();
        }
        return hasText(memberStationId) ? memberStationId.trim() : "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public enum RefreshStatus {
        UPDATED,
        NO_DATA,
        PARTIAL,
        ERROR,
        SKIPPED
    }

    public record MemberRefreshResult(
        RefreshStatus status,
        String message,
        List<LoanRefreshResult> loans
    ) {
    }

    public record LoanRefreshResult(
        UUID applicationId,
        String loanId,
        RefreshStatus status,
        String message
    ) {
        static LoanRefreshResult skipped(UUID applicationId, String loanId, String message) {
            return new LoanRefreshResult(applicationId, loanId, RefreshStatus.SKIPPED, message);
        }
    }

    public record ScheduledSyncResult(
        int updated,
        int noData,
        int errors,
        int skipped
    ) {
    }
}
