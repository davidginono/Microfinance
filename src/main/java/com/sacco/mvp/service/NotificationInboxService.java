package com.sacco.mvp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.Notification;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationInboxService {
    private static final EnumSet<Position> MEMBER_SIDE_POSITIONS = EnumSet.of(
        Position.MEMBER, Position.MANAGER, Position.ACCOUNTANT, Position.DISBURSEMENT_OFFICER, Position.BOARD, Position.LOAN_OFFICER
    );
    private static final List<String> MEMBER_SIDE_HIDDEN_TYPES = List.of("SYSTEM_ALERT", "SUPPORT_MESSAGE");

    private final NotificationRepository notificationRepository;
    private final NotificationViewService notificationViewService;
    private final LoanApplicationRepository loanApplicationRepository;
    private final ObjectMapper objectMapper;

    public long unreadCount(UUID memberId, Collection<Position> positions) {
        if (memberId == null || positions == null || positions.isEmpty()) {
            return 0;
        }
        if (Position.containsAdminRole(positions)) {
            return notificationRepository.countByRecipientMemberIdAndReadAtIsNull(memberId);
        }
        if (positions.stream().anyMatch(MEMBER_SIDE_POSITIONS::contains)) {
            return notificationRepository.countByRecipientMemberIdAndReadAtIsNullAndTypeNotIn(
                memberId,
                MEMBER_SIDE_HIDDEN_TYPES
            );
        }
        return notificationRepository.countByRecipientMemberIdAndReadAtIsNull(memberId);
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
            .filter(view -> "SUPPORT_MESSAGE".equals(view.getType()) && view.getIncidentId() != null)
            .toList();
    }

    public List<NotificationViewService.NotificationView> allViews(UUID memberId, Collection<Position> positions) {
        if (memberId == null || positions == null || positions.isEmpty()) {
            return Collections.emptyList();
        }
        return notificationViewService.toViewsForPosition(
            notificationRepository.findTop50ByRecipientMemberIdOrderByCreatedAtDesc(memberId),
            positions
        );
    }

    public List<NotificationViewService.NotificationView> allViewsByType(UUID memberId, String type) {
        if (memberId == null || type == null || type.isBlank()) {
            return Collections.emptyList();
        }
        return notificationViewService.toViews(
            notificationRepository.findTop100ByRecipientMemberIdAndTypeOrderByCreatedAtDesc(memberId, type)
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
    public int markAllAsReadByTypes(UUID memberId, Collection<String> types) {
        if (memberId == null || types == null || types.isEmpty()) {
            return 0;
        }
        List<String> normalizedTypes = types.stream()
            .filter(type -> type != null && !type.isBlank())
            .distinct()
            .toList();
        if (normalizedTypes.isEmpty()) {
            return 0;
        }
        return notificationRepository.markAllAsReadForMemberAndTypes(memberId, normalizedTypes);
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
        String applicationId = resolveApplicationId(details, notification.getRecipientMemberId());
        String incidentId = stringValue(details.get("incidentId"));

        if (position != null && position.isAdminRole() && "SUPPORT_MESSAGE".equals(notification.getType()) && !incidentId.isBlank()) {
            return "/admin/incidents/" + incidentId;
        }
        if (!applicationId.isBlank()) {
            return switch (position) {
                case ADMIN, MINOR_ADMIN -> defaultTarget;
                case MANAGER -> "/manager/loan-applications/" + applicationId;
                case ACCOUNTANT -> "/accountant/loan-applications/" + applicationId;
                case DISBURSEMENT_OFFICER -> "/disbursement/loan-applications/" + applicationId;
                case BOARD -> "/board/loan-applications/" + applicationId;
                case LOAN_OFFICER -> "/loan-officer/loan-applications/" + applicationId;
                case MEMBER -> "/app/loan-applications/" + applicationId;
            };
        }
        return defaultTarget;
    }

    private String resolveApplicationId(Map<String, Object> details, UUID recipientMemberId) {
        String applicationId = stringValue(details.get("applicationId"));
        if (isUuid(applicationId)) {
            return applicationId;
        }
        String loanId = stringValue(details.get("loanId"));
        if (isUuid(loanId)) {
            return loanId;
        }
        if (!loanId.isBlank()) {
            return loanApplicationRepository.findFirstByApplicantMemberIdAndLoanIdOrderByCreatedAtDesc(recipientMemberId, loanId)
                .map(LoanApplication::getId)
                .map(UUID::toString)
                .orElse("");
        }
        return "";
    }

    private boolean isUuid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
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
