package com.sacco.mvp.accounting.business.service;

import com.sacco.mvp.accounting.business.dto.BusinessAccountingDtos.*;
import com.sacco.mvp.accounting.business.repository.BusinessAccountingRepository;
import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.*;
import com.sacco.mvp.accounting.policy.*;
import com.sacco.mvp.accounting.policy.AccountingPolicyService.*;
import com.sacco.mvp.accounting.repository.GeneralLedgerRepository;
import com.sacco.mvp.reporting.execution.service.AccountingReleaseGateService;
import com.sacco.mvp.reporting.execution.repository.AccountingReleaseRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import com.zaxxer.hikari.HikariDataSource;
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

@EnabledIfEnvironmentVariable(named="MICROFINANCE_ACCOUNTING_C_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_accounting_c_test(?:_baseline_20261002|_source_corrections_20261003|_release_gate_20261003|_owned_sources_20261003(?:_v2)?)?")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BusinessAccountingPostgresTest {
    private static final LocalDate OPENING=LocalDate.of(2026,10,1), DAY=OPENING.plusDays(1);
    private JdbcTemplate jdbc;
    private HikariDataSource ds;
    private TransactionTemplate tx;
    private BusinessAccountingService service;
    private BusinessAccountingRepository repository;
    private GeneralLedgerService books;
    private AccountingPolicyService policies;
    private AuditService audit;
    private MemberDirectoryService members;
    private UserClaimService claims;
    private SaccoRegistryService institutions;
    private ApplicationClock clock;
    private String institution;
    private AppUserPrincipal maker,checker,third,fourth;
    private Map<String,UUID> mappings;
    private PolicySnapshot policy;
    private AccountingReleaseGateService releaseGate;
    private DataSourceTransactionManager transactionManager;

    @BeforeAll void start(){
        ds=new HikariDataSource();ds.setJdbcUrl(System.getenv("MICROFINANCE_ACCOUNTING_C_DATABASE_URL"));ds.setUsername("microfinance_test");ds.setPassword("");ds.setMaximumPoolSize(4);ds.setMinimumIdle(0);ds.setConnectionTimeout(30000);ds.addDataSourceProperty("sslmode","disable");ds.addDataSourceProperty("connectTimeout","5");ds.addDataSourceProperty("socketTimeout","30");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);transactionManager=manager;tx=new TransactionTemplate(manager);
        var mapper=JsonMapper.builder().findAndAddModules().build();
        policies=mock(AccountingPolicyService.class);audit=mock(AuditService.class);members=mock(MemberDirectoryService.class);claims=mock(UserClaimService.class);institutions=mock(SaccoRegistryService.class);clock=mock(ApplicationClock.class);
        when(clock.today()).thenReturn(DAY);when(clock.now()).thenReturn(OffsetDateTime.parse("2026-10-02T12:00:00+03:00"));
        books=proxy(new GeneralLedgerService(new GeneralLedgerRepository(jdbc),policies,new AccessControlService(),audit,clock,claims,members,institutions,mock(com.sacco.mvp.reporting.execution.service.AccountingReleaseGateService.class)),manager);
        repository=new BusinessAccountingRepository(jdbc,mapper);
        releaseGate=mock(AccountingReleaseGateService.class);
        service=businessService(releaseGate);
    }
    @AfterAll void stop(){if(ds!=null)ds.close();}
    @BeforeEach void fixture(){
        reset(policies,audit,members,claims,institutions,releaseGate);institution="C-"+UUID.randomUUID();
        when(clock.today()).thenReturn(DAY);when(clock.now()).thenReturn(OffsetDateTime.parse("2026-10-02T12:00:00+03:00"));
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,?,true,now(),now())",institution,"Synthetic business accounting institution");
        when(institutions.findActiveSacco(institution)).thenReturn(Optional.of(RegisteredSacco.builder().saccoId(institution).active(true).build()));
        when(institutions.findStation(institution,"B1")).thenReturn(Optional.of(SaccoStation.builder().saccoId(institution).stationId("B1").active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));
        maker=operator("B1");checker=operator("B1");third=operator("B1");fourth=operator("B1");
        mappings=new LinkedHashMap<>();
        for(String key:List.of("CASH","BANK","MOBILE_MONEY","OWNER_CAPITAL","OWNER_DISTRIBUTIONS","SUPPLIER_PAYABLE","OPERATING_EXPENSE","FUNDING_PRINCIPAL","FUNDING_INTEREST_EXPENSE","FIXED_ASSET","DEPRECIATION_EXPENSE","ACCUMULATED_DEPRECIATION","DISPOSAL_GAIN","DISPOSAL_LOSS","PREPAYMENT_ASSET","ACCRUED_LIABILITY","TAX_EXPENSE","TAX_PAYABLE","UNAPPLIED_FUNDS","INTERNAL_DUE_FROM","INTERNAL_DUE_TO")){
            String type=key.contains("EXPENSE")||key.contains("LOSS")?"EXPENSE":Set.of("SUPPLIER_PAYABLE","FUNDING_PRINCIPAL","ACCRUED_LIABILITY","TAX_PAYABLE","UNAPPLIED_FUNDS","INTERNAL_DUE_TO").contains(key)?"LIABILITY":key.startsWith("OWNER")?"EQUITY":key.contains("GAIN")?"INCOME":"ASSET";
            mappings.put(key,books.createAccount(maker,new AccountCommand(key,"Synthetic "+key,type,Set.of("LIABILITY","EQUITY","INCOME").contains(type)?"CREDIT":"DEBIT","POSTING","OTHER",null)));
        }
        UUID id=UUID.randomUUID();var decisions=new EnumMap<PolicyDecision,String>(PolicyDecision.class);for(var v:PolicyDecision.values())decisions.put(v,"Synthetic independently approved evidence");decisions.put(PolicyDecision.INTEREST_RECOGNITION,"CASH_DUE_INTEREST_V1");
        var matrix=new EnumMap<PostingEvent,PostingRule>(PostingEvent.class);for(var v:PostingEvent.values())matrix.put(v,new PostingRule(PostingPermission.ALLOWED,"Synthetic verified treatment"));
        var mapper=JsonMapper.builder().findAndAddModules().build();
        jdbc.update("insert into accounting_policies(id,sacco_id,policy_version,effective_from,opening_date,authoritative_ledger,decisions_json,posting_matrix_json,account_mappings_json,evidence_reference,maker_id,request_key,created_at) values(?,?,1,?,?,'LOCAL_GL',?,?,?,'Synthetic policy evidence',?,?,now())",id,institution,OPENING,OPENING,mapper.writeValueAsString(decisions),mapper.writeValueAsString(matrix),mapper.writeValueAsString(mappings),maker.getMemberId(),UUID.randomUUID());
        jdbc.update("insert into accounting_policy_approvals(policy_id,sacco_id,policy_version,effective_from,checker_id,decision,evidence_reference,reason,decided_at) values(?,?,1,?,?,'APPROVED','Synthetic checker evidence','Synthetic only',now())",id,institution,OPENING,checker.getMemberId());
        policy=new PolicySnapshot(id,institution,1,OPENING,OPENING,AuthoritativeLedger.LOCAL_GL,decisions,matrix,mappings,"Synthetic policy evidence",maker.getMemberId(),Decision.APPROVED,checker.getMemberId(),"Synthetic checker evidence","Synthetic only",clock.now());
        when(policies.requireApprovedLocalPolicy(eq(institution),any())).thenReturn(policy);books.createPeriod(maker,OPENING,OPENING.plusMonths(1));
        var opening=books.importOpening(maker,new JournalCommand(UUID.randomUUID(),"SYNTHETIC-OPENING",OPENING,"Synthetic reconciled cash/capital",null,List.of(new Line(mappings.get("CASH"),new BigDecimal("10000.00"),BigDecimal.ZERO),new Line(mappings.get("OWNER_CAPITAL"),BigDecimal.ZERO,new BigDecimal("10000.00")))));
        books.approve(checker,opening.id(),"Synthetic independent opening reconciliation");books.post(checker,opening.id(),true);
    }
    @Test void draftEvidenceSurvivesMissingPolicyWithoutPostingMoney(){
        when(policies.requireApprovedLocalPolicy(eq(institution),any())).thenThrow(new IllegalArgumentException("accounting.policy.error.unapproved"));
        var d=service.create(maker,command(Kind.CAPITAL_RECEIPT,"10.01",null));
        assertThat(service.create(maker,d.command()).id()).isEqualTo(d.id());
        assertThatThrownBy(()->service.submit(maker,d.id())).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.view(maker,d.id()).state()).isEqualTo("DRAFT");assertThat(countSources("POSTED")).isZero();
    }
    @Test void invoicePartialPaymentAndCreditDoNotDoubleCountExpense(){
        var invoice=posted(Kind.EXPENSE_INVOICE,"100.01",null);
        posted(Kind.PAYABLE_PAYMENT,"40.00",invoice.id());posted(Kind.SUPPLIER_CREDIT,"10.01",invoice.id());
        assertThat(repository.remaining(invoice.id())).isEqualByComparingTo("50.00");
        assertThat(balance("SUPPLIER_PAYABLE")).isEqualByComparingTo("-50.00");assertThat(balance("OPERATING_EXPENSE")).isEqualByComparingTo("90.00");
        var excess=service.create(maker,command(Kind.PAYABLE_PAYMENT,"50.01",invoice.id()));assertThatThrownBy(()->service.submit(maker,excess.id())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void concurrentPartialPaymentsSerializeAgainstTheOriginalObligation()throws Exception{
        var invoice=posted(Kind.EXPENSE_INVOICE,"100.00",null);var a=submitted(Kind.PAYABLE_PAYMENT,"70.00",invoice.id());var b=submitted(Kind.PAYABLE_PAYMENT,"70.00",invoice.id());
        try(var pool=Executors.newFixedThreadPool(2)){
            var x=pool.submit(()->{try{service.approveAndPost(checker,a.id(),"Independent evidence A",true);return true;}catch(IllegalArgumentException e){return false;}});
            var y=pool.submit(()->{try{service.approveAndPost(checker,b.id(),"Independent evidence B",true);return true;}catch(IllegalArgumentException e){return false;}});
            assertThat(List.of(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(repository.remaining(invoice.id())).isEqualByComparingTo("30.00");
    }
    @Test void fundingPrincipalAndInterestRemainSeparate(){var funding=posted(Kind.FUNDING_RECEIPT,"1000.01",null);posted(Kind.FUNDING_PRINCIPAL_PAYMENT,"100.00",funding.id());posted(Kind.FUNDING_INTEREST,"20.01",funding.id());assertThat(repository.remaining(funding.id())).isEqualByComparingTo("900.01");assertThat(balance("FUNDING_PRINCIPAL")).isEqualByComparingTo("-900.01");assertThat(balance("FUNDING_INTEREST_EXPENSE")).isEqualByComparingTo("20.01");assertThat(balance("OPERATING_EXPENSE")).isZero();}
    @Test void unappliedMoneyRefundHasLiabilityAndNoFalseLoanReceipt(){var received=posted(Kind.UNMATCHED_RECEIPT,"50.01",null);posted(Kind.REFUND,"20.00",received.id());assertThat(repository.remaining(received.id())).isEqualByComparingTo("30.01");assertThat(balance("UNAPPLIED_FUNDS")).isEqualByComparingTo("-30.01");assertThat(jdbc.queryForObject("select count(*) from accounting_business_document where sacco_id=? and loan_transaction_id is not null",Integer.class,institution)).isZero();}
    @Test void prepaymentAccrualAndTaxLiabilitiesRemainTraceable(){var pre=posted(Kind.PREPAYMENT,"120.00",null);posted(Kind.PREPAYMENT_RELEASE,"10.00",pre.id());var accrued=posted(Kind.ACCRUAL,"35.00",null);posted(Kind.ACCRUAL_PAYMENT,"20.00",accrued.id());var tax=posted(Kind.TAX_LIABILITY,"12.01",null);posted(Kind.TAX_PAYMENT,"2.01",tax.id());assertThat(repository.remaining(pre.id())).isEqualByComparingTo("110.00");assertThat(repository.remaining(accrued.id())).isEqualByComparingTo("15.00");assertThat(repository.remaining(tax.id())).isEqualByComparingTo("10.00");}
    @Test void assetDepreciationAndDisposalPreserveTheRegister(){var asset=posted(Kind.ASSET_PURCHASE,"100.00",null);posted(Kind.DEPRECIATION,"20.00",asset.id());posted(Kind.ASSET_DISPOSAL,"70.00",asset.id());var row=service.assets(maker,0).rows().getFirst();assertThat(row.cost()).isEqualByComparingTo("100.00");assertThat(row.depreciation()).isEqualByComparingTo("20.00");assertThat(row.disposed()).isTrue();assertThat(balance("FIXED_ASSET")).isZero();assertThat(balance("DISPOSAL_LOSS")).isEqualByComparingTo("10.00");}
    @Test void originalAndLinkedReversalRemainImmutableAndNetToZero(){
        var original=posted(Kind.EXPENSE_INVOICE,"12.01",null);
        var reversal=service.create(third,command(Kind.BUSINESS_REVERSAL,"12.01",original.id()));
        service.submit(third,reversal.id());
        service.approveAndPost(fourth,reversal.id(),"Independent correction review",true);
        assertThat(balance("OPERATING_EXPENSE")).isZero();
        assertThat(balance("SUPPLIER_PAYABLE")).isZero();
        assertThat(books.journal(third,service.view(third,reversal.id()).journalId()).reversesId()).isEqualTo(original.journalId());
        assertThat(books.journal(third,service.view(third,reversal.id()).journalId()).sourceType()).isEqualTo("SOURCE_REVERSAL");
        assertThat(repository.remaining(original.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from accounting_business_control_entry where sacco_id=?",Integer.class,institution)).isEqualTo(2);
        assertThatThrownBy(()->jdbc.update("delete from accounting_business_document where id=?",original.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("update accounting_business_document set amount=0 where id=?",original.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("delete from accounting_business_control_entry where sacco_id=?",institution)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void failedSourceAuditRollsBackGlOutboxAndControlTogether(){var d=submitted(Kind.CAPITAL_RECEIPT,"3.01",null);doThrow(new IllegalStateException("Synthetic audit failure")).when(audit).logEvent(anyString(),eq(d.id()),eq("SOURCE_POSTED"),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),any());assertThatThrownBy(()->service.approveAndPost(checker,d.id(),"Independent review",true)).isInstanceOf(IllegalStateException.class);assertThat(service.view(maker,d.id()).state()).isEqualTo("SUBMITTED");assertThat(books.journal(maker,d.journalId()).state()).isEqualTo("DRAFT");assertThat(repository.remaining(d.id())).isZero();assertThat(jdbc.queryForObject("select count(*) from accounting_outbox where journal_id=?",Integer.class,d.journalId())).isZero();}
    @Test void makerCheckerRevocationAndBranchScopeAreEnforced(){var d=submitted(Kind.CAPITAL_RECEIPT,"1.01",null);assertThatThrownBy(()->service.approveAndPost(maker,d.id(),"Own review",true)).isInstanceOf(IllegalArgumentException.class);when(claims.effectiveClaims(eq(checker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of());assertThatThrownBy(()->service.approveAndPost(checker,d.id(),"Revoked review",true)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);var foreign=operator("B2");assertThatThrownBy(()->service.view(foreign,d.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);when(institutions.findStation(institution,"B1")).thenReturn(Optional.of(SaccoStation.builder().active(false).build()));assertThatThrownBy(()->service.view(maker,d.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}
    @Test void retainedSupplierEvidenceBlocksInstitutionAndAuthorDeletionBeforeAnyJournal(){
        assertThat(repository.hasInstitutionHistory(institution)).isFalse();
        assertThat(repository.hasMemberHistory(maker.getMemberId())).isFalse();
        service.supplier(maker,"Synthetic retained supplier","Synthetic supplier identity evidence");
        assertThat(repository.hasInstitutionHistory(institution)).isTrue();
        assertThat(repository.hasMemberHistory(maker.getMemberId())).isTrue();
        assertThat(repository.hasMemberHistory(checker.getMemberId())).isFalse();
    }
    @Test void draftSourceAndRejectedReviewerKeepIndependentHistory(){
        var d=service.create(maker,command(Kind.DIRECT_EXPENSE,"2.01",null));
        assertThat(repository.hasInstitutionHistory(institution)).isTrue();
        assertThat(repository.hasMemberHistory(maker.getMemberId())).isTrue();
        assertThat(repository.hasMemberHistory(checker.getMemberId())).isFalse();
        service.reject(checker,d.id(),"Synthetic rejection evidence");
        assertThat(repository.hasMemberHistory(checker.getMemberId())).isTrue();
    }
    @Test void rejectedSubmittedSourceRetainsCancellationAndExactRetry(){
        var d=submitted(Kind.CAPITAL_RECEIPT,"2.01",null);
        var rejected=service.reject(checker,d.id(),"Independent source rejection");
        assertThat(rejected.state()).isEqualTo("REJECTED");
        assertThat(books.journal(checker,d.journalId()).state()).isEqualTo("CANCELLED");
        assertThat(books.sourceCancellation(checker,d.journalId())).isPresent();
        assertThat(service.reject(checker,d.id(),"Independent source rejection").id()).isEqualTo(d.id());
        assertThatThrownBy(()->service.reject(checker,d.id(),"Changed evidence")).hasMessage("finance.business.error.retry");
        assertThatThrownBy(()->books.approve(checker,d.journalId(),"Attempt resurrection")).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select count(*) from gl_source_cancellation where journal_id=?",Integer.class,d.journalId())).isEqualTo(1);
        assertThat(repository.remaining(d.id())).isZero();
    }
    @Test void submittedSourceCannotBeRejectedInDatabaseWithoutItsJournalCancellation(){
        var d=submitted(Kind.CAPITAL_RECEIPT,"2.01",null);
        assertThatThrownBy(()->jdbc.update("update accounting_business_document set state='REJECTED',checker_id=?,approval_evidence='Unlinked rejection' where id=?",checker.getMemberId(),d.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(service.view(checker,d.id()).state()).isEqualTo("SUBMITTED");
        assertThat(books.journal(checker,d.journalId()).state()).isEqualTo("DRAFT");
    }
    @Test void rejectionAuditFailureRollsBackSourceAndCancellationTogether(){
        var d=submitted(Kind.CAPITAL_RECEIPT,"2.01",null);
        doThrow(new IllegalStateException("Synthetic rejection audit failure")).when(audit).logEvent(anyString(),eq(d.id()),eq("SOURCE_REJECTED"),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),any());
        assertThatThrownBy(()->service.reject(checker,d.id(),"Independent rejection")).isInstanceOf(IllegalStateException.class);
        assertThat(service.view(checker,d.id()).state()).isEqualTo("SUBMITTED");
        assertThat(books.journal(checker,d.journalId()).state()).isEqualTo("DRAFT");
        assertThat(books.sourceCancellation(checker,d.journalId())).isEmpty();
    }
    @Test void rejectedCorrectionReleasesReservationWithoutDeletingEitherJournal(){
        var original=posted(Kind.EXPENSE_INVOICE,"12.01",null);
        var first=service.create(third,command(Kind.BUSINESS_REVERSAL,"12.01",original.id()));service.submit(third,first.id());
        service.reject(fourth,first.id(),"Reject first correction evidence");
        var replacement=service.create(third,command(Kind.BUSINESS_REVERSAL,"12.01",original.id()));service.submit(third,replacement.id());
        var posted=service.approveAndPost(fourth,replacement.id(),"Independent replacement correction",true);
        assertThat(books.journal(third,service.view(third,first.id()).journalId()).state()).isEqualTo("CANCELLED");
        assertThat(books.journal(third,posted.journalId()).reversesId()).isEqualTo(original.journalId());
        assertThat(balance("OPERATING_EXPENSE")).isZero();assertThat(balance("SUPPLIER_PAYABLE")).isZero();
    }
    @Test void sourceEvidenceAndAssetCountersCannotBeEditedWithoutPostedAdjustments(){
        UUID supplier=service.supplier(maker,"Retained supplier","Synthetic identity evidence");
        assertThatThrownBy(()->jdbc.update("delete from accounting_supplier where id=?",supplier)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("update accounting_supplier set evidence_reference='Replacement' where id=?",supplier)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        var asset=posted(Kind.ASSET_PURCHASE,"100.00",null);posted(Kind.DEPRECIATION,"20.00",asset.id());
        assertThatThrownBy(()->jdbc.update("update accounting_fixed_asset set accumulated_depreciation=25 where id=?",asset.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("update accounting_fixed_asset set disposed=true where id=?",asset.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("delete from accounting_fixed_asset where id=?",asset.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(service.assets(maker,0).rows().getFirst().depreciation()).isEqualByComparingTo("20.00");
    }
    @Test void concurrentIdenticalRejectionsKeepOneCancellationAndOneSourceAudit()throws Exception{
        var d=submitted(Kind.CAPITAL_RECEIPT,"2.01",null);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->service.reject(checker,d.id(),"Independent concurrent rejection"));
            var b=pool.submit(()->service.reject(checker,d.id(),"Independent concurrent rejection"));
            assertThat(a.get(30,TimeUnit.SECONDS).state()).isEqualTo("REJECTED");
            assertThat(b.get(30,TimeUnit.SECONDS).state()).isEqualTo("REJECTED");
        }
        assertThat(jdbc.queryForObject("select count(*) from gl_source_cancellation where journal_id=?",Integer.class,d.journalId())).isEqualTo(1);
        verify(audit,times(1)).logEvent(anyString(),eq(d.id()),eq("SOURCE_REJECTED"),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),any());
    }
    @Test void independentlyRejectedMoneyReferenceCanBeReusedByReviewedReplacement(){
        BigDecimal openingCapital=balance("OWNER_CAPITAL");
        var original=submitted(Kind.CAPITAL_RECEIPT,"2.01",null);service.reject(checker,original.id(),"Reject original incomplete evidence");
        var c=original.command();var command=new Command(UUID.randomUUID(),c.kind(),c.effectiveDate(),c.amount(),c.loanId(),c.relatedDocumentId(),c.supplierId(),c.description(),c.evidenceReference(),c.channelReference(),c.moneyAccountKey(),c.destinationBranch(),c.loanNumber(),c.firstRepaymentDate(),c.frequency(),c.installmentAmount());
        var replacement=service.create(maker,command);service.submit(maker,replacement.id());service.approveAndPost(checker,replacement.id(),"Review corrected evidence",true);
        assertThat(books.journal(checker,original.journalId()).state()).isEqualTo("CANCELLED");
        assertThat(balance("OWNER_CAPITAL")).isEqualByComparingTo(openingCapital.subtract(new BigDecimal("2.01")));
        assertThat(repository.remaining(original.id())).isZero();assertThat(repository.remaining(replacement.id())).isEqualByComparingTo("2.01");
    }
    @Test void reviewedSourceCannotPostWithoutTheExactLiveRelease() {
        var leaf=proxy(new AccountingReleaseGateService(new AccountingReleaseRepository(jdbc,JsonMapper.builder().findAndAddModules().build())),transactionManager);
        var restricted=businessService(leaf);
        var d=submitted(Kind.DIRECT_EXPENSE,"2.01",null);
        var originalBank=balance("BANK");clearInvocations(audit);
        assertThatThrownBy(()->restricted.approveAndPost(checker,d.id(),"Independent source review",true))
            .hasMessage("accounting.release.error.restricted");
        assertThat(restricted.view(checker,d.id()).state()).isEqualTo("SUBMITTED");
        assertThat(books.journal(checker,d.journalId()).state()).isEqualTo("DRAFT");
        assertThat(balance("BANK")).isEqualByComparingTo(originalBank);
        assertThat(repository.remaining(d.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from accounting_outbox where journal_id=?",Integer.class,d.journalId())).isZero();
        verifyNoInteractions(audit);
    }
    @Test void sourceEvidenceCanBeSubmittedAndRejectedWhileLiveReleaseIsRestricted() {
        var leaf=proxy(new AccountingReleaseGateService(new AccountingReleaseRepository(jdbc,JsonMapper.builder().findAndAddModules().build())),transactionManager);
        var restricted=businessService(leaf);
        var d=restricted.create(maker,command(Kind.CAPITAL_RECEIPT,"2.01",null));
        var submitted=restricted.submit(maker,d.id());
        assertThat(restricted.reject(checker,d.id(),"Independent rejection before activation").state()).isEqualTo("REJECTED");
        assertThat(books.journal(checker,submitted.journalId()).state()).isEqualTo("CANCELLED");
        assertThat(countSources("POSTED")).isZero();
    }
    @Test void releaseWithdrawalBlocksNewMoneyAndRetainsExactPostedRetries() {
        var posted=posted(Kind.CAPITAL_RECEIPT,"2.01",null);
        clearInvocations(releaseGate);
        doThrow(new IllegalArgumentException("accounting.release.error.restricted")).when(releaseGate).requireLiveRelease(any(),any(),anyInt());
        assertThat(service.approveAndPost(checker,posted.id(),"Synthetic independent evidence",true).id()).isEqualTo(posted.id());
        verifyNoInteractions(releaseGate);
        var next=submitted(Kind.CAPITAL_RECEIPT,"1.01",null);
        assertThatThrownBy(()->service.approveAndPost(checker,next.id(),"Independent next source review",true)).hasMessage("accounting.release.error.restricted");
        verify(releaseGate).requireLiveRelease(checker,policy.id(),policy.version());
        assertThat(countSources("POSTED")).isEqualTo(1);
    }
    @Test void setupAndPriorAndTargetPeriodsAreLockedBeforeTheFreshReleaseDecision() {
        var date=LocalDate.of(2026,11,2);
        when(clock.today()).thenReturn(date);when(clock.now()).thenReturn(OffsetDateTime.parse("2026-11-02T12:00:00+03:00"));
        UUID target=books.createPeriod(maker,date,date.plusMonths(1));
        UUID prior=jdbc.queryForObject("select id from accounting_period where sacco_id=? and starts_on=?",UUID.class,institution,OPENING);
        var c=command(Kind.CAPITAL_RECEIPT,"2.01",null);
        var dated=new Command(c.requestKey(),c.kind(),date,c.amount(),c.loanId(),c.relatedDocumentId(),c.supplierId(),c.description(),c.evidenceReference(),c.channelReference(),c.moneyAccountKey(),c.destinationBranch(),c.loanNumber(),c.firstRepaymentDate(),c.frequency(),c.installmentAmount());
        var d=service.create(maker,dated);service.submit(maker,d.id());
        doAnswer(invocation->{
            assertThat(jdbc.queryForObject("select current_setting('transaction_isolation')",String.class)).isEqualTo("read committed");
            try(var pool=Executors.newSingleThreadExecutor()) {
                for(UUID period:List.of(prior,target)) {
                    var blocked=pool.submit(()->databaseLockWasBlocked(()->jdbc.queryForObject("select id from accounting_period where id=? for update",UUID.class,period)));
                    assertThat(blocked.get(30,TimeUnit.SECONDS)).isTrue();
                }
                var metadata=pool.submit(()->databaseLockWasBlocked(()->jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","GL_SETUP/"+institution)));
                assertThat(metadata.get(30,TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(releaseGate).requireLiveRelease(checker,policy.id(),policy.version());
        assertThat(service.approveAndPost(checker,d.id(),"Independent future-period review",true).state()).isEqualTo("POSTED");
        verify(releaseGate).requireLiveRelease(checker,policy.id(),policy.version());
    }
    private BusinessAccountingService businessService(AccountingReleaseGateService gate) {
        return proxy(new BusinessAccountingService(repository,books,policies,mock(LoanRepaymentLedgerService.class),mock(ManagerService.class),new AccessControlService(),members,claims,clock,audit,institutions,gate,mock(BusinessOperationalBridge.class)),transactionManager);
    }
    private boolean databaseLockWasBlocked(Runnable lock) {
        try {tx.execute(status->{jdbc.execute("set local lock_timeout='500ms'");lock.run();return null;});return false;}
        catch(org.springframework.dao.DataAccessException exception) {
            if(exception.getMostSpecificCause() instanceof java.sql.SQLException sql && "55P03".equals(sql.getSQLState()))return true;
            throw exception;
        }
    }
    private Document submitted(Kind kind,String amount,UUID related){var d=service.create(maker,command(kind,amount,related));return service.submit(maker,d.id());}
    private Document posted(Kind kind,String amount,UUID related){var d=submitted(kind,amount,related);var p=service.approveAndPost(checker,d.id(),"Synthetic independent evidence",true);assertThat(service.approveAndPost(checker,d.id(),"Synthetic independent evidence",true).id()).isEqualTo(p.id());assertThat(jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where journal_id=?",BigDecimal.class,p.journalId())).isZero();return p;}
    private Command command(Kind kind,String amount,UUID related){return new Command(UUID.randomUUID(),kind,DAY,new BigDecimal(amount),null,related,null,"Synthetic "+kind,"Synthetic source evidence",Set.of(Kind.BUSINESS_REVERSAL,Kind.EXPENSE_INVOICE,Kind.SUPPLIER_CREDIT,Kind.PREPAYMENT_RELEASE,Kind.ACCRUAL,Kind.TAX_LIABILITY,Kind.DEPRECIATION).contains(kind)?null:"C-REF-"+UUID.randomUUID(),"BANK",null,null,null,null,null);}
    private BigDecimal balance(String key){return jdbc.queryForObject("select coalesce(sum(l.debit-l.credit),0) from gl_journal_line l join gl_journal j on j.id=l.journal_id where j.sacco_id=? and j.state='POSTED' and l.account_id=?",BigDecimal.class,institution,mappings.get(key));}
    private long countSources(String state){return jdbc.queryForObject("select count(*) from accounting_business_document where sacco_id=? and state=?",Long.class,institution,state);}
    private AppUserPrincipal operator(String branch){UUID id=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,?,?,?,'ACTIVE','MANAGER',now(),false,'synthetic')",id,institution,branch,id.toString(),"Synthetic staff");var m=Member.builder().id(id).saccoId(institution).stationId(branch).memberNo(id.toString()).fullName("Synthetic staff").status(MemberStatus.ACTIVE).position(Position.MANAGER).build();when(members.find(id)).thenReturn(Optional.of(m));return new AppUserPrincipal(m,EnumSet.allOf(UserClaim.class),true);}
    @SuppressWarnings("unchecked") private <T>T proxy(T target,DataSourceTransactionManager manager){var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (T)factory.getProxy();}
}
