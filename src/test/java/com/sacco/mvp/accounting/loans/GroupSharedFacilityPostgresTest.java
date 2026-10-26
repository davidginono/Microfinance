package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupSharedFacilityPostgresTest {
    final GroupCyclePostgresTest g=new GroupCyclePostgresTest();
    LocalDate firstPaymentDate=LocalDate.of(2026,11,8);
    @BeforeAll void start(){g.start(GroupCyclePostgresTest.Config.class);}
    @AfterAll void stop(){g.stop();}
    @BeforeEach void setup(){g.setup();g.f.jdbc.update("update loan_product_settings set guarantors_required=0 where id=?",g.f.product);rule(SharedLiability.PROPORTIONAL);}
    void rule(SharedLiability liability){var p=g.policy();p.setModel(Model.SHARED);p.setSharedLiability(liability);g.policy=g.cycles.configure(g.f.actor,p);}
    CycleForm form(){var f=g.form();f.setApplicationDate(LoanRecordingPostgresTest.TODAY);f.setFirstPaymentDate(firstPaymentDate);f.getParticipants().getFirst().setPrincipal(new BigDecimal("123.45"));f.getParticipants().getLast().setPrincipal(new BigDecimal("876.56"));return f;}
    UUID submit(){var f=form();UUID id=g.cycles.create(g.f.actor,g.group,f);g.cycles.submit(g.f.actor,id,f);return id;}
    int count(String table){return g.f.jdbc.queryForObject("select count(*) from "+table+" where sacco_id=?",Integer.class,g.f.institution);}
    @Test void oneJointBorrowerOwnsTheParentWhileAllocationsConserveEveryComponent(){
        UUID id=submit();var shared=g.cycles.sharedFacility(g.f.actor,id).orElseThrow();
        assertThat(count("loan_applications")).isEqualTo(1);assertThat(count("loan_ledgers")).isZero();assertThat(count("gl_journal")).isZero();
        assertThat(g.f.jdbc.queryForObject("select applicant_member_id is null from loan_applications where id=?",Boolean.class,shared.loanId())).isTrue();
        assertThat(g.f.jdbc.queryForObject("select p.kind from loan_applications l join loan_borrower_party p on p.id=l.borrower_party_id where l.id=?",String.class,shared.loanId())).isEqualTo("JOINT_GROUP");
        assertThat(shared.quote().principal()).isEqualByComparingTo("1000.01");
        assertThat(g.cycles.participants(g.f.actor,id)).hasSize(2).allMatch(p->p.loanId().equals(shared.loanId()));
        assertThat(g.f.jdbc.queryForObject("select count(*) from lending_group_guarantee where cycle_id=?",Integer.class,id)).isZero();
        assertThat(g.f.jdbc.queryForObject("select sum(principal) from lending_group_shared_allocation where cycle_id=?",BigDecimal.class,id)).isEqualByComparingTo(shared.quote().principal());
        assertThat(shared.quote().dueShares().stream().map(SharedDue::principal).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(shared.quote().principal());
        assertThat(shared.quote().dueShares()).allMatch(d->d.date().equals(LocalDate.of(2026,11,8)));
    }
    @Test void proportionateAndWholeLoanLiabilitiesAreExplicitAndDoNotCreateGuarantees(){
        rule(SharedLiability.JOINT_AND_SEVERAL);UUID id=submit();var shared=g.cycles.sharedFacility(g.f.actor,id).orElseThrow();
        for(var client:List.of(g.f.client,g.second)){
            var packet=g.consents.packet(g.client(client),id);assertThat(packet.shared().liability()).isEqualTo(SharedLiability.JOINT_AND_SEVERAL);
            assertThat(packet.shared().cap()).isEqualByComparingTo(shared.quote().totalPayable());assertThat(packet.terms().principal()).isEqualByComparingTo("1000.01");assertThat(packet.guarantees()).isEmpty();
        }
    }
    @Test void personalSigningDoesNotRequireOrGrantGuarantorPermission(){
        UUID id=submit();var a=g.client(g.f.client);var claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.GUARANTOR_REQUESTS_APPROVE);
        when(g.f.context.getBean(UserClaimService.class).effectiveClaims(eq(g.f.client),anyCollection(),anyBoolean())).thenReturn(claims);
        assertThat(g.consents.accept(a,id,true,"")).isNull();assertThat(g.consents.packet(a,id).accepted()).isTrue();
        assertThat(g.consents.packet(a,id).shared().cap()).isEqualByComparingTo(g.consents.packet(a,id).shared().allocation().principal().add(g.consents.packet(a,id).shared().allocation().interest()));
        assertThat(g.f.jdbc.queryForObject("select count(*) from lending_group_guarantee where cycle_id=?",Integer.class,id)).isZero();
    }
    @Test void retryReturnsTheSameParentAndConcurrentSubmissionsCannotDuplicateIt()throws Exception{
        var f=form();UUID id=g.cycles.create(g.f.actor,g.group,f);
        try(var workers=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);var work=new ArrayList<Future<?>>();for(int n=0;n<2;n++)work.add(workers.submit(()->{start.await();g.cycles.submit(g.f.actor,id,f);return null;}));start.countDown();for(var result:work)result.get(45,TimeUnit.SECONDS);}
        g.cycles.submit(g.f.actor,id,f);assertThat(count("loan_applications")).isEqualTo(1);assertThat(g.cycles.participants(g.f.actor,id)).hasSize(2);
    }
    @Test void refusalClosesTheOneUnfundedParentAndRetainsPriorSignatures(){
        UUID id=submit();var first=g.client(g.f.client);assertThat(g.consents.accept(first,id,true,"")).isNull();
        g.consents.refuse(g.client(g.second),id,"Synthetic joint borrower declined",true);
        assertThat(g.cycles.detail(g.f.actor,id).state()).isEqualTo("CANCELLED");assertThat(g.cycles.sharedFacility(g.f.actor,id).orElseThrow().status()).isEqualTo("WITHDRAWN");
        assertThat(g.consents.packet(first,id).accepted()).isTrue();assertThat(count("loan_ledgers")).isZero();
    }
    @Test void allFrozenSharedEvidenceIsAppendOnlyAndLateAllocationsCannotBeAdded(){
        UUID id=submit();assertThatThrownBy(()->g.f.jdbc.update("update lending_group_shared_allocation set principal=principal+1 where cycle_id=?",id)).hasMessageContaining("append-only");
        assertThatThrownBy(()->g.f.jdbc.update("delete from lending_group_shared_facility where cycle_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(g.cycles.sharedFacility(g.f.actor,id).orElseThrow().quote().principal()).isEqualByComparingTo("1000.01");
    }
    @Test void auditFailureRollsBackTheWholeSharedAggregate(){
        var f=form();UUID id=g.cycles.create(g.f.actor,g.group,f);
        doThrow(new IllegalStateException("Synthetic shared audit failure")).when(g.f.context.getBean(AuditService.class)).logEvent(eq("GROUP_LENDING"),eq(id),eq("SHARED_LOAN_SUBMITTED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->g.cycles.submit(g.f.actor,id,f)).hasMessage("Synthetic shared audit failure");assertThat(count("loan_applications")).isZero();assertThat(g.cycles.participants(g.f.actor,id)).isEmpty();assertThat(g.cycles.detail(g.f.actor,id).state()).isEqualTo("DRAFT");
    }
    @Test void scopeAndNewlyRevokedSubmissionPermissionAreRechecked(){
        var f=form();UUID id=g.cycles.create(g.f.actor,g.group,f);var claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.GROUP_LENDING_SUBMIT);
        when(g.f.context.getBean(UserClaimService.class).effectiveClaims(eq(g.f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(claims);
        assertThatThrownBy(()->g.cycles.submit(g.f.actor,id,f)).isInstanceOf(AccessDeniedException.class);assertThat(count("loan_applications")).isZero();
    }
    @Test void standaloneJointApplicationCannotCommitWithoutItsFrozenSharedSource(){
        UUID id=submit();UUID party=g.f.jdbc.queryForObject("select borrower_party_id from loan_applications where id=?",UUID.class,g.cycles.sharedFacility(g.f.actor,id).orElseThrow().loanId());
        var f=g.f.form();f.setClientId(null);assertThatThrownBy(()->g.f.service.saveShared(g.f.actor,f,party)).isInstanceOf(RuntimeException.class);assertThat(count("loan_applications")).isEqualTo(1);
    }
    @Test void malformedDueDatesCannotCommitEvenWhenMoneyTotalsMatch(){
        assertThatThrownBy(()->forgedSource(true,false)).isInstanceOf(RuntimeException.class).hasStackTraceContaining("Every shared due component must conserve its parent and personal totals");
        assertThat(count("loan_applications")).isZero();
    }
    @Test void changedCashFlowInTheFrozenFormCannotDisagreeWithItsRetainedAssessment(){
        assertThatThrownBy(()->forgedSource(false,true)).isInstanceOf(RuntimeException.class).hasStackTraceContaining("Shared form, contractual shares and stored allocation must agree");
        assertThat(count("loan_applications")).isZero();
    }
    void forgedSource(boolean changeDate,boolean changeIncome){
        var form=form();UUID id=g.cycles.create(g.f.actor,g.group,form);var original=g.cycles.previewShared(g.f.actor,id,form);var dues=new ArrayList<>(original.dueShares());
        if(changeDate){var d=dues.getFirst();dues.set(0,new SharedDue(d.clientId(),d.number(),d.date().plusDays(1),d.principal(),d.interest()));}
        var quote=new SharedQuote(original.principal(),original.interest(),original.charges(),original.deductions(),original.net(),original.totalPayable(),original.monthlyPayment(),original.frequency(),original.shares(),original.schedule(),dues);
        new org.springframework.transaction.support.TransactionTemplate(g.f.context.getBean(org.springframework.transaction.PlatformTransactionManager.class)).executeWithoutResult(tx->{
            var repo=g.f.context.getBean(com.sacco.mvp.grouplending.repository.GroupCycleRepository.class);var c=repo.cycle(g.f.actor,id,true);
            var command=g.f.form();command.setClientId(null);command.setPrincipal(quote.principal());command.setApplicationDate(form.getApplicationDate());command.setFirstPaymentDate(form.getFirstPaymentDate());
            UUID loan=g.f.service.saveShared(g.f.actor,command,repo.sharedParty(g.f.actor,g.group));
            repo.sharedParticipants(g.f.actor,c,form,quote,repo.memberships(g.f.actor,g.group,form.getParticipants().stream().map(ParticipantForm::getClientId).toList()));
            repo.sharedFacility(g.f.actor,c,loan,SharedLiability.PROPORTIONAL,quote,g.f.context.getBean(ApplicationClock.class).now());
            if(changeIncome)form.getParticipants().getFirst().setIncome(form.getParticipants().getFirst().getIncome().add(BigDecimal.ONE));
            repo.save(c,form,true,g.f.context.getBean(ApplicationClock.class).now());
        });
    }
}
