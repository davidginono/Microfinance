package com.sacco.mvp.service;

import com.sacco.mvp.domain.PlatformSessionSettings;
import com.sacco.mvp.repository.PlatformSessionSettingsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformSessionSettingsServiceTest {

    @Test
    void createsDefaultSettingsWhenMissing() {
        PlatformSessionSettingsRepository repository = Mockito.mock(PlatformSessionSettingsRepository.class);
        when(repository.findById(PlatformSessionSettings.DEFAULT_ID)).thenReturn(Optional.empty());
        when(repository.save(any(PlatformSessionSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformSessionSettingsService service = new PlatformSessionSettingsService(repository, Mockito.mock(AuditService.class));

        SessionTimeoutPolicy policy = service.policy();

        assertThat(policy.timeoutMinutes()).isEqualTo(30);
        assertThat(policy.timeoutMs()).isEqualTo(1_800_000L);
        assertThat(policy.warningMs()).isEqualTo(60_000L);
        verify(repository).save(any(PlatformSessionSettings.class));
    }

    @Test
    void updatesValidTimeoutAndAudits() {
        PlatformSessionSettingsRepository repository = Mockito.mock(PlatformSessionSettingsRepository.class);
        AuditService auditService = Mockito.mock(AuditService.class);
        PlatformSessionSettings existing = existingSettings(30);
        when(repository.findById(PlatformSessionSettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(PlatformSessionSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformSessionSettingsService service = new PlatformSessionSettingsService(repository, auditService);
        UUID actorId = UUID.randomUUID();

        PlatformSessionSettings saved = service.updateTimeout(45, actorId);

        assertThat(saved.getTimeoutMinutes()).isEqualTo(45);
        assertThat(saved.getUpdatedByMemberId()).isEqualTo(actorId);
        assertThat(service.policy().timeoutMinutes()).isEqualTo(45);
        verify(auditService).log(eq("PLATFORM_SESSION_SETTINGS"), isNull(), eq("ADMIN_UPDATE_SESSION_TIMEOUT"), eq(actorId), any(), any());
    }

    @Test
    void rejectsTimeoutOutsideAllowedRange() {
        PlatformSessionSettingsService service = new PlatformSessionSettingsService(
            Mockito.mock(PlatformSessionSettingsRepository.class),
            Mockito.mock(AuditService.class)
        );

        assertThatThrownBy(() -> service.updateTimeout(1, UUID.randomUUID()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Session timeout must be between 2 and 480 minutes.");
    }

    @Test
    void usesCachedPolicyUntilSettingsAreSaved() {
        PlatformSessionSettingsRepository repository = Mockito.mock(PlatformSessionSettingsRepository.class);
        PlatformSessionSettings existing = existingSettings(20);
        when(repository.findById(PlatformSessionSettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(PlatformSessionSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformSessionSettingsService service = new PlatformSessionSettingsService(repository, Mockito.mock(AuditService.class));

        assertThat(service.policy().timeoutMinutes()).isEqualTo(20);
        assertThat(service.policy().timeoutMinutes()).isEqualTo(20);

        service.updateTimeout(35, UUID.randomUUID());

        assertThat(service.policy().timeoutMinutes()).isEqualTo(35);
        verify(repository, times(2)).findById(PlatformSessionSettings.DEFAULT_ID);
    }

    private PlatformSessionSettings existingSettings(int timeoutMinutes) {
        OffsetDateTime now = OffsetDateTime.now();
        return PlatformSessionSettings.builder()
            .id(PlatformSessionSettings.DEFAULT_ID)
            .timeoutMinutes(timeoutMinutes)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
