package com.sacco.mvp.web;

import com.sacco.mvp.service.PlatformSessionSettingsService;
import com.sacco.mvp.service.SessionTimeoutPolicy;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SessionKeepaliveControllerTest {

    @Test
    void keepaliveRefreshesExistingSession() {
        PlatformSessionSettingsService settingsService = mock(PlatformSessionSettingsService.class);
        when(settingsService.policy()).thenReturn(new SessionTimeoutPolicy(30, 1_800_000L, 60_000L));
        SessionKeepaliveController controller = new SessionKeepaliveController(settingsService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession(true);

        ResponseEntity<SessionKeepaliveController.SessionKeepalivePayload> response = controller.keepalive(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().refreshed()).isTrue();
        assertThat(session.getAttribute("sessionKeepaliveAt")).isInstanceOf(Long.class);
        assertThat(session.getMaxInactiveInterval()).isEqualTo(1800);
    }

    @Test
    void keepaliveDoesNotCreateSession() {
        PlatformSessionSettingsService settingsService = mock(PlatformSessionSettingsService.class);
        SessionKeepaliveController controller = new SessionKeepaliveController(settingsService);
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<SessionKeepaliveController.SessionKeepalivePayload> response = controller.keepalive(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request.getSession(false)).isNull();
        verifyNoInteractions(settingsService);
    }
}
