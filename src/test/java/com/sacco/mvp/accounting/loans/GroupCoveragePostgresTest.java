package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupCoverageDtos.*;
import com.sacco.mvp.grouplending.repository.GroupCoverageRepository;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupCoveragePostgresTest {
    final GroupPersonalGuaranteePostgresTest p=new GroupPersonalGuaranteePostgresTest();
    final LoanRecordingPostgresTest f=p.f;
    GroupCoverageService coverage;UUID obligation,policy;LocalDate day;
    static GroupCoverageService register(LoanRecordingPostgresTest f){
        f.context.registerBean(GroupCoverageRepository.class,()->new GroupCoverageRepository(f.jdbc));
        f.context.registerBean(GroupCoverageService.class,()->new GroupCoverageService(f.context.getBean(GroupCoverageRepository.class),f.context.getBean(com.sacco.mvp.accounting.service.GeneralLedgerService.class),f.context.getBean(GroupConsentService.class),f.context.getBean(ApplicationClock.class),f.context.getBean(AuditService.class),f.context.getBean(tools.jackson.databind.ObjectMapper.class),f.context.getBean(org.springframework.transaction.PlatformTransactionManager.class),new ReportExportLimiter(2)));
        return f.context.getBean(GroupCoverageService.class);
    }
    @BeforeAll void start(){p.start();coverage=register(f);}
    @AfterAll void stop(){p.stop();}
    @BeforeEach void fixture(){p.fixture(LoanRecordingPostgresTest.TODAY.plusDays(1));obligation=p.offer();p.fund(obligation);LocalDate due=f.jdbc.queryForObject("select min(due_date) from loan_ledger_installments where loan_application_id=?",LocalDate.class,p.s.loan);advance(due.plusDays(16));policy=coverage.configure(f.actor,p.s.cycle,policy());}
    void advance(LocalDate date){day=date;var clock=f.context.getBean(ApplicationClock.class);when(clock.today()).thenReturn(date);when(clock.now()).thenReturn(date.atTime(12,0).atOffset(ZoneOffset.ofHours(3)));}
    PolicyForm policy(){var x=new PolicyForm();x.setNoticeDays(14);x.setCaseDays(30);x.setPromiseDays(7);x.setReference("Synthetic independently approved collection policy");x.setEnabled(true);x.setVerified(true);return x;}
    CallForm callForm(){var x=new CallForm();x.setSourceId(obligation);x.setSourceKind("PERSONAL");x.setPolicyId(policy);x.setAmount(new BigDecimal("100.00"));x.setBorrowerNoticeDate(day.minusDays(15));x.setPersonNoticeDate(day.minusDays(15));x.setBorrowerNoticeEvidence("Synthetic verified borrower service record");x.setPersonNoticeEvidence("Synthetic verified guarantor service record");x.setNoticeLanguage("BOTH");x.setNoticesVerified(true);return x;}
    UUID call(){return coverage.call(f.actor,p.s.cycle,callForm(),GroupOrganizationPostgresTest.documentFile());}
    Detail detail(UUID id){return coverage.detail(f.actor,id,0,0,false);}
    ResponseForm response(String kind,int sequence){var x=new ResponseForm();x.setKind(kind);x.setExpectedSequence(sequence);x.setConfirmed(true);x.setReason("Synthetic personal response");return x;}
    LinkForm linkForm(UUID posting){var x=new LinkForm();x.setPostingId(posting);x.setPayerEvidence("Synthetic verified identity of actual guarantor payer");x.setPayerVerified(true);return x;}
    UUID receipt(String amount){var x=f.post(p.s.loan,amount,"COVERAGE-"+UUID.randomUUID());x.setEffectiveDate(day);x.setNotes("Synthetic actual guarantor payer");return f.service.repay(f.actor,x).id();}
    long count(String table){return f.jdbc.queryForObject("select count(*) from "+table+" where sacco_id=?",Long.class,f.institution);}
    @Test void casesAndPromisesRetainServedEvidenceAndNeverPostMoney()throws Exception{
        long journals=count("gl_journal");var form=callForm();var pdf=GroupOrganizationPostgresTest.documentFile();UUID id=coverage.call(f.actor,p.s.cycle,form,pdf);assertThat(coverage.call(f.actor,p.s.cycle,form,pdf)).isEqualTo(id);var c=detail(id).header();assertThat(c.sourceKind()).isEqualTo("PERSONAL");assertThat(c.borrower()).isEqualTo("Synthetic organization <Company>");assertThat(c.requested()).isEqualByComparingTo("100.00");assertThat(coverage.notice(p.s.signer,id,true)).isEqualTo(pdf.getBytes());
        var promise=response("PROMISED",0);promise.setAmount(new BigDecimal("70.00"));promise.setPromiseOn(day.plusDays(5));UUID event=coverage.respond(p.s.signer,id,promise);assertThat(coverage.respond(p.s.signer,id,promise)).isEqualTo(event);assertThat(detail(id).header().state()).isEqualTo("PROMISED");assertThat(detail(id).header().received()).isZero();assertThat(detail(id).events().rows()).hasSize(1);assertThat(count("gl_journal")).isEqualTo(journals);assertThat(count("loan_ledgers")).isEqualTo(1);form.setAmount(new BigDecimal("90.00"));assertThatThrownBy(()->coverage.call(f.actor,p.s.cycle,form,pdf)).hasMessage("group.error.retry");
    }
    @Test void callsNeedCurrentApprovedPolicyAndNoticesForTheActualMissedDueDate(){
        var x=callForm();x.setPersonNoticeDate(day.minusDays(13));assertThatThrownBy(()->coverage.call(f.actor,p.s.cycle,x,GroupOrganizationPostgresTest.documentFile())).hasMessage("coverage.error.notice");x.setPersonNoticeDate(day.minusDays(21));assertThatThrownBy(()->coverage.call(f.actor,p.s.cycle,x,GroupOrganizationPostgresTest.documentFile())).hasMessage("coverage.error.notice");x.setPersonNoticeDate(day.minusDays(15));x.setNoticesVerified(false);assertThatThrownBy(()->coverage.call(f.actor,p.s.cycle,x,GroupOrganizationPostgresTest.documentFile())).hasMessage("coverage.error.notice");var next=policy();next.setExpectedPreviousId(policy);next.setEnabled(false);coverage.configure(f.actor,p.s.cycle,next);x.setNoticesVerified(true);assertThatThrownBy(()->coverage.call(f.actor,p.s.cycle,x,GroupOrganizationPostgresTest.documentFile())).hasMessage("coverage.error.policy");assertThat(count("lending_group_coverage_case")).isZero();
    }
    @Test void policiesFreezeAndOldCasePromisesUseTheirRecordedLimit(){
        UUID id=call();var next=policy();next.setExpectedPreviousId(policy);next.setPromiseDays(1);next.setEnabled(false);UUID newPolicy=coverage.configure(f.actor,p.s.cycle,next);assertThat(coverage.configure(f.actor,p.s.cycle,next)).isEqualTo(newPolicy);var promise=response("PROMISED",0);promise.setAmount(new BigDecimal("40.00"));promise.setPromiseOn(day.plusDays(5));coverage.respond(p.s.signer,id,promise);assertThat(detail(id).header().policy()).isEqualTo(policy);assertThatThrownBy(()->coverage.configure(f.actor,p.s.cycle,policy())).hasMessage("coverage.error.changed");
    }
    @Test void aReceiptIsLinkedOnceAndFinancialCorrectionRemovesCoverageCredit(){
        UUID id=call(),payment=receipt("50.00");long journals=count("gl_journal");var form=linkForm(payment);UUID link=coverage.link(f.actor,id,form);assertThat(coverage.link(f.actor,id,form)).isEqualTo(link);assertThat(detail(id).header().received()).isEqualByComparingTo("50.00");assertThat(detail(id).header().state()).isEqualTo("PART_RECEIVED");assertThatThrownBy(()->coverage.link(f.actor,id,linkForm(payment))).hasMessage("coverage.error.linked");assertThat(count("gl_journal")).isEqualTo(journals);f.service.reverse(f.operator("B1"),payment,UUID.randomUUID(),day,"Synthetic independent receipt correction");assertThat(detail(id).header().received()).isZero();assertThat(detail(id).payments().rows()).singleElement().satisfies(x->{assertThat(x.reversedBy()).isNotNull();assertThat(x.net()).isZero();assertThat(x.amount()).isEqualByComparingTo("50.00");});assertThat(p.own(obligation).offer().state()).isEqualTo("ACCEPTED");
    }
    @Test void independentAttributionCorrectionRetainsCashAndAllowsACorrectLink(){
        UUID id=call(),payment=receipt("50.00"),link=coverage.link(f.actor,id,linkForm(payment));long journals=count("gl_journal");assertThatThrownBy(()->coverage.correct(f.actor,id,link,UUID.randomUUID(),"Synthetic wrong attribution")).hasMessage("coverage.error.independent");var checker=f.operator("B1");var key=UUID.randomUUID();UUID correction=coverage.correct(checker,id,link,key,"Synthetic wrong attribution");assertThat(coverage.correct(checker,id,link,key,"Synthetic wrong attribution")).isEqualTo(correction);assertThat(detail(id).header().received()).isZero();assertThat(detail(id).payments().rows()).singleElement().satisfies(x->assertThat(x.correctedBy()).isEqualTo(correction));coverage.link(f.actor,id,linkForm(payment));assertThat(detail(id).header().received()).isEqualByComparingTo("50.00");assertThat(count("gl_journal")).isEqualTo(journals);
    }
    @Test void ownershipFreshClaimsScopeAndStaffImpersonationAreEnforced(){
        UUID id=call();assertThatThrownBy(()->coverage.detail(p.w.p.person(),id,0,0,true)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->coverage.respond(f.actor,id,response("ACKNOWLEDGED",0))).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->coverage.detail(f.operator("B2"),id,0,0,false)).isInstanceOf(AccessDeniedException.class);var current=EnumSet.allOf(UserClaim.class);current.remove(UserClaim.GROUP_COVERAGE_OWN_RESPOND);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.client),anyCollection(),anyBoolean())).thenReturn(current);assertThatThrownBy(()->coverage.respond(p.s.signer,id,response("ACKNOWLEDGED",0))).isInstanceOf(AccessDeniedException.class);current=EnumSet.allOf(UserClaim.class);current.remove(UserClaim.GROUP_COVERAGE_LINK);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(current);assertThatThrownBy(()->coverage.link(f.actor,id,linkForm(UUID.randomUUID()))).isInstanceOf(AccessDeniedException.class);
    }
    @Test void expirationAndClosureDoNotEraseAnAcceptedGuaranteeOrCreateCash(){
        UUID id=call();advance(day.plusDays(31));assertThat(detail(id).header().state()).isEqualTo("EXPIRED");assertThatThrownBy(()->coverage.respond(p.s.signer,id,response("ACKNOWLEDGED",0))).hasMessage("coverage.error.expired");coverage.close(f.actor,id,response("CLOSED",0));assertThat(detail(id).header().state()).isEqualTo("CLOSED");assertThat(p.own(obligation).offer().state()).isEqualTo("ACCEPTED");assertThat(count("accountant_loan_post")).isEqualTo(1);assertThatThrownBy(()->coverage.respond(p.s.signer,id,response("DISPUTED",1))).hasMessage("coverage.error.changed");
    }
    @Test void callsReserveTheShortfallUnderTheLoanLock()throws Exception{
        var source=coverage.sources(f.actor,p.s.cycle,0).rows().getFirst();var one=callForm();one.setAmount(source.arrears());var two=callForm();two.setAmount(source.arrears());try(var pool=Executors.newFixedThreadPool(2)){var gate=new CountDownLatch(1);var a=pool.submit(()->{gate.await();try{coverage.call(f.actor,p.s.cycle,one,GroupOrganizationPostgresTest.documentFile());return true;}catch(IllegalArgumentException no){return false;}});var b=pool.submit(()->{gate.await();try{coverage.call(f.actor,p.s.cycle,two,GroupOrganizationPostgresTest.documentFile());return true;}catch(IllegalArgumentException no){return false;}});gate.countDown();assertThat(a.get(60,TimeUnit.SECONDS)).isNotEqualTo(b.get(60,TimeUnit.SECONDS));}assertThat(count("lending_group_coverage_case")).isEqualTo(1);
    }
    @Test void immutableCasesAndEventsAndFailedAuditPreserveHistoryAtomically(){
        UUID id=call();coverage.respond(p.s.signer,id,response("DISPUTED",0));for(var sql:List.of("delete from lending_group_coverage_case where id=?","update lending_group_coverage_case set requested=1 where id=?","delete from lending_group_coverage_event where case_id=?"))assertThatThrownBy(()->f.jdbc.update(sql,id)).isInstanceOf(DataAccessException.class);doThrow(new IllegalStateException("Synthetic audit failure")).when(f.context.getBean(AuditService.class)).logEvent(eq("GROUP_LENDING"),eq(id),eq("COVERAGE_CLOSED"),any(),any(),any(),any(),any(),any(),any(),any());assertThatThrownBy(()->coverage.close(f.actor,id,response("CLOSED",1))).hasMessage("Synthetic audit failure");assertThat(detail(id).events().rows()).hasSize(1);
    }
    @Test void receiptDiscoveryExcludesLinkedReversedAndForeignSourcesAndExportRedactsVerification(){
        UUID id=call(),payment=receipt("50.00");
        assertThat(coverage.paymentChoices(f.actor,id,0).rows()).singleElement().satisfies(x->{assertThat(x.posting()).isEqualTo(payment);assertThat(x.contribution()).isNull();assertThat(x.amount()).isEqualByComparingTo("50.00");});
        assertThat(coverage.selectedPayment(f.actor,id,payment,null).posting()).isEqualTo(payment);
        assertThatThrownBy(()->coverage.paymentChoices(f.operator("B2"),id,0)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->coverage.paymentChoices(p.s.signer,id,0)).isInstanceOf(AccessDeniedException.class);
        UUID link=coverage.link(f.actor,id,linkForm(payment));assertThat(coverage.paymentChoices(f.actor,id,0).rows()).isEmpty();
        assertThat(coverage.document(f.actor,id,false).payments().rows().getFirst().evidence()).contains("verified identity");
        assertThat(coverage.document(p.s.signer,id,true).payments().rows().getFirst().evidence()).isEmpty();
        assertThatThrownBy(()->coverage.selectedPayment(f.actor,id,payment,null)).hasMessage("coverage.error.payment");
        coverage.correct(f.operator("B1"),id,link,UUID.randomUUID(),"Synthetic wrong attribution");
        assertThat(coverage.paymentChoices(f.actor,id,0).rows()).hasSize(1);
        f.service.reverse(f.operator("B1"),payment,UUID.randomUUID(),day,"Synthetic independent receipt correction");
        assertThat(coverage.paymentChoices(f.actor,id,0).rows()).isEmpty();
        assertThatThrownBy(()->coverage.paymentChoices(f.actor,id,-1)).hasMessage("coverage.error.query");
    }
    @Test void documentIncludesHistoryBeyondScreenPageWithoutChangingCaseState(){
        UUID id=call();for(int n=0;n<27;n++)coverage.respond(p.s.signer,id,response("ACKNOWLEDGED",n));
        assertThat(detail(id).events().rows()).hasSize(25);assertThat(detail(id).events().hasNext()).isTrue();
        var document=coverage.document(p.s.signer,id,true);assertThat(document.events().rows()).hasSize(27);assertThat(document.events().hasNext()).isFalse();assertThat(document.header().received()).isZero();
        assertThatThrownBy(()->coverage.document(p.w.p.person(),id,true)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void coverageClaimsAreNeverDefaultRoleAssignments(){for(var role:Position.values())for(boolean client:List.of(false,true))assertThat(UserClaim.defaultClaims(List.of(role),client)).noneMatch(x->x.name().startsWith("GROUP_COVERAGE"));}
}
