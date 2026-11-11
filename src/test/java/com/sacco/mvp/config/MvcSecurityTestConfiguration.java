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
    /** Models a retired session so route isolation remains tested after borrower principals are removed. */
    public static com.sacco.mvp.security.AppUserPrincipal formerClient(com.sacco.mvp.domain.Member member,java.util.Set<com.sacco.mvp.domain.UserClaim> claims) {
        var identity=com.sacco.mvp.domain.Member.builder().id(member.getId()).saccoId(member.getSaccoId()).stationId(member.getStationId())
            .fullName(member.getFullName()).email(member.getEmail()).memberNo(member.getMemberNo()).memberAccount(true)
            .status(com.sacco.mvp.domain.MemberStatus.ACTIVE).staffAccessStatus(com.sacco.mvp.domain.StaffAccessStatus.ACTIVE)
            .position(com.sacco.mvp.domain.Position.LOAN_OFFICER).build();
        return new com.sacco.mvp.security.AppUserPrincipal(identity,claims) {
            @Override public boolean isStaffSession(){return false;}
            @Override public com.sacco.mvp.domain.Position getPosition(){return com.sacco.mvp.domain.Position.MEMBER;}
            @Override public java.util.Set<com.sacco.mvp.domain.Position> getStaffRoles(){return java.util.Set.of();}
        };
    }

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
        when(service.policy()).thenReturn(new SessionTimeoutPolicy(30, 1_800_000L, 60_000L, false));
        return service;
    }
}
