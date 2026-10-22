package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.reconciliation.dto.ReconciliationReport.*;
import com.sacco.mvp.accounting.reconciliation.repository.ReconciliationRepository;
import com.sacco.mvp.accounting.reconciliation.service.ReconciliationService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Valid sources use the real owning services and PostgreSQL constraints. Damage scenarios use
 * transaction-local shadow copies, never disable a financial protection or alter retained rows. */
@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_(recording|reconciliation_activity_[a-f0-9]{8})_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReconciliationPostgresTest {
    final LoanRecordingPostgresTest f=new LoanRecordingPostgresTest();
    ReconciliationService service;
    @BeforeAll void start() {
        f.start();
        f.context.registerBean(ReconciliationRepository.class,()->new ReconciliationRepository(new NamedParameterJdbcTemplate(f.jdbc)));
        var clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(LocalDate.of(2026,10,16));when(clock.now()).thenAnswer(i->OffsetDateTime.now());
        f.context.registerBean(ReconciliationService.class,()->new ReconciliationService(f.context.getBean(ReconciliationRepository.class),
            f.context.getBean(GeneralLedgerService.class),clock,new ReportExportLimiter(2)));
        service=f.context.getBean(ReconciliationService.class);
    }
    @BeforeEach void fixture() { f.fixture(); }
    @AfterAll void stop() { f.stop(); }
    Parameters p(String component) { return new Parameters(LocalDate.of(2026,9,1),LocalDate.of(2026,10,8),OffsetDateTime.now(),component,0); }
    Result report() { return service.report(f.actor,p("ALL"),false); }
    UUID loan() { return f.disbursed("D-RECON-"+UUID.randomUUID()); }
    UUID repay(UUID loan) {var command=f.post(loan,"120.00","RECON-"+UUID.randomUUID());command.setInterestAccountId(f.interest);return f.service.repay(f.actor,command).id();}
    TransactionTemplate tx() {var t=new TransactionTemplate(f.context.getBean(PlatformTransactionManager.class));t.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);return t;}
    void shadow(String table) {
        f.jdbc.execute("create temporary table "+table+" on commit drop as select * from public."+table);
        // Explicit path changes force re-planning of previously prepared queries against the copies.
        f.jdbc.execute("set local search_path to pg_temp, public");
    }

    @Test void matchingDisbursementsPartialPaymentsAndLinkedReversalsAreOmitted() {
        var id=loan();var receipt=repay(id);assertThat(report().count()).isZero();
        f.service.reverse(f.operator("B1"),receipt,UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Correction");
        assertThat(report().rows()).isEmpty();assertThat(report().count()).isZero();
    }
    @Test void repaymentActivityUsesItsRecordedSourcesAndExcludesManualVouchersAndDisbursements() {
        var id=loan();var payment=repay(id);
        UUID activity=f.jdbc.queryForObject("select id from gl_activity where sacco_id=? and system_key='LOAN_REPAYMENT'",UUID.class,f.institution);
        var all=p("ALL");var selected=new Parameters(all.from(),all.through(),all.cutoff(),"ALL",0,activity);
        assertThat(service.report(f.actor,selected,false).count()).isZero();
        tx().executeWithoutResult(t->{shadow("gl_journal_line");
            f.jdbc.update("delete from gl_journal_line where account_id=? and credit>0",f.principal);
            assertThat(service.report(f.actor,selected,false).rows()).anySatisfy(row->{assertThat(row.section()).isEqualTo("MOVEMENT");assertThat(row.issue()).isEqualTo("SL_ONLY");});
            var manual=new Parameters(all.from(),all.through(),all.cutoff(),"ALL",0,null,true);
            assertThat(service.report(f.actor,manual,false).count()).isZero();
        });
        f.service.reverse(f.operator("B1"),payment,UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic linked correction");
        assertThat(service.report(f.actor,selected,false).count()).isZero();
    }
    @Test void provisionsWriteOffWriteBackAndDisbursementCorrectionsCompareIndependently() {
        var id=loan();repay(id);f.provide(id,"900.00");
        var request=f.adjustments().request(f.actor,f.adjustment(id,"WRITE_OFF"));var reviewer=f.operator("B1");
        f.adjustments().decide(reviewer,request,UUID.randomUUID(),true,"Write off remaining principal");assertThat(report().count()).isZero();
        var back=f.adjustments().request(reviewer,f.adjustment(id,"WRITE_BACK"));f.adjustments().decide(f.actor,back,UUID.randomUUID(),true,"Reinstate debt");assertThat(report().count()).isZero();
        var other=loan();var reverse=f.adjustments().request(f.actor,f.adjustment(other,"DISBURSEMENT_REVERSAL"));f.adjustments().decide(reviewer,reverse,UUID.randomUUID(),true,"No funds remain");assertThat(report().count()).isZero();
    }
    @Test void missingGlCounterpartHasExactVarianceAndOpeningClosingDifferences() {
        var id=loan();
        tx().executeWithoutResult(t->{shadow("gl_journal_line");f.jdbc.update("delete from gl_journal_line where account_id=?",f.principal);
            var r=report();assertThat(r.rows()).filteredOn(x->x.section().equals("MOVEMENT")).singleElement().satisfies(row->{
                assertThat(row.issue()).isEqualTo("SL_ONLY");assertThat(row.debitVariance()).isEqualByComparingTo("1000.00");assertThat(row.glDebit()).isZero();});
            assertThat(r.rows()).filteredOn(x->x.section().equals("CLOSING")).hasSize(1);
            var later=new Parameters(LocalDate.of(2026,10,1),LocalDate.of(2026,10,8),OffsetDateTime.now(),"PRINCIPAL",0);
            var opening=service.report(f.actor,later,false);assertThat(opening.rows()).extracting(Row::section).containsExactly("OPENING","CLOSING");
        });
    }
    @Test void missingSubledgerCounterpartAndUnknownOpeningAreNotHiddenOrCalledZero() {
        var id=loan();
        tx().executeWithoutResult(t->{shadow("loan_journal_entries");f.jdbc.update("delete from loan_journal_entries where loan_application_id=? and account_code='LOAN_PRINCIPAL'",id);
            var r=report();assertThat(r.rows()).anySatisfy(row->{assertThat(row.issue()).isEqualTo("GL_ONLY");assertThat(row.debitVariance()).isEqualByComparingTo("-1000.00");});
            assertThat(r.rows()).filteredOn(x->x.issue().equals("OPENING_UNAVAILABLE")).singleElement().satisfies(row->assertThat(row.slDebit()).isNull());
        });
    }
    @Test void duplicatesAreDetectedEvenWhenAmountsNetToZero() {
        loan();
        tx().executeWithoutResult(t->{shadow("accountant_loan_post");f.jdbc.update("insert into accountant_loan_post select * from accountant_loan_post where sacco_id=?",f.institution);
            assertThat(report().rows()).filteredOn(x->x.section().equals("MOVEMENT")).singleElement().satisfies(row->{
                assertThat(row.issue()).isEqualTo("DUPLICATE_LINK");assertThat(row.debitVariance()).isZero();assertThat(row.creditVariance()).isZero();});
        });
    }
    @Test void incorrectAmountAndAllocationDoNotPassBecauseBothBooksBalance() {
        var id=loan();repay(id);
        tx().executeWithoutResult(t->{shadow("loan_journal_entries");shadow("gl_journal_line");
            f.jdbc.update("update loan_journal_entries set credit=credit-1 where loan_application_id=? and transaction_id is not null and account_code='LOAN_PRINCIPAL'",id);
            f.jdbc.update("update gl_journal_line set credit=credit-1 where account_id=? and credit>0",f.principal);
            assertThat(report().rows()).filteredOn(x->x.issue().equals("SOURCE_AMOUNT")).singleElement().satisfies(row->assertThat(row.creditVariance()).isZero());
        });
    }
    @Test void standaloneGlDuplicateJournalCannotBeCancelledByAnotherDifference() {
        loan();
        tx().executeWithoutResult(t->{shadow("gl_journal_line");f.jdbc.update("insert into gl_journal_line select * from gl_journal_line where account_id=?",f.principal);
            assertThat(report().rows()).filteredOn(x->x.section().equals("MOVEMENT")).singleElement().satisfies(row->{
                assertThat(row.issue()).isEqualTo("AMOUNT");assertThat(row.debitVariance()).isEqualByComparingTo("-1000.00");});
        });
    }
    @Test void dateMismatchProducesBothSidesInsteadOfMatchingByReferenceAlone() {
        loan();
        tx().executeWithoutResult(t->{shadow("gl_journal");f.jdbc.update("update gl_journal set effective_date=effective_date+1 where sacco_id=?",f.institution);
            assertThat(report().rows()).filteredOn(x->x.section().equals("MOVEMENT")).extracting(Row::issue).containsExactlyInAnyOrder("SL_ONLY","GL_ONLY");});
    }
    @Test void legacyOpeningIsUnavailableRatherThanInvented() {
        var id=loan();
        tx().executeWithoutResult(t->{shadow("accountant_loan_record");f.jdbc.update("delete from accountant_loan_record where loan_id=?",id);
            assertThat(report().rows()).anyMatch(x->x.issue().equals("OPENING_UNAVAILABLE") && x.slDebit()==null);});
    }
    @Test void accruedInterestHasUnknownSubledgerAndVarianceRatherThanFabricatedZero() {
        loan();var accrual=f.account("ACCRUED","ASSET","DEBIT","POSTING","INTEREST_RECEIVABLE");
        tx().executeWithoutResult(t->{shadow("gl_journal_line");
            f.jdbc.update("insert into gl_journal_line select gen_random_uuid(),j.id,j.sacco_id,j.station_id,?,6,0 from gl_journal j where j.sacco_id=?",accrual,f.institution);
            assertThat(report().rows()).filteredOn(r->r.component().equals("INTEREST_RECEIVABLE")).hasSize(2).allSatisfy(row->{
                assertThat(row.issue()).isEqualTo("UNSUPPORTED_INTEREST");assertThat(row.slDebit()).isNull();assertThat(row.debitVariance()).isNull();assertThat(row.glDebit()).isEqualByComparingTo("6");});
        });
    }
    @Test void unrelatedOrdinaryClearingActivityDoesNotBecomeALoanInterestDiscrepancy() {
        var id=loan();repay(id);
        tx().executeWithoutResult(t->{shadow("gl_journal");shadow("gl_journal_line");
            var journal=UUID.randomUUID();
            f.jdbc.update("""
                insert into gl_journal select (jsonb_populate_record(null::gl_journal,to_jsonb(j)||jsonb_build_object(
                  'id',?::text,'source_type','CORE_JOURNAL','source_reference','ordinary-clearing'))).* from gl_journal j
                  where j.sacco_id=? order by j.id limit 1
                """,journal,f.institution);
            f.jdbc.update("insert into gl_journal_line values(?,?,?,?,?,25,0),(?,?,?,?,?,0,25)",
                UUID.randomUUID(),journal,f.institution,"B1",f.cash,UUID.randomUUID(),journal,f.institution,"B1",f.interest);
            assertThat(report().count()).isZero();
        });
    }
    @Test void duplicateJournalSourceIsAnExceptionEvenWhenItsFirstPostingAgrees() {
        loan();
        tx().executeWithoutResult(t->{shadow("gl_journal");shadow("gl_journal_line");
            f.jdbc.update("insert into gl_journal select (jsonb_populate_record(null::gl_journal,to_jsonb(j)||jsonb_build_object('id',gen_random_uuid()))).* from gl_journal j where j.sacco_id=?",f.institution);
            f.jdbc.update("insert into gl_journal_line select gen_random_uuid(),j.id,j.sacco_id,j.station_id,?,1000,0 from gl_journal j where j.sacco_id=? and not exists(select 1 from gl_journal_line z where z.journal_id=j.id)",f.principal,f.institution);
            assertThat(report().rows()).filteredOn(r->r.section().equals("MOVEMENT")).hasSize(2).allSatisfy(row->assertThat(row.issue()).isEqualTo("DUPLICATE_LINK"));
        });
    }
    @Test void repaymentSourceMissingFromBothBooksStillProducesAnException() {
        var id=loan();repay(id);
        tx().executeWithoutResult(t->{shadow("loan_journal_entries");shadow("gl_journal_line");
            f.jdbc.update("delete from loan_journal_entries where loan_application_id=? and transaction_id is not null",id);
            f.jdbc.update("delete from gl_journal_line where journal_id in(select journal_id from accountant_loan_post where loan_id=? and kind='PAYMENT')",id);
            assertThat(report().rows()).filteredOn(r->r.issue().equals("SOURCE_MISSING")).hasSize(2).allSatisfy(row->assertThat(row.slDebit()).isNull());
        });
    }
    @Test void recordedCutoffAndEffectiveDatesApplyToReversalsWithoutRewritingOriginals() {
        var id=loan();var receipt=repay(id);
        var before=new Parameters(LocalDate.of(2026,9,1),LocalDate.of(2026,10,7),OffsetDateTime.now(),"ALL",0);
        assertThat(service.report(f.actor,before,false).count()).isZero();
        f.service.reverse(f.operator("B1"),receipt,UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Linked correction");
        assertThat(service.report(f.actor,before,false).count()).isZero();
        var empty=new Parameters(LocalDate.of(2026,9,1),LocalDate.of(2026,10,8),OffsetDateTime.parse("2026-08-01T00:00:00Z"),"ALL",0);
        assertThat(service.report(f.actor,empty,false).count()).isZero();
    }
    @Test void realBackdatedCollectionFlowsIntoOpeningRatherThanCurrentPeriodMovements() {
        var id=loan();var payment=f.post(id,"120.00","BACKDATED-RECONCILIATION");
        payment.setInterestAccountId(f.interest);payment.setEffectiveDate(LocalDate.of(2026,10,1));f.service.repay(f.actor,payment);
        assertThat(report().count()).isZero();
        tx().executeWithoutResult(t->{shadow("gl_journal_line");
            f.jdbc.update("update gl_journal_line set credit=credit-1 where account_id=? and credit>0",f.principal);
            var later=new Parameters(LocalDate.of(2026,10,2),LocalDate.of(2026,10,8),OffsetDateTime.now(),"PRINCIPAL",0);
            assertThat(service.report(f.actor,later,false).rows()).extracting(Row::section).containsExactly("OPENING","CLOSING");
            assertThat(service.report(f.actor,later,false).rows()).allSatisfy(row->assertThat(row.debitVariance()).isEqualByComparingTo("-1.00"));
        });
    }
    @Test void scopeUsesLoanBranchAfterClientMovesAndRechecksCurrentPermissions() {
        var id=loan();f.jdbc.update("update members set station_id='B2' where id=?",f.client);
        tx().executeWithoutResult(t->{shadow("gl_journal_line");f.jdbc.update("delete from gl_journal_line where account_id=?",f.principal);
            assertThat(report().rows()).anyMatch(x->x.section().equals("MOVEMENT"));
            assertThat(service.report(f.operator("B2"),p("ALL"),false).count()).isZero();});
        when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of());
        assertThatThrownBy(this::report).isInstanceOf(AccessDeniedException.class);
    }
    @Test void concurrentPostingIsAtomicAndReaderSnapshotStaysConsistent() throws Exception {
        var id=loan();
        try(var pool=Executors.newSingleThreadExecutor()) {
            tx().executeWithoutResult(status->{
                assertThat(f.jdbc.queryForObject("select principal_paid from loan_ledgers where loan_application_id=?",BigDecimal.class,id)).isZero();
                try{pool.submit(()->repay(id)).get(30,TimeUnit.SECONDS);}catch(Exception e){throw new RuntimeException(e);}
                assertThat(f.jdbc.queryForObject("select principal_paid from loan_ledgers where loan_application_id=?",BigDecimal.class,id)).isZero();
                assertThat(report().count()).isZero();
            });
        }
        assertThat(f.jdbc.queryForObject("select principal_paid from loan_ledgers where loan_application_id=?",BigDecimal.class,id)).isEqualByComparingTo("100.00");assertThat(report().count()).isZero();
    }
    @Test void anotherInstitutionCannotSeeExistingDiscrepancies() {
        loan();var foreignPrincipal=f.principal;
        var other=new LoanRecordingPostgresTest();other.context=f.context;other.jdbc=f.jdbc;other.service=f.service;other.ledger=f.ledger;
        other.fixture();other.disbursed("D-OTHER-INSTITUTION");
        tx().executeWithoutResult(t->{shadow("gl_journal_line");f.jdbc.update("delete from gl_journal_line where account_id=?",foreignPrincipal);
            assertThat(service.report(other.actor,p("ALL"),false).count()).isZero();});
    }
    @Test void allDiscrepancyRowsArePagedButTotalsAndExportsCoverEveryPage() {
        loan();
        tx().executeWithoutResult(t->{shadow("gl_journal");shadow("gl_journal_line");
            f.jdbc.update("""
                insert into gl_journal select (jsonb_populate_record(null::gl_journal,to_jsonb(j)||jsonb_build_object('id',gen_random_uuid(),
                  'source_reference','synthetic-orphan-'||g.n))).* from gl_journal j cross join generate_series(1,30) g(n) where j.sacco_id=?
                """,f.institution);
            f.jdbc.update("""
                insert into gl_journal_line select gen_random_uuid(),j.id,j.sacco_id,j.station_id,?,1000,0
                from gl_journal j where j.sacco_id=? and j.source_reference like 'synthetic-orphan-%'
                """,f.principal,f.institution);
            var r=report();assertThat(r.rows()).hasSize(25);assertThat(r.hasNext()).isTrue();
            assertThat(r.count()).isEqualTo(31);assertThat(r.count()).isEqualTo(r.groups().stream().mapToLong(Group::count).sum());
            assertThat(r.groups()).filteredOn(g->g.section().equals("MOVEMENT")).singleElement().satisfies(g->assertThat(g.totals().get(2)).isEqualByComparingTo("30000.00"));
            var second=service.report(f.actor,new Parameters(p("ALL").from(),p("ALL").through(),p("ALL").cutoff(),"ALL",1),false);
            assertThat(second.rows()).hasSize(6);assertThat(second.hasNext()).isFalse();
            assertThat(service.report(f.actor,p("ALL"),true).rows()).hasSize((int)r.count());
        });
    }
}
