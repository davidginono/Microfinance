package com.sacco.mvp.web;

import com.sacco.mvp.domain.UserSettings;
import com.sacco.mvp.repository.UserSettingsRepository;
import com.sacco.mvp.security.AppUserPrincipal;
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

import java.time.OffsetDateTime;
import java.util.Locale;

@Controller
@RequestMapping("/staff/settings")
@RequiredArgsConstructor
@PreAuthorize("@authz.staffAnalyticsAccess(principal)")
public class StaffSettingsController {
    private final UserSettingsRepository userSettingsRepository;

    @GetMapping
    public String settings(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        UserSettings settings = userSettingsRepository.findById(principal.getMemberId())
            .orElseGet(() -> UserSettings.builder()
                .memberId(principal.getMemberId())
                .language("en")
                .notificationPrefs("{}")
                .build());
        model.addAttribute("staffSettingsLanguage", normalizeLanguage(settings.getLanguage()));
        return "staff/settings";
    }

    @PostMapping("/language")
    public String updateLanguage(@AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam String language,
                                 RedirectAttributes ra) {
        OffsetDateTime now = OffsetDateTime.now();
        UserSettings settings = userSettingsRepository.findById(principal.getMemberId())
            .orElseGet(() -> UserSettings.builder()
                .memberId(principal.getMemberId())
                .language("en")
                .notificationPrefs("{}")
                .createdAt(now)
                .build());
        settings.setLanguage(normalizeLanguage(language));
        if (settings.getNotificationPrefs() == null || settings.getNotificationPrefs().isBlank()) {
            settings.setNotificationPrefs("{}");
        }
        if (settings.getCreatedAt() == null) {
            settings.setCreatedAt(now);
        }
        settings.setUpdatedAt(now);
        userSettingsRepository.save(settings);
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
