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
    void financeWorkspaceRequiresExplicitClaimAndScopedStaffSession() {
        assertThat(access.canAccessFinanceArea(principal(List.of(Position.ACCOUNTANT), false, true, Set.of())))
            .isFalse();
        assertThat(access.canAccessFinanceArea(principal(List.of(Position.MANAGER), false, true,
            Set.of(UserClaim.ACCOUNTING_JOURNAL_APPROVE)))).isTrue();
        assertThat(access.canAccessFinanceArea(principal(List.of(Position.ADMIN), false, true,
            Set.of(UserClaim.ACCOUNTING_VIEW)))).isFalse();
        assertThat(access.canAccessFinanceArea(principal(List.of(), true, false,
            Set.of(UserClaim.REPORTS_RUN)))).isFalse();
        AppUserPrincipal missingBranch = org.mockito.Mockito.mock(AppUserPrincipal.class);
        org.mockito.Mockito.when(missingBranch.isStaffSession()).thenReturn(true);
        org.mockito.Mockito.when(missingBranch.getSaccoId()).thenReturn("I1");
        org.mockito.Mockito.when(missingBranch.getClaims()).thenReturn(Set.of("ACCOUNTING_VIEW"));
        assertThat(access.canAccessFinanceArea(missingBranch)).isFalse();
    }

    @Test
    void staffQueueRequiresStaffSessionAndGranularClaim() {
        assertThat(access.canAccessManagerArea(principal(List.of(Position.MANAGER), false, true, Set.of())))
            .isFalse();
        assertThat(access.canAccessManagerArea(principal(
            List.of(Position.MANAGER),
            false,
            true,
            Set.of(UserClaim.MANAGER_QUEUE_VIEW)
        ))).isTrue();
        assertThat(access.canAccessManagerArea(principal(
            List.of(Position.MANAGER),
            true,
            false,
            Set.of(UserClaim.MANAGER_QUEUE_VIEW)
        ))).isFalse();
    }

    @Test
    void memberAreaRequiresMemberSessionAndClaim() {
        assertThat(access.canAccessMemberArea(principal(List.of(), true, false, Set.of())))
            .isFalse();
        assertThat(access.canAccessMemberArea(principal(
            List.of(),
            true,
            false,
            Set.of(UserClaim.MEMBER_LOANS_VIEW)
        ))).isTrue();
        assertThat(access.canAccessMemberArea(principal(
            List.of(Position.MANAGER),
            true,
            true,
            Set.of(UserClaim.MEMBER_LOANS_VIEW)
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
        ))).isFalse();
    }

    @Test
    void legacyClaimNamesAreAcceptedAsTemporaryFallbackInput() {
        AppUserPrincipal principal = principal(
            List.of(Position.MANAGER),
            false,
            true,
            Set.of(UserClaim.MANAGER_QUEUE_APPROVE)
        );

        assertThat(access.has(principal, "REVIEW_MANAGER_QUEUE")).isTrue();
    }

    @Test
    void stringHasAnySupportsSpelClaimLists() {
        AppUserPrincipal principal = principal(
            List.of(Position.MANAGER),
            false,
            true,
            Set.of(UserClaim.MANAGER_QUEUE_REJECT)
        );

        assertThat(access.hasAny(principal, "MANAGER_QUEUE_APPROVE", "MANAGER_QUEUE_REJECT")).isTrue();
        assertThat(access.hasAny(principal, "ACCOUNTANT_QUEUE_APPROVE", "ACCOUNTANT_QUEUE_REJECT")).isFalse();
    }

    @Test
    void decisionChecksRequireMatchingApproveOrRejectClaim() {
        AppUserPrincipal approver = principal(
            List.of(Position.MANAGER),
            false,
            true,
            Set.of(UserClaim.MANAGER_QUEUE_APPROVE)
        );
        AppUserPrincipal rejecter = principal(
            List.of(Position.MANAGER),
            false,
            true,
            Set.of(UserClaim.MANAGER_QUEUE_REJECT)
        );

        assertThat(access.canDecide(approver, "ACCEPT", "MANAGER_QUEUE_APPROVE", "MANAGER_QUEUE_REJECT")).isTrue();
        assertThat(access.canDecide(approver, "REJECT", "MANAGER_QUEUE_APPROVE", "MANAGER_QUEUE_REJECT")).isFalse();
        assertThat(access.canDecide(rejecter, "REJECTED", "MANAGER_QUEUE_APPROVE", "MANAGER_QUEUE_REJECT")).isTrue();
    }

    @Test
    void memberAreaAcceptsNarrowMemberReportAndDocumentClaims() {
        assertThat(access.canAccessMemberArea(principal(
            List.of(),
            true,
            false,
            Set.of(UserClaim.LOAN_REPORTS_VIEW)
        ))).isTrue();
        assertThat(access.canAccessMemberArea(principal(
            List.of(),
            true,
            false,
            Set.of(UserClaim.LOAN_DOCUMENTS_VIEW)
        ))).isTrue();
    }

    @Test
    void memberAreaAcceptsNarrowSettingsAndPaymentClaims() {
        assertThat(access.canAccessMemberArea(principal(
            List.of(),
            true,
            false,
            Set.of(UserClaim.MEMBER_SETTINGS_VIEW)
        ))).isTrue();
        assertThat(access.canAccessMemberArea(principal(
            List.of(),
            true,
            false,
            Set.of(UserClaim.PAYMENT_DETAILS_UPDATE)
        ))).isTrue();
    }

    @Test
    void chairpersonReadOnlyDestinationsDependOnIndependentClaimsNotPosition() {
        AppUserPrincipal managerWithProcessedLoans = principal(
            List.of(Position.MANAGER), false, true, Set.of(UserClaim.PROCESSED_LOANS_VIEW));
        AppUserPrincipal chairpersonWithoutClaims = principal(
            List.of(Position.CHAIRPERSON), false, true, Set.of());
        AppUserPrincipal configurationsOnly = principal(
            List.of(Position.ACCOUNTANT), false, true, Set.of(UserClaim.SACCO_CONFIGURATIONS_VIEW));

        assertThat(access.canViewProcessedLoans(managerWithProcessedLoans)).isTrue();
        assertThat(access.canViewSaccoConfigurations(managerWithProcessedLoans)).isFalse();
        assertThat(access.canViewProcessedLoans(chairpersonWithoutClaims)).isFalse();
        assertThat(access.canViewSaccoConfigurations(chairpersonWithoutClaims)).isFalse();
        assertThat(access.canViewSaccoConfigurations(configurationsOnly)).isTrue();
        assertThat(access.canViewProcessedLoans(configurationsOnly)).isFalse();
    }

    @Test
    void chairpersonReadOnlyDestinationsFailClosedWithoutStationScope() {
        Member member = Member.builder()
            .id(UUID.randomUUID()).saccoId("SACCO-01").stationId(null).memberNo("MEM-1").staffNo("STAFF-1")
            .fullName("Missing Station").memberAccount(false).staffAccessStatus(StaffAccessStatus.ACTIVE)
            .status(MemberStatus.ACTIVE).position(Position.MANAGER)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MANAGER))).passwordHash("secret")
            .createdAt(OffsetDateTime.now()).build();
        AppUserPrincipal principal = new AppUserPrincipal(member,
            Set.of(UserClaim.PROCESSED_LOANS_VIEW, UserClaim.SACCO_CONFIGURATIONS_VIEW), true);

        assertThat(access.canViewProcessedLoans(principal)).isFalse();
        assertThat(access.canViewSaccoConfigurations(principal)).isFalse();
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
        return new AppUserPrincipal(member, claims, staffSession);
    }
}
