package com.sacco.mvp.security;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoAccessStatus;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.service.StationAccessService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaccoAccessFilterTest {
    @Mock private SaccoStationRepository saccoStationRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void suspendedWorkspaceUserIsSignedOutAndRedirected() throws ServletException, IOException {
        SaccoAccessFilter filter = new SaccoAccessFilter(new StationAccessService(saccoStationRepository));
        AppUserPrincipal principal = principal(Position.MANAGER, "SACCO-01");
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "AR704")).thenReturn(Optional.of(station(SaccoAccessStatus.SUSPENDED, "payment overdue")));
        SecurityContextHolder.getContext().setAuthentication(authentication(principal));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/staff/analytics");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getSession(false).getAttribute("loginErrorMessage"))
            .isEqualTo("This station workspace has been suspended: payment overdue");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void platformAdminBypassesSuspendedWorkspaceCheck() throws ServletException, IOException {
        SaccoAccessFilter filter = new SaccoAccessFilter(new StationAccessService(saccoStationRepository));
        AppUserPrincipal principal = principal(Position.ADMIN, "SACCO-01");
        SecurityContextHolder.getContext().setAuthentication(authentication(principal));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
        verify(saccoStationRepository, never()).findBySaccoIdAndStationId("SACCO-01", "AR704");
    }

    @Test
    void suspendedStationDoesNotBlockOtherStationsInSameSacco() throws ServletException, IOException {
        SaccoAccessFilter filter = new SaccoAccessFilter(new StationAccessService(saccoStationRepository));
        AppUserPrincipal principal = principal(Position.MANAGER, "SACCO-01", "BR001");
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "BR001")).thenReturn(Optional.of(station(SaccoAccessStatus.ACTIVE, null)));
        SecurityContextHolder.getContext().setAuthentication(authentication(principal));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/staff/analytics");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void missingSaccoIdDoesNotBreakFiltering() throws ServletException, IOException {
        SaccoAccessFilter filter = new SaccoAccessFilter(new StationAccessService(saccoStationRepository));
        AppUserPrincipal principal = principal(Position.MANAGER, null);
        SecurityContextHolder.getContext().setAuthentication(authentication(principal));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/staff/analytics");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
        verify(saccoStationRepository, never()).findBySaccoIdAndStationId(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    private UsernamePasswordAuthenticationToken authentication(AppUserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
    }

    private AppUserPrincipal principal(Position position, String saccoId) {
        return principal(position, saccoId, "AR704");
    }

    private AppUserPrincipal principal(Position position, String saccoId, String stationId) {
        LinkedHashSet<Position> staffRoles = new LinkedHashSet<>(List.of(position));
        Member member = Member.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .stationId(stationId)
            .memberNo(position.name())
            .fullName("Test User")
            .memberAccount(false)
            .status(MemberStatus.ACTIVE)
            .position(position)
            .staffRoles(staffRoles)
            .passwordHash("x")
            .createdAt(OffsetDateTime.now())
            .build();
        return new AppUserPrincipal(member, Collections.emptySet());
    }

    private SaccoStation station(SaccoAccessStatus accessStatus, String reason) {
        return SaccoStation.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .stationId("AR704")
            .active(true)
            .accessStatus(accessStatus)
            .accessRestrictionReason(reason)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }
}
