package com.sacco.mvp.accounting.reconciliation;

import com.sacco.mvp.accounting.reconciliation.ReconciliationDtos.*;
import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.AccountCommand;
import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.JournalCommand;
import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.Line;
import com.sacco.mvp.accounting.policy.*;
import com.sacco.mvp.accounting.policy.AccountingPolicyService.*;
import com.sacco.mvp.accounting.repository.GeneralLedgerRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.accounting.reports.CashFlowAllocation;
import com.sacco.mvp.accounting.reports.CashFlowAllocationRepository;
import com.sacco.mvp.accounting.reports.CashFlowAllocationService;
import com.sacco.mvp.accounting.reports.CashFlowClosingAdapter;
import com.sacco.mvp.accounting.reports.LedgerReportRepository;
import com.sacco.mvp.accounting.reports.LedgerReportService;
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

@EnabledIfEnvironmentVariable(named="MICROFINANCE_ACCOUNTING_D_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_accounting_d_test(?:_(?:bootstrap|zero)_20261004)?")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReconciliationPostgresTest {
    private JdbcTemplate jdbc;private TransactionTemplate tx;private GeneralLedgerService gl;private ReconciliationService service;
    private AccountingPolicyService policies;private AuditService audit;private UserClaimService claims;private MemberDirectoryService directory;
    private SaccoRegistryService institutions;
    private ApplicationClock clock;
    private PeriodReopenListener reopenListener;
    private HikariDataSource testPool;
    private DataSourceTransactionManager manager;
    private String institution;private AppUserPrincipal maker,checker,third,fourth;private UUID bank,capital,period,policy,format;
    private static final LocalDate DAY=LocalDate.of(2026,10,1);private static final OffsetDateTime NOW=OffsetDateTime.parse("2026-10-02T10:00:00+03:00");
    @BeforeAll void start() {
        var poolConfig=new HikariConfig();poolConfig.setJdbcUrl(System.getenv("MICROFINANCE_ACCOUNTING_D_DATABASE_URL")+"?sslmode=disable&connectTimeout=5&socketTimeout=30");poolConfig.setUsername("microfinance_test");poolConfig.setPassword("");poolConfig.setMaximumPoolSize(4);poolConfig.setMinimumIdle(1);poolConfig.setConnectionTimeout(5000);poolConfig.setValidationTimeout(2000);testPool=new HikariDataSource(poolConfig);var ds=testPool;Flyway.configure().dataSource(ds).locations("classpath:db/migration").outOfOrder(System.getenv("MICROFINANCE_ACCOUNTING_D_DATABASE_URL").endsWith("/microfinance_accounting_d_test")).load().migrate();
        jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);policies=mock(AccountingPolicyService.class);audit=mock(AuditService.class);claims=mock(UserClaimService.class);directory=mock(MemberDirectoryService.class);institutions=mock(SaccoRegistryService.class);
        clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(DAY.plusDays(1));when(clock.now()).thenReturn(NOW);
        gl=proxy(new GeneralLedgerService(new GeneralLedgerRepository(jdbc),policies,new AccessControlService(),audit,clock,claims,directory,institutions,mock(com.sacco.mvp.reporting.execution.service.AccountingReleaseGateService.class)),manager);
        reopenListener=mock(PeriodReopenListener.class);service=proxy(new ReconciliationService(new ReconciliationRepository(jdbc),policies,new AccessControlService(),directory,claims,audit,clock,Optional.empty(),List.of(reopenListener),Optional.empty()),manager);
    }
    @AfterAll void closePool(){if(testPool!=null)testPool.close();}
    private static <T> T proxy(T raw,DataSourceTransactionManager manager) {var f=new ProxyFactory(raw);f.setProxyTargetClass(true);f.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (T)f.getProxy();}
    @BeforeEach void fixture() {
        reset(policies,audit,claims,directory,institutions,reopenListener);enableSources(null,null);when(clock.today()).thenReturn(DAY.plusDays(1));when(clock.now()).thenReturn(NOW);institution="D-"+UUID.randomUUID().toString().toUpperCase(Locale.ROOT);jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,?,true,now(),now())",institution,"Synthetic reconciliation test");
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
        verify(reopenListener).periodReopened(eq(fourth),eq(period),eq(DAY),contains("Verified late correction"));
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
        UUID reopen=service.proposeClose(third,period,"Verified correction",true);doThrow(new IllegalStateException("Synthetic downstream audit failure")).when(reopenListener).periodReopened(any(),eq(period),eq(DAY),anyString());assertThatThrownBy(()->service.approveClose(fourth,reopen,"Independent reopen")).hasMessage("Synthetic downstream audit failure");assertThat(service.finalizedSnapshot(maker,close).periodClosed()).isTrue();assertThat(jdbc.queryForObject("select count(*) from accounting_close_decision where review_id=?",Integer.class,reopen)).isZero();
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

    private com.sacco.mvp.accounting.dto.GeneralLedgerDtos.Journal sourceDraft(String ref) {
        return tx.execute(status->gl.draftSourceEvent(maker,PostingEvent.CAPITAL,new JournalCommand(UUID.randomUUID(),ref,DAY.plusDays(1),"Synthetic source evidence",null,List.of(new Line(bank,new BigDecimal("3.01"),BigDecimal.ZERO),new Line(capital,BigDecimal.ZERO,new BigDecimal("3.01"))))));
    }
    private UUID sourcePosted(String ref) {var draft=sourceDraft(ref);return tx.execute(status->gl.approveAndPostSourceEvent(checker,draft.id(),PostingEvent.CAPITAL,ref,"Synthetic independent source review")).id();}
    private JournalCommand sourceCorrection(String ref) {return new JournalCommand(UUID.randomUUID(),ref,DAY.plusDays(1),"Synthetic actual source correction","Verified source correction",List.of(new Line(bank,BigDecimal.ZERO,new BigDecimal("3.01")),new Line(capital,new BigDecimal("3.01"),BigDecimal.ZERO)));}
    @Test void sourceLifecycleRequiresOwningTransactionAndExactIndependentCorrection() {
        opening();UUID original=sourcePosted("SOURCE");JournalCommand command=sourceCorrection("CORRECTION");
        assertThatThrownBy(()->gl.draftSourceReversal(third,original,command)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(()->tx.execute(status->gl.draftSourceReversal(maker,original,command))).hasMessage("accounting.error.independentReversal");
        assertThatThrownBy(()->tx.execute(status->gl.draftSourceReversal(checker,original,command))).hasMessage("accounting.error.independentReversal");
        var wrong=new JournalCommand(UUID.randomUUID(),"WRONG",command.effectiveDate(),command.evidenceReference(),command.reason(),List.of(new Line(bank,BigDecimal.ZERO,BigDecimal.ONE),new Line(capital,BigDecimal.ONE,BigDecimal.ZERO)));
        assertThatThrownBy(()->tx.execute(status->gl.draftSourceReversal(third,original,wrong))).hasMessage("accounting.error.original");
        var correction=tx.execute(status->gl.draftSourceReversal(third,original,command));
        assertThat(tx.execute(status->gl.draftSourceReversal(third,original,command)).id()).isEqualTo(correction.id());
        assertThatThrownBy(()->gl.approve(fourth,correction.id(),"Generic shortcut")).hasMessage("accounting.error.sourceType");
        assertThatThrownBy(()->gl.post(fourth,correction.id(),false)).hasMessage("accounting.error.sourceType");
        assertThatThrownBy(()->tx.execute(status->gl.approveAndPostSourceReversal(fourth,correction.id(),"WRONG","Synthetic review"))).hasMessage("accounting.error.sourceType");
        assertThatThrownBy(()->tx.execute(status->gl.approveAndPostSourceReversal(third,correction.id(),command.sourceReference(),"Own review"))).hasMessage("accounting.error.independentChecker");
        var posted=tx.execute(status->gl.approveAndPostSourceReversal(fourth,correction.id(),command.sourceReference(),"Independent source correction"));
        assertThat(posted.state()).isEqualTo("POSTED");assertThat(posted.reversesId()).isEqualTo(original);
        assertThat(tx.execute(status->gl.approveAndPostSourceReversal(fourth,correction.id(),command.sourceReference(),"Independent source correction")).id()).isEqualTo(correction.id());
        assertThatThrownBy(()->tx.execute(status->gl.approveAndPostSourceReversal(fourth,correction.id(),command.sourceReference(),"Changed correction review"))).hasMessage("accounting.error.changedRetry");
        assertThat(jdbc.queryForObject("select count(*) from accounting_outbox where journal_id=?",Integer.class,correction.id())).isEqualTo(1);
        assertThat(gl.journal(maker,original).reversed()).isTrue();
    }
    @Test void retainedCancellationIsAtomicIdempotentScopedAndCannotBePosted() {
        opening();var draft=sourceDraft("REJECTED");
        assertThatThrownBy(()->gl.cancelSourceEvent(checker,draft.id(),PostingEvent.CAPITAL,"REJECTED","Independent rejection")).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(()->tx.execute(status->gl.cancelSourceEvent(maker,draft.id(),PostingEvent.CAPITAL,"REJECTED","Own rejection"))).hasMessage("accounting.error.independentChecker");
        station("B2");var b2=operator("B2");
        assertThatThrownBy(()->tx.execute(status->gl.cancelSourceEvent(b2,draft.id(),PostingEvent.CAPITAL,"REJECTED","Wrong branch"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->tx.execute(status->gl.cancelSourceEvent(checker,draft.id(),PostingEvent.FUNDING,"REJECTED","Wrong event"))).hasMessage("accounting.error.sourceType");
        assertThat(service.closeChecks(maker,period)).anyMatch(c->c.key().equals("drafts")&&c.blockers()==1);
        var cancelled=tx.execute(status->gl.cancelSourceEvent(checker,draft.id(),PostingEvent.CAPITAL,"REJECTED","Independent rejection"));
        assertThat(cancelled.state()).isEqualTo("CANCELLED");
        assertThat(tx.execute(status->gl.cancelSourceEvent(checker,draft.id(),PostingEvent.CAPITAL,"REJECTED","Independent rejection")).state()).isEqualTo("CANCELLED");
        assertThat(gl.journals(maker,0).rows()).anyMatch(j->j.id().equals(draft.id())&&j.state().equals("CANCELLED"));
        assertThat(gl.sourceCancellation(maker,draft.id()).orElseThrow().checker()).isEqualTo(checker.getMemberId());
        assertThat(service.closeChecks(maker,period)).anyMatch(c->c.key().equals("drafts")&&c.blockers()==0);
        assertThatThrownBy(()->tx.execute(status->gl.cancelSourceEvent(checker,draft.id(),PostingEvent.CAPITAL,"REJECTED","Changed rejection"))).hasMessage("accounting.error.changedRetry");
        assertThatThrownBy(()->tx.execute(status->gl.approveAndPostSourceEvent(checker,draft.id(),PostingEvent.CAPITAL,"REJECTED","Resurrection"))).hasMessage("accounting.error.state");
        assertThatThrownBy(()->jdbc.update("update gl_journal set state='APPROVED',checker_id=?,checked_at=now(),approval_evidence_reference='Bypass' where id=?",checker.getMemberId(),draft.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("insert into gl_journal_line(id,journal_id,sacco_id,station_id,account_id,debit,credit) values(?,?,?,?,?,1,0)",UUID.randomUUID(),draft.id(),institution,"B1",bank)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("delete from gl_source_cancellation where journal_id=?",draft.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("update gl_source_cancellation set evidence_reference='Rewrite' where journal_id=?",draft.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("select count(*) from accounting_outbox where journal_id=?",Integer.class,draft.id())).isZero();
    }
    @Test void callerAndAuditFailuresRollBackSourceCancellationAndCorrection() {
        opening();var draft=sourceDraft("ROLLBACK");
        assertThatThrownBy(()->tx.execute(status->{gl.cancelSourceEvent(checker,draft.id(),PostingEvent.CAPITAL,"ROLLBACK","Independent rejected source");throw new IllegalStateException("Owning source rejection failed");})).hasMessage("Owning source rejection failed");
        assertThat(gl.journal(maker,draft.id()).state()).isEqualTo("DRAFT");assertThat(gl.sourceCancellation(maker,draft.id())).isEmpty();
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit).logEvent(anyString(),eq(draft.id()),eq("SOURCE_JOURNAL_CANCELLED"),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),any());
        assertThatThrownBy(()->tx.execute(status->gl.cancelSourceEvent(checker,draft.id(),PostingEvent.CAPITAL,"ROLLBACK","Independent rejected source"))).hasMessage("Synthetic audit failure");assertThat(gl.sourceCancellation(maker,draft.id())).isEmpty();
        reset(audit);UUID original=sourcePosted("POSTED-ROLLBACK");var command=sourceCorrection("CORRECTION-ROLLBACK");var correction=tx.execute(status->gl.draftSourceReversal(third,original,command));
        assertThatThrownBy(()->tx.execute(status->{gl.approveAndPostSourceReversal(fourth,correction.id(),command.sourceReference(),"Independent actual correction");throw new IllegalStateException("Subledger correction failed");})).hasMessage("Subledger correction failed");
        assertThat(gl.journal(maker,correction.id()).state()).isEqualTo("DRAFT");assertThat(gl.journal(maker,original).reversed()).isFalse();assertThat(jdbc.queryForObject("select count(*) from accounting_outbox where journal_id=?",Integer.class,correction.id())).isZero();
    }
    @Test void cancellationNeverAcceptsManualOpeningOrPostedSourcesAndReplacementRetainsHistory() {
        UUID opening=opening(),manual=gl.draftManual(maker,new JournalCommand(UUID.randomUUID(),"MANUAL-CANCEL",DAY.plusDays(1),"Synthetic manual",null,List.of(new Line(bank,BigDecimal.ONE,BigDecimal.ZERO),new Line(capital,BigDecimal.ZERO,BigDecimal.ONE)))).id();
        assertThatThrownBy(()->tx.execute(status->gl.cancelSourceEvent(checker,manual,PostingEvent.MANUAL_JOURNAL,"MANUAL-CANCEL","Wrong source"))).hasMessage("accounting.error.sourceType");
        assertThatThrownBy(()->tx.execute(status->gl.cancelSourceEvent(checker,opening,PostingEvent.OPENING_BALANCE,"OPENING","Wrong opening"))).hasMessage("accounting.error.sourceType");
        UUID original=sourcePosted("REPLACE-SOURCE");assertThatThrownBy(()->tx.execute(status->gl.cancelSourceEvent(checker,original,PostingEvent.CAPITAL,"REPLACE-SOURCE","Posted cannot cancel"))).hasMessage("accounting.error.state");
        var first=sourceCorrection("REJECTED-CORRECTION");var rejected=tx.execute(status->gl.draftSourceReversal(third,original,first));tx.execute(status->gl.cancelSourceEvent(fourth,rejected.id(),PostingEvent.REVERSAL,first.sourceReference(),"Rejected owning correction"));
        var second=sourceCorrection("REPLACEMENT-CORRECTION");var replacement=tx.execute(status->gl.draftSourceReversal(third,original,second));tx.execute(status->gl.approveAndPostSourceReversal(fourth,replacement.id(),second.sourceReference(),"Independently reviewed replacement"));
        assertThat(gl.journal(maker,rejected.id()).state()).isEqualTo("CANCELLED");assertThat(gl.journal(maker,replacement.id()).state()).isEqualTo("POSTED");assertThat(jdbc.queryForObject("select count(*) from gl_journal where reverses_id=?",Integer.class,original)).isEqualTo(2);
    }
    @Test void reversalPolicyAndCurrentStaffRevocationGateSourceLifecycle() {
        opening();UUID original=sourcePosted("POLICY-SOURCE");var command=sourceCorrection("POLICY-CORRECTION");
        doThrow(new IllegalArgumentException("accounting.error.postingDisabled")).when(policies).requireAllowedPosting(any(),eq(PostingEvent.REVERSAL));
        assertThatThrownBy(()->tx.execute(status->gl.draftSourceReversal(third,original,command))).hasMessage("accounting.error.postingDisabled");
        doNothing().when(policies).requireAllowedPosting(any(),eq(PostingEvent.REVERSAL));var correction=tx.execute(status->gl.draftSourceReversal(third,original,command));
        doThrow(new IllegalArgumentException("accounting.error.postingDisabled")).when(policies).requireAllowedPosting(any(),eq(PostingEvent.REVERSAL));
        assertThatThrownBy(()->tx.execute(status->gl.approveAndPostSourceReversal(fourth,correction.id(),command.sourceReference(),"Policy now disabled"))).hasMessage("accounting.error.postingDisabled");assertThat(gl.journal(maker,correction.id()).state()).isEqualTo("DRAFT");
        when(claims.effectiveClaims(eq(fourth.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of());
        assertThatThrownBy(()->tx.execute(status->gl.cancelSourceEvent(fourth,correction.id(),PostingEvent.REVERSAL,command.sourceReference(),"Revoked checker"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void concurrentSourceCorrectionsKeepOnlyOneActiveReservation() throws Exception {
        opening();UUID original=sourcePosted("SOURCE-RACE");var a=sourceCorrection("RACE-A");var b=sourceCorrection("RACE-B");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var jobs=List.of(pool.submit(()->sourceCorrectionAttempt(original,a)),pool.submit(()->sourceCorrectionAttempt(original,b)));
            assertThat(jobs.get(0).get(20,TimeUnit.SECONDS)+jobs.get(1).get(20,TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("select count(*) from gl_journal where reverses_id=?",Integer.class,original)).isEqualTo(1);
    }
    private int sourceCorrectionAttempt(UUID original,JournalCommand c) {try{tx.execute(status->gl.draftSourceReversal(third,original,c));return 1;}catch(org.springframework.dao.DataAccessException e){return 0;}}
    private UUID balancedTimingClose(UUID p,LocalDate date,String prefix) {
        UUID statement=service.importStatement(maker,new StatementCommand(UUID.randomUUID(),bank,format,date,date,new BigDecimal("100.00"),new BigDecimal("100.00"),prefix+".csv","Synthetic retained timing","date,reference,amount,kind\n"+date+","+prefix+"-IN,1.00,RECEIPT\n"+date+","+prefix+"-OUT,-1.00,DISBURSEMENT"));
        for(var row:service.rows(maker,statement,0).rows()) {UUID ex=service.assignException(maker,row.id(),"TIMING",checker.getMemberId(),"Verified retained timing");service.reviewException(checker,ex,"Independent retained difference");}
        UUID cert=service.certify(maker,bank,date,"STATEMENT",new BigDecimal("100.00"),"Verified balanced bank statement");service.reviewCertificate(checker,cert,"Independent balanced statement review");
        UUID close=service.proposeClose(maker,p,"Prepared synthetic close",false);service.approveClose(checker,close,"Independent synthetic close");service.completeInstitutionClose(checker,p,"All institution branches reviewed");return close;
    }
    @Test void earlierPeriodReopenInvalidatesLaterCumulativePublicationWithoutRewritingSnapshots() {
        opening();UUID first=balancedTimingClose(period,DAY.plusDays(1),"FIRST");UUID laterPeriod=gl.createPeriod(maker,DAY.plusDays(2),DAY.plusDays(2));when(clock.today()).thenReturn(DAY.plusDays(2));when(clock.now()).thenReturn(NOW.plusDays(1));
        UUID later=balancedTimingClose(laterPeriod,DAY.plusDays(2),"LATER");var frozen=service.finalizedSnapshot(maker,later);assertThat(tx.execute(status->service.finalizedSnapshotForPublication(maker,later)).periodClosed()).isTrue();
        when(clock.now()).thenReturn(NOW.plusDays(1).plusMinutes(1));UUID reopen=service.proposeClose(third,period,"Earlier backdated correction",true);service.approveClose(fourth,reopen,"Independent earlier reopening");
        verify(reopenListener).periodReopened(eq(fourth),eq(period),eq(DAY),contains("Earlier backdated correction"));
        assertThat(service.finalizedSnapshot(maker,later).periodClosed()).isFalse();assertThat(service.finalizedSnapshot(maker,later).snapshot()).isEqualTo(frozen.snapshot());
        assertThatThrownBy(()->tx.execute(status->service.finalizedSnapshotForPublication(maker,later))).hasMessage("reconciliation.error.approvalRequired");
        when(clock.now()).thenReturn(NOW.plusDays(1).plusMinutes(2));UUID revised=service.proposeClose(maker,period,"Reclosed earlier source",false);service.approveClose(checker,revised,"Independent earlier reclose");service.completeInstitutionClose(checker,period,"Reviewed earlier branches");
        assertThat(service.finalizedSnapshot(maker,later).periodClosed()).isFalse();assertThat(service.finalizedSnapshot(maker,first).snapshot()).isNotNull();
    }

    @Test void concurrentRetainedCancellationRetriesBothReturnCancelledWithoutDuplicateAudit() throws Exception {
        opening();var draft=sourceDraft("CANCEL-RACE");var retained=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->tx.execute(status->{var result=gl.cancelSourceEvent(checker,draft.id(),PostingEvent.CAPITAL,"CANCEL-RACE","Independent concurrent rejection");retained.countDown();try{if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Cancellation barrier timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return result;}));
            assertThat(retained.await(20,TimeUnit.SECONDS)).isTrue();
            var retry=pool.submit(()->tx.execute(status->gl.cancelSourceEvent(checker,draft.id(),PostingEvent.CAPITAL,"CANCEL-RACE","Independent concurrent rejection")));
            try{assertThatThrownBy(()->retry.get(250,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}finally{release.countDown();}
            assertThat(first.get(20,TimeUnit.SECONDS).state()).isEqualTo("CANCELLED");assertThat(retry.get(20,TimeUnit.SECONDS).state()).isEqualTo("CANCELLED");
        }finally{release.countDown();}
        assertThat(jdbc.queryForObject("select count(*) from gl_source_cancellation where journal_id=?",Integer.class,draft.id())).isEqualTo(1);
        verify(audit,times(1)).logEvent(anyString(),eq(draft.id()),eq("SOURCE_JOURNAL_CANCELLED"),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),any());
    }
    @Test void databaseRejectsDisabledReversalPolicyWhileRetainedRejectionNeedsNoNewPostingPermission() {
        opening();UUID original=sourcePosted("DB-POLICY-SOURCE");var rejected=sourceDraft("OLD-POLICY-REJECTION");var previous=policies.requireApprovedLocalPolicy(institution,DAY.plusDays(1));UUID next=UUID.randomUUID();
        var matrix=new EnumMap<PostingEvent,PostingRule>(PostingEvent.class);matrix.putAll(previous.postingMatrix());matrix.put(PostingEvent.REVERSAL,new PostingRule(PostingPermission.DISABLED,"Independent synthetic reversal disabled"));matrix.put(PostingEvent.CAPITAL,new PostingRule(PostingPermission.DISABLED,"Independent synthetic capital disabled"));
        var json=JsonMapper.builder().findAndAddModules().build();
        jdbc.update("insert into accounting_policies(id,sacco_id,policy_version,effective_from,opening_date,authoritative_ledger,decisions_json,posting_matrix_json,account_mappings_json,evidence_reference,maker_id,request_key,created_at) values(?,?,2,?,?,'LOCAL_GL',?,?,?,'Synthetic independently reviewed disabled events',?,?,?)",next,institution,DAY.plusDays(1),DAY,json.writeValueAsString(previous.decisions()),json.writeValueAsString(matrix),json.writeValueAsString(previous.accountMappings()),maker.getMemberId(),UUID.randomUUID(),NOW);
        jdbc.update("insert into accounting_policy_approvals(policy_id,sacco_id,policy_version,effective_from,checker_id,decision,evidence_reference,reason,decided_at) values(?,?,2,?,?,'APPROVED','Synthetic independent policy review','No institution policy is represented',?)",next,institution,DAY.plusDays(1),checker.getMemberId(),NOW);
        var snapshot=new PolicySnapshot(next,institution,2,DAY,DAY.plusDays(1),AuthoritativeLedger.LOCAL_GL,previous.decisions(),matrix,previous.accountMappings(),"Synthetic policy",maker.getMemberId(),Decision.APPROVED,checker.getMemberId(),"Synthetic independent review","Test",NOW);
        when(policies.requireApprovedLocalPolicy(eq(institution),any())).thenReturn(snapshot);
        // Mocked service permission allows this attempted draft; the genuine database policy must reject it.
        assertThatThrownBy(()->tx.execute(status->gl.draftSourceReversal(third,original,sourceCorrection("DB-DISABLED-CORRECTION")))).hasStackTraceContaining("Approved explicit reversal treatment is required");
        assertThat(jdbc.queryForObject("select count(*) from gl_journal where reverses_id=?",Integer.class,original)).isZero();
        var cancelled=tx.execute(status->gl.cancelSourceEvent(checker,rejected.id(),PostingEvent.CAPITAL,"OLD-POLICY-REJECTION","Independent source rejection after disabling posting"));
        assertThat(cancelled.state()).isEqualTo("CANCELLED");assertThat(gl.sourceCancellation(maker,rejected.id()).orElseThrow().payloadChecksum()).isEqualTo(rejected.payloadHash());
    }

    private void enableSources(BusinessReconciliationSource controls,CashFlowReconciliationSource cash) {
        service=proxy(new ReconciliationService(new ReconciliationRepository(jdbc),policies,new AccessControlService(),directory,claims,audit,clock,Optional.ofNullable(controls),List.of(reopenListener),Optional.ofNullable(cash)),manager);
    }
    private UUID controlAccount(String purpose) {return gl.createAccount(maker,new AccountCommand(purpose,"Synthetic "+purpose+" control",purpose.equals("LOAN_PRINCIPAL")?"ASSET":"LIABILITY",purpose.equals("LOAN_PRINCIPAL")?"DEBIT":"CREDIT","CONTROL",purpose,null));}
    private BusinessReconciliationSource.HistoricalControlSnapshot zeroSource(AppUserPrincipal actor,UUID account,LocalDate date,String purpose,OffsetDateTime cutoff,UUID evidence,String digest) {
        var opening=new ReconciliationRepository(jdbc).opening(actor.getSaccoId(),actor.getStationId()).orElseThrow();
        var proof=new BusinessReconciliationSource.SourceOpeningEvidence(evidence,opening.journal(),account,opening.through(),policy,1,opening.maker(),opening.reviewer(),"Synthetic explicit zero source coverage","Independent zero source-opening review","a".repeat(64),NOW,true,true);
        return new BusinessReconciliationSource.HistoricalControlSnapshot(actor.getSaccoId(),actor.getStationId(),account,date,purpose,cutoff,BigDecimal.ZERO,List.of(proof),0,digest,null,true);
    }
    private void prepareBankDifference() {
        UUID s=importOne("SOURCE-TIMING","1.00"),ex=service.assignException(maker,statementLine(s),"TIMING",checker.getMemberId(),"Verified independent timing");service.reviewException(checker,ex,"Retained timing review");certify(new BigDecimal("101.00"));
    }
    @Test void balanceOnlyControlProvidersRemainUnavailableEvenWhenBothNumbersAreZero() {
        opening();UUID account=controlAccount("PAYABLE");enableSources((a,id,date,purpose)->Optional.of(BigDecimal.ZERO),null);
        assertThatThrownBy(()->service.certify(maker,account,DAY.plusDays(1),"SUPPLIER",BigDecimal.ZERO,"Numbers alone are insufficient")).hasMessage("reconciliation.error.controlUnavailable");
        assertThat(service.closeChecks(maker,period)).anyMatch(c->c.key().equals("businessControlSources")&&c.blockers()==1);
        assertThat(jdbc.queryForObject("select count(*) from reconciliation_certificate where account_id=?",Integer.class,account)).isZero();
    }
    @Test void explicitReviewedZeroSourceProofIsFrozenAndLaterProvenanceChangesBlockNewPublication() {
        opening();UUID account=controlAccount("PAYABLE"),coverage=UUID.randomUUID();var source=mock(BusinessReconciliationSource.class);var digest=new java.util.concurrent.atomic.AtomicReference<>("b".repeat(64));
        when(source.historicalControlSnapshot(any(),eq(account),any(),eq("PAYABLE"),any())).thenAnswer(i->Optional.of(zeroSource(i.getArgument(0),account,i.getArgument(2),"PAYABLE",i.getArgument(4),coverage,digest.get())));
        enableSources(source,null);UUID certificate=service.certify(maker,account,DAY.plusDays(1),"SUPPLIER",BigDecimal.ZERO,"Independently reviewed zero obligations");service.reviewCertificate(checker,certificate,"Independent control certificate review");prepareBankDifference();
        UUID close=service.proposeClose(maker,period,"Prepared with frozen source-opening proof",false);digest.set("c".repeat(64));
        assertThatThrownBy(()->service.approveClose(checker,close,"Cannot reuse changed source evidence")).hasMessage("reconciliation.error.staleEvidence");assertThat(jdbc.queryForObject("select count(*) from accounting_close_decision where review_id=?",Integer.class,close)).isZero();
        digest.set("b".repeat(64));service.approveClose(checker,close,"Independent typed source close review");service.completeInstitutionClose(checker,period,"All source coverage revalidated");
        var frozen=service.finalizedSnapshot(maker,close);var json=JsonMapper.builder().findAndAddModules().build().readTree(frozen.snapshot());var proof=json.get("businessControlSources").get(0);
        assertThat(proof.get("completeCoverage").asBoolean()).isTrue();assertThat(proof.get("signedBalance").decimalValue()).isZero();assertThat(proof.get("movementDigest").asText()).isEqualTo(digest.get());
        assertThat(proof.get("reviewedOpenings").get(0).get("generalLedgerOpeningId").asText()).isEqualTo(service.reviewedOpeningEvidence(maker).journal().toString());assertThat(proof.get("reviewedOpenings").get(0).get("reviewedZero").asBoolean()).isTrue();
        assertThat(tx.execute(status->service.finalizedSnapshotForPublication(maker,close)).periodClosed()).isTrue();digest.set("d".repeat(64));
        assertThatThrownBy(()->tx.execute(status->service.finalizedSnapshotForPublication(maker,close))).hasMessage("reconciliation.error.staleEvidence");assertThat(service.finalizedSnapshot(maker,close).snapshot()).isEqualTo(frozen.snapshot());
    }
    @Test void incompleteForeignFutureAndSelfReviewedControlProofsCannotCreateCertificates() {
        opening();UUID account=controlAccount("FUNDING"),coverage=UUID.randomUUID();var source=mock(BusinessReconciliationSource.class);
        var baseline=zeroSource(maker,account,DAY.plusDays(1),"FUNDING",NOW,coverage,"b".repeat(64));var e=baseline.reviewedOpenings().getFirst();
        var variants=List.of(
            new BusinessReconciliationSource.HistoricalControlSnapshot(institution,"B2",account,baseline.asOf(),"FUNDING",NOW,BigDecimal.ZERO,baseline.reviewedOpenings(),0,baseline.movementDigest(),null,true),
            new BusinessReconciliationSource.HistoricalControlSnapshot(institution,"B1",account,baseline.asOf(),"FUNDING",NOW,BigDecimal.ZERO,List.of(),0,baseline.movementDigest(),null,true),
            new BusinessReconciliationSource.HistoricalControlSnapshot(institution,"B1",account,baseline.asOf(),"FUNDING",NOW,BigDecimal.ZERO,baseline.reviewedOpenings(),0,baseline.movementDigest(),null,false),
            new BusinessReconciliationSource.HistoricalControlSnapshot(institution,"B1",account,baseline.asOf(),"FUNDING",NOW,BigDecimal.ZERO,List.of(new BusinessReconciliationSource.SourceOpeningEvidence(e.id(),UUID.randomUUID(),account,e.through(),policy,1,e.maker(),e.reviewer(),e.sourceEvidence(),e.reviewEvidence(),e.payloadChecksum(),NOW,true,true)),0,baseline.movementDigest(),null,true),
            new BusinessReconciliationSource.HistoricalControlSnapshot(institution,"B1",account,baseline.asOf(),"FUNDING",NOW,BigDecimal.ZERO,List.of(new BusinessReconciliationSource.SourceOpeningEvidence(e.id(),e.generalLedgerOpeningId(),account,e.through(),policy,1,e.maker(),e.maker(),e.sourceEvidence(),e.reviewEvidence(),e.payloadChecksum(),NOW,true,true)),0,baseline.movementDigest(),null,true),
            new BusinessReconciliationSource.HistoricalControlSnapshot(institution,"B1",account,baseline.asOf(),"FUNDING",NOW,BigDecimal.ZERO,baseline.reviewedOpenings(),1,baseline.movementDigest(),NOW.plusMinutes(1),true));
        enableSources(source,null);
        for(var candidate:variants) {
            when(source.historicalControlSnapshot(any(),eq(account),any(),eq("FUNDING"),any())).thenReturn(Optional.of(candidate));
            assertThatThrownBy(()->service.certify(maker,account,DAY.plusDays(1),"FUNDING",BigDecimal.ZERO,"Untrusted source coverage")).hasMessage("reconciliation.error.controlUnavailable");
        }
        assertThat(jdbc.queryForObject("select count(*) from reconciliation_certificate where account_id=?",Integer.class,account)).isZero();
    }
    private UUID compoundCash() {
        UUID expense=gl.createAccount(maker,new AccountCommand("NONCASH","Synthetic noncash expense","EXPENSE","DEBIT","POSTING","EXPENSE",null));
        UUID journal=gl.draftManual(maker,new JournalCommand(UUID.randomUUID(),"CASH-ALLOCATED",DAY.plusDays(1),"Synthetic reviewed compound source",null,List.of(new Line(bank,new BigDecimal("10.00"),BigDecimal.ZERO),new Line(expense,new BigDecimal("5.00"),BigDecimal.ZERO),new Line(capital,BigDecimal.ZERO,new BigDecimal("15.00"))))).id();gl.approve(checker,journal,"Independent compound posting");gl.post(checker,journal,false);return journal;
    }
    private CashFlowReconciliationSource.Version cashVersion(UUID journal,UUID version) {
        var lines=jdbc.query("select l.id,l.account_id,a.code,a.type,a.purpose,l.debit-l.credit signed_amount from gl_journal_line l join gl_account a on a.id=l.account_id where l.journal_id=? order by l.id",(r,n)->new CashFlowReconciliationSource.SourceLine(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getString(5),r.getBigDecimal(6)),journal);
        return new CashFlowReconciliationSource.Version(version,journal,1,third.getMemberId(),NOW,"Synthetic cash allocation source evidence","Verified owner-funded noncash expense","b".repeat(64),"c".repeat(64),new CashFlowReconciliationSource.Source(journal,policy,1,"CASH-ALLOCATED",lines),List.of(new CashFlowReconciliationSource.Split(journalLine(journal),capital,"FINANCING",new BigDecimal("10.00"))),fourth.getMemberId(),NOW,"Independent cash allocation review");
    }
    @Test void cashAllocationVersionsAreFrozenAndCompoundJournalsAreExcludedFromAutomaticCash() {
        opening();UUID journal=compoundCash(),version=UUID.randomUUID();var cash=mock(CashFlowReconciliationSource.class);var proof=cashVersion(journal,version);
        when(cash.reviewedAllocations(any(),eq(List.of(journal)),any())).thenReturn(new CashFlowReconciliationSource.Coverage(List.of(proof),List.of()));enableSources(null,cash);
        UUID s=importOne("CASH-ALLOCATED","10.00"),match=service.proposeMatch(maker,"EXACT","Compound money source",List.of(new Allocation(statementLine(s),journalLine(journal),new BigDecimal("10.00"))));service.reviewMatch(checker,match,true,"Independent cash matching");certify(new BigDecimal("110.00"));UUID close=service.proposeClose(maker,period,"Prepared allocated cash close",false);service.approveClose(checker,close,"Independent cash-flow evidence");service.completeInstitutionClose(checker,period,"Reviewed institution cash evidence");
        var frozen=service.finalizedSnapshot(maker,close);var json=JsonMapper.builder().findAndAddModules().build().readTree(frozen.snapshot());
        assertThat(json.get("automaticCashMovements").size()).isZero();assertThat(json.get("cashFlowAllocations").get("missingJournalIds").size()).isZero();assertThat(json.get("cashFlowAllocations").get("versions").get(0).get("id").asText()).isEqualTo(version.toString());assertThat(json.get("cashFlowAllocations").get("versions").get(0).get("splits").get(0).get("activity").asText()).isEqualTo("FINANCING");
        assertThat(tx.execute(status->service.finalizedSnapshotForPublication(maker,close)).periodClosed()).isTrue();
        when(cash.reviewedAllocations(any(),eq(List.of(journal)),any())).thenReturn(new CashFlowReconciliationSource.Coverage(List.of(),List.of(journal)));
        assertThatThrownBy(()->tx.execute(status->service.finalizedSnapshotForPublication(maker,close))).hasMessage("reconciliation.error.staleEvidence");assertThat(service.finalizedSnapshot(maker,close).snapshot()).isEqualTo(frozen.snapshot());
    }
    @Test void absentCashAllocationCoverageIsExplicitAndUnapprovedOrIncompleteSplitsFailClosed() {
        opening();UUID journal=compoundCash(),version=UUID.randomUUID();var cash=mock(CashFlowReconciliationSource.class);var v=cashVersion(journal,version);enableSources(null,cash);
        when(cash.reviewedAllocations(any(),anyList(),any())).thenReturn(new CashFlowReconciliationSource.Coverage(List.of(),List.of(journal)));
        var provenance=new ReconciliationProvenance(new ReconciliationRepository(jdbc),Optional.empty(),Optional.of(cash));var p=new ReconciliationDtos.Period(period,DAY,DAY.plusDays(1),"OPEN");
        assertThat(provenance.prepareCash(maker,p,NOW).missingJournalIds()).containsExactly(journal);
        var ownReview=new CashFlowReconciliationSource.Version(v.id(),v.journalId(),v.version(),v.maker(),v.madeAt(),v.evidence(),v.noncashEvidence(),v.sourceChecksum(),v.definitionChecksum(),v.source(),v.splits(),v.maker(),v.reviewedAt(),v.reviewEvidence());
        when(cash.reviewedAllocations(any(),anyList(),any())).thenReturn(new CashFlowReconciliationSource.Coverage(List.of(ownReview),List.of()));
        assertThatThrownBy(()->provenance.prepareCash(maker,p,NOW)).hasMessage("reconciliation.error.cashFlowEvidence");
        var incomplete=new CashFlowReconciliationSource.Version(v.id(),v.journalId(),v.version(),v.maker(),v.madeAt(),v.evidence(),v.noncashEvidence(),v.sourceChecksum(),v.definitionChecksum(),v.source(),List.of(new CashFlowReconciliationSource.Split(journalLine(journal),capital,"FINANCING",new BigDecimal("9.99"))),v.checker(),v.reviewedAt(),v.reviewEvidence());
        when(cash.reviewedAllocations(any(),anyList(),any())).thenReturn(new CashFlowReconciliationSource.Coverage(List.of(incomplete),List.of()));
        assertThatThrownBy(()->provenance.prepareCash(maker,p,NOW)).hasMessage("reconciliation.error.cashFlowEvidence");
    }

    @Test void institutionCompletionUsesOnlyExplicitlyAuthorizedMinimalForeignBranchProof() {
        opening();UUID account=controlAccount("PAYABLE");station("B2");var b2maker=operator("B2");var b2checker=operator("B2");
        var opening=gl.importOpening(b2maker,new JournalCommand(UUID.randomUUID(),"B2-OPENING",DAY,"Synthetic independently reviewed B2 opening",null,List.of(new Line(bank,new BigDecimal("100.00"),BigDecimal.ZERO),new Line(capital,BigDecimal.ZERO,new BigDecimal("100.00")))));
        gl.approve(b2checker,opening.id(),"Synthetic B2 independent cutover");gl.post(b2checker,opening.id(),true);
        var coverage=Map.of("B1",UUID.randomUUID(),"B2",UUID.randomUUID());var source=mock(BusinessReconciliationSource.class);var globalAllowed=new java.util.concurrent.atomic.AtomicBoolean(false);
        when(source.historicalControlSnapshot(any(),eq(account),any(),eq("PAYABLE"),any())).thenAnswer(i->{
            AppUserPrincipal actor=i.getArgument(0);assertThat(directory.find(actor.getMemberId()).orElseThrow().getStationId()).isEqualTo(actor.getStationId());
            return Optional.of(zeroSource(actor,account,i.getArgument(2),"PAYABLE",i.getArgument(4),coverage.get(actor.getStationId()),"b".repeat(64)));
        });
        when(source.historicalControlSnapshotCurrentForInstitutionClose(any(),any())).thenAnswer(i->{AppUserPrincipal actor=i.getArgument(0);BusinessReconciliationSource.HistoricalControlSnapshot frozen=i.getArgument(1);assertThat(actor.getStationId()).isEqualTo("B1");assertThat(frozen.branch()).isEqualTo("B2");return globalAllowed.get();});
        enableSources(source,null);
        UUID own=service.certify(maker,account,DAY.plusDays(1),"SUPPLIER",BigDecimal.ZERO,"Reviewed B1 empty obligations");service.reviewCertificate(checker,own,"Independent B1 source agreement");prepareBankDifference();UUID ownClose=service.proposeClose(maker,period,"B1 reviewed source branch",false);service.approveClose(checker,ownClose,"Independent B1 closing");
        UUID other=service.certify(b2maker,account,DAY.plusDays(1),"SUPPLIER",BigDecimal.ZERO,"Reviewed B2 empty obligations");service.reviewCertificate(b2checker,other,"Independent B2 source agreement");
        UUID statement=service.importStatement(b2maker,new StatementCommand(UUID.randomUUID(),bank,format,DAY.plusDays(1),DAY.plusDays(1),new BigDecimal("100.00"),new BigDecimal("100.00"),"b2-bank.csv","Synthetic B2 verified source","date,reference,amount,kind\n2026-10-02,B2-IN,1.00,RECEIPT\n2026-10-02,B2-OUT,-1.00,DISBURSEMENT"));
        for(var row:service.rows(b2maker,statement,0).rows()) {UUID ex=service.assignException(b2maker,row.id(),"TIMING",b2checker.getMemberId(),"B2 verified timing");service.reviewException(b2checker,ex,"B2 independent retained timing");}
        UUID bankCertificate=service.certify(b2maker,bank,DAY.plusDays(1),"STATEMENT",new BigDecimal("100.00"),"B2 balanced bank source");service.reviewCertificate(b2checker,bankCertificate,"B2 independent bank review");
        UUID otherClose=service.proposeClose(b2maker,period,"B2 prepared independent source close",false);service.approveClose(b2checker,otherClose,"Independent B2 closing");
        assertThatThrownBy(()->service.completeInstitutionClose(checker,period,"No global source validation yet")).hasMessage("reconciliation.error.staleEvidence");
        assertThat(jdbc.queryForObject("select state from accounting_period where id=?",String.class,period)).isEqualTo("OPEN");
        globalAllowed.set(true);service.completeInstitutionClose(checker,period,"Explicitly authorized institution source proof");
        assertThat(service.finalizedSnapshot(maker,ownClose).periodClosed()).isTrue();assertThat(service.finalizedSnapshot(b2maker,otherClose).periodClosed()).isTrue();
        verify(source,times(2)).historicalControlSnapshotCurrentForInstitutionClose(eq(checker),argThat(s->s.branch().equals("B2")));
        var aggregate=tx.execute(status->service.finalizedInstitutionSnapshotForPublication(checker,period));
        var retained=JsonMapper.builder().findAndAddModules().build().readTree(aggregate.snapshot());assertThat(retained.get("businessControlSources").size()).isEqualTo(2);
        globalAllowed.set(false);assertThatThrownBy(()->tx.execute(status->service.finalizedInstitutionSnapshotForPublication(checker,period))).hasMessage("reconciliation.error.staleEvidence");assertThat(aggregate.checksum()).isEqualTo(ReconciliationService.sha(aggregate.snapshot()));
    }
    private UUID secondBranchOpening(AppUserPrincipal actor,AppUserPrincipal reviewer) {
        var j=gl.importOpening(actor,new JournalCommand(UUID.randomUUID(),"B2-INDEPENDENT-OPENING",DAY,"Synthetic reviewed B2 source",null,List.of(new Line(bank,new BigDecimal("100.00"),BigDecimal.ZERO),new Line(capital,BigDecimal.ZERO,new BigDecimal("100.00")))));
        gl.approve(reviewer,j.id(),"Independent B2 imported balances");gl.post(reviewer,j.id(),true);return j.id();
    }
    private void matchBranchMovement(AppUserPrincipal actor,AppUserPrincipal reviewer,UUID journal,String reference,BigDecimal amount) {
        UUID statement=service.importStatement(actor,new StatementCommand(UUID.randomUUID(),bank,format,DAY.plusDays(1),DAY.plusDays(1),
            new BigDecimal("100.00"),new BigDecimal("100.00").add(amount),reference+".csv","Synthetic independent bank source",
            "date,reference,amount,kind\n2026-10-02,"+reference+","+amount.toPlainString()+",RECEIPT"));
        UUID row=service.rows(actor,statement,0).rows().getFirst().id();UUID match=service.proposeMatch(actor,"EXACT","Verified journal money line",List.of(new Allocation(row,journalLine(journal),amount)));
        service.reviewMatch(reviewer,match,true,"Independent branch statement matching");UUID certificate=service.certify(actor,bank,DAY.plusDays(1),"STATEMENT",new BigDecimal("100.00").add(amount),"Reviewed bank closing balance");
        service.reviewCertificate(reviewer,certificate,"Independent branch bank certificate");
    }
    @Test void institutionFinancialSourceSumsExactCentsAndRetainsOnlyMinimalLineageAndEssentialCashProof() {
        opening();station("B2");var b2maker=operator("B2");var b2checker=operator("B2");secondBranchOpening(b2maker,b2checker);
        UUID compound=compoundCash(),version=UUID.randomUUID();var cash=mock(CashFlowReconciliationSource.class);
        when(cash.reviewedAllocations(any(),eq(List.of(compound)),any())).thenReturn(new CashFlowReconciliationSource.Coverage(List.of(cashVersion(compound,version)),List.of()));enableSources(null,cash);
        matchBranchMovement(maker,checker,compound,"CASH-ALLOCATED",new BigDecimal("10.00"));
        when(clock.now()).thenReturn(NOW.plusMinutes(1));UUID first=service.proposeClose(maker,period,"B1 independently prepared",false);service.approveClose(checker,first,"B1 independent closing approval");
        UUID cent=gl.draftManual(b2maker,new JournalCommand(UUID.randomUUID(),"B2-CENT",DAY.plusDays(1),"Synthetic exact cent source",null,List.of(new Line(bank,new BigDecimal("0.01"),BigDecimal.ZERO),new Line(capital,BigDecimal.ZERO,new BigDecimal("0.01"))))).id();
        gl.approve(b2checker,cent,"Independent cent posting");gl.post(b2checker,cent,false);matchBranchMovement(b2maker,b2checker,cent,"B2-CENT",new BigDecimal("0.01"));
        when(clock.now()).thenReturn(NOW.plusMinutes(2));UUID second=service.proposeClose(b2maker,period,"B2 independently prepared",false);service.approveClose(b2checker,second,"B2 independent closing approval");service.completeInstitutionClose(checker,period,"Reviewed B1 and B2 sources");
        when(claims.effectiveClaims(eq(checker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.FINANCIAL_REPORTS_VIEW,UserClaim.FINANCIAL_REPORTS_INSTITUTION));
        var combined=tx.execute(status->service.finalizedInstitutionSnapshotForPublication(checker,period));
        assertThat(combined.dimension()).isEqualTo("INSTITUTION");assertThat(combined.periodClosed()).isTrue();assertThat(combined.branches()).extracting(BranchCloseSource::reviewId).containsExactly(first,second);
        assertThat(combined.branches().get(0).recordedCutoff().toInstant()).isEqualTo(NOW.plusMinutes(1).toInstant());assertThat(combined.branches().get(1).recordedCutoff().toInstant()).isEqualTo(NOW.plusMinutes(2).toInstant());
        var json=JsonMapper.builder().enable(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).findAndAddModules().build().readTree(combined.snapshot());
        var bankRow=java.util.stream.StreamSupport.stream(json.get("accounts").spliterator(),false).filter(n->n.get("id").asText().equals(bank.toString())).findFirst().orElseThrow();
        assertThat(bankRow.get("opening").decimalValue()).isEqualByComparingTo("200.00");assertThat(bankRow.get("period_debit").decimalValue()).isEqualByComparingTo("10.01");assertThat(bankRow.get("closing").decimalValue()).isEqualByComparingTo("210.01");
        assertThat(json.get("cashFlowAllocations").get("versions").size()).isEqualTo(1);assertThat(json.get("cashFlowAllocations").get("versions").get(0).get("id").asText()).isEqualTo(version.toString());
        assertThat(json.get("automaticCashMovements").size()).isEqualTo(1);assertThat(json.get("automaticCashMovements").get(0).get("counterpart_movement").decimalValue()).isEqualByComparingTo("-0.01");
        assertThat(json.get("recordedCutoff")).isNull();assertThat(json.get("statementEvidence")).isNull();assertThat(json.get("certificates")).isNull();assertThat(json.get("retainedDifferences")).isNull();assertThat(combined.checksum()).isEqualTo(ReconciliationService.sha(combined.snapshot()));
        assertThatThrownBy(()->service.close(checker,second)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->service.finalizedInstitutionSnapshotForPublication(checker,period)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        when(claims.effectiveClaims(eq(checker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.FINANCIAL_REPORTS_VIEW));
        assertThatThrownBy(()->tx.execute(status->service.finalizedInstitutionSnapshotForPublication(checker,period))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void unreviewedActiveBranchesAndRevokedWorkspaceCannotPublishInstitutionAggregates() {
        opening();UUID own=balancedTimingClose(period,DAY.plusDays(1),"INSTITUTION-BASE");
        var source=tx.execute(status->service.finalizedInstitutionSnapshotForPublication(maker,period));station("B2");
        assertThatThrownBy(()->tx.execute(status->service.finalizedInstitutionSnapshotForPublication(maker,period))).hasMessage("reconciliation.error.branchCoverage");
        assertThat(service.finalizedSnapshot(maker,own).snapshot()).isNotBlank();assertThat(source.branches()).hasSize(1);
        jdbc.update("update sacco_stations set active=false where sacco_id=? and station_id='B1'",institution);
        assertThatThrownBy(()->tx.execute(status->service.finalizedInstitutionSnapshotForPublication(maker,period))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void publicationMetadataPinBlocksEarlierGapSetupUntilIndependentReopening() throws Exception {
        opening();balancedTimingClose(period,DAY.plusDays(1),"SETUP-FIRST");when(clock.today()).thenReturn(DAY.plusDays(3));when(clock.now()).thenReturn(NOW.plusDays(2));
        UUID later=gl.createPeriod(maker,DAY.plusDays(3),DAY.plusDays(3));UUID retained=balancedTimingClose(later,DAY.plusDays(3),"SETUP-LATER");String frozen=service.finalizedSnapshot(maker,retained).snapshot();
        when(clock.now()).thenReturn(NOW.plusDays(2).plusMinutes(1));var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var writerStarted=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var publication=pool.submit(()->tx.execute(status->{var snapshot=service.finalizedInstitutionSnapshotForPublication(maker,later);entered.countDown();try{assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new IllegalStateException(e);}return snapshot;}));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            var writerPid=new java.util.concurrent.atomic.AtomicInteger();
            var writer=pool.submit(()->tx.execute(status->{writerPid.set(jdbc.queryForObject("select pg_backend_pid()",Integer.class));writerStarted.countDown();return gl.createPeriod(maker,DAY.plusDays(2),DAY.plusDays(2));}));
            assertThat(writerStarted.await(10,TimeUnit.SECONDS)).isTrue();boolean waiting=false;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(!waiting&&System.nanoTime()<deadline) {waiting=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_locks where pid=? and locktype='advisory' and not granted)",Boolean.class,writerPid.get()));if(!waiting)Thread.sleep(10);}
            assertThat(waiting).isTrue();assertThatThrownBy(()->writer.get(250,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();assertThat(publication.get(10,TimeUnit.SECONDS).periodClosed()).isTrue();
            assertThatThrownBy(()->writer.get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasRootCauseMessage("accounting.error.earlierPeriodNeedsReopen");
        } finally {release.countDown();}
        assertThat(jdbc.queryForObject("select count(*) from accounting_period where sacco_id=? and starts_on=?",Integer.class,institution,DAY.plusDays(2))).isZero();
        UUID reopen=service.proposeClose(third,later,"Explicit reopening for earlier gap setup",true);service.approveClose(fourth,reopen,"Independent review of cumulative impact");
        verify(reopenListener).periodReopened(eq(fourth),eq(later),eq(DAY.plusDays(3)),contains("earlier gap setup"));
        when(clock.now()).thenReturn(NOW.plusDays(2).plusMinutes(2));UUID gap=gl.createPeriod(maker,DAY.plusDays(2),DAY.plusDays(2));assertThat(gap).isNotNull();
        when(clock.now()).thenReturn(NOW.plusDays(2).plusMinutes(3));balancedTimingClose(gap,DAY.plusDays(2),"SETUP-GAP");
        assertThatThrownBy(()->tx.execute(status->service.finalizedInstitutionSnapshotForPublication(maker,later))).hasMessage("reconciliation.error.approvalRequired");
        when(clock.now()).thenReturn(NOW.plusDays(2).plusMinutes(4));UUID fresh=balancedTimingClose(later,DAY.plusDays(3),"SETUP-LATER-FRESH");
        assertThat(tx.execute(status->service.finalizedInstitutionSnapshotForPublication(maker,later)).branches()).extracting(BranchCloseSource::reviewId).containsExactly(fresh);
        assertThat(service.finalizedSnapshot(maker,retained).snapshot()).isEqualTo(frozen);assertThat(service.finalizedSnapshot(maker,retained).periodClosed()).isFalse();
    }
    @Test void retainedLegacyEarlierPeriodMetadataKeepsOldLaterSourceIneligibleEvenAfterGapClosing() {
        opening();balancedTimingClose(period,DAY.plusDays(1),"LEGACY-FIRST");when(clock.today()).thenReturn(DAY.plusDays(3));when(clock.now()).thenReturn(NOW.plusDays(2));
        UUID later=gl.createPeriod(maker,DAY.plusDays(3),DAY.plusDays(3));UUID old=balancedTimingClose(later,DAY.plusDays(3),"LEGACY-LATER");String frozen=service.finalizedSnapshot(maker,old).snapshot();
        when(clock.now()).thenReturn(NOW.plusDays(2).plusMinutes(1));
        // Models retained metadata written before the new owning-service guard, never an exposed mutation route.
        UUID gap=tx.execute(status->{var books=new GeneralLedgerRepository(jdbc);books.lockAccounts(institution);return books.createPeriod(institution,DAY.plusDays(2),DAY.plusDays(2),policy,maker.getMemberId(),NOW.plusDays(2).plusMinutes(1));});
        when(clock.now()).thenReturn(NOW.plusDays(2).plusMinutes(2));balancedTimingClose(gap,DAY.plusDays(2),"LEGACY-GAP");
        assertThat(service.finalizedSnapshot(maker,old).periodClosed()).isFalse();assertThat(service.finalizedSnapshot(maker,old).snapshot()).isEqualTo(frozen);
        assertThatThrownBy(()->tx.execute(status->service.finalizedInstitutionSnapshotForPublication(maker,later))).hasMessage("reconciliation.error.branchCoverage");
        assertThatThrownBy(()->tx.execute(status->service.finalizedSnapshotForPublication(maker,old))).hasMessage("reconciliation.error.approvalRequired");
    }


    private UUID datedMovement(String reference,LocalDate date,String value) {
        when(clock.today()).thenReturn(date.isAfter(DAY.plusDays(1))?date:DAY.plusDays(1));
        if(date.isAfter(DAY.plusDays(1))&&!jdbc.queryForObject("select exists(select 1 from accounting_period where sacco_id=? and ? between starts_on and ends_on)",Boolean.class,institution,date))gl.createPeriod(maker,DAY.plusDays(2),date);
        BigDecimal amount=new BigDecimal(value);UUID id=gl.draftManual(maker,new JournalCommand(UUID.randomUUID(),reference,date,"Synthetic dated movement",null,List.of(new Line(bank,amount,BigDecimal.ZERO),new Line(capital,BigDecimal.ZERO,amount)))).id();
        gl.approve(checker,id,"Independent dated source review");return gl.post(checker,id,false).id();
    }
    @Test void differentDateMatchesRequireExactIndependentlyReviewedTimingPairAndFreezeIt() {
        opening();UUID journal=datedMovement("TIMED",DAY.plusDays(2),"1.01"),other=datedMovement("OTHER",DAY.plusDays(2),"1.01"),statement=importOne("TIMED","1.01"),line=statementLine(statement);
        var part=new Allocation(line,journalLine(journal),new BigDecimal("1.01"));
        for(String kind:List.of("EXACT","SPLIT","BATCH"))assertThatThrownBy(()->service.proposeMatch(maker,kind,"No timing source",List.of(part))).hasMessage("reconciliation.error.timingReview");
        UUID generic=service.assignException(maker,line,"TIMING",checker.getMemberId(),"Unlinked retained timing difference");service.reviewException(checker,generic,"Independent generic difference");
        assertThatThrownBy(()->service.proposeMatch(maker,"EXACT","Unrelated timing decision",List.of(part))).hasMessage("reconciliation.error.timingReview");
        UUID timing=service.assignTimingExceptionToStaff(maker,line,journalLine(journal),checker.getMemberId().toString(),"Verified source dates and reference");
        assertThatThrownBy(()->service.proposeMatch(maker,"EXACT","Unreviewed pair",List.of(part))).hasMessage("reconciliation.error.timingReview");
        assertThatThrownBy(()->service.reviewException(maker,timing,"Self review")).hasMessage("reconciliation.error.independentReview");
        service.reviewException(checker,timing,"Independent source-linked timing evidence");
        UUID repeated=service.assignTimingExceptionToStaff(third,line,journalLine(journal),fourth.getMemberId().toString(),"Separately reviewed same source pair");service.reviewException(fourth,repeated,"Independent retained duplicate proof");
        assertThat(new ReconciliationRepository(jdbc).reviewedTimingPairs(institution,"B1",List.of(part))).containsExactly(line+"/"+journalLine(journal));
        var retained=service.exceptions(checker,0).rows().stream().filter(e->e.id().equals(timing)).findFirst().orElseThrow();assertThat(retained.timing().journal()).isEqualTo(journal);assertThat(retained.timing().statementDate()).isEqualTo(DAY.plusDays(1));assertThat(retained.timing().journalDate()).isEqualTo(DAY.plusDays(2));assertThat(retained.timing().statementReference()).isEqualTo("TIMED");assertThat(retained.timing().statementAmount()).isEqualByComparingTo("1.01");assertThat(retained.timing().amount()).isEqualByComparingTo("1.01");
        assertThatThrownBy(()->service.proposeMatch(maker,"SPLIT","Different voucher",List.of(new Allocation(line,journalLine(other),new BigDecimal("1.01"))))).hasMessage("reconciliation.error.timingReview");
        UUID match=service.proposeMatch(maker,"EXACT","Reviewed dated pair",List.of(part));service.reviewMatch(checker,match,true,"Independent exact match");
        assertThat(service.rows(maker,statement,0).rows().getFirst().status()).isEqualTo("MATCHED");assertThatThrownBy(()->service.proposeMatch(maker,"SPLIT","Second allocation",List.of(new Allocation(line,journalLine(journal),new BigDecimal("0.01"))))).hasMessage("reconciliation.error.overmatch");
        certify(new BigDecimal("101.01"));UUID close=service.proposeClose(maker,period,"Reviewed timing source close",false);service.approveClose(checker,close,"Independent close");service.completeInstitutionClose(checker,period,"Scoped institution completion");
        String bytes=service.finalizedSnapshot(maker,close).snapshot();assertThat(bytes).contains("timingSources",timing.toString(),journalLine(journal).toString());
        assertThatThrownBy(()->jdbc.update("delete from reconciliation_timing_source where exception_id=?",timing)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(service.finalizedSnapshot(maker,close).snapshot()).isEqualTo(bytes);
    }
    @Test void timingPairRejectsForeignDirectionSameDateAndOverThirtyDaysWithoutOrphanEvidence() {
        opening();UUID statement=importOne("TIME","1.00"),line=statementLine(statement),same=post("TIME","1.00",false),wrong=outgoing("WRONG","1.00");
        long before=jdbc.queryForObject("select count(*) from reconciliation_exception where sacco_id=?",Long.class,institution);
        assertThatThrownBy(()->service.assignTimingExceptionToStaff(maker,line,journalLine(same),checker.getMemberId().toString(),"Same date")).hasMessage("reconciliation.error.timingWindow");
        assertThatThrownBy(()->service.assignTimingExceptionToStaff(maker,line,journalLine(wrong),checker.getMemberId().toString(),"Wrong direction")).hasMessage("reconciliation.error.direction");
        station("B2");var foreign=operator("B2");UUID foreignOpening=secondBranchOpening(foreign,operator("B2"));assertThatThrownBy(()->service.assignTimingExceptionToStaff(maker,line,journalLine(foreignOpening),checker.getMemberId().toString(),"Foreign voucher")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        when(clock.today()).thenReturn(DAY.plusDays(32));gl.createPeriod(maker,DAY.plusDays(2),DAY.plusDays(32));UUID distant=datedMovement("DISTANT",DAY.plusDays(32),"1.00");
        assertThatThrownBy(()->service.assignTimingExceptionToStaff(maker,line,journalLine(distant),checker.getMemberId().toString(),"Outside reviewed window")).hasMessage("reconciliation.error.timingWindow");
        assertThatThrownBy(()->service.proposeMatch(maker,"SPLIT","Unbounded timing",List.of(new Allocation(line,journalLine(distant),BigDecimal.ONE)))).hasMessage("reconciliation.error.timingWindow");
        assertThat(jdbc.queryForObject("select count(*) from reconciliation_exception where sacco_id=?",Long.class,institution)).isEqualTo(before);
    }
    @Test void timingReviewAndNewMatchingRejectAChangedVoucherReversalAndAuditRollsBackItsLink() {
        opening();UUID journal=datedMovement("CHANGE",DAY.plusDays(2),"1.00"),statement=importOne("CHANGE","1.00"),line=statementLine(statement);
        doThrow(new IllegalStateException("Synthetic timing audit failure")).when(audit).logEvent(anyString(),any(),eq("EXCEPTION_ASSIGNED"),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),any());
        assertThatThrownBy(()->service.assignTimingExceptionToStaff(maker,line,journalLine(journal),checker.getMemberId().toString(),"Atomic timing evidence")).hasMessage("Synthetic timing audit failure");
        assertThat(jdbc.queryForObject("select count(*) from reconciliation_exception where sacco_id=?",Long.class,institution)).isZero();reset(audit);
        UUID pending=service.assignTimingExceptionToStaff(maker,line,journalLine(journal),checker.getMemberId().toString(),"Source before correction");
        UUID reviewed=service.assignTimingExceptionToStaff(third,line,journalLine(journal),fourth.getMemberId().toString(),"Separate source before correction");service.reviewException(fourth,reviewed,"Independent original timing pair");
        var part=new Allocation(line,journalLine(journal),BigDecimal.ONE);UUID draft=service.proposeMatch(maker,"EXACT","Pending dated allocation",List.of(part));
        when(clock.now()).thenReturn(NOW.plusMinutes(1));UUID reversal=gl.reverse(third,journal,UUID.randomUUID(),DAY.plusDays(2),"Verified correction","Evidence").id();gl.approve(fourth,reversal,"Independent reversal");gl.post(fourth,reversal,false);
        assertThatThrownBy(()->service.reviewException(checker,pending,"Stale source review")).hasMessage("reconciliation.error.staleEvidence");
        assertThatThrownBy(()->service.reviewMatch(checker,draft,true,"Stale match")).hasMessage("reconciliation.error.timingReview");
        assertThatThrownBy(()->service.proposeMatch(maker,"REVERSAL","Earlier timing proof cannot discharge correction",List.of(part))).hasMessage("reconciliation.error.timingReview");
        UUID current=service.assignTimingExceptionToStaff(maker,line,journalLine(journal),checker.getMemberId().toString(),"Linked corrected source");service.reviewException(checker,current,"Independent corrected timing proof");
        UUID corrected=service.proposeMatch(maker,"REVERSAL","Explicit corrected pair",List.of(part));service.reviewMatch(checker,corrected,true,"Independent corrected pair review");assertThat(service.rows(maker,statement,0).rows().getFirst().status()).isEqualTo("REVERSED");
    }
    @Test void databaseTimingGuardRejectsDirectApprovalAndNonAtomicForgedLink() {
        opening();UUID journal=datedMovement("DB-TIME",DAY.plusDays(2),"0.01"),statement=importOne("DB-TIME","0.01"),line=statementLine(statement);var repo=new ReconciliationRepository(jdbc);
        UUID draft=tx.execute(s->repo.match(institution,"B1",bank,"EXACT",maker.getMemberId(),"Synthetic database-only negative fixture",null,List.of(new Allocation(line,journalLine(journal),new BigDecimal("0.01"))),NOW));
        assertThatThrownBy(()->tx.executeWithoutResult(s->repo.decideMatch(draft,checker.getMemberId(),true,"No timing proof",NOW))).satisfies(failure->{Throwable root=failure;while(root.getCause()!=null)root=root.getCause();assertThat(root).isInstanceOf(java.sql.SQLException.class);var sql=(java.sql.SQLException)root;assertThat(sql.getSQLState()).isEqualTo("P0001");assertThat(sql.getMessage()).contains("Date-mismatched allocations require independent reviewed source-linked timing evidence");});
        assertThat(repo.allocated("statement_line_id",line)).isZero();
        UUID generic=service.assignException(maker,line,"TIMING",checker.getMemberId(),"Unlinked immutable exception");
        assertThatThrownBy(()->tx.executeWithoutResult(s->repo.timingSource(generic,journalLine(journal),false,NOW))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("select count(*) from reconciliation_timing_source t join reconciliation_exception e on e.id=t.exception_id where e.sacco_id=?",Long.class,institution)).isZero();
    }

    @Test void reviewedOpeningBalancesNeverMasqueradeAsStatementMoneyMovements() {
        UUID opening=opening(),statement=importOne("OPENING","100.00"),line=statementLine(statement);
        assertThat(service.candidates(maker,statement,0).rows()).noneMatch(c->c.journal().equals(opening));
        assertThatThrownBy(()->service.proposeMatch(maker,"SPLIT","Opening is not a receipt",List.of(new Allocation(line,journalLine(opening),new BigDecimal("100.00"))))).hasMessage("reconciliation.error.matchSource");
        assertThatThrownBy(()->service.assignTimingExceptionToStaff(maker,line,journalLine(opening),checker.getMemberId().toString(),"Not a timing receipt")).hasMessage("reconciliation.error.matchSource");
        var repo=new ReconciliationRepository(jdbc);assertThatThrownBy(()->tx.execute(s->repo.match(institution,"B1",bank,"SPLIT",maker.getMemberId(),"Direct opening negative fixture",null,List.of(new Allocation(line,journalLine(opening),new BigDecimal("100.00"))),NOW))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("select count(*) from reconciliation_match where sacco_id=?",Long.class,institution)).isZero();
    }

    private CashFlowAllocationService actualCashProvider() {
        var mapper=JsonMapper.builder().findAndAddModules().build();
        var reports=proxy(new LedgerReportService(new LedgerReportRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc)),policies,directory,claims,institutions,clock),manager);
        var allocations=proxy(new CashFlowAllocationService(new CashFlowAllocationRepository(jdbc,mapper),reports,clock,audit,mapper,List.of()),manager);
        enableSources(null,new CashFlowClosingAdapter(allocations));return allocations;
    }
    private UUID realCashVersion(CashFlowAllocationService allocations,AppUserPrincipal actor,AppUserPrincipal reviewer,UUID journal) {
        UUID id=allocations.draft(actor,journal,UUID.randomUUID(),List.of(new CashFlowAllocation.Split(journalLine(journal),capital,CashFlowAllocation.Activity.FINANCING,new BigDecimal("10.00"))),"Verified synthetic capital and cash source","Verified synthetic owner-funded noncash expense");
        allocations.approve(reviewer,id,"Independent actual cash allocation review");return id;
    }
    @Test void actualReviewedCashProviderFreezesItsExactVersionAndClosingOnlyClaimsCannotReadFinanceRegistry() {
        opening();UUID journal=compoundCash();var allocations=actualCashProvider();UUID allocation=realCashVersion(allocations,maker,checker,journal);
        matchBranchMovement(maker,checker,journal,"CASH-ALLOCATED",new BigDecimal("10.00"));
        when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(EnumSet.of(UserClaim.ACCOUNTING_CLOSING_CREATE,UserClaim.ACCOUNTING_CLOSING_VIEW));
        when(claims.effectiveClaims(eq(checker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(EnumSet.of(UserClaim.ACCOUNTING_CLOSING_APPROVE,UserClaim.ACCOUNTING_CLOSING_VIEW,UserClaim.ACCOUNTING_CLOSING_INSTITUTION));
        assertThatThrownBy(()->allocations.versions(maker,0)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        UUID close=service.proposeClose(maker,period,"Actual reviewed cash provider close",false);service.approveClose(checker,close,"Independent frozen allocation review");service.completeInstitutionClose(checker,period,"Actual source proof completion");
        String frozen=service.finalizedSnapshot(maker,close).snapshot();var json=JsonMapper.builder().findAndAddModules().build().readTree(frozen);assertThat(json.path("cashFlowAllocations").path("missingJournalIds").isEmpty()).isTrue();assertThat(json.path("cashFlowAllocations").path("versions").get(0).path("id").asText()).isEqualTo(allocation.toString());assertThat(json.path("automaticCashMovements").isEmpty()).isTrue();String publicationChecksum=tx.execute(s->service.finalizedSnapshotForPublication(maker,close).checksum());assertThat(publicationChecksum).isEqualTo(ReconciliationService.sha(frozen));
        when(clock.now()).thenReturn(NOW.plusMinutes(1));UUID later=realCashVersion(allocations,third,fourth,journal);assertThat(later).isNotEqualTo(allocation);
        assertThatThrownBy(()->tx.execute(s->service.finalizedSnapshotForPublication(maker,close))).hasMessage("reconciliation.error.staleEvidence");assertThat(service.finalizedSnapshot(maker,close).snapshot()).isEqualTo(frozen);
    }
    @Test void actualCashProviderRetainsMissingAndCutoffCoverageWithoutInventingAnAllocation() {
        opening();UUID journal=compoundCash();var allocations=actualCashProvider();UUID draft=allocations.draft(maker,journal,UUID.randomUUID(),List.of(new CashFlowAllocation.Split(journalLine(journal),capital,CashFlowAllocation.Activity.FINANCING,new BigDecimal("10.00"))),"Unreviewed synthetic cash classification","Synthetic noncash source evidence");
        var missing=allocations.reviewedForClosing(maker,List.of(journal),NOW);assertThat(missing.versions()).isEmpty();assertThat(missing.missingJournalIds()).containsExactly(journal);
        assertThatThrownBy(()->allocations.reviewedForClosing(maker,List.of(journal),NOW.minusSeconds(1))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        matchBranchMovement(maker,checker,journal,"CASH-ALLOCATED",new BigDecimal("10.00"));UUID close=service.proposeClose(maker,period,"Known unresolved cash classification",false);service.approveClose(checker,close,"Independent source coverage review");service.completeInstitutionClose(checker,period,"Explicit unresolved cash coverage");
        String retained=service.finalizedSnapshot(maker,close).snapshot();assertThat(retained).contains("missingJournalIds",journal.toString()).doesNotContain(draft.toString());
        when(clock.now()).thenReturn(NOW.plusMinutes(1));allocations.approve(checker,draft,"Independent later cash classification");
        assertThatThrownBy(()->tx.execute(s->service.finalizedSnapshotForPublication(maker,close))).hasMessage("reconciliation.error.staleEvidence");assertThat(service.finalizedSnapshot(maker,close).snapshot()).isEqualTo(retained);
    }
    @Test void actualCashProviderMinimalForeignProofSupportsInstitutionFinanceWithoutForeignRegistryAccess() {
        opening();station("B2");var b2maker=operator("B2");var b2checker=operator("B2");secondBranchOpening(b2maker,b2checker);var allocations=actualCashProvider();
        var b1maker=maker;var b1checker=checker;maker=b2maker;checker=b2checker;UUID journal=compoundCash();maker=b1maker;checker=b1checker;
        UUID allocation=realCashVersion(allocations,b2maker,b2checker,journal);matchBranchMovement(b2maker,b2checker,journal,"CASH-ALLOCATED",new BigDecimal("10.00"));
        UUID b2close=service.proposeClose(b2maker,period,"Actual B2 allocation coverage",false);service.approveClose(b2checker,b2close,"Independent B2 close");balancedTimingClose(period,DAY.plusDays(1),"ACTUAL-E-B1");
        when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(EnumSet.of(UserClaim.FINANCIAL_REPORTS_VIEW,UserClaim.FINANCIAL_REPORTS_INSTITUTION));
        var frozen=tx.execute(s->service.finalizedInstitutionSnapshotForPublication(maker,period));assertThat(frozen.branches()).hasSize(2);assertThat(frozen.snapshot()).contains(allocation.toString()).doesNotContain("statementEvidence","retainedDifferences");
        assertThatThrownBy(()->allocations.source(maker,journal)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        when(clock.now()).thenReturn(NOW.plusMinutes(1));realCashVersion(allocations,b2maker,b2checker,journal);
        assertThatThrownBy(()->tx.execute(s->service.finalizedInstitutionSnapshotForPublication(maker,period))).hasMessage("reconciliation.error.staleEvidence");assertThat(ReconciliationService.sha(frozen.snapshot())).isEqualTo(frozen.checksum());
        when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(EnumSet.of(UserClaim.FINANCIAL_REPORTS_VIEW));
        assertThatThrownBy(()->tx.execute(s->service.finalizedInstitutionSnapshotForPublication(maker,period))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    private SaccoRegistryService cohortOwner(java.util.concurrent.atomic.AtomicBoolean directoryRead) {
        var registry=mock(com.sacco.mvp.repository.RegisteredSaccoRepository.class);
        var stations=mock(com.sacco.mvp.repository.SaccoStationRepository.class);
        var settings=mock(com.sacco.mvp.repository.SaccoSettingsRepository.class);
        when(registry.findById(institution)).thenAnswer(call->{directoryRead.set(true);return Optional.of(RegisteredSacco.builder().saccoId(institution).saccoName("Synthetic cohort").active(true).build());});
        when(stations.findBySaccoIdOrderByStationIdAsc(institution)).thenAnswer(call->jdbc.query("select id,sacco_id,station_id,active from sacco_stations where sacco_id=? order by station_id",(r,n)->SaccoStation.builder().id(r.getObject("id",UUID.class)).saccoId(r.getString("sacco_id")).stationId(r.getString("station_id")).active(r.getBoolean("active")).build(),institution));
        when(stations.save(any(SaccoStation.class))).thenAnswer(call->{SaccoStation s=call.getArgument(0);jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,access_status,created_at,updated_at) values(?,?,?,?,'ACTIVE',now(),now()) on conflict(id) do update set active=excluded.active",s.getId(),s.getSaccoId(),s.getStationId(),s.isActive());return s;});
        when(settings.findById(institution)).thenReturn(Optional.empty());
        return proxy(new SaccoRegistryService(registry,stations,settings,mock(SaccoLogoStorageService.class),mock(SmsUnitTransactionService.class),audit,jdbc),manager);
    }
    @Test void actualRegistryCohortSyncWaitsBeforeDirectoryReadAndRetainsHistoricalInactiveBranch() throws Exception {
        opening();UUID review=balancedTimingClose(period,DAY.plusDays(1),"COHORT");String frozen=service.finalizedSnapshot(maker,review).snapshot();
        var directoryRead=new java.util.concurrent.atomic.AtomicBoolean();var owner=cohortOwner(directoryRead);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var writerStarted=new CountDownLatch(1);var writerPid=new java.util.concurrent.atomic.AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var publication=pool.submit(()->tx.execute(status->{var snapshot=service.finalizedInstitutionSnapshotForPublication(maker,period);entered.countDown();try{assertThat(release.await(40,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new IllegalStateException(e);}return snapshot;}));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            var writer=pool.submit(()->tx.execute(status->{writerPid.set(jdbc.queryForObject("select pg_backend_pid()",Integer.class));writerStarted.countDown();owner.updateStationsOnly(institution,"B1,B2");return true;}));
            assertThat(writerStarted.await(10,TimeUnit.SECONDS)).isTrue();boolean waiting=false;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(!waiting&&System.nanoTime()<deadline){waiting=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_locks where pid=? and locktype='advisory' and not granted)",Boolean.class,writerPid.get()));if(!waiting)Thread.sleep(10);}
            assertThat(waiting).isTrue();assertThat(directoryRead.get()).isFalse();assertThatThrownBy(()->writer.get(250,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            // The metadata pin belongs only to the publishing institution.
            String foreign="OTHER-"+UUID.randomUUID();jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,?,true,now(),now())",foreign,"Synthetic unrelated cohort");
            jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,access_status,created_at,updated_at) values(?,?,?,true,'ACTIVE',now(),now())",UUID.randomUUID(),foreign,"B1");
            release.countDown();assertThat(publication.get(10,TimeUnit.SECONDS).branches()).extracting(BranchCloseSource::branch).containsExactly("B1");assertThat(writer.get(10,TimeUnit.SECONDS)).isTrue();
        } finally {release.countDown();}
        assertThat(directoryRead.get()).isTrue();assertThat(jdbc.queryForObject("select active from sacco_stations where sacco_id=? and station_id='B1'",Boolean.class,institution)).isTrue();
        assertThat(new ReconciliationRepository(jdbc).branches(institution,DAY.plusDays(1))).containsExactly("B1","B2");
        assertThatThrownBy(()->tx.execute(status->service.finalizedInstitutionSnapshotForPublication(maker,period))).hasMessage("reconciliation.error.branchCoverage");
        assertThat(service.finalizedSnapshot(maker,review).snapshot()).isEqualTo(frozen);
        jdbc.update("update sacco_stations set active=false where sacco_id=? and station_id='B1'",institution);
        assertThat(new ReconciliationRepository(jdbc).branches(institution,DAY.plusDays(1))).containsExactly("B1","B2");
        assertThatThrownBy(()->jdbc.update("delete from sacco_stations where sacco_id=? and station_id='B1'",institution)).isInstanceOf(org.springframework.dao.DataAccessException.class).hasStackTraceContaining("Retained financial branch identity cannot be changed or deleted");
    }
    @Test void directCohortAccessMutationWaitsForSetupPinAndFinancialIdentityCannotMove() throws Exception {
        opening();var held=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);var pid=new java.util.concurrent.atomic.AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var reader=pool.submit(()->tx.execute(status->{new ReconciliationRepository(jdbc).lockSetupForPublication(institution);held.countDown();try{assertThat(release.await(40,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new IllegalStateException(e);}return true;}));
            assertThat(held.await(10,TimeUnit.SECONDS)).isTrue();var writer=pool.submit(()->tx.execute(status->{pid.set(jdbc.queryForObject("select pg_backend_pid()",Integer.class));started.countDown();return jdbc.update("update sacco_stations set access_status='SUSPENDED' where sacco_id=? and station_id='B1'",institution);}));
            assertThat(started.await(10,TimeUnit.SECONDS)).isTrue();boolean waiting=false;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(!waiting&&System.nanoTime()<deadline){waiting=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_locks where pid=? and locktype='advisory' and not granted)",Boolean.class,pid.get()));if(!waiting)Thread.sleep(10);}
            assertThat(waiting).isTrue();assertThatThrownBy(()->writer.get(250,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);release.countDown();assertThat(reader.get(10,TimeUnit.SECONDS)).isTrue();assertThat(writer.get(10,TimeUnit.SECONDS)).isEqualTo(1);
        } finally {release.countDown();}
        assertThatThrownBy(()->jdbc.update("update sacco_stations set station_id='MOVED' where sacco_id=? and station_id='B1'",institution)).isInstanceOf(org.springframework.dao.DataAccessException.class).hasStackTraceContaining("Retained financial branch identity cannot be changed or deleted");
    }

    @Test void institutionalPublicationRefreshesBranchAccessAfterWaitingForCohortMutation() throws Exception {
        opening();UUID review=balancedTimingClose(period,DAY.plusDays(1),"SUSPEND-PUB");String frozen=service.finalizedSnapshot(maker,review).snapshot();
        var changed=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);var pid=new java.util.concurrent.atomic.AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var suspender=pool.submit(()->tx.execute(status->{new GeneralLedgerRepository(jdbc).lockAccounts(institution);jdbc.update("update sacco_stations set access_status='SUSPENDED' where sacco_id=? and station_id='B1'",institution);changed.countDown();try{assertThat(release.await(40,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new IllegalStateException(e);}return true;}));
            assertThat(changed.await(10,TimeUnit.SECONDS)).isTrue();
            var publisher=pool.submit(()->tx.execute(status->{pid.set(jdbc.queryForObject("select pg_backend_pid()",Integer.class));started.countDown();return service.finalizedInstitutionSnapshotForPublication(maker,period);}));
            assertThat(started.await(10,TimeUnit.SECONDS)).isTrue();boolean waiting=false;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(!waiting&&System.nanoTime()<deadline){waiting=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_locks where pid=? and locktype='advisory' and not granted)",Boolean.class,pid.get()));if(!waiting)Thread.sleep(10);}
            assertThat(waiting).isTrue();assertThatThrownBy(()->publisher.get(250,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();assertThat(suspender.get(10,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->publisher.get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasRootCauseInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        } finally {release.countDown();}
        assertThat(jdbc.queryForObject("select snapshot_json from accounting_close_review where id=?",String.class,review)).isEqualTo(frozen);
    }

    private StatementCommand zeroCommand(UUID account,LocalDate from,LocalDate through,String amount) {return new StatementCommand(UUID.randomUUID(),account,format,from,through,new BigDecimal(amount),new BigDecimal(amount),"ignored-form-name.csv","Synthetic actual uploaded no-movement statement","");}
    private org.springframework.mock.web.MockMultipartFile zeroFile() {return new org.springframework.mock.web.MockMultipartFile("statementFile","zero.csv","text/csv","date,reference,amount,kind\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    @Test void uploadedZeroMovementStatementRetainsExactFileWithoutInventingRowsOrJournalsAndCloses() {
        opening();actualCashProvider();var command=zeroCommand(bank,DAY,DAY.plusDays(1),"100.00");var file=zeroFile();assertThat(service.previewUploadedStatement(maker,command,file).rows()).isEmpty();
        UUID id=service.importUploadedStatement(maker,command,file);assertThat(service.importUploadedStatement(maker,command,file)).isEqualTo(id);
        var statement=service.statement(maker,id);assertThat(statement.noMovement()).isTrue();assertThat(statement.sourceAvailable()).isTrue();assertThat(statement.filename()).isEqualTo("zero.csv");assertThat(statement.checksum()).hasSize(64);
        assertThat(service.statementFile(maker,id).content()).isEqualTo("date,reference,amount,kind\r\n");assertThat(service.rows(maker,id,0).rows()).isEmpty();
        UUID certificate=service.certify(maker,bank,DAY.plusDays(1),"STATEMENT",new BigDecimal("100.00"),"Actual retained no-movement file");service.reviewCertificate(checker,certificate,"Independent source-file review");
        assertThat(jdbc.queryForObject("select statement_id from reconciliation_certificate_statement where certificate_id=?",UUID.class,certificate)).isEqualTo(id);
        UUID review=service.proposeClose(maker,period,"Synthetic exact no-movement closing",false);service.approveClose(checker,review,"Independent unchanged balance review");service.completeInstitutionClose(checker,period,"All synthetic branch evidence reviewed");
        var snapshot=service.finalizedSnapshot(maker,review);var data=JsonMapper.builder().findAndAddModules().build().readTree(snapshot.snapshot());
        assertThat(data.path("statementEvidence").get(0).path("no_movement").asBoolean()).isTrue();assertThat(data.path("certificates").get(0).path("source_statement_id").asString()).isEqualTo(id.toString());
        assertThat(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=? and station_id='B1'",Integer.class,institution)).isEqualTo(1);
        assertThat(tx.execute(status->service.finalizedInstitutionSnapshotForPublication(maker,period)).periodClosed()).isTrue();
    }
    @Test void zeroMovementRequiresActualUploadExactPeriodAndEqualBalances() {
        opening();var c=zeroCommand(bank,DAY,DAY.plusDays(1),"100.00");
        var pasted=new StatementCommand(c.key(),bank,format,c.from(),c.through(),c.opening(),c.closing(),"pasted.csv",c.evidence(),"date,reference,amount,kind");
        assertThatThrownBy(()->service.importStatement(maker,pasted)).hasMessage("reconciliation.error.zeroMovementFile");
        assertThatThrownBy(()->service.importUploadedStatement(maker,zeroCommand(bank,DAY.plusDays(1),DAY.plusDays(1),"100.00"),zeroFile())).hasMessage("reconciliation.error.zeroMovementPeriod");
        var changed=new StatementCommand(c.key(),bank,format,c.from(),c.through(),c.opening(),new BigDecimal("100.01"),c.filename(),c.evidence(),c.content());
        assertThatThrownBy(()->service.importUploadedStatement(maker,changed,zeroFile())).hasMessage("reconciliation.error.statementBalance");
        assertThat(jdbc.queryForObject("select count(*) from reconciliation_statement where sacco_id=?",Integer.class,institution)).isZero();
    }
    @Test void zeroMovementFileAndCertificateSourceAreImmutableAndImporterCannotReviewAnotherMakersCertificate() {
        opening();UUID id=service.importUploadedStatement(maker,zeroCommand(bank,DAY,DAY.plusDays(1),"100.00"),zeroFile());
        UUID certificate=service.certify(third,bank,DAY.plusDays(1),"STATEMENT",new BigDecimal("100.00"),"Synthetic retained source");
        assertThatThrownBy(()->service.reviewCertificate(maker,certificate,"Original file importer reviewing another maker")).hasMessage("reconciliation.error.independentReview");
        assertThatThrownBy(()->jdbc.update("update reconciliation_statement_file set content='changed' where statement_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class).hasStackTraceContaining("append-only");
        assertThatThrownBy(()->jdbc.update("delete from reconciliation_certificate_statement where certificate_id=?",certificate)).isInstanceOf(org.springframework.dao.DataAccessException.class).hasStackTraceContaining("append-only");
        assertThatThrownBy(()->jdbc.update("insert into reconciliation_statement_line(id,statement_id,row_number,effective_date,reference,amount,kind,duplicate) values(?,?,1,?,'FAKE',1,'RECEIPT',false)",UUID.randomUUID(),id,DAY.plusDays(1))).isInstanceOf(org.springframework.dao.DataAccessException.class).hasStackTraceContaining("atomically");
        service.reviewCertificate(checker,certificate,"Independent retained file and unchanged ledger");
    }
    @Test void invalidOrUnapprovedUploadedEvidenceAndChangedRetryAreRejectedAndAuditFailureRollsBack() throws Exception {
        opening();var c=zeroCommand(bank,DAY,DAY.plusDays(1),"100.00");
        var malformed=new org.springframework.mock.web.MockMultipartFile("statementFile","bad.csv","text/csv",new byte[]{(byte)0xc3,0x28});
        assertThatThrownBy(()->service.importUploadedStatement(maker,c,malformed)).hasMessage("reconciliation.error.uploadedFile");
        var fakeZero=new org.springframework.mock.web.MockMultipartFile("statementFile","fake.csv","text/csv","date,reference,amount,kind\n2026-10-02,R,0.00,RECEIPT".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(()->service.importUploadedStatement(maker,c,fakeZero)).hasMessage("reconciliation.error.money");
        UUID unapproved=service.proposeFormat(maker,"Unapproved upload format","Synthetic");var badFormat=new StatementCommand(c.key(),bank,unapproved,c.from(),c.through(),c.opening(),c.closing(),c.filename(),c.evidence(),c.content());
        assertThatThrownBy(()->service.importUploadedStatement(maker,badFormat,zeroFile())).hasMessage("reconciliation.error.formatApproval");
        doThrow(new IllegalStateException("Synthetic file audit failure")).when(audit).logEvent(anyString(),any(),eq("STATEMENT_IMPORTED"),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),anyMap());
        assertThatThrownBy(()->service.importUploadedStatement(maker,c,zeroFile())).hasMessage("Synthetic file audit failure");assertThat(jdbc.queryForObject("select count(*) from reconciliation_statement where sacco_id=?",Integer.class,institution)).isZero();reset(audit);
        UUID id=service.importUploadedStatement(maker,c,zeroFile());var changed=new org.springframework.mock.web.MockMultipartFile("statementFile","other.csv","text/csv",zeroFile().getBytes());
        assertThatThrownBy(()->service.importUploadedStatement(maker,c,changed)).hasMessage("reconciliation.error.changedRetry");
        station("B2");var foreignBranch=operator("B2");assertThatThrownBy(()->service.statementFile(foreignBranch,id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test void explicitlyUploadedReviewedZeroMobileMoneyBalanceRemainsEvidenceOnly() {
        UUID mobile=gl.createAccount(maker,new AccountCommand("MOBILE","Synthetic mobile money","ASSET","DEBIT","POSTING","MOBILE_MONEY",null));opening();
        UUID statement=service.importUploadedStatement(maker,zeroCommand(mobile,DAY,DAY.plusDays(1),"0.00"),zeroFile());UUID certificate=service.certify(maker,mobile,DAY.plusDays(1),"STATEMENT",BigDecimal.ZERO,"Explicit uploaded zero source");service.reviewCertificate(checker,certificate,"Independent zero source review");
        assertThat(service.statement(maker,statement).noMovement()).isTrue();assertThat(service.rows(maker,statement,0).rows()).isEmpty();assertThat(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,institution)).isEqualTo(1);
    }

    @Test void databaseRejectsForgedZeroFileChecksumAndPartialPeriodProofAtomically() {
        var c=zeroCommand(bank,DAY,DAY.plusDays(1),"0.00");var actual=new StatementCommand(c.key(),bank,format,c.from(),c.through(),c.opening(),c.closing(),"zero.csv",c.evidence(),"date,reference,amount,kind");
        assertThatThrownBy(()->tx.execute(status->new ReconciliationRepository(jdbc).insertStatement(institution,"B1",maker.getMemberId(),actual,"a".repeat(64),"b".repeat(64),List.of(),NOW,"UPLOAD"))).isInstanceOf(org.springframework.dao.DataAccessException.class).hasStackTraceContaining("exact immutable checksum");
        String checksum;try{checksum=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(actual.content().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        var partial=new StatementCommand(UUID.randomUUID(),bank,format,DAY.plusDays(1),DAY.plusDays(1),BigDecimal.ZERO,BigDecimal.ZERO,"zero.csv",c.evidence(),actual.content());
        assertThatThrownBy(()->tx.execute(status->new ReconciliationRepository(jdbc).insertStatement(institution,"B1",maker.getMemberId(),partial,"a".repeat(64),checksum,List.of(),NOW,"UPLOAD"))).isInstanceOf(org.springframework.dao.DataAccessException.class).hasStackTraceContaining("exact open reporting period");
        assertThat(jdbc.queryForObject("select count(*) from reconciliation_statement where sacco_id=?",Integer.class,institution)).isZero();
    }

}
