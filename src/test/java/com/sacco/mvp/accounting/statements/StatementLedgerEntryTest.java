package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.statements.controller.StatementController;
import com.sacco.mvp.accounting.statements.dto.StatementModels.LedgerAccount;
import com.sacco.mvp.accounting.statements.service.StatementService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StatementLedgerEntryTest {
    private static final String PATH="/reports/financial/statements/ledger";
    private final StatementService statements=mock(StatementService.class);
    private final AppUserPrincipal actor=mock(AppUserPrincipal.class);
    private final HttpSessionCsrfTokenRepository tokens=new HttpSessionCsrfTokenRepository();
    private final UUID account=UUID.randomUUID(),key=UUID.randomUUID();
    private final LocalDate from=LocalDate.of(2026,1,1),through=from.plusDays(30);
    private MockMvc mvc;

    @BeforeEach void setUp(){
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,List.of()));
        var controller=new StatementController(statements,null,null,new ApplicationClock("Africa/Dar_es_Salaam"));
        var filter=new CsrfFilter(tokens);
        filter.setRequestHandler(new CsrfTokenRequestAttributeHandler());
        mvc=MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).addFilters(filter).build();
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder form(){
        return post(PATH).param("account",account.toString()).param("from",from.toString())
            .param("through",through.toString()).param("requestKey",key.toString()).with(request->{
                var token=tokens.generateToken(request);
                tokens.saveToken(token,request,new MockHttpServletResponse());
                request.addHeader(token.getHeaderName(),token.getToken());return request;
            });
    }
    @Test void accountSelectionOpensTheExistingBranchLedger()throws Exception{
        UUID run=UUID.randomUUID();
        when(statements.generateLedger(actor,from,through,account,key)).thenReturn(run);
        mvc.perform(get(PATH)).andExpect(status().isOk()).andExpect(model().attribute("ledgerForm",true));
        verify(statements).authorize(actor,false);
        mvc.perform(get(PATH+"/accounts").param("search","Bank")).andExpect(status().isOk());
        verify(statements).ledgerAccounts(actor,"Bank",0,null);
        mvc.perform(post(PATH)).andExpect(status().isForbidden());
        mvc.perform(form().param("institutionWide","true")).andExpect(redirectedUrl("/reports/financial/statements/"+run+"/ledger/"+account));
    }
    @Test void noAccountSelectionOpensTheAllAccountLedger()throws Exception{
        UUID run=UUID.randomUUID();
        when(statements.generateLedger(actor,from,through,null,key)).thenReturn(run);
        mvc.perform(form().with(request->{request.setParameter("account","");return request;}))
            .andExpect(redirectedUrl("/reports/financial/statements/"+run+"/ledger"));
    }
    @Test void failedGenerationRetainsDatesAndTheScopedAccount()throws Exception{
        var selected=new LedgerAccount(account,"111001 — Bank");
        when(statements.generateLedger(actor,from,through,account,key)).thenThrow(new IllegalArgumentException("fs.error.dates"));
        when(statements.ledgerAccounts(actor,"",0,account)).thenReturn(List.of(selected));
        mvc.perform(form()).andExpect(view().name("accounting/statements/configure"))
            .andExpect(model().attribute("ledgerForm",true)).andExpect(model().attribute("ledgerAccount",selected))
            .andExpect(model().attribute("from",from)).andExpect(model().attribute("through",through))
            .andExpect(model().attribute("requestKey",key)).andExpect(model().attribute("errorKey","fs.error.dates"));
    }
}
