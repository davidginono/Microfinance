package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.GroupForm;
import com.sacco.mvp.repository.LoanApplicationRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BorrowerIdentityPostgresTest {
    final GroupCyclePostgresTest setup=new GroupCyclePostgresTest();LoanRecordingPostgresTest f;
    @BeforeAll void start(){setup.start();f=setup.f;}
    @AfterAll void stop(){setup.stop();}
    @BeforeEach void fixture(){setup.setup();}
    UUID identity(UUID loan){return f.jdbc.queryForObject("select borrower_party_id from loan_applications where id=?",UUID.class,loan);}
    void rejected(String sql,String reason,Object... args){assertThatThrownBy(()->f.jdbc.update(sql,args)).isInstanceOf(DataAccessException.class).satisfies(failure->assertThat(((DataAccessException)failure).getMostSpecificCause().getMessage()).contains(reason));}
    @Test void recordedAndGroupIndividualLoansReuseOneIdentityPerClient(){
        var ordinary=f.service.save(f.actor,f.form());var form=setup.form();var cycle=setup.cycles.create(f.actor,setup.group,form);
        assertThatThrownBy(()->setup.cycles.submit(f.actor,cycle,form)).hasMessage("group.error.sharedApplicationPending");
        f.service.disburse(f.actor,f.post(ordinary,"1000.00","D-IDENTITY-REUSE"));
        f.service.repay(f.actor,f.post(ordinary,"1020.00","R-IDENTITY-REUSE"));
        assertThat(f.service.detail(f.actor,ordinary).loan().status()).isEqualTo("PAID");
        setup.cycles.submit(f.actor,cycle,form);
        assertThat(identity(ordinary)).isEqualTo(f.client);assertThat(f.context.getBean(LoanApplicationRepository.class).findById(ordinary).orElseThrow().getBorrowerPartyId()).isEqualTo(f.client);
        for(var participant:setup.cycles.participants(f.actor,cycle))assertThat(identity(participant.loanId())).isEqualTo(participant.clientId());
        assertThat(f.jdbc.queryForObject("select count(*) from loan_borrower_party where sacco_id=?",Integer.class,f.institution)).isEqualTo(2);
        assertThat(f.jdbc.queryForObject("select count(*) from loan_borrower_party where id=?",Integer.class,f.actor.getMemberId())).isZero();
        assertThat(f.service.detail(f.actor,ordinary).loan().clientId()).isEqualTo(f.client);
    }
    @Test void existingPaymentAndIndependentCorrectionRetainTheSameBorrower(){
        UUID loan=f.disbursed("D-IDENTITY");var paid=f.service.repay(f.actor,f.post(loan,"120.00","IDENTITY-PAYMENT"));f.service.reverse(f.operator("B1"),paid.id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic borrower identity correction");
        assertThat(identity(loan)).isEqualTo(f.client);assertThat(f.service.detail(f.actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
        assertThat(f.jdbc.queryForObject("select applicant_member_id from loan_ledgers where loan_application_id=?",UUID.class,loan)).isEqualTo(f.client);
        assertThat(f.jdbc.queryForObject("select count(*) from loan_repayment_transactions where loan_application_id=?",Integer.class,loan)).isEqualTo(2);
    }
    @Test void applicationAndLedgerCannotBeReassignedToAnotherBorrower(){
        UUID loan=f.disbursed("D-LOCKED-BORROWER");
        rejected("update loan_applications set borrower_party_id=? where id=?","Loan borrower identity is immutable",setup.second,loan);
        rejected("update loan_applications set applicant_member_id=? where id=?","Recorded loan contract is immutable",setup.second,loan);
        rejected("update loan_ledgers set applicant_member_id=? where loan_application_id=?","Ledger borrower identity is inherited",setup.second,loan);
        rejected("update loan_ledgers set station_id='B2' where loan_application_id=?","Ledger borrower identity is inherited",loan);
        assertThat(identity(loan)).isEqualTo(f.client);
        assertThatThrownBy(()->f.jdbc.update("delete from loan_borrower_party where id=?",f.client)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->f.jdbc.update("update loan_borrower_party set client_id=? where id=?",setup.second,f.client)).isInstanceOf(DataAccessException.class);
    }
    @Test void aClientMovingBranchesDoesNotMoveTheLoanOrItsFinancialDirectory(){
        UUID loan=f.disbursed("D-BORROWER-BRANCH");f.jdbc.update("update members set station_id='B2' where id=?",f.client);
        assertThat(f.service.detail(f.actor,loan).loan().clientId()).isEqualTo(f.client);
        assertThatThrownBy(()->f.service.detail(f.operator("B2"),loan)).isInstanceOf(AccessDeniedException.class);
        assertThat(identity(loan)).isEqualTo(f.client);
        assertThatThrownBy(()->f.jdbc.update("update members set sacco_id=? where id=?",f.institution+"-FOREIGN",f.client)).isInstanceOf(DataAccessException.class);
    }
    @Test void organizationIdentityIsAGroupAndCannotMasqueradeAsAPersonLoan(){
        var form=new GroupForm();form.setLegalForm("ORGANIZATION");form.setName("Synthetic organization borrower");form.setEvidence("Synthetic identity record, not lending authority");var group=setup.groups.create(f.actor,form);var party=UUID.randomUUID();
        f.jdbc.update("insert into loan_borrower_party(id,sacco_id,kind,group_id,group_station_id) values(?,?,'ORGANIZATION',?,'B1')",party,f.institution,group);
        var row=f.jdbc.queryForMap("select kind,client_id,group_id,full_name from loan_borrower_directory where party_id=?",party);
        assertThat(row).containsEntry("kind","ORGANIZATION").containsEntry("client_id",null).containsEntry("group_id",group).containsEntry("full_name",form.getName());
        UUID source=f.service.save(f.actor,f.form());
        rejected("""
            insert into loan_applications(id,sacco_id,station_id,borrower_party_id,applicant_member_id,application_number,loan_type,status,amount,tenor_months,
            form_data,required_guarantors,policy_snapshot,version,created_at,updated_at)
            values(?,?,'B1',?,?,900001,'DEVELOPMENT_LOAN','DRAFT',1000,1,'{}',0,'{}',0,now(),now())
            ""","Unsupported loan borrower or origination path",UUID.randomUUID(),f.institution,party,f.client);
        assertThat(identity(source)).isEqualTo(f.client);
        assertThatThrownBy(()->f.jdbc.update("insert into loan_borrower_party(id,sacco_id,kind,group_id,group_station_id) values(?,?,'JOINT_GROUP',?,'B2')",UUID.randomUUID(),f.institution,group)).isInstanceOf(DataAccessException.class);
    }
    @Test void concurrentOrdinaryApplicationsDoNotCreateDuplicateClientIdentities()throws Exception{
        var one=f.form();var two=f.form();var start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)){
            var first=workers.submit(()->{start.await();return f.service.save(f.actor,one);});var second=workers.submit(()->{start.await();return f.service.save(f.actor,two);});start.countDown();
            var loans=List.of(first.get(60,TimeUnit.SECONDS),second.get(60,TimeUnit.SECONDS));assertThat(loans).doesNotHaveDuplicates();for(var loan:loans)assertThat(identity(loan)).isEqualTo(f.client);
        }
        assertThat(f.jdbc.queryForObject("select count(*) from loan_borrower_party where client_id=?",Integer.class,f.client)).isEqualTo(1);
    }
}
