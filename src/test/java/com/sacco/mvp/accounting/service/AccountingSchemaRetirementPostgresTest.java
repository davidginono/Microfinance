package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.loans.repository.LoanPostingAuthorizationRepository;
import com.sacco.mvp.accounting.repository.RetainedFinancialHistoryRepository;
import com.sacco.mvp.accounting.repository.VoucherRepository;
import com.sacco.mvp.accounting.reports.LoanPortfolioReportRepository;
import com.sacco.mvp.accounting.reports.LoanPortfolioReportService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RESET_TEST_URL", matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_accounting_reset_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingSchemaRetirementPostgresTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private final UUID staff = UUID.randomUUID(), supplier = UUID.randomUUID(), template = UUID.randomUUID(), legacyLoan = UUID.randomUUID();
    private final String institution = "RESET-" + UUID.randomUUID();
    private String supplierBefore, templateBefore;
    private UUID closedPeriod;

    @BeforeAll void upgradeWithRetainedEvidence() {
        var source = new DriverManagerDataSource(System.getenv("MICROFINANCE_RESET_TEST_URL"), "microfinance_test", "");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("62").load().migrate();
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,?,true,now(),now())", institution, "Synthetic reset test");
        jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,'B1',?,'Synthetic officer','ACTIVE','ACCOUNTANT',now(),false,'test-only')", staff,institution,staff.toString());
        jdbc.update("insert into accounting_supplier(id,sacco_id,station_id,name,evidence_reference,maker_id,created_at) values(?,?,'B1','Retained supplier','Synthetic evidence',?,now())", supplier,institution,staff);
        jdbc.update("insert into financial_statement_templates(id,sacco_id,made_by,made_at) values(?,?,?,now())", template,institution,staff);
        jdbc.update("insert into loan_applications(id,amount,applicant_member_id,created_at,updated_at,form_data,loan_type,policy_snapshot,repayment_schedule_json,required_guarantors,sacco_id,station_id,status,tenor_months,version,loan_id,application_number) values(?,100,?,now(),now(),'{}','DEVELOPMENT_LOAN','{}','{}'::jsonb,0,?,'B1','DRAFT',1,0,?,1)",legacyLoan,staff,institution,"RESET-"+legacyLoan.toString().substring(0,8));
        jdbc.update("insert into accounting_business_document(id,sacco_id,station_id,maker_id,request_key,kind,effective_date,amount,loan_id,description,evidence_reference,command_json,payload_hash,state,created_at) values(?,?,'B1',?,?,'LOAN_DISBURSEMENT',date '2026-01-01',100,?,'Retained source','Synthetic evidence','{}',?,'DRAFT',now())",UUID.randomUUID(),institution,staff,UUID.randomUUID(),legacyLoan,"0".repeat(64));
        supplierBefore = jdbc.queryForObject("select row_to_json(s)::text from accounting_supplier s where id=?",String.class,supplier);
        templateBefore = jdbc.queryForObject("select row_to_json(s)::text from financial_statement_templates s where id=?",String.class,template);
        closedPeriod = UUID.randomUUID();
        jdbc.update("insert into accounting_period(id,sacco_id,starts_on,ends_on,state,created_by,created_at,closed_by,closed_at) values(?,?,date '2026-01-01',date '2026-01-31','CLOSED',?,now(),?,now())",closedPeriod,institution,staff,staff);
        assertThat(Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate().migrationsExecuted).isEqualTo(2);
    }

    @Test void retiredTablesLeavePublicSchemaWithoutChangingRows() {
        assertThat(jdbc.queryForObject("select count(*) from pg_tables where schemaname='accounting_archive'",Integer.class)).isEqualTo(43);
        assertThat(jdbc.queryForObject("select to_regclass('public.accounting_supplier') is null and to_regclass('public.financial_statement_templates') is null and to_regclass('public.reconciliation_active_allocation') is null",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select row_to_json(s)::text from accounting_archive.accounting_supplier s where id=?",String.class,supplier)).isEqualTo(supplierBefore);
        assertThat(jdbc.queryForObject("select row_to_json(s)::text from accounting_archive.financial_statement_templates s where id=?",String.class,template)).isEqualTo(templateBefore);
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint where connamespace='accounting_archive'::regnamespace and contype='f' and not convalidated",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname in ('validate_accounting_policy','accounting_validate_period_decision','check_financial_statement_source','validate_business_posted_source','check_accounting_release_decision')",Integer.class)).isZero();
    }

    @Test void archivedEvidenceRejectsEveryWriteIncludingTruncate() {
        for (var sql : new String[]{
            "update accounting_archive.accounting_supplier set name='Changed'",
            "delete from accounting_archive.accounting_supplier",
            "insert into accounting_archive.accounting_supplier select * from accounting_archive.accounting_supplier",
            "truncate accounting_archive.accounting_supplier cascade"}) {
            assertThatThrownBy(() -> jdbc.execute(sql)).hasMessageContaining("Archived accounting evidence is read-only");
        }
        assertThat(jdbc.queryForObject("select name from accounting_archive.accounting_supplier where id=?",String.class,supplier)).isEqualTo("Retained supplier");
    }

    @Test void historyProtectionAndOrdinaryLoanOwnershipQueriesStillWork() {
        var history = new RetainedFinancialHistoryRepository(new NamedParameterJdbcTemplate(jdbc));
        assertThat(history.hasInstitutionHistory(institution)).isTrue();
        assertThat(history.hasMemberHistory(staff)).isTrue();
        assertThat(history.hasInstitutionHistory("NO-SUCH-INSTITUTION")).isFalse();
        assertThat(new LoanPostingAuthorizationRepository(jdbc).retainedSourceOwnsLoan(UUID.randomUUID())).isFalse();
        assertThat(new LoanPostingAuthorizationRepository(jdbc).retainedSourceOwnsLoan(legacyLoan)).isTrue();
        var portfolio = new LoanPortfolioReportRepository(new NamedParameterJdbcTemplate(jdbc));
        var scope = new LoanPortfolioReportService.Scope(institution,"B1",false);
        var parameters = new LoanPortfolioReportService.Parameters(LocalDate.of(2026,1,1),LocalDate.of(2026,10,15),OffsetDateTime.now(),0);
        assertThat(portfolio.rows(scope,parameters)).isEmpty();
        assertThat(portfolio.coverage(scope,parameters)).isNotNull();
    }

    @Test void coreDateBucketsRemainAvailableAndExistingClosedDatesCannotChange() {
        var vouchers = new VoucherRepository(jdbc);
        var day = LocalDate.of(2026,10,15);
        UUID period = transactions.execute(status -> vouchers.period(institution,day,staff,OffsetDateTime.now()));
        assertThat(period).isNotNull();
        UUID repeated = transactions.execute(status -> vouchers.period(institution,day,staff,OffsetDateTime.now()));
        assertThat(repeated).isEqualTo(period);
        assertThatThrownBy(() -> jdbc.update("update accounting_period set state='OPEN' where id=?",closedPeriod)).hasMessageContaining("Period management is retired");
        assertThatThrownBy(() -> jdbc.update("update accounting_period set state='CLOSED' where id=?",period)).hasMessageContaining("Period management is retired");
        assertThatThrownBy(() -> transactions.execute(status -> vouchers.period(institution,LocalDate.of(2026,1,10),staff,OffsetDateTime.now()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jdbc.update("insert into gl_journal(id,direct_post) values(?,false)",UUID.randomUUID())).hasMessageContaining("Legacy accounting workflows are retired");
    }
}
