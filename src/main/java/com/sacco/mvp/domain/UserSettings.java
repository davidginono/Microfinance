package com.sacco.mvp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings {
    @Id
    @Column(name = "member_id")
    private UUID memberId;

    @Column(nullable = false)
    private String language;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_prefs", nullable = false, columnDefinition = "jsonb")
    private String notificationPrefs;

    @Builder.Default
    @Column(name = "login_otp_enabled", nullable = false)
    private boolean loginOtpEnabled = true;

    @Builder.Default
    @Column(name = "approval_otp_enabled", nullable = false)
    private boolean approvalOtpEnabled = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
