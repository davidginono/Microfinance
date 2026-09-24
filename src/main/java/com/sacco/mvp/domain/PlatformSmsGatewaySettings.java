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
@Table(name = "platform_sms_gateway_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformSmsGatewaySettings {
    public static final String DEFAULT_ID = "DEFAULT";

    @Id
    private String id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "base_url", nullable = false, length = 255)
    private String baseUrl;

    @Column(name = "send_path", nullable = false, length = 255)
    private String sendPath;

    @Column(name = "client_id", nullable = false, length = 120)
    private String clientId;

    @Column(name = "api_key_encrypted", nullable = false, length = 512)
    private String apiKeyEncrypted;

    @Column(name = "sender_id", nullable = false, length = 40)
    private String senderId;

    @Column(name = "connect_timeout_seconds", nullable = false)
    private int connectTimeoutSeconds;

    @Column(name = "read_timeout_seconds", nullable = false)
    private int readTimeoutSeconds;

    @Column(name = "updated_by_member_id")
    private UUID updatedByMemberId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public boolean hasStoredApiKey() {
        return apiKeyEncrypted != null && !apiKeyEncrypted.isBlank();
    }
}
