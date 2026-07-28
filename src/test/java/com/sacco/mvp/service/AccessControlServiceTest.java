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
