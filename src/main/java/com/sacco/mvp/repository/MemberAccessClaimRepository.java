package com.sacco.mvp.repository;

import com.sacco.mvp.domain.MemberAccessClaim;
import com.sacco.mvp.domain.MemberAccessClaimId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MemberAccessClaimRepository extends JpaRepository<MemberAccessClaim, MemberAccessClaimId> {
    List<MemberAccessClaim> findByIdMemberId(UUID memberId);

    boolean existsByIdMemberId(UUID memberId);

    @Modifying
    @Query("delete from MemberAccessClaim c where c.id.memberId = :memberId")
    void deleteByMemberId(@Param("memberId") UUID memberId);
}
