package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminScopeService {
    private static final String SESSION_SACCO_ID = "adminSelectedSaccoId";
    private static final String SESSION_STATION_ID = "adminSelectedStationId";
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
        List<SaccoRegistryService.RegisteredSaccoView> options = availableOptions(assignment.saccoId());
        if (options.isEmpty()) {
            throw new IllegalStateException(
                "Your assigned SACCO is not active. Ask a platform administrator to restore it, then refresh this page."
            );
        }

        HttpSession session = request.getSession(true);
        String selectedSaccoId = assignment.saccoId();
        SaccoRegistryService.RegisteredSaccoView selectedSacco = options.stream()
            .filter(option -> option.saccoId().equals(selectedSaccoId))
            .findFirst()
            .orElse(options.getFirst());

        SaccoRegistryService.StationView selectedStation = selectedSacco.getStations().stream()
            .filter(station -> station.stationId().equalsIgnoreCase(assignment.stationId()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Your assigned station is not active for this SACCO. Ask a platform administrator to correct the assignment, then refresh this page."
            ));
        String selectedStationId = selectedStation.stationId();

        session.setAttribute(SESSION_SACCO_ID, selectedSacco.saccoId());
        session.setAttribute(SESSION_STATION_ID, selectedStationId);

        AdminScopeView scopeView = new AdminScopeView(
            selectedSacco.saccoId(),
            selectedSacco.saccoName(),
            selectedStationId,
            selectedStation.getAddressLocationLabel(),
            options
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

    public void updateScope(AppUserPrincipal principal, String saccoId, String stationId) {
        if (!isAdminWorkspaceUser(principal)) {
            throw new IllegalStateException("Admin workspace access is not available for this account.");
        }
        WorkspaceAssignment assignment = currentAssignment(principal);
        if (saccoId == null || !assignment.saccoId().equalsIgnoreCase(saccoId.trim())
            || stationId == null || !assignment.stationId().equalsIgnoreCase(stationId.trim())) {
            throw new IllegalStateException("Your workspace is locked to the SACCO and station assigned to your account.");
        }
        String resolvedSaccoId = assignment.saccoId();
        String resolvedStationId = saccoRegistryService.requireStationForSacco(resolvedSaccoId, assignment.stationId());
        HttpSession session = requestFactory.getObject().getSession(true);
        session.setAttribute(SESSION_SACCO_ID, resolvedSaccoId);
        session.setAttribute(SESSION_STATION_ID, resolvedStationId);
        requestFactory.getObject().removeAttribute(REQUEST_SCOPE_VIEW);
    }

    public boolean hasExplicitScopeSelection() {
        HttpSession session = requestFactory.getObject().getSession(false);
        if (session == null) {
            return false;
        }
        return attributeAsString(session.getAttribute(SESSION_SACCO_ID)) != null
            && attributeAsString(session.getAttribute(SESSION_STATION_ID)) != null;
    }

    public void clearScope() {
        HttpSession session = requestFactory.getObject().getSession(false);
        if (session == null) {
            return;
        }
        session.removeAttribute(SESSION_SACCO_ID);
        session.removeAttribute(SESSION_STATION_ID);
        requestFactory.getObject().removeAttribute(REQUEST_SCOPE_VIEW);
    }

    private String attributeAsString(Object value) {
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : null;
    }

    private boolean isAdminWorkspaceUser(AppUserPrincipal principal) {
        return principal != null
            && principal.isWorkspaceAdminScope()
            && !principal.isPlatformIdentity();
    }

    private List<SaccoRegistryService.RegisteredSaccoView> availableOptions(String saccoId) {
        return saccoRegistryService.findRegisteredSaccoView(saccoId)
            .map(List::of)
            .orElseGet(List::of);
    }

    private WorkspaceAssignment currentAssignment(AppUserPrincipal principal) {
        Member member = memberRepository.findById(principal.getMemberId())
            .filter(existing -> existing.getStatus() == MemberStatus.ACTIVE)
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
        private List<SaccoRegistryService.RegisteredSaccoView> options;
    }
}
