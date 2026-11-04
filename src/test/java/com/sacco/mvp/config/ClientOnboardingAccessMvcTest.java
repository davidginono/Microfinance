package com.sacco.mvp.config;

import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.OwnStatus;
import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserDetailsService;
import com.sacco.mvp.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class, ClientOnboardingAccessMvcTest.Config.class})
class ClientOnboardingAccessMvcTest {
    @Autowired WebApplicationContext context;
    @Autowired OnboardingService onboarding;
    @Autowired AppUserDetailsService users;
    MockMvc mvc;
    AppUserPrincipal client;

    @BeforeEach void setup() {
        reset(onboarding, users);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        client = new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1")
            .stationId("B1").fullName("Synthetic Client").email("client@example.invalid")
            .username("synthetic.client").passwordHash("test-password").memberAccount(true)
            .position(Position.MEMBER).status(MemberStatus.ACTIVE).build(), EnumSet.allOf(UserClaim.class), false);
        when(onboarding.ownStatus(client)).thenReturn(new OwnStatus("NOT_STARTED", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOT_STARTED", "DRAFT", "SUBMITTED", "RETURN", "REJECT", "REVOKE", "EXPIRED", "STALE"})
    void onlyCurrentIndependentVerificationUnlocksBorrowerWorkspace(String state) throws Exception {
        when(onboarding.ownStatus(client)).thenReturn(new OwnStatus(state, null));
        for (String path : new String[]{"/app/dashboard", "/app/loan-products", "/app/loan-applications/new",
            "/app/guarantee-requests", "/app/group-loans", "/app/group-coverage", "/app/onboarding-bypass"}) {
            mvc.perform(get(path).with(user(client)).param("clientOnboardingRequired", "false"))
                .andExpect(redirectedUrl("/app/onboarding"))
                .andExpect(header().string("Cache-Control", "no-store"));
        }
    }

    @Test void blockedWritesAndJsonReadsNeverReachBorrowerHandlers() throws Exception {
        mvc.perform(post("/app/loan-applications").with(user(client)).with(csrf()))
            .andExpect(status().isForbidden()).andExpect(header().string("Location", "/app/onboarding"))
            .andExpect(jsonPath("$.error").value("client_onboarding_required"));
        mvc.perform(get("/app/dashboard").with(user(client)).accept("application/json"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("client_onboarding_required"));
    }

    @Test void contextPathAndDecodedServletPathsCannotBypassOnboarding() throws Exception {
        mvc.perform(get("/tenant/app/dashboard").contextPath("/tenant").servletPath("/app/dashboard")
            .with(user(client))).andExpect(redirectedUrl("/tenant/app/onboarding"));
        mvc.perform(get(java.net.URI.create("/ap%70/dashboard"))
            .with(request -> { request.setServletPath("/app/dashboard"); return request; }).with(user(client)))
            .andExpect(redirectedUrl("/app/onboarding"));
    }

    @Test void onboardingAndRecoveryPagesRemainAvailableWithoutRedirectLoops() throws Exception {
        for (String path : new String[]{"/app/onboarding", "/app/onboarding/edit", "/app/onboarding/versions/1",
            "/app/settings", "/app/support", "/app/notifications", "/account/security", "/profile"}) {
            mvc.perform(get(path).with(user(client))).andExpect(status().isOk())
                .andExpect(content().string("onboarding required"));
        }
        mvc.perform(post("/app/onboarding/edit").with(user(client)).with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(post("/logout").with(user(client)).with(csrf())).andExpect(status().is3xxRedirection());
    }

    @Test void sharedAccountPagesReflectVerificationChangesInTheSameSession() throws Exception {
        var session = new MockHttpSession();
        when(onboarding.ownStatus(client)).thenReturn(new OwnStatus("DRAFT", null));
        for (String path : new String[]{"/app/onboarding", "/account/security", "/profile"}) {
            mvc.perform(get(path).session(session).with(user(client))).andExpect(status().isOk())
                .andExpect(request().attribute("clientOnboardingRequired", true));
        }
        when(onboarding.ownStatus(client)).thenReturn(new OwnStatus("VERIFY", null));
        mvc.perform(get("/account/security").session(session).with(user(client))).andExpect(status().isOk())
            .andExpect(request().attribute("clientOnboardingRequired", false));
        when(onboarding.ownStatus(client)).thenReturn(new OwnStatus("REVOKE", null));
        mvc.perform(get("/profile").session(session).with(user(client))).andExpect(status().isOk())
            .andExpect(request().attribute("clientOnboardingRequired", true));
    }

    @Test void csrfAndExistingPermissionsAreStillRequired() throws Exception {
        mvc.perform(post("/app/onboarding/edit").with(user(client))).andExpect(status().isForbidden());
        mvc.perform(post("/app/loan-applications").with(user(client))).andExpect(status().isForbidden());
        var noClaims = new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1")
            .stationId("B1").position(Position.MEMBER).memberAccount(true).status(MemberStatus.ACTIVE).build(), Set.of(), false);
        mvc.perform(get("/app/loan-products").with(user(noClaims))).andExpect(status().isForbidden());
        verifyNoInteractions(onboarding);
    }

    @Test void decisionsTakeEffectInTheSameSessionAndApprovalIsNeverCached() throws Exception {
        var session = new MockHttpSession();
        when(onboarding.ownStatus(client)).thenReturn(new OwnStatus("VERIFY", null));
        mvc.perform(get("/app/dashboard").session(session).with(user(client)))
            .andExpect(status().isOk()).andExpect(content().string("borrower workspace"));
        when(onboarding.ownStatus(client)).thenReturn(new OwnStatus("REVOKE", null));
        mvc.perform(get("/app/dashboard").session(session).with(user(client)))
            .andExpect(redirectedUrl("/app/onboarding"));
        when(onboarding.ownStatus(client)).thenReturn(new OwnStatus("VERIFY", null));
        mvc.perform(get("/app/dashboard").session(session).with(user(client))).andExpect(status().isOk());
        verify(onboarding, times(3)).ownStatus(client);
    }

    @Test void missingStatusAndChangedScopeCannotUnlockTheWorkspace() throws Exception {
        when(onboarding.ownStatus(client)).thenReturn(null);
        mvc.perform(get("/app/dashboard").with(user(client))).andExpect(redirectedUrl("/app/onboarding"));
        when(onboarding.ownStatus(client)).thenThrow(new AccessDeniedException("Client unavailable"));
        mvc.perform(get("/app/dashboard").with(user(client))).andExpect(status().isForbidden());
    }

    @Test void staffSessionsAndAnonymousRequestsDoNotQueryClientVerification() throws Exception {
        var staff = new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1")
            .position(Position.MANAGER).memberAccount(true).staffAccessStatus(StaffAccessStatus.ACTIVE)
            .status(MemberStatus.ACTIVE).build(), EnumSet.allOf(UserClaim.class), true);
        mvc.perform(get("/manager/dashboard").with(user(staff))).andExpect(status().isOk());
        mvc.perform(get("/account/security").with(user(staff))).andExpect(status().isOk())
            .andExpect(result -> assertThat(result.getRequest().getAttribute("clientOnboardingRequired")).isNull());
        mvc.perform(get("/app/dashboard")).andExpect(status().is3xxRedirection());
        verifyNoInteractions(onboarding);
    }

    @Test void firstLoginAndSavedDeepLinksEndAtMandatoryOnboarding() throws Exception {
        when(users.loadClientByEmailOrUsername("SYNTHETIC.CLIENT")).thenReturn(client);
        var login = mvc.perform(post("/login").with(csrf()).param("username", "synthetic.client")
            .param("password", "test-password")).andExpect(status().is3xxRedirection()).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get(login.getResponse().getRedirectedUrl()).session(session))
            .andExpect(redirectedUrl("/app/onboarding"));
        // A saved/bookmarked creation URL goes through the same live check.
        mvc.perform(get("/app/loan-applications/new").session(session))
            .andExpect(redirectedUrl("/app/onboarding"));
    }

    @Configuration @EnableWebMvc static class Config {
        @Bean FixtureController fixtureController() { return new FixtureController(); }
    }

    @RestController static class FixtureController {
        @GetMapping({"/app/dashboard", "/app/loan-products", "/app/loan-applications/new",
            "/app/guarantee-requests", "/app/group-loans", "/app/group-coverage", "/app/onboarding-bypass",
            "/app/onboarding", "/app/onboarding/edit", "/app/onboarding/versions/1",
            "/app/settings", "/app/support", "/app/notifications", "/manager/dashboard",
            "/account/security", "/profile"})
        String page(HttpServletRequest request) {
            return Boolean.TRUE.equals(request.getAttribute("clientOnboardingRequired"))
                ? "onboarding required" : "borrower workspace";
        }
        @PostMapping({"/app/loan-applications", "/app/onboarding/edit"})
        String write() { return "saved"; }
    }
}
