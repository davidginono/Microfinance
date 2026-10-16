package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.accounting.management.controller.*;
import com.sacco.mvp.accounting.management.dto.ManagementReports.*;
import com.sacco.mvp.accounting.management.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.*;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ManagementControllerTest {
    BudgetService budgets;ManagementReportService reports;AppUserPrincipal actor;MockMvc mvc;
    final HttpSessionCsrfTokenRepository tokens=new HttpSessionCsrfTokenRepository();
    final UUID account=UUID.randomUUID(),id=UUID.randomUUID();
    @BeforeEach void setup(){
        budgets=mock(BudgetService.class);reports=mock(ManagementReportService.class);actor=mock(AppUserPrincipal.class);
        var clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(ManagementReportTest.DAY);when(clock.now()).thenReturn(ManagementReportTest.NOW);
        var csrfFilter=new CsrfFilter(tokens);csrfFilter.setRequestHandler(new CsrfTokenRequestAttributeHandler());
        mvc=MockMvcBuilders.standaloneSetup(new BudgetController(budgets,clock),new ManagementReportController(reports,mock(ManagementReportPdf.class),clock,mock(AuditService.class)))
            .addFilters(csrfFilter).setCustomArgumentResolvers(new HandlerMethodArgumentResolver(){public boolean supportsParameter(MethodParameter p){return p.getParameterType()==AppUserPrincipal.class;}public Object resolveArgument(MethodParameter p,ModelAndViewContainer m,NativeWebRequest r,WebDataBinderFactory b){return actor;}}).build();
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder form(){var request=post("/finance/budgets").param("requestKey",UUID.randomUUID().toString()).param("accountId",account.toString()).param("year","2027").param("date","2026-10-15");for(int n=0;n<12;n++)request.param("amounts["+n+"]","10.01");return request;}
    org.springframework.test.web.servlet.request.RequestPostProcessor validCsrf(){return request->{var token=tokens.generateToken(request);tokens.saveToken(token,request,new org.springframework.mock.web.MockHttpServletResponse());request.addHeader(token.getHeaderName(),token.getToken());return request;};}
    @Test void budgetPostRequiresCsrfAndBindsIsoDatesAndExactMonthlyAmounts()throws Exception{
        mvc.perform(form()).andExpect(status().isForbidden());verify(budgets,never()).save(any(),any());
        when(budgets.save(eq(actor),any())).thenAnswer(i->{BudgetForm f=i.getArgument(1);BudgetService.validate(f,ManagementReportTest.DAY);assertThat(f.getDate()).isEqualTo(ManagementReportTest.DAY);assertThat(f.getAmounts()).hasSize(12);return id;});
        mvc.perform(form().with(validCsrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/finance/budgets/"+id));
    }
    @Test void invalidBudgetKeepsEnteredValuesAndReturnsAnError()throws Exception{
        when(budgets.save(any(),any())).thenThrow(new IllegalArgumentException("management.error.months"));
        var result=mvc.perform(form().with(validCsrf())).andExpect(status().isBadRequest()).andExpect(view().name("accounting/budgets/form")).andExpect(model().attribute("formError","management.error.months")).andReturn();
        var f=(BudgetForm)result.getModelAndView().getModel().get("budgetForm");assertThat(f.getAccountId()).isEqualTo(account);assertThat(f.getAmounts().getFirst()).isEqualByComparingTo("10.01");
    }
    @Test void reportParametersOpenWithoutRunningAndRejectMalformedDates()throws Exception{
        mvc.perform(get("/reports/financial/operations/cashbook")).andExpect(status().isOk()).andExpect(view().name("accounting/management/report"));verify(reports,never()).report(any(),any(),any(),anyBoolean());
        mvc.perform(get("/reports/financial/operations/cashbook").param("run","true").param("from","invalid")).andExpect(status().isBadRequest()).andExpect(model().attribute("formError","management.error.parameters"));
    }
    @Test void printAndPdfRequireTheExportAuthorizationPath()throws Exception{
        when(reports.report(eq(actor),eq(Kind.CASHBOOK),any(),eq(true))).thenThrow(new AccessDeniedException("Export revoked"));
        assertThatThrownBy(()->mvc.perform(get("/reports/financial/operations/cashbook/pdf"))).hasRootCauseInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->mvc.perform(get("/reports/financial/operations/cashbook/print"))).hasRootCauseInstanceOf(AccessDeniedException.class);
    }
}
