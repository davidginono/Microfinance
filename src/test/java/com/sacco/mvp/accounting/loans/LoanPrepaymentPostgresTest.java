package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_(recording|flexible_repayment_[a-f0-9]{8})_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoanPrepaymentPostgresTest {
    LoanRecordingPostgresTest f;
    static final LocalDate PAYMENT=LocalDate.of(2026,9,16);
    static BigDecimal m(String s){return new BigDecimal(s);}
    @BeforeAll void start(){f=new LoanRecordingPostgresTest();f.start();}
    @AfterAll void stop(){if(f!=null && f.context!=null)f.context.close();}
    @BeforeEach void setup(){f.fixture();}
    UUID loan(InterestMethod method){
        f.jdbc.update("update loan_product_settings set interest_method=? where id=?",method.name(),f.product);
        var form=f.form();form.setMonths(2);var loan=f.service.save(f.actor,form);f.service.disburse(f.actor,f.post(loan,"1000.00","D-PREPAY-"+UUID.randomUUID()));return loan;
    }
    com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Posting payment(UUID loan,String amount,LocalDate date){
        var post=f.post(loan,amount,"PREPAY-"+UUID.randomUUID());post.setEffectiveDate(date);return f.service.repay(f.actor,post);
    }
    @Test void partialFlatAdvanceRebatesOnlyUnearnedInterestAndCurrentProjectionsUseTheEffectiveDate(){
        var loan=loan(InterestMethod.FLAT_RATE);var post=payment(loan,"410.00",PAYMENT);
        assertThat(post.interest()).isEqualByComparingTo("10.00");assertThat(post.principal()).isEqualByComparingTo("400.00");
        assertThat(f.jdbc.queryForObject("select sum(interest_excluded) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,loan)).isEqualByComparingTo("12.00");
        assertThat(f.ledger.paymentBalance(loan,f.actor,PAYMENT).dueInterest()).isZero();
        assertThat(f.service.detail(f.actor,loan).loan().dueInterest()).isEqualByComparingTo("8.71");
        var quote=f.ledger.settlementQuote(loan,f.actor,LoanRecordingPostgresTest.TODAY);
        assertThat(quote.total()).isEqualByComparingTo("608.71");assertThat(quote.futureInterest()).isEqualByComparingTo("9.29");
        assertThat(f.jdbc.queryForObject("select sum(interest) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,loan)).isEqualByComparingTo("40.00");
    }
    @Test void reducingBalanceAdvanceRecomputesFutureInterestAndAnIndependentReversalRestoresIt(){
        var loan=loan(InterestMethod.REDUCING_BALANCE);var post=payment(loan,"410.00",PAYMENT);
        var original=f.jdbc.queryForObject("select sum(interest) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,loan);
        assertThat(original).isGreaterThan(m("20.00"));
        assertThat(f.jdbc.queryForObject("select interest_excluded from loan_ledger_installments where loan_application_id=? and installment_number=2",BigDecimal.class,loan)).isEqualByComparingTo("8.00");
        f.service.reverse(f.operator("B1"),post.id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Independent servicing correction");
        assertThat(f.jdbc.queryForObject("select sum(interest_excluded) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,loan)).isZero();
        assertThat(f.jdbc.queryForObject("select sum(interest) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,loan)).isEqualByComparingTo(original);
    }
    @Test void fullSettlementBetweenDueDatesCollectsEarnedInterestAndNeverCollectsTheRebate(){
        var loan=loan(InterestMethod.FLAT_RATE);var post=payment(loan,"1010.00",PAYMENT);
        assertThat(post.principal()).isEqualByComparingTo("1000.00");assertThat(post.interest()).isEqualByComparingTo("10.00");
        assertThat(f.service.detail(f.actor,loan).loan().status()).isEqualTo("PAID");
        assertThat(f.jdbc.queryForObject("select future_interest_excluded from loan_repayment_transactions where loan_application_id=?",BigDecimal.class,loan)).isEqualByComparingTo("30.00");
        assertThat(f.jdbc.queryForObject("select sum(credit) from gl_journal_line where journal_id=? and account_id=?",BigDecimal.class,post.journalId(),f.interest)).isEqualByComparingTo("10.00");
    }
    @Test void waiverIsIndependentOfCashIdempotentScopedAndReversibleByAnotherOfficer(){
        var loan=loan(InterestMethod.FLAT_RATE);var key=UUID.randomUUID();var date=LoanRecordingPostgresTest.TODAY;
        var id=f.ledger.waiveInterest(loan,f.actor,key,date,m("6.00"),"Approved borrower concession",null);
        assertThat(f.ledger.waiveInterest(loan,f.actor,key,date,m("6.00"),"Approved borrower concession",null)).isEqualTo(id);
        assertThat(f.service.detail(f.actor,loan).loan().dueInterest()).isEqualByComparingTo("18.52");
        assertThat(f.jdbc.queryForObject("select sum(interest_paid) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,loan)).isZero();
        var reports=new com.sacco.mvp.accounting.reports.LoanPortfolioReportRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(f.jdbc));
        var scope=new com.sacco.mvp.accounting.reports.LoanPortfolioReportService.Scope(f.institution,"B1",false);
        var parameters=new com.sacco.mvp.accounting.reports.LoanPortfolioReportService.Parameters(LocalDate.of(2026,9,1),date,java.time.OffsetDateTime.now(),0);
        assertThat(reports.rows(scope,parameters)).singleElement().satisfies(row->assertThat(row.overdueInterest()).isEqualByComparingTo("14.00"));
        assertThatThrownBy(()->f.ledger.waiveInterest(loan,f.actor,UUID.randomUUID(),date,m("6.00"),"Self correction",id)).hasMessage("prepayment.error.checker");
        assertThatThrownBy(()->f.ledger.waiveInterest(loan,f.operator("B2"),UUID.randomUUID(),date,m("1.00"),"Wrong branch",null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        f.ledger.waiveInterest(loan,f.operator("B1"),UUID.randomUUID(),date,m("6.00"),"Independent correction",id);
        assertThat(f.service.detail(f.actor,loan).loan().dueInterest()).isEqualByComparingTo("24.52");
        assertThatThrownBy(()->f.jdbc.update("update loan_interest_waiver set amount=1 where id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->f.jdbc.update("update loan_ledger_installments set interest_waived=1 where loan_application_id=?",loan)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void concurrentWaiverRetriesPostOneConcessionAndViewPermissionCannotWaive()throws Exception{
        var loan=loan(InterestMethod.FLAT_RATE);var key=UUID.randomUUID();
        var viewOnly=new com.sacco.mvp.security.AppUserPrincipal(Member.builder().id(f.actor.getMemberId()).saccoId(f.institution).stationId("B1").position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),EnumSet.of(UserClaim.LOAN_RECORDING_VIEW),true);
        assertThatThrownBy(()->f.ledger.waiveInterest(loan,viewOnly,key,LoanRecordingPostgresTest.TODAY,m("5.00"),"No waiver claim",null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var start=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.Callable<UUID> command=()->{start.await();return f.ledger.waiveInterest(loan,f.actor,key,LoanRecordingPostgresTest.TODAY,m("5.00"),"Approved concurrent concession",null);};
            var a=pool.submit(command);var b=pool.submit(command);start.countDown();assertThat(a.get()).isEqualTo(b.get());
        }
        assertThat(f.jdbc.queryForObject("select count(*) from loan_interest_waiver where loan_id=?",Integer.class,loan)).isOne();
        assertThat(f.jdbc.queryForObject("select sum(interest_waived) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,loan)).isEqualByComparingTo("5.00");
    }
    @Test void repeatedBatchPaymentsOnDifferentDatesMatchPostingAfterAnEarlierRebate(){
        f.configurePolicy(false);var loan=loan(InterestMethod.FLAT_RATE);var batch=f.batches().create(f.actor,"Dated partial prepayments");
        var first=f.receiptForm(loan);first.setEffectiveDate(PAYMENT);first.getRows().getFirst().setAmount(m("410.00"));f.batchItem(batch,first);
        var second=f.receiptForm(loan);second.setEffectiveDate(PAYMENT.plusDays(4));second.getRows().getFirst().setAmount(m("100.00"));f.batchItem(batch,second);
        f.processBatch(batch,false);assertThat(f.batches().items(f.actor,batch,0,"").rows()).allMatch(i->i.state().equals("READY"));
        var plan=f.batches().items(f.actor,batch,0,"").rows().getLast().preview().lines().getFirst();assertThat(plan.interest()).isEqualByComparingTo("1.60");
        f.processBatch(batch,true);assertThat(f.batches().get(f.actor,batch).posted()).isEqualTo(2);
        assertThat(f.service.detail(f.actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("501.60");
    }
    @Test void acceptedTermsStayFrozenWhenTheProductChangesBeforeDisbursement(){
        var draft=f.service.save(f.actor,f.form());
        f.jdbc.update("update loan_product_settings set prepayment_schedule='NEXT_INSTALMENTS',partial_prepayment_interest='RETAIN_UNTIL_SETTLEMENT',settlement_day_basis='THIRTY_360' where id=?",f.product);
        f.service.disburse(f.actor,f.post(draft,"1000.00","D-FROZEN-SERVICING"));
        var quote=f.ledger.settlementQuote(draft,f.actor,LoanRecordingPostgresTest.TODAY);
        assertThat(quote.terms()).isEqualTo(LoanPrepaymentTerms.defaults());
    }
    @Test void legacyTermsAreNotBackfilledAndAdoptionRequiresAnUntouchedLedger(){
        var draft=f.service.save(f.actor,f.form());
        f.jdbc.update("update loan_applications set financial_snapshot=financial_snapshot-'prepaymentTerms' where id=?",draft);
        f.service.disburse(f.actor,f.post(draft,"1000.00","D-LEGACY-SERVICING"));
        assertThat(f.ledger.settlementQuote(draft,f.actor,LoanRecordingPostgresTest.TODAY).legacy()).isTrue();
        f.ledger.adoptPrepaymentTerms(draft,f.actor,"Borrower-agreed servicing convention, signed evidence 001");
        assertThat(f.ledger.settlementQuote(draft,f.actor,LoanRecordingPostgresTest.TODAY).legacy()).isFalse();
        assertThatThrownBy(()->f.ledger.adoptPrepaymentTerms(draft,f.actor,"Duplicate adoption")).hasMessage("prepayment.error.state");
        var active=loan(InterestMethod.FLAT_RATE);payment(active,"410.00",PAYMENT);
        assertThatThrownBy(()->f.ledger.adoptPrepaymentTerms(active,f.actor,"Cannot rewrite a posted history")).hasMessage("repayment.error.state");
    }
    @Test void waiverCannotExceedEarnedInterestOrPostInAClosedPeriod(){
        var loan=loan(InterestMethod.FLAT_RATE);var date=LoanRecordingPostgresTest.TODAY;
        assertThatThrownBy(()->f.ledger.waiveInterest(loan,f.actor,UUID.randomUUID(),date,m("24.53"),"Over the earned amount",null)).hasMessage("prepayment.error.amount");
        f.jdbc.update("insert into accounting_period(id,sacco_id,starts_on,ends_on,state,created_by,created_at) values(?,?,?,?,'CLOSED',?,now())",UUID.randomUUID(),f.institution,date,date,f.actor.getMemberId());
        assertThatThrownBy(()->f.ledger.waiveInterest(loan,f.actor,UUID.randomUUID(),date,m("1.00"),"Closed date",null)).hasMessage("voucher.error.period");
        assertThat(f.jdbc.queryForObject("select count(*) from loan_interest_waiver where loan_id=?",Integer.class,loan)).isZero();
    }
}
