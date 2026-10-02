package com.sacco.mvp.reporting;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OperationalReportControllerTest {
    private AnnotationConfigWebApplicationContext context;private MockMvc mvc;private OperationalReportTemplateService templates;private OperationalReportService reports;
    @BeforeEach void start(){context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();templates=context.getBean(OperationalReportTemplateService.class);reports=context.getBean(OperationalReportService.class);
        when(context.getBean(ApplicationClock.class).today()).thenReturn(LocalDate.of(2026,10,2));when(context.getBean(ApplicationClock.class).now()).thenReturn(OffsetDateTime.parse("2026-10-02T10:00:00+03:00"));
        when(templates.list(any(),anyInt())).thenReturn(List.of());when(context.getBean(OperationalReportExportService.class).message(anyString(),anyString())).thenAnswer(i->i.getArgument(0));}
    @AfterEach void close(){context.close();}
    @Test void missingCsrfCannotCreateDraft()throws Exception{mvc.perform(post("/reports/builder/templates").param("definition",definition()).with(user(actor(UserClaim.REPORT_TEMPLATE_DESIGN)))).andExpect(status().isForbidden());verifyNoInteractions(templates);}
    @Test void runAndSharingPermissionCannotDesignOrPublish()throws Exception{
        mvc.perform(post("/reports/builder/templates").param("definition",definition()).with(user(actor(UserClaim.REPORT_RUN,UserClaim.REPORT_TEMPLATE_SHARE))).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/reports/builder/templates/"+UUID.randomUUID()+"/publish").with(user(actor(UserClaim.REPORT_TEMPLATE_DESIGN))).with(csrf())).andExpect(status().isForbidden());verifyNoInteractions(templates);
    }
    @Test void previewRequiresRunPermissionInAdditionToDesign()throws Exception{mvc.perform(post("/reports/builder/preview").param("definition",definition()).param("from","2026-10-01").param("through","2026-10-02").with(user(actor(UserClaim.REPORT_TEMPLATE_DESIGN))).with(csrf())).andExpect(status().isForbidden());verifyNoInteractions(reports);}
    @Test void unknownJsonPropertiesAreRejectedBeforePersistence()throws Exception{
        String hostile=definition().replaceFirst("\\{","{\"sql\":\"SELECT * FROM members\",");
        mvc.perform(post("/reports/builder/templates").param("definition",hostile).with(user(actor(UserClaim.REPORT_TEMPLATE_DESIGN))).with(csrf())).andExpect(status().isOk()).andExpect(view().name("reports/builder")).andExpect(model().attribute("reportError","report.error.definition"));
        verify(templates,never()).save(any(),any(),anyBoolean(),any());
    }
    @Test void validDraftRedirectsToConcreteReviewableVersion()throws Exception{
        UUID version=UUID.randomUUID();when(templates.save(any(),isNull(),eq(false),any())).thenReturn(version);
        mvc.perform(post("/reports/builder/templates").param("definition",definition()).with(user(actor(UserClaim.REPORT_TEMPLATE_DESIGN))).with(csrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/reports/builder?version="+version));
    }
    @Test void exportPermissionIsIndependentFromRun()throws Exception{mvc.perform(get("/reports/builder/templates/"+UUID.randomUUID()+"/export").param("from","2026-10-01").param("through","2026-10-02").param("cutoff","2026-10-02T10:00:00+03:00").param("format","CSV").with(user(actor(UserClaim.REPORT_RUN)))).andExpect(status().isForbidden());verifyNoInteractions(templates,reports);}
    private String definition(){return context.getBean(ObjectMapper.class).writeValueAsString(OperationalReportDefinition.standard(OperationalReportDefinition.Dataset.COLLECTIONS,"en"));}
    private AppUserPrincipal actor(UserClaim... claims){return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("12345").fullName("Test officer").position(Position.MANAGER).memberAccount(true).status(MemberStatus.ACTIVE).build(),Set.of(claims),true);}
    @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean OperationalReportService reports(){return mock(OperationalReportService.class);}@Bean OperationalReportTemplateService templates(){return mock(OperationalReportTemplateService.class);}
        @Bean OperationalReportExportService exports(){return mock(OperationalReportExportService.class);}@Bean ObjectMapper mapper(){return JsonMapper.builder().findAndAddModules().build();}
        @Bean ApplicationClock clock(){return mock(ApplicationClock.class);}@Bean(name="access")AccessControlService access(){return new AccessControlService();}@Bean AuditService audit(){return mock(AuditService.class);}
        @Bean ReportExportLimiter limiter(){return new ReportExportLimiter(1);}
        @Bean OperationalReportController controller(OperationalReportService r,OperationalReportTemplateService t,OperationalReportExportService e,ObjectMapper m,ApplicationClock c,AccessControlService a,AuditService audit,ReportExportLimiter l){return new OperationalReportController(r,t,e,m,c,a,audit,l);}
        @Bean SecurityFilterChain security(HttpSecurity http)throws Exception{return http.authorizeHttpRequests(auth->auth.anyRequest().authenticated()).build();}
        @Bean InternalResourceViewResolver views(){var r=new InternalResourceViewResolver();r.setPrefix("/WEB-INF/jsp/");r.setSuffix(".jsp");return r;}
    }
}
