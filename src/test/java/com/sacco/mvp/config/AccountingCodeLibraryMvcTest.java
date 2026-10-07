package com.sacco.mvp.config;

import com.sacco.mvp.accounting.controller.AccountingCodeLibraryController;
import com.sacco.mvp.accounting.dto.AccountingLibraryDtos.*;
import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.Page;
import com.sacco.mvp.accounting.service.AccountingCodeLibraryService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({LoginCsrfAccessDeniedTest.TestConfig.class,AccountingCodeLibraryMvcTest.Config.class})
class AccountingCodeLibraryMvcTest {
    @Autowired WebApplicationContext context;@Autowired AccountingCodeLibraryService library;
    MockMvc mvc;AppUserPrincipal actor;UUID activity=UUID.randomUUID(),transaction=UUID.randomUUID();
    @BeforeEach void setup() {
        reset(library);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        actor=principal(true,Set.of(UserClaim.ACCOUNTING_ACCOUNTS_VIEW,UserClaim.ACCOUNTING_ACCOUNTS_CREATE,UserClaim.ACCOUNTING_ACCOUNTS_UPDATE));
        lenient().when(library.activity(any(),eq(activity))).thenReturn(new Activity(activity,"OPS","Operations","Shughuli","",true));
        lenient().when(library.transaction(any(),eq(transaction))).thenReturn(new TransactionCode(transaction,activity,"OPS","Operations","Shughuli","CASH-EXPENSE","Cash expense","","","MANUAL_JOURNAL",true,0,null));
        lenient().when(library.accounts(any(),anyString(),anyInt())).thenReturn(new Page<>(List.of(),0,false));
        lenient().when(library.activities(any(),anyString(),anyString(),anyInt())).thenReturn(new Page<>(List.of(),0,false));
        lenient().when(library.transactionRegister(any(),any(),anyString(),anyString(),anyInt())).thenReturn(new Page<>(List.of(),0,false));
    }
    @Test void filtersReachTheScopedList() throws Exception {
        when(library.activities(any(),eq("Office"),eq("ACTIVE"),eq(2))).thenReturn(new Page<>(List.of(),2,false));
        mvc.perform(get("/finance/library").with(user(actor)).param("search","Office").param("state","ACTIVE").param("page","2"))
            .andExpect(status().isOk()).andExpect(view().name("accounting/library")).andExpect(model().attribute("search","Office"));
    }
    @Test void creationDerivesActivityFromPathAndIgnoresTenantAndActiveParameters() throws Exception {
        when(library.createTransaction(any(),any())).thenReturn(transaction);
        mvc.perform(post("/finance/library/activities/"+activity+"/transactions").with(user(actor)).with(csrf())
            .param("activityId",UUID.randomUUID().toString()).param("saccoId","FOREIGN").param("active","false").param("code","PAY").param("name","Payment"))
            .andExpect(redirectedUrl("/finance/library/transactions/"+transaction));
        verify(library).createTransaction(eq(actor),argThat(f->activity.equals(f.getActivityId()) && f.getSourceEvent().equals("MANUAL_JOURNAL")));
    }
    @Test void duplicateCodeKeepsEnteredValues() throws Exception {
        when(library.createActivity(any(),any())).thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));
        var result=mvc.perform(post("/finance/library/activities").with(user(actor)).with(csrf()).param("code","OPS").param("name","Operations").param("nameSw","Shughuli"))
            .andExpect(status().isOk()).andExpect(view().name("accounting/library")).andExpect(model().attribute("modalOpen",true))
            .andExpect(model().attributeHasFieldErrors("codeForm","code")).andReturn();
        assertThat(((CodeForm)result.getModelAndView().getModel().get("codeForm")).getNameSw()).isEqualTo("Shughuli");
    }
    @Test void separateTransactionRegisterPreservesActivityAndPaginationFilters() throws Exception {
        mvc.perform(get("/finance/library/transactions").with(user(actor)).param("activityId",activity.toString()).param("search","cash").param("state","ACTIVE").param("page","1"))
            .andExpect(status().isOk()).andExpect(model().attribute("isTransactions",true));
        verify(library).transactionRegister(actor,activity,"cash","ACTIVE",1);
    }
    @Test void modalCreatesTransactionWithInitialAccountTemplateAndIgnoresOwnershipFields() throws Exception {
        when(library.onboardTransaction(any(),any())).thenReturn(transaction);
        mvc.perform(post("/finance/library/transactions").with(user(actor)).with(csrf()).param("activityCode","OPS").param("code","PAY").param("name","Office payment")
            .param("template.rules[0].component","TOTAL").param("template.rules[0].debitCode","EXPENSE").param("template.rules[0].creditCode","CASH").param("template.reason","Office payment")
            .param("activityId",UUID.randomUUID().toString()).param("saccoId","FOREIGN").param("active","false").param("nameSw","ignored"))
            .andExpect(redirectedUrl("/finance/library/transactions?search=PAY")).andExpect(flash().attribute("createdCode","PAY"));
        verify(library).onboardTransaction(eq(actor),argThat(f->f.getActivityId()==null && f.getNameSw()==null && f.getActivityCode().equals("OPS") && f.getTemplate().getRules().getFirst().getDebitCode().equals("EXPENSE")));
    }
    @Test void modalErrorsKeepTransactionAndAccountDetailsOnTheRegister() throws Exception {
        when(library.onboardTransaction(any(),any())).thenThrow(new IllegalArgumentException("library.error.templateAccount"));
        var result=mvc.perform(post("/finance/library/transactions").with(user(actor)).with(csrf()).param("activityCode","OPS").param("code","PAY").param("name","Office payment")
            .param("template.rules[0].debitCode","EXPENSE").param("template.rules[0].creditCode","UNKNOWN").param("template.reason","Office payment"))
            .andExpect(status().isOk()).andExpect(view().name("accounting/library")).andExpect(model().attribute("modalOpen",true)).andExpect(model().attributeHasErrors("transactionForm")).andReturn();
        var form=(TransactionForm)result.getModelAndView().getModel().get("transactionForm");assertThat(form.getCode()).isEqualTo("PAY");assertThat(form.getTemplate().getRules().getFirst().getCreditCode()).isEqualTo("UNKNOWN");
    }
    @Test void modalRequiresBothClaimsCsrfAndBoundedRuleBinding() throws Exception {
        mvc.perform(post("/finance/library/transactions").with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(post("/finance/library/transactions").with(user(principal(true,Set.of(UserClaim.ACCOUNTING_ACCOUNTS_VIEW,UserClaim.ACCOUNTING_ACCOUNTS_CREATE)))).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/finance/library/transactions").with(user(actor)).with(csrf()).param("template.rules[99].component","TOTAL")).andExpect(status().isBadRequest());
        verify(library,never()).onboardTransaction(any(),any());
    }
    @Test void saveTemplateBindsOnlyDefinitionFieldsAndRedirectsToSavedVersion() throws Exception {
        UUID version=UUID.randomUUID(),request=UUID.randomUUID();when(library.saveTemplate(any(),eq(transaction),any())).thenReturn(version);
        mvc.perform(post("/finance/library/transactions/"+transaction+"/templates").with(user(actor)).with(csrf()).param("requestKey",request.toString()).param("expectedRevision","0")
            .param("rules[0].component","TOTAL").param("rules[0].debitCode","EXPENSE").param("rules[0].creditCode","CASH").param("reason","Office payment").param("sourceEvent","REPAYMENT").param("saccoId","FOREIGN"))
            .andExpect(redirectedUrl("/finance/library/transactions/"+transaction+"?version="+version));
        verify(library).saveTemplate(eq(actor),eq(transaction),argThat(f->f.getRequestKey().equals(request) && f.getRules().size()==1 && f.getRules().getFirst().getDebitCode().equals("EXPENSE")));
    }
    @Test void staleTemplatePreservesRequestAndEnteredRules() throws Exception {
        UUID request=UUID.randomUUID();when(library.saveTemplate(any(),any(),any())).thenThrow(new IllegalArgumentException("library.error.staleTemplate"));
        var result=mvc.perform(post("/finance/library/transactions/"+transaction+"/templates").with(user(actor)).with(csrf()).param("requestKey",request.toString()).param("expectedRevision","1")
            .param("rules[0].component","TOTAL").param("rules[0].debitCode","EXPENSE").param("rules[0].creditCode","CASH").param("reason","Office payment"))
            .andExpect(status().isOk()).andExpect(view().name("accounting/library-template-form")).andExpect(model().attributeHasErrors("templateForm")).andReturn();
        var form=(TemplateForm)result.getModelAndView().getModel().get("templateForm");assertThat(form.getRequestKey()).isEqualTo(request);assertThat(form.getExpectedRevision()).isEqualTo(1);assertThat(form.getRules().getFirst().getCreditCode()).isEqualTo("CASH");
    }
    @Test void invalidRequestReferenceCannotMutate() throws Exception {
        mvc.perform(post("/finance/library/transactions/"+transaction+"/templates").with(user(actor)).with(csrf()).param("requestKey","invalid"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("templateForm","requestKey"));verify(library,never()).saveTemplate(any(),any(),any());
    }
    @Test void allMutationsRequireCsrfAndClientSessionsAreDenied() throws Exception {
        mvc.perform(post("/finance/library/activities").with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(post("/finance/library/transactions/"+transaction+"/templates").with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(get("/finance/library").with(user(principal(false,Set.of(UserClaim.ACCOUNTING_ACCOUNTS_VIEW))))).andExpect(status().isForbidden());
    }
    @Test void oversizedRuleIndicesFailWithoutAllocatingOrSaving() throws Exception {
        mvc.perform(post("/finance/library/transactions/"+transaction+"/templates").with(user(actor)).with(csrf()).param("rules[99].component","TOTAL"))
            .andExpect(status().isBadRequest());verify(library,never()).saveTemplate(any(),any(),any());
    }
    @Test void readOnlyAccountantCannotCreateOrSaveTemplates() throws Exception {
        var reader=principal(true,Set.of(UserClaim.ACCOUNTING_ACCOUNTS_VIEW));
        mvc.perform(get("/finance/library/activities/new").with(user(reader))).andExpect(status().isForbidden());
        mvc.perform(post("/finance/library/activities").with(user(reader)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/finance/library/transactions/"+transaction+"/template").with(user(reader))).andExpect(status().isForbidden());
        verifyNoInteractions(library);
    }
    private AppUserPrincipal principal(boolean staff,Set<UserClaim> claims) {
        var member=Member.builder().id(UUID.randomUUID()).memberNo(UUID.randomUUID().toString()).saccoId("I1").stationId("B1").fullName("Synthetic")
            .position(staff?Position.ACCOUNTANT:null).staffAccessStatus(staff?StaffAccessStatus.ACTIVE:StaffAccessStatus.NONE).status(MemberStatus.ACTIVE).build();
        return new AppUserPrincipal(member,claims,staff);
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean("access") @Primary com.sacco.mvp.service.AccessControlService access(){return new com.sacco.mvp.service.AccessControlService();}
        @Bean AccountingCodeLibraryService library(){return mock(AccountingCodeLibraryService.class);}
        @Bean AccountingCodeLibraryController controller(AccountingCodeLibraryService library){return new AccountingCodeLibraryController(library);}
    }
}
