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
            return parseStoredClaims(storedClaims, staffRoles);
        }
        return userSettingsRepository.findById(memberId)
            .map(UserSettings::getNotificationPrefs)
            .map(this::parsePrefs)
            .filter(prefs -> prefs.containsKey("claims"))
            .map(prefs -> parseClaims(prefs, staffRoles))
            .orElse(defaults);
    }

    /**
     * Batched form of {@link #effectiveClaims(UUID, Collection, boolean)}: resolves a whole
     * candidate set with two queries instead of two per member.
     */
    public Map<UUID, Set<UserClaim>> effectiveClaims(Map<UUID, ClaimSubject> subjects) {
        if (subjects == null || subjects.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Set<UserClaim>> resolved = new LinkedHashMap<>();
        for (MemberAccessClaim claim : memberAccessClaimRepository.findByIdMemberIdIn(subjects.keySet())) {
            UUID memberId = claim.getId().getMemberId();
            Set<UserClaim> claims = resolved.computeIfAbsent(memberId, key -> EnumSet.noneOf(UserClaim.class));
            String claimName = claim.getId().getClaimName();
            claims.addAll(UserClaim.fromStoredName(claimName));
            ClaimSubject subject = subjects.get(memberId);
            if (isLegacyPlatformAdminSettingsClaim(claimName, subject == null ? List.of() : subject.staffRoles())) {
                claims.add(UserClaim.PLATFORM_SETTINGS_VIEW);
                claims.add(UserClaim.PLATFORM_SETTINGS_UPDATE);
            }
        }

        List<UUID> withoutStoredClaims = subjects.keySet().stream()
            .filter(memberId -> !resolved.containsKey(memberId))
            .toList();
        if (!withoutStoredClaims.isEmpty()) {
            Map<UUID, String> prefsByMember = new LinkedHashMap<>();
            userSettingsRepository.findAllById(withoutStoredClaims)
                .forEach(settings -> prefsByMember.put(settings.getMemberId(), settings.getNotificationPrefs()));
            for (UUID memberId : withoutStoredClaims) {
                ClaimSubject subject = subjects.get(memberId);
                Map<String, Object> prefs = parsePrefs(prefsByMember.get(memberId));
                resolved.put(memberId, prefs.containsKey("claims")
                    ? parseClaims(prefs, subject.staffRoles())
                    : defaultClaims(subject.staffRoles(), subject.memberAccess()));
            }
        }
        return resolved;
    }

    public record ClaimSubject(Collection<Position> staffRoles, boolean memberAccess) {}

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
        java.util.Optional<UserSettings> settingsResult = userSettingsRepository.findById(memberId);
        if (settingsResult == null) {
            return;
        }
        settingsResult.ifPresent(settings -> {
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

    private Set<UserClaim> parseClaims(Map<String, Object> prefs, Collection<Position> staffRoles) {
        Set<UserClaim> claims = parseClaims(prefs);
        if (containsLegacyClaimName(prefs.get("claims"), "ACCESS_ADMIN_SETTINGS")
            && isPlatformAdminRole(staffRoles)) {
            claims.add(UserClaim.PLATFORM_SETTINGS_VIEW);
            claims.add(UserClaim.PLATFORM_SETTINGS_UPDATE);
        }
        return claims;
    }

    private Set<UserClaim> parseStoredClaims(List<MemberAccessClaim> storedClaims,
                                             Collection<Position> staffRoles) {
        EnumSet<UserClaim> claims = EnumSet.noneOf(UserClaim.class);
        for (MemberAccessClaim claim : storedClaims) {
            String claimName = claim.getId().getClaimName();
            claims.addAll(UserClaim.fromStoredName(claimName));
            if (isLegacyPlatformAdminSettingsClaim(claimName, staffRoles)) {
                claims.add(UserClaim.PLATFORM_SETTINGS_VIEW);
                claims.add(UserClaim.PLATFORM_SETTINGS_UPDATE);
            }
        }
        return claims;
    }

    private boolean isLegacyPlatformAdminSettingsClaim(String claimName, Collection<Position> staffRoles) {
        return "ACCESS_ADMIN_SETTINGS".equals(claimName) && isPlatformAdminRole(staffRoles);
    }

    private boolean isPlatformAdminRole(Collection<Position> staffRoles) {
        return Position.normalizeStaffRoles(staffRoles).contains(Position.ADMIN);
    }

    private boolean containsLegacyClaimName(Object rawClaims, String legacyClaimName) {
        if (!(rawClaims instanceof List<?> items)) {
            return false;
        }
        return items.stream()
            .filter(java.util.Objects::nonNull)
            .map(String::valueOf)
            .anyMatch(legacyClaimName::equals);
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
