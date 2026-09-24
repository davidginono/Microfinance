package com.sacco.mvp.web;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.PlatformSessionSettingsService;
import com.sacco.mvp.service.SessionReauthenticationService;
import com.sacco.mvp.service.SessionTimeoutPolicy;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionKeepaliveControllerTest {

    @Test
    void keepaliveRefreshesExistingSession() {
        PlatformSessionSettingsService settingsService = mock(PlatformSessionSettingsService.class);
        SessionReauthenticationService reauthenticationService = mock(SessionReauthenticationService.class);
        when(settingsService.policy()).thenReturn(new SessionTimeoutPolicy(30, 1_800_000L, 60_000L));
        SessionKeepaliveController controller = new SessionKeepaliveController(settingsService, reauthenticationService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession(true);
        AppUserPrincipal principal = principal();
        when(reauthenticationService.passwordMatches(principal, "current-password")).thenReturn(true);

        ResponseEntity<SessionKeepaliveController.SessionKeepalivePayload> response = controller.keepalive(request, principal, "current-password");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().refreshed()).isTrue();
        assertThat(response.getBody().message()).isNull();
        assertThat(session.getAttribute("sessionKeepaliveAt")).isInstanceOf(Long.class);
        assertThat(session.getMaxInactiveInterval()).isEqualTo(1800);
        verify(reauthenticationService).passwordMatches(principal, "current-password");
    }

    @Test
    void keepaliveDoesNotCreateSession() {
        PlatformSessionSettingsService settingsService = mock(PlatformSessionSettingsService.class);
        SessionReauthenticationService reauthenticationService = mock(SessionReauthenticationService.class);
        SessionKeepaliveController controller = new SessionKeepaliveController(settingsService, reauthenticationService);
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<SessionKeepaliveController.SessionKeepalivePayload> response = controller.keepalive(request, principal(), "current-password");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request.getSession(false)).isNull();
        verifyNoInteractions(settingsService);
        verifyNoInteractions(reauthenticationService);
    }

    @Test
    void keepaliveRejectsInvalidPasswordWithoutRefreshingSession() {
        PlatformSessionSettingsService settingsService = mock(PlatformSessionSettingsService.class);
        SessionReauthenticationService reauthenticationService = mock(SessionReauthenticationService.class);
        SessionKeepaliveController controller = new SessionKeepaliveController(settingsService, reauthenticationService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession(true);
        AppUserPrincipal principal = principal();
        when(reauthenticationService.passwordMatches(principal, "wrong-password")).thenReturn(false);

        ResponseEntity<SessionKeepaliveController.SessionKeepalivePayload> response = controller.keepalive(request, principal, "wrong-password");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().refreshed()).isFalse();
        assertThat(response.getBody().message()).contains("current password");
        assertThat(session.getAttribute("sessionKeepaliveAt")).isNull();
        verifyNoInteractions(settingsService);
    }

    private AppUserPrincipal principal() {
        Member member = Member.builder()
            .id(UUID.randomUUID())
            .memberNo("MEM001")
            .fullName("Test User")
            .memberAccount(true)
            .status(MemberStatus.ACTIVE)
            .passwordHash("encoded-password")
            .build();
        return new AppUserPrincipal(member, Set.of(), false);
    }
}
