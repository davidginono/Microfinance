package com.sacco.mvp.config;

import com.sacco.mvp.clients.portal.controller.ClientPortalController;
import com.sacco.mvp.clients.portal.dto.ClientPortalDtos.Session;
import com.sacco.mvp.clients.portal.service.ClientPortalService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,ClientPortalSecurityMvcTest.Config.class})
class ClientPortalSecurityMvcTest {
    @Autowired WebApplicationContext context;@Autowired ClientPortalService portal;MockMvc mvc;
    Session session(){return new Session(UUID.randomUUID(),"I1","B1","fingerprint",true,OffsetDateTime.now().plusMinutes(15));}
    @BeforeEach void setup(){reset(portal);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    @Test void publicEntryIsVisibleAndAllOtpPostsRequireCsrf()throws Exception{
        mvc.perform(get("/client-portal")).andExpect(status().isOk()).andExpect(view().name("portal/entry"));
        mvc.perform(post("/client-portal/request-code")).andExpect(status().isForbidden());mvc.perform(post("/client-portal/verify").param("code","123456")).andExpect(status().isForbidden());verifyNoInteractions(portal);
    }
    @Test void provisionalAuthenticationIsRestrictedEvenOnOtherwiseAuthenticatedRoutes()throws Exception{
        var auth=UsernamePasswordAuthenticationToken.authenticated(session(),null,List.of());
        for(String route:List.of("/app/dashboard","/app/settings","/admin/users","/finance/private","/documents/loan-applications/secret"))mvc.perform(get(route).with(authentication(auth))).andExpect(status().isForbidden());
        mvc.perform(get("/client-portal/workspace/probe").with(authentication(auth))).andExpect(status().isOk()).andExpect(content().string("own portal"));
        mvc.perform(get("/client-portal/workspace/probe")).andExpect(status().is3xxRedirection());
    }
    @Test void publicHomeRemainsAccessibleToAnonymousAndProvisionalClients()throws Exception{
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string("public home"));
        var auth=UsernamePasswordAuthenticationToken.authenticated(session(),null,List.of());
        mvc.perform(get("/").with(authentication(auth))).andExpect(status().isOk()).andExpect(content().string("public home"));
    }
    @Test void registrationBindsProfileDetailsWithoutAcceptingPrivateOrStaffFields()throws Exception{
        mvc.perform(post("/client-portal/request-code").with(csrf()).param("intent","REGISTER")
            .param("registration.profile.hasBusiness","false")
            .param("registration.profile.businessName","Synthetic shop").param("registration.profile.dailySales","12500.50")
            .param("registration.profile.spouseName","Synthetic spouse").param("registration.profile.bankAccountNumber","12345678")
            .param("registration.profile.groupId",UUID.randomUUID().toString()).param("registration.profile.groupName","Forged group")
            .param("registration.profile.lawfulBasis","LEGAL_OBLIGATION")).andExpect(status().is3xxRedirection());
        var captured=org.mockito.ArgumentCaptor.forClass(com.sacco.mvp.clients.portal.dto.ClientPortalDtos.EntryForm.class);
        verify(portal).request(captured.capture(),anyString());var p=captured.getValue().getRegistration().getProfile();
        assertThat(p.getHasBusiness()).isFalse();
        assertThat(p.getBusinessName()).isEqualTo("Synthetic shop");assertThat(p.getDailySales()).isEqualByComparingTo("12500.50");
        assertThat(p.getSpouseName()).isEqualTo("Synthetic spouse");assertThat(p.getBankAccountNumber()).isEqualTo("12345678");
        assertThat(p.getGroupId()).isNull();assertThat(p.getGroupName()).isEmpty();
    }
    @Test void invalidRegistrationDetailsReturnTheSameFormWithFieldErrors()throws Exception{
        when(portal.request(any(),anyString())).thenThrow(new IllegalArgumentException("kyc.error.dailySales"));
        mvc.perform(post("/client-portal/request-code").with(csrf()).param("intent","REGISTER")
            .param("registration.profile.businessName","Keep my shop").param("registration.profile.dailySales","-1"))
            .andExpect(status().isOk()).andExpect(view().name("portal/entry"))
            .andExpect(model().attributeHasFieldErrors("entryForm","registration.profile.dailySales"));
    }
    @Test void successfulCodeVerificationRotatesSessionAndNeverGrantsNormalAuthorities()throws Exception{
        var http=new MockHttpSession();String before=http.getId();UUID nonce=UUID.randomUUID();http.setAttribute("clientPortalChallenge",nonce);Session access=session();when(portal.verify(nonce,"123456","127.0.0.1")).thenReturn(Optional.of(access));
        mvc.perform(post("/client-portal/verify").session(http).with(csrf()).param("code","123456")).andExpect(redirectedUrl("/client-portal/workspace/applications"));
        assertThat(http.getId()).isNotEqualTo(before);assertThat(http.getAttribute("clientPortalChallenge")).isNull();var security=(SecurityContext)http.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);assertThat(security.getAuthentication().getPrincipal()).isEqualTo(access);assertThat(security.getAuthentication().getAuthorities()).isEmpty();
        mvc.perform(get("/app/dashboard").session(http)).andExpect(status().isForbidden());
    }
    @Configuration @EnableWebMvc @Import({ClientPortalController.class,Probe.class}) static class Config {@Bean ClientPortalService portal(){return mock(ClientPortalService.class);}}
    @RestController static class Probe {@GetMapping("/") String home(){return "public home";}@GetMapping("/client-portal/workspace/probe") String own(){return "own portal";}@GetMapping({"/app/dashboard","/app/settings","/admin/users","/finance/private"}) String normal(){return "normal workspace";}}
}
