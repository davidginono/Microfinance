package com.sacco.mvp.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.Notification;
import com.sacco.mvp.domain.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.outbox.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisherScheduler {
    private final OutboxPublishService outboxPublishService;
    private final NotificationPublishService notificationPublishService;
    private final ObjectMapper objectMapper;
    private final AdminAlertService adminAlertService;
    private final NotificationViewService notificationViewService;
    private final NotificationDeliveryService notificationDeliveryService;
    private final LoanNotificationFormatter loanNotificationFormatter;

    @Value("${app.outbox.batch-size:250}")
    private int batchSize;

    // Delivery performs SMS/e-mail calls, so this method is deliberately not @Transactional.
    // Claim, persist, and outcome writes each run in their own short transaction.
    @Scheduled(fixedDelayString = "${app.outbox.fixed-delay-ms:500}")
    public void publish() {
        List<OutboxEvent> events = outboxPublishService.claimBatch(Math.max(1, batchSize));
        for (OutboxEvent event : events) {
            try {
                JsonNode payload = objectMapper.readTree(event.getPayload());
                if (notificationViewService.isObsoleteRepaymentSyncNotification(event.getPayload())) {
                    log.info("Discarding obsolete repayment-sync outbox event {}", event.getId());
                    outboxPublishService.markPublished(event);
                    continue;
                }
                UUID recipientId = UUID.fromString(payload.get("recipientId").asString());
                String saccoId = textOrNull(payload, "saccoId");
                String stationId = textOrNull(payload, "stationId");

                Optional<Notification> notification = notificationPublishService.createFromOutboxIfAbsent(
                    event,
                    recipientId,
                    payload
                );
                if (notification.isEmpty()) {
                    log.info("Skipping duplicate notification delivery for outbox event {}", event.getId());
                    outboxPublishService.markPublished(event);
                    continue;
                }

                Notification savedNotification = notification.get();
                NotificationViewService.NotificationView view = notificationViewService.toView(savedNotification);
                NotificationDeliveryService.DeliveryContent content = loanNotificationFormatter.format(
                    event,
                    payload,
                    NotificationDeliveryService.DeliveryContent.plain(view.getSubject(), view.getMessage())
                );
                notificationDeliveryService.deliver(
                    saccoId,
                    stationId,
                    savedNotification.getId(),
                    recipientId,
                    event.getEventType(),
                    content
                );

                outboxPublishService.markPublished(event);
            } catch (Exception ex) {
                log.error("Outbox publish failed for event {}", event.getId(), ex);
                outboxPublishService.markFailed(event);
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
        String text = value == null || value.isNull() ? null : value.asString();
        return text == null || text.isBlank() ? null : text;
    }
}
