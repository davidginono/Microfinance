package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.repository.MemberRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleDirectoryService {
    private final MemberRepository memberRepository;

    public List<RoleAccountRef> activeByRole(String saccoId, Position position) {
        if (saccoId == null || saccoId.isBlank() || position == null) {
            return List.of();
        }
        // findActiveRoleMembers already returns every active member in the SACCO
        // whose primary position or staff role matches, so no in-memory fallback
        // (which loaded the whole SACCO member list on a request path) is needed.
        return toRoleRefs(memberRepository.findActiveRoleMembers(saccoId, position));
    }

    public List<RoleAccountRef> activeByRoleInStation(String saccoId, String stationId, Position position) {
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

    public List<RoleAccountRef> activeByAnyRole(String saccoId, java.util.Collection<Position> positions) {
        if (saccoId == null || saccoId.isBlank() || positions == null || positions.isEmpty()) {
            return List.of();
        }
        return toRoleRefs(memberRepository.findActiveMembersWithAnyRole(saccoId, positions));
    }

    public List<RoleAccountRef> activeByAnyRoleInStation(String saccoId, String stationId, java.util.Collection<Position> positions) {
        if (saccoId == null || saccoId.isBlank() || positions == null || positions.isEmpty() || stationId == null || stationId.isBlank()) {
            return List.of();
        }
        String normalizedStationId = stationId.trim();
        return toRoleRefs(memberRepository.findActiveMembersWithAnyRoleInStation(saccoId, normalizedStationId, positions));
    }

    public List<RoleAccountRef> activeGlobalByRole(Position position) {
        if (position == null) {
            return List.of();
        }
        // findActiveGlobalRoleMembers already returns every active member whose
        // primary position or staff role matches, so the prior findAll() fallback
        // was redundant (it could only reproduce the same set) and loaded the whole
        // members table on a request path.
        return toRoleRefs(memberRepository.findActiveGlobalRoleMembers(position));
    }

    public boolean hasActiveRoleInSacco(UUID memberId, String saccoId, Position position) {
        if (memberId == null || saccoId == null || position == null) {
            return false;
        }
        return memberRepository.findById(memberId)
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .filter(member -> saccoId.equals(member.getSaccoId()))
            .filter(member -> member.getStaffRolesResolved().contains(position))
            .isPresent();
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
            .identifier(member.getMemberNo())
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
