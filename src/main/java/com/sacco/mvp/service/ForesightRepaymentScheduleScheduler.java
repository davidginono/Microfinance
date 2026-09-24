package com.sacco.mvp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ForesightRepaymentScheduleScheduler {
    private final ForesightRepaymentScheduleService foresightRepaymentScheduleService;
    private final SchedulerLockService schedulerLockService;

    @Value("${external.foresight.repayment-schedule-sync-batch-size:100}")
    private int batchSize;

    @Scheduled(cron = "${external.foresight.repayment-schedule-sync-cron:0 45 2 * * *}")
    public void syncRepaymentSchedules() {
        schedulerLockService.runExclusive(
            SchedulerLockService.FORESIGHT_REPAYMENT_SCHEDULE,
            this::syncRepaymentSchedulesLocked
        );
    }

    private void syncRepaymentSchedulesLocked() {
        ForesightRepaymentScheduleService.ScheduledSyncResult result =
            foresightRepaymentScheduleService.syncActiveLoanSchedules(batchSize);
        log.info(
            "Foresight repayment schedule sync completed: updated={}, noData={}, errors={}, skipped={}",
            result.updated(),
            result.noData(),
            result.errors(),
            result.skipped()
        );
    }
}
