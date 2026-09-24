package com.sacco.mvp.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.Notification;
import com.sacco.mvp.domain.NotificationStatus;
import com.sacco.mvp.domain.SmsUnitStatus;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsUsageAlertService {
    private final RoleDirectoryService roleDirectoryService;
    private final NotificationRepository notificationRepository;
    private final NotificationEmailService notificationEmailService;
    private final SmsGateway smsGateway;
    private final SmsUnitTransactionService smsUnitTransactionService;
    private final ObjectMapper objectMapper;

    public void alertStatus(String saccoId, String stationId, SmsUnitStatus status, long availableUnits) {
        if (status == null || status == SmsUnitStatus.HEALTHY) {
            return;
        }
        String subject = status == SmsUnitStatus.DEPLETED ? "Station SMS units depleted" : "Station SMS units running low";
        String message = switch (status) {
            case LOW -> "SMS units for " + saccoId + " / " + stationId + " have reached the low-balance level.";
            case CRITICAL -> "SMS units for " + saccoId + " / " + stationId + " have reached the critical level.";
            case DEPLETED -> "SMS units for " + saccoId + " / " + stationId + " are depleted. Further SMS notifications are blocked.";
            case HEALTHY -> "";
        };
        List<RoleDirectoryService.RoleAccountRef> stationSmsAdmins =
            roleDirectoryService.activeWorkspaceAdminsByAnyClaimInStation(saccoId, stationId, List.of(UserClaim.SMS_USAGE_VIEW));
        notifyRecipients(
            recipientsForStation(saccoId, stationId, stationSmsAdmins),
            subject,
            message,
            Map.of("saccoId", saccoId, "stationId", stationId, "status", status.name(), "availableUnits", availableUnits)
        );
        sendStationFundedSmsAlert(saccoId, stationId, status, message, stationSmsAdmins);
    }

    public void alertInvalidScope(String saccoId, String stationId, String eventType, String reason) {
        notifyRecipients(
            roleDirectoryService.activePlatformAdminsByClaim(UserClaim.SMS_USAGE_VIEW),
            "SMS notification blocked",
            "An SMS notification was blocked because its originating SACCO-station scope was missing or invalid.",
            Map.of(
                "saccoId", valueOrBlank(saccoId),
                "stationId", valueOrBlank(stationId),
                "eventType", valueOrBlank(eventType),
                "reason", valueOrBlank(reason)
            )
        );
    }

    private List<RoleDirectoryService.RoleAccountRef> recipientsForStation(
        String saccoId,
        String stationId,
        List<RoleDirectoryService.RoleAccountRef> stationSmsAdmins
    ) {
        LinkedHashMap<UUID, RoleDirectoryService.RoleAccountRef> recipients = new LinkedHashMap<>();
        roleDirectoryService.activePlatformAdminsByClaim(UserClaim.SMS_USAGE_VIEW).forEach(ref -> recipients.put(ref.getId(), ref));
        stationSmsAdmins.forEach(ref -> recipients.put(ref.getId(), ref));
        return List.copyOf(recipients.values());
    }

    private void sendStationFundedSmsAlert(String saccoId,
                                           String stationId,
                                           SmsUnitStatus status,
                                           String message,
                                           List<RoleDirectoryService.RoleAccountRef> stationSmsAdmins) {
        for (RoleDirectoryService.RoleAccountRef recipient : stationSmsAdmins) {
            if (recipient.getPhoneVerifiedAt() == null || TanzaniaPhoneNumber.normalizeOptional(recipient.getPhone()) == null) {
                continue;
            }
            SmsUnitTransactionService.ReservationResult reservation;
            try {
                reservation = smsUnitTransactionService.reserveAlert(saccoId, stationId, status);
            } catch (Exception ex) {
                log.warn("Unable to reserve station SMS alert unit for {} / {}: {}", saccoId, stationId, ex.getMessage());
                return;
            }
            if (!reservation.reserved()) {
                log.warn("SMS usage alert could not be sent to {}: {}", recipient.getIdentifier(), reservation.reason());
                return;
            }
            SmsSendResult result;
            try {
                result = smsGateway.send(recipient.getPhone(), message);
            } catch (Exception ex) {
                result = SmsSendResult.acceptanceUnknown(ex.getMessage());
            }
            smsUnitTransactionService.completeAlert(reservation.accountId(), reservation.ledgerId(), result);
        }
    }

    private void notifyRecipients(List<RoleDirectoryService.RoleAccountRef> recipients,
                                  String subject,
                                  String message,
                                  Map<String, Object> details) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        String payload = payload(subject, message, details);
        OffsetDateTime now = OffsetDateTime.now();
        LinkedHashSet<UUID> delivered = new LinkedHashSet<>();
        for (RoleDirectoryService.RoleAccountRef recipient : recipients) {
            if (!delivered.add(recipient.getId())) {
                continue;
            }
            try {
                notificationRepository.save(Notification.builder()
                    .id(UUID.randomUUID())
                    .recipientMemberId(recipient.getId())
                    .type("SYSTEM_ALERT")
                    .payload(payload)
                    .status(NotificationStatus.SENT)
                    .createdAt(now)
                    .sentAt(now)
                    .build());
                notificationEmailService.sendNotificationEmail(recipient.getId(), subject, message);
            } catch (Exception ex) {
                log.warn("Unable to deliver SMS usage alert to {}: {}", recipient.getIdentifier(), ex.getMessage());
            }
        }
    }

    private String payload(String subject, String message, Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "source", "SMS Usage Control",
                "subject", subject,
                "message", message,
                "details", details
            ));
        } catch (JacksonException ex) {
            return "{\"source\":\"SMS Usage Control\",\"subject\":\"SMS usage alert\",\"message\":\"Review station SMS usage.\"}";
        }
    }

    private String valueOrBlank(String value) {
        return value == null ? "" : value;
    }
}
