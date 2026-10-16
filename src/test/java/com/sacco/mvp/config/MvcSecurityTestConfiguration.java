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
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import static org.mockito.Mockito.when;

@TestConfiguration
@EnableWebSecurity
@Import(SecurityConfig.class)
public class MvcSecurityTestConfiguration {
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
