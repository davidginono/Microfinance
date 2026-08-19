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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {
    @Mock private MemberRepository memberRepository;
    @Mock private SaccoStationRepository saccoStationRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private MemberAccessClaimRepository memberAccessClaimRepository;

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
        when(memberRepository.findByMemberNo("MEM001")).thenReturn(Optional.of(member));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "AR704")).thenReturn(Optional.of(station));

        AppUserDetailsService service = new AppUserDetailsService(
            new com.sacco.mvp.service.MemberDirectoryService(memberRepository),
            new com.sacco.mvp.service.StationAccessService(saccoStationRepository),
            new UserClaimService(memberAccessClaimRepository, userSettingsRepository, new ObjectMapper())
        );

        assertThatThrownBy(() -> service.loadUserByUsername("MEM001"))
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

        service.loadUserByUsername("ADM001");
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
