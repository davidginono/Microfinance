package com.sacco.mvp.config;

import com.sacco.mvp.accounting.reconciliation.controller.ReconciliationController;
import com.sacco.mvp.accounting.reconciliation.dto.ReconciliationReport.*;
import com.sacco.mvp.accounting.reconciliation.service.*;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,ReconciliationMvcTest.Config.class})
class ReconciliationMvcTest {
    static final String URL="/reports/financial/sl-gl-reconciliation";
    @Autowired WebApplicationContext context;
    @Autowired ReconciliationService service;
    @Autowired ReconciliationPdf pdf;
    MockMvc mvc;AppUserPrincipal actor;
    @BeforeEach void setup() {
        reset(service,pdf);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("TEST")
            .position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),true);
    }
    Result result(Parameters p) {return new Result(p,"Institution","Branch",List.of(),List.of(),0,false);}
    @Test void initialSettingsDoNotRunTheComparison()throws Exception {
        mvc.perform(get(URL).with(user(actor))).andExpect(status().isOk()).andExpect(view().name("accounting/reconciliation/report")).andExpect(header().string("Cache-Control","no-store"));
        verify(service).authorize(actor,false);verify(service,never()).report(any(),any(),anyBoolean());
    }
    @Test void filtersAndCutoffReachTheScopedServiceAndRemainInTheModel()throws Exception {
        var p=new Parameters(LocalDate.of(2026,10,1),LocalDate.of(2026,10,8),OffsetDateTime.parse("2026-10-08T12:00:00+03:00"),"PRINCIPAL",2);
        var r=result(p);when(service.report(actor,p,false)).thenReturn(r);
        mvc.perform(get(URL).with(user(actor)).param("run","true").param("from",p.from().toString()).param("through",p.through().toString())
            .param("cutoff",p.cutoff().toString()).param("component","PRINCIPAL").param("page","2").param("saccoId","OTHER").param("stationId","OTHER"))
            .andExpect(status().isOk()).andExpect(model().attribute("report",r));verify(service).report(actor,p,false);
    }
    @Test void malformedDateOrPageProducesRecoverableError()throws Exception {
        mvc.perform(get(URL).with(user(actor)).param("run","true").param("from","invalid")).andExpect(status().isBadRequest()).andExpect(model().attribute("formError","reconciliation.error.parameters"));
        mvc.perform(get(URL).with(user(actor)).param("run","true").param("page","invalid")).andExpect(status().isBadRequest());verify(service,never()).report(any(),any(),anyBoolean());
    }
    @Test void configuredActivityChoicesUseTheReportScopeAndLocalizedNames()throws Exception {
        var activity=new Activity(UUID.randomUUID(),"Configured operations","Shughuli zilizosanidiwa",null);
        when(service.activities(actor,"configured",1)).thenReturn(List.of(activity));
        mvc.perform(get(URL+"/activities").with(user(actor)).locale(Locale.forLanguageTag("sw")).param("search","configured").param("page","1"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$[0].id").value(activity.id().toString())).andExpect(jsonPath("$[0].label").value(activity.nameSw()));
        verify(service).activities(actor,"configured",1);
    }
    @Test void selectedActivityReachesPreviewPrintAndPdfAndSurvivesOutsideTheFirstOptionsPage()throws Exception {
        var activity=new Activity(UUID.randomUUID(),"Older configured activity",null,null);
        when(service.activity(actor,activity.id())).thenReturn(activity);
        when(service.report(eq(actor),any(),anyBoolean())).thenAnswer(i->result(i.getArgument(1)));
        when(pdf.build(any(),any())).thenReturn("%PDF-test".getBytes());
        mvc.perform(get(URL).with(user(actor)).param("run","true").param("activityId",activity.id().toString()))
            .andExpect(status().isOk()).andExpect(model().attribute("selectedActivity",activity));
        mvc.perform(get(URL+"/print").with(user(actor)).param("activityId",activity.id().toString())).andExpect(status().isOk());
        mvc.perform(get(URL+"/pdf").with(user(actor)).param("activityId",activity.id().toString())).andExpect(status().isOk());
        verify(service,times(3)).report(eq(actor),argThat(p->activity.id().equals(p.activityId()) && "ALL".equals(p.component())),anyBoolean());
        mvc.perform(get(URL).with(user(actor)).param("activityId","not-an-id")).andExpect(status().isBadRequest());
    }
    @Test void manualSelectionReachesPreviewAndBothExports()throws Exception {
        when(service.report(eq(actor),any(),anyBoolean())).thenAnswer(i->result(i.getArgument(1)));
        when(pdf.build(any(),any())).thenReturn("%PDF-test".getBytes());
        var response=mvc.perform(get(URL).with(user(actor)).param("run","true").param("activityId","UNASSIGNED"))
            .andExpect(status().isOk()).andReturn();
        org.assertj.core.api.Assertions.assertThat(((ReconciliationController.Filter)response.getModelAndView().getModel().get("filter")).getActivityFilter()).isEqualTo("UNASSIGNED");
        mvc.perform(get(URL+"/print").with(user(actor)).param("activityId","UNASSIGNED")).andExpect(status().isOk());
        mvc.perform(get(URL+"/pdf").with(user(actor)).param("activityId","UNASSIGNED")).andExpect(status().isOk());
        verify(service,times(3)).report(eq(actor),argThat(p->p.unassigned() && p.activityId()==null && "ALL".equals(p.component())),anyBoolean());
    }
    @Test void printAndPdfRequireExportAuthorizationAndUseTheWholeReport()throws Exception {
        when(service.report(eq(actor),any(),eq(true))).thenAnswer(i->result(i.getArgument(1)));when(pdf.build(any(),any())).thenReturn("%PDF-test".getBytes());
        mvc.perform(get(URL+"/print").with(user(actor))).andExpect(status().isOk()).andExpect(view().name("accounting/reconciliation/print"));
        mvc.perform(get(URL+"/pdf").with(user(actor))).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        verify(service,times(2)).report(eq(actor),any(),eq(true));
    }
    @Test void clientAndAnonymousSessionsCannotUseTheReport()throws Exception {
        var client=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("CLIENT").position(Position.MEMBER).memberAccount(true).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),false);
        mvc.perform(get(URL).with(user(client))).andExpect(status().isForbidden());mvc.perform(get(URL)).andExpect(status().is3xxRedirection());verifyNoInteractions(service);
    }
    @Test void noFinancialWriteOrSpreadsheetEndpointIsAdded()throws Exception {
        mvc.perform(post(URL).with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(post(URL).with(user(actor)).with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(get(URL+"/xlsx").with(user(actor))).andExpect(status().isNotFound());verifyNoInteractions(service);
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean("access") @Primary AccessControlService access(){return new AccessControlService();}
        @Bean ReconciliationService service(){return mock(ReconciliationService.class);}
        @Bean ReconciliationPdf pdf(){return mock(ReconciliationPdf.class);}
        @Bean ReconciliationController controller(ReconciliationService s,ReconciliationPdf p){return new ReconciliationController(s,p,new ApplicationClock("Africa/Nairobi"),mock(AuditService.class));}
    }
}
