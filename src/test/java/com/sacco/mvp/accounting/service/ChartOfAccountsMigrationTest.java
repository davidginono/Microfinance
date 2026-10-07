package com.sacco.mvp.accounting.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_COA_MIGRATION_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_coa_migration_test")
class ChartOfAccountsMigrationTest {
    @Test void upgradeGrantsOnlyScopedAccountantsAndPreservesOtherClaims() throws Exception {
        var ds=new DriverManagerDataSource(System.getenv("MICROFINANCE_COA_MIGRATION_DATABASE_URL"),"microfinance_test","");
        try(var lock=ds.getConnection();var statement=lock.createStatement()) {
        statement.execute("select pg_advisory_lock(510071)");
        var current=Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().info().current();
        org.junit.jupiter.api.Assumptions.assumeTrue(current==null || current.getVersion().compareTo(org.flywaydb.core.api.MigrationVersion.fromVersion("50"))<=0,
            "Upgrade test requires a disposable database before V51");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("50").load().migrate();
        var jdbc=new JdbcTemplate(ds);
        String institution="COA-MIGRATION-"+UUID.randomUUID();
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,'Synthetic upgrade',true,now(),now())",institution);
        UUID active=staff(jdbc,institution,"ACCOUNTANT","ACTIVE"),legacy=staff(jdbc,institution,"ACCOUNTANT","NONE"),
            manager=staff(jdbc,institution,"MANAGER","ACTIVE"),pending=staff(jdbc,institution,"ACCOUNTANT","PENDING_ACKNOWLEDGEMENT"),
            platform=staff(jdbc,institution,"ADMIN","ACTIVE");
        jdbc.update("insert into member_staff_roles(member_id,role_name) values(?,'ACCOUNTANT')",platform);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        for(UUID id:new UUID[]{active,legacy}) {
            assertThat(jdbc.queryForList("select claim_name from member_access_claims where member_id=?",String.class,id))
                .containsExactlyInAnyOrder("ACCOUNTANT_QUEUE_VIEW","ACCOUNTING_ACCOUNTS_VIEW","ACCOUNTING_ACCOUNTS_CREATE","ACCOUNTING_ACCOUNTS_UPDATE");
        }
        for(UUID id:new UUID[]{manager,pending,platform})assertThat(jdbc.queryForObject(
            "select count(*) from member_access_claims where member_id=? and claim_name like 'ACCOUNTING_ACCOUNTS_%'",Integer.class,id)).isZero();
        }
    }
    private UUID staff(JdbcTemplate jdbc,String institution,String position,String access) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,staff_access_status,created_at,is_member,password_hash) values(?,?,'B1',?,'Synthetic staff','ACTIVE',?,?,now(),false,'test-only')",id,institution,id.toString(),position,access);
        jdbc.update("insert into member_access_claims(member_id,claim_name) values(?,'ACCOUNTANT_QUEUE_VIEW')",id);return id;
    }
}
