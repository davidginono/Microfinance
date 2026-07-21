package com.sacco.mvp.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.domain.AuditLog;
import com.sacco.mvp.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {
    private static final String EXPLICIT_AUDIT_LOGGED =
        AuditService.class.getName() + ".explicitAuditLogged";
    private static final int MAX_LABEL_LENGTH = 255;

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void log(String entityType, UUID entityId, String action, UUID actorMemberId, Object beforeState, Object afterState) {
        Map<String, Object> before = asMap(beforeState);
        Map<String, Object> after = asMap(afterState);
        AuditEventStatus status = resolveStatus(before, after);
        String actionDescription = firstNonBlank(after, before, "auditActionDescription", "actionDescription");
        String referenceType = firstNonBlank(after, before, "auditReferenceType", "referenceType");
        String referenceValue = firstNonBlank(after, before, "auditReferenceValue", "referenceValue");
        String saccoId = firstNonBlank(after, before, "saccoId");
        String stationId = firstNonBlank(after, before, "stationId");

        save(entityType, entityId, action, actorMemberId, beforeState, afterState, status,
            actionDescription, referenceType, referenceValue, saccoId, stationId);
    }

    public void logEvent(String entityType,
                         UUID entityId,
                         String action,
                         UUID actorMemberId,
                         AuditEventStatus status,
                         String actionDescription,
                         String referenceType,
                         String referenceValue,
                         String saccoId,
                         String stationId,
                         Object details) {
        save(entityType, entityId, action, actorMemberId, null, details,
            status == null ? AuditEventStatus.SUCCESS : status,
            actionDescription, referenceType, referenceValue, saccoId, stationId);
    }

    public boolean hasCurrentRequestAuditMarker() {
        HttpServletRequest request = currentRequest();
        return request != null && Boolean.TRUE.equals(request.getAttribute(EXPLICIT_AUDIT_LOGGED));
    }

    private void save(String entityType,
                      UUID entityId,
                      String action,
                      UUID actorMemberId,
                      Object beforeState,
                      Object afterState,
                      AuditEventStatus status,
                      String actionDescription,
                      String referenceType,
                      String referenceValue,
                      String saccoId,
                      String stationId) {
        markCurrentRequestAudited();
        auditLogRepository.save(AuditLog.builder()
            .id(UUID.randomUUID())
            .entityType(entityType)
            .entityId(entityId)
            .action(action)
            .actorMemberId(actorMemberId)
            .eventStatus(status == null ? AuditEventStatus.SUCCESS : status)
            .saccoId(trim(saccoId, MAX_LABEL_LENGTH))
            .stationId(trim(stationId, MAX_LABEL_LENGTH))
            .actionDescription(trim(resolveActionDescription(action, actionDescription), MAX_LABEL_LENGTH))
            .referenceType(trim(resolveReferenceType(entityType, referenceType), 80))
            .referenceValue(trim(resolveReferenceValue(entityType, entityId, referenceValue, asMap(beforeState), asMap(afterState)), MAX_LABEL_LENGTH))
            .beforeState(toJson(beforeState))
            .afterState(toJson(afterState))
            .requestMetadata(toJson(requestMetadata()))
            .createdAt(OffsetDateTime.now())
            .build());
    }

    private AuditEventStatus resolveStatus(Map<String, Object> before, Map<String, Object> after) {
        String explicitStatus = firstNonBlank(after, before, "eventStatus");
        if (explicitStatus != null) {
            String normalized = explicitStatus.trim().toUpperCase(Locale.ROOT);
            if (normalized.contains("FAIL") || normalized.contains("ERROR") || normalized.contains("REJECTED")) {
                return AuditEventStatus.FAIL;
            }
            if (normalized.contains("SUCCESS") || normalized.contains("OK")) {
                return AuditEventStatus.SUCCESS;
            }
        }
        String result = firstNonBlank(after, before, "result", "valid");
        if (result != null) {
            String normalized = result.trim().toUpperCase(Locale.ROOT);
            if (normalized.contains("ERROR") || normalized.contains("FAIL") || normalized.equals("FALSE")) {
                return AuditEventStatus.FAIL;
            }
        }
        return AuditEventStatus.SUCCESS;
    }

    private String resolveActionDescription(String action, String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        String key = normalizeKey(action);
        return switch (key) {
            case "LOGIN", "WEBLOGIN" -> "Login";
            case "LOGOUT" -> "Logout";
            case "WEBCREATEDRAFT", "WEBCREATDRAFT" -> "Loan application draft";
            case "WEBSUBMIT", "LOANAPPLICATIONSUBMITTED" -> "Loan application submitted";
            case "WEBCANCELSUBMISSION" -> "Loan submission moved back to draft";
            case "WEBDELETEAPPLICATION" -> "Loan application removed";
            case "WEBAPPROVEREQUEST" -> "Guarantor request approved";
            case "WEBREJECTREQUEST" -> "Guarantor request rejected";
            case "WEBUNDOGUARANTORDECISION" -> "Guarantor decision reversed";
            case "WEBREQUESTMEMBERLOGINOTP", "WEBREQUESTSTAFFLOGINOTP",
                 "WEBREQUESTNONMEMBERLOGINOTP", "WEBREQUESTAPPLICANTSIGNATUREOTP",
                 "WEBREQUESTGUARANTORSIGNATUREOTP", "WEBREQUESTPAYMENTDETAILSOTP",
                 "WEBREQUESTDECISIONOTP", "WEBREQUESTMEMBERREGISTRATIONOTP" -> "OTP request";
            case "WEBVERIFYMEMBERLOGINOTP", "WEBVERIFYSTAFFLOGINOTP",
                 "WEBVERIFYNONMEMBERLOGINOTP", "WEBVERIFYAPPLICANTSIGNATUREOTP",
                 "WEBVERIFYGUARANTORSIGNATUREOTP", "WEBVERIFYPAYMENTDETAILSOTP",
                 "WEBVERIFYDECISIONOTP" -> "OTP verification";
            case "WEBREGISTERMEMBERSUBMIT" -> "Registration";
            case "WEBREQUESTPASSWORDRESETOTP" -> "Password reset request";
            case "WEBSAVEPASSWORDRESET" -> "Password reset saved";
            case "ADMINCREATESTAFFUSER", "ADMINCREATENONMEMBERUSER" -> "Staff account created";
            case "ADMINUPDATESTAFFUSER", "ADMINUPDATENONMEMBERUSER", "ADMINUPDATEMEMBER" -> "User access updated";
            case "ADMINRETRYOUTBOX" -> "Outbox event retried";
            case "ADMINCREATELOANPRODUCT" -> "Loan product created";
            case "ADMINUPDATELOANPRODUCT" -> "Loan product updated";
            case "ADMINARCHIVELOANPRODUCT" -> "Loan product archived";
            case "PLATFORMSUSPENDSTATIONACCESS" -> "Station access suspended";
            case "PLATFORMRESTORESTATIONACCESS" -> "Station access restored";
            case "SMSUNITSALLOCATED" -> "SMS units allocated";
            case "SMSTHRESHOLDSUPDATED" -> "SMS thresholds updated";
            case "REPORTEXPORTED" -> "Report exported";
            case "LOANAPPLICATIONDOCUMENTEXPORTED" -> "Loan application document exported";
            case "ATTACHMENTDOWNLOADED" -> "Attachment downloaded";
            default -> humanize(action == null || action.isBlank() ? "System activity" : action);
        };
    }

    private String resolveReferenceType(String entityType, String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        return switch (normalizeKey(entityType)) {
            case "MEMBER" -> "MEMBER";
            case "STAFFUSER", "NONMEMBERUSER" -> "STAFF";
            case "LOANAPPLICATION" -> "LOAN_APPLICATION";
            case "LOANPRODUCT" -> "LOAN_PRODUCT";
            case "SACCOSTATION" -> "STATION";
            case "OUTBOX" -> "OUTBOX";
            default -> entityType == null || entityType.isBlank() ? "SYSTEM" : entityType;
        };
    }

    private String resolveReferenceValue(String entityType,
                                         UUID entityId,
                                         String explicit,
                                         Map<String, Object> before,
                                         Map<String, Object> after) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        String applicationNumber = firstNonBlank(after, before, "applicationNumber");
        if (applicationNumber != null) {
            return "Loan Application #" + applicationNumber;
        }
        String loanId = firstNonBlank(after, before, "loanId");
        if (loanId != null) {
            return "Loan ID " + loanId;
        }
        String memberNo = firstNonBlank(after, before, "memberNo", "recipientMemberNo");
        if (memberNo != null) {
            return (normalizeKey(entityType).contains("STAFF") ? "Staff " : "Member ") + memberNo;
        }
        String reportName = firstNonBlank(after, before, "reportName");
        if (reportName != null) {
            return "Report " + reportName;
        }
        String station = firstNonBlank(after, before, "stationId");
        if (station != null && "SACCOSTATION".equals(normalizeKey(entityType))) {
            return "Station " + station;
        }
        return entityId == null ? "-" : "#" + entityId.toString().substring(0, 8);
    }

    private Map<String, Object> requestMetadata() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("ipAddress", clientIp(request));
        metadata.put("userAgentFamily", userAgentFamily(request.getHeader("User-Agent")));
        metadata.put("requestId", requestId(request));
        return metadata;
    }

    private void markCurrentRequestAudited() {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            request.setAttribute(EXPLICIT_AUDIT_LOGGED, Boolean.TRUE);
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return trim(forwarded.split(",")[0].trim(), 80);
        }
        return trim(request.getRemoteAddr(), 80);
    }

    private String requestId(HttpServletRequest request) {
        String requestId = firstHeader(request, "X-Request-ID", "X-Correlation-ID", "X-Amzn-Trace-Id");
        if (requestId == null) {
            Object existing = request.getAttribute("requestId");
            requestId = existing instanceof String value && !value.isBlank() ? value : UUID.randomUUID().toString();
            request.setAttribute("requestId", requestId);
        }
        return trim(requestId, 120);
    }

    private String firstHeader(HttpServletRequest request, String... headers) {
        for (String header : headers) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String userAgentFamily(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }
        String lower = userAgent.toLowerCase(Locale.ROOT);
        if (lower.contains("edg/")) {
            return "Edge";
        }
        if (lower.contains("chrome/") || lower.contains("chromium/")) {
            return "Chrome";
        }
        if (lower.contains("firefox/")) {
            return "Firefox";
        }
        if (lower.contains("safari/")) {
            return "Safari";
        }
        return "Other";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (key != null) {
                    copy.put(String.valueOf(key), item);
                }
            });
            return copy;
        }
        return Map.of();
    }

    private String firstNonBlank(Map<String, Object> primary, Map<String, Object> secondary, String... keys) {
        for (String key : keys) {
            String value = stringValue(primary.get(key));
            if (value != null) {
                return value;
            }
        }
        for (String key : keys) {
            String value = stringValue(secondary.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            return String.valueOf(value);
        }
    }

    private String humanize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "System activity";
        }
        String spaced = raw
            .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
            .replace('_', ' ')
            .trim();
        String[] parts = spaced.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.length() == 0 ? raw : builder.toString();
    }

    private String normalizeKey(String raw) {
        return raw == null ? "" : raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
