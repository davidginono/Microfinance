package com.sacco.mvp.service;

import com.sacco.mvp.domain.PlatformSupportContactSettings;
import com.sacco.mvp.repository.PlatformSupportContactSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PlatformSupportContactSettingsService {
    private static final String DEFAULT_DISPLAY_NAME = "Platform Support";
    private static final String DEFAULT_DISPLAY_ROLE = "Super Admin Support";
    private static final int DISPLAY_NAME_MAX = 120;
    private static final int DISPLAY_ROLE_MAX = 120;
    private static final int PHONE_MAX = 40;
    private static final int EMAIL_MAX = 160;
    private static final int OFFICE_HOURS_MAX = 120;
    private static final int SUPPORT_NOTE_MAX = 280;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final PlatformSupportContactSettingsRepository repository;
    private final AuditService auditService;

    @Transactional
    public PlatformSupportContactSettings settings() {
        return repository.findById(PlatformSupportContactSettings.DEFAULT_ID)
            .orElseGet(() -> repository.save(defaultSettings(OffsetDateTime.now())));
    }

    @Transactional(readOnly = true)
    public PlatformSupportContactSettings visibleContact() {
        return repository.findById(PlatformSupportContactSettings.DEFAULT_ID)
            .filter(PlatformSupportContactSettings::isVisible)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public PlatformSupportContactSettings sidebarContact() {
        return repository.findById(PlatformSupportContactSettings.DEFAULT_ID)
            .orElseGet(() -> defaultSettings(OffsetDateTime.now()));
    }

    @Transactional
    public PlatformSupportContactSettings updateContact(String displayName,
                                                        String displayRole,
                                                        String phone,
                                                        String email,
                                                        String officeHours,
                                                        String supportNote,
                                                        UUID actorMemberId) {
        String normalizedDisplayName = normalizeText(displayName, "Display name", DISPLAY_NAME_MAX, DEFAULT_DISPLAY_NAME);
        String normalizedDisplayRole = normalizeText(displayRole, "Role", DISPLAY_ROLE_MAX, DEFAULT_DISPLAY_ROLE);
        String normalizedPhone = normalizePhone(phone);
        String normalizedEmail = normalizeEmail(email);
        String normalizedOfficeHours = normalizeText(officeHours, "Office hours", OFFICE_HOURS_MAX, "");
        String normalizedSupportNote = normalizeText(supportNote, "Support note", SUPPORT_NOTE_MAX, "");
        if (normalizedPhone.isBlank() && normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("Enter at least a phone number or email address.");
        }

        PlatformSupportContactSettings settings = settings();
        Map<String, Object> before = snapshot(settings);
        settings.setDisplayName(normalizedDisplayName);
        settings.setDisplayRole(normalizedDisplayRole);
        settings.setPhone(normalizedPhone);
        settings.setEmail(normalizedEmail);
        settings.setOfficeHours(normalizedOfficeHours);
        settings.setSupportNote(normalizedSupportNote);
        settings.setUpdatedByMemberId(actorMemberId);
        settings.setUpdatedAt(OffsetDateTime.now());
        PlatformSupportContactSettings saved = repository.save(settings);
        auditService.log("PLATFORM_SUPPORT_CONTACT_SETTINGS", null, "ADMIN_UPDATE_SUPPORT_CONTACT", actorMemberId, before, snapshot(saved));
        return saved;
    }

    private String normalizeEmail(String value) {
        String normalized = normalizeText(value, "Email address", EMAIL_MAX, "");
        if (normalized.isBlank()) {
            return "";
        }
        normalized = normalized.toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        return normalized;
    }

    private String normalizePhone(String value) {
        String normalized = normalizeText(value, "Phone number", PHONE_MAX, "");
        if (normalized.isBlank()) {
            return "";
        }
        long digitCount = normalized.chars().filter(Character::isDigit).count();
        if (digitCount < 7) {
            throw new IllegalArgumentException("Enter a valid phone number.");
        }
        return normalized;
    }

    private String normalizeText(String value, String label, int maxLength, String fallback) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank() && fallback != null) {
            normalized = fallback;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be " + maxLength + " characters or less.");
        }
        return normalized;
    }

    private PlatformSupportContactSettings defaultSettings(OffsetDateTime now) {
        return PlatformSupportContactSettings.builder()
            .id(PlatformSupportContactSettings.DEFAULT_ID)
            .displayName(DEFAULT_DISPLAY_NAME)
            .displayRole(DEFAULT_DISPLAY_ROLE)
            .phone("")
            .email("")
            .officeHours("")
            .supportNote("")
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    private Map<String, Object> snapshot(PlatformSupportContactSettings settings) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("displayName", settings.getDisplayName());
        data.put("displayRole", settings.getDisplayRole());
        data.put("phone", settings.getPhone());
        data.put("email", settings.getEmail());
        data.put("officeHours", settings.getOfficeHours());
        data.put("supportNote", settings.getSupportNote());
        return data;
    }
}
