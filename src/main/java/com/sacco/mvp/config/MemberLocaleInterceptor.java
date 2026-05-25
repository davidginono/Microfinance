package com.sacco.mvp.config;

import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.UserSettings;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.UserSettingsRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MemberLocaleInterceptor implements HandlerInterceptor {
    private final UserSettingsRepository userSettingsRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return true;
        }

        Locale locale = resolveRequestLocale(request, principal);
        if (locale == null) {
            return true;
        }

        LocaleResolver resolver = RequestContextUtils.getLocaleResolver(request);
        if (resolver != null) {
            resolver.setLocale(request, response, locale);
        }
        return true;
    }

    private Locale resolveRequestLocale(HttpServletRequest request, AppUserPrincipal principal) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        String adminPrefix = contextPath + "/admin/";
        String appPrefix = contextPath + "/app/";
        boolean staffPath = path.startsWith(contextPath + "/staff/")
            || path.startsWith(contextPath + "/manager/")
            || path.startsWith(contextPath + "/board/")
            || path.startsWith(contextPath + "/loan-officer/")
            || path.startsWith(contextPath + "/accountant/")
            || path.startsWith(contextPath + "/disbursement/");
        if (path.startsWith(adminPrefix) && principal.getStaffRoles().stream().anyMatch(Position::isAdminRole)) {
            String saccoId = principal.getSaccoId();
            if (saccoId == null || saccoId.isBlank()) {
                return null;
            }
            return saccoSettingsRepository.findById(saccoId)
                .map(settings -> resolveLocale(settings.getDefaultLanguage()))
                .orElse(Locale.ENGLISH);
        }
        if (path.startsWith(appPrefix) && principal.hasRole(Position.MEMBER)) {
            return userSettingsRepository.findById(principal.getMemberId())
                .map(UserSettings::getLanguage)
                .map(this::resolveLocale)
                .orElse(Locale.ENGLISH);
        }
        if (staffPath && !principal.hasRole(Position.ADMIN) && !principal.hasRole(Position.MINOR_ADMIN)) {
            return userSettingsRepository.findById(principal.getMemberId())
                .map(UserSettings::getLanguage)
                .map(this::resolveLocale)
                .orElse(Locale.ENGLISH);
        }
        return null;
    }

    private Locale resolveLocale(String language) {
        if (language == null || language.isBlank()) {
            return Locale.ENGLISH;
        }
        return switch (language.trim().toLowerCase(Locale.ROOT)) {
            case "sw", "swahili" -> Locale.of("sw");
            default -> Locale.ENGLISH;
        };
    }
}
