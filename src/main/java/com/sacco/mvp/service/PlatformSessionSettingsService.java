package com.sacco.mvp.service;

import com.sacco.mvp.domain.PlatformSessionSettings;
import com.sacco.mvp.repository.PlatformSessionSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformSessionSettingsService {
    public static final int DEFAULT_TIMEOUT_MINUTES = 30;
    public static final int MIN_TIMEOUT_MINUTES = 2;
    public static final int MAX_TIMEOUT_MINUTES = 480;
    public static final long WARNING_MS = 60_000L;

    private final PlatformSessionSettingsRepository repository;
    private final AuditService auditService;
    private volatile SessionTimeoutPolicy cachedPolicy;

    @Transactional
    public PlatformSessionSettings settings() {
        return repository.findById(PlatformSessionSettings.DEFAULT_ID)
            .orElseGet(() -> repository.save(defaultSettings(OffsetDateTime.now())));
    }

    @Transactional
    public SessionTimeoutPolicy policy() {
        SessionTimeoutPolicy policy = cachedPolicy;
        if (policy != null) {
            return policy;
        }
        return loadAndCachePolicy();
    }

    @Transactional
    public PlatformSessionSettings updateTimeout(int timeoutMinutes, UUID actorMemberId) {
        validateTimeout(timeoutMinutes);
        PlatformSessionSettings settings = settings();
        Map<String, Object> before = snapshot(settings);
        settings.setTimeoutMinutes(timeoutMinutes);
        settings.setUpdatedByMemberId(actorMemberId);
        settings.setUpdatedAt(OffsetDateTime.now());
        PlatformSessionSettings saved = repository.save(settings);
        cachedPolicy = toPolicy(saved);
        auditService.log("PLATFORM_SESSION_SETTINGS", null, "ADMIN_UPDATE_SESSION_TIMEOUT", actorMemberId, before, snapshot(saved));
        return saved;
    }

    private synchronized SessionTimeoutPolicy loadAndCachePolicy() {
        SessionTimeoutPolicy policy = cachedPolicy;
        if (policy != null) {
            return policy;
        }
        policy = toPolicy(settings());
        cachedPolicy = policy;
        return policy;
    }

    private void validateTimeout(int timeoutMinutes) {
        if (timeoutMinutes < MIN_TIMEOUT_MINUTES || timeoutMinutes > MAX_TIMEOUT_MINUTES) {
            throw new IllegalArgumentException("Session timeout must be between 2 and 480 minutes.");
        }
    }

    private PlatformSessionSettings defaultSettings(OffsetDateTime now) {
        return PlatformSessionSettings.builder()
            .id(PlatformSessionSettings.DEFAULT_ID)
            .timeoutMinutes(DEFAULT_TIMEOUT_MINUTES)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    private SessionTimeoutPolicy toPolicy(PlatformSessionSettings settings) {
        long timeoutMs = settings.getTimeoutMinutes() * 60_000L;
        return new SessionTimeoutPolicy(settings.getTimeoutMinutes(), timeoutMs, WARNING_MS);
    }

    private Map<String, Object> snapshot(PlatformSessionSettings settings) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timeoutMinutes", settings.getTimeoutMinutes());
        return data;
    }
}
