package com.sacco.mvp.web;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoanPrepaymentControllerTest {
    AnnotationConfigWebApplicationContext context;MockMvc mvc;LoanRepaymentLedgerService ledger;
    final UUID loan=UUID.randomUUID(),key=UUID.randomUUID();
    @BeforeEach void setup(){context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();ledger=context.getBean(LoanRepaymentLedgerService.class);when(context.getBean(ApplicationClock.class).today()).thenReturn(LocalDate.of(2026,10,19));mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    @AfterEach void stop(){context.close();}
    AppUserPrincipal actor(UserClaim... claims){return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),Set.of(claims),true);}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder command(){return post("/repayments/loans/"+loan+"/interest-waivers").param("requestKey",key.toString()).param("effectiveDate","2026-10-19").param("amount","5.00").param("reason","Approved concession").param("confirmed","true");}
    @Test void waiverRequiresCsrf()throws Exception{mvc.perform(command().with(user(actor(UserClaim.LOAN_INTEREST_WAIVE)))).andExpect(status().isForbidden());verifyNoInteractions(ledger);}
    @Test void viewAndPostingClaimsDoNotGrantWaiver()throws Exception{mvc.perform(command().with(user(actor(UserClaim.LOAN_RECORDING_VIEW,UserClaim.LOAN_RECORDING_POST))).with(csrf())).andExpect(status().isForbidden());verifyNoInteractions(ledger);}
    @Test void validWaiverUsesTheScopedPathAndIgnoresInjectedActorAndLoanFields()throws Exception{
        mvc.perform(command().param("loanId",UUID.randomUUID().toString()).param("actorId",UUID.randomUUID().toString()).with(user(actor(UserClaim.LOAN_INTEREST_WAIVE))).with(csrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/repayments/loans/"+loan+"/settlement"));
        verify(ledger).waiveInterest(eq(loan),any(),eq(key),eq(LocalDate.of(2026,10,19)),eq(new java.math.BigDecimal("5.00")),eq("Approved concession"),isNull());
    }
    @Test void settlementIsNotAClientWorkspace()throws Exception{
        mvc.perform(get("/repayments/loans/"+loan+"/settlement").with(user(actor(UserClaim.MEMBER_LOANS_VIEW)))).andExpect(status().isForbidden());verifyNoInteractions(ledger);
    }
    @Test void anAdoptionCannotUseViewPermission()throws Exception{
        mvc.perform(post("/repayments/loans/"+loan+"/prepayment-terms").param("reason","Agreed change").with(user(actor(UserClaim.LOAN_RECORDING_VIEW))).with(csrf())).andExpect(status().isForbidden());verifyNoInteractions(ledger);
    }
    @Configuration @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity static class Config {
        @Bean LoanRepaymentLedgerService ledger(){return mock(LoanRepaymentLedgerService.class);}
        @Bean ApplicationClock clock(){return mock(ApplicationClock.class);}
        @Bean AdminService admin(){return mock(AdminService.class);}
        @Bean AdminScopeService scope(){return mock(AdminScopeService.class);}
        @Bean("access") AccessControlService access(){return new AccessControlService();}
        @Bean LoanPrepaymentController controller(LoanRepaymentLedgerService l,ApplicationClock c,AdminService a,AdminScopeService s){return new LoanPrepaymentController(l,c,a,s);}
        @Bean InternalResourceViewResolver views(){return new InternalResourceViewResolver("/WEB-INF/jsp/",".jsp");}
        @Bean SecurityFilterChain security(HttpSecurity http)throws Exception{return http.authorizeHttpRequests(a->a.anyRequest().authenticated()).build();}
    }
}
