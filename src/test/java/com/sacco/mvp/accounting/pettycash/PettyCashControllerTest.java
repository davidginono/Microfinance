package com.sacco.mvp.accounting.pettycash;

import com.sacco.mvp.accounting.pettycash.controller.PettyCashController;
import com.sacco.mvp.accounting.pettycash.dto.PettyCashDtos.*;
import com.sacco.mvp.accounting.pettycash.service.PettyCashService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.web.csrf.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PettyCashControllerTest {
    PettyCashService service;AppUserPrincipal actor;MockMvc mvc;final HttpSessionCsrfTokenRepository tokens=new HttpSessionCsrfTokenRepository();
    final LocalDate today=LocalDate.of(2026,11,19);
    @BeforeEach void setup(){
        service=mock(PettyCashService.class);actor=mock(AppUserPrincipal.class);var clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(today);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(actor,"", "ACCOUNTING_BUSINESS_VIEW"));
        var csrfFilter=new CsrfFilter(tokens);csrfFilter.setRequestHandler(new CsrfTokenRequestAttributeHandler());
        mvc=MockMvcBuilders.standaloneSetup(new PettyCashController(service,clock))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .addFilters(csrfFilter).build();
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void defaultsToThisMonthsExpensesAndRequiresValidDates()throws Exception{
        mvc.perform(get("/finance/petty-cash")).andExpect(status().isOk()).andExpect(view().name("accounting/pettycash/workspace"));
        verify(service).workspace(actor,new Query(today.withDayOfMonth(1),today,"expenses","","date_desc",0,30));
        mvc.perform(get("/finance/petty-cash").param("from","wrong")).andExpect(status().isBadRequest());
    }
    @Test void recordingUsesTheSharedPaymentFormAndRetainsAuthorization()throws Exception{
        when(service.expenseFund(actor)).thenReturn(new Fund(UUID.randomUUID(),"111001","Petty cash",today,"NEW_ZERO",null,"Evidence"));
        mvc.perform(get("/finance/petty-cash/expense")).andExpect(redirectedUrl("/finance/vouchers/payments/new?pettyCash=true"));
        when(service.expenseFund(actor)).thenThrow(new IllegalArgumentException("petty.error.setup"));
        mvc.perform(get("/finance/petty-cash/expense")).andExpect(status().isBadRequest());
    }
    @Test void setupRequiresCsrfAndKeepsTheFailedSubmission()throws Exception{
        UUID id=UUID.randomUUID();String evidence="Verified evidence";
        mvc.perform(post("/finance/petty-cash/setup").param("accountId",id.toString()).param("evidence",evidence)).andExpect(status().isForbidden());
        verify(service,never()).configure(any(),any());
        doThrow(new IllegalArgumentException("petty.error.coverage")).when(service).configure(actor,new Setup(id,evidence));
        when(service.setup(actor)).thenReturn(Optional.empty());when(service.selectedAccount(actor,id)).thenReturn("Cash box");
        mvc.perform(post("/finance/petty-cash/setup").with(request->{var token=tokens.generateToken(request);tokens.saveToken(token,request,new org.springframework.mock.web.MockHttpServletResponse());request.addHeader(token.getHeaderName(),token.getToken());return request;}).param("accountId",id.toString()).param("evidence",evidence))
            .andExpect(status().isOk()).andExpect(view().name("accounting/pettycash/setup"))
            .andExpect(model().attribute("accountId",id)).andExpect(model().attribute("evidence",evidence))
            .andExpect(model().attribute("selectedAccountName","Cash box"));
    }
    @Test void missingAccountKeepsTheEnteredEvidence()throws Exception{
        doThrow(new IllegalArgumentException("petty.error.evidence")).when(service).configure(actor,new Setup(null,"Evidence"));
        when(service.setup(actor)).thenReturn(Optional.empty());
        mvc.perform(post("/finance/petty-cash/setup").with(request->{var token=tokens.generateToken(request);tokens.saveToken(token,request,new org.springframework.mock.web.MockHttpServletResponse());request.addHeader(token.getHeaderName(),token.getToken());return request;}).param("evidence","Evidence"))
            .andExpect(status().isOk()).andExpect(view().name("accounting/pettycash/setup"))
            .andExpect(model().attribute("evidence","Evidence")).andExpect(model().attribute("setupError","petty.error.evidence"));
    }
}
