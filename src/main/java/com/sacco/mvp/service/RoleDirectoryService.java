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
        List<RoleAccountRef> refs = new ArrayList<>();
        memberRepository.findBySaccoIdAndStatusOrderByFullNameAsc(saccoId, MemberStatus.ACTIVE).stream()
            .filter(member -> member.getStaffRolesResolved().contains(position))
            .forEach(member -> refs.add(fromMember(member)));
        return deduplicateAndSort(refs);
    }

    public List<RoleAccountRef> activeGlobalByRole(Position position) {
        List<RoleAccountRef> refs = new ArrayList<>();
        memberRepository.findAll().stream()
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .filter(member -> member.getStaffRolesResolved().contains(position))
            .forEach(member -> refs.add(fromMember(member)));
        return deduplicateAndSort(refs);
    }

    private List<RoleAccountRef> deduplicateAndSort(List<RoleAccountRef> refs) {
        Map<UUID, RoleAccountRef> uniqueRefs = new LinkedHashMap<>();
        refs.forEach(ref -> uniqueRefs.putIfAbsent(ref.getId(), ref));
        return uniqueRefs.values().stream()
            .sorted(Comparator.comparing(RoleAccountRef::getFullName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private RoleAccountRef fromMember(Member member) {
        return RoleAccountRef.builder()
            .id(member.getId())
            .saccoId(member.getSaccoId())
            .identifier(member.getMemberNo())
            .fullName(member.getFullName())
            .email(member.getEmail())
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
        boolean memberBased;
    }
}
