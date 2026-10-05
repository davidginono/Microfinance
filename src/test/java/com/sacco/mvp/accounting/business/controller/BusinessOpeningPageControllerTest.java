package com.sacco.mvp.accounting.business.controller;

import com.sacco.mvp.accounting.business.dto.BusinessOpeningDtos.*;
import com.sacco.mvp.accounting.business.service.BusinessOpeningService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.*;
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
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BusinessOpeningPageControllerTest {
    private AnnotationConfigWebApplicationContext context;
    private BusinessOpeningService service;
    private MockMvc mvc;
    @BeforeEach void start(){context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();service=context.getBean(BusinessOpeningService.class);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    @AfterEach void stop(){context.close();}
    private AppUserPrincipal actor(Set<UserClaim> claims){return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("SYNTHETIC").fullName("Synthetic").status(MemberStatus.ACTIVE).position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),claims,true);}
    private Opening opening(UUID id){var c=new Command(UUID.randomUUID(),UUID.randomUUID(),null,LocalDate.of(2026,10,1),"SUPPLIER_PAYABLE","Synthetic register",false);return new Opening(id,"I1","B1",UUID.randomUUID(),new Preview(c,"opening.csv","0".repeat(64),List.of(),new BigDecimal("0.00")),"1".repeat(64),OffsetDateTime.parse("2026-10-01T09:00:00Z"),null,null,null,null);}
    @Test void csrfBlocksImportsPreviewsAndReviewBeforeAnyServiceAccess()throws Exception{
        var actor=actor(EnumSet.allOf(UserClaim.class));mvc.perform(multipart("/finance/business/openings").file(new MockMultipartFile("file","opening.csv","text/csv","reference,signed_balance,evidence".getBytes(StandardCharsets.UTF_8))).with(user(actor))).andExpect(status().isForbidden());mvc.perform(post("/finance/business/openings/"+UUID.randomUUID()+"/review").with(user(actor))).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void institutionFinanceScopeDoesNotGrantRawSourceFileOrReviewAccess()throws Exception{
        var actor=actor(EnumSet.of(UserClaim.FINANCIAL_REPORTS_VIEW,UserClaim.FINANCIAL_REPORTS_INSTITUTION));UUID id=UUID.randomUUID();mvc.perform(get("/finance/business/openings/"+id+"/file").with(user(actor))).andExpect(status().isForbidden());mvc.perform(post("/finance/business/openings/"+id+"/review").with(user(actor)).with(csrf()).param("decision","APPROVED").param("evidence","Foreign permission").param("confirmed","true")).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void reviewGatePreservesEnteredEvidenceAndPendingSourceInsteadOfSuccess()throws Exception{
        var actor=actor(EnumSet.allOf(UserClaim.class));UUID id=UUID.randomUUID();var o=opening(id);when(service.view(actor,id)).thenReturn(o);when(service.review(actor,id,"APPROVED","Retained independent evidence",true)).thenThrow(new DataIntegrityViolationException("Synthetic GL mismatch"));mvc.perform(post("/finance/business/openings/"+id+"/review").with(user(actor)).with(csrf()).param("decision","APPROVED").param("evidence","Retained independent evidence").param("confirmed","true")).andExpect(status().isOk()).andExpect(view().name("accounting/business/openings/view")).andExpect(model().attribute("opening",o)).andExpect(model().attribute("openingError","finance.business.opening.error.review")).andExpect(model().attribute("reviewEvidence","Retained independent evidence"));
    }
    @Test void scopeAndOpeningIdsCannotBeOverpostedThroughTheUploadForm()throws Exception{
        var actor=actor(EnumSet.allOf(UserClaim.class));UUID key=UUID.randomUUID();var o=opening(UUID.randomUUID());var c=o.preview().command();when(service.command(actor,key,"2100",LocalDate.of(2026,10,1),"SUPPLIER_PAYABLE","Retained verified register",true)).thenReturn(c);when(service.importFile(eq(actor),eq(c),any())).thenReturn(o);mvc.perform(multipart("/finance/business/openings").file(new MockMultipartFile("file","opening.csv","text/csv","reference,signed_balance,evidence".getBytes(StandardCharsets.UTF_8))).with(user(actor)).with(csrf()).param("requestKey",key.toString()).param("accountCode","2100").param("through","2026-10-01").param("purpose","SUPPLIER_PAYABLE").param("evidence","Retained verified register").param("completeCoverage","true").param("saccoId","FOREIGN").param("stationId","B2").param("generalLedgerOpening",UUID.randomUUID().toString()).param("decision","APPROVED")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/finance/business/openings/"+o.id()));verify(service).command(actor,key,"2100",LocalDate.of(2026,10,1),"SUPPLIER_PAYABLE","Retained verified register",true);verify(service).importFile(eq(actor),eq(c),any());verify(service,never()).review(any(),any(),anyString(),anyString(),anyBoolean());
    }
    @Test void sourceDownloadIsAnAttachmentWithNoStoreAndFreshActorDelegation()throws Exception{
        var actor=actor(EnumSet.of(UserClaim.ACCOUNTING_BUSINESS_VIEW));UUID id=UUID.randomUUID();byte[] bytes="reference,signed_balance,evidence\n".getBytes(StandardCharsets.UTF_8);when(service.file(actor,id)).thenReturn(new File("opening.csv",bytes,"0".repeat(64)));mvc.perform(get("/finance/business/openings/"+id+"/file").with(user(actor))).andExpect(status().isOk()).andExpect(content().bytes(bytes)).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("attachment")));verify(service).file(actor,id);
    }
    @Test void readonlyPreviewDoesNotImportSourceEvidence()throws Exception{
        var actor=actor(EnumSet.allOf(UserClaim.class));UUID key=UUID.randomUUID();var o=opening(UUID.randomUUID());when(service.command(eq(actor),eq(key),anyString(),any(),anyString(),anyString(),anyBoolean())).thenReturn(o.preview().command());when(service.preview(eq(actor),eq(o.preview().command()),any())).thenReturn(o.preview());mvc.perform(multipart("/finance/business/openings").file(new MockMultipartFile("file","opening.csv","text/csv","reference,signed_balance,evidence".getBytes(StandardCharsets.UTF_8))).with(user(actor)).with(csrf()).param("requestKey",key.toString()).param("accountCode","2100").param("through","2026-10-01").param("purpose","SUPPLIER_PAYABLE").param("evidence","Synthetic preview").param("preview","true")).andExpect(status().isOk()).andExpect(view().name("accounting/business/openings/new")).andExpect(model().attribute("preview",o.preview()));verify(service,never()).importFile(any(),any(),any());
    }
    @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean BusinessOpeningService service(){return mock(BusinessOpeningService.class);}
        @Bean BusinessOpeningPageController controller(BusinessOpeningService service){return new BusinessOpeningPageController(service);}
        @Bean(name="access") AccessControlService access(){return new AccessControlService();}
        @Bean SecurityFilterChain filter(HttpSecurity http)throws Exception{return http.authorizeHttpRequests(a->a.anyRequest().authenticated()).build();}
        @Bean InternalResourceViewResolver views(){return new InternalResourceViewResolver("/WEB-INF/jsp/",".jsp");}
    }
}
