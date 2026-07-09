package com.sacco.mvp.web;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class SessionKeepaliveControllerTest {

    @Test
    void keepaliveRefreshesExistingSession() {
        SessionKeepaliveController controller = new SessionKeepaliveController();
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession(true);

        ResponseEntity<SessionKeepaliveController.SessionKeepalivePayload> response = controller.keepalive(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().refreshed()).isTrue();
        assertThat(session.getAttribute("sessionKeepaliveAt")).isInstanceOf(Long.class);
    }

    @Test
    void keepaliveDoesNotCreateSession() {
        SessionKeepaliveController controller = new SessionKeepaliveController();
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<SessionKeepaliveController.SessionKeepalivePayload> response = controller.keepalive(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request.getSession(false)).isNull();
    }
}
