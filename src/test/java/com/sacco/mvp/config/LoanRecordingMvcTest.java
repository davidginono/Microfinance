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

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,LoanRecordingMvcTest.Config.class})
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
        var payment=new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ScheduleRow(1,null,new BigDecimal("1.00"),new BigDecimal("9007199254740991.01"),new BigDecimal("9007199254740992.01"),new BigDecimal("9007199254740992.01"),new BigDecimal("0.00"));
        when(service.interestQuote(any(),any())).thenReturn(new LoanRecordingService.Quote("Customised loan",BigDecimal.ZERO,"FLAT_RATE","MONTHLY",payment.amount(),payment.interest(),List.of(payment),"MONTHS",1));
        mvc.perform(post("/finance/loan-recording/quote").with(user(actor)).with(csrf()).param("principal","1000.00"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.interest").value("9007199254740991.01"))
            .andExpect(jsonPath("$.schedule[0].number").value(1)).andExpect(jsonPath("$.schedule[0].interest").value("9007199254740991.01"))
            .andExpect(jsonPath("$.schedule[0].amount").value("9007199254740992.01"))
            .andExpect(jsonPath("$.schedule[0].beginningBalance").value("9007199254740992.01")).andExpect(jsonPath("$.schedule[0].endingBalance").value("0.00"));
    }
    @Test void multipartRecordingPassesDocumentsAndRetainsValuesOnInvalidInput() throws Exception {
        var id=UUID.randomUUID();when(service.save(any(),any(),any())).thenReturn(id);var file=new MockMultipartFile("attachments","proof.pdf","application/pdf","%PDF synthetic".getBytes());
        mvc.perform(multipart("/finance/loan-records").file(file).with(user(actor)).with(csrf()).param("principal","1000.00").param("loanMode","CUSTOM").param("tenureValue","10").param("tenureUnit","DAYS"))
            .andExpect(redirectedUrl("/finance/loan-records/"+id));verify(service).save(eq(actor),argThat(f->Integer.valueOf(10).equals(f.getTenureValue())),argThat(files->files.size()==1 && "proof.pdf".equals(files.getFirst().getOriginalFilename())));
        mvc.perform(multipart("/finance/loan-records").with(user(actor)).with(csrf()).param("principal","invalid").param("purpose","Business stock"))
            .andExpect(view().name("accounting/loans/record-form")).andExpect(model().attributeHasFieldErrors("recordForm","principal"));
    }
    @Test void productChoicesReturnExactLimitsAndTenureUnitWithoutAnotherLookup() throws Exception {
        var id=UUID.randomUUID();
        when(service.choices(actor,"products","",0)).thenReturn(List.of(new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice(id,"Day loan","","DAYS","1000.00","9007199254740991.01",7,30)));
        mvc.perform(get("/finance/loan-recording/choices/products").with(user(actor)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id.toString()))
            .andExpect(jsonPath("$[0].tenureUnit").value("DAYS"))
            .andExpect(jsonPath("$[0].minimumAmount").value("1000.00")).andExpect(jsonPath("$[0].maximumAmount").value("9007199254740991.01"))
            .andExpect(jsonPath("$[0].minimumTenure").value(7)).andExpect(jsonPath("$[0].maximumTenure").value(30));
    }
    @Test void invalidRecordingRetainsSelectedProductLimitsOutsideTheFirstLookupPage() throws Exception {
        var id=UUID.randomUUID();var product=new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice(id,"Day loan","","DAYS","500.00","5000.00",7,30);
        when(service.recordProduct(actor,id)).thenReturn(product);
        mvc.perform(multipart("/finance/loan-records").with(user(actor)).with(csrf()).param("loanMode","CONFIGURED").param("productId",id.toString()).param("principal","invalid"))
            .andExpect(view().name("accounting/loans/record-form")).andExpect(model().attributeHasFieldErrors("recordForm","principal"))
            .andExpect(model().attribute("selectedProduct",product));
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
    @Test void topUpPreviewIsScopedUncacheableAndPreservesDecimalStrings() throws Exception {
        var source=UUID.randomUUID();var date=java.time.LocalDate.of(2026,10,8);
        when(service.topUpQuote(actor,source,date,null)).thenReturn(new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.TopUpQuote(source,"202600001",UUID.randomUUID(),"Synthetic client",date,new BigDecimal("1000.00"),new BigDecimal("20.00"),new BigDecimal("50.00"),3));
        mvc.perform(get("/finance/loan-recording/top-up-quote").with(user(actor)).param("source",source.toString()).param("date",date.toString()).param("principal","9007199254740992.01"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.principal").value("1000.00")).andExpect(jsonPath("$.cash").value("9007199254739972.01"));
        verify(service).authorize(actor,"CREATE");verify(service).topUpQuote(actor,source,date,null);
    }
    @Test void topUpCorrectionRequiresCsrfAndDelegatesTheLinkedCommand() throws Exception {
        var id=UUID.randomUUID();var key=UUID.randomUUID();
        mvc.perform(post("/finance/loan-records/"+id+"/reverse-top-up").with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(post("/finance/loan-records/"+id+"/reverse-top-up").with(user(actor)).with(csrf()).param("requestKey",key.toString()).param("date","2026-10-08").param("reason","Correct transfer").param("evidence","Synthetic bank evidence"))
            .andExpect(redirectedUrl("/finance/loan-records/"+id));
        verify(service).reverseTopUp(actor,id,key,java.time.LocalDate.of(2026,10,8),"Correct transfer","Synthetic bank evidence");
    }
    @Test void chargeAssessmentRequiresCsrfAndBindsOnlyEvidenceCommand() throws Exception {
        var id=UUID.randomUUID();var key=UUID.randomUUID();var charges=context.getBean(com.sacco.mvp.accounting.charges.service.LoanChargeService.class);reset(charges);
        mvc.perform(post("/finance/loan-records/"+id+"/charges/assess").with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(post("/finance/loan-records/"+id+"/charges/assess").with(user(actor)).with(csrf()).param("requestKey",key.toString()).param("date","2026-09-01").param("evidence","Synthetic signed agreement").param("amount","1.00").param("saccoId","FOREIGN"))
            .andExpect(redirectedUrl("/finance/loan-records/"+id));verify(charges).assess(eq(actor),eq(id),argThat(f->f.getRequestKey().equals(key) && f.getDate().equals(java.time.LocalDate.of(2026,9,1)) && f.getEvidence().equals("Synthetic signed agreement")));
    }
    @Test void chargePolicyUsesIsoDatesAndSessionScope() throws Exception {
        var product=UUID.randomUUID();var charges=context.getBean(com.sacco.mvp.accounting.charges.service.LoanChargeService.class);reset(charges);
        mvc.perform(post("/finance/loan-recording/charge-policy").with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(post("/finance/loan-recording/charge-policy").with(user(actor)).with(csrf()).param("productId",product.toString()).param("effectiveFrom","2026-09-01").param("enabled","true").param("managementTiming","DEDUCT").param("insuranceTiming","SEPARATE").param("saccoId","FOREIGN"))
            .andExpect(redirectedUrl("/finance/loan-recording/charge-policy?productId="+product));verify(charges).savePolicy(eq(actor),argThat(f->f.getProductId().equals(product) && f.getEffectiveFrom().equals(java.time.LocalDate.of(2026,9,1)) && f.getManagementTiming()==com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Timing.DEDUCT));
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean("access") @Primary AccessControlService access(){return new AccessControlService();}
        @Bean com.sacco.mvp.accounting.charges.service.LoanChargeService charges(){return mock(com.sacco.mvp.accounting.charges.service.LoanChargeService.class);}
        @Bean com.sacco.mvp.accounting.charges.controller.LoanChargeController chargeController(com.sacco.mvp.accounting.charges.service.LoanChargeService charges){return new com.sacco.mvp.accounting.charges.controller.LoanChargeController(charges,mock(ApplicationClock.class));}
        @Bean LoanRecordingService service(){return mock(LoanRecordingService.class);}
        @Bean LoanRecordingController controller(LoanRecordingService service){return new LoanRecordingController(service,mock(LoanRecordingPdfService.class),mock(ApplicationClock.class),new org.springframework.context.support.StaticMessageSource(),mock(SaccoLogoStorageService.class));}
    }
}
