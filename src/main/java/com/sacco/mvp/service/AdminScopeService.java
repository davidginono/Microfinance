package com.sacco.mvp.service;

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

    public AdminScopeView currentScope(AppUserPrincipal principal) {
        if (!isAdminWorkspaceUser(principal)) {
            return null;
        }
        HttpServletRequest request = requestFactory.getObject();
        Object cachedScope = request.getAttribute(REQUEST_SCOPE_VIEW);
        if (cachedScope instanceof AdminScopeView scopeView) {
            return scopeView;
        }
        List<SaccoRegistryService.RegisteredSaccoView> options = availableOptions(principal);
        if (options.isEmpty()) {
            return null;
        }

        HttpSession session = request.getSession(true);
        String selectedSaccoId = principal.getSaccoId();
        SaccoRegistryService.RegisteredSaccoView selectedSacco = options.stream()
            .filter(option -> option.saccoId().equals(selectedSaccoId))
            .findFirst()
            .orElse(options.getFirst());

        String selectedStationId = attributeAsString(session.getAttribute(SESSION_STATION_ID));
        if (selectedStationId == null || !selectedSacco.stationIds().contains(selectedStationId)) {
            selectedStationId = principal.getStationId() != null && selectedSacco.stationIds().contains(principal.getStationId())
                ? principal.getStationId()
                : selectedSacco.stationIds().getFirst();
        }

        session.setAttribute(SESSION_SACCO_ID, selectedSacco.saccoId());
        session.setAttribute(SESSION_STATION_ID, selectedStationId);
        String currentStationId = selectedStationId;

        AdminScopeView scopeView = new AdminScopeView(
            selectedSacco.saccoId(),
            selectedSacco.saccoName(),
            selectedStationId,
            selectedSacco.getStations().stream()
                .filter(station -> station.stationId().equals(currentStationId))
                .findFirst()
                .map(SaccoRegistryService.StationView::getAddressLocationLabel)
                .orElse("Location not set"),
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
        String resolvedSaccoId = principal.getSaccoId();
        String resolvedStationId = saccoRegistryService.requireStationForSacco(resolvedSaccoId, stationId);
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

    private List<SaccoRegistryService.RegisteredSaccoView> availableOptions(AppUserPrincipal principal) {
        List<SaccoRegistryService.RegisteredSaccoView> options = saccoRegistryService.listRegisteredSaccos();
        return options.stream()
            .filter(option -> option.saccoId().equals(principal.getSaccoId()))
            .toList();
    }

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
