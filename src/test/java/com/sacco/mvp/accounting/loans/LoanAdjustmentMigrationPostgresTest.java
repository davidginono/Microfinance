package com.sacco.mvp.accounting.loans;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_ADJUSTMENT_MIGRATION_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_adjustment_migration_test")
class LoanAdjustmentMigrationPostgresTest {
    @Test void originalV69UpgradesWithoutRepairAndRetainsLaterChargeAndTopUpGuards() {
        try(var ds=new HikariDataSource()) {
            ds.setJdbcUrl(System.getenv("MICROFINANCE_ADJUSTMENT_MIGRATION_TEST_URL"));ds.setUsername("microfinance_test");ds.setPassword("");ds.setMaximumPoolSize(2);ds.addDataSourceProperty("sslmode","disable");
            Flyway.configure().dataSource(ds).target("69").load().migrate();var jdbc=new JdbcTemplate(ds);
            assertThat(jdbc.queryForObject("select checksum from flyway_schema_history where version='69'",Integer.class)).isEqualTo(-1591283630);
            var flyway=Flyway.configure().dataSource(ds).load();flyway.migrate();flyway.validate();
            assertThat(jdbc.queryForObject("select checksum from flyway_schema_history where version='69'",Integer.class)).isEqualTo(-1591283630);
            assertThat(flyway.info().current().getVersion().toString()).isEqualTo("75");
            String validator=jdbc.queryForObject("select pg_get_functiondef('loan_adjustment_validate_journal(gl_journal)'::regprocedure)",String.class);
            assertThat(validator).contains("x.event_sequence<d.event_sequence","a.status<>'WRITTEN_OFF'","a.status<>'DEFAULTED'","a.status<>'DISBURSEMENT_REVERSED'","original.maker_id=d.actor_id","cr.kind='CONTROL'","loan_charge_term","loan_charge_balance");
            assertThat(jdbc.queryForObject("select pg_get_functiondef('core_post_commit_guard()'::regprocedure)",String.class)).contains("LOAN_TOPUP","LOAN_ADJUSTMENT","LOAN_CHARGE_ASSESS");
            assertThat(jdbc.queryForObject("select pg_get_functiondef('loan_adjustment_payment_guard()'::regprocedure)",String.class)).contains("Repayment date cannot precede a posted loan adjustment");
        }
    }
}
