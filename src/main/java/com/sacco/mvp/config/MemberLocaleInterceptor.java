package com.sacco.mvp.config;

import com.sacco.mvp.domain.UserSettings;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.UserSettingsRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.web.WebRequestClassifier;
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
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MemberLocaleInterceptor implements HandlerInterceptor {
    private static final String USER_LOCALE_SESSION_PREFIX = MemberLocaleInterceptor.class.getName() + ".USER_LOCALE.";
    private final UserSettingsRepository userSettingsRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (WebRequestClassifier.isJsonRequest(request)) {
            return true;
        }
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
        if (path.startsWith(adminPrefix) && (principal.isPlatformIdentity() || principal.isWorkspaceAdminScope())) {
            String saccoId = principal.getSaccoId();
            if (saccoId == null || saccoId.isBlank()) {
                return null;
            }
            return saccoSettingsRepository.findById(saccoId)
                .map(settings -> resolveLocale(settings.getDefaultLanguage()))
                .orElse(Locale.ENGLISH);
        }
        if (path.startsWith(appPrefix) && principal.isMemberAccess()) {
            return resolveUserLocale(request, principal.getMemberId());
        }
        if (staffPath && !principal.isPlatformIdentity() && !principal.isWorkspaceAdminScope()) {
            return resolveUserLocale(request, principal.getMemberId());
        }
        return null;
    }

    public void cacheUserLocale(HttpServletRequest request, UUID memberId, String language) {
        request.getSession().setAttribute(userLocaleSessionKey(memberId), resolveLocale(language));
    }

    private Locale resolveUserLocale(HttpServletRequest request, UUID memberId) {
        Object cachedLocale = request.getSession().getAttribute(userLocaleSessionKey(memberId));
        if (cachedLocale instanceof Locale locale) {
            return locale;
        }
        Locale locale = userSettingsRepository.findById(memberId)
            .map(UserSettings::getLanguage)
            .map(this::resolveLocale)
            .orElse(Locale.ENGLISH);
        request.getSession().setAttribute(userLocaleSessionKey(memberId), locale);
        return locale;
    }

    private String userLocaleSessionKey(UUID memberId) {
        return USER_LOCALE_SESSION_PREFIX + memberId;
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
