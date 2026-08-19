package com.sacco.mvp.service;

import com.sacco.mvp.domain.PlatformEmailSettings;
import com.sacco.mvp.repository.PlatformEmailSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PlatformEmailSettingsService {
    private static final int HOST_MAX = 255;
    private static final int USERNAME_MAX = 160;
    private static final int ADDRESS_MAX = 160;
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;
    private static final int MIN_TIMEOUT_MS = 1_000;
    private static final int MAX_TIMEOUT_MS = 120_000;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final PlatformEmailSettingsRepository repository;
    private final PlatformSecretProtectionService secretProtectionService;
    private final ObjectProvider<PlatformMailSenderFactory> mailSenderFactoryProvider;
    private final AuditService auditService;

    @Value("${spring.mail.host:}")
    private String envHost = "";

    @Value("${spring.mail.port:465}")
    private int envPort = 465;

    @Value("${spring.mail.username:}")
    private String envUsername = "";

    @Value("${spring.mail.password:}")
    private String envPassword = "";

    @Value("${app.mail.from-address:}")
    private String envFromAddress = "";

    @Value("${app.mail.override-recipient:}")
    private String envOverrideRecipient = "";

    @Value("${spring.mail.properties.mail.smtp.ssl.enable:true}")
    private boolean envSslEnabled = true;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}")
    private boolean envStartTlsEnabled = false;

    @Value("${spring.mail.properties.mail.smtp.connectiontimeout:10000}")
    private int envConnectionTimeoutMs = 10_000;

    @Value("${spring.mail.properties.mail.smtp.timeout:10000}")
    private int envReadTimeoutMs = 10_000;

    @Value("${spring.mail.properties.mail.smtp.writetimeout:10000}")
    private int envWriteTimeoutMs = 10_000;

    private volatile ResolvedEmailConfig cachedConfig;

    @Transactional
    public PlatformEmailSettings settings() {
        return repository.findById(PlatformEmailSettings.DEFAULT_ID)
            .orElseGet(() -> repository.save(defaultSettings(OffsetDateTime.now())));
    }

    public ResolvedEmailConfig resolvedConfig() {
        ResolvedEmailConfig config = cachedConfig;
        if (config != null) {
            return config;
        }
        return loadAndCache();
    }

    @Transactional
    public PlatformEmailSettings updateSettings(boolean enabled,
                                                String host,
                                                int port,
                                                String username,
                                                String password,
                                                String fromAddress,
                                                String overrideRecipient,
                                                boolean sslEnabled,
                                                boolean starttlsEnabled,
                                                int connectionTimeoutMs,
                                                int readTimeoutMs,
                                                int writeTimeoutMs,
                                                UUID actorMemberId) {
        String normalizedHost = normalizeText(host, "SMTP host", HOST_MAX);
        validatePort(port);
        String normalizedUsername = normalizeText(username, "SMTP username", USERNAME_MAX);
        String normalizedFromAddress = enabled
            ? normalizeEmail(fromAddress, "From address")
            : normalizeOptionalEmail(fromAddress, "From address");
        String normalizedOverrideRecipient = normalizeOptionalEmail(overrideRecipient, "Override recipient");
        validateTimeout(connectionTimeoutMs, "Connection timeout");
        validateTimeout(readTimeoutMs, "Read timeout");
        validateTimeout(writeTimeoutMs, "Write timeout");
        if (enabled && normalizedHost.isBlank()) {
            throw new IllegalArgumentException("Enter SMTP host.");
        }
        if (enabled && normalizedUsername.isBlank()) {
            throw new IllegalArgumentException("Enter SMTP username.");
        }

        PlatformEmailSettings settings = settings();
        Map<String, Object> before = snapshot(settings);
        settings.setEnabled(enabled);
        settings.setHost(normalizedHost);
        settings.setPort(port);
        settings.setUsername(normalizedUsername);
        settings.setFromAddress(normalizedFromAddress);
        settings.setOverrideRecipient(normalizedOverrideRecipient);
        settings.setSslEnabled(sslEnabled);
        settings.setStarttlsEnabled(starttlsEnabled);
        settings.setConnectionTimeoutMs(connectionTimeoutMs);
        settings.setReadTimeoutMs(readTimeoutMs);
        settings.setWriteTimeoutMs(writeTimeoutMs);
        if (password != null && !password.isBlank()) {
            settings.setPasswordEncrypted(secretProtectionService.encrypt(password));
        } else if (enabled && !settings.hasStoredPassword() && blank(envPassword)) {
            throw new IllegalArgumentException("Enter the SMTP password before enabling email delivery.");
        }
        settings.setUpdatedByMemberId(actorMemberId);
        settings.setUpdatedAt(OffsetDateTime.now());
        PlatformEmailSettings saved = repository.save(settings);
        cachedConfig = null;
        mailSenderFactoryProvider.ifAvailable(PlatformMailSenderFactory::invalidate);
        auditService.log("PLATFORM_EMAIL_SETTINGS", null, "ADMIN_UPDATE_EMAIL_SETTINGS", actorMemberId, before, snapshot(saved));
        return saved;
    }

    @Transactional
    public void sendTestEmail(String recipientEmail, UUID actorMemberId) {
        String normalizedRecipient = normalizeEmail(recipientEmail, "Test recipient");
        ResolvedEmailConfig config = resolvedConfig();
        if (!config.enabled()) {
            throw new IllegalStateException("Email delivery is not configured.");
        }
        PlatformMailSenderFactory mailSenderFactory = mailSenderFactoryProvider.getIfAvailable();
        var mailSender = mailSenderFactory == null ? null : mailSenderFactory.getMailSender();
        if (mailSender == null) {
            throw new IllegalStateException("Email delivery is not configured.");
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(normalizedRecipient);
        mail.setFrom(config.fromAddress());
        mail.setSubject("SACCO platform email test");
        mail.setText("This is a test email from Platform Settings.");
        mailSender.send(mail);
        auditService.log("PLATFORM_EMAIL_SETTINGS", null, "ADMIN_TEST_EMAIL", actorMemberId, Map.of("recipient", normalizedRecipient), Map.of());
    }

    private synchronized ResolvedEmailConfig loadAndCache() {
        ResolvedEmailConfig config = cachedConfig;
        if (config != null) {
            return config;
        }
        PlatformEmailSettings settings = repository.findById(PlatformEmailSettings.DEFAULT_ID)
            .orElseGet(() -> defaultSettings(OffsetDateTime.now()));
        config = toResolved(settings);
        cachedConfig = config;
        return config;
    }

    private ResolvedEmailConfig toResolved(PlatformEmailSettings settings) {
        boolean usingDatabase = !blank(settings.getHost());
        String host = firstNonBlank(settings.getHost(), envHost);
        int port = usingDatabase ? settings.getPort() : envPort;
        String username = firstNonBlank(settings.getUsername(), envUsername);
        String password = settings.hasStoredPassword()
            ? secretProtectionService.decrypt(settings.getPasswordEncrypted())
            : nullToEmpty(envPassword);
        String fromAddress = firstNonBlank(settings.getFromAddress(), envFromAddress, username, "no-reply@sacco.local");
        String overrideRecipient = blank(settings.getOverrideRecipient())
            ? firstNonBlank(envOverrideRecipient)
            : settings.getOverrideRecipient().trim();
        boolean sslEnabled = usingDatabase ? settings.isSslEnabled() : envSslEnabled;
        boolean startTlsEnabled = usingDatabase ? settings.isStarttlsEnabled() : envStartTlsEnabled;
        int connectionTimeoutMs = usingDatabase ? settings.getConnectionTimeoutMs() : envConnectionTimeoutMs;
        int readTimeoutMs = usingDatabase ? settings.getReadTimeoutMs() : envReadTimeoutMs;
        int writeTimeoutMs = usingDatabase ? settings.getWriteTimeoutMs() : envWriteTimeoutMs;
        boolean configured = !host.isBlank() && !username.isBlank() && !password.isBlank();
        boolean sendEnabled = (usingDatabase ? settings.isEnabled() : hasEnvConfiguration()) && configured;
        return new ResolvedEmailConfig(
            sendEnabled,
            host,
            port,
            username,
            password,
            fromAddress,
            overrideRecipient,
            sslEnabled,
            startTlsEnabled,
            connectionTimeoutMs,
            readTimeoutMs,
            writeTimeoutMs,
            configured,
            settings.isEnabled(),
            settings.hasStoredPassword() || !blank(envPassword)
        );
    }

    private boolean hasEnvConfiguration() {
        return !blank(envHost) && !blank(envUsername) && !blank(envPassword);
    }

    private void validatePort(int port) {
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException("SMTP port must be between 1 and 65535.");
        }
    }

    private void validateTimeout(int timeoutMs, String label) {
        if (timeoutMs < MIN_TIMEOUT_MS || timeoutMs > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException(label + " must be between 1000 and 120000 milliseconds.");
        }
    }

    private String normalizeEmail(String value, String label) {
        String normalized = normalizeOptionalEmail(value, label);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Enter " + label.toLowerCase() + ".");
        }
        return normalized;
    }

    private String normalizeOptionalEmail(String value, String label) {
        String normalized = normalizeText(value, label, ADDRESS_MAX);
        if (normalized.isBlank()) {
            return "";
        }
        normalized = normalized.toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Enter a valid " + label.toLowerCase() + ".");
        }
        return normalized;
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

    private PlatformEmailSettings defaultSettings(OffsetDateTime now) {
        return PlatformEmailSettings.builder()
            .id(PlatformEmailSettings.DEFAULT_ID)
            .enabled(false)
            .host("")
            .port(465)
            .username("")
            .passwordEncrypted("")
            .fromAddress("")
            .overrideRecipient("")
            .sslEnabled(true)
            .starttlsEnabled(false)
            .connectionTimeoutMs(10_000)
            .readTimeoutMs(10_000)
            .writeTimeoutMs(10_000)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    private Map<String, Object> snapshot(PlatformEmailSettings settings) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", settings.isEnabled());
        data.put("host", settings.getHost());
        data.put("port", settings.getPort());
        data.put("username", settings.getUsername());
        data.put("passwordConfigured", settings.hasStoredPassword());
        data.put("fromAddress", settings.getFromAddress());
        data.put("overrideRecipient", settings.getOverrideRecipient());
        data.put("sslEnabled", settings.isSslEnabled());
        data.put("starttlsEnabled", settings.isStarttlsEnabled());
        data.put("connectionTimeoutMs", settings.getConnectionTimeoutMs());
        data.put("readTimeoutMs", settings.getReadTimeoutMs());
        data.put("writeTimeoutMs", settings.getWriteTimeoutMs());
        return data;
    }

    public record ResolvedEmailConfig(
        boolean enabled,
        String host,
        int port,
        String username,
        String password,
        String fromAddress,
        String overrideRecipient,
        boolean sslEnabled,
        boolean starttlsEnabled,
        int connectionTimeoutMs,
        int readTimeoutMs,
        int writeTimeoutMs,
        boolean configured,
        boolean uiEnabled,
        boolean passwordConfigured
    ) {
        public String cacheKey() {
            return enabled + "|" + host + "|" + port + "|" + username + "|" + password + "|"
                + fromAddress + "|" + sslEnabled + "|" + starttlsEnabled + "|"
                + connectionTimeoutMs + "|" + readTimeoutMs + "|" + writeTimeoutMs;
        }
    }
}
