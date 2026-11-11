package com.sacco.mvp.dashboard;

import com.sacco.mvp.dashboard.repository.DashboardWorkRepository;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Uses only a disposable, explicitly named database; never runs against operational data. */
@EnabledIfEnvironmentVariable(named="MICROFINANCE_DASHBOARD_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_dashboard_test")
class DashboardWorkPostgresTest {
    JdbcTemplate jdbc; DashboardWorkRepository repository; AppUserPrincipal actor;
    final OffsetDateTime now=OffsetDateTime.parse("2026-10-22T09:42:00+03:00");
    @BeforeEach void setup(){
        jdbc=new JdbcTemplate(new DriverManagerDataSource(System.getenv("MICROFINANCE_DASHBOARD_TEST_URL"),"dashboard_test",""));repository=new DashboardWorkRepository(jdbc);
        jdbc.execute("create table if not exists loan_applications(id uuid primary key,sacco_id text,station_id text,status text,applicant_member_id uuid,amount numeric(18,2),created_at timestamptz,top_up_source_loan_id uuid)");
        jdbc.execute("create table if not exists voucher_workflow(sacco_id text,station_id text,type text,total numeric(18,2),created_at timestamptz,posting text,validation text,approval text)");
        for(var table:List.of("loan_applications","voucher_workflow"))jdbc.update("delete from "+table);
        actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.BOARD).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),Set.of(UserClaim.BOARD_QUEUE_VIEW));
    }
    UUID loan(String institution,String branch,UUID owner,String status,BigDecimal amount,int age){var id=UUID.randomUUID();jdbc.update("insert into loan_applications values(?,?,?,?,?,?,?,null)",id,institution,branch,status,owner,amount,now.minusDays(age));return id;}
    @Test void queueCountsRespectFirstClassBranchReviewerStageAndDisplayThresholds(){
        loan("I1","b1",UUID.randomUUID(),"SUBMITTED",new BigDecimal("500000"),4);
        loan("I1","B1",UUID.randomUUID(),"SUBMITTED",new BigDecimal("900000"),5);
        loan("I1","B2",UUID.randomUUID(),"SUBMITTED",new BigDecimal("800000"),6);
        var rows=repository.loans(actor,List.of("SUBMITTED"),now.minusDays(3),new BigDecimal("400000"));
        assertThat(rows).hasSize(1);assertThat(rows.getFirst().count()).isEqualTo(2);assertThat(rows.getFirst().amount()).isEqualByComparingTo("1400000");
        assertThat(repository.loans(actor,List.of("SUBMITTED"),now.minusDays(8),BigDecimal.ZERO)).isEmpty();

    }
    @Test void ownedDraftsDoNotIncludeOtherClientsInstitutionsBranchesOrMissingBranches(){
        loan("I1","B1",actor.getMemberId(),"DRAFT",new BigDecimal("200000"),2);
        loan("I1","B1",UUID.randomUUID(),"DRAFT",new BigDecimal("200000"),2);
        loan("I2","B1",actor.getMemberId(),"DRAFT",new BigDecimal("200000"),2);
        loan("I1",null,actor.getMemberId(),"DRAFT",new BigDecimal("200000"),2);
        var rows=repository.loans(actor,List.of("DRAFT"),now,BigDecimal.ZERO);assertThat(rows.getFirst().count()).isEqualTo(2);
    }
    @Test void voucherCountsKeepDraftsApprovalsAndPostedRecordsDistinctAndTypeScoped(){
        for(var row:List.of(new String[]{"I1","B1","RECEIPT","NOT_POSTED","INCOMPLETE","REQUIRED"},new String[]{"I1","B1","RECEIPT","NOT_POSTED","VALIDATED","INITIATED"},new String[]{"I1","B1","JOURNAL","NOT_POSTED","INCOMPLETE","REQUIRED"},new String[]{"I1","B2","RECEIPT","NOT_POSTED","INCOMPLETE","REQUIRED"},new String[]{"I2","B1","RECEIPT","NOT_POSTED","INCOMPLETE","REQUIRED"},new String[]{"I1","B1","RECEIPT","POSTED","VALIDATED","NOT_REQUIRED"}))
            jdbc.update("insert into voucher_workflow values(?,?,?,?,?,?,?,?)",row[0],row[1],row[2],new BigDecimal("1000"),now.minusDays(4),row[3],row[4],row[5]);
        var rows=repository.vouchers(actor,List.of("RECEIPT"),now,BigDecimal.ZERO);assertThat(rows).hasSize(3);assertThat(rows).extracting(DashboardWorkRepository.Bucket::category).containsExactlyInAnyOrder("INCOMPLETE","REQUIRED","INITIATED");assertThat(rows).allSatisfy(r->assertThat(r.count()).isEqualTo(1));
        assertThat(repository.vouchers(actor,List.of("RECEIPT"),now.minusDays(5),BigDecimal.ZERO)).isEmpty();
    }

}
