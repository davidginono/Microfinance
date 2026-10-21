package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service @RequiredArgsConstructor
public class StaffDashboardService {
    private final AccessControlService access;
    private final ManagerService managers;
    private final AdminService admins;
    private final ApplicationClock clock;

    public record Role(String key,String labelKey,long pending,List<ManagerService.StatusCount> statuses) {}
    public record Dashboard(ManagerService.ManagerDashboard portfolio,List<Role> roles,AdminService.AdminDashboard administration,int year) {}

    @Transactional(readOnly=true)
    public Dashboard read(AppUserPrincipal actor) {
        if(!access.staffDashboardAccess(actor))throw new AccessDeniedException("Staff dashboard unavailable");
        var roles=new ArrayList<Role>();
        var portfolio=access.canViewAnyReviewQueue(actor)||access.canAccessDisbursementArea(actor)
            ?managers.dashboardSummary(actor.getSaccoId(),actor.getStationId()):null;
        if(portfolio!=null){
            if(access.canAccessManagerArea(actor))add(roles,portfolio,"manager","role.manager",LoanStatus.READY_FOR_MANAGER,LoanStatus.MANAGER_ACCEPTED,LoanStatus.MANAGER_REJECTED);
            if(access.canAccessLoanOfficerArea(actor))add(roles,portfolio,"loan-officer","role.loanOfficer",LoanStatus.AWAITING_LOAN_OFFICER,LoanStatus.LOAN_OFFICER_APPROVED,LoanStatus.LOAN_OFFICER_REJECTED);
            if(access.canAccessChairpersonArea(actor))add(roles,portfolio,"chairperson","role.chairperson",LoanStatus.AWAITING_CHAIRPERSON,LoanStatus.CHAIRPERSON_APPROVED,LoanStatus.CHAIRPERSON_REJECTED);
            if(access.canAccessBoardArea(actor))add(roles,portfolio,"board","role.boardMember",LoanStatus.AWAITING_BOARD,LoanStatus.BOARD_APPROVED,LoanStatus.BOARD_REJECTED);
            if(access.canAccessCreditCommitteeArea(actor))add(roles,portfolio,"credit-committee","role.committee",LoanStatus.AWAITING_CREDIT_COMMITTEE,LoanStatus.CREDIT_COMMITTEE_APPROVED,LoanStatus.CREDIT_COMMITTEE_REJECTED);
            if(access.canAccessAccountantArea(actor))add(roles,portfolio,"accountant","role.accountant",LoanStatus.AWAITING_ACCOUNTANT,LoanStatus.ACCOUNTANT_APPROVED,LoanStatus.ACCOUNTANT_REJECTED);
            if(access.canAccessDisbursementArea(actor))add(roles,portfolio,"disbursement","role.disbursementOfficer",LoanStatus.READY_FOR_DISBURSEMENT,LoanStatus.DISBURSED,LoanStatus.PAR,LoanStatus.PAID,LoanStatus.DEFAULTED);
        }
        var administration=access.isWorkspaceAdminScope(actor)&&access.has(actor,UserClaim.ADMIN_DASHBOARD_VIEW)
            ?admins.dashboard(actor.getSaccoId(),actor.getStationId(),actor.getMemberId()):null;
        return new Dashboard(portfolio,List.copyOf(roles),administration,clock.today().getYear());
    }
    private static void add(List<Role> roles,ManagerService.ManagerDashboard portfolio,String key,String label,LoanStatus... statuses){
        var rows=java.util.Arrays.stream(statuses).map(status->new ManagerService.StatusCount(status,
            portfolio.statusBreakdown().stream().filter(row->row.status()==status).mapToLong(ManagerService.StatusCount::count).findFirst().orElse(0))).toList();
        roles.add(new Role(key,label,rows.getFirst().count(),rows));
    }
}
