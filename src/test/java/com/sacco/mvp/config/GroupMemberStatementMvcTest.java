package com.sacco.mvp.config;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.controller.GroupMemberStatementController;
import com.sacco.mvp.grouplending.service.*;
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
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,GroupMemberStatementMvcTest.Config.class})
class GroupMemberStatementMvcTest {
    @Autowired WebApplicationContext context;@Autowired GroupMemberStatementService statements;@Autowired GroupMemberStatementPdf pdf;
    MockMvc mvc;AppUserPrincipal client;UUID cycle=UUID.randomUUID();
    @BeforeEach void setup(){reset(statements,pdf);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();client=MvcSecurityTestConfiguration.formerClient(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").status(MemberStatus.ACTIVE).memberAccount(true).position(Position.MEMBER).build(),EnumSet.allOf(UserClaim.class));}

    @Test void retiredBorrowerSessionCannotOpenStaffOrOldPersonalStatements()throws Exception {
        for(var path:List.of("/app/group-loans/"+cycle+"/statement","/finance/groups/cycles/"+cycle+"/allocations/"+client.getMemberId()+"/statement"))
            mvc.perform(get(path).with(user(client))).andExpect(status().isForbidden());
        verifyNoInteractions(statements,pdf);
    }
    @Test void staffStatementBindsTheNamedClientAndHasPrivateExportHeaders()throws Exception {
        var staff=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class));
        String path="/finance/groups/cycles/"+cycle+"/allocations/"+client.getMemberId()+"/statement";
        mvc.perform(get(path).with(user(staff))).andExpect(view().name("groups/member-statement"));
        verify(statements).staff(staff,cycle,client.getMemberId(),null,null,0,0,0,false);
        when(pdf.render(any(),any())).thenReturn("%PDF-test".getBytes());
        mvc.perform(get(path+"/pdf").with(user(staff))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"));
        verify(statements).staff(staff,cycle,client.getMemberId(),null,null,0,0,true);
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean GroupMemberStatementService statements(){return mock(GroupMemberStatementService.class);}
        @Bean GroupMemberStatementPdf pdf(){return mock(GroupMemberStatementPdf.class);}
        @Bean GroupMemberStatementController controller(GroupMemberStatementService s,GroupMemberStatementPdf p){return new GroupMemberStatementController(s,p);}
    }
}
