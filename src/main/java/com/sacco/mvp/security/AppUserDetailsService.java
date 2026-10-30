package com.sacco.mvp.security;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.service.MemberDirectoryService;
import com.sacco.mvp.service.StationAccessService;
import com.sacco.mvp.service.UserClaimService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {
    private final MemberDirectoryService memberDirectoryService;
    private final StationAccessService stationAccessService;
    private final UserClaimService userClaimService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return loadClientByEmailOrUsername(username);
    }

    public UserDetails loadClientByEmailOrUsername(String username) throws UsernameNotFoundException {
        return memberDirectoryService.findClientLoginAccount(username)
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .filter(Member::isMemberAccess)
            .filter(member -> !member.getActiveStaffRolesResolved().contains(Position.ADMIN))
            .map(member -> ensureStationAllowed(member, false))
            .map(member -> new AppUserPrincipal(
                member,
                userClaimService.clientSessionClaims(member.getId()),
                false
            ))
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    public UserDetails loadStaffByStaffNo(String staffNo) throws UsernameNotFoundException {
        return memberDirectoryService.findStaffLoginAccount(staffNo)
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .filter(Member::isStaffAccessActive)
            .filter(member -> !member.getActiveStaffRolesResolved().contains(Position.ADMIN))
            .map(member -> ensureStationAllowed(member, true))
            .map(member -> new AppUserPrincipal(
                member,
                userClaimService.effectiveClaims(member.getId(), member.getActiveStaffRolesResolved(), member.isMemberAccess()),
                true
            ))
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    public UserDetails loadPlatformAdminByLoginId(String loginId) throws UsernameNotFoundException {
        return memberDirectoryService.findPlatformAdminLoginAccount(loginId)
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .filter(Member::isStaffAccessActive)
            .filter(member -> member.getActiveStaffRolesResolved().contains(Position.ADMIN))
            .map(member -> new AppUserPrincipal(
                member,
                userClaimService.effectiveClaims(member.getId(), member.getActiveStaffRolesResolved(), member.isMemberAccess()),
                true
            ))
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    private Member ensureStationAllowed(Member member, boolean staffLogin) {
        boolean adminBypass = staffLogin && member.getActiveStaffRolesResolved().contains(Position.ADMIN);
        if (!adminBypass
            && member.getSaccoId() != null
            && !member.getSaccoId().isBlank()
            && member.getStationId() != null
            && !member.getStationId().isBlank()) {
            stationAccessService.suspendedStation(member.getSaccoId(), member.getStationId())
                .ifPresent(station -> {
                    throw new DisabledException(suspendedMessage(station));
                });
        }
        return member;
    }

    private String suspendedMessage(SaccoStation station) {
        String reason = station.getAccessRestrictionReason();
        if (reason == null || reason.isBlank()) {
            return "This station workspace has been suspended. Contact the platform administrator.";
        }
        return "This station workspace has been suspended: " + reason.trim();
    }
}
