package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Opt-in and restricted to a dedicated loopback test database; never use the application's datasource.
@EnabledIfEnvironmentVariable(named = "MICROFINANCE_TEST_DATABASE_URL",
    matches="(?:(?:jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_ledger_test|jdbc:postgresql://127\\.0\\.0\\.1:55439/microfinance_accounting_h_release_combined_test_20261004)|jdbc:postgresql://127\\.0\\.0\\.1:55439/microfinance_accounting_h_release_integrity_test_20261004)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoanRepaymentLedgerPostgresTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    private static final AtomicLong NUMBER = new AtomicLong(System.currentTimeMillis());
    private AnnotationConfigApplicationContext context;
    private LoanRepaymentLedgerService service;
    private LoanApplicationRepository loans;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private AppUserPrincipal poster, checker;
    private LoanApplication loan;

    @BeforeAll
    void start() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        service = context.getBean(LoanRepaymentLedgerService.class);
        loans = context.getBean(LoanApplicationRepository.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @AfterAll
    void stop() { if (context != null) context.close(); }

    @BeforeEach
    void fixture() {
        reset(context.getBean(AuditService.class));
        jdbc.update("INSERT INTO registered_saccos(sacco_id, sacco_name, active, created_at, updated_at) "
            + "VALUES ('I-PG', 'Test Institution', true, now(), now()) ON CONFLICT DO NOTHING");
        poster = operator("B1");
        checker = operator("B1");
        loan = openLoan();
    }

    @Test
    void operationalCleanupRetainsFinancialAndReportEvidence() {
        List<String> retainedTypes = List.of("LOAN_REPAYMENT", "ACCOUNTING", "ACCOUNTING_POLICY",
            "ACCOUNTING_PERIOD", "OPERATIONAL_REPORT_TEMPLATE", "REPORT_RUN", "FINANCIAL_STATEMENT");
        List<UUID> retained = new ArrayList<>();
        OffsetDateTime old = OffsetDateTime.now().minusDays(1000);
        for (String type : retainedTypes) {
            UUID id = UUID.randomUUID(); retained.add(id);
            jdbc.update("INSERT INTO audit_log(id, entity_type, action, created_at) VALUES (?, ?, ?, ?)",
                id, type, "RETENTION_TEST", old);
        }
        UUID operational = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_log(id, entity_type, action, created_at) VALUES (?, 'LOGIN', 'RETENTION_TEST', ?)",
            operational, old);
        tx.executeWithoutResult(status -> context.getBean(AuditLogRepository.class)
            .deleteExpiredOperationalAudit(OffsetDateTime.now().minusDays(70)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE id = ?", Long.class, operational)).isZero();
        for (UUID id : retained) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE id = ?", Long.class, id)).isEqualTo(1L);
        }
    }

    @Test
    void postingRetryAndReversalPersistExactlyOnceAndBalance() {
        var command = command("100.00", "PG-" + UUID.randomUUID(), UUID.randomUUID());
        var receipt = service.post(loan.getId(), poster, command);
        assertThat(service.post(loan.getId(), poster, command).id()).isEqualTo(receipt.id());
        assertThat(count("loan_repayment_transactions")).isEqualTo(1);
        assertThat(balance("principal_paid")).isEqualByComparingTo("82.00");
        assertThat(balance("interest_paid")).isEqualByComparingTo("18.00");
        assertThat(service.view(loan.getId(), poster, 0, 0).history().getTotalElements()).isEqualTo(1);
        assertBalanced();

        UUID key = UUID.randomUUID();
        var reversal = service.reverse(loan.getId(), receipt.id(), checker, key, "Verified incorrect allocation");
        assertThat(service.reverse(loan.getId(), receipt.id(), checker, key, "Verified incorrect allocation").id())
            .isEqualTo(reversal.id());
        assertThat(count("loan_repayment_transactions")).isEqualTo(2);
        assertThat(balance("principal_paid")).isZero();
        assertThat(balance("interest_paid")).isZero();
        assertThat(service.receipt(loan.getId(), receipt.id(), poster).reversedBy()).isEqualTo(reversal.id());
        assertThat(jdbc.queryForObject("SELECT amount FROM loan_repayment_transactions WHERE id = ?", BigDecimal.class, receipt.id()))
            .isEqualByComparingTo("100.00");
        assertBalanced();
    }

    @Test
    void concurrentSettlementCannotOverdrawOrDuplicateTheReceipt() throws Exception {
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> post = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                try {
                    service.post(loan.getId(), poster, command("218.00", "PG-" + UUID.randomUUID(), UUID.randomUUID()));
                    return true;
                } catch (IllegalArgumentException ex) {
                    assertThat(ex.getMessage()).isEqualTo("repayment.error.state");
                    return false;
                }
            };
            Future<Boolean> first = pool.submit(post), second = pool.submit(post);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(count("loan_repayment_transactions")).isEqualTo(1);
        assertThat(balance("principal_paid")).isEqualByComparingTo("200.00");
        assertThat(loans.findById(loan.getId()).orElseThrow().getStatus()).isEqualTo(LoanStatus.PAID);
        assertBalanced();
    }

    @Test
    void failedAuditRollsBackReceiptAllocationsBalancesJournalAndStatus() {
        doThrow(new IllegalStateException("Audit unavailable")).when(context.getBean(AuditService.class))
            .logEvent(anyString(), any(), anyString(), any(), any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
        assertThatThrownBy(() -> service.post(loan.getId(), poster, command("218.00", "PG-" + UUID.randomUUID(), UUID.randomUUID())))
            .isInstanceOf(IllegalStateException.class).hasMessage("Audit unavailable");
        assertThat(count("loan_repayment_transactions")).isZero();
        assertThat(balance("principal_paid")).isZero();
        assertThat(balance("interest_paid")).isZero();
        assertThat(count("loan_journal_entries")).isEqualTo(2);
        assertThat(loans.findById(loan.getId()).orElseThrow().getStatus()).isEqualTo(LoanStatus.DISBURSED);
        assertThat(jdbc.queryForObject("SELECT SUM(principal_paid + interest_paid) FROM loan_ledger_installments WHERE loan_application_id = ?",
            BigDecimal.class, loan.getId())).isZero();
    }

    @Test
    void databaseRejectsHistoryEditsContractChangesAndUnbalancedVouchers() {
        var receipt = service.post(loan.getId(), poster, command("20.00", "PG-" + UUID.randomUUID(), UUID.randomUUID()));
        assertThatThrownBy(() -> jdbc.update("UPDATE loan_repayment_transactions SET amount = amount WHERE id = ?", receipt.id()))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM loan_repayment_transactions WHERE id = ?", receipt.id()))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE loan_ledger_installments SET due_date = due_date + 1 WHERE loan_application_id = ?", loan.getId()))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO loan_journal_entries(id, loan_application_id, voucher_id, account_code, debit, credit, effective_date, posted_at) "
            + "VALUES (?, ?, ?, 'CASH_CLEARING', 1, 0, ?, now())", UUID.randomUUID(), loan.getId(), UUID.randomUUID(), TODAY))
            .isInstanceOf(DataAccessException.class);
        assertThat(count("loan_journal_entries")).isEqualTo(5);
        assertBalanced();
    }

    @Test
    void scopedReceiptAndReversalGuardsDoNotMutateTheLoan() {
        var receipt = service.post(loan.getId(), poster, command("20.00", "PG-" + UUID.randomUUID(), UUID.randomUUID()));
        AppUserPrincipal otherBranch = operator("B2");
        assertThatThrownBy(() -> service.receipt(loan.getId(), receipt.id(), otherBranch)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.post(loan.getId(), otherBranch, command("20.00", "PG-X", UUID.randomUUID())))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.reverse(loan.getId(), receipt.id(), poster, UUID.randomUUID(), "Correction"))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("repayment.error.checker");
        assertThat(count("loan_repayment_transactions")).isEqualTo(1);
    }

    private LoanApplication openLoan() {
        long number = NUMBER.incrementAndGet();
        OffsetDateTime now = OffsetDateTime.parse("2026-10-01T12:00:00+03:00");
        LoanApplication result = LoanApplication.builder().id(UUID.randomUUID()).applicationNumber(number).loanId(Long.toString(number))
            .saccoId("I-PG").stationId("B1").applicantMemberId(poster.getMemberId()).loanType(LoanType.DEVELOPMENT_LOAN)
            .amount(new BigDecimal("200.00")).tenorMonths(2).status(LoanStatus.DISBURSED).formData("{}")
            .policySnapshot("{}").requiredGuarantors(0).financialSnapshot("{\"annualInterestRate\":12}")
            .disbursementDate(LocalDate.of(2026, 8, 1)).createdAt(now).updatedAt(now)
            .repaymentScheduleJson("{\"schedule\":[{\"dueDate\":\"2026-09-01\",\"principalComponent\":100,\"interestComponent\":10,\"amount\":110},"
                + "{\"dueDate\":\"2026-10-01\",\"principalComponent\":100,\"interestComponent\":8,\"amount\":108}]}").build();
        tx.executeWithoutResult(status -> {
            loans.saveAndFlush(result);
            service.openAtDisbursement(result);
        });
        return result;
    }

    private AppUserPrincipal operator(String branch) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO members(id, sacco_id, station_id, member_no, full_name, status, position, created_at, is_member, password_hash) "
            + "VALUES (?, 'I-PG', ?, ?, 'Test Operator', 'ACTIVE', 'MANAGER', now(), true, 'not-a-login-password')", id, branch, id.toString());
        return new AppUserPrincipal(Member.builder().id(id).saccoId("I-PG").stationId(branch).memberNo(id.toString())
            .fullName("Test Operator").memberAccount(true).position(Position.MANAGER).status(MemberStatus.ACTIVE).build(),
            Set.of(UserClaim.LOAN_REPAYMENTS_VIEW, UserClaim.LOAN_REPAYMENTS_CREATE, UserClaim.LOAN_REPAYMENTS_REVERSE), true);
    }

    private LoanRepaymentLedgerService.PaymentCommand command(String amount, String reference, UUID key) {
        return new LoanRepaymentLedgerService.PaymentCommand(new BigDecimal(amount), TODAY, LoanRepaymentTransaction.Channel.CASH, reference, key);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE loan_application_id = ?", Integer.class, loan.getId());
    }

    private BigDecimal balance(String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM loan_ledgers WHERE loan_application_id = ?", BigDecimal.class, loan.getId());
    }

    private void assertBalanced() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT voucher_id FROM loan_journal_entries WHERE loan_application_id = ? "
            + "GROUP BY voucher_id HAVING SUM(debit - credit) <> 0) AS unbalanced", Integer.class, loan.getId())).isZero();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = "com.sacco.mvp.repository")
    static class TestConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(System.getenv("MICROFINANCE_TEST_DATABASE_URL"), "microfinance_test", "");
        }
        @Bean(initMethod = "migrate") Flyway flyway(DataSource source) {
            return Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
        }
        @Bean @DependsOn("flyway") LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.sacco.mvp.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate",
                "hibernate.order_inserts", "true", "hibernate.order_updates", "true", "hibernate.jdbc.batch_size", "32",
                "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean ObjectMapper mapper() { return JsonMapper.builder().findAndAddModules().build(); }
        @Bean ApplicationClock clock() {
            ApplicationClock clock = mock(ApplicationClock.class);
            when(clock.today()).thenReturn(TODAY);
            when(clock.now()).thenReturn(OffsetDateTime.parse("2026-10-01T12:00:00+03:00"));
            return clock;
        }
        @Bean AccessControlService access() { return new AccessControlService(); }
        @Bean AuditService audit() { return mock(AuditService.class); }
        @Bean LoanRepaymentLedgerService ledger(LoanLedgerRepository ledgers, LoanLedgerInstallmentRepository installments,
                LoanRepaymentTransactionRepository payments, LoanRepaymentAllocationRepository allocations, LoanJournalEntryRepository journal,
                LoanApplicationRepository loans, ObjectMapper mapper, ApplicationClock clock, AccessControlService access, AuditService audit) {
            return new LoanRepaymentLedgerService(ledgers, installments, payments, allocations, journal, loans, mapper, clock, access, audit,
                org.mockito.Mockito.mock(com.sacco.mvp.accounting.business.service.BusinessAccountingGuard.class));
        }
    }
}
