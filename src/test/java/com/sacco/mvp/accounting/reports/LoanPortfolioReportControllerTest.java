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
    final LedgerReportService.Parameters p=new LedgerReportService.Parameters(LocalDate.of(2026,10,1),LocalDate.of(2026,10,2),OffsetDateTime.parse("2026-10-02T09:15:30+03:00"),2);
    @BeforeEach void setup(){reports=mock(LoanPortfolioReportService.class);actor=mock(AppUserPrincipal.class);messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);mvc=MockMvcBuilders.standaloneSetup(new LoanPortfolioReportController(reports,new ApplicationClock("Africa/Nairobi"),messages)).setCustomArgumentResolvers(new HandlerMethodArgumentResolver(){public boolean supportsParameter(MethodParameter p){return p.getParameterType()==AppUserPrincipal.class;}public Object resolveArgument(MethodParameter p,ModelAndViewContainer m,NativeWebRequest r,WebDataBinderFactory b){return actor;}}).build();}
    @Test void filtersRetainExactCutoffAndOwnBranchPage()throws Exception{var summary=new LoanPortfolioReportRepository.Summary(0,1,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);var report=new LoanPortfolioReportService.Report(new LedgerReportService.Scope("I1","B1",false),p,summary,new LoanPortfolioReportService.Ratios(null,null,null),new LedgerReportService.Coverage(1,0,1),List.of(),List.of(),List.of(),List.of(),false,LoanRiskMetrics.VERSION,LoanRiskMetrics.REGULATORY_REFERENCE);when(reports.report(actor,p,false)).thenReturn(report);mvc.perform(get("/reports/financial/portfolio").param("from","2026-10-01").param("through","2026-10-02").param("recordedThrough","2026-10-02T09:15:30").param("page","2")).andExpect(status().isOk()).andExpect(view().name("reporting/loan-portfolio")).andExpect(model().attribute("report",report)).andExpect(model().attribute("cutoffInput","2026-10-02T09:15:30"));verify(reports).report(actor,p,false);}
    @Test void invalidDatesReturnLocalizedBoundedErrorNotInternalFailure()throws Exception{when(reports.report(eq(actor),any(),eq(false))).thenThrow(new IllegalArgumentException("financial.report.error.dates"));mvc.perform(get("/reports/financial/portfolio").locale(Locale.forLanguageTag("sw"))).andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control","no-store")).andExpect(content().string(messages.getMessage("financial.report.error.dates",null,Locale.forLanguageTag("sw"))));}
    @Test void deniedWidePermissionRemainsAccessDenied(){when(reports.report(eq(actor),any(),eq(true))).thenThrow(new AccessDeniedException("No institution claim"));assertThatThrownBy(()->mvc.perform(get("/reports/financial/portfolio").param("institutionWide","true"))).hasRootCauseInstanceOf(AccessDeniedException.class);}
}
