package com.sacco.mvp.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_ADMIN_PERMISSIONS_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_admin_permissions_test")
class AdminChargePermissionsPostgresTest {
    @Test void backfillPreservesCustomGrantsAndAuditAndDoesNotReplaceDefaultBundles() throws Exception {
        var ds=new DriverManagerDataSource(System.getenv("MICROFINANCE_ADMIN_PERMISSIONS_TEST_URL"),"microfinance_test","");
        var setup=Flyway.configure().dataSource(ds).locations("filesystem:src/main/resources/db/migration").target("165").load();
        setup.migrate();var jdbc=new JdbcTemplate(ds);
        String institution="PERM-"+UUID.randomUUID();
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,'Permission fixture',true,now(),now())",institution);
        jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,access_status,created_at,updated_at) values(?,?,'B1',true,'ACTIVE',now(),now())",UUID.randomUUID(),institution);
        UUID matrix=person(jdbc,institution,"MINOR_ADMIN","ACTIVE"),roleOnly=person(jdbc,institution,"MANAGER","ACTIVE"),json=person(jdbc,institution,"ICT_MANAGER","ACTIVE"),defaults=person(jdbc,institution,"ICT_MANAGER","ACTIVE"),
            nonAdmin=person(jdbc,institution,"ACCOUNTANT","ACTIVE"),inactive=person(jdbc,institution,"ICT_MANAGER","INACTIVE"),already=person(jdbc,institution,"ICT_MANAGER","ACTIVE"),platform=person(jdbc,null,"ADMIN","ACTIVE");
        jdbc.update("insert into member_staff_roles(member_id,role_name) values(?,'ICT_MANAGER')",roleOnly);
        for(var id:List.of(matrix,roleOnly,nonAdmin,inactive,already,platform))grant(jdbc,id,"USER_ACCESS_VIEW");
        grant(jdbc,already,"LOAN_PRODUCTS_CONFIGURE");grant(jdbc,already,"ACCOUNTING_ACCOUNTS_UPDATE");
        jdbc.update("insert into user_settings(member_id,language,notification_prefs,created_at,updated_at) values(?,'sw','{\"claims\":[\"USER_ACCESS_VIEW\"],\"theme\":\"compact\"}',now(),now())",json);
        // Structured assignments take precedence; unrelated legacy preferences must stay untouched.
        jdbc.update("insert into user_settings(member_id,language,notification_prefs,created_at,updated_at) values(?,'en','{\"claims\":[],\"theme\":\"compact\"}',now(),now())",matrix);
        var people=jdbc.queryForList("select row_to_json(m)::text from members m where id in (?,?,?,?,?,?,?,?) order by id",String.class,matrix,roleOnly,json,defaults,nonAdmin,inactive,already,platform);
        int audits=jdbc.queryForObject("select count(*) from audit_log",Integer.class);
        var migration=Flyway.configure().dataSource(ds).locations("filesystem:src/main/resources/db/migration").load();
        assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
        for(var id:List.of(matrix,roleOnly,platform))assertThat(claims(jdbc,id)).containsExactlyInAnyOrder("USER_ACCESS_VIEW","LOAN_PRODUCTS_CONFIGURE","ACCOUNTING_ACCOUNTS_UPDATE");
        assertThat(claims(jdbc,already)).containsExactlyInAnyOrder("USER_ACCESS_VIEW","LOAN_PRODUCTS_CONFIGURE","ACCOUNTING_ACCOUNTS_UPDATE");
        for(var id:List.of(nonAdmin,inactive))assertThat(claims(jdbc,id)).containsExactly("USER_ACCESS_VIEW");
        for(var id:List.of(json,defaults))assertThat(claims(jdbc,id)).isEmpty();
        assertThat(jdbc.queryForObject("select notification_prefs->>'theme' from user_settings where member_id=?",String.class,json)).isEqualTo("compact");
        assertThat(jdbc.queryForList("select jsonb_array_elements_text(notification_prefs->'claims') from user_settings where member_id=?",String.class,json))
            .containsExactlyInAnyOrder("USER_ACCESS_VIEW","LOAN_PRODUCTS_CONFIGURE","ACCOUNTING_ACCOUNTS_UPDATE");
        assertThat(jdbc.queryForObject("select notification_prefs->'claims'='[]'::jsonb from user_settings where member_id=?",Boolean.class,matrix)).isTrue();
        int upgradedAudits=jdbc.queryForObject("select count(*) from audit_log",Integer.class);
        assertThat(upgradedAudits).isGreaterThanOrEqualTo(audits+4);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action='SYSTEM_ADMIN_CHARGE_DEFAULTS' and actor_member_id is null and reference_value='V166' and entity_id in (?,?,?,?)",Integer.class,matrix,roleOnly,json,platform)).isEqualTo(4);
        assertThat(jdbc.queryForList("select row_to_json(m)::text from members m where id in (?,?,?,?,?,?,?,?) order by id",String.class,matrix,roleOnly,json,defaults,nonAdmin,inactive,already,platform)).isEqualTo(people);
        assertThat(migration.migrate().migrationsExecuted).isZero();
        // Replaying the SQL itself also leaves grants and audit history unchanged.
        try(var connection=ds.getConnection()) {
            connection.setAutoCommit(false);
            connection.createStatement().execute(Files.readString(Path.of("src/main/resources/db/migration/V166__admin_charge_account_permissions.sql")));
            connection.commit();
        }
        assertThat(jdbc.queryForObject("select count(*) from audit_log",Integer.class)).isEqualTo(upgradedAudits);
        // A deliberate later revocation is not silently re-granted by defaults on structured reads.
        jdbc.update("delete from member_access_claims where member_id=? and claim_name='ACCOUNTING_ACCOUNTS_UPDATE'",matrix);
        assertThat(claims(jdbc,matrix)).containsExactlyInAnyOrder("USER_ACCESS_VIEW","LOAN_PRODUCTS_CONFIGURE");
    }
    private UUID person(JdbcTemplate jdbc,String institution,String role,String status) {
        var id=UUID.randomUUID();
        jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,is_member,password_hash,staff_access_status,client_portal_enabled,created_at) values(?,?,?,?, 'Permission fixture',?,?,false,'!FIXTURE','ACTIVE',false,now())",id,institution,institution==null?null:"B1",id.toString(),status,role);
        return id;
    }
    private void grant(JdbcTemplate jdbc,UUID id,String claim){jdbc.update("insert into member_access_claims(member_id,claim_name) values(?,?)",id,claim);}
    private List<String> claims(JdbcTemplate jdbc,UUID id){return jdbc.queryForList("select claim_name from member_access_claims where member_id=? order by claim_name",String.class,id);}
}
