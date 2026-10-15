package com.sacco.mvp.config;

import com.sacco.mvp.accounting.loans.controller.LoanRecordingController;
import com.sacco.mvp.accounting.loans.service.*;
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
import org.springframework.mock.web.MockMultipartFile;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({LoginCsrfAccessDeniedTest.TestConfig.class,LoanRecordingMvcTest.Config.class})
class LoanRecordingMvcTest {
    @Autowired WebApplicationContext context;
    @Autowired LoanRecordingService service;
    MockMvc mvc;AppUserPrincipal actor;
    @BeforeEach void setup(){reset(service);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("SYNTHETIC").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),true);}
    @Test void quoteRequiresCsrfAndDoesNotAcceptAFirstDueDateOrRequestedPrincipal() throws Exception {
        when(service.interestQuote(any(),any())).thenReturn(new LoanRecordingService.Quote("Customised loan",new BigDecimal("0.12"),"FLAT_RATE","MONTHLY",new BigDecimal("176.67"),new BigDecimal("60.00"),List.of(),"MONTHS",6));
        mvc.perform(post("/finance/loan-recording/quote").with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(post("/finance/loan-recording/quote").with(user(actor)).with(csrf()).param("loanMode","CUSTOM").param("principal","1000.00").param("tenureUnit","MONTHS").param("tenureValue","6").param("interestBasis","ANNUAL").param("interestPercent","12").param("customMethod","REDUCING_BALANCE").param("requestedPrincipal","9999").param("firstPaymentDate","2026-12-01"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.interest").value("60.00"));
        verify(service).interestQuote(eq(actor),argThat(f->f.getRequestedPrincipal()==null && f.getFirstPaymentDate()==null && Integer.valueOf(6).equals(f.getTenureValue()) && "ANNUAL".equals(f.getInterestBasis()) && f.getCustomMethod()==InterestMethod.REDUCING_BALANCE));
    }
    @Test void interestResponsePreservesCentsBeyondTheJavascriptIntegerRange() throws Exception {
        var payment=new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ScheduleRow(1,null,new BigDecimal("1.00"),new BigDecimal("9007199254740991.01"),new BigDecimal("9007199254740992.01"));
        when(service.interestQuote(any(),any())).thenReturn(new LoanRecordingService.Quote("Customised loan",BigDecimal.ZERO,"FLAT_RATE","MONTHLY",payment.amount(),payment.interest(),List.of(payment),"MONTHS",1));
        mvc.perform(post("/finance/loan-recording/quote").with(user(actor)).with(csrf()).param("principal","1000.00"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.interest").value("9007199254740991.01"))
            .andExpect(jsonPath("$.schedule[0].number").value(1)).andExpect(jsonPath("$.schedule[0].interest").value("9007199254740991.01"))
            .andExpect(jsonPath("$.schedule[0].amount").value("9007199254740992.01"));
    }
    @Test void multipartRecordingPassesDocumentsAndRetainsValuesOnInvalidInput() throws Exception {
        var id=UUID.randomUUID();when(service.save(any(),any(),any())).thenReturn(id);var file=new MockMultipartFile("attachments","proof.pdf","application/pdf","%PDF synthetic".getBytes());
        mvc.perform(multipart("/finance/loan-records").file(file).with(user(actor)).with(csrf()).param("principal","1000.00").param("loanMode","CUSTOM").param("tenureValue","10").param("tenureUnit","DAYS"))
            .andExpect(redirectedUrl("/finance/loan-records/"+id));verify(service).save(eq(actor),argThat(f->Integer.valueOf(10).equals(f.getTenureValue())),argThat(files->files.size()==1 && "proof.pdf".equals(files.getFirst().getOriginalFilename())));
        mvc.perform(multipart("/finance/loan-records").with(user(actor)).with(csrf()).param("principal","invalid").param("purpose","Business stock"))
            .andExpect(view().name("accounting/loans/record-form")).andExpect(model().attributeHasFieldErrors("recordForm","principal"));
    }
    @Test void documentDownloadChecksScopeThroughServiceAndPreventsSniffing() throws Exception {
        var id=UUID.randomUUID();when(service.attachment(eq(actor),eq(id),eq("proof"))).thenReturn(new LoanAttachmentService.AttachmentResource("%PDF synthetic".getBytes(),"proof.pdf","application/pdf"));
        mvc.perform(get("/finance/loan-records/"+id+"/attachments/proof").with(user(actor))).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(content().contentType("application/pdf"));
    }
    @Test void obsoleteDisbursementEntryRedirectsToReviewAndCannotPost() throws Exception {
        mvc.perform(get("/finance/loan-disbursements/new").with(user(actor))).andExpect(redirectedUrl("/disbursement/loan-applications"));
        mvc.perform(post("/finance/loan-disbursements").with(user(actor)).with(csrf())).andExpect(status().isGone());
        verify(service).authorize(actor,"VIEW");verify(service,never()).disburse(any(),any(),any());
    }
    @Test void historicalRecordedApplicationsRequireCsrfAndSubmitThroughScopedService() throws Exception {
        var id=UUID.randomUUID();
        mvc.perform(post("/finance/loan-records/"+id+"/submit-for-review").with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(post("/finance/loan-records/"+id+"/submit-for-review").with(user(actor)).with(csrf())).andExpect(redirectedUrl("/finance/loan-records/"+id));
        verify(service).submitRecordedApplication(actor,id);
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean("access") @Primary AccessControlService access(){return new AccessControlService();}
        @Bean LoanRecordingService service(){return mock(LoanRecordingService.class);}
        @Bean LoanRecordingController controller(LoanRecordingService service){return new LoanRecordingController(service,mock(LoanRecordingPdfService.class),mock(ApplicationClock.class),new org.springframework.context.support.StaticMessageSource(),mock(SaccoLogoStorageService.class));}
    }
}
