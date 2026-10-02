package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.reconciliation.ReconciliationService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import java.util.*;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StatementDesignerControllerTest {
    private AnnotationConfigWebApplicationContext context;
    private StatementDesignerService service;
    private MockMvc mvc;
    @BeforeEach void start(){context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();service=context.getBean(StatementDesignerService.class);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    @AfterEach void stop(){context.close();}
    @Test void csrfProtectsEveryFinancialMutation()throws Exception{
        UUID id=UUID.randomUUID();for(String route:List.of("/versions","/versions/"+id+"/approve","/versions/"+id+"/retire","/versions/"+id+"/preview","/versions/"+id+"/finalize","/regulatory","/regulatory/"+id+"/review"))mvc.perform(post("/reports/statements"+route).with(user(actor()))).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void finalizationPassesOnlyAuthenticatedActorAndSourceIdentifiers()throws Exception{
        var actor=actor();UUID version=UUID.randomUUID(),close=UUID.randomUUID(),prior=UUID.randomUUID(),result=UUID.randomUUID();when(service.finalize(any(),any(),any(),nullable(UUID.class),nullable(UUID.class))).thenReturn(result);
        mvc.perform(post("/reports/statements/versions/"+version+"/finalize").with(user(actor)).with(csrf()).param("closeReview",close.toString()).param("priorResult",prior.toString()).param("saccoId","FOREIGN").param("stationId","B2").param("coverage","REVIEWED").param("amount","999999")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/reports/statements/results/"+result));
        verify(service).finalize(actor,version,close,null,prior);
    }
    @Test void malformedLayoutReturnsLocalizedValidationWithoutWriting()throws Exception{
        mvc.perform(post("/reports/statements/versions").with(user(actor())).with(csrf()).param("definition","{invalid").param("evidence","Synthetic")).andExpect(status().isBadRequest()).andExpect(view().name("reports/statement-error")).andExpect(model().attribute("statementError","statement.error.validation"));verify(service,never()).save(any(),any(),any(),any());
    }
    @Test void rejectedAccountMappingRetainsTheEditableRowsAndApprovalEvidence()throws Exception{
        var heading=new StatementDefinition.Row("HEADING","Retained title","Jina lililohifadhiwa",StatementDefinition.RowKind.HEADING,StatementDefinition.Section.ASSETS,StatementDefinition.Unit.NONE,List.of(),null,null,null,null,true,false,false,null);var definition=new StatementDefinition(1,1,StatementDefinition.Kind.BALANCE_SHEET,"Retained statement","Taarifa iliyohifadhiwa",List.of(heading),List.of(),false);String json=context.getBean(ObjectMapper.class).writeValueAsString(definition);UUID template=UUID.randomUUID();when(service.save(any(),any(),any(),any())).thenThrow(new IllegalArgumentException("statement.error.omitted"));
        mvc.perform(post("/reports/statements/versions").with(user(actor())).with(csrf()).param("definition",json).param("template",template.toString()).param("evidence","Retained independent treatment evidence")).andExpect(status().isBadRequest()).andExpect(view().name("reports/statements")).andExpect(model().attribute("statementError","statement.error.omitted")).andExpect(model().attribute("definitionJson",json)).andExpect(model().attribute("draftTemplate",template)).andExpect(model().attribute("draftEvidence","Retained independent treatment evidence"));
    }
    @Test void policyAndPeriodGatesKeepTheirSpecificLocalizedMessage()throws Exception{
        UUID version=UUID.randomUUID();when(service.preview(any(),any(),any(),any(),any(),any(),any())).thenThrow(new IllegalArgumentException("accounting.policy.error.unapproved"));
        mvc.perform(post("/reports/statements/versions/"+version+"/preview").with(user(actor())).with(csrf()).param("from","2026-10-01").param("through","2026-10-31")).andExpect(status().isBadRequest()).andExpect(model().attribute("statementError","accounting.policy.error.unapproved"));
    }
    private AppUserPrincipal actor(){return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("SYNTHETIC").fullName("Synthetic").status(MemberStatus.ACTIVE).position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),true);}
    @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity static class Config {
        @Bean StatementDesignerService service(){return mock(StatementDesignerService.class);}
        @Bean ReconciliationService closing(){return mock(ReconciliationService.class);}
        @Bean ApplicationClock clock(){return mock(ApplicationClock.class);}
        @Bean ObjectMapper mapper(){return JsonMapper.builder().findAndAddModules().build();}
        @Bean StatementDesignerController controller(StatementDesignerService service,ReconciliationService closing,ApplicationClock clock,ObjectMapper mapper){return new StatementDesignerController(service,closing,clock,mapper);}
        @Bean SecurityFilterChain filter(HttpSecurity http)throws Exception{return http.authorizeHttpRequests(a->a.anyRequest().authenticated()).build();}
        @Bean InternalResourceViewResolver views(){return new InternalResourceViewResolver("/WEB-INF/jsp/",".jsp");}
    }
}
