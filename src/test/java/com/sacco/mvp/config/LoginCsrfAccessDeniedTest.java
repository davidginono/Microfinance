package com.sacco.mvp.config;

import com.sacco.mvp.security.AppUserDetailsService;
import com.sacco.mvp.security.AuthzService;
import com.sacco.mvp.security.SaccoAccessFilter;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.AdminScopeService;
import com.sacco.mvp.service.AppUsageAnalyticsService;
import com.sacco.mvp.service.AuditService;
import com.sacco.mvp.service.MemberDirectoryService;
import com.sacco.mvp.service.PlatformSessionSettingsService;
import com.sacco.mvp.service.SessionTimeoutPolicy;
import com.sacco.mvp.service.StaffMfaService;
import com.sacco.mvp.service.StationAccessService;
import com.sacco.mvp.service.StationOtpSettingsService;
import com.sacco.mvp.service.UserClaimService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(LoginCsrfAccessDeniedTest.TestConfig.class)
class LoginCsrfAccessDeniedTest {
    private static final String EXPIRED_LOGIN_MESSAGE = "Your sign-in page expired. Please try logging in again.";

    @Autowired private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    void expiredStaffLoginSubmissionReturnsToStaffLoginTab() throws Exception {
        var result = mockMvc.perform(post("/login")
                .param("loginType", "staff-password"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error&tab=staff"))
            .andReturn();

        assertThat(result.getRequest().getSession(false).getAttribute("loginErrorMessage"))
            .isEqualTo(EXPIRED_LOGIN_MESSAGE);
    }

    @Test
    void expiredSystemAdminLoginSubmissionReturnsToSystemAdminLogin() throws Exception {
        var result = mockMvc.perform(post("/login")
                .param("loginType", "system-admin-password"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system-admin/login?error"))
            .andReturn();

        assertThat(result.getRequest().getSession(false).getAttribute("loginErrorMessage"))
            .isEqualTo(EXPIRED_LOGIN_MESSAGE);
    }

    @Configuration
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean
        SaccoAccessFilter saccoAccessFilter(StationAccessService stationAccessService) {
            return new SaccoAccessFilter(stationAccessService);
        }

        @Bean
        AccessControlService accessControlService() {
            return new AccessControlService();
        }

        @Bean("authz")
        AuthzService authzService(AccessControlService accessControlService) {
            return Mockito.mock(AuthzService.class);
        }

        @Bean AppUsageAnalyticsService appUsageAnalyticsService() { return Mockito.mock(AppUsageAnalyticsService.class); }
        @Bean AuditService auditService() { return Mockito.mock(AuditService.class); }
        @Bean MemberDirectoryService memberDirectoryService() { return Mockito.mock(MemberDirectoryService.class); }
        @Bean StationAccessService stationAccessService() { return Mockito.mock(StationAccessService.class); }
        @Bean AdminScopeService adminScopeService() { return Mockito.mock(AdminScopeService.class); }
        @Bean StaffMfaService staffMfaService() { return Mockito.mock(StaffMfaService.class); }
        @Bean StationOtpSettingsService stationOtpSettingsService() { return Mockito.mock(StationOtpSettingsService.class); }
        @Bean UserClaimService userClaimService() { return Mockito.mock(UserClaimService.class); }
        @Bean AppUserDetailsService appUserDetailsService() { return Mockito.mock(AppUserDetailsService.class); }

        @Bean
        PlatformSessionSettingsService platformSessionSettingsService() {
            PlatformSessionSettingsService service = Mockito.mock(PlatformSessionSettingsService.class);
            when(service.policy()).thenReturn(new SessionTimeoutPolicy(30, 1_800_000L, 60_000L));
            return service;
        }
    }
}
