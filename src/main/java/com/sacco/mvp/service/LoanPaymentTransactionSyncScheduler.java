package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.integration.memberportal.LoanPaymentLookupException;
import com.sacco.mvp.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reconciles complete payment histories for disbursed loans. Failures are
 * isolated per loan so a single bad call does not stop the batch.
 */
@Component
@ConditionalOnProperty(name = "app.loan-payments.sync-enabled", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class LoanPaymentTransactionSyncScheduler {

    private static final List<LoanStatus> SYNC_STATUSES = List.of(
        LoanStatus.DISBURSED,
        LoanStatus.DEFAULTED,
        LoanStatus.PAID
    );

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanPaymentTransactionSyncService syncService;
    private final AtomicBoolean recentSyncRunning = new AtomicBoolean(false);

    @Value("${app.loan-payments.sync-page-size:100}")
    private int syncPageSize;

    @Scheduled(cron = "${app.loan-payments.sync-cron:0 17 3 1 * *}", zone = "${app.loan-payments.sync-zone:Africa/Nairobi}")
    public void syncPreviousMonth() {
        SyncTotals totals = syncPaged("monthly full loan payment history reconciliation",
            syncService::syncAllAndRefreshSummary);
        log.info("Monthly loan payment history reconciliation completed: loans={}, changes={}, failures={}",
            totals.loans(), totals.changes(), totals.failures());
    }

    @Scheduled(cron = "${app.loan-payments.recent-sync-cron:0 0 6-21/3 * * *}", zone = "${app.loan-payments.sync-zone:Africa/Nairobi}")
    public void syncRecentPayments() {
        if (!recentSyncRunning.compareAndSet(false, true)) {
            log.info("Skipping recent loan payment transaction sync because a previous run is still active.");
            return;
        }
        try {
            SyncTotals totals = syncPaged("scheduled full loan payment history reconciliation",
                syncService::syncAllAndRefreshSummary);
            log.info("Scheduled loan payment history reconciliation completed: loans={}, changes={}, failures={}",
                totals.loans(), totals.changes(), totals.failures());
        } finally {
            recentSyncRunning.set(false);
        }
    }

    private SyncTotals syncPaged(String label, LoanSyncAction action) {
        int pageSize = Math.max(25, syncPageSize);
        int pageNumber = 0;
        int totalLoans = 0;
        int totalChanges = 0;
        int failures = 0;
        Page<LoanApplication> page;
        log.info("Starting {} with pageSize={}", label, pageSize);
        do {
            page = loanApplicationRepository.findByStatusInAndLoanIdIsNotNull(
                SYNC_STATUSES,
                PageRequest.of(pageNumber, pageSize, Sort.by("createdAt").ascending())
            );
            for (LoanApplication loan : page.getContent()) {
                totalLoans++;
                try {
                    int changes = action.sync(loan);
                    totalChanges += changes;
                    if (changes > 0) {
                        log.info("Loan payment sync for applicationNumber={} loanId={}: reconciled {} transaction change(s)",
                            loan.getApplicationNumber(), loan.getLoanId(), changes);
                    }
                } catch (LoanPaymentLookupException ex) {
                    failures++;
                    log.warn("Loan payment sync failed for applicationNumber={} loanId={}: {}",
                        loan.getApplicationNumber(), loan.getLoanId(), ex.getMessage());
                } catch (RuntimeException ex) {
                    failures++;
                    log.warn("Unexpected error during loan payment sync for applicationNumber={} loanId={}: {}",
                        loan.getApplicationNumber(), loan.getLoanId(), ex.getMessage(), ex);
                }
            }
            pageNumber++;
        } while (page.hasNext());
        return new SyncTotals(totalLoans, totalChanges, failures);
    }

    @FunctionalInterface
    private interface LoanSyncAction {
        int sync(LoanApplication loan);
    }

    private record SyncTotals(int loans, int changes, int failures) {
    }
}
