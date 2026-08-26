package com.sacco.mvp.service;

import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.OutboxEvent;
import com.sacco.mvp.domain.OutboxStatus;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.NotificationRepository;
import com.sacco.mvp.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherSchedulerTest {

    @Test
    void queuedLegacySyncEventIsDiscardedWithoutCreatingOrDeliveringNotification() {
        ObjectMapper objectMapper = new ObjectMapper();
        OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        NotificationDeliveryService deliveryService = mock(NotificationDeliveryService.class);
        OutboxEvent event = OutboxEvent.builder()
            .id(UUID.randomUUID())
            .aggregateType("LOAN")
            .aggregateId(UUID.randomUUID())
            .eventType("PAID")
            .payload("""
                {"recipientId":"00000000-0000-0000-0000-000000000001","details":{"source":"SYNC"}}
                """)
            .status(OutboxStatus.NEW)
            .createdAt(OffsetDateTime.now())
            .build();
        when(outboxRepository.findNextPublishBatch(anyInt())).thenReturn(List.of(event));
        when(outboxRepository.saveAll(List.of(event))).thenReturn(List.of(event));

        OutboxPublisherScheduler scheduler = new OutboxPublisherScheduler(
            new OutboxPublishService(outboxRepository),
            notificationRepository,
            objectMapper,
            mock(AdminAlertService.class),
            new NotificationViewService(
                objectMapper,
                mock(MemberRepository.class),
                new AccessControlService(),
                new ApplicationClock("Africa/Nairobi")
            ),
            deliveryService,
            mock(LoanNotificationFormatter.class)
        );

        scheduler.publish();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(outboxRepository).save(event);
        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(deliveryService, never()).deliver(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(NotificationDeliveryService.DeliveryContent.class)
        );
    }
}
