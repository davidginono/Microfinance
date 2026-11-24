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

import java.util.Locale;

@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
@PreAuthorize("principal.staffSession")
public class StaffSettingsController {
    private final com.sacco.mvp.service.NotificationInboxService notificationInboxService;
    private final UserSettingsService userSettingsService;
    private final MemberLocaleInterceptor memberLocaleInterceptor;

    @PostMapping("/settings/language")
    public String updateLanguage(@AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam String language,
                                 @RequestParam(required = false) String returnTo,
                                 HttpServletRequest request) {
        String savedLanguage = userSettingsService.updateLanguage(principal.getMemberId(), normalizeLanguage(language));
        memberLocaleInterceptor.cacheUserLocale(request, principal.getMemberId(), savedLanguage);
        if (returnTo != null && returnTo.length() <= 2048) {
            try {
                var destination = java.net.URI.create(returnTo);
                String path = destination.getPath();
                if (!destination.isAbsolute() && destination.getRawAuthority() == null
                    && path != null && path.startsWith("/") && !path.startsWith("//")
                    && !returnTo.contains("\\") && path.indexOf('\\') < 0
                    && returnTo.chars().noneMatch(Character::isISOControl) && path.chars().noneMatch(Character::isISOControl)) {
                    return "redirect:" + returnTo;
                }
            } catch (IllegalArgumentException invalid) {
                // Invalid destinations return to the staff dashboard.
            }
        }
        return "redirect:/staff/dashboard";
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
    @GetMapping("/notifications") @PreAuthorize("principal.staffSession and @access.has(principal,'NOTIFICATIONS_VIEW')")
    public String notifications(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(required=false) java.util.UUID highlight,Model m){m.addAttribute("notifications",notificationInboxService.allViews(a));m.addAttribute("highlightNotificationId",highlight);return "staff/notifications";}
    @GetMapping("/notifications/{id}/open") @PreAuthorize("principal.staffSession and @access.has(principal,'NOTIFICATIONS_VIEW')")
    public String notification(@AuthenticationPrincipal AppUserPrincipal a,@org.springframework.web.bind.annotation.PathVariable java.util.UUID id){return "redirect:"+notificationInboxService.openForMember(id,a,"/staff/notifications");}
    @PostMapping("/notifications/mark-all-read") @PreAuthorize("principal.staffSession and @access.has(principal,'NOTIFICATIONS_UPDATE')")
    public String readNotifications(@AuthenticationPrincipal AppUserPrincipal a){notificationInboxService.markAllAsRead(a.getMemberId());return "redirect:/staff/notifications";}

}
