package com.sacco.mvp.config;

import com.sacco.mvp.accounting.payroll.controller.PayrollFeedbackController;
import com.sacco.mvp.accounting.payroll.dto.PayrollFeedbackDtos.RemittanceForm;
import com.sacco.mvp.accounting.payroll.service.*;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,PayrollFeedbackMvcTest.Config.class})
class PayrollFeedbackMvcTest {
    @Autowired WebApplicationContext context;@Autowired PayrollFeedbackService feedback;@Autowired PayrollService payroll;MockMvc mvc;
    AppUserPrincipal actor(Set<UserClaim> claims){return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),claims,true);}
    @BeforeEach void setup(){reset(feedback,payroll);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    @Test void uploadRequiresViewCurrentImportClaimAndCsrf()throws Exception {
        var a=actor(EnumSet.allOf(UserClaim.class));String path="/finance/payroll/"+UUID.randomUUID()+"/feedback/new";var file=new MockMultipartFile("file","results.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",new byte[]{1});
        mvc.perform(get(path).with(user(actor(Set.of())))).andExpect(status().isForbidden());
        mvc.perform(multipart(path).file(file).param("evidence","Employer evidence").with(user(a))).andExpect(status().isForbidden());verifyNoInteractions(feedback);
        doThrow(new org.springframework.security.access.AccessDeniedException("Revoked")).when(payroll).authorize(a,"IMPORT");mvc.perform(multipart(path).file(file).param("evidence","Evidence").with(csrf()).with(user(a))).andExpect(status().isForbidden());verifyNoInteractions(feedback);
    }
    @Test void preparesOnlyTheBoundRemittanceInTheAuthenticatedScope()throws Exception {
        var a=actor(EnumSet.allOf(UserClaim.class));UUID batch=UUID.randomUUID(),id=UUID.randomUUID(),receipts=UUID.randomUUID(),money=UUID.randomUUID();when(feedback.prepare(eq(a),eq(batch),eq(id),any())).thenReturn(receipts);
        mvc.perform(post("/finance/payroll/"+batch+"/feedback/"+id+"/prepare").with(user(a)).with(csrf()).param("moneyAccountId",money.toString()).param("receivedDate","2026-10-08").param("receivedAmount","100.01").param("reference","BANK-1").param("evidence","Bank evidence").param("verified","true").param("saccoId","OTHER").param("clientId",UUID.randomUUID().toString()).param("rows[0].amount","1"))
            .andExpect(redirectedUrl("/finance/batches/receipts/"+receipts)).andExpect(header().string("Cache-Control","no-store"));
        var form=ArgumentCaptor.forClass(RemittanceForm.class);verify(feedback).prepare(eq(a),eq(batch),eq(id),form.capture());assertThat(form.getValue().getReceivedAmount()).isEqualByComparingTo("100.01");assertThat(form.getValue().getMoneyAccountId()).isEqualTo(money);assertThat(form.getValue().isVerified()).isTrue();
    }
    @Test void invalidPagingReturnsALocalizedBadRequest()throws Exception {
        var a=actor(EnumSet.allOf(UserClaim.class));UUID batch=UUID.randomUUID();when(feedback.list(a,batch,-1,"date","desc")).thenThrow(new IllegalArgumentException("payroll.feedback.error.input"));
        mvc.perform(get("/finance/payroll/"+batch+"/feedback?page=-1").locale(Locale.ENGLISH).with(user(a))).andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.containsString("Check the values")));
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor alias(){return factory->factory.registerAlias("accessControlService","access");}
        @Bean PayrollService payroll(){return mock(PayrollService.class);}
        @Bean PayrollFeedbackService feedback(){return mock(PayrollFeedbackService.class);}
        @Bean LoanRecordingService loans(){return mock(LoanRecordingService.class);}
        @Bean ApplicationClock clock(){return new ApplicationClock("Africa/Nairobi");}
        @Bean org.springframework.context.support.ResourceBundleMessageSource messages(){var m=new org.springframework.context.support.ResourceBundleMessageSource();m.setBasename("messages");m.setDefaultEncoding("UTF-8");return m;}
        @Bean PayrollFeedbackController controller(PayrollFeedbackService f,PayrollService p,LoanRecordingService l,ApplicationClock c,org.springframework.context.support.ResourceBundleMessageSource messages){return new PayrollFeedbackController(f,p,l,c,messages);}
    }
}
