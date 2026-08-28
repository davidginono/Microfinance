package com.sacco.mvp.service;

import com.sacco.mvp.domain.UserSettings;
import com.sacco.mvp.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Owns per-user preference rows so request-handling code (member and staff
 * settings screens, the locale interceptor) never touches
 * {@link UserSettingsRepository} directly.
 */
@Service
@RequiredArgsConstructor
public class UserSettingsService {
    private static final String DEFAULT_LANGUAGE = "en";
    private static final String EMPTY_PREFS = "{}";

    private final UserSettingsRepository userSettingsRepository;

    public String languageOrDefault(UUID memberId) {
        return find(memberId)
            .map(UserSettings::getLanguage)
            .filter(language -> language != null && !language.isBlank())
            .orElse(DEFAULT_LANGUAGE);
    }

    public Optional<String> notificationPrefs(UUID memberId) {
        return find(memberId).map(UserSettings::getNotificationPrefs);
    }

    public OtpPreferences otpPreferences(UUID memberId) {
        return find(memberId)
            .map(settings -> new OtpPreferences(settings.isLoginOtpEnabled(), settings.isApprovalOtpEnabled()))
            .orElseGet(OtpPreferences::defaults);
    }

    public boolean requiresLoginOtp(UUID memberId) {
        return otpPreferences(memberId).loginOtpEnabled();
    }

    public boolean requiresApprovalOtp(UUID memberId) {
        return otpPreferences(memberId).approvalOtpEnabled();
    }

    @Transactional
    public String updateLanguage(UUID memberId, String language) {
        OffsetDateTime now = OffsetDateTime.now();
        UserSettings settings = findOrNew(memberId, now);
        settings.setLanguage(language);
        normalizeAndSave(settings, now);
        return settings.getLanguage();
    }

    /**
     * Applies {@code prefsUpdater} to the stored preferences JSON in a single
     * read-modify-write so dismiss-style toggles stay one query each way.
     */
    @Transactional
    public void updateNotificationPrefs(UUID memberId, UnaryOperator<String> prefsUpdater) {
        OffsetDateTime now = OffsetDateTime.now();
        UserSettings settings = findOrNew(memberId, now);
        settings.setNotificationPrefs(prefsUpdater.apply(settings.getNotificationPrefs()));
        normalizeAndSave(settings, now);
    }

    @Transactional
    public OtpPreferences updateOtpPreferences(UUID memberId,
                                               boolean loginOtpEnabled,
                                               boolean approvalOtpEnabled) {
        OffsetDateTime now = OffsetDateTime.now();
        UserSettings settings = findOrNew(memberId, now);
        settings.setLoginOtpEnabled(loginOtpEnabled);
        settings.setApprovalOtpEnabled(approvalOtpEnabled);
        normalizeAndSave(settings, now);
        return new OtpPreferences(loginOtpEnabled, approvalOtpEnabled);
    }

    private Optional<UserSettings> find(UUID memberId) {
        return memberId == null ? Optional.empty() : userSettingsRepository.findById(memberId);
    }

    private UserSettings findOrNew(UUID memberId, OffsetDateTime now) {
        return find(memberId).orElseGet(() -> UserSettings.builder()
            .memberId(memberId)
            .language(DEFAULT_LANGUAGE)
            .notificationPrefs(EMPTY_PREFS)
            .createdAt(now)
            .updatedAt(now)
            .build());
    }

    private void normalizeAndSave(UserSettings settings, OffsetDateTime now) {
        if (settings.getLanguage() == null || settings.getLanguage().isBlank()) {
            settings.setLanguage(DEFAULT_LANGUAGE);
        }
        if (settings.getNotificationPrefs() == null || settings.getNotificationPrefs().isBlank()) {
            settings.setNotificationPrefs(EMPTY_PREFS);
        }
        if (settings.getCreatedAt() == null) {
            settings.setCreatedAt(now);
        }
        settings.setUpdatedAt(now);
        userSettingsRepository.save(settings);
    }

    public record OtpPreferences(boolean loginOtpEnabled, boolean approvalOtpEnabled) {
        private static OtpPreferences defaults() {
            return new OtpPreferences(true, false);
        }
    }
}
