package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminScopeService {
    private static final String REQUEST_SCOPE_VIEW = AdminScopeService.class.getName() + ".scopeView";

    private final ObjectFactory<HttpServletRequest> requestFactory;
    private final SaccoRegistryService saccoRegistryService;
    private final MemberRepository memberRepository;

    public AdminScopeView currentScope(AppUserPrincipal principal) {
        if (!isAdminWorkspaceUser(principal)) {
            return null;
        }
        HttpServletRequest request = requestFactory.getObject();
        Object cachedScope = request.getAttribute(REQUEST_SCOPE_VIEW);
        if (cachedScope instanceof AdminScopeView scopeView) {
            return scopeView;
        }
        WorkspaceAssignment assignment = currentAssignment(principal);
        var selectedSacco=saccoRegistryService.findRegisteredSaccoView(assignment.saccoId())
            .orElseThrow(()->new IllegalStateException("Your assigned institution is not active. Contact a platform administrator."));

        SaccoRegistryService.StationView selectedStation = selectedSacco.getStations().stream()
            .filter(station -> station.stationId().equalsIgnoreCase(assignment.stationId()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Your assigned station is not active for this SACCO. Ask a platform administrator to correct the assignment, then refresh this page."
            ));
        String selectedStationId = selectedStation.stationId();

        AdminScopeView scopeView = new AdminScopeView(
            selectedSacco.saccoId(),
            selectedSacco.saccoName(),
            selectedStationId,
            selectedStation.getAddressLocationLabel()
        );
        request.setAttribute(REQUEST_SCOPE_VIEW, scopeView);
        return scopeView;
    }

    public String currentSaccoId(AppUserPrincipal principal) {
        AdminScopeView scope = currentScope(principal);
        return scope == null ? principal.getSaccoId() : scope.getSaccoId();
    }

    public String currentStationId(AppUserPrincipal principal) {
        AdminScopeView scope = currentScope(principal);
        return scope == null ? principal.getStationId() : scope.getStationId();
    }

    private String attributeAsString(Object value) {
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : null;
    }

    private boolean isAdminWorkspaceUser(AppUserPrincipal principal) {
        return principal != null
            && principal.isStaffSession()
            && !principal.isPlatformIdentity();
    }

    private WorkspaceAssignment currentAssignment(AppUserPrincipal principal) {
        Member member = memberRepository.findById(principal.getMemberId())
            .filter(existing -> existing.getStatus() == MemberStatus.ACTIVE)
            .filter(Member::isStaffAccessActive)
            .filter(existing -> java.util.Objects.equals(existing.getSaccoId(),principal.getSaccoId())
                && java.util.Objects.equals(existing.getStationId(),principal.getStationId()))
            .orElseThrow(() -> new IllegalStateException(
                "Your admin account is no longer active. Sign out and contact a platform administrator."
            ));
        String saccoId = attributeAsString(member.getSaccoId());
        String stationId = attributeAsString(member.getStationId());
        if (saccoId == null || stationId == null) {
            throw new IllegalStateException(
                "Your admin account has no complete SACCO and station assignment. Ask a platform administrator to correct it, then refresh this page."
            );
        }
        return new WorkspaceAssignment(saccoId, stationId);
    }

    private record WorkspaceAssignment(String saccoId, String stationId) {}

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class AdminScopeView {
        private String saccoId;
        private String saccoName;
        private String stationId;
        private String stationAddressLocation;
    }
}
