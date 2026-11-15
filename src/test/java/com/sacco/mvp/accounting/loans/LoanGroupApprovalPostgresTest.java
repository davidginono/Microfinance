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
    final SimpleGroupLoanPostgresTest s=new SimpleGroupLoanPostgresTest();
    final LoanRecordingPostgresTest f=s.f;
    UUID loan;
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
    @Test void recorderAndReviewerCommandsStaySeparateInTheDatabase(){
        assertThat(service.review(f.actor,loan).canSubmit()).isTrue();
        assertThat(service.review(first,loan).canSubmit()).isFalse();
        assertThatThrownBy(()->service.submit(first,loan,UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
        assertThat(f.jdbc.queryForObject("select count(*) from loan_group_request where loan_id=?",Integer.class,loan)).isZero();
        UUID request=submit();
        var member=f.context.getBean(MemberDirectoryService.class).find(first.getMemberId()).orElseThrow();
        var reviewer=new AppUserPrincipal(member,EnumSet.of(UserClaim.LOAN_RECORDING_VIEW,UserClaim.LOAN_RECORDING_APPROVE,UserClaim.LOAN_RECORDING_REJECT));
        assertThat(service.review(reviewer,loan).canApprove()).isTrue();assertThat(service.review(reviewer,loan).canSubmit()).isFalse();
        assertThatThrownBy(()->service.decide(reviewer,loan,request,UUID.randomUUID(),"REJECT"," ","Paper reference")).hasMessage("loanGroups.error.reason");
        assertThat(f.jdbc.queryForObject("select count(*) from loan_group_decision where request_id=?",Integer.class,request)).isZero();
        service.decide(reviewer,loan,request,UUID.randomUUID(),"APPROVE","Checked signed papers","Paper reference");
        assertThat(f.jdbc.queryForObject("select count(*) from loan_group_decision where request_id=?",Integer.class,request)).isEqualTo(1);
        assertThat(status()).isEqualTo("SUBMITTED");
    }
    @AfterAll void stop(){s.stop();}
    @BeforeEach void fixture(){s.fixture();first=reviewer(Position.ACCOUNTANT);second=reviewer(Position.ACCOUNTANT);manager=reviewer(Position.MANAGER);rule(0,2);loan=s.record();}
    RecordForm individual(){
        var registration=new com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.CreateForm();var profile=registration.getProfile();profile.setFirstName("Individual");profile.setLastName("Borrower");profile.setPhone("+255712"+String.format("%06d",++s.phone));profile.setMonthlyIncome(new java.math.BigDecimal("2000000.00"));profile.setMonthlyExpenses(new java.math.BigDecimal("500000.00"));profile.setMonthlyDebtRepayments(java.math.BigDecimal.ZERO);profile.setActivity("Synthetic business");
        f.client=f.context.getBean(com.sacco.mvp.clients.onboarding.service.OnboardingService.class).create(f.actor,registration);return f.form();
    }
    AppUserPrincipal reviewer(Position role){
        var p=f.operator("B1");var m=f.context.getBean(MemberDirectoryService.class).find(p.getMemberId()).orElseThrow();m.setPosition(role);m.setStaffRoles(Set.of(role));
        f.jdbc.update("update members set position=? where id=?",role.name(),m.getId());
        for(var claim:List.of("LOAN_RECORDING_VIEW","LOAN_RECORDING_APPROVE","LOAN_RECORDING_REJECT"))f.jdbc.update("insert into member_access_claims(member_id,claim_name) values(?,?)",m.getId(),claim);
        return new AppUserPrincipal(m,EnumSet.allOf(UserClaim.class));
    }
    void rule(int expected,int count){var r=new GroupRuleForm();r.setProductId(f.product);r.setExpectedRevision(expected);r.setGroups(List.of(Position.ACCOUNTANT,Position.MANAGER));r.setApprovals(List.of(count,1));service.saveRule(f.actor,r);}
    UUID submit(){UUID key=UUID.randomUUID();service.submit(f.actor,loan,key);return key;}
    void decide(AppUserPrincipal p,UUID request,String decision){service.decide(p,loan,request,UUID.randomUUID(),decision,"Synthetic independent paper review","Synthetic paper reference");}
    String status(){return f.jdbc.queryForObject("select status from loan_applications where id=?",String.class,loan);}
    List<String> queue(AppUserPrincipal actor,Position group) {
        return service.officeReviews(actor,group,"","number",0).rows().stream().map(com.sacco.mvp.staffoffice.dto.OfficeWork.Task::href).toList();
    }
    @Test void officeQueueUsesFrozenOrderedGroupsAndExcludesPeopleWhoAlreadyDecided() {
        var request=submit();rule(1,1);
        String href="/finance/loan-records/"+loan;
        assertThat(queue(first,Position.ACCOUNTANT)).contains(href);
        assertThat(queue(manager,Position.MANAGER)).isEmpty();
        decide(first,request,"APPROVE");
        assertThat(queue(first,Position.ACCOUNTANT)).isEmpty();
        assertThat(queue(second,Position.ACCOUNTANT)).contains(href);
        assertThat(queue(manager,Position.MANAGER)).isEmpty();
        decide(second,request,"APPROVE");
        assertThat(queue(manager,Position.MANAGER)).contains(href);
        decide(manager,request,"APPROVE");
        assertThat(queue(manager,Position.MANAGER)).isEmpty();
    }
    @Test void officeQueueFollowsTheLatestRoundAndNeverReturnsAnotherBranch() {
        var request=submit();decide(first,request,"REJECT");
        assertThat(queue(second,Position.ACCOUNTANT)).isEmpty();
        submit();assertThat(queue(second,Position.ACCOUNTANT)).hasSize(1);
        var repository=new LoanRecordingRepository(f.jdbc);
        assertThat(repository.officeReviews(f.operator("B2"),Position.ACCOUNTANT,"","number",0)).isEmpty();
        assertThatThrownBy(()->queue(first,Position.MANAGER)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.officeReviews(first,Position.ACCOUNTANT,"","number",10001)).isInstanceOf(IllegalArgumentException.class);
    }
    int count(String table){return f.jdbc.queryForObject("select count(*) from "+table+" where sacco_id=?",Integer.class,f.institution);}
    @Test void staffRecordedIndividualApplicationUsesItsSignedPaperAndTheSameIndependentGroups(){
        var form=individual();form.setApplicationDetails(new ApplicationDetails());form.getApplicationDetails().setDocumentTypes(List.of("SIGNED_APPLICATION"));form.getApplicationDetails().setDocumentNames(List.of("Synthetic signed application"));
        UUID loan=f.service.save(f.actor,form,List.of(s.paper()));
        UUID request=UUID.randomUUID();service.submit(f.actor,loan,request);
        for(var reviewer:List.of(first,second,manager))service.decide(reviewer,loan,request,UUID.randomUUID(),"APPROVE","Synthetic independent review","Synthetic signed application");
        assertThat(f.jdbc.queryForObject("select status from loan_applications where id=?",String.class,loan)).isEqualTo("READY_FOR_DISBURSEMENT");
        assertThat(count("loan_ledgers")).isZero();
        f.service.disburse(f.actor,f.post(loan,"1000.00","D-SYNTHETIC-INDIVIDUAL"));
        assertThat(count("loan_ledgers")).isEqualTo(1);
    }
    @Test void anUnsignedIndividualApplicationCannotEnterReview(){UUID individual=f.service.save(f.actor,individual());assertThatThrownBy(()->service.submit(f.actor,individual,UUID.randomUUID())).hasMessage("recording.error.signedApplication");}
    @Test void orderedFrozenPlanRequiresDifferentPeopleAndPostsNoMoney(){
        UUID request=submit();rule(1,1);
        assertThatThrownBy(()->decide(manager,request,"APPROVE")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->decide(f.actor,request,"APPROVE")).hasMessage("loanGroups.error.independent");
        decide(first,request,"APPROVE");assertThat(status()).isEqualTo("SUBMITTED");
        assertThatThrownBy(()->decide(first,request,"APPROVE")).hasMessage("loanGroups.error.independent");
        decide(second,request,"APPROVE");decide(manager,request,"APPROVE");
        assertThat(status()).isEqualTo("READY_FOR_DISBURSEMENT");assertThat(count("loan_ledgers")).isZero();assertThat(count("gl_journal")).isZero();
        assertThatThrownBy(()->f.jdbc.update("delete from loan_group_decision where request_id=?",request)).isInstanceOf(DataAccessException.class);
    }
    @Test void termFingerprintIgnoresNumericScaleAndKeepsActualValuesDistinct(){
        assertThat(f.jdbc.queryForObject("select loan_group_canonical(?::jsonb)::text=loan_group_canonical(?::jsonb)::text",Boolean.class,
            "{\"amount\":1000.00,\"fees\":[10.00,0.0500]}","{\"fees\":[10.0,0.05],\"amount\":1000}")).isTrue();
        assertThat(f.jdbc.queryForObject("select loan_group_canonical(?::jsonb)::text=loan_group_canonical(?::jsonb)::text",Boolean.class,
            "{\"amount\":1000.00,\"fees\":[10.00]}","{\"amount\":1000.00,\"fees\":[11.00]}")).isFalse();
    }
    @Test void databaseRejectsFundingAndForgedOrRevokedDecisions(){
        assertThatThrownBy(()->f.jdbc.update("update loan_applications set status='READY_FOR_DISBURSEMENT' where id=?",loan)).isInstanceOf(DataAccessException.class);
        UUID request=submit();
        assertThatThrownBy(()->f.jdbc.update("insert into loan_group_decision(id,request_id,step,actor_id,decision,reason,evidence,recorded_at) values(?,?,1,?,'APPROVE','Wrong order','Paper',now())",UUID.randomUUID(),request,manager.getMemberId())).isInstanceOf(DataAccessException.class);
        f.jdbc.update("delete from member_access_claims where member_id=? and claim_name='LOAN_RECORDING_APPROVE'",first.getMemberId());
        assertThatThrownBy(()->decide(first,request,"APPROVE")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->service.review(f.operator("B2"),loan)).isInstanceOf(AccessDeniedException.class);
        assertThat(status()).isEqualTo("SUBMITTED");assertThat(count("loan_ledgers")).isZero();
    }
    @Test void aRejectedRoundRetainsItsDecisionAndRequiresFreshIndependentPeople(){
        UUID request=UUID.randomUUID();service.submit(f.actor,loan,request);decide(first,request,"REJECT");
        assertThat(status()).isEqualTo("REJECTED");UUID next=UUID.randomUUID();service.submit(f.actor,loan,next);
        assertThat(f.jdbc.queryForObject("select count(*) from loan_group_request where loan_id=?",Integer.class,loan)).isEqualTo(2);
        assertThat(f.jdbc.queryForObject("select decision from loan_group_decision where request_id=?",String.class,request)).isEqualTo("REJECT");
    }
    @Test void aNamedGroupBorrowerWithStaffPermissionsCannotApproveTheirOwnLoan(){
        UUID request=submit();
        var m=Member.builder().id(s.clients.getFirst()).saccoId(f.institution).stationId("B1").fullName("Synthetic group borrower")
            .status(MemberStatus.ACTIVE).position(Position.ACCOUNTANT).staffRoles(Set.of(Position.ACCOUNTANT))
            .staffAccessStatus(StaffAccessStatus.ACTIVE).memberAccount(true).build();
        f.jdbc.update("update members set position='ACCOUNTANT',staff_access_status='ACTIVE' where id=?",s.clients.getFirst());
        for(var claim:List.of("LOAN_RECORDING_VIEW","LOAN_RECORDING_APPROVE"))f.jdbc.update("insert into member_access_claims(member_id,claim_name) values(?,?)",s.clients.getFirst(),claim);
        when(f.context.getBean(MemberDirectoryService.class).find(s.clients.getFirst())).thenReturn(Optional.of(m));
        var actor=new AppUserPrincipal(m,EnumSet.allOf(UserClaim.class));
        assertThat(service.review(actor,loan).canApprove()).isFalse();
        assertThatThrownBy(()->decide(actor,request,"APPROVE")).hasMessage("loanGroups.error.independent");
        assertThatThrownBy(()->f.jdbc.update("insert into loan_group_decision(id,request_id,step,actor_id,decision,reason,evidence,recorded_at) values(?,?,0,?,'APPROVE','Own group','Paper',now())",UUID.randomUUID(),request,s.clients.getFirst())).isInstanceOf(DataAccessException.class);
    }
}
