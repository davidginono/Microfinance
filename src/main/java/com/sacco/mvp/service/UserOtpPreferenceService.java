package com.sacco.mvp.service;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserOtpPreferenceService {
    private static final String OTP_ONLY_PASSWORD = "OTP_ONLY_LOGIN";

    private final UserSettingsService userSettingsService;
    private final MemberDirectoryService memberDirectoryService;
    private final EmailOtpService emailOtpService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public UserSettingsService.OtpPreferences current(UUID memberId) {
        return userSettingsService.otpPreferences(memberId);
    }

    @Transactional
    public EmailOtpService.OtpIssueResult requestConfirmationOtp(UUID memberId) {
        Member member = requireMember(memberId);
        String email = requireEmail(member);
        return emailOtpService.issueOtpWithMetadata(
            email,
            EmailOtpPurpose.SECURITY_PREFERENCE_CHANGE,
            member.getId(),
            "Your OTP security preference code",
            "Use this OTP code to confirm a change to your login and approval security preferences.",
            member.getSaccoId(),
            member.getStationId(),
            member.getPhone()
        );
    }

    @Transactional
    public UserSettingsService.OtpPreferences update(UUID memberId,
                                                     boolean loginOtpEnabled,
                                                     boolean approvalOtpEnabled,
                                                     String currentPassword,
                                                     String otpCode) {
        Member member = requireMember(memberId);
        UserSettingsService.OtpPreferences before = userSettingsService.otpPreferences(memberId);
        boolean disablingProtection = (before.loginOtpEnabled() && !loginOtpEnabled)
            || (before.approvalOtpEnabled() && !approvalOtpEnabled);

        UUID confirmationOtpId = null;
        if (disablingProtection) {
            boolean passwordConfirmed = passwordMatches(member, currentPassword);
            if (!passwordConfirmed) {
                if (otpCode == null || otpCode.isBlank()) {
                    throw new IllegalStateException("Enter your current password or request an OTP code before turning off OTP protection.");
                }
                confirmationOtpId = emailOtpService.validateOtp(
                    requireEmail(member),
                    EmailOtpPurpose.SECURITY_PREFERENCE_CHANGE,
                    member.getId(),
                    otpCode
                );
            }
        }

        UserSettingsService.OtpPreferences saved = userSettingsService.updateOtpPreferences(
            memberId,
            loginOtpEnabled,
            approvalOtpEnabled
        );
        if (confirmationOtpId != null) {
            emailOtpService.consumeOtpById(confirmationOtpId);
        }
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

    private String requireEmail(Member member) {
        if (member.getEmail() == null || member.getEmail().isBlank()) {
            throw new IllegalStateException("Add an email address to your profile before requesting a confirmation OTP.");
        }
        return member.getEmail();
    }

    private boolean passwordMatches(Member member, String currentPassword) {
        String storedPassword = member.getPasswordHash();
        return currentPassword != null
            && !currentPassword.isBlank()
            && storedPassword != null
            && !OTP_ONLY_PASSWORD.equals(storedPassword)
            && passwordEncoder.matches(currentPassword, storedPassword);
    }

    private Map<String, Object> auditState(Member member, UserSettingsService.OtpPreferences preferences) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("loginOtpEnabled", preferences.loginOtpEnabled());
        state.put("approvalOtpEnabled", preferences.approvalOtpEnabled());
        state.put("saccoId", member.getSaccoId());
        state.put("stationId", member.getStationId());
        return state;
    }
}
