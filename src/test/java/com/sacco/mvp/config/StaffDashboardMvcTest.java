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
    MockMvc mvc;
    @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    AppUserPrincipal actor(Position role,String branch,boolean staff,UserClaim... claims){return (staff ? new com.sacco.mvp.security.AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId(branch).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).position(role).build(),new HashSet<>(List.of(claims))) : com.sacco.mvp.config.MvcSecurityTestConfiguration.formerClient(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId(branch).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).position(role).build(),new HashSet<>(List.of(claims))));}
    @Test void roleDashboardAccessDoesNotRequireAnUnrelatedAnalyticsPermissionAndIgnoresForgedScope()throws Exception{
        var actor=actor(Position.ACCOUNTANT,"B1",true,UserClaim.LOAN_RECORDING_VIEW);
        mvc.perform(get("/staff/dashboard").with(user(actor)).param("stationId","FOREIGN").param("saccoId","FOREIGN"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/offices/chief-accountant/dashboard"));
    }
    @Test void everyStaffGroupCanEnterWithoutClaimsOrACompletedProfile()throws Exception{
        for(var office:com.sacco.mvp.staffoffice.model.StaffOffice.values())
            mvc.perform(get("/staff/dashboard").with(user(actor(office.getGroup(),"B1",true))))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(office.getDashboard()));
    }
    @Test void missingBranchAndRetiredSessionsCannotEnter()throws Exception{
        for(var actor:List.of(actor(Position.ACCOUNTANT,"B1",false),actor(Position.ACCOUNTANT,null,true,UserClaim.LOAN_RECORDING_VIEW))){
            mvc.perform(get("/staff/dashboard").with(user(actor))).andExpect(status().isForbidden());
        }
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean("access") @Primary AccessControlService access(){return new AccessControlService();}
        @Bean StaffDashboardController controller(){return new StaffDashboardController();}
    }
}
