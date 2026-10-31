package com.sacco.mvp.config;

import com.sacco.mvp.clients.onboarding.controller.LoanIntakeController;
import com.sacco.mvp.clients.onboarding.dto.LoanIntakeDtos.*;
import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.*;
import com.sacco.mvp.clients.onboarding.service.*;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,ProvisionalClientMvcTest.Config.class})
class ProvisionalClientMvcTest {
    @Autowired WebApplicationContext context;@Autowired LoanIntakeService intake;@Autowired OnboardingService onboarding;
    MockMvc mvc;AppUserPrincipal staff,client;UUID id=UUID.randomUUID();String root;
    @BeforeEach void setup(){reset(intake);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        staff=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),true);
        client=new AppUserPrincipal(Member.builder().id(id).saccoId("I1").stationId("B1").position(Position.MEMBER).memberAccount(true).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),false);
        root="/finance/clients/onboarding/"+id+"/loan";when(intake.form(any(),any())).thenReturn(new Form());when(intake.latest(any(),any())).thenReturn(Optional.empty());when(intake.product(any(),any(),any())).thenReturn(Optional.empty());
        when(intake.authorize(any(),any(),anyBoolean())).thenReturn(new Client(id,"CL-00000001","Synthetic Client",null,null,"DRAFT",null,0,null));
    }
    @Test void csrfAndSessionBoundariesProtectBothChannels()throws Exception {
        mvc.perform(post(root+"/edit").with(user(staff))).andExpect(status().isForbidden());
        mvc.perform(post("/app/onboarding/loan/edit").with(user(client))).andExpect(status().isForbidden());
        mvc.perform(post(root+"/edit").with(user(client)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/app/onboarding/loan/edit").with(user(staff)).with(csrf())).andExpect(status().isForbidden());verifyNoInteractions(intake);
    }
    @Test void pendingClientCanSaveIntakeWithoutBindingAuthorityOrScope()throws Exception {
        mvc.perform(post("/app/onboarding/loan/edit").with(user(client)).with(csrf()).param("action","DRAFT").param("details.purpose","My stock").param("details.income","42.50").param("details.tenureUnit","YEARS").param("clientId",UUID.randomUUID().toString()).param("saccoId","OTHER").param("state","VERIFY").param("details.informationSource","VERIFIED"))
            .andExpect(redirectedUrl("/app/onboarding/loan"));
        verify(intake).save(eq(client),eq(id),argThat(f->f.getDetails().getPurpose().equals("My stock")&&f.getDetails().getIncome().toPlainString().equals("42.50")&&f.getDetails().getTenureUnit().isEmpty()));
    }
    @Test void failedSubmissionsPreserveValues()throws Exception {
        doThrow(new IllegalArgumentException("kyc.error.loanMoney")).when(intake).save(any(),any(),any());
        var result=mvc.perform(post("/app/onboarding/loan/edit").with(user(client)).with(csrf()).param("action","SUBMITTED").param("details.business","Keep my shop").param("details.income","1.001"))
            .andExpect(view().name("onboarding/loan-request-form")).andExpect(model().attributeHasErrors("loanRequestForm")).andReturn();
        assertThat(((Form)result.getModelAndView().getModel().get("loanRequestForm")).getDetails().getBusiness()).isEqualTo("Keep my shop");
    }
    @Test void malformedMoneyNeverReachesTheService()throws Exception {
        mvc.perform(post("/app/onboarding/loan/edit").with(user(client)).with(csrf()).param("details.income","invalid")).andExpect(view().name("onboarding/loan-request-form"));verify(intake,never()).save(any(),any(),any());
    }
    @Test void foreignClientAndProductLookupsRemainServiceScoped()throws Exception {
        doThrow(new org.springframework.security.access.AccessDeniedException("Unavailable")).when(intake).authorize(eq(staff),eq(id),anyBoolean());
        mvc.perform(get(root).with(user(staff))).andExpect(status().isForbidden());
        mvc.perform(get("/app/onboarding/loan/products").with(user(client)).param("search","shop").param("page","2")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));verify(intake).products(client,id,"shop",2);
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean LoanIntakeService intake(){return mock(LoanIntakeService.class);}
        @Bean LoanRecordingService recording(){return mock(LoanRecordingService.class);}
        @Bean LoanIntakeController controller(LoanIntakeService intake,OnboardingService onboarding,LoanRecordingService recording){return new LoanIntakeController(intake,onboarding,recording);}
    }
}
