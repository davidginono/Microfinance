package com.sacco.mvp.accounting.policy;

import com.sacco.mvp.accounting.policy.controller.AccountingPolicyController;
import com.sacco.mvp.accounting.policy.dto.*;
import com.sacco.mvp.accounting.policy.service.AccountingPolicyService;
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
    private AnnotationConfigWebApplicationContext context;
    private AccountingPolicyService service;
    private MockMvc mvc;
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        context.register(Config.class);context.refresh();service=context.getBean(AccountingPolicyService.class);
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }

    @Test void viewClaimLoadsOnlyServiceScopedPage() throws Exception {
        when(service.list(any(),eq(0))).thenReturn(new PolicyPage(List.of(),0,false));
        mvc.perform(get("/accounting/policies").with(user(actor(UserClaim.ACCOUNTING_POLICY_VIEW))))
            .andExpect(status().isOk()).andExpect(view().name("accounting/policies/index"));
        verify(service).list(argThat(a->a.getSaccoId().equals("I1") && a.getStationId().equals("B1")),eq(0));
    }
    @Test void viewClaimCannotApproveOrCreate() throws Exception {
        mvc.perform(post("/accounting/policies").with(user(actor(UserClaim.ACCOUNTING_POLICY_VIEW))).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(post("/accounting/policies/"+UUID.randomUUID()+"/approve")
            .param("expectedHash","a".repeat(64)).param("evidence","A").param("reason","A")
            .with(user(actor(UserClaim.ACCOUNTING_POLICY_VIEW))).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void csrfIsRequiredForCreationAndReview() throws Exception {
        mvc.perform(post("/accounting/policies").with(user(actor(UserClaim.ACCOUNTING_POLICY_CREATE)))).andExpect(status().isForbidden());
        mvc.perform(post("/accounting/policies/"+UUID.randomUUID()+"/approve")
            .param("expectedHash","a".repeat(64)).param("evidence","A").param("reason","A")
            .with(user(actor(UserClaim.ACCOUNTING_POLICY_APPROVE)))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void invalidUuidDateUnknownFieldsAndOversizedTextStayOnDraftForm() throws Exception {
        for(var entry:Map.of("requestKey","invalid","effectiveFrom","bad-date","saccoId","foreign","authorityEvidence","A".repeat(2001)).entrySet()) {
            var request=post("/accounting/policies").param("requestKey",entry.getKey().equals("requestKey")?entry.getValue():UUID.randomUUID().toString())
                .param("authority","LOCAL").param("effectiveFrom",entry.getKey().equals("effectiveFrom")?entry.getValue():"2026-10-04")
                .param("openingDate","2026-09-01").param("authorityEvidence",entry.getKey().equals("authorityEvidence")?entry.getValue():"A");
            if(entry.getKey().equals("saccoId")) request.param("saccoId",entry.getValue());
            mvc.perform(request.with(user(actor(UserClaim.ACCOUNTING_POLICY_CREATE))).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("accounting/policies/new"))
                .andExpect(model().attribute("policyError","policy.error.invalid"));
        }
        mvc.perform(post("/accounting/policies").param("authority","LOCAL").param("effectiveFrom","2026-10-04")
            .param("openingDate","2026-09-01").param("authorityEvidence","A")
            .with(user(actor(UserClaim.ACCOUNTING_POLICY_CREATE))).with(csrf()))
            .andExpect(status().isOk()).andExpect(model().attribute("policyError","policy.error.invalid"));
        verifyNoInteractions(service);
    }
    @Test void independentReviewFailureIsLocalizedAndRetainsInput() throws Exception {
        UUID id=UUID.randomUUID();
        when(service.approve(any(),eq(id),anyString(),anyString(),anyString())).thenThrow(new IllegalArgumentException("policy.error.independent"));
        mvc.perform(post("/accounting/policies/"+id+"/approve").param("expectedHash","a".repeat(64)).param("evidence","Minute A").param("reason","Review")
            .with(user(actor(UserClaim.ACCOUNTING_POLICY_APPROVE))).with(csrf()))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("policyError","policy.error.independent"))
            .andExpect(flash().attribute("reviewEvidence","Minute A")).andExpect(redirectedUrl("/accounting/policies/"+id));
    }
    @Test void unauthenticatedRequestIsDenied() throws Exception {
        mvc.perform(get("/accounting/policies")).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    private AppUserPrincipal actor(UserClaim claim) {
        return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("C1")
            .memberAccount(true).position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).build(),Set.of(claim),true);
    }
    @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean(name="access") AccessControlService access() { return new AccessControlService(); }
        @Bean AccountingPolicyService service() { return mock(AccountingPolicyService.class); }
        @Bean AccountingPolicyController controller(AccountingPolicyService service) { return new AccountingPolicyController(service); }
        @Bean InternalResourceViewResolver views() { return new InternalResourceViewResolver("/WEB-INF/jsp/",".jsp"); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception { return http.authorizeHttpRequests(a->a.anyRequest().authenticated()).build(); }
    }
}
