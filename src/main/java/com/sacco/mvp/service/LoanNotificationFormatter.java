package com.sacco.mvp.service;

import tools.jackson.databind.JsonNode;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.OutboxEvent;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanNotificationFormatter {
    private static final Set<String> STAFF_ACTION_EVENTS = Set.of(
        "LOAN_READY_FOR_MANAGER",
        "LOAN_OFFICER_REVIEW_ASSIGNED",
        "CHAIRPERSON_REVIEW_ASSIGNED",
        "BOARD_REVIEW_ASSIGNED",
        "CREDIT_COMMITTEE_REVIEW_ASSIGNED",
        "LOAN_READY_FOR_ACCOUNTANT",
        "LOAN_READY_FOR_DISBURSEMENT"
    );
    private static final Set<String> APPLICANT_STATUS_EVENTS = Set.of(
        "MANAGER_REJECTED",
        "MANAGER_ACCEPTED",
        "LOAN_OFFICER_REJECTED",
        "LOAN_OFFICER_APPROVED",
        "CHAIRPERSON_REJECTED",
        "CHAIRPERSON_APPROVED",
        "BOARD_REJECTED",
        "BOARD_APPROVED",
        "CREDIT_COMMITTEE_REJECTED",
        "CREDIT_COMMITTEE_APPROVED",
        "ACCOUNTANT_REJECTED",
        "ACCOUNTANT_APPROVED",
        "REJECTED",
        "DISBURSED",
        "DEFAULTED",
        "PAID",
        "LOAN_GUARANTORS_APPROVED"
    );
    private final LoanApplicationRepository loanApplicationRepository;
    private final MemberRepository memberRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public NotificationDeliveryService.DeliveryContent format(OutboxEvent event,
                                                              JsonNode payload,
                                                              NotificationDeliveryService.DeliveryContent fallback) {
        if (event == null) {
            return fallback;
        }
        if ("GUARANTOR_REQUEST".equals(event.getAggregateType())) {
            return guarantorRequestContent(event, payload, fallback);
        }
        if ("REVERSAL_REQUEST".equals(event.getAggregateType())) {
            return reversalRequestContent(event, payload, fallback);
        }
        if (!"LOAN".equals(event.getAggregateType())) {
            return fallback;
        }
        String eventType = event.getEventType();
        boolean staffAction = STAFF_ACTION_EVENTS.contains(eventType);
        boolean applicantStatus = isApplicantStatusEvent(eventType);
        if (!staffAction && !applicantStatus) {
            return fallback;
        }
        Optional<LoanApplication> loan = loanApplicationRepository.findById(event.getAggregateId());
        if (loan.isEmpty()) {
            return fallback;
        }
        LoanApplication app = loan.get();
        Member applicant = memberRepository.findById(app.getApplicantMemberId()).orElse(null);
        return staffAction
            ? staffContent(app, applicant, eventType)
            : applicantContent(app, eventType, payload);
    }

    private NotificationDeliveryService.DeliveryContent guarantorRequestContent(OutboxEvent event,
                                                                                JsonNode payload,
                                                                                NotificationDeliveryService.DeliveryContent fallback) {
        Optional<LoanApplication> loan = loanFromDetails(payload);
        if (loan.isEmpty()) {
            return fallback;
        }
        LoanApplication app = loan.get();
        String eventType = event.getEventType();
        UUID actorId = uuid(payloadText(payload, "actorId"));
        String guarantor = memberLabel(actorId, "Guarantor");
        Map<String, String> rows = baseLoanRows(app);
        String url = absoluteUrl("/app/loan-applications/" + app.getId());

        if ("GUARANTOR_REQUEST_ASSIGNED".equals(eventType)) {
            rows.put("Applicant", memberLabel(app.getApplicantMemberId(), "Applicant"));
            return content(
                "New guarantor request",
                "New Guarantor Request",
                "You have a new guarantor request waiting for your decision.",
                rows,
                absoluteUrl("/app/guarantee-requests")
            );
        }
        if ("GUARANTOR_REQUEST_APPROVED".equals(eventType)) {
            rows.put("Guarantor", guarantor);
            return content(
                "Guarantor request approved",
                "Guarantor Request Approved",
                guarantor + " approved your guarantee request.",
                rows,
                url
            );
        }
        if ("GUARANTOR_REQUEST_REJECTED".equals(eventType)) {
            rows.put("Guarantor", guarantor);
            String reason = detailText(payload, "reasons");
            if (!reason.isBlank()) {
                rows.put("Reason", reason);
            }
            return content(
                "Guarantor request rejected",
                "Guarantor Request Rejected",
                guarantor + " rejected your guarantee request.",
                rows,
                url
            );
        }
        return fallback;
    }

    private NotificationDeliveryService.DeliveryContent reversalRequestContent(OutboxEvent event,
                                                                               JsonNode payload,
                                                                               NotificationDeliveryService.DeliveryContent fallback) {
        Optional<LoanApplication> loan = loanFromDetails(payload);
        if (loan.isEmpty()) {
            return fallback;
        }
        LoanApplication app = loan.get();
        String eventType = event.getEventType();
        UUID actorId = uuid(payloadText(payload, "actorId"));
        Map<String, String> rows = baseLoanRows(app);
        String loanUrl = absoluteUrl("/app/loan-applications/" + app.getId());

        if ("GUARANTOR_UNDO_REQUESTED".equals(eventType)) {
            rows.put("Guarantor", memberLabel(actorId, "Guarantor"));
            return content(
                "Guarantor removal requested",
                "Guarantor Removal Requested",
                "A guarantor asked to be removed from your loan application.",
                rows,
                loanUrl
            );
        }
        if ("GUARANTOR_UNDO_APPROVED".equals(eventType)) {
            rows.put("Applicant", memberLabel(actorId, "Applicant"));
            return content(
                "Guarantor removal approved",
                "Guarantor Removal Approved",
                "The applicant approved your request to be removed from this loan.",
                rows,
                absoluteUrl("/app/guarantee-requests")
            );
        }
        if ("GUARANTOR_UNDO_REJECTED".equals(eventType)) {
            rows.put("Applicant", memberLabel(actorId, "Applicant"));
            return content(
                "Guarantor removal declined",
                "Guarantor Removal Declined",
                "The applicant kept you on this loan as an active guarantor.",
                rows,
                absoluteUrl("/app/guarantee-requests")
            );
        }
        if ("MANAGER_REVERSAL_REQUESTED".equals(eventType)) {
            rows.put("Applicant", memberLabel(actorId, "Applicant"));
            return content(
                "Application removal requested",
                "Application Removal Requested",
                "An applicant asked for manager approval to remove a loan still under manager review.",
                rows,
                loanUrl
            );
        }
        if ("MANAGER_REVERSAL_APPROVED".equals(eventType) || "MANAGER_REVERSAL_REJECTED".equals(eventType)) {
            boolean approved = "MANAGER_REVERSAL_APPROVED".equals(eventType);
            rows.put("Manager", memberLabel(actorId, "Manager"));
            return content(
                approved ? "Application removal approved" : "Application removal declined",
                approved ? "Application Removal Approved" : "Application Removal Declined",
                approved
                    ? "The manager approved your request and removed the application from review."
                    : "The manager declined your application removal request.",
                rows,
                loanUrl
            );
        }
        return fallback;
    }

    private NotificationDeliveryService.DeliveryContent staffContent(LoanApplication app,
                                                                     Member applicant,
                                                                     String eventType) {
        ApprovalWorkflowStage stage = reviewStage(eventType);
        String stageLabel = stage == null ? "Staff" : stage.getDisplayLabel();
        String subject = "Loan application review required";
        String title = "Loan Application Review Required By " + stageLabel;
        String summary = "A loan application is now ready for your " + stageLabel.toLowerCase(Locale.ENGLISH) + " action.";
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("Loan Application ID", applicationReference(app));
        rows.put("Applicant", applicantLabel(applicant));
        rows.put("Loan Product", loanProductLabel(app));
        rows.put("Amount", amountLabel(app.getAmount()));
        rows.put("Review Stage", stageLabel);
        String url = absoluteUrl(staffDetailPath(stage, app));
        return content(subject, title, summary, rows, url);
    }

    private NotificationDeliveryService.DeliveryContent applicantContent(LoanApplication app,
                                                                         String eventType,
                                                                         JsonNode payload) {
        LoanStatus status = statusForApplicantEvent(app, eventType);
        String statusLabel = statusLabel(status);
        String subject = "Loan application status updated";
        String title = "Loan Application Status Updated";
        String summary = "Your loan application status changed to " + statusLabel + ".";
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("Loan Application ID", applicationReference(app));
        rows.put("Loan Product", loanProductLabel(app));
        rows.put("Amount", amountLabel(app.getAmount()));
        rows.put("Status", statusLabel);
        String reason = reason(payload);
        if (!reason.isBlank()) {
            rows.put("Reason", reason);
        }
        String url = absoluteUrl("/app/loan-applications/" + app.getId());
        return content(subject, title, summary, rows, url);
    }

    private NotificationDeliveryService.DeliveryContent content(String subject,
                                                                String title,
                                                                String summary,
                                                                Map<String, String> rows,
                                                                String url) {
        String plainText = plainText(title, summary, rows, url);
        String html = html(title, summary, rows, url);
        return new NotificationDeliveryService.DeliveryContent(subject, plainText, html, plainText);
    }

    private String plainText(String title, String summary, Map<String, String> rows, String url) {
        StringBuilder builder = new StringBuilder();
        builder.append(title).append("\n\n").append(summary);
        rows.forEach((label, value) -> builder.append("\n").append(label).append(": ").append(value));
        builder.append("\nView Loan Details: ").append(url);
        return builder.toString();
    }

    private String html(String title, String summary, Map<String, String> rows, String url) {
        StringBuilder details = new StringBuilder();
        rows.forEach((label, value) -> details
            .append("<p style=\"margin:0 0 8px 0;font-size:13px;line-height:1.4;color:#1f2937;\">")
            .append("<strong style=\"font-weight:700;color:#111827;\">")
            .append(escape(label))
            .append(":</strong> ")
            .append(escape(value))
            .append("</p>"));
        return """
            <!doctype html>
            <html>
            <body style="margin:0;padding:0;background:#ffffff;font-family:Arial,Helvetica,sans-serif;color:#111827;">
              <div style="max-width:460px;padding:24px;">
                <h1 style="margin:0 0 12px 0;color:#1f5f66;font-size:22px;line-height:1.25;font-weight:700;">%s</h1>
                <p style="margin:0 0 18px 0;color:#374151;font-size:14px;line-height:1.45;">%s</p>
                <div style="border:1px solid #d7e0e5;background:#f8fbfc;border-radius:6px;padding:16px 18px;margin:0 0 18px 0;">%s</div>
                <a href="%s" style="display:inline-block;background:#1f5f66;color:#ffffff;text-decoration:none;border-radius:5px;padding:11px 16px;font-size:13px;font-weight:700;">View Loan Details</a>
              </div>
            </body>
            </html>
            """.formatted(escape(title), escape(summary), details, escape(url));
    }

    private boolean isApplicantStatusEvent(String eventType) {
        return eventType != null
            && (eventType.startsWith("LOAN_STATUS_") || APPLICANT_STATUS_EVENTS.contains(eventType));
    }

    private LoanStatus statusForApplicantEvent(LoanApplication app, String eventType) {
        if ("LOAN_GUARANTORS_APPROVED".equals(eventType)) {
            return LoanStatus.ALL_GUARANTORS_APPROVED;
        }
        if (eventType != null && eventType.startsWith("LOAN_STATUS_")) {
            try {
                return LoanStatus.valueOf(eventType.substring("LOAN_STATUS_".length()));
            } catch (IllegalArgumentException ignored) {
                return app.getStatus();
            }
        }
        try {
            return LoanStatus.valueOf(eventType);
        } catch (Exception ignored) {
            return app.getStatus();
        }
    }

    private ApprovalWorkflowStage reviewStage(String eventType) {
        return switch (eventType) {
            case "LOAN_READY_FOR_MANAGER" -> ApprovalWorkflowStage.MANAGER;
            case "LOAN_OFFICER_REVIEW_ASSIGNED" -> ApprovalWorkflowStage.LOAN_OFFICER;
            case "CHAIRPERSON_REVIEW_ASSIGNED" -> ApprovalWorkflowStage.CHAIRPERSON;
            case "BOARD_REVIEW_ASSIGNED" -> ApprovalWorkflowStage.BOARD;
            case "CREDIT_COMMITTEE_REVIEW_ASSIGNED" -> ApprovalWorkflowStage.CREDIT_COMMITTEE;
            case "LOAN_READY_FOR_ACCOUNTANT" -> ApprovalWorkflowStage.ACCOUNTANT;
            case "LOAN_READY_FOR_DISBURSEMENT" -> ApprovalWorkflowStage.DISBURSEMENT_OFFICER;
            default -> null;
        };
    }

    private String staffDetailPath(ApprovalWorkflowStage stage, LoanApplication app) {
        return "/loan-notifications/" + app.getId() + "/open";
    }

    private String reason(JsonNode payload) {
        JsonNode details = payload == null ? null : payload.get("details");
        JsonNode reasons = details == null ? null : details.get("reasons");
        if (reasons == null || reasons.isNull()) {
            return "";
        }
        return reasons.asString("").trim();
    }

    private Optional<LoanApplication> loanFromDetails(JsonNode payload) {
        UUID loanId = uuid(detailText(payload, "loanId"));
        return loanId == null ? Optional.empty() : loanApplicationRepository.findById(loanId);
    }

    private Map<String, String> baseLoanRows(LoanApplication app) {
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("Loan Application ID", applicationReference(app));
        rows.put("Loan Product", loanProductLabel(app));
        rows.put("Amount", amountLabel(app.getAmount()));
        return rows;
    }

    private String memberLabel(UUID memberId, String fallback) {
        if (memberId == null) {
            return fallback;
        }
        return memberRepository.findById(memberId)
            .map(this::applicantLabel)
            .filter(label -> label != null && !label.isBlank())
            .orElse(fallback);
    }

    private String payloadText(JsonNode payload, String field) {
        JsonNode value = payload == null ? null : payload.get(field);
        return value == null || value.isNull() ? "" : value.asString("").trim();
    }

    private String detailText(JsonNode payload, String field) {
        JsonNode details = payload == null ? null : payload.get("details");
        JsonNode value = details == null ? null : details.get(field);
        return value == null || value.isNull() ? "" : value.asString("").trim();
    }

    private UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String applicationReference(LoanApplication app) {
        if (app.getApplicationNumber() != null) {
            return String.valueOf(app.getApplicationNumber());
        }
        return shortId(String.valueOf(app.getId()));
    }

    private String applicantLabel(Member applicant) {
        if (applicant == null) {
            return "Applicant";
        }
        if (applicant.getMemberNo() == null || applicant.getMemberNo().isBlank()) {
            return applicant.getFullName();
        }
        return applicant.getFullName() + " (" + applicant.getMemberNo() + ")";
    }

    private String loanProductLabel(LoanApplication app) {
        return app.getLoanType() == null ? "Loan Product" : app.getLoanType().getDisplayLabel();
    }

    private String amountLabel(BigDecimal amount) {
        if (amount == null) {
            return "TSh 0";
        }
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return "TSh " + format.format(amount);
    }

    private String statusLabel(LoanStatus status) {
        if (status == null) {
            return "Updated";
        }
        return switch (status) {
            case READY_FOR_MANAGER -> "On Review By Manager";
            case AWAITING_LOAN_OFFICER -> "On Review By Loan Officer";
            case AWAITING_CHAIRPERSON -> "On Review By Chairperson";
            case AWAITING_BOARD -> "On Review By Board";
            case AWAITING_CREDIT_COMMITTEE -> "On Review By Credit Committee";
            case AWAITING_ACCOUNTANT -> "On Review By Accountant";
            case READY_FOR_DISBURSEMENT -> "Ready For Disbursement";
            case ALL_GUARANTORS_APPROVED -> "All Guarantors Approved";
            default -> humanize(status.name());
        };
    }

    private String absoluteUrl(String path) {
        String root = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8080" : baseUrl.trim();
        if (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        return root + path;
    }

    private String humanize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] parts = value.toLowerCase(Locale.ENGLISH).split("_+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() > 8 ? "#" + value.substring(0, 8) : value;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
