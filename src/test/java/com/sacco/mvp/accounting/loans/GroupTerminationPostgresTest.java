package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.TerminationForm;
import com.sacco.mvp.grouplending.repository.GroupCycleRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupTerminationPostgresTest {
    final GroupCyclePostgresTest setup=new GroupCyclePostgresTest();LoanRecordingPostgresTest f;
    @BeforeAll void start(){setup.start();f=setup.f;}
    @AfterAll void stop(){setup.stop();}
    @BeforeEach void fixture(){setup.setup();}
    TerminationForm command(UUID id){var form=new TerminationForm();form.setExpectedVersion(setup.cycles.detail(f.actor,id).version());form.setReason("Synthetic unfunded withdrawal request");return form;}
    com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.PostForm funding(UUID loan,String reference){var post=f.post(loan,"1000.00",reference);post.setEffectiveDate(java.time.LocalDate.of(2026,9,1));return post;}
    void signed(UUID id){setup.consents.accept(setup.client(f.client),id,true,"");setup.consents.accept(setup.client(setup.second),id,true,"");}
    @Test void withdrawalPreservesSignedEvidenceAndReleasesOnlyUnfundedCommitments(){
        var id=setup.submitted();setup.consents.accept(setup.client(f.client),id,true,"");var c=command(id);var original=setup.cycles.detail(f.actor,id).form();
        setup.cycles.withdraw(f.actor,id,c);setup.cycles.withdraw(f.actor,id,c);
        assertThat(setup.cycles.detail(f.actor,id).state()).isEqualTo("CANCELLED");assertThat(setup.cycles.detail(f.actor,id).form()).isEqualTo(original);
        assertThat(setup.cycles.participants(f.actor,id)).hasSize(2).allSatisfy(p->assertThat(p.loanStatus()).isEqualTo("WITHDRAWN"));
        assertThat(f.jdbc.queryForObject("select count(*) from lending_group_consent_evidence where cycle_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("select count(*) from lending_group_guarantee where cycle_id=?",Integer.class,id)).isEqualTo(2);
        assertThat(f.context.getBean(GuarantorRequestRepository.class).countActiveGuarantees(f.client,f.institution,"B1")).isZero();
        assertThat(f.context.getBean(GroupCycleRepository.class).exposures(f.actor,List.of(f.client)).get(f.client).activeCycles()).isZero();
        assertThat(setup.consents.list(setup.client(f.client),0).rows()).singleElement().satisfies(p->assertThat(p.state()).isEqualTo("CANCELLED"));
        assertThat(setup.consents.packet(setup.client(f.client),id).accepted()).isTrue();assertThat(setup.consents.accept(setup.client(setup.second),id,true,"")).isEqualTo("group.error.endedCycle");
        UUID loan=setup.cycles.participants(f.actor,id).getFirst().loanId();assertThatThrownBy(()->f.jdbc.update("update loan_applications set status='READY_FOR_DISBURSEMENT' where id=?",loan)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->f.jdbc.update("delete from lending_group_termination where cycle_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(f.jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,f.institution)).isZero();
        var next=setup.submitted();assertThat(next).isNotEqualTo(id);
    }
    @Test void draftWithdrawalHasNoFabricatedLoansAndRetriesMustMatch(){
        var id=setup.cycles.create(f.actor,setup.group,setup.form());var command=command(id);setup.cycles.withdraw(f.actor,id,command);
        assertThat(setup.cycles.participants(f.actor,id)).isEmpty();command.setReason("Changed reason");assertThatThrownBy(()->setup.cycles.withdraw(f.actor,id,command)).hasMessage("group.error.changed");
        assertThatThrownBy(()->setup.cycles.detail(f.operator("B2"),id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void unsignedParticipantMayRefuseButCannotUndoASignature(){
        var id=setup.submitted();var client=setup.client(f.client);assertThatThrownBy(()->setup.consents.refuse(client,id,"Terms declined",false)).hasMessage("group.error.refusal");
        setup.consents.accept(client,id,true,"");assertThatThrownBy(()->setup.consents.refuse(client,id,"Terms declined",true)).hasMessage("group.error.alreadySigned");
        var other=setup.client(setup.second);setup.consents.refuse(other,id,"Terms declined",true);setup.consents.refuse(other,id,"Terms declined",true);
        assertThat(setup.consents.packet(client,id).termination().kind()).isEqualTo("REFUSED");
        assertThat(setup.cycles.participants(f.actor,id)).allSatisfy(p->assertThat(p.loanStatus()).isEqualTo("WITHDRAWN"));
        assertThatThrownBy(()->setup.consents.prepareCode(other,id)).hasMessage("group.error.endedCycle");
    }
    @Test void anyFundingHistoryPreventsWithdrawalWithoutPartialChanges(){
        var id=setup.submitted();signed(id);UUID loan=setup.cycles.participants(f.actor,id).getFirst().loanId();f.service.disburse(f.actor,funding(loan,"FUND-TERMINATION"));
        assertThatThrownBy(()->setup.cycles.withdraw(f.actor,id,command(id))).hasMessage("group.error.fundedCycle");
        assertThat(setup.cycles.termination(f.actor,id)).isEmpty();assertThat(setup.cycles.detail(f.actor,id).state()).isEqualTo("SUBMITTED");assertThat(f.service.detail(f.actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
    }
    @Test void auditFailureRollsBackTerminationAndEveryLoan(){
        var id=setup.submitted();var c=command(id);
        doThrow(new IllegalStateException("Synthetic withdrawal audit failure")).when(f.context.getBean(AuditService.class)).logEvent(eq("GROUP_LENDING"),any(),eq("GROUP_CYCLE_WITHDRAWN"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->setup.cycles.withdraw(f.actor,id,c)).hasMessage("Synthetic withdrawal audit failure");assertThat(setup.cycles.termination(f.actor,id)).isEmpty();assertThat(setup.cycles.detail(f.actor,id).state()).isEqualTo("SUBMITTED");
        assertThat(setup.cycles.participants(f.actor,id)).noneSatisfy(p->assertThat(p.loanStatus()).isEqualTo("WITHDRAWN"));
    }
    @Test void directSqlCannotPartiallyCancelACycleOrInventWithdrawalEvidence(){
        var id=setup.submitted();assertThatThrownBy(()->f.jdbc.update("update lending_group_cycle set state='CANCELLED',version=version+1 where id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        var loan=setup.cycles.participants(f.actor,id).getFirst().loanId();assertThatThrownBy(()->f.jdbc.update("update loan_applications set status='WITHDRAWN' where id=?",loan)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->f.jdbc.update("insert into lending_group_termination(cycle_id,sacco_id,station_id,kind,actor_id,reason,recorded_at) values(?,?,'B1','WITHDRAWN',?,'Incomplete direct mutation',now())",id,f.institution,f.actor.getMemberId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(setup.cycles.detail(f.actor,id).state()).isEqualTo("SUBMITTED");
    }
    @Test void directConsentWritesWaitForTerminationAndCannotSignAnEndedCycle()throws Exception{
        var id=setup.submitted();var command=command(id);var client=setup.client(f.client);var repository=f.context.getBean(GroupCycleRepository.class);
        var locked=new CountDownLatch(1);var attempted=new CountDownLatch(1);var signerPid=new java.util.concurrent.atomic.AtomicInteger();
        var transaction=new org.springframework.transaction.support.TransactionTemplate(f.context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        try(var workers=Executors.newFixedThreadPool(2)){
            var withdrawal=workers.submit(()->transaction.execute(status->{
                int makerPid=f.jdbc.queryForObject("select pg_backend_pid()",Integer.class);f.jdbc.queryForObject("select id from lending_group_cycle where id=? for update",UUID.class,id);locked.countDown();
                try{assertThat(attempted.await(10,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).pollInterval(java.time.Duration.ofMillis(10)).until(()->Boolean.TRUE.equals(f.jdbc.queryForObject("select ?=any(pg_blocking_pids(?))",Boolean.class,makerPid,signerPid.get())));
                setup.cycles.withdraw(f.actor,id,command);return true;
            }));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            var signature=workers.submit(()->{try{return transaction.execute(status->{
                signerPid.set(f.jdbc.queryForObject("select pg_backend_pid()",Integer.class));attempted.countDown();
                repository.consent(client,id,setup.policy,"Synthetic personal signature",null,f.context.getBean(ApplicationClock.class).now());return true;
            });}catch(org.springframework.dao.DataAccessException expected){return false;}});
            assertThat(withdrawal.get(60,TimeUnit.SECONDS)).isTrue();assertThat(signature.get(60,TimeUnit.SECONDS)).isFalse();
        }
        assertThat(setup.cycles.detail(f.actor,id).state()).isEqualTo("CANCELLED");assertThat(f.jdbc.queryForObject("select count(*) from lending_group_consent_evidence where cycle_id=?",Integer.class,id)).isZero();
    }
    @Test void withdrawalAndFundingCannotBothWin()throws Exception{
        var id=setup.submitted();signed(id);var c=command(id);var loan=setup.cycles.participants(f.actor,id).getFirst().loanId();var posting=funding(loan,"RACE-TERMINATION");var start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)){
            var withdrawal=workers.submit(()->{start.await();try{setup.cycles.withdraw(f.actor,id,c);return true;}catch(IllegalArgumentException expected){return false;}});
            var funding=workers.submit(()->{start.await();try{f.service.disburse(f.actor,posting);return true;}catch(IllegalArgumentException|org.springframework.dao.DataAccessException expected){return false;}});
            start.countDown();assertThat(List.of(withdrawal.get(60,TimeUnit.SECONDS),funding.get(60,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        boolean closed=setup.cycles.detail(f.actor,id).state().equals("CANCELLED");assertThat(f.jdbc.queryForObject("select count(*) from loan_ledgers where sacco_id=?",Integer.class,f.institution)).isEqualTo(closed?0:1);
    }
}
