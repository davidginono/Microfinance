package com.sacco.mvp.security;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.UserClaim;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Getter
public class AppUserPrincipal implements UserDetails {
    private static final long serialVersionUID = -4042803929349064595L;

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
        this.authorities = java.util.Collections.emptyList();
        this.claims = claims == null || claims.isEmpty()
            ? java.util.Collections.emptySet()
            : claims.stream().map(UserClaim::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Set<Position> resolveGrantedPositions(Set<Position> staffRoles, boolean memberAccess) {
        LinkedHashSet<Position> granted = new LinkedHashSet<>(Position.normalizeStaffRoles(staffRoles));
        if (memberAccess) {
            granted.add(Position.MEMBER);
        }
        return java.util.Collections.unmodifiableSet(granted);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public boolean hasMetadataRole(Position role) {
        return role != null && grantedPositions != null && grantedPositions.contains(role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities == null ? java.util.Collections.emptyList() : authorities;
    }

    public Set<String> getClaims() {
        return claims == null ? java.util.Collections.emptySet() : claims;
    }

    public Set<Position> getGrantedPositions() {
        return grantedPositions == null ? java.util.Collections.emptySet() : grantedPositions;
    }

    public Set<Position> getStaffRoles() {
        return staffRoles == null ? java.util.Collections.emptySet() : staffRoles;
    }

    public boolean isPlatformIdentity() {
        return hasMetadataRole(Position.ADMIN);
    }

    public boolean isWorkspaceAdminScope() {
        return hasMetadataRole(Position.MINOR_ADMIN) && !isPlatformIdentity();
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

