package com.sacco.mvp.config;

import com.sacco.mvp.accounting.adjustments.controller.LoanAdjustmentController;
import com.sacco.mvp.accounting.adjustments.service.LoanAdjustmentService;
import com.sacco.mvp.accounting.adjustments.dto.LoanAdjustmentDtos.*;
import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,LoanAdjustmentMvcTest.Config.class})
class LoanAdjustmentMvcTest {
    @Autowired WebApplicationContext context;
    @Autowired LoanAdjustmentService service;
    MockMvc mvc;AppUserPrincipal actor;
    @BeforeEach void setup(){reset(service);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("TEST").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),true);}
    @Test void requestsRequireCsrfAndExplicitConfirmation()throws Exception{
        mvc.perform(post("/finance/loan-adjustments").with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(post("/finance/loan-adjustments").with(user(actor)).with(csrf())).andExpect(status().isBadRequest());
        verify(service,never()).request(any(),any());
    }
    @Test void requestBindingUsesIsoDateAndExcludesDerivedFinancialFields()throws Exception{
        var id=UUID.randomUUID();when(service.request(eq(actor),any())).thenReturn(id);
        mvc.perform(post("/finance/loan-adjustments").with(user(actor)).with(csrf()).param("loanId",UUID.randomUUID().toString()).param("kind","WRITE_OFF").param("date","2026-10-16").param("confirmed","true").param("amount","1").param("principalChange","999").param("reason","Reviewed").param("evidence","Policy"))
            .andExpect(redirectedUrl("/finance/loan-adjustments/"+id));
        verify(service).request(eq(actor),argThat(f->f.getDate().equals(LocalDate.of(2026,10,16)) && f.getKind()==Kind.WRITE_OFF));
    }
    @Test void approvalRequiresCsrfConfirmationAndScopedServiceAuthorization()throws Exception{
        var id=UUID.randomUUID();var key=UUID.randomUUID();
        mvc.perform(post("/finance/loan-adjustments/"+id+"/decision").with(user(actor)).param("requestKey",key.toString()).param("approve","true").param("reason","Review")).andExpect(status().isForbidden());
        when(service.decide(actor,id,key,true,"Review")).thenReturn(id);
        mvc.perform(post("/finance/loan-adjustments/"+id+"/decision").with(user(actor)).with(csrf()).param("requestKey",key.toString()).param("approve","true").param("confirmed","true").param("reason","Review")).andExpect(redirectedUrl("/finance/loan-adjustments/"+id));
        verify(service).authorize(actor,"APPROVE");
    }
    @Test void clientSessionCannotAccessTheStaffWorkflow()throws Exception{
        var client=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("CLIENT").position(Position.MEMBER).memberAccount(true).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),false);
        mvc.perform(get("/finance/loan-adjustments").with(user(client))).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean("access") @Primary AccessControlService access(){return new AccessControlService();}
        @Bean LoanAdjustmentService service(){return mock(LoanAdjustmentService.class);}
        @Bean LoanAdjustmentController controller(LoanAdjustmentService s){return new LoanAdjustmentController(s,mock(ApplicationClock.class),mock(LoanRecordingPdfService.class),new org.springframework.context.support.StaticMessageSource());}
    }
}
