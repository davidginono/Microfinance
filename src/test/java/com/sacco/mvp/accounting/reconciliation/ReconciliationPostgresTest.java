package com.sacco.mvp.accounting.reconciliation;

import com.sacco.mvp.accounting.reconciliation.ReconciliationDtos.*;
import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.AccountCommand;
import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.JournalCommand;
import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.Line;
import com.sacco.mvp.accounting.policy.*;
import com.sacco.mvp.accounting.policy.AccountingPolicyService.*;
import com.sacco.mvp.accounting.repository.GeneralLedgerRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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

@EnabledIfEnvironmentVariable(named="MICROFINANCE_ACCOUNTING_D_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_accounting_d_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReconciliationPostgresTest {
    private JdbcTemplate jdbc;private TransactionTemplate tx;private GeneralLedgerService gl;private ReconciliationService service;
    private AccountingPolicyService policies;private AuditService audit;private UserClaimService claims;private MemberDirectoryService directory;
    private SaccoRegistryService institutions;
    private ApplicationClock clock;
    private PeriodReopenListener reopenListener;
    private HikariDataSource testPool;
    private String institution;private AppUserPrincipal maker,checker,third,fourth;private UUID bank,capital,period,policy,format;
    private static final LocalDate DAY=LocalDate.of(2026,10,1);private static final OffsetDateTime NOW=OffsetDateTime.parse("2026-10-02T10:00:00+03:00");
    @BeforeAll void start() {
        var poolConfig=new HikariConfig();poolConfig.setJdbcUrl(System.getenv("MICROFINANCE_ACCOUNTING_D_DATABASE_URL")+"?sslmode=disable&connectTimeout=5&socketTimeout=30");poolConfig.setUsername("microfinance_test");poolConfig.setPassword("");poolConfig.setMaximumPoolSize(4);poolConfig.setMinimumIdle(1);poolConfig.setConnectionTimeout(5000);poolConfig.setValidationTimeout(2000);testPool=new HikariDataSource(poolConfig);var ds=testPool;Flyway.configure().dataSource(ds).locations("classpath:db/migration").outOfOrder(true).load().migrate();
        jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);policies=mock(AccountingPolicyService.class);audit=mock(AuditService.class);claims=mock(UserClaimService.class);directory=mock(MemberDirectoryService.class);institutions=mock(SaccoRegistryService.class);
        clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(DAY.plusDays(1));when(clock.now()).thenReturn(NOW);
        gl=proxy(new GeneralLedgerService(new GeneralLedgerRepository(jdbc),policies,new AccessControlService(),audit,clock,claims,directory,institutions),manager);
        reopenListener=mock(PeriodReopenListener.class);service=proxy(new ReconciliationService(new ReconciliationRepository(jdbc),policies,new AccessControlService(),directory,claims,audit,clock,Optional.empty(),List.of(reopenListener)),manager);
    }
    @AfterAll void closePool(){if(testPool!=null)testPool.close();}
    private static <T> T proxy(T raw,DataSourceTransactionManager manager) {var f=new ProxyFactory(raw);f.setProxyTargetClass(true);f.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (T)f.getProxy();}
    @BeforeEach void fixture() {
        reset(policies,audit,claims,directory,institutions,reopenListener);when(clock.today()).thenReturn(DAY.plusDays(1));when(clock.now()).thenReturn(NOW);institution="D-"+UUID.randomUUID();jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,?,true,now(),now())",institution,"Synthetic reconciliation test");
        when(institutions.findActiveSacco(institution)).thenReturn(Optional.of(RegisteredSacco.builder().saccoId(institution).active(true).build()));when(institutions.findStation(eq(institution),anyString())).thenAnswer(i->Optional.of(SaccoStation.builder().saccoId(institution).stationId(i.getArgument(1)).active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        station("B1");when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));maker=operator("B1");checker=operator("B1");third=operator("B1");fourth=operator("B1");
        bank=gl.createAccount(maker,new AccountCommand("BANK","Verified synthetic bank","ASSET","DEBIT","POSTING","BANK",null));capital=gl.createAccount(maker,new AccountCommand("CAPITAL","Synthetic owner capital","EQUITY","CREDIT","POSTING","CAPITAL",null));
        policy=UUID.randomUUID();var decisions=new EnumMap<PolicyDecision,String>(PolicyDecision.class);for(var k:PolicyDecision.values())decisions.put(k,"Synthetic evidence only");var matrix=new EnumMap<PostingEvent,PostingRule>(PostingEvent.class);for(var k:PostingEvent.values())matrix.put(k,new PostingRule(PostingPermission.ALLOWED,"Synthetic treatment"));var mapping=Map.of("DISBURSEMENT_CLEARING",bank,"REPAYMENT_CLEARING",bank,"OWNER_CAPITAL",capital);var json=JsonMapper.builder().findAndAddModules().build();
        jdbc.update("insert into accounting_policies(id,sacco_id,policy_version,effective_from,opening_date,authoritative_ledger,decisions_json,posting_matrix_json,account_mappings_json,evidence_reference,maker_id,request_key,created_at) values(?,?,1,?,?,'LOCAL_GL',?,?,?,'Synthetic reviewer evidence',?,?,?)",policy,institution,DAY,DAY,json.writeValueAsString(decisions),json.writeValueAsString(matrix),json.writeValueAsString(mapping),maker.getMemberId(),UUID.randomUUID(),NOW);
        jdbc.update("insert into accounting_policy_approvals(policy_id,sacco_id,policy_version,effective_from,checker_id,decision,evidence_reference,reason,decided_at) values(?,?,1,?,?,'APPROVED','Synthetic independent review','Test evidence only',?)",policy,institution,DAY,checker.getMemberId(),NOW);
        var snapshot=new PolicySnapshot(policy,institution,1,DAY,DAY,AuthoritativeLedger.LOCAL_GL,decisions,matrix,mapping,"Synthetic evidence",maker.getMemberId(),Decision.APPROVED,checker.getMemberId(),"Synthetic approval","Test",NOW);when(policies.requireApprovedLocalPolicy(eq(institution),any())).thenReturn(snapshot);
        period=gl.createPeriod(maker,DAY,DAY.plusDays(1));format=service.proposeFormat(maker,"Verified bank CSV", "Synthetic approved format evidence");service.approveFormat(checker,format,"Independent synthetic format review");
    }
    private void station(String branch) {jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,created_at,updated_at,access_status) values(?,?,?,true,now(),now(),'ACTIVE')",UUID.randomUUID(),institution,branch);}
    private AppUserPrincipal operator(String branch) {UUID id=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,staff_no,full_name,status,position,created_at,is_member,staff_access_status,password_hash) values(?,?,?,?,?,?,'ACTIVE','MANAGER',now(),false,'ACTIVE','test-only')",id,institution,branch,id.toString(),id.toString(),"Synthetic staff");var m=Member.builder().id(id).saccoId(institution).stationId(branch).memberNo(id.toString()).staffNo(id.toString()).fullName("Synthetic staff").position(Position.MANAGER).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build();when(directory.find(id)).thenReturn(Optional.of(m));return new AppUserPrincipal(m,EnumSet.allOf(UserClaim.class),true);}
    private UUID post(String ref,String amount,boolean opening) {var a=new BigDecimal(amount);var c=new JournalCommand(UUID.randomUUID(),ref,opening?DAY:DAY.plusDays(1),"Synthetic verified movement",null,List.of(new Line(bank,a,BigDecimal.ZERO),new Line(capital,BigDecimal.ZERO,a)));var j=opening?gl.importOpening(maker,c):gl.draftManual(maker,c);gl.approve(checker,j.id(),"Independent synthetic posting review");return gl.post(checker,j.id(),opening).id();}
    private UUID opening() {return post("OPENING","100.00",true);}
    private StatementCommand statementCommand(String csv,String close) {return new StatementCommand(UUID.randomUUID(),bank,format,DAY.plusDays(1),DAY.plusDays(1),new BigDecimal("100.00"),new BigDecimal(close),"verified.csv","Synthetic statement evidence",csv);}
    private UUID importOne(String ref,String amount) {return service.importStatement(maker,statementCommand("date,reference,amount,kind\n2026-10-02,"+ref+","+amount+",RECEIPT",new BigDecimal("100.00").add(new BigDecimal(amount)).toPlainString()));}
    private UUID journalLine(UUID journal) {return jdbc.queryForObject("select id from gl_journal_line where journal_id=? and account_id=?",UUID.class,journal,bank);}
    private UUID outgoing(String reference,String value) {
        BigDecimal amount=new BigDecimal(value);UUID id=gl.draftManual(maker,new JournalCommand(UUID.randomUUID(),reference,DAY.plusDays(1),"Synthetic outgoing movement",null,List.of(new Line(bank,BigDecimal.ZERO,amount),new Line(capital,amount,BigDecimal.ZERO)))).id();gl.approve(checker,id,"Independent outgoing review");return gl.post(checker,id,false).id();
    }
    private UUID statementLine(UUID statement) {return service.rows(maker,statement,0).rows().getFirst().id();}
    private void certify(BigDecimal amount) {var id=service.certify(maker,bank,DAY.plusDays(1),"STATEMENT",amount,"Verified statement reconciliation");service.reviewCertificate(checker,id,"Independent reconciliation evidence");}

    @Test void approvedFormatsIdempotentImportsAndAtomicProvenance() {
        var unapproved=service.proposeFormat(maker,"Unapproved source","Synthetic");var c=statementCommand("date,reference,amount,kind\n2026-10-02,R,1.01,RECEIPT","101.01");
        assertThatThrownBy(()->service.importStatement(maker,new StatementCommand(c.key(),bank,unapproved,c.from(),c.through(),c.opening(),c.closing(),c.filename(),c.evidence(),c.content()))).hasMessage("reconciliation.error.formatApproval");
        UUID id=service.importStatement(maker,c);assertThat(service.importStatement(maker,c)).isEqualTo(id);assertThat(service.statement(maker,id).checksum()).hasSize(64);
        assertThatThrownBy(()->service.importStatement(maker,new StatementCommand(c.key(),bank,format,c.from(),c.through(),c.opening(),c.closing(),"changed.csv",c.evidence(),c.content()))).hasMessage("reconciliation.error.changedRetry");
        assertThatThrownBy(()->jdbc.update("update reconciliation_statement_line set amount=2 where statement_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("insert into reconciliation_statement_line(id,statement_id,row_number,effective_date,reference,amount,kind,duplicate) values(?,?,2,?,'FAKE',1,'RECEIPT',false)",UUID.randomUUID(),id,DAY.plusDays(1))).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void partialAndSplitMatchesIndependentReviewAndNoDoubleCount() {
        opening();UUID j=post("RECEIPT","10.01",false),s=importOne("RECEIPT","10.01");UUID sl=statementLine(s),jl=journalLine(j);
        UUID a=service.proposeMatch(maker,"SPLIT","Partial source evidence",List.of(new Allocation(sl,jl,new BigDecimal("4.00"))));
        assertThatThrownBy(()->service.reviewMatch(maker,a,true,"Own evidence")).hasMessage("reconciliation.error.independentReview");service.reviewMatch(checker,a,true,"Independent partial review");assertThat(service.rows(maker,s,0).rows().getFirst().status()).isEqualTo("PARTIAL");
        assertThatThrownBy(()->service.proposeMatch(maker,"SPLIT","Too much",List.of(new Allocation(sl,jl,new BigDecimal("6.02"))))).hasMessage("reconciliation.error.overmatch");
        UUID b=service.proposeMatch(maker,"SPLIT","Remaining exact cents",List.of(new Allocation(sl,jl,new BigDecimal("6.01"))));service.reviewMatch(checker,b,true,"Remaining review");assertThat(service.rows(maker,s,0).rows().getFirst().matched()).isEqualByComparingTo("10.01");
        assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where sacco_id=?",Integer.class,institution)).isZero();
    }
    @Test void concurrentReviewsCannotOverallocateAndAuditFailureRollsBack() throws Exception {
        opening();UUID j=post("RACE","10.00",false),s=importOne("RACE","10.00"),sl=statementLine(s),jl=journalLine(j);
        UUID a=service.proposeMatch(maker,"EXACT","First proposal",List.of(new Allocation(sl,jl,new BigDecimal("10.00"))));UUID b=service.proposeMatch(maker,"EXACT","Second proposal",List.of(new Allocation(sl,jl,new BigDecimal("10.00"))));
        try(var pool=Executors.newFixedThreadPool(2)) {var futures=List.of(pool.submit(()->attempt(a)),pool.submit(()->attempt(b)));assertThat(futures.get(0).get(15,TimeUnit.SECONDS)+futures.get(1).get(15,TimeUnit.SECONDS)).isEqualTo(1);}
        assertThat(service.rows(maker,s,0).rows().getFirst().matched()).isEqualByComparingTo("10.00");
        UUID ex=service.assignException(maker,sl,"TIMING",checker.getMemberId(),"Timing evidence");doThrow(new IllegalStateException("Synthetic audit failure")).when(audit).logEvent(anyString(),eq(ex),eq("EXCEPTION_DIFFERENCE_REVIEWED"),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),any());
        assertThatThrownBy(()->service.reviewException(checker,ex,"Reviewed timing")).isInstanceOf(IllegalStateException.class);assertThat(jdbc.queryForObject("select count(*) from reconciliation_exception_decision where exception_id=?",Integer.class,ex)).isZero();
    }
    private int attempt(UUID id) {try{service.reviewMatch(checker,id,true,"Concurrent independent evidence");return 1;}catch(IllegalArgumentException e){return 0;}}
    @Test void matchingReversalPreservesEvidenceAndReleasesCapacity() {
        opening();UUID j=post("REV","3.01",false),s=importOne("REV","3.01");UUID m=service.proposeMatch(maker,"EXACT","Evidence",List.of(new Allocation(statementLine(s),journalLine(j),new BigDecimal("3.01"))));service.reviewMatch(checker,m,true,"Independent");
        assertThatThrownBy(()->service.reverseMatch(checker,m,"Correction")).hasMessage("reconciliation.error.independentReview");UUID r=service.reverseMatch(third,m,"Correction evidence");service.reviewMatch(fourth,r,true,"Independent reversal review");assertThat(service.rows(maker,s,0).rows().getFirst().matched()).isZero();assertThat(jdbc.queryForObject("select count(*) from reconciliation_match where sacco_id=?",Integer.class,institution)).isEqualTo(2);
    }
    @Test void closeSnapshotsReopeningAndLateCorrectionRemainVersioned() {
        opening();UUID j=post("CLOSE","10.00",false),s=importOne("CLOSE","10.00");UUID m=service.proposeMatch(maker,"EXACT","Source",List.of(new Allocation(statementLine(s),journalLine(j),new BigDecimal("10.00"))));service.reviewMatch(checker,m,true,"Independent");certify(new BigDecimal("110.00"));
        assertThat(service.closeChecks(maker,period)).allMatch(c->c.blockers()==0);UUID review=service.proposeClose(maker,period,"Prepared branch close",false);service.approveClose(checker,review,"Independent branch close");service.completeInstitutionClose(checker,period,"All branches reviewed");var original=service.finalizedSnapshot(maker,review);assertThat(original.periodClosed()).isTrue();
        var frozen=JsonMapper.builder().findAndAddModules().build().readTree(original.snapshot());
        assertThat(frozen.get("accounts").size()).isEqualTo(2);
        for(var row:frozen.get("accounts"))if(row.get("code").asText().equals("BANK")) {
            assertThat(row.get("opening").decimalValue()).isEqualByComparingTo("100.00");assertThat(row.get("period_debit").decimalValue()).isEqualByComparingTo("10.00");assertThat(row.get("closing").decimalValue()).isEqualByComparingTo("110.00");
        }
        assertThat(frozen.get("cashMovements").get(0).get("counterpart_movement").decimalValue()).isEqualByComparingTo("-10.00");
        assertThat(frozen.get("cashTransfers").get("ambiguous_journals").asLong()).isZero();assertThat(frozen.get("cashTransfers").get("noncash_pairs_possible").asLong()).isZero();
        var openingEvidence=service.reviewedOpeningEvidence(maker);assertThat(openingEvidence.policy()).isEqualTo(policy);assertThat(openingEvidence.reviewer()).isEqualTo(checker.getMemberId());assertThat(openingEvidence.payloadChecksum()).hasSize(64);assertThat(openingEvidence.reviewedAt()).isNotNull();assertThat(frozen.get("reviewedOpening").get("id").asText()).isEqualTo(openingEvidence.id().toString());
        assertThatThrownBy(()->service.finalizedSnapshotForPublication(maker,review)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThat(tx.execute(status->service.finalizedSnapshotForPublication(maker,review)).periodClosed()).isTrue();
        assertThatThrownBy(()->post("BACKDATED","1.00",false)).hasMessage("accounting.error.openPeriodRequired");UUID reopen=service.proposeClose(third,period,"Verified late correction",true);service.approveClose(fourth,reopen,"Independent controlled reopen");assertThat(service.finalizedSnapshot(maker,review).periodClosed()).isFalse();assertThat(service.finalizedSnapshot(maker,review).snapshot()).isEqualTo(original.snapshot());assertThat(service.finalizedSnapshot(maker,review).restatement()).isTrue();
        verify(reopenListener).periodReopened(eq(fourth),eq(period),contains("Verified late correction"));
        post("LATE","1.00",false);assertThat(service.closeChecks(maker,period)).anyMatch(c->c.blockers()>0);assertThatThrownBy(()->service.proposeClose(maker,period,"Cannot reuse stale evidence",false)).hasMessage("reconciliation.error.closeBlocked");
        UUID late=jdbc.queryForObject("select id from gl_journal where sacco_id=? and source_reference='LATE'",UUID.class,institution),lateStatement=service.importStatement(maker,new StatementCommand(UUID.randomUUID(),bank,format,DAY.plusDays(1),DAY.plusDays(1),new BigDecimal("110.00"),new BigDecimal("111.00"),"late.csv","Correction evidence","date,reference,amount,kind\n2026-10-02,LATE,1.00,RECEIPT"));
        UUID lateMatch=service.proposeMatch(maker,"EXACT","Correction matching",List.of(new Allocation(statementLine(lateStatement),journalLine(late),BigDecimal.ONE)));service.reviewMatch(checker,lateMatch,true,"Correction independent review");certify(new BigDecimal("111.00"));when(clock.now()).thenReturn(NOW.plusMinutes(1));
        UUID revised=service.proposeClose(maker,period,"Restated close",false);service.approveClose(checker,revised,"Independent restatement review");service.completeInstitutionClose(checker,period,"Reviewed corrected branches");
        assertThat(service.finalizedSnapshot(maker,review).periodClosed()).isFalse();assertThat(service.finalizedSnapshot(maker,review).snapshot()).isEqualTo(original.snapshot());assertThatThrownBy(()->tx.execute(status->service.finalizedSnapshotForPublication(maker,review))).hasMessage("reconciliation.error.approvalRequired");assertThat(tx.execute(status->service.finalizedSnapshotForPublication(maker,revised)).periodClosed()).isTrue();
    }
    @Test void staleCloseAndMissingBranchCoverageFailAndFreshScopeIsRequired() {
        opening();UUID s=importOne("NO_MATCH","1.00");assertThatThrownBy(()->service.proposeClose(maker,period,"Blocked",false)).hasMessage("reconciliation.error.closeBlocked");
        station("B2");var foreign=operator("B2");assertThatThrownBy(()->service.statement(foreign,s)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of());assertThatThrownBy(()->service.statements(maker,0)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));jdbc.update("update sacco_stations set active=false where sacco_id=? and station_id='B1'",institution);assertThatThrownBy(()->service.statements(maker,0)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void databaseRejectsUnauditedPeriodLockAndSelfReview() {
        assertThatThrownBy(()->jdbc.update("update accounting_period set state='CLOSED',closed_by=?,closed_at=now() where id=?",checker.getMemberId(),period)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        UUID s=importOne("UNMATCHED","1.00");UUID ex=service.assignException(maker,statementLine(s),"UNMATCHED",checker.getMemberId(),"Source not located");assertThatThrownBy(()->jdbc.update("insert into reconciliation_exception_decision(exception_id,checker_id,evidence,decided_at) values(?,?,'Own review',now())",ex,maker.getMemberId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void closingAndNewPostingCannotSlipAcrossThePeriodLock() throws Exception {
        opening();UUID j=post("LOCK","10.00",false),s=importOne("LOCK","10.00");UUID match=service.proposeMatch(maker,"EXACT","Verified",List.of(new Allocation(statementLine(s),journalLine(j),new BigDecimal("10.00"))));service.reviewMatch(checker,match,true,"Independent");certify(new BigDecimal("110.00"));UUID review=service.proposeClose(maker,period,"Branch prepared",false);service.approveClose(checker,review,"Independent branch close");
        var closed=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var closing=pool.submit(()->tx.executeWithoutResult(status->{service.completeInstitutionClose(checker,period,"All branches reviewed");closed.countDown();try{if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Race barrier timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}));
            assertThat(closed.await(20,TimeUnit.SECONDS)).isTrue();var posting=pool.submit(()->post("LATE-RACE","1.00",false));
            try {assertThatThrownBy(()->posting.get(250,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}finally{release.countDown();}
            closing.get(20,TimeUnit.SECONDS);assertThatThrownBy(()->posting.get(20,TimeUnit.SECONDS)).hasRootCauseInstanceOf(IllegalArgumentException.class);
        }finally{release.countDown();}
        assertThat(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=? and source_reference='LATE-RACE'",Integer.class,institution)).isZero();assertThat(service.finalizedSnapshot(maker,review).periodClosed()).isTrue();
    }
    @Test void missingBusinessControlSourceIsUnknownAndCannotCertifyOrClose() {
        opening();UUID payable=gl.createAccount(maker,new AccountCommand("PAYABLE","Supplier control","LIABILITY","CREDIT","CONTROL","PAYABLE",null));
        assertThatThrownBy(()->service.certify(maker,payable,DAY.plusDays(1),"SUPPLIER",BigDecimal.ZERO,"External statement alone")).hasMessage("reconciliation.error.controlUnavailable");assertThat(service.closeChecks(maker,period)).anyMatch(c->c.key().equals("accountEvidence")&&c.blockers()>0);
        assertThatThrownBy(()->service.certify(maker,payable,DAY.plusDays(1),"OPENING",BigDecimal.ZERO,"Cannot substitute manual evidence")).hasMessage("reconciliation.error.certificateKind");
        assertThat(service.hasInstitutionHistory(institution)).isTrue();assertThat(service.hasMemberHistory(checker.getMemberId())).isTrue();
    }
    @Test void publicationHoldsPeriodLockUntilResultCommit() throws Exception {
        opening();UUID s=service.importStatement(maker,statementCommand("date,reference,amount,kind\n2026-10-02,TIMING,1.00,RECEIPT","101.00"));UUID ex=service.assignException(maker,statementLine(s),"TIMING",checker.getMemberId(),"Verified timing difference");service.reviewException(checker,ex,"Independent retained difference");certify(new BigDecimal("101.00"));
        UUID close=service.proposeClose(maker,period,"Close with reviewed difference",false);service.approveClose(checker,close,"Independent close review");service.completeInstitutionClose(checker,period,"All branches reviewed");UUID reopen=service.proposeClose(third,period,"Correction reason",true);
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var publication=pool.submit(()->tx.executeWithoutResult(status->{assertThat(service.finalizedSnapshotForPublication(maker,close).periodClosed()).isTrue();locked.countDown();try{if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Publication race barrier timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}));
            assertThat(locked.await(20,TimeUnit.SECONDS)).isTrue();var reopening=pool.submit(()->service.approveClose(fourth,reopen,"Independent controlled reopen"));
            try{assertThatThrownBy(()->reopening.get(250,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}finally{release.countDown();}
            publication.get(20,TimeUnit.SECONDS);reopening.get(20,TimeUnit.SECONDS);
        }finally{release.countDown();}
        assertThatThrownBy(()->tx.execute(status->service.finalizedSnapshotForPublication(maker,close))).hasMessage("reconciliation.error.approvalRequired");assertThat(service.finalizedSnapshot(maker,close).periodClosed()).isFalse();
    }
    @Test void inactiveHistoricalBranchesAndAccountsRemainClosingObligations() {
        opening();jdbc.update("update gl_account set active=false where id=?",bank);assertThat(service.closeChecks(maker,period)).anyMatch(c->c.key().equals("accountEvidence")&&c.blockers()>0);jdbc.update("update gl_account set active=true where id=?",bank);
        UUID s=importOne("TIMING","1.00"),ex=service.assignException(maker,statementLine(s),"TIMING",checker.getMemberId(),"Evidence");service.reviewException(checker,ex,"Reviewed difference");certify(new BigDecimal("101.00"));UUID close=service.proposeClose(maker,period,"Prepared close",false);service.approveClose(checker,close,"Independent review");
        station("B2");var b2=operator("B2");service.importStatement(b2,new StatementCommand(UUID.randomUUID(),bank,format,DAY.plusDays(1),DAY.plusDays(1),BigDecimal.ZERO,BigDecimal.ONE,"b2.csv","Historical source evidence","date,reference,amount,kind\n2026-10-02,HISTORY,1.00,RECEIPT"));jdbc.update("update sacco_stations set active=false where sacco_id=? and station_id='B2'",institution);
        assertThatThrownBy(()->service.completeInstitutionClose(checker,period,"Cannot omit historical branch")).hasMessage("reconciliation.error.branchCoverage");assertThatThrownBy(()->jdbc.update("update accounting_period set state='CLOSED',closed_by=?,closed_at=now() where id=?",checker.getMemberId(),period)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void ambiguousCashJournalsAreFrozenAndDownstreamInvalidationIsAtomic() {
        opening();UUID expense=gl.createAccount(maker,new AccountCommand("EXPENSE","Synthetic expense","EXPENSE","DEBIT","POSTING","EXPENSE",null));
        UUID journal=gl.draftManual(maker,new JournalCommand(UUID.randomUUID(),"COMPOUND",DAY.plusDays(1),"Synthetic compound movement",null,List.of(new Line(bank,new BigDecimal("10.00"),BigDecimal.ZERO),new Line(expense,new BigDecimal("5.00"),BigDecimal.ZERO),new Line(capital,BigDecimal.ZERO,new BigDecimal("15.00"))))).id();gl.approve(checker,journal,"Independent synthetic review");gl.post(checker,journal,false);
        UUID s=importOne("COMPOUND","10.00"),match=service.proposeMatch(maker,"EXACT","Evidence",List.of(new Allocation(statementLine(s),journalLine(journal),new BigDecimal("10.00"))));service.reviewMatch(checker,match,true,"Independent evidence");certify(new BigDecimal("110.00"));UUID close=service.proposeClose(maker,period,"Prepared compound close",false);service.approveClose(checker,close,"Independent close");service.completeInstitutionClose(checker,period,"All branches reviewed");
        var frozen=JsonMapper.builder().findAndAddModules().build().readTree(service.finalizedSnapshot(maker,close).snapshot());assertThat(frozen.get("cashTransfers").get("ambiguous_journals").asLong()).isEqualTo(1);assertThat(frozen.get("cashTransfers").get("noncash_pairs_possible").asLong()).isEqualTo(1);
        UUID reopen=service.proposeClose(third,period,"Verified correction",true);doThrow(new IllegalStateException("Synthetic downstream audit failure")).when(reopenListener).periodReopened(any(),eq(period),anyString());assertThatThrownBy(()->service.approveClose(fourth,reopen,"Independent reopen")).hasMessage("Synthetic downstream audit failure");assertThat(service.finalizedSnapshot(maker,close).periodClosed()).isTrue();assertThat(jdbc.queryForObject("select count(*) from accounting_close_decision where review_id=?",Integer.class,reopen)).isZero();
    }
    @Test void chargesDisbursementsAndSettlementSplitsKeepExplicitTreatmentAndDirection() {
        opening();UUID charge=outgoing("FEE","0.03"),disbursement=outgoing("DISBURSE","10.00"),settlement=outgoing("SETTLE","6.00"),receipt=post("OPPOSITE","10.00",false);
        UUID s=service.importStatement(maker,statementCommand("date,reference,amount,kind\n2026-10-02,FEE,-0.03,CHARGE\n2026-10-02,DISBURSE,-10.00,DISBURSEMENT\n2026-10-02,SETTLE,-6.00,SETTLEMENT","83.97"));var rows=service.rows(maker,s,0).rows();UUID fee=rows.get(0).id(),disb=rows.get(1).id(),settle=rows.get(2).id();
        assertThatThrownBy(()->service.proposeMatch(maker,"SPLIT","Unreviewed charge",List.of(new Allocation(fee,journalLine(charge),new BigDecimal("0.03"))))).hasMessage("reconciliation.error.feeReview");
        assertThatThrownBy(()->service.proposeMatch(maker,"SETTLEMENT","Wrong direction",List.of(new Allocation(disb,journalLine(receipt),BigDecimal.ONE)))).hasMessage("reconciliation.error.direction");
        assertThatThrownBy(()->service.proposeMatch(maker,"EXACT","Implicit settlement",List.of(new Allocation(settle,journalLine(settlement),new BigDecimal("6.00"))))).hasMessage("reconciliation.error.settlementReview");
        UUID feeMatch=service.proposeMatch(maker,"CHARGE","Verified channel fee",List.of(new Allocation(fee,journalLine(charge),new BigDecimal("0.03"))));service.reviewMatch(checker,feeMatch,true,"Independent fee review");
        UUID split=service.proposeMatch(maker,"SPLIT","Disbursement partial",List.of(new Allocation(disb,journalLine(disbursement),new BigDecimal("4.01"))));service.reviewMatch(checker,split,true,"Independent split review");
        UUID remainder=service.proposeMatch(maker,"SPLIT","Disbursement remaining cents",List.of(new Allocation(disb,journalLine(disbursement),new BigDecimal("5.99"))));service.reviewMatch(checker,remainder,true,"Independent remainder review");
        UUID first=service.proposeMatch(maker,"SETTLEMENT","Settlement split",List.of(new Allocation(settle,journalLine(settlement),new BigDecimal("2.01"))));service.reviewMatch(checker,first,true,"Independent settlement review");UUID second=service.proposeMatch(maker,"SETTLEMENT","Settlement remainder",List.of(new Allocation(settle,journalLine(settlement),new BigDecimal("3.99"))));service.reviewMatch(checker,second,true,"Independent settlement remainder");
        assertThat(service.rows(maker,s,0).rows()).allMatch(r->r.status().equals("MATCHED"));assertThatThrownBy(()->service.proposeMatch(maker,"SETTLEMENT","Double-count",List.of(new Allocation(settle,journalLine(settlement),new BigDecimal("0.01"))))).hasMessage("reconciliation.error.overmatch");
    }
    @Test void duplicateBatchesRequireReviewAndRejectedAllocationsNeverConsumeCapacity() {
        opening();UUID j=post("DUP","20.00",false),s=service.importStatement(maker,statementCommand("date,reference,amount,kind\n2026-10-02,DUP,10.00,RECEIPT\n2026-10-02,DUP,10.00,RECEIPT","120.00"));var rows=service.rows(maker,s,0).rows();var first=new Allocation(rows.get(0).id(),journalLine(j),new BigDecimal("10.00"));var second=new Allocation(rows.get(1).id(),journalLine(j),new BigDecimal("10.00"));
        assertThatThrownBy(()->service.proposeMatch(maker,"SPLIT","Duplicate shortcut",List.of(second))).hasMessage("reconciliation.error.duplicateReview");assertThatThrownBy(()->service.proposeMatch(maker,"BATCH","Duplicate pair",List.of(first,first))).hasMessage("reconciliation.error.allocations");
        UUID rejected=service.proposeMatch(maker,"BATCH","Unverified batch",List.of(first,second));service.reviewMatch(checker,rejected,false,"Insufficient source evidence");assertThat(service.rows(maker,s,0).rows()).allMatch(r->r.matched().signum()==0);
        UUID accepted=service.proposeMatch(maker,"BATCH","Explicit duplicate investigation",List.of(first,second));assertThatThrownBy(()->service.reviewMatch(maker,accepted,true,"Self-review")).hasMessage("reconciliation.error.independentReview");service.reviewMatch(checker,accepted,true,"Independent confirmed source occurrences");assertThat(service.rows(maker,s,0).rows()).allMatch(r->r.matched().compareTo(new BigDecimal("10.00"))==0);assertThat(service.rows(maker,s,0).rows().get(1).duplicate()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where sacco_id=?",Integer.class,institution)).isZero();
    }
    @Test void reversingPreviouslyMatchedVoucherRequiresSubsequentLinkedIndependentReview() {
        opening();UUID j=post("REVERSED","3.01",false),s=importOne("REVERSED","3.01"),sl=statementLine(s);UUID match=service.proposeMatch(maker,"EXACT","Original source",List.of(new Allocation(sl,journalLine(j),new BigDecimal("3.01"))));service.reviewMatch(checker,match,true,"Original independent review");UUID timing=service.assignException(maker,sl,"TIMING",checker.getMemberId(),"Earlier timing difference");service.reviewException(checker,timing,"Earlier review");certify(new BigDecimal("103.01"));
        UUID old=service.proposeClose(maker,period,"Original close",false);service.approveClose(checker,old,"Original independent close");service.completeInstitutionClose(checker,period,"Original institution close");String frozen=service.finalizedSnapshot(maker,old).snapshot();UUID reopen=service.proposeClose(third,period,"Verified voucher correction",true);service.approveClose(fourth,reopen,"Independent controlled reopen");
        when(clock.now()).thenReturn(NOW.plusMinutes(1));UUID reversal=gl.reverse(third,j,UUID.randomUUID(),DAY.plusDays(1),"Verified correction","Reversal evidence").id();gl.approve(fourth,reversal,"Independent voucher reversal");gl.post(fourth,reversal,false);
        assertThat(service.rows(maker,s,0).rows().getFirst().status()).isEqualTo("REVERSED");assertThat(service.closeChecks(maker,period)).anyMatch(c->c.key().equals("reversed")&&c.blockers()==1);assertThatThrownBy(()->service.proposeMatch(maker,"EXACT","Old source",List.of(new Allocation(sl,journalLine(j),new BigDecimal("0.01"))))).hasMessage("reconciliation.error.reversalReview");
        UUID linked=service.assignException(maker,sl,"REVERSED",checker.getMemberId(),"Posted reversal linked by statement line");service.reviewException(checker,linked,"Independent subsequent reversal investigation");assertThat(service.closeChecks(maker,period)).anyMatch(c->c.key().equals("reversed")&&c.blockers()==0);assertThat(service.finalizedSnapshot(maker,old).snapshot()).isEqualTo(frozen);
        UUID reverseStatement=service.importStatement(maker,statementCommand("date,reference,amount,kind\n2026-10-02,REVERSAL,-3.01,REVERSAL","96.99"));UUID reverseLine=statementLine(reverseStatement);assertThatThrownBy(()->service.proposeMatch(maker,"SPLIT","Implicit reversal",List.of(new Allocation(reverseLine,journalLine(reversal),new BigDecimal("3.01"))))).hasMessage("reconciliation.error.reversalReview");UUID explicit=service.proposeMatch(maker,"REVERSAL","Posted reversal source",List.of(new Allocation(reverseLine,journalLine(reversal),new BigDecimal("3.01"))));service.reviewMatch(checker,explicit,true,"Independent explicit reversal matching");assertThat(service.rows(maker,reverseStatement,0).rows().getFirst().status()).isEqualTo("REVERSED");
    }
    @Test void statementEffectiveDateBoundsFailWithoutCreatingFinancialEvidence() {
        for(String date:List.of("2026-10-01","2026-10-03"))assertThatThrownBy(()->service.importStatement(maker,statementCommand("date,reference,amount,kind\n"+date+",OUTSIDE,1.00,RECEIPT","101.00"))).hasMessage("reconciliation.error.dates");
        assertThat(jdbc.queryForObject("select count(*) from reconciliation_statement where sacco_id=?",Integer.class,institution)).isZero();assertThat(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,institution)).isZero();
    }
    @Test void laterPeriodVoucherReversalDoesNotRewriteEarlierFinalizedSnapshot() {
        opening();UUID j=post("LATER-REVERSED","2.00",false),s=importOne("LATER-REVERSED","2.00"),match=service.proposeMatch(maker,"EXACT","Original source",List.of(new Allocation(statementLine(s),journalLine(j),new BigDecimal("2.00"))));service.reviewMatch(checker,match,true,"Independent source review");certify(new BigDecimal("102.00"));UUID close=service.proposeClose(maker,period,"Original closed period",false);service.approveClose(checker,close,"Independent period review");service.completeInstitutionClose(checker,period,"Reviewed institution close");var frozen=service.finalizedSnapshot(maker,close);
        gl.createPeriod(maker,DAY.plusDays(2),DAY.plusDays(2));when(clock.today()).thenReturn(DAY.plusDays(2));when(clock.now()).thenReturn(NOW.plusDays(1));UUID reversal=gl.reverse(third,j,UUID.randomUUID(),DAY.plusDays(2),"Approved subsequent-period correction","Synthetic correction evidence").id();gl.approve(fourth,reversal,"Independent correction review");gl.post(fourth,reversal,false);
        assertThat(service.closeChecks(maker,period)).anyMatch(c->c.key().equals("reversed")&&c.blockers()==0);var after=service.finalizedSnapshot(maker,close);assertThat(after.snapshot()).isEqualTo(frozen.snapshot());assertThat(after.checksum()).isEqualTo(frozen.checksum());assertThat(after.periodClosed()).isTrue();assertThat(tx.execute(status->service.finalizedSnapshotForPublication(maker,close)).recordedCutoff()).isEqualTo(frozen.recordedCutoff());
    }
    @Test void earlierMatchCancellationCannotHideSubsequentVoucherReversal() {
        opening();UUID j=post("HISTORICAL-LINK","4.00",false),s=importOne("HISTORICAL-LINK","4.00"),sl=statementLine(s),match=service.proposeMatch(maker,"EXACT","Original match",List.of(new Allocation(sl,journalLine(j),new BigDecimal("4.00"))));service.reviewMatch(checker,match,true,"Independent original review");UUID timing=service.assignException(maker,sl,"TIMING",checker.getMemberId(),"Earlier timing evidence");service.reviewException(checker,timing,"Earlier independent timing review");
        UUID cancelled=service.reverseMatch(third,match,"Earlier match cancellation");service.reviewMatch(fourth,cancelled,true,"Earlier independent cancellation review");assertThat(service.rows(maker,s,0).rows().getFirst().matched()).isZero();
        when(clock.now()).thenReturn(NOW.plusMinutes(1));UUID voucher=gl.reverse(third,j,UUID.randomUUID(),DAY.plusDays(1),"Subsequent voucher correction","Subsequent source evidence").id();gl.approve(fourth,voucher,"Independent voucher reversal");gl.post(fourth,voucher,false);
        assertThat(service.rows(maker,s,0).rows().getFirst().status()).isEqualTo("REVERSED");assertThat(service.closeChecks(maker,period)).anyMatch(c->c.key().equals("reversed")&&c.blockers()==1);
        UUID investigated=service.assignException(maker,sl,"REVERSED",checker.getMemberId(),"Subsequent linked investigation");service.reviewException(checker,investigated,"Independent reviewed reversal difference");assertThat(service.closeChecks(maker,period)).anyMatch(c->c.key().equals("reversed")&&c.blockers()==0);
    }
}
