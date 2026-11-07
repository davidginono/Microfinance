package com.sacco.mvp.accounting.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import static org.assertj.core.api.Assertions.assertThat;

class AppliedMigrationChecksumTest {
    @Test void v150RetainsTheChecksumAlreadyRecordedInSchemaHistory() throws Exception {
        String sql=new ClassPathResource("db/migration/V150__generated_loan_account_coa_numbers.sql").getContentAsString(StandardCharsets.UTF_8);
        var crc=new CRC32();
        sql.lines().forEach(line->crc.update(line.getBytes(StandardCharsets.UTF_8)));
        assertThat((int)crc.getValue()).as("V150 is already applied; subsequent changes need a new migration").isEqualTo(1456148062);
    }
}
