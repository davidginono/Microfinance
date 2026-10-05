package com.sacco.mvp.accounting.policy;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountingPolicyControllerTest {
    AnnotationConfigWebApplicationContext context; AccountingPolicyService service; MockMvc mvc;
    @BeforeEach void start() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh(); service = context.getBean(AccountingPolicyService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void stop() { context.close(); }
    @Test void missingCsrfCannotCreateOrApprove() throws Exception {
        mvc.perform(post("/finance/policies").with(user(actor(UserClaim.ACCOUNTING_POLICIES_CREATE)))).andExpect(status().isForbidden());
        mvc.perform(post("/finance/policies/" + UUID.randomUUID() + "/decision").with(user(actor(UserClaim.ACCOUNTING_POLICIES_APPROVE)))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void viewingDoesNotGrantPostingOrApproval() throws Exception {
        mvc.perform(post("/finance/policies").with(user(actor(UserClaim.ACCOUNTING_POLICIES_VIEW))).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/finance/policies/" + UUID.randomUUID() + "/decision").param("decision", "APPROVED")
            .param("evidenceReference", "Synthetic evidence").param("reason", "Synthetic reason")
            .with(user(actor(UserClaim.ACCOUNTING_POLICIES_VIEW))).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void malformedDatesPreserveProposalAndDoNotReachService() throws Exception {
        mvc.perform(post("/finance/policies").param("openingDate", "invalid-date")
            .with(user(actor(UserClaim.ACCOUNTING_POLICIES_CREATE))).with(csrf())).andExpect(status().isOk())
            .andExpect(view().name("accounting/policies/new")).andExpect(model().attribute("policyError", "accounting.policy.error.validation"));
        verify(service, never()).create(any(), any());
    }
    @Test void eventMapsBindTypedValuesAndScopeFieldsAreIgnored() throws Exception {
        var request = post("/finance/policies").param("requestKey", UUID.randomUUID().toString())
            .param("authoritativeLedger", "LOCAL_GL").param("openingDate", "2026-10-02").param("effectiveFrom", "2026-10-02")
            .param("evidenceReference", "Synthetic evidence").param("saccoId", "OTHER").param("makerId", UUID.randomUUID().toString());
        for (var d : PolicyDecision.values()) request.param("decisions[" + d + "]", "Synthetic decision");
        for (var e : PostingEvent.values()) { request.param("permissions[" + e + "]", "DISABLED"); request.param("treatments[" + e + "]", "Synthetic disabled treatment"); }
        when(service.create(any(), any())).thenThrow(new IllegalArgumentException("accounting.policy.error.incomplete"));
        mvc.perform(request.with(user(actor(UserClaim.ACCOUNTING_POLICIES_CREATE))).with(csrf())).andExpect(status().isOk());
        verify(service).create(any(), argThat(c -> c.decisions().size() == PolicyDecision.values().length
            && c.postingMatrix().get(PostingEvent.FEE).permission() == AccountingPolicyService.PostingPermission.DISABLED));
    }
    AppUserPrincipal actor(UserClaim claim) { return new AppUserPrincipal(AccountingPolicyServiceTest.member(UUID.randomUUID(), "I1"), Set.of(claim), true); }
    @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean AccountingPolicyService policies() { return mock(AccountingPolicyService.class); }
        @Bean AccountingPolicyController controller(AccountingPolicyService p) { return new AccountingPolicyController(p); }
        @Bean AccessControlService access() { return new AccessControlService(); }
        @Bean SecurityFilterChain filter(HttpSecurity http) throws Exception { return http.authorizeHttpRequests(a -> a.anyRequest().authenticated()).build(); }
        @Bean InternalResourceViewResolver views() { return new InternalResourceViewResolver("/WEB-INF/jsp/", ".jsp"); }
    }
}
