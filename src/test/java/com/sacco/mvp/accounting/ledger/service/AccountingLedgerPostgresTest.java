package com.sacco.mvp.accounting.ledger.service;

import com.sacco.mvp.accounting.ledger.dto.LedgerDtos.*;
import com.sacco.mvp.accounting.ledger.exception.LedgerException;
import com.sacco.mvp.accounting.ledger.repository.AccountingLedgerRepository;
import com.sacco.mvp.accounting.policy.dto.*;
import com.sacco.mvp.accounting.policy.model.*;
import com.sacco.mvp.accounting.policy.repository.AccountingPolicyRepository;
import com.sacco.mvp.accounting.policy.service.AccountingPolicyService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in dedicated disposable PostgreSQL; operational datasource credentials are never loaded. */
@EnabledIfEnvironmentVariable(named="MICROFINANCE_ACCOUNTING_B_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_accounting_b_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingLedgerPostgresTest {
    private static final LocalDate TODAY=LocalDate.of(2026,10,4);
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private AccountingLedgerService ledger;
    private AccountingPolicyService policies;
    private TransactionTemplate tx;
    private AppUserPrincipal maker,checker,third;
    private UUID period,openingJournal,openingBatch;
    private String institution;

    @BeforeAll void start() {
        context=new AnnotationConfigApplicationContext(Config.class);
        jdbc=context.getBean(JdbcTemplate.class);ledger=context.getBean(AccountingLedgerService.class);policies=context.getBean(AccountingPolicyService.class);
        tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }
    @AfterAll void stop(){if(context!=null)context.close();}
    @BeforeEach void fixture() {
        institution="TEST-B-"+UUID.randomUUID();
        jdbc.update("INSERT INTO registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) VALUES(?,'Synthetic accounting tests',true,now(),now())",institution);
        maker=operator("B1",Position.ACCOUNTANT);checker=operator("B1",Position.ACCOUNTANT);third=operator("B1",Position.ACCOUNTANT);
        ledger.createAccount(maker,new AccountCommand("1000","Synthetic cash",AccountKind.ASSET,NormalBalance.DEBIT,null,AccountUsage.POSTING,AccountCategory.CASH));
        ledger.createAccount(maker,new AccountCommand("3000","Synthetic owner capital",AccountKind.EQUITY,NormalBalance.CREDIT,null,AccountUsage.POSTING,AccountCategory.CAPITAL));
        ledger.createAccount(maker,new AccountCommand("1100","Synthetic loan principal",AccountKind.ASSET,NormalBalance.DEBIT,null,AccountUsage.CONTROL,AccountCategory.LOAN_PRINCIPAL));
        Map<PolicyDecision,String> decisions=new EnumMap<>(PolicyDecision.class);for(var d:PolicyDecision.values())decisions.put(d,"SYNTHETIC TEST APPROVAL, not an institution policy: "+d);
        Map<AccountingEvent,PostingRule> rules=new EnumMap<>(AccountingEvent.class);
        for(var e:AccountingEvent.values())rules.put(e,new PostingRule(false,Map.of(),"Disabled test capability","TEST-POLICY"));
        for(var e:List.of(AccountingEvent.MANUAL_JOURNAL,AccountingEvent.OPENING_BALANCE,AccountingEvent.REVERSAL))rules.put(e,new PostingRule(true,Map.of(),"Synthetic test recognition","TEST-POLICY"));
        rules.put(AccountingEvent.CAPITAL,new PostingRule(true,Map.of(AccountRole.CASH_BANK,"1000",AccountRole.CAPITAL,"3000"),"Synthetic test owner capital","TEST-POLICY"));
        PolicyView p=policies.create(maker,UUID.randomUUID(),new PolicyContent(GlAuthority.LOCAL,TODAY.minusDays(2),TODAY.minusDays(2),"SYNTHETIC TEST evidence",decisions,rules));
        policies.approve(checker,p.id(),p.contentHash(),"SYNTHETIC TEST REVIEW","Fixtures only");
        period=ledger.openPeriod(maker,TODAY.minusDays(30),TODAY.plusDays(30),"SYNTHETIC TEST PERIOD");
        openingBatch=ledger.previewOpening(maker,new OpeningCommand(UUID.randomUUID(),TODAY.minusDays(2),"SYNTHETIC TEST opening",lines("100.00")));
        openingJournal=ledger.openings(maker,0).getContent().getFirst().journalId();
        ledger.approveOpening(checker,openingBatch,"SYNTHETIC TEST source reconciliation");ledger.post(maker,openingJournal);
        ledger.approveCutover(checker,openingBatch,"SYNTHETIC TEST no legacy loans");
    }
    @Test void manualDraftApprovalPostingReversalAndTotalsKeepOriginals() {
        JournalCommand c=command("10.01");UUID id=ledger.draft(maker,c);
        assertThat(ledger.draft(maker,c)).isEqualTo(id);
        assertThatThrownBy(()->ledger.approve(maker,id,"SELF")).isInstanceOf(LedgerException.class).hasMessageContaining("independent");
        ledger.approve(checker,id,"SYNTHETIC independent review");ledger.post(maker,id);ledger.post(maker,id);
        UUID reversal=ledger.requestReversal(checker,id,UUID.randomUUID(),TODAY,"Correction","SYNTHETIC corrected source");
        ledger.approve(third,reversal,"SYNTHETIC reversal checked");ledger.post(third,reversal);
        assertThat(ledger.view(maker,id).state()).isEqualTo("REVERSED");
        assertThat(jdbc.queryForObject("SELECT sum(l.debit-l.credit) FROM accounting_journal_line l JOIN accounting_journal j ON j.id=l.journal_id WHERE j.sacco_id=? AND j.state IN ('POSTED','REVERSED')",BigDecimal.class,institution)).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounting_outbox WHERE sacco_id=?",Integer.class,institution)).isEqualTo(3);
        assertThatThrownBy(()->ledger.requestReversal(checker,id,UUID.randomUUID(),TODAY,"Changed","Different")).hasMessageContaining("retry");
    }
    @Test void exactCentsHighAmountsAndChangedPayloadRetriesFailSafely() {
        JournalCommand c=command("9999999999999999.99");UUID id=ledger.draft(maker,c);
        assertThat(ledger.view(maker,id).lines().getFirst().debit()).isEqualByComparingTo("9999999999999999.99");
        assertThatThrownBy(()->ledger.draft(maker,new JournalCommand(c.requestKey(),c.effectiveDate(),c.description(),c.evidenceReference(),lines("1.00")))).hasMessageContaining("retry");
        assertThatThrownBy(()->ledger.draft(maker,command("1.001"))).hasMessageContaining("precision");
        assertThatThrownBy(()->ledger.draft(maker,command("1e-1000000000"))).hasMessageContaining("precision");
        assertThatThrownBy(()->ledger.draft(maker,command("1e2147483647"))).hasMessageContaining("amount");
        assertThatThrownBy(()->ledger.draft(maker,command("10000000000000000.00"))).hasMessageContaining("amount");
        assertThatThrownBy(()->ledger.draft(maker,new JournalCommand(UUID.randomUUID(),TODAY,"Unbalanced","TEST",List.of(line("1000","10","0"),line("3000","0","9"))))).hasMessageContaining("balance");
    }
    @Test void manualControlAndClassificationBypassesFail() {
        assertThatThrownBy(()->ledger.draft(maker,new JournalCommand(UUID.randomUUID(),TODAY,"Bypass","TEST",List.of(line("1100","10","0"),line("3000","0","10"))))).hasMessageContaining("control");
        assertThatThrownBy(()->ledger.createAccount(maker,new AccountCommand("BROKEN","Misclassified principal",AccountKind.INCOME,NormalBalance.CREDIT,null,AccountUsage.CONTROL,AccountCategory.LOAN_PRINCIPAL))).hasMessageContaining("classification");
        assertThatThrownBy(()->ledger.createAccount(maker,new AccountCommand("BYPASS","Uncontrolled principal",AccountKind.ASSET,NormalBalance.DEBIT,null,AccountUsage.POSTING,AccountCategory.LOAN_PRINCIPAL))).hasMessageContaining("control");
    }
    @Test void databaseRejectsHistoryMutationCrossTenantAccountAndHierarchyLinks() {
        UUID id=ledger.draft(maker,command("2.00"));ledger.approve(checker,id,"TEST");ledger.post(maker,id);
        assertThatThrownBy(()->jdbc.update("UPDATE accounting_journal_line SET debit=debit+1 WHERE journal_id=?",id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE accounting_journal SET description='silent rewrite' WHERE id=?",id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM accounting_journal WHERE id=?",id)).isInstanceOf(DataAccessException.class);
        UUID cash=ledger.accounts(maker,0).getContent().stream().filter(a->a.code().equals("1000")).findFirst().orElseThrow().id();
        assertThatThrownBy(()->jdbc.update("UPDATE accounting_account SET parent_id=id WHERE id=?",cash)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM accounting_account WHERE id=?",cash)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE accounting_opening_batch SET source_evidence='rewritten' WHERE id=?",openingBatch)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->ledger.requestReversal(checker,openingJournal,UUID.randomUUID(),TODAY,"Remove opening","TEST")).hasMessageContaining("cutover");
    }
    @Test void unapprovedBooksAndUnreviewedOpeningsStayUnavailable() {
        AppUserPrincipal otherBranch=operator("B2",Position.ACCOUNTANT);
        assertThat(ledger.coverage(otherBranch).status()).isEqualTo("INCOMPLETE");
        assertThatThrownBy(()->tx.execute(s->ledger.postSource(otherBranch,source(UUID.randomUUID(),"NO-CUTOVER",checker.getMemberId())))).hasMessageContaining("cutover");
        assertThatThrownBy(()->ledger.view(otherBranch,openingJournal)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->ledger.accounts(operator("B1",Position.ADMIN),0)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->ledger.approveCutover(maker,openingBatch,"SELF")).hasMessageContaining("openingReview");
    }
    @Test void sourcePostingSharesCallerTransactionAndRejectsChangedCheckerAndSourceKey() {
        UUID key=UUID.randomUUID();SourceJournalCommand c=source(key,"SYNTHETIC-CAPITAL",checker.getMemberId());
        assertThatThrownBy(()->ledger.postSource(maker,c)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        UUID id=tx.execute(s->ledger.postSource(maker,c));UUID retried=tx.execute(s->ledger.postSource(maker,c));assertThat(retried).isEqualTo(id);
        assertThatThrownBy(()->tx.execute(s->ledger.postSource(maker,source(key,c.sourceReference(),third.getMemberId())))).hasMessageContaining("retry");
        assertThatThrownBy(()->tx.execute(s->ledger.postSource(maker,source(UUID.randomUUID(),c.sourceReference(),checker.getMemberId())))).hasMessageContaining("retry");
        UUID failedKey=UUID.randomUUID();
        assertThatThrownBy(()->tx.execute(s->{ledger.postSource(maker,source(failedKey,"ROLLBACK",checker.getMemberId()));throw new IllegalStateException("source failed");})).hasMessage("source failed");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounting_journal WHERE sacco_id=? AND request_key=?",Integer.class,institution,failedKey)).isZero();
    }
    @Test void zeroOpeningNeedsExplicitIndependentCertificateAndCreatesNoJournal() {
        AppUserPrincipal b2Maker=operator("B2",Position.ACCOUNTANT),b2Checker=operator("B2",Position.ACCOUNTANT);
        UUID key=UUID.randomUUID(),zero=ledger.previewZeroOpening(b2Maker,key,TODAY.minusDays(2),"SYNTHETIC independent zero bank/cash/source evidence");
        assertThat(ledger.coverage(b2Maker).reviewedOpening()).isFalse();
        assertThatThrownBy(()->ledger.approveOpening(b2Maker,zero,"Self")).hasMessageContaining("independent");
        assertThat(ledger.previewZeroOpening(b2Maker,key,TODAY.minusDays(2),"SYNTHETIC independent zero bank/cash/source evidence")).isEqualTo(zero);
        ledger.approveOpening(b2Checker,zero,"SYNTHETIC zero reconciled");ledger.approveCutover(b2Checker,zero,"SYNTHETIC zero boundary approved");
        assertThat(ledger.coverage(b2Maker).status()).isEqualTo("REVIEWED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounting_journal WHERE sacco_id=? AND station_id='B2'",Integer.class,institution)).isZero();
    }
    @Test void sourceLookupAndReversalRequireSameTransactionScopeAndIndependentSourceEvidence() {
        assertThatThrownBy(()->ledger.sourceAccount(maker,"1000")).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        AccountView cash=tx.execute(s->ledger.sourceAccount(maker,"1000"));assertThat(cash.category()).isEqualTo("CASH");
        SourceJournalCommand original=source(UUID.randomUUID(),"SOURCE-REVERSAL",checker.getMemberId());UUID id=tx.execute(s->ledger.postSource(maker,original));
        assertThatThrownBy(()->ledger.sourceJournalId(checker,"OWNER_CAPITAL",original.sourceReference())).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        UUID found=tx.execute(s->ledger.sourceJournalId(checker,"OWNER_CAPITAL",original.sourceReference()));assertThat(found).isEqualTo(id);
        assertThatThrownBy(()->tx.execute(s->ledger.sourceJournalId(operator("B2",Position.ACCOUNTANT),"OWNER_CAPITAL",original.sourceReference()))).hasMessageContaining("source");
        SourceJournalCommand correction=new SourceJournalCommand(UUID.randomUUID(),TODAY,"REVERSAL","OWNER_CAPITAL_REVERSAL","REV-"+original.sourceReference(),"SYNTHETIC correction","TEST-CORRECTION",checker.getMemberId(),third.getMemberId(),List.of());
        UUID reversal=tx.execute(s->ledger.reverseSource(third,id,correction));assertThat(ledger.view(maker,reversal).reversesJournalId()).isEqualTo(id);
        assertThat(ledger.view(maker,id).state()).isEqualTo("REVERSED");UUID retry=tx.execute(s->ledger.reverseSource(third,id,correction));assertThat(retry).isEqualTo(reversal);
    }
    @Test void concurrentDuplicateSourcesCommitOnce() throws Exception {
        SourceJournalCommand c=source(UUID.randomUUID(),"RACE",checker.getMemberId());CountDownLatch start=new CountDownLatch(1);
        try(ExecutorService executor=Executors.newFixedThreadPool(2)) {
            Callable<UUID> post=()->{start.await();return tx.execute(s->ledger.postSource(maker,c));};Future<UUID> a=executor.submit(post),b=executor.submit(post);start.countDown();
            assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounting_journal WHERE sacco_id=? AND source_reference='RACE'",Integer.class,institution)).isEqualTo(1);
    }
    @Test void closeWinsAgainstWaitingPostAndDatabaseRejectsUnbalancedApproval() throws Exception {
        UUID id=ledger.draft(maker,command("3.00"));ledger.approve(checker,id,"TEST");CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1);
        try(ExecutorService executor=Executors.newFixedThreadPool(2)) {
            Future<?> close=executor.submit(()->tx.execute(s->{jdbc.queryForObject("SELECT id FROM accounting_period WHERE id=? FOR UPDATE",UUID.class,period);locked.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}jdbc.update("UPDATE accounting_period SET state='CLOSED',closed_by=?,closed_at=now(),closing_evidence='SYNTHETIC CLOSE' WHERE id=?",checker.getMemberId(),period);return null;}));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();Future<?> post=executor.submit(()->ledger.post(maker,id));release.countDown();close.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(()->post.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(LedgerException.class);
        }
        assertThat(ledger.view(maker,id).state()).isEqualTo("APPROVED");
    }
    @Test void databaseDeferredBalanceAndClosedPeriodDefendBypassedService() {
        UUID id=ledger.draft(maker,command("5.00"));
        assertThatThrownBy(()->tx.execute(s->{jdbc.update("INSERT INTO accounting_journal_line(id,journal_id,sacco_id,account_id,line_number,debit,credit,memo) SELECT ?,?,?,account_id,3,1,0,'bypassed' FROM accounting_journal_line WHERE journal_id=? AND line_number=1",UUID.randomUUID(),id,institution,id);jdbc.update("UPDATE accounting_journal SET state='APPROVED',approved_by=?,approved_at=now() WHERE id=?",checker.getMemberId(),id);return null;})).isInstanceOf(org.springframework.transaction.TransactionSystemException.class).rootCause().hasMessageContaining("Approved and posted journals require 2-200 balanced lines");
        ledger.approve(checker,id,"TEST");jdbc.update("UPDATE accounting_period SET state='CLOSED',closed_by=?,closed_at=now(),closing_evidence='SYNTHETIC CLOSE' WHERE id=?",checker.getMemberId(),period);
        assertThatThrownBy(()->jdbc.update("UPDATE accounting_journal SET state='POSTED',posted_by=?,posted_at=now() WHERE id=?",maker.getMemberId(),id)).isInstanceOf(DataAccessException.class);
    }
    private AppUserPrincipal operator(String branch,Position position) {
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) VALUES(?,?,?,?,'Synthetic operator','ACTIVE',?,now(),true,'not-a-password')",id,institution,branch,id.toString(),position.name());
        return new AppUserPrincipal(Member.builder().id(id).saccoId(institution).stationId(branch).memberNo(id.toString()).position(position).status(MemberStatus.ACTIVE).memberAccount(true).build(),EnumSet.allOf(UserClaim.class),true);
    }
    private JournalCommand command(String amount){return new JournalCommand(UUID.randomUUID(),TODAY,"Synthetic manual entry","TEST-EVIDENCE",lines(amount));}
    private List<LineCommand> lines(String amount){return List.of(line("1000",amount,"0"),line("3000","0",amount));}
    private LineCommand line(String code,String debit,String credit){return new LineCommand(code,new BigDecimal(debit),new BigDecimal(credit),"");}
    private SourceJournalCommand source(UUID key,String reference,UUID checkerId){return new SourceJournalCommand(key,TODAY,"CAPITAL","OWNER_CAPITAL",reference,"Synthetic capital","TEST-CAPITAL",maker.getMemberId(),checkerId,lines("10.00"));}
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement
    static class Config {
        @Bean DataSource source(){return new DriverManagerDataSource(System.getenv("MICROFINANCE_ACCOUNTING_B_TEST_URL")+"?connectTimeout=3&socketTimeout=20","microfinance_test","");}
        @Bean(initMethod="migrate") Flyway flyway(DataSource source){return Flyway.configure().dataSource(source).locations("classpath:db/migration").load();}
        @Bean @DependsOn("flyway") JdbcTemplate jdbc(DataSource source){return new JdbcTemplate(source);}
        @Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}
        @Bean AccountingLedgerRepository repository(JdbcTemplate jdbc){return new AccountingLedgerRepository(jdbc);}
        @Bean AccountingPolicyRepository policies(JdbcTemplate jdbc){return new AccountingPolicyRepository(jdbc);}
        @Bean ObjectMapper mapper(){return JsonMapper.builder().findAndAddModules().build();}
        @Bean AccessControlService access(){return new AccessControlService();}
        @Bean ApplicationClock clock(){ApplicationClock clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(TODAY);when(clock.now()).thenReturn(OffsetDateTime.parse("2026-10-04T12:00:00+03:00"));return clock;}
        @Bean AccountingPolicyService policyService(AccountingPolicyRepository repository,ObjectMapper mapper,AccessControlService access,ApplicationClock clock){return new AccountingPolicyService(repository,mapper,access,clock);}
        @Bean AccountingLedgerService ledger(AccountingLedgerRepository repository,AccountingPolicyService policies,AccessControlService access,ApplicationClock clock){return new AccountingLedgerService(repository,policies,access,clock);}
    }
}
