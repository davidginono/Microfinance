package com.sacco.mvp.config;

import com.sacco.mvp.accounting.controller.ChartOfAccountsPageController;
import com.sacco.mvp.accounting.dto.AccountOnboardingForm;
import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.*;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({LoginCsrfAccessDeniedTest.TestConfig.class,ChartOfAccountsMvcTest.Config.class})
class ChartOfAccountsMvcTest {
    @Autowired WebApplicationContext context;
    @Autowired GeneralLedgerService ledger;
    MockMvc mvc;
    AppUserPrincipal accountant;
    UUID parent=UUID.randomUUID();
    @BeforeEach void setup() {
        reset(ledger);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var member=Member.builder().id(UUID.randomUUID()).memberNo("COA-TEST").saccoId("I1").stationId("B1")
            .fullName("Accountant").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build();
        accountant=new AppUserPrincipal(member,Set.of(UserClaim.ACCOUNTING_ACCOUNTS_VIEW,UserClaim.ACCOUNTING_ACCOUNTS_CREATE),true);
        lenient().when(ledger.chartParents(any(),anyBoolean(),anyString(),anyInt())).thenReturn(new Page<>(List.of(),0,false));
        lenient().when(ledger.chartParent(any(),eq(parent),anyBoolean())).thenReturn(new Account(parent,"121000","Equipment","ASSET","DEBIT","HEADING","OTHER",UUID.randomUUID(),true));
    }
    @Test void chartPreservesSearchAndFilters() throws Exception {
        when(ledger.chart(any(),any(),eq(1))).thenReturn(new Page<>(List.of(),1,false));
        mvc.perform(get("/finance/accounts").param("search","Land").param("type","ASSET").param("kind","POSTING").param("state","ACTIVE").param("page","1").with(user(accountant)))
            .andExpect(status().isOk()).andExpect(view().name("accounting/accounts"))
            .andExpect(model().attribute("filter",new AccountFilter("Land","ASSET","POSTING","ACTIVE")));
    }
    @Test void savesDirectlyAndIgnoresSubmittedInstitutionClassificationAndActiveState() throws Exception {
        mvc.perform(post("/finance/accounts/posting").with(user(accountant)).with(csrf()).param("parentId",parent.toString())
            .param("code","121001").param("name","Land").param("normalBalance","DEBIT").param("saccoId","FOREIGN")
            .param("type","INCOME").param("active","false"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/finance/accounts"));
        verify(ledger).onboardAccount(eq(accountant),argThat(f->f.getName().equals("Land") && f.getParentId().equals(parent)),eq(false));
    }
    @Test void duplicatePreservesValuesAndShowsInlineCodeError() throws Exception {
        when(ledger.onboardAccount(any(),any(),eq(false))).thenThrow(new IllegalArgumentException("accounting.error.duplicate"));
        var result=mvc.perform(post("/finance/accounts/posting").with(user(accountant)).with(csrf()).param("parentId",parent.toString())
            .param("code","121001").param("name","Land").param("nameSw","Ardhi"))
            .andExpect(status().isOk()).andExpect(view().name("accounting/account-form"))
            .andExpect(model().attributeHasFieldErrors("accountForm","code")).andReturn();
        assertThat(((AccountOnboardingForm)result.getModelAndView().getModel().get("accountForm")).getNameSw()).isEqualTo("Ardhi");
    }
    @Test void invalidParentBindingDoesNotCallTheMutation() throws Exception {
        mvc.perform(post("/finance/accounts/groups").with(user(accountant)).with(csrf()).param("parentId","invalid").param("name","Assets"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("accountForm","parentId"));
        verify(ledger,never()).onboardAccount(any(),any(),anyBoolean());
    }
    @Test void setupAndSaveRequireCsrfAndClientSessionsCannotEnter() throws Exception {
        mvc.perform(post("/finance/accounts/initialize").with(user(accountant))).andExpect(status().isForbidden());
        mvc.perform(post("/finance/accounts/posting").with(user(accountant))).andExpect(status().isForbidden());
        mvc.perform(post("/finance/accounts/initialize").with(user(accountant)).with(csrf())).andExpect(redirectedUrl("/finance/accounts"));
        var member=Member.builder().id(UUID.randomUUID()).memberNo("CLIENT").saccoId("I1").stationId("B1").fullName("Client").status(MemberStatus.ACTIVE).build();
        var client=new AppUserPrincipal(member,Set.of(UserClaim.ACCOUNTING_ACCOUNTS_VIEW),false);
        mvc.perform(get("/finance/accounts").with(user(client))).andExpect(status().isForbidden());
    }
    @Test void readOnlyAccountantCannotOpenOrSubmitCreationForms() throws Exception {
        var reader=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).memberNo("READER").saccoId("I1").stationId("B1")
            .position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),Set.of(UserClaim.ACCOUNTING_ACCOUNTS_VIEW),true);
        mvc.perform(get("/finance/accounts/groups/new").with(user(reader))).andExpect(status().isForbidden());
        mvc.perform(post("/finance/accounts/groups").with(user(reader)).with(csrf())).andExpect(status().isForbidden());
    }
    @Configuration @EnableWebMvc
    static class Config {
        @Bean("access") @org.springframework.context.annotation.Primary
        com.sacco.mvp.service.AccessControlService access(){return new com.sacco.mvp.service.AccessControlService();}
        @Bean GeneralLedgerService ledger(){return mock(GeneralLedgerService.class);}
        @Bean ChartOfAccountsPageController chartController(GeneralLedgerService ledger){return new ChartOfAccountsPageController(ledger);}
    }
}
