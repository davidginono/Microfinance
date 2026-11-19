package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.statements.controller.*;
import com.sacco.mvp.accounting.statements.dto.StatementLayoutModels.*;
import com.sacco.mvp.accounting.statements.service.*;
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

class StatementLayoutMvcTest {
    final HttpSessionCsrfTokenRepository tokens=new HttpSessionCsrfTokenRepository();
    final StatementLayoutService layouts=mock(StatementLayoutService.class);
    final EquityMovementService movements=mock(EquityMovementService.class);
    final AppUserPrincipal actor=mock(AppUserPrincipal.class);MockMvc mvc;
    @BeforeEach void configure(){
        when(layouts.overview(actor)).thenReturn(new Layout(2,List.of(),List.of()));when(layouts.accounts(eq(actor),anyBoolean())).thenReturn(List.of());
        when(layouts.group(actor,-1,null)).thenReturn(new GroupEditor(new GroupForm(),List.of(),1));when(layouts.note(actor,-1,null)).thenReturn(new NoteEditor(new NoteForm(),List.of()));
        when(layouts.saveGroup(eq(actor),any())).thenReturn(true);when(layouts.saveNote(eq(actor),any())).thenReturn(true);
        var csrf=new CsrfFilter(tokens);csrf.setRequestHandler(new CsrfTokenRequestAttributeHandler());
        mvc=MockMvcBuilders.standaloneSetup(new StatementLayoutController(layouts),new EquityMovementsController(movements,new ApplicationClock("Africa/Dar_es_Salaam")))
            .setCustomArgumentResolvers(new HandlerMethodArgumentResolver(){public boolean supportsParameter(MethodParameter p){return p.hasParameterAnnotation(org.springframework.security.core.annotation.AuthenticationPrincipal.class);}public Object resolveArgument(MethodParameter p,ModelAndViewContainer m,NativeWebRequest r,org.springframework.web.bind.support.WebDataBinderFactory b){return actor;}}).addFilters(csrf).build();
    }
    org.springframework.test.web.servlet.request.RequestPostProcessor csrf(){return request->{var token=tokens.generateToken(request);tokens.saveToken(token,request,new org.springframework.mock.web.MockHttpServletResponse());request.addHeader(token.getHeaderName(),token.getToken());return request;};}
    @Test void replacementOverviewAndFocusedFormsUseAuthorizedServices()throws Exception{mvc.perform(get("/reports/financial/statements/layout")).andExpect(status().isOk()).andExpect(view().name("accounting/statements/layout"));mvc.perform(get("/reports/financial/statements/layout/groups/new")).andExpect(view().name("accounting/statements/layout-group"));mvc.perform(get("/reports/financial/statements/layout/notes/new")).andExpect(view().name("accounting/statements/layout-note"));verify(layouts).overview(actor);}
    @Test void retiredSetupGetAndBulkPostAreAbsent()throws Exception{mvc.perform(get("/reports/financial/statements/setup")).andExpect(status().isNotFound());mvc.perform(post("/reports/financial/statements/setup").with(csrf()).param("components[0].name","Retired editor")).andExpect(status().isNotFound());verify(layouts,never()).saveGroup(any(),any());verify(layouts,never()).saveNote(any(),any());}
    @Test void everyMetadataMutationStillRequiresCsrf()throws Exception{for(String route:List.of("layout/groups","layout/notes","layout/groups/remove","layout/notes/remove","movements"))mvc.perform(post("/reports/financial/statements/"+route)).andExpect(status().isForbidden());verify(layouts,never()).saveGroup(any(),any());verify(layouts,never()).remove(any(),anyBoolean(),any());verify(movements,never()).classify(any(),any(),anyInt(),any(),any());}
    @Test void groupFailureRetainsValuesAndBindsTheRelevantField()throws Exception{
        doThrow(new IllegalArgumentException("fs.error.layoutGroupName")).when(layouts).saveGroup(eq(actor),any());UUID account=UUID.randomUUID();
        mvc.perform(post("/reports/financial/statements/layout/groups").with(csrf()).param("version","3").param("index","0").param("requestKey",UUID.randomUUID().toString()).param("name","Owner capital").param("accounts",account.toString()).param("position","1").param("reason","Keep my reason").param("saccoId","FOREIGN").param("components[0].name","Retired field"))
            .andExpect(view().name("accounting/statements/layout-group")).andExpect(model().attributeHasFieldErrors("layoutGroup","name")).andExpect(model().attribute("positions",1));
        verify(layouts).saveGroup(eq(actor),argThat(f->f.getVersion()==3&&f.getIndex()==0&&f.getName().equals("Owner capital")&&f.getAccounts().equals(List.of(account))&&f.getReason().equals("Keep my reason")));
    }
    @Test void staleNoteErrorKeepsTheOriginalVersionAndReason()throws Exception{doThrow(new IllegalArgumentException("fs.error.setupStale")).when(layouts).saveNote(eq(actor),any());mvc.perform(post("/reports/financial/statements/layout/notes").with(csrf()).param("version","2").param("index","1").param("title","Policy").param("text","Keep this text").param("reason","Keep my reason")).andExpect(view().name("accounting/statements/layout-note")).andExpect(model().hasErrors());verify(layouts).saveNote(eq(actor),argThat(f->f.getVersion()==2&&f.getIndex()==1&&f.getText().equals("Keep this text")&&f.getReason().equals("Keep my reason")));}
    @Test void malformedAccountsCannotReachTheSaveUseCase()throws Exception{mvc.perform(post("/reports/financial/statements/layout/groups").with(csrf()).param("accounts","not-an-account-id")).andExpect(view().name("accounting/statements/layout-group")).andExpect(model().attributeHasFieldErrors("layoutGroup","accounts"));verify(layouts,never()).saveGroup(any(),any());}
    @Test void successfulAndUnchangedEditsReturnToTheOverview()throws Exception{mvc.perform(post("/reports/financial/statements/layout/groups").with(csrf())).andExpect(redirectedUrl("/reports/financial/statements/layout?saved=true"));when(layouts.saveGroup(eq(actor),any())).thenReturn(false);mvc.perform(post("/reports/financial/statements/layout/groups").with(csrf())).andExpect(redirectedUrl("/reports/financial/statements/layout?unchanged=true"));}
    @Test void removalCannotAcceptAnotherResourceKind()throws Exception{mvc.perform(post("/reports/financial/statements/layout/journals/remove").with(csrf())).andExpect(status().isBadRequest());verify(layouts,never()).remove(any(),anyBoolean(),any());}
    @Test void movementRoutesStillRejectUnknownTypes()throws Exception{mvc.perform(post("/reports/financial/statements/movements").with(csrf()).param("journal",UUID.randomUUID().toString()).param("version","0").param("category","ARBITRARY_FORMULA").param("reason","x").param("from","2026-01-01").param("through","2026-01-31")).andExpect(status().isBadRequest());verify(movements,never()).classify(any(),any(),anyInt(),any(),any());}
    @Test void customMovementRowUsesTheFocusedLayoutFormAndScopedSave()throws Exception{
        String path="/reports/financial/statements/layout/movement-rows";
        when(layouts.movementRow(actor,-1,null)).thenReturn(new MovementRowForm());
        when(layouts.saveMovementRow(eq(actor),any())).thenReturn(true);
        mvc.perform(get(path+"/new")).andExpect(view().name("accounting/statements/layout-movement-row"));
        mvc.perform(post(path)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(csrf()).param("name","Profit set aside").param("category","INVALID"))
            .andExpect(model().attributeHasFieldErrors("layoutMovementRow","category"));
        mvc.perform(post(path).with(csrf()).param("version","2").param("requestKey",UUID.randomUUID().toString())
            .param("name","Profit set aside").param("reason","Reserve policy").param("category","TRANSFER").param("saccoId","FOREIGN"))
            .andExpect(redirectedUrl("/reports/financial/statements/layout?saved=true"));
        verify(layouts).saveMovementRow(eq(actor),argThat(form->form.getVersion()==2&&form.getName().equals("Profit set aside")&&form.getCategory().name().equals("TRANSFER")));
    }
}
