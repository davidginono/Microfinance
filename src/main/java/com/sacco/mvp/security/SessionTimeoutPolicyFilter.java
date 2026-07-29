package com.sacco.mvp.security;

import com.sacco.mvp.service.PlatformSessionSettingsService;
import com.sacco.mvp.service.SessionTimeoutPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class SessionTimeoutPolicyFilter extends OncePerRequestFilter {
    private final PlatformSessionSettingsService platformSessionSettingsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        applySessionTimeout(request);
        filterChain.doFilter(request, response);
    }

    private void applySessionTimeout(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal)) {
            return;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        SessionTimeoutPolicy policy = platformSessionSettingsService.policy();
        session.setMaxInactiveInterval(policy.timeoutSeconds());
    }
}
