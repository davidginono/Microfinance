package com.sacco.mvp.config;

import com.sacco.mvp.accounting.loans.controller.LoanCancellationController;
import com.sacco.mvp.accounting.loans.dto.LoanCancellationDtos.*;
import com.sacco.mvp.accounting.loans.service.LoanCancellationService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,LoanCancellationMvcTest.Config.class})
class LoanCancellationMvcTest {
    @Autowired WebApplicationContext context;
    @Autowired LoanCancellationService service;
    MockMvc mvc;AppUserPrincipal actor;UUID id,key;
    @BeforeEach void setup() {
        reset(service);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();id=UUID.randomUUID();key=UUID.randomUUID();
        actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT)
            .staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class));
        when(service.view(actor,id)).thenReturn(new View(id,"LN-TEST","Synthetic client","RECORDED","a".repeat(32),new BigDecimal("1000.00"),false,null,null,null,null,null,null));
    }
    @Test void confirmationIsReadOnlyAndProtectedFromCaching() throws Exception {
        when(service.action(eq(actor),any(View.class))).thenReturn(new Action("cancellation.title",null));
        mvc.perform(get("/finance/loan-records/"+id+"/cancel").with(user(actor))).andExpect(status().isOk())
            .andExpect(view().name("accounting/loans/cancel")).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(model().attribute("cancellationAction",new Action("cancellation.title",null)));
        verify(service,never()).cancel(any(),any(),any());
    }
    @Test void jspCanReadCancellationRecordComponentsAndAvailability() {
        var factory=jakarta.el.ExpressionFactory.newInstance();var el=new jakarta.el.StandardELContext(factory);
        el.getVariableMapper().setVariable("cancellation",factory.createValueExpression(service.view(actor,id),View.class));
        org.assertj.core.api.Assertions.assertThat((Boolean)factory.createValueExpression(el,"${cancellation.actionable}",Boolean.class).getValue(el)).isTrue();
        org.assertj.core.api.Assertions.assertThat((String)factory.createValueExpression(el,"${cancellation.number}",String.class).getValue(el)).isEqualTo("LN-TEST");
        var pending=new View(id,"LN-TEST","Client","DISBURSED","a".repeat(32),BigDecimal.TEN,true,"cancellation.error.pending",UUID.randomUUID(),"PENDING",null,null,null);
        el.getVariableMapper().setVariable("cancellation",factory.createValueExpression(pending,View.class));
        org.assertj.core.api.Assertions.assertThat((Boolean)factory.createValueExpression(el,"${cancellation.actionable}",Boolean.class).getValue(el)).isFalse();
    }
    @Test void mutationsRequireCsrfAndUseTheScopedPathId() throws Exception {
        String path="/finance/loan-records/"+id+"/cancel";
        mvc.perform(post(path).with(user(actor))).andExpect(status().isForbidden());
        verify(service,never()).cancel(any(),any(),any());
        when(service.cancel(eq(actor),eq(id),any())).thenReturn(new Result(id,null));
        mvc.perform(post(path).with(user(actor)).with(csrf()).param("requestKey",key.toString()).param("state","RECORDED")
            .param("terms","a".repeat(32)).param("reason","Wrong loan").param("confirmed","true").param("loanId",UUID.randomUUID().toString())
            .param("saccoId","OTHER").param("amount","0")).andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/finance/loan-records/"+id)).andExpect(flash().attribute("loanCancelled",true));
        verify(service).cancel(eq(actor),eq(id),argThat(f->f.getRequestKey().equals(key)&&f.isConfirmed()&&f.getReason().equals("Wrong loan")));
    }
    @Test void fundedRequestsRedirectToTheirExistingIndependentReview() throws Exception {
        UUID adjustment=UUID.randomUUID();when(service.cancel(eq(actor),eq(id),any())).thenReturn(new Result(id,adjustment));
        mvc.perform(post("/finance/loan-records/"+id+"/cancel").with(user(actor)).with(csrf()))
            .andExpect(redirectedUrl("/finance/loan-adjustments/"+adjustment));
    }
    @Test void validationFailureKeepsTheRequestAndEnteredReason() throws Exception {
        when(service.cancel(eq(actor),eq(id),any())).thenThrow(new IllegalArgumentException("cancellation.error.confirmation"));
        mvc.perform(post("/finance/loan-records/"+id+"/cancel").with(user(actor)).with(csrf()).param("requestKey",key.toString()).param("reason","Keep this reason"))
            .andExpect(status().isBadRequest()).andExpect(view().name("accounting/loans/cancel"))
            .andExpect(model().attributeHasFieldErrors("cancellationForm","confirmed"))
            .andExpect(model().attribute("cancellationForm",org.hamcrest.Matchers.hasProperty("reason",org.hamcrest.Matchers.is("Keep this reason"))));
    }
    @Test void deniedScopeOrPermissionIsNotConvertedIntoASuccess() throws Exception {
        when(service.view(actor,id)).thenThrow(new org.springframework.security.access.AccessDeniedException("Loan unavailable"));
        mvc.perform(get("/finance/loan-records/"+id+"/cancel").with(user(actor))).andExpect(status().isForbidden());
        when(service.cancel(eq(actor),eq(id),any())).thenThrow(new org.springframework.security.access.AccessDeniedException("Permission unavailable"));
        mvc.perform(post("/finance/loan-records/"+id+"/cancel").with(user(actor)).with(csrf())).andExpect(status().isForbidden());
    }
    @Configuration @EnableWebMvc @Import(LoanCancellationController.class)
    static class Config {
        @Bean LoanCancellationService cancellations() {return mock(LoanCancellationService.class);}
        @Bean ApplicationClock clock() {var c=mock(ApplicationClock.class);when(c.today()).thenReturn(LocalDate.of(2026,11,18));return c;}
    }
}
