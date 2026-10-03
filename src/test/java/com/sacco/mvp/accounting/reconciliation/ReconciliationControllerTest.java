package com.sacco.mvp.accounting.reconciliation;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
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

class ReconciliationControllerTest {
    private AnnotationConfigWebApplicationContext context;private ReconciliationService service;private MockMvc mvc;
    @BeforeEach void start() {context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();service=context.getBean(ReconciliationService.class);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    @AfterEach void stop() {context.close();}
    @Test void csrfRequiredBeforeImportMatchCloseOrReopen() throws Exception {
        for(String path:List.of("/finance/reconciliation/import","/finance/reconciliation/formats","/finance/reconciliation/exceptions","/finance/closing/reviews","/finance/closing/complete"))mvc.perform(post(path).with(user(actor()))).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void importUsesAuthenticatedScopeAndDecimalCommand() throws Exception {
        UUID key=UUID.randomUUID(),account=UUID.randomUUID(),format=UUID.randomUUID(),id=UUID.randomUUID();when(service.importStatement(any(),any())).thenReturn(id);
        var actor=actor();mvc.perform(post("/finance/reconciliation/import").with(user(actor)).with(csrf()).param("requestKey",key.toString()).param("account",account.toString()).param("format",format.toString()).param("from","2026-10-01").param("through","2026-10-02").param("opening","100.01").param("closing","100.02").param("filename","source.csv").param("evidence","Synthetic").param("content","date,reference,amount,kind\n2026-10-02,R,0.01,RECEIPT").param("action","import").param("saccoId","FOREIGN").param("stationId","B2")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/finance/reconciliation/statements/"+id));
        verify(service).importStatement(eq(actor),argThat(c->c.opening().toPlainString().equals("100.01")&&c.closing().toPlainString().equals("100.02")&&c.key().equals(key)));
    }
    @Test void closeRequiresExplicitConfirmationAndValidationPreservesEvidence() throws Exception {
        UUID id=UUID.randomUUID();mvc.perform(post("/finance/closing/reviews/"+id+"/approve").with(user(actor())).with(csrf()).param("evidence","Review").param("confirmed","false")).andExpect(status().isOk()).andExpect(view().name("accounting/reconciliation/error")).andExpect(model().attribute("reconciliationError","reconciliation.error.confirmation"));verify(service,never()).approveClose(any(),any(),any());
        when(service.importStatement(any(),any())).thenThrow(new IllegalArgumentException("reconciliation.error.statementBalance"));mvc.perform(post("/finance/reconciliation/import").with(user(actor())).with(csrf()).param("requestKey",UUID.randomUUID().toString()).param("account",UUID.randomUUID().toString()).param("format",UUID.randomUUID().toString()).param("from","2026-10-01").param("through","2026-10-02").param("opening","0").param("closing","2").param("filename","source.csv").param("evidence","Synthetic").param("content","retained CSV").param("action","import")).andExpect(status().isOk()).andExpect(model().attribute("content","retained CSV"));
    }

    @Test void timingPairUsesAuthenticatedActorAndRejectsSourceLinkForAnotherExceptionKind() throws Exception {
        UUID line=UUID.randomUUID(),journal=UUID.randomUUID();var actor=actor();
        mvc.perform(post("/finance/reconciliation/exceptions").with(user(actor)).with(csrf()).param("statementLine",line.toString()).param("journalLine",journal.toString()).param("kind","TIMING").param("assigned","STAFF-7").param("evidence","Verified dated source pair").param("saccoId","FOREIGN").param("stationId","B2")).andExpect(status().is3xxRedirection());
        verify(service).assignTimingExceptionToStaff(eq(actor),eq(line),eq(journal),eq("STAFF-7"),eq("Verified dated source pair"));verify(service,never()).assignExceptionToStaff(any(),any(),any(),any(),any());
        clearInvocations(service);
        mvc.perform(post("/finance/reconciliation/exceptions").with(user(actor)).with(csrf()).param("statementLine",line.toString()).param("journalLine",journal.toString()).param("kind","CHANNEL_FEE").param("assigned","STAFF-7").param("evidence","Wrong source link")).andExpect(status().isOk()).andExpect(model().attribute("reconciliationError","reconciliation.error.kind"));verifyNoInteractions(service);
    }
    private AppUserPrincipal actor() {var m=Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("TEST").fullName("Synthetic").status(MemberStatus.ACTIVE).position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).build();return new AppUserPrincipal(m,EnumSet.allOf(UserClaim.class),true);}
    @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity
    static class Config {
        @Bean ReconciliationService service() {return mock(ReconciliationService.class);}
        @Bean ReconciliationController controller(ReconciliationService s) {return new ReconciliationController(s);}
        @Bean SecurityFilterChain filter(HttpSecurity http) throws Exception {return http.authorizeHttpRequests(a->a.anyRequest().authenticated()).build();}
        @Bean InternalResourceViewResolver views() {return new InternalResourceViewResolver("/WEB-INF/jsp/",".jsp");}
    }
}
