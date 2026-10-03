package com.sacco.mvp.reporting.execution;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.reporting.*;
import com.sacco.mvp.reporting.execution.controller.*;
import com.sacco.mvp.reporting.execution.dto.ReportRunDtos.Download;
import com.sacco.mvp.reporting.execution.service.*;
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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReportExecutionControllerTest {
 private AnnotationConfigWebApplicationContext context;private MockMvc mvc;private ReportRunService runs;
 @BeforeEach void start(){context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();runs=context.getBean(ReportRunService.class);}
 @AfterEach void stop(){context.close();}
 @Test void queueMutationsRequireCsrfAndExportPermission()throws Exception{mvc.perform(queue().with(user(actor(UserClaim.REPORT_RUN,UserClaim.REPORT_EXPORT)))).andExpect(status().isForbidden());mvc.perform(queue().with(user(actor(UserClaim.REPORT_RUN))).with(csrf())).andExpect(status().isForbidden());verifyNoInteractions(runs);}
 @Test void templatePublicationDoesNotAuthorizeRunOrFinancialOutputApproval()throws Exception{var actor=actor(UserClaim.REPORT_TEMPLATE_PUBLISH,UserClaim.REPORT_RUN,UserClaim.STATEMENT_EXPORT);mvc.perform(post("/reports/runs/"+UUID.randomUUID()+"/approve").param("evidence","Synthetic review").with(user(actor)).with(csrf())).andExpect(status().isForbidden());mvc.perform(post("/finance/statement-outputs/"+UUID.randomUUID()+"/review").param("evidence","Synthetic review").with(user(actor)).with(csrf())).andExpect(status().isForbidden());verifyNoInteractions(runs,context.getBean(StatementOutputService.class));}
 @Test void accountantApprovalCannotChooseComplianceOrStaffDecision()throws Exception{for(String stage:List.of("COMPLIANCE","STAFF"))mvc.perform(post("/finance/accounting-release/"+UUID.randomUUID()+"/decide").param("stage",stage).param("approved","true").param("evidence","Synthetic review").with(user(actor(UserClaim.ACCOUNTING_RELEASE_APPROVE))).with(csrf())).andExpect(status().isForbidden());verifyNoInteractions(context.getBean(AccountingReleaseService.class));}
 @Test void releaseApprovalAlsoRequiresCsrf()throws Exception{mvc.perform(post("/finance/accounting-release/"+UUID.randomUUID()+"/decide").param("stage","ACCOUNTANT").param("approved","true").param("evidence","Synthetic review").with(user(actor(UserClaim.ACCOUNTING_RELEASE_APPROVE)))).andExpect(status().isForbidden());verifyNoInteractions(context.getBean(AccountingReleaseService.class));}
 @Test void failedQueuePreservesTypedInputsAndSpecificRecoveryMessage()throws Exception{when(runs.request(any(),any())).thenThrow(new IllegalArgumentException("report.run.error.quota"));mvc.perform(queue().param("reason","Synthetic explanation").with(user(actor(UserClaim.REPORT_RUN,UserClaim.REPORT_EXPORT))).with(csrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/reports/runs")).andExpect(flash().attribute("runError","report.run.error.quota")).andExpect(flash().attribute("from",LocalDate.of(2026,10,1))).andExpect(flash().attribute("reason","Synthetic explanation"));}
 @Test void retainedArtifactDownloadUsesProtectedHeadersAndStoredChecksum()throws Exception{UUID id=UUID.randomUUID(),artifact=UUID.randomUUID();byte[] bytes="synthetic exact artifact".getBytes(java.nio.charset.StandardCharsets.UTF_8);when(runs.download(eq(id),eq(artifact),any())).thenReturn(new Download(OperationalReportExportService.Format.CSV,bytes,ReportRunService.sha256(bytes)));mvc.perform(get("/reports/runs/"+id+"/artifacts/"+artifact).with(user(actor(UserClaim.REPORT_RUN,UserClaim.REPORT_EXPORT)))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("X-Report-SHA256",ReportRunService.sha256(bytes))).andExpect(content().bytes(bytes));}
 @Test void releasePermissionsHaveNoAutomaticRoleDefaults(){for(Position role:Position.values())assertThat(UserClaim.defaultClaims(Set.of(role),true)).noneMatch(claim->claim.name().startsWith("ACCOUNTING_RELEASE_")||claim==UserClaim.ACCOUNTING_COMPLIANCE_RELEASE_APPROVE||claim==UserClaim.REPORT_RUN_APPROVE||claim==UserClaim.REPORT_JOBS_REVIEW);}
 @Test void releaseViewerCanReadRegistryWithoutLoadingProposalSources()throws Exception{mvc.perform(get("/finance/accounting-release").with(user(actor(UserClaim.ACCOUNTING_RELEASE_VIEW)))).andExpect(status().isOk()).andExpect(view().name("reports/accounting-release"));verifyNoInteractions(context.getBean(StatementOutputService.class),runs);}
 @Test void failedReleaseProposalPreservesEvidenceAndScopedRecordSelections()throws Exception{UUID key=UUID.randomUUID(),output=UUID.randomUUID(),first=UUID.randomUUID(),second=UUID.randomUUID();when(context.getBean(AccountingReleaseService.class).propose(any(),any())).thenThrow(new IllegalArgumentException("accounting.release.error.layouts"));mvc.perform(post("/finance/accounting-release").param("requestKey",key.toString()).param("statementOutput",output.toString()).param("firstSample",first.toString()).param("secondSample",second.toString()).param("evidence","Synthetic retained explanation").with(user(actor(UserClaim.ACCOUNTING_RELEASE_REQUEST))).with(csrf())).andExpect(status().is3xxRedirection()).andExpect(flash().attribute("requestKey",key)).andExpect(flash().attribute("selectedOutput",output)).andExpect(flash().attribute("selectedFirst",first)).andExpect(flash().attribute("selectedSecond",second)).andExpect(flash().attribute("proposalEvidence","Synthetic retained explanation"));}
 private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder queue(){return post("/reports/runs").param("requestKey",UUID.randomUUID().toString()).param("templateVersion",UUID.randomUUID().toString()).param("from","2026-10-01").param("through","2026-10-02").param("recordedCutoff","2026-10-02T10:00:00+03:00").param("formats","CSV");}
 private AppUserPrincipal actor(UserClaim... claims){return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("SYNTHETIC").stationId("B1").memberNo("STAFF-TEST").fullName("Synthetic officer").position(Position.MANAGER).status(MemberStatus.ACTIVE).build(),Set.of(claims),true);}
 @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
 static class Config {
  @Bean ReportRunService runs(){return mock(ReportRunService.class);}@Bean OperationalReportTemplateService templates(){return mock(OperationalReportTemplateService.class);}@Bean OperationalReportExportService exports(){return mock(OperationalReportExportService.class);}@Bean ApplicationClock clock(){return mock(ApplicationClock.class);}@Bean AccountingReleaseService releases(){return mock(AccountingReleaseService.class);}@Bean StatementOutputService outputs(){return mock(StatementOutputService.class);}@Bean StatementTypedExporter typedExporter(){return mock(StatementTypedExporter.class);}@Bean ObjectMapper mapper(){return JsonMapper.builder().findAndAddModules().build();}@Bean(name="access")AccessControlService access(){return new AccessControlService();}
  @Bean ReportRunController runsController(ReportRunService r,OperationalReportTemplateService t,OperationalReportExportService e,ApplicationClock c){return new ReportRunController(r,t,e,c);}
  @Bean AccountingReleaseController releaseController(AccountingReleaseService r,StatementOutputService o,ReportRunService runs,ObjectMapper mapper,StatementTypedExporter exporter){return new AccountingReleaseController(r,o,runs,mapper,exporter);}
  @Bean StatementOutputController outputController(StatementOutputService o,StatementTypedExporter e){return new StatementOutputController(o,e);}
  @Bean SecurityFilterChain security(HttpSecurity http)throws Exception{return http.authorizeHttpRequests(auth->auth.anyRequest().authenticated()).build();}
  @Bean InternalResourceViewResolver views(){var resolver=new InternalResourceViewResolver();resolver.setPrefix("/WEB-INF/jsp/");resolver.setSuffix(".jsp");return resolver;}
 }
}
