package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.integration.memberportal.LoanPaymentLookupException;
import com.sacco.mvp.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;

/**
 * Monthly job that pulls the previous month's payment transactions for every
 * disbursed loan. Failures are isolated per loan so a single bad call does not
 * stop the batch.
 */
@Component
@ConditionalOnProperty(name = "app.loan-payments.sync-enabled", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class LoanPaymentTransactionSyncScheduler {

    private static final List<LoanStatus> SYNC_STATUSES = List.of(
        LoanStatus.FINAL_APPROVED,
        LoanStatus.DEFAULTED,
        LoanStatus.PAID
    );

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanPaymentTransactionSyncService syncService;

    @Scheduled(cron = "${app.loan-payments.sync-cron:0 17 3 1 * *}")
    public void syncPreviousMonth() {
        YearMonth target = YearMonth.now().minusMonths(1);
        List<LoanApplication> loans = loanApplicationRepository.findByStatusInAndLoanIdIsNotNull(SYNC_STATUSES);
        log.info("Starting loan payment transaction sync for {}: {} disbursed loan(s)", target, loans.size());

        int totalInserted = 0;
        int failures = 0;
        for (LoanApplication loan : loans) {
            try {
                int inserted = syncService.syncMonth(loan, target);
                totalInserted += inserted;
                if (inserted > 0) {
                    log.info("Loan payment sync for applicationNumber={} loanId={}: inserted {} transaction(s)",
                        loan.getApplicationNumber(), loan.getLoanId(), inserted);
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
        log.info("Loan payment transaction sync for {} completed: loans={}, inserted={}, failures={}",
            target, loans.size(), totalInserted, failures);
    }
}
