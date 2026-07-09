package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.Notification;
import com.sacco.mvp.domain.NotificationStatus;
import com.sacco.mvp.domain.OutboxEvent;
import com.sacco.mvp.domain.OutboxStatus;
import com.sacco.mvp.repository.NotificationRepository;
import com.sacco.mvp.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.outbox.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisherScheduler {
    private final OutboxEventRepository outboxEventRepository;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;
    private final AdminAlertService adminAlertService;
    private final NotificationViewService notificationViewService;
    private final NotificationDeliveryService notificationDeliveryService;
    private final LoanNotificationFormatter loanNotificationFormatter;

    @Value("${app.outbox.batch-size:250}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.outbox.fixed-delay-ms:500}")
    @Transactional
    public void publish() {
        List<OutboxEvent> events = outboxEventRepository.findNextPublishBatch(Math.max(1, batchSize));
        for (OutboxEvent event : events) {
            try {
                JsonNode payload = objectMapper.readTree(event.getPayload());
                UUID recipientId = UUID.fromString(payload.get("recipientId").asText());
                String saccoId = textOrNull(payload, "saccoId");
                String stationId = textOrNull(payload, "stationId");

                if (notificationRepository.existsDeliveredDuplicate(recipientId, event.getEventType(), event.getPayload())) {
                    log.info("Skipping duplicate notification delivery for outbox event {}", event.getId());
                    event.setStatus(OutboxStatus.PUBLISHED);
                    event.setPublishedAt(OffsetDateTime.now());
                    outboxEventRepository.save(event);
                    continue;
                }

                Notification notification = notificationRepository.save(Notification.builder()
                    .id(UUID.randomUUID())
                    .recipientMemberId(recipientId)
                    .type(event.getEventType())
                    .payload(event.getPayload())
                    .status(NotificationStatus.SENT)
                    .createdAt(OffsetDateTime.now())
                    .sentAt(OffsetDateTime.now())
                    .build());
                NotificationViewService.NotificationView view = notificationViewService.toView(notification);
                NotificationDeliveryService.DeliveryContent content = loanNotificationFormatter.format(
                    event,
                    payload,
                    NotificationDeliveryService.DeliveryContent.plain(view.getSubject(), view.getMessage())
                );
                notificationDeliveryService.deliver(
                    saccoId,
                    stationId,
                    notification.getId(),
                    recipientId,
                    event.getEventType(),
                    content
                );

                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(OffsetDateTime.now());
                outboxEventRepository.save(event);
            } catch (Exception ex) {
                log.error("Outbox publish failed for event {}", event.getId(), ex);
                event.setStatus(OutboxStatus.FAILED);
                outboxEventRepository.save(event);
                adminAlertService.alertAllAdmins(
                    "Outbox Publisher",
                    "Outbox publish failed",
                    "An outbox event failed during notification publishing.",
                    java.util.Map.of(
                        "eventId", event.getId().toString(),
                        "eventType", event.getEventType(),
                        "aggregateType", event.getAggregateType(),
                        "aggregateId", String.valueOf(event.getAggregateId()),
                        "error", ex.getMessage() == null ? "Unknown publish error" : ex.getMessage()
                    )
                );
            }
        }
    }

    private String textOrNull(JsonNode payload, String field) {
        JsonNode value = payload == null ? null : payload.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
