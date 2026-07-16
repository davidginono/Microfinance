package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.StaffAccessStatus;
import com.sacco.mvp.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffAccessService {
    private final MemberRepository memberRepository;

    @Transactional
    public Member acknowledgeStaffAccess(UUID memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalStateException("Member account not found."));
        if (member.getStatus() != MemberStatus.ACTIVE || !member.isMemberAccess()) {
            throw new IllegalStateException("Active member access is required.");
        }
        if (!member.isStaffAccessPendingAcknowledgement()) {
            throw new IllegalStateException("No staff access is waiting for acknowledgement.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        member.setStaffAccessStatus(StaffAccessStatus.ACTIVE);
        member.setStaffAccessActivatedAt(now);
        if (member.getStaffAccessAssignedAt() == null) {
            member.setStaffAccessAssignedAt(now);
        }
        return memberRepository.save(member);
    }
}
