package com.sacco.mvp.web;

import com.sacco.mvp.config.SecurityConfig;
import com.sacco.mvp.domain.InterestMethod;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanProductStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.OtpDeliveryChannel;
import com.sacco.mvp.domain.PlatformSessionSettings;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoAccessStatus;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.domain.StaffAccessStatus;
import com.sacco.mvp.domain.StationSmsAccount;
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
import org.springframework.http.MediaType;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringJUnitWebConfig(AdminControllerUserAccessSecurityTest.TestConfig.class)
class AdminControllerUserAccessSecurityTest {
    @Test void contactChecksRequireCreatePermissionAndCsrfAndIgnoreSubmittedScope() throws Exception {
        var actor = principal(Set.of(UserClaim.USER_ACCESS_VIEW, UserClaim.USER_ACCESS_CREATE));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));
        String url = "/admin/users/contact-availability";
        mockMvc.perform(post(url).with(authentication(authenticationFor(actor))).param("field", "email").param("value", "asha@example.invalid"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post(url).with(authentication(authenticationFor(principal(Set.of(UserClaim.USER_ACCESS_VIEW))))).with(csrf())
                .param("field", "email").param("value", "asha@example.invalid"))
            .andExpect(status().isForbidden());
        verify(adminService, never()).staffContactAvailable(any(), any(), any());
        when(adminService.staffContactAvailable(actor, "email", "asha@example.invalid")).thenReturn(true);
        mockMvc.perform(post(url).with(authentication(authenticationFor(actor))).with(csrf()).accept(MediaType.APPLICATION_JSON)
                .param("field", "email").param("value", "asha@example.invalid").param("saccoId", "OTHER").param("stationId", "OTHER"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("{\"available\":true}"));
        verify(adminService).staffContactAvailable(actor, "email", "asha@example.invalid");
        when(adminService.staffContactAvailable(actor, "phone", "bad")).thenThrow(new IllegalArgumentException("staffRegistration.phoneInvalid"));
        mockMvc.perform(post(url).with(authentication(authenticationFor(actor))).with(csrf()).accept(MediaType.APPLICATION_JSON)
                .param("field", "phone").param("value", "bad"))
            .andExpect(status().isBadRequest())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("{}"));
    }

    @Test void staffRegistrationFailureKeepsEnteredValuesAndGroupChoice() throws Exception {
        var actor = principal(Set.of(UserClaim.USER_ACCESS_VIEW, UserClaim.USER_ACCESS_CREATE));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));
        when(adminScopeService.currentSaccoId(actor)).thenReturn("SACCO-01");
        when(adminScopeService.currentStationId(actor)).thenReturn("ST-1");
        when(adminService.createBranchUser(eq(actor), isNull(),
            eq("Asha Mushi"), eq("asha@example.invalid"), eq("255712345678"), eq(List.of(Position.CREDIT_OFFICER))))
            .thenThrow(new IllegalStateException("staffRegistration.nameInvalid"));
        mockMvc.perform(post("/admin/users").with(authentication(authenticationFor(actor))).with(csrf())
                .param("fullName", "Asha Mushi").param("email", "asha@example.invalid").param("phone", "255712345678")
                .param("positions", "CREDIT_OFFICER"))
            .andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attribute("staffRegistrationValues", Map.of("fullName", "Asha Mushi", "email", "asha@example.invalid",
                "phone", "255712345678", "positions", List.of(Position.CREDIT_OFFICER), "branch", "")))
            .andExpect(flash().attribute("error", "Enter three names using letters, up to 120 characters."));
    }

    @Autowired private WebApplicationContext context;
    @Autowired private AdminService adminService;
    @Autowired private com.sacco.mvp.accounting.loans.service.LoanProductAccountService productAccounts;
    @Autowired private com.sacco.mvp.accounting.charges.service.LoanChargeService loanCharges;
    @Autowired private AdminScopeService adminScopeService;
    @Autowired private SaccoStationRepository saccoStationRepository;
    @Autowired private SaccoRegistryService saccoRegistryService;
    @Autowired private PlatformSessionSettingsService platformSessionSettingsService;
    @Autowired private PlatformEmailSettingsService platformEmailSettingsService;
    @Autowired private PlatformSmsGatewaySettingsService platformSmsGatewaySettingsService;
    @Autowired private SmsUsageManagementService smsUsageManagementService;
    @Autowired private StationOtpSettingsService stationOtpSettingsService;

    @Autowired private com.sacco.mvp.accounting.loans.service.LoanGroupApprovalService groupApprovals;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(loanCharges,groupApprovals,productAccounts,adminService, adminScopeService, saccoStationRepository, platformSessionSettingsService,
            platformEmailSettingsService, platformSmsGatewaySettingsService, saccoRegistryService, smsUsageManagementService,
            stationOtpSettingsService);
        when(loanCharges.editor(any(),any(),any())).thenReturn(new com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Editor(new com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.PolicyForm(),null,Map.of(),List.of(),Map.of()));
        when(loanCharges.accountEditor(any(),any(),any())).thenReturn(new com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Editor(new com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.PolicyForm(),null,Map.of(),List.of(),Map.of()));
        when(platformSessionSettingsService.policy()).thenReturn(new SessionTimeoutPolicy(30, 1_800_000L, 60_000L, false));
        when(platformSessionSettingsService.settings()).thenReturn(platformSessionSettings(30));
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test void institutionAdminDashboardRedirectsToThePersonalDashboardWithoutReadingOverviewData() throws Exception {
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01","ST-1")).thenReturn(Optional.of(station()));
        mockMvc.perform(get("/admin/dashboard").with(authentication(authenticationFor(principal(Set.of(UserClaim.ADMIN_DASHBOARD_VIEW))))))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/dashboard"));
        Mockito.verifyNoInteractions(adminService);
        mockMvc.perform(get("/admin/dashboard").with(authentication(authenticationFor(principal(Set.of(UserClaim.USER_ACCESS_VIEW))))))
            .andExpect(status().isForbidden());
    }

    @Test void productAccountLookupRequiresProductConfigurationClaimsAndUsesSelectedInstitution() throws Exception {
        var actor=principal(Set.of(UserClaim.LOAN_PRODUCTS_UPDATE));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01","ST-1")).thenReturn(Optional.of(station()));
        when(adminScopeService.currentSaccoId(any())).thenReturn("SACCO-01");
        mockMvc.perform(get("/admin/settings-controls/loan-product-accounts/INTEREST").param("search","Maendeleo").param("saccoId","FOREIGN").with(authentication(authenticationFor(actor)))).andExpect(status().isOk());
        verify(productAccounts).choices("SACCO-01","INTEREST","Maendeleo",0,null);
        mockMvc.perform(get("/admin/settings-controls/loan-product-accounts/INTEREST").with(authentication(authenticationFor(principal(Set.of(UserClaim.LOAN_PRODUCTS_VIEW)))))).andExpect(status().isForbidden());
        Mockito.verifyNoMoreInteractions(productAccounts);
    }

    @Test void productViewerSeesChargeMappingsWithoutGettingTheChargeEditor() throws Exception {
        var actor=principal(Set.of(UserClaim.LOAN_PRODUCTS_VIEW));var id=UUID.randomUUID();
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01","ST-1")).thenReturn(Optional.of(station()));
        when(adminScopeService.currentSaccoId(actor)).thenReturn("SACCO-01");
        when(adminService.loanProduct("SACCO-01",id)).thenReturn(LoanProductSetting.builder().id(id).build());
        var mappings=Map.of("APPLICATION","512001 - Application fee","PROCESSING","513001 - Processing fee");
        when(loanCharges.accountSummary(actor,id)).thenReturn(new com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.AccountSummary(null,List.of(),mappings));
        mockMvc.perform(get("/admin/settings-controls/loan-products/"+id+"/edit").param("saccoId","FOREIGN").with(authentication(authenticationFor(actor))))
            .andExpect(status().isOk()).andExpect(view().name("admin/loan-product-edit"))
            .andExpect(model().attribute("currentChargeMappings",mappings)).andExpect(model().attributeDoesNotExist("chargePolicy","chargeAccounts"));
        verify(loanCharges).accountSummary(actor,id);verify(loanCharges,never()).editor(any(),any(),any());
        doThrow(new org.springframework.security.access.AccessDeniedException("Configure required")).when(loanCharges).saveAccountMappings(eq(actor),any());
        mockMvc.perform(post("/admin/settings-controls/loan-products/"+id+"/charge-accounts").with(csrf()).with(authentication(authenticationFor(actor))))
            .andExpect(status().isForbidden());
    }

    @Test void approvalOnlyViewerDoesNotReceiveChargeAccountData() throws Exception {
        var actor=principal(Set.of(UserClaim.APPROVAL_FLOW_VIEW));var id=UUID.randomUUID();
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01","ST-1")).thenReturn(Optional.of(station()));
        when(adminScopeService.currentSaccoId(actor)).thenReturn("SACCO-01");
        when(adminService.loanProduct("SACCO-01",id)).thenReturn(LoanProductSetting.builder().id(id).build());
        mockMvc.perform(get("/admin/settings-controls/loan-products/"+id+"/edit").with(authentication(authenticationFor(actor))))
            .andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("currentChargeMappings","generatedChargeAccounts","chargePolicy"));
        Mockito.verifyNoInteractions(loanCharges);
    }

    @Test void chargeConfigurerKeepsTheExistingIndependentEditor() throws Exception {
        var actor=principal(Set.of(UserClaim.LOAN_PRODUCTS_CONFIGURE,UserClaim.ACCOUNTING_ACCOUNTS_UPDATE));var id=UUID.randomUUID();
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01","ST-1")).thenReturn(Optional.of(station()));
        when(adminScopeService.currentSaccoId(actor)).thenReturn("SACCO-01");
        when(adminService.loanProduct("SACCO-01",id)).thenReturn(LoanProductSetting.builder().id(id).build());
        mockMvc.perform(get("/admin/settings-controls/loan-products/"+id+"/edit").with(authentication(authenticationFor(actor))))
            .andExpect(status().isOk()).andExpect(model().attributeExists("chargePolicy","chargeAccounts"));
        verify(loanCharges).accountEditor(actor,id,null);verify(loanCharges,never()).accountSummary(any(),any());
    }

    @Test void productWorkflowUsesThePathProductAndRequiresExplicitClaimsAndCsrf() throws Exception {
        var actor=principal(Set.of(UserClaim.APPROVAL_FLOW_VIEW,UserClaim.APPROVAL_FLOW_CONFIGURE));
        var product=UUID.randomUUID();var url="/admin/settings-controls/loan-products/"+product+"/approval";
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01","ST-1")).thenReturn(Optional.of(station()));
        mockMvc.perform(post(url).with(authentication(authenticationFor(actor)))).andExpect(status().isForbidden());
        mockMvc.perform(post(url).with(authentication(authenticationFor(principal(Set.of(UserClaim.APPROVAL_FLOW_VIEW))))).with(csrf()))
            .andExpect(status().isForbidden());
        verify(groupApprovals,never()).saveRule(any(),any());
        mockMvc.perform(post(url).with(authentication(authenticationFor(actor))).with(csrf())
            .param("productId",UUID.randomUUID().toString()).param("expectedRevision","3")
            .param("groups[0]","CREDIT_OFFICER").param("approvals[0]","2"))
            .andExpect(redirectedUrl("/admin/settings-controls/loan-products/"+product+"/edit?tab=workflow"));
        verify(groupApprovals).saveRule(eq(actor),org.mockito.ArgumentMatchers.argThat(f -> product.equals(f.getProductId())
            && f.getExpectedRevision()==3 && f.getGroups().equals(List.of(Position.CREDIT_OFFICER)) && f.getApprovals().equals(List.of(2))));
        verify(adminService,never()).loanProduct(any(),any());
    }

    @Test void invalidWorkflowReturnsToTheProductWithItsEnteredGroups() throws Exception {
        var actor=principal(Set.of(UserClaim.APPROVAL_FLOW_VIEW,UserClaim.APPROVAL_FLOW_CONFIGURE));var id=UUID.randomUUID();
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01","ST-1")).thenReturn(Optional.of(station()));
        doThrow(new IllegalArgumentException("loanGroups.error.changed")).when(groupApprovals).saveRule(eq(actor),any());
        mockMvc.perform(post("/admin/settings-controls/loan-products/"+id+"/approval").with(authentication(authenticationFor(actor))).with(csrf())
            .param("groups[0]","ACCOUNTANT").param("approvals[0]","2").param("expectedRevision","1"))
            .andExpect(redirectedUrl("/admin/settings-controls/loan-products/"+id+"/edit?tab=workflow"))
            .andExpect(flash().attribute("groupError","loanGroups.error.changed")).andExpect(flash().attributeExists("groupRuleForm"));
    }

    @Test void customWorkflowSavesWithoutProductCreationPermissionsAndRequiresCsrf() throws Exception {
        var actor=principal(Set.of(UserClaim.APPROVAL_FLOW_VIEW,UserClaim.APPROVAL_FLOW_CONFIGURE));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01","ST-1")).thenReturn(Optional.of(station()));
        var url="/admin/settings-controls/custom-loan/approval";
        mockMvc.perform(post(url).with(authentication(authenticationFor(actor)))).andExpect(status().isForbidden());
        mockMvc.perform(post(url).with(authentication(authenticationFor(principal(Set.of(UserClaim.APPROVAL_FLOW_VIEW))))).with(csrf())).andExpect(status().isForbidden());
        verify(groupApprovals,never()).saveCustomRule(any(),any());
        mockMvc.perform(post(url).with(authentication(authenticationFor(actor))).with(csrf())
            .param("productId",UUID.randomUUID().toString()).param("groups[0]","CREDIT_OFFICER").param("approvals[0]","2"))
            .andExpect(redirectedUrl("/admin/settings-controls?section=loan&custom=true"));
        verify(groupApprovals).saveCustomRule(eq(actor),org.mockito.ArgumentMatchers.argThat(f -> f.getProductId()==null && f.getGroups().equals(List.of(Position.CREDIT_OFFICER))));
        Mockito.verifyNoInteractions(adminService);
    }

    @Test void customWorkflowOpensWithoutLoadingOrCreatingProducts() throws Exception {
        var actor=principal(Set.of(UserClaim.APPROVAL_FLOW_VIEW));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01","ST-1")).thenReturn(Optional.of(station()));
        when(groupApprovals.customRule(actor)).thenReturn(new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.GroupRuleForm());
        mockMvc.perform(get("/admin/settings-controls").param("section","loan").param("custom","true").with(authentication(authenticationFor(actor))))
            .andExpect(status().isOk()).andExpect(model().attributeExists("groupRuleForm","userGroups"));
        Mockito.verifyNoInteractions(adminService);
    }

    @Test
    void resendStaffInviteRequiresUpdateAndCsrfAndUsesAuthenticatedScope() throws Exception {
        UUID id = UUID.randomUUID();
        var actor = principal(Set.of(UserClaim.USER_ACCESS_VIEW, UserClaim.USER_ACCESS_UPDATE));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));
        when(adminScopeService.currentSaccoId(any())).thenReturn("SACCO-01");
        when(adminScopeService.currentStationId(any())).thenReturn("ST-1");
        mockMvc.perform(post("/admin/users/{id}/resend-invite", id).with(authentication(authenticationFor(actor))).with(csrf())
                .param("saccoId", "FOREIGN").param("stationId", "FOREIGN").param("returnToList", "true"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attributeExists("message"));
        verify(adminService).resendStaffInvitation("SACCO-01", "ST-1", actor.getMemberId(), actor.getGrantedPositions(), id);
        Mockito.clearInvocations(adminService);
        mockMvc.perform(post("/admin/users/{id}/resend-invite", id).with(authentication(authenticationFor(actor))))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/users/{id}/resend-invite", id)
                .with(authentication(authenticationFor(principal(Set.of(UserClaim.USER_ACCESS_VIEW))))).with(csrf()))
            .andExpect(status().isForbidden());
        Mockito.verifyNoInteractions(adminService);
    }

    @Test
    void failedStaffResendShowsErrorAndReturnsToAccount() throws Exception {
        UUID id = UUID.randomUUID();
        var actor = principal(Set.of(UserClaim.USER_ACCESS_VIEW, UserClaim.USER_ACCESS_UPDATE));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));
        when(adminScopeService.currentSaccoId(any())).thenReturn("SACCO-01");
        when(adminScopeService.currentStationId(any())).thenReturn("ST-1");
        doThrow(new IllegalStateException("admin.users.invite.ineligible")).when(adminService)
            .resendStaffInvitation("SACCO-01", "ST-1", actor.getMemberId(), actor.getGrantedPositions(), id);
        mockMvc.perform(post("/admin/users/{id}/resend-invite", id).with(authentication(authenticationFor(actor))).with(csrf()))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/users/" + id + "/edit"))
            .andExpect(flash().attributeExists("error"));
    }

    @Test
    void editUserPageAllowsWorkspaceAdminWithUpdateClaim() throws Exception {
        UUID accountId = UUID.randomUUID();
        AppUserPrincipal principal = principal(Set.of(UserClaim.USER_ACCESS_VIEW, UserClaim.USER_ACCESS_UPDATE));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));
        when(adminScopeService.currentSaccoId(any(AppUserPrincipal.class))).thenReturn("SACCO-01");
        when(adminScopeService.currentStationId(any(AppUserPrincipal.class))).thenReturn("ST-1");
        when(adminService.staffBranch(eq(principal),any(),isNull())).thenReturn("ST-1");
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
        when(adminService.staffBranch(eq(principal),any(),isNull())).thenReturn("ST-1");
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
    void platformCreatePagesRequireTheirExplicitClaimsAndDoNotLoadTheRegisters() throws Exception {
        var actor = platformPrincipal(Set.of(UserClaim.SACCO_REGISTRY_VIEW, UserClaim.SACCO_REGISTRY_CREATE,
            UserClaim.USER_ACCESS_VIEW, UserClaim.USER_ACCESS_CREATE));
        mockMvc.perform(get("/admin/saccos/new").with(authentication(authenticationFor(actor))))
            .andExpect(status().isOk()).andExpect(view().name("admin/sacco-registry"))
            .andExpect(model().attribute("createMode", true));
        mockMvc.perform(get("/admin/saccos/minor-admins/new").with(authentication(authenticationFor(actor))))
            .andExpect(status().isOk()).andExpect(view().name("admin/minor-admins"))
            .andExpect(model().attribute("createMode", true));
        verify(adminService, never()).minorAdmins();
        var viewer = platformPrincipal(Set.of(UserClaim.SACCO_REGISTRY_VIEW, UserClaim.USER_ACCESS_VIEW));
        mockMvc.perform(get("/admin/saccos/new").with(authentication(authenticationFor(viewer))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/saccos/minor-admins/new").with(authentication(authenticationFor(viewer))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/saccos/new").with(authentication(authenticationFor(principal(Set.of(UserClaim.SACCO_REGISTRY_CREATE, UserClaim.SACCO_REGISTRY_VIEW))))))
            .andExpect(status().isForbidden());
    }

    @Test
    void failedInstitutionCreationReturnsToItsFormWithEnteredValuesAndStillRequiresCsrf() throws Exception {
        var actor = platformPrincipal(Set.of(UserClaim.SACCO_REGISTRY_CREATE, UserClaim.SACCO_REGISTRY_VIEW));
        doThrow(new IllegalArgumentException("Branch ID is already used"))
            .when(saccoRegistryService).registerSacco("Test institution", "BRANCH-1", (org.springframework.web.multipart.MultipartFile) null);
        mockMvc.perform(post("/admin/saccos").param("saccoName", "Test institution").param("stationIds", "BRANCH-1")
                .with(csrf()).with(authentication(authenticationFor(actor))))
            .andExpect(redirectedUrl("/admin/saccos/new"))
            .andExpect(flash().attribute("saccoNameDraft", "Test institution"))
            .andExpect(flash().attribute("stationIdsDraft", "BRANCH-1"))
            .andExpect(flash().attribute("error", "Branch ID is already used"));
        mockMvc.perform(post("/admin/saccos").param("saccoName", "Test institution").param("stationIds", "BRANCH-1")
                .with(authentication(authenticationFor(actor))))
            .andExpect(status().isForbidden());
    }

    @Test
    void failedAdminCreationReturnsToCreationWithItsBoundForm() throws Exception {
        var actor = platformPrincipal(Set.of(UserClaim.USER_ACCESS_CREATE, UserClaim.USER_ACCESS_VIEW));
        doThrow(new IllegalArgumentException("Branch already has an admin"))
            .when(adminService).registerMinorAdmin(actor.getMemberId(), "SACCO-01", "ST-1", "TEST ADMIN", "admin@example.test", "255712345678");
        mockMvc.perform(post("/admin/saccos/minor-admins").param("saccoId", "SACCO-01").param("stationId", "ST-1")
                .param("fullName", "TEST ADMIN").param("email", "admin@example.test").param("phone", "255712345678")
                .with(csrf()).with(authentication(authenticationFor(actor))))
            .andExpect(redirectedUrl("/admin/saccos/minor-admins/new"))
            .andExpect(flash().attributeExists("registrationForm"))
            .andExpect(flash().attribute("error", "Branch already has an admin"));
    }

    @Test
    void platformSettingsDeniesPlatformAdminWithoutViewClaim() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.ADMIN_DASHBOARD_VIEW));

        mockMvc.perform(get("/admin/platform-settings")
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isForbidden());
    }

    @Test
    void platformSettingsEditorsAreSeparateTasksWithExplicitClaims() throws Exception {
        var editor = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_VIEW, UserClaim.PLATFORM_SETTINGS_UPDATE));
        for (String section : List.of("session-timeout", "support-contact", "email", "sms-gateway", "logo-policy")) {
            mockMvc.perform(get("/admin/platform-settings/" + section + "/edit").with(authentication(authenticationFor(editor))))
                .andExpect(status().isOk()).andExpect(view().name("admin/platform-settings-edit"))
                .andExpect(model().attribute("settingsSection", section));
        }
        mockMvc.perform(get("/admin/platform-settings/email/test").with(authentication(authenticationFor(editor))))
            .andExpect(status().isOk()).andExpect(view().name("admin/platform-settings-test"));
        var viewer = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_VIEW));
        mockMvc.perform(get("/admin/platform-settings/email/edit").with(authentication(authenticationFor(viewer))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/platform-settings/email/test").with(authentication(authenticationFor(viewer))))
            .andExpect(status().isForbidden());
        verify(saccoRegistryService, never()).listRegisteredSaccosFresh();
    }

    @Test
    void rejectedEmailEditPreservesOnlyNonSecretDraftFields() throws Exception {
        var editor = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_VIEW, UserClaim.PLATFORM_SETTINGS_UPDATE));
        doThrow(new IllegalArgumentException("Enter SMTP host"))
            .when(platformEmailSettingsService).updateSettings(true, "", 465, "test-user", "synthetic-secret", "from@example.test", null, false, false, 10000, 10000, 10000, editor.getMemberId());
        mockMvc.perform(post("/admin/platform-settings/email").with(csrf()).with(authentication(authenticationFor(editor)))
                .param("enabled", "true").param("host", "").param("port", "465").param("username", "test-user")
                .param("password", "synthetic-secret").param("fromAddress", "from@example.test")
                .param("connectionTimeoutMs", "10000").param("readTimeoutMs", "10000").param("writeTimeoutMs", "10000"))
            .andExpect(redirectedUrl("/admin/platform-settings/email/edit"))
            .andExpect(result -> {
                @SuppressWarnings("unchecked") var draft = (java.util.Map<String, String>) result.getFlashMap().get("platformSettingsDraft");
                org.assertj.core.api.Assertions.assertThat(draft).containsEntry("username", "test-user").containsEntry("enabled", "true")
                    .doesNotContainKeys("password", "apiKey", "_csrf");
            });
    }

    @Test
    void topUpSettingsReadOnlyRegisterAndEditorKeepSeparateClaimsAndMissingRecordHandling() throws Exception {
        var viewer = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_VIEW));
        var row = new SaccoRegistryService.LoanTopUpAccessView("INST-1", "Example institution", true);
        when(saccoRegistryService.loanTopUpAccessPage(viewer, "Example", 0, 25)).thenReturn(new PageImpl<>(List.of(row)));
        mockMvc.perform(get("/admin/platform-settings/loan-top-up").param("q", "Example").with(authentication(authenticationFor(viewer))))
            .andExpect(status().isOk()).andExpect(view().name("admin/platform-settings-top-up"));
        mockMvc.perform(get("/admin/platform-settings/loan-top-up/INST-1/edit").with(authentication(authenticationFor(viewer))))
            .andExpect(status().isForbidden());
        var editor = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_VIEW, UserClaim.PLATFORM_SETTINGS_UPDATE));
        when(saccoRegistryService.loanTopUpAccess(editor, "INST-1")).thenReturn(Optional.of(row));
        mockMvc.perform(get("/admin/platform-settings/loan-top-up/INST-1/edit").with(authentication(authenticationFor(editor))))
            .andExpect(status().isOk()).andExpect(view().name("admin/platform-settings-edit"));
        when(saccoRegistryService.loanTopUpAccess(editor, "MISSING")).thenReturn(Optional.empty());
        mockMvc.perform(get("/admin/platform-settings/loan-top-up/MISSING/edit").with(authentication(authenticationFor(editor))))
            .andExpect(status().isNotFound());
    }

    @Test
    void replacingAnAdminHasItsOwnTaskAndRequiresBothCreateAndUpdateClaims() throws Exception {
        var creator = platformPrincipal(Set.of(UserClaim.USER_ACCESS_VIEW, UserClaim.USER_ACCESS_CREATE));
        mockMvc.perform(get("/admin/saccos/minor-admins/change").with(authentication(authenticationFor(creator))))
            .andExpect(status().isForbidden());
        var editor = platformPrincipal(Set.of(UserClaim.USER_ACCESS_VIEW, UserClaim.USER_ACCESS_CREATE, UserClaim.USER_ACCESS_UPDATE));
        mockMvc.perform(get("/admin/saccos/minor-admins/change").with(authentication(authenticationFor(editor))))
            .andExpect(status().isOk()).andExpect(model().attribute("changeAdmin", true)).andExpect(view().name("admin/minor-admins"));
    }

    @Test
    void topUpSaveReturnsToItsBoundedRegisterContext() throws Exception {
        var editor = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE));
        mockMvc.perform(post("/admin/platform-settings/sacco-loan-top-up").with(csrf()).with(authentication(authenticationFor(editor)))
                .param("saccoId", "INST-1").param("loanTopUpEnabled", "true").param("returnQuery", "Alpha")
                .param("returnPage", "2").param("returnSize", "25"))
            .andExpect(redirectedUrl("/admin/platform-settings/loan-top-up?q=Alpha&page=2&size=25"));
    }

    @Test
    void updateSessionTimeoutAllowsPlatformAdminWithUpdateClaim() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE));

        mockMvc.perform(post("/admin/platform-settings/session-timeout")
                .param("timeoutMinutes", "45")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/platform-settings#session-timeout"));

        verify(platformSessionSettingsService).updateTimeout(45, false, principal.getMemberId());
    }

    @Test
    void updateSessionTimeoutDeniesPlatformAdminWithoutUpdateClaim() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_VIEW));

        mockMvc.perform(post("/admin/platform-settings/session-timeout")
                .param("timeoutMinutes", "45")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isForbidden());

        verify(platformSessionSettingsService, never()).updateTimeout(any(Integer.class), anyBoolean(), any());
    }

    @Test
    void updateSessionTimeoutSavesEnabledWarningAndRequiresCsrf() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE));
        mockMvc.perform(post("/admin/platform-settings/session-timeout")
                .param("timeoutMinutes", "45").param("warningEnabled", "true")
                .with(csrf()).with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/platform-settings#session-timeout"));
        verify(platformSessionSettingsService).updateTimeout(45, true, principal.getMemberId());

        mockMvc.perform(post("/admin/platform-settings/session-timeout")
                .param("timeoutMinutes", "60").param("warningEnabled", "false")
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isForbidden());
        verify(platformSessionSettingsService, never()).updateTimeout(60, false, principal.getMemberId());
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
            .andExpect(redirectedUrl("/admin/platform-settings#email"));

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
                .param("sendPath", "/version1/messaging/bulk")
                .param("clientId", "microfinance")
                .param("apiKey", "api-key")
                .param("senderId", "MFI")
                .param("connectTimeoutSeconds", "3")
                .param("readTimeoutSeconds", "8")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/platform-settings#sms-gateway"));

        verify(platformSmsGatewaySettingsService).updateSettings(
            true,
            "https://api.bentergroup.com",
            "/version1/messaging/bulk",
            "microfinance",
            "api-key",
            "MFI",
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
                .param("sendPath", "/version1/messaging/bulk")
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
            .andExpect(redirectedUrl("/admin/platform-settings/loan-top-up"));

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

    private String productRatioParameterFromForm() throws Exception {
        String form = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/webapp/WEB-INF/jsp/fragments/loan-product-eligibility-fields.jspf"));
        java.util.regex.Matcher field = java.util.regex.Pattern.compile(
            "<input name=\"([^\"]+)\"[^>]*data-savings-percent").matcher(form);
        org.assertj.core.api.Assertions.assertThat(field.find()).isTrue();
        return field.group(1);
    }

    @Test
    void createLoanProductWithoutDisplayOrderUsesDefaultAndTargetsCreatedProductCard() throws Exception {
        UUID productId = UUID.randomUUID();
        AppUserPrincipal principal = principal(Set.of(UserClaim.LOAN_PRODUCTS_CREATE));
        LoanProductSetting createdProduct = LoanProductSetting.builder().id(productId).build();
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));
        when(adminScopeService.currentSaccoId(any(AppUserPrincipal.class))).thenReturn("SACCO-01");
        when(adminService.createLoanProductTenure(eq("SACCO-01"),eq(principal.getMemberId()),eq(null),eq("School Fees Booster"),eq("Created product"),eq(5),eq(new BigDecimal("0")),eq(new BigDecimal("500000")),eq(2),eq(new BigDecimal("3.000000")),eq(false),eq(new BigDecimal("15000")),eq(new BigDecimal("0.015000")),eq(new BigDecimal("0.000000")),eq(new BigDecimal("0.100000")),eq(InterestMethod.FLAT_RATE),eq(1),eq(12),eq(true),eq(false),eq(false),eq(false),eq(false),eq(false),eq(null),eq(LoanProductStatus.ACTIVE),eq(2),eq("MONTHS"),eq(null),eq(null),eq(principal),any(com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.GroupRuleForm.class),isNull(),any(com.sacco.mvp.service.dto.LoanProductDocumentsForm.class))).thenReturn(createdProduct);

        mockMvc.perform(post("/admin/settings-controls/loan-products")
                .param("modalKey", "create-product")
                .param("productName", "School Fees Booster")
                .param("productDescription", "Created product")
                .param("minimumAmount", "0")
                .param("maximumAmount", "500000")
                .param("guarantorsRequired", "2")
                .param(productRatioParameterFromForm(), "300")
                .param("allowApplicationWithActiveLoan", "true")
                .param("maximumActiveLoans", "2")
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

        org.mockito.Mockito.doThrow(new IllegalStateException("Minimum amount is invalid.")).when(adminService).createLoanProductTenure(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),any(com.sacco.mvp.service.dto.LoanProductDocumentsForm.class));
        mockMvc.perform(post("/admin/settings-controls/loan-products")
                .param("modalKey", "create-product")
                .param("productName", "School Fees Booster")
                .param("productDescription", "Submitted text should remain visible")
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
                .param("disbursementOfficerRequired", "false")
                .param("disbursementProofRequired", "false")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/settings-controls?section=loan&modal=create-product"))
            .andExpect(flash().attribute("loanProductFormDraft", org.hamcrest.Matchers.hasEntry("productName", List.of("School Fees Booster"))))
            .andExpect(flash().attribute("loanProductFormDraft", org.hamcrest.Matchers.hasEntry("productDescription", List.of("Submitted text should remain visible"))))
            .andExpect(flash().attribute("loanProductFormDraft", org.hamcrest.Matchers.hasEntry("applicantAttachmentRequired", List.of("false"))))
            .andExpect(flash().attribute("loanProductFormDraft", org.hamcrest.Matchers.hasEntry("disbursementProofRequired", List.of("false"))));
    }

    @Test
    void updateLoanProductWithoutDisplayOrderSucceeds() throws Exception {
        UUID productId = UUID.randomUUID();
        AppUserPrincipal principal = principal(Set.of(UserClaim.LOAN_PRODUCTS_UPDATE));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));
        when(adminScopeService.currentSaccoId(any(AppUserPrincipal.class))).thenReturn("SACCO-01");

        mockMvc.perform(post("/admin/settings-controls/{id}", productId)
                .param("productName", "Business Loan")
                .param("productDescription", "Updated product")
                .param("minimumAmount", "0")
                .param("guarantorsRequired", "0")
                .param(productRatioParameterFromForm(), "0")
                .param("applicationFee", "0")
                .param("insurancePercent", "0")
                .param("processingFeePercent", "0")
                .param("annualInterestPercent", "10")
                .param("minRepaymentMonths", "1")
                .param("maxRepaymentMonths", "12")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/settings-controls/loan-products/" + productId + "/edit"))
            .andExpect(flash().attribute("message", "Loan product updated."))
            .andExpect(flash().attribute("error", org.hamcrest.Matchers.nullValue()));
        var saved=org.mockito.Mockito.mockingDetails(adminService).getInvocations().stream().filter(i->i.getMethod().getName().equals("updateLoanProductTenure")).findFirst().orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getArgument(22,Boolean.class)).isFalse();
        org.assertj.core.api.Assertions.assertThat(saved.getArgument(23,Boolean.class)).isFalse();
    }

    @Test
    void updateLoanProductValidationErrorPreservesSubmittedDraft() throws Exception {
        UUID productId = UUID.randomUUID();
        AppUserPrincipal principal = principal(Set.of(UserClaim.LOAN_PRODUCTS_UPDATE));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));

        org.mockito.Mockito.doThrow(new IllegalStateException("Minimum amount is invalid.")).when(adminService).updateLoanProductTenure(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),any(com.sacco.mvp.service.dto.LoanProductDocumentsForm.class));
        mockMvc.perform(post("/admin/settings-controls/{id}", productId)
                .param("modalKey", "product-" + productId)
                .param("productName", "Emergency Loan")
                .param("productDescription", "Edited text should remain visible")
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
                .param("disbursementOfficerRequired", "false")
                .param("disbursementProofRequired", "false")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/settings-controls/loan-products/" + productId + "/edit"))
            .andExpect(flash().attribute("loanProductFormDraft", org.hamcrest.Matchers.hasEntry("productName", List.of("Emergency Loan"))))
            .andExpect(flash().attribute("loanProductFormDraft", org.hamcrest.Matchers.hasEntry("productDescription", List.of("Edited text should remain visible"))))
            .andExpect(flash().attribute("loanProductFormDraft", org.hamcrest.Matchers.hasEntry("applicantAttachmentRequired", List.of("false"))))
            .andExpect(flash().attribute("loanProductFormDraft", org.hamcrest.Matchers.hasEntry("disbursementProofRequired", List.of("false"))));
    }

    @Test
    void sendTestEmailRedirectsWithSuccessFlash() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE));

        mockMvc.perform(post("/admin/platform-settings/email/test")
                .param("testRecipient", "admin@example.com")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/platform-settings#email"))
            .andExpect(flash().attribute("message", "Test email sent."))
            .andExpect(flash().attribute("error", org.hamcrest.Matchers.nullValue()));

        verify(platformEmailSettingsService).sendTestEmail("admin@example.com", principal.getMemberId());
    }

    @Test
    void sendTestEmailRedirectsWithErrorFlashWhenSmtpConnectionFails() throws Exception {
        AppUserPrincipal principal = platformPrincipal(Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE));
        doThrow(new MailSendException(
            "Mail server connection failed. Couldn't connect to host, port: smtp.microfinance.local, 465; timeout 10000",
            new SocketTimeoutException("Connect timed out")
        )).when(platformEmailSettingsService).sendTestEmail(any(), any());

        mockMvc.perform(post("/admin/platform-settings/email/test")
                .param("testRecipient", "admin@example.com")
                .with(csrf())
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/platform-settings/email/test"))
            .andExpect(flash().attribute(
                "error",
                "Test email could not be sent. Connection to smtp.microfinance.local on port 465 timed out."
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
            .andExpect(redirectedUrl("/admin/platform-settings#sms-gateway"))
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
            .andExpect(redirectedUrl("/admin/platform-settings/sms-gateway/test"))
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

    @Test
    void workspaceSmsUsagePageProvidesAccountsModelForJspUrls() throws Exception {
        AppUserPrincipal principal = principal(Set.of(UserClaim.SMS_USAGE_VIEW));
        var criteria = new SmsUsageManagementService.LoanSmsUsageCriteria(
            "SACCO-01",
            "ST-1",
            null,
            null,
            null,
            List.of()
        );
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "ST-1")).thenReturn(Optional.of(station()));
        when(adminScopeService.currentSaccoId(any(AppUserPrincipal.class))).thenReturn("SACCO-01");
        when(adminScopeService.currentStationId(any(AppUserPrincipal.class))).thenReturn("ST-1");
        when(smsUsageManagementService.loanUsageCriteria("SACCO-01", "ST-1", null, null, null, null))
            .thenReturn(criteria);
        when(smsUsageManagementService.account("SACCO-01", "ST-1")).thenReturn(smsAccount());
        when(stationOtpSettingsService.channel("SACCO-01", "ST-1")).thenReturn(OtpDeliveryChannel.SMS);
        when(smsUsageManagementService.loanUsageRows(eq(criteria), any())).thenReturn(new PageImpl<>(List.of()));
        when(smsUsageManagementService.selectedLoanUsageApplicants(criteria)).thenReturn(List.of());
        when(smsUsageManagementService.loanStatusOptions()).thenReturn(List.of());

        mockMvc.perform(get("/admin/sms-usage")
                .with(authentication(authenticationFor(principal))))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/sms-usage"))
            .andExpect(model().attributeExists("accounts"));
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
        return new AppUserPrincipal(member,claims);
    }

    private StationSmsAccount smsAccount() {
        return StationSmsAccount.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .availableUnits(100)
            .alertReservedUnits(0)
            .warningBaseline(100)
            .status(com.sacco.mvp.domain.SmsUnitStatus.HEALTHY)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
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
        return new AppUserPrincipal(member,claims);
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
    @Import({SecurityConfig.class, com.sacco.mvp.staffregistration.controller.StaffRegistrationApiController.class})
    static class TestConfig {
        @Bean
        com.sacco.mvp.clients.onboarding.service.OnboardingService onboardingService() {
            return Mockito.mock(com.sacco.mvp.clients.onboarding.service.OnboardingService.class);
        }
        @Bean com.sacco.mvp.accounting.loans.service.LoanProductAccountService productAccounts(){return Mockito.mock(com.sacco.mvp.accounting.loans.service.LoanProductAccountService.class);}
        @Bean com.sacco.mvp.accounting.charges.service.LoanChargeService loanCharges(){return Mockito.mock(com.sacco.mvp.accounting.charges.service.LoanChargeService.class);}
        @Bean com.sacco.mvp.accounting.charges.controller.LoanChargeController loanChargeController(com.sacco.mvp.accounting.charges.service.LoanChargeService charges){return new com.sacco.mvp.accounting.charges.controller.LoanChargeController(charges);}
        @Bean
        AdminController adminController(AdminService adminService,
                                        AdminScopeService adminScopeService,
                                        SaccoRegistryService saccoRegistryService,
                                        PlatformAdminService platformAdminService,
                                        DatabaseUtilizationService databaseUtilizationService,
                                        AppUsageAnalyticsService appUsageAnalyticsService,
                                        NotificationInboxService notificationInboxService,
                                        LoanProductRequiredAttachmentService requiredAttachmentService,
                                        com.sacco.mvp.accounting.loans.service.LoanProductAccountService productAccounts,
                                        com.sacco.mvp.accounting.charges.service.LoanChargeService loanCharges,
                                        SmsUsageManagementService smsUsageManagementService,
                                        StationOtpSettingsService stationOtpSettingsService,
                                        SaccoDataDeletionService saccoDataDeletionService,
                                        PlatformBrandingSettingsService platformBrandingSettingsService,
                                        PlatformSessionSettingsService platformSessionSettingsService,
                                        PlatformSupportContactSettingsService platformSupportContactSettingsService,
                                        PlatformEmailSettingsService platformEmailSettingsService,
                                        PlatformSmsGatewaySettingsService platformSmsGatewaySettingsService) {
            return new AdminController(
                loanCharges,
                adminService,
                new com.sacco.mvp.service.ApplicationClock("Africa/Nairobi"),
                adminScopeService,
                saccoRegistryService,
                platformAdminService,
                databaseUtilizationService,
                appUsageAnalyticsService,
                notificationInboxService,
                requiredAttachmentService,
                productAccounts,
                smsUsageManagementService,
                stationOtpSettingsService,
                saccoDataDeletionService,
                platformBrandingSettingsService,
                platformSessionSettingsService,
                platformSupportContactSettingsService,
                platformEmailSettingsService,
                platformSmsGatewaySettingsService,
                new org.springframework.context.support.ResourceBundleMessageSource() {{setBasename("messages");setDefaultEncoding("UTF-8");}},
                groupApprovals()
            );
        }

        @Bean
        SaccoAccessFilter saccoAccessFilter(SaccoStationRepository saccoStationRepository) {
            return new SaccoAccessFilter(new com.sacco.mvp.service.StationAccessService(saccoStationRepository),com.sacco.mvp.config.MvcSecurityTestConfiguration.activeAssignmentDirectory());
        }

        @Bean("authz")
        AuthzService authzService(LoanApplicationRepository loanApplicationRepository,
                                  GuarantorRequestRepository guarantorRequestRepository,
                                  BoardReviewRepository boardReviewRepository) {
            return new AuthzService(loanApplicationRepository,accessControlService());
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

        @Bean com.sacco.mvp.accounting.loans.service.LoanGroupApprovalService groupApprovals() { return Mockito.mock(com.sacco.mvp.accounting.loans.service.LoanGroupApprovalService.class); }
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
            when(service.policy()).thenReturn(new SessionTimeoutPolicy(30, 1_800_000L, 60_000L, false));
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
