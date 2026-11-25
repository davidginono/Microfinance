package com.sacco.mvp.repository;

import com.sacco.mvp.domain.MemberAccessClaim;
import com.sacco.mvp.domain.MemberAccessClaimId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MemberAccessClaimRepository extends JpaRepository<MemberAccessClaim, MemberAccessClaimId> {
    List<MemberAccessClaim> findByIdMemberId(UUID memberId);

    List<MemberAccessClaim> findByIdMemberIdIn(java.util.Collection<UUID> memberIds);

    boolean existsByIdMemberId(UUID memberId);
}
