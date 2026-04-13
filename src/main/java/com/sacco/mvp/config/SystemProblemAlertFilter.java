package com.sacco.mvp.config;

import com.sacco.mvp.service.AdminAlertService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SystemProblemAlertFilter extends OncePerRequestFilter {
    private final AdminAlertService adminAlertService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("method", request.getMethod());
            details.put("path", request.getRequestURI());
            Principal principal = request.getUserPrincipal();
            details.put("user", principal == null ? "anonymous" : principal.getName());
            details.put("error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            adminAlertService.alertAllAdmins(
                "Web Request",
                "Unhandled application error",
                "A web request failed before completing successfully.",
                details
            );
            throw ex;
        }
    }
}
