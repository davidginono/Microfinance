package com.sacco.mvp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.domain.UserSettings;
import com.sacco.mvp.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service("userClaims")
@RequiredArgsConstructor
public class UserClaimService {
    private final UserSettingsRepository userSettingsRepository;
    private final ObjectMapper objectMapper;

    public Set<UserClaim> effectiveClaims(UUID memberId, Collection<Position> staffRoles, boolean memberAccess) {
        Set<UserClaim> defaults = defaultClaims(staffRoles, memberAccess);
        if (memberId == null) {
            return defaults;
        }
        return userSettingsRepository.findById(memberId)
            .map(UserSettings::getNotificationPrefs)
            .map(this::parsePrefs)
            .filter(prefs -> prefs.containsKey("claims"))
            .map(this::parseClaims)
            .orElse(defaults);
    }

    public boolean has(com.sacco.mvp.security.AppUserPrincipal principal, String claimName) {
        if (principal == null || claimName == null || claimName.isBlank()) {
            return false;
        }
        return principal.getClaims().contains(claimName);
    }

    @Transactional
    public void updateClaims(UUID memberId, List<UserClaim> claims) {
        UserSettings settings = userSettingsRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("User settings not found"));
        Map<String, Object> prefs = parsePrefs(settings.getNotificationPrefs());
        List<String> claimNames = new ArrayList<>();
        if (claims != null) {
            claims.stream().distinct().forEach(claim -> claimNames.add(claim.name()));
        }
        prefs.put("claims", claimNames);
        settings.setNotificationPrefs(writePrefs(prefs));
        settings.setUpdatedAt(OffsetDateTime.now());
        userSettingsRepository.save(settings);
    }

    public Set<UserClaim> parseClaims(String notificationPrefs) {
        Map<String, Object> prefs = parsePrefs(notificationPrefs);
        return parseClaims(prefs);
    }

    private Set<UserClaim> parseClaims(Map<String, Object> prefs) {
        Object rawClaims = prefs.get("claims");
        if (!(rawClaims instanceof List<?> items)) {
            return EnumSet.noneOf(UserClaim.class);
        }
        EnumSet<UserClaim> claims = EnumSet.noneOf(UserClaim.class);
        for (Object item : items) {
            if (item == null) {
                continue;
            }
            try {
                claims.add(UserClaim.valueOf(String.valueOf(item)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return claims;
    }

    public Set<UserClaim> defaultClaims(Collection<Position> staffRoles, boolean memberAccess) {
        EnumSet<UserClaim> claims = EnumSet.noneOf(UserClaim.class);
        if (memberAccess) {
            claims.add(UserClaim.APPLY_LOANS);
            claims.add(UserClaim.APPROVE_GUARANTOR_REQUESTS);
        }
        for (Position position : Position.normalizeStaffRoles(staffRoles)) {
            switch (position) {
                case MEMBER -> {
                }
                case MANAGER -> claims.add(UserClaim.REVIEW_MANAGER_QUEUE);
                case ACCOUNTANT -> claims.add(UserClaim.REVIEW_ACCOUNTANT_QUEUE);
                case DISBURSEMENT_OFFICER -> {
                    claims.add(UserClaim.ACCESS_DISBURSEMENT_QUEUE);
                    claims.add(UserClaim.DISBURSE_LOAN);
                }
                case BOARD -> claims.add(UserClaim.REVIEW_BOARD_QUEUE);
                case CREDIT_COMMITTEE -> claims.add(UserClaim.REVIEW_CREDIT_COMMITTEE_QUEUE);
                case LOAN_OFFICER -> claims.add(UserClaim.REVIEW_LOAN_OFFICER_QUEUE);
                case ADMIN, MINOR_ADMIN -> {
                    claims.add(UserClaim.ACCESS_ADMIN_SETTINGS);
                    claims.add(UserClaim.ACCESS_OUTBOX_MONITOR);
                }
            }
        }
        return claims;
    }

    private Map<String, Object> parsePrefs(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private String writePrefs(Map<String, Object> prefs) {
        try {
            return objectMapper.writeValueAsString(prefs == null ? Collections.emptyMap() : prefs);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
