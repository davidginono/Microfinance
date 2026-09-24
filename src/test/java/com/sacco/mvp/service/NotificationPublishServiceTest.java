package com.sacco.mvp.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.Notification;
import com.sacco.mvp.domain.OutboxEvent;
import com.sacco.mvp.domain.OutboxStatus;
import com.sacco.mvp.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationPublishServiceTest {

    @Test
    void staffReviewDuplicateUsesStableStageContextEvenWithoutReviewerMetadata() throws Exception {
        UUID loanId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
            .id(UUID.randomUUID())
            .aggregateType("LOAN")
            .aggregateId(loanId)
            .eventType("LOAN_READY_FOR_MANAGER")
            .payload("""
                {
                  "recipientId":"%s",
                  "eventType":"LOAN_READY_FOR_MANAGER",
                  "details":{
                    "loanId":"%s"
                  }
                }
                """.formatted(recipientId, loanId))
            .status(OutboxStatus.PROCESSING)
            .createdAt(OffsetDateTime.now())
            .build();
        JsonNode payload = new ObjectMapper().readTree(event.getPayload());
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(notificationRepository.existsDeliveredStaffReviewDuplicate(
            recipientId,
            "LOAN_READY_FOR_MANAGER",
            loanId.toString(),
            "MANAGER"
        )).thenReturn(true);

        NotificationPublishService service = new NotificationPublishService(notificationRepository, jdbcTemplate);
        Optional<Notification> notification = service.createFromOutboxIfAbsent(event, recipientId, payload);

        assertThat(notification).isEmpty();
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(notificationRepository, never()).existsDeliveredDuplicate(any(), any(), any());
        verify(jdbcTemplate).queryForList(
            eq("select pg_advisory_xact_lock(hashtext(?), hashtext(?))"),
            eq("sacco:outbox-notification"),
            eq("LOAN|" + loanId + "|LOAN_READY_FOR_MANAGER|" + recipientId)
        );
    }

    @Test
    void nonReviewOutboxDuplicateUsesExactPayloadAfterSerializedContextLock() throws Exception {
        UUID aggregateId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
            .id(UUID.randomUUID())
            .aggregateType("REVERSAL_REQUEST")
            .aggregateId(aggregateId)
            .eventType("MANAGER_REVERSAL_REQUESTED")
            .payload("""
                {
                  "recipientId":"%s",
                  "eventType":"MANAGER_REVERSAL_REQUESTED",
                  "details":{
                    "loanId":"%s",
                    "reversalRequestId":"%s"
                  }
                }
                """.formatted(recipientId, UUID.randomUUID(), aggregateId))
            .status(OutboxStatus.PROCESSING)
            .createdAt(OffsetDateTime.now())
            .build();
        JsonNode payload = new ObjectMapper().readTree(event.getPayload());
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(notificationRepository.existsDeliveredDuplicate(recipientId, "MANAGER_REVERSAL_REQUESTED", event.getPayload()))
            .thenReturn(true);

        NotificationPublishService service = new NotificationPublishService(notificationRepository, jdbcTemplate);
        Optional<Notification> notification = service.createFromOutboxIfAbsent(event, recipientId, payload);

        assertThat(notification).isEmpty();
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(jdbcTemplate).queryForList(
            eq("select pg_advisory_xact_lock(hashtext(?), hashtext(?))"),
            eq("sacco:outbox-notification"),
            eq("REVERSAL_REQUEST|" + aggregateId + "|MANAGER_REVERSAL_REQUESTED|" + recipientId)
        );
    }
}
