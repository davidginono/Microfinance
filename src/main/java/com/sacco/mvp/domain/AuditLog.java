
package com.sacco.mvp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
    name = "audit_log",
    indexes = {
        @Index(name = "idx_audit_log_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    private static final DateTimeFormatter DISPLAY_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Africa/Nairobi");

    @Id
    private UUID id;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    private String action;

    @Column(name = "actor_member_id")
    private UUID actorMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_status")
    private AuditEventStatus eventStatus;

    @Column(name = "sacco_id")
    private String saccoId;

    @Column(name = "station_id")
    private String stationId;

    @Column(name = "action_description")
    private String actionDescription;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_value")
    private String referenceValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "jsonb")
    private String beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "jsonb")
    private String afterState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_metadata", columnDefinition = "jsonb")
    private String requestMetadata;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Transient
    public String getDisplayAction() {
        if (actionDescription != null && !actionDescription.isBlank()) {
            return actionDescription;
        }
        if (action == null || action.isBlank()) {
            return "System activity";
        }
        return switch (normalizeKey(action)) {
            case "WEBFINANCIALPREVIEW" -> "Loan calculations loaded";
            case "WEBDECIDE" -> "Review decision submitted";
            case "WEBFINALIZE" -> "Final loan decision completed";
            case "WEBRETRYOUTBOX" -> "Outbox event retried";
            case "WEBUPDATEINCIDENT" -> "Incident updated";
            case "WEBBROADCAST" -> "Broadcast message sent";
            case "WEBREPLY" -> "Reply sent to member";
            case "WEBCREATEUSER" -> "Staff account created";
            case "WEBUPDATEUSER" -> "User access updated";
            case "WEBUPDATELOANPRODUCT" -> "Loan product updated";
            case "WEBSENDSUPPORT" -> "Support message submitted";
            case "WEBAPPROVEREQUEST" -> "Guarantor request approved";
            case "WEBREJECTREQUEST" -> "Guarantor request rejected";
            case "WEBUNDOGUARANTORDECISION" -> "Guarantor decision reversed";
            case "WEBCREATDRAFT", "WEBCREATEDRAFT" -> "Loan draft saved";
            case "WEBSUBMIT" -> "Loan application submitted";
            case "WEBCANCELSUBMISSION" -> "Loan submission moved back to draft";
            case "WEBDELETEAPPLICATION" -> "Loan application removed";
            case "WEBMARKALLNOTIFICATIONSREAD" -> "Notifications marked as read";
            case "WEBREQUESTMEMBERLOGINOTP", "WEBVERIFYMEMBERLOGINOTP" -> "Member sign-in requested";
            case "WEBREQUESTSTAFFLOGINOTP", "WEBVERIFYSTAFFLOGINOTP",
                 "WEBREQUESTNONMEMBERLOGINOTP", "WEBVERIFYNONMEMBERLOGINOTP" -> "Staff sign-in requested";
            case "WEBREQUESTMEMBERREGISTRATIONOTP", "WEBREGISTERMEMBERSUBMIT" -> "Member registration processed";
            case "WEBREQUESTAPPLICANTSIGNATUREOTP" -> "Applicant signature OTP requested";
            case "WEBREQUESTGUARANTORSIGNATUREOTP" -> "Guarantor signature OTP requested";
            case "WEBMARKEXPIREDACTIVELOANCHARTSEEN" -> "Expired loan chart marked as seen";
            case "ADMINCREATESTAFFUSER", "ADMINCREATENONMEMBERUSER" -> "Staff account created by admin";
            case "ADMINUPDATESTAFFUSER", "ADMINUPDATENONMEMBERUSER" -> "Staff account updated by admin";
            case "ADMINUPDATEMEMBER" -> "Member access updated by admin";
            case "ADMINUPDATELOANPRODUCT" -> "Loan product settings updated";
            case "ADMINUPDATEBOARDREVIEWREQUIREMENT" -> "Board review requirement updated";
            case "ADMINUPDATEAPPROVALFLOWCONFIGURATION" -> "Approval flow updated";
            case "ADMINUPDATEINCIDENT" -> "Incident reviewed by admin";
            case "ADMINBROADCAST" -> "Admin broadcast sent";
            case "ADMINREPLYTOMEMBER" -> "Admin replied to member";
            case "MEMBERSUPPORTMESSAGE" -> "Member support request logged";
            case "ADMINRETRYOUTBOX" -> "Failed event queued for retry";
            default -> humanize(action);
        };
    }

    @Transient
    public String getDisplayEntityType() {
        if (entityType == null || entityType.isBlank()) {
            return "System";
        }
        return switch (normalizeKey(entityType)) {
            case "APPCONTROLLER" -> "Member workspace";
            case "ADMINCONTROLLER" -> "Admin tools";
            case "MANAGERCONTROLLER" -> "Manager panel";
            case "BOARDCONTROLLER" -> "Board panel";
            case "LOANOFFICERCONTROLLER" -> "Loan officer panel";
            case "ACCOUNTANTCONTROLLER" -> "Accountant panel";
            case "DISBURSEMENTCONTROLLER" -> "Disbursement panel";
            case "LOANDOCUMENTCONTROLLER" -> "Printable documents";
            case "NONMEMBERUSER", "STAFFUSER" -> "Staff account";
            case "MEMBER" -> "Member account";
            case "LOANPRODUCT" -> "Loan product";
            case "LOANAPPLICATION" -> "Loan application";
            case "SACCOSETTINGS" -> "SACCO settings";
            case "ADMININCIDENT" -> "Admin incident";
            case "NOTIFICATION" -> "Notification";
            case "SUPPORT" -> "Support";
            case "OUTBOX" -> "Outbox event";
            case "REPORT" -> "Report";
            case "SMSUNITS" -> "SMS units";
            case "SMSSETTINGS" -> "SMS settings";
            default -> humanize(entityType);
        };
    }

    @Transient
    public String getShortEntityReference() {
        if (referenceValue != null && !referenceValue.isBlank()) {
            return referenceValue;
        }
        return entityId == null ? "-" : "#" + entityId.toString().substring(0, 8);
    }

    @Transient
    public String getActorReferenceLabel() {
        return actorMemberId == null ? "System" : actorMemberId.toString().substring(0, 8);
    }

    @Transient
    public String getActorMemberIdText() {
        return actorMemberId == null ? "" : actorMemberId.toString();
    }

    @Transient
    public String getDisplayStatus() {
        return eventStatus == AuditEventStatus.FAIL ? "Fail" : "Success";
    }

    @Transient
    public String getStatusBadgeClass() {
        return eventStatus == AuditEventStatus.FAIL
            ? "bg-rose-50 text-rose-700 border-rose-200"
            : "bg-emerald-50 text-emerald-700 border-emerald-200";
    }

    @Transient
    public String getSaccoReferenceLabel() {
        return saccoId == null || saccoId.isBlank() ? "-" : saccoId;
    }

    @Transient
    public String getCreatedAtLabel() {
        return createdAt == null ? "-" : createdAt.atZoneSameInstant(DISPLAY_ZONE).format(DISPLAY_TIMESTAMP);
    }

    private String humanize(String raw) {
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
}
