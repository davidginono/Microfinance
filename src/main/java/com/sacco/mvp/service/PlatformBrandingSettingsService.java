package com.sacco.mvp.service;

import com.sacco.mvp.domain.PlatformBrandingSettings;
import com.sacco.mvp.repository.PlatformBrandingSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformBrandingSettingsService {
    private static final int DEFAULT_MIN_WIDTH = 64;
    private static final int DEFAULT_MIN_HEIGHT = 64;
    private static final int DEFAULT_MAX_WIDTH = 1024;
    private static final int DEFAULT_MAX_HEIGHT = 1024;
    private static final int DEFAULT_MAX_FILE_SIZE_KB = 1024;
    private static final int MIN_DIMENSION = 32;
    private static final int MAX_DIMENSION = 4096;
    private static final int MIN_FILE_SIZE_KB = 64;
    private static final int MAX_FILE_SIZE_KB = 5120;

    private final PlatformBrandingSettingsRepository repository;
    private final AuditService auditService;

    @Transactional
    public PlatformBrandingSettings settings() {
        return repository.findById(PlatformBrandingSettings.DEFAULT_ID)
            .orElseGet(() -> repository.save(defaultSettings(OffsetDateTime.now())));
    }

    @Transactional
    public LogoUploadPolicy logoUploadPolicy() {
        PlatformBrandingSettings settings = settings();
        return new LogoUploadPolicy(
            settings.getLogoMinWidthPx(),
            settings.getLogoMinHeightPx(),
            settings.getLogoMaxWidthPx(),
            settings.getLogoMaxHeightPx(),
            settings.getLogoMaxFileSizeKb()
        );
    }

    @Transactional
    public PlatformBrandingSettings updateLogoPolicy(int minWidthPx,
                                                     int minHeightPx,
                                                     int maxWidthPx,
                                                     int maxHeightPx,
                                                     int maxFileSizeKb,
                                                     UUID actorMemberId) {
        validateLogoPolicy(minWidthPx, minHeightPx, maxWidthPx, maxHeightPx, maxFileSizeKb);
        PlatformBrandingSettings settings = settings();
        Map<String, Object> before = snapshot(settings);
        settings.setLogoMinWidthPx(minWidthPx);
        settings.setLogoMinHeightPx(minHeightPx);
        settings.setLogoMaxWidthPx(maxWidthPx);
        settings.setLogoMaxHeightPx(maxHeightPx);
        settings.setLogoMaxFileSizeKb(maxFileSizeKb);
        settings.setUpdatedByMemberId(actorMemberId);
        settings.setUpdatedAt(OffsetDateTime.now());
        PlatformBrandingSettings saved = repository.save(settings);
        auditService.log("PLATFORM_BRANDING_SETTINGS", null, "ADMIN_UPDATE_LOGO_POLICY", actorMemberId, before, snapshot(saved));
        return saved;
    }

    private void validateLogoPolicy(int minWidthPx, int minHeightPx, int maxWidthPx, int maxHeightPx, int maxFileSizeKb) {
        validateRange("Minimum logo width", minWidthPx, MIN_DIMENSION, MAX_DIMENSION);
        validateRange("Minimum logo height", minHeightPx, MIN_DIMENSION, MAX_DIMENSION);
        validateRange("Maximum logo width", maxWidthPx, MIN_DIMENSION, MAX_DIMENSION);
        validateRange("Maximum logo height", maxHeightPx, MIN_DIMENSION, MAX_DIMENSION);
        validateRange("Logo file size", maxFileSizeKb, MIN_FILE_SIZE_KB, MAX_FILE_SIZE_KB);
        if (minWidthPx > maxWidthPx) {
            throw new IllegalArgumentException("Minimum logo width cannot be greater than the maximum logo width.");
        }
        if (minHeightPx > maxHeightPx) {
            throw new IllegalArgumentException("Minimum logo height cannot be greater than the maximum logo height.");
        }
    }

    private void validateRange(String label, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
        }
    }

    private PlatformBrandingSettings defaultSettings(OffsetDateTime now) {
        return PlatformBrandingSettings.builder()
            .id(PlatformBrandingSettings.DEFAULT_ID)
            .logoMinWidthPx(DEFAULT_MIN_WIDTH)
            .logoMinHeightPx(DEFAULT_MIN_HEIGHT)
            .logoMaxWidthPx(DEFAULT_MAX_WIDTH)
            .logoMaxHeightPx(DEFAULT_MAX_HEIGHT)
            .logoMaxFileSizeKb(DEFAULT_MAX_FILE_SIZE_KB)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    private Map<String, Object> snapshot(PlatformBrandingSettings settings) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("logoMinWidthPx", settings.getLogoMinWidthPx());
        data.put("logoMinHeightPx", settings.getLogoMinHeightPx());
        data.put("logoMaxWidthPx", settings.getLogoMaxWidthPx());
        data.put("logoMaxHeightPx", settings.getLogoMaxHeightPx());
        data.put("logoMaxFileSizeKb", settings.getLogoMaxFileSizeKb());
        return data;
    }
}
