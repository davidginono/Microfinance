package com.sacco.mvp.security;

import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.repository.SaccoStationRepository;
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
    private final SaccoStationRepository saccoStationRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof AppUserPrincipal appUser) {
            Optional<SaccoStation> suspendedStation = suspendedStation(appUser);
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

    private Optional<SaccoStation> suspendedStation(AppUserPrincipal appUser) {
        if (appUser == null || appUser.hasRole(Position.ADMIN)) {
            return Optional.empty();
        }
        String saccoId = appUser.getSaccoId();
        String stationId = appUser.getStationId();
        if (saccoId == null || saccoId.isBlank() || stationId == null || stationId.isBlank()) {
            return Optional.empty();
        }
        return saccoStationRepository.findBySaccoIdAndStationId(saccoId, stationId)
            .filter(SaccoStation::isAccessSuspended);
    }

    private String suspendedMessage(SaccoStation station) {
        String reason = station.getAccessRestrictionReason();
        if (reason == null || reason.isBlank()) {
            return "This station workspace has been suspended. Contact the platform administrator.";
        }
        return "This station workspace has been suspended: " + reason.trim();
    }
}
