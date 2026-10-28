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
        var existing=f.context.getBeansOfType(GroupCoverageService.class);if(!existing.isEmpty())return existing.values().iterator().next();
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
    @Test void unavailablePaymentSourcesRemainRecoverableDomainErrorsWithoutNewCashOrLinks(){
        UUID id=call(),posting=receipt("40.00"),missing=UUID.randomUUID();long journals=count("gl_journal"),payments=count("accountant_loan_post");
        assertThat(coverage.selectedPayment(f.actor,id,posting,null).amount()).isEqualByComparingTo("40.00");
        assertThatThrownBy(()->coverage.selectedPayment(f.actor,id,missing,null)).isExactlyInstanceOf(IllegalArgumentException.class).hasMessage("coverage.error.payment");
        assertThatThrownBy(()->coverage.link(f.actor,id,linkForm(missing))).isExactlyInstanceOf(IllegalArgumentException.class).hasMessage("coverage.error.payment");
        assertThatThrownBy(()->coverage.selectedPayment(f.actor,id,posting,UUID.randomUUID())).isExactlyInstanceOf(IllegalArgumentException.class).hasMessage("coverage.error.payment");
        f.service.reverse(f.operator("B1"),posting,UUID.randomUUID(),day,"Synthetic independent receipt reversal");long reversedJournals=count("gl_journal"),reversedPayments=count("accountant_loan_post");
        assertThatThrownBy(()->coverage.selectedPayment(f.actor,id,posting,null)).isExactlyInstanceOf(IllegalArgumentException.class).hasMessage("coverage.error.payment");
        assertThatThrownBy(()->coverage.link(f.actor,id,linkForm(posting))).isExactlyInstanceOf(IllegalArgumentException.class).hasMessage("coverage.error.payment");
        assertThat(detail(id).payments().rows()).isEmpty();assertThat(count("gl_journal")).isEqualTo(reversedJournals);assertThat(count("accountant_loan_post")).isEqualTo(reversedPayments);assertThat(reversedJournals).isGreaterThan(journals);assertThat(reversedPayments).isGreaterThan(payments);
    }
    @Test void legacyCaseAndObligationRegistersSupportTheirDisplayedColumnOrders(){
        UUID id=call();try {
            for(String table:List.of("coverage-cases","coverage-obligations"))for(String key:table.equals("coverage-cases")?List.of("loan","person","kind","date","amount","received","state"):List.of("loan","person","kind","cap","arrears"))for(String direction:List.of("asc","desc")){
                var request=new org.springframework.mock.web.MockHttpServletRequest("GET","/");request.setParameter("tableSort["+table+"]",key);request.setParameter("tableDirection["+table+"]",direction);org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
                if(table.equals("coverage-cases")){assertThat(coverage.list(f.actor,p.s.cycle,0,false).rows()).extracting(Case::id).containsExactly(id);assertThat(coverage.list(f.actor,p.s.cycle,1,false).rows()).isEmpty();}
                else {assertThat(coverage.sources(f.actor,p.s.cycle,0).rows()).isNotEmpty();assertThat(coverage.sources(f.actor,p.s.cycle,1).rows()).isEmpty();}
            }
            assertThatThrownBy(()->coverage.sources(f.operator("B2"),p.s.cycle,0)).isInstanceOf(AccessDeniedException.class);
        } finally {org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
    }
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
        UUID id=call(),payment=receipt("50.00"),link=coverage.link(f.actor,id,linkForm(payment));long journals=count("gl_journal");assertThatThrownBy(()->coverage.correct(f.actor,id,link,UUID.randomUUID(),"Synthetic wrong attribution")).isExactlyInstanceOf(IllegalArgumentException.class).hasMessage("coverage.error.independent");var checker=f.operator("B1");var key=UUID.randomUUID();UUID correction=coverage.correct(checker,id,link,key,"Synthetic wrong attribution");assertThat(coverage.correct(checker,id,link,key,"Synthetic wrong attribution")).isEqualTo(correction);assertThat(detail(id).header().received()).isZero();assertThat(detail(id).payments().rows()).singleElement().satisfies(x->assertThat(x.correctedBy()).isEqualTo(correction));coverage.link(f.actor,id,linkForm(payment));assertThat(detail(id).header().received()).isEqualByComparingTo("50.00");assertThat(count("gl_journal")).isEqualTo(journals);
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
    @Test void cycleStatementTraceDoesNotCountPromisesOrReceiptsTwiceAndRespectsFreshCoverageClaims()throws Exception{
        UUID id=call();var promise=response("PROMISED",0);promise.setAmount(new BigDecimal("70.00"));promise.setPromiseOn(day.plusDays(1));coverage.respond(p.s.signer,id,promise);
        var statements=GroupCycleStatementPostgresTest.register(f);var before=statements.statement(f.actor,p.s.cycle,null,null,0,0,0,0,false);
        assertThat(before.coverage().rows()).singleElement().satisfies(x->{assertThat(x.id()).isEqualTo(id);assertThat(x.state()).isEqualTo("PROMISED");assertThat(x.received()).isZero();});
        assertThat(coverage.trace(p.s.signer,p.s.cycle,null,null,day,0,true,false).rows()).hasSize(1);
        UUID payment=receipt("50.00");coverage.link(f.actor,id,linkForm(payment));var paid=statements.statement(f.actor,p.s.cycle,null,null,0,0,0,0,false);
        assertThat(paid.coverage().rows().getFirst().received()).isEqualByComparingTo("50.00");
        assertThat(paid.totals().outstanding()).isEqualByComparingTo(before.totals().outstanding().subtract(new BigDecimal("50.00")));
        f.service.reverse(f.operator("B1"),payment,UUID.randomUUID(),day,"Synthetic statement correction");
        var reversed=statements.statement(f.actor,p.s.cycle,null,null,0,0,0,0,true);assertThat(reversed.totals()).isEqualTo(before.totals());assertThat(reversed.coverage().rows().getFirst().received()).isZero();
        var messages=new org.springframework.context.support.ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");var pdf=new GroupCycleStatementPdf(new com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService(new ReportExportLimiter(1)),messages);
        for(var locale:List.of(Locale.ENGLISH,Locale.of("sw")))try(var doc=org.apache.pdfbox.Loader.loadPDF(pdf.render(reversed,locale))){var text=new org.apache.pdfbox.text.PDFTextStripper().getText(doc);assertThat(text.toLowerCase(locale).replaceAll("\\s+", "")).contains(messages.getMessage("coverage.title",null,locale).toLowerCase(locale).replaceAll("\\s+", ""));assertThat(text).doesNotContain("Synthetic verified identity of actual guarantor payer");}
        var claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.GROUP_COVERAGE_VIEW);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(claims);
        assertThat(statements.statement(f.actor,p.s.cycle,null,null,0,0,0,0,false).coverage()).isNull();
        assertThat(coverage.trace(p.w.p.person(),p.s.cycle,null,null,day,0,true,false).rows()).isEmpty();
    }
    @Test void workspacePickersStayScopedAndHideLinkedOrReversedPaymentSources(){
        assertThat(coverage.context(f.actor,p.s.cycle).group()).isEqualTo("Synthetic organization <Company>");var pick="PERSONAL:"+obligation;var prepared=coverage.prepare(f.actor,p.s.cycle,pick);assertThat(prepared.maximum()).isPositive();assertThat(coverage.sourceChoices(f.actor,p.s.cycle,"Synthetic",0).rows()).hasSize(1);assertThat(coverage.sourceChoices(f.actor,p.s.cycle,"No such person",0).rows()).isEmpty();assertThatThrownBy(()->coverage.sourceChoices(f.operator("B2"),p.s.cycle,"",0)).isInstanceOf(AccessDeniedException.class);UUID id=call();assertThat(coverage.prepare(f.actor,p.s.cycle,pick).maximum()).isEqualByComparingTo(prepared.maximum().subtract(new BigDecimal("100.00")));assertThat(coverage.paymentChoices(f.actor,id,"",0).rows()).isEmpty();UUID posting=receipt("50.00");assertThat(coverage.paymentChoices(f.actor,id,"COVERAGE-",0).rows()).singleElement().satisfies(x->{assertThat(x.posting()).isEqualTo(posting);assertThat(x.amount()).isEqualByComparingTo("50.00");assertThat(x.amount()).isEqualByComparingTo(x.principal().add(x.interest()).add(x.charges()));});UUID link=coverage.link(f.actor,id,linkForm(posting));assertThat(coverage.paymentChoices(f.actor,id,"",0).rows()).isEmpty();coverage.correct(f.operator("B1"),id,link,UUID.randomUUID(),"Synthetic wrong attribution");assertThat(coverage.paymentChoices(f.actor,id,"",0).rows()).hasSize(1);f.service.reverse(f.operator("B1"),posting,UUID.randomUUID(),day,"Synthetic receipt correction");assertThat(coverage.paymentChoices(f.actor,id,"",0).rows()).isEmpty();
    }
    @Test void ownWorkspaceKeepsFinancialTraceWithoutPrivateVerificationDetails(){
        UUID id=call(),posting=receipt("50.00"),link=coverage.link(f.actor,id,linkForm(posting));var checker=f.operator("B1");coverage.correct(checker,id,link,UUID.randomUUID(),"Synthetic staff-only correction evidence");var staff=detail(id);assertThat(staff.notice().borrowerEvidence()).isNotBlank();assertThat(staff.payments().rows()).singleElement().satisfies(x->{assertThat(x.recordedBy()).isEqualTo(f.actor.getMemberId());assertThat(x.correctionReason()).isEqualTo("Synthetic staff-only correction evidence");assertThat(x.correctedAt()).isNotNull();});var own=coverage.detail(p.s.signer,id,0,0,true);assertThat(own.notice().borrowerDate()).isEqualTo(callForm().getBorrowerNoticeDate());assertThat(own.notice().borrowerEvidence()).isEmpty();assertThat(own.notice().personEvidence()).isEmpty();assertThat(own.payments().rows()).singleElement().satisfies(x->{assertThat(x.evidence()).isEmpty();assertThat(x.recordedBy()).isNull();assertThat(x.correctionReason()).isEmpty();assertThat(x.correctedAt()).isNotNull();assertThat(x.correctedBy()).isNotNull();assertThat(x.net()).isZero();});
    }
    @Test void tableOrderingIsWhitelistedAndAppliedBeforeEachBoundedPage(){
        var pdf=GroupOrganizationPostgresTest.documentFile();UUID id=null;for(int n=1;n<=27;n++){var request=callForm();request.setAmount(BigDecimal.valueOf(n).setScale(2));UUID created=coverage.call(f.actor,p.s.cycle,request,pdf);if(id==null)id=created;}
        assertThat(coverage.list(f.actor,p.s.cycle,"amount_asc",0,false).rows()).hasSize(25).first().extracting(com.sacco.mvp.grouplending.dto.GroupCoverageDtos.Case::requested).isEqualTo(new BigDecimal("1.00"));assertThat(coverage.list(f.actor,p.s.cycle,"amount_desc",0,false).rows().getFirst().requested()).isEqualByComparingTo("27.00");assertThat(coverage.list(p.s.signer,null,"amount_asc",1,true).rows()).extracting(com.sacco.mvp.grouplending.dto.GroupCoverageDtos.Case::requested).containsExactly(new BigDecimal("26.00"),new BigDecimal("27.00"));
        for(int n=0;n<27;n++){var response=response("ACKNOWLEDGED",n);response.setReason("Synthetic acknowledgement "+n);coverage.respond(p.s.signer,id,response);UUID posting=receipt(Integer.toString(n+1));coverage.link(f.actor,id,linkForm(posting));}
        assertThat(coverage.detail(f.actor,id,0,0,"reason_asc","amount_desc",false).payments().rows()).hasSize(25).first().extracting(com.sacco.mvp.grouplending.dto.GroupCoverageDtos.Payment::amount).isEqualTo(new BigDecimal("27.00"));var next=coverage.detail(f.actor,id,1,1,"newest","amount_asc",false);assertThat(next.events().rows()).hasSize(2);assertThat(next.payments().rows()).extracting(com.sacco.mvp.grouplending.dto.GroupCoverageDtos.Payment::amount).containsExactly(new BigDecimal("26.00"),new BigDecimal("27.00"));
        for(var column:GroupCoverageRepository.CASE_SORT.keySet())for(var direction:List.of("asc","desc"))assertThat(coverage.list(f.actor,p.s.cycle,column+"_"+direction,0,false).rows()).hasSize(25);for(var column:GroupCoverageRepository.EVENT_SORT.keySet())for(var direction:List.of("asc","desc"))assertThat(coverage.detail(f.actor,id,0,0,column+"_"+direction,"newest",false).events().rows()).hasSize(25);for(var column:GroupCoverageRepository.PAYMENT_SORT.keySet())for(var direction:List.of("asc","desc"))assertThat(coverage.detail(f.actor,id,0,0,"newest",column+"_"+direction,false).payments().rows()).hasSize(25);
        UUID caseId=id;assertThatThrownBy(()->coverage.list(f.actor,p.s.cycle,"amount;delete",0,false)).hasMessage("coverage.error.query");assertThatThrownBy(()->coverage.detail(f.actor,caseId,0,0,"unknown_asc","newest",false)).hasMessage("coverage.error.query");assertThatThrownBy(()->coverage.detail(f.actor,caseId,0,0,"newest","payer;drop",false)).hasMessage("coverage.error.query");
    }
    @Test void coverageClaimsAreNeverDefaultRoleAssignments(){for(var role:Position.values())for(boolean client:List.of(false,true))assertThat(UserClaim.defaultClaims(List.of(role),client)).noneMatch(x->x.name().startsWith("GROUP_COVERAGE"));}
}
