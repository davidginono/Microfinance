package com.sacco.mvp.service;

import com.sacco.mvp.lending.controls.service.InstalmentReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class RepaymentReminderScheduler {
    private final InstalmentReminderService reminders;
    private final SchedulerLockService locks;
    @Scheduled(fixedDelayString="${app.collections.reminder-delay-ms:3600000}")
    public void sendRepaymentReminders(){
        locks.runExclusive(SchedulerLockService.REPAYMENT_REMINDERS,()->{
            for(int batch=0;batch<10;batch++)if(reminders.queueBatch()<200)return;
        });
    }
}
