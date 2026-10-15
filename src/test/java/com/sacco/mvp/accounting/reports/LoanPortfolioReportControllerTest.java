package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import org.junit.jupiter.api.*;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.*;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoanPortfolioReportControllerTest {
    LoanPortfolioReportService reports;AppUserPrincipal actor;MockMvc mvc;ResourceBundleMessageSource messages;
    final LoanPortfolioReportService.Parameters p=new LoanPortfolioReportService.Parameters(LocalDate.of(2026,10,1),LocalDate.of(2026,10,2),OffsetDateTime.parse("2026-10-02T09:15:30+03:00"),2);
    @ControllerAdvice static class WorkspaceContext {
        @ModelAttribute("headerStation") String branch(){return "B1";}
    }
    @BeforeEach void setup(){reports=mock(LoanPortfolioReportService.class);actor=mock(AppUserPrincipal.class);messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);mvc=MockMvcBuilders.standaloneSetup(new LoanPortfolioReportController(reports,new ApplicationClock("Africa/Nairobi"),messages)).setControllerAdvice(new WorkspaceContext()).setCustomArgumentResolvers(new HandlerMethodArgumentResolver(){public boolean supportsParameter(MethodParameter p){return p.getParameterType()==AppUserPrincipal.class;}public Object resolveArgument(MethodParameter p,ModelAndViewContainer m,NativeWebRequest r,WebDataBinderFactory b){return actor;}}).build();}
    @Test void filtersRetainExactCutoffAndOwnBranchPage()throws Exception{var summary=new LoanPortfolioReportRepository.Summary(0,1,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);var report=new LoanPortfolioReportService.Report(new LoanPortfolioReportService.Scope("I1","B1",false),p,summary,new LoanPortfolioReportService.Ratios(null,null,null),new LoanPortfolioReportService.Coverage(1,0,1),List.of(),List.of(),List.of(),List.of(),false,LoanRiskMetrics.VERSION,LoanRiskMetrics.REGULATORY_REFERENCE);when(reports.report(actor,p,false)).thenReturn(report);mvc.perform(get("/reports/financial/portfolio").param("from","2026-10-01").param("through","2026-10-02").param("recordedThrough","2026-10-02T09:15:30").param("page","2")).andExpect(status().isOk()).andExpect(view().name("reporting/loan-portfolio")).andExpect(model().attribute("report",report)).andExpect(model().attribute("cutoffInput","2026-10-02T09:15:30"));verify(reports).report(actor,p,false);}
    @Test void configurationRetainsSettingsWithoutRunningReport()throws Exception {
        var scope=new LoanPortfolioReportService.Scope("I1","B1",false);
        when(reports.configurationScope(actor,false)).thenReturn(scope);
        mvc.perform(get("/reports/financial/portfolio/configure").param("from","2026-10-01")
                .param("through","2026-10-02").param("recordedThrough","2026-10-02T09:15:30"))
            .andExpect(status().isOk()).andExpect(view().name("reporting/loan-portfolio-configure"))
            .andExpect(model().attribute("scope",scope)).andExpect(model().attribute("fromInput","2026-10-01"))
            .andExpect(model().attribute("throughInput","2026-10-02"))
            .andExpect(model().attribute("cutoffInput","2026-10-02T09:15:30"));
        verify(reports).configurationScope(actor,false);
        verify(reports,never()).report(any(),any(),anyBoolean());
    }
    @Test void invalidDatesReturnLocalizedFormWithRetainedValues()throws Exception {
        when(reports.report(eq(actor),any(),eq(false))).thenThrow(new IllegalArgumentException("financial.report.error.dates"));
        mvc.perform(get("/reports/financial/portfolio").locale(Locale.forLanguageTag("sw"))
                .param("from","2026-10-03").param("through","2026-10-02")
                .param("recordedThrough","2026-10-02T09:15:30"))
            .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(view().name("reporting/loan-portfolio-configure"))
            .andExpect(model().attribute("headerStation","B1"))
            .andExpect(model().attribute("formError",messages.getMessage("financial.report.error.dates",null,Locale.forLanguageTag("sw"))))
            .andExpect(model().attribute("fromInput","2026-10-03"))
            .andExpect(model().attribute("throughInput","2026-10-02"))
            .andExpect(model().attribute("cutoffInput","2026-10-02T09:15:30"));
        verify(reports).configurationScope(actor,false);
    }
    @Test void malformedDatesReturnSettingsInsteadOfGenericError()throws Exception {
        mvc.perform(get("/reports/financial/portfolio").param("from","invalid-date"))
            .andExpect(status().isBadRequest()).andExpect(view().name("reporting/loan-portfolio-configure"))
            .andExpect(model().attribute("headerStation","B1"))
            .andExpect(model().attribute("fromInput","invalid-date"));
        verify(reports,never()).report(any(),any(),anyBoolean());
        verify(reports).configurationScope(actor,false);
    }
    @Test void configurationDoesNotGrantInstitutionAccess() {
        when(reports.configurationScope(actor,true)).thenThrow(new AccessDeniedException("No institution claim"));
        assertThatThrownBy(()->mvc.perform(get("/reports/financial/portfolio/configure").param("institutionWide","true")))
            .hasRootCauseInstanceOf(AccessDeniedException.class);
        verify(reports,never()).report(any(),any(),anyBoolean());
    }
    @Test void deniedWidePermissionRemainsAccessDenied(){when(reports.report(eq(actor),any(),eq(true))).thenThrow(new AccessDeniedException("No institution claim"));assertThatThrownBy(()->mvc.perform(get("/reports/financial/portfolio").param("institutionWide","true"))).hasRootCauseInstanceOf(AccessDeniedException.class);}
}
