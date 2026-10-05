package com.sacco.mvp.reporting.operational;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.reporting.operational.controller.*;
import com.sacco.mvp.reporting.operational.dto.*;
import com.sacco.mvp.reporting.operational.dto.ReportDefinition.Dataset;
import com.sacco.mvp.reporting.operational.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
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
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OperationalReportControllerTest {
    private AnnotationConfigWebApplicationContext context;private MockMvc mvc;private OperationalReportService service;
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();
        service=context.getBean(OperationalReportService.class);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }
    @Test void previewNeedsCsrfAndRunClaim() throws Exception {
        mvc.perform(post("/reports/operational/api/preview").contentType("application/json").content("{\"definitionJson\":\"{}\",\"page\":0,\"size\":25}").with(user(actor(UserClaim.REPORTS_RUN))))
            .andExpect(status().isForbidden());
        mvc.perform(post("/reports/operational/api/preview").contentType("application/json").content("{\"definitionJson\":\"{}\",\"page\":0,\"size\":25}").with(csrf()).with(user(actor(UserClaim.REPORT_TEMPLATES_VIEW))))
            .andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void previewRejectsOversizedPageBeforeCallingService() throws Exception {
        mvc.perform(post("/reports/operational/api/preview").contentType("application/json").content("{\"definitionJson\":\"{}\",\"page\":0,\"size\":101}").with(csrf()).with(user(actor(UserClaim.REPORTS_RUN))))
            .andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void authorizedPreviewPreservesMoneyCentsAsStringsAndUnknownAsNull() throws Exception {
        var d=new OperationalReportCatalog().system(Dataset.COLLECTIONS,LocalDate.of(2026,10,4));when(service.parse("{}")).thenReturn(d);
        Map<String,Object> row=new LinkedHashMap<>();row.put("amount",new BigDecimal("9999999999999999.99"));row.put("principal",null);
        when(service.preview(any(),eq(d),eq(0),eq(25))).thenReturn(new OperationalReportResult(null,0,d,OffsetDateTime.now(),"I1","B1",List.of(),List.of(row),1,0,25,
            Map.of("amount",new OperationalReportResult.Total(new BigDecimal("9999999999999999.99"),1,0)),List.of(),new OperationalReportResult.Coverage(1,0,"opreport.coverage.COLLECTIONS")));
        mvc.perform(post("/reports/operational/api/preview").contentType("application/json").content("{\"definitionJson\":\"{}\",\"page\":0,\"size\":25}").with(csrf()).with(user(actor(UserClaim.REPORTS_RUN))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].amount").value("9999999999999999.99"))
            .andExpect(jsonPath("$.rows[0].principal").isEmpty()).andExpect(jsonPath("$.totals.amount.value").value("9999999999999999.99"));
    }
    @Test void publishingDoesNotFollowFromDesignPermissionAndShareNeedsSeparateClaim() throws Exception {
        UUID id=UUID.randomUUID();
        mvc.perform(post("/reports/operational/"+id+"/publish").param("expectedVersion","0").with(csrf()).with(user(actor(UserClaim.REPORT_TEMPLATES_UPDATE))))
            .andExpect(status().isForbidden());
        mvc.perform(post("/reports/operational/"+id+"/share").param("expectedVersion","0").param("institutionVisible","true").with(csrf()).with(user(actor(UserClaim.REPORT_TEMPLATES_PUBLISH))))
            .andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void controllerLayerDoesNotInjectPersistence() {
        for(var type:List.of(OperationalReportPageController.class,OperationalReportApiController.class))
            for(var field:type.getDeclaredFields()) org.assertj.core.api.Assertions.assertThat(field.getType().getName()).doesNotContain("repository","EntityManager","JdbcTemplate");
    }
    private AppUserPrincipal actor(UserClaim claim) {
        return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("T1")
            .fullName("Test").position(Position.MANAGER).memberAccount(true).status(MemberStatus.ACTIVE).build(),Set.of(claim),true);
    }
    @Configuration @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    @Import({OperationalReportApiController.class,OperationalReportPageController.class,AccessControlService.class})
    static class Config {
        @Bean OperationalReportService service() { return mock(OperationalReportService.class); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception { return http.authorizeHttpRequests(a -> a.anyRequest().authenticated()).build(); }
        @Bean InternalResourceViewResolver views() { var resolver=new InternalResourceViewResolver();resolver.setPrefix("/WEB-INF/jsp/");resolver.setSuffix(".jsp");return resolver; }
    }
}
