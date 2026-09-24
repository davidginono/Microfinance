package com.sacco.mvp.web;

import com.sacco.mvp.config.MemberLocaleInterceptor;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.UserSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

@Controller
@RequestMapping("/staff/settings")
@RequiredArgsConstructor
@PreAuthorize("@authz.staffAnalyticsAccess(principal)")
public class StaffSettingsController {
    private final UserSettingsService userSettingsService;
    private final MemberLocaleInterceptor memberLocaleInterceptor;

    @GetMapping
    public String settings(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("staffSettingsLanguage",
            normalizeLanguage(userSettingsService.languageOrDefault(principal.getMemberId())));
        return "staff/settings";
    }

    @PostMapping("/language")
    public String updateLanguage(@AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam String language,
                                 HttpServletRequest request,
                                 RedirectAttributes ra) {
        String savedLanguage = userSettingsService.updateLanguage(principal.getMemberId(), normalizeLanguage(language));
        memberLocaleInterceptor.cacheUserLocale(request, principal.getMemberId(), savedLanguage);
        ra.addFlashAttribute("message", "Language preference updated.");
        return "redirect:/staff/settings";
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "en";
        }
        return switch (language.trim().toLowerCase(Locale.ROOT)) {
            case "sw", "swahili" -> "sw";
            default -> "en";
        };
    }
}
