package com.sacco.mvp.web;

import com.sacco.mvp.config.SecurityConfig;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
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
import com.sacco.mvp.service.AppUsageAnalyticsService;
import com.sacco.mvp.service.AuditService;
import com.sacco.mvp.service.LoanAttachmentService;
import com.sacco.mvp.service.LoanPresentationService;
import com.sacco.mvp.service.LoanReportService;
import com.sacco.mvp.service.MemberDirectoryService;
import com.sacco.mvp.service.MemberProfileImageService;
import com.sacco.mvp.service.PlatformSessionSettingsService;
import com.sacco.mvp.service.SaccoLogoStorageService;
import com.sacco.mvp.service.SaccoRegistryService;
import com.sacco.mvp.service.SessionTimeoutPolicy;
import com.sacco.mvp.service.SmsUsageManagementService;
import com.sacco.mvp.service.StaffMfaService;
import com.sacco.mvp.service.StationAccessService;
import com.sacco.mvp.service.StationOtpSettingsService;
import com.sacco.mvp.service.UserClaimService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(LoanDocumentControllerSmsUsageSecurityTest.TestConfig.class)
class LoanDocumentControllerSmsUsageSecurityTest {
    @Autowired private WebApplicationContext context;
    @Autowired private AdminScopeService adminScopeService;
    @Autowired private LoanReportService loanReportService;
    @Autowired private SmsUsageManagementService smsUsageManagementService;
    @Autowired private SaccoStationRepository saccoStationRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(adminScopeService, loanReportService, smsUsageManagementService, saccoStationRepository);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    void smsUsagePdfExportRequiresSmsUsageViewClaim() throws Exception {
        AppUserPrincipal principal = principal(Set.of(UserClaim.USER_ACCESS_VIEW));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));

        mockMvc.perform(get("/documents/reports/sms-usage.pdf")
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isForbidden());

        verify(smsUsageManagementService, never()).loanUsageExport(any(), any());
    }

    @Test
    void workspaceSmsUsagePdfExportUsesScopedSaccoAndStation() throws Exception {
        UUID applicantId = UUID.randomUUID();
        LocalDate fromDate = LocalDate.of(2026, 9, 1);
        LocalDate toDate = LocalDate.of(2026, 9, 23);
        AppUserPrincipal principal = principal(Set.of(UserClaim.SMS_USAGE_VIEW));
        SmsUsageManagementService.LoanSmsUsageCriteria criteria =
            new SmsUsageManagementService.LoanSmsUsageCriteria(
                "SACCO-01",
                "ST-1",
                fromDate,
                toDate,
                LoanStatus.READY_FOR_MANAGER,
                List.of(applicantId)
            );
        SmsUsageManagementService.LoanSmsUsageExportReport report =
            new SmsUsageManagementService.LoanSmsUsageExportReport(
                "SACCO-01",
                "ST-1",
                fromDate,
                toDate,
                "On Review By Manager",
                "1 selected applicant",
                "Workspace Admin",
                toDate,
                List.of(),
                0,
                0
            );
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));
        when(adminScopeService.currentSaccoId(any(AppUserPrincipal.class))).thenReturn("SACCO-01");
        when(adminScopeService.currentStationId(any(AppUserPrincipal.class))).thenReturn("ST-1");
        when(smsUsageManagementService.loanUsageCriteria(
            "SACCO-01",
            "ST-1",
            fromDate,
            toDate,
            LoanStatus.READY_FOR_MANAGER,
            List.of(applicantId)
        )).thenReturn(criteria);
        when(smsUsageManagementService.loanUsageExport(criteria, "Workspace Admin")).thenReturn(report);
        when(loanReportService.buildSmsUsagePdf(report)).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/documents/reports/sms-usage.pdf")
                .param("saccoId", "OTHER")
                .param("stationId", "OTHER")
                .param("fromDate", "2026-09-01")
                .param("toDate", "2026-09-23")
                .param("loanStatus", "READY_FOR_MANAGER")
                .param("applicantIds", applicantId.toString())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(content().bytes(new byte[]{1, 2, 3}))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("sms-usage-2026-09-01-to-2026-09-23.pdf")));

        verify(smsUsageManagementService).loanUsageCriteria(
            "SACCO-01",
            "ST-1",
            fromDate,
            toDate,
            LoanStatus.READY_FOR_MANAGER,
            List.of(applicantId)
        );
        verify(smsUsageManagementService, never()).loanUsageCriteria(
            "OTHER",
            "OTHER",
            fromDate,
            toDate,
            LoanStatus.READY_FOR_MANAGER,
            List.of(applicantId)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "csv"})
    void retiredSmsUsageExportsAreDeniedEvenWithViewPermission(String extension) throws Exception {
        AppUserPrincipal principal = principal(Set.of(UserClaim.SMS_USAGE_VIEW));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));

        mockMvc.perform(get("/documents/reports/sms-usage." + extension)
                .param("saccoId", "OTHER")
                .param("stationId", "OTHER")
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isForbidden());

        verifyNoInteractions(smsUsageManagementService, loanReportService);
    }

    private UsernamePasswordAuthenticationToken authenticationFor(AppUserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
    }

    private AppUserPrincipal principal(Set<UserClaim> claims) {
        Member member = Member.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("STAFF-100")
            .staffNo("STAFF-100")
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

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean
        LoanDocumentController loanDocumentController(MemberDirectoryService memberDirectoryService,
                                                      SaccoRegistryService saccoRegistryService,
                                                      LoanPresentationService loanPresentationService,
                                                      LoanAttachmentService loanAttachmentService,
                                                      LoanReportService loanReportService,
                                                      MemberProfileImageService memberProfileImageService,
                                                      SaccoLogoStorageService saccoLogoStorageService,
                                                      AuditService auditService,
                                                      AccessControlService access,
                                                      AdminScopeService adminScopeService,
                                                      SmsUsageManagementService smsUsageManagementService) {
            return new LoanDocumentController(
                memberDirectoryService,
                saccoRegistryService,
                loanPresentationService,
                loanAttachmentService,
                loanReportService,
                memberProfileImageService,
                saccoLogoStorageService,
                auditService,
                access,
                adminScopeService,
                smsUsageManagementService
            );
        }

        @Bean
        SaccoAccessFilter saccoAccessFilter(SaccoStationRepository saccoStationRepository) {
            return new SaccoAccessFilter(new StationAccessService(saccoStationRepository));
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

        @Bean MemberRepository memberRepository() { return Mockito.mock(MemberRepository.class); }
        @Bean MemberDirectoryService memberDirectoryService(MemberRepository memberRepository) { return new MemberDirectoryService(memberRepository); }
        @Bean SaccoRegistryService saccoRegistryService() { return Mockito.mock(SaccoRegistryService.class); }
        @Bean LoanPresentationService loanPresentationService() { return Mockito.mock(LoanPresentationService.class); }
        @Bean LoanAttachmentService loanAttachmentService() { return Mockito.mock(LoanAttachmentService.class); }
        @Bean LoanReportService loanReportService() { return Mockito.mock(LoanReportService.class); }
        @Bean MemberProfileImageService memberProfileImageService() { return Mockito.mock(MemberProfileImageService.class); }
        @Bean SaccoLogoStorageService saccoLogoStorageService() { return Mockito.mock(SaccoLogoStorageService.class); }
        @Bean AuditService auditService() { return Mockito.mock(AuditService.class); }
        @Bean AdminScopeService adminScopeService() { return Mockito.mock(AdminScopeService.class); }
        @Bean SmsUsageManagementService smsUsageManagementService() { return Mockito.mock(SmsUsageManagementService.class); }
        @Bean SaccoStationRepository saccoStationRepository() { return Mockito.mock(SaccoStationRepository.class); }
        @Bean StationAccessService stationAccessService(SaccoStationRepository saccoStationRepository) { return new StationAccessService(saccoStationRepository); }
        @Bean StationOtpSettingsService stationOtpSettingsService() { return Mockito.mock(StationOtpSettingsService.class); }
        @Bean AppUsageAnalyticsService appUsageAnalyticsService() { return Mockito.mock(AppUsageAnalyticsService.class); }
        @Bean StaffMfaService staffMfaService() { return Mockito.mock(StaffMfaService.class); }
        @Bean UserClaimService userClaimService() { return Mockito.mock(UserClaimService.class); }
        @Bean AppUserDetailsService appUserDetailsService() { return Mockito.mock(AppUserDetailsService.class); }
        @Bean LoanApplicationRepository loanApplicationRepository() { return Mockito.mock(LoanApplicationRepository.class); }
        @Bean GuarantorRequestRepository guarantorRequestRepository() { return Mockito.mock(GuarantorRequestRepository.class); }
        @Bean BoardReviewRepository boardReviewRepository() { return Mockito.mock(BoardReviewRepository.class); }
        @Bean
        PlatformSessionSettingsService platformSessionSettingsService() {
            PlatformSessionSettingsService service = Mockito.mock(PlatformSessionSettingsService.class);
            when(service.policy()).thenReturn(new SessionTimeoutPolicy(30, 1_800_000L, 60_000L));
            return service;
        }
    }
}
