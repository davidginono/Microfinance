package com.sacco.mvp.service;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.OtpDeliveryChannel;
import com.sacco.mvp.domain.OtpSelectionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
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
    @Mock private EmailOtpService emailOtpService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;

    @Test
    void disablingProtectionRequiresPasswordOrOtp() {
        UUID memberId = UUID.randomUUID();
        Member member = member(memberId, "encoded-password");
        UserOtpPreferenceService service = service();
        when(memberDirectoryService.find(memberId)).thenReturn(Optional.of(member));
        when(userSettingsService.otpPreferences(memberId))
            .thenReturn(new UserSettingsService.OtpPreferences(true, true));

        assertThatThrownBy(() -> service.update(memberId, false, true, "", ""))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("current password or request an OTP");

        verify(userSettingsService, never()).updateOtpPreferences(any(), anyBoolean(), anyBoolean());
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void currentPasswordCanConfirmDisablingProtection() {
        UUID memberId = UUID.randomUUID();
        Member member = member(memberId, "encoded-password");
        UserOtpPreferenceService service = service();
        when(memberDirectoryService.find(memberId)).thenReturn(Optional.of(member));
        when(userSettingsService.otpPreferences(memberId))
            .thenReturn(new UserSettingsService.OtpPreferences(true, false));
        when(passwordEncoder.matches("correct-password", "encoded-password")).thenReturn(true);
        when(userSettingsService.updateOtpPreferences(memberId, false, true))
            .thenReturn(new UserSettingsService.OtpPreferences(false, true));

        service.update(memberId, false, true, "correct-password", "");

        verify(userSettingsService).updateOtpPreferences(memberId, false, true);
        verify(emailOtpService, never()).validateOtp(any(), any(), any(), any());
        verify(auditService).log(
            eq("USER_SETTINGS"),
            eq(memberId),
            eq("USER_UPDATE_OTP_PREFERENCES"),
            eq(memberId),
            any(),
            any()
        );
    }

    @Test
    void otpOnlyAccountCanConfirmDisablingProtectionWithOtp() {
        UUID memberId = UUID.randomUUID();
        UUID otpTokenId = UUID.randomUUID();
        Member member = member(memberId, "OTP_ONLY_LOGIN");
        UserOtpPreferenceService service = service();
        when(memberDirectoryService.find(memberId)).thenReturn(Optional.of(member));
        when(userSettingsService.otpPreferences(memberId))
            .thenReturn(new UserSettingsService.OtpPreferences(false, true));
        when(emailOtpService.validateOtp(
            member.getEmail(),
            EmailOtpPurpose.SECURITY_PREFERENCE_CHANGE,
            memberId,
            "123456"
        )).thenReturn(otpTokenId);
        when(userSettingsService.updateOtpPreferences(memberId, true, false))
            .thenReturn(new UserSettingsService.OtpPreferences(true, false));

        service.update(memberId, true, false, "OTP_ONLY_LOGIN", "123456");

        verify(passwordEncoder, never()).matches(any(), any());
        verify(emailOtpService).consumeOtpById(otpTokenId);
        verify(userSettingsService).updateOtpPreferences(memberId, true, false);
    }

    @Test
    void atLeastOnePolicyRejectsNeitherOption() {
        UUID memberId = UUID.randomUUID();
        Member member = member(memberId, "encoded-password");
        UserOtpPreferenceService service = service();
        when(memberDirectoryService.find(memberId)).thenReturn(Optional.of(member));
        when(stationOtpSettingsService.selectionPolicy(member.getSaccoId(), member.getStationId()))
            .thenReturn(OtpSelectionPolicy.AT_LEAST_ONE);

        assertThatThrownBy(() -> service.update(memberId, false, false, "correct-password", ""))
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
        when(stationOtpSettingsService.selectionPolicy(member.getSaccoId(), member.getStationId()))
            .thenReturn(OtpSelectionPolicy.NONE);
        when(userSettingsService.otpPreferences(memberId))
            .thenReturn(new UserSettingsService.OtpPreferences(false, false));
        when(userSettingsService.updateOtpPreferences(memberId, false, false))
            .thenReturn(new UserSettingsService.OtpPreferences(false, false));

        service.update(memberId, false, false, "", "");

        verify(userSettingsService).updateOtpPreferences(memberId, false, false);
    }

    @Test
    void bothPolicyRejectsAUserSelectingOnlyOneOption() {
        UUID memberId = UUID.randomUUID();
        Member member = member(memberId, "encoded-password");
        UserOtpPreferenceService service = service();
        when(memberDirectoryService.find(memberId)).thenReturn(Optional.of(member));
        when(stationOtpSettingsService.selectionPolicy(member.getSaccoId(), member.getStationId()))
            .thenReturn(OtpSelectionPolicy.BOTH);

        assertThatThrownBy(() -> service.update(memberId, true, false, "", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requires OTP for both");

        verify(userSettingsService, never()).updateOtpPreferences(any(), anyBoolean(), anyBoolean());
    }

    @Test
    void confirmationOtpUsesDedicatedPurposeAndUserScope() {
        UUID memberId = UUID.randomUUID();
        Member member = member(memberId, "encoded-password");
        UserOtpPreferenceService service = service();
        when(memberDirectoryService.find(memberId)).thenReturn(Optional.of(member));
        when(emailOtpService.issueOtpWithMetadata(
            eq(member.getEmail()),
            eq(EmailOtpPurpose.SECURITY_PREFERENCE_CHANGE),
            eq(memberId),
            any(),
            any(),
            eq(member.getSaccoId()),
            eq(member.getStationId()),
            eq(member.getPhone())
        )).thenReturn(otpResult());

        service.requestConfirmationOtp(memberId);

        verify(emailOtpService).issueOtpWithMetadata(
            eq(member.getEmail()),
            eq(EmailOtpPurpose.SECURITY_PREFERENCE_CHANGE),
            eq(memberId),
            any(),
            any(),
            eq(member.getSaccoId()),
            eq(member.getStationId()),
            eq(member.getPhone())
        );
    }

    private UserOtpPreferenceService service() {
        return new UserOtpPreferenceService(
            userSettingsService,
            stationOtpSettingsService,
            memberDirectoryService,
            emailOtpService,
            passwordEncoder,
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

    private EmailOtpService.OtpIssueResult otpResult() {
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(10);
        return new EmailOtpService.OtpIssueResult(
            true,
            new StationOtpDeliveryService.DeliveryReceipt(OtpDeliveryChannel.EMAIL, "OTP sent."),
            expiresAt,
            600,
            OffsetDateTime.now(),
            0,
            3,
            3
        );
    }
}
