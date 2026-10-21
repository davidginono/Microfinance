package com.sacco.mvp.config;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import com.sacco.mvp.web.StaffDashboardController;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,StaffDashboardMvcTest.Config.class})
class StaffDashboardMvcTest {
    @Autowired WebApplicationContext context;
    @Autowired StaffDashboardService service;
    MockMvc mvc;
    @BeforeEach void setup(){reset(service);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    AppUserPrincipal actor(Position role,String branch,boolean staff,UserClaim... claims){return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId(branch).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).position(role).build(),new HashSet<>(List.of(claims)),staff);}
    @Test void roleDashboardAccessDoesNotRequireAnUnrelatedAnalyticsPermissionAndIgnoresForgedScope()throws Exception{
        var actor=actor(Position.ACCOUNTANT,"B1",true,UserClaim.ACCOUNTANT_QUEUE_VIEW);
        var dashboard=new StaffDashboardService.Dashboard(null,List.of(),null,2026);when(service.read(actor)).thenReturn(dashboard);
        mvc.perform(get("/staff/dashboard").with(user(actor)).param("stationId","FOREIGN").param("saccoId","FOREIGN"))
            .andExpect(status().isOk()).andExpect(view().name("staff/dashboard")).andExpect(model().attribute("staffDashboard",dashboard));
        verify(service).read(actor);
    }
    @Test void missingClaimsOrBranchCannotLoadDashboardData()throws Exception{
        for(var actor:List.of(actor(Position.ACCOUNTANT,"B1",true),actor(Position.ACCOUNTANT,null,true,UserClaim.ACCOUNTANT_QUEUE_VIEW))){
            mvc.perform(get("/staff/dashboard").with(user(actor))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean("access") @Primary AccessControlService access(){return new AccessControlService();}
        @Bean StaffDashboardService dashboard(){return mock(StaffDashboardService.class);}
        @Bean StaffDashboardController controller(StaffDashboardService dashboard){return new StaffDashboardController(dashboard);}
    }
}
