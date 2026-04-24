package com.sacco.mvp.service;

import com.sacco.mvp.domain.Position;
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

    private final ObjectFactory<HttpServletRequest> requestFactory;
    private final SaccoRegistryService saccoRegistryService;

    public AdminScopeView currentScope(AppUserPrincipal principal) {
        if (!isAdminWorkspaceUser(principal)) {
            return null;
        }
        List<SaccoRegistryService.RegisteredSaccoView> options = availableOptions(principal);
        if (options.isEmpty()) {
            return null;
        }

        HttpSession session = requestFactory.getObject().getSession(true);
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

        return new AdminScopeView(
            selectedSacco.saccoId(),
            selectedSacco.saccoName(),
            selectedStationId,
            options
        );
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
    }

    private String attributeAsString(Object value) {
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : null;
    }

    private boolean isAdminWorkspaceUser(AppUserPrincipal principal) {
        return principal != null
            && principal.hasRole(Position.MINOR_ADMIN)
            && !principal.hasRole(Position.ADMIN);
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
        private List<SaccoRegistryService.RegisteredSaccoView> options;
    }
}
