package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionReauthenticationServiceTest {

    @Test
    void activeAccountWithMatchingPasswordCanRefreshSession() {
        MemberDirectoryService directory = mock(MemberDirectoryService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        SessionReauthenticationService service = new SessionReauthenticationService(directory, passwordEncoder);
        Member member = member(MemberStatus.ACTIVE, "encoded-password");
        AppUserPrincipal principal = new AppUserPrincipal(member, Set.of(), false);
        when(directory.find(member.getId())).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("current-password", "encoded-password")).thenReturn(true);

        assertThat(service.passwordMatches(principal, "current-password")).isTrue();
    }

    @Test
    void wrongPasswordCannotRefreshSession() {
        MemberDirectoryService directory = mock(MemberDirectoryService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        SessionReauthenticationService service = new SessionReauthenticationService(directory, passwordEncoder);
        Member member = member(MemberStatus.ACTIVE, "encoded-password");
        AppUserPrincipal principal = new AppUserPrincipal(member, Set.of(), false);
        when(directory.find(member.getId())).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThat(service.passwordMatches(principal, "wrong-password")).isFalse();
    }

    @Test
    void inactiveAccountCannotRefreshSession() {
        MemberDirectoryService directory = mock(MemberDirectoryService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        SessionReauthenticationService service = new SessionReauthenticationService(directory, passwordEncoder);
        Member member = member(MemberStatus.INACTIVE, "encoded-password");
        AppUserPrincipal principal = new AppUserPrincipal(member, Set.of(), false);
        when(directory.find(member.getId())).thenReturn(Optional.of(member));

        assertThat(service.passwordMatches(principal, "current-password")).isFalse();
    }

    @Test
    void otpOnlyPlaceholderCannotRefreshSessionAsPassword() {
        MemberDirectoryService directory = mock(MemberDirectoryService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        SessionReauthenticationService service = new SessionReauthenticationService(directory, passwordEncoder);
        Member member = member(MemberStatus.ACTIVE, "OTP_ONLY_LOGIN");
        AppUserPrincipal principal = new AppUserPrincipal(member, Set.of(), false);
        when(directory.find(member.getId())).thenReturn(Optional.of(member));

        assertThat(service.passwordMatches(principal, "OTP_ONLY_LOGIN")).isFalse();
    }

    private Member member(MemberStatus status, String passwordHash) {
        return Member.builder()
            .id(UUID.randomUUID())
            .memberNo("MEM001")
            .fullName("Test User")
            .memberAccount(true)
            .status(status)
            .passwordHash(passwordHash)
            .build();
    }
}
