package com.sacco.mvp.web;

import com.sacco.mvp.config.MvcSecurityTestConfiguration;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.OtpSelectionPolicy;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.UserOtpPreferenceService;
import com.sacco.mvp.service.UserSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class, AccountSecurityControllerTest.Config.class})
class AccountSecurityControllerTest {
    @Autowired private WebApplicationContext context;
    @Autowired private UserOtpPreferenceService preferences;
    private MockMvc mvc;
    private AppUserPrincipal principal;

    @BeforeEach
    void setUp() {
        reset(preferences);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        principal = new AppUserPrincipal(Member.builder().id(UUID.randomUUID())
            .memberNo("OTP-TEST").memberAccount(true).status(MemberStatus.ACTIVE).build(), Set.of(), false);
    }

    @Test
    void pageUsesAuthenticatedUsersPreferences() throws Exception {
        var saved = new UserSettingsService.OtpPreferences(false, false);
        when(preferences.current(principal.getMemberId()))
            .thenReturn(new UserOtpPreferenceService.OtpSecuritySettings(saved, OtpSelectionPolicy.NONE));

        mvc.perform(get("/account/security").with(user(principal)))
            .andExpect(status().isOk()).andExpect(view().name("account/security"))
            .andExpect(model().attribute("otpPreferences", saved));
    }

    @Test
    void disablingBothNeedsOnlyAuthenticatedSessionAndCsrf() throws Exception {
        mvc.perform(post("/account/security/preferences").with(user(principal)).with(csrf())
                .param("memberId", UUID.randomUUID().toString()))
            .andExpect(redirectedUrl("/account/security"))
            .andExpect(flash().attribute("message", "OTP security preferences updated."));

        verify(preferences).update(principal.getMemberId(), false, false);
    }

    @Test
    void savesAreStillProtectedByCsrfAndAuthentication() throws Exception {
        mvc.perform(post("/account/security/preferences").with(user(principal)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/account/security/preferences").with(csrf()))
            .andExpect(status().is3xxRedirection());
        verifyNoInteractions(preferences);
    }

    @Test
    void branchPolicyFailureReturnsAnError() throws Exception {
        doThrow(new IllegalArgumentException("Select login OTP, approval OTP, or both."))
            .when(preferences).update(principal.getMemberId(), false, false);

        mvc.perform(post("/account/security/preferences").with(user(principal)).with(csrf()))
            .andExpect(redirectedUrl("/account/security"))
            .andExpect(flash().attribute("error", "Select login OTP, approval OTP, or both."));
    }

    @Test
    void removedConfirmationEndpointIsUnavailable() throws Exception {
        mvc.perform(post("/account/security/request-otp").with(user(principal)).with(csrf()))
            .andExpect(status().isNotFound());
        verifyNoInteractions(preferences);
    }

    @Configuration
    @EnableWebMvc
    static class Config {
        @Bean UserOtpPreferenceService userOtpPreferenceService() { return mock(UserOtpPreferenceService.class); }
        @Bean AccountSecurityController controller(UserOtpPreferenceService service) {
            return new AccountSecurityController(service);
        }
    }
}
