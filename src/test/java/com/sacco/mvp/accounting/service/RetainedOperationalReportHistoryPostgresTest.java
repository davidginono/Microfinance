package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.repository.RetainedFinancialHistoryRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_PERMISSION_TEST_URL", matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_permissions_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetainedOperationalReportHistoryPostgresTest {
    JdbcTemplate jdbc;
    RetainedFinancialHistoryRepository history;
    final String institution="PERMISSIONS-"+UUID.randomUUID();
    final UUID owner=UUID.randomUUID(), maker=UUID.randomUUID(), checker=UUID.randomUUID(),
        requester=UUID.randomUUID(), approver=UUID.randomUUID(), template=UUID.randomUUID(), version=UUID.randomUUID(), run=UUID.randomUUID();

    @BeforeAll void retainedEvidence() {
        var source=new DriverManagerDataSource(System.getenv("MICROFINANCE_PERMISSION_TEST_URL"),"microfinance_test","");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(source);
        history=new RetainedFinancialHistoryRepository(new NamedParameterJdbcTemplate(jdbc));
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,'Synthetic retained report',true,now(),now())",institution);
        for(var member:java.util.List.of(owner,maker,checker,requester,approver))
            jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,'B1',?,'Synthetic report officer','ACTIVE','ACCOUNTANT',now(),false,'test-only')",member,institution,member.toString());
        jdbc.update("insert into operational_report_templates(id,sacco_id,created_by,created_at) values(?,?,?,now())",template,institution,owner);
        jdbc.update("insert into operational_report_template_versions(id,template_id,sacco_id,version,dataset_version,definition,title,visibility,state,made_by,made_at) values(?,?,?,1,1,'{}','Retained definition','PRIVATE','DRAFT',?,now())",version,template,institution,maker);
        jdbc.update("update operational_report_template_versions set state='PUBLISHED',checked_by=?,checked_at=now() where id=?",checker,version);
        jdbc.update("insert into report_runs(id,sacco_id,station_id,requested_by,request_key,request_hash,template_version_id,template_version,dataset_version,metric_version,dataset,definition,from_date,through_date,recorded_cutoff,formats,status,requested_at) values(?,?,'B1',?,?,?, ?,1,1,'OPERATIONAL_V1','COLLECTIONS','{}',date '2026-10-01',date '2026-10-02',now(),'PDF','RUNNING',now())",run,institution,requester,UUID.randomUUID(),"0".repeat(64),version);
        jdbc.update("insert into report_artifacts(id,run_id,format,payload,checksum,generated_at) values(?,?,'PDF',?, ?,now())",UUID.randomUUID(),run,new byte[]{1,2,3},"0".repeat(64));
        jdbc.update("update report_runs set status='READY',generated_at=now(),row_count=0,result_checksum=?,snapshot_id='retained' where id=?","0".repeat(64),run);
        jdbc.update("update report_runs set status='APPROVED',approved_by=?,approved_at=now(),approval_evidence='Retained evidence' where id=?",approver,run);
    }

    @Test void historyChecksRetainAllFiveHistoricalRolesAndDoNotRewriteEvidence() {
        String before=jdbc.queryForObject("select row_to_json(r)::text from report_runs r where id=?",String.class,run);
        assertThat(history.hasInstitutionHistory(institution)).isTrue();
        for(var member:java.util.List.of(owner,maker,checker,requester,approver))
            assertThat(history.hasMemberHistory(member)).as(member.toString()).isTrue();
        assertThat(history.hasInstitutionHistory("NO-SUCH-INSTITUTION")).isFalse();
        assertThat(history.hasMemberHistory(UUID.randomUUID())).isFalse();
        assertThat(history.hasInstitutionHistory(null)).isFalse();
        assertThat(history.hasMemberHistory(null)).isFalse();
        assertThat(jdbc.queryForObject("select row_to_json(r)::text from report_runs r where id=?",String.class,run)).isEqualTo(before);
    }

    @Test void databaseStillRejectsChangesToFrozenDefinitionsRunsAndArtifacts() {
        assertThatThrownBy(()->jdbc.update("delete from operational_report_template_versions where id=?",version)).hasMessageContaining("cannot be deleted");
        assertThatThrownBy(()->jdbc.update("delete from report_runs where id=?",run)).hasMessageContaining("retention lifecycle");
        assertThatThrownBy(()->jdbc.update("update report_runs set approval_evidence='Changed' where id=?",run)).hasMessageContaining("Invalid report run transition");
        assertThatThrownBy(()->jdbc.update("delete from report_artifacts where run_id=?",run)).hasMessageContaining("immutable");
        assertThat(jdbc.queryForObject("select count(*) from report_artifacts where run_id=?",Integer.class,run)).isOne();
    }
}
