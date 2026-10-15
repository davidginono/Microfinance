package com.sacco.mvp.config;

import com.sacco.mvp.accounting.batch.controller.ReceiptBatchController;
import com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.*;
import com.sacco.mvp.accounting.batch.service.ReceiptBatchService;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.repayment.service.RepaymentPolicyService;
import com.sacco.mvp.accounting.service.LoanReceiptService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({LoginCsrfAccessDeniedTest.TestConfig.class,ReceiptBatchMvcTest.Config.class})
class ReceiptBatchMvcTest {
    @Autowired WebApplicationContext context;
    @Autowired ReceiptBatchService batches;
    MockMvc mvc;AppUserPrincipal actor;UUID batch=UUID.randomUUID(),item=UUID.randomUUID();
    @BeforeEach void setup(){reset(batches);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();var member=Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("TEST").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build();actor=new AppUserPrincipal(member,EnumSet.allOf(UserClaim.class),true);}
    @Test void mutationsRequireCsrfAndClientSessionsCannotAccessWorkspace()throws Exception{
        mvc.perform(put("/finance/batches/receipts/"+batch+"/items/"+item).with(user(actor)).contentType("application/json").content("{\"expectedVersion\":0,\"form\":{}}" )).andExpect(status().isForbidden());
        var client=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").status(MemberStatus.ACTIVE).build(),Set.of(),false);
        mvc.perform(get("/finance/batches/receipts").with(user(client))).andExpect(status().isForbidden());verifyNoInteractions(batches);
    }
    @Test void jsonMoneyKeepsExactDecimalInputAndScopeComesFromPrincipal()throws Exception{
        mvc.perform(put("/finance/batches/receipts/"+batch+"/items/"+item).with(user(actor)).with(csrf()).contentType("application/json").content("{\"expectedVersion\":2,\"form\":{\"rows\":[{\"amount\":\"9999999999999999.99\"}]}}" )).andExpect(status().isOk());
        verify(batches).save(eq(actor),eq(batch),eq(item),argThat(f->f.expectedVersion()==2 && f.form().getRows().getFirst().getAmount().toPlainString().equals("9999999999999999.99")));
    }
    @Test void postingUsesReviewedVersionAndDoesNotInvokeAnApprovalWorkflow()throws Exception{
        var b=new Batch(batch,"I1","B1",actor.getMemberId(),"Collections",7,"POSTING",false,null,OffsetDateTime.now());when(batches.get(actor,batch)).thenReturn(new Summary(b,2,0,2,0,new BigDecimal("3.03"),BigDecimal.ZERO));
        mvc.perform(post("/finance/batches/receipts/"+batch+"/post?version=6").with(user(actor)).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.remainingTotal").value("3.03")).andExpect(header().string("Cache-Control","no-store"));verify(batches).start(actor,batch,6,true);
    }
    @Test void validationErrorsAreLocalisedAndDoNotExposeExceptions()throws Exception{
        doThrow(new IllegalArgumentException("batch.error.changed")).when(batches).start(actor,batch,0,true);
        mvc.perform(post("/finance/batches/receipts/"+batch+"/post?version=0").with(user(actor)).with(csrf()).locale(Locale.forLanguageTag("sw"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("batch.error.changed")).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("imebadilika")));
    }
    @Test void oversizedChunkedAndDeclaredBodiesAreRejectedBeforeBinding()throws Exception{
        var filter=new com.sacco.mvp.accounting.batch.controller.ReceiptBatchBodyLimit();
        for(boolean chunked:List.of(false,true)){
            var request=new org.springframework.mock.web.MockHttpServletRequest("PUT","/finance/batches/receipts/"+batch+"/items/"+item){@Override public long getContentLengthLong(){return chunked?-1:super.getContentLengthLong();}};
            request.setContentType("application/json");request.setContent(new byte[128*1024+1]);var response=new org.springframework.mock.web.MockHttpServletResponse();var chain=new org.springframework.mock.web.MockFilterChain();filter.doFilter(request,response,chain);
            org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(413);org.assertj.core.api.Assertions.assertThat(chain.getRequest()).isNull();
        }
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean("access") @Primary com.sacco.mvp.service.AccessControlService access(){return new com.sacco.mvp.service.AccessControlService();}
        @Bean ReceiptBatchService batches(){return mock(ReceiptBatchService.class);}
        @Bean LoanReceiptService receipts(){return mock(LoanReceiptService.class);}
        @Bean LoanRecordingService loans(){return mock(LoanRecordingService.class);}
        @Bean RepaymentPolicyService policies(){return mock(RepaymentPolicyService.class);}
        @Bean ApplicationClock clock(){return mock(ApplicationClock.class);}
        @Bean ResourceBundleMessageSource messageSource(){var m=new ResourceBundleMessageSource();m.setBasename("messages");m.setDefaultEncoding("UTF-8");return m;}
        @Bean ReceiptBatchController controller(ReceiptBatchService batches,LoanReceiptService receipts,LoanRecordingService loans,RepaymentPolicyService policies,ApplicationClock clock,ResourceBundleMessageSource messages){return new ReceiptBatchController(batches,receipts,loans,policies,clock,messages);}
    }
}
