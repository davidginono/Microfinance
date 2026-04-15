package com.sacco.mvp.config;

import com.sacco.mvp.domain.Position;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AdminScopeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class AdminScopeInterceptor implements HandlerInterceptor {
    private final AdminScopeService adminScopeService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return true;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AppUserPrincipal userPrincipal) || !userPrincipal.hasRole(Position.ADMIN)) {
            return true;
        }
        if (adminScopeService.hasExplicitScopeSelection()) {
            return true;
        }

        String requestUri = request.getRequestURI();
        if (!requiresScopeSelection(requestUri)) {
            return true;
        }

        String next = requestUri;
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            next += "?" + query;
        }
        String redirectUrl = UriComponentsBuilder.fromPath("/admin/scope/select")
            .queryParam("next", next)
            .build()
            .toUriString();
        response.sendRedirect(redirectUrl);
        return false;
    }

    private boolean requiresScopeSelection(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return false;
        }
        return requestUri.equals("/admin/dashboard")
            || requestUri.startsWith("/admin/messages")
            || requestUri.startsWith("/admin/incidents")
            || requestUri.startsWith("/admin/users")
            || requestUri.startsWith("/admin/settings-controls")
            || requestUri.startsWith("/admin/loan-products");
    }
}
