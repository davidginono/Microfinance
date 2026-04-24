package com.sacco.mvp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.Notification;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationViewService {
    private static final EnumSet<Position> MEMBER_SIDE_POSITIONS = EnumSet.of(
        Position.MEMBER, Position.MANAGER, Position.BOARD, Position.CHAIRPERSON
    );
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final ObjectMapper objectMapper;
    private final MemberRepository memberRepository;

    public List<NotificationView> toViews(List<Notification> notifications) {
        if (notifications == null) {
            return Collections.emptyList();
        }
        return notifications.stream().map(this::toView).toList();
    }

    public List<NotificationView> toViewsForPosition(List<Notification> notifications, Collection<Position> positions) {
        return toViews(notifications).stream()
            .filter(view -> isVisibleToPosition(view.type(), positions))
            .toList();
    }

    public boolean isVisibleToPosition(String type, Collection<Position> positions) {
        if (positions == null || positions.isEmpty()) {
            return false;
        }
        if (Position.containsAdminRole(positions)) {
            return true;
        }
        if (positions.stream().anyMatch(MEMBER_SIDE_POSITIONS::contains)) {
            return !"SYSTEM_ALERT".equals(type) && !"SUPPORT_MESSAGE".equals(type);
        }
        return true;
    }

    public NotificationView toView(Notification notification) {
        Map<String, Object> payload = parse(notification.getPayload());
        String subject = stringValue(payload.get("subject"));
        String message = stringValue(payload.get("message"));
        String source = stringValue(payload.get("source"));
        String senderName = stringValue(payload.get("senderName"));
        UUID senderId = parseUuid(payload.get("senderId"));
        UUID incidentId = parseIncidentId(payload.get("details"));

        if (subject.isBlank()) {
            subject = humanizeType(notification.getType());
        }
        if (message.isBlank()) {
            message = fallbackMessage(notification.getType(), payload);
        }
        if (source.isBlank()) {
            source = senderName.isBlank() ? "System" : senderName;
        }

        return new NotificationView(
            notification.getId(),
            notification.getType(),
            subject,
            message,
            source,
            senderName,
            senderId,
            incidentId,
            flattenDetails(payload.get("details")),
            notification.getStatus().name(),
            notification.getCreatedAt() == null ? "" : notification.getCreatedAt().toString(),
            formatCreatedAt(notification.getCreatedAt()),
            notification.getReadAt() == null
        );
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private UUID parseIncidentId(Object detailsObject) {
        Map<String, Object> details = toMap(detailsObject);
        return parseUuid(details.get("incidentId"));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String humanizeType(String type) {
        if (type == null || type.isBlank()) {
            return "Notification";
        }
        return switch (type) {
            case "SUPPORT_MESSAGE" -> "Support Message";
            case "ADMIN_REPLY" -> "Admin Reply";
            case "ADMIN_BROADCAST" -> "Admin Broadcast";
            case "SYSTEM_ALERT" -> "System Alert";
            case "REPAYMENT_REMINDER" -> "Repayment Reminder";
            case "MANAGER_REJECTED" -> "Manager Rejected";
            case "BOARD_APPROVED" -> "Board Approved";
            case "BOARD_REJECTED" -> "Board Rejected";
            case "FINAL_APPROVED" -> "Loan Approved";
            case "DEFAULTED" -> "Loan Defaulted";
            case "PAID" -> "Loan Marked As Paid";
            case "FINAL_REJECTED" -> "Loan Rejected";
            case "LOAN_READY_FOR_MANAGER" -> "On Review By Manager";
            case "LOAN_GUARANTORS_APPROVED" -> "All Guarantors Approved";
            case "GUARANTOR_REQUEST_ASSIGNED" -> "Guarantor Request";
            case "BOARD_REVIEW_ASSIGNED" -> "Board Review Assigned";
            case "GUARANTOR_UNDO_REQUESTED" -> "Guarantor Removal Requested";
            case "GUARANTOR_UNDO_APPROVED" -> "Guarantor Removal Approved";
            case "GUARANTOR_UNDO_REJECTED" -> "Guarantor Removal Rejected";
            case "MANAGER_REVERSAL_REQUESTED" -> "Application Removal Requested";
            case "MANAGER_REVERSAL_APPROVED" -> "Application Removal Approved";
            case "MANAGER_REVERSAL_REJECTED" -> "Application Removal Rejected";
            default -> type.replace('_', ' ');
        };
    }

    private String fallbackMessage(String type, Map<String, Object> payload) {
        Map<String, Object> details = toMap(payload.get("details"));
        return switch (type) {
            case "MANAGER_REJECTED" -> {
                String reasons = stringValue(details.get("reasons"));
                yield reasons.isBlank() ? "Your application was rejected by the manager." : "Manager reason: " + reasons;
            }
            case "BOARD_APPROVED" -> "Your application has passed board review.";
            case "BOARD_REJECTED" -> "Your application was rejected at board review.";
            case "FINAL_APPROVED" -> {
                String finalDueDate = stringValue(details.get("finalDueDate"));
                String firstRepaymentDate = stringValue(details.get("firstRepaymentDate"));
                yield finalDueDate.isBlank()
                    ? "Your loan application has been fully approved."
                    : "Your loan was approved. First repayment: " + firstRepaymentDate + ". Final due date: " + finalDueDate;
            }
            case "PAID" -> {
                String source = stringValue(details.get("source"));
                yield "SYNC".equalsIgnoreCase(source)
                    ? "Your loan was automatically marked as fully paid after repayment sync."
                    : "Your manager marked this disbursed loan as fully paid.";
            }
            case "DEFAULTED" -> "Your loan has passed the final due date and remains unpaid.";
            case "FINAL_REJECTED" -> "Your loan application has been finally rejected.";
            case "LOAN_READY_FOR_MANAGER" -> "Your application is now on review by manager.";
            case "LOAN_GUARANTORS_APPROVED" -> "All selected guarantors have approved your application. Submit it now to send it for manager review.";
            case "GUARANTOR_REQUEST_ASSIGNED" -> "You have a new guarantor request waiting for a decision.";
            case "BOARD_REVIEW_ASSIGNED" -> "A loan application has been assigned to you for board review.";
            case "GUARANTOR_UNDO_REQUESTED" -> "A guarantor asked to be removed from your loan application.";
            case "GUARANTOR_UNDO_APPROVED" -> "The applicant approved your request to be removed from this loan.";
            case "GUARANTOR_UNDO_REJECTED" -> "The applicant kept you on the loan as an active guarantor.";
            case "MANAGER_REVERSAL_REQUESTED" -> "An applicant asked for manager approval to remove a loan that is still under manager review.";
            case "MANAGER_REVERSAL_APPROVED" -> "The manager approved your request and removed the application from review.";
            case "MANAGER_REVERSAL_REJECTED" -> "The manager declined your application removal request.";
            case "REPAYMENT_REMINDER" -> {
                String daysLeft = stringValue(details.get("daysLeft"));
                String dueDate = stringValue(details.get("finalDueDate"));
                yield daysLeft.isBlank()
                    ? "Your loan repayment timeline has an upcoming reminder."
                    : "Final repayment due in " + daysLeft + " day(s). Due date: " + dueDate;
            }
            default -> "Notification received.";
        };
    }

    private String flattenDetails(Object value) {
        Map<String, Object> details = toMap(value);
        if (details.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(formatDetailLabel(entry.getKey())).append(": ").append(formatDetailValue(entry.getKey(), entry.getValue()));
        }
        return builder.toString();
    }

    public List<String> detailItems(String details) {
        if (details == null || details.isBlank()) {
            return Collections.emptyList();
        }
        return java.util.Arrays.stream(details.split("\\s\\|\\s"))
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .toList();
    }

    private String formatDetailLabel(String key) {
        return switch (key) {
            case "managerId" -> "Manager";
            case "loanId" -> "Loan Reference";
            case "finalDueDate" -> "Final Due Date";
            case "firstRepaymentDate" -> "First Repayment Date";
            case "installmentAmount" -> "Installment Amount";
            case "repaymentFrequency" -> "Repayment Frequency";
            case "incidentId" -> "Incident";
            case "reasons" -> "Reason";
            default -> humanizeKey(key);
        };
    }

    private String formatDetailValue(String key, Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        return switch (key) {
            case "managerId", "senderId", "boardMemberId", "recipientMemberId" -> resolveMemberLabel(text);
            case "loanId", "incidentId" -> shortenUuid(text);
            case "installmentAmount" -> text.matches("-?\\d+(\\.\\d+)?") ? "TSh " + text : text;
            case "repaymentFrequency" -> humanizeKey(text);
            default -> text;
        };
    }

    private String resolveMemberLabel(String value) {
        try {
            UUID id = UUID.fromString(value);
            return memberRepository.findById(id)
                .map(member -> member.getMemberNo() + " - " + member.getFullName())
                .orElse(shortenUuid(value));
        } catch (Exception ex) {
            return value;
        }
    }

    private String shortenUuid(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() >= 8 ? "#" + value.substring(0, 8) : value;
    }

    private String humanizeKey(String key) {
        String normalized = key.replace('_', ' ');
        String[] parts = normalized.split("(?=[A-Z])|\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String lower = part.toLowerCase(Locale.ROOT);
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(lower.charAt(0)));
            if (lower.length() > 1) {
                builder.append(lower.substring(1));
            }
        }
        return builder.toString();
    }

    private String formatCreatedAt(OffsetDateTime createdAt) {
        return createdAt == null ? "" : DATE_TIME_FORMATTER.format(createdAt);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, val) -> result.put(String.valueOf(key), val));
            return result;
        }
        return Collections.emptyMap();
    }

    public record NotificationView(
        UUID id,
        String type,
        String subject,
        String message,
        String source,
        String senderName,
        UUID senderId,
        UUID incidentId,
        String details,
        String status,
        String createdAt,
        String createdAtLabel,
        boolean unread
    ) {
        public UUID getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public String getSubject() {
            return subject;
        }

        public String getMessage() {
            return message;
        }

        public String getSource() {
            return source;
        }

        public String getSenderName() {
            return senderName;
        }

        public UUID getSenderId() {
            return senderId;
        }

        public UUID getIncidentId() {
            return incidentId;
        }

        public String getDetails() {
            return details;
        }

        public List<String> getDetailItems() {
            return details == null || details.isBlank()
                ? Collections.emptyList()
                : java.util.Arrays.stream(details.split("\\s\\|\\s"))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .toList();
        }

        public String getStatus() {
            return status;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public String getCreatedAtLabel() {
            return createdAtLabel;
        }

        public boolean isUnread() {
            return unread;
        }
    }
}
