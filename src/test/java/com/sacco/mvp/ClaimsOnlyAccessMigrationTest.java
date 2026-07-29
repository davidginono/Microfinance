package com.sacco.mvp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimsOnlyAccessMigrationTest {

    @Test
    void releasedInitialMigrationChecksumRemainsStable() throws IOException {
        Path migration = Path.of(
            "src/main/resources/db/migration/V17__member_access_claim_matrix.sql"
        );

        assertThat(flywayChecksum(migration)).isEqualTo(-1440696127);
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

    private int flywayChecksum(Path migration) throws IOException {
        CRC32 crc32 = new CRC32();
        for (String line : Files.readAllLines(migration, StandardCharsets.UTF_8)) {
            crc32.update(line.getBytes(StandardCharsets.UTF_8));
        }
        return (int) crc32.getValue();
    }
}
