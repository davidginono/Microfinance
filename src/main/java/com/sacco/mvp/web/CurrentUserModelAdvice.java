package com.sacco.mvp.web;

import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AdminScopeService;
import com.sacco.mvp.service.NotificationInboxService;
import com.sacco.mvp.service.NotificationViewService;
import com.sacco.mvp.web.view.CurrentUserView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Collections;

@ControllerAdvice
@RequiredArgsConstructor
public class CurrentUserModelAdvice {
    private final NotificationInboxService notificationInboxService;
    private final AdminScopeService adminScopeService;
    private final RegisteredSaccoRepository registeredSaccoRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final ObjectMapper objectMapper;

    @ModelAttribute("currentMember")
    public CurrentUserView currentMember(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            return null;
        }
        return CurrentUserView.builder()
            .memberNo(principal.getUsername())
            .fullName(principal.getFullName())
            .email(principal.getEmail())
            .position(principal.getPosition())
            .memberAccess(principal.isMemberAccess())
            .build();
    }

    @ModelAttribute("notificationCount")
    public long notificationCount(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            return 0;
        }
        return notificationInboxService.unreadCount(principal.getMemberId(), principal.getGrantedPositions());
    }

    @ModelAttribute("headerNotifications")
    public java.util.List<NotificationViewService.NotificationView> headerNotifications(
        @AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            return Collections.emptyList();
        }
        return notificationInboxService.unreadViews(principal.getMemberId(), principal.getGrantedPositions());
    }

    @ModelAttribute("notificationTargetUrl")
    public String notificationTargetUrl(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            return "/login";
        }
        return switch (principal.getPosition()) {
            case ADMIN -> "/admin/messages";
            case MANAGER -> "/manager/loan-applications?status=READY_FOR_MANAGER";
            case BOARD -> "/board/queue";
            case CHAIRPERSON -> "/chairperson/manager-decisions";
            case MEMBER -> "/app/notifications";
        };
    }

    @ModelAttribute("notificationOpenBaseUrl")
    public String notificationOpenBaseUrl(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            return "/login";
        }
        return switch (principal.getPosition()) {
            case ADMIN -> "/admin/messages/";
            case MANAGER -> "/manager/loan-applications/";
            case BOARD -> "/board/loan-applications/";
            case CHAIRPERSON -> "/chairperson/manager-decisions";
            case MEMBER -> "/app/notifications/";
        };
    }

    @ModelAttribute("adminScope")
    public AdminScopeService.AdminScopeView adminScope(@AuthenticationPrincipal AppUserPrincipal principal) {
        return adminScopeService.currentScope(principal);
    }

    @ModelAttribute("adminScopeOptionsJson")
    public String adminScopeOptionsJson(@AuthenticationPrincipal AppUserPrincipal principal) {
        AdminScopeService.AdminScopeView scope = adminScopeService.currentScope(principal);
        if (scope == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(scope.getOptions());
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    @ModelAttribute("activeSaccoName")
    public String activeSaccoName(@AuthenticationPrincipal AppUserPrincipal principal) {
        ActiveSaccoBrand brand = resolveActiveSaccoBrand(principal);
        return brand == null ? null : brand.name();
    }

    @ModelAttribute("activeSaccoId")
    public String activeSaccoId(@AuthenticationPrincipal AppUserPrincipal principal) {
        ActiveSaccoBrand brand = resolveActiveSaccoBrand(principal);
        return brand == null ? null : brand.id();
    }

    @ModelAttribute("activeSaccoLogoText")
    public String activeSaccoLogoText(@AuthenticationPrincipal AppUserPrincipal principal) {
        ActiveSaccoBrand brand = resolveActiveSaccoBrand(principal);
        return brand == null ? "LM" : brand.logoText();
    }

    private ActiveSaccoBrand resolveActiveSaccoBrand(AppUserPrincipal principal) {
        if (principal == null) {
            return null;
        }

        AdminScopeService.AdminScopeView scope = adminScopeService.currentScope(principal);
        if (scope != null && scope.getSaccoId() != null) {
            return buildBrand(scope.getSaccoId(), scope.getSaccoName());
        }

        String saccoId = principal.getSaccoId();
        String saccoName = registeredSaccoRepository.findById(saccoId)
            .filter(RegisteredSacco::isActive)
            .map(RegisteredSacco::getSaccoName)
            .filter(name -> name != null && !name.isBlank())
            .or(() -> saccoSettingsRepository.findById(saccoId)
                .map(SaccoSettings::getExternalSaccoName)
                .filter(name -> name != null && !name.isBlank()))
            .orElse(saccoId);
        return buildBrand(saccoId, saccoName);
    }

    private ActiveSaccoBrand buildBrand(String saccoId, String saccoName) {
        String safeId = saccoId == null || saccoId.isBlank() ? "SACCO" : saccoId.trim();
        String safeName = saccoName == null || saccoName.isBlank() ? safeId : saccoName.trim();
        return new ActiveSaccoBrand(safeId, safeName, logoTextForSacco(safeName));
    }

    private String logoTextForSacco(String name) {
        String[] parts = name == null ? new String[0] : name.trim().split("\\s+");
        java.util.List<String> letters = new java.util.ArrayList<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            char first = Character.toUpperCase(part.charAt(0));
            if (Character.isLetterOrDigit(first)) {
                letters.add(String.valueOf(first));
            }
            if (letters.size() == 2) {
                break;
            }
        }
        if (letters.isEmpty()) {
            return "SC";
        }
        if (letters.size() == 1) {
            return letters.getFirst() + "S";
        }
        return String.join("", letters);
    }

    private record ActiveSaccoBrand(String id, String name, String logoText) {}
}
