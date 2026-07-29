package com.sacco.mvp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimsOnlyAccessMigrationTest {

    @Test
    void initialMigrationIgnoresOrphanedLegacyUserSettings() throws IOException {
        String migration = Files.readString(Path.of(
            "src/main/resources/db/migration/V17__member_access_claim_matrix.sql"
        ));

        assertThat(migration).contains(
            "FROM public.user_settings us",
            "JOIN public.members m ON m.id = us.member_id"
        );
    }

    @Test
    void repairMigrationCoversEveryStaffWorkspaceAndAdministratorRecoveryClaim() throws IOException {
        String migration = Files.readString(Path.of(
            "src/main/resources/db/migration/V21__repair_required_workspace_access_claims.sql"
        ));

        assertThat(migration).contains(
            "('LOAN_OFFICER', 'LOAN_OFFICER_QUEUE_VIEW')",
            "('MANAGER', 'MANAGER_QUEUE_VIEW')",
            "('ACCOUNTANT', 'ACCOUNTANT_QUEUE_VIEW')",
            "('DISBURSEMENT_OFFICER', 'DISBURSEMENT_QUEUE_VIEW')",
            "('CHAIRPERSON', 'CHAIRPERSON_QUEUE_VIEW')",
            "('BOARD', 'BOARD_QUEUE_VIEW')",
            "('CREDIT_COMMITTEE', 'CREDIT_COMMITTEE_QUEUE_VIEW')",
            "('MINOR_ADMIN', 'ADMIN_DASHBOARD_VIEW')",
            "('MINOR_ADMIN', 'ACCESS_MATRIX_VIEW')",
            "('MINOR_ADMIN', 'ACCESS_MATRIX_UPDATE')",
            "('MINOR_ADMIN', 'USER_ACCESS_VIEW')",
            "('MINOR_ADMIN', 'USER_ACCESS_UPDATE')",
            "('ADMIN', 'ADMIN_DASHBOARD_VIEW')",
            "('ADMIN', 'ACCESS_MATRIX_VIEW')",
            "('ADMIN', 'ACCESS_MATRIX_UPDATE')",
            "('ADMIN', 'USER_ACCESS_VIEW')",
            "('ADMIN', 'USER_ACCESS_UPDATE')",
            "ON CONFLICT DO NOTHING"
        );
    }
}
