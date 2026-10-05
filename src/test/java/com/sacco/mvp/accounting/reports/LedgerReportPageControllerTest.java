package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import org.junit.jupiter.api.*;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.*;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;

import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LedgerReportPageControllerTest {
    LedgerReportService reports; ApplicationClock clock; AppUserPrincipal actor; MockMvc mvc;
    final LedgerReportService.Scope scope=new LedgerReportService.Scope("I1","B1",false);
    @BeforeEach void setup() {
        reports=mock(LedgerReportService.class);clock=new ApplicationClock("Africa/Nairobi");actor=mock(AppUserPrincipal.class);
        when(reports.authorize(actor,false)).thenReturn(scope);
        mvc=MockMvcBuilders.standaloneSetup(new LedgerReportPageController(reports,clock))
            .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                public boolean supportsParameter(MethodParameter p){return p.getParameterType()==AppUserPrincipal.class;}
                public Object resolveArgument(MethodParameter p,ModelAndViewContainer m,NativeWebRequest r,WebDataBinderFactory b){return actor;}
            }).build();
    }
    @Test void localRecordedTimeBindsInApplicationZoneAndRetainsCutoffDuringDrillDown() throws Exception {
        UUID id=UUID.randomUUID();var p=new LedgerReportService.Parameters(LocalDate.of(2026,10,1),LocalDate.of(2026,10,2),OffsetDateTime.parse("2026-10-02T09:15:30+03:00"),2);
        when(reports.accountActivity(actor,id,p,false)).thenReturn(new LedgerReportService.AccountActivity(scope,p,id,"CASH","Synthetic cash",null,java.util.List.of(),null,new LedgerReportService.Coverage(1,0,0),false));
        mvc.perform(get("/reports/financial/accounts/"+id).param("from","2026-10-01").param("through","2026-10-02").param("recordedThrough","2026-10-02T09:15:30").param("page","2"))
            .andExpect(status().isOk()).andExpect(view().name("reporting/financial-ledger"))
            .andExpect(model().attribute("parameters",p)).andExpect(model().attribute("cutoffInput","2026-10-02T09:15:30"));
        verify(reports).accountActivity(actor,id,p,false);
    }
    @Test void missingApprovedPolicyShowsRestrictedStateWithOriginalDateFilters() throws Exception {
        when(reports.trialBalance(eq(actor),any(),eq(false))).thenThrow(new IllegalArgumentException("accounting.policy.error.approvalRequired"));
        mvc.perform(get("/reports/financial").param("from","2026-10-01").param("through","2026-10-02").param("recordedThrough","2026-10-02T09:15:30"))
            .andExpect(status().isOk()).andExpect(model().attribute("reportError","financial.report.error.policy"))
            .andExpect(model().attribute("cutoffInput","2026-10-02T09:15:30"));
        verify(reports).authorize(actor,false);
    }
    @Test void forbiddenInstitutionScopeIsNotConvertedToRecoverablePolicyError() {
        when(reports.authorize(actor,true)).thenThrow(new AccessDeniedException("No institution permission"));
        assertThatThrownBy(()->mvc.perform(get("/reports/financial").param("institutionWide","true")))
            .hasRootCauseInstanceOf(AccessDeniedException.class);
        verify(reports,never()).trialBalance(any(),any(),anyBoolean());
    }
}
