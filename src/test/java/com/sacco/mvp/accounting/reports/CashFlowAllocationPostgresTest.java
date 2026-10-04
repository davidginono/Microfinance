package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.*;
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
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.sacco.mvp.accounting.reports.CashFlowAllocation.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_ACCOUNTING_E_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_accounting_e_(test|cash_test|test_reviewed_20261003_v2|test_portfolio_20261003_v1|test_portfolio_20261003_v2)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CashFlowAllocationPostgresTest {
    JdbcTemplate jdbc;DataSourceTransactionManager manager;GeneralLedgerService ledger;CashFlowAllocationService service;LedgerReportService ledgerReports;
    CashFlowAllocationRepository allocations;AccountingPolicyService policies;MemberDirectoryService members;UserClaimService claims;SaccoRegistryService institutions;AuditService audit;
    ApplicationClock clock;String institution;AppUserPrincipal maker,checker;UUID cash,capital,income,funding,journal;Source source;
    CashFlowApprovalListener listener;
    static final LocalDate DAY=LocalDate.of(2026,10,1);
    @BeforeAll void start(){
        var ds=new DriverManagerDataSource(System.getenv("MICROFINANCE_ACCOUNTING_E_DATABASE_URL"),"microfinance_test","");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);
        jdbc.execute("create table if not exists accounting_e_listener_probe(institution varchar(255) not null,allocation_id uuid not null primary key)");
        policies=mock(AccountingPolicyService.class);members=mock(MemberDirectoryService.class);claims=mock(UserClaimService.class);institutions=mock(SaccoRegistryService.class);audit=mock(AuditService.class);clock=mock(ApplicationClock.class);listener=mock(CashFlowApprovalListener.class);
        when(clock.today()).thenReturn(DAY.plusDays(1));when(clock.now()).thenAnswer(i->OffsetDateTime.now());var mapper=JsonMapper.builder().findAndAddModules().build();
        ledger=proxy(new GeneralLedgerService(new GeneralLedgerRepository(jdbc),policies,new AccessControlService(),audit,clock,claims,members,institutions,mock(com.sacco.mvp.reporting.execution.service.AccountingReleaseGateService.class)));
        var reports=new LedgerReportService(new LedgerReportRepository(new NamedParameterJdbcTemplate(jdbc)),policies,members,claims,institutions,clock);
        ledgerReports=proxy(reports);
        allocations=new CashFlowAllocationRepository(jdbc,mapper);service=proxy(new CashFlowAllocationService(allocations,reports,clock,audit,mapper,List.of(listener)));
    }
    @SuppressWarnings("unchecked") private <T>T proxy(T object){var p=new ProxyFactory(object);p.setProxyTargetClass(true);p.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (T)p.getProxy();}
    @BeforeEach void fixture(){
        reset(policies,members,claims,institutions,audit,listener);institution="FLOW-"+UUID.randomUUID();
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,?,true,now(),now())",institution,"Synthetic allocation tests");
        jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,created_at,updated_at,access_status,otp_requirement_mode,user_otp_selection_policy) values(?,?,'B1',true,now(),now(),'ACTIVE','APPROVAL_ONLY','NONE')",UUID.randomUUID(),institution);
        when(institutions.findActiveSacco(institution)).thenReturn(Optional.of(RegisteredSacco.builder().saccoId(institution).active(true).build()));
        when(institutions.findStation(institution,"B1")).thenReturn(Optional.of(SaccoStation.builder().saccoId(institution).stationId("B1").active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));maker=operator(institution,"B1");checker=operator(institution,"B1");
        cash=account("CASH","ASSET","DEBIT","CASH");capital=account("CAPITAL","EQUITY","CREDIT","CAPITAL");income=account("INCOME","INCOME","CREDIT","INCOME");funding=account("FUNDING","LIABILITY","CREDIT","FUNDING");
        var mapper=JsonMapper.builder().findAndAddModules().build();UUID policy=UUID.randomUUID();var decisions=new EnumMap<PolicyDecision,String>(PolicyDecision.class);for(var x:PolicyDecision.values())decisions.put(x,"Synthetic reviewed decision");var matrix=new EnumMap<PostingEvent,PostingRule>(PostingEvent.class);for(var x:PostingEvent.values())matrix.put(x,new PostingRule(PostingPermission.ALLOWED,"Synthetic reviewed treatment"));var mappings=Map.of("OWNER_CAPITAL",capital);var now=OffsetDateTime.now();
        jdbc.update("insert into accounting_policies(id,sacco_id,policy_version,effective_from,opening_date,authoritative_ledger,decisions_json,posting_matrix_json,account_mappings_json,evidence_reference,maker_id,request_key,created_at) values(?,?,1,?,?,'LOCAL_GL',?,?,?,'Synthetic evidence',?,?,?)",policy,institution,DAY,DAY,mapper.writeValueAsString(decisions),mapper.writeValueAsString(matrix),mapper.writeValueAsString(mappings),maker.getMemberId(),UUID.randomUUID(),now);
        jdbc.update("insert into accounting_policy_approvals(policy_id,sacco_id,policy_version,effective_from,checker_id,decision,evidence_reference,reason,decided_at) values(?,?,1,?,?,'APPROVED','Synthetic independent approval','Synthetic only',?)",policy,institution,DAY,checker.getMemberId(),now);
        when(policies.requireApprovedLocalPolicy(eq(institution),any())).thenReturn(new PolicySnapshot(policy,institution,1,DAY,DAY,AuthoritativeLedger.LOCAL_GL,decisions,matrix,mappings,"Synthetic",maker.getMemberId(),Decision.APPROVED,checker.getMemberId(),"Synthetic review","Synthetic",now));
        ledger.createPeriod(maker,DAY,DAY.plusMonths(1));var opening=ledger.importOpening(maker,new JournalCommand(UUID.randomUUID(),"OPENING",DAY,"Synthetic reviewed opening",null,List.of(new Line(cash,new BigDecimal("1000.00"),BigDecimal.ZERO),new Line(capital,BigDecimal.ZERO,new BigDecimal("1000.00")))));ledger.approve(checker,opening.id(),"Synthetic opening checker");ledger.post(checker,opening.id(),true);
        var j=ledger.draftManual(maker,new JournalCommand(UUID.randomUUID(),"COMPOUND",DAY.plusDays(1),"Synthetic receipt source",null,List.of(new Line(cash,new BigDecimal("100.01"),BigDecimal.ZERO),new Line(income,BigDecimal.ZERO,new BigDecimal("30.01")),new Line(funding,BigDecimal.ZERO,new BigDecimal("70.00")))));ledger.approve(checker,j.id(),"Synthetic receipt checker");journal=ledger.post(checker,j.id(),false).id();source=service.source(maker,journal);
    }
    private UUID account(String code,String type,String normal,String purpose){return ledger.createAccount(maker,new AccountCommand(code,"Synthetic "+code,type,normal,"POSTING",purpose,null));}
    private AppUserPrincipal operator(String inst,String branch){UUID id=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,?,?,?,'ACTIVE','MANAGER',now(),false,'test-only')",id,inst,branch,id.toString(),"Synthetic staff");var m=Member.builder().id(id).saccoId(inst).stationId(branch).memberNo(id.toString()).fullName("Synthetic staff").position(Position.MANAGER).status(MemberStatus.ACTIVE).build();when(members.find(id)).thenReturn(Optional.of(m));return new AppUserPrincipal(m,EnumSet.allOf(UserClaim.class),true);}
    private List<Split> splits(){UUID line=source.lines().stream().filter(SourceLine::money).findFirst().orElseThrow().id();return List.of(new Split(line,income,Activity.OPERATING,new BigDecimal("30.01")),new Split(line,funding,Activity.FINANCING,new BigDecimal("70.00")));}
    private UUID draft(UUID request){return service.draft(maker,journal,request,splits(),"Synthetic compound evidence",null);}
    private void station(String branch,OffsetDateTime registered){jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,created_at,updated_at,access_status,otp_requirement_mode,user_otp_selection_policy) values(?,?,?,true,?,now(),'ACTIVE','APPROVAL_ONLY','NONE')",UUID.randomUUID(),institution,branch,registered);when(institutions.findStation(institution,branch)).thenReturn(Optional.of(SaccoStation.builder().saccoId(institution).stationId(branch).active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));}
    private <T>T owningTransaction(java.util.function.Supplier<T> action){var tx=new TransactionTemplate(manager);tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);return tx.execute(status->action.get());}
    @Test void fixedNanosecondClockRetainsExactDraftAndReviewChronology(){
        var fixed=OffsetDateTime.now().plusSeconds(2).withNano(123456789);when(clock.now()).thenReturn(fixed);
        try{UUID id=draft(UUID.randomUUID());service.approve(checker,id,"Same-clock independent review");var retained=service.reviewedAllocations(maker,List.of(journal),fixed).versions().getFirst();assertThat(retained.madeAt().toInstant()).isEqualTo(fixed.truncatedTo(java.time.temporal.ChronoUnit.MICROS).toInstant());assertThat(retained.reviewedAt().toInstant()).isEqualTo(retained.madeAt().toInstant());}
        finally{when(clock.now()).thenAnswer(i->OffsetDateTime.now());}
    }
    @Test void browserMultilineEvidenceNormalizesBeforeIdempotentRetryAndReview(){
        UUID request=UUID.randomUUID();UUID id=service.draft(maker,journal,request,splits(),"Synthetic source\r\nSecond evidence line","Noncash explanation\r\nSecond line");assertThat(service.draft(maker,journal,request,splits(),"Synthetic source\nSecond evidence line","Noncash explanation\nSecond line")).isEqualTo(id);
        service.approve(checker,id,"Independent reviewer\r\nSecond review line");var retained=service.reviewedAllocations(maker,List.of(journal),OffsetDateTime.now()).versions().getFirst();assertThat(retained.evidence()).isEqualTo("Synthetic source\nSecond evidence line");assertThat(retained.noncashEvidence()).isEqualTo("Noncash explanation\nSecond line");assertThat(retained.reviewEvidence()).isEqualTo("Independent reviewer\nSecond review line");assertThatThrownBy(()->service.draft(maker,journal,UUID.randomUUID(),splits(),"Invalid\rbare carriage return",null)).hasMessage("financial.cash.error.evidence");
    }
    @Test void supersededFlagsStayBoundedScopedAndRespectCurrentClaims(){
        UUID first=draft(UUID.randomUUID()),second=draft(UUID.randomUUID());service.approve(checker,second,"Newer independent classification");assertThat(service.supersededVersions(maker,List.of(first,second))).containsExactly(first);
        station("B2",OffsetDateTime.now());var other=operator(institution,"B2");assertThat(service.supersededVersions(other,List.of(first,second))).isEmpty();assertThatThrownBy(()->service.supersededVersions(maker,Collections.nCopies(26,first))).hasMessage("financial.cash.error.size");when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(EnumSet.of(UserClaim.LOAN_REPORTS_VIEW));assertThatThrownBy(()->service.supersededVersions(maker,List.of(first))).isInstanceOf(AccessDeniedException.class);
    }
    @Test void laterRegisteredBranchDoesNotRewriteOldOpeningCoverageAndInactiveKnownBranchStaysUnknown(){
        var cutoff=OffsetDateTime.now();var old=new LedgerReportService.Parameters(DAY,DAY.plusDays(1),cutoff,0);var scope=new LedgerReportService.Scope(institution,"B1",true);var repo=new LedgerReportRepository(new NamedParameterJdbcTemplate(jdbc));assertThat(repo.coverage(scope,old).missingOpenings()).isZero();station("B2",OffsetDateTime.now());assertThat(repo.coverage(scope,old).missingOpenings()).isZero();var current=new LedgerReportService.Parameters(DAY,DAY.plusDays(1),OffsetDateTime.now(),0);assertThat(repo.coverage(scope,current).missingOpenings()).isEqualTo(1);jdbc.update("update sacco_stations set active=false where sacco_id=? and station_id='B2'",institution);assertThat(repo.coverage(scope,current).missingOpenings()).isEqualTo(1);assertThat(repo.coverage(scope,old).missingOpenings()).isZero();
    }
    @Test void retainedLoanSourceRequiresOpeningDespiteLaterRegistrationMetadata(){
        station("B2",OffsetDateTime.now().plusDays(1));var b2maker=operator(institution,"B2");var b2checker=operator(institution,"B2");
        var j=ledger.draftManual(b2maker,new JournalCommand(UUID.randomUUID(),"KNOWN-B2-SOURCE",DAY.plusDays(1),"Synthetic source before registration metadata",null,List.of(new Line(cash,new BigDecimal("10.00"),BigDecimal.ZERO),new Line(income,BigDecimal.ZERO,new BigDecimal("10.00")))));ledger.approve(b2checker,j.id(),"Independent B2 source review");
        assertThatThrownBy(()->ledger.post(b2checker,j.id(),false)).hasMessage("accounting.error.reviewedOpeningRequired");
        // Retained pre-GL loan evidence is a synthetic migration fixture, never a new permitted GL posting.
        UUID loan=UUID.randomUUID(),voucher=UUID.randomUUID();owningTransaction(()->{
            jdbc.update("insert into loan_applications(id,amount,applicant_member_id,created_at,updated_at,disbursement_date,form_data,loan_type,policy_snapshot,required_guarantors,sacco_id,station_id,status,tenor_months,version,loan_id,application_number) values(?,10.00,?,now(),now(),?,'{}','DEVELOPMENT_LOAN','{}',0,?,'B2','DISBURSED',1,0,'12345678',1)",loan,b2maker.getMemberId(),DAY,institution);
            jdbc.update("insert into loan_ledgers(loan_application_id,sacco_id,station_id,loan_id,applicant_member_id,disbursement_date,principal,created_at) values(?,?,'B2','12345678',?,?,10.00,now())",loan,institution,b2maker.getMemberId(),DAY);
            jdbc.update("insert into loan_journal_entries(id,loan_application_id,voucher_id,account_code,debit,credit,effective_date,posted_at) values(?,?,?,'LOAN_PRINCIPAL',10.00,0,?,now()),(?,?,?,'DISBURSEMENT_CLEARING',0,10.00,?,now())",UUID.randomUUID(),loan,voucher,DAY,UUID.randomUUID(),loan,voucher,DAY);return null;
        });
        var repo=new LedgerReportRepository(new NamedParameterJdbcTemplate(jdbc));var coverage=repo.coverage(new LedgerReportService.Scope(institution,"B1",true),new LedgerReportService.Parameters(DAY,DAY.plusDays(1),OffsetDateTime.now(),0));
        assertThat(coverage.missingOpenings()).isEqualTo(1);assertThat(coverage.unbridgedVouchers()).isEqualTo(1);
    }
    @Test void exactReviewedVersionsAndCutoffAreRetained(){UUID id=draft(UUID.randomUUID());var cutoff=OffsetDateTime.now();assertThat(service.reviewedAllocations(maker,List.of(journal),cutoff).missingJournalIds()).containsExactly(journal);service.approve(checker,id,"Synthetic independent flow classification");assertThat(service.reviewedAllocations(maker,List.of(journal),cutoff).complete()).isFalse();var result=service.reviewedAllocations(maker,List.of(journal),OffsetDateTime.now());assertThat(result.complete()).isTrue();assertThat(result.versions()).singleElement().satisfies(v->{assertThat(v.sourceChecksum()).hasSize(64);assertThat(v.definitionChecksum()).hasSize(64);assertThat(v.source().lines()).hasSize(3);assertThat(v.checker()).isEqualTo(checker.getMemberId());assertThat(v.splits().stream().map(Split::signedAmount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("100.01");});}
    @Test void independentCheckerRequiredAndSamePayloadRetryIsIdempotent(){UUID request=UUID.randomUUID(),id=draft(request);assertThat(draft(request)).isEqualTo(id);assertThatThrownBy(()->service.approve(maker,id,"Self review")).hasMessage("financial.cash.error.checker");assertThatThrownBy(()->service.draft(maker,journal,request,splits(),"Changed evidence",null)).hasMessage("financial.cash.error.changedRetry");service.approve(checker,id,"Independent review");assertThatThrownBy(()->service.approve(checker,id,"Repeated review")).hasMessage("financial.cash.error.checker");}
    @Test void freshRevocationBranchTamperingAndForeignIdsAreDenied(){UUID id=draft(UUID.randomUUID());when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.FINANCIAL_REPORTS_VIEW));assertThatThrownBy(()->service.approve(checker,id,"Revoked review")).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->draft(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->service.source(maker,UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);var moved=members.find(maker.getMemberId()).orElseThrow();moved.setStationId("B2");assertThatThrownBy(()->service.versions(maker,0)).isInstanceOf(AccessDeniedException.class);}
    @Test void appendOnlyDatabaseGuardsPreserveSourceAndReviewEvidence(){UUID id=draft(UUID.randomUUID());service.approve(checker,id,"Independent review");assertThatThrownBy(()->jdbc.update("update cash_flow_allocations set evidence='tampered' where id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThatThrownBy(()->jdbc.update("delete from cash_flow_allocation_reviews where allocation_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(service.hasInstitutionHistory(institution)).isTrue();assertThat(service.hasMemberHistory(checker.getMemberId())).isTrue();assertThat(service.hasMemberHistory(maker.getMemberId())).isTrue();}
    @Test void failedAuditRollsBackDraft(){doThrow(new IllegalStateException("Synthetic audit failure")).when(audit).log(eq("ACCOUNTING_CASH_FLOW"),any(),eq("DRAFTED"),any(),isNull(),anyMap());assertThatThrownBy(()->draft(UUID.randomUUID())).hasMessage("Synthetic audit failure");assertThat(service.versions(maker,0)).isEmpty();}
    @Test void concurrentDuplicateDraftsHaveOneImmutableVersion()throws Exception{UUID request=UUID.randomUUID();var pool=Executors.newFixedThreadPool(2);try{var latch=new CountDownLatch(1);Callable<UUID> command=()->{latch.await();return draft(request);};var a=pool.submit(command);var b=pool.submit(command);latch.countDown();assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));assertThat(service.versions(maker,0)).hasSize(1);}finally{pool.shutdownNow();}}
    @Test void newerApprovedVersionDoesNotRewriteArchivedCutoff(){UUID first=draft(UUID.randomUUID());service.approve(checker,first,"First classification");var cutoff=OffsetDateTime.now();UUID second=draft(UUID.randomUUID());service.approve(checker,second,"Revised reviewed classification");assertThat(service.reviewedAllocations(maker,List.of(journal),cutoff).versions().getFirst().id()).isEqualTo(first);assertThat(service.reviewedAllocations(maker,List.of(journal),OffsetDateTime.now()).versions().getFirst().id()).isEqualTo(second);}
    @Test void approvalListenerReceivesExactCommittedSourceIdentity(){
        UUID id=draft(UUID.randomUUID());doAnswer(call->{assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();jdbc.update("insert into accounting_e_listener_probe(institution,allocation_id) values(?,?)",call.getArgument(0,String.class),call.getArgument(2,UUID.class));return null;}).when(listener).approved(anyString(),any(),any(),anyInt(),any(),any());
        service.approve(checker,id,"Independent approval listener evidence");var retained=service.reviewedAllocations(maker,List.of(journal),OffsetDateTime.now()).versions().getFirst();
        verify(listener).approved(eq(institution),eq(journal),eq(id),eq(1),eq(checker.getMemberId()),argThat(at->at.toInstant().equals(retained.reviewedAt().toInstant())));
        assertThat(jdbc.queryForObject("select count(*) from accounting_e_listener_probe where institution=? and allocation_id=?",Integer.class,institution,id)).isEqualTo(1);
    }
    @Test void callbackFailureRollsBackReviewAndCallbackWrites(){
        UUID id=draft(UUID.randomUUID());doAnswer(call->{jdbc.update("insert into accounting_e_listener_probe(institution,allocation_id) values(?,?)",call.getArgument(0,String.class),call.getArgument(2,UUID.class));throw new IllegalStateException("Synthetic release invalidation failure");}).when(listener).approved(anyString(),any(),any(),anyInt(),any(),any());
        assertThatThrownBy(()->service.approve(checker,id,"Independent approval")).hasMessage("Synthetic release invalidation failure");
        assertThat(jdbc.queryForObject("select count(*) from cash_flow_allocation_reviews where allocation_id=?",Integer.class,id)).isZero();assertThat(jdbc.queryForObject("select count(*) from accounting_e_listener_probe where allocation_id=?",Integer.class,id)).isZero();
        assertThat(service.reviewedAllocations(maker,List.of(journal),OffsetDateTime.now()).complete()).isFalse();verify(audit,never()).log(eq("ACCOUNTING_CASH_FLOW"),eq(id),eq("APPROVED"),any(),isNull(),anyMap());
    }
    @Test void obsoleteDraftCannotSupersedeNewerApprovedVersion(){
        UUID older=draft(UUID.randomUUID()),newer=draft(UUID.randomUUID());service.approve(checker,newer,"Newer independently approved version");
        assertThatThrownBy(()->service.approve(checker,older,"Obsolete draft review")).hasMessage("financial.cash.error.superseded");assertThat(service.reviewedAllocations(maker,List.of(journal),OffsetDateTime.now()).versions().getFirst().id()).isEqualTo(newer);
        verify(listener,never()).approved(anyString(),any(),eq(older),anyInt(),any(),any());
    }
    @Test void trustedProofRequiresWritableReadCommittedOwningTransaction(){
        assertThatThrownBy(()->service.reviewedForClosing(maker,List.of(journal),OffsetDateTime.now())).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(()->service.currentForInstitutionClosingProof(maker,institution,"B1",DAY,DAY.plusDays(1),OffsetDateTime.now(),new CashFlowAllocation.Coverage(List.of(),List.of(journal)))).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        var readOnly=new TransactionTemplate(manager);readOnly.setReadOnly(true);assertThatThrownBy(()->readOnly.execute(s->service.reviewedForClosing(maker,List.of(journal),OffsetDateTime.now()))).hasMessage("Cash-flow approval/proof requires a writable READ_COMMITTED owning transaction");
        var repeatable=new TransactionTemplate(manager);repeatable.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);assertThatThrownBy(()->repeatable.execute(s->service.reviewedForClosing(maker,List.of(journal),OffsetDateTime.now()))).hasMessage("Cash-flow approval/proof requires a writable READ_COMMITTED owning transaction");
        assertThat(service.reviewedAllocations(maker,List.of(journal),OffsetDateTime.now()).missingJournalIds()).containsExactly(journal);
    }
    @Test void pinnedPublicationProofBlocksNewApprovalUntilOwningCommit()throws Exception{
        UUID first=draft(UUID.randomUUID());service.approve(checker,first,"First reviewed classification");UUID next=draft(UUID.randomUUID());var pinned=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var proof=pool.submit(()->owningTransaction(()->{var retained=service.reviewedForClosing(maker,List.of(journal),OffsetDateTime.now());pinned.countDown();await(release);return retained;}));assertThat(pinned.await(20,TimeUnit.SECONDS)).isTrue();
            assertThat(owningTransaction(()->jdbc.queryForObject("select pg_try_advisory_xact_lock(hashtextextended(?,0))",Boolean.class,"CASH_FLOW/"+institution+"/B1/"+journal))).isFalse();
            var approval=pool.submit(()->service.approve(checker,next,"New classification after publication"));assertThatThrownBy(()->approval.get(250,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();assertThat(proof.get(30,TimeUnit.SECONDS).versions().getFirst().id()).isEqualTo(first);approval.get(30,TimeUnit.SECONDS);
            assertThat(service.reviewedAllocations(maker,List.of(journal),OffsetDateTime.now()).versions().getFirst().id()).isEqualTo(next);
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test void approvalGuardBlocksProofAndRejectsPreLockCutoffWhileArchiveRemainsReadable()throws Exception{
        UUID first=draft(UUID.randomUUID());service.approve(checker,first,"First approved classification");var old=OffsetDateTime.now();UUID next=draft(UUID.randomUUID());var pinned=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        doAnswer(call->{if(next.equals(call.getArgument(2,UUID.class))){pinned.countDown();await(release);}return null;}).when(listener).approved(anyString(),any(),any(),anyInt(),any(),any());
        try{
            var approval=pool.submit(()->service.approve(checker,next,"Approval holds source through callback"));assertThat(pinned.await(20,TimeUnit.SECONDS)).isTrue();
            assertThat(owningTransaction(()->jdbc.queryForObject("select pg_try_advisory_xact_lock_shared(hashtextextended(?,0))",Boolean.class,"CASH_FLOW/"+institution+"/B1/"+journal))).isFalse();
            assertThat(service.reviewedAllocations(maker,List.of(journal),old).versions().getFirst().id()).isEqualTo(first);
            var proof=pool.submit(()->owningTransaction(()->service.reviewedForClosing(maker,List.of(journal),old)));assertThatThrownBy(()->proof.get(250,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();approval.get(30,TimeUnit.SECONDS);assertThatThrownBy(()->proof.get(30,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalArgumentException.class).hasRootCauseMessage("financial.cash.error.superseded");
            assertThat(owningTransaction(()->service.reviewedForClosing(maker,List.of(journal),OffsetDateTime.now())).versions().getFirst().id()).isEqualTo(next);assertThat(service.reviewedAllocations(maker,List.of(journal),old).versions().getFirst().id()).isEqualTo(first);
        }finally{release.countDown();pool.shutdownNow();}
    }
    private static void await(CountDownLatch latch){try{if(!latch.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic concurrency latch timed out");}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException(failure);}}
    @Test void latestApprovedHistoryIsBoundedBeforeJdbcMappingAndRetainsEarlierCutoff(){
        UUID first=draft(UUID.randomUUID());service.approve(checker,first,"First retained classification");var old=OffsetDateTime.now();UUID latest=first;
        for(int i=0;i<7;i++){latest=draft(UUID.randomUUID());service.approve(checker,latest,"Later classification "+i);}
        var mapped=new java.util.concurrent.atomic.AtomicInteger();var tracking=new JdbcTemplate(jdbc.getDataSource()){
            @Override public <T>List<T> query(String sql,RowMapper<T> rowMapper,Object...args){return super.query(sql,(r,n)->{mapped.incrementAndGet();return rowMapper.mapRow(r,n);},args);}
        };
        var bounded=new CashFlowAllocationRepository(tracking,JsonMapper.builder().findAndAddModules().build());
        assertThat(bounded.approved(institution,"B1",OffsetDateTime.now(),List.of(journal))).singleElement().satisfies(v->assertThat(v.version()).isEqualTo(8));assertThat(mapped.get()).isEqualTo(1);
        mapped.set(0);assertThat(bounded.approved(institution,"B1",old,List.of(journal))).singleElement().satisfies(v->assertThat(v.id()).isEqualTo(first));assertThat(mapped.get()).isEqualTo(1);
        assertThatThrownBy(()->bounded.approved(institution,"B1",OffsetDateTime.now(),Collections.nCopies(1001,journal))).hasMessage("financial.cash.error.size");
    }
    @Test void postedBookExportsReadEntireBoundedSourceAndPreserveCutoff(){
        for(int i=0;i<30;i++){account("EXTRA"+i,"ASSET","DEBIT","OTHER");var j=ledger.draftManual(maker,new JournalCommand(UUID.randomUUID(),"EXPORT-"+i,DAY.plusDays(1),"Synthetic source",null,List.of(new Line(cash,new BigDecimal("0.01"),BigDecimal.ZERO),new Line(income,BigDecimal.ZERO,new BigDecimal("0.01")))));ledger.approve(checker,j.id(),"Independent export fixture");ledger.post(checker,j.id(),false);}
        var cutoff=OffsetDateTime.now();var p=new LedgerReportService.Parameters(DAY,DAY.plusDays(1),cutoff,0);
        assertThat(ledgerReports.trialBalance(maker,p,false).accounts()).hasSize(25);assertThat(ledgerReports.trialBalance(maker,p,false).hasNext()).isTrue();
        var trial=ledgerReports.exportTrialBalance(maker,p,false);assertThat(trial.accounts()).hasSize(34);assertThat(trial.hasNext()).isFalse();assertThat(trial.totals().movementDebit()).isEqualByComparingTo(trial.totals().movementCredit());
        assertThat(ledgerReports.accountActivity(maker,cash,p,false).movements()).hasSize(25);var full=ledgerReports.exportAccountActivity(maker,cash,p,false);assertThat(full.activity().movements()).hasSize(31);assertThat(full.activity().closing()).isEqualByComparingTo("1100.31");assertThat(full.policyVersion()).isEqualTo(1);
        var late=ledger.draftManual(maker,new JournalCommand(UUID.randomUUID(),"LATER-EXPORT",DAY.plusDays(1),"Synthetic later source",null,List.of(new Line(cash,new BigDecimal("0.01"),BigDecimal.ZERO),new Line(income,BigDecimal.ZERO,new BigDecimal("0.01")))));ledger.approve(checker,late.id(),"Independent later fixture");ledger.post(checker,late.id(),false);
        assertThat(ledgerReports.exportAccountActivity(maker,cash,p,false).activity().movements()).hasSize(31);
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.FINANCIAL_REPORTS_VIEW));assertThatThrownBy(()->ledgerReports.exportTrialBalance(maker,p,false)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void laterPolicyApprovalPreservesHistoricalReportLineageAndActivityStaysInBranch() {
        var cutoff=OffsetDateTime.now();var oldParameters=new LedgerReportService.Parameters(DAY,DAY.plusDays(1),cutoff,0);
        var old=ledgerReports.exportTrialBalance(maker,oldParameters,false);UUID second=UUID.randomUUID();
        jdbc.update("insert into accounting_policies(id,sacco_id,policy_version,effective_from,opening_date,authoritative_ledger,decisions_json,posting_matrix_json,account_mappings_json,evidence_reference,maker_id,request_key,created_at) select ?,sacco_id,2,effective_from+1,opening_date,authoritative_ledger,decisions_json,posting_matrix_json,account_mappings_json,'Synthetic later policy',maker_id,?,now() from accounting_policies where id=?",second,UUID.randomUUID(),old.policyId());
        jdbc.update("insert into accounting_policy_approvals(policy_id,sacco_id,policy_version,effective_from,checker_id,decision,evidence_reference,reason,decided_at) values(?,?,2,?,?,'APPROVED','Synthetic later approval','Synthetic only',now())",second,institution,DAY.plusDays(1),checker.getMemberId());
        assertThat(ledgerReports.exportTrialBalance(maker,oldParameters,false).policyId()).isEqualTo(old.policyId());
        assertThat(ledgerReports.trialBalance(maker,new LedgerReportService.Parameters(DAY,DAY.plusDays(1),OffsetDateTime.now(),0),false).policyId()).isEqualTo(second);
        assertThatThrownBy(()->ledgerReports.accountActivity(maker,cash,oldParameters,true)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->ledgerReports.exportAccountActivity(maker,cash,oldParameters,true)).isInstanceOf(AccessDeniedException.class);
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(Set.of());
        assertThatThrownBy(()->ledgerReports.exportTrialBalance(maker,oldParameters,false)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void realLeafAllowsClosingProofWithoutReportRegistryAccess(){UUID id=draft(UUID.randomUUID());service.approve(checker,id,"Independent classification");var adapter=new CashFlowClosingAdapter(service);
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.ACCOUNTING_CLOSING_APPROVE));
        var proof=owningTransaction(()->adapter.reviewedAllocations(maker,List.of(journal),OffsetDateTime.now()));assertThat(proof.missingJournalIds()).isEmpty();assertThat(proof.versions()).singleElement().satisfies(v->{assertThat(v.id()).isEqualTo(id);assertThat(v.splits()).extracting(com.sacco.mvp.accounting.reconciliation.CashFlowReconciliationSource.Split::activity).containsExactlyInAnyOrder("OPERATING","FINANCING");});
        assertThatThrownBy(()->service.source(maker,journal)).isInstanceOf(AccessDeniedException.class);
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(Set.of());assertThatThrownBy(()->owningTransaction(()->adapter.reviewedAllocations(maker,List.of(journal),OffsetDateTime.now()))).isInstanceOf(AccessDeniedException.class);
    }
    @Test void institutionProofValidatesForeignBranchWithoutForeignPrincipalOrRegistryAccess(){
        jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,created_at,updated_at,access_status,otp_requirement_mode,user_otp_selection_policy) values(?,?,'B2',true,now(),now(),'ACTIVE','APPROVAL_ONLY','NONE')",UUID.randomUUID(),institution);
        when(institutions.findStation(institution,"B2")).thenReturn(Optional.of(SaccoStation.builder().saccoId(institution).stationId("B2").active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        var b2maker=operator(institution,"B2");var b2checker=operator(institution,"B2");var opening=ledger.importOpening(b2maker,new JournalCommand(UUID.randomUUID(),"B2-OPENING",DAY,"Synthetic B2 opening",null,List.of(new Line(cash,new BigDecimal("1000.00"),BigDecimal.ZERO),new Line(capital,BigDecimal.ZERO,new BigDecimal("1000.00")))));ledger.approve(b2checker,opening.id(),"Independent B2 opening");ledger.post(b2checker,opening.id(),true);
        var j=ledger.draftManual(b2maker,new JournalCommand(UUID.randomUUID(),"B2-COMPOUND",DAY.plusDays(1),"Synthetic B2 source",null,List.of(new Line(cash,new BigDecimal("100.01"),BigDecimal.ZERO),new Line(income,BigDecimal.ZERO,new BigDecimal("30.01")),new Line(funding,BigDecimal.ZERO,new BigDecimal("70.00")))));ledger.approve(b2checker,j.id(),"B2 source review");ledger.post(b2checker,j.id(),false);
        UUID line=service.source(b2maker,j.id()).lines().stream().filter(SourceLine::money).findFirst().orElseThrow().id();var allocation=List.of(new Split(line,income,Activity.OPERATING,new BigDecimal("30.010")),new Split(line,funding,Activity.FINANCING,new BigDecimal("70")));UUID id=service.draft(b2maker,j.id(),UUID.randomUUID(),allocation,"B2 classification",null);service.approve(b2checker,id,"Independent B2 classification");var adapter=new CashFlowClosingAdapter(service);var cutoff=OffsetDateTime.now();var frozen=owningTransaction(()->adapter.reviewedAllocations(b2maker,List.of(j.id()),cutoff));
        when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.ACCOUNTING_CLOSING_APPROVE,UserClaim.ACCOUNTING_CLOSING_INSTITUTION));
        assertThat(owningTransaction(()->adapter.allocationCoverageCurrentForInstitutionClose(maker,institution,"B2",DAY,DAY.plusDays(1),cutoff,frozen))).isTrue();
        assertThatThrownBy(()->owningTransaction(()->adapter.reviewedAllocations(maker,List.of(j.id()),cutoff))).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->service.source(maker,j.id())).isInstanceOf(AccessDeniedException.class);
        when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.ACCOUNTING_CLOSING_APPROVE));assertThatThrownBy(()->owningTransaction(()->adapter.allocationCoverageCurrentForInstitutionClose(maker,institution,"B2",DAY,DAY.plusDays(1),cutoff,frozen))).isInstanceOf(AccessDeniedException.class);
        when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.ACCOUNTING_CLOSING_APPROVE,UserClaim.ACCOUNTING_CLOSING_INSTITUTION));UUID next=service.draft(b2maker,j.id(),UUID.randomUUID(),allocation,"New retained classification",null);service.approve(b2checker,next,"New independent B2 classification");assertThat(owningTransaction(()->adapter.allocationCoverageCurrentForInstitutionClose(maker,institution,"B2",DAY,DAY.plusDays(1),cutoff,frozen))).isFalse();assertThat(frozen.versions().getFirst().id()).isEqualTo(id);
    }
}
