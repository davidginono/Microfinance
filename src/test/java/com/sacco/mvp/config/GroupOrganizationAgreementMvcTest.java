package com.sacco.mvp.config;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.controller.GroupOrganizationAgreementController;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,GroupOrganizationAgreementMvcTest.Config.class})
class GroupOrganizationAgreementMvcTest {
    @Autowired WebApplicationContext context;@Autowired GroupOrganizationAgreementService service;@Autowired GroupOrganizationAgreementDeliveryService delivery;@Autowired GroupOrganizationAgreementExportService exports;
    MockMvc mvc;AppUserPrincipal actor;UUID cycle=UUID.randomUUID();String hash="a".repeat(64);
    String path(){return "/app/group-loans/"+cycle+"/organization-agreement";}
    @BeforeEach void setup(){reset(service,delivery,exports);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.MEMBER).status(MemberStatus.ACTIVE).memberAccount(true).build(),EnumSet.allOf(UserClaim.class),false);}
    @Test void signatureAndCodeRequireCsrfAndCannotBindAnotherPerson()throws Exception{
        mvc.perform(post(path()+"/sign").with(user(actor)).param("packetHash",hash)).andExpect(status().isForbidden());
        mvc.perform(post(path()+"/code").with(user(actor)).param("packetHash",hash)).andExpect(status().isForbidden());verifyNoInteractions(service,delivery);
        mvc.perform(post(path()+"/sign").with(user(actor)).with(csrf()).param("packetHash",hash).param("accepted","true").param("otpCode","123456").param("clientId",UUID.randomUUID().toString()).param("signatureText","forged").param("saccoId","OTHER"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path()));verify(service).sign(actor,cycle,hash,true,"123456");
    }
    @Test void failedCodeReturnsRecoverableMessageWithoutClaimingSignature()throws Exception{
        when(service.sign(actor,cycle,hash,true,"123456")).thenReturn("group.error.otpInvalid");
        mvc.perform(post(path()+"/sign").with(user(actor)).with(csrf()).param("packetHash",hash).param("accepted","true").param("otpCode","123456"))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("consentError","group.error.otpInvalid"));
    }
    @Test void revokedScopeCannotRenderOrSignCompanyTerms()throws Exception{
        doThrow(new AccessDeniedException("Synthetic scoped denial")).when(service).packet(actor,cycle,0);
        mvc.perform(get(path()).with(user(actor))).andExpect(status().isForbidden());
        doThrow(new AccessDeniedException("Synthetic scoped denial")).when(service).sign(eq(actor),eq(cycle),anyString(),anyBoolean(),anyString());
        mvc.perform(post(path()+"/sign").with(user(actor)).with(csrf()).param("packetHash",hash)).andExpect(status().isForbidden());
    }
    @Test void deliveryFailureKeepsTheAgreementAndShowsSafeFeedback()throws Exception{
        doThrow(new IllegalArgumentException("group.error.otpDelivery")).when(delivery).send(eq(actor),eq(cycle),eq(hash),any());
        mvc.perform(post(path()+"/code").with(user(actor)).with(csrf()).param("packetHash",hash)).andExpect(status().is3xxRedirection()).andExpect(flash().attribute("consentError","group.error.otpDelivery"));
    }
    @Test void pdfDelegatesToTheScopedFullDocumentUseCase()throws Exception{
        mvc.perform(get(path()+"/pdf").with(user(actor))).andExpect(status().isMethodNotAllowed());
        mvc.perform(post(path()+"/pdf").with(user(actor))).andExpect(status().isForbidden());verifyNoInteractions(exports);
        when(exports.client(eq(actor),eq(cycle),any())).thenReturn("%PDF-test".getBytes());mvc.perform(post(path()+"/pdf").with(user(actor)).with(csrf())).andExpect(status().isOk()).andExpect(header().string("Content-Type","application/pdf")).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("Content-Disposition","attachment; filename=organization-agreement.pdf"));
        verify(exports).client(eq(actor),eq(cycle),any());reset(exports);when(exports.client(any(),any(),any())).thenThrow(new AccessDeniedException("Synthetic missing export permission"));mvc.perform(post(path()+"/pdf").with(user(actor)).with(csrf())).andExpect(status().isForbidden());
    }
    @Configuration(proxyBeanMethods=false) @EnableWebMvc static class Config {
        @Bean GroupOrganizationAgreementService signing(){return mock(GroupOrganizationAgreementService.class);}
        @Bean GroupOrganizationAgreementDeliveryService delivery(){return mock(GroupOrganizationAgreementDeliveryService.class);}
        @Bean GroupOrganizationAgreementExportService exports(){return mock(GroupOrganizationAgreementExportService.class);}
        @Bean GroupOrganizationAgreementController controller(GroupOrganizationAgreementService s,GroupOrganizationAgreementDeliveryService d,GroupOrganizationAgreementExportService e){return new GroupOrganizationAgreementController(s,d,e);}
    }
}
