package com.sacco.mvp.config;

import com.sacco.mvp.dashboard.controller.DashboardWorkController;
import com.sacco.mvp.dashboard.service.DashboardWorkService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,DashboardWorkMvcTest.Config.class})
class DashboardWorkMvcTest {
    @Autowired WebApplicationContext context;
    @Autowired DashboardWorkService work;
    MockMvc mvc;
    @BeforeEach void setup(){reset(work);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    AppUserPrincipal actor(String branch,UserClaim... claims){return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId(branch).position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),new HashSet<>(List.of(claims)),true);}
    @Test void endpointIgnoresForgedScopeAndDoesNotCacheFinancialSummaries()throws Exception{
        var a=actor("B1",UserClaim.ACCOUNTANT_QUEUE_VIEW);when(work.read(a,3,new BigDecimal("400000"))).thenReturn(new DashboardWorkService.Work(List.of(),"2026-10-22T09:42:00+03:00"));
        mvc.perform(get("/api/dashboard/work").with(user(a)).param("stationId","OTHER").param("saccoId","OTHER").param("days","3").param("amount","400000").accept("application/json"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.infolets").isArray());
        verify(work).read(a,3,new BigDecimal("400000"));
    }
    @Test void missingClaimsOrBranchCannotReachTheReadService()throws Exception{
        for(var a:List.of(actor("B1"),actor(null,UserClaim.ACCOUNTANT_QUEUE_VIEW)))mvc.perform(get("/api/dashboard/work").with(user(a)).accept("application/json")).andExpect(status().isForbidden());
        verifyNoInteractions(work);
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean("access") @Primary AccessControlService access(){return new AccessControlService();}
        @Bean DashboardWorkService work(){return mock(DashboardWorkService.class);}
        @Bean DashboardWorkController controller(DashboardWorkService work){return new DashboardWorkController(work);}
    }
}
