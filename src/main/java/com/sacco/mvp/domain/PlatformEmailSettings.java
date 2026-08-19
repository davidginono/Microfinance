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
@Table(name = "platform_email_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformEmailSettings {
    public static final String DEFAULT_ID = "DEFAULT";

    @Id
    private String id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(nullable = false, length = 160)
    private String username;

    @Column(name = "password_encrypted", nullable = false, length = 512)
    private String passwordEncrypted;

    @Column(name = "from_address", nullable = false, length = 160)
    private String fromAddress;

    @Column(name = "override_recipient", nullable = false, length = 160)
    private String overrideRecipient;

    @Column(name = "ssl_enabled", nullable = false)
    private boolean sslEnabled;

    @Column(name = "starttls_enabled", nullable = false)
    private boolean starttlsEnabled;

    @Column(name = "connection_timeout_ms", nullable = false)
    private int connectionTimeoutMs;

    @Column(name = "read_timeout_ms", nullable = false)
    private int readTimeoutMs;

    @Column(name = "write_timeout_ms", nullable = false)
    private int writeTimeoutMs;

    @Column(name = "updated_by_member_id")
    private UUID updatedByMemberId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public boolean hasStoredPassword() {
        return passwordEncrypted != null && !passwordEncrypted.isBlank();
    }
}
