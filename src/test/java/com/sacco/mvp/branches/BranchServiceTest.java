package com.sacco.mvp.branches;

import com.sacco.mvp.branches.service.BranchService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AdminService;
import com.sacco.mvp.service.AuditService;
import com.sacco.mvp.service.SaccoRegistryService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BranchServiceTest {
    private final AdminService access=mock(AdminService.class);
    private final SaccoRegistryService registry=mock(SaccoRegistryService.class);
    private final AuditService audit=mock(AuditService.class);
    private final BranchService service=new BranchService(access,registry,audit);
    private final AppUserPrincipal actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1")
        .position(Position.ICT_MANAGER).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),Set.of());

    @Test void oversightChecksTheLiveGrantAndEachAreaIndependently() {
        when(access.workspaceClaims(actor)).thenReturn(Set.of(UserClaim.OTHER_BRANCHES_VIEW,UserClaim.LOAN_RECORDING_VIEW));
        when(registry.findStation("I1","B2")).thenReturn(Optional.of(branch("B2",true,SaccoAccessStatus.ACTIVE)));
        when(registry.institutionBranchIds("I1")).thenReturn(List.of("B1","B2"));
        var scope=service.oversight(actor,"B2");
        assertThat(scope.institution()).isEqualTo("I1");
        assertThat(scope.branch().id()).isEqualTo("B2");
        assertThat(scope.permits(UserClaim.LOAN_RECORDING_VIEW)).isTrue();
        assertThatThrownBy(() -> scope.require(UserClaim.CLIENT_ONBOARDING_VIEW)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.oversight(actor,"FOREIGN")).isInstanceOf(AccessDeniedException.class);
        when(access.workspaceClaims(actor)).thenReturn(Set.of(UserClaim.LOAN_RECORDING_VIEW));
        assertThatThrownBy(() -> service.oversight(actor,"B2")).isInstanceOf(AccessDeniedException.class);
    }

    @Test void suspendedBranchShowsItsStatusWithoutItsRecords() {
        when(access.workspaceClaims(actor)).thenReturn(Set.of(UserClaim.OTHER_BRANCHES_VIEW,UserClaim.LOAN_RECORDING_VIEW));
        when(registry.findStation("I1","B2")).thenReturn(Optional.of(branch("B2",true,SaccoAccessStatus.SUSPENDED)));
        var scope=service.oversight(actor,"B2");
        assertThat(scope.branch().suspended()).isTrue();
        assertThatThrownBy(() -> scope.require(UserClaim.LOAN_RECORDING_VIEW)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void ictAdministratorsCreateBranchesWithTheLiveCreateClaim() {
        var locked=UserClaim.lockedClaims(Set.of(Position.ICT_MANAGER));
        assertThat(locked).contains(UserClaim.USER_ACCESS_VIEW,UserClaim.USER_ACCESS_UPDATE,UserClaim.BRANCHES_UPDATE,UserClaim.BRANCHES_CREATE,UserClaim.STAFF_BRANCH_ASSIGN)
            .doesNotContain(UserClaim.INSTITUTION_BRANCH_ADMIN,UserClaim.OTHER_BRANCHES_VIEW,UserClaim.LOAN_RECORDING_APPROVE);
        assertThat(UserClaim.defaultClaims(Set.of(Position.ICT_MANAGER),false)).containsAll(locked);
        when(access.workspaceClaims(actor)).thenReturn(locked);
        when(registry.findStation("I1","B1")).thenReturn(Optional.of(branch("B1",true,SaccoAccessStatus.ACTIVE)));
        assertThat(service.branchForEdit(actor,"B1").id()).isEqualTo("B1");
        assertThatThrownBy(() -> service.branchForEdit(actor,"B2")).isInstanceOf(AccessDeniedException.class);
        when(access.workspaceClaims(actor)).thenReturn(Set.of(UserClaim.BRANCHES_VIEW,UserClaim.BRANCHES_UPDATE));
        assertThatThrownBy(() -> service.create(actor,"B2","Arusha")).isInstanceOf(AccessDeniedException.class);
        when(access.workspaceClaims(actor)).thenReturn(locked);
        service.create(actor,"b2","Arusha");
        verify(registry).addStation("I1","B2","Arusha");
    }

    private static SaccoStation branch(String id,boolean active,SaccoAccessStatus status) {
        return SaccoStation.builder().saccoId("I1").stationId(id).active(active).accessStatus(status).build();
    }
}
