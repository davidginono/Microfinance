package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.reconciliation.dto.ReconciliationReport.*;
import com.sacco.mvp.accounting.reconciliation.repository.ReconciliationRepository;
import com.sacco.mvp.accounting.reconciliation.service.*;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.service.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Corruption cases use temporary shadow tables; retained financial evidence and its guards stay intact. */
@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupReconciliationPostgresTest {
    final GroupSharedWaiverPostgresTest w=new GroupSharedWaiverPostgresTest();final GroupSharedLoanPostgresTest s=w.s;final LoanRecordingPostgresTest f=w.f;
    ReconciliationService service;
    static ReconciliationService register(LoanRecordingPostgresTest f){
        f.context.registerBean(ReconciliationRepository.class,()->new ReconciliationRepository(new NamedParameterJdbcTemplate(f.jdbc)));
        f.context.registerBean(ReconciliationService.class,()->new ReconciliationService(f.context.getBean(ReconciliationRepository.class),f.context.getBean(GeneralLedgerService.class),f.context.getBean(ApplicationClock.class),new ReportExportLimiter(2)));
        return f.context.getBean(ReconciliationService.class);
    }
    @BeforeAll void start(){w.start();service=register(f);}
    @AfterAll void stop(){w.stop();}
    @BeforeEach void fixture(){w.setup();}
    Parameters p(String component){return new Parameters(LoanRecordingPostgresTest.TODAY.minusMonths(1),w.date,f.context.getBean(ApplicationClock.class).now(),component,0);}
    Result report(){return service.report(f.actor,p("ALL"),false);}
    TransactionTemplate tx(){var t=new TransactionTemplate(f.context.getBean(PlatformTransactionManager.class));t.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);return t;}
    void shadow(String table){f.jdbc.execute("create temporary table "+table+" on commit drop as select * from public."+table);f.jdbc.execute("set local search_path to pg_temp,public");}
    UUID payment(){return f.service.repay(f.actor,w.datedPayment("120.00")).id();}
    @Test void matchingSharedFundingPaymentWaiverAndCorrectionsAreOmitted(){
        UUID waiver=w.waive("1.01");assertThat(report().count()).isZero();
        w.ledger.waiveInterest(s.loan,f.operator("B1"),UUID.randomUUID(),w.date,new BigDecimal("1.01"),"Synthetic independent waiver correction",waiver);assertThat(report().count()).isZero();
        UUID paid=payment();assertThat(report().count()).isZero();f.service.reverse(f.operator("B1"),paid,UUID.randomUUID(),w.date,"Synthetic payment correction");assertThat(report().count()).isZero();
    }
    @Test void missingMemberRowsAreExceptionsEvenWithMatchingBooksAndZeroComponents(){
        UUID paid=payment();assertThat(report().count()).isZero();
        tx().executeWithoutResult(t->{shadow("lending_group_shared_movement");f.jdbc.update("delete from lending_group_shared_movement where posting_id=? and client_id=?",paid,f.client);
            var r=report();assertThat(r.rows()).hasSize(4).allSatisfy(x->{assertThat(x.section()).isEqualTo("ATTRIBUTION");assertThat(x.issue()).isEqualTo("ATTRIBUTION_MISSING");assertThat(x.slDebit()).isNull();assertThat(x.glDebit()).isNull();assertThat(x.amounts()).hasSize(3);});assertThat(r.rows()).anySatisfy(x->{assertThat(x.component()).isEqualTo("GROUP_PRINCIPAL");assertThat(x.sourceAmount()).isEqualByComparingTo("100.00");assertThat(x.attributedAmount()).isLessThan(x.sourceAmount());});
        });assertThat(report().count()).isZero();
    }
    @Test void duplicateAttributionIsDetectedEvenWhenItsAmountIsZero(){UUID paid=payment();tx().executeWithoutResult(t->{shadow("lending_group_shared_movement");f.jdbc.update("insert into lending_group_shared_movement select * from lending_group_shared_movement where posting_id=?",paid);assertThat(report().rows()).hasSize(4).allSatisfy(x->assertThat(x.issue()).isEqualTo("ATTRIBUTION_DUPLICATE")).anySatisfy(x->{assertThat(x.component()).isEqualTo("GROUP_CHARGES");assertThat(x.attributionVariance()).isZero();});});}
    @Test void principalDifferencesDoNotBecomeSlGlVariancesAndPdfUsesDistinctHeadings(){
        UUID paid=payment();tx().executeWithoutResult(t->{shadow("lending_group_shared_movement");f.jdbc.update("update lending_group_shared_movement set principal=principal+0.01 where posting_id=? and client_id=?",paid,f.client);var r=service.report(f.actor,p("GROUP_ATTRIBUTION"),true);assertThat(r.rows()).singleElement().satisfies(x->{assertThat(x.issue()).isEqualTo("ATTRIBUTION_AMOUNT");assertThat(x.attributionVariance()).isEqualByComparingTo("0.01");assertThat(x.debitVariance()).isNull();});
            var messages=new ReloadableResourceBundleMessageSource();messages.setBasename("classpath:messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);var pdf=new ReconciliationPdf(messages,new ReportExportLimiter(2));
            for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw")))try{byte[] bytes=pdf.build(r,locale);try(var doc=Loader.loadPDF(bytes)){String text=new PDFTextStripper().getText(doc);assertThat(text).contains(locale.getLanguage().equals("sw")?"Kiasi cha chanzo":"Source amount").doesNotContain("null","reconciliation.issue.");}Files.write(Path.of("target/group-attribution-"+locale.getLanguage()+".pdf"),bytes);}catch(Exception e){throw new AssertionError(e);}
        });
    }
    @Test void originalAllocationsConfirmedTransfersAndUnknownTotalsRemainDistinct(){tx().executeWithoutResult(t->{shadow("lending_group_shared_allocation");f.jdbc.update("delete from lending_group_shared_allocation where cycle_id=? and client_id=?",s.s.cycle,f.client);assertThat(report().rows()).hasSize(5).allSatisfy(x->{assertThat(x.activity()).isEqualTo("ALLOCATIONS");assertThat(x.issue()).isEqualTo("ATTRIBUTION_MISSING");});});tx().executeWithoutResult(t->{shadow("lending_group_shared_transfer");f.jdbc.update("delete from lending_group_shared_transfer where cycle_id=?",s.s.cycle);var r=report();assertThat(r.rows()).singleElement().satisfies(x->{assertThat(x.activity()).isEqualTo("FUNDING_ALLOCATIONS");assertThat(x.attributedAmount()).isNull();});assertThat(r.groups()).singleElement().satisfies(g->assertThat(g.totals()).containsExactly(new BigDecimal("1000.01"),null,null));});}
    @Test void waiversRespectRecordedCutoffAndEffectiveDateWithoutTreatingMissingHistoryAsZero(){UUID id=w.waive("1.01");var before=new Parameters(p("ALL").from(),w.date,p("ALL").cutoff().minusSeconds(1),"ALL",0);tx().executeWithoutResult(t->{shadow("lending_group_shared_waiver");f.jdbc.update("delete from lending_group_shared_waiver where waiver_id=?",id);assertThat(service.report(f.actor,before,false).count()).isZero();assertThat(report().rows()).singleElement().satisfies(x->{assertThat(x.component()).isEqualTo("GROUP_WAIVED_INTEREST");assertThat(x.attributedAmount()).isNull();assertThat(x.issue()).isEqualTo("ATTRIBUTION_MISSING");});var old=new Parameters(p("ALL").from(),LoanRecordingPostgresTest.TODAY,p("ALL").cutoff(),"ALL",0);assertThat(service.report(f.actor,old,false).count()).isZero();});}
    @Test void writeOffAndRestorationUseIndependentApprovedDecisionAmounts(){
        f.provide(s.loan,"1000.01");var id=f.adjustments().request(f.actor,f.adjustment(s.loan,"WRITE_OFF"));f.adjustments().decide(f.operator("B1"),id,UUID.randomUUID(),true,"Synthetic group write-off");assertThat(report().count()).isZero();tx().executeWithoutResult(t->{shadow("lending_group_shared_adjustment");f.jdbc.update("delete from lending_group_shared_adjustment where request_id=?",id);assertThat(report().rows()).singleElement().satisfies(x->{assertThat(x.component()).isEqualTo("GROUP_WRITE_OFF");assertThat(x.sourceAmount()).isEqualByComparingTo("1000.01");assertThat(x.issue()).isEqualTo("ATTRIBUTION_MISSING");});});var back=f.adjustments().request(f.operator("B1"),f.adjustment(s.loan,"WRITE_BACK"));f.adjustments().decide(f.actor,back,UUID.randomUUID(),true,"Synthetic independent restoration");assertThat(report().count()).isZero();
    }
    @Test void sharedBooksAreComparedAndMissingBorrowerIdentityNeverHidesTheirMoney(){tx().executeWithoutResult(t->{shadow("gl_journal_line");f.jdbc.update("delete from gl_journal_line where account_id=?",f.principal);assertThat(report().rows()).anySatisfy(x->{assertThat(x.section()).isEqualTo("MOVEMENT");assertThat(x.issue()).isEqualTo("SL_ONLY");assertThat(x.debitVariance()).isEqualByComparingTo("1000.01");assertThat(x.client()).startsWith("GRP-");});});tx().executeWithoutResult(t->{shadow("loan_applications");f.jdbc.update("update loan_applications set borrower_party_id=null where id=?",s.loan);assertThat(report().rows()).singleElement().satisfies(x->{assertThat(x.issue()).isEqualTo("BORROWER_UNAVAILABLE");assertThat(x.slDebit()).isNull();});});}
    @Test void sharedReceiptContributionsAndWholeCorrectionReconcileWithoutDoubleCounting(){
        var collections=f.context.getBean(com.sacco.mvp.grouplending.service.GroupCollectionService.class);var form=new com.sacco.mvp.grouplending.dto.GroupCollectionDtos.Form();form.setEffectiveDate(w.date);form.setMoneyAccountId(f.cash);form.setReference("QA-DIAG-"+UUID.randomUUID());form.setRemitter("Synthetic group treasurer");form.setEvidence("Synthetic verified cash");var row=new com.sacco.mvp.grouplending.dto.GroupCollectionDtos.Row();row.setLoanId(s.loan);row.setPayerId(f.client);row.setAmount(new BigDecimal("120.00"));row.setDescription("Synthetic member contribution");form.setRows(new ArrayList<>(List.of(row)));form.setPreviewHash(collections.preview(f.actor,s.s.cycle,form).hash());var receipt=collections.post(f.actor,s.s.cycle,form);assertThat(report().count()).isZero();collections.reverse(f.operator("B1"),receipt.header().id(),UUID.randomUUID(),w.date,"Synthetic independent receipt correction");assertThat(report().count()).isZero();
        tx().executeWithoutResult(status->{shadow("lending_group_contribution");f.jdbc.update("delete from lending_group_contribution where receipt_id=?",receipt.header().id());assertThat(report().rows()).hasSize(4).allSatisfy(x->{assertThat(x.issue()).isEqualTo("ATTRIBUTION_MISSING");assertThat(x.reference()).isEqualTo(receipt.header().number());});});
        tx().executeWithoutResult(status->{shadow("lending_group_receipt_loan");f.jdbc.update("insert into lending_group_receipt_loan select * from lending_group_receipt_loan where receipt_id=?",receipt.header().id());assertThat(report().rows()).anySatisfy(x->{assertThat(x.component()).isEqualTo("GROUP_CONTRIBUTIONS");assertThat(x.issue()).isEqualTo("ATTRIBUTION_DUPLICATE");});});
    }
    @Test void missingFacilityOrFundingExecutionCannotLookLikeCompleteAttribution(){
        tx().executeWithoutResult(t->{shadow("lending_group_shared_facility");f.jdbc.update("delete from lending_group_shared_facility where loan_id=?",s.loan);assertThat(report().rows()).singleElement().satisfies(x->{assertThat(x.component()).isEqualTo("GROUP_SOURCE_LINKS");assertThat(x.issue()).isEqualTo("ATTRIBUTION_MISSING");assertThat(x.sourceAmount()).isNull();});});
        tx().executeWithoutResult(t->{shadow("lending_group_shared_funding");f.jdbc.update("delete from lending_group_shared_funding where loan_id=?",s.loan);assertThat(report().rows()).singleElement().satisfies(x->{assertThat(x.activity()).isEqualTo("FUNDING_ALLOCATIONS");assertThat(x.issue()).isEqualTo("ATTRIBUTION_MISSING");assertThat(x.sourceAmount()).isEqualByComparingTo("1000.01");assertThat(x.attributedAmount()).isNull();});});
    }
    @Test void freshGroupClaimsAndFirstClassBranchScopeControlAttributionAndExports(){
        UUID paid=payment();tx().executeWithoutResult(t->{shadow("lending_group_shared_movement");f.jdbc.update("delete from lending_group_shared_movement where posting_id=?",paid);assertThat(report().count()).isEqualTo(4);assertThat(service.report(f.operator("B2"),p("ALL"),false).count()).isZero();var claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.GROUP_LENDING_VIEW);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(claims);assertThat(report().count()).isZero();assertThatThrownBy(()->service.report(f.actor,p("GROUP_ATTRIBUTION"),false)).isInstanceOf(AccessDeniedException.class);claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.GROUP_LENDING_EXPORT);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(claims);assertThat(service.canExport(f.actor,"ALL")).isFalse();assertThatThrownBy(()->service.report(f.actor,p("ALL"),true)).isInstanceOf(AccessDeniedException.class);assertThat(service.canExport(f.actor,"PRINCIPAL")).isTrue();t.setRollbackOnly();});
    }
}
