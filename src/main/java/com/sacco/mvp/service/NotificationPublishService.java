package com.sacco.mvp.service;

import tools.jackson.databind.JsonNode;
import com.sacco.mvp.domain.Notification;
import com.sacco.mvp.domain.NotificationStatus;
import com.sacco.mvp.domain.OutboxEvent;
import com.sacco.mvp.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationPublishService {
    private static final String LOCK_NAMESPACE = "sacco:outbox-notification";

    private final NotificationRepository notificationRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public Optional<Notification> createFromOutboxIfAbsent(OutboxEvent event, UUID recipientId, JsonNode payload) {
        lockDeliveryContext(event, recipientId);
        if (isDuplicateDelivery(recipientId, event, payload)) {
            return Optional.empty();
        }
        OffsetDateTime now = OffsetDateTime.now();
        return Optional.of(notificationRepository.save(Notification.builder()
            .id(UUID.randomUUID())
            .recipientMemberId(recipientId)
            .type(event.getEventType())
            .payload(event.getPayload())
            .status(NotificationStatus.SENT)
            .createdAt(now)
            .sentAt(now)
            .build()));
    }

    private void lockDeliveryContext(OutboxEvent event, UUID recipientId) {
        String lockKey = deliveryLockKey(event, recipientId);
        if (lockKey == null) {
            return;
        }
        jdbcTemplate.queryForList(
            "select pg_advisory_xact_lock(hashtext(?), hashtext(?))",
            LOCK_NAMESPACE,
            lockKey
        );
    }

    private String deliveryLockKey(OutboxEvent event, UUID recipientId) {
        if (event == null
            || event.getAggregateType() == null
            || event.getAggregateId() == null
            || event.getEventType() == null
            || recipientId == null) {
            return null;
        }
        return event.getAggregateType() + "|" + event.getAggregateId() + "|" + event.getEventType() + "|" + recipientId;
    }

    private boolean isDuplicateDelivery(UUID recipientId, OutboxEvent event, JsonNode payload) {
        LoanStaffNotificationEvents.ReviewAssignmentContext staffReview =
            LoanStaffNotificationEvents.reviewAssignmentContext(event, payload, recipientId);
        if (staffReview != null) {
            return notificationRepository.existsDeliveredStaffReviewDuplicate(
                recipientId,
                event.getEventType(),
                staffReview.loanId(),
                staffReview.reviewStage()
            );
        }
        return notificationRepository.existsDeliveredDuplicate(recipientId, event.getEventType(), event.getPayload());
    }
}
