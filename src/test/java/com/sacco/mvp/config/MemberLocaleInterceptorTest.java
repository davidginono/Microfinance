package com.sacco.mvp.config;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.UserSettings;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.UserSettingsRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MemberLocaleInterceptorTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void jsonRequestSkipsDatabaseBackedLocaleResolution() {
        UserSettingsRepository userSettings = mock(UserSettingsRepository.class);
        SaccoSettingsRepository saccoSettings = mock(SaccoSettingsRepository.class);
        MemberLocaleInterceptor interceptor = new MemberLocaleInterceptor(userSettings, saccoSettings);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/dashboard/external-account-status");
        request.addHeader("Accept", "application/json");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        verifyNoInteractions(userSettings, saccoSettings);
    }

    @Test
    void memberHtmlRequestsReuseSessionCachedLocale() {
        UUID memberId = UUID.randomUUID();
        UserSettingsRepository userSettings = mock(UserSettingsRepository.class);
        SaccoSettingsRepository saccoSettings = mock(SaccoSettingsRepository.class);
        when(userSettings.findById(memberId)).thenReturn(Optional.of(UserSettings.builder()
            .memberId(memberId)
            .language("sw")
            .build()));
        MemberLocaleInterceptor interceptor = new MemberLocaleInterceptor(userSettings, saccoSettings);
        authenticateMember(memberId);
        MockHttpServletRequest firstRequest = new MockHttpServletRequest("GET", "/app/dashboard");

        interceptor.preHandle(firstRequest, new MockHttpServletResponse(), new Object());
        MockHttpServletRequest secondRequest = new MockHttpServletRequest("GET", "/app/loan-applications");
        secondRequest.setSession(firstRequest.getSession());
        interceptor.preHandle(secondRequest, new MockHttpServletResponse(), new Object());

        verify(userSettings, times(1)).findById(memberId);
        verifyNoInteractions(saccoSettings);
    }

    @Test
    void updatedUserLocaleReplacesCachedValueWithoutDatabaseLookup() {
        UUID memberId = UUID.randomUUID();
        UserSettingsRepository userSettings = mock(UserSettingsRepository.class);
        SaccoSettingsRepository saccoSettings = mock(SaccoSettingsRepository.class);
        MemberLocaleInterceptor interceptor = new MemberLocaleInterceptor(userSettings, saccoSettings);
        authenticateMember(memberId);
        MockHttpServletRequest updateRequest = new MockHttpServletRequest("POST", "/app/settings/language");
        interceptor.cacheUserLocale(updateRequest, memberId, "sw");
        MockHttpServletRequest nextRequest = new MockHttpServletRequest("GET", "/app/dashboard");
        nextRequest.setSession(updateRequest.getSession());

        interceptor.preHandle(nextRequest, new MockHttpServletResponse(), new Object());

        assertThat(nextRequest.getSession().getAttribute(
            MemberLocaleInterceptor.class.getName() + ".USER_LOCALE." + memberId)).isEqualTo(java.util.Locale.of("sw"));
        verifyNoInteractions(userSettings, saccoSettings);
    }

    private void authenticateMember(UUID memberId) {
        Member member = Member.builder()
            .id(memberId)
            .saccoId("SACCO-1")
            .memberNo("MEM-1")
            .fullName("Member")
            .position(Position.MEMBER)
            .memberAccount(true)
            .passwordHash("secret")
            .build();
        AppUserPrincipal principal = new AppUserPrincipal(member, Collections.emptySet());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities()));
    }
}
