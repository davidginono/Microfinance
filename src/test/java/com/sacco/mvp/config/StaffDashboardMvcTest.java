package com.sacco.mvp.config;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import com.sacco.mvp.web.StaffDashboardController;
import com.sacco.mvp.staffoffice.service.StaffOfficeService;
import com.sacco.mvp.staffoffice.controller.PersonalDashboardApiController;
import com.sacco.mvp.accounting.loans.service.LoanGroupApprovalService;
import com.sacco.mvp.staffoffice.dto.OfficeWork;
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
    @Autowired MemberDirectoryService members;
    @Autowired UserClaimService claims;
    @Autowired SaccoRegistryService institutions;
    @Autowired LoanGroupApprovalService reviews;
    MockMvc mvc;
    @BeforeEach void setup(){reset(members,claims,institutions,reviews);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    AppUserPrincipal actor(Position role,String branch,boolean staff,UserClaim... permissions){
        var member=Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId(branch).status(MemberStatus.ACTIVE)
            .staffAccessStatus(StaffAccessStatus.ACTIVE).position(role).build();
        when(members.find(member.getId())).thenReturn(Optional.of(member));
        when(claims.effectiveClaims(member.getId(),member.getActiveStaffRolesResolved(),false)).thenReturn(Set.of(permissions));
        when(institutions.findActiveSacco("I1")).thenReturn(Optional.of(RegisteredSacco.builder().saccoId("I1").active(true).build()));
        when(institutions.findStation("I1","B1")).thenReturn(Optional.of(SaccoStation.builder().stationId("B1").active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        return staff?new AppUserPrincipal(member,Set.of(permissions)):MvcSecurityTestConfiguration.formerClient(member,Set.of(permissions));
    }
    @Test void roleDashboardAccessDoesNotRequireAnUnrelatedAnalyticsPermissionAndIgnoresForgedScope()throws Exception{
        var actor=actor(Position.ACCOUNTANT,"B1",true,UserClaim.LOAN_RECORDING_VIEW);
        mvc.perform(get("/staff/dashboard").with(user(actor)).param("stationId","FOREIGN").param("saccoId","FOREIGN"))
            .andExpect(status().isOk()).andExpect(view().name("staff/dashboard"))
            .andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void everyStaffGroupCanEnterWithoutClaimsOrACompletedProfile()throws Exception{
        for(var office:com.sacco.mvp.staffoffice.model.StaffOffice.values())
            mvc.perform(get("/staff/dashboard").with(user(actor(office.getGroup(),"B1",true))))
                .andExpect(status().isOk()).andExpect(view().name("staff/dashboard"));
    }
    @Test void missingBranchAndRetiredSessionsCannotEnter()throws Exception{
        for(var actor:List.of(actor(Position.ACCOUNTANT,"B1",false),actor(Position.ACCOUNTANT,null,true,UserClaim.LOAN_RECORDING_VIEW))){
            mvc.perform(get("/staff/dashboard").with(user(actor))).andExpect(status().isForbidden());
        }
    }
    @Test void activityCountsUseTheSessionScopeAndNeverReturnQueueRecords()throws Exception{
        var a=actor(Position.LOAN_OFFICER,"B1",true,UserClaim.LOAN_RECORDING_VIEW,UserClaim.LOAN_RECORDING_REJECT);
        when(reviews.officeReviews(any(),eq(Position.LOAN_OFFICER),eq(""),eq("number"),eq(0)))
            .thenReturn(new OfficeWork.Page(List.of(new OfficeWork.Task("SECRET","Private name","office.readyReview",null,"/details")),0,false));
        mvc.perform(get("/api/dashboard/activities/loan-reviews").with(user(a)).accept("application/json")
                .param("stationId","FOREIGN").param("saccoId","FOREIGN").param("office","ict-manager"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("loan-reviews"))
            .andExpect(jsonPath("$.entries[0].office").value("loan-officer"))
            .andExpect(jsonPath("$.entries[0].shown").value(1))
            .andExpect(jsonPath("$.entries[0].more").value(false))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Private name"))))
            .andExpect(header().string("Cache-Control","no-store"));
        verify(reviews).officeReviews(argThat(p->p.getStationId().equals("B1")&&p.getSaccoId().equals("I1")),eq(Position.LOAN_OFFICER),eq(""),eq("number"),eq(0));
    }
    @Test void activityEndpointsDenyUnknownSourcesRevokedClaimsAndPlatformSessions()throws Exception{
        var a=actor(Position.LOAN_OFFICER,"B1",true);
        for(var source:List.of("unknown","loan-reviews"))
            mvc.perform(get("/api/dashboard/activities/"+source).with(user(a)).accept("application/json")).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/activities/loan-reviews").with(user(actor(Position.ADMIN,"B1",true))).accept("application/json")).andExpect(status().isForbidden());
        mvc.perform(post("/api/dashboard/activities/loan-reviews").with(user(a))).andExpect(status().isForbidden());
        verifyNoInteractions(reviews);
    }
    @Configuration @EnableWebMvc @Import({StaffOfficeMvcTest.Config.class,PersonalDashboardApiController.class}) static class Config {
        @Bean StaffDashboardController controller(StaffOfficeService offices){return new StaffDashboardController(offices);}
    }
}
