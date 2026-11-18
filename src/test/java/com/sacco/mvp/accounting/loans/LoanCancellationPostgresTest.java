package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.dto.LoanCancellationDtos.*;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Filter;
import com.sacco.mvp.accounting.loans.repository.*;
import com.sacco.mvp.accounting.loans.service.LoanCancellationService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoanCancellationPostgresTest {
    final LoanGroupApprovalPostgresTest workflow=new LoanGroupApprovalPostgresTest();
    final LoanRecordingPostgresTest f=workflow.f;
    LoanCancellationService service;

    @BeforeAll void start() {
        workflow.start();
        f.context.registerBean(LoanCancellationService.class,()->new LoanCancellationService(new LoanCancellationRepository(f.jdbc),
            new LoanRecordingRepository(f.jdbc),f.adjustments(),f.context.getBean(GeneralLedgerService.class),
            f.context.getBean(ApplicationClock.class),f.context.getBean(AuditService.class)));
        service=f.context.getBean(LoanCancellationService.class);
    }
    @AfterAll void stop() {workflow.stop();}
    @BeforeEach void fixture() {
        workflow.fixture();
        f.jdbc.update("insert into member_access_claims(member_id,claim_name) values(?,'LOAN_RECORDING_CANCEL')",f.actor.getMemberId());
    }
    UUID individual() {
        individualRule();return f.service.save(f.actor,workflow.individual());
    }
    void individualRule() {
        var rule=new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.GroupRuleForm();rule.setProductId(f.product);
        rule.setExpectedRevision(new LoanRecordingRepository(f.jdbc).groupRule(f.actor,f.product).orElseThrow().revision());
        rule.setGroups(List.of(Position.ACCOUNTANT));rule.setApprovals(List.of(1));workflow.service.saveRule(f.actor,rule);
    }
    String status(UUID id) {return f.jdbc.queryForObject("select status from loan_applications where id=?",String.class,id);}
    Form form(UUID id) {
        var view=service.view(f.actor,id);var form=new Form();form.setRequestKey(UUID.randomUUID());form.setState(view.status());
        form.setTerms(view.terms());form.setReason("Synthetic mistaken loan");form.setConfirmed(true);
        if(view.funded()){form.setEffectiveDate(LoanRecordingPostgresTest.TODAY);form.setEvidence("Synthetic verified returned funds");}
        return form;
    }
    UUID fund() {UUID id=individual();f.service.disburse(f.actor,f.post(id,"1000.00","D-CANCEL"));return id;}

    @Test void recordedIndividualCancelsOnceAndKeepsEvidenceWithoutMoney() {
        UUID id=individual();var command=form(id);var terms=command.getTerms();
        assertThat(service.cancel(f.actor,id,command).adjustmentId()).isNull();
        assertThat(service.cancel(f.actor,id,command).loanId()).isEqualTo(id);
        assertThat(status(id)).isEqualTo("WITHDRAWN");
        assertThat(service.view(f.actor,id).terms()).isEqualTo(terms);
        assertThat(service.view(f.actor,id).reason()).isEqualTo(command.getReason());
        assertThat(f.jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,f.institution)).isZero();
        assertThat(f.jdbc.queryForObject("select count(*) from staff_group_withdrawal where loan_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(f.service.list(f.actor,new Filter("",null,null,"","newest",0,null)).rows()).extracting(r->r.id()).doesNotContain(id);
        assertThat(f.service.list(f.actor,new Filter("",null,null,"WITHDRAWN","newest",0,null)).rows()).extracting(r->r.id()).contains(id);
        assertThatThrownBy(()->f.service.editForm(f.actor,id)).isInstanceOf(AccessDeniedException.class);
        command.setReason("Different reason");assertThatThrownBy(()->service.cancel(f.actor,id,command)).hasMessage("cancellation.error.retry");
        verify(f.context.getBean(AuditService.class),times(1)).logEvent(eq("ACCOUNTING_LOAN_ADJUSTMENT"),eq(id),eq("LOAN_CANCELLED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->f.jdbc.update("delete from staff_group_withdrawal where loan_id=?",id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->f.jdbc.update("update loan_applications set status='RECORDED' where id=?",id)).isInstanceOf(DataAccessException.class);
    }

    @Test void submittedGroupCancellationRetainsDecisionsAndStopsFurtherReview() {
        UUID id=workflow.loan,request=workflow.submit();workflow.decide(workflow.first,request,"APPROVE");
        service.cancel(f.actor,id,form(id));
        assertThat(status(id)).isEqualTo("WITHDRAWN");
        assertThat(workflow.service.review(workflow.second,id).canApprove()).isFalse();
        assertThat(workflow.queue(workflow.second,Position.ACCOUNTANT)).doesNotContain("/finance/loan-records/"+id);
        assertThatThrownBy(()->workflow.decide(workflow.second,request,"APPROVE")).isInstanceOf(IllegalArgumentException.class);
        assertThat(f.jdbc.queryForObject("select count(*) from loan_group_decision where request_id=?",Integer.class,request)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("select count(*) from staff_group_borrower where loan_id=?",Integer.class,id)).isEqualTo(5);
    }

    @Test void readyLoanCancelsButAStaleRecordedFormCannotCancelChangedState() {
        UUID id=individual();var stale=form(id);f.approve(id);
        assertThatThrownBy(()->service.cancel(f.actor,id,stale)).hasMessage("cancellation.error.changed");
        service.cancel(f.actor,id,form(id));assertThat(status(id)).isEqualTo("WITHDRAWN");
        assertThatThrownBy(()->f.service.disburse(f.actor,f.post(id,"1000.00","D-STALE"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void fundedCancellationWaitsForIndependentApprovalAndExactlyReversesFunding() {
        UUID id=fund();var command=form(id);var result=service.cancel(f.actor,id,command);
        assertThat(result.adjustmentId()).isNotNull();assertThat(status(id)).isEqualTo("DISBURSED");
        assertThat(service.cancel(f.actor,id,command)).isEqualTo(result);
        assertThat(service.view(f.actor,id).adjustmentState()).isEqualTo("PENDING");
        assertThatThrownBy(()->f.adjustments().decide(f.actor,result.adjustmentId(),UUID.randomUUID(),true,"Self review")).hasMessage("adjustment.error.independent");
        var reviewer=f.operator("B1");f.adjustments().decide(reviewer,result.adjustmentId(),UUID.randomUUID(),true,"Verified funds returned");
        assertThat(status(id)).isEqualTo("DISBURSEMENT_REVERSED");assertThat(service.cancel(f.actor,id,command)).isEqualTo(result);
        assertThat(f.adjustments().context(f.actor,id).bookPrincipal()).isZero();
        assertThat(f.jdbc.queryForObject("select count(*) from loan_ledgers where loan_application_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("select count(*) from accountant_loan_post where loan_id=? and kind='DISBURSEMENT'",Integer.class,id)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("select count(*) from loan_repayment_transactions where loan_application_id=?",Integer.class,id)).isZero();
    }

    @Test void anyRepaymentIncludingReversedPaymentsPermanentlyBlocksCancellation() {
        UUID id=fund();var payment=f.service.repay(f.actor,f.post(id,"10.00","R-CANCEL"));
        assertThat(service.view(f.actor,id).blockedReason()).isEqualTo("cancellation.error.repayments");
        assertThatThrownBy(()->service.cancel(f.actor,id,form(id))).hasMessage("cancellation.error.repayments");
        f.service.reverse(f.operator("B1"),payment.id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic wrong receipt");
        assertThatThrownBy(()->service.cancel(f.actor,id,form(id))).hasMessage("cancellation.error.repayments");
    }

    @Test void aReversedChargeOnlyReceiptAlsoBlocksCancellationAndPendingApproval() {
        f.chargePolicy("SEPARATE");individualRule();var recorded=workflow.individual();
        recorded.setInsurancePremium(new java.math.BigDecimal("10.00"));recorded.setInsuranceEvidence("Synthetic verified premium");recorded.setEvidence("Synthetic signed agreement");
        UUID id=f.service.save(f.actor,recorded);f.service.disburse(f.actor,f.chargedFunding(id));
        var request=service.cancel(f.actor,id,form(id));
        var payment=f.service.repay(f.actor,f.post(id,"1.00","R-CHARGE-CANCEL"));
        assertThat(payment.transactionId()).isNull();assertThat(payment.charges()).isPositive();
        var reviewer=f.operator("B1");f.service.reverse(reviewer,payment.id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic wrong charge receipt");
        assertThatThrownBy(()->service.cancel(f.actor,id,form(id))).hasMessage("cancellation.error.repayments");
        assertThatThrownBy(()->f.adjustments().decide(reviewer,request.adjustmentId(),UUID.randomUUID(),true,"Funds return"))
            .hasMessage("adjustment.error.repayments");
        assertThat(status(id)).isEqualTo("DISBURSED");
        assertThat(service.view(f.actor,id).adjustmentState()).isEqualTo("PENDING");
    }

    @Test void permissionsScopeAndLiveRevocationsFailClosed() {
        UUID id=individual();var foreign=f.operator("B2");
        assertThatThrownBy(()->service.view(foreign,id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.cancel(foreign,id,form(id))).isInstanceOf(AccessDeniedException.class);
        var current=f.context.getBean(MemberDirectoryService.class).find(f.actor.getMemberId()).orElseThrow();
        var noCancel=new AppUserPrincipal(current,Set.of(UserClaim.LOAN_RECORDING_VIEW,UserClaim.LOAN_RECORDING_CREATE));
        assertThatThrownBy(()->service.cancel(noCancel,id,form(id))).isInstanceOf(AccessDeniedException.class);
        when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean()))
            .thenReturn(Set.of(UserClaim.LOAN_RECORDING_VIEW));
        assertThatThrownBy(()->service.cancel(f.actor,id,form(id))).isInstanceOf(AccessDeniedException.class);
        assertThat(status(id)).isEqualTo("RECORDED");
        for(var role:Position.values())assertThat(UserClaim.defaultClaims(Set.of(role),false)).doesNotContain(UserClaim.LOAN_RECORDING_CANCEL);
    }

    @Test void auditFailureRollsBackCancellationEvidenceAndState() {
        UUID id=individual();var command=form(id);
        doThrow(new IllegalStateException("Synthetic audit failure")).when(f.context.getBean(AuditService.class))
            .logEvent(eq("ACCOUNTING_LOAN_ADJUSTMENT"),eq(id),eq("LOAN_CANCELLED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->service.cancel(f.actor,id,command)).hasMessage("Synthetic audit failure");
        assertThat(status(id)).isEqualTo("RECORDED");
        assertThat(f.jdbc.queryForObject("select count(*) from staff_group_withdrawal where loan_id=?",Integer.class,id)).isZero();
    }

    @Test void rejectionKeepsFundingAndAllowsANewCancellationRequest() {
        UUID id=fund();var command=form(id);var request=service.cancel(f.actor,id,command);
        f.adjustments().decide(f.operator("B1"),request.adjustmentId(),UUID.randomUUID(),false,"Funds still with client");
        assertThat(status(id)).isEqualTo("DISBURSED");assertThat(service.view(f.actor,id).adjustmentState()).isEqualTo("REJECTED");
        var fresh=service.cancel(f.actor,id,form(id));assertThat(fresh.adjustmentId()).isNotEqualTo(request.adjustmentId());
        assertThat(service.view(f.actor,id).adjustmentId()).isEqualTo(fresh.adjustmentId());
    }

    @Test void cancellationApprovalAndRepaymentCannotBothWin() throws Exception {
        UUID id=fund();var request=service.cancel(f.actor,id,form(id));var reviewer=f.operator("B1");
        var payment=f.post(id,"10.00","R-CANCEL-RACE");var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->attempt(start,()->f.adjustments().decide(reviewer,request.adjustmentId(),UUID.randomUUID(),true,"Verified returned funds")));
            var b=pool.submit(()->attempt(start,()->f.service.repay(f.actor,payment)));
            start.countDown();assertThat(List.of(a.get(60,TimeUnit.SECONDS),b.get(60,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(status(id)).isIn("DISBURSED","DISBURSEMENT_REVERSED");
        int payments=f.jdbc.queryForObject("select count(*) from loan_repayment_transactions where loan_application_id=?",Integer.class,id);
        assertThat(payments).isEqualTo(status(id).equals("DISBURSEMENT_REVERSED")?0:1);
    }

    @Test void simultaneousIdenticalRequestsCancelOnlyOnce() throws Exception {
        UUID id=individual();var command=form(id);try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->service.cancel(f.actor,id,command));var b=pool.submit(()->service.cancel(f.actor,id,command));
            assertThat(a.get(60,TimeUnit.SECONDS)).isEqualTo(b.get(60,TimeUnit.SECONDS));
        }
        assertThat(status(id)).isEqualTo("WITHDRAWN");
        assertThat(f.jdbc.queryForObject("select count(*) from staff_group_withdrawal where loan_id=?",Integer.class,id)).isEqualTo(1);
    }

    @Test void cancellationAndFundingCannotBothWin() throws Exception {
        UUID id=individual();var funding=f.post(id,"1000.00","D-CANCEL-RACE");var command=form(id);
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->attempt(start,()->service.cancel(f.actor,id,command)));
            var b=pool.submit(()->attempt(start,()->f.service.disburse(f.actor,funding)));
            start.countDown();assertThat(List.of(a.get(60,TimeUnit.SECONDS),b.get(60,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(status(id)).isIn("WITHDRAWN","DISBURSED");
        int withdrawn=f.jdbc.queryForObject("select count(*) from staff_group_withdrawal where loan_id=?",Integer.class,id);
        int ledger=f.jdbc.queryForObject("select count(*) from loan_ledgers where loan_application_id=?",Integer.class,id);
        assertThat(withdrawn+ledger).isEqualTo(1);
    }
    static boolean attempt(CountDownLatch start,Runnable work) throws InterruptedException {
        start.await();try{work.run();return true;}catch(IllegalArgumentException expected){return false;}
    }
}
