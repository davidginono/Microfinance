package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.*;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FundingDeductionPostgresTest {
    final LoanRecordingPostgresTest f=new LoanRecordingPostgresTest();
    @BeforeAll void start(){f.start();}
    @AfterAll void stop(){f.stop();}
    @BeforeEach void fixture(){f.fixture();f.chargePolicy("SEPARATE");}
    List<UUID> selected(UUID loan,String... kinds){return f.service.chargeSummary(f.actor,loan).plan().terms().stream().filter(t->List.of(kinds).contains(t.kind())).map(Term::id).toList();}
    @Test void electedFeesReduceCashWithoutChangingFrozenTermsOrPrincipal(){
        var loan=f.chargedLoan();var ids=selected(loan,"APPLICATION","INSURANCE");
        var quote=f.service.fundingQuote(f.actor,loan,LoanRecordingPostgresTest.TODAY,ids);
        assertThat(quote.cash()).isEqualByComparingTo("980.00");assertThat(quote.deductions()).isEqualByComparingTo("20.00");assertThat(quote.separate()).isEqualByComparingTo("10.00");
        var form=f.chargedFunding(loan);form.setDeductChargeIds(ids);var posted=f.service.disburse(f.actor,form);
        assertThat(posted.amount()).isEqualByComparingTo("980.00");
        assertThat(f.service.detail(f.actor,loan).loan().principal()).isEqualByComparingTo("1000.00");
        var summary=f.service.chargeSummary(f.actor,loan);
        assertThat(summary.plan().terms()).allMatch(t->t.timing()==Timing.SEPARATE);
        assertThat(summary.balance().collected()).isEqualByComparingTo("20.00");assertThat(summary.balance().outstanding()).isEqualByComparingTo("10.00");
        assertThat(f.jdbc.queryForObject("select sum(credit-debit) from gl_journal_line where journal_id=? and account_id=?",BigDecimal.class,posted.journalId(),f.cash)).isEqualByComparingTo("980.00");
        assertThat(new BigDecimal(f.jdbc.queryForObject("select financial_snapshot->>'recordedNetCashEstimate' from loan_applications where id=?",String.class,loan))).isEqualByComparingTo("1000.00");
        assertThat(f.service.disburse(f.actor,form).id()).isEqualTo(posted.id());
        form.setDeductChargeIds(selected(loan,"PROCESSING"));
        assertThatThrownBy(()->f.service.disburse(f.actor,form)).hasMessage("recording.error.retry");
    }
    @Test void agreedDeductionsRemainMandatory(){
        f.fixture();f.chargePolicy("DEDUCT");var loan=f.chargedLoan();
        assertThat(f.service.fundingQuote(f.actor,loan,LoanRecordingPostgresTest.TODAY,List.of()).deductions()).isEqualByComparingTo("30.00");
        f.service.disburse(f.actor,f.chargedFunding(loan));
        assertThat(f.service.chargeSummary(f.actor,loan).balance().outstanding()).isZero();
    }
    @Test void foreignAndDuplicateChoicesAndMissingEvidenceFailBeforeMoneyMoves(){
        var loan=f.chargedLoan();var foreign=f.chargedLoan();var form=f.chargedFunding(loan);
        form.setDeductChargeIds(selected(foreign,"APPLICATION"));
        assertThatThrownBy(()->f.service.disburse(f.actor,form)).hasMessage("charges.error.selection");
        var id=selected(loan,"APPLICATION").getFirst();form.setDeductChargeIds(List.of(id,id));
        assertThatThrownBy(()->f.service.disburse(f.actor,form)).hasMessage("charges.error.selection");
        form.setDeductChargeIds(List.of(id));form.setEvidence("");
        assertThatThrownBy(()->f.service.disburse(f.actor,form)).isInstanceOf(IllegalArgumentException.class);
        assertThat(f.service.chargeSummary(f.actor,loan).balance().assessed()).isZero();
        assertThat(f.jdbc.queryForObject("select count(*) from accountant_loan_post where loan_id=?",Integer.class,loan)).isZero();
        assertThatThrownBy(()->f.service.fundingQuote(f.operator("B2"),loan,LoanRecordingPostgresTest.TODAY,List.of(id))).isInstanceOf(AccessDeniedException.class);
    }
    @Test void selectionNeedsTheExistingDisbursementClaim(){
        var loan=f.chargedLoan();var member=f.context.getBean(com.sacco.mvp.service.MemberDirectoryService.class).find(f.actor.getMemberId()).orElseThrow();
        var reader=new AppUserPrincipal(member,Set.of(UserClaim.DISBURSEMENT_QUEUE_VIEW));
        assertThatThrownBy(()->f.service.fundingQuote(reader,loan,LoanRecordingPostgresTest.TODAY,selected(loan,"APPLICATION"))).isInstanceOf(AccessDeniedException.class);
    }
    @Test void aChoiceCannotLeaveZeroCashAndFailureRollsBackAssessment(){
        f.jdbc.update("update loan_product_settings set application_fee=1000 where id=?",f.product);
        var loan=f.chargedLoan();var ids=selected(loan,"APPLICATION");
        assertThatThrownBy(()->f.service.fundingQuote(f.actor,loan,LoanRecordingPostgresTest.TODAY,ids)).hasMessage("recording.error.disbursementAmount");
        var form=f.chargedFunding(loan);form.setDeductChargeIds(ids);
        assertThatThrownBy(()->f.service.disburse(f.actor,form)).hasMessage("recording.error.disbursementAmount");
        assertThat(f.service.chargeSummary(f.actor,loan).balance().assessed()).isZero();
    }
    @Test void concurrentRetriesCreateOneCashPostingAndOneCollectionPerSelectedFee() throws Exception {
        var loan=f.chargedLoan();var form=f.chargedFunding(loan);form.setDeductChargeIds(selected(loan,"APPLICATION","PROCESSING"));
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            var a=pool.submit(()->{start.await();return f.service.disburse(f.actor,form).id();});
            var b=pool.submit(()->{start.await();return f.service.disburse(f.actor,form).id();});start.countDown();
            assertThat(a.get(30,TimeUnit.SECONDS)).isEqualTo(b.get(30,TimeUnit.SECONDS));
            assertThat(f.jdbc.queryForObject("select count(*) from accountant_loan_post where loan_id=?",Integer.class,loan)).isEqualTo(1);
            assertThat(f.jdbc.queryForObject("select count(*) from loan_charge_event where loan_id=? and kind='DEDUCT'",Integer.class,loan)).isEqualTo(2);
        }finally{pool.shutdownNow();}
    }
    @Test void independentFundingCorrectionRestoresTheElectedFeesAndPreservesTheirHistory(){
        var loan=f.chargedLoan();var form=f.chargedFunding(loan);form.setDeductChargeIds(selected(loan,"APPLICATION","INSURANCE"));f.service.disburse(f.actor,form);
        var request=f.adjustments().request(f.actor,f.adjustment(loan,"DISBURSEMENT_REVERSAL"));
        f.adjustments().decide(f.operator("B1"),request,UUID.randomUUID(),true,"Synthetic independently verified cash return");
        var balance=f.service.chargeSummary(f.actor,loan).balance();assertThat(balance.assessed()).isZero();assertThat(balance.collected()).isZero();
        assertThat(f.jdbc.queryForObject("select count(*) from loan_charge_event where loan_id=? and kind='DEDUCT_UNDO'",Integer.class,loan)).isEqualTo(2);
        assertThat(f.service.chargeSummary(f.actor,loan).plan().terms()).allMatch(t->t.timing()==Timing.SEPARATE);
    }
    @Test void topUpDeductionKeepsSourceSettlementSeparate(){
        var source=f.chargedLoan();f.service.disburse(f.actor,f.chargedFunding(source));
        f.jdbc.update("update loan_product_settings set application_fee=0,processing_fee_rate=0 where id=?",f.product);
        var loan=f.recordTopUp(source);var form=f.topUpPost(loan);var s=f.service.disbursementTopUp(f.actor,loan,LoanRecordingPostgresTest.TODAY).orElseThrow();
        form.setSettlementCharges(s.charges());form.setSettlementChargeSequence(s.chargeSequence());form.setDeductChargeIds(selected(loan,"INSURANCE"));
        var quote=f.service.fundingQuote(f.actor,loan,LoanRecordingPostgresTest.TODAY,form.getDeductChargeIds());
        assertThat(quote.cash()).isEqualByComparingTo(new BigDecimal("1500.00").subtract(s.total()).subtract(new BigDecimal("15.00")));
        f.service.disburse(f.actor,form);
        assertThat(f.service.chargeSummary(f.actor,source).balance().outstanding()).isZero();assertThat(f.service.chargeSummary(f.actor,loan).balance().outstanding()).isZero();
        assertThat(f.service.topUp(f.actor,loan).orElseThrow().newDeductions()).isEqualByComparingTo("15.00");
    }
}
