package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.core.MethodParameter;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.*;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

class CashFlowAllocationControllerTest {
    CashFlowAllocationService service;AppUserPrincipal actor;MockMvc mvc;
    @BeforeEach void setup(){service=mock(CashFlowAllocationService.class);actor=mock(AppUserPrincipal.class);when(service.versions(actor,0)).thenReturn(List.of());when(service.journalChoices(actor,0)).thenReturn(List.of());mvc=MockMvcBuilders.standaloneSetup(new CashFlowAllocationController(service)).apply(springSecurity(new FilterChainProxy(new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE,new CsrfFilter(new HttpSessionCsrfTokenRepository()))))).setCustomArgumentResolvers(new HandlerMethodArgumentResolver(){public boolean supportsParameter(MethodParameter p){return p.getParameterType()==AppUserPrincipal.class;}public Object resolveArgument(MethodParameter p,ModelAndViewContainer m,NativeWebRequest r,WebDataBinderFactory b){return actor;}}).build();}
    @Test void emptyScopedPageHasBoundedHistoryAndSourceChoices()throws Exception{mvc.perform(get("/reports/financial/cash-flow")).andExpect(status().isOk()).andExpect(view().name("reporting/cash-flow-allocation")).andExpect(model().attribute("versions",List.of()));verify(service).versions(actor,0);verify(service).journalChoices(actor,0);}
    @Test void missingCsrfCannotApproveClassification()throws Exception{mvc.perform(post("/reports/financial/cash-flow/"+UUID.randomUUID()+"/approve").param("evidence","Review")).andExpect(status().isForbidden());verify(service,never()).approve(any(),any(),any());}
    @Test void reviewedCommandUsesAuthenticatedActorAndEvidence()throws Exception{UUID id=UUID.randomUUID();mvc.perform(post("/reports/financial/cash-flow/"+id+"/approve").with(csrf()).param("evidence","Independent source review")).andExpect(status().is3xxRedirection()).andExpect(flash().attribute("cashSuccess","financial.cash.approved"));verify(service).approve(actor,id,"Independent source review");}
    @Test void unequalSubmittedArraysAreRejectedBeforeServiceMutation()throws Exception{mvc.perform(post("/reports/financial/cash-flow/draft").with(csrf()).param("journal",UUID.randomUUID().toString()).param("requestKey",UUID.randomUUID().toString()).param("moneyLineId",UUID.randomUUID().toString()).param("counterpartAccountId",UUID.randomUUID().toString()).param("activity","OPERATING").param("amount","1.00","2.00").param("evidence","Source evidence")).andExpect(status().is3xxRedirection()).andExpect(flash().attribute("cashError","financial.cash.error.size"));verify(service,never()).draft(any(),any(),any(),any(),any(),any());}
}
