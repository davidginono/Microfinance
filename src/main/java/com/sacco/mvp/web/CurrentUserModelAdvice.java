package com.sacco.mvp.web;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AdminScopeService;
import com.sacco.mvp.service.SaccoLogoStorageService;
import com.sacco.mvp.web.view.CurrentUserView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class CurrentUserModelAdvice {
    private static final String REQUEST_ACTIVE_SACCO_BRAND = CurrentUserModelAdvice.class.getName() + ".activeSaccoBrand";

    private final AdminScopeService adminScopeService;
    private final MemberRepository memberRepository;
    private final RegisteredSaccoRepository registeredSaccoRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final SaccoStationRepository saccoStationRepository;
    private final SaccoLogoStorageService saccoLogoStorageService;
    private final ObjectMapper objectMapper;
    private final ObjectFactory<HttpServletRequest> requestFactory;

    @ModelAttribute("currentMember")
    public CurrentUserView currentMember(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            return null;
        }
        return CurrentUserView.builder()
            .memberNo(principal.getMemberNo())
            .staffNo(principal.getStaffNo())
            .fullName(principal.getFullName())
            .email(principal.getEmail())
            .position(principal.getPosition())
            .memberAccess(principal.isMemberAccess())
            .staffSession(principal.isStaffSession())
            .build();
    }

    @ModelAttribute("pendingStaffAccess")
    public PendingStaffAccessView pendingStaffAccess(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null || isJsonRequest()) {
            return null;
        }
        return memberRepository.findById(principal.getMemberId())
            .filter(Member::isStaffAccessPendingAcknowledgement)
            .map(member -> new PendingStaffAccessView(
                member.getStaffNo(),
                member.getStaffRolesResolved().stream()
                    .map(Position::getDisplayName)
                    .collect(java.util.stream.Collectors.joining(", "))
            ))
            .orElse(null);
    }

    @ModelAttribute("adminScope")
    public AdminScopeService.AdminScopeView adminScope(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (isJsonRequest()) {
            return null;
        }
        return adminScopeService.currentScope(principal);
    }

    @ModelAttribute("headerStation")
    public HeaderStationView headerStation(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null || isPlatformAdminIdentity(principal) || isJsonRequest()) {
            return null;
        }
        AdminScopeService.AdminScopeView scope = adminScopeService.currentScope(principal);
        if (scope != null && scope.getStationId() != null && !scope.getStationId().isBlank()) {
            return new HeaderStationView(scope.getStationId(), scope.getStationAddressLocation());
        }
        String stationId = principal.getStationId();
        if (stationId == null || stationId.isBlank()) {
            return null;
        }
        String addressLocation = saccoStationRepository.findBySaccoIdAndStationId(principal.getSaccoId(), stationId)
            .map(station -> station.getAddressLocation() == null || station.getAddressLocation().isBlank()
                ? "Location not set"
                : station.getAddressLocation().trim())
            .orElse("Location not set");
        return new HeaderStationView(stationId.trim(), addressLocation);
    }

    @ModelAttribute("isPlatformAdminIdentity")
    public boolean isPlatformAdminIdentityModel(@AuthenticationPrincipal AppUserPrincipal principal) {
        return isPlatformAdminIdentity(principal);
    }

    /**
     * Separation of Duties — true when the signed-in user is a pure Super Admin
     * (ADMIN without MINOR_ADMIN). SACCO-scoped write controls should be hidden
     * from this user in JSPs because the backend will reject their POSTs.
     */
    @ModelAttribute("isSuperAdminObserver")
    public boolean isSuperAdminObserver(@AuthenticationPrincipal AppUserPrincipal principal) {
        return isPlatformAdminIdentity(principal);
    }

    @ModelAttribute("adminScopeOptionsJson")
    public String adminScopeOptionsJson(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (isJsonRequest()) {
            return "[]";
        }
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
        if (isJsonRequest()) {
            return null;
        }
        ActiveSaccoBrand brand = resolveActiveSaccoBrand(principal);
        return brand == null ? null : brand.name();
    }

    @ModelAttribute("activeSaccoId")
    public String activeSaccoId(@AuthenticationPrincipal AppUserPrincipal principal) {
        return null;
    }

    @ModelAttribute("activeSaccoLogoText")
    public String activeSaccoLogoText(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (isJsonRequest()) {
            return "LM";
        }
        ActiveSaccoBrand brand = resolveActiveSaccoBrand(principal);
        return brand == null ? "LM" : brand.logoText();
    }

    @ModelAttribute("activeSaccoLogoUrl")
    public String activeSaccoLogoUrl(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (isJsonRequest()) {
            return null;
        }
        ActiveSaccoBrand brand = resolveActiveSaccoBrand(principal);
        return brand == null ? null : brand.logoUrl();
    }

    private ActiveSaccoBrand resolveActiveSaccoBrand(AppUserPrincipal principal) {
        if (principal == null) {
            return null;
        }
        if (isPlatformAdminIdentity(principal)) {
            return null;
        }
        HttpServletRequest request = requestFactory.getObject();
        Object cachedBrand = request.getAttribute(REQUEST_ACTIVE_SACCO_BRAND);
        if (cachedBrand instanceof ActiveSaccoBrand activeSaccoBrand) {
            return activeSaccoBrand;
        }

        AdminScopeService.AdminScopeView scope = adminScopeService.currentScope(principal);
        if (scope != null && scope.getSaccoId() != null) {
            RegisteredSacco registeredSacco = registeredSaccoRepository.findById(scope.getSaccoId())
                .filter(RegisteredSacco::isActive)
                .orElse(null);
            String saccoName = registeredSacco != null && registeredSacco.getSaccoName() != null && !registeredSacco.getSaccoName().isBlank()
                ? registeredSacco.getSaccoName()
                : scope.getSaccoName();
            ActiveSaccoBrand brand = buildBrand(scope.getSaccoId(), saccoName, registeredSacco == null ? null : registeredSacco.getUpdatedAt());
            request.setAttribute(REQUEST_ACTIVE_SACCO_BRAND, brand);
            return brand;
        }

        String saccoId = principal.getSaccoId();
        RegisteredSacco registeredSacco = registeredSaccoRepository.findById(saccoId)
            .filter(RegisteredSacco::isActive)
            .orElse(null);
        String saccoName = (registeredSacco != null ? java.util.Optional.ofNullable(registeredSacco.getSaccoName()) : java.util.Optional.<String>empty())
            .filter(name -> name != null && !name.isBlank())
            .or(() -> saccoSettingsRepository.findById(saccoId)
                .map(SaccoSettings::getExternalSaccoName)
                .filter(name -> name != null && !name.isBlank()))
            .orElse(saccoId);
        ActiveSaccoBrand brand = buildBrand(saccoId, saccoName, registeredSacco == null ? null : registeredSacco.getUpdatedAt());
        request.setAttribute(REQUEST_ACTIVE_SACCO_BRAND, brand);
        return brand;
    }

    private ActiveSaccoBrand buildBrand(String saccoId, String saccoName, java.time.OffsetDateTime updatedAt) {
        String safeId = saccoId == null || saccoId.isBlank() ? "SACCO" : saccoId.trim();
        String safeName = saccoName == null || saccoName.isBlank() ? safeId : saccoName.trim();
        return new ActiveSaccoBrand(
            safeId,
            safeName,
            logoTextForSacco(safeName),
            saccoLogoStorageService.publicLogoUrl(safeId, updatedAt)
        );
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

    private boolean isPlatformAdminIdentity(AppUserPrincipal principal) {
        return principal != null && principal.hasRole(Position.ADMIN);
    }

    private boolean isJsonRequest() {
        return WebRequestClassifier.isJsonRequest(requestFactory.getObject());
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class HeaderStationView {
        private String stationId;
        private String stationAddressLocation;
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class PendingStaffAccessView {
        private String staffNo;
        private String roles;
    }

    private record ActiveSaccoBrand(String id, String name, String logoText, String logoUrl) {}
}
