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

@EnabledIfEnvironmentVariable(named="MICROFINANCE_ACCOUNTING_C_DATABASE_URL",matches="(?:jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/(?:microfinance_accounting_c_test(?:_baseline_20261002|_source_corrections_20261003|_release_gate_20261003|_openings_20261004|_owned_sources_20261003(?:_v2)?)?|microfinance_accounting_h_release_combined_test_20261004)|jdbc:postgresql://127\\.0\\.0\\.1:55439/microfinance_accounting_h_release_integrity_test_20261004)")
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
            mappings.put(key,books.createAccount(maker,new AccountCommand(key,"Synthetic "+key,type,Set.of("LIABILITY","EQUITY","INCOME").contains(type)?"CREDIT":"DEBIT","POSTING",Set.of("CASH","BANK","MOBILE_MONEY").contains(key)?key:"OTHER",null)));
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
    private BusinessInternalTransferSource transferProvider(){return proxy(new BusinessInternalTransferSource(new com.sacco.mvp.accounting.business.repository.BusinessTransferSourceRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc)),new BusinessSourceProofAuthorization(members,claims,institutions),clock,JsonMapper.builder().findAndAddModules().build()),transactionManager);}
    private Document transfer(AppUserPrincipal sourceMaker,AppUserPrincipal sourceChecker,Kind kind,String amount,UUID related,String destination){
        var command=new Command(UUID.randomUUID(),kind,DAY,new BigDecimal(amount),null,related,null,"Synthetic actual internal transfer","Synthetic independently verified transfer source","TRANSFER-"+UUID.randomUUID(),"BANK",destination,null,null,null,null);
        var draft=service.create(sourceMaker,command);service.submit(sourceMaker,draft.id());return service.approveAndPost(sourceChecker,draft.id(),"Independent synthetic transfer review",true);
    }
    @Test void actualOwningOutgoingAndIncomingTransfersProjectExactScopedJournalLegs(){
        when(institutions.findStation(institution,"B2")).thenReturn(Optional.of(SaccoStation.builder().saccoId(institution).stationId("B2").active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,created_at,updated_at,access_status) values(?,?,'B2',true,now(),now(),'ACTIVE')",UUID.randomUUID(),institution);
        var b2maker=operator("B2");var b2checker=operator("B2");var b2opening=books.importOpening(b2maker,new JournalCommand(UUID.randomUUID(),"SYNTHETIC-B2-OPENING",OPENING,"Synthetic reviewed B2 cash/capital",null,List.of(new Line(mappings.get("BANK"),new BigDecimal("100.00"),BigDecimal.ZERO),new Line(mappings.get("OWNER_CAPITAL"),BigDecimal.ZERO,new BigDecimal("100.00")))));
        books.approve(b2checker,b2opening.id(),"Independent B2 opening evidence");books.post(b2checker,b2opening.id(),true);
        var out=transfer(maker,checker,Kind.INTERNAL_TRANSFER_OUT,"10.01",null,"B2");var in=transfer(b2maker,b2checker,Kind.INTERNAL_TRANSFER_IN,"10.01",out.id(),null);var provider=transferProvider();
        var own=tx.execute(s->provider.reviewedTransfers(maker,OPENING,DAY,clock.now()));var other=tx.execute(s->provider.reviewedTransfers(b2maker,OPENING,DAY,clock.now()));
        assertThat(own.complete()).isTrue();assertThat(own.unknownJournalIds()).isEmpty();assertThat(own.legs()).hasSize(1);var a=own.legs().getFirst();assertThat(a.documentId()).isEqualTo(out.id());assertThat(a.journalId()).isEqualTo(out.journalId());assertThat(a.transferId()).isEqualTo(out.id());assertThat(a.destinationBranch()).isEqualTo("B2");assertThat(a.signedMoneyAmount()).isEqualByComparingTo("-10.01");assertThat(a.signedCounterpartAmount()).isEqualByComparingTo("10.01");assertThat(a.sourceDigest()).matches("[0-9a-f]{64}");
        assertThat(other.complete()).isTrue();assertThat(other.legs()).hasSize(1);var b=other.legs().getFirst();assertThat(b.documentId()).isEqualTo(in.id());assertThat(b.journalId()).isEqualTo(in.journalId());assertThat(b.transferId()).isEqualTo(out.id());assertThat(b.relatedDocumentId()).isEqualTo(out.id());assertThat(b.branch()).isEqualTo("B2");assertThat(b.signedMoneyAmount()).isEqualByComparingTo("10.01");assertThat(b.signedCounterpartAmount()).isEqualByComparingTo("-10.01");
        when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(EnumSet.of(UserClaim.FINANCIAL_REPORTS_VIEW,UserClaim.FINANCIAL_REPORTS_INSTITUTION));
        assertThatThrownBy(()->service.view(maker,in.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);assertThat(tx.<Boolean>execute(s->provider.currentForInstitutionClose(maker,"B2",OPENING,DAY,clock.now(),other))).isTrue();
        when(institutions.findStation(institution,"B2")).thenReturn(Optional.of(SaccoStation.builder().saccoId(institution).stationId("B2").active(false).accessStatus(SaccoAccessStatus.SUSPENDED).build()));
        assertThat(tx.<Boolean>execute(s->provider.currentForInstitutionClose(maker,"B2",OPENING,DAY,clock.now(),other))).isTrue();
        when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(EnumSet.of(UserClaim.FINANCIAL_REPORTS_VIEW));assertThatThrownBy(()->tx.execute(s->provider.currentForInstitutionClose(maker,"B2",OPENING,DAY,clock.now(),other))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void manualInternalMovementsStayUnknownEvenWhenTheyNetToZero(){
        for(boolean reverse:List.of(false,true)){var debit=mappings.get(reverse?"BANK":"INTERNAL_DUE_FROM");var credit=mappings.get(reverse?"INTERNAL_DUE_FROM":"BANK");var journal=books.draftManual(maker,new JournalCommand(UUID.randomUUID(),"UNKNOWN-INTERNAL-"+reverse,DAY,"Synthetic unidentified internal movement",null,List.of(new Line(debit,new BigDecimal("2.01"),BigDecimal.ZERO),new Line(credit,BigDecimal.ZERO,new BigDecimal("2.01")))));books.approve(checker,journal.id(),"Independent manual review");books.post(checker,journal.id(),false);}
        var proof=tx.execute(s->transferProvider().reviewedTransfers(maker,OPENING,DAY,clock.now()));assertThat(proof.complete()).isFalse();assertThat(proof.legs()).isEmpty();assertThat(proof.unknownJournalIds()).hasSize(2);assertThat(balance("INTERNAL_DUE_FROM")).isZero();
    }
    @Test void transferProjectionRequiresFreshScopeCutoverAndOwningTransaction(){
        var provider=transferProvider();assertThatThrownBy(()->provider.reviewedTransfers(maker,OPENING,DAY,clock.now())).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        var proof=tx.execute(s->provider.reviewedTransfers(maker,OPENING,OPENING,clock.now()));assertThat(proof.legs()).isEmpty();assertThat(proof.unknownJournalIds()).isEmpty();
        assertThatThrownBy(()->tx.execute(s->provider.reviewedTransfers(maker,OPENING.minusDays(1),DAY,clock.now()))).hasMessage("business.source.error.opening");
        assertThatThrownBy(()->tx.execute(s->provider.reviewedTransfers(maker,OPENING,DAY,clock.now().plusSeconds(1)))).hasMessage("business.source.error.dates");
        when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(EnumSet.of(UserClaim.ACCOUNTING_BUSINESS_VIEW));assertThatThrownBy(()->tx.execute(s->provider.reviewedTransfers(maker,OPENING,DAY,clock.now()))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void currentTransferProofRejectsAnewBackdatedMovementAfterFrozenCutoff(){
        var provider=transferProvider();var before=clock.now();var frozen=tx.execute(s->provider.reviewedTransfers(maker,OPENING,DAY,before));when(clock.now()).thenReturn(before.plusMinutes(1));
        when(institutions.findStation(institution,"B2")).thenReturn(Optional.of(SaccoStation.builder().saccoId(institution).stationId("B2").active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,created_at,updated_at,access_status) values(?,?,'B2',true,now(),now(),'ACTIVE')",UUID.randomUUID(),institution);
        transfer(maker,checker,Kind.INTERNAL_TRANSFER_OUT,"1.01",null,"B2");var historical=tx.execute(s->provider.reviewedTransfers(maker,OPENING,DAY,before));assertThat(historical).isEqualTo(frozen);assertThat(tx.<Boolean>execute(s->provider.currentForInstitutionClose(maker,"B1",OPENING,DAY,before,frozen))).isFalse();
    }
    private BusinessOpeningService openingService(){return proxy(new BusinessOpeningService(new com.sacco.mvp.accounting.business.repository.BusinessOpeningRepository(jdbc,JsonMapper.builder().findAndAddModules().build()),new BusinessSourceProofAuthorization(members,claims,institutions),clock,audit),transactionManager);}
    private org.springframework.mock.web.MockMultipartFile openingFile(String body){return new org.springframework.mock.web.MockMultipartFile("file","source-opening.csv","text/csv",("reference,signed_balance,evidence\n"+body).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private com.sacco.mvp.accounting.business.dto.BusinessOpeningDtos.Command openingCommand(String purpose,boolean complete){UUID gl=jdbc.queryForObject("select opening_journal_id from gl_cutover_coverage where sacco_id=? and station_id='B1'",UUID.class,institution);return new com.sacco.mvp.accounting.business.dto.BusinessOpeningDtos.Command(UUID.randomUUID(),mappings.get(purpose),gl,OPENING,purpose,"Synthetic independently complete source register",complete);}
    @Test void explicitSourceZeroRequiresIndependentReviewAndCreatesNoMoney()throws Exception{
        var openings=openingService();var c=openingCommand("INTERNAL_DUE_FROM",true);long money=jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Long.class,institution);var d=openings.importFile(maker,c,openingFile(""));
        assertThat(openings.importFile(maker,c,openingFile("")).id()).isEqualTo(d.id());assertThat(d.decision()).isNull();assertThatThrownBy(()->openings.review(maker,d.id(),"APPROVED","Own approval",true)).hasMessage("finance.business.opening.error.checker");
        var approved=openings.review(checker,d.id(),"APPROVED","Independent complete zero source review",true);assertThat(approved.decision()).isEqualTo("APPROVED");assertThat(openings.review(checker,d.id(),"APPROVED","Independent complete zero source review",true)).isEqualTo(approved);assertThat(openings.file(checker,d.id()).bytes()).isEqualTo(openingFile("").getBytes());
        assertThat(repository.hasInstitutionHistory(institution)).isTrue();assertThat(repository.hasMemberHistory(maker.getMemberId())).isTrue();assertThat(repository.hasMemberHistory(checker.getMemberId())).isTrue();
        assertThatThrownBy(()->jdbc.update("update accounting_business_opening set evidence='Changed' where id=?",d.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThatThrownBy(()->jdbc.update("delete from accounting_business_opening_review where opening_id=?",d.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Long.class,institution)).isEqualTo(money);
    }
    @Test void stagedIncompleteAndUnlinkedRegistersRemainRejectableWhileAccountingIsRestricted(){
        var openings=openingService();var initial=openingCommand("SUPPLIER_PAYABLE",false);var c=new com.sacco.mvp.accounting.business.dto.BusinessOpeningDtos.Command(initial.requestKey(),initial.account(),null,initial.through(),initial.purpose(),initial.evidence(),false);var d=openings.importFile(maker,c,openingFile("Supplier-A,-1.01,Unverified retained invoice\n"));
        assertThatThrownBy(()->openings.review(checker,d.id(),"APPROVED","Unsupported approval",true)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(openings.view(checker,d.id()).decision()).isNull();assertThat(openings.review(checker,d.id(),"REJECTED","Missing posted opening and complete history",true).decision()).isEqualTo("REJECTED");
        assertThatThrownBy(()->openings.importFile(maker,c,openingFile("Supplier-A,-2.01,Changed retry\n"))).hasMessage("finance.business.opening.error.retry");
    }
    @Test void independentSourceApprovalRejectsGlMismatchNetZeroInternalRowsAndIncompleteCoverage(){
        var openings=openingService();for(var purpose:List.of("SUPPLIER_PAYABLE","INTERNAL_DUE_FROM")){var d=openings.importFile(maker,openingCommand(purpose,true),openingFile(purpose.equals("SUPPLIER_PAYABLE")?"Supplier-A,-1.01,Register\n":"Transfer-A,1.01,Register\nTransfer-B,-1.01,Register\n"));assertThatThrownBy(()->openings.review(checker,d.id(),"APPROVED","Unverified total",true)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(openings.view(maker,d.id()).decision()).isNull();}
        var incomplete=openings.importFile(maker,openingCommand("FUNDING_PRINCIPAL",false),openingFile(""));assertThatThrownBy(()->openings.review(checker,incomplete.id(),"APPROVED","Unknown history",true)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void importedSourceFilesAndApprovalsRequireFreshCurrentBranchBusinessClaims(){
        var openings=openingService();var d=openings.importFile(maker,openingCommand("SUPPLIER_PAYABLE",true),openingFile(""));var foreign=operator("B2");assertThatThrownBy(()->openings.view(foreign,d.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        when(claims.effectiveClaims(eq(checker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(EnumSet.of(UserClaim.FINANCIAL_REPORTS_VIEW,UserClaim.FINANCIAL_REPORTS_INSTITUTION));assertThatThrownBy(()->openings.file(checker,d.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);assertThatThrownBy(()->openings.review(checker,d.id(),"APPROVED","Revoked review",true)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void sourceOpeningApprovalAndImportAuditFailuresRollBackAtomically(){
        var openings=openingService();var c=openingCommand("SUPPLIER_PAYABLE",true);doThrow(new IllegalStateException("audit failure")).when(audit).logEvent(anyString(),any(),eq("SOURCE_OPENING_IMPORTED"),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),any());assertThatThrownBy(()->openings.importFile(maker,c,openingFile(""))).hasMessage("audit failure");assertThat(jdbc.queryForObject("select count(*) from accounting_business_opening where sacco_id=?",Integer.class,institution)).isZero();reset(audit);
        var d=openings.importFile(maker,c,openingFile(""));doThrow(new IllegalStateException("review audit failure")).when(audit).logEvent(anyString(),any(),eq("SOURCE_OPENING_APPROVED"),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),any());assertThatThrownBy(()->openings.review(checker,d.id(),"APPROVED","Independent evidence",true)).hasMessage("review audit failure");assertThat(openings.view(maker,d.id()).decision()).isNull();
    }
    @Test void concurrentIdenticalIndependentSourceReviewsRetainOneDecisionAndAudit()throws Exception{
        var openings=openingService();var d=openings.importFile(maker,openingCommand("SUPPLIER_PAYABLE",true),openingFile(""));clearInvocations(audit);try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->openings.review(checker,d.id(),"APPROVED","Same independent review",true));var b=pool.submit(()->openings.review(checker,d.id(),"APPROVED","Same independent review",true));assertThat(a.get(30,TimeUnit.SECONDS).decision()).isEqualTo("APPROVED");assertThat(b.get(30,TimeUnit.SECONDS).decision()).isEqualTo("APPROVED");}assertThat(jdbc.queryForObject("select count(*) from accounting_business_opening_review where opening_id=?",Integer.class,d.id())).isEqualTo(1);verify(audit,times(1)).logEvent(anyString(),eq(d.id()),eq("SOURCE_OPENING_APPROVED"),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),any());
    }
    @Test void positiveSupplierSourceRegisterMustAgreeWithItsActualIndependentlyPostedOpening(){
        when(institutions.findStation(institution,"B2")).thenReturn(Optional.of(SaccoStation.builder().saccoId(institution).stationId("B2").active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));var branchMaker=operator("B2");var branchChecker=operator("B2");var gl=books.importOpening(branchMaker,new JournalCommand(UUID.randomUUID(),"SYNTHETIC-SUPPLIER-OPENING",OPENING,"Synthetic complete supplier and cash register",null,List.of(new Line(mappings.get("BANK"),new BigDecimal("100.01"),BigDecimal.ZERO),new Line(mappings.get("SUPPLIER_PAYABLE"),BigDecimal.ZERO,new BigDecimal("100.01")))));books.approve(branchChecker,gl.id(),"Independent synthetic GL reconciliation");books.post(branchChecker,gl.id(),true);
        var c=new com.sacco.mvp.accounting.business.dto.BusinessOpeningDtos.Command(UUID.randomUUID(),mappings.get("SUPPLIER_PAYABLE"),gl.id(),OPENING,"SUPPLIER_PAYABLE","Synthetic complete verified supplier register",true);var openings=openingService();var d=openings.importFile(branchMaker,c,openingFile("Supplier-A,-60.00,Retained verified invoice A\nSupplier-B,-40.01,Retained verified invoice B\n"));var approved=openings.review(branchChecker,d.id(),"APPROVED","Independent source invoices and GL agreement",true);assertThat(approved.preview().signedBalance()).isEqualByComparingTo("-100.01");assertThat(approved.preview().rows()).hasSize(2);assertThat(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=? and station_id='B2'",Integer.class,institution)).isEqualTo(1);
    }
    @Test void sourceRegisterBytesAndRowsCannotBeForgedThroughDirectRepositoryWrites()throws Exception{
        var c=openingCommand("SUPPLIER_PAYABLE",true);var original=BusinessOpeningImport.parse(c,openingFile("Supplier-A,-1.01,Proof\n"));byte[] changed=openingFile("Supplier-A,-2.01,Proof\n").getBytes();var v=original.preview();var forged=new com.sacco.mvp.accounting.business.dto.BusinessOpeningDtos.Preview(c,v.filename(),BusinessOpeningImport.sha(changed),v.rows(),v.signedBalance());var repo=new com.sacco.mvp.accounting.business.repository.BusinessOpeningRepository(jdbc,JsonMapper.builder().findAndAddModules().build());assertThatThrownBy(()->tx.execute(status->{repo.insert(UUID.randomUUID(),institution,"B1",maker.getMemberId(),new BusinessOpeningImport.Parsed(forged,changed),clock.now());return null;})).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(jdbc.queryForObject("select count(*) from accounting_business_opening where sacco_id=?",Integer.class,institution)).isZero();
    }
    @Test void approvedBranchCloseRequiresControlledReopeningBeforeSourceCoverageChanges(){
        var openings=openingService();var d=openings.importFile(maker,openingCommand("SUPPLIER_PAYABLE",true),openingFile(""));
        tx.execute(status->{
            UUID period=jdbc.queryForObject("select id from accounting_period where sacco_id=?",UUID.class,institution);UUID close=UUID.randomUUID();
            String snapshot="{}";String checksum=BusinessOpeningImport.sha(snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jdbc.update("insert into accounting_close_review(id,sacco_id,station_id,period_id,version,action,snapshot_json,checksum,evidence,maker_id,recorded_at) values(?,?,?, ?,1,'CLOSE',?,?,'Synthetic prior approved branch close',?,?)",close,institution,"B1",period,snapshot,checksum,maker.getMemberId(),clock.now());
            jdbc.update("insert into accounting_close_decision(review_id,checker_id,evidence,decided_at) values(?,?,'Synthetic independent branch close',?)",close,checker.getMemberId(),clock.now());
            assertThatThrownBy(()->openings.review(checker,d.id(),"APPROVED","Late opening approval",true)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            status.setRollbackOnly();return null;
        });
        assertThat(openings.view(maker,d.id()).decision()).isNull();
        assertThat(jdbc.queryForObject("select count(*) from accounting_close_review where sacco_id=?",Integer.class,institution)).isZero();
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
