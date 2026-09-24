package com.sacco.mvp.service;

import com.sacco.mvp.domain.PlatformBrandingSettings;
import com.sacco.mvp.repository.PlatformBrandingSettingsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformBrandingSettingsServiceTest {

    @Test
    void createsDefaultSettingsWhenMissing() {
        PlatformBrandingSettingsRepository repository = Mockito.mock(PlatformBrandingSettingsRepository.class);
        when(repository.findById(PlatformBrandingSettings.DEFAULT_ID)).thenReturn(Optional.empty());
        when(repository.save(any(PlatformBrandingSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformBrandingSettingsService service = new PlatformBrandingSettingsService(repository, Mockito.mock(AuditService.class));

        LogoUploadPolicy policy = service.logoUploadPolicy();

        assertEquals(64, policy.minWidthPx());
        assertEquals(64, policy.minHeightPx());
        assertEquals(1024, policy.maxWidthPx());
        assertEquals(1024, policy.maxHeightPx());
        assertEquals(1024, policy.maxFileSizeKb());
        verify(repository).save(any(PlatformBrandingSettings.class));
    }

    @Test
    void updatesValidLogoPolicyAndAudits() {
        PlatformBrandingSettingsRepository repository = Mockito.mock(PlatformBrandingSettingsRepository.class);
        AuditService auditService = Mockito.mock(AuditService.class);
        PlatformBrandingSettings existing = existingSettings();
        when(repository.findById(PlatformBrandingSettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(PlatformBrandingSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformBrandingSettingsService service = new PlatformBrandingSettingsService(repository, auditService);
        UUID actorId = UUID.randomUUID();

        PlatformBrandingSettings saved = service.updateLogoPolicy(96, 96, 2048, 2048, 2048, actorId);

        assertEquals(96, saved.getLogoMinWidthPx());
        assertEquals(2048, saved.getLogoMaxWidthPx());
        assertEquals(2048, saved.getLogoMaxFileSizeKb());
        assertEquals(actorId, saved.getUpdatedByMemberId());
        verify(auditService).log(eq("PLATFORM_BRANDING_SETTINGS"), isNull(), eq("ADMIN_UPDATE_LOGO_POLICY"), eq(actorId), any(), any());
    }

    @Test
    void rejectsMinimumWidthGreaterThanMaximumWidth() {
        PlatformBrandingSettingsService service = new PlatformBrandingSettingsService(
            Mockito.mock(PlatformBrandingSettingsRepository.class),
            Mockito.mock(AuditService.class)
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.updateLogoPolicy(512, 64, 256, 1024, 1024, UUID.randomUUID()));

        assertEquals("Minimum logo width cannot be greater than the maximum logo width.", ex.getMessage());
    }

    private PlatformBrandingSettings existingSettings() {
        return PlatformBrandingSettings.builder()
            .id(PlatformBrandingSettings.DEFAULT_ID)
            .logoMinWidthPx(64)
            .logoMinHeightPx(64)
            .logoMaxWidthPx(1024)
            .logoMaxHeightPx(1024)
            .logoMaxFileSizeKb(1024)
            .createdAt(java.time.OffsetDateTime.now())
            .updatedAt(java.time.OffsetDateTime.now())
            .build();
    }
}
