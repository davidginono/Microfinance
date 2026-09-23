package com.sacco.mvp.web;

import com.sacco.mvp.config.SecurityConfig;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.InterestMethod;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanProductStatus;
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
import com.sacco.mvp.service.MemberDirectoryService;
import com.sacco.mvp.service.NotificationInboxService;
import com.sacco.mvp.service.PlatformAdminService;
import com.sacco.mvp.service.PlatformEmailSettingsService;
import com.sacco.mvp.service.PlatformBrandingSettingsService;
import com.sacco.mvp.service.PlatformSessionSettingsService;
import com.sacco.mvp.service.PlatformSmsGatewaySettingsService;
import com.sacco.mvp.service.PlatformSupportContactSettingsService;
import com.sacco.mvp.service.SaccoDataDeletionService;
import com.sacco.mvp.service.SaccoRegistryService;
import com.sacco.mvp.service.SessionTimeoutPolicy;
import com.sacco.mvp.service.SmsUsageManagementService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mail.MailSendException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextListener;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringJUnitWebConfig(AdminControllerUserAccessSecurityTest.TestConfig.class)
class AdminControllerUserAccessSecurityTest {
    @Autowired private WebApplicationContext context;
    @Autowired private AdminService adminService;
    @Autowired private AdminScopeService adminScopeService;
    @Autowired private SaccoStationRepository saccoStationRepository;
    @Autowired private SaccoRegistryService saccoRegistryService;
    @Autowired private PlatformSessionSettingsService platformSessionSettingsService;
    @Autowired private PlatformEmailSettingsService platformEmailSettingsService;
    @Autowired private PlatformSmsGatewaySettingsService platformSmsGatewaySettingsService;
    @Autowired private SmsUsageManagementService smsUsageManagementService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(adminService, adminScopeService, saccoStationRepository, platformSessionSettingsService,
            platformEmailSettingsService, platformSmsGatewaySettingsService, saccoRegistryService, smsUsageManagementService);
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

    @Test
    void updateEmailSettingsAllowsPlatformAdminWithUpdateClaim() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE));

        mockMvc.perform(post("/admin/platform-settings/email")
                .param("enabled", "true")
                .param("host", "smtp.example.com")
                .param("port", "465")
                .param("username", "noreply@example.com")
                .param("password", "secret")
                .param("fromAddress", "noreply@example.com")
                .param("sslEnabled", "true")
                .param("connectionTimeoutMs", "10000")
                .param("readTimeoutMs", "10000")
                .param("writeTimeoutMs", "10000")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/platform-settings"));

        verify(platformEmailSettingsService).updateSettings(
            true,
            "smtp.example.com",
            465,
            "noreply@example.com",
            "secret",
            "noreply@example.com",
            null,
            true,
            false,
            10_000,
            10_000,
            10_000,
            principal.getMemberId()
        );
    }

    @Test
    void updateEmailSettingsDeniesPlatformAdminWithoutUpdateClaim() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_VIEW));

        mockMvc.perform(post("/admin/platform-settings/email")
                .param("host", "smtp.example.com")
                .param("port", "465")
                .param("connectionTimeoutMs", "10000")
                .param("readTimeoutMs", "10000")
                .param("writeTimeoutMs", "10000")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isForbidden());

        verify(platformEmailSettingsService, never()).updateSettings(
            any(Boolean.class), any(), any(Integer.class), any(), any(), any(), any(),
            any(Boolean.class), any(Boolean.class), any(Integer.class), any(Integer.class), any(Integer.class), any()
        );
    }

    @Test
    void updateSmsGatewaySettingsAllowsPlatformAdminWithUpdateClaim() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE));

        mockMvc.perform(post("/admin/platform-settings/sms-gateway")
                .param("enabled", "true")
                .param("baseUrl", "https://api.bentergroup.com")
                .param("sendPath", "/version2/messaging/legacy")
                .param("clientId", "foresight")
                .param("apiKey", "api-key")
                .param("senderId", "FORESIGHT")
                .param("connectTimeoutSeconds", "3")
                .param("readTimeoutSeconds", "8")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/platform-settings"));

        verify(platformSmsGatewaySettingsService).updateSettings(
            true,
            "https://api.bentergroup.com",
            "/version2/messaging/legacy",
            "foresight",
            "api-key",
            "FORESIGHT",
            3,
            8,
            principal.getMemberId()
        );
    }

    @Test
    void updateSmsGatewaySettingsDeniesPlatformAdminWithoutUpdateClaim() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_VIEW));

        mockMvc.perform(post("/admin/platform-settings/sms-gateway")
                .param("baseUrl", "https://api.bentergroup.com")
                .param("sendPath", "/version2/messaging/legacy")
                .param("connectTimeoutSeconds", "3")
                .param("readTimeoutSeconds", "8")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isForbidden());

        verify(platformSmsGatewaySettingsService, never()).updateSettings(
            any(Boolean.class), any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class), any()
        );
    }

    @Test
    void updateSaccoLoanTopUpAllowsPlatformAdminWithUpdateClaim() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE));

        mockMvc.perform(post("/admin/platform-settings/sacco-loan-top-up")
                .param("saccoId", "SACCO-01")
                .param("loanTopUpEnabled", "true")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/platform-settings"));

        verify(saccoRegistryService).updateLoanTopUpFeature("SACCO-01", true, principal.getMemberId());
    }

    @Test
    void updateSaccoLoanTopUpDeniesPlatformAdminWithoutUpdateClaim() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_VIEW));

        mockMvc.perform(post("/admin/platform-settings/sacco-loan-top-up")
                .param("saccoId", "SACCO-01")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isForbidden());

        verify(saccoRegistryService, never()).updateLoanTopUpFeature(any(), anyBoolean(), any());
    }

    @Test
    void createLoanProductSuccessClosesModalAndTargetsCreatedProductCard() throws Exception {
        UUID productId = UUID.randomUUID();
        AppUserPrincipal principal = principal(Set.of(UserClaim.LOAN_PRODUCTS_CREATE));
        LoanProductSetting createdProduct = LoanProductSetting.builder().id(productId).build();
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));
        when(adminScopeService.currentSaccoId(any(AppUserPrincipal.class))).thenReturn("SACCO-01");
        when(adminService.createLoanProduct(
            "SACCO-01", principal.getMemberId(), null, "School Fees Booster", "Created product", 5,
            new BigDecimal("0"), new BigDecimal("500000"), 2, new BigDecimal("3.000000"), false,
            new BigDecimal("15000"), new BigDecimal("0.015000"), new BigDecimal("0.000000"),
            new BigDecimal("0.100000"), InterestMethod.FLAT_RATE, 1, 12, false, false, true, false,
            ApprovalWorkflowStage.MANAGER, 1, 2, false, null, null, false, null, null, false, null,
            null, null, null, false, null, false, false, false, false, null, LoanProductStatus.ACTIVE
        )).thenReturn(createdProduct);

        mockMvc.perform(post("/admin/settings-controls/loan-products")
                .param("modalKey", "create-product")
                .param("productName", "School Fees Booster")
                .param("productDescription", "Created product")
                .param("displayOrder", "5")
                .param("minimumAmount", "0")
                .param("maximumAmount", "500000")
                .param("guarantorsRequired", "2")
                .param("maxLoanSavingsPercent", "300")
                .param("applicationFee", "15000")
                .param("insurancePercent", "1.5")
                .param("processingFeePercent", "0")
                .param("annualInterestPercent", "10")
                .param("minRepaymentMonths", "1")
                .param("maxRepaymentMonths", "12")
                .param("managerReviewRequired", "true")
                .param("managerPriority", "1")
                .param("accountantReviewRequired", "false")
                .param("disbursementOfficerRequired", "false")
                .param("disbursementProofRequired", "false")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/settings-controls?section=loan"))
            .andExpect(flash().attribute("message", "Loan product created successfully."))
            .andExpect(flash().attribute("createdLoanProductId", productId));
    }

    @Test
    void createLoanProductValidationErrorPreservesSubmittedDraft() throws Exception {
        AppUserPrincipal principal = principal(Set.of(UserClaim.LOAN_PRODUCTS_CREATE));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));

        mockMvc.perform(post("/admin/settings-controls/loan-products")
                .param("modalKey", "create-product")
                .param("productName", "School Fees Booster")
                .param("productDescription", "Submitted text should remain visible")
                .param("displayOrder", "5")
                .param("minimumAmount", "0")
                .param("maximumAmount", "500000")
                .param("guarantorsRequired", "2")
                .param("maxLoanSavingsPercent", "300")
                .param("applicationFee", "15000")
                .param("insurancePercent", "1.5")
                .param("processingFeePercent", "0")
                .param("annualInterestPercent", "10")
                .param("minRepaymentMonths", "1")
                .param("maxRepaymentMonths", "12")
                .param("managerReviewRequired", "true")
                .param("managerPriority", "0")
                .param("accountantReviewRequired", "false")
                .param("disbursementOfficerRequired", "false")
                .param("disbursementProofRequired", "false")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/settings-controls?section=loan&modal=create-product"))
            .andExpect(flash().attribute("loanProductFormDraft", org.hamcrest.Matchers.hasEntry("productName", List.of("School Fees Booster"))))
            .andExpect(flash().attribute("loanProductFormDraft", org.hamcrest.Matchers.hasEntry("productDescription", List.of("Submitted text should remain visible"))))
            .andExpect(flash().attribute("loanProductFormDraft", org.hamcrest.Matchers.hasEntry("committeeReviewRequired", List.of("false"))))
            .andExpect(flash().attribute("loanProductFormDraft", org.hamcrest.Matchers.hasEntry("accountantReviewRequired", List.of("false"))));
    }

    @Test
    void updateLoanProductValidationErrorPreservesSubmittedDraft() throws Exception {
        UUID productId = UUID.randomUUID();
        AppUserPrincipal principal = principal(Set.of(UserClaim.LOAN_PRODUCTS_UPDATE));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));

        mockMvc.perform(post("/admin/settings-controls/{id}", productId)
                .param("modalKey", "product-" + productId)
                .param("productName", "Emergency Loan")
                .param("productDescription", "Edited text should remain visible")
                .param("displayOrder", "2")
                .param("minimumAmount", "10000")
                .param("maximumAmount", "300000")
                .param("guarantorsRequired", "1")
                .param("maxLoanSavingsPercent", "250")
                .param("applicationFee", "10000")
                .param("insurancePercent", "1")
                .param("processingFeePercent", "0")
                .param("annualInterestPercent", "12")
                .param("minRepaymentMonths", "1")
                .param("maxRepaymentMonths", "6")
                .param("managerReviewRequired", "true")
                .param("managerPriority", "0")
                .param("accountantReviewRequired", "false")
                .param("disbursementOfficerRequired", "false")
                .param("disbursementProofRequired", "false")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/settings-controls/loan-products/" + productId + "/edit"))
            .andExpect(flash().attribute("loanProductFormDraft", org.hamcrest.Matchers.hasEntry("productName", List.of("Emergency Loan"))))
            .andExpect(flash().attribute("loanProductFormDraft", org.hamcrest.Matchers.hasEntry("productDescription", List.of("Edited text should remain visible"))))
            .andExpect(flash().attribute("loanProductFormDraft", org.hamcrest.Matchers.hasEntry("committeeReviewRequired", List.of("false"))))
            .andExpect(flash().attribute("loanProductFormDraft", org.hamcrest.Matchers.hasEntry("accountantReviewRequired", List.of("false"))));
    }

    @Test
    void sendTestEmailRedirectsWithSuccessFlash() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE));

        mockMvc.perform(post("/admin/platform-settings/email/test")
                .param("testRecipient", "admin@example.com")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/platform-settings"))
            .andExpect(flash().attribute("message", "Test email sent."))
            .andExpect(flash().attribute("error", org.hamcrest.Matchers.nullValue()));

        verify(platformEmailSettingsService).sendTestEmail("admin@example.com", principal.getMemberId());
    }

    @Test
    void sendTestEmailRedirectsWithErrorFlashWhenSmtpConnectionFails() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE));
        doThrow(new MailSendException(
            "Mail server connection failed. Couldn't connect to host, port: smtp.foresight.co.tz, 465; timeout 10000",
            new SocketTimeoutException("Connect timed out")
        )).when(platformEmailSettingsService).sendTestEmail(any(), any());

        mockMvc.perform(post("/admin/platform-settings/email/test")
                .param("testRecipient", "admin@example.com")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/platform-settings"))
            .andExpect(flash().attribute(
                "error",
                "Test email could not be sent. Connection to smtp.foresight.co.tz on port 465 timed out."
            ))
            .andExpect(flash().attribute("message", org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void sendTestSmsRedirectsWithSuccessFlash() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE));

        mockMvc.perform(post("/admin/platform-settings/sms-gateway/test")
                .param("testPhone", "0673054445")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/platform-settings"))
            .andExpect(flash().attribute("message", "Test SMS sent."))
            .andExpect(flash().attribute("error", org.hamcrest.Matchers.nullValue()));

        verify(platformSmsGatewaySettingsService).sendTestSms("0673054445", principal.getMemberId());
    }

    @Test
    void sendTestSmsRedirectsWithErrorFlashWhenGatewayFails() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE));
        doThrow(new RuntimeException("I/O error on POST request for \"https://api.example.com/send\""))
            .when(platformSmsGatewaySettingsService).sendTestSms(any(), any());

        mockMvc.perform(post("/admin/platform-settings/sms-gateway/test")
                .param("testPhone", "0673054445")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/platform-settings"))
            .andExpect(flash().attribute(
                "error",
                "Test SMS could not be sent. The SMS gateway could not be reached."
            ))
            .andExpect(flash().attribute("message", org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void smsApplicantSearchRequiresSmsUsageViewClaim() throws Exception {
        AppUserPrincipal principal = principal(Set.of(UserClaim.USER_ACCESS_VIEW));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));

        mockMvc.perform(get("/admin/sms-usage/applicants/search")
                .param("q", "ali")
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isForbidden());

        verify(smsUsageManagementService, never()).searchLoanUsageApplicants(any(), any(), any());
    }

    @Test
    void workspaceSmsApplicantSearchUsesCurrentScopeInsteadOfRequestedScope() throws Exception {
        AppUserPrincipal principal = principal(Set.of(UserClaim.SMS_USAGE_VIEW));
        UUID applicantId = UUID.randomUUID();
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));
        when(adminScopeService.currentSaccoId(any(AppUserPrincipal.class))).thenReturn("SACCO-01");
        when(adminScopeService.currentStationId(any(AppUserPrincipal.class))).thenReturn("ST-1");
        when(smsUsageManagementService.searchLoanUsageApplicants("SACCO-01", "ST-1", "ali"))
            .thenReturn(List.of(new SmsUsageManagementService.ApplicantFilterOption(
                applicantId,
                "Alice Member (MEM-1)",
                "MEM-1",
                "-",
                "SACCO-01",
                "ST-1"
            )));

        mockMvc.perform(get("/admin/sms-usage/applicants/search")
                .param("q", "ali")
                .param("saccoId", "OTHER")
                .param("stationId", "OTHER")
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isOk());

        verify(smsUsageManagementService).searchLoanUsageApplicants("SACCO-01", "ST-1", "ali");
        verify(smsUsageManagementService, never()).searchLoanUsageApplicants("OTHER", "OTHER", "ali");
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
                                        PlatformSupportContactSettingsService platformSupportContactSettingsService,
                                        PlatformEmailSettingsService platformEmailSettingsService,
                                        PlatformSmsGatewaySettingsService platformSmsGatewaySettingsService) {
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
                platformSupportContactSettingsService,
                platformEmailSettingsService,
                platformSmsGatewaySettingsService
            );
        }

        @Bean
        SaccoAccessFilter saccoAccessFilter(SaccoStationRepository saccoStationRepository) {
            return new SaccoAccessFilter(new com.sacco.mvp.service.StationAccessService(saccoStationRepository));
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
        @Bean PlatformEmailSettingsService platformEmailSettingsService() { return Mockito.mock(PlatformEmailSettingsService.class); }
        @Bean PlatformSmsGatewaySettingsService platformSmsGatewaySettingsService() { return Mockito.mock(PlatformSmsGatewaySettingsService.class); }
        @Bean MemberRepository memberRepository() { return Mockito.mock(MemberRepository.class); }
        @Bean SaccoStationRepository saccoStationRepository() { return Mockito.mock(SaccoStationRepository.class); }
        @Bean MemberDirectoryService memberDirectoryService(MemberRepository memberRepository) {
            return new MemberDirectoryService(memberRepository);
        }
        @Bean StationAccessService stationAccessService(SaccoStationRepository saccoStationRepository) {
            return new StationAccessService(saccoStationRepository);
        }
        @Bean StaffMfaService staffMfaService() { return Mockito.mock(StaffMfaService.class); }
        @Bean UserClaimService userClaimService() { return Mockito.mock(UserClaimService.class); }
        @Bean AppUserDetailsService appUserDetailsService() { return Mockito.mock(AppUserDetailsService.class); }
        @Bean AuditService auditService() { return Mockito.mock(AuditService.class); }
        @Bean LoanApplicationRepository loanApplicationRepository() { return Mockito.mock(LoanApplicationRepository.class); }
        @Bean GuarantorRequestRepository guarantorRequestRepository() { return Mockito.mock(GuarantorRequestRepository.class); }
        @Bean BoardReviewRepository boardReviewRepository() { return Mockito.mock(BoardReviewRepository.class); }
    }
}
