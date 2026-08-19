package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-side member lookups shared by request-handling code so controllers and
 * model advice never talk to {@link MemberRepository} directly. Batch lookups
 * short-circuit on empty input to keep queue and review pages free of pointless
 * round trips.
 */
@Service
@RequiredArgsConstructor
public class MemberDirectoryService {
    private final MemberRepository memberRepository;

    public Optional<Member> find(UUID memberId) {
        return memberId == null ? Optional.empty() : memberRepository.findById(memberId);
    }

    public List<Member> findAll(Collection<UUID> memberIds) {
        return memberIds == null || memberIds.isEmpty()
            ? List.of()
            : memberRepository.findAllById(memberIds);
    }

    public Map<UUID, String> fullNames(Collection<UUID> memberIds) {
        Map<UUID, String> names = new LinkedHashMap<>();
        for (Member member : findAll(memberIds)) {
            names.putIfAbsent(member.getId(), member.getFullName());
        }
        return names;
    }

    public Map<UUID, Member> membersById(Collection<UUID> memberIds) {
        Map<UUID, Member> members = new LinkedHashMap<>();
        for (Member member : findAll(memberIds)) {
            members.putIfAbsent(member.getId(), member);
        }
        return members;
    }

    public Optional<Member> findByEmail(String email) {
        return email == null || email.isBlank()
            ? Optional.empty()
            : memberRepository.findByEmailIgnoreCase(email);
    }

    public Optional<Member> findByMemberNo(String memberNo) {
        return memberNo == null || memberNo.isBlank()
            ? Optional.empty()
            : memberRepository.findByMemberNo(memberNo);
    }

    public Optional<Member> findByStaffNo(String staffNo) {
        return staffNo == null || staffNo.isBlank()
            ? Optional.empty()
            : memberRepository.findByStaffNo(staffNo);
    }

    public String savedSignatureText(UUID memberId) {
        return find(memberId)
            .map(Member::getSignatureText)
            .filter(text -> text != null && !text.isBlank())
            .orElse("");
    }

    public void updatePasswordHash(Member member, String passwordHash) {
        member.setPasswordHash(passwordHash);
        memberRepository.save(member);
    }
}
