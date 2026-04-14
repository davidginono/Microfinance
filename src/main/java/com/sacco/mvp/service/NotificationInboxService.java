package com.sacco.mvp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.Notification;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationInboxService {
    private final NotificationRepository notificationRepository;
    private final NotificationViewService notificationViewService;
    private final ObjectMapper objectMapper;

    public long unreadCount(UUID memberId, Collection<Position> positions) {
        if (memberId == null || positions == null || positions.isEmpty()) {
            return 0;
        }
        return notificationViewService.toViewsForPosition(
            notificationRepository.findByRecipientMemberIdOrderByCreatedAtDesc(memberId),
            positions
        ).stream().filter(NotificationViewService.NotificationView::isUnread).count();
    }

    public long unreadIncidentCount(UUID memberId) {
        return unreadIncidentViews(memberId).size();
    }

    public List<NotificationViewService.NotificationView> unreadViews(UUID memberId, Collection<Position> positions) {
        if (memberId == null || positions == null || positions.isEmpty()) {
            return Collections.emptyList();
        }
        return notificationViewService.toViewsForPosition(
            notificationRepository.findTop10ByRecipientMemberIdAndReadAtIsNullOrderByCreatedAtDesc(memberId),
            positions
        );
    }

    public List<NotificationViewService.NotificationView> unreadIncidentViews(UUID memberId) {
        if (memberId == null) {
            return Collections.emptyList();
        }
        return notificationViewService.toViews(
            notificationRepository.findTop10ByRecipientMemberIdAndReadAtIsNullOrderByCreatedAtDesc(memberId)
        ).stream()
            .filter(view -> view.getIncidentId() != null)
            .toList();
    }

    public List<NotificationViewService.NotificationView> allViews(UUID memberId, Collection<Position> positions) {
        if (memberId == null || positions == null || positions.isEmpty()) {
            return Collections.emptyList();
        }
        return notificationViewService.toViewsForPosition(
            notificationRepository.findByRecipientMemberIdOrderByCreatedAtDesc(memberId),
            positions
        );
    }

    @Transactional
    public int markAllAsRead(UUID memberId) {
        if (memberId == null) {
            return 0;
        }
        return notificationRepository.markAllAsReadForMember(memberId);
    }

    @Transactional
    public String openForMember(UUID notificationId, UUID memberId, Collection<Position> positions, Position primaryPosition, String defaultTarget) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (!notification.getRecipientMemberId().equals(memberId)) {
            throw new IllegalArgumentException("Notification not found");
        }
        if (!notificationViewService.isVisibleToPosition(notification.getType(), positions)) {
            throw new IllegalArgumentException("Notification not available");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.now());
            notificationRepository.save(notification);
        }
        return resolveTarget(notification, primaryPosition, defaultTarget);
    }

    private String resolveTarget(Notification notification, Position position, String defaultTarget) {
        Map<String, Object> payload = parse(notification.getPayload());
        Map<String, Object> details = toMap(payload.get("details"));
        String loanId = stringValue(details.get("loanId"));
        String incidentId = stringValue(details.get("incidentId"));

        if (position == Position.ADMIN && !incidentId.isBlank()) {
            return "/admin/incidents/" + incidentId;
        }
        if (!loanId.isBlank()) {
            return switch (position) {
                case ADMIN -> defaultTarget;
                case MANAGER -> "/manager/loan-applications/" + loanId;
                case BOARD -> "/board/loan-applications/" + loanId;
                case CHAIRPERSON -> "/chairperson/manager-decisions";
                case MEMBER -> "/app/loan-applications/" + loanId;
            };
        }
        return defaultTarget;
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            return (Map<String, Object>) raw;
        }
        return Collections.emptyMap();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
