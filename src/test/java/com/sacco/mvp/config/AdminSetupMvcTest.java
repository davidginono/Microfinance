package com.sacco.mvp.config;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.platform.onboarding.controller.AdminSetupController;
import com.sacco.mvp.platform.onboarding.service.AdminSetupService;
import com.sacco.mvp.platform.onboarding.dto.AdminSetupForm;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,AdminSetupMvcTest.Config.class})
class AdminSetupMvcTest {
    @Autowired WebApplicationContext context;
    @Autowired AdminSetupService service;
    MockMvc mvc;AppUserPrincipal root;
    @BeforeEach void setup() {
        reset(service);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        root=new AppUserPrincipal(Member.builder().id(AdminSetupService.BOOTSTRAP_ID).memberNo("ADM001").position(Position.ADMIN)
            .staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).platformSetupRequired(true).build(),Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE),true);
        doCallRealMethod().when(service).authorize(any());
    }
    @Test void pendingAdministratorCannotBypassSetupWithAnotherPageOrWrite()throws Exception {
        mvc.perform(get("/admin/dashboard").servletPath("/admin/dashboard").with(user(root))).andExpect(redirectedUrl("/system-admin/setup"));
        mvc.perform(post("/admin/saccos").servletPath("/admin/saccos").with(user(root)).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void setupNeedsAuthenticationAndCsrf()throws Exception {
        mvc.perform(get("/system-admin/setup")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/system-admin/setup").with(user(root))).andExpect(status().isForbidden());verify(service,never()).complete(any(),any());
    }
    @Test void pendingAdministratorCanOpenSetupAndOtherRoleCannot()throws Exception {
        mvc.perform(get("/system-admin/setup").servletPath("/system-admin/setup").with(user(root))).andExpect(view().name("auth/admin-setup"));
        var staff=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).position(Position.ACCOUNTANT).build(),Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE),true);
        mvc.perform(get("/system-admin/setup").with(user(staff))).andExpect(status().isForbidden());
    }
    @Test void failedSetupPreservesContactValuesButClearsEveryPassword()throws Exception {
        doThrow(new IllegalArgumentException("adminSetup.error.reuse")).when(service).complete(any(),any());
        var result=mvc.perform(post("/system-admin/setup").servletPath("/system-admin/setup").with(user(root)).with(csrf())
            .param("email","admin@example.invalid").param("currentPassword","secret").param("password","secret").param("confirmPassword","secret"))
            .andExpect(view().name("auth/admin-setup")).andReturn();
        var f=(AdminSetupForm)result.getModelAndView().getModel().get("setupForm");
        assertThat(f.getEmail()).isEqualTo("admin@example.invalid");assertThat(List.of(f.getPassword(),f.getCurrentPassword(),f.getConfirmPassword())).containsOnly("");
    }
    @Test void completionEndsSessionAndLogoutRemainsAvailable()throws Exception {
        mvc.perform(post("/system-admin/setup").servletPath("/system-admin/setup").with(user(root)).with(csrf()))
            .andExpect(redirectedUrl("/system-admin/login?setupComplete"));
        mvc.perform(post("/logout").servletPath("/logout").with(user(root)).with(csrf())).andExpect(status().is3xxRedirection());
    }
    @Configuration @EnableWebMvc @Import(AdminSetupController.class)
    static class Config {@Bean AdminSetupService setup(){return mock(AdminSetupService.class);}}
}
