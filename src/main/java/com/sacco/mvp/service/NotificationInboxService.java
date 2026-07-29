package com.sacco.mvp.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.Notification;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.repository.LoanApplicationRepository;
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
    private static final List<String> MEMBER_SIDE_HIDDEN_TYPES = List.of("SYSTEM_ALERT", "SUPPORT_MESSAGE");

    private final NotificationRepository notificationRepository;
    private final NotificationViewService notificationViewService;
    private final LoanApplicationRepository loanApplicationRepository;
    private final ObjectMapper objectMapper;
    private final AccessControlService access;

    public long unreadCount(AppUserPrincipal principal) {
        if (principal == null) {
            return 0;
        }
        if (principal.isPlatformIdentity() || principal.isWorkspaceAdminScope()) {
            return notificationRepository.countUnreadExcludingRepaymentSync(principal.getMemberId());
        }
        if (notificationViewService.isMemberSidePrincipal(principal)) {
            return notificationRepository.countUnreadExcludingRepaymentSyncAndTypeNotIn(
                principal.getMemberId(),
                MEMBER_SIDE_HIDDEN_TYPES
            );
        }
        return notificationRepository.countUnreadExcludingRepaymentSync(principal.getMemberId());
    }

    public long unreadIncidentCount(UUID memberId) {
        return unreadIncidentViews(memberId).size();
    }

    public List<NotificationViewService.NotificationView> unreadViews(AppUserPrincipal principal) {
        if (principal == null) {
            return Collections.emptyList();
        }
        return notificationViewService.toViewsForPrincipal(
            notificationRepository.findTop10ByRecipientMemberIdAndReadAtIsNullOrderByCreatedAtDesc(principal.getMemberId()),
            principal
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

    public List<NotificationViewService.HeaderNotificationView> unreadHeaderViews(AppUserPrincipal principal) {
        if (principal == null) {
            return Collections.emptyList();
        }
        return notificationViewService.toHeaderViewsForPrincipal(
            notificationRepository.findTop10ByRecipientMemberIdAndReadAtIsNullOrderByCreatedAtDesc(principal.getMemberId()),
            principal
        );
    }

    public List<NotificationViewService.HeaderNotificationView> unreadIncidentHeaderViews(UUID memberId) {
        if (memberId == null) {
            return Collections.emptyList();
        }
        return notificationViewService.toIncidentHeaderViews(
            notificationRepository.findTop10ByRecipientMemberIdAndReadAtIsNullOrderByCreatedAtDesc(memberId)
        );
    }

    public List<NotificationViewService.NotificationView> allViews(AppUserPrincipal principal) {
        if (principal == null) {
            return Collections.emptyList();
        }
        return notificationViewService.toViewsForPrincipal(
            notificationRepository.findTop50ByRecipientMemberIdOrderByCreatedAtDesc(principal.getMemberId()),
            principal
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
    public String openForMember(UUID notificationId, AppUserPrincipal principal, String defaultTarget) {
        if (principal == null) {
            throw new IllegalArgumentException("Notification not found");
        }
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (!notification.getRecipientMemberId().equals(principal.getMemberId())) {
            throw new IllegalArgumentException("Notification not found");
        }
        if (!notificationViewService.isVisibleToPrincipal(notification, principal)) {
            throw new IllegalArgumentException("Notification not available");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.now());
            notificationRepository.save(notification);
        }
        return resolveTarget(notification, principal, defaultTarget);
    }

    private String resolveTarget(Notification notification, AppUserPrincipal principal, String defaultTarget) {
        Map<String, Object> payload = parse(notification.getPayload());
        Map<String, Object> details = toMap(payload.get("details"));
        String applicationId = resolveApplicationId(details, notification.getRecipientMemberId());
        String incidentId = stringValue(details.get("incidentId"));

        if ((principal.isPlatformIdentity() || principal.isWorkspaceAdminScope())
            && "SUPPORT_MESSAGE".equals(notification.getType())
            && !incidentId.isBlank()) {
            return "/admin/incidents/" + incidentId;
        }
        if (!applicationId.isBlank()) {
            if (principal.isPlatformIdentity() || principal.isWorkspaceAdminScope()) {
                return defaultTarget;
            }
            if (access.canAccessManagerArea(principal)) {
                return "/manager/loan-applications/" + applicationId;
            }
            if (access.canAccessAccountantArea(principal)) {
                return "/accountant/loan-applications/" + applicationId;
            }
            if (access.canAccessDisbursementArea(principal)) {
                return "/disbursement/loan-applications/" + applicationId;
            }
            if (access.canAccessChairpersonArea(principal)) {
                return "/chairperson/loan-applications/" + applicationId;
            }
            if (access.canAccessCreditCommitteeArea(principal)) {
                return "/credit-committee/loan-applications/" + applicationId;
            }
            if (access.canAccessBoardArea(principal)) {
                return "/board/loan-applications/" + applicationId;
            }
            if (access.canAccessLoanOfficerArea(principal)) {
                return "/loan-officer/loan-applications/" + applicationId;
            }
            if (access.canAccessMemberArea(principal)) {
                return "/app/loan-applications/" + applicationId;
            }
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
