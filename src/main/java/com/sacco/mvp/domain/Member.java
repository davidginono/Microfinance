package com.sacco.mvp.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {
    @Id
    private UUID id;

    @Column(name = "sacco_id", nullable = false)
    private String saccoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sacco_id", referencedColumnName = "sacco_id", insertable = false, updatable = false)
    private RegisteredSacco registeredSacco;

    @Column(name = "member_no", nullable = false, unique = true)
    private String memberNo;

    @Column(name = "station_id")
    private String stationId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;

    @Column(name = "phone_verified_at")
    private OffsetDateTime phoneVerifiedAt;

    private String email;

    @Column(name = "signature_text")
    private String signatureText;

    @Column(name = "signature_registered_at")
    private OffsetDateTime signatureRegisteredAt;

    @Column(name = "is_member")
    private Boolean memberAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Position position;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "member_staff_roles", joinColumns = @JoinColumn(name = "member_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", nullable = false)
    private Set<Position> staffRoles = new LinkedHashSet<>();

    private Integer rank;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "profile_last_synced_at")
    private OffsetDateTime profileLastSyncedAt;

    @Transient
    public boolean isMemberAccess() {
        return Boolean.TRUE.equals(memberAccount) || (memberAccount == null && position == Position.MEMBER);
    }

    @Transient
    public Set<Position> getStaffRolesResolved() {
        LinkedHashSet<Position> roles = Position.normalizeStaffRoles(staffRoles);
        if (position != null && position.isStaffRole()) {
            roles.add(position);
        }
        return roles;
    }
}
