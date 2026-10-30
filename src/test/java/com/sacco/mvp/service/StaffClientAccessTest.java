package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class StaffClientAccessTest {
    @Mock MemberRepository members;
    @Mock UserSettingsRepository settings;
    @Mock UserClaimService claims;
    @Mock RoleDirectoryService roles;
    @Mock SaccoRegistryService registry;
    @Mock AuditService audit;
    @InjectMocks AdminService service;

    Member administrator = staff(Position.MINOR_ADMIN);
    Member borrower = staff(Position.MANAGER);

    static Member staff(Position role) {
        return Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1")
            .memberNo("STAFF-12345").staffNo("12345").fullName("Synthetic Staff Person")
            .email("synthetic@example.test").phone("255700000001").passwordHash("existing-hash")
            .status(MemberStatus.ACTIVE).position(role).staffRoles(new LinkedHashSet<>(Set.of(role)))
            .staffAccessStatus(StaffAccessStatus.ACTIVE).memberAccount(false)
            .staffAccessActivatedAt(OffsetDateTime.now()).build();
    }

    AppUserPrincipal actor(Set<UserClaim> powers) { return new AppUserPrincipal(administrator, powers, true); }

    void authorized() {
        when(members.findByIdAndSaccoIdAndStationIdIgnoreCase(administrator.getId(), "I1", "B1"))
            .thenReturn(Optional.of(administrator));
        when(roles.hasActiveClaimInSacco(administrator.getId(), "I1", UserClaim.USER_ACCESS_UPDATE)).thenReturn(true);
        when(members.findForClientAccess(borrower.getId(), "I1", "B1")).thenReturn(Optional.of(borrower));
    }

    @Test void retainsIdentityCredentialsRolesAndExactStaffClaimsAndDoesNotVerifyKyc() {
        authorized();
        var before = new AppUserPrincipal(borrower, Set.of(UserClaim.MANAGER_QUEUE_VIEW), true);
        when(members.nextClientNumberValue()).thenReturn(42L);
        when(settings.existsById(borrower.getId())).thenReturn(true);

        assertThat(service.enableClientAccess(actor(Set.of(UserClaim.USER_ACCESS_UPDATE)), borrower.getId())).isEqualTo("CL-00000042");
        assertThat(borrower.getId()).isEqualTo(before.getMemberId());
        assertThat(borrower.getPasswordHash()).isEqualTo(before.getPassword());
        assertThat(borrower.getStaffNo()).isEqualTo(before.getStaffNo());
        assertThat(borrower.getActiveStaffRolesResolved()).isEqualTo(before.getStaffRoles());
        assertThat(borrower.isMemberAccess()).isTrue();
        assertThat(borrower.getSignatureRegisteredAt()).isNull();
        verify(claims).preserveStaffClaimsForClientAccess(borrower.getId(), Set.of(Position.MANAGER));
        verify(claims, never()).updateClaims(any(), any());
        verify(audit).log(eq("MEMBER"), eq(borrower.getId()), eq("STAFF_CLIENT_ACCESS_ENABLED"), eq(administrator.getId()),
            argThat(value -> "STAFF-12345".equals(((Map<?, ?>)value).get("memberNo"))),
            argThat(value -> "CL-00000042".equals(((Map<?, ?>)value).get("memberNo"))));
        verify(settings, never()).save(any());
    }

    @Test void repeatedCommandKeepsNumberAndRecordsOnlyOneEvent() {
        authorized();
        when(members.nextClientNumberValue()).thenReturn(43L);
        var actor = actor(Set.of(UserClaim.USER_ACCESS_UPDATE));
        assertThat(service.enableClientAccess(actor, borrower.getId())).isEqualTo("CL-00000043");
        assertThat(service.enableClientAccess(actor, borrower.getId())).isEqualTo("CL-00000043");
        verify(members).nextClientNumberValue();
        verify(members).save(borrower);
        verify(audit).log(anyString(), any(), anyString(), any(), any(), any());
    }

    @Test void missingExplicitPermissionAndClientSessionsCannotEnableAccess() {
        assertThatThrownBy(() -> service.enableClientAccess(actor(Set.of()), borrower.getId())).isInstanceOf(AccessDeniedException.class);
        administrator.setMemberAccount(true);
        var client = new AppUserPrincipal(administrator, Set.of(UserClaim.USER_ACCESS_UPDATE), false);
        assertThatThrownBy(() -> service.enableClientAccess(client, borrower.getId())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(members);
    }

    @Test void staleAdministratorAssignmentIsRejected() {
        when(members.findForClientAccess(borrower.getId(), "I1", "B1")).thenReturn(Optional.of(borrower));
        assertThatThrownBy(() -> service.enableClientAccess(actor(Set.of(UserClaim.USER_ACCESS_UPDATE)), borrower.getId()))
            .isInstanceOf(AccessDeniedException.class);
        verify(members, never()).save(any());
    }

    @Test void recordOutsideAssignedBranchIsUnavailable() {
        when(members.findForClientAccess(borrower.getId(), "I1", "B1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.enableClientAccess(actor(Set.of(UserClaim.USER_ACCESS_UPDATE)), borrower.getId()))
            .hasMessage("staffClient.error.unavailable");
        verify(members, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test void invitationsInactiveAccountsAndPlatformIdentitiesAreNotConverted() {
        authorized();
        var actor = actor(Set.of(UserClaim.USER_ACCESS_UPDATE));
        for (MemberStatus status : List.of(MemberStatus.INVITED, MemberStatus.INACTIVE)) {
            borrower.setStatus(status);
            assertThatThrownBy(() -> service.enableClientAccess(actor, borrower.getId())).hasMessage("staffClient.error.inactive");
        }
        borrower.setStatus(MemberStatus.ACTIVE);
        borrower.setStaffAccessStatus(StaffAccessStatus.PENDING_ACKNOWLEDGEMENT);
        assertThatThrownBy(() -> service.enableClientAccess(actor, borrower.getId())).hasMessage("staffClient.error.inactive");
        borrower.setStaffAccessStatus(StaffAccessStatus.ACTIVE);
        borrower.setStaffRoles(Set.of(Position.ADMIN));
        assertThatThrownBy(() -> service.enableClientAccess(actor, borrower.getId())).hasMessage("staffClient.error.inactive");
        verify(members, never()).nextClientNumberValue();
        verifyNoInteractions(audit);
    }
}
