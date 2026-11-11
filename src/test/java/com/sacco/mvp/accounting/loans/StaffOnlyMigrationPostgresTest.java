package com.sacco.mvp.accounting.loans;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataAccessException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_STAFF_MIGRATION_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_staff_migration_test")
class StaffOnlyMigrationPostgresTest {
    @Test void cutoverRetiresBorrowerCredentialsAndKeepsStaffClientsAndHistoricalEvidence(){
        var ds=new DriverManagerDataSource(System.getenv("MICROFINANCE_STAFF_MIGRATION_TEST_URL"),"microfinance_test","");
        var flyway=Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("153").cleanDisabled(false).load();
        flyway.clean();var jdbc=new JdbcTemplate(ds);jdbc.execute("drop schema if exists accounting_archive cascade");flyway.migrate();
        String institution="MIG-"+UUID.randomUUID();
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,'Migration fixture',true,now(),now())",institution);
        jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,access_status,created_at,updated_at) values(?,?,'B1',true,'ACTIVE',now(),now())",UUID.randomUUID(),institution);
        UUID borrower=person(jdbc,institution,"MEMBER",true,"ACTIVE","NONE"),unfinished=person(jdbc,institution,"MEMBER",true,"INVITED","NONE"),staff=person(jdbc,institution,"BOARD",false,"ACTIVE","ACTIVE"),combined=person(jdbc,institution,"MEMBER",true,"ACTIVE","PENDING_ACKNOWLEDGEMENT");
        jdbc.update("insert into member_staff_roles(member_id,role_name) values(?,'ACCOUNTANT')",combined);
        jdbc.update("insert into member_access_claims(member_id,claim_name) values(?,'LOAN_RECORDING_VIEW')",staff);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        assertThat(jdbc.queryForObject("select count(*) from members where sacco_id=?",Integer.class,institution)).isEqualTo(4);
        for(UUID id:new UUID[]{borrower,unfinished}){
            assertThat(jdbc.queryForObject("select password_hash from members where id=?",String.class,id)).isEqualTo("!NO_PORTAL_ACCESS");
            assertThat(jdbc.queryForObject("select username from members where id=?",String.class,id)).isNull();
            assertThat(jdbc.queryForObject("select status from members where id=?",String.class,id)).isEqualTo("ACTIVE");
        }
        assertThat(jdbc.queryForObject("select previous_status from staff_only_client_cutover where member_id=?",String.class,unfinished)).isEqualTo("INVITED");
        for(UUID id:new UUID[]{staff,combined})assertThat(jdbc.queryForObject("select password_hash from members where id=?",String.class,id)).isEqualTo("staff-password-evidence");
        assertThat(jdbc.queryForList("select role_name from member_staff_roles where member_id=?",String.class,staff)).contains("CREDIT_OFFICER");
        assertThat(jdbc.queryForObject("select staff_access_status from members where id=?",String.class,combined)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForList("select claim_name from member_access_claims where member_id=?",String.class,staff)).containsExactly("LOAN_RECORDING_VIEW");
        assertThat(jdbc.queryForObject("select count(*) from member_access_claims where member_id=?",Integer.class,combined)).isZero();
        assertThatThrownBy(()->jdbc.update("update members set client_portal_enabled=true where id=?",borrower)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("delete from staff_only_client_cutover where member_id=?",borrower)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.execute("truncate staff_only_client_cutover")).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("select count(*) from loan_group_request",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from lending_group_signed_paper",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from loan_ledgers",Integer.class)).isZero();
    }
    private UUID person(JdbcTemplate jdbc,String institution,String role,boolean client,String status,String access){
        UUID id=UUID.randomUUID();
        jdbc.update("insert into members(id,sacco_id,station_id,member_no,username,full_name,status,position,is_member,password_hash,staff_access_status,client_portal_enabled,created_at) values(?,?,'B1',?,?,'Migration person',?,?,?,'staff-password-evidence',?,true,now())",id,institution,id.toString(),"qa"+id.toString().replace("-","").substring(0,28),status,role,client,access);
        return id;
    }
}
