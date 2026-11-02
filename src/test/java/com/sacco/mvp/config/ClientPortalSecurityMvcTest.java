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
    @Test void successfulCodeVerificationRotatesSessionAndNeverGrantsNormalAuthorities()throws Exception{
        var http=new MockHttpSession();String before=http.getId();UUID nonce=UUID.randomUUID();http.setAttribute("clientPortalChallenge",nonce);Session access=session();when(portal.verify(nonce,"123456","127.0.0.1")).thenReturn(Optional.of(access));
        mvc.perform(post("/client-portal/verify").session(http).with(csrf()).param("code","123456")).andExpect(redirectedUrl("/client-portal/workspace/applications"));
        assertThat(http.getId()).isNotEqualTo(before);assertThat(http.getAttribute("clientPortalChallenge")).isNull();var security=(SecurityContext)http.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);assertThat(security.getAuthentication().getPrincipal()).isEqualTo(access);assertThat(security.getAuthentication().getAuthorities()).isEmpty();
        mvc.perform(get("/app/dashboard").session(http)).andExpect(status().isForbidden());
    }
    @Configuration @EnableWebMvc @Import({ClientPortalController.class,Probe.class}) static class Config {@Bean ClientPortalService portal(){return mock(ClientPortalService.class);}}
    @RestController static class Probe {@GetMapping("/client-portal/workspace/probe") String own(){return "own portal";}@GetMapping({"/app/dashboard","/app/settings","/admin/users","/finance/private"}) String normal(){return "normal workspace";}}
}
