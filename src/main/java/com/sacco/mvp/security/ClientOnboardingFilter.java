package com.sacco.mvp.security;

import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Enforces current verification without caching an approval in the login session. */
@RequiredArgsConstructor
public class ClientOnboardingFilter extends OncePerRequestFilter {
    private final OnboardingService onboarding;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getServletPath();
        if (path.isEmpty()) {
            path = request.getRequestURI().substring(request.getContextPath().length());
        }
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal actor
            && actor.isMemberAccess() && !actor.isStaffSession() && within(path, "/app")) {
            var status = onboarding.ownStatus(actor);
            boolean required = status == null || !"VERIFY".equals(status.state());
            request.setAttribute("clientOnboardingRequired", required);
            if (required && !within(path, "/app/onboarding") && !within(path, "/app/settings")
                && !within(path, "/app/support") && !within(path, "/app/notifications")) {
                String destination = request.getContextPath() + "/app/onboarding";
                response.setHeader("Cache-Control", "no-store");
                String accept = request.getHeader("Accept");
                if (("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
                    && (accept == null || !accept.contains("application/json"))) {
                    response.sendRedirect(destination);
                } else {
                    // Never replay a blocked mutation after onboarding or redirect it as a POST.
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setHeader("Location", destination);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"client_onboarding_required\"}");
                }
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean within(String path, String root) {
        return path.equals(root) || path.startsWith(root + "/");
    }
}
