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

    private final ObjectFactory<HttpServletRequest> requestFactory;
    private final SaccoRegistryService saccoRegistryService;

    public AdminScopeView currentScope(AppUserPrincipal principal) {
        if (principal == null || !principal.hasRole(com.sacco.mvp.domain.Position.ADMIN)) {
            return null;
        }
        List<SaccoRegistryService.RegisteredSaccoView> options = saccoRegistryService.listRegisteredSaccos();
        if (options.isEmpty()) {
            return null;
        }

        HttpSession session = requestFactory.getObject().getSession(true);
        String selectedSaccoId = attributeAsString(session.getAttribute(SESSION_SACCO_ID));
        SaccoRegistryService.RegisteredSaccoView selectedSacco = options.stream()
            .filter(option -> option.saccoId().equals(selectedSaccoId))
            .findFirst()
            .orElseGet(() -> options.stream()
                .filter(option -> option.saccoId().equals(principal.getSaccoId()))
                .findFirst()
                .orElse(options.getFirst()));

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

    public void updateScope(String saccoId, String stationId) {
        String resolvedStationId = saccoRegistryService.requireStationForSacco(saccoId, stationId);
        HttpSession session = requestFactory.getObject().getSession(true);
        session.setAttribute(SESSION_SACCO_ID, saccoRegistryService.resolveRegisteredSacco(saccoId).getSaccoId());
        session.setAttribute(SESSION_STATION_ID, resolvedStationId);
    }

    private String attributeAsString(Object value) {
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : null;
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
