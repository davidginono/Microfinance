package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.repository.GroupCycleRepository;
import com.sacco.mvp.repository.*;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupGuaranteePostgresTest {
    final GroupCyclePostgresTest setup=new GroupCyclePostgresTest();
    LoanRecordingPostgresTest f;long number=500000;
    @BeforeAll void start(){setup.start();f=setup.f;}
    @AfterAll void stop(){setup.stop();}
    @BeforeEach void fixture(){setup.setup();}
    UUID loan(UUID borrower,String status,String snapshot,BigDecimal instalment){
        UUID id=UUID.randomUUID();f.jdbc.update("""
            insert into loan_applications(id,sacco_id,station_id,applicant_member_id,application_number,loan_product_setting_id,loan_type,status,amount,tenor_months,
             repayment_frequency,installment_amount,financial_snapshot,form_data,policy_snapshot,required_guarantors,version,created_at,updated_at)
            values(?,?,'B1',?,?,?,'DEVELOPMENT_LOAN',?,1000,1,'MONTHLY',?,?::jsonb,'{}','{}',2,0,now(),now())
            """,id,f.institution,borrower,++number,f.product,status,instalment,snapshot);return id;
    }
    UUID promise(UUID loan,UUID guarantor,String status){UUID id=UUID.randomUUID();f.jdbc.update("insert into guarantor_requests(id,loan_application_id,guarantor_member_id,guarantor_source,status,requested_amount,version,created_at) values(?,?,?,'LMS',?,1000,0,now())",id,loan,guarantor,status);return id;}
    UUID borrower(){UUID id=UUID.randomUUID();f.jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,is_member,password_hash,created_at) values(?,?,'B1',?,'Synthetic ordinary borrower','ACTIVE','MEMBER',true,'test-only',now())",id,f.institution,id.toString());return id;}
    void limit(int count){f.jdbc.update("insert into sacco_settings(sacco_id,board_quorum,board_size,created_at,updated_at,default_language,external_station_id,max_loan_savings_ratio,required_guarantors,guarantor_with_active_loan_allowed,guarantor_max_guaranteed_loan_amount) values(?,1,1,now(),now(),'en','B1',0,1,true,?)",f.institution,count);}
    GuarantorRequestRepository requests(){return f.context.getBean(GuarantorRequestRepository.class);}
    @Test void groupPromisesCountInOrdinaryPolicyWithoutDuplicatingTheSameLoan(){
        limit(1);var cycle=setup.submitted();var participants=setup.cycles.participants(f.actor,cycle);UUID peer=participants.stream().filter(p->p.clientId().equals(setup.second)).findFirst().orElseThrow().loanId();
        assertThat(requests().countActiveGuarantees(f.client,f.institution,"B1")).isEqualTo(1);assertThat(requests().sumActiveGuaranteedAmount(f.client,f.institution,"B1")).isEqualByComparingTo("1000.00");
        promise(peer,f.client,"APPROVED");assertThat(requests().countActiveGuarantees(f.client,f.institution,"B1")).isEqualTo(1);
        assertThat(requests().countActiveGuarantees(f.client,f.institution,"B2")).isZero();assertThat(requests().countActiveGuarantees(f.client,"FOREIGN","B1")).isZero();
        assertThat(f.context.getBean(LoanQualificationPolicyService.class).guarantorFailureReason(f.institution,f.client,new BigDecimal("100.00"))).hasValueSatisfying(reason->assertThat(reason).contains("guarantee count limit"));
    }
    @Test void everyPendingReviewAndWrittenOffPromiseRemainsReserved(){
        UUID client=borrower();for(var status:List.of("AWAITING_CHAIRPERSON","CHAIRPERSON_APPROVED","AWAITING_BOARD","WRITTEN_OFF"))promise(loan(client,status,"{\"interestAmount\":20}",new BigDecimal("1020.00")),f.client,"APPROVED");
        assertThat(requests().countActiveGuarantees(f.client,f.institution,"B1")).isEqualTo(4);assertThat(requests().sumActiveGuaranteedAmount(f.client,f.institution,"B1")).isEqualByComparingTo("4000.00");
    }
    @Test void unavailableGuaranteeTermsAndInstalmentsCannotBecomeZeroExposure(){
        promise(loan(borrower(),"AWAITING_BOARD","{\"interestAmount\":\"unknown\"}",new BigDecimal("1020.00")),f.client,"APPROVED");
        loan(f.client,"DISBURSED","{\"interestAmount\":20}",null);
        var form=setup.form();var cycle=setup.cycles.create(f.actor,setup.group,form);assertThatThrownBy(()->setup.cycles.submit(f.actor,cycle,form)).hasMessage("group.error.historyMissing");
        assertThat(f.context.getBean(GroupCycleRepository.class).exposures(f.actor,List.of(f.client)).get(f.client).missingHistory()).isEqualTo(2);
        assertThat(f.jdbc.queryForObject("select count(*) from lending_group_participant where cycle_id=?",Integer.class,cycle)).isZero();
    }

    @Test void aPendingTopupDoesNotReleaseAnExistingPromise(){
        UUID source=f.topUpSource();promise(source,setup.second,"APPROVED");assertThat(requests().countActiveGuarantees(setup.second,f.institution,"B1")).isEqualTo(1);
        f.recordTopUp(source);assertThat(requests().countActiveGuarantees(setup.second,f.institution,"B1")).isEqualTo(1);
        assertThat(f.service.detail(f.actor,source).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
    }

    @Test void capacityLocksRequireTheCurrentInstitutionAndBranch(){
        var tx=new org.springframework.transaction.support.TransactionTemplate(f.context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        Boolean foreign=tx.execute(status->requests().lockGuarantorCapacity(f.client,"FOREIGN","B1"));Boolean branch=tx.execute(status->requests().lockGuarantorCapacity(f.client,f.institution,"B2"));Boolean own=tx.execute(status->requests().lockGuarantorCapacity(f.client,f.institution,"B1"));
        assertThat(foreign).isFalse();assertThat(branch).isFalse();assertThat(own).isTrue();
    }
}
