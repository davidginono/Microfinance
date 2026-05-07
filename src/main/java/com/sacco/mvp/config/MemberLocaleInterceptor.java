package com.sacco.mvp.config;

import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.UserSettings;
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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return true;
        }
        if (!principal.hasRole(Position.MEMBER)) {
            return true;
        }

        Locale locale = userSettingsRepository.findById(principal.getMemberId())
            .map(UserSettings::getLanguage)
            .map(this::resolveLocale)
            .orElse(Locale.ENGLISH);

        LocaleResolver resolver = RequestContextUtils.getLocaleResolver(request);
        if (resolver != null) {
            resolver.setLocale(request, response, locale);
        }
        return true;
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
