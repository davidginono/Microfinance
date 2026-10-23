package com.sacco.mvp.config;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.controller.GroupLendingController;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.*;
import com.sacco.mvp.grouplending.service.GroupLendingService;
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

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,GroupLendingMvcTest.Config.class})
class GroupLendingMvcTest {
    @Autowired WebApplicationContext context;@Autowired GroupLendingService groups;
    MockMvc mvc;AppUserPrincipal actor;final UUID id=UUID.randomUUID();
    @BeforeEach void setup(){reset(groups);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),true);}
    @Test void csrfAndStaffSessionAreRequired()throws Exception{
        mvc.perform(post("/finance/groups").with(user(actor))).andExpect(status().isForbidden());
        var client=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").status(MemberStatus.ACTIVE).build(),Set.of(),false);
        mvc.perform(get("/finance/groups").with(user(client))).andExpect(status().isForbidden());verifyNoInteractions(groups);
    }
    @Test void creationBindsOnlyGroupFieldsAndDerivesScopeFromActor()throws Exception{
        when(groups.create(eq(actor),any())).thenReturn(id);
        mvc.perform(post("/finance/groups").with(user(actor)).with(csrf()).param("name","Test group").param("evidence","Identity evidence").param("saccoId","FOREIGN").param("stationId","FOREIGN").param("status","ACTIVE"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/finance/groups/"+id));
        verify(groups).create(eq(actor),argThat(f->f.getName().equals("Test group") && f.getLegalForm().equals("INFORMAL")));
    }
    @Test void invalidMembershipKeepsValuesAndDisplaysLocalError()throws Exception{
        when(groups.detail(actor,id)).thenReturn(new Group(id,"GRP-1","Test group","INFORMAL","Evidence","ACTIVE",0,0));
        when(groups.selectedClient(eq(actor),any())).thenReturn(Optional.empty());
        when(groups.members(actor,id,0,"startsOn_desc")).thenReturn(new Page<>(List.of(),0,false));doThrow(new IllegalArgumentException("group.error.membership")).when(groups).addMember(eq(actor),eq(id),any());
        mvc.perform(post("/finance/groups/"+id+"/members").with(user(actor)).with(csrf()).param("clientId",UUID.randomUUID().toString()).param("evidence","Retained evidence").param("startsOn","2026-10-23"))
            .andExpect(status().isOk()).andExpect(view().name("groups/details")).andExpect(model().attributeHasErrors("memberForm"));
    }
    @Test void lookupIsBoundedAndUncacheable()throws Exception{
        when(groups.clients(actor,"A",2)).thenReturn(List.of(new Choice(id,"Client A")));
        mvc.perform(get("/finance/groups/clients?search=A&page=2").with(user(actor))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$[0].label").value("Client A"));
    }
    @Test void endingMembershipRequiresCsrfAndPreservesInvalidDates()throws Exception{
        UUID member=UUID.randomUUID();String path="/finance/groups/"+id+"/members/"+member+"/end";
        mvc.perform(post(path).with(user(actor)).param("date","2026-10-23").param("reason","Exit")).andExpect(status().isForbidden());
        when(groups.detail(actor,id)).thenReturn(new Group(id,"GRP-1","Test group","INFORMAL","Evidence","ACTIVE",0,1));
        when(groups.membership(actor,id,member)).thenReturn(new Membership(member,UUID.randomUUID(),"C1","Client","MEMBER",LocalDate.of(2026,10,1),null,"Evidence",null));
        mvc.perform(post(path).with(user(actor)).with(csrf()).param("date","invalid").param("reason","Retain this reason"))
            .andExpect(status().isOk()).andExpect(view().name("groups/end-membership")).andExpect(model().attributeHasErrors("endForm"));
        verify(groups,never()).endMember(any(),any(),any(),any(),any());
    }
    @Test void invalidQueryReturnsSafeBadRequest()throws Exception{
        when(groups.list(actor,"","unsafe",0)).thenThrow(new IllegalArgumentException("group.error.query"));
        mvc.perform(get("/finance/groups?sort=unsafe").with(user(actor))).andExpect(status().isBadRequest()).andExpect(view().name("groups/error"));
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean GroupLendingService groups(){return mock(GroupLendingService.class);}
        @Bean ApplicationClock clock(){var c=mock(ApplicationClock.class);when(c.today()).thenReturn(LocalDate.of(2026,10,23));return c;}
        @Bean ResourceBundleMessageSource messageSource(){var m=new ResourceBundleMessageSource();m.setBasename("messages");m.setDefaultEncoding("UTF-8");return m;}
        @Bean GroupLendingController controller(GroupLendingService service,ApplicationClock clock){return new GroupLendingController(service,clock);}
    }
}
