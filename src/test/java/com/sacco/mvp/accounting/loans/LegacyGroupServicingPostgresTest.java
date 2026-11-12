package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.service.*;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Preserve V156 contracts exactly; every servicing test rolls its monetary work back. */
@EnabledIfEnvironmentVariable(named="MICROFINANCE_LEGACY_GROUP_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_legacy_servicing_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LegacyGroupServicingPostgresTest {
    final LoanRecordingPostgresTest f=new LoanRecordingPostgresTest();
    static final UUID FUNDED=UUID.fromString("bf6e9fb7-7279-465d-9b98-f667099cfcd1"),UNFUNDED=UUID.fromString("054ee60d-0956-4da7-9584-160be2c096e8");
    @BeforeAll void start()throws Exception{
        var ds=new DriverManagerDataSource(System.getenv("MICROFINANCE_LEGACY_GROUP_TEST_URL"),"microfinance_test","");var jdbc=new JdbcTemplate(ds);
        if(!Boolean.TRUE.equals(jdbc.queryForObject("select to_regclass('public.loan_applications') is not null",Boolean.class))){
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("156").load().migrate();
            try(var stream=getClass().getResourceAsStream("/fixtures/legacy-shared-group-v156.sql")){
                String sql=new String(Objects.requireNonNull(stream).readAllBytes(),StandardCharsets.UTF_8);
                new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds)).executeWithoutResult(tx->{jdbc.execute("set local session_replication_role=replica");jdbc.execute(sql);});
            }
        }
        f.context=new AnnotationConfigApplicationContext(Config.class);f.jdbc=f.context.getBean(JdbcTemplate.class);f.service=f.context.getBean(com.sacco.mvp.accounting.loans.service.LoanRecordingService.class);f.ledger=f.context.getBean(LoanRepaymentLedgerService.class);
    }
    @AfterAll void stop(){f.stop();}
    void institution(UUID loan){
        f.institution=f.jdbc.queryForObject("select sacco_id from loan_applications where id=?",String.class,loan);f.actor=f.operator("B1");
        var registry=f.context.getBean(SaccoRegistryService.class);when(registry.findActiveSacco(f.institution)).thenReturn(Optional.of(RegisteredSacco.builder().saccoId(f.institution).active(true).build()));when(registry.findStation(eq(f.institution),anyString())).thenAnswer(i->Optional.of(SaccoStation.builder().saccoId(f.institution).stationId(i.getArgument(1)).active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));when(f.context.getBean(UserClaimService.class).effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));
        var accounts=f.jdbc.queryForMap("select money_account_id,principal_account_id from accountant_loan_post where loan_id=? and kind='DISBURSEMENT'",FUNDED);f.cash=(UUID)accounts.get("money_account_id");f.principal=(UUID)accounts.get("principal_account_id");
    }
    void rollback(Runnable task){new TransactionTemplate(f.context.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx->{task.run();f.jdbc.execute("set constraints all immediate");tx.setRollbackOnly();});}
    PostForm payment(String amount){var p=f.post(FUNDED,amount,"P-LEGACY-"+UUID.randomUUID());p.setSharedPayer("Historical group treasurer");return p;}
    @Test void fundedContractKeepsPaymentsExactAttributionSettlementCorrectionsAndOriginalPapers(){rollback(()->{
        institution(FUNDED);var shared=f.context.getBean(GroupSharedLoanService.class);assertThat(f.service.detail(f.actor,FUNDED).loan().outstandingPrincipal()).isEqualByComparingTo("1000.01");
        var paid=f.service.repay(f.actor,payment("120.00"));assertThat(f.service.detail(f.actor,FUNDED).loan().outstandingPrincipal()).isEqualByComparingTo("880.01");assertThat(shared.balances(f.actor,FUNDED).stream().map(x->x.remainingPrincipal()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("880.01");
        f.service.reverse(f.operator("B1"),paid.id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Independent historical correction");
        var settled=f.service.repay(f.actor,payment("1000.01"));assertThat(settled.interest()).isZero();assertThat(f.service.detail(f.actor,FUNDED).loan().outstandingPrincipal()).isZero();f.service.reverse(f.operator("B1"),settled.id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Independent settlement correction");assertThat(f.service.detail(f.actor,FUNDED).loan().outstandingPrincipal()).isEqualByComparingTo("1000.01");
        var groups=f.context.getBean(SimpleGroupLoanService.class);assertThat(groups.history(f.actor,FUNDED,0).rows()).isNotEmpty();assertThat(groups.simple(f.actor,FUNDED)).isFalse();assertThat(f.jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where sacco_id=?",BigDecimal.class,f.institution)).isZero();
    });}
    @Test void unfundedLegacyContractCannotFundAndCanOnlyBeWithdrawnExplicitly(){rollback(()->{
        institution(UNFUNDED);assertThat(f.jdbc.queryForObject("select status from loan_applications where id=?",String.class,UNFUNDED)).isEqualTo("READY_FOR_DISBURSEMENT");assertThat(f.service.retiredGroup(f.actor,UNFUNDED)).isTrue();
        var p=new PostForm();p.setLoanId(UNFUNDED);p.setAmount(new BigDecimal("1000.01"));p.setEffectiveDate(LoanRecordingPostgresTest.TODAY);p.setMoneyAccountId(f.cash);p.setPrincipalAccountId(f.principal);p.setReference("NO-FUNDING");assertThatThrownBy(()->f.service.disburse(f.actor,p)).isInstanceOf(IllegalArgumentException.class).hasMessage("recording.error.simpleGroupRetired");
        var current=new com.sacco.mvp.accounting.loans.repository.LoanRecordingRepository(f.jdbc).groupLoan(f.actor,UNFUNDED,false);f.service.withdrawGroup(f.actor,UNFUNDED,UUID.randomUUID(),current.status(),current.termsHash(),"Replace through the simple form");assertThat(f.jdbc.queryForObject("select status from loan_applications where id=?",String.class,UNFUNDED)).isEqualTo("WITHDRAWN");assertThat(f.jdbc.queryForObject("select count(*) from loan_ledgers where loan_application_id=?",Integer.class,UNFUNDED)).isZero();
    });}
    @Configuration(proxyBeanMethods=false) @Import(LoanRecordingPostgresTest.Config.class)
    static class Config {
        @Bean(destroyMethod="close") @Primary DataSource legacyDataSource(){var ds=new HikariDataSource();ds.setJdbcUrl(System.getenv("MICROFINANCE_LEGACY_GROUP_TEST_URL"));ds.setUsername("microfinance_test");ds.setPassword("");ds.addDataSourceProperty("sslmode","disable");ds.setMaximumPoolSize(4);return ds;}
    }
}
