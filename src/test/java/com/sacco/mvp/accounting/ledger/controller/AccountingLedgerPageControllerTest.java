package com.sacco.mvp.accounting.ledger.controller;

import com.sacco.mvp.accounting.ledger.dto.LedgerDtos.*;
import com.sacco.mvp.accounting.ledger.exception.LedgerException;
import com.sacco.mvp.accounting.ledger.service.AccountingLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
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

class AccountingLedgerPageControllerTest {
    private AnnotationConfigWebApplicationContext context;
    private AccountingLedgerService ledger;
    private MockMvc mvc;
    @BeforeEach void start(){context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();ledger=context.getBean(AccountingLedgerService.class);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    @AfterEach void stop(){context.close();}
    @Test void csrfAndDedicatedClaimProtectJournalDraft() throws Exception {
        mvc.perform(post("/accounting/journals").with(user(actor(UserClaim.ACCOUNTING_JOURNAL_DRAFT))).param("requestKey",UUID.randomUUID().toString())).andExpect(status().isForbidden());
        mvc.perform(draft().with(user(actor(UserClaim.ACCOUNTING_VIEW))).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(ledger);
    }
    @Test void authorizedDraftDelegatesWithServerActorAndRedirectsToRecord() throws Exception {
        UUID id=UUID.randomUUID();when(ledger.parseLines(anyString())).thenReturn(List.of());when(ledger.draft(any(),any())).thenReturn(id);
        mvc.perform(draft().with(user(actor(UserClaim.ACCOUNTING_JOURNAL_DRAFT))).with(csrf()).param("saccoId","FORGED").param("stationId","FOREIGN")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/accounting/journals/"+id));
        verify(ledger).draft(argThat(a->a.getSaccoId().equals("TEST-I")&&a.getStationId().equals("B1")),any());
    }
    @Test void failedAmountsRetainBoundedSafeFieldsAndNeverShowPostedSuccess() throws Exception {
        when(ledger.parseLines(anyString())).thenThrow(new LedgerException("balance"));
        mvc.perform(draft().with(user(actor(UserClaim.ACCOUNTING_JOURNAL_DRAFT))).with(csrf())).andExpect(status().isOk()).andExpect(view().name("accounting/error")).andExpect(model().attribute("ledgerError","accounting.ledger.error.balance")).andExpect(model().attributeExists("retained"));
        verify(ledger,never()).draft(any(),any());
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder draft(){return post("/accounting/journals").param("requestKey",UUID.randomUUID().toString()).param("effectiveDate","2026-10-04").param("description","Synthetic").param("evidenceReference","TEST").param("lines","1000,10,0\n3000,0,10");}
    private AppUserPrincipal actor(UserClaim claim){return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("TEST-I").stationId("B1").memberNo("TEST-STAFF").position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).memberAccount(true).build(),Set.of(claim),true);}
    @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean AccountingLedgerService ledger(){return mock(AccountingLedgerService.class);}
        @Bean AccountingLedgerPageController controller(AccountingLedgerService ledger){return new AccountingLedgerPageController(ledger);}
        @Bean(name="access") AccessControlService access(){return new AccessControlService();}
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {return http.authorizeHttpRequests(r->r.anyRequest().authenticated()).build();}
        @Bean InternalResourceViewResolver views(){return new InternalResourceViewResolver("/WEB-INF/jsp/",".jsp");}
    }
}
