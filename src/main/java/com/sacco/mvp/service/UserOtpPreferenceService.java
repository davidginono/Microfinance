package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.OtpSelectionPolicy;
import com.sacco.mvp.domain.Position;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserOtpPreferenceService {
    private final UserSettingsService userSettingsService;
    private final StationOtpSettingsService stationOtpSettingsService;
    private final MemberDirectoryService memberDirectoryService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public OtpSecuritySettings current(UUID memberId) {
        Member member = requireMember(memberId);
        OtpSelectionPolicy policy = selectionPolicy(member);
        return new OtpSecuritySettings(effectivePreferences(memberId, policy), policy);
    }

    @Transactional
    public UserSettingsService.OtpPreferences update(UUID memberId,
                                                     boolean loginOtpEnabled,
                                                     boolean approvalOtpEnabled) {
        Member member = requireMember(memberId);
        OtpSelectionPolicy policy = selectionPolicy(member);
        validateSelection(policy, loginOtpEnabled, approvalOtpEnabled);
        UserSettingsService.OtpPreferences before = effectivePreferences(memberId, policy);

        UserSettingsService.OtpPreferences saved = userSettingsService.updateOtpPreferences(
            memberId,
            loginOtpEnabled,
            approvalOtpEnabled
        );
        auditService.log(
            "USER_SETTINGS",
            memberId,
            "USER_UPDATE_OTP_PREFERENCES",
            memberId,
            auditState(member, before),
            auditState(member, saved)
        );
        return saved;
    }

    private Member requireMember(UUID memberId) {
        return memberDirectoryService.find(memberId)
            .orElseThrow(() -> new IllegalStateException("Your user account is unavailable."));
    }

    private OtpSelectionPolicy selectionPolicy(Member member) {
        return stationOtpSettingsService.selectionPolicy(
            member.getSaccoId(),
            member.getStationId(),
            member.getStaffRolesResolved().contains(Position.ADMIN)
        );
    }

    private UserSettingsService.OtpPreferences effectivePreferences(UUID memberId, OtpSelectionPolicy policy) {
        UserSettingsService.OtpPreferences preferences = userSettingsService.otpPreferences(memberId);
        if (policy == OtpSelectionPolicy.BOTH) {
            return new UserSettingsService.OtpPreferences(true, true);
        }
        if (policy == OtpSelectionPolicy.AT_LEAST_ONE
            && !preferences.loginOtpEnabled()
            && !preferences.approvalOtpEnabled()) {
            return new UserSettingsService.OtpPreferences(true, false);
        }
        return preferences;
    }

    private void validateSelection(OtpSelectionPolicy policy,
                                   boolean loginOtpEnabled,
                                   boolean approvalOtpEnabled) {
        if (policy == OtpSelectionPolicy.BOTH && (!loginOtpEnabled || !approvalOtpEnabled)) {
            throw new IllegalArgumentException("Your SACCO requires OTP for both login and approvals.");
        }
        if (policy == OtpSelectionPolicy.AT_LEAST_ONE && !loginOtpEnabled && !approvalOtpEnabled) {
            throw new IllegalArgumentException("Select login OTP, approval OTP, or both.");
        }
    }

    private Map<String, Object> auditState(Member member, UserSettingsService.OtpPreferences preferences) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("loginOtpEnabled", preferences.loginOtpEnabled());
        state.put("approvalOtpEnabled", preferences.approvalOtpEnabled());
        state.put("saccoId", member.getSaccoId());
        state.put("stationId", member.getStationId());
        return state;
    }

    public record OtpSecuritySettings(UserSettingsService.OtpPreferences preferences,
                                      OtpSelectionPolicy selectionPolicy) {
    }
}
