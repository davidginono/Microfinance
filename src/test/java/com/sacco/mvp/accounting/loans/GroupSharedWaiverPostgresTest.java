package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupSharedLoanDtos.WaiverAllocation;
import com.sacco.mvp.grouplending.repository.GroupSharedLoanRepository;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.service.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.AopTestUtils;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupSharedWaiverPostgresTest {
    final GroupSharedLoanPostgresTest s=new GroupSharedLoanPostgresTest();final LoanRecordingPostgresTest f=s.f;
    GroupSharedLoanService hook;LoanRepaymentLedgerService ledger;GroupMemberStatementService statements;
    final java.time.LocalDate date=LoanRecordingPostgresTest.TODAY.plusDays(1);
    @BeforeAll void start(){s.s.s.g.start(Config.class);s.s.agreements=f.context.getBean(GroupSharedAgreementService.class);s.shared=f.context.getBean(GroupSharedLoanService.class);hook=AopTestUtils.getUltimateTargetObject(s.shared);ledger=f.context.getBean(LoanRepaymentLedgerService.class);statements=f.context.getBean(GroupMemberStatementService.class);}
    @AfterAll void stop(){s.stop();}
    @BeforeEach void setup(){reset(hook);s.s.s.firstPaymentDate=date;s.setup();f.service.disburse(f.actor,s.funding());var clock=f.context.getBean(ApplicationClock.class);when(clock.today()).thenReturn(date);when(clock.now()).thenReturn(date.atTime(12,0).atOffset(java.time.ZoneOffset.ofHours(3)));}
    UUID waive(String amount){return ledger.waiveInterest(s.loan,f.actor,UUID.randomUUID(),date,new BigDecimal(amount),"Synthetic shared interest concession",null);}
    com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.PostForm datedPayment(String amount){var form=s.payment(amount);form.setEffectiveDate(date);return form;}
    BigDecimal waived(){return f.jdbc.queryForObject("select coalesce(sum(interest_waived),0) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,s.loan);}
    @Test void partialPaymentThenConcessionConservesCentsWithoutCashOrContractChanges()throws Exception{
        var clock=f.context.getBean(ApplicationClock.class);when(clock.today()).thenReturn(LoanRecordingPostgresTest.TODAY);when(clock.now()).thenReturn(LoanRecordingPostgresTest.TODAY.atTime(12,0).atOffset(java.time.ZoneOffset.ofHours(3)));f.service.repay(f.actor,s.payment("120.00"));when(clock.today()).thenReturn(date);when(clock.now()).thenReturn(date.atTime(12,0).atOffset(java.time.ZoneOffset.ofHours(3)));
        UUID id=waive("1.01");var rows=s.shared.balances(f.actor,s.loan);assertThat(rows).hasSize(2).allSatisfy(b->assertThat(b.known()).isTrue());assertThat(rows.getFirst().waivedInterest()).isEqualByComparingTo("0.12");assertThat(rows.getLast().waivedInterest()).isEqualByComparingTo("0.89");assertThat(waived()).isEqualByComparingTo("1.01");assertThat(rows.stream().map(b->b.remainingInterest()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("16.59");assertThat(s.count("gl_journal")).isEqualTo(2);assertThat(f.service.detail(f.actor,s.loan).contractualInterest()).isEqualByComparingTo("20.00");
        var own=statements.client(s.s.client,s.s.cycle,null,null,0,0,true);assertThat(own.source().known()).isTrue();assertThat(own.source().maximumLiability()).isEqualByComparingTo("110.69");assertThat(own.credits()).hasSize(2).anySatisfy(c->{assertThat(c.kind()).isEqualTo("INTEREST_WAIVER");assertThat(c.waivedInterest()).isEqualByComparingTo("0.12");assertThat(c.amount()).isZero();assertThat(c.excludedInterest()).isZero();});
        Files.writeString(Path.of("target/shared-waiver-fixture.json"),"{\"institution\":\""+f.institution+"\",\"client\":\""+f.client+"\",\"actor\":\""+f.actor.getMemberId()+"\",\"cycle\":\""+s.s.cycle+"\",\"loan\":\""+s.loan+"\",\"waiver\":\""+id+"\"}");
    }
    @Test void linkedIndependentCorrectionRestoresTheExactAmountsAndRetryDoesNotDuplicate()throws Exception{
        var key=UUID.randomUUID();UUID id=ledger.waiveInterest(s.loan,f.actor,key,date,new BigDecimal("1.01"),"Synthetic original waiver",null);assertThat(ledger.waiveInterest(s.loan,f.actor,key,date,new BigDecimal("1.01"),"Synthetic original waiver",null)).isEqualTo(id);
        assertThatThrownBy(()->ledger.waiveInterest(s.loan,f.actor,UUID.randomUUID(),date,new BigDecimal("1.01"),"Synthetic self reversal",id)).hasMessage("prepayment.error.checker");
        var checker=f.operator("B1");var reversalKey=UUID.randomUUID();var reversed=ledger.waiveInterest(s.loan,checker,reversalKey,date,new BigDecimal("1.01"),"Synthetic independent restoration",id);assertThat(ledger.waiveInterest(s.loan,checker,reversalKey,date,new BigDecimal("1.01"),"Synthetic independent restoration",id)).isEqualTo(reversed);assertThat(waived()).isZero();assertThat(s.count("lending_group_shared_waiver")).isEqualTo(4);assertThat(s.count("gl_journal")).isEqualTo(1);assertThat(s.shared.balances(f.actor,s.loan)).allSatisfy(b->{assertThat(b.waivedInterest()).isZero();assertThat(b.remainingInterest()).isEqualByComparingTo(b.interest());});
        var own=statements.client(s.s.client,s.s.cycle,null,null,0,0,true);assertThat(own.credits()).hasSize(2).allSatisfy(c->{assertThat(c.amount()).isZero();assertThat(c.waivedInterest()).isEqualByComparingTo("0.12");});assertThat(own.credits()).anySatisfy(c->assertThat(c.reverses()).startsWith("W-")).anySatisfy(c->assertThat(c.reversedBy()).startsWith("W-"));var pdf=new GroupMemberStatementPdf(new LoanRecordingPdfService(new ReportExportLimiter(1)),f.context.getBean(org.springframework.context.MessageSource.class));for(var lang:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw"))){byte[] bytes=pdf.render(own,lang);try(var document=Loader.loadPDF(bytes)){assertThat(new PDFTextStripper().getText(document)).contains("0.12","W-").doesNotContain("0.89","Second synthetic client");}Files.write(Path.of("target/shared-waiver-"+lang.getLanguage()+".pdf"),bytes);}
    }
    @Test void laterCashUsesReducedInterestAndMustBeReversedBeforeRestoringTheWaiver(){
        var id=waive("1.01");var payment=f.service.repay(f.actor,datedPayment("120.00"));assertThat(payment.interest()).isEqualByComparingTo("18.99");assertThat(s.shared.balances(f.actor,s.loan).stream().map(b->b.paidInterest()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("18.99");var checker=f.operator("B1");assertThatThrownBy(()->ledger.waiveInterest(s.loan,checker,UUID.randomUUID(),date,new BigDecimal("1.01"),"Synthetic premature restoration",id)).hasMessage("prepayment.error.latest");f.service.reverse(checker,payment.id(),UUID.randomUUID(),date,"Synthetic cash correction before concession restoration");ledger.waiveInterest(s.loan,checker,UUID.randomUUID(),date,new BigDecimal("1.01"),"Synthetic permitted restoration",id);assertThat(waived()).isZero();assertThat(s.shared.balances(f.actor,s.loan)).allSatisfy(b->assertThat(b.remainingPrincipal()).isEqualByComparingTo(b.principal()));
    }
    @Test void revokedGroupPermissionAlsoRejectsIdempotentRetriesAndForeignScope(){
        var key=UUID.randomUUID();var amount=new BigDecimal("1.01");var source=ledger.waiveInterest(s.loan,f.actor,key,date,amount,"Synthetic current permission test",null);var claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.GROUP_LENDING_POST);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(claims);assertThatThrownBy(()->ledger.waiveInterest(s.loan,f.actor,key,date,amount,"Synthetic current permission test",null)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->ledger.waiveInterest(s.loan,f.operator("B2"),UUID.randomUUID(),date,amount,"Synthetic foreign branch waiver",null)).isInstanceOf(AccessDeniedException.class);var checker=f.operator("B1");var reverseClaims=EnumSet.allOf(UserClaim.class);reverseClaims.remove(UserClaim.GROUP_LENDING_REVERSE);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(checker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(reverseClaims);assertThatThrownBy(()->ledger.waiveInterest(s.loan,checker,UUID.randomUUID(),date,amount,"Synthetic revoked restoration permission",source)).isInstanceOf(AccessDeniedException.class);var foreign=new com.sacco.mvp.security.AppUserPrincipal(Member.builder().id(f.actor.getMemberId()).saccoId("FOREIGN").stationId("B1").position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),true);assertThatThrownBy(()->ledger.waiveInterest(s.loan,foreign,UUID.randomUUID(),date,amount,"Synthetic foreign institution concession",null)).isInstanceOf(AccessDeniedException.class);assertThat(waived()).isEqualByComparingTo(amount);
    }
    @Test void lateAuditFailureRollsBackParentAndEveryMemberBenefit(){
        doThrow(new IllegalStateException("Synthetic waiver audit failure")).when(f.context.getBean(AuditService.class)).logEvent(eq("LOAN_REPAYMENT"),any(),eq("INTEREST_WAIVED"),any(),any(),any(),any(),any(),any(),any(),any());assertThatThrownBy(()->waive("1.01")).hasMessage("Synthetic waiver audit failure");assertThat(waived()).isZero();assertThat(s.count("loan_interest_waiver")).isZero();assertThat(s.count("lending_group_shared_waiver")).isZero();assertThat(s.count("gl_journal")).isEqualTo(1);
    }
    @Test void databaseRejectsAnOwningServiceThatOmitsMemberEvidence(){
        doNothing().when(hook).waiver(any(),any(),any(),any(),any());assertThatThrownBy(()->waive("1.01")).isInstanceOf(DataAccessException.class).hasStackTraceContaining("Shared interest waiver");assertThat(waived()).isZero();assertThat(s.count("loan_interest_waiver")).isZero();assertThat(s.count("lending_group_shared_waiver")).isZero();
    }
    @Test void conservingTheParentTotalCannotHideIncorrectPersonalCents(){
        doAnswer(inv->{var source=inv.getArgument(2,UUID.class);f.context.getBean(GroupSharedLoanRepository.class).waivers(f.actor,s.loan,List.of(new WaiverAllocation(source,f.client,new BigDecimal("0.13"),null),new WaiverAllocation(source,s.s.s.g.second,new BigDecimal("0.88"),null)));return null;}).when(hook).waiver(any(),any(),any(),any(),any());assertThatThrownBy(()->waive("1.01")).isInstanceOf(DataAccessException.class).hasStackTraceContaining("proportional remaining-interest");assertThat(waived()).isZero();assertThat(s.count("loan_interest_waiver")).isZero();
    }
    @Test void evidenceCannotBeChangedDeletedOrAddedToAnOldSource(){
        UUID id=waive("1.01");assertThatThrownBy(()->f.jdbc.update("update lending_group_shared_waiver set amount=0 where waiver_id=?",id)).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");assertThatThrownBy(()->f.jdbc.update("delete from lending_group_shared_waiver where waiver_id=?",id)).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");assertThatThrownBy(()->f.jdbc.update("insert into lending_group_shared_waiver select * from lending_group_shared_waiver where waiver_id=? limit 1",id)).isInstanceOf(DataAccessException.class).hasMessageContaining("newly recorded scoped source");assertThat(s.count("lending_group_shared_waiver")).isEqualTo(2);
    }
    @Test void concurrentRetriesHaveOneConcessionAndOneConservedAttribution()throws Exception{
        var key=UUID.randomUUID();try(var workers=Executors.newFixedThreadPool(2)){var gate=new CountDownLatch(1);var a=workers.submit(()->{gate.await();return ledger.waiveInterest(s.loan,f.actor,key,date,new BigDecimal("0.01"),"Synthetic concurrent one-cent concession",null);});var b=workers.submit(()->{gate.await();return ledger.waiveInterest(s.loan,f.actor,key,date,new BigDecimal("0.01"),"Synthetic concurrent one-cent concession",null);});gate.countDown();assertThat(a.get(60,TimeUnit.SECONDS)).isEqualTo(b.get(60,TimeUnit.SECONDS));}assertThat(waived()).isEqualByComparingTo("0.01");assertThat(s.count("lending_group_shared_waiver")).isEqualTo(2);assertThat(s.shared.balances(f.actor,s.loan).getFirst().waivedInterest()).isZero();assertThat(s.shared.balances(f.actor,s.loan).getLast().waivedInterest()).isEqualByComparingTo("0.01");assertThat(s.count("gl_journal")).isEqualTo(1);
    }
    @Test void concurrentCashAndConcessionUseTheSameOwningLoanLockOrder()throws Exception{
        try(var workers=Executors.newFixedThreadPool(2)){var gate=new CountDownLatch(1);var a=workers.submit(()->{gate.await();return waive("1.01");});var b=workers.submit(()->{gate.await();return f.service.repay(f.actor,datedPayment("5.00")).id();});gate.countDown();assertThat(a.get(60,TimeUnit.SECONDS)).isNotNull();assertThat(b.get(60,TimeUnit.SECONDS)).isNotNull();}assertThat(waived()).isEqualByComparingTo("1.01");assertThat(s.shared.balances(f.actor,s.loan).stream().map(r->r.remainingInterest()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("13.99");assertThat(s.count("gl_journal")).isEqualTo(2);
    }
    @Test void earlySettlementKeepsWaiversCashAndFutureExclusionsSeparateThroughCorrections(){
        s.s.s.firstPaymentDate=LoanRecordingPostgresTest.TODAY.plusMonths(1);s.setup();f.service.disburse(f.actor,s.funding());f.service.repay(f.actor,s.payment("120.00"));var at=LoanRecordingPostgresTest.TODAY.plusDays(15);var clock=f.context.getBean(ApplicationClock.class);when(clock.today()).thenReturn(at);when(clock.now()).thenReturn(at.atTime(12,0).atOffset(java.time.ZoneOffset.ofHours(3)));var original=ledger.waiveInterest(s.loan,f.actor,UUID.randomUUID(),at,new BigDecimal("1.01"),"Synthetic earned-interest concession before early settlement",null);var quote=ledger.settlementQuote(s.loan,f.actor,at);var form=s.payment(quote.total().toPlainString());form.setEffectiveDate(at);var payment=f.service.repay(f.actor,form);assertThat(quote.futureInterest()).isPositive();assertThat(payment.interest()).isEqualByComparingTo(quote.interest());assertThat(s.shared.balances(f.actor,s.loan)).allSatisfy(b->{assertThat(b.remainingPrincipal()).isZero();assertThat(b.remainingInterest()).isZero();assertThat(b.known()).isTrue();});assertThat(waived()).isEqualByComparingTo("1.01");assertThat(f.service.detail(f.actor,s.loan).contractualInterest()).isEqualByComparingTo("20.00");var checker=f.operator("B1");f.service.reverse(checker,payment.id(),UUID.randomUUID(),at,"Synthetic early settlement correction retaining concession");ledger.waiveInterest(s.loan,checker,UUID.randomUUID(),at,new BigDecimal("1.01"),"Synthetic final concession restoration",original);assertThat(waived()).isZero();assertThat(s.shared.balances(f.actor,s.loan).stream().map(b->b.remainingInterest()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("17.60");
    }
    @Configuration(proxyBeanMethods=false) @Import(GroupMemberStatementPostgresTest.Config.class) static class Config {
        @Bean GroupSharedLoanRepository waiverRepository(org.springframework.jdbc.core.JdbcTemplate jdbc,tools.jackson.databind.ObjectMapper json){return new GroupSharedLoanRepository(jdbc,json);}
        @Bean @Primary GroupSharedLoanService guardedShared(GroupSharedLoanRepository r,GeneralLedgerService a,MemberDirectoryService m,ApplicationClock c,AuditService audit){return spy(new GroupSharedLoanService(r,a,m,c,audit));}
    }
}
