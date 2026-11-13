package com.sacco.mvp.config;

import com.sacco.mvp.accounting.approval.service.VoucherWorkflowService;
import com.sacco.mvp.accounting.loans.service.*;
import com.sacco.mvp.accounting.service.AccountOfficeWorkService;
import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.lending.controls.service.CreditControlService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import com.sacco.mvp.staffoffice.controller.StaffOfficeController;
import com.sacco.mvp.staffoffice.model.StaffOffice;
import com.sacco.mvp.staffoffice.service.StaffOfficeService;
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

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,StaffOfficeMvcTest.Config.class})
class StaffOfficeMvcTest {
    @Autowired WebApplicationContext context;
    @Autowired MemberDirectoryService members;
    @Autowired UserClaimService claims;
    @Autowired SaccoRegistryService institutions;
    MockMvc mvc;
    @BeforeEach void setup(){reset(members,claims,institutions);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    AppUserPrincipal actor(Position role,String branch) {
        var member=Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId(branch).position(role)
            .status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).staffNo("NEW").build();
        when(members.find(member.getId())).thenReturn(Optional.of(member));
        when(claims.effectiveClaims(member.getId(),member.getActiveStaffRolesResolved(),false)).thenReturn(Set.of());
        when(institutions.findActiveSacco("I1")).thenReturn(Optional.of(RegisteredSacco.builder().saccoId("I1").active(true).build()));
        when(institutions.findStation("I1","B1")).thenReturn(Optional.of(SaccoStation.builder().stationId("B1").active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        return new AppUserPrincipal(member,Set.of());
    }
    @Test void allAssignedOfficesRenderAllThreePagesWithoutProfileCompletionOrBusinessClaims()throws Exception {
        for(var office:StaffOffice.values())for(var section:List.of("dashboard","queue","reports"))
            mvc.perform(get(office.getBase()+"/"+section).with(user(actor(office.getGroup(),"B1")))
                    .param("stationId","FOREIGN").param("saccoId","FOREIGN"))
                .andExpect(status().isOk()).andExpect(view().name("staff/office"))
                .andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void officeUrlsDenyPlatformRetiredUnassignedAndUnscopedSessions()throws Exception {
        var current=actor(Position.CUSTOMER_CARE,"B1");
        mvc.perform(get("/staff/offices/ict-manager/queue").with(user(current))).andExpect(status().isForbidden());
        mvc.perform(get("/staff/offices/customer-care/queue").with(user(current)).param("source","loan-reviews")).andExpect(status().isForbidden());
        mvc.perform(get("/staff/offices/customer-care/dashboard").with(user(actor(Position.ADMIN,"B1")))).andExpect(status().isForbidden());
        mvc.perform(get("/staff/offices/customer-care/dashboard").with(user(actor(Position.CUSTOMER_CARE,null)))).andExpect(status().isForbidden());
        var retired=MvcSecurityTestConfiguration.formerClient(Member.builder().id(UUID.randomUUID()).build(),Set.of());
        mvc.perform(get("/staff/offices/customer-care/dashboard").with(user(retired))).andExpect(status().isForbidden());
        mvc.perform(post("/staff/offices/customer-care/queue").with(user(current))).andExpect(status().isForbidden());
        mvc.perform(get("/staff/offices/customer-care/queue").with(user(current)).param("page","-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/staff/offices/customer-care/queue").with(user(current)).param("search","x".repeat(101))).andExpect(status().isBadRequest());
    }
    @Configuration @EnableWebMvc @Import({StaffOfficeService.class,StaffOfficeController.class}) static class Config {
        @Bean("access") @Primary AccessControlService access(){return new AccessControlService();}
        @Bean SaccoRegistryService institutions(){return mock(SaccoRegistryService.class);}
        @Bean LoanGroupApprovalService reviews(){return mock(LoanGroupApprovalService.class);}
        @Bean LoanRecordingService loans(){return mock(LoanRecordingService.class);}
        @Bean VoucherWorkflowService vouchers(){return mock(VoucherWorkflowService.class);}
        @Bean AccountOfficeWorkService accounts(){return mock(AccountOfficeWorkService.class);}
        @Bean OnboardingService clients(){return mock(OnboardingService.class);}
        @Bean CreditControlService credit(){return mock(CreditControlService.class);}
        @Bean AdminOfficeWorkService administration(){return mock(AdminOfficeWorkService.class);}
    }
}
