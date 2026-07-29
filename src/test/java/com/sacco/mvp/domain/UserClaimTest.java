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
}
