package com.sacco.mvp.service;

import com.sacco.mvp.domain.PlatformSmsGatewaySettings;
import com.sacco.mvp.repository.PlatformSmsGatewaySettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformSmsGatewaySettingsService {
    private static final int URL_MAX = 255;
    private static final int PATH_MAX = 255;
    private static final int CLIENT_ID_MAX = 120;
    private static final int SENDER_ID_MAX = 40;
    private static final int MIN_TIMEOUT_SECONDS = 1;
    private static final int MAX_TIMEOUT_SECONDS = 60;

    private final PlatformSmsGatewaySettingsRepository repository;
    private final PlatformSecretProtectionService secretProtectionService;
    private final ObjectProvider<SmsGateway> smsGatewayProvider;
    private final AuditService auditService;

    @Value("${app.sms.enabled:false}")
    private boolean envEnabled = false;

    @Value("${app.sms.benter.base-url:}")
    private String envBaseUrl = "";

    @Value("${app.sms.benter.send-path:}")
    private String envSendPath = "";

    @Value("${app.sms.benter.client-id:}")
    private String envClientId = "";

    @Value("${app.sms.benter.api-key:}")
    private String envApiKey = "";

    @Value("${app.sms.benter.sender-id:}")
    private String envSenderId = "";

    @Value("${app.sms.connect-timeout:3s}")
    private String envConnectTimeout = "3s";

    @Value("${app.sms.read-timeout:8s}")
    private String envReadTimeout = "8s";

    private volatile ResolvedSmsGatewayConfig cachedConfig;

    @Transactional
    public PlatformSmsGatewaySettings settings() {
        return repository.findById(PlatformSmsGatewaySettings.DEFAULT_ID)
            .orElseGet(() -> repository.save(defaultSettings(OffsetDateTime.now())));
    }

    public ResolvedSmsGatewayConfig resolvedConfig() {
        ResolvedSmsGatewayConfig config = cachedConfig;
        if (config != null) {
            return config;
        }
        return loadAndCache();
    }

    @Transactional
    public PlatformSmsGatewaySettings updateSettings(boolean enabled,
                                                     String baseUrl,
                                                     String sendPath,
                                                     String clientId,
                                                     String apiKey,
                                                     String senderId,
                                                     int connectTimeoutSeconds,
                                                     int readTimeoutSeconds,
                                                     UUID actorMemberId) {
        String normalizedBaseUrl = normalizeText(baseUrl, "Base URL", URL_MAX);
        String normalizedSendPath = normalizeText(sendPath, "Send path", PATH_MAX);
        String normalizedClientId = normalizeText(clientId, "Client ID", CLIENT_ID_MAX);
        String normalizedSenderId = normalizeText(senderId, "Sender ID", SENDER_ID_MAX);
        validateTimeout(connectTimeoutSeconds, "Connect timeout");
        validateTimeout(readTimeoutSeconds, "Read timeout");
        if (enabled && (normalizedBaseUrl.isBlank() || normalizedSendPath.isBlank()
            || normalizedClientId.isBlank() || normalizedSenderId.isBlank())) {
            throw new IllegalArgumentException("Enter the SMS gateway URL, send path, client ID, and sender ID before enabling the gateway.");
        }

        PlatformSmsGatewaySettings settings = settings();
        Map<String, Object> before = snapshot(settings);
        settings.setEnabled(enabled);
        settings.setBaseUrl(normalizedBaseUrl);
        settings.setSendPath(normalizedSendPath);
        settings.setClientId(normalizedClientId);
        settings.setSenderId(normalizedSenderId);
        settings.setConnectTimeoutSeconds(connectTimeoutSeconds);
        settings.setReadTimeoutSeconds(readTimeoutSeconds);
        if (apiKey != null && !apiKey.isBlank()) {
            settings.setApiKeyEncrypted(secretProtectionService.encrypt(apiKey));
        } else if (enabled && !settings.hasStoredApiKey() && blank(envApiKey)) {
            throw new IllegalArgumentException("Enter the SMS API key before enabling the gateway.");
        }
        settings.setUpdatedByMemberId(actorMemberId);
        settings.setUpdatedAt(OffsetDateTime.now());
        PlatformSmsGatewaySettings saved = repository.save(settings);
        cachedConfig = null;
        auditService.log("PLATFORM_SMS_GATEWAY_SETTINGS", null, "ADMIN_UPDATE_SMS_GATEWAY_SETTINGS", actorMemberId, before, snapshot(saved));
        return saved;
    }

    @Transactional
    public void sendTestSms(String phoneNumber, UUID actorMemberId) {
        String normalizedPhone = TanzaniaPhoneNumber.normalizeOptional(phoneNumber);
        if (normalizedPhone == null) {
            throw new IllegalArgumentException("Enter a valid test phone number.");
        }
        ResolvedSmsGatewayConfig config = resolvedConfig();
        if (!config.enabled()) {
            throw new IllegalStateException("SMS gateway is not configured.");
        }
        SmsGateway smsGateway = smsGatewayProvider.getIfAvailable();
        if (smsGateway == null) {
            throw new IllegalStateException("SMS gateway is not configured.");
        }
        SmsSendResult result;
        try {
            result = smsGateway.send(phoneNumber, "SACCO platform SMS test");
        } catch (RuntimeException ex) {
            log.warn("Platform SMS test failed: {}", ex.getMessage());
            throw new IllegalStateException(describeTestSendFailure(ex), ex);
        }
        if (!result.sent()) {
            throw new IllegalStateException(describeTestSendFailure(result));
        }
        auditService.log("PLATFORM_SMS_GATEWAY_SETTINGS", null, "ADMIN_TEST_SMS", actorMemberId, Map.of("phone", normalizedPhone), Map.of());
    }

    public static String describeTestSendFailure(Throwable ex) {
        if (causedBy(ex, SocketTimeoutException.class) || messageContains(ex, "timed out", "connect timed out")) {
            return "Test SMS could not be sent. The SMS gateway connection timed out.";
        }
        if (causedBy(ex, ConnectException.class) || messageContains(ex, "connection refused", "could not connect", "i/o error")) {
            return "Test SMS could not be sent. The SMS gateway could not be reached.";
        }
        return "Test SMS could not be sent. Check the SMS gateway URL and credentials.";
    }

    static String describeTestSendFailure(SmsSendResult result) {
        String raw = result == null || result.message() == null ? "" : result.message().trim();
        String lower = raw.toLowerCase();
        if (lower.contains("timed out") || lower.contains("timeout")) {
            return "Test SMS could not be sent. The SMS gateway connection timed out.";
        }
        if (lower.contains("could not be reached") || lower.contains("unknown") || lower.contains("empty")) {
            return "Test SMS could not be sent. The SMS gateway could not be reached.";
        }
        if (!raw.isBlank() && raw.length() <= 180 && !lower.contains("exception")) {
            String detail = stripLeadingTestSmsPrefix(raw);
            return "Test SMS could not be sent. " + (detail.endsWith(".") ? detail : detail + ".");
        }
        return "Test SMS could not be sent. The SMS gateway returned an error.";
    }

    private static String stripLeadingTestSmsPrefix(String message) {
        String prefix = "Test SMS could not be sent. ";
        if (message.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return message.substring(prefix.length()).trim();
        }
        return message;
    }

    private static boolean causedBy(Throwable ex, Class<? extends Throwable> type) {
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth < 8) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }

    private static boolean messageContains(Throwable ex, String... snippets) {
        StringBuilder text = new StringBuilder();
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth < 8) {
            if (current.getMessage() != null) {
                if (text.length() > 0) {
                    text.append(' ');
                }
                text.append(current.getMessage());
            }
            current = current.getCause();
            depth++;
        }
        String combined = text.toString().toLowerCase();
        for (String snippet : snippets) {
            if (combined.contains(snippet.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private synchronized ResolvedSmsGatewayConfig loadAndCache() {
        ResolvedSmsGatewayConfig config = cachedConfig;
        if (config != null) {
            return config;
        }
        PlatformSmsGatewaySettings settings = repository.findById(PlatformSmsGatewaySettings.DEFAULT_ID)
            .orElseGet(() -> defaultSettings(OffsetDateTime.now()));
        config = toResolved(settings);
        cachedConfig = config;
        return config;
    }

    private ResolvedSmsGatewayConfig toResolved(PlatformSmsGatewaySettings settings) {
        boolean usingDatabase = !blank(settings.getBaseUrl());
        String baseUrl = firstNonBlank(settings.getBaseUrl(), envBaseUrl);
        String sendPath = firstNonBlank(settings.getSendPath(), envSendPath);
        String clientId = firstNonBlank(settings.getClientId(), envClientId);
        String apiKey = settings.hasStoredApiKey()
            ? secretProtectionService.decrypt(settings.getApiKeyEncrypted())
            : nullToEmpty(envApiKey);
        String senderId = firstNonBlank(settings.getSenderId(), envSenderId);
        int connectTimeoutSeconds = usingDatabase ? settings.getConnectTimeoutSeconds() : seconds(envConnectTimeout, 3);
        int readTimeoutSeconds = usingDatabase ? settings.getReadTimeoutSeconds() : seconds(envReadTimeout, 8);
        boolean configured = !baseUrl.isBlank() && !sendPath.isBlank() && !clientId.isBlank()
            && !apiKey.isBlank() && !senderId.isBlank();
        boolean sendEnabled = (usingDatabase ? settings.isEnabled() : envEnabled) && configured;
        return new ResolvedSmsGatewayConfig(
            sendEnabled,
            baseUrl,
            sendPath,
            clientId,
            apiKey,
            senderId,
            connectTimeoutSeconds,
            readTimeoutSeconds,
            configured,
            settings.isEnabled(),
            settings.hasStoredApiKey() || !blank(envApiKey)
        );
    }

    private int seconds(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase();
        if (normalized.endsWith("ms")) {
            try {
                int millis = Integer.parseInt(normalized.substring(0, normalized.length() - 2).trim());
                return millis > 0 ? Math.max(1, millis / 1000) : fallback;
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }
        if (normalized.endsWith("s")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        try {
            int parsed = Integer.parseInt(normalized);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void validateTimeout(int timeoutSeconds, String label) {
        if (timeoutSeconds < MIN_TIMEOUT_SECONDS || timeoutSeconds > MAX_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException(label + " must be between 1 and 60 seconds.");
        }
    }

    private String normalizeText(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be " + maxLength + " characters or less.");
        }
        return normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!blank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private PlatformSmsGatewaySettings defaultSettings(OffsetDateTime now) {
        return PlatformSmsGatewaySettings.builder()
            .id(PlatformSmsGatewaySettings.DEFAULT_ID)
            .enabled(false)
            .baseUrl("")
            .sendPath("")
            .clientId("")
            .apiKeyEncrypted("")
            .senderId("")
            .connectTimeoutSeconds(3)
            .readTimeoutSeconds(8)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    private Map<String, Object> snapshot(PlatformSmsGatewaySettings settings) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", settings.isEnabled());
        data.put("baseUrl", settings.getBaseUrl());
        data.put("sendPath", settings.getSendPath());
        data.put("clientId", settings.getClientId());
        data.put("apiKeyConfigured", settings.hasStoredApiKey());
        data.put("senderId", settings.getSenderId());
        data.put("connectTimeoutSeconds", settings.getConnectTimeoutSeconds());
        data.put("readTimeoutSeconds", settings.getReadTimeoutSeconds());
        return data;
    }

    public record ResolvedSmsGatewayConfig(
        boolean enabled,
        String baseUrl,
        String sendPath,
        String clientId,
        String apiKey,
        String senderId,
        int connectTimeoutSeconds,
        int readTimeoutSeconds,
        boolean configured,
        boolean uiEnabled,
        boolean apiKeyConfigured
    ) {
    }
}
