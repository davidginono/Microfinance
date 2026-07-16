package com.sacco.mvp.security;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.UserClaim;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
public class AppUserPrincipal implements UserDetails {
    private final UUID memberId;
    private final String saccoId;
    private final String stationId;
    private final String fullName;
    private final String email;
    private final Position position;
    private final Set<Position> staffRoles;
    private final Set<Position> grantedPositions;
    private final boolean memberAccess;
    private final boolean staffSession;
    private final String memberNo;
    private final String username;
    private final String staffNo;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;
    private final Set<String> claims;

    public AppUserPrincipal(Member member, Set<UserClaim> claims) {
        this(member, claims, member.isStaffAccessActive());
    }

    public AppUserPrincipal(Member member, Set<UserClaim> claims, boolean staffSession) {
        this.memberId = member.getId();
        this.saccoId = member.getSaccoId();
        this.stationId = member.getStationId();
        this.fullName = member.getFullName();
        this.email = member.getEmail();
        this.memberNo = member.getMemberNo();
        this.staffNo = member.getStaffNo();
        this.memberAccess = member.isMemberAccess();
        this.staffSession = staffSession || (!this.memberAccess && member.isStaffAccessActive());
        Set<Position> sessionStaffRoles = this.staffSession
            ? member.getActiveStaffRolesResolved()
            : java.util.Collections.emptySet();
        this.staffRoles = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(sessionStaffRoles));
        this.position = Position.primaryRole(this.staffRoles, this.memberAccess);
        this.grantedPositions = resolveGrantedPositions(this.staffRoles, this.memberAccess);
        this.username = this.staffSession && hasText(member.getStaffNo()) ? member.getStaffNo() : member.getMemberNo();
        this.password = member.getPasswordHash();
        this.authorities = buildAuthorities(this.grantedPositions);
        this.claims = filterClaims(claims, this.grantedPositions, this.memberAccess);
    }

    private Set<Position> resolveGrantedPositions(Set<Position> staffRoles, boolean memberAccess) {
        LinkedHashSet<Position> granted = new LinkedHashSet<>(Position.normalizeStaffRoles(staffRoles));
        if (memberAccess) {
            granted.add(Position.MEMBER);
        }
        return java.util.Collections.unmodifiableSet(granted);
    }

    private Set<String> filterClaims(Set<UserClaim> claims, Set<Position> grantedPositions, boolean memberAccess) {
        if (claims == null || claims.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        Set<UserClaim> allowedClaims = allowedClaims(grantedPositions, memberAccess);
        return claims.stream()
            .filter(allowedClaims::contains)
            .map(UserClaim::name)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Set<UserClaim> allowedClaims(Set<Position> grantedPositions, boolean memberAccess) {
        java.util.EnumSet<UserClaim> allowed = java.util.EnumSet.noneOf(UserClaim.class);
        if (memberAccess && grantedPositions.contains(Position.MEMBER)) {
            allowed.add(UserClaim.APPLY_LOANS);
            allowed.add(UserClaim.APPROVE_GUARANTOR_REQUESTS);
        }
        for (Position position : Position.normalizeStaffRoles(grantedPositions)) {
            switch (position) {
                case MEMBER -> {
                }
                case MANAGER -> allowed.add(UserClaim.REVIEW_MANAGER_QUEUE);
                case ACCOUNTANT -> allowed.add(UserClaim.REVIEW_ACCOUNTANT_QUEUE);
                case DISBURSEMENT_OFFICER -> {
                    allowed.add(UserClaim.ACCESS_DISBURSEMENT_QUEUE);
                    allowed.add(UserClaim.DISBURSE_LOAN);
                }
                case CHAIRPERSON -> allowed.add(UserClaim.REVIEW_CHAIRPERSON_QUEUE);
                case BOARD -> allowed.add(UserClaim.REVIEW_BOARD_QUEUE);
                case CREDIT_COMMITTEE -> allowed.add(UserClaim.REVIEW_CREDIT_COMMITTEE_QUEUE);
                case LOAN_OFFICER -> allowed.add(UserClaim.REVIEW_LOAN_OFFICER_QUEUE);
                case ADMIN, MINOR_ADMIN -> {
                    allowed.add(UserClaim.ACCESS_ADMIN_SETTINGS);
                    allowed.add(UserClaim.ACCESS_OUTBOX_MONITOR);
                }
            }
        }
        return allowed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Collection<? extends GrantedAuthority> buildAuthorities(Set<Position> grantedPositions) {
        List<GrantedAuthority> granted = new ArrayList<>();
        grantedPositions.stream()
            .map(position -> new SimpleGrantedAuthority("ROLE_" + position.name()))
            .forEach(granted::add);
        return granted;
    }

    public boolean hasRole(Position role) {
        return role != null && grantedPositions.contains(role);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

