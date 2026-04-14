package com.sacco.mvp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.AdminIncident;
import com.sacco.mvp.domain.IncidentSeverity;
import com.sacco.mvp.domain.IncidentStatus;
import com.sacco.mvp.domain.Notification;
import com.sacco.mvp.domain.NotificationStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.repository.AdminIncidentRepository;
import com.sacco.mvp.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAlertService {
    private final RoleDirectoryService roleDirectoryService;
    private final NotificationRepository notificationRepository;
    private final AdminIncidentRepository adminIncidentRepository;
    private final ObjectMapper objectMapper;

    public void alertAllAdmins(String source, String subject, String message, Map<String, Object> details) {
        List<RoleDirectoryService.RoleAccountRef> admins = roleDirectoryService.activeGlobalByRole(Position.ADMIN);
        notifyAdmins(admins, "SYSTEM_ALERT", source, subject, message, IncidentSeverity.HIGH, null, null, details);
    }

    public void alertSaccoAdmins(String saccoId, String source, String subject, String message, Map<String, Object> details) {
        List<RoleDirectoryService.RoleAccountRef> admins = roleDirectoryService.activeByRole(saccoId, Position.ADMIN);
        notifyAdmins(admins, "SYSTEM_ALERT", source, subject, message, IncidentSeverity.HIGH, saccoId, null, details);
    }

    private void notifyAdmins(List<RoleDirectoryService.RoleAccountRef> admins, String type, String source, String subject, String message,
                              IncidentSeverity severity, String saccoId, UUID reportedByMemberId, Map<String, Object> details) {
        if (admins == null || admins.isEmpty()) {
            return;
        }
        AdminIncident incident = openIncident(saccoId, reportedByMemberId, type, source, subject, message, severity, details);
        Map<String, Object> enrichedDetails = new LinkedHashMap<>();
        if (details != null) {
            enrichedDetails.putAll(details);
        }
        enrichedDetails.put("incidentId", incident.getId().toString());
        String payload = buildPayload(source, subject, message, null, null, enrichedDetails);
        OffsetDateTime now = OffsetDateTime.now();
        for (RoleDirectoryService.RoleAccountRef admin : admins) {
            try {
                Notification notification = notificationRepository.save(Notification.builder()
                    .id(UUID.randomUUID())
                    .recipientMemberId(admin.getId())
                    .type(type)
                    .payload(payload)
                    .status(NotificationStatus.SENT)
                    .createdAt(now)
                    .sentAt(now)
                    .build());
                if (incident.getRelatedNotificationId() == null) {
                    incident.setRelatedNotificationId(notification.getId());
                    adminIncidentRepository.save(incident);
                }
            } catch (Exception ex) {
                log.error("Failed to write admin alert {} for admin {}", subject, admin.getIdentifier(), ex);
            }
        }
    }

    public AdminIncident openSupportIncident(String saccoId, UUID memberId, String source, String subject, String message,
                                             IncidentSeverity severity, Map<String, Object> details) {
        List<RoleDirectoryService.RoleAccountRef> admins = roleDirectoryService.activeByRole(saccoId, Position.ADMIN);
        notifyAdmins(admins, "SUPPORT_MESSAGE", source, subject, message, severity, saccoId, memberId, details);
        return adminIncidentRepository.findBySaccoIdOrderByCreatedAtDesc(saccoId).stream()
            .filter(item -> subject.equals(item.getSubject()) && message.equals(item.getMessage()))
            .findFirst()
            .orElse(null);
    }

    public AdminIncident openIncident(String saccoId, UUID reportedByMemberId, String category, String source,
                                      String subject, String message, IncidentSeverity severity, Map<String, Object> details) {
        OffsetDateTime now = OffsetDateTime.now();
        AdminIncident incident = AdminIncident.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .reportedByMemberId(reportedByMemberId)
            .category(category)
            .source(source)
            .subject(subject)
            .message(message)
            .severity(severity == null ? IncidentSeverity.MEDIUM : severity)
            .status(IncidentStatus.OPEN)
            .detailsJson(toJson(details))
            .createdAt(now)
            .updatedAt(now)
            .build();
        return adminIncidentRepository.save(incident);
    }

    public String buildPayload(String source, String subject, String message, UUID senderId, String senderName,
                               Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", source);
        payload.put("subject", subject);
        payload.put("message", message);
        if (senderId != null) {
            payload.put("senderId", senderId.toString());
        }
        if (senderName != null && !senderName.isBlank()) {
            payload.put("senderName", senderName);
        }
        if (details != null && !details.isEmpty()) {
            payload.put("details", details);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"subject\":\"System Alert\",\"message\":\"Unable to serialize payload\"}";
        }
    }

    private String toJson(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}

