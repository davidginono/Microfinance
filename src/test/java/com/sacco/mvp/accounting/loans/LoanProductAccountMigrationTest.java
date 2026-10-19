package com.sacco.mvp.accounting.loans;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_PRODUCT_ACCOUNT_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_product_migration_test")
class LoanProductAccountMigrationTest {
    @Test void originalPostingsRequireLinksButHistoricalCorrectionsKeepTheirOriginalAccounts() throws Exception {
        String url=System.getenv("MICROFINANCE_PRODUCT_ACCOUNT_TEST_URL");
        var flyway=Flyway.configure().dataSource(url,"microfinance_test","").locations("filesystem:src/main/resources/db/migration").load();
        flyway.migrate();flyway.validate();
        try(var connection=DriverManager.getConnection(url,"microfinance_test","");var sql=connection.createStatement()){
            connection.setAutoCommit(false);
            // Exercise the real trigger function without fabricating or modifying financial sources.
            sql.execute("create temporary table account_guard_probe(kind text,loan_id uuid,principal_account_id uuid,interest_account_id uuid) on commit drop");
            sql.execute("create trigger account_guard_probe before insert on account_guard_probe for each row execute function enforce_loan_product_posting_accounts()");
            try(var insert=connection.prepareStatement("insert into account_guard_probe values(?,?,?,?)")){
                insert.setObject(2,UUID.randomUUID());insert.setObject(3,UUID.randomUUID());insert.setObject(4,UUID.randomUUID());
                for(String correction:new String[]{"REVERSAL","FUNDING_UNDO"}){insert.setString(1,correction);assertThat(insert.executeUpdate()).isEqualTo(1);}
                for(String original:new String[]{"PAYMENT","DISBURSEMENT"}){
                    var savepoint=connection.setSavepoint();insert.setString(1,original);
                    assertThatThrownBy(insert::executeUpdate).isInstanceOf(SQLException.class).hasMessageContaining("Link loan product accounts before posting");connection.rollback(savepoint);
                }
            }
            connection.rollback();
        }
    }
}
