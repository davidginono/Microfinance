package com.sacco.mvp.accounting.policy;

import com.sacco.mvp.accounting.repository.GeneralLedgerRepository;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_POLICY_TEST_DATABASE_URL", matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_accounting_a_test(?:_integration_20261002)?")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingPolicyPostgresTest {
    AnnotationConfigApplicationContext context;
    AccountingPolicyService service;
    JdbcTemplate jdbc;
    String institution;
    AppUserPrincipal maker, checker;

    @BeforeAll void start() {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(AccountingPolicyService.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    }
    @AfterAll void stop() { if (context != null) context.close(); }
    @BeforeEach void fixture() {
        institution = "AP-" + UUID.randomUUID();
        jdbc.update("INSERT INTO registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) VALUES (?, 'Synthetic policy test',true,now(),now())", institution);
        maker = actor("B1"); checker = actor("B2");
        reset(context.getBean(AuditService.class), context.getBean(MemberDirectoryService.class), context.getBean(UserClaimService.class), context.getBean(SaccoRegistryService.class));
        when(context.getBean(MemberDirectoryService.class).find(any())).thenAnswer(i -> {
            UUID memberId = i.getArgument(0); var member = AccountingPolicyServiceTest.member(memberId, institution);
            member.setStationId(memberId.equals(checker.getMemberId()) ? "B2" : "B1"); return Optional.of(member);
        });
        when(context.getBean(SaccoRegistryService.class).findActiveSacco(institution)).thenReturn(Optional.of(
            RegisteredSacco.builder().saccoId(institution).active(true).build()));
        when(context.getBean(SaccoRegistryService.class).findStation(eq(institution), anyString())).thenAnswer(i -> Optional.of(
            SaccoStation.builder().saccoId(institution).stationId(i.getArgument(1)).active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        when(context.getBean(UserClaimService.class).effectiveClaims(any(), anyCollection(), anyBoolean())).thenReturn(Set.of(
            UserClaim.ACCOUNTING_POLICIES_VIEW, UserClaim.ACCOUNTING_POLICIES_CREATE, UserClaim.ACCOUNTING_POLICIES_APPROVE));
    }
    @Test void approvedPolicyIsIsolatedImmutableAndIdempotent() {
        var c = AccountingPolicyServiceTest.command();
        var proposal = service.create(maker, c);
        assertThat(service.create(maker, c).id()).isEqualTo(proposal.id());
        assertThatThrownBy(() -> service.requireApprovedLocalPolicy(institution, c.openingDate())).hasMessage("accounting.policy.error.unapproved");
        var decision = decision();
        service.decide(proposal.id(), checker, decision);
        assertThat(service.decide(proposal.id(), checker, decision).checkerId()).isEqualTo(checker.getMemberId());
        assertThat(service.requireApprovedLocalPolicy(institution, c.effectiveFrom()).id()).isEqualTo(proposal.id());
        assertThatThrownBy(() -> service.requireApprovedLocalPolicy("OTHER-INSTITUTION", c.effectiveFrom())).hasMessage("accounting.policy.error.unapproved");
        assertThatThrownBy(() -> jdbc.update("UPDATE accounting_policies SET evidence_reference='changed' WHERE id=?", proposal.id())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM accounting_policy_approvals WHERE policy_id=?", proposal.id())).isInstanceOf(DataAccessException.class);
        assertThat(count("accounting_policies")).isEqualTo(1);
        assertThat(count("accounting_policy_approvals")).isEqualTo(1);
    }
    @Test void localMappingsRejectForeignMissingHeadingAndInactiveAccounts() {
        var valid=account(institution,"POSTING",true);
        for(var id:List.of(UUID.randomUUID(),account("FOREIGN-"+institution,"POSTING",true),account(institution,"HEADING",true),account(institution,"POSTING",false))) {
            var c=withMapping(id);
            assertThatThrownBy(()->service.create(maker,c)).hasMessage("accounting.policy.error.accountMappings");
        }
        assertThat(count("accounting_policies")).isZero();
        var p=service.create(maker,withMapping(valid));service.decide(p.id(),checker,decision());
        assertThat(service.requireApprovedLocalPolicy(institution,p.effectiveFrom()).accountMappings()).containsEntry("CASH",valid);
    }
    @Test void accountDeactivatedAfterProposalCannotBeApproved() {
        var id=account(institution,"POSTING",true);var p=service.create(maker,withMapping(id));
        jdbc.update("update gl_account set active=false where id=?",id);
        assertThatThrownBy(()->service.decide(p.id(),checker,decision())).hasMessage("accounting.policy.error.accountMappings");
        assertThat(count("accounting_policy_approvals")).isZero();
        assertThatThrownBy(()->service.requireApprovedLocalPolicy(institution,p.effectiveFrom())).hasMessage("accounting.policy.error.unapproved");
    }
    UUID account(String institutionId,String kind,boolean active) {
        var id=UUID.randomUUID();
        jdbc.update("insert into gl_account(id,sacco_id,code,name,type,normal_balance,kind,purpose,active,maker_id,created_at) values(?,?,?,'Synthetic mapped cash','ASSET','DEBIT',?,'CASH',?,?,now())",id,institutionId,"T"+id.toString().replace("-",""),kind,active,maker.getMemberId());return id;
    }
    AccountingPolicyService.PolicyCommand withMapping(UUID id) {
        var c=AccountingPolicyServiceTest.command();return new AccountingPolicyService.PolicyCommand(c.requestKey(),c.authoritativeLedger(),c.openingDate(),c.effectiveFrom(),c.decisions(),c.postingMatrix(),Map.of("CASH",id),c.evidenceReference());
    }

    @Test void failedAuditRollsBackProposalAndApproval() {
        doThrow(new IllegalStateException("Audit unavailable")).when(context.getBean(AuditService.class))
            .log(anyString(), any(), anyString(), any(), any(), any());
        assertThatThrownBy(() -> service.create(maker, AccountingPolicyServiceTest.command())).hasMessage("Audit unavailable");
        assertThat(count("accounting_policies")).isZero();
        reset(context.getBean(AuditService.class));
        var p = service.create(maker, AccountingPolicyServiceTest.command());
        doThrow(new IllegalStateException("Audit unavailable")).when(context.getBean(AuditService.class))
            .log(anyString(), any(), anyString(), any(), any(), any());
        assertThatThrownBy(() -> service.decide(p.id(), checker, decision())).hasMessage("Audit unavailable");
        assertThat(count("accounting_policy_approvals")).isZero();
    }
    @Test void databaseRejectsMakerApprovalAndIncompleteDecision() {
        var p = service.create(maker, AccountingPolicyServiceTest.command());
        assertThatThrownBy(() -> jdbc.update("INSERT INTO accounting_policy_approvals(policy_id,sacco_id,policy_version,effective_from,checker_id,decision,evidence_reference,reason,decided_at) VALUES (?,?,1,?,?, 'APPROVED','Test','Test',now())",
            p.id(), institution, p.effectiveFrom(), maker.getMemberId())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO accounting_policies(id,sacco_id,policy_version,effective_from,opening_date,authoritative_ledger,decisions_json,posting_matrix_json,account_mappings_json,evidence_reference,maker_id,request_key,created_at) VALUES (?,?,2,?,?,'LOCAL_GL','{}','{}','{}','Test',?,?,now())",
            UUID.randomUUID(), institution, p.effectiveFrom(), p.openingDate(), maker.getMemberId(), UUID.randomUUID())).isInstanceOf(DataAccessException.class);
    }
    @Test void concurrentCompetingBooksCannotBothBeApproved() throws Exception {
        var c = AccountingPolicyServiceTest.command();
        var local = service.create(maker, c);
        var external = service.create(maker, new AccountingPolicyService.PolicyCommand(UUID.randomUUID(),
            AccountingPolicyService.AuthoritativeLedger.EXTERNAL_GL, c.openingDate(), c.effectiveFrom(), c.decisions(), c.postingMatrix(), c.accountMappings(), c.evidenceReference()));
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            Callable<Boolean> one = () -> decideAfter(start, local.id());
            Callable<Boolean> two = () -> decideAfter(start, external.id());
            var first = pool.submit(one); var second = pool.submit(two); start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
        assertThat(count("accounting_policy_approvals")).isEqualTo(1);
    }
    boolean decideAfter(CountDownLatch start, UUID id) throws Exception {
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout");
        try { service.decide(id, checker, decision()); return true; }
        catch (DataAccessException | IllegalArgumentException e) { return false; }
    }
    int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE sacco_id=?", Integer.class, institution); }
    AccountingPolicyService.ApprovalCommand decision() { return new AccountingPolicyService.ApprovalCommand(AccountingPolicyService.Decision.APPROVED, "Synthetic evidence", "Synthetic reviewed fixture", true); }
    AppUserPrincipal actor(String branch) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) VALUES (?,?,?,?,'Synthetic accountant','ACTIVE','ACCOUNTANT',now(),false,'not-a-login-password')", id, institution, branch, id.toString());
        var member = AccountingPolicyServiceTest.member(id, institution); member.setStationId(branch);
        return new AppUserPrincipal(member, Set.of(UserClaim.ACCOUNTING_POLICIES_VIEW, UserClaim.ACCOUNTING_POLICIES_CREATE, UserClaim.ACCOUNTING_POLICIES_APPROVE), true);
    }
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement
    @EnableJpaRepositories(basePackages="com.sacco.mvp.accounting.policy")
    static class Config {
        @Bean(destroyMethod="close") HikariDataSource dataSource() {
            var source=new HikariDataSource(); source.setJdbcUrl(System.getenv("MICROFINANCE_POLICY_TEST_DATABASE_URL"));
            source.setUsername("microfinance_test"); source.setPassword(""); source.setMaximumPoolSize(4); source.setMinimumIdle(0);
            source.setConnectionTimeout(30000); source.addDataSourceProperty("sslmode","disable");
            source.addDataSourceProperty("connectTimeout","10"); source.addDataSourceProperty("socketTimeout","30"); return source;
        }
        @Bean(initMethod="migrate") Flyway flyway(DataSource source) { return Flyway.configure().dataSource(source).locations("classpath:db/migration").load(); }
        @Bean @DependsOn("flyway") LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(source);
            factory.setPackagesToScan("com.sacco.mvp.domain", "com.sacco.mvp.accounting.policy"); factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")); return factory;
        }
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean ObjectMapper mapper() { return JsonMapper.builder().findAndAddModules().build(); }
        @Bean ApplicationClock clock() { var c = mock(ApplicationClock.class); when(c.today()).thenReturn(LocalDate.of(2026,10,2)); when(c.now()).thenReturn(OffsetDateTime.parse("2026-10-02T12:00:00+03:00")); return c; }
        @Bean AccessControlService access() { return new AccessControlService(); }
        @Bean AuditService audit() { return mock(AuditService.class); }
        @Bean UserClaimService userClaims() { return mock(UserClaimService.class); }
        @Bean MemberDirectoryService directory() { return mock(MemberDirectoryService.class); }
        @Bean SaccoRegistryService institutions() { return mock(SaccoRegistryService.class); }
        @Bean GeneralLedgerRepository accounts(DataSource source) { return new GeneralLedgerRepository(new JdbcTemplate(source)); }
        @Bean AccountingPolicyService policies(AccountingPolicyRepository policies, AccountingPolicyApprovalRepository approvals,
            AccessControlService access, AuditService audit, ApplicationClock clock, ObjectMapper mapper, UserClaimService claims, MemberDirectoryService directory, SaccoRegistryService institutions, GeneralLedgerRepository accounts) {
            return new AccountingPolicyService(policies, approvals, access, audit, clock, mapper, claims, directory, institutions, accounts);
        }
    }
}
