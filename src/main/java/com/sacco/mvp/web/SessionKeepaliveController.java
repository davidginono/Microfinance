package com.sacco.mvp.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionKeepaliveController {
    private static final String KEEPALIVE_ATTRIBUTE = "sessionKeepaliveAt";

    @PostMapping("/session/keepalive")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SessionKeepalivePayload> keepalive(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        session.setAttribute(KEEPALIVE_ATTRIBUTE, System.currentTimeMillis());
        return ResponseEntity.ok(new SessionKeepalivePayload(true));
    }

    public record SessionKeepalivePayload(boolean refreshed) {
    }
}
