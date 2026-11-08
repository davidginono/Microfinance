package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.OtpSelectionPolicy;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.StaffAccessStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserOtpPreferenceServiceTest {
    @Mock private UserSettingsService userSettingsService;
    @Mock private StationOtpSettingsService stationOtpSettingsService;
    @Mock private MemberDirectoryService memberDirectoryService;
    @Mock private AuditService auditService;

    @Test
    void disablingBothOptionsNeedsNoExtraVerificationAndIsAudited() {
        UUID memberId = UUID.randomUUID();
        Member member = member(memberId, "encoded-password");
        UserOtpPreferenceService service = service();
        when(memberDirectoryService.find(memberId)).thenReturn(Optional.of(member));
        when(userSettingsService.otpPreferences(memberId))
            .thenReturn(new UserSettingsService.OtpPreferences(true, true));
        when(userSettingsService.updateOtpPreferences(memberId, false, false))
            .thenReturn(new UserSettingsService.OtpPreferences(false, false));

        service.update(memberId, false, false);

        verify(userSettingsService).updateOtpPreferences(memberId, false, false);
        verify(auditService).log(
            eq("USER_SETTINGS"), eq(memberId), eq("USER_UPDATE_OTP_PREFERENCES"),
            eq(memberId),
            eq(java.util.Map.of("loginOtpEnabled", true, "approvalOtpEnabled", true,
                "saccoId", member.getSaccoId(), "stationId", member.getStationId())),
            eq(java.util.Map.of("loginOtpEnabled", false, "approvalOtpEnabled", false,
                "saccoId", member.getSaccoId(), "stationId", member.getStationId()))
        );
    }

    @Test
    void missingAccountCannotChangePreferences() {
        UUID memberId = UUID.randomUUID();
        when(memberDirectoryService.find(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(memberId, false, false))
            .isInstanceOf(IllegalStateException.class);

        verify(userSettingsService, never()).updateOtpPreferences(any(), anyBoolean(), anyBoolean());
    }

    @Test
    void atLeastOnePolicyRejectsNeitherOption() {
        UUID memberId = UUID.randomUUID();
        Member member = member(memberId, "encoded-password");
        UserOtpPreferenceService service = service();
        when(memberDirectoryService.find(memberId)).thenReturn(Optional.of(member));
        when(stationOtpSettingsService.selectionPolicy(member.getSaccoId(), member.getStationId(), false))
            .thenReturn(OtpSelectionPolicy.AT_LEAST_ONE);

        assertThatThrownBy(() -> service.update(memberId, false, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Select login OTP, approval OTP, or both");

        verify(userSettingsService, never()).updateOtpPreferences(any(), anyBoolean(), anyBoolean());
    }

    @Test
    void noMinimumPolicyAllowsNeitherOption() {
        UUID memberId = UUID.randomUUID();
        Member member = member(memberId, "encoded-password");
        UserOtpPreferenceService service = service();
        when(memberDirectoryService.find(memberId)).thenReturn(Optional.of(member));
        when(stationOtpSettingsService.selectionPolicy(member.getSaccoId(), member.getStationId(), false))
            .thenReturn(OtpSelectionPolicy.NONE);
        when(userSettingsService.otpPreferences(memberId))
            .thenReturn(new UserSettingsService.OtpPreferences(false, false));
        when(userSettingsService.updateOtpPreferences(memberId, false, false))
            .thenReturn(new UserSettingsService.OtpPreferences(false, false));

        service.update(memberId, false, false);

        verify(userSettingsService).updateOtpPreferences(memberId, false, false);
    }

    @Test
    void bothPolicyRejectsAUserSelectingOnlyOneOption() {
        UUID memberId = UUID.randomUUID();
        Member member = member(memberId, "encoded-password");
        UserOtpPreferenceService service = service();
        when(memberDirectoryService.find(memberId)).thenReturn(Optional.of(member));
        when(stationOtpSettingsService.selectionPolicy(member.getSaccoId(), member.getStationId(), false))
            .thenReturn(OtpSelectionPolicy.BOTH);

        assertThatThrownBy(() -> service.update(memberId, true, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requires OTP for both");

        verify(userSettingsService, never()).updateOtpPreferences(any(), anyBoolean(), anyBoolean());
    }

    @Test
    void platformAdminCanChooseNeitherWithoutAStationPolicy() {
        UUID memberId = UUID.randomUUID();
        Member member = member(memberId, "encoded-password");
        member.setSaccoId(null);
        member.setStationId(null);
        member.setPosition(Position.ADMIN);
        member.setStaffAccessStatus(StaffAccessStatus.ACTIVE);
        UserOtpPreferenceService service = service();
        when(memberDirectoryService.find(memberId)).thenReturn(Optional.of(member));
        when(stationOtpSettingsService.selectionPolicy(null, null, true))
            .thenReturn(OtpSelectionPolicy.NONE);
        when(userSettingsService.otpPreferences(memberId))
            .thenReturn(new UserSettingsService.OtpPreferences(false, false));
        when(userSettingsService.updateOtpPreferences(memberId, false, false))
            .thenReturn(new UserSettingsService.OtpPreferences(false, false));

        service.update(memberId, false, false);

        verify(userSettingsService).updateOtpPreferences(memberId, false, false);
    }

    private UserOtpPreferenceService service() {
        return new UserOtpPreferenceService(
            userSettingsService,
            stationOtpSettingsService,
            memberDirectoryService,
            auditService
        );
    }

    private Member member(UUID memberId, String passwordHash) {
        return Member.builder()
            .id(memberId)
            .email("member@example.com")
            .phone("255700000001")
            .saccoId("SACCO-01")
            .stationId("ST-01")
            .passwordHash(passwordHash)
            .build();
    }

}
