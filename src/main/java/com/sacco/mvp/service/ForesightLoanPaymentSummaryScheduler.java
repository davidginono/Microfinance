package com.sacco.mvp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ForesightLoanPaymentSummaryScheduler {
    private final LoanPaymentSummarySyncService loanPaymentSummarySyncService;
    private final SchedulerLockService schedulerLockService;

    @Value("${external.foresight.loan-payment-summary-sync-batch-size:100}")
    private int batchSize;

    @Scheduled(cron = "${external.foresight.loan-payment-summary-sync-cron:0 15 2 * * *}")
    public void syncLoanPaymentSummaries() {
        schedulerLockService.runExclusive(
            SchedulerLockService.FORESIGHT_LOAN_PAYMENT_SUMMARY,
            this::syncLoanPaymentSummariesLocked
        );
    }

    private void syncLoanPaymentSummariesLocked() {
        LoanPaymentSummarySyncService.ScheduledSyncResult result =
            loanPaymentSummarySyncService.syncActiveLoanPaymentSummaries(batchSize);
        log.info(
            "Foresight loan payment summary sync completed: updated={}, noData={}, errors={}, skipped={}",
            result.updated(),
            result.noData(),
            result.errors(),
            result.skipped()
        );
    }
}
