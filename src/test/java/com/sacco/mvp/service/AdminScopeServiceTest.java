package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoAccessStatus;
import com.sacco.mvp.domain.StaffAccessStatus;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminScopeServiceTest {

    @Test
    void staffUseTheirAssignedScopeWithoutAnInstitutionSelectionPermission() {
        var request=new MockHttpServletRequest();request.getSession().setAttribute("adminSelectedStationId","AR419");
        var members=mock(MemberRepository.class);var registry=mock(SaccoRegistryService.class);
        var member=member(UUID.randomUUID(),"AR704");member.setPosition(Position.ACCOUNTANT);member.setStaffRoles(Set.of(Position.ACCOUNTANT));
        var principal=new AppUserPrincipal(member,Set.of());
        when(members.findById(member.getId())).thenReturn(Optional.of(member));
        when(registry.findRegisteredSaccoView("1001")).thenReturn(Optional.of(saccoView("AR704")));
        var service=service(request,registry,members);var scope=service.currentScope(principal);
        assertThat(scope.getSaccoId()).isEqualTo("1001");assertThat(scope.getStationId()).isEqualTo("AR704");
        assertThat(service.currentScope(principal)).isSameAs(scope);verify(members).findById(member.getId());
    }

    @Test
    void currentScopeRejectsAStalePrincipalAfterTheLiveAssignmentChanges() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("adminSelectedStationId", "AR419");
        MemberRepository members = mock(MemberRepository.class);
        SaccoRegistryService registry = mock(SaccoRegistryService.class);
        AppUserPrincipal principal = principal("AR419");
        when(members.findById(principal.getMemberId())).thenReturn(Optional.of(member(principal.getMemberId(), "AR704")));
        AdminScopeService service = service(request, registry, members);

        assertThatThrownBy(()->service.currentScope(principal)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Sign out");
        assertThat(request.getSession().getAttribute("adminSelectedStationId")).isEqualTo("AR419");
        verify(members).findById(principal.getMemberId());
    }

    @Test
    void currentScopeFailsClosedWhenLiveAssignedStationIsNotActive() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MemberRepository members = mock(MemberRepository.class);
        SaccoRegistryService registry = mock(SaccoRegistryService.class);
        AppUserPrincipal principal = principal("AR704");
        when(members.findById(principal.getMemberId())).thenReturn(Optional.of(member(principal.getMemberId(), "AR704")));
        when(registry.findRegisteredSaccoView("1001")).thenReturn(Optional.of(saccoView("AR419")));
        AdminScopeService service = service(request, registry, members);

        assertThatThrownBy(() -> service.currentScope(principal))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("assigned station is not active")
            .hasMessageContaining("platform administrator");
    }

    private AdminScopeService service(MockHttpServletRequest request,
                                      SaccoRegistryService registry,
                                      MemberRepository members) {
        ObjectFactory<HttpServletRequest> requestFactory = () -> request;
        return new AdminScopeService(requestFactory, registry, members);
    }

    private AppUserPrincipal principal(String stationId) {
        Member member = member(UUID.randomUUID(), stationId);
        return new AppUserPrincipal(member,Set.of());
    }

    private Member member(UUID id, String stationId) {
        return Member.builder()
            .id(id)
            .saccoId("1001")
            .stationId(stationId)
            .memberNo("STAFF-10000")
            .staffNo("10000")
            .fullName("SACCO Admin")
            .memberAccount(false)
            .staffAccessStatus(StaffAccessStatus.ACTIVE)
            .staffRoles(Set.of(Position.MINOR_ADMIN))
            .position(Position.MINOR_ADMIN)
            .status(MemberStatus.ACTIVE)
            .passwordHash("hash")
            .build();
    }

    private SaccoRegistryService.RegisteredSaccoView saccoView(String stationId) {
        return new SaccoRegistryService.RegisteredSaccoView(
            "1001",
            "TAHA SACCOS",
            List.of(new SaccoRegistryService.StationView(stationId, null)),
            false,
            null,
            SaccoAccessStatus.ACTIVE,
            true
        );
    }
}
