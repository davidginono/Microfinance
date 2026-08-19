package com.sacco.mvp.web;

import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.config.SecurityConfig;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoAccessStatus;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.security.AppUserDetailsService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.security.AuthzService;
import com.sacco.mvp.security.SaccoAccessFilter;
import com.sacco.mvp.service.AdminScopeService;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.AppUsageAnalyticsService;
import com.sacco.mvp.service.AuditService;
import com.sacco.mvp.service.LoanAnalyticsService;
import com.sacco.mvp.service.LoanProductDisplayService;
import com.sacco.mvp.service.LoanReportService;
import com.sacco.mvp.service.PlatformSessionSettingsService;
import com.sacco.mvp.service.SessionTimeoutPolicy;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringJUnitWebConfig(StaffAnalyticsControllerSecurityTest.TestConfig.class)
class StaffAnalyticsControllerSecurityTest {
    @Autowired private WebApplicationContext context;
    @Autowired private LoanAnalyticsService loanAnalyticsService;
    @Autowired private LoanReportService loanReportService;
    @Autowired private SaccoStationRepository saccoStationRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(loanAnalyticsService, loanReportService, saccoStationRepository);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    void managerCanReachStaffAnalytics() throws Exception {
        AppUserPrincipal principal = principal(Position.MANAGER, false, Set.of(UserClaim.STAFF_ANALYTICS_VIEW));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "AR704")).thenReturn(Optional.of(station(SaccoAccessStatus.ACTIVE)));
        when(loanAnalyticsService.forStation(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new LoanAnalyticsService.MemberLoanAnalytics(0, 0, 0, 0, 0, 0, BigDecimal.ZERO));
        when(loanAnalyticsService.statusTrendForStation(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(loanAnalyticsService.productPerformanceForStation(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(loanAnalyticsService.productChartSeries(any())).thenReturn(List.of());
        when(loanAnalyticsService.stationPortfolio(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new LoanAnalyticsService.StaffPortfolioSummary(0, 0, 0, 0, 0, BigDecimal.ZERO, "Low"));
        when(loanReportService.stationAnalyticsReport(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new LoanReportService.StationAnalyticsExportReport(
                "SACCO-01",
                "SACCO One",
                "AR704",
                java.time.LocalDate.now().minusYears(1),
                java.time.LocalDate.now(),
                null,
                "Test User",
                "MANAGER",
                java.time.LocalDate.now(),
                List.of(),
                new LoanReportService.StationParticipationSummary(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0),
                List.of(),
                List.of(),
                "All Products"
            ));

        mockMvc.perform(get("/staff/analytics")
                .param("fromDate", "2025-07-10")
                .param("toDate", "2025-07-11")
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isOk())
            .andExpect(view().name("staff/analytics"))
            .andExpect(model().attribute("fromDate", "10/07/2025"))
            .andExpect(model().attribute("toDate", "11/07/2025"))
            .andExpect(model().attribute("fromDateInput", "2025-07-10"))
            .andExpect(model().attribute("toDateInput", "2025-07-11"))
            .andExpect(model().attribute("viewAs", "staff"))
            .andExpect(model().attribute("stationWideStaffView", true))
            .andExpect(model().attribute("staffReviewView", false))
            .andExpect(model().attribute("staffAnalyticsTitle", "Station Loan Status"));

        verify(loanAnalyticsService).forStation(any(), any(), any(), any(), any(), any(), any());
        verify(loanAnalyticsService, never()).forStaff(any(), any(), any(), any(), any());
    }

    @Test
    void staffReportUsesReviewMetricsAndKeepsTheSameAnalyticsPage() throws Exception {
        AppUserPrincipal principal = principal(Position.MANAGER, false, Set.of(UserClaim.STAFF_ANALYTICS_VIEW));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "AR704")).thenReturn(Optional.of(station(SaccoAccessStatus.ACTIVE)));
        LoanAnalyticsService.StaffReviewAnalytics reviewAnalytics = new LoanAnalyticsService.StaffReviewAnalytics(
            4, 2, 1, 1, 2, 0, List.of(), List.of()
        );
        when(loanAnalyticsService.staffReviewAnalytics(any(), any(), any(), any(), any(), any())).thenReturn(reviewAnalytics);
        when(loanAnalyticsService.forStaff(any(), any(), any(), any(), any(), any()))
            .thenReturn(new LoanAnalyticsService.MemberLoanAnalytics(0, 0, 0, 4, 2, 1, BigDecimal.ZERO));
        when(loanAnalyticsService.staffPortfolio(any(), any(), any(), any(), any(), any()))
            .thenReturn(new LoanAnalyticsService.StaffPortfolioSummary(4, 2, 1, 2, 0, BigDecimal.ZERO, "Low"));
        when(loanAnalyticsService.staffReviewProductChartSeries(any())).thenReturn(List.of());

        mockMvc.perform(get("/staff/analytics")
                .param("viewAs", "member")
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isOk())
            .andExpect(view().name("staff/analytics"))
            .andExpect(model().attribute("viewAs", "member"))
            .andExpect(model().attribute("stationWideStaffView", false))
            .andExpect(model().attribute("staffReviewView", true))
            .andExpect(model().attribute("staffAnalyticsTitle", "Staff Loan Review Analytics"));

        verify(loanAnalyticsService).staffReviewAnalytics(any(), any(), any(), any(), any(), any());
        verify(loanAnalyticsService).forStaff(any(), any(), any(), any(), any(), any());
        verify(loanAnalyticsService, never()).forStation(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void memberCannotReachStaffAnalytics() throws Exception {
        AppUserPrincipal principal = principal(Position.MEMBER, true);
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "AR704")).thenReturn(Optional.of(station(SaccoAccessStatus.ACTIVE)));

        mockMvc.perform(get("/staff/analytics").with(authentication(authenticationFor(principal))))
            .andExpect(status().isForbidden());

        verify(loanAnalyticsService, never()).forMember(any(UUID.class), any(), any());
    }

    @Test
    void minorAdminCannotReachStaffAnalytics() throws Exception {
        AppUserPrincipal principal = principal(Position.MINOR_ADMIN, false);
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "AR704")).thenReturn(Optional.of(station(SaccoAccessStatus.ACTIVE)));

        mockMvc.perform(get("/staff/analytics").with(authentication(authenticationFor(principal))))
            .andExpect(status().isForbidden());

        verify(loanAnalyticsService, never()).forMember(any(UUID.class), any(), any());
    }

    @Test
    void suspendedStaffIsRedirectedBeforeAnalyticsRuns() throws Exception {
        AppUserPrincipal principal = principal(Position.MANAGER, false, Set.of(UserClaim.STAFF_ANALYTICS_VIEW));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "AR704")).thenReturn(Optional.of(station(SaccoAccessStatus.SUSPENDED)));

        mockMvc.perform(get("/staff/analytics").with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error"));

        verify(loanAnalyticsService, never()).forMember(any(UUID.class), any(), any());
    }

    private UsernamePasswordAuthenticationToken authenticationFor(AppUserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
    }

    private AppUserPrincipal principal(Position position, boolean memberAccess) {
        return principal(position, memberAccess, Collections.emptySet());
    }

    private AppUserPrincipal principal(Position position, boolean memberAccess, Set<UserClaim> claims) {
        LinkedHashSet<Position> staffRoles = new LinkedHashSet<>();
        if (position != null && position.isStaffRole()) {
            staffRoles.add(position);
        }
        Member member = Member.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .stationId("AR704")
            .memberNo(position.name())
            .fullName("Test User")
            .memberAccount(memberAccess)
            .status(MemberStatus.ACTIVE)
            .position(position)
            .staffRoles(staffRoles)
            .passwordHash("x")
            .createdAt(OffsetDateTime.now())
            .build();
        return new AppUserPrincipal(member, claims);
    }

    private SaccoStation station(SaccoAccessStatus accessStatus) {
        return SaccoStation.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .stationId("AR704")
            .active(true)
            .accessStatus(accessStatus)
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
        StaffAnalyticsController staffAnalyticsController(LoanAnalyticsService loanAnalyticsService,
                                                          LoanReportService loanReportService,
                                                          ObjectMapper objectMapper,
                                                          LoanProductDisplayService loanProductDisplayService) {
            return new StaffAnalyticsController(loanAnalyticsService, loanReportService, objectMapper, new ApplicationClock("Africa/Nairobi"), loanProductDisplayService);
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

        @Bean
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

        @Bean LoanAnalyticsService loanAnalyticsService() { return Mockito.mock(LoanAnalyticsService.class); }
        @Bean LoanReportService loanReportService() { return Mockito.mock(LoanReportService.class); }
        @Bean LoanProductDisplayService loanProductDisplayService() { return Mockito.mock(LoanProductDisplayService.class); }
        @Bean AppUsageAnalyticsService appUsageAnalyticsService() { return Mockito.mock(AppUsageAnalyticsService.class); }
        @Bean
        PlatformSessionSettingsService platformSessionSettingsService() {
            PlatformSessionSettingsService service = Mockito.mock(PlatformSessionSettingsService.class);
            when(service.policy()).thenReturn(new SessionTimeoutPolicy(30, 1_800_000L, 60_000L));
            return service;
        }
        @Bean AuditService auditService() { return Mockito.mock(AuditService.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean MemberRepository memberRepository() { return Mockito.mock(MemberRepository.class); }
        @Bean SaccoStationRepository saccoStationRepository() { return Mockito.mock(SaccoStationRepository.class); }
        @Bean AdminScopeService adminScopeService() { return Mockito.mock(AdminScopeService.class); }
        @Bean StaffMfaService staffMfaService() { return Mockito.mock(StaffMfaService.class); }
        @Bean StationOtpSettingsService stationOtpSettingsService() { return Mockito.mock(StationOtpSettingsService.class); }
        @Bean UserClaimService userClaimService() { return Mockito.mock(UserClaimService.class); }
        @Bean AppUserDetailsService appUserDetailsService() { return Mockito.mock(AppUserDetailsService.class); }
        @Bean LoanApplicationRepository loanApplicationRepository() { return Mockito.mock(LoanApplicationRepository.class); }
        @Bean LoanProductSettingRepository loanProductSettingRepository() { return Mockito.mock(LoanProductSettingRepository.class); }
        @Bean GuarantorRequestRepository guarantorRequestRepository() { return Mockito.mock(GuarantorRequestRepository.class); }
        @Bean BoardReviewRepository boardReviewRepository() { return Mockito.mock(BoardReviewRepository.class); }
    }
}
