package com.sacco.mvp.web;

import com.sacco.mvp.service.PlatformSessionSettingsService;
import com.sacco.mvp.service.SessionTimeoutPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SessionKeepaliveController {
    private static final String KEEPALIVE_ATTRIBUTE = "sessionKeepaliveAt";

    private final PlatformSessionSettingsService platformSessionSettingsService;

    @PostMapping("/session/keepalive")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SessionKeepalivePayload> keepalive(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        SessionTimeoutPolicy policy = platformSessionSettingsService.policy();
        session.setMaxInactiveInterval(policy.timeoutSeconds());
        session.setAttribute(KEEPALIVE_ATTRIBUTE, System.currentTimeMillis());
        return ResponseEntity.ok(new SessionKeepalivePayload(true));
    }

    public record SessionKeepalivePayload(boolean refreshed) {
    }
}
