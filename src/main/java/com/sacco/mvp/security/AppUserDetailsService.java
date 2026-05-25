package com.sacco.mvp.security;

import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.service.UserClaimService;
import com.sacco.mvp.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {
    private final MemberRepository memberRepository;
    private final SaccoStationRepository saccoStationRepository;
    private final UserClaimService userClaimService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return memberRepository.findByMemberNo(username)
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .filter(member -> member.isMemberAccess() || !member.getStaffRolesResolved().isEmpty())
            .map(member -> {
                if (!member.getStaffRolesResolved().contains(Position.ADMIN)
                    && member.getSaccoId() != null
                    && !member.getSaccoId().isBlank()
                    && member.getStationId() != null
                    && !member.getStationId().isBlank()) {
                    saccoStationRepository.findBySaccoIdAndStationId(member.getSaccoId(), member.getStationId())
                        .filter(SaccoStation::isAccessSuspended)
                        .ifPresent(station -> {
                            throw new DisabledException(suspendedMessage(station));
                        });
                }
                return member;
            })
            .map(member -> new AppUserPrincipal(member, userClaimService.effectiveClaims(member.getId(), member.getStaffRolesResolved(), member.isMemberAccess())))
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    private String suspendedMessage(SaccoStation station) {
        String reason = station.getAccessRestrictionReason();
        if (reason == null || reason.isBlank()) {
            return "This station workspace has been suspended. Contact the platform administrator.";
        }
        return "This station workspace has been suspended: " + reason.trim();
    }
}
