package com.sacco.mvp.security;

import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.service.UserClaimService;
import com.sacco.mvp.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {
    private final MemberRepository memberRepository;
    private final UserClaimService userClaimService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return memberRepository.findByMemberNo(username)
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .filter(member -> member.isMemberAccess() || !member.getStaffRolesResolved().isEmpty())
            .map(member -> new AppUserPrincipal(member, userClaimService.effectiveClaims(member.getId(), member.getStaffRolesResolved(), member.isMemberAccess())))
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
