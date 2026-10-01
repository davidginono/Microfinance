package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanPaymentSummarySyncService {
    public static final String COMMUNICATION_ERROR_MESSAGE =
        "Loan balances are calculated from local repayment records.";
    private static final String UPDATED_MESSAGE = "Loan balances recalculated from posted repayments.";
    private static final String NO_DATA_MESSAGE = "No local active loans were found.";
    private static final int DEFAULT_MANUAL_REFRESH_LIMIT = 50;
    private static final List<LoanStatus> ACTIVE_STATUSES = List.of(
        LoanStatus.DISBURSED,
        LoanStatus.PAR,
        LoanStatus.DEFAULTED
    );

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanRepaymentLedgerService loanRepaymentLedgerService;

    public MemberRefreshResult refreshMemberActiveLoanPaymentSummaries(UUID memberId, int limit) {
        if (memberId == null) {
            return new MemberRefreshResult(RefreshStatus.NO_DATA, NO_DATA_MESSAGE, List.of());
        }
        int pageSize = limit <= 0 ? DEFAULT_MANUAL_REFRESH_LIMIT : Math.min(limit, DEFAULT_MANUAL_REFRESH_LIMIT);
        Page<LoanApplication> page = loanApplicationRepository.findByApplicantMemberIdAndStatusInAndLoanIdIsNotNull(
            memberId,
            ACTIVE_STATUSES,
            PageRequest.of(0, pageSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")))
        );
        List<LoanRefreshResult> results = page.getContent().stream()
            .map(loan -> refreshLoanPaymentSummary(loan.getId()))
            .toList();
        if (results.isEmpty()) return new MemberRefreshResult(RefreshStatus.NO_DATA, NO_DATA_MESSAGE, results);
        long updated = results.stream().filter(r -> r.status() == RefreshStatus.UPDATED).count();
        return new MemberRefreshResult(updated == results.size() ? RefreshStatus.UPDATED
            : updated == 0 ? RefreshStatus.NO_DATA : RefreshStatus.PARTIAL,
            updated == results.size() ? UPDATED_MESSAGE : "Some opening balances require reconciliation before ledger refresh.", results);
    }

    public LoanRefreshResult refreshLoanPaymentSummary(UUID loanApplicationId) {
        if (loanApplicationId == null) {
            return LoanRefreshResult.skipped(null, "", "Loan application ID is missing.");
        }
        LoanApplication loan = loanApplicationRepository.findById(loanApplicationId).orElse(null);
        if (loan == null) {
            return LoanRefreshResult.skipped(loanApplicationId, "", "Loan application was not found.");
        }
        if (!loanRepaymentLedgerService.refreshProjection(loanApplicationId)) {
            return new LoanRefreshResult(loan.getId(), safeLoanId(loan), RefreshStatus.NO_DATA,
                "This loan requires opening-balance reconciliation before repayment posting.");
        }
        return new LoanRefreshResult(loan.getId(), safeLoanId(loan), RefreshStatus.UPDATED, UPDATED_MESSAGE);
    }

    public ScheduledSyncResult syncActiveLoanPaymentSummaries(int batchSize) {
        return new ScheduledSyncResult(0, 0, 0, 0);
    }

    private String safeLoanId(LoanApplication loan) {
        return loan == null || loan.getLoanId() == null ? "" : loan.getLoanId().trim();
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
