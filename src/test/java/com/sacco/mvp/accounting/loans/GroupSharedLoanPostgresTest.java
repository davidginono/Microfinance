package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.service.GroupSharedLoanService;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupSharedLoanPostgresTest {
    final GroupSharedAgreementPostgresTest s=new GroupSharedAgreementPostgresTest();
    final LoanRecordingPostgresTest f=s.s.g.f;
    GroupSharedLoanService shared;UUID loan;
    @BeforeAll void start(){s.start();shared=f.context.getBean(GroupSharedLoanService.class);}
    @AfterAll void stop(){s.stop();}
    @BeforeEach void setup(){s.setup();loan=s.s.g.cycles.sharedFacility(f.actor,s.cycle).orElseThrow().loanId();
        String hash=s.open();assertThat(s.agreements.confirm(s.client,s.cycle,hash,true,"")).isNull();
        var second=s.s.g.client(s.s.g.second);s.agreements.exportClient(second,s.cycle,Locale.ENGLISH);assertThat(s.agreements.confirm(second,s.cycle,s.agreements.packet(second,s.cycle).row().hash(),true,"")).isNull();
        f.context.getBean(MemberDirectoryService.class).find(f.actor.getMemberId()).orElseThrow().setSignatureText("Synthetic lender signature");
        f.jdbc.update("update members set signature_text='Synthetic lender signature' where id=?",f.actor.getMemberId());
    }
    com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.PostForm funding(){var x=f.post(loan,"1000.01","SHARED-FUND-"+UUID.randomUUID());var p=shared.funding(f.actor,loan).orElseThrow();x.setSharedPacketHash(p.hash());x.setSharedAccepted(true);x.setSharedPayoutsConfirmed(true);x.setSharedPayoutReferences(List.of("QA-A-"+UUID.randomUUID(),"QA-B-"+UUID.randomUUID()));return x;}
    com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.PostForm payment(String amount){var x=f.post(loan,amount,"SHARED-PAY-"+UUID.randomUUID());x.setSharedPayer("Synthetic member A covering shared obligations");return x;}
    int count(String table){return s.s.count(table);}
    BigDecimal sum(String column){return f.jdbc.queryForObject("select coalesce(sum("+column+"),0) from lending_group_shared_movement where sacco_id=?",BigDecimal.class,f.institution);}
    @Test void completeUnequalFundingCreatesOneDebtAndTwoExactConfirmedTransfers(){
        var x=funding();var post=f.service.disburse(f.actor,x);assertThat(f.service.disburse(f.actor,x).id()).isEqualTo(post.id());
        assertThat(count("loan_ledgers")).isEqualTo(1);assertThat(count("gl_journal")).isEqualTo(1);assertThat(count("lending_group_shared_transfer")).isEqualTo(2);
        assertThat(f.jdbc.queryForObject("select sum(amount) from lending_group_shared_transfer where cycle_id=?",BigDecimal.class,s.cycle)).isEqualByComparingTo("1000.01");
        assertThat(f.jdbc.queryForObject("select applicant_member_id is null from loan_ledgers where loan_application_id=?",Boolean.class,loan)).isTrue();
        assertThat(shared.balances(f.actor,loan)).hasSize(2).allSatisfy(b->assertThat(b.remainingPrincipal()).isEqualByComparingTo(b.principal()));
        assertThat(f.jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where sacco_id=?",BigDecimal.class,f.institution)).isZero();
    }
    @Test void partialRepaymentsConserveCentsAndIndependentCorrectionsRestoreEachMember(){
        f.service.disburse(f.actor,funding());var first=f.service.repay(f.actor,payment("0.01"));var second=f.service.repay(f.actor,payment("120.00"));
        assertThat(sum("principal")).isEqualByComparingTo(first.principal().add(second.principal()));assertThat(sum("interest")).isEqualByComparingTo(first.interest().add(second.interest()));
        assertThat(shared.balances(f.actor,loan).stream().map(b->b.remainingPrincipal()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("880.00");
        assertThatThrownBy(()->f.service.reverse(f.actor,second.id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Self correction")).hasMessage("recording.error.independentCorrection");
        f.service.reverse(f.operator("B1"),second.id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic independent correction");
        assertThat(shared.balances(f.actor,loan).stream().map(b->b.remainingPrincipal()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("1000.00");
        assertThat(count("gl_journal")).isEqualTo(4);assertThat(count("lending_group_shared_movement")).isEqualTo(6);
    }
    @Test void earlySettlementExclusionsAreNotCashAndTheirReversalRestoresShares(){
        f.service.disburse(f.actor,funding());var paid=f.service.repay(f.actor,payment("1000.01"));
        assertThat(paid.interest()).isZero();assertThat(sum("excluded_interest")).isEqualByComparingTo("20.00");
        assertThat(shared.balances(f.actor,loan)).allSatisfy(b->{assertThat(b.remainingPrincipal()).isZero();assertThat(b.remainingInterest()).isZero();});
        f.service.reverse(f.operator("B1"),paid.id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic shared settlement correction");
        assertThat(shared.balances(f.actor,loan)).allSatisfy(b->{assertThat(b.remainingPrincipal()).isEqualByComparingTo(b.principal());assertThat(b.remainingInterest()).isEqualByComparingTo(b.interest());});
        assertThat(f.service.detail(f.actor,loan).contractualInterest()).isEqualByComparingTo("20.00");
    }
    @Test void missingTransferConfirmationAndChangedTermsCannotOpenDebt(){
        var x=funding();x.setSharedPayoutsConfirmed(false);assertThatThrownBy(()->f.service.disburse(f.actor,x)).hasMessage("recording.error.sharedTransfers");
        x.setSharedPayoutsConfirmed(true);x.setSharedPayoutReferences(List.of("DUPLICATE","DUPLICATE"));assertThatThrownBy(()->f.service.disburse(f.actor,x)).hasMessage("recording.error.sharedTransfers");
        var changed=funding();changed.setFirstPaymentDate(LoanRecordingPostgresTest.TODAY.plusMonths(2));assertThatThrownBy(()->f.service.disburse(f.actor,changed)).hasMessage("recording.error.sharedTerms");
        assertThat(count("loan_ledgers")).isZero();assertThat(count("gl_journal")).isZero();
    }
    @Test void currentExecutionClaimsBranchAndPersonalPayerRemainMandatory(){
        assertThatThrownBy(()->f.service.disburse(f.operator("B2"),funding())).isInstanceOf(AccessDeniedException.class);
        var claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.GROUP_LENDING_EXECUTE);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(claims);
        assertThatThrownBy(()->f.service.disburse(f.actor,funding())).isInstanceOf(AccessDeniedException.class);
        when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));f.service.disburse(f.actor,funding());
        var payment=payment("20.00");payment.setSharedPayer(null);assertThatThrownBy(()->f.service.repay(f.actor,payment)).hasMessage("recording.error.sharedPayer");assertThat(count("lending_group_shared_movement")).isZero();
    }
    @Test void latePostingAuditFailureRollsBackCashAndEveryAttribution(){
        f.service.disburse(f.actor,funding());
        doThrow(new IllegalStateException("Synthetic repayment audit failure")).when(f.context.getBean(AuditService.class)).logEvent(eq("LOAN_RECORDING"),any(),eq("LOAN_REPAYMENT_POSTED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->f.service.repay(f.actor,payment("120.00"))).hasMessage("Synthetic repayment audit failure");
        assertThat(count("lending_group_shared_movement")).isZero();assertThat(count("gl_journal")).isEqualTo(1);assertThat(f.service.detail(f.actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("1000.01");
    }
    @Test void concurrentFundingRetriesHaveOnePostingAndPermanentTransferEvidence()throws Exception{
        var x=funding();try(var workers=Executors.newFixedThreadPool(2)){var gate=new CountDownLatch(1);var a=workers.submit(()->{gate.await();return f.service.disburse(f.actor,x).id();});var b=workers.submit(()->{gate.await();return f.service.disburse(f.actor,x).id();});gate.countDown();assertThat(a.get(60,TimeUnit.SECONDS)).isEqualTo(b.get(60,TimeUnit.SECONDS));}
        assertThat(count("gl_journal")).isEqualTo(1);assertThatThrownBy(()->f.jdbc.update("delete from lending_group_shared_transfer where cycle_id=?",s.cycle)).hasMessageContaining("append-only");
    }
    @Test void unifiedReceiptRetainsActualPayerTypedBorrowerAndWholeCorrection(){
        f.service.disburse(f.actor,funding());var party=f.service.detail(f.actor,loan).loan().clientId();var x=f.receiptForm(loan);x.setClientId(party);x.setLoanPayer("Synthetic treasurer / members' remittance");
        assertThat(f.receiptService().preview(f.actor,x)).hasSize(1);var receipt=f.receiptService().post(f.actor,x);assertThat(receipt.payer()).isEqualTo(x.getLoanPayer());
        assertThat(f.receiptService().post(f.actor,x).header().id()).isEqualTo(receipt.header().id());assertThat(f.service.statement(f.actor,party,0).principal()).isEqualByComparingTo("880.01");
        var correction=f.receiptService().reverse(f.operator("B1"),receipt.header().id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic whole shared receipt correction");
        assertThat(correction.payer()).isEqualTo(x.getLoanPayer());assertThat(f.service.statement(f.actor,party,0).principal()).isEqualByComparingTo("1000.01");
        assertThat(shared.balances(f.actor,loan)).allSatisfy(b->assertThat(b.remainingPrincipal()).isEqualByComparingTo(b.principal()));
    }
    @Test void incompleteBorrowerAuthorityCannotFund(){
        var withdrawal=new com.sacco.mvp.grouplending.dto.GroupCycleDtos.TerminationForm();withdrawal.setExpectedVersion(1);withdrawal.setReason("Synthetic replacement fixture");s.s.g.cycles.withdraw(f.actor,s.cycle,withdrawal);
        s.cycle=s.s.submit();loan=s.s.g.cycles.sharedFacility(f.actor,s.cycle).orElseThrow().loanId();s.document();
        var current=funding();assertThat(shared.funding(f.actor,loan).orElseThrow().ready()).isFalse();assertThatThrownBy(()->f.service.disburse(f.actor,current)).hasMessage("recording.error.sharedFunding");
        assertThat(count("gl_journal")).isZero();assertThat(count("lending_group_shared_transfer")).isZero();
    }
    @Test void lateFundingAuditFailureRollsBackEveryTransferAndTheParentLedger(){
        doThrow(new IllegalStateException("Synthetic final shared funding failure")).when(f.context.getBean(AuditService.class)).logEvent(eq("LOAN_RECORDING"),any(),eq("LOAN_DISBURSEMENT_POSTED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->f.service.disburse(f.actor,funding())).hasMessage("Synthetic final shared funding failure");
        for(var table:List.of("lending_group_shared_funding","lending_group_shared_transfer","loan_ledgers","gl_journal","accountant_loan_post"))assertThat(count(table)).isZero();
    }
    @Test void independentFundingReturnKeepsInstructionsAndClearsAllocationBalances(){
        f.service.disburse(f.actor,funding());var request=f.adjustments().request(f.actor,f.adjustment(loan,"DISBURSEMENT_REVERSAL"));
        f.adjustments().decide(f.operator("B1"),request,UUID.randomUUID(),true,"Synthetic verified return of every member transfer");
        assertThat(count("lending_group_shared_transfer")).isEqualTo(2);assertThat(count("lending_group_shared_funding")).isEqualTo(1);
        assertThat(shared.balances(f.actor,loan)).allSatisfy(b->{assertThat(b.remainingPrincipal()).isZero();assertThat(b.remainingInterest()).isZero();assertThat(b.fundingReversed()).isTrue();});
    }
    @Test void staffAllocationStatementDistinguishesUnfundedSharesAndEnforcesBranchAndExportClaims(){
        var statements=new com.sacco.mvp.grouplending.service.GroupSharedStatementService(s.s.g.cycles,shared,f.service,f.context.getBean(com.sacco.mvp.accounting.service.GeneralLedgerService.class));
        assertThat(statements.statement(f.actor,s.cycle,false).funded()).isFalse();assertThat(statements.statement(f.actor,s.cycle,false).known()).isFalse();f.service.disburse(f.actor,funding());f.service.repay(f.actor,payment("120.00"));
        var statement=statements.statement(f.actor,s.cycle,true);assertThat(statement.funded()).isTrue();assertThat(statement.known()).isTrue();assertThat(statement.members()).hasSize(2);
        assertThat(statement.members().stream().map(b->b.remainingPrincipal()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(statement.parent().loan().outstandingPrincipal());
        assertThatThrownBy(()->statements.statement(f.operator("B2"),s.cycle,false)).isInstanceOf(AccessDeniedException.class);
        var claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.GROUP_LENDING_EXPORT);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(claims);
        assertThatThrownBy(()->statements.statement(f.actor,s.cycle,true)).isInstanceOf(AccessDeniedException.class);
    }
}
