package com.sacco.mvp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MemberAccessClaimId implements Serializable {
    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "claim_name", nullable = false)
    private String claimName;
}
