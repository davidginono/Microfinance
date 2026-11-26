package com.sacco.mvp.accounting.loans;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.sacco.mvp.accounting.dto.VoucherDtos.Form;
import com.sacco.mvp.accounting.dto.VoucherDtos.Type;
import com.sacco.mvp.accounting.repayment.service.LoanPrepaymentService;
import com.sacco.mvp.accounting.service.VoucherService;
import com.sacco.mvp.domain.EarlyPayoffInterest;
import com.sacco.mvp.domain.InterestAccrualMode;
import com.sacco.mvp.service.ApplicationClock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "MICROFINANCE_RECORDING_TEST_URL",
    matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_(recording|flexible_repayment_[a-f0-9]{8})_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonthlyLoanInterestPostgresTest {
    private static final LocalDate DISBURSED = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIRST_DUE = LocalDate.of(2026, 10, 1);
    private LoanRecordingPostgresTest fixture;

    @BeforeAll void start() {
        fixture = new LoanRecordingPostgresTest();
        fixture.start();
    }

    @AfterAll void stop() {
        if (fixture != null && fixture.context != null) fixture.context.close();
    }

    @BeforeEach void setup() {
        fixture.fixture();
    }

    @Test void monthlyAmountsComeFromTheRetainedScheduleAndOnlyOnePeriodOpensOnTheDueDate() {
        UUID loan = loan();
        bindContractTerms(loan);
        assertThat(earned(loan, 1, DISBURSED)).isEqualByComparingTo("20.00");
        assertThat(earned(loan, 2, FIRST_DUE)).isZero();
        assertThat(earned(loan, 2, FIRST_DUE.plusDays(1))).isEqualByComparingTo("20.00");
        assertThat(fixture.ledger.paymentBalance(loan, fixture.actor, DISBURSED).dueInterest())
            .isEqualByComparingTo("20.00");
    }

    @Test void anAmendmentStartsOnItsOwnPeriodAndRetainsTheOriginalInterestBasisBeforeIt() {
        UUID loan = loan();
        fixture.jdbc.update("""
            insert into loan_monthly_interest_terms(loan_id,effective_date,first_installment_number,payoff_interest,
                actor_id,request_key,agreement_reference,approval_reference,recorded_at)
            values (?, ?, 2, 'ELAPSED_DAYS', ?, ?, 'Signed amendment', 'Approved change', now())
            """, loan, FIRST_DUE.plusDays(1), fixture.actor.getMemberId(), UUID.randomUUID());
        assertThat(earned(loan, 1, DISBURSED.plusDays(15))).isEqualByComparingTo("10.00");
        assertThat(earned(loan, 2, FIRST_DUE.plusDays(1))).isEqualByComparingTo("20.00");
        assertThatThrownBy(() -> fixture.jdbc.update("update loan_monthly_interest_terms set payoff_interest='EXCLUDE_OPEN_PERIOD' where loan_id=?", loan))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void anEarlierReportIgnoresInterestCollectedOnALaterDate() {
        UUID loan = loan();
        bindContractTerms(loan);
        var payment = fixture.post(loan, "20.00", "MONTHLY-PAID-" + UUID.randomUUID());
        payment.setEffectiveDate(DISBURSED.plusDays(15));
        assertThat(fixture.service.repay(fixture.actor, payment).interest()).isEqualByComparingTo("20.00");

        String due = "select loan_interest_due(id,?) from loan_ledger_installments where loan_application_id=? and installment_number=1";
        assertThat(fixture.jdbc.queryForObject(due, BigDecimal.class, DISBURSED, loan)).isEqualByComparingTo("20.00");
        assertThat(fixture.jdbc.queryForObject(due, BigDecimal.class, FIRST_DUE, loan)).isZero();
    }

    @Test void contractActivationFreezesThePolicyAndAnApprovedAmendmentStartsAtTheNextPeriod() throws Exception {
        var service=fixture.context.getBean(LoanPrepaymentService.class);
        var clock=fixture.context.getBean(ApplicationClock.class);
        ReflectionTestUtils.setField(service,"monthlyInterestEnabled",true);
        try {
            var form=fixture.form();
            form.setMonths(3);
            UUID loan=fixture.service.save(fixture.actor,form);
            fixture.jdbc.update("update loan_product_settings set early_payoff_interest='ELAPSED_DAYS' where id=?",fixture.product);
            fixture.service.disburse(fixture.actor,fixture.post(loan,"1000.00","D-ACTIVATED-"+loan));
            assertThat(fixture.jdbc.queryForObject("select payoff_interest from loan_monthly_interest_terms where loan_id=?",String.class,loan))
                .isEqualTo("EXCLUDE_OPEN_PERIOD");
            var before=DISBURSED.plusDays(15);
            when(clock.today()).thenReturn(before);
            LocalDate effective=FIRST_DUE.plusDays(1);
            assertThat(fixture.service.proposedInterestChange(fixture.actor,loan)).contains(effective);
            var document=new MockMultipartFile("agreement","agreement.pdf","application/pdf","%PDF-1.4\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            UUID key=UUID.randomUUID();
            assertThat(fixture.service.recordInterestChange(fixture.actor,loan,key,effective,EarlyPayoffInterest.ELAPSED_DAYS,"APPROVAL-42",document)).isTrue();
            assertThat(fixture.service.recordInterestChange(fixture.actor,loan,key,effective,EarlyPayoffInterest.ELAPSED_DAYS,"APPROVAL-42",document)).isFalse();
            BigDecimal firstScheduledInterest=fixture.jdbc.queryForObject(
                "select interest from loan_ledger_installments where loan_application_id=? and installment_number=1",BigDecimal.class,loan);
            assertThat(earned(loan,1,before)).isEqualByComparingTo(firstScheduledInterest);
            assertThat(earned(loan,2,FIRST_DUE)).isZero();
            assertThat(earned(loan,2,effective)).isEqualByComparingTo(firstScheduledInterest);
            assertThat(fixture.ledger.settlementQuote(loan,fixture.actor,before).interest()).isZero();
            assertThat(fixture.ledger.settlementQuote(loan,fixture.actor,effective.plusDays(6)).interest()).isGreaterThan(firstScheduledInterest);
        } finally {
            ReflectionTestUtils.setField(service,"monthlyInterestEnabled",false);
            when(clock.today()).thenReturn(LoanRecordingPostgresTest.TODAY);
        }
    }

    @Test void payoffReturnsCollectedInterestAndRefundVoucherCanBeCorrectedBeforeThePayoff() {
        UUID loan=loan();
        bindContractTerms(loan);
        LocalDate date=DISBURSED.plusDays(15);
        var receipt=fixture.post(loan,"1005.00","ADVANCE-"+loan);
        receipt.setEffectiveDate(date);
        var advance=fixture.service.repay(fixture.actor,receipt);
        assertThat(advance.interest()).isEqualByComparingTo("20.00");
        var quote=fixture.ledger.settlementQuote(loan,fixture.actor,date);
        assertThat(quote.total()).isZero();
        assertThat(quote.refundDue()).isEqualByComparingTo("5.00");
        UUID payable=fixture.account("250005","LIABILITY","CREDIT","POSTING","PAYABLE"),key=UUID.randomUUID();
        var payoff=fixture.service.postMonthlyPayoff(fixture.actor,loan,key,date,quote.sequence(),quote.total(),quote.refundDue(),null,payable,"");
        assertThat(fixture.service.postMonthlyPayoff(fixture.actor,loan,key,date,quote.sequence(),quote.total(),quote.refundDue(),null,payable,"").id()).isEqualTo(payoff.id());
        assertThat(payoff.returned()).isEqualByComparingTo("20.00");
        assertThat(netDebit(payoff.journal(),fixture.interest)).isEqualByComparingTo("20.00");
        assertThat(netDebit(payoff.journal(),fixture.principal)).isEqualByComparingTo("-15.00");
        assertThat(netDebit(payoff.journal(),payable)).isEqualByComparingTo("-5.00");
        assertThat(fixture.ledger.paymentBalance(loan,fixture.actor,date).outstandingPrincipal()).isZero();

        var vouchers=fixture.context.getBean(VoucherService.class);
        var refund=new Form();
        vouchers.prefillPayoffRefund(fixture.actor,refund,payoff.id());
        refund.setRequestKey(UUID.randomUUID());
        refund.setEffectiveDate(date);
        refund.setMoneyAccountId(fixture.cash);
        refund.setReference("REFUND-"+loan);
        refund.setEvidence("Verified refund transfer");
        var paid=vouchers.post(fixture.actor,Type.PAYMENT,refund);
        assertThat(vouchers.post(fixture.actor,Type.PAYMENT,refund).id()).isEqualTo(paid.id());
        assertThat(netDebit(paid.journalId(),payable)).isEqualByComparingTo("5.00");
        assertThat(netDebit(paid.journalId(),fixture.cash)).isEqualByComparingTo("-5.00");
        var reviewer=fixture.operator("B1");
        assertThatThrownBy(() -> fixture.service.reverseMonthlyPayoff(reviewer,loan,payoff.id(),UUID.randomUUID(),date,"Correction"))
            .isInstanceOf(IllegalArgumentException.class);
        vouchers.reverse(reviewer,Type.PAYMENT,paid.id(),UUID.randomUUID(),date,"Correct refund");
        var undone=fixture.service.reverseMonthlyPayoff(reviewer,loan,payoff.id(),UUID.randomUUID(),date,"Correct payoff");
        assertThat(netDebit(undone.journal(),payable)).isEqualByComparingTo("5.00");
        assertThat(fixture.ledger.paymentBalance(loan,fixture.actor,date).outstandingPrincipal()).isEqualByComparingTo("15.00");
        fixture.service.reverse(reviewer,advance.id(),UUID.randomUUID(),date,"Correct advance");
        assertThat(fixture.ledger.paymentBalance(loan,fixture.actor,date).outstandingPrincipal()).isEqualByComparingTo("1000.00");
    }

    @Test void cashPayoffAppearsInTheReceiptRegisterAtTheAmountReceived() {
        UUID loan=loan();
        bindContractTerms(loan);
        LocalDate date=DISBURSED.plusDays(15);
        var receipt=fixture.post(loan,"1000.00","ADVANCE-"+loan);
        receipt.setEffectiveDate(date);
        fixture.service.repay(fixture.actor,receipt);
        var quote=fixture.ledger.settlementQuote(loan,fixture.actor,FIRST_DUE);
        assertThat(quote.total()).isPositive();
        var posted=fixture.service.postMonthlyPayoff(fixture.actor,loan,UUID.randomUUID(),FIRST_DUE,quote.sequence(),
            quote.total(),quote.refundDue(),fixture.cash,null,"Verified payoff receipt");
        assertThat(netDebit(posted.journal(),fixture.cash)).isEqualByComparingTo(quote.total());
        var vouchers=fixture.context.getBean(VoucherService.class);
        assertThat(vouchers.view(fixture.actor,Type.RECEIPT,posted.journal()).total()).isEqualByComparingTo(quote.total());
        var rows=new com.sacco.mvp.accounting.repository.LoanReceiptRepository(fixture.jdbc).list(fixture.actor,
            new com.sacco.mvp.accounting.dto.VoucherDtos.Filter("",null,null,"","newest",0),true);
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.id()).isEqualTo(posted.journal());
            assertThat(row.total()).isEqualByComparingTo(quote.total());
        });
    }

    @Test void productAccrualFreezesTheModeAndTheLoanTenureAndYearlyAccrualDoesNotAccelerateCollection() {
        var prepayments=fixture.context.getBean(LoanPrepaymentService.class);
        ReflectionTestUtils.setField(prepayments,"monthlyInterestEnabled",true);
        try {
            fixture.jdbc.update("update loan_product_settings set interest_accrual_mode='PERIODIC' where id=?",fixture.product);
            var form=fixture.form();
            form.setMonths(null);
            form.setTenureUnit("YEARS");
            form.setTenureValue(1);
            UUID loan=fixture.service.save(fixture.actor,form);
            fixture.jdbc.update("update loan_product_settings set interest_accrual_mode='DAILY' where id=?",fixture.product);
            fixture.service.disburse(fixture.actor,fixture.post(loan,"1000.00","D-YEAR-"+loan));
            assertThat(fixture.jdbc.queryForMap("select mode,tenure_unit from loan_contract_interest_accrual where loan_id=?",loan))
                .containsEntry("mode",InterestAccrualMode.PERIODIC.name()).containsEntry("tenure_unit","YEARS");

            assertThat(earned(loan,2,DISBURSED)).isEqualByComparingTo("20.00");
            assertThat(prepayments.collectible(loan,DISBURSED).values()).containsOnly(new BigDecimal("0"),new BigDecimal("20.00"));
            assertThat(fixture.ledger.paymentBalance(loan,fixture.actor,DISBURSED).dueInterest()).isEqualByComparingTo("20.00");
            var receipt=fixture.post(loan,"70.00","R-YEAR-"+loan);
            receipt.setEffectiveDate(DISBURSED);
            var preview=fixture.ledger.previewPayment(loan,fixture.actor,DISBURSED,receipt.getAmount(),java.util.List.of());
            var posted=fixture.service.repay(fixture.actor,receipt);
            assertThat(posted.interest()).isEqualByComparingTo(preview.interest()).isEqualByComparingTo("20.00");
            assertThat(posted.principal()).isEqualByComparingTo("50.00");
            assertThat(fixture.ledger.paymentBalance(loan,fixture.actor,FIRST_DUE.plusDays(1)).dueInterest()).isEqualByComparingTo("20.00");

            var schedule=fixture.ledger.waiverSchedule(loan,fixture.actor,DISBURSED);
            var second=schedule.installments().get(1);
            assertThat(second.accrued()).isEqualByComparingTo("20.00");
            var waiver=fixture.ledger.waiveInstallments(loan,fixture.actor,UUID.randomUUID(),DISBURSED,"Approved interest waiver",
                schedule.sequence(),java.util.Map.of(second.id(),new BigDecimal("5.00")));
            assertThat(fixture.jdbc.queryForObject("select future_amount from loan_interest_waiver where id=?",BigDecimal.class,waiver)).isZero();
            assertThat(fixture.ledger.paymentBalance(loan,fixture.actor,FIRST_DUE.plusDays(1)).dueInterest()).isEqualByComparingTo("15.00");
        } finally {
            ReflectionTestUtils.setField(prepayments,"monthlyInterestEnabled",false);
        }
    }

    @Test void theProductAccrualBoundariesHandleDailySlicesAndCalendarAnniversaries() {
        String fraction="select loan_contract_interest_fraction(?,?,'MONTHLY',?::date,?::date,?::date,?::date,false)";
        assertThat(fixture.jdbc.queryForObject(fraction,BigDecimal.class,"DAILY","DAYS",DISBURSED,null,FIRST_DUE,DISBURSED)).isZero();
        assertThat(fixture.jdbc.queryForObject(fraction,BigDecimal.class,"PERIODIC","DAYS",DISBURSED,null,FIRST_DUE,DISBURSED))
            .isEqualByComparingTo(new BigDecimal("0.03333333333333333333"));
        assertThat(fixture.jdbc.queryForObject(fraction,BigDecimal.class,"PERIODIC","MONTHS",DISBURSED,FIRST_DUE,FIRST_DUE.plusMonths(1),FIRST_DUE)).isZero();
        assertThat(fixture.jdbc.queryForObject(fraction,BigDecimal.class,"PERIODIC","MONTHS",DISBURSED,FIRST_DUE,FIRST_DUE.plusMonths(1),FIRST_DUE.plusDays(1)))
            .isEqualByComparingTo(BigDecimal.ONE);
        var leap=LocalDate.of(2024,2,29);
        assertThat(fixture.jdbc.queryForObject("select loan_accrual_bucket_open(?,?,'YEARS')",LocalDate.class,leap,LocalDate.of(2025,3,1)))
            .isEqualTo(LocalDate.of(2025,3,1));
    }

    private BigDecimal netDebit(UUID journal,UUID account) {
        return fixture.jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where journal_id=? and account_id=?",
            BigDecimal.class,journal,account);
    }

    private UUID loan() {
        var form = fixture.form();
        form.setMonths(2);
        UUID loan = fixture.service.save(fixture.actor, form);
        fixture.service.disburse(fixture.actor, fixture.post(loan, "1000.00", "D-MONTHLY-" + UUID.randomUUID()));
        return loan;
    }

    private void bindContractTerms(UUID loan) {
        fixture.jdbc.update("""
            insert into loan_monthly_interest_terms(loan_id,effective_date,first_installment_number,payoff_interest,recorded_at)
            values (?, ?, 1, 'EXCLUDE_OPEN_PERIOD', now())
            """, loan, DISBURSED);
    }

    private BigDecimal earned(UUID loan, int installment, LocalDate date) {
        return fixture.jdbc.queryForObject("""
            select loan_interest_earned(id,?) from loan_ledger_installments
            where loan_application_id=? and installment_number=?
            """, BigDecimal.class, date, loan, installment);
    }
}
