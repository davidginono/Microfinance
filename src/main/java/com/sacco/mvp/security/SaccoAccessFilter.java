package com.sacco.mvp.security;

import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.service.StationAccessService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SaccoAccessFilter extends OncePerRequestFilter {
    static final String CHECKED_AT_ATTR = SaccoAccessFilter.class.getName() + ".checkedAt";
    static final long RECHECK_AFTER_MS = 30_000L;

    private final StationAccessService stationAccessService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof AppUserPrincipal appUser) {
            Optional<SaccoStation> suspendedStation = suspendedStation(request, appUser);
            if (suspendedStation.isEmpty()) {
                filterChain.doFilter(request, response);
                return;
            }
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            request.getSession(true).setAttribute("loginErrorMessage", suspendedMessage(suspendedStation.get()));
            response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + "/login?error"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Optional<SaccoStation> suspendedStation(HttpServletRequest request, AppUserPrincipal appUser) {
        if (appUser == null || appUser.isPlatformIdentity()) {
            return Optional.empty();
        }
        HttpSession session = request.getSession(false);
        long now = System.currentTimeMillis();
        if (session != null) {
            Object checkedAt = session.getAttribute(CHECKED_AT_ATTR);
            if (checkedAt instanceof Long checked && now - checked < RECHECK_AFTER_MS) {
                return Optional.empty();
            }
        }
        Optional<SaccoStation> suspended = stationAccessService.suspendedStation(appUser.getSaccoId(), appUser.getStationId());
        if (suspended.isEmpty() && session != null) {
            session.setAttribute(CHECKED_AT_ATTR, now);
        }
        return suspended;
    }

    private String suspendedMessage(SaccoStation station) {
        String reason = station.getAccessRestrictionReason();
        if (reason == null || reason.isBlank()) {
            return "This station workspace has been suspended. Contact the platform administrator.";
        }
        return "This station workspace has been suspended: " + reason.trim();
    }
}
