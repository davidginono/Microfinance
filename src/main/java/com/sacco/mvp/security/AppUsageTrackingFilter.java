package com.sacco.mvp.security;

import com.sacco.mvp.service.AppUsageAnalyticsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class AppUsageTrackingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AppUsageTrackingFilter.class);

    private final AppUsageAnalyticsService usageAnalyticsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
                try {
                    usageAnalyticsService.recordAuthenticatedRequest(principal, request);
                } catch (RuntimeException ex) {
                    log.warn("App usage tracking failed for {}", request.getRequestURI(), ex);
                }
            }
        }
    }
}
