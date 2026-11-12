package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.loans.repository.LoanRecordingRepository;
import com.sacco.mvp.accounting.loans.service.LoanGroupApprovalService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoanGroupApprovalPostgresTest {
    final GroupOrganizationAgreementPostgresTest s=new GroupOrganizationAgreementPostgresTest();
    final LoanRecordingPostgresTest f=s.o.f;
    LoanGroupApprovalService service;
    AppUserPrincipal first,second,manager;
    @BeforeAll void start(){
        s.start();
        var evidence=new com.sacco.mvp.accounting.loans.service.LoanRecordingEvidenceService(new LoanRecordingRepository(f.jdbc),f.context.getBean(GeneralLedgerService.class),
            new FormSchemaService(f.context.getBean(com.sacco.mvp.repository.LoanProductSettingRepository.class),f.context.getBean(tools.jackson.databind.ObjectMapper.class)),
            new LoanProductRequiredAttachmentService(f.context.getBean(com.sacco.mvp.repository.LoanProductRequiredAttachmentRepository.class)),mock(com.sacco.mvp.clients.applications.service.ClientApplicationService.class),f.context.getBean(com.sacco.mvp.clients.onboarding.service.OnboardingService.class));
        Object recordingTarget=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(f.service);
        org.springframework.test.util.ReflectionTestUtils.setField(recordingTarget,"evidence",evidence);
        f.context.registerBean(LoanGroupApprovalService.class,()->new LoanGroupApprovalService(new LoanRecordingRepository(f.jdbc),f.context.getBean(GeneralLedgerService.class),f.context.getBean(MemberDirectoryService.class),f.context.getBean(AuditService.class),f.context.getBean(ApplicationClock.class),f.context.getBean(tools.jackson.databind.ObjectMapper.class),f.service,mock(SaccoConfigurationService.class)));
        service=f.context.getBean(LoanGroupApprovalService.class);
    }
    @AfterAll void stop(){s.stop();}
    @BeforeEach void fixture(){s.fixture();first=reviewer(Position.ACCOUNTANT);second=reviewer(Position.ACCOUNTANT);manager=reviewer(Position.MANAGER);}
    AppUserPrincipal reviewer(Position role){
        var p=f.operator("B1");var m=f.context.getBean(MemberDirectoryService.class).find(p.getMemberId()).orElseThrow();m.setPosition(role);m.setStaffRoles(Set.of(role));
        f.jdbc.update("update members set position=? where id=?",role.name(),m.getId());
        for(var claim:List.of("LOAN_RECORDING_VIEW","LOAN_RECORDING_APPROVE","LOAN_RECORDING_REJECT"))f.jdbc.update("insert into member_access_claims(member_id,claim_name) values(?,?)",m.getId(),claim);
        return new AppUserPrincipal(m,EnumSet.allOf(UserClaim.class));
    }
    void rule(int expected,int count){var r=new GroupRuleForm();r.setProductId(f.product);r.setExpectedRevision(expected);r.setGroups(List.of(Position.ACCOUNTANT,Position.MANAGER));r.setApprovals(List.of(count,1));service.saveRule(f.actor,r);}
    UUID submit(){s.sign(s.signer,s.cycle);rule(0,2);UUID key=UUID.randomUUID();service.submit(f.actor,s.loan,key);return key;}
    void decide(AppUserPrincipal p,UUID request,String decision){service.decide(p,s.loan,request,UUID.randomUUID(),decision,"Synthetic independent paper review","Synthetic paper reference");}
    @Test void staffRecordedIndividualApplicationUsesItsSignedPaperAndTheSameIndependentGroups(){
        var form=f.form();form.setApplicationDetails(new ApplicationDetails());form.getApplicationDetails().setDocumentTypes(List.of("SIGNED_APPLICATION"));form.getApplicationDetails().setDocumentNames(List.of("Synthetic signed application"));
        UUID loan=f.service.save(f.actor,form,List.of(GroupOrganizationPostgresTest.documentFile()));rule(0,2);
        UUID request=UUID.randomUUID();service.submit(f.actor,loan,request);
        for(var reviewer:List.of(first,second,manager))service.decide(reviewer,loan,request,UUID.randomUUID(),"APPROVE","Synthetic independent review","Synthetic signed application");
        assertThat(f.jdbc.queryForObject("select status from loan_applications where id=?",String.class,loan)).isEqualTo("READY_FOR_DISBURSEMENT");
        assertThat(s.count("loan_ledgers")).isZero();
        f.service.disburse(f.actor,f.post(loan,"1000.00","D-SYNTHETIC-INDIVIDUAL"));
        assertThat(s.count("loan_ledgers")).isEqualTo(1);
    }
    @Test void orderedFrozenPlanRequiresDifferentPeopleAndPostsNoMoney(){
        UUID request=submit();rule(1,1);
        assertThatThrownBy(()->decide(manager,request,"APPROVE")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->decide(f.actor,request,"APPROVE")).hasMessage("loanGroups.error.independent");
        decide(first,request,"APPROVE");assertThat(s.o.status(s.loan)).isEqualTo("SUBMITTED");
        assertThatThrownBy(()->decide(first,request,"APPROVE")).hasMessage("loanGroups.error.independent");
        decide(second,request,"APPROVE");decide(manager,request,"APPROVE");
        assertThat(s.o.status(s.loan)).isEqualTo("READY_FOR_DISBURSEMENT");assertThat(s.count("loan_ledgers")).isZero();assertThat(s.count("gl_journal")).isZero();
        assertThatThrownBy(()->f.jdbc.update("delete from loan_group_decision where request_id=?",request)).isInstanceOf(DataAccessException.class);
    }
    @Test void termFingerprintIgnoresNumericScaleAndKeepsActualValuesDistinct(){
        assertThat(f.jdbc.queryForObject("select loan_group_canonical(?::jsonb)::text=loan_group_canonical(?::jsonb)::text",Boolean.class,
            "{\"amount\":1000.00,\"fees\":[10.00,0.0500]}","{\"fees\":[10.0,0.05],\"amount\":1000}")).isTrue();
        assertThat(f.jdbc.queryForObject("select loan_group_canonical(?::jsonb)::text=loan_group_canonical(?::jsonb)::text",Boolean.class,
            "{\"amount\":1000.00,\"fees\":[10.00]}","{\"amount\":1000.00,\"fees\":[11.00]}")).isFalse();
    }
    @Test void databaseRejectsFundingAndForgedOrRevokedDecisions(){
        assertThatThrownBy(()->f.jdbc.update("update loan_applications set status='READY_FOR_DISBURSEMENT' where id=?",s.loan)).isInstanceOf(DataAccessException.class);
        UUID request=submit();
        assertThatThrownBy(()->f.jdbc.update("insert into loan_group_decision(id,request_id,step,actor_id,decision,reason,evidence,recorded_at) values(?,?,1,?,'APPROVE','Wrong order','Paper',now())",UUID.randomUUID(),request,manager.getMemberId())).isInstanceOf(DataAccessException.class);
        f.jdbc.update("delete from member_access_claims where member_id=? and claim_name='LOAN_RECORDING_APPROVE'",first.getMemberId());
        assertThatThrownBy(()->decide(first,request,"APPROVE")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->service.review(f.operator("B2"),s.loan)).isInstanceOf(AccessDeniedException.class);
        assertThat(s.o.status(s.loan)).isEqualTo("SUBMITTED");assertThat(s.count("loan_ledgers")).isZero();
    }
    @Test void missingSignedPaperCannotEnterReviewAndARejectedRoundRetainsItsDecision(){
        rule(0,2);assertThatThrownBy(()->service.submit(f.actor,s.loan,UUID.randomUUID())).hasMessage("recording.error.signedApplication");
        s.sign(s.signer,s.cycle);UUID request=UUID.randomUUID();service.submit(f.actor,s.loan,request);decide(first,request,"REJECT");
        assertThat(s.o.status(s.loan)).isEqualTo("REJECTED");UUID next=UUID.randomUUID();service.submit(f.actor,s.loan,next);
        assertThat(f.jdbc.queryForObject("select count(*) from loan_group_request where loan_id=?",Integer.class,s.loan)).isEqualTo(2);
        assertThat(f.jdbc.queryForObject("select decision from loan_group_decision where request_id=?",String.class,request)).isEqualTo("REJECT");
    }
    @Test void aCompanyRepresentativeWithStaffPermissionsCannotApproveTheirOwnBorrowingSide(){
        UUID request=submit();
        var m=Member.builder().id(f.client).saccoId(f.institution).stationId("B1").fullName("Synthetic company representative")
            .status(MemberStatus.ACTIVE).position(Position.ACCOUNTANT).staffRoles(Set.of(Position.ACCOUNTANT))
            .staffAccessStatus(StaffAccessStatus.ACTIVE).memberAccount(true).build();
        f.jdbc.update("update members set position='ACCOUNTANT',staff_access_status='ACTIVE' where id=?",f.client);
        for(var claim:List.of("LOAN_RECORDING_VIEW","LOAN_RECORDING_APPROVE"))f.jdbc.update("insert into member_access_claims(member_id,claim_name) values(?,?)",f.client,claim);
        when(f.context.getBean(MemberDirectoryService.class).find(f.client)).thenReturn(Optional.of(m));
        var actor=new AppUserPrincipal(m,EnumSet.allOf(UserClaim.class));
        assertThat(service.review(actor,s.loan).canApprove()).isFalse();
        assertThatThrownBy(()->decide(actor,request,"APPROVE")).hasMessage("loanGroups.error.independent");
        assertThatThrownBy(()->f.jdbc.update("insert into loan_group_decision(id,request_id,step,actor_id,decision,reason,evidence,recorded_at) values(?,?,0,?,'APPROVE','Own company','Paper',now())",UUID.randomUUID(),request,f.client)).isInstanceOf(DataAccessException.class);
    }
}
