package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BorrowerNamesPostgresTest {
    final GroupCyclePostgresTest setup=new GroupCyclePostgresTest();LoanRecordingPostgresTest f;LoanPresentationService names;
    @BeforeAll void start(){setup.start(Config.class);f=setup.f;names=f.context.getBean(LoanPresentationService.class);}
    @AfterAll void stop(){setup.stop();}
    @BeforeEach void fixture(){setup.setup();}
    @Test void nativeNamesAreLoanKeyedAndNeverIncludeForeignBranchesOrInstitutions(){
        var own=f.service.save(f.actor,f.form());f.jdbc.update("update members set station_id='B2' where id=?",setup.second);var other=f.form();other.setClientId(setup.second);var otherBranch=f.service.save(f.operator("B2"),other);
        var tenant="FOREIGN-"+UUID.randomUUID();var foreignClient=UUID.randomUUID();var foreignLoan=UUID.randomUUID();
        f.jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,'Synthetic foreign institution',true,now(),now())",tenant);
        f.jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,access_status,created_at,updated_at) values(?,?,'B1',true,'ACTIVE',now(),now())",UUID.randomUUID(),tenant);
        f.jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,is_member,password_hash,created_at) values(?,?,'B1',?,'Foreign synthetic client','ACTIVE','MEMBER',true,'test-only',now())",foreignClient,tenant,foreignClient.toString());
        f.jdbc.update("""
            insert into loan_applications(id,sacco_id,station_id,applicant_member_id,application_number,loan_type,status,amount,tenor_months,form_data,required_guarantors,policy_snapshot,version,created_at,updated_at)
            values(?,?,'B1',?,902001,'DEVELOPMENT_LOAN','DRAFT',1000,1,'{}',0,'{}',0,now(),now())
            """,foreignLoan,tenant,foreignClient);
        assertThat(names.borrowerNames(f.actor,UserClaim.MANAGER_QUEUE_VIEW,List.of(own,otherBranch,foreignLoan,UUID.randomUUID()))).containsExactly(Map.entry(own,"Synthetic client"));
        assertThatThrownBy(()->names.borrowerProfile(f.actor,UserClaim.MANAGER_QUEUE_VIEW,foreignLoan)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void movingTheCurrentClientBranchDoesNotMoveHistoricalLoanLabels(){
        var loan=f.service.save(f.actor,f.form());f.jdbc.update("update members set station_id='B2' where id=?",f.client);
        assertThat(names.borrowerNames(f.actor,UserClaim.ACCOUNTANT_QUEUE_VIEW,List.of(loan))).containsEntry(loan,"Synthetic client");
        assertThat(names.borrowerNames(f.operator("B2"),UserClaim.ACCOUNTANT_QUEUE_VIEW,List.of(loan))).isEmpty();
    }
    @Test void currentPermissionRevocationAndClientSessionsCannotReadStaffNames(){
        var loan=f.service.save(f.actor,f.form());assertThatThrownBy(()->names.borrowerNames(setup.client(f.client),UserClaim.MANAGER_QUEUE_VIEW,List.of(loan))).isInstanceOf(AccessDeniedException.class);
        when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.ACCOUNTANT_QUEUE_VIEW));
        assertThatThrownBy(()->names.borrowerNames(f.actor,UserClaim.MANAGER_QUEUE_VIEW,List.of(loan))).isInstanceOf(AccessDeniedException.class);
    }
    @Test void personProfileUsesTheLoanBranchAndRealClientContactWithoutRequiringCurrentClientBranch(){
        var loan=f.service.save(f.actor,f.form());f.jdbc.update("update members set station_id='B2',email='synthetic@example.invalid',phone='255700000000' where id=?",f.client);
        var profile=names.borrowerProfile(f.actor,UserClaim.MANAGER_QUEUE_VIEW,loan);
        assertThat(profile.isPerson()).isTrue();assertThat(profile.partyId()).isEqualTo(f.client);assertThat(profile.clientId()).isEqualTo(f.client);assertThat(profile.groupId()).isNull();
        assertThat(profile.email()).isEqualTo("synthetic@example.invalid");assertThat(profile.phone()).isEqualTo("255700000000");assertThat(profile.branch()).isEqualTo("B1");assertThat(profile.legalForm()).isNull();
        assertThatThrownBy(()->names.borrowerProfile(f.operator("B2"),UserClaim.ACCOUNTANT_QUEUE_VIEW,loan)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->names.borrowerProfile(f.actor,UserClaim.MANAGER_QUEUE_VIEW,UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=UserClaim.class,names={"BOARD_QUEUE_VIEW","CHAIRPERSON_QUEUE_VIEW","CREDIT_COMMITTEE_QUEUE_VIEW","LOAN_OFFICER_QUEUE_VIEW"})
    void databaseAssignmentGuardRequiresTheExactActorLoanAndStage(UserClaim claim){
        var loan=f.service.save(f.actor,f.form());String stage=claim.name().replace("_QUEUE_VIEW","");
        assertThatThrownBy(()->names.borrowerProfile(f.actor,claim,loan)).isInstanceOf(AccessDeniedException.class);
        f.jdbc.update("insert into board_reviews(id,loan_application_id,board_member_id,review_stage,decision,created_at) values(?,?,?,?, 'PENDING',now())",UUID.randomUUID(),loan,f.actor.getMemberId(),stage);
        assertThat(names.borrowerProfile(f.actor,claim,loan).clientId()).isEqualTo(f.client);
        f.jdbc.update("delete from board_reviews where loan_application_id=? and board_member_id=?",loan,f.actor.getMemberId());
        assertThatThrownBy(()->names.borrowerProfile(f.actor,claim,loan)).isInstanceOf(AccessDeniedException.class);
    }
    @Configuration(proxyBeanMethods=false) @Import(GroupCyclePostgresTest.Config.class) static class Config {
        @Bean LoanPresentationService presentation(ObjectMapper json,ManagerReviewRepository managers,BoardReviewRepository boards,MemberRepository members,LoanApplicationRepository loans,GuarantorRequestRepository guarantors,GeneralLedgerService access){
            var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");return new LoanPresentationService(json,managers,boards,members,loans,guarantors,mock(LoanAttachmentService.class),mock(LoanProductWorkflowService.class),messages,access);
        }
    }
}
