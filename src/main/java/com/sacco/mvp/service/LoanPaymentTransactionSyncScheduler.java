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

import java.time.Duration;
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
    private final AtomicBoolean syncRunning = new AtomicBoolean(false);

    @Value("${app.loan-payments.sync-page-size:100}")
    private int syncPageSize;

    @Value("${app.loan-payments.retry-delay:2m}")
    private Duration retryDelay;

    @Scheduled(cron = "${app.loan-payments.sync-cron:0 17 3 1 * *}", zone = "${app.loan-payments.sync-zone:Africa/Nairobi}")
    public void syncPreviousMonth() {
        runScheduledSync("monthly full loan payment history reconciliation");
    }

    @Scheduled(cron = "${app.loan-payments.recent-sync-cron:0 0 8,14 * * *}", zone = "${app.loan-payments.sync-zone:Africa/Nairobi}")
    public void syncRecentPayments() {
        runScheduledSync("scheduled loan payment history reconciliation");
    }

    private void runScheduledSync(String label) {
        if (!syncRunning.compareAndSet(false, true)) {
            log.info("Skipping {} because a previous loan payment sync is still active.", label);
            return;
        }
        try {
            SyncTotals totals = syncPaged(label, syncService::syncAllAndRefreshSummary);
            int totalLoans = totals.loans();
            int totalChanges = totals.changes();
            List<LoanApplication> failures = totals.failures();
            while (!failures.isEmpty()) {
                Duration delay = resolvedRetryDelay();
                log.warn("{} completed with {} failed loan(s); retrying after {}.",
                    label, failures.size(), delay);
                sleepBeforeRetry(delay);
                totals = retryFailures(label, failures, syncService::syncAllAndRefreshSummary);
                totalChanges += totals.changes();
                failures = totals.failures();
            }
            log.info("{} completed successfully: loans={}, changes={}",
                label, totalLoans, totalChanges);
        } finally {
            syncRunning.set(false);
        }
    }

    private SyncTotals syncPaged(String label, LoanSyncAction action) {
        int pageSize = Math.max(25, syncPageSize);
        int pageNumber = 0;
        int totalLoans = 0;
        int totalChanges = 0;
        List<LoanApplication> failures = new java.util.ArrayList<>();
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
                    failures.add(loan);
                    log.warn("Loan payment sync failed for applicationNumber={} loanId={}: {}",
                        loan.getApplicationNumber(), loan.getLoanId(), ex.getMessage());
                } catch (RuntimeException ex) {
                    failures.add(loan);
                    log.warn("Unexpected error during loan payment sync for applicationNumber={} loanId={}: {}",
                        loan.getApplicationNumber(), loan.getLoanId(), ex.getMessage(), ex);
                }
            }
            pageNumber++;
        } while (page.hasNext());
        return new SyncTotals(totalLoans, totalChanges, failures);
    }

    private SyncTotals retryFailures(String label, List<LoanApplication> failedLoans, LoanSyncAction action) {
        int totalChanges = 0;
        List<LoanApplication> remainingFailures = new java.util.ArrayList<>();
        for (LoanApplication loan : failedLoans) {
            try {
                int changes = action.sync(loan);
                totalChanges += changes;
                log.info("Retry succeeded for {} applicationNumber={} loanId={}: changes={}",
                    label, loan.getApplicationNumber(), loan.getLoanId(), changes);
            } catch (LoanPaymentLookupException ex) {
                remainingFailures.add(loan);
                log.warn("Retry failed for applicationNumber={} loanId={}: {}",
                    loan.getApplicationNumber(), loan.getLoanId(), ex.getMessage());
            } catch (RuntimeException ex) {
                remainingFailures.add(loan);
                log.warn("Unexpected retry error for applicationNumber={} loanId={}: {}",
                    loan.getApplicationNumber(), loan.getLoanId(), ex.getMessage(), ex);
            }
        }
        return new SyncTotals(failedLoans.size(), totalChanges, remainingFailures);
    }

    private void sleepBeforeRetry(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Loan payment sync retry was interrupted.", ex);
        }
    }

    private Duration resolvedRetryDelay() {
        long delayMillis = retryDelay == null ? Duration.ofMinutes(2).toMillis() : retryDelay.toMillis();
        long minMillis = Duration.ofMinutes(2).toMillis();
        long maxMillis = Duration.ofMinutes(3).toMillis();
        return Duration.ofMillis(Math.min(Math.max(delayMillis, minMillis), maxMillis));
    }

    @FunctionalInterface
    private interface LoanSyncAction {
        int sync(LoanApplication loan);
    }

    private record SyncTotals(int loans, int changes, List<LoanApplication> failures) {
    }
}
