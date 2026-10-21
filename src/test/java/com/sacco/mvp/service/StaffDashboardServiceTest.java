package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffDashboardServiceTest {
    final ManagerService managers=mock(ManagerService.class);
    final AdminService admins=mock(AdminService.class);
    final ApplicationClock clock=mock(ApplicationClock.class);
    final StaffDashboardService service=new StaffDashboardService(new AccessControlService(),managers,admins,clock);
    AppUserPrincipal actor(Position role,String branch,UserClaim... claims){
        return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId(branch).position(role)
            .status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),new HashSet<>(List.of(claims)),true);
    }
    @Test void combinesAuthorizedRolesFromOneBranchSummaryWithoutLoadingOtherRoleData(){
        var actor=actor(Position.MANAGER,"B1",UserClaim.MANAGER_QUEUE_VIEW,UserClaim.ACCOUNTANT_QUEUE_VIEW);
        var counts=List.of(new ManagerService.StatusCount(LoanStatus.READY_FOR_MANAGER,4),new ManagerService.StatusCount(LoanStatus.AWAITING_ACCOUNTANT,2));
        when(managers.dashboardSummary("I1","B1")).thenReturn(new ManagerService.ManagerDashboard(9,5,3,1,2,4,0,counts,List.of()));
        when(clock.today()).thenReturn(LocalDate.of(2026,10,21));
        var dashboard=service.read(actor);
        assertThat(dashboard.roles()).extracting(StaffDashboardService.Role::key).containsExactly("manager","accountant");
        assertThat(dashboard.roles()).extracting(StaffDashboardService.Role::pending).containsExactly(4L,2L);
        assertThat(dashboard.administration()).isNull();verify(managers).dashboardSummary("I1","B1");verifyNoMoreInteractions(managers);verifyNoInteractions(admins);
    }
    @Test void voucherCreationAloneDoesNotExposePortfolioOrAdministratorMetrics(){
        var actor=actor(Position.ACCOUNTANT,"B1",UserClaim.ACCOUNTING_BUSINESS_CREATE);when(clock.today()).thenReturn(LocalDate.of(2026,10,21));
        var dashboard=service.read(actor);assertThat(dashboard.portfolio()).isNull();assertThat(dashboard.roles()).isEmpty();assertThat(dashboard.administration()).isNull();verifyNoInteractions(managers,admins);
    }
    @Test void administratorMetricsUseTheAuthenticatedBranchAndRequireTheirOwnClaim(){
        var actor=actor(Position.MINOR_ADMIN,"B1",UserClaim.ADMIN_DASHBOARD_VIEW);when(clock.today()).thenReturn(LocalDate.of(2026,10,21));
        service.read(actor);verify(admins).dashboard("I1","B1",actor.getMemberId());verifyNoInteractions(managers);
    }
    @Test void missingBranchPlatformIdentityAndMissingClaimsFailBeforeQueries(){
        for(var actor:List.of(actor(Position.MANAGER,null,UserClaim.MANAGER_QUEUE_VIEW),actor(Position.ADMIN,"B1",UserClaim.MANAGER_QUEUE_VIEW),actor(Position.MANAGER,"B1"))){
            assertThatThrownBy(()->service.read(actor)).isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(managers,admins,clock);
    }
}
