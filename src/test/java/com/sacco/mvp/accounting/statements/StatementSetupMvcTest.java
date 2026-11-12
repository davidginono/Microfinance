package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.statements.controller.StatementSetupController;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.*;
import com.sacco.mvp.accounting.statements.service.StatementSetupService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.*;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.core.MethodParameter;
import org.springframework.security.web.csrf.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


class StatementSetupMvcTest {
    final HttpSessionCsrfTokenRepository tokens=new HttpSessionCsrfTokenRepository();
    StatementSetupService service=mock(StatementSetupService.class);AppUserPrincipal actor=mock(AppUserPrincipal.class);MockMvc mvc;
    @BeforeEach void configure(){
        when(service.accounts(actor)).thenReturn(List.of());when(service.edit(actor)).thenReturn(new Form());
        var csrfFilter=new CsrfFilter(tokens);csrfFilter.setRequestHandler(new CsrfTokenRequestAttributeHandler());
        mvc=MockMvcBuilders.standaloneSetup(new StatementSetupController(service,new ApplicationClock("Africa/Dar_es_Salaam")))
            .setCustomArgumentResolvers(new HandlerMethodArgumentResolver(){public boolean supportsParameter(MethodParameter p){return p.hasParameterAnnotation(org.springframework.security.core.annotation.AuthenticationPrincipal.class);}public Object resolveArgument(MethodParameter p,ModelAndViewContainer m,NativeWebRequest r,org.springframework.web.bind.support.WebDataBinderFactory b){return actor;}})
            .addFilters(csrfFilter).build();
    }
    org.springframework.test.web.servlet.request.RequestPostProcessor validCsrf(){return request->{var token=tokens.generateToken(request);tokens.saveToken(token,request,new org.springframework.mock.web.MockHttpServletResponse());request.addHeader(token.getHeaderName(),token.getToken());return request;};}
    @Test void getUsesAuthorizedServiceAndExpectedView()throws Exception{mvc.perform(get("/reports/financial/statements/setup")).andExpect(status().isOk()).andExpect(view().name("accounting/statements/setup"));verify(service).edit(actor);}
    @Test void financialMetadataCannotBeSavedWithoutCsrf()throws Exception{mvc.perform(post("/reports/financial/statements/setup")).andExpect(status().isForbidden());verify(service,never()).save(any(),any());}
    @Test void validationFailureRetainsSubmittedSetup()throws Exception{doThrow(new IllegalArgumentException("fs.error.setupStale")).when(service).save(eq(actor),any());mvc.perform(post("/reports/financial/statements/setup").with(validCsrf()).param("version","3").param("requestKey",UUID.randomUUID().toString()).param("reason","Keep this reason").param("components[0].name","Owner capital").param("components[0].accounts","311001"))
        .andExpect(status().isOk()).andExpect(view().name("accounting/statements/setup")).andExpect(model().attribute("errorKey","fs.error.setupStale"));verify(service).save(eq(actor),argThat(f->f.getVersion()==3&&f.getReason().equals("Keep this reason")&&f.getComponents().getFirst().getName().equals("Owner capital")));}
    @Test void classificationNeedsCsrfAndRejectsUnknownCategory()throws Exception{mvc.perform(post("/reports/financial/statements/movements").param("journal",UUID.randomUUID().toString())).andExpect(status().isForbidden());mvc.perform(post("/reports/financial/statements/movements").with(validCsrf()).param("journal",UUID.randomUUID().toString()).param("version","0").param("category","ARBITRARY_FORMULA").param("reason","x").param("from","2026-01-01").param("through","2026-01-31")).andExpect(status().isBadRequest());verify(service,never()).classify(any(),any(),anyInt(),any(),any());}
}
