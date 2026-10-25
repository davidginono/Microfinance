package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.grouplending.dto.GroupCollectionDtos;
import com.sacco.mvp.grouplending.service.GroupCollectionService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupCollectionPostgresTest {
    final GroupCyclePostgresTest setup=new GroupCyclePostgresTest();
    LoanRecordingPostgresTest f;GroupCollectionService collections;UUID cycle,first,second;
    @BeforeAll void start(){setup.start();f=setup.f;collections=f.context.getBean(GroupCollectionService.class);}
    @AfterAll void stop(){setup.stop();}
    @BeforeEach void fixture(){setup.setup();var form=setup.form();form.setTenureValue(3);cycle=setup.cycles.create(f.actor,setup.group,form);setup.cycles.submit(f.actor,cycle,form);
        setup.consents.accept(setup.client(f.client),cycle,true,"");setup.consents.accept(setup.client(setup.second),cycle,true,"");
        var participants=setup.cycles.participants(f.actor,cycle);first=participants.stream().filter(p->p.clientId().equals(f.client)).findFirst().orElseThrow().loanId();second=participants.stream().filter(p->p.clientId().equals(setup.second)).findFirst().orElseThrow().loanId();
        for(var loan:List.of(first,second))f.service.disburse(f.actor,f.post(loan,"1000.00","D-GROUP-"+loan));
    }
    GroupCollectionDtos.Form form(){var form=new GroupCollectionDtos.Form();form.setEffectiveDate(LoanRecordingPostgresTest.TODAY);form.setMoneyAccountId(f.cash);form.setReference("GR-"+UUID.randomUUID());form.setRemitter("Synthetic group treasurer");form.setEvidence("Synthetic verified cash receipt");form.setRows(new ArrayList<>(List.of(row(first,f.client,"30.00"),row(first,setup.second,"90.00"),row(second,f.client,"120.00"))));return form;}
    GroupCollectionDtos.Row row(UUID loan,UUID payer,String amount){var r=new GroupCollectionDtos.Row();r.setLoanId(loan);r.setPayerId(payer);r.setAmount(new BigDecimal(amount));r.setDescription("Synthetic verified contribution");return r;}
    GroupCollectionDtos.Form preview(){var form=form();form.setPreviewHash(collections.preview(f.actor,cycle,form).hash());return form;}
    @Test void previewPostsNoMoneyAndReceiptConservesEveryContributorComponent(){
        var form=preview();assertThat(journals()).isEqualTo(2);var receipt=collections.post(f.actor,cycle,form);
        assertThat(receipt.header().total()).isEqualByComparingTo("240.00");assertThat(receipt.rows()).hasSize(2);assertThat(receipt.group().contributions()).hasSize(3);
        var contributions=receipt.group().contributions();assertThat(contributions.getFirst().interest()).isEqualByComparingTo("23.04");assertThat(contributions.getFirst().principal()).isEqualByComparingTo("6.96");
        assertThat(contributions.get(1).payerId()).isEqualTo(setup.second);assertThat(contributions.get(1).beneficiaryId()).isEqualTo(f.client);
        assertThat(contributions.stream().map(ReceiptContribution::principal).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("193.92");
        assertThat(journals()).isEqualTo(4);assertThat(f.service.detail(f.actor,first).loan().outstandingPrincipal()).isEqualByComparingTo("903.04");
        assertThat(collections.post(f.actor,cycle,form).header().id()).isEqualTo(receipt.header().id());assertThat(journals()).isEqualTo(4);
        var register=f.receiptService().list(f.actor,new Filter("",null,null,"","newest",0,25));assertThat(register.rows()).extracting(Voucher::id).containsExactly(receipt.header().id());
        assertThatThrownBy(()->f.jdbc.update("update lending_group_contribution set amount=1 where receipt_id=?",receipt.header().id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void stalePreviewRejectsTheWholeCollectionBeforeAnyOtherLoanChanges(){
        var form=preview();f.service.repay(f.actor,f.post(first,"1.00","OTHER-COLLECTION"));
        assertThatThrownBy(()->collections.post(f.actor,cycle,form)).hasMessage("group.error.previewChanged");assertThat(journals()).isEqualTo(3);
        assertThat(f.service.detail(f.actor,second).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");assertThat(headers()).isZero();
    }
    @Test void correctionIsCompleteIndependentAndRequiresNoCreateClaim(){
        var form=preview();var receipt=collections.post(f.actor,cycle,form);assertThatThrownBy(()->collections.reverse(f.actor,receipt.header().id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic correction")).hasMessage("group.error.correction");
        assertThatThrownBy(()->f.service.reverse(f.actor,receipt.rows().getFirst().postingId(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Individual correction")).hasMessage("recording.error.receiptCorrection");
        var operator=f.operator("B1");var claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.ACCOUNTING_BUSINESS_CREATE);var corrector=new AppUserPrincipal(f.context.getBean(MemberDirectoryService.class).find(operator.getMemberId()).orElseThrow(),claims,true);
        var key=UUID.randomUUID();var correction=collections.reverse(corrector,receipt.header().id(),key,LoanRecordingPostgresTest.TODAY,"Synthetic returned cash");
        assertThat(correction.header().reversesId()).isEqualTo(receipt.header().id());assertThat(correction.group().contributions()).hasSize(3).allSatisfy(c->assertThat(c.reversesId()).isNotNull());
        assertThat(correction.group().reversesNumber()).isEqualTo(receipt.header().number());
        assertThat(collections.find(f.actor,receipt.header().id()).orElseThrow().group().reversedByNumber()).isEqualTo(correction.header().number());
        assertThat(f.service.detail(f.actor,first).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");assertThat(f.service.detail(f.actor,second).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
        assertThat(collections.reverse(corrector,receipt.header().id(),key,LoanRecordingPostgresTest.TODAY,"Synthetic returned cash").header().id()).isEqualTo(correction.header().id());assertThat(journals()).isEqualTo(6);
    }
    @Test void scopeAndPayerMembershipCannotBeBypassed(){
        var form=form();form.getRows().getFirst().setPayerId(UUID.randomUUID());assertThatThrownBy(()->collections.preview(f.actor,cycle,form)).hasMessage("group.error.collectionPayer");
        var foreign=form();foreign.getRows().getFirst().setLoanId(UUID.randomUUID());assertThatThrownBy(()->collections.preview(f.actor,cycle,foreign)).hasMessage("group.error.collectionLoan");
        assertThat(journals()).isEqualTo(2);var saved=collections.post(f.actor,cycle,preview());assertThat(collections.find(f.operator("B2"),saved.header().id())).isEmpty();
        var claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.GROUP_LENDING_VIEW);var noGroup=new AppUserPrincipal(f.context.getBean(MemberDirectoryService.class).find(f.actor.getMemberId()).orElseThrow(),claims,true);
        assertThat(f.receiptService().find(noGroup,saved.header().id())).isEmpty();assertThat(f.receiptService().list(noGroup,new Filter("",null,null,"","newest",0,25)).rows()).isEmpty();
    }
    @Test void auditFailureRollsBackAllLoansAndTheEnvelope(){
        var form=preview();doThrow(new IllegalStateException("Synthetic group audit failure")).when(f.context.getBean(AuditService.class)).logEvent(eq("GROUP_LENDING"),any(),eq("GROUP_COLLECTION_POSTED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->collections.post(f.actor,cycle,form)).hasMessage("Synthetic group audit failure");assertThat(headers()).isZero();assertThat(journals()).isEqualTo(2);
        assertThat(f.service.detail(f.actor,first).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
    }
    @Test void concurrentRetryCreatesOneEnvelopeAndOnePaymentPerLoan()throws Exception{
        var form=preview();var latch=new CountDownLatch(1);try(var workers=Executors.newFixedThreadPool(2)){
            var one=workers.submit(()->{latch.await();return collections.post(f.actor,cycle,form).header().id();});var two=workers.submit(()->{latch.await();return collections.post(f.actor,cycle,form).header().id();});latch.countDown();assertThat(one.get(60,TimeUnit.SECONDS)).isEqualTo(two.get(60,TimeUnit.SECONDS));
        }assertThat(headers()).isEqualTo(1);assertThat(journals()).isEqualTo(4);
    }
    @Test void earlySettlementKeepsExcludedFutureInterestOutOfCashAndCorrectionRestoresIt(){
        var contractualInterest=f.service.detail(f.actor,first).contractualInterest();var form=form();form.setRows(new ArrayList<>(List.of(row(first,f.client,"1023.04"))));form.setPreviewHash(collections.preview(f.actor,cycle,form).hash());var receipt=collections.post(f.actor,cycle,form);
        assertThat(receipt.rows().getFirst().principal()).isEqualByComparingTo("1000.00");assertThat(receipt.rows().getFirst().interest()).isEqualByComparingTo("23.04");assertThat(receipt.header().total()).isEqualByComparingTo("1023.04");
        assertThat(f.jdbc.queryForObject("select sum(interest_excluded) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,first)).isEqualByComparingTo(contractualInterest.subtract(new BigDecimal("23.04")));
        assertThat(f.service.detail(f.actor,first).contractualInterest()).isEqualByComparingTo(contractualInterest);
        collections.reverse(f.operator("B1"),receipt.header().id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic settlement correction");
        assertThat(f.jdbc.queryForObject("select sum(interest_excluded) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,first)).isZero();assertThat(f.service.detail(f.actor,first).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
    }
    @Test void aLaterPaymentOnOneLoanBlocksTheWholeReceiptCorrection(){
        var receipt=collections.post(f.actor,cycle,preview());f.service.repay(f.actor,f.post(second,"1.00","AFTER-GROUP"));
        assertThatThrownBy(()->collections.reverse(f.operator("B1"),receipt.header().id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic correction with dependency")).isInstanceOf(IllegalArgumentException.class);
        assertThat(journals()).isEqualTo(5);assertThat(headers()).isEqualTo(1);assertThat(collections.find(f.actor,receipt.header().id()).orElseThrow().header().reversedBy()).isNull();
        assertThat(f.service.detail(f.actor,first).loan().outstandingPrincipal()).isEqualByComparingTo("903.04");assertThat(f.service.detail(f.actor,second).loan().outstandingPrincipal()).isEqualByComparingTo("902.04");
    }
    @Test void aClosedPeriodBlocksEveryLoanInTheCollection(){
        var form=preview();f.jdbc.update("insert into accounting_period(id,sacco_id,starts_on,ends_on,state,created_by,created_at) values(?,?,?,?,'CLOSED',?,now())",UUID.randomUUID(),f.institution,LoanRecordingPostgresTest.TODAY,LoanRecordingPostgresTest.TODAY,f.actor.getMemberId());
        assertThatThrownBy(()->collections.post(f.actor,cycle,form)).hasMessage("voucher.error.period");assertThat(headers()).isZero();assertThat(journals()).isEqualTo(2);
        assertThat(f.service.detail(f.actor,first).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");assertThat(f.service.detail(f.actor,second).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
    }
    @Test void revokedPostingPermissionCannotBeReusedFromAnOldSession(){
        var form=preview();var current=EnumSet.allOf(UserClaim.class);current.remove(UserClaim.GROUP_LENDING_POST);
        when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(current);
        assertThatThrownBy(()->collections.post(f.actor,cycle,form)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);assertThat(headers()).isZero();assertThat(journals()).isEqualTo(2);
    }
    int journals(){return f.jdbc.queryForObject("select count(*) from gl_journal where sacco_id=? and state='POSTED'",Integer.class,f.institution);}
    int headers(){return f.jdbc.queryForObject("select count(*) from lending_group_receipt where sacco_id=?",Integer.class,f.institution);}
}
