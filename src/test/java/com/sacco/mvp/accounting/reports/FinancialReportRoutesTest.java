package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.accounting.reconciliation.ReconciliationService;
import com.sacco.mvp.accounting.statements.StatementDesignerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.reporting.*;
import com.sacco.mvp.reporting.execution.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FinancialReportRoutesTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private AppUserPrincipal actor;

    @BeforeEach void start() {
        context=new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("TEST").stationId("B1")
            .memberNo("STAFF").fullName("Test accountant").position(Position.ACCOUNTANT)
            .status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),true);
    }
    @AfterEach void stop(){context.close();}

    @Test void retiredPagesDownloadsAndMutationsAreAbsentEvenWithAllPermissions() throws Exception {
        UUID id=UUID.randomUUID();
        for(String route:List.of("/reports/builder","/reports/runs","/reports/runs/"+id,
            "/reports/runs/"+id+"/artifacts/"+id,"/reports/financial","/reports/financial/accounts/"+id,
            "/reports/financial/export","/reports/financial/accounts/"+id+"/export",
            "/reports/financial/cash-flow","/reports/statements/regulatory",
            "/reports/statements/regulatory/"+id+"/file","/finance/statement-outputs",
            "/finance/statement-outputs/"+id,"/finance/statement-outputs/"+id+"/artifacts/"+id))
            mvc.perform(get(route).with(user(actor))).andExpect(status().isNotFound());
        for(String route:List.of("/reports/runs","/reports/runs/"+id+"/approve",
            "/reports/runs/"+id+"/retry","/reports/runs/"+id+"/cancel",
            "/reports/financial/cash-flow/draft","/reports/financial/cash-flow/"+id+"/approve",
            "/reports/statements/regulatory","/reports/statements/regulatory/"+id+"/review",
            "/finance/statement-outputs","/finance/statement-outputs/"+id+"/review"))
            mvc.perform(post(route).with(user(actor)).with(csrf())).andExpect(status().isNotFound());
        verifyNoInteractions(context.getBean(StatementDesignerService.class),context.getBean(StatementOutputService.class),
            context.getBean(ReportRunService.class),context.getBean(LedgerReportService.class),
            context.getBean(CashFlowAllocationService.class),context.getBean(LoanPortfolioReportService.class));
    }

    @Test void retainedWorkspacesAndContextualStatementActionsRemainRegistered() throws Exception {
        when(context.getBean(ApplicationClock.class).today()).thenReturn(LocalDate.of(2026,10,12));
        mvc.perform(get("/reports/statements").with(user(actor)))
            .andExpect(status().isOk()).andExpect(view().name("reports/financial-statements"));
        Set<String> routes=new HashSet<>();
        context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().keySet()
            .forEach(mapping->routes.addAll(mapping.getPatternValues()));
        assertThat(routes).contains("/reports/financial/portfolio","/reports/statements/mappings",
            "/reports/statements/versions/{id}/preview","/reports/statements/versions/{id}/finalize",
            "/reports/statements/versions/{id}/pdf","/reports/statements/results/{id}/pdf");
    }

    @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity
    @ComponentScan(basePackages={"com.sacco.mvp.accounting.reports","com.sacco.mvp.accounting.statements",
        "com.sacco.mvp.reporting.execution.controller"},useDefaultFilters=false,
        includeFilters=@ComponentScan.Filter(type=FilterType.ANNOTATION,classes=Controller.class))
    static class Config {
        @Bean StatementDesignerService statements(){return mock(StatementDesignerService.class);}
        @Bean StatementOutputService outputs(){return mock(StatementOutputService.class);}
        @Bean StatementTypedExporter exporter(){return mock(StatementTypedExporter.class);}
        @Bean ReportExportLimiter limiter(){return mock(ReportExportLimiter.class);}
        @Bean ReconciliationService closing(){return mock(ReconciliationService.class);}
        @Bean LoanPortfolioReportService portfolio(){return mock(LoanPortfolioReportService.class);}
        @Bean AccountingReleaseService releases(){return mock(AccountingReleaseService.class);}
        @Bean ReportRunService runs(){return mock(ReportRunService.class);}
        @Bean LedgerReportService ledger(){return mock(LedgerReportService.class);}
        @Bean LedgerReportExporter ledgerExporter(){return mock(LedgerReportExporter.class);}
        @Bean CashFlowAllocationService cashFlow(){return mock(CashFlowAllocationService.class);}
        @Bean OperationalReportTemplateService templates(){return mock(OperationalReportTemplateService.class);}
        @Bean OperationalReportExportService operationalExporter(){return mock(OperationalReportExportService.class);}
        @Bean ApplicationClock clock(){return mock(ApplicationClock.class);}
        @Bean ObjectMapper mapper(){return JsonMapper.builder().findAndAddModules().build();}
        @Bean SecurityFilterChain filter(HttpSecurity http)throws Exception {
            return http.authorizeHttpRequests(requests->requests.anyRequest().authenticated()).build();
        }
        @Bean InternalResourceViewResolver views(){return new InternalResourceViewResolver("/WEB-INF/jsp/",".jsp");}
    }
}
