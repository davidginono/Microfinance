package com.sacco.mvp.config;

import com.sacco.mvp.accounting.payroll.controller.PayrollController;
import com.sacco.mvp.accounting.payroll.dto.PayrollDtos.*;
import com.sacco.mvp.accounting.payroll.service.*;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,PayrollMvcTest.Config.class})
class PayrollMvcTest {
    @Autowired WebApplicationContext context;@Autowired PayrollService payroll;MockMvc mvc;
    AppUserPrincipal actor(Set<UserClaim> claims){return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),claims,true);}
    @BeforeEach void setup(){reset(payroll);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    @Test void requiresClaimAndCsrfAndBindsOnlyCommandFields()throws Exception {
        var permitted=actor(EnumSet.allOf(UserClaim.class));
        mvc.perform(get("/finance/payroll").with(user(actor(Set.of())))).andExpect(status().isForbidden());verifyNoInteractions(payroll);
        mvc.perform(post("/finance/payroll/freeze").with(user(permitted))).andExpect(status().isForbidden());verifyNoInteractions(payroll);
        var id=UUID.randomUUID();when(payroll.freeze(eq(permitted),any())).thenReturn(id);
        mvc.perform(post("/finance/payroll/freeze").with(user(permitted)).with(csrf()).param("employerId",UUID.randomUUID().toString())
            .param("month","2026-10").param("deductionDate","2026-10-31").param("saccoId","OTHER").param("stationId","FOREIGN").param("amount","1.00"))
            .andExpect(redirectedUrl("/finance/payroll/"+id));verify(payroll).freeze(eq(permitted),any(BatchForm.class));
    }
    @Test void exportUsesSeparateClaimAndRejectsUnsupportedFormats()throws Exception {
        var permitted=actor(Set.of(UserClaim.PAYROLL_DEDUCTIONS_VIEW));
        doThrow(new org.springframework.security.access.AccessDeniedException("Export unavailable")).when(payroll).authorize(permitted,"EXPORT");
        mvc.perform(get("/finance/payroll/"+UUID.randomUUID()+"/pdf").with(user(permitted))).andExpect(status().isForbidden());
        mvc.perform(get("/finance/payroll/"+UUID.randomUUID()+"/csv").with(user(permitted))).andExpect(status().isNotFound());
    }
    @Test void printAndDownloadReturnTheSameRetainedDocumentWithProtectedHeaders()throws Exception {
        var actor=actor(EnumSet.allOf(UserClaim.class));var id=UUID.randomUUID();var bytes="%PDF-test-document".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var document=new Document(bytes,PayrollService.hash(bytes));
        when(payroll.document(actor,id,"en")).thenReturn(Optional.of(document));when(payroll.downloaded(actor,id,"en",document)).thenReturn(document);
        for(var output:List.of("pdf","print"))mvc.perform(get("/finance/payroll/"+id+"/"+output).with(user(actor)).locale(Locale.ENGLISH))
            .andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(content().bytes(bytes))
            .andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(header().string("Content-Disposition",(output.equals("print")?"inline":"attachment")+"; filename=payroll-"+id+".pdf"));
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor accessAlias(){return factory->factory.registerAlias("accessControlService","access");}
        @Bean PayrollService payroll(){return mock(PayrollService.class);}
        @Bean PayrollPdfService pdf(){return mock(PayrollPdfService.class);}
        @Bean ApplicationClock clock(){return new ApplicationClock("Africa/Nairobi");}
        @Bean ResourceBundleMessageSource messages(){var m=new ResourceBundleMessageSource();m.setBasename("messages");m.setDefaultEncoding("UTF-8");return m;}
        @Bean PayrollController controller(PayrollService p,PayrollPdfService pdf,ApplicationClock clock,ResourceBundleMessageSource messages){return new PayrollController(p,pdf,clock,messages);}
    }
}
