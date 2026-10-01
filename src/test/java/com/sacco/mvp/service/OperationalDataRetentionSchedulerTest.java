package com.sacco.mvp.service;

import com.sacco.mvp.repository.AuditLogRepository;
import com.sacco.mvp.repository.NotificationRepository;
import com.sacco.mvp.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationalDataRetentionSchedulerTest {
    @Test
    void purgeExpiredOperationalDataPrunesAuditOutboxAndReadNotifications() {
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        SchedulerLockService schedulerLockService = mock(SchedulerLockService.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(schedulerLockService).runExclusive(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        OperationalDataRetentionScheduler scheduler = new OperationalDataRetentionScheduler(
            auditLogRepository,
            outboxEventRepository,
            notificationRepository,
            schedulerLockService
        );
        ReflectionTestUtils.setField(scheduler, "auditLogRetentionDays", 70L);
        ReflectionTestUtils.setField(scheduler, "outboxRetentionDays", 45L);
        ReflectionTestUtils.setField(scheduler, "readNotificationRetentionDays", 180L);

        when(auditLogRepository.deleteByCreatedAtBeforeAndEntityTypeNot(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("LOAN_REPAYMENT"))).thenReturn(1L);
        when(outboxEventRepository.deleteByCreatedAtBefore(org.mockito.ArgumentMatchers.any())).thenReturn(2L);
        when(notificationRepository.deleteByReadAtIsNotNullAndCreatedAtBefore(org.mockito.ArgumentMatchers.any())).thenReturn(3L);

        scheduler.purgeExpiredOperationalData();

        org.mockito.ArgumentCaptor<OffsetDateTime> notificationCutoff =
            org.mockito.ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(auditLogRepository).deleteByCreatedAtBeforeAndEntityTypeNot(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("LOAN_REPAYMENT"));
        org.mockito.Mockito.verify(auditLogRepository, org.mockito.Mockito.never()).deleteByCreatedAtBefore(org.mockito.ArgumentMatchers.any());
        verify(outboxEventRepository).deleteByCreatedAtBefore(org.mockito.ArgumentMatchers.any());
        verify(notificationRepository).deleteByReadAtIsNotNullAndCreatedAtBefore(notificationCutoff.capture());
        assertThat(notificationCutoff.getValue()).isBefore(OffsetDateTime.now().minusDays(179));
    }
}
