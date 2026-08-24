package com.sacco.mvp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanPortfolioRiskStatusScheduler {
    private final LoanPortfolioRiskStatusService loanPortfolioRiskStatusService;
    private final SchedulerLockService schedulerLockService;

    @Value("${app.loan-risk-status-batch-size:250}")
    private int batchSize;

    @Scheduled(cron = "${app.loan-risk-status-cron:0 35 2 * * *}")
    public void reevaluatePortfolioRiskStatuses() {
        schedulerLockService.runExclusive(
            SchedulerLockService.LOAN_PORTFOLIO_RISK_STATUS,
            this::reevaluatePortfolioRiskStatusesLocked
        );
    }

    private void reevaluatePortfolioRiskStatusesLocked() {
        LoanPortfolioRiskStatusService.PortfolioRiskStatusResult result =
            loanPortfolioRiskStatusService.reevaluatePortfolioRiskStatuses(batchSize);
        log.info(
            "Loan PAR status evaluation completed: changed={}, paid={}, par={}, defaulted={}, reverted={}, skipped={}",
            result.changed(),
            result.paid(),
            result.par(),
            result.defaulted(),
            result.reverted(),
            result.skipped()
        );
    }
}
