package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.StaffAccessStatus;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccessControlServiceTest {
    private final AccessControlService access = new AccessControlService();

    @Test
    void rawClaimsFromAnOlderSessionCannotReactivateRetiredPermissions() {
        var actor = org.mockito.Mockito.mock(AppUserPrincipal.class);
        var stored = java.util.Arrays.stream(UserClaim.values()).map(UserClaim::name).collect(java.util.stream.Collectors.toSet());
        org.mockito.Mockito.when(actor.getClaims()).thenReturn(stored);
        var userClaims = new UserClaimService(null, null, null);
        for (var claim : UserClaim.values()) {
            assertThat(access.has(actor, claim)).as(claim.name()).isEqualTo(claim.isAvailable());
            assertThat(userClaims.has(actor, claim.name())).as(claim.name()).isEqualTo(claim.isAvailable());
        }
    }

    @Test
    void newPrincipalsDropUnavailableClaims() {
        var actor = principal(List.of(Position.ACCOUNTANT), false, true, java.util.EnumSet.allOf(UserClaim.class));
        assertThat(actor.getClaims()).contains(UserClaim.FINANCIAL_REPORTS_VIEW.name())
            .doesNotContain(UserClaim.REPORT_RUN.name(), UserClaim.FINANCIAL_REPORTS_FINALIZE.name());
    }

    @Test
    void staffQueueRequiresStaffSessionAndGranularClaim() {
        assertThat(access.canAccessDisbursementArea(principal(List.of(Position.MANAGER), false, true, Set.of())))
            .isFalse();
        assertThat(access.canAccessDisbursementArea(principal(
            List.of(Position.MANAGER),
            false,
            true,
            Set.of(UserClaim.DISBURSEMENT_QUEUE_VIEW)
        ))).isTrue();
        assertThat(access.canAccessDisbursementArea(principal(
            List.of(Position.MANAGER),
            true,
            false,
            Set.of(UserClaim.DISBURSEMENT_QUEUE_VIEW)
        ))).isFalse();
    }

    @Test
    void adminAreaRequiresAdminScopeAndClaim() {
        assertThat(access.canAccessAdminArea(principal(
            List.of(Position.MINOR_ADMIN),
            false,
            true,
            Set.of(UserClaim.USER_ACCESS_VIEW)
        ))).isTrue();
        assertThat(access.canAccessAdminArea(principal(List.of(Position.MINOR_ADMIN), false, true, Set.of())))
            .isFalse();
        assertThat(access.canAccessAdminArea(principal(
            List.of(Position.MANAGER),
            false,
            true,
            Set.of(UserClaim.USER_ACCESS_VIEW)
        ))).isTrue();
    }

    @Test
    void legacyClaimNamesAreAcceptedAsTemporaryFallbackInput() {
        AppUserPrincipal principal = principal(
            List.of(Position.MANAGER),
            false,
            true,
            Set.of(UserClaim.LOAN_RECORDING_APPROVE)
        );

        assertThat(access.has(principal, "REVIEW_MANAGER_QUEUE")).isFalse();
    }

    @Test
    void stringHasAnySupportsSpelClaimLists() {
        AppUserPrincipal principal = principal(
            List.of(Position.MANAGER),
            false,
            true,
            Set.of(UserClaim.LOAN_RECORDING_REJECT)
        );

        assertThat(access.hasAny(principal, "LOAN_RECORDING_APPROVE", "LOAN_RECORDING_REJECT")).isTrue();
        assertThat(access.hasAny(principal, "ACCOUNTANT_QUEUE_APPROVE", "ACCOUNTANT_QUEUE_REJECT")).isFalse();
    }

    @Test
    void decisionChecksRequireMatchingApproveOrRejectClaim() {
        AppUserPrincipal approver = principal(
            List.of(Position.MANAGER),
            false,
            true,
            Set.of(UserClaim.LOAN_RECORDING_APPROVE)
        );
        AppUserPrincipal rejecter = principal(
            List.of(Position.MANAGER),
            false,
            true,
            Set.of(UserClaim.LOAN_RECORDING_REJECT)
        );

        assertThat(access.canDecide(approver, "ACCEPT", "LOAN_RECORDING_APPROVE", "LOAN_RECORDING_REJECT")).isTrue();
        assertThat(access.canDecide(approver, "REJECT", "LOAN_RECORDING_APPROVE", "LOAN_RECORDING_REJECT")).isFalse();
        assertThat(access.canDecide(rejecter, "REJECTED", "LOAN_RECORDING_APPROVE", "LOAN_RECORDING_REJECT")).isTrue();
    }

    private AppUserPrincipal principal(List<Position> staffRoles,
                                       boolean memberAccess,
                                       boolean staffSession,
                                       Set<UserClaim> claims) {
        LinkedHashSet<Position> roles = new LinkedHashSet<>(staffRoles);
        Position primaryRole = Position.primaryRole(roles, memberAccess);
        Member member = Member.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("MEM-" + UUID.randomUUID())
            .staffNo(staffSession ? "STAFF-" + UUID.randomUUID() : null)
            .fullName("Access Test User")
            .memberAccount(memberAccess)
            .staffAccessStatus(staffSession ? StaffAccessStatus.ACTIVE : StaffAccessStatus.NONE)
            .status(MemberStatus.ACTIVE)
            .position(primaryRole)
            .staffRoles(roles)
            .passwordHash("secret")
            .createdAt(OffsetDateTime.now())
            .build();
        return (staffSession ? new com.sacco.mvp.security.AppUserPrincipal(member,claims) : com.sacco.mvp.config.MvcSecurityTestConfiguration.formerClient(member,claims));
    }
}
