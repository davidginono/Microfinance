package com.sacco.mvp.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserClaimTest {
    @Test
    void accountingAndReportCapabilitiesRequireExplicitGrantsAndDistinctMatrixCells() {
        var defaults = UserClaim.defaultClaims(java.util.List.of(Position.values()), true);
        for (UserClaim claim : UserClaim.values()) {
            if (claim.name().startsWith("ACCOUNTING_") || claim.name().startsWith("REPORT_TEMPLATES_")
                || claim.name().startsWith("REPORTS_")) {
                assertThat(defaults).doesNotContain(claim);
                assertThat(UserClaim.forFeatureAction(claim.getFeature(), claim.getAction())).contains(claim);
                assertThat(UserClaim.fromStoredName(claim.name())).containsExactly(claim);
            }
        }
    }

    @Test
    void roleLabelsUseMicrofinanceLanguageWithoutRenamingStoredRoles() {
        assertThat(Position.MEMBER.getDisplayName()).isEqualTo("Client");
        assertThat(Position.MINOR_ADMIN.getDisplayName()).isEqualTo("Institution Admin");
        assertThat(Position.valueOf("MEMBER")).isSameAs(Position.MEMBER);
        assertThat(Position.valueOf("MINOR_ADMIN")).isSameAs(Position.MINOR_ADMIN);
    }

    @Test
    void repaymentPermissionsAreExplicitRatherThanAutomaticallyGranted() {
        assertThat(UserClaim.defaultClaims(java.util.List.of(Position.values()), true))
            .doesNotContain(UserClaim.LOAN_REPAYMENTS_VIEW, UserClaim.LOAN_REPAYMENTS_CREATE, UserClaim.LOAN_REPAYMENTS_REVERSE);
        assertThat(UserClaim.forFeatureAction(AccessFeature.LOAN_REPAYMENTS, AccessAction.REVERSE))
            .contains(UserClaim.LOAN_REPAYMENTS_REVERSE);
    }

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

    @Test
    void chairpersonReadOnlyClaimsAreIndependentMatrixFeaturesAndFutureDefaults() {
        assertThat(UserClaim.forFeatureAction(AccessFeature.PROCESSED_LOANS, AccessAction.VIEW))
            .contains(UserClaim.PROCESSED_LOANS_VIEW);
        assertThat(UserClaim.forFeatureAction(AccessFeature.SACCO_CONFIGURATIONS, AccessAction.VIEW))
            .contains(UserClaim.SACCO_CONFIGURATIONS_VIEW);
        assertThat(UserClaim.defaultClaims(java.util.List.of(Position.CHAIRPERSON), false))
            .contains(UserClaim.PROCESSED_LOANS_VIEW, UserClaim.SACCO_CONFIGURATIONS_VIEW);
        assertThat(UserClaim.defaultClaims(java.util.List.of(Position.MANAGER), false))
            .doesNotContain(UserClaim.PROCESSED_LOANS_VIEW, UserClaim.SACCO_CONFIGURATIONS_VIEW);
    }
}
