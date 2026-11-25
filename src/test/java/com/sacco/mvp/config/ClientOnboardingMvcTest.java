package com.sacco.mvp.config;

import com.sacco.mvp.clients.onboarding.controller.OnboardingController;
import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.*;
import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,ClientOnboardingMvcTest.Config.class})
class ClientOnboardingMvcTest {
    @Autowired WebApplicationContext context;
    @Autowired OnboardingService service;
    MockMvc mvc;AppUserPrincipal actor;UUID client,revision;
    @BeforeEach void setup(){
        reset(service);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // A pre-removal session can still contain the old claim string.
        actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("STAFF-TEST").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class)) {
            @Override public Set<String> getClaims(){var claims=new HashSet<>(super.getClaims());claims.add(UserClaim.CLIENT_ONBOARDING_REVIEW.name());return Set.copyOf(claims);}
        };
        client=UUID.randomUUID();revision=UUID.randomUUID();
        when(service.client(actor,client)).thenReturn(new Client(client,"CL-TEST","Example client","255712345678",null,"VERIFY",revision,1,null,false));
        when(service.latest(actor,client)).thenReturn(Optional.empty());when(service.documents(actor,client)).thenReturn(List.of());
        when(service.history(actor,client,0)).thenReturn(new Page<>(List.of(),0,false));when(service.today()).thenReturn(LocalDate.of(2026,11,13));
    }
    @Test void registryPassesClientStatusAndGroupSortToTheService() throws Exception {
        var records=new Page<>(List.of(new RegistryClient(client,"CL-TEST","Example client","INACTIVE","Example group")),1,true);
        when(service.registry(actor,"Example","INACTIVE",1,"group_desc")).thenReturn(records);
        mvc.perform(get("/finance/clients/onboarding").with(user(actor)).param("search","Example")
                .param("status","INACTIVE").param("page","1").param("sort","group_desc"))
            .andExpect(status().isOk()).andExpect(view().name("onboarding/register"))
            .andExpect(model().attribute("records",records)).andExpect(model().attribute("status","INACTIVE"))
            .andExpect(model().attribute("sort","group_desc"));
    }
    @Test void currentDetailsKeepPastDecisionsWithoutCreatingAReviewForm() throws Exception {
        var past=new Decision(UUID.randomUUID(),"VERIFY","Earlier recorded decision","","UNAVAILABLE","",LocalDate.of(2026,11,1),null,"",actor.getMemberId(),"Earlier reviewer",OffsetDateTime.now());
        when(service.decisions(actor,client)).thenReturn(List.of(past));
        mvc.perform(get("/finance/clients/onboarding/"+client).with(user(actor))).andExpect(status().isOk()).andExpect(view().name("onboarding/details"))
            .andExpect(model().attributeDoesNotExist("reviewForm")).andExpect(model().attribute("decisions",List.of(past)));
    }
    @Test void removedDecisionCommandsCannotBePostedWithAnOldPermissionAndValidCsrf() throws Exception {
        clearInvocations(service);
        for(String kind:List.of("VERIFY","RETURN","REJECT","REVOKE"))mvc.perform(post("/finance/clients/onboarding/"+client+"/review").with(user(actor)).with(csrf()).param("kind",kind).param("revisionId",revision.toString()))
            .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
    @Test void historicalVersionsRemainReadOnlyAndScopedThroughTheService() throws Exception {
        var old=new Revision(revision,client,1,"RECORDED",new Profile(),actor.getMemberId(),"Earlier staff",OffsetDateTime.now());
        when(service.version(actor,client,1)).thenReturn(old);when(service.versionDecisions(actor,client,1)).thenReturn(List.of());when(service.versionDocuments(actor,client,1)).thenReturn(List.of());
        mvc.perform(get("/finance/clients/onboarding/"+client+"/versions/1").with(user(actor))).andExpect(status().isOk()).andExpect(model().attribute("historical",true))
            .andExpect(model().attribute("revision",old)).andExpect(model().attributeDoesNotExist("reviewForm"));
        verify(service).version(actor,client,1);
    }
    @Test void staffCanStillOpenAndSaveClientRecordsDirectly() throws Exception {
        when(service.create(eq(actor),any())).thenReturn(client);
        mvc.perform(get("/finance/clients/onboarding/new").with(user(actor))).andExpect(status().isOk()).andExpect(model().attribute("formKind","create")).andExpect(model().attributeDoesNotExist("reviewForm"));
        mvc.perform(post("/finance/clients/onboarding/new").with(user(actor)).with(csrf()).param("profile.firstName","Example").param("profile.lastName","Client"))
            .andExpect(redirectedUrl("/finance/clients/onboarding/"+client));
        mvc.perform(post("/finance/clients/onboarding/"+client+"/edit").with(user(actor)).with(csrf()).param("action","RECORDED"))
            .andExpect(redirectedUrl("/finance/clients/onboarding/"+client));
        verify(service).save(eq(actor),eq(client),any());
    }
    @Test void clientRecordingStillRequiresLoginAndCsrf() throws Exception {
        mvc.perform(get("/finance/clients/onboarding/new")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/finance/clients/onboarding/new").with(user(actor))).andExpect(status().isForbidden());
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean("access") @Primary AccessControlService access(){return new AccessControlService();}
        @Bean OnboardingService service(){return mock(OnboardingService.class);}
        @Bean OnboardingController controller(OnboardingService service){return new OnboardingController(service);}
    }
}
