package com.sacco.mvp.repository;

import com.sacco.mvp.domain.MinorAdminInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MinorAdminInvitationRepository extends JpaRepository<MinorAdminInvitation, UUID> {
    Optional<MinorAdminInvitation> findByMemberIdAndClaimedAtIsNullAndRevokedAtIsNull(UUID memberId);

    Optional<MinorAdminInvitation> findByTokenHashAndClaimedAtIsNullAndRevokedAtIsNull(String tokenHash);

    List<MinorAdminInvitation> findByClaimedAtIsNullAndRevokedAtIsNullAndExpiresAtBefore(OffsetDateTime now);
}
