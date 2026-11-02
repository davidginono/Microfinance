package com.sacco.mvp.config;

import com.sacco.mvp.clients.onboarding.controller.ClientOnboardingController;
import com.sacco.mvp.clients.onboarding.controller.OnboardingController;
import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.*;
import com.sacco.mvp.clients.onboarding.service.*;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,ProvisionalClientMvcTest.Config.class})
class ProvisionalClientMvcTest {
    @Autowired WebApplicationContext context;@Autowired OnboardingService onboarding;
    MockMvc mvc;AppUserPrincipal staff,client;UUID id=UUID.randomUUID();String root;
    @BeforeEach void setup(){reset(onboarding);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        staff=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),true);
        client=new AppUserPrincipal(Member.builder().id(id).saccoId("I1").stationId("B1").position(Position.MEMBER).memberAccount(true).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),false);
        root="/finance/clients/onboarding/"+id+"/loan";
    }
    @Test void oldLoanLinksReturnToProfilesWithoutLoadingAnIntake()throws Exception {
        for(String path:List.of("/app/onboarding/loan","/app/onboarding/loan/edit","/app/onboarding/loan/products","/app/onboarding/loan/continue"))
            mvc.perform(get(path).with(user(client))).andExpect(redirectedUrl("/app/onboarding"));
        mvc.perform(get(root).with(user(staff))).andExpect(redirectedUrl("/finance/clients/onboarding/"+id));
        verify(onboarding).client(staff,id);
    }
    @Test void retiredLoanWritesCannotCreateRequests()throws Exception {
        mvc.perform(post("/app/onboarding/loan/edit").with(user(client))).andExpect(status().isForbidden());
        mvc.perform(post("/app/onboarding/loan/edit").with(user(client)).with(csrf())).andExpect(status().is4xxClientError());
        mvc.perform(post(root+"/edit").with(user(staff)).with(csrf())).andExpect(status().is4xxClientError());
        verify(onboarding).ownStatus(client);verifyNoMoreInteractions(onboarding);
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean ClientOnboardingController clientController(OnboardingService onboarding){return new ClientOnboardingController(onboarding);}
        @Bean OnboardingController staffController(OnboardingService onboarding){return new OnboardingController(onboarding);}
    }
}
