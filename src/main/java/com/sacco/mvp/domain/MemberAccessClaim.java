package com.sacco.mvp.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "member_access_claims")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberAccessClaim {
    @EmbeddedId
    private MemberAccessClaimId id;
}
