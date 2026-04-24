package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "minor_admin_invitations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MinorAdminInvitation {
    @Id
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Column(name = "invited_at", nullable = false)
    private OffsetDateTime invitedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "claimed_at")
    private OffsetDateTime claimedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Transient
    public boolean isPending(OffsetDateTime now) {
        return claimedAt == null
            && revokedAt == null
            && expiresAt != null
            && !expiresAt.isBefore(now);
    }

    @Transient
    public boolean isExpired(OffsetDateTime now) {
        return claimedAt == null
            && revokedAt == null
            && expiresAt != null
            && expiresAt.isBefore(now);
    }
}
