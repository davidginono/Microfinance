package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.Notification;
import com.sacco.mvp.domain.NotificationStatus;
import com.sacco.mvp.domain.OutboxEvent;
import com.sacco.mvp.domain.OutboxStatus;
import com.sacco.mvp.repository.MemberRepository;
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
@ConditionalOnProperty(name = "app.outbox.scheduler-enabled", havingValue = "true")
public class OutboxPublisherScheduler {
    private final OutboxEventRepository outboxEventRepository;
    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;
    private final AdminAlertService adminAlertService;
    private final NotificationViewService notificationViewService;
    private final NotificationEmailService notificationEmailService;

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

                Notification notification = notificationRepository.save(Notification.builder()
                    .id(UUID.randomUUID())
                    .recipientMemberId(recipientId)
                    .type(event.getEventType())
                    .payload(event.getPayload())
                    .status(NotificationStatus.SENT)
                    .createdAt(OffsetDateTime.now())
                    .sentAt(OffsetDateTime.now())
                    .build());
                if (memberRepository.findById(recipientId).map(Member::getEmail).filter(email -> !email.isBlank()).isPresent()) {
                    NotificationViewService.NotificationView view = notificationViewService.toView(notification);
                    notificationEmailService.sendNotificationEmail(recipientId, view.getSubject(), view.getMessage());
                }

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
}
