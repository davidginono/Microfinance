package com.sacco.mvp.service;

import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.MemberAccessClaim;
import com.sacco.mvp.domain.MemberAccessClaimId;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.domain.UserSettings;
import com.sacco.mvp.repository.MemberAccessClaimRepository;
import com.sacco.mvp.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserClaimServiceTest {
    @Mock private MemberAccessClaimRepository memberAccessClaimRepository;
    @Mock private UserSettingsRepository userSettingsRepository;

    private UserClaimService userClaimService;

    @BeforeEach
    void setUp() {
        userClaimService = new UserClaimService(
            memberAccessClaimRepository,
            userSettingsRepository,
            new ObjectMapper()
        );
    }

    @Test
    void structuredClaimsTakePrecedenceOverLegacyJsonFallback() {
        UUID memberId = UUID.randomUUID();
        when(memberAccessClaimRepository.findByIdMemberId(memberId))
            .thenReturn(List.of(accessClaim(memberId, UserClaim.MANAGER_QUEUE_APPROVE.name())));

        Set<UserClaim> claims = userClaimService.effectiveClaims(
            memberId,
            List.of(Position.MANAGER),
            false
        );

        assertThat(claims).containsExactly(UserClaim.MANAGER_QUEUE_APPROVE);
        verifyNoInteractions(userSettingsRepository);
    }

    @Test
    void storedLegacyAdminSettingsClaimKeepsPlatformAdminPlatformSettingsAccess() {
        UUID memberId = UUID.randomUUID();
        when(memberAccessClaimRepository.findByIdMemberId(memberId))
            .thenReturn(List.of(accessClaim(memberId, "ACCESS_ADMIN_SETTINGS")));

        Set<UserClaim> claims = userClaimService.effectiveClaims(memberId, List.of(Position.ADMIN), false);

        assertThat(claims).contains(
            UserClaim.ADMIN_DASHBOARD_VIEW,
            UserClaim.PLATFORM_SETTINGS_VIEW,
            UserClaim.PLATFORM_SETTINGS_UPDATE
        );
    }

    @Test
    void storedLegacyAdminSettingsClaimDoesNotGrantWorkspaceAdminPlatformSettingsAccess() {
        UUID memberId = UUID.randomUUID();
        when(memberAccessClaimRepository.findByIdMemberId(memberId))
            .thenReturn(List.of(accessClaim(memberId, "ACCESS_ADMIN_SETTINGS")));

        Set<UserClaim> claims = userClaimService.effectiveClaims(memberId, List.of(Position.MINOR_ADMIN), false);

        assertThat(claims)
            .contains(UserClaim.ADMIN_DASHBOARD_VIEW)
            .doesNotContain(UserClaim.PLATFORM_SETTINGS_VIEW, UserClaim.PLATFORM_SETTINGS_UPDATE);
    }

    @Test
    void legacyJsonClaimsAreMappedToGranularClaimsWhenMatrixRowsAreMissing() {
        UUID memberId = UUID.randomUUID();
        UserSettings settings = UserSettings.builder()
            .memberId(memberId)
            .language("en")
            .notificationPrefs("{\"claims\":[\"REVIEW_MANAGER_QUEUE\",\"DISBURSE_LOAN\"]}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(memberAccessClaimRepository.findByIdMemberId(memberId)).thenReturn(List.of());
        when(userSettingsRepository.findById(memberId)).thenReturn(Optional.of(settings));

        Set<UserClaim> claims = userClaimService.effectiveClaims(memberId, List.of(), false);

        assertThat(claims).contains(
            UserClaim.MANAGER_QUEUE_VIEW,
            UserClaim.MANAGER_QUEUE_APPROVE,
            UserClaim.MANAGER_QUEUE_REJECT,
            UserClaim.DISBURSEMENT_QUEUE_VIEW,
            UserClaim.DISBURSEMENT_QUEUE_DISBURSE
        );
    }

    @Test
    void memberLegacyDefaultsIncludeReportsDocumentsAndGranularMutationClaims() {
        UUID memberId = UUID.randomUUID();
        when(memberAccessClaimRepository.findByIdMemberId(memberId)).thenReturn(List.of());
        when(userSettingsRepository.findById(memberId)).thenReturn(Optional.empty());

        Set<UserClaim> claims = userClaimService.effectiveClaims(memberId, List.of(), true);

        assertThat(claims).contains(
            UserClaim.MEMBER_SETTINGS_VIEW,
            UserClaim.MEMBER_SETTINGS_UPDATE,
            UserClaim.PAYMENT_DETAILS_VIEW,
            UserClaim.PAYMENT_DETAILS_UPDATE,
            UserClaim.MEMBER_LOANS_VIEW,
            UserClaim.MEMBER_LOANS_ASSIGN,
            UserClaim.MEMBER_LOANS_DELETE,
            UserClaim.GUARANTOR_REQUESTS_UPDATE,
            UserClaim.LOAN_DOCUMENTS_EXPORT,
            UserClaim.LOAN_REPORTS_VIEW,
            UserClaim.LOAN_REPORTS_EXPORT
        );
    }

    @Test
    void defaultClaimsCombineMultipleRolesAndMemberAccess() {
        Set<UserClaim> claims = userClaimService.defaultClaims(
            List.of(Position.MANAGER, Position.DISBURSEMENT_OFFICER),
            true
        );

        assertThat(claims).contains(
            UserClaim.MEMBER_LOANS_VIEW,
            UserClaim.GUARANTOR_REQUESTS_APPROVE,
            UserClaim.MANAGER_QUEUE_APPROVE,
            UserClaim.DISBURSEMENT_QUEUE_DISBURSE,
            UserClaim.LOAN_DOCUMENTS_VIEW,
            UserClaim.NOTIFICATIONS_UPDATE
        );
    }

    @Test
    void updateClaimsPersistsMatrixRowsAndRemovesLegacyJsonClaims() {
        UUID memberId = UUID.randomUUID();
        UserSettings settings = UserSettings.builder()
            .memberId(memberId)
            .language("en")
            .notificationPrefs("{\"claims\":[\"APPLY_LOANS\"],\"theme\":\"compact\"}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        when(userSettingsRepository.findById(memberId)).thenReturn(Optional.of(settings));

        userClaimService.updateClaims(memberId, List.of(UserClaim.USER_ACCESS_VIEW, UserClaim.USER_ACCESS_UPDATE));

        verify(memberAccessClaimRepository).deleteByMemberId(memberId);
        verify(memberAccessClaimRepository).save(claimNamed(memberId, UserClaim.USER_ACCESS_VIEW.name()));
        verify(memberAccessClaimRepository).save(claimNamed(memberId, UserClaim.USER_ACCESS_UPDATE.name()));
        verify(userSettingsRepository).save(settings);
        assertThat(settings.getNotificationPrefs()).contains("theme").doesNotContain("claims");
    }

    private MemberAccessClaim accessClaim(UUID memberId, String claimName) {
        return MemberAccessClaim.builder()
            .id(new MemberAccessClaimId(memberId, claimName))
            .build();
    }

    private MemberAccessClaim claimNamed(UUID memberId, String claimName) {
        return argThat(saved -> saved != null
            && saved.getId() != null
            && memberId.equals(saved.getId().getMemberId())
            && claimName.equals(saved.getId().getClaimName()));
    }
}
