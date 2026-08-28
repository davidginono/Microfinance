package com.sacco.mvp.service;

import com.sacco.mvp.domain.OtpDeliveryChannel;
import com.sacco.mvp.domain.OtpSelectionPolicy;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.repository.SaccoStationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StationOtpSettingsServiceTest {
    private static final String SACCO_ID = "SACCO-01";
    private static final String STATION_ID = "ST-01";

    @Mock private SaccoStationRepository stationRepository;
    @Mock private UserSettingsService userSettingsService;
    @Mock private AuditService auditService;

    @Test
    void bothPolicyRequiresLoginAndApprovalRegardlessOfUserPreferences() {
        UUID memberId = UUID.randomUUID();
        StationOtpSettingsService service = serviceWithPolicy(OtpSelectionPolicy.BOTH);

        assertThat(service.requiresLoginMfa(memberId, SACCO_ID, STATION_ID)).isTrue();
        assertThat(service.requiresApprovalOtp(memberId, SACCO_ID, STATION_ID)).isTrue();

        verify(userSettingsService, never()).otpPreferences(memberId);
        verify(userSettingsService, never()).requiresApprovalOtp(memberId);
    }

    @Test
    void atLeastOnePolicyFallsBackToLoginWhenStoredPreferencesAreBothDisabled() {
        UUID memberId = UUID.randomUUID();
        StationOtpSettingsService service = serviceWithPolicy(OtpSelectionPolicy.AT_LEAST_ONE);
        when(userSettingsService.otpPreferences(memberId))
            .thenReturn(new UserSettingsService.OtpPreferences(false, false));
        when(userSettingsService.requiresApprovalOtp(memberId)).thenReturn(false);

        assertThat(service.requiresLoginMfa(memberId, SACCO_ID, STATION_ID)).isTrue();
        assertThat(service.requiresApprovalOtp(memberId, SACCO_ID, STATION_ID)).isFalse();
    }

    @Test
    void noMinimumPolicyAllowsBothUserPreferencesToRemainDisabled() {
        UUID memberId = UUID.randomUUID();
        StationOtpSettingsService service = serviceWithPolicy(OtpSelectionPolicy.NONE);
        when(userSettingsService.otpPreferences(memberId))
            .thenReturn(new UserSettingsService.OtpPreferences(false, false));
        when(userSettingsService.requiresApprovalOtp(memberId)).thenReturn(false);

        assertThat(service.requiresLoginMfa(memberId, SACCO_ID, STATION_ID)).isFalse();
        assertThat(service.requiresApprovalOtp(memberId, SACCO_ID, STATION_ID)).isFalse();
    }

    @Test
    void adminUpdatePersistsAndAuditsDeliveryAndSelectionPolicy() {
        UUID actorMemberId = UUID.randomUUID();
        SaccoStation station = SaccoStation.builder()
            .id(UUID.randomUUID())
            .active(true)
            .otpDeliveryChannel(OtpDeliveryChannel.EMAIL)
            .userOtpSelectionPolicy(OtpSelectionPolicy.AT_LEAST_ONE)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        when(stationRepository.findBySaccoIdAndStationIdAndActiveTrue(SACCO_ID, STATION_ID))
            .thenReturn(Optional.of(station));
        when(stationRepository.save(station)).thenReturn(station);
        StationOtpSettingsService service =
            new StationOtpSettingsService(stationRepository, userSettingsService, auditService);

        service.update(
            SACCO_ID,
            STATION_ID,
            OtpDeliveryChannel.SMS_WITH_EMAIL_FALLBACK,
            OtpSelectionPolicy.BOTH,
            actorMemberId
        );

        assertThat(station.getOtpDeliveryChannel()).isEqualTo(OtpDeliveryChannel.SMS_WITH_EMAIL_FALLBACK);
        assertThat(station.getUserOtpSelectionPolicy()).isEqualTo(OtpSelectionPolicy.BOTH);
        verify(auditService).log(
            eq("SACCO_STATION"),
            eq(station.getId()),
            eq("MINOR_ADMIN_UPDATE_OTP_SETTINGS"),
            eq(actorMemberId),
            any(),
            any()
        );
    }

    @Test
    void clearingAdminSelectionPersistsNoMinimumPolicy() {
        UUID actorMemberId = UUID.randomUUID();
        SaccoStation station = SaccoStation.builder()
            .id(UUID.randomUUID())
            .active(true)
            .otpDeliveryChannel(OtpDeliveryChannel.EMAIL)
            .userOtpSelectionPolicy(OtpSelectionPolicy.AT_LEAST_ONE)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        when(stationRepository.findBySaccoIdAndStationIdAndActiveTrue(SACCO_ID, STATION_ID))
            .thenReturn(Optional.of(station));
        when(stationRepository.save(station)).thenReturn(station);
        StationOtpSettingsService service =
            new StationOtpSettingsService(stationRepository, userSettingsService, auditService);

        service.update(SACCO_ID, STATION_ID, OtpDeliveryChannel.EMAIL, null, actorMemberId);

        assertThat(station.getUserOtpSelectionPolicy()).isEqualTo(OtpSelectionPolicy.NONE);
    }

    private StationOtpSettingsService serviceWithPolicy(OtpSelectionPolicy policy) {
        SaccoStation station = SaccoStation.builder()
            .active(true)
            .userOtpSelectionPolicy(policy)
            .build();
        when(stationRepository.findBySaccoIdAndStationIdAndActiveTrue(SACCO_ID, STATION_ID))
            .thenReturn(Optional.of(station));
        return new StationOtpSettingsService(stationRepository, userSettingsService, auditService);
    }
}
