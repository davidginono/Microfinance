package com.sacco.mvp.accounting.business.controller;

import com.sacco.mvp.accounting.business.dto.BusinessAccountingDtos.*;
import com.sacco.mvp.accounting.business.service.BusinessAccountingService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BusinessAccountingPageControllerTest {
    private AnnotationConfigWebApplicationContext context;
    private BusinessAccountingService service;
    private MockMvc mvc;
    @BeforeEach void start() {
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        context.register(Config.class);context.refresh();service=context.getBean(BusinessAccountingService.class);
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void stop(){context.close();}
    @Test void missingReleaseRetainsSubmittedSourceAndIndependentEvidence() throws Exception {
        var actor=actor(EnumSet.allOf(UserClaim.class));UUID id=UUID.randomUUID();var document=document(id);
        when(service.approveAndPost(any(),any(),anyString(),anyBoolean())).thenThrow(new IllegalArgumentException("accounting.release.error.restricted"));
        when(service.view(actor,id)).thenReturn(document);
        mvc.perform(post("/finance/business/"+id+"/post").with(user(actor)).with(csrf())
            .param("approvalEvidence","Independent source evidence retained").param("confirmed","true")
            .param("saccoId","FOREIGN").param("stationId","B2").param("releaseApproved","true"))
            .andExpect(status().isOk()).andExpect(view().name("accounting/business/view"))
            .andExpect(model().attribute("sourceError","accounting.release.error.restricted"))
            .andExpect(model().attribute("approvalEvidence","Independent source evidence retained"))
            .andExpect(model().attribute("document",document));
        verify(service).approveAndPost(actor,id,"Independent source evidence retained",true);
    }
    @Test void csrfBlocksEveryMoneyAndSourceMutationBeforeTheService() throws Exception {
        UUID id=UUID.randomUUID();for(String path:List.of("/finance/business","/finance/business/suppliers",
            "/finance/business/"+id+"/submit","/finance/business/"+id+"/post","/finance/business/"+id+"/reject"))
            mvc.perform(post(path).with(user(actor(EnumSet.allOf(UserClaim.class))))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void missingPostingClaimIsDeniedBeforeAnySourceReadOrWrite() throws Exception {
        mvc.perform(post("/finance/business/"+UUID.randomUUID()+"/post")
            .with(user(actor(EnumSet.of(UserClaim.ACCOUNTING_BUSINESS_VIEW)))).with(csrf())
            .param("approvalEvidence","Independent evidence").param("confirmed","true"))
            .andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void failedRejectionAlsoPreservesTheEnteredEvidence() throws Exception {
        var actor=actor(EnumSet.allOf(UserClaim.class));UUID id=UUID.randomUUID();
        when(service.reject(actor,id,"Retained rejection evidence")).thenThrow(new IllegalArgumentException("finance.business.error.state"));
        when(service.view(actor,id)).thenReturn(document(id));
        mvc.perform(post("/finance/business/"+id+"/reject").with(user(actor)).with(csrf())
            .param("approvalEvidence","Retained rejection evidence"))
            .andExpect(status().isOk()).andExpect(view().name("accounting/business/view"))
            .andExpect(model().attribute("sourceError","finance.business.error.state"))
            .andExpect(model().attribute("approvalEvidence","Retained rejection evidence"));
    }
    private Document document(UUID id){
        var command=new Command(UUID.randomUUID(),Kind.CAPITAL_RECEIPT,LocalDate.of(2026,10,1),new BigDecimal("1.01"),
            null,null,null,"Synthetic source","Retained source evidence","SYNTHETIC-REF","CASH",null,null,null,null,null);
        return new Document(id,"I1","B1",UUID.randomUUID(),null,command,"SUBMITTED",UUID.randomUUID(),null,null,
            OffsetDateTime.parse("2026-10-01T09:00:00Z"),null,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
    }
    private AppUserPrincipal actor(Set<UserClaim> claims){return new AppUserPrincipal(Member.builder()
        .id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("SYNTHETIC").fullName("Synthetic")
        .status(MemberStatus.ACTIVE).position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),claims,true);}
    @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean BusinessAccountingService service(){return mock(BusinessAccountingService.class);}
        @Bean BusinessAccountingPageController controller(BusinessAccountingService service){return new BusinessAccountingPageController(service);}
        @Bean(name="access") AccessControlService access(){return new AccessControlService();}
        @Bean SecurityFilterChain filter(HttpSecurity http)throws Exception{return http.authorizeHttpRequests(a->a.anyRequest().authenticated()).build();}
        @Bean InternalResourceViewResolver views(){return new InternalResourceViewResolver("/WEB-INF/jsp/",".jsp");}
    }
}
