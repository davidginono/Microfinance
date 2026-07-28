package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.repository.MemberRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleDirectoryService {
    private final MemberRepository memberRepository;
    private final UserClaimService userClaimService;

    private List<RoleAccountRef> activeByRole(String saccoId, Position position) {
        if (saccoId == null || saccoId.isBlank() || position == null) {
            return List.of();
        }
        // findActiveRoleMembers already returns every active member in the SACCO
        // whose primary position or staff role matches, so no in-memory fallback
        // (which loaded the whole SACCO member list on a request path) is needed.
        return toRoleRefs(memberRepository.findActiveRoleMembers(saccoId, position));
    }

    private List<RoleAccountRef> activeByRoleInStation(String saccoId, String stationId, Position position) {
        if (saccoId == null || saccoId.isBlank() || position == null) {
            return List.of();
        }
        if (stationId == null || stationId.isBlank()) {
            // Legacy records without a first-class station reference fall back to
            // SACCO scope so loan routing never silently loses its only candidate.
            return activeByRole(saccoId, position);
        }
        return toRoleRefs(memberRepository.findActiveRoleMembersInStation(saccoId, stationId.trim(), position));
    }

    public List<RoleAccountRef> activeByClaim(String saccoId, UserClaim claim) {
        return activeByClaimInStation(saccoId, null, claim);
    }

    public List<RoleAccountRef> activeByClaimInStation(String saccoId, String stationId, UserClaim claim) {
        if (saccoId == null || saccoId.isBlank() || claim == null) {
            return List.of();
        }
        String normalizedStationId = stationId == null || stationId.isBlank() ? null : stationId.trim();
        return toRoleRefs(memberRepository.findActiveMembersWithClaimInStation(saccoId, normalizedStationId, claim.name()));
    }

    public List<RoleAccountRef> activeByAnyClaim(String saccoId, Collection<UserClaim> claims) {
        if (saccoId == null || saccoId.isBlank() || claims == null || claims.isEmpty()) {
            return List.of();
        }
        List<RoleAccountRef> refs = new ArrayList<>();
        claims.stream()
            .filter(java.util.Objects::nonNull)
            .forEach(claim -> refs.addAll(activeByClaim(saccoId, claim)));
        return deduplicateAndSort(refs);
    }

    public List<RoleAccountRef> activeByAnyClaimInStation(String saccoId, String stationId, Collection<UserClaim> claims) {
        if (saccoId == null || saccoId.isBlank() || claims == null || claims.isEmpty()) {
            return List.of();
        }
        List<RoleAccountRef> refs = new ArrayList<>();
        claims.stream()
            .filter(java.util.Objects::nonNull)
            .forEach(claim -> refs.addAll(activeByClaimInStation(saccoId, stationId, claim)));
        return deduplicateAndSort(refs);
    }

    private List<RoleAccountRef> activeGlobalByRole(Position position) {
        if (position == null) {
            return List.of();
        }
        // findActiveGlobalRoleMembers already returns every active member whose
        // primary position or staff role matches, so the prior findAll() fallback
        // was redundant (it could only reproduce the same set) and loaded the whole
        // members table on a request path.
        return toRoleRefs(memberRepository.findActiveGlobalRoleMembers(position));
    }

    public List<RoleAccountRef> activeRoleHoldersByClaim(Position position, UserClaim claim) {
        if (position == null || claim == null) {
            return List.of();
        }
        return activeGlobalByRole(position).stream()
            .filter(ref -> hasActiveStaffClaim(ref.getId(), claim))
            .toList();
    }

    public List<RoleAccountRef> activeRoleHoldersByClaimInStation(String saccoId,
                                                                  String stationId,
                                                                  Position position,
                                                                  UserClaim claim) {
        if (saccoId == null || saccoId.isBlank() || position == null || claim == null) {
            return List.of();
        }
        return activeByRoleInStation(saccoId, stationId, position).stream()
            .filter(ref -> hasActiveStaffClaim(ref.getId(), claim))
            .toList();
    }

    public List<RoleAccountRef> activeRoleHoldersByAnyClaim(String saccoId,
                                                            Position position,
                                                            Collection<UserClaim> claims) {
        if (saccoId == null || saccoId.isBlank() || position == null || claims == null || claims.isEmpty()) {
            return List.of();
        }
        return activeByRole(saccoId, position).stream()
            .filter(ref -> hasAnyActiveStaffClaim(ref.getId(), claims))
            .toList();
    }

    public List<RoleAccountRef> activeRoleHoldersByAnyClaimInStation(String saccoId,
                                                                     String stationId,
                                                                     Position position,
                                                                     Collection<UserClaim> claims) {
        if (saccoId == null || saccoId.isBlank() || position == null || claims == null || claims.isEmpty()) {
            return List.of();
        }
        return activeByRoleInStation(saccoId, stationId, position).stream()
            .filter(ref -> hasAnyActiveStaffClaim(ref.getId(), claims))
            .toList();
    }

    public List<RoleAccountRef> activePlatformAdminsByClaim(UserClaim claim) {
        return activeRoleHoldersByClaim(Position.ADMIN, claim);
    }

    public List<RoleAccountRef> activeWorkspaceAdminsByAnyClaim(String saccoId, Collection<UserClaim> claims) {
        return activeRoleHoldersByAnyClaim(saccoId, Position.MINOR_ADMIN, claims);
    }

    public List<RoleAccountRef> activeWorkspaceAdminsByAnyClaimInStation(String saccoId,
                                                                         String stationId,
                                                                         Collection<UserClaim> claims) {
        return activeRoleHoldersByAnyClaimInStation(saccoId, stationId, Position.MINOR_ADMIN, claims);
    }

    public boolean hasActiveClaimInSacco(UUID memberId, String saccoId, UserClaim claim) {
        if (memberId == null || saccoId == null || claim == null) {
            return false;
        }
        return memberRepository.findById(memberId)
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .filter(member -> saccoId.equals(member.getSaccoId()))
            .filter(member -> member.isMemberAccess() || member.isStaffAccessActive())
            .map(member -> userClaimService.effectiveClaims(member.getId(), member.getActiveStaffRolesResolved(), member.isMemberAccess()))
            .map(claims -> claims.contains(claim))
            .orElse(false);
    }

    public boolean hasActiveStaffClaim(UUID memberId, UserClaim claim) {
        if (memberId == null || claim == null) {
            return false;
        }
        return memberRepository.findById(memberId)
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .filter(Member::isStaffAccessActive)
            .map(member -> userClaimService.effectiveClaims(member.getId(), member.getActiveStaffRolesResolved(), member.isMemberAccess()))
            .map(claims -> claims.contains(claim))
            .orElse(false);
    }

    private boolean hasAnyActiveStaffClaim(UUID memberId, Collection<UserClaim> claims) {
        if (memberId == null || claims == null || claims.isEmpty()) {
            return false;
        }
        return claims.stream()
            .filter(java.util.Objects::nonNull)
            .anyMatch(claim -> hasActiveStaffClaim(memberId, claim));
    }

    private List<RoleAccountRef> deduplicateAndSort(List<RoleAccountRef> refs) {
        Map<UUID, RoleAccountRef> uniqueRefs = new LinkedHashMap<>();
        refs.forEach(ref -> uniqueRefs.putIfAbsent(ref.getId(), ref));
        return uniqueRefs.values().stream()
            .sorted(Comparator.comparing(RoleAccountRef::getFullName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private List<RoleAccountRef> toRoleRefs(List<Member> members) {
        List<RoleAccountRef> refs = new ArrayList<>();
        members.stream()
            .map(this::fromMember)
            .forEach(refs::add);
        return deduplicateAndSort(refs);
    }

    private RoleAccountRef fromMember(Member member) {
        return RoleAccountRef.builder()
            .id(member.getId())
            .saccoId(member.getSaccoId())
            .identifier(member.getStaffNo() == null || member.getStaffNo().isBlank() ? member.getMemberNo() : member.getStaffNo())
            .fullName(member.getFullName())
            .email(member.getEmail())
            .phone(member.getPhone())
            .phoneVerifiedAt(member.getPhoneVerifiedAt())
            .stationId(member.getStationId())
            .memberBased(true)
            .build();
    }

    @Value
    @Builder
    public static class RoleAccountRef {
        UUID id;
        String saccoId;
        String identifier;
        String fullName;
        String email;
        String phone;
        java.time.OffsetDateTime phoneVerifiedAt;
        String stationId;
        boolean memberBased;
    }
}
