package com.sacco.mvp.service;

import com.sacco.mvp.domain.PlatformSupportContactSettings;
import com.sacco.mvp.repository.PlatformSupportContactSettingsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformSupportContactSettingsServiceTest {

    @Test
    void createsDefaultSettingsWhenMissing() {
        PlatformSupportContactSettingsRepository repository = Mockito.mock(PlatformSupportContactSettingsRepository.class);
        when(repository.findById(PlatformSupportContactSettings.DEFAULT_ID)).thenReturn(Optional.empty());
        when(repository.save(any(PlatformSupportContactSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformSupportContactSettingsService service = new PlatformSupportContactSettingsService(repository, Mockito.mock(AuditService.class));

        PlatformSupportContactSettings settings = service.settings();

        assertEquals("Platform Support", settings.getDisplayName());
        assertEquals("Super Admin Support", settings.getDisplayRole());
        assertFalse(settings.isVisible());
        verify(repository).save(any(PlatformSupportContactSettings.class));
    }

    @Test
    void visibleContactReturnsOnlySavedReachableContact() {
        PlatformSupportContactSettingsRepository repository = Mockito.mock(PlatformSupportContactSettingsRepository.class);
        PlatformSupportContactSettings visible = existingSettings();
        visible.setPhone("+255 746 359 369");
        when(repository.findById(PlatformSupportContactSettings.DEFAULT_ID)).thenReturn(Optional.of(visible));
        PlatformSupportContactSettingsService service = new PlatformSupportContactSettingsService(repository, Mockito.mock(AuditService.class));

        assertTrue(service.visibleContact().isVisible());

        visible.setPhone("");
        visible.setEmail("");

        assertNull(service.visibleContact());
    }

    @Test
    void sidebarContactReturnsDefaultWithoutPersistingWhenMissing() {
        PlatformSupportContactSettingsRepository repository = Mockito.mock(PlatformSupportContactSettingsRepository.class);
        when(repository.findById(PlatformSupportContactSettings.DEFAULT_ID)).thenReturn(Optional.empty());
        PlatformSupportContactSettingsService service = new PlatformSupportContactSettingsService(repository, Mockito.mock(AuditService.class));

        PlatformSupportContactSettings contact = service.sidebarContact();

        assertEquals("Platform Support", contact.getDisplayName());
        assertEquals("Super Admin Support", contact.getDisplayRole());
        assertFalse(contact.isVisible());
        verify(repository, never()).save(any(PlatformSupportContactSettings.class));
    }

    @Test
    void updatesValidContactAndAudits() {
        PlatformSupportContactSettingsRepository repository = Mockito.mock(PlatformSupportContactSettingsRepository.class);
        AuditService auditService = Mockito.mock(AuditService.class);
        PlatformSupportContactSettings existing = existingSettings();
        when(repository.findById(PlatformSupportContactSettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(PlatformSupportContactSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformSupportContactSettingsService service = new PlatformSupportContactSettingsService(repository, auditService);
        UUID actorId = UUID.randomUUID();

        PlatformSupportContactSettings saved = service.updateContact(
            "  Help Desk  ",
            " Super Admin Desk ",
            " +255 746 359 369 ",
            " Support@Example.COM ",
            " Mon - Fri, 08:00 - 17:00 ",
            " Contact us for platform support. ",
            actorId
        );

        assertEquals("Help Desk", saved.getDisplayName());
        assertEquals("Super Admin Desk", saved.getDisplayRole());
        assertEquals("+255 746 359 369", saved.getPhone());
        assertEquals("support@example.com", saved.getEmail());
        assertEquals("Mon - Fri, 08:00 - 17:00", saved.getOfficeHours());
        assertEquals("Contact us for platform support.", saved.getSupportNote());
        assertEquals(actorId, saved.getUpdatedByMemberId());
        verify(auditService).log(eq("PLATFORM_SUPPORT_CONTACT_SETTINGS"), isNull(), eq("ADMIN_UPDATE_SUPPORT_CONTACT"), eq(actorId), any(), any());
    }

    @Test
    void rejectsMissingPhoneAndEmail() {
        PlatformSupportContactSettingsService service = new PlatformSupportContactSettingsService(
            Mockito.mock(PlatformSupportContactSettingsRepository.class),
            Mockito.mock(AuditService.class)
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.updateContact("Support", "Admin", "", "", "", "", UUID.randomUUID()));

        assertEquals("Enter at least a phone number or email address.", ex.getMessage());
    }

    @Test
    void rejectsInvalidEmailAddress() {
        PlatformSupportContactSettingsService service = new PlatformSupportContactSettingsService(
            Mockito.mock(PlatformSupportContactSettingsRepository.class),
            Mockito.mock(AuditService.class)
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.updateContact("Support", "Admin", "", "not-an-email", "", "", UUID.randomUUID()));

        assertEquals("Enter a valid email address.", ex.getMessage());
    }

    @Test
    void rejectsShortPhoneNumber() {
        PlatformSupportContactSettingsService service = new PlatformSupportContactSettingsService(
            Mockito.mock(PlatformSupportContactSettingsRepository.class),
            Mockito.mock(AuditService.class)
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.updateContact("Support", "Admin", "123", "", "", "", UUID.randomUUID()));

        assertEquals("Enter a valid phone number.", ex.getMessage());
    }

    @Test
    void rejectsOverlongSupportNote() {
        PlatformSupportContactSettingsService service = new PlatformSupportContactSettingsService(
            Mockito.mock(PlatformSupportContactSettingsRepository.class),
            Mockito.mock(AuditService.class)
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.updateContact("Support", "Admin", "+255 746 359 369", "", "", "x".repeat(281), UUID.randomUUID()));

        assertEquals("Support note must be 280 characters or less.", ex.getMessage());
    }

    private PlatformSupportContactSettings existingSettings() {
        OffsetDateTime now = OffsetDateTime.now();
        return PlatformSupportContactSettings.builder()
            .id(PlatformSupportContactSettings.DEFAULT_ID)
            .displayName("Platform Support")
            .displayRole("Super Admin Support")
            .phone("")
            .email("")
            .officeHours("")
            .supportNote("")
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
