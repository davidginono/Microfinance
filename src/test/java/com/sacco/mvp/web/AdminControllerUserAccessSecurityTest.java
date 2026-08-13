package com.sacco.mvp.web;

import com.sacco.mvp.config.SecurityConfig;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.PlatformSessionSettings;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoAccessStatus;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.domain.StaffAccessStatus;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.security.AppUserDetailsService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.security.AuthzService;
import com.sacco.mvp.security.SaccoAccessFilter;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.AdminScopeService;
import com.sacco.mvp.service.AdminService;
import com.sacco.mvp.service.AppUsageAnalyticsService;
import com.sacco.mvp.service.AuditService;
import com.sacco.mvp.service.DatabaseUtilizationService;
import com.sacco.mvp.service.LoanProductRequiredAttachmentService;
import com.sacco.mvp.service.NotificationInboxService;
import com.sacco.mvp.service.PlatformAdminService;
import com.sacco.mvp.service.PlatformBrandingSettingsService;
import com.sacco.mvp.service.PlatformSessionSettingsService;
import com.sacco.mvp.service.PlatformSupportContactSettingsService;
import com.sacco.mvp.service.SaccoDataDeletionService;
import com.sacco.mvp.service.SaccoRegistryService;
import com.sacco.mvp.service.SessionTimeoutPolicy;
import com.sacco.mvp.service.SmsUsageManagementService;
import com.sacco.mvp.service.StaffMfaService;
import com.sacco.mvp.service.StationOtpSettingsService;
import com.sacco.mvp.service.UserClaimService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextListener;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringJUnitWebConfig(AdminControllerUserAccessSecurityTest.TestConfig.class)
class AdminControllerUserAccessSecurityTest {
    @Autowired private WebApplicationContext context;
    @Autowired private AdminService adminService;
    @Autowired private AdminScopeService adminScopeService;
    @Autowired private SaccoStationRepository saccoStationRepository;
    @Autowired private PlatformSessionSettingsService platformSessionSettingsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(adminService, adminScopeService, saccoStationRepository, platformSessionSettingsService);
        when(platformSessionSettingsService.policy()).thenReturn(new SessionTimeoutPolicy(30, 1_800_000L, 60_000L));
        when(platformSessionSettingsService.settings()).thenReturn(platformSessionSettings(30));
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    void editUserPageAllowsWorkspaceAdminWithUpdateClaim() throws Exception {
        UUID accountId = UUID.randomUUID();
        AppUserPrincipal principal = principal(Set.of(UserClaim.USER_ACCESS_VIEW, UserClaim.USER_ACCESS_UPDATE));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));
        when(adminScopeService.currentSaccoId(any(AppUserPrincipal.class))).thenReturn("SACCO-01");
        when(adminScopeService.currentStationId(any(AppUserPrincipal.class))).thenReturn("ST-1");
        when(adminService.userAccess("SACCO-01", "ST-1", accountId)).thenReturn(userView(accountId));

        mockMvc.perform(get("/admin/users/{id}/edit", accountId)
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/user-edit"));

        verify(adminService).userAccess("SACCO-01", "ST-1", accountId);
    }

    @Test
    void editUserPageDeniesWorkspaceAdminWithoutUpdateClaim() throws Exception {
        UUID accountId = UUID.randomUUID();
        AppUserPrincipal principal = principal(Set.of(UserClaim.USER_ACCESS_VIEW));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));

        mockMvc.perform(get("/admin/users/{id}/edit", accountId)
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isForbidden());

        verify(adminService, never()).userAccess(any(), any(), any());
    }

    @Test
    void usersListRouteUsesPagedService() throws Exception {
        AppUserPrincipal principal = principal(Set.of(UserClaim.USER_ACCESS_VIEW, UserClaim.USER_ACCESS_UPDATE));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));
        when(adminScopeService.currentSaccoId(any(AppUserPrincipal.class))).thenReturn("SACCO-01");
        when(adminScopeService.currentStationId(any(AppUserPrincipal.class))).thenReturn("ST-1");
        when(adminService.usersPage("SACCO-01", "ST-1", "userId", null, 0, 25))
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/admin/users")
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/users"));

        verify(adminService).usersPage("SACCO-01", "ST-1", "userId", null, 0, 25);
    }

    @Test
    void platformSettingsAllowsPlatformAdminWithViewClaim() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_VIEW));

        mockMvc.perform(get("/admin/platform-settings")
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/platform-settings"));

        verify(platformSessionSettingsService).settings();
    }

    @Test
    void platformSettingsDeniesPlatformAdminWithoutViewClaim() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.ADMIN_DASHBOARD_VIEW));

        mockMvc.perform(get("/admin/platform-settings")
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateSessionTimeoutAllowsPlatformAdminWithUpdateClaim() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE));

        mockMvc.perform(post("/admin/platform-settings/session-timeout")
                .param("timeoutMinutes", "45")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/platform-settings"));

        verify(platformSessionSettingsService).updateTimeout(45, principal.getMemberId());
    }

    @Test
    void updateSessionTimeoutDeniesPlatformAdminWithoutUpdateClaim() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_VIEW));

        mockMvc.perform(post("/admin/platform-settings/session-timeout")
                .param("timeoutMinutes", "45")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isForbidden());

        verify(platformSessionSettingsService, never()).updateTimeout(any(Integer.class), any());
    }

    private UsernamePasswordAuthenticationToken authenticationFor(AppUserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
    }

    private AppUserPrincipal principal(Set<UserClaim> claims) {
        Member member = Member.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("STAFF-10000")
            .staffNo("10000")
            .fullName("Workspace Admin")
            .memberAccount(false)
            .status(MemberStatus.ACTIVE)
            .position(Position.MINOR_ADMIN)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MINOR_ADMIN)))
            .staffAccessStatus(StaffAccessStatus.ACTIVE)
            .passwordHash("x")
            .createdAt(OffsetDateTime.now())
            .build();
        return new AppUserPrincipal(member, claims, true);
    }

    private AppUserPrincipal platformPrincipal(Set<UserClaim> claims) {
        Member member = Member.builder()
            .id(UUID.randomUUID())
            .saccoId("PLATFORM")
            .stationId(null)
            .memberNo("ADMIN-1")
            .staffNo("ADMIN-1")
            .fullName("Platform Admin")
            .memberAccount(false)
            .status(MemberStatus.ACTIVE)
            .position(Position.ADMIN)
            .staffRoles(new LinkedHashSet<>(List.of(Position.ADMIN)))
            .staffAccessStatus(StaffAccessStatus.ACTIVE)
            .passwordHash("x")
            .createdAt(OffsetDateTime.now())
            .build();
        return new AppUserPrincipal(member, claims, true);
    }

    private PlatformSessionSettings platformSessionSettings(int timeoutMinutes) {
        OffsetDateTime now = OffsetDateTime.now();
        return PlatformSessionSettings.builder()
            .id(PlatformSessionSettings.DEFAULT_ID)
            .timeoutMinutes(timeoutMinutes)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    private SaccoStation station() {
        return SaccoStation.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .active(true)
            .accessStatus(SaccoAccessStatus.ACTIVE)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private AdminService.UserAccessView userView(UUID accountId) {
        return AdminService.UserAccessView.builder()
            .accountId(accountId)
            .userIdLabel(accountId.toString().substring(0, 8))
            .loginId("10001")
            .memberNumber("-")
            .staffMemberNumber("10001")
            .fullName("Manager User")
            .email("manager@example.com")
            .phone("255712345678")
            .roleSummary("Manager")
            .staffRoles(new LinkedHashSet<>(List.of(Position.MANAGER)))
            .claims(Set.of(UserClaim.MANAGER_QUEUE_VIEW))
            .status(MemberStatus.ACTIVE)
            .displayStatus("ACTIVE")
            .membershipLabel("Staff")
            .memberAccess(false)
            .canDeleteStaffRecord(false)
            .build();
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean
        AdminController adminController(AdminService adminService,
                                        AdminScopeService adminScopeService,
                                        SaccoRegistryService saccoRegistryService,
                                        PlatformAdminService platformAdminService,
                                        DatabaseUtilizationService databaseUtilizationService,
                                        AppUsageAnalyticsService appUsageAnalyticsService,
                                        NotificationInboxService notificationInboxService,
                                        LoanProductRequiredAttachmentService requiredAttachmentService,
                                        SmsUsageManagementService smsUsageManagementService,
                                        StationOtpSettingsService stationOtpSettingsService,
                                        SaccoDataDeletionService saccoDataDeletionService,
                                        PlatformBrandingSettingsService platformBrandingSettingsService,
                                        PlatformSessionSettingsService platformSessionSettingsService,
                                        PlatformSupportContactSettingsService platformSupportContactSettingsService) {
            return new AdminController(
                adminService,
                new com.sacco.mvp.service.ApplicationClock("Africa/Nairobi"),
                adminScopeService,
                saccoRegistryService,
                platformAdminService,
                databaseUtilizationService,
                appUsageAnalyticsService,
                notificationInboxService,
                requiredAttachmentService,
                smsUsageManagementService,
                stationOtpSettingsService,
                saccoDataDeletionService,
                platformBrandingSettingsService,
                platformSessionSettingsService,
                platformSupportContactSettingsService
            );
        }

        @Bean
        SaccoAccessFilter saccoAccessFilter(SaccoStationRepository saccoStationRepository) {
            return new SaccoAccessFilter(saccoStationRepository);
        }

        @Bean("authz")
        AuthzService authzService(LoanApplicationRepository loanApplicationRepository,
                                  GuarantorRequestRepository guarantorRequestRepository,
                                  BoardReviewRepository boardReviewRepository) {
            return new AuthzService(
                loanApplicationRepository,
                guarantorRequestRepository,
                boardReviewRepository,
                accessControlService()
            );
        }

        @Bean("access")
        AccessControlService accessControlService() {
            return new AccessControlService();
        }

        @Bean
        ViewResolver viewResolver() {
            InternalResourceViewResolver resolver = new InternalResourceViewResolver();
            resolver.setPrefix("/WEB-INF/jsp/");
            resolver.setSuffix(".jsp");
            return resolver;
        }

        @Bean
        RequestContextListener requestContextListener() {
            return new RequestContextListener();
        }

        @Bean AdminService adminService() { return Mockito.mock(AdminService.class); }
        @Bean AdminScopeService adminScopeService() { return Mockito.mock(AdminScopeService.class); }
        @Bean SaccoRegistryService saccoRegistryService() { return Mockito.mock(SaccoRegistryService.class); }
        @Bean PlatformAdminService platformAdminService() { return Mockito.mock(PlatformAdminService.class); }
        @Bean DatabaseUtilizationService databaseUtilizationService() { return Mockito.mock(DatabaseUtilizationService.class); }
        @Bean AppUsageAnalyticsService appUsageAnalyticsService() { return Mockito.mock(AppUsageAnalyticsService.class); }
        @Bean NotificationInboxService notificationInboxService() { return Mockito.mock(NotificationInboxService.class); }
        @Bean LoanProductRequiredAttachmentService requiredAttachmentService() { return Mockito.mock(LoanProductRequiredAttachmentService.class); }
        @Bean SmsUsageManagementService smsUsageManagementService() { return Mockito.mock(SmsUsageManagementService.class); }
        @Bean StationOtpSettingsService stationOtpSettingsService() { return Mockito.mock(StationOtpSettingsService.class); }
        @Bean SaccoDataDeletionService saccoDataDeletionService() { return Mockito.mock(SaccoDataDeletionService.class); }
        @Bean PlatformBrandingSettingsService platformBrandingSettingsService() { return Mockito.mock(PlatformBrandingSettingsService.class); }
        @Bean
        PlatformSessionSettingsService platformSessionSettingsService() {
            PlatformSessionSettingsService service = Mockito.mock(PlatformSessionSettingsService.class);
            when(service.policy()).thenReturn(new SessionTimeoutPolicy(30, 1_800_000L, 60_000L));
            return service;
        }
        @Bean PlatformSupportContactSettingsService platformSupportContactSettingsService() { return Mockito.mock(PlatformSupportContactSettingsService.class); }
        @Bean MemberRepository memberRepository() { return Mockito.mock(MemberRepository.class); }
        @Bean SaccoStationRepository saccoStationRepository() { return Mockito.mock(SaccoStationRepository.class); }
        @Bean StaffMfaService staffMfaService() { return Mockito.mock(StaffMfaService.class); }
        @Bean UserClaimService userClaimService() { return Mockito.mock(UserClaimService.class); }
        @Bean AppUserDetailsService appUserDetailsService() { return Mockito.mock(AppUserDetailsService.class); }
        @Bean AuditService auditService() { return Mockito.mock(AuditService.class); }
        @Bean LoanApplicationRepository loanApplicationRepository() { return Mockito.mock(LoanApplicationRepository.class); }
        @Bean GuarantorRequestRepository guarantorRequestRepository() { return Mockito.mock(GuarantorRequestRepository.class); }
        @Bean BoardReviewRepository boardReviewRepository() { return Mockito.mock(BoardReviewRepository.class); }
    }
}
