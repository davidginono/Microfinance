package com.sacco.mvp.config;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.controller.GroupOrganizationPayoutController;
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

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,GroupOrganizationPayoutMvcTest.Config.class})
class GroupOrganizationPayoutMvcTest {
    @Autowired WebApplicationContext context;@Autowired GroupOrganizationPayoutService service;@Autowired GroupOrganizationPayoutDeliveryService delivery;
    MockMvc mvc;AppUserPrincipal actor;UUID cycle=UUID.randomUUID();String hash="a".repeat(64);
    String path(){return "/app/group-loans/"+cycle+"/organization-payout";}
    @BeforeEach void setup(){reset(service,delivery);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.MEMBER).status(MemberStatus.ACTIVE).memberAccount(true).build(),EnumSet.allOf(UserClaim.class),false);}
    @Test void confirmationAndCodeRequireCsrf()throws Exception{
        mvc.perform(post(path()+"/confirm").with(user(actor)).param("packetHash",hash)).andExpect(status().isForbidden());
        mvc.perform(post(path()+"/code").with(user(actor)).param("packetHash",hash)).andExpect(status().isForbidden());verifyNoInteractions(service,delivery);
    }
    @Test void onlyHashAcceptanceAndCodeReachTheUseCase()throws Exception{
        mvc.perform(post(path()+"/confirm").with(user(actor)).with(csrf()).param("packetHash",hash).param("accepted","true").param("otpCode","123456").param("clientId",UUID.randomUUID().toString()).param("netAmount","1").param("accountIdentifier","FORGED").param("signatureText","forged").param("saccoId","OTHER"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path()));verify(service).confirm(actor,cycle,hash,true,"123456");
    }
    @Test void failedCodeReturnsRecoverableFeedback()throws Exception{
        when(service.confirm(actor,cycle,hash,true,"123456")).thenReturn("group.error.otpInvalid");mvc.perform(post(path()+"/confirm").with(user(actor)).with(csrf()).param("packetHash",hash).param("accepted","true").param("otpCode","123456"))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("payoutError","group.error.otpInvalid"));
    }
    @Test void scopedDenialsNeverRenderThePayoutDestination()throws Exception{
        when(service.packet(actor,cycle)).thenThrow(new AccessDeniedException("Synthetic named authority denied"));mvc.perform(get(path()).with(user(actor))).andExpect(status().isForbidden());
        when(service.confirm(eq(actor),eq(cycle),anyString(),anyBoolean(),anyString())).thenThrow(new AccessDeniedException("Synthetic named authority denied"));mvc.perform(post(path()+"/confirm").with(user(actor)).with(csrf()).param("packetHash",hash)).andExpect(status().isForbidden());
    }
    @Test void deliveryFailureDoesNotClaimConfirmation()throws Exception{
        doThrow(new IllegalArgumentException("group.error.otpDelivery")).when(delivery).send(eq(actor),eq(cycle),eq(hash),any());mvc.perform(post(path()+"/code").with(user(actor)).with(csrf()).param("packetHash",hash)).andExpect(status().is3xxRedirection()).andExpect(flash().attribute("payoutError","group.error.otpDelivery"));
    }
    @Configuration(proxyBeanMethods=false) @EnableWebMvc static class Config {
        @Bean GroupOrganizationPayoutService payouts(){return mock(GroupOrganizationPayoutService.class);}
        @Bean GroupOrganizationPayoutDeliveryService delivery(){return mock(GroupOrganizationPayoutDeliveryService.class);}
        @Bean GroupOrganizationPayoutController controller(GroupOrganizationPayoutService s,GroupOrganizationPayoutDeliveryService d){return new GroupOrganizationPayoutController(s,d);}
    }
}
