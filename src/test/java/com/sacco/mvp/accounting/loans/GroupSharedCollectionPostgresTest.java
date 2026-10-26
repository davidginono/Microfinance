package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.grouplending.dto.GroupCollectionDtos;
import com.sacco.mvp.grouplending.service.GroupCollectionService;
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
class GroupSharedCollectionPostgresTest {
    final GroupSharedLoanPostgresTest s=new GroupSharedLoanPostgresTest();final LoanRecordingPostgresTest f=s.f;
    GroupCollectionService collections;
    @BeforeAll void start(){s.start();collections=f.context.getBean(GroupCollectionService.class);}
    @AfterAll void stop(){s.stop();}
    @BeforeEach void setup(TestInfo test){s.s.s.firstPaymentDate=test.getTags().contains("due-interest")?LoanRecordingPostgresTest.TODAY.plusDays(1):java.time.LocalDate.of(2026,11,8);s.setup();f.service.disburse(f.actor,s.funding());}
    UUID cycle(){return s.s.cycle;}
    UUID second(){return s.s.s.g.second;}
    GroupCollectionDtos.Row row(UUID payer,String amount){var r=new GroupCollectionDtos.Row();r.setLoanId(s.loan);r.setPayerId(payer);r.setAmount(new BigDecimal(amount));r.setDescription("Synthetic verified contributor cash");return r;}
    GroupCollectionDtos.Form form(){var x=new GroupCollectionDtos.Form();x.setEffectiveDate(LoanRecordingPostgresTest.TODAY);x.setMoneyAccountId(f.cash);x.setReference("QA-SHARED-GROUP-"+UUID.randomUUID());x.setRemitter("Synthetic shared group treasurer");x.setEvidence("Synthetic verified cash evidence");x.setRows(new ArrayList<>(List.of(row(f.client,"30.00"),row(second(),"90.00"))));return x;}
    GroupCollectionDtos.Form preview(){var x=form();x.setPreviewHash(collections.preview(f.actor,cycle(),x).hash());return x;}
    int journals(){return s.count("gl_journal");}
    @Test void previewConservesActualPayersAndSignedBeneficiariesWithoutPosting(){
        var x=form();var p=collections.preview(f.actor,cycle(),x);assertThat(journals()).isEqualTo(1);assertThat(p.loans()).hasSize(1);
        assertThat(p.loans().getFirst().beneficiary().clientId()).isNotEqualTo(f.client).isNotEqualTo(second());
        assertThat(p.contributions().stream().filter(r->r.sourceLineNo()==1).map(ReceiptContribution::amount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("30.00");
        assertThat(p.contributions().stream().filter(r->r.sourceLineNo()==2).map(ReceiptContribution::amount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("90.00");
        assertThat(p.contributions()).anySatisfy(r->{assertThat(r.payerId()).isEqualTo(f.client);assertThat(r.beneficiaryId()).isEqualTo(second());});
        x.setPreviewHash(p.hash());var saved=collections.post(f.actor,cycle(),x);assertThat(saved.rows()).hasSize(1);assertThat(saved.header().total()).isEqualByComparingTo("120.00");assertThat(journals()).isEqualTo(2);
        assertThat(collections.find(f.operator("B2"),saved.header().id())).isEmpty();
        assertThat(saved.group().contributions()).isEqualTo(p.contributions());assertThat(collections.post(f.actor,cycle(),x).header().id()).isEqualTo(saved.header().id());assertThat(journals()).isEqualTo(2);
        assertThat(f.jdbc.queryForObject("select count(*) from lending_group_shared_movement where posting_id=?",Integer.class,saved.rows().getFirst().postingId())).isEqualTo(2);
        assertThat(f.receiptService().list(f.actor,new Filter("",null,null,"","newest",0,25)).rows()).extracting(Voucher::id).contains(saved.header().id());
    }
    @Test void oneMemberCanCoverBothAllocationsWithoutCreatingAnotherLoanOrJournal(){
        var x=form();x.setRows(new ArrayList<>(List.of(row(f.client,"120.00"))));x.setPreviewHash(collections.preview(f.actor,cycle(),x).hash());var saved=collections.post(f.actor,cycle(),x);
        assertThat(saved.group().contributions()).hasSize(2).allSatisfy(r->assertThat(r.payerId()).isEqualTo(f.client));
        assertThat(s.count("loan_applications")).isEqualTo(1);assertThat(s.count("loan_ledgers")).isEqualTo(1);assertThat(journals()).isEqualTo(2);
        assertThat(saved.group().contributions().stream().map(ReceiptContribution::principal).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(saved.rows().getFirst().principal());
    }
    @Test void fiftyInputContributionsRetainMoreThanFiftyDerivedEdges(){
        var x=form();x.setRows(new ArrayList<>());for(int i=0;i<50;i++)x.getRows().add(row(i%2==0?f.client:second(),"2.40"));
        var p=collections.preview(f.actor,cycle(),x);assertThat(p.contributions()).hasSize(51);x.setPreviewHash(p.hash());var saved=collections.post(f.actor,cycle(),x);
        assertThat(collections.find(f.actor,saved.header().id()).orElseThrow().group().contributions()).hasSize(51);
        var correction=collections.reverse(f.operator("B1"),saved.header().id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic whole fifty-contributor correction");
        assertThat(correction.group().contributions()).hasSize(51).allSatisfy(r->assertThat(r.reversesId()).isNotNull());assertThat(journals()).isEqualTo(3);
    }
    @Test void correctionKeepsEverySourceAndBenefitAndRestoresNonCashExclusions(){
        var x=preview();var saved=collections.post(f.actor,cycle(),x);assertThat(s.sum("excluded_interest")).isPositive();
        assertThatThrownBy(()->collections.reverse(f.actor,saved.header().id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Self correction")).hasMessage("group.error.correction");
        assertThatThrownBy(()->f.service.reverse(f.operator("B1"),saved.rows().getFirst().postingId(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Partial envelope correction")).hasMessage("recording.error.receiptCorrection");
        assertThatThrownBy(()->f.jdbc.update("update lending_group set name='Renamed synthetic shared group' where id=(select group_id from lending_group_cycle where id=?)",cycle())).hasMessageContaining("Group identity is immutable");
        var key=UUID.randomUUID();var corrector=f.operator("B1");var corrected=collections.reverse(corrector,saved.header().id(),key,LoanRecordingPostgresTest.TODAY,"Synthetic independently returned contribution cash");
        for(int i=0;i<saved.group().contributions().size();i++){var before=saved.group().contributions().get(i);var after=corrected.group().contributions().get(i);assertThat(after.reversesId()).isEqualTo(before.id());assertThat(after.sourceLineNo()).isEqualTo(before.sourceLineNo());assertThat(after.amount()).isEqualByComparingTo(before.amount());assertThat(after.payerId()).isEqualTo(before.payerId());assertThat(after.beneficiaryId()).isEqualTo(before.beneficiaryId());}
        assertThat(collections.reverse(corrector,saved.header().id(),key,LoanRecordingPostgresTest.TODAY,"Synthetic independently returned contribution cash").header().id()).isEqualTo(corrected.header().id());
        assertThat(s.shared.balances(f.actor,s.loan)).allSatisfy(b->{assertThat(b.remainingPrincipal()).isEqualByComparingTo(b.principal());assertThat(b.remainingInterest()).isEqualByComparingTo(b.interest());});assertThat(journals()).isEqualTo(3);
    }
    @Test void changedBalancesOrPayerCommandsRequireAnotherPreview(){
        var x=preview();f.service.repay(f.actor,s.payment("1.00"));assertThatThrownBy(()->collections.post(f.actor,cycle(),x)).hasMessage("group.error.previewChanged");assertThat(s.count("lending_group_receipt")).isZero();assertThat(journals()).isEqualTo(2);
        var changed=preview();changed.getRows().getFirst().setPayerId(second());assertThatThrownBy(()->collections.post(f.actor,cycle(),changed)).hasMessage("group.error.previewChanged");
    }
    @Test void missingMemberForeignLoanBranchAndRevokedClaimsCannotPost(){
        var x=form();x.getRows().getFirst().setPayerId(f.actor.getMemberId());assertThatThrownBy(()->collections.preview(f.actor,cycle(),x)).hasMessage("group.error.collectionPayer");
        x.getRows().getFirst().setPayerId(f.client);x.getRows().getFirst().setLoanId(UUID.randomUUID());assertThatThrownBy(()->collections.preview(f.actor,cycle(),x)).hasMessage("group.error.collectionLoan");
        assertThatThrownBy(()->collections.preview(f.operator("B2"),cycle(),form())).isInstanceOf(AccessDeniedException.class);
        var accepted=preview();var claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.GROUP_LENDING_POST);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(claims);
        assertThatThrownBy(()->collections.post(f.actor,cycle(),accepted)).isInstanceOf(AccessDeniedException.class);assertThat(journals()).isEqualTo(1);
    }
    @Test void auditFailureRollsBackParentCashEveryBenefitAndTheEnvelope(){
        var x=preview();doThrow(new IllegalStateException("Synthetic shared collection audit failure")).when(f.context.getBean(AuditService.class)).logEvent(eq("GROUP_LENDING"),any(),eq("GROUP_COLLECTION_POSTED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->collections.post(f.actor,cycle(),x)).hasMessage("Synthetic shared collection audit failure");assertThat(journals()).isEqualTo(1);assertThat(s.count("lending_group_receipt")).isZero();assertThat(s.count("lending_group_shared_movement")).isZero();
    }
    @Test void concurrentRetriesPostOneParentPaymentAndOneReceipt()throws Exception{
        var x=preview();var gate=new CountDownLatch(1);try(var workers=Executors.newFixedThreadPool(2)){var a=workers.submit(()->{gate.await();return collections.post(f.actor,cycle(),x).header().id();});var b=workers.submit(()->{gate.await();return collections.post(f.actor,cycle(),x).header().id();});gate.countDown();assertThat(a.get(60,TimeUnit.SECONDS)).isEqualTo(b.get(60,TimeUnit.SECONDS));}assertThat(journals()).isEqualTo(2);assertThat(s.count("lending_group_receipt")).isEqualTo(1);
    }
    @Test void earlySettlementKeepsFutureInterestOutOfContributions(){
        var x=form();x.setRows(new ArrayList<>(List.of(row(f.client,"1000.01"))));x.setPreviewHash(collections.preview(f.actor,cycle(),x).hash());var saved=collections.post(f.actor,cycle(),x);
        assertThat(saved.group().contributions().stream().map(ReceiptContribution::interest).reduce(BigDecimal.ZERO,BigDecimal::add)).isZero();assertThat(s.sum("excluded_interest")).isEqualByComparingTo("20.00");assertThat(saved.header().total()).isEqualByComparingTo("1000.01");
        assertThat(s.shared.balances(f.actor,s.loan)).allSatisfy(b->{assertThat(b.remainingPrincipal()).isZero();assertThat(b.remainingInterest()).isZero();});
    }
    void forgedEnvelope(java.util.function.Consumer<List<ReceiptContribution>> change,boolean changeSource){
        var x=form();var preview=collections.preview(f.actor,cycle(),x);var contributions=new ArrayList<>(preview.contributions());change.accept(contributions);
        if(changeSource)x.getRows().getFirst().setAmount(new BigDecimal("31.00"));
        var tx=new org.springframework.transaction.support.TransactionTemplate(f.context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        tx.executeWithoutResult(status->{
            var command=s.payment("120.00");command.setReference(x.getReference()+"/G1");var post=f.service.repay(f.actor,command);
            var header=new Voucher(UUID.randomUUID(),preview.number(),Type.RECEIPT,x.getEffectiveDate(),x.getRemitter(),x.getReference(),"Synthetic adversarial envelope",x.getEvidence(),f.cash,new BigDecimal("120.00"),1,null,f.actor.getMemberId(),f.actor.getFullName(),f.context.getBean(ApplicationClock.class).now(),null,null,"","",List.of());
            var line=new LoanReceiptRow(1,post.id(),s.loan,post.loanNumber(),"Synthetic payment",post.amount(),post.principal(),post.interest(),post.principalBalance(),post.moneyAccount(),post.principalAccount(),post.interestAccount(),post.reference());
            var c=collections.cycle(f.actor,cycle());var receipt=new LoanReceipt(header,"",List.of(line),new GroupReceipt(c.id(),c.groupCode(),c.groupName(),contributions));
            f.context.getBean(com.sacco.mvp.grouplending.repository.GroupCollectionRepository.class).insert(f.actor,receipt,x.getRequestKey(),"0".repeat(64),f.context.getBean(tools.jackson.databind.ObjectMapper.class).writeValueAsString(x));
            f.context.getBean(com.sacco.mvp.accounting.service.VoucherService.class).consumeNumber(f.actor,Type.RECEIPT,x.getRequestKey());
        });
    }
    ReceiptContribution amount(ReceiptContribution r,BigDecimal delta){return new ReceiptContribution(r.id(),r.lineNo(),r.loanId(),r.loanNumber(),r.payerId(),r.payerNumber(),r.payer(),r.beneficiaryId(),r.beneficiaryNumber(),r.beneficiary(),r.description(),r.amount().add(delta),r.principal().add(delta),r.interest(),r.charges(),null,r.sourceLineNo());}
    @Test void databaseRejectsChangedBeneficiarySharesEvenWhenCashAndPayerTotalsAgree(){
        assertThatThrownBy(()->forgedEnvelope(rows->{rows.set(0,amount(rows.get(0),new BigDecimal("0.01")));rows.set(1,amount(rows.get(1),new BigDecimal("-0.01")));},false)).hasStackTraceContaining("Shared contribution components must match");
        assertThat(journals()).isEqualTo(1);assertThat(s.count("lending_group_receipt")).isZero();
    }
    @Test void databaseRejectsUnconservedActualContributorInput(){
        assertThatThrownBy(()->forgedEnvelope(rows->{},true)).hasStackTraceContaining("Shared contribution attribution must conserve");assertThat(journals()).isEqualTo(1);assertThat(s.count("lending_group_shared_movement")).isZero();
    }

    @Test @Tag("due-interest") void dueInterestIsCollectedOnceAndAttributedSeparatelyFromPrincipal(){
        var date=LoanRecordingPostgresTest.TODAY.plusDays(1);when(f.context.getBean(ApplicationClock.class).today()).thenReturn(date);when(f.context.getBean(ApplicationClock.class).now()).thenReturn(date.atTime(12,0).atOffset(java.time.ZoneOffset.ofHours(3)));var x=form();x.setEffectiveDate(date);
        var preview=collections.preview(f.actor,cycle(),x);assertThat(preview.loans().getFirst().allocation().interest()).isEqualByComparingTo("20.00");x.setPreviewHash(preview.hash());var saved=collections.post(f.actor,cycle(),x);
        assertThat(saved.group().contributions().stream().map(ReceiptContribution::interest).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("20.00");
        assertThat(saved.group().contributions().stream().map(ReceiptContribution::principal).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("100.00");assertThat(journals()).isEqualTo(2);
        collections.reverse(f.operator("B1"),saved.header().id(),UUID.randomUUID(),date,"Synthetic due-interest correction");assertThat(s.shared.balances(f.actor,s.loan)).allSatisfy(b->assertThat(b.remainingInterest()).isEqualByComparingTo(b.interest()));
    }
    @Test void closedPeriodPreventsParentAndContributionPostingTogether(){
        var date=LoanRecordingPostgresTest.TODAY.plusDays(1);when(f.context.getBean(ApplicationClock.class).today()).thenReturn(date);var x=form();x.setEffectiveDate(date);x.setPreviewHash(collections.preview(f.actor,cycle(),x).hash());
        f.jdbc.update("insert into accounting_period(id,sacco_id,starts_on,ends_on,state,created_by,created_at) values(?,?,?,?,'CLOSED',?,now())",UUID.randomUUID(),f.institution,date,date,f.actor.getMemberId());
        assertThatThrownBy(()->collections.post(f.actor,cycle(),x)).hasMessage("voucher.error.period");assertThat(journals()).isEqualTo(1);assertThat(s.count("lending_group_receipt")).isZero();assertThat(s.count("lending_group_shared_movement")).isZero();
    }

}
