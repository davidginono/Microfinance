package com.sacco.mvp.service;

import com.sacco.mvp.domain.UserSettings;
import com.sacco.mvp.repository.UserSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {
    @Mock private UserSettingsRepository repository;

    @Test
    void newUsersAndPreferenceRowsDefaultBothOptionsOff() {
        UUID memberId = UUID.randomUUID();
        when(repository.findById(memberId)).thenReturn(Optional.empty());
        var service = new UserSettingsService(repository);

        assertThat(service.otpPreferences(memberId))
            .isEqualTo(new UserSettingsService.OtpPreferences(false, false));
        assertThat(new UserSettings().isLoginOtpEnabled()).isFalse();
        assertThat(UserSettings.builder().build().isLoginOtpEnabled()).isFalse();

        service.updateLanguage(memberId, "sw");

        var saved = ArgumentCaptor.forClass(UserSettings.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().isLoginOtpEnabled()).isFalse();
        assertThat(saved.getValue().isApprovalOtpEnabled()).isFalse();
    }

    @Test
    void existingChoicesSurviveOtherPreferenceChanges() {
        UUID memberId = UUID.randomUUID();
        var existing = UserSettings.builder().memberId(memberId)
            .loginOtpEnabled(true).approvalOtpEnabled(true).build();
        when(repository.findById(memberId)).thenReturn(Optional.of(existing));
        var service = new UserSettingsService(repository);

        service.updateLanguage(memberId, "sw");

        assertThat(service.requiresLoginOtp(memberId)).isTrue();
        assertThat(service.requiresApprovalOtp(memberId)).isTrue();
    }
}
