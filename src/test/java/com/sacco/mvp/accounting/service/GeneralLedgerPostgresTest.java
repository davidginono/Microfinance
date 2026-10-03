package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.*;
import com.sacco.mvp.accounting.policy.*;
import com.sacco.mvp.accounting.policy.AccountingPolicyService.*;
import com.sacco.mvp.accounting.repository.GeneralLedgerRepository;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_ACCOUNTING_B_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_accounting_b_(test|gate_test)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeneralLedgerPostgresTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private GeneralLedgerService service;
    private AccountingPolicyService policies;
    private AuditService audit;
    private UserClaimService claims;
    private MemberDirectoryService directory;
    private SaccoRegistryService institutions;
    private String institution;
    private AppUserPrincipal maker,checker,reverseMaker,reverseChecker;
    private UUID cash,capital,principal,period,policy;
    private static final LocalDate DAY=LocalDate.of(2026,10,1);
    private static final OffsetDateTime NOW=OffsetDateTime.parse("2026-10-02T10:00:00+03:00");

    @BeforeAll void start() {
        var ds=new DriverManagerDataSource(System.getenv("MICROFINANCE_ACCOUNTING_B_DATABASE_URL"),"microfinance_test","");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
        policies=mock(AccountingPolicyService.class);audit=mock(AuditService.class);claims=mock(UserClaimService.class);directory=mock(MemberDirectoryService.class);institutions=mock(SaccoRegistryService.class);
        ApplicationClock clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(DAY.plusDays(1));when(clock.now()).thenReturn(NOW);
        var raw=new GeneralLedgerService(new GeneralLedgerRepository(jdbc),policies,new AccessControlService(),audit,clock,claims,directory,institutions,mock(com.sacco.mvp.reporting.execution.service.AccountingReleaseGateService.class));
        var factory=new ProxyFactory(raw);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        service=(GeneralLedgerService)factory.getProxy();
    }
    @BeforeEach void fixture() {
        reset(policies,audit,claims,directory,institutions);
        institution="GL-"+UUID.randomUUID();jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,?,true,now(),now())",institution,"Synthetic GL test");
        when(institutions.findActiveSacco(institution)).thenReturn(Optional.of(RegisteredSacco.builder().saccoId(institution).active(true).build()));
        when(institutions.findStation(eq(institution),anyString())).thenAnswer(i->Optional.of(SaccoStation.builder()
            .saccoId(institution).stationId(i.getArgument(1)).active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));
        maker=operator("B1");checker=operator("B1");reverseMaker=operator("B1");reverseChecker=operator("B1");
        cash=service.createAccount(maker,new AccountCommand("CASH","Verified test cash","ASSET","DEBIT","POSTING","CASH",null));
        capital=service.createAccount(maker,new AccountCommand("CAPITAL","Verified owner capital","EQUITY","CREDIT","POSTING","CAPITAL",null));
        principal=service.createAccount(maker,new AccountCommand("PRINCIPAL","Loan principal control","ASSET","DEBIT","CONTROL","LOAN_PRINCIPAL",null));
        policy=UUID.randomUUID();var decisions=new EnumMap<PolicyDecision,String>(PolicyDecision.class);for(var k:PolicyDecision.values())decisions.put(k,"Synthetic approved evidence only");
        var matrix=new EnumMap<PostingEvent,PostingRule>(PostingEvent.class);for(var k:PostingEvent.values())matrix.put(k,new PostingRule(PostingPermission.ALLOWED,"Synthetic treatment"));
        var mapping=Map.of("LOAN_PRINCIPAL",principal,"DISBURSEMENT_CLEARING",cash,"REPAYMENT_CLEARING",cash,"OWNER_CAPITAL",capital);
        var mapper=JsonMapper.builder().findAndAddModules().build();
        jdbc.update("insert into accounting_policies(id,sacco_id,policy_version,effective_from,opening_date,authoritative_ledger,decisions_json,posting_matrix_json,account_mappings_json,evidence_reference,maker_id,request_key,created_at) values(?,?,1,?,?,'LOCAL_GL',?,?,?,'Synthetic reviewer evidence',?,?,?)",policy,institution,DAY,DAY,mapper.writeValueAsString(decisions),mapper.writeValueAsString(matrix),mapper.writeValueAsString(mapping),maker.getMemberId(),UUID.randomUUID(),NOW);
        jdbc.update("insert into accounting_policy_approvals(policy_id,sacco_id,policy_version,effective_from,checker_id,decision,evidence_reference,reason,decided_at) values(?,?,1,?,?,'APPROVED','Synthetic independent review','Test evidence only',?)",policy,institution,DAY,checker.getMemberId(),NOW);
        var snapshot=new PolicySnapshot(policy,institution,1,DAY,DAY,AuthoritativeLedger.LOCAL_GL,decisions,matrix,mapping,"Synthetic evidence",maker.getMemberId(),Decision.APPROVED,checker.getMemberId(),"Synthetic approval","Test",NOW);
        when(policies.requireApprovedLocalPolicy(eq(institution),any())).thenReturn(snapshot);
        period=service.createPeriod(maker,DAY,DAY.plusMonths(1));
    }
    @Test void reviewedOpeningAndExactRetryAreBalancedAndTraceable() {
        var c=command("OPEN-1","1000.01");var j=service.importOpening(maker,c);
        assertThat(service.importOpening(maker,c).id()).isEqualTo(j.id());
        assertThatThrownBy(()->service.approve(maker,j.id(),"Own evidence")).isInstanceOf(IllegalArgumentException.class);
        service.approve(checker,j.id(),"Independently reconciled all source records");
        assertThatThrownBy(()->service.post(checker,j.id(),false)).isInstanceOf(IllegalArgumentException.class);
        service.post(checker,j.id(),true);service.post(checker,j.id(),true);
        assertThat(jdbc.queryForObject("select count(*) from accounting_outbox where journal_id=?",Integer.class,j.id())).isEqualTo(1);
        assertThat(service.coverage(checker).status()).isEqualTo("REVIEWED_COVERAGE");
        assertThat(service.hasInstitutionHistory(institution)).isTrue();assertThat(service.hasMemberHistory(maker.getMemberId())).isTrue();
        assertThat(jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where journal_id=?",BigDecimal.class,j.id())).isZero();
        assertThat(jdbc.queryForObject("select evidence_reference from gl_cutover_coverage where opening_journal_id=?",String.class,j.id())).isEqualTo("Independently reconciled all source records");
    }
    @Test void manualControlPrecisionHierarchyAndChangedRetryAreRejected() {
        var c=command("MANUAL-1","100.00");var j=service.draftManual(maker,c);
        assertThatThrownBy(()->service.draftManual(maker,new JournalCommand(c.requestKey(),"CHANGED",DAY,"Evidence",null,c.lines()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.draftManual(maker,new JournalCommand(UUID.randomUUID(),"CONTROL",DAY,"Evidence",null,List.of(new Line(principal,new BigDecimal("1.00"),BigDecimal.ZERO),new Line(capital,BigDecimal.ZERO,new BigDecimal("1.00")))))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.previewImport(maker,"CASH,1.001,0\nCAPITAL,0,1.001",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.createAccount(maker,new AccountCommand("BAD","Wrong parent","ASSET","DEBIT","POSTING","OTHER",cash))).isInstanceOf(IllegalArgumentException.class);
        service.approve(checker,j.id(),"Reviewed");assertThatThrownBy(()->service.post(checker,j.id(),false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.journal(checker,j.id()).state()).isEqualTo("APPROVED");
    }
    @Test void concurrentIdenticalRequestsPersistOnce() throws Exception {
        var c=command("RACE","2.01");try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->service.importOpening(maker,c));var b=pool.submit(()->service.importOpening(maker,c));
            assertThat(a.get(15,TimeUnit.SECONDS).id()).isEqualTo(b.get(15,TimeUnit.SECONDS).id());
        }
        assertThat(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,institution)).isEqualTo(1);
    }
    @Test void failedAuditRollsBackJournalStateOutboxAndOpeningCoverage() {
        var j=service.importOpening(maker,command("ROLLBACK","5.00"));service.approve(checker,j.id(),"Reviewed");
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit).logEvent(anyString(),eq(j.id()),eq("JOURNAL_POSTED"),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),any());
        assertThatThrownBy(()->service.post(checker,j.id(),true)).isInstanceOf(IllegalStateException.class);
        assertThat(service.journal(checker,j.id()).state()).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select count(*) from accounting_outbox where journal_id=?",Integer.class,j.id())).isZero();
        assertThat(service.coverage(checker).reviewedOpening()).isFalse();
    }
    @Test void databaseProtectsPostedHistoryPrecisionAndUnbalancedJournals() {
        var j=opening();
        assertThatThrownBy(()->jdbc.update("update gl_journal_line set debit=2 where journal_id=?",j.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("delete from gl_journal where id=?",j.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        var draft=service.draftManual(maker,command("DB-BALANCE","3.00"));
        assertThatThrownBy(()->tx.executeWithoutResult(s->{
            jdbc.update("insert into gl_journal_line(id,journal_id,sacco_id,station_id,account_id,debit,credit) values(?,?,?,'B1',?,0.001,0)",UUID.randomUUID(),draft.id(),institution,cash);
        })).isInstanceOf(org.springframework.dao.DataAccessException.class);
        service.approve(checker,draft.id(),"Reviewed");
        assertThatThrownBy(()->tx.executeWithoutResult(s->jdbc.update("update gl_journal set state='POSTED',posted_at=now() where id=?",draft.id()))).hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
        assertThatThrownBy(()->jdbc.update("insert into gl_journal_line(id,journal_id,sacco_id,station_id,account_id,debit,credit) values(?,?,?,'B1',?,1.00,0)",UUID.randomUUID(),draft.id(),institution,cash)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("delete from gl_account where id=?",cash)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void fourStaffLinkedReversalPreservesOriginalAndNetsToZero() {
        opening();var j=service.draftManual(maker,command("ORIGINAL","10.25"));service.approve(checker,j.id(),"Verified");service.post(checker,j.id(),false);
        assertThatThrownBy(()->service.reverse(maker,j.id(),UUID.randomUUID(),DAY.plusDays(1),"Correction","Evidence")).isInstanceOf(IllegalArgumentException.class);
        var key=UUID.randomUUID();var reversal=service.reverse(reverseMaker,j.id(),key,DAY.plusDays(1),"Verified error","Correction evidence");
        assertThat(service.reverse(reverseMaker,j.id(),key,DAY.plusDays(1),"Verified error","Correction evidence").id()).isEqualTo(reversal.id());
        assertThatThrownBy(()->service.approve(checker,reversal.id(),"Original reviewer")).isInstanceOf(IllegalArgumentException.class);
        service.approve(reverseChecker,reversal.id(),"Independent correction review");service.post(reverseChecker,reversal.id(),false);
        assertThat(service.journal(maker,j.id()).reversed()).isTrue();
        assertThat(jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where journal_id in (?,?) and account_id=?",BigDecimal.class,j.id(),reversal.id(),cash)).isZero();
    }
    @Test void postingSerializesClosingAndUnreviewedCloseRemainsBlocked() throws Exception {
        opening();var j=service.draftManual(maker,command("CLOSE-RACE","9.00"));service.approve(checker,j.id(),"Reviewed");
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var post=pool.submit(()->tx.execute(s->{service.post(checker,j.id(),false);locked.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){throw new IllegalStateException(e);}return true;}));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            var close=pool.submit(()->{try{jdbc.update("update accounting_period set state='CLOSED',closed_by=?,closed_at=now() where id=?",reverseChecker.getMemberId(),period);return true;}catch(org.springframework.dao.DataAccessException expected){return false;}});
            Thread.sleep(150);assertThat(close.isDone()).isFalse();release.countDown();assertThat(post.get(10,TimeUnit.SECONDS)).isTrue();assertThat(close.get(10,TimeUnit.SECONDS)).isFalse();
        }
        assertThat(jdbc.queryForObject("select state from accounting_period where id=?",String.class,period)).isEqualTo("OPEN");
        assertThat(service.journal(maker,j.id()).state()).isEqualTo("POSTED");
    }
    @Test void databaseRejectsUnbalancedPostingEvenWithOutboxAndReviewedOpening() {
        opening();var j=service.draftManual(maker,command("UNBALANCED","3.00"));
        jdbc.update("insert into gl_journal_line(id,journal_id,sacco_id,station_id,account_id,debit,credit) values(?,?,?,'B1',?,1.00,0)",UUID.randomUUID(),j.id(),institution,cash);
        service.approve(checker,j.id(),"Reviewed header");
        assertThatThrownBy(()->service.post(checker,j.id(),false)).hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
        assertThat(service.journal(checker,j.id()).state()).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select count(*) from accounting_outbox where journal_id=?",Integer.class,j.id())).isZero();
    }
    @Test void operationalBridgePreservesSourceAndUsesApprovedMappingsExactlyOnce() {
        opening();UUID loan=UUID.randomUUID(),voucher=UUID.randomUUID();long number=Math.abs(loan.getLeastSignificantBits()%1000000000000000L);
        jdbc.update("insert into loan_applications(id,amount,applicant_member_id,created_at,updated_at,form_data,policy_snapshot,loan_type,required_guarantors,sacco_id,station_id,status,tenor_months,version,application_number,loan_id) values(?,100,?,now(),now(),'{}','{}','DEVELOPMENT_LOAN',0,?,'B1','DISBURSED',1,0,?,?)",loan,maker.getMemberId(),institution,number,Long.toString(number));
        jdbc.update("insert into loan_ledgers(loan_application_id,sacco_id,station_id,loan_id,applicant_member_id,disbursement_date,principal,created_at) values(?,?,'B1',?,?,?,100,now())",loan,institution,Long.toString(number),maker.getMemberId(),DAY.plusDays(1));
        tx.executeWithoutResult(s->{
            jdbc.update("insert into loan_journal_entries(id,loan_application_id,voucher_id,account_code,debit,credit,effective_date,posted_at) values(?,?,?,'LOAN_PRINCIPAL',100,0,?,now())",UUID.randomUUID(),loan,voucher,DAY.plusDays(1));
            jdbc.update("insert into loan_journal_entries(id,loan_application_id,voucher_id,account_code,debit,credit,effective_date,posted_at) values(?,?,?,'DISBURSEMENT_CLEARING',0,100,?,now())",UUID.randomUUID(),loan,voucher,DAY.plusDays(1));
        });
        assertThat(service.coverage(maker).unbridgedOperationalVouchers()).isEqualTo(1);
        UUID key=UUID.randomUUID();var j=service.bridgeOperationalVoucher(maker,voucher,key,"Verified source voucher and mapping");
        assertThat(service.bridgeOperationalVoucher(maker,voucher,key,"Verified source voucher and mapping").id()).isEqualTo(j.id());
        service.approve(checker,j.id(),"Independent bridge reconciliation");service.post(checker,j.id(),false);
        assertThat(service.coverage(maker).unbridgedOperationalVouchers()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from loan_journal_entries where voucher_id=?",Integer.class,voucher)).isEqualTo(2);
        assertThatThrownBy(()->service.reverse(reverseMaker,j.id(),UUID.randomUUID(),DAY.plusDays(1),"Invalid GL-only reversal","Evidence")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void sourcePostingRequiresOwningTransactionAndCannotUseGenericEndpoints() {
        opening();var c=command("CAPITAL-SOURCE","12.01");
        var j=tx.execute(s->service.draftSourceEvent(maker,PostingEvent.CAPITAL,c));
        assertThatThrownBy(()->service.approve(checker,j.id(),"Generic approval")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.post(checker,j.id(),false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.approveAndPostSourceEvent(checker,j.id(),PostingEvent.CAPITAL,c.sourceReference(),"Evidence")).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(()->tx.execute(s->service.approveAndPostSourceEvent(checker,j.id(),PostingEvent.EXPENSE,c.sourceReference(),"Evidence"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->tx.execute(s->{service.approveAndPostSourceEvent(checker,j.id(),PostingEvent.CAPITAL,c.sourceReference(),"Independent source review");throw new IllegalStateException("Source subledger update failed");})).isInstanceOf(IllegalStateException.class);
        assertThat(service.journal(maker,j.id()).state()).isEqualTo("DRAFT");
        var posted=tx.execute(s->service.approveAndPostSourceEvent(checker,j.id(),PostingEvent.CAPITAL,c.sourceReference(),"Independent source review"));
        assertThat(posted.state()).isEqualTo("POSTED");
        assertThat(tx.execute(s->service.approveAndPostSourceEvent(checker,j.id(),PostingEvent.CAPITAL,c.sourceReference(),"Independent source review")).id()).isEqualTo(j.id());
    }
    @Test void revokedPermissionAndMovedBranchCannotUseSessionClaims() {
        var foreign=operator("B2");var j=service.importOpening(maker,command("SCOPE","6.00"));
        assertThatThrownBy(()->service.journal(foreign,j.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of());
        assertThatThrownBy(()->service.accounts(maker,0)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    private Journal opening() {var j=service.importOpening(maker,command("OPENING-"+UUID.randomUUID(),"100.00"));service.approve(checker,j.id(),"Independent verified source reconciliation");return service.post(checker,j.id(),true);}
    private JournalCommand command(String reference,String amount) {var a=new BigDecimal(amount);return new JournalCommand(UUID.randomUUID(),reference,reference.startsWith("OPEN") || reference.equals("SCOPE") || reference.equals("RACE") || reference.equals("ROLLBACK")?DAY:DAY.plusDays(1),"Synthetic source evidence",null,List.of(new Line(cash,a,BigDecimal.ZERO),new Line(capital,BigDecimal.ZERO,a)));}
    private AppUserPrincipal operator(String branch) {
        UUID id=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,?,?,?,'ACTIVE','MANAGER',now(),false,'test-only')",id,institution,branch,id.toString(),"Synthetic accounting staff");
        var m=Member.builder().id(id).saccoId(institution).stationId(branch).memberNo(id.toString()).fullName("Synthetic staff").position(Position.MANAGER).status(MemberStatus.ACTIVE).build();
        when(directory.find(id)).thenReturn(Optional.of(m));return new AppUserPrincipal(m,EnumSet.allOf(UserClaim.class),true);
    }
}
