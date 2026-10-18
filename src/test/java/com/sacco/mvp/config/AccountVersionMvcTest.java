package com.sacco.mvp.config;

import com.sacco.mvp.accounting.controller.AccountVersionController;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.accounting.dto.AccountVersionDtos.*;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,AccountVersionMvcTest.Config.class})
class AccountVersionMvcTest {
    @Autowired WebApplicationContext context;@Autowired GeneralLedgerService ledger;MockMvc mvc;AppUserPrincipal actor;
    @BeforeEach void setup(){reset(ledger);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();actor=principal(EnumSet.allOf(UserClaim.class),true);}
    AppUserPrincipal principal(Set<UserClaim> claims,boolean staff){return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).memberNo("COAV-MVC").saccoId("I1").stationId("B1").fullName("Accountant").position(staff?Position.ACCOUNTANT:Position.MEMBER).staffAccessStatus(staff?StaffAccessStatus.ACTIVE:null).status(MemberStatus.ACTIVE).build(),claims,staff);}
    @Test void registerRetainsBoundedFiltersAndOrdering()throws Exception{
        when(ledger.accountVersionRegister(any(),anyString(),anyString(),anyString(),anyString(),anyInt(),anyInt())).thenReturn(new com.sacco.mvp.accounting.dto.GeneralLedgerDtos.Page<>(List.of(),2,true));
        mvc.perform(get("/finance/accounts/versions").with(user(actor)).param("state","PENDING").param("search","Cash").param("sort","code").param("direction","asc").param("page","2").param("size","50")).andExpect(status().isOk()).andExpect(view().name("accounting/account-versions")).andExpect(model().attribute("state","PENDING"));
        verify(ledger).accountVersionRegister(actor,"PENDING","Cash","code","asc",2,50);
    }
    @Test void savingDraftAllowListsMetadataAndDelegatesWithoutApproval()throws Exception{
        UUID id=UUID.randomUUID();when(ledger.saveAccountVersion(any(),eq(id),any(),eq(false))).thenReturn(id);
        mvc.perform(post("/finance/accounts/versions/"+id+"/edit").with(user(actor)).with(csrf()).param("command","DRAFT").param("revision","3").param("name","Cash").param("nameSw","Uneditable name").param("saccoId","OTHER").param("active","false").param("reviewer","OTHER")).andExpect(redirectedUrl("/finance/accounts/versions/"+id));
        verify(ledger).saveAccountVersion(eq(actor),eq(id),argThat(f->f.getRevision()==3 && f.getName().equals("Cash") && f.getNameSw().isEmpty()),eq(false));
    }
    @Test void policyUsesExpectedVersionAndFourIndependentModes()throws Exception{
        mvc.perform(post("/finance/accounts/approval-configuration").with(user(actor)).with(csrf()).param("version","4").param("createAutomatic","true").param("updateAutomatic","false").param("deactivateAutomatic","false").param("reactivateAutomatic","true").param("reason","Institution decision")).andExpect(redirectedUrl("/finance/accounts/approval-configuration"));
        verify(ledger).configureAccountApproval(eq(actor),argThat(f->f.getVersion()==4 && f.isCreateAutomatic() && !f.isUpdateAutomatic() && !f.isDeactivateAutomatic() && f.isReactivateAutomatic()));
    }
    @Test void decisionRetainsVersionAndReasonAndRequiresCsrf()throws Exception{
        UUID id=UUID.randomUUID();mvc.perform(post("/finance/accounts/versions/"+id+"/action").with(user(actor)).param("revision","5").param("action","APPROVE").param("reason","Checked")).andExpect(status().isForbidden());verifyNoInteractions(ledger);
        mvc.perform(post("/finance/accounts/versions/"+id+"/action").with(user(actor)).with(csrf()).param("revision","5").param("action","APPROVE").param("reason","Checked")).andExpect(redirectedUrl("/finance/accounts/versions/"+id));verify(ledger).accountVersionAction(actor,id,5,"APPROVE","Checked");
    }
    @Test void missingClaimsAndClientSessionsCannotChangeConfiguration()throws Exception{
        mvc.perform(get("/finance/accounts/versions").with(user(principal(Set.of(),true)))).andExpect(status().isForbidden());
        mvc.perform(post("/finance/accounts/approval-configuration").with(user(principal(Set.of(UserClaim.ACCOUNTING_ACCOUNTS_VIEW),true))).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/finance/accounts/versions").with(user(principal(EnumSet.allOf(UserClaim.class),false)))).andExpect(status().isForbidden());verifyNoInteractions(ledger);
    }
    @Test void legacyLifecycleRoutesOpenVersionCreationWithoutChangingTheAccount()throws Exception{
        UUID id=UUID.randomUUID();mvc.perform(post("/finance/accounts/"+id+"/deactivate").with(user(actor)).with(csrf())).andExpect(redirectedUrl("/finance/accounts/"+id+"/versions/new?operation=DEACTIVATE"));
        verify(ledger).accountCurrent(actor,id);verify(ledger,never()).deactivateAccount(any(),any());verify(ledger,never()).reactivateAccount(any(),any());
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean("access") @Primary com.sacco.mvp.service.AccessControlService access(){return new com.sacco.mvp.service.AccessControlService();}
        @Bean GeneralLedgerService ledger(){return mock(GeneralLedgerService.class);}
        @Bean AccountVersionController controller(GeneralLedgerService ledger){return new AccountVersionController(ledger);}
    }
}
