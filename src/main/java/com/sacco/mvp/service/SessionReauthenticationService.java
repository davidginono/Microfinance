package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionReauthenticationService {
    private static final String OTP_ONLY_PASSWORD = "OTP_ONLY_LOGIN";

    private final MemberDirectoryService memberDirectoryService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public boolean passwordMatches(AppUserPrincipal principal, String currentPassword) {
        if (principal == null || currentPassword == null || currentPassword.isBlank()) {
            return false;
        }
        return memberDirectoryService.find(principal.getMemberId())
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .filter(member -> passwordMatches(member, currentPassword))
            .isPresent();
    }

    private boolean passwordMatches(Member member, String currentPassword) {
        String storedPassword = member.getPasswordHash();
        return storedPassword != null
            && !storedPassword.isBlank()
            && !OTP_ONLY_PASSWORD.equals(storedPassword)
            && passwordEncoder.matches(currentPassword, storedPassword);
    }
}
