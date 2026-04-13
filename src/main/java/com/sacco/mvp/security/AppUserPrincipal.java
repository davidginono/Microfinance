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
    private final String username;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;
    private final Set<String> claims;

    public AppUserPrincipal(Member member, Set<UserClaim> claims) {
        this.memberId = member.getId();
        this.saccoId = member.getSaccoId();
        this.stationId = member.getStationId();
        this.fullName = member.getFullName();
        this.email = member.getEmail();
        this.staffRoles = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(member.getStaffRolesResolved()));
        this.memberAccess = member.isMemberAccess();
        this.position = Position.primaryRole(this.staffRoles, this.memberAccess);
        this.grantedPositions = resolveGrantedPositions(this.staffRoles, this.memberAccess);
        this.username = member.getMemberNo();
        this.password = member.getPasswordHash();
        this.authorities = buildAuthorities(this.grantedPositions);
        this.claims = claims == null ? java.util.Collections.emptySet() : claims.stream().map(UserClaim::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Set<Position> resolveGrantedPositions(Set<Position> staffRoles, boolean memberAccess) {
        LinkedHashSet<Position> granted = new LinkedHashSet<>(Position.normalizeStaffRoles(staffRoles));
        if (memberAccess) {
            granted.add(Position.MEMBER);
        }
        return java.util.Collections.unmodifiableSet(granted);
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

