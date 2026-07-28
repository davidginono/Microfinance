package com.sacco.mvp.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.MemberAccessClaim;
import com.sacco.mvp.domain.MemberAccessClaimId;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.domain.UserSettings;
import com.sacco.mvp.repository.MemberAccessClaimRepository;
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
import java.util.stream.Collectors;

@Service("userClaims")
@RequiredArgsConstructor
public class UserClaimService {
    private final MemberAccessClaimRepository memberAccessClaimRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final ObjectMapper objectMapper;

    public Set<UserClaim> effectiveClaims(UUID memberId, Collection<Position> staffRoles, boolean memberAccess) {
        Set<UserClaim> defaults = defaultClaims(staffRoles, memberAccess);
        if (memberId == null) {
            return defaults;
        }
        List<MemberAccessClaim> storedClaims = memberAccessClaimRepository.findByIdMemberId(memberId);
        if (!storedClaims.isEmpty()) {
            return storedClaims.stream()
                .flatMap(claim -> UserClaim.fromStoredName(claim.getId().getClaimName()).stream())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(UserClaim.class)));
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
        if (principal.getClaims().contains(claimName)) {
            return true;
        }
        return UserClaim.fromStoredName(claimName).stream()
            .map(UserClaim::name)
            .anyMatch(principal.getClaims()::contains);
    }

    @Transactional
    public void updateClaims(UUID memberId, List<UserClaim> claims) {
        if (memberId == null) {
            return;
        }
        memberAccessClaimRepository.deleteByMemberId(memberId);
        List<UserClaim> normalizedClaims = claims == null ? List.of() : claims.stream()
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        normalizedClaims.forEach(claim -> memberAccessClaimRepository.save(MemberAccessClaim.builder()
            .id(new MemberAccessClaimId(memberId, claim.name()))
            .build()));
        userSettingsRepository.findById(memberId).ifPresent(settings -> {
            Map<String, Object> prefs = parsePrefs(settings.getNotificationPrefs());
            prefs.remove("claims");
            settings.setNotificationPrefs(writePrefs(prefs));
            settings.setUpdatedAt(OffsetDateTime.now());
            userSettingsRepository.save(settings);
        });
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
            claims.addAll(UserClaim.fromStoredName(String.valueOf(item)));
        }
        return claims;
    }

    public Set<UserClaim> defaultClaims(Collection<Position> staffRoles, boolean memberAccess) {
        return UserClaim.defaultClaims(staffRoles, memberAccess);
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
