package com.sacco.mvp.config;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.SaccoRegistryService;
import com.sacco.mvp.service.UserSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MemberLocaleInterceptorTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"/login","/system-admin/login"})
    void publicLoginLanguageSwitchesWithoutLookingUpAUser(String path)throws Exception {
        var settings=mock(UserSettingsService.class);var institutions=mock(SaccoRegistryService.class);
        var request=new MockHttpServletRequest("GET",path);var response=new MockHttpServletResponse();
        var resolver=new org.springframework.web.servlet.i18n.SessionLocaleResolver();
        request.setAttribute(org.springframework.web.servlet.DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE,resolver);
        org.springframework.web.util.ServletRequestPathUtils.parseAndCache(request);
        var registrations=new RegisteredInterceptors();new WebConfig(new MemberLocaleInterceptor(settings,institutions)).addInterceptors(registrations);
        request.setParameter("lang","sw");registrations.apply(request,response);assertThat(resolver.resolveLocale(request)).isEqualTo(java.util.Locale.of("sw"));
        request.setParameter("lang","en");registrations.apply(request,response);assertThat(resolver.resolveLocale(request)).isEqualTo(java.util.Locale.ENGLISH);
        verifyNoInteractions(settings,institutions);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"/finance/groups","/finance/groups/example","/finance/groups/example/authority/new","/finance/groups/cycles/example"})
    void registeredGroupWorkspacesUseTheStaffLanguage(String path) throws Exception {
        UUID id=UUID.randomUUID();var settings=mock(UserSettingsService.class);var institutions=mock(SaccoRegistryService.class);
        when(settings.languageOrDefault(id)).thenReturn("sw");
        var principal=new AppUserPrincipal(Member.builder().id(id).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT)
            .status(com.sacco.mvp.domain.MemberStatus.ACTIVE).staffAccessStatus(com.sacco.mvp.domain.StaffAccessStatus.ACTIVE).build(),Collections.emptySet());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        var request=new MockHttpServletRequest("GET",path);var response=new MockHttpServletResponse();var resolver=new org.springframework.web.servlet.i18n.SessionLocaleResolver();
        request.setAttribute(org.springframework.web.servlet.DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE,resolver);
        org.springframework.web.util.ServletRequestPathUtils.parseAndCache(request);
        var registrations=new RegisteredInterceptors();new WebConfig(new MemberLocaleInterceptor(settings,institutions)).addInterceptors(registrations);
        registrations.apply(request,response);
        assertThat(resolver.resolveLocale(request)).isEqualTo(java.util.Locale.of("sw"));verify(settings).languageOrDefault(id);verifyNoInteractions(institutions);
    }

    static class RegisteredInterceptors extends org.springframework.web.servlet.config.annotation.InterceptorRegistry {
        void apply(MockHttpServletRequest request,MockHttpServletResponse response)throws Exception {
            for(var interceptor:getInterceptors())if(interceptor instanceof org.springframework.web.servlet.handler.MappedInterceptor mapped && mapped.matches(request))mapped.preHandle(request,response,new Object());
        }
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void clientRepaymentReceiptsUseTheirCachedLanguagePreference() {
        UUID memberId = UUID.randomUUID();
        UserSettingsService userSettings = mock(UserSettingsService.class);
        SaccoRegistryService registry = mock(SaccoRegistryService.class);
        when(userSettings.languageOrDefault(memberId)).thenReturn("sw");
        MemberLocaleInterceptor interceptor = new MemberLocaleInterceptor(userSettings, registry);
        authenticateClientRecorder(memberId);
        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/repayments/loans/123/receipts/456");
        interceptor.preHandle(first, new MockHttpServletResponse(), new Object());
        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/repayments/loans/123");
        second.setSession(first.getSession());
        interceptor.preHandle(second, new MockHttpServletResponse(), new Object());
        verify(userSettings, times(1)).languageOrDefault(memberId);
        verifyNoInteractions(registry);
    }

    @Test
    void jsonRequestSkipsDatabaseBackedLocaleResolution() {
        UserSettingsService userSettings = mock(UserSettingsService.class);
        SaccoRegistryService saccoRegistry = mock(SaccoRegistryService.class);
        MemberLocaleInterceptor interceptor = new MemberLocaleInterceptor(userSettings, saccoRegistry);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/finance/loan-records/choices");
        request.addHeader("Accept", "application/json");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        verifyNoInteractions(userSettings, saccoRegistry);
    }

    @Test
    void memberHtmlRequestsReuseSessionCachedLocale() {
        UUID memberId = UUID.randomUUID();
        UserSettingsService userSettings = mock(UserSettingsService.class);
        SaccoRegistryService saccoRegistry = mock(SaccoRegistryService.class);
        when(userSettings.languageOrDefault(memberId)).thenReturn("sw");
        MemberLocaleInterceptor interceptor = new MemberLocaleInterceptor(userSettings, saccoRegistry);
        authenticateClientRecorder(memberId);
        MockHttpServletRequest firstRequest = new MockHttpServletRequest("GET", "/staff/dashboard");

        interceptor.preHandle(firstRequest, new MockHttpServletResponse(), new Object());
        MockHttpServletRequest secondRequest = new MockHttpServletRequest("GET", "/finance/loan-records");
        secondRequest.setSession(firstRequest.getSession());
        interceptor.preHandle(secondRequest, new MockHttpServletResponse(), new Object());

        verify(userSettings, times(1)).languageOrDefault(memberId);
        verifyNoInteractions(saccoRegistry);
    }

    @Test
    void updatedUserLocaleReplacesCachedValueWithoutDatabaseLookup() {
        UUID memberId = UUID.randomUUID();
        UserSettingsService userSettings = mock(UserSettingsService.class);
        SaccoRegistryService saccoRegistry = mock(SaccoRegistryService.class);
        MemberLocaleInterceptor interceptor = new MemberLocaleInterceptor(userSettings, saccoRegistry);
        authenticateClientRecorder(memberId);
        MockHttpServletRequest updateRequest = new MockHttpServletRequest("POST", "/staff/settings/language");
        interceptor.cacheUserLocale(updateRequest, memberId, "sw");
        MockHttpServletRequest nextRequest = new MockHttpServletRequest("GET", "/staff/dashboard");
        nextRequest.setSession(updateRequest.getSession());

        interceptor.preHandle(nextRequest, new MockHttpServletResponse(), new Object());

        assertThat(nextRequest.getSession().getAttribute(
            MemberLocaleInterceptor.class.getName() + ".USER_LOCALE." + memberId)).isEqualTo(java.util.Locale.of("sw"));
        verifyNoInteractions(userSettings, saccoRegistry);
    }

    @Test
    void financialStaffRoutesUseOneCachedPersonalLocale() {
        UUID id=UUID.randomUUID();var settings=mock(UserSettingsService.class);var registry=mock(SaccoRegistryService.class);
        when(settings.languageOrDefault(id)).thenReturn("sw");authenticateStaff(id,Position.ACCOUNTANT);
        var interceptor=new MemberLocaleInterceptor(settings,registry);
        org.springframework.mock.web.MockHttpSession session=new org.springframework.mock.web.MockHttpSession();
        for(String path:java.util.List.of("/finance/accounts","/finance/vouchers/receipts","/reports/builder","/reports/financial/portfolio")) {
            var request=new MockHttpServletRequest("GET",path);request.setSession(session);
            var resolver=new org.springframework.web.servlet.i18n.SessionLocaleResolver();
            request.setAttribute(org.springframework.web.servlet.DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE,resolver);
            interceptor.preHandle(request,new MockHttpServletResponse(),new Object());
            assertThat(resolver.resolveLocale(request)).isEqualTo(java.util.Locale.of("sw"));
        }
        verify(settings,times(1)).languageOrDefault(id);verifyNoInteractions(registry);
    }

    @Test
    void financialWorkspaceAdminRoutesRetainInstitutionDefaultLanguage() {
        UUID id=UUID.randomUUID();var settings=mock(UserSettingsService.class);var registry=mock(SaccoRegistryService.class);
        when(registry.defaultLanguage("SACCO-1")).thenReturn(java.util.Optional.of("sw"));authenticateStaff(id,Position.MINOR_ADMIN);
        var interceptor=new MemberLocaleInterceptor(settings,registry);
        for(String path:java.util.List.of("/finance/accounts","/reports/builder")) {
            var request=new MockHttpServletRequest("GET",path);var resolver=new org.springframework.web.servlet.i18n.SessionLocaleResolver();
            request.setAttribute(org.springframework.web.servlet.DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE,resolver);
            interceptor.preHandle(request,new MockHttpServletResponse(),new Object());
            assertThat(resolver.resolveLocale(request)).isEqualTo(java.util.Locale.of("sw"));
        }
        verifyNoInteractions(settings);verify(registry,times(2)).defaultLanguage("SACCO-1");
    }

    @Test
    void selectedNavbarLanguageOverridesAdminDefaultAcrossRegisteredWorkspaces() throws Exception {
        UUID id=UUID.randomUUID();var settings=mock(UserSettingsService.class);var registry=mock(SaccoRegistryService.class);
        authenticateStaff(id,Position.MINOR_ADMIN);var interceptor=new MemberLocaleInterceptor(settings,registry);
        var selected=new MockHttpServletRequest();interceptor.cacheUserLocale(selected,id,"sw");
        var registrations=new RegisteredInterceptors();new WebConfig(interceptor).addInterceptors(registrations);
        for(String path:java.util.List.of("/staff/dashboard","/admin/settings-controls","/finance/accounts","/reports/builder","/profile")) {
            var request=new MockHttpServletRequest("GET",path);request.setSession(selected.getSession());
            var resolver=new org.springframework.web.servlet.i18n.SessionLocaleResolver();
            request.setAttribute(org.springframework.web.servlet.DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE,resolver);
            org.springframework.web.util.ServletRequestPathUtils.parseAndCache(request);registrations.apply(request,new MockHttpServletResponse());
            assertThat(resolver.resolveLocale(request)).isEqualTo(java.util.Locale.of("sw"));
        }
        verifyNoInteractions(settings,registry);
    }

    private void authenticateStaff(UUID id,Position position) {
        Member member=Member.builder().id(id).saccoId("SACCO-1").stationId("B1").memberNo("STAFF-1")
            .fullName("Synthetic staff").status(com.sacco.mvp.domain.MemberStatus.ACTIVE).position(position).memberAccount(false).passwordHash("test-only").build();
        AppUserPrincipal principal=new AppUserPrincipal(member,Collections.emptySet());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,principal.getPassword(),principal.getAuthorities()));
    }

    private void authenticateClientRecorder(UUID memberId) {
        Member member = Member.builder()
            .id(memberId)
            .saccoId("SACCO-1")
            .memberNo("MEM-1")
            .fullName("Recorder")
            .status(com.sacco.mvp.domain.MemberStatus.ACTIVE)
            .stationId("B1")
            .position(Position.ACCOUNTANT)
            .memberAccount(false)
            .passwordHash("secret")
            .staffAccessStatus(com.sacco.mvp.domain.StaffAccessStatus.ACTIVE).build();
        AppUserPrincipal principal = new AppUserPrincipal(member, Collections.emptySet());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities()));
    }
}
