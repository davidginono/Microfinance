package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.TerminationForm;
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
    void signed(UUID id){GroupPaperFixture.record(f,id,"TERMS",f.client);GroupPaperFixture.record(f,id,"TERMS",setup.second);}

    @Test void draftWithdrawalHasNoFabricatedLoansAndRetriesMustMatch(){
        var id=setup.cycles.create(f.actor,setup.group,setup.form());var command=command(id);setup.cycles.withdraw(f.actor,id,command);
        assertThat(setup.cycles.participants(f.actor,id)).isEmpty();command.setReason("Changed reason");assertThatThrownBy(()->setup.cycles.withdraw(f.actor,id,command)).hasMessage("group.error.changed");
        assertThatThrownBy(()->setup.cycles.detail(f.operator("B2"),id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
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
