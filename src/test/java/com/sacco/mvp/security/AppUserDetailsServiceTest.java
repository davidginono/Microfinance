package com.sacco.mvp.security;

import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoAccessStatus;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.MemberAccessClaimRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.repository.UserSettingsRepository;
import com.sacco.mvp.service.UserClaimService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {
    @Mock private MemberRepository memberRepository;
    @Mock private SaccoStationRepository saccoStationRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private MemberAccessClaimRepository memberAccessClaimRepository;

    @Test void clientPasswordLoginCarriesExplicitOwnCoverageClaimsWithoutStaffClaims(){
        var id=UUID.randomUUID();var member=Member.builder().id(id).saccoId("SACCO-01").stationId("AR704").memberNo("MEM001").fullName("Client").memberAccount(true).status(MemberStatus.ACTIVE).passwordHash("test-only").build();
        member.setUsername("client.one");
        when(memberRepository.findByUsername("client.one")).thenReturn(Optional.of(member));when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01","AR704")).thenReturn(Optional.of(station(SaccoAccessStatus.ACTIVE)));
        when(memberAccessClaimRepository.findByIdMemberId(id)).thenReturn(List.of(new com.sacco.mvp.domain.MemberAccessClaim(new com.sacco.mvp.domain.MemberAccessClaimId(id,"GROUP_COVERAGE_OWN_VIEW")),new com.sacco.mvp.domain.MemberAccessClaim(new com.sacco.mvp.domain.MemberAccessClaimId(id,"GROUP_COVERAGE_LINK"))));
        var service=new AppUserDetailsService(new com.sacco.mvp.service.MemberDirectoryService(memberRepository),new com.sacco.mvp.service.StationAccessService(saccoStationRepository),new UserClaimService(memberAccessClaimRepository,userSettingsRepository,new ObjectMapper()));
        var principal=(AppUserPrincipal)service.loadClientByEmailOrUsername(" CLIENT.ONE ");assertThat(principal.isStaffSession()).isFalse();assertThat(principal.getUsername()).isEqualTo("client.one");assertThat(principal.getClaims()).contains("GROUP_COVERAGE_OWN_VIEW").doesNotContain("GROUP_COVERAGE_LINK");
    }

    @Test
    void suspendedSaccoBlocksWorkspaceUserLogin() {
        Member member = Member.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .stationId("AR704")
            .memberNo("MEM001")
            .fullName("Member One")
            .memberAccount(true)
            .status(MemberStatus.ACTIVE)
            .passwordHash("secret")
            .build();
        SaccoStation station = station(SaccoAccessStatus.SUSPENDED);
        station.setAccessRestrictionReason("payment overdue");
        when(memberRepository.findByUsername("member.one")).thenReturn(Optional.of(member));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "AR704")).thenReturn(Optional.of(station));

        AppUserDetailsService service = new AppUserDetailsService(
            new com.sacco.mvp.service.MemberDirectoryService(memberRepository),
            new com.sacco.mvp.service.StationAccessService(saccoStationRepository),
            new UserClaimService(memberAccessClaimRepository, userSettingsRepository, new ObjectMapper())
        );

        assertThatThrownBy(() -> service.loadUserByUsername("member.one"))
            .isInstanceOf(DisabledException.class)
            .hasMessage("This station workspace has been suspended: payment overdue");
    }

    @Test
    void suspendedSaccoDoesNotBlockPlatformAdminLogin() {
        Member admin = Member.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .stationId("AR704")
            .memberNo("ADM001")
            .fullName("Platform Admin")
            .staffRoles(new LinkedHashSet<>(List.of(Position.ADMIN)))
            .memberAccount(false)
            .status(MemberStatus.ACTIVE)
            .passwordHash("secret")
            .build();
        when(memberRepository.findByMemberNo("ADM001")).thenReturn(Optional.of(admin));
        when(memberAccessClaimRepository.findByIdMemberId(admin.getId())).thenReturn(List.of());

        AppUserDetailsService service = new AppUserDetailsService(
            new com.sacco.mvp.service.MemberDirectoryService(memberRepository),
            new com.sacco.mvp.service.StationAccessService(saccoStationRepository),
            new UserClaimService(memberAccessClaimRepository, userSettingsRepository, new ObjectMapper())
        );

        service.loadPlatformAdminByLoginId("ADM001");
    }

    @Test
    void platformAdminUsesDedicatedLoginWithStableMemberNumber() {
        Member admin = Member.builder()
            .id(UUID.randomUUID())
            .memberNo("ADM001")
            .staffNo(null)
            .fullName("Platform Admin")
            .staffRoles(new LinkedHashSet<>(List.of(Position.ADMIN)))
            .memberAccount(false)
            .status(MemberStatus.ACTIVE)
            .passwordHash("secret")
            .build();
        when(memberRepository.findByMemberNo("ADM001")).thenReturn(Optional.of(admin));
        when(memberAccessClaimRepository.findByIdMemberId(admin.getId())).thenReturn(List.of());

        AppUserDetailsService service = new AppUserDetailsService(
            new com.sacco.mvp.service.MemberDirectoryService(memberRepository),
            new com.sacco.mvp.service.StationAccessService(saccoStationRepository),
            new UserClaimService(memberAccessClaimRepository, userSettingsRepository, new ObjectMapper())
        );

        AppUserPrincipal principal = (AppUserPrincipal) service.loadPlatformAdminByLoginId("ADM001");

        assertThat(principal.isPlatformIdentity()).isTrue();
        assertThat(principal.isStaffSession()).isTrue();
    }

    @Test
    void ordinaryStaffLoginRejectsPlatformAdminIdentity() {
        Member admin = Member.builder()
            .id(UUID.randomUUID())
            .memberNo("ADM001")
            .staffNo("ADM001")
            .fullName("Platform Admin")
            .staffRoles(new LinkedHashSet<>(List.of(Position.ADMIN)))
            .memberAccount(false)
            .staffAccessStatus(com.sacco.mvp.domain.StaffAccessStatus.ACTIVE)
            .status(MemberStatus.ACTIVE)
            .passwordHash("secret")
            .build();
        when(memberRepository.findByStaffNo("ADM001")).thenReturn(Optional.of(admin));

        AppUserDetailsService service = new AppUserDetailsService(
            new com.sacco.mvp.service.MemberDirectoryService(memberRepository),
            new com.sacco.mvp.service.StationAccessService(saccoStationRepository),
            new UserClaimService(memberAccessClaimRepository, userSettingsRepository, new ObjectMapper())
        );

        assertThatThrownBy(() -> service.loadStaffByStaffNo("ADM001"))
            .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
    }

    @Test
    void ordinaryMemberLoginRejectsPlatformAdminIdentity() {
        Member admin = Member.builder()
            .id(UUID.randomUUID())
            .memberNo("ADM001")
            .fullName("Platform Admin")
            .staffRoles(new LinkedHashSet<>(List.of(Position.ADMIN)))
            .memberAccount(true)
            .staffAccessStatus(com.sacco.mvp.domain.StaffAccessStatus.ACTIVE)
            .status(MemberStatus.ACTIVE)
            .passwordHash("secret")
            .build();
        when(memberRepository.findByUsername("platform.admin")).thenReturn(Optional.of(admin));

        AppUserDetailsService service = new AppUserDetailsService(
            new com.sacco.mvp.service.MemberDirectoryService(memberRepository),
            new com.sacco.mvp.service.StationAccessService(saccoStationRepository),
            new UserClaimService(memberAccessClaimRepository, userSettingsRepository, new ObjectMapper())
        );

        assertThatThrownBy(() -> service.loadClientByEmailOrUsername("platform.admin"))
            .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
    }

    @Test
    void clientEmailLoginIsCaseInsensitiveAndDoesNotGrantStaffClaims() {
        Member client = Member.builder().id(UUID.randomUUID()).memberNo("CL-00000001").username("asha.mushi")
            .email("asha@example.com").memberAccount(true).status(MemberStatus.ACTIVE).build();
        when(memberRepository.findTop2ByEmailIgnoreCase("asha@example.com")).thenReturn(List.of(client));
        var users = new AppUserDetailsService(new com.sacco.mvp.service.MemberDirectoryService(memberRepository),
            new com.sacco.mvp.service.StationAccessService(saccoStationRepository),
            new UserClaimService(memberAccessClaimRepository, userSettingsRepository, new ObjectMapper()));

        var principal = (AppUserPrincipal) users.loadUserByUsername(" ASHA@EXAMPLE.COM ");
        assertThat(principal.getMemberId()).isEqualTo(client.getId());
        assertThat(principal.getUsername()).isEqualTo("asha.mushi");
        assertThat(principal.isStaffSession()).isFalse();
        assertThat(principal.getStaffRoles()).isEmpty();
    }

    @Test
    void clientNumbersAndAmbiguousEmailsCannotAuthenticate() {
        var directory = new com.sacco.mvp.service.MemberDirectoryService(memberRepository);
        var users = new AppUserDetailsService(directory, new com.sacco.mvp.service.StationAccessService(saccoStationRepository),
            new UserClaimService(memberAccessClaimRepository, userSettingsRepository, new ObjectMapper()));
        when(memberRepository.findTop2ByEmailIgnoreCase("duplicate@example.com")).thenReturn(List.of(
            Member.builder().id(UUID.randomUUID()).build(), Member.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> users.loadUserByUsername("CL-00000001"))
            .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
        assertThatThrownBy(() -> users.loadUserByUsername("duplicate@example.com"))
            .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
        org.mockito.Mockito.verify(memberRepository, org.mockito.Mockito.never()).findByMemberNo(org.mockito.ArgumentMatchers.anyString());
    }

    private SaccoStation station(SaccoAccessStatus accessStatus) {
        return SaccoStation.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .stationId("AR704")
            .active(true)
            .accessStatus(accessStatus)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }
}
