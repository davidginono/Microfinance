package com.sacco.mvp.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserClaimTest {
    @Test
    void matrixRowsKeepUnsupportedActionCellsAsMissingClaims() {
        UserClaim.AccessMatrixRow accessMatrix = UserClaim.matrixRows().stream()
            .filter(row -> row.feature() == AccessFeature.ACCESS_MATRIX)
            .findFirst()
            .orElseThrow();

        assertThat(accessMatrix.claimsByAction().get(AccessAction.VIEW))
            .isEqualTo(UserClaim.ACCESS_MATRIX_VIEW);
        assertThat(accessMatrix.claimsByAction().get(AccessAction.ADD))
            .isNull();
        assertThat(UserClaim.forFeatureAction(AccessFeature.ACCESS_MATRIX, AccessAction.ADD))
            .isEmpty();
    }

    @Test
    void reviewQueueMatrixDoesNotExposeUnimplementedClaims() {
        assertThat(UserClaim.forFeatureAction(AccessFeature.LOAN_OFFICER_QUEUE, AccessAction.UPDATE)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.MANAGER_QUEUE, AccessAction.ASSIGN)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.MANAGER_QUEUE, AccessAction.UPDATE)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.ACCOUNTANT_QUEUE, AccessAction.ASSIGN)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.ACCOUNTANT_QUEUE, AccessAction.UPDATE)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.BOARD_QUEUE, AccessAction.ASSIGN)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.BOARD_QUEUE, AccessAction.UPDATE)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.CHAIRPERSON_QUEUE, AccessAction.ASSIGN)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.CHAIRPERSON_QUEUE, AccessAction.UPDATE)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.CREDIT_COMMITTEE_QUEUE, AccessAction.ASSIGN)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.CREDIT_COMMITTEE_QUEUE, AccessAction.UPDATE)).isEmpty();
        assertThat(UserClaim.forFeatureAction(AccessFeature.DISBURSEMENT_QUEUE, AccessAction.UPDATE)).isEmpty();
    }

    @Test
    void defaultsAndLegacyFallbackDoNotSeedUnimplementedClaims() {
        assertThat(UserClaim.defaultClaims(java.util.List.of(Position.values()), true))
            .extracting(UserClaim::name)
            .doesNotContain(
                "LOAN_OFFICER_QUEUE_UPDATE",
                "MANAGER_QUEUE_ASSIGN",
                "MANAGER_QUEUE_UPDATE",
                "ACCOUNTANT_QUEUE_ASSIGN",
                "ACCOUNTANT_QUEUE_UPDATE",
                "BOARD_QUEUE_ASSIGN",
                "BOARD_QUEUE_UPDATE",
                "CHAIRPERSON_QUEUE_ASSIGN",
                "CHAIRPERSON_QUEUE_UPDATE",
                "CREDIT_COMMITTEE_QUEUE_ASSIGN",
                "CREDIT_COMMITTEE_QUEUE_UPDATE",
                "DISBURSEMENT_QUEUE_UPDATE"
            );
        assertThat(UserClaim.fromStoredName("MANAGER_QUEUE_UPDATE")).isEmpty();
    }

    @Test
    void everyOperationalStaffWorkspaceHasARequiredViewClaim() {
        assertThat(UserClaim.requiredWorkspaceViewClaim(Position.LOAN_OFFICER))
            .contains(UserClaim.LOAN_OFFICER_QUEUE_VIEW);
        assertThat(UserClaim.requiredWorkspaceViewClaim(Position.MANAGER))
            .contains(UserClaim.MANAGER_QUEUE_VIEW);
        assertThat(UserClaim.requiredWorkspaceViewClaim(Position.ACCOUNTANT))
            .contains(UserClaim.ACCOUNTANT_QUEUE_VIEW);
        assertThat(UserClaim.requiredWorkspaceViewClaim(Position.DISBURSEMENT_OFFICER))
            .contains(UserClaim.DISBURSEMENT_QUEUE_VIEW);
        assertThat(UserClaim.requiredWorkspaceViewClaim(Position.CHAIRPERSON))
            .contains(UserClaim.CHAIRPERSON_QUEUE_VIEW);
        assertThat(UserClaim.requiredWorkspaceViewClaim(Position.BOARD))
            .contains(UserClaim.BOARD_QUEUE_VIEW);
        assertThat(UserClaim.requiredWorkspaceViewClaim(Position.CREDIT_COMMITTEE))
            .contains(UserClaim.CREDIT_COMMITTEE_QUEUE_VIEW);
        assertThat(UserClaim.requiredWorkspaceViewClaim(Position.MINOR_ADMIN)).isEmpty();
        assertThat(UserClaim.requiredWorkspaceViewClaim(Position.ADMIN)).isEmpty();
    }

    @Test
    void administratorRecoveryClaimsKeepTheAccessMatrixReachable() {
        assertThat(UserClaim.administratorRecoveryClaims()).containsExactlyInAnyOrder(
            UserClaim.ADMIN_DASHBOARD_VIEW,
            UserClaim.ACCESS_MATRIX_VIEW,
            UserClaim.ACCESS_MATRIX_UPDATE,
            UserClaim.USER_ACCESS_VIEW,
            UserClaim.USER_ACCESS_UPDATE
        );
    }
}
