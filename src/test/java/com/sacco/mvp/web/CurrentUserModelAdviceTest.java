package com.sacco.mvp.web;

import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.PlatformSupportContactSettings;
import com.sacco.mvp.domain.StaffAccessStatus;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AdminScopeService;
import com.sacco.mvp.service.PlatformSupportContactSettingsService;
import com.sacco.mvp.service.SaccoLogoStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CurrentUserModelAdviceTest {

    @Test
    void jsonRequestSkipsNotificationAndPageBrandQueries() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", "application/json");
        AdminScopeService scopes = mock(AdminScopeService.class);
        MemberRepository members = mock(MemberRepository.class);
        RegisteredSaccoRepository registeredSaccos = mock(RegisteredSaccoRepository.class);
        SaccoSettingsRepository settings = mock(SaccoSettingsRepository.class);
        SaccoStationRepository stations = mock(SaccoStationRepository.class);
        SaccoLogoStorageService logos = mock(SaccoLogoStorageService.class);
        PlatformSupportContactSettingsService supportContacts = mock(PlatformSupportContactSettingsService.class);
        CurrentUserModelAdvice advice = advice(request, scopes, members, registeredSaccos, settings, stations, logos, supportContacts);
        AppUserPrincipal principal = memberPrincipal();

        assertThat(advice.adminScope(principal)).isNull();
        assertThat(advice.headerStation(principal)).isNull();
        assertThat(advice.activeSaccoName(principal)).isNull();
        assertThat(advice.activeSaccoLogoUrl(principal)).isNull();
        assertThat(advice.platformSupportContact(principal)).isNull();

        verifyNoInteractions(scopes, registeredSaccos, settings, stations, logos, supportContacts);
    }

    @Test
    void workspaceUsersReceiveVisiblePlatformSupportContact() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        PlatformSupportContactSettingsService supportContacts = mock(PlatformSupportContactSettingsService.class);
        PlatformSupportContactSettings contact = PlatformSupportContactSettings.builder()
            .displayName("Platform Support")
            .displayRole("Super Admin Support")
            .phone("+255 746 359 369")
            .email("support@example.com")
            .build();
        when(supportContacts.sidebarContact()).thenReturn(contact);
        CurrentUserModelAdvice advice = advice(
            request,
            mock(AdminScopeService.class),
            mock(MemberRepository.class),
            mock(RegisteredSaccoRepository.class),
            mock(SaccoSettingsRepository.class),
            mock(SaccoStationRepository.class),
            mock(SaccoLogoStorageService.class),
            supportContacts
        );

        assertThat(advice.platformSupportContact(memberPrincipal())).isSameAs(contact);
        assertThat(advice.platformSupportContact(staffPrincipal(Position.MANAGER))).isSameAs(contact);
        assertThat(advice.platformSupportContact(staffPrincipal(Position.MINOR_ADMIN))).isSameAs(contact);
        verify(supportContacts, org.mockito.Mockito.times(3)).sidebarContact();
    }

    @Test
    void platformAdminDoesNotReceiveSidebarSupportContact() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        PlatformSupportContactSettingsService supportContacts = mock(PlatformSupportContactSettingsService.class);
        CurrentUserModelAdvice advice = advice(
            request,
            mock(AdminScopeService.class),
            mock(MemberRepository.class),
            mock(RegisteredSaccoRepository.class),
            mock(SaccoSettingsRepository.class),
            mock(SaccoStationRepository.class),
            mock(SaccoLogoStorageService.class),
            supportContacts
        );

        assertThat(advice.platformSupportContact(staffPrincipal(Position.ADMIN))).isNull();
        verifyNoInteractions(supportContacts);
    }

    private CurrentUserModelAdvice advice(MockHttpServletRequest request,
                                          AdminScopeService scopes,
                                          MemberRepository members,
                                          RegisteredSaccoRepository registeredSaccos,
                                          SaccoSettingsRepository settings,
                                          SaccoStationRepository stations,
                                          SaccoLogoStorageService logos,
                                          PlatformSupportContactSettingsService supportContacts) {
        ObjectFactory<HttpServletRequest> requestFactory = () -> request;
        return new CurrentUserModelAdvice(
            scopes,
            members,
            registeredSaccos,
            settings,
            stations,
            logos,
            supportContacts,
            new ObjectMapper(),
            requestFactory
        );
    }

    private AppUserPrincipal memberPrincipal() {
        Member member = Member.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .stationId("ST01")
            .memberNo("MEM-1")
            .fullName("Member One")
            .email("member@example.com")
            .memberAccount(true)
            .position(Position.MEMBER)
            .status(MemberStatus.ACTIVE)
            .passwordHash("hash")
            .build();
        return new AppUserPrincipal(member, Collections.emptySet());
    }

    private AppUserPrincipal staffPrincipal(Position role) {
        Member member = Member.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .stationId("ST01")
            .memberNo("MEM-2")
            .staffNo("STF-2")
            .fullName("Staff One")
            .email("staff@example.com")
            .memberAccount(false)
            .staffAccessStatus(StaffAccessStatus.ACTIVE)
            .staffRoles(java.util.Set.of(role))
            .position(role)
            .status(MemberStatus.ACTIVE)
            .passwordHash("hash")
            .build();
        return new AppUserPrincipal(member, Collections.emptySet(), true);
    }
}
