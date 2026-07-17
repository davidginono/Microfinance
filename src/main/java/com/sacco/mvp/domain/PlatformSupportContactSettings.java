package com.sacco.mvp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "platform_support_contact_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformSupportContactSettings {
    public static final String DEFAULT_ID = "DEFAULT";

    @Id
    private String id;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "display_role", nullable = false, length = 120)
    private String displayRole;

    @Column(name = "phone", nullable = false, length = 40)
    private String phone;

    @Column(name = "email", nullable = false, length = 160)
    private String email;

    @Column(name = "office_hours", nullable = false, length = 120)
    private String officeHours;

    @Column(name = "support_note", nullable = false, length = 280)
    private String supportNote;

    @Column(name = "updated_by_member_id")
    private UUID updatedByMemberId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public boolean isVisible() {
        return hasText(phone) || hasText(email);
    }

    public String getPhoneHref() {
        if (!hasText(phone)) {
            return "";
        }
        String normalized = phone.trim().replaceAll("[^0-9+]", "");
        if (normalized.startsWith("+")) {
            normalized = "+" + normalized.substring(1).replace("+", "");
        } else {
            normalized = normalized.replace("+", "");
        }
        return normalized.isBlank() ? "" : "tel:" + normalized;
    }

    public String getEmailHref() {
        return hasText(email) ? "mailto:" + email.trim().toLowerCase() : "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
