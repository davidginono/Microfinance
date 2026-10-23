package com.sacco.mvp.config;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.controller.*;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.*;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,GroupCycleMvcTest.Config.class})
class GroupCycleMvcTest {
    @Autowired WebApplicationContext context;@Autowired GroupCycleService cycles;@Autowired GroupLendingService groups;@Autowired GroupConsentService consents;
    MockMvc mvc;AppUserPrincipal actor;final UUID group=UUID.randomUUID(),cycle=UUID.randomUUID(),client=UUID.randomUUID();
    @BeforeEach void setup(){reset(cycles,groups,consents);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),true);
        when(groups.detail(actor,group)).thenReturn(new Group(group,"GRP-1","Synthetic group","INFORMAL","Evidence","ACTIVE",0,2));
        when(cycles.policies(actor,0)).thenReturn(new Page<>(List.of(),0,false));when(cycles.memberLabels(eq(actor),eq(group),any())).thenReturn(Map.of(client,"C31 · Synthetic participant"));}
    @Test void cycleCreationRequiresCsrfAndCannotBindScopeOrLoanIdentity()throws Exception{
        String path="/finance/groups/"+group+"/cycles";mvc.perform(post(path).with(user(actor))).andExpect(status().isForbidden());verifyNoInteractions(cycles);
        when(cycles.create(eq(actor),eq(group),any())).thenReturn(cycle);
        mvc.perform(post(path).with(user(actor)).with(csrf()).param("purpose","Business stock").param("stationId","FOREIGN").param("participants[0].clientId",client.toString()).param("participants[0].loanId",UUID.randomUUID().toString()))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/finance/groups/cycles/"+cycle));
        verify(cycles).create(eq(actor),eq(group),argThat(f->f.getParticipants().size()==1 && f.getParticipants().getFirst().getClientId().equals(client) && f.getPurpose().equals("Business stock")));
    }
    @Test void invalidAmountsRetainTheFormAndSelectedParticipant()throws Exception{
        mvc.perform(post("/finance/groups/"+group+"/cycles").with(user(actor)).with(csrf()).param("participants[0].clientId",client.toString()).param("participants[0].income","invalid").param("purpose","Retained purpose"))
            .andExpect(status().isOk()).andExpect(view().name("groups/cycle-form")).andExpect(model().attributeHasErrors("cycleForm")).andExpect(model().attribute("memberLabels",Map.of(client,"C31 · Synthetic participant")));
        verify(cycles,never()).create(any(),any(),any());
    }
    @Test void addingRowsRetainsUnsavedValuesWithoutWritingADraft()throws Exception{
        mvc.perform(post("/finance/groups/"+group+"/cycles").with(user(actor)).with(csrf()).param("action","addRow").param("participants[0].clientId",client.toString()).param("purpose","Unsaved purpose"))
            .andExpect(status().isOk()).andExpect(view().name("groups/cycle-form"));
        verify(cycles).memberLabels(eq(actor),eq(group),argThat(f->f.getParticipants().size()==2 && f.getPurpose().equals("Unsaved purpose")));
        verify(cycles,never()).create(any(),any(),any());
    }
    @Test void oversizedParticipantIndexReturnsSafeBadRequest()throws Exception{
        mvc.perform(post("/finance/groups/"+group+"/cycles").with(user(actor)).with(csrf()).param("participants[50].clientId",client.toString()))
            .andExpect(status().isBadRequest()).andExpect(view().name("groups/error"));verify(cycles,never()).create(any(),any(),any());
    }
    @Test void personalAcceptanceUsesAuthenticatedClientAndCsrf()throws Exception{
        var principal=new AppUserPrincipal(Member.builder().id(client).saccoId("I1").stationId("B1").position(Position.MEMBER).memberAccount(true).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),false);
        String path="/app/group-loans/"+cycle+"/accept";mvc.perform(post(path).with(user(principal)).param("accepted","true")).andExpect(status().isForbidden());verifyNoInteractions(consents);
        mvc.perform(post(path).with(user(principal)).with(csrf()).param("accepted","true").param("clientId",UUID.randomUUID().toString()))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/app/group-loans/"+cycle));verify(consents).accept(principal,cycle,true);
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean GroupCycleService cycles(){return mock(GroupCycleService.class);}@Bean GroupLendingService groups(){return mock(GroupLendingService.class);}@Bean GroupConsentService consents(){return mock(GroupConsentService.class);}
        @Bean ApplicationClock clock(){var c=mock(ApplicationClock.class);when(c.today()).thenReturn(LocalDate.of(2026,10,23));return c;}
        @Bean ResourceBundleMessageSource messageSource(){var m=new ResourceBundleMessageSource();m.setBasename("messages");m.setDefaultEncoding("UTF-8");return m;}
        @Bean GroupCycleController controller(GroupCycleService c,GroupLendingService g,ApplicationClock clock,ResourceBundleMessageSource m){return new GroupCycleController(c,g,clock,m);}
        @Bean GroupConsentController consentController(GroupConsentService c){return new GroupConsentController(c);}
    }
}
