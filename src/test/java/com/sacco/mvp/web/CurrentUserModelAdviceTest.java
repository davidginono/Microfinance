package com.sacco.mvp.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AdminScopeService;
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
        RegisteredSaccoRepository registeredSaccos = mock(RegisteredSaccoRepository.class);
        SaccoSettingsRepository settings = mock(SaccoSettingsRepository.class);
        SaccoStationRepository stations = mock(SaccoStationRepository.class);
        SaccoLogoStorageService logos = mock(SaccoLogoStorageService.class);
        CurrentUserModelAdvice advice = advice(request, scopes, registeredSaccos, settings, stations, logos);
        AppUserPrincipal principal = memberPrincipal();

        assertThat(advice.adminScope(principal)).isNull();
        assertThat(advice.headerStation(principal)).isNull();
        assertThat(advice.activeSaccoName(principal)).isNull();
        assertThat(advice.activeSaccoLogoUrl(principal)).isNull();

        verifyNoInteractions(scopes, registeredSaccos, settings, stations, logos);
    }

    private CurrentUserModelAdvice advice(MockHttpServletRequest request,
                                          AdminScopeService scopes,
                                          RegisteredSaccoRepository registeredSaccos,
                                          SaccoSettingsRepository settings,
                                          SaccoStationRepository stations,
                                          SaccoLogoStorageService logos) {
        ObjectFactory<HttpServletRequest> requestFactory = () -> request;
        return new CurrentUserModelAdvice(
            scopes,
            registeredSaccos,
            settings,
            stations,
            logos,
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
}
