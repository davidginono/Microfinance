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
        jdbc.execute("create table if not exists board_reviews(loan_application_id uuid,board_member_id uuid,review_stage text,decision text)");
        jdbc.execute("create table if not exists guarantor_requests(loan_application_id uuid,guarantor_member_id uuid,status text,created_at timestamptz)");
        jdbc.execute("create table if not exists voucher_workflow(sacco_id text,station_id text,type text,total numeric(18,2),created_at timestamptz,posting text,validation text,approval text)");
        for(var table:List.of("board_reviews","guarantor_requests","loan_applications","voucher_workflow"))jdbc.update("delete from "+table);
        actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.BOARD).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),Set.of(UserClaim.BOARD_QUEUE_VIEW),true);
    }
    UUID loan(String institution,String branch,UUID owner,String status,BigDecimal amount,int age){var id=UUID.randomUUID();jdbc.update("insert into loan_applications values(?,?,?,?,?,?,?,null)",id,institution,branch,status,owner,amount,now.minusDays(age));return id;}
    @Test void queueCountsRespectFirstClassBranchReviewerStageAndDisplayThresholds(){
        var own=loan("I1","b1",UUID.randomUUID(),"AWAITING_BOARD",new BigDecimal("500000"),4);
        var other=loan("I1","B1",UUID.randomUUID(),"AWAITING_BOARD",new BigDecimal("900000"),5);
        var foreign=loan("I1","B2",UUID.randomUUID(),"AWAITING_BOARD",new BigDecimal("800000"),6);
        for(var id:List.of(own,foreign))jdbc.update("insert into board_reviews values(?,?,?,?)",id,actor.getMemberId(),"BOARD","PENDING");
        jdbc.update("insert into board_reviews values(?,?,?,?)",other,UUID.randomUUID(),"BOARD","PENDING");
        var rows=repository.loans(actor,List.of("AWAITING_BOARD"),false,"BOARD",now.minusDays(3),new BigDecimal("400000"));
        assertThat(rows).hasSize(1);assertThat(rows.getFirst().count()).isEqualTo(1);assertThat(rows.getFirst().amount()).isEqualByComparingTo("500000");
        assertThat(repository.loans(actor,List.of("AWAITING_BOARD"),false,"CHAIRPERSON",now,BigDecimal.ZERO)).isEmpty();
    }
    @Test void ownedDraftsDoNotIncludeOtherClientsInstitutionsBranchesOrMissingBranches(){
        loan("I1","B1",actor.getMemberId(),"DRAFT",new BigDecimal("200000"),2);
        loan("I1","B1",UUID.randomUUID(),"DRAFT",new BigDecimal("200000"),2);
        loan("I2","B1",actor.getMemberId(),"DRAFT",new BigDecimal("200000"),2);
        loan("I1",null,actor.getMemberId(),"DRAFT",new BigDecimal("200000"),2);
        var rows=repository.loans(actor,List.of("DRAFT"),true,null,now,BigDecimal.ZERO);assertThat(rows.getFirst().count()).isEqualTo(1);
    }
    @Test void voucherCountsKeepDraftsApprovalsAndPostedRecordsDistinctAndTypeScoped(){
        for(var row:List.of(new String[]{"I1","B1","RECEIPT","NOT_POSTED","INCOMPLETE","REQUIRED"},new String[]{"I1","B1","RECEIPT","NOT_POSTED","VALIDATED","INITIATED"},new String[]{"I1","B1","JOURNAL","NOT_POSTED","INCOMPLETE","REQUIRED"},new String[]{"I1","B2","RECEIPT","NOT_POSTED","INCOMPLETE","REQUIRED"},new String[]{"I2","B1","RECEIPT","NOT_POSTED","INCOMPLETE","REQUIRED"},new String[]{"I1","B1","RECEIPT","POSTED","VALIDATED","NOT_REQUIRED"}))
            jdbc.update("insert into voucher_workflow values(?,?,?,?,?,?,?,?)",row[0],row[1],row[2],new BigDecimal("1000"),now.minusDays(4),row[3],row[4],row[5]);
        var rows=repository.vouchers(actor,List.of("RECEIPT"),now,BigDecimal.ZERO);assertThat(rows).hasSize(3);assertThat(rows).extracting(DashboardWorkRepository.Bucket::category).containsExactlyInAnyOrder("INCOMPLETE","REQUIRED","INITIATED");assertThat(rows).allSatisfy(r->assertThat(r.count()).isEqualTo(1));
        assertThat(repository.vouchers(actor,List.of("RECEIPT"),now.minusDays(5),BigDecimal.ZERO)).isEmpty();
    }
    @Test void guaranteesAreOwnedScopedAndExcludeSupersededLoans(){
        var loan=loan("I1","B1",UUID.randomUUID(),"AWAITING_GUARANTORS",new BigDecimal("500000"),3);
        jdbc.update("insert into guarantor_requests values(?,?,?,?)",loan,actor.getMemberId(),"PENDING",now.minusDays(2));
        jdbc.update("insert into guarantor_requests values(?,?,?,?)",loan,UUID.randomUUID(),"PENDING",now.minusDays(2));
        assertThat(repository.guarantees(actor,now).getFirst().count()).isEqualTo(1);
        var newer=loan("I1","B1",UUID.randomUUID(),"SUBMITTED",new BigDecimal("700000"),1);jdbc.update("update loan_applications set top_up_source_loan_id=? where id=?",loan,newer);
        assertThat(repository.guarantees(actor,now)).isEmpty();
    }
}
