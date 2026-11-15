package com.sacco.mvp.staffoffice;

import com.sacco.mvp.accounting.approval.service.VoucherWorkflowService;
import com.sacco.mvp.accounting.loans.service.*;
import com.sacco.mvp.accounting.service.AccountOfficeWorkService;
import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.lending.controls.service.CreditControlService;
import com.sacco.mvp.security.*;
import com.sacco.mvp.service.*;
import com.sacco.mvp.staffoffice.dto.OfficeWork;
import com.sacco.mvp.staffoffice.model.StaffOffice;
import com.sacco.mvp.staffoffice.service.StaffOfficeService;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffOfficeServiceTest {
    MemberDirectoryService members=mock(MemberDirectoryService.class);
    UserClaimService claims=mock(UserClaimService.class);
    SaccoRegistryService institutions=mock(SaccoRegistryService.class);
    LoanGroupApprovalService reviews=mock(LoanGroupApprovalService.class);
    LoanRecordingService loans=mock(LoanRecordingService.class);
    VoucherWorkflowService vouchers=mock(VoucherWorkflowService.class);
    AccountOfficeWorkService accounts=mock(AccountOfficeWorkService.class);
    OnboardingService clients=mock(OnboardingService.class);
    CreditControlService credit=mock(CreditControlService.class);
    AdminOfficeWorkService administration=mock(AdminOfficeWorkService.class);
    StaffOfficeService service=new StaffOfficeService(members,claims,institutions,new AccessControlService(),reviews,loans,vouchers,accounts,clients,credit,administration);
    Member member;
    AppUserPrincipal actor(StaffOffice office,UserClaim... permissions) {
        member=Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(office.getGroup())
            .staffRoles(Set.of(office.getGroup())).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE)
            .fullName("New staff").staffNo("STAFF-1").build();
        when(members.find(member.getId())).thenReturn(Optional.of(member));
        when(claims.effectiveClaims(member.getId(),member.getActiveStaffRolesResolved(),false)).thenReturn(Set.of(permissions));
        when(institutions.findActiveSacco("I1")).thenReturn(Optional.of(RegisteredSacco.builder().saccoId("I1").active(true).build()));
        when(institutions.findStation("I1","B1")).thenReturn(Optional.of(SaccoStation.builder().saccoId("I1").stationId("B1").active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        return new AppUserPrincipal(member,Set.of(permissions));
    }
    @Test void allOfficesAcceptStaffWithNoProfileAndNoClaimsWithoutBusinessQueries() {
        for(var office:StaffOffice.values()) {
            var a=actor(office);
            for(var section:List.of("dashboard","queue","reports")) {
                var view=service.read(a,office.getSlug(),section,"","","",0);
                assertThat(view.office()).isEqualTo(office);assertThat(view.summaries()).isEmpty();
                assertThat(view.work().rows()).isEmpty();assertThat(view.reports()).isEmpty();
            }
            assertThat(WorkspaceLanding.staffDashboard(a)).isEqualTo(office.getDashboard());
        }
        verifyNoInteractions(reviews,loans,vouchers,accounts,clients,credit,administration);
    }
    @Test void assignmentsAreOrderedAndHistoricalAliasesHaveOneOffice() {
        assertThat(StaffOffice.assigned(Set.of(Position.CHAIRPERSON,Position.FINANCE_MANAGER,Position.MINOR_ADMIN,Position.ICT_MANAGER,Position.LOAN_OFFICER)))
            .containsExactly(StaffOffice.LOAN_OFFICER,StaffOffice.FINANCE_MANAGER,StaffOffice.ICT_MANAGER);
        assertThat(StaffOffice.assigned(Set.of(Position.ADMIN,Position.ACCOUNTANT))).isEmpty();
        assertThat(StaffOffice.assigned(Set.of(Position.BOARD,Position.CREDIT_COMMITTEE,Position.DISBURSEMENT_OFFICER)))
            .containsExactly(StaffOffice.CREDIT_OFFICER,StaffOffice.ACCOUNTS_CLERK);
    }
    @Test void savedRequestsStayLocalAndRetiredOrUnassignedWorkspacesUseTheNewLanding() {
        var a=actor(StaffOffice.CUSTOMER_CARE);
        assertThat(WorkspaceLanding.afterLogin(a,"/manager/loan-applications")).isEqualTo("/staff/offices/customer-care/dashboard");
        assertThat(WorkspaceLanding.afterLogin(a,"/staff/offices/chief-accountant/queue")).isEqualTo("/staff/offices/customer-care/dashboard");
        assertThat(WorkspaceLanding.afterLogin(a,"http://localhost:8080/profile?tab=contact")).isEqualTo("/profile?tab=contact");
        assertThat(WorkspaceLanding.afterLogin(a,"//outside.invalid/path")).isEqualTo("/staff/offices/customer-care/dashboard");
        assertThat(WorkspaceLanding.afterLogin(a,"/%2Foutside.invalid/path")).isEqualTo("/staff/offices/customer-care/dashboard");
    }
    @Test void officeAndBranchChangesFailClosed() {
        var a=actor(StaffOffice.CUSTOMER_CARE);
        assertThatThrownBy(()->service.read(a,"ict-manager","queue","","","",0)).isInstanceOf(AccessDeniedException.class);
        member.setStationId("B2");
        assertThatThrownBy(()->service.read(a,"customer-care","dashboard","","","",0)).isInstanceOf(AccessDeniedException.class);
        member.setStationId("B1");member.setStaffRoles(Set.of(Position.CREDIT_OFFICER));member.setPosition(Position.CREDIT_OFFICER);
        assertThatThrownBy(()->service.read(a,"customer-care","dashboard","","","",0)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(reviews,loans,vouchers,accounts,clients,credit,administration);
    }
    @Test void suspendedStaffAndPlatformIdentitiesCannotEnterAnOffice() {
        var a=actor(StaffOffice.ICT_MANAGER);member.setStaffAccessStatus(StaffAccessStatus.NONE);
        assertThatThrownBy(()->service.read(a,"ict-manager","dashboard","","","",0)).isInstanceOf(AccessDeniedException.class);
        member.setStaffAccessStatus(StaffAccessStatus.ACTIVE);member.setStaffRoles(Set.of(Position.ADMIN));member.setPosition(Position.ADMIN);
        var platform=new AppUserPrincipal(member,Set.of());
        assertThat(WorkspaceLanding.staffDashboard(platform)).isEqualTo("/admin/dashboard");
        assertThatThrownBy(()->service.read(platform,"ict-manager","dashboard","","","",0)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void revokedClaimsCannotLeaveReportsOrQueueSourcesVisible() {
        var a=actor(StaffOffice.CHIEF_ACCOUNTANT,UserClaim.FINANCIAL_REPORTS_VIEW,UserClaim.LOAN_REPORTS_VIEW,UserClaim.CLIENT_ONBOARDING_VIEW);
        when(claims.effectiveClaims(member.getId(),member.getActiveStaffRolesResolved(),false)).thenReturn(Set.of());
        var view=service.read(a,"chief-accountant","dashboard","","","",0);
        assertThat(view.sources()).isEmpty();assertThat(view.reports()).isEmpty();
        verifyNoInteractions(reviews,loans,vouchers,accounts,clients,credit,administration);
    }
    @Test void reportsRequireEveryExistingClaimAndNeverGrantExports() {
        var a=actor(StaffOffice.FINANCE_MANAGER,UserClaim.FINANCIAL_REPORTS_VIEW,UserClaim.LOAN_REPORTS_VIEW);
        var view=service.read(a,"finance-manager","reports","","","",0);
        assertThat(view.reports().stream().map(r->r.href().split("\\?")[0]).toList()).contains("/reports/financial/operations/expected-repayments")
            .doesNotContain("/reports/financial/operations/cashbook","/reports/financial/sl-gl-reconciliation");
        assertThat(view.reports()).allMatch(r->!r.href().endsWith("/excel")&&!r.href().endsWith("/pdf"));
        verifyNoInteractions(reviews,loans,vouchers,accounts,clients,credit,administration);
    }
    @Test void reviewQueuesDelegateOnceWithTheActorAndSelectedOffice() {
        var a=actor(StaffOffice.CREDIT_OFFICER,UserClaim.LOAN_RECORDING_VIEW,UserClaim.LOAN_RECORDING_REJECT);
        when(reviews.officeReviews(any(),eq(Position.CREDIT_OFFICER),eq("LN-2"),eq("oldest"),eq(2)))
            .thenReturn(OfficeWork.Page.empty(2));
        var view=service.read(a,"credit-officer","queue","loan-reviews","LN-2","oldest",2);
        assertThat(view.work().page()).isEqualTo(2);
        verify(reviews).officeReviews(argThat(p->p.getMemberId().equals(a.getMemberId())&&p.getSaccoId().equals("I1")&&p.getStationId().equals("B1")),eq(Position.CREDIT_OFFICER),eq("LN-2"),eq("oldest"),eq(2));
        verifyNoInteractions(loans,vouchers,accounts,clients,credit,administration);
    }
    @Test void forgedSourcesAndOversizedFiltersNeverReachBusinessServices() {
        var a=actor(StaffOffice.CUSTOMER_CARE);
        assertThatThrownBy(()->service.read(a,"customer-care","queue","loan-reviews","","",0)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.read(a,"customer-care","queue","","x".repeat(101),"",0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.read(a,"customer-care","queue","","","",10001)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(reviews,loans,vouchers,accounts,clients,credit,administration);
    }
    @Test void loanReviewCardIsIncludedBeforeOtherFinanceWorkAndOpensTheReviewerQueue() {
        var a=actor(StaffOffice.FINANCE_MANAGER,UserClaim.LOAN_RECORDING_VIEW,UserClaim.LOAN_RECORDING_APPROVE,
            UserClaim.VOUCHER_WORKSPACE_VIEW,UserClaim.ACCOUNTING_BUSINESS_VIEW,UserClaim.ACCOUNTING_ACCOUNTS_VIEW,
            UserClaim.ACCOUNTING_ACCOUNT_APPROVAL_VIEW,UserClaim.ACCOUNTING_ACCOUNT_APPROVAL_APPROVE);
        var pending=new OfficeWork.Page(List.of(new OfficeWork.Task("LN-1","Borrower","office.readyReview",null,"/finance/loan-records/1")),0,false);
        when(reviews.officeReviews(any(),eq(Position.FINANCE_MANAGER),anyString(),anyString(),anyInt())).thenReturn(pending);
        when(vouchers.officeWork(any(),any(),anyBoolean(),anyString(),anyString(),anyInt())).thenReturn(OfficeWork.Page.empty(0));
        when(accounts.read(any(),anyBoolean(),anyString(),anyString(),anyInt())).thenReturn(OfficeWork.Page.empty(0));
        var dashboard=service.read(a,"finance-manager","dashboard","","","",0);
        assertThat(dashboard.sources().getFirst().key()).isEqualTo("loan-reviews");
        assertThat(dashboard.summaries()).hasSize(3);assertThat(dashboard.summaries().getFirst().labelKey()).isEqualTo("office.source.loan-reviews");
        assertThat(dashboard.summaries().getFirst().shown()).isEqualTo(1);
        var queue=service.read(a,"finance-manager","queue","","","",0);
        assertThat(queue.source().key()).isEqualTo("loan-reviews");
        assertThat(queue.work().rows().getFirst().href()).contains("office=finance-manager","officeSource=loan-reviews");
    }
}
