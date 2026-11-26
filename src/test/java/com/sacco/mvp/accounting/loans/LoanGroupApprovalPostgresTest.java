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
    @Test void archiveCountsOnlyDisbursedLoansWithTheSignedInReviewersDecision() {
        decide(first,submit(),"REJECT");
        UUID request=submit();
        decide(first,request,"APPROVE");decide(second,request,"APPROVE");decide(manager,request,"APPROVE");
        assertThat(service.archive(first,"","newest",0).count()).isZero();
        var post=f.post(loan,"5000000.00","REVIEW-ARCHIVE-"+UUID.randomUUID());
        post.setEffectiveDate(SimpleGroupLoanPostgresTest.DAY);
        post.setFirstPaymentDate(SimpleGroupLoanPostgresTest.DAY.plusDays(1));
        f.service.disburse(f.actor,post);
        var archive=service.archive(first,"","newest",0);
        assertThat(archive.count()).isEqualTo(1);
        assertThat(archive.rows()).extracting(ReviewedLoan::id).containsExactly(loan);
        assertThat(service.archive(first,"no-match","newest",0).rows()).isEmpty();
        assertThat(service.archive(f.actor,"","newest",0).count()).isZero();
        assertThat(queue(first,Position.ACCOUNTANT)).doesNotContain("/finance/loan-records/"+loan);
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
        assertThatThrownBy(()->service.decide(reviewer,loan,request,UUID.randomUUID(),"REJECT"," ")).hasMessage("loanGroups.error.reason");
        assertThat(f.jdbc.queryForObject("select count(*) from loan_group_decision where request_id=?",Integer.class,request)).isZero();
        service.decide(reviewer,loan,request,UUID.randomUUID(),"APPROVE","Checked signed papers");
        assertThat(f.jdbc.queryForObject("select count(*) from loan_group_decision where request_id=?",Integer.class,request)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("select evidence from loan_group_decision where request_id=?",String.class,request)).isEmpty();
        assertThat(status()).isEqualTo("SUBMITTED");
    }
    @Test void loanDetailsAndRegisterShowTheNextFrozenReviewGroup() {
        var request=submit();
        assertThat(service.review(first,loan).request().submittedAt()).isNotNull();
        assertThat(f.service.detail(f.actor,loan).loan().reviewGroup()).isEqualTo("ACCOUNTANT");
        decide(first,request,"APPROVE");
        assertThat(f.service.detail(f.actor,loan).loan().reviewGroup()).isEqualTo("ACCOUNTANT");
        decide(second,request,"APPROVE");
        assertThat(f.service.detail(f.actor,loan).loan().reviewGroup()).isEqualTo("MANAGER");
        var filter=new Filter("",null,null,"SUBMITTED","newest",0,null);
        assertThat(f.service.list(f.actor,filter).rows())
            .filteredOn(row -> row.id().equals(loan)).extracting(LoanRow::reviewGroup).containsExactly("MANAGER");
        assertThat(service.officeReviews(manager,Position.MANAGER,"","number",0).rows())
            .singleElement().satisfies(row -> assertThat(row.reviewGroup()).isEqualTo("MANAGER"));
        decide(manager,request,"APPROVE");
        var ready=f.service.detail(f.actor,loan).loan();
        assertThat(ready.status()).isEqualTo("READY_FOR_DISBURSEMENT");
        assertThat(ready.reviewGroup()).isNull();
    }
    @AfterAll void stop(){s.stop();}
    @Test void historicalReferencesStayReadOnlyAlongsideNewDecisionsWithoutReferences(){
        var request=submit();var id=UUID.randomUUID();
        new LoanRecordingRepository(f.jdbc).groupDecision(service.review(first,loan).request(),
            new GroupDecision(id,0,first.getMemberId(),first.getFullName(),"APPROVE","Historical review","Retained signed reference",f.context.getBean(ApplicationClock.class).now()));
        decide(second,request,"APPROVE");decide(manager,request,"APPROVE");
        assertThat(status()).isEqualTo("READY_FOR_DISBURSEMENT");
        assertThat(f.jdbc.queryForObject("select evidence from loan_group_decision where id=?",String.class,id)).isEqualTo("Retained signed reference");
        assertThat(f.jdbc.queryForObject("select count(*) from loan_group_decision where request_id=? and evidence=''",Integer.class,request)).isEqualTo(2);
        assertThatThrownBy(()->f.jdbc.update("update loan_group_decision set evidence='' where id=?",id)).isInstanceOf(DataAccessException.class);
    }
    @BeforeEach void fixture(){s.fixture();first=reviewer(Position.ACCOUNTANT);second=reviewer(Position.ACCOUNTANT);manager=reviewer(Position.MANAGER);rule(0,2);loan=s.record();}
    RecordForm individual(){
        var registration=new com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.CreateForm();var profile=registration.getProfile();profile.setFirstName("Individual");profile.setLastName("Borrower");profile.setPhone("+255712"+String.format("%06d",++s.phone));profile.setMonthlyIncome(new java.math.BigDecimal("2000000.00"));profile.setActivity("Synthetic business");
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
    void decide(AppUserPrincipal p,UUID request,String decision){service.decide(p,loan,request,UUID.randomUUID(),decision,"Synthetic independent paper review");}
    String status(){return f.jdbc.queryForObject("select status from loan_applications where id=?",String.class,loan);}
    List<String> queue(AppUserPrincipal actor,Position group) {
        return service.officeReviews(actor,group,"","number",0).rows().stream().map(com.sacco.mvp.staffoffice.dto.OfficeWork.Task::href).toList();
    }
    @Test void assignedReviewersCannotReadTheirQueueOrDecideWithoutExplicitClaims() {
        UUID request=submit();
        var viewer=new AppUserPrincipal(f.context.getBean(MemberDirectoryService.class).find(first.getMemberId()).orElseThrow(),Set.of(UserClaim.LOAN_RECORDING_VIEW));
        when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(viewer.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.LOAN_RECORDING_VIEW));
        assertThatThrownBy(()->queue(viewer,Position.ACCOUNTANT)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.review(viewer,loan).canApprove()).isFalse();assertThat(service.review(viewer,loan).canReject()).isFalse();
        for(var decision:List.of("APPROVE","REJECT"))
            assertThatThrownBy(()->service.decide(viewer,loan,request,UUID.randomUUID(),decision,"Checked")).isInstanceOf(AccessDeniedException.class);
        assertThat(f.jdbc.queryForObject("select count(*) from loan_group_decision where request_id=?",Integer.class,request)).isZero();
        assertThat(queue(f.operator("B2"),Position.ACCOUNTANT)).isEmpty();
    }
    @Test void reviewerCardsKeepPendingFrozenAssignmentsAfterConfigurationChanges() {
        UUID request=submit();
        var form=new GroupRuleForm();form.setProductId(f.product);form.setExpectedRevision(1);form.setGroups(List.of(Position.LOAN_OFFICER));form.setApprovals(List.of(1));
        service.saveRule(f.actor,form);
        assertThat(queue(first,Position.ACCOUNTANT)).contains("/finance/loan-records/"+loan);
        decide(second,request,"REJECT");
        assertThat(queue(first,Position.ACCOUNTANT)).doesNotContain("/finance/loan-records/"+loan);
    }
    @Test void personalDashboardCountsKeepAllAssignedReviewGroupsAndFrozenEligibility() {
        var directory=f.context.getBean(MemberDirectoryService.class);
        var member=directory.find(manager.getMemberId()).orElseThrow();
        member.setStaffRoles(Set.of(Position.ACCOUNTANT,Position.MANAGER));
        var combined=new AppUserPrincipal(member,EnumSet.allOf(UserClaim.class));
        var offices=new com.sacco.mvp.staffoffice.service.StaffOfficeService(directory,
            f.context.getBean(UserClaimService.class),f.context.getBean(SaccoRegistryService.class),new AccessControlService(),service,f.service,
            mock(com.sacco.mvp.accounting.approval.service.VoucherWorkflowService.class),mock(com.sacco.mvp.accounting.service.AccountOfficeWorkService.class),
            f.context.getBean(com.sacco.mvp.clients.onboarding.service.OnboardingService.class),mock(com.sacco.mvp.lending.controls.service.CreditControlService.class),
            mock(AdminOfficeWorkService.class),mock(AdminService.class));
        var request=submit();
        var pending=offices.activity(combined,"loan-reviews");
        assertThat(pending.entries()).hasSize(2);
        assertThat(pending.entries()).filteredOn(c->c.office().equals("chief-accountant")).extracting(c->c.shown()).containsExactly(1);
        assertThat(pending.entries()).filteredOn(c->c.office().equals("general-manager")).extracting(c->c.shown()).containsExactly(0);
        decide(first,request,"APPROVE");decide(second,request,"APPROVE");
        var next=offices.activity(combined,"loan-reviews");
        assertThat(next.entries()).filteredOn(c->c.office().equals("chief-accountant")).extracting(c->c.shown()).containsExactly(0);
        assertThat(next.entries()).filteredOn(c->c.office().equals("general-manager")).extracting(c->c.shown()).containsExactly(1);
        assertThat(offices.activity(f.operator("B2"),"loan-reviews").entries()).allMatch(c->c.shown()==0);
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
    @Test void eachProductRoutesOnlyToItsConfiguredReviewers() {
        UUID otherProduct=UUID.randomUUID();
        f.jdbc.update("""
            insert into loan_product_settings(id,sacco_id,loan_type,active,product_status,product_code,product_name,form_schema,
                guarantors_required,interest_rate,interest_method,repayment_frequency,min_repayment_months,max_repayment_months,created_at,updated_at)
            select ?,sacco_id,loan_type,active,product_status,'MANAGER-REVIEW','Synthetic manager-reviewed loan',form_schema,
                guarantors_required,interest_rate,interest_method,repayment_frequency,min_repayment_months,max_repayment_months,now(),now()
            from loan_product_settings where id=?
            """,otherProduct,f.product);
        var form=individual();form.setProductId(otherProduct);form.setApplicationDetails(new ApplicationDetails());
        form.getApplicationDetails().setDocumentTypes(List.of("SIGNED_APPLICATION"));
        form.getApplicationDetails().setDocumentNames(List.of("Synthetic signed application"));
        UUID otherLoan=f.service.save(f.actor,form,List.of(s.paper()));
        assertThatThrownBy(()->service.submit(f.actor,otherLoan,UUID.randomUUID())).hasMessage("loanGroups.error.rule");
        assertThat(service.review(f.actor,otherLoan).request()).isNull();

        var configuration=new GroupRuleForm();configuration.setProductId(otherProduct);
        configuration.setGroups(List.of(Position.MANAGER));configuration.setApprovals(List.of(1));
        service.saveRule(f.actor,configuration);
        UUID request=submit(),otherRequest=UUID.randomUUID();service.submit(f.actor,otherLoan,otherRequest);
        assertThat(queue(first,Position.ACCOUNTANT)).containsExactly("/finance/loan-records/"+loan);
        assertThat(queue(manager,Position.MANAGER)).containsExactly("/finance/loan-records/"+otherLoan);
        assertThat(service.review(first,otherLoan).canApprove()).isFalse();
        assertThat(service.review(manager,otherLoan).canApprove()).isTrue();
        assertThatThrownBy(()->service.decide(first,otherLoan,otherRequest,UUID.randomUUID(),"APPROVE","Checked"))
            .isInstanceOf(AccessDeniedException.class);
        var frozen=service.review(manager,otherLoan);
        assertThat(frozen.steps()).extracting(GroupStep::group).containsExactly(Position.MANAGER);
        assertThat(f.jdbc.queryForObject("select product_id from loan_group_rule where id=?",UUID.class,frozen.request().rule())).isEqualTo(otherProduct);
        decide(first,request,"APPROVE");decide(second,request,"APPROVE");
        assertThat(queue(manager,Position.MANAGER)).containsExactlyInAnyOrder("/finance/loan-records/"+loan,"/finance/loan-records/"+otherLoan);
        service.decide(manager,otherLoan,otherRequest,UUID.randomUUID(),"REJECT","Missing income proof");
        assertThat(queue(manager,Position.MANAGER)).containsExactly("/finance/loan-records/"+loan);
        assertThat(status()).isEqualTo("SUBMITTED");assertThat(count("loan_ledgers")).isZero();
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
        for(var reviewer:List.of(first,second,manager))service.decide(reviewer,loan,request,UUID.randomUUID(),"APPROVE","Synthetic independent review");
        assertThat(f.jdbc.queryForObject("select status from loan_applications where id=?",String.class,loan)).isEqualTo("READY_FOR_DISBURSEMENT");
        assertThat(count("loan_ledgers")).isZero();
        f.service.disburse(f.actor,f.post(loan,"1000.00","D-SYNTHETIC-INDIVIDUAL"));
        assertThat(count("loan_ledgers")).isEqualTo(1);
    }
    @Test void anIndividualApplicationCanBeReviewedWithoutASignedApplication(){
        UUID individual=f.service.save(f.actor,individual()),request=UUID.randomUUID();
        service.submit(f.actor,individual,request);
        assertThat(f.jdbc.queryForObject("select status from loan_applications where id=?",String.class,individual)).isEqualTo("SUBMITTED");
        assertThat(queue(first,Position.ACCOUNTANT)).containsExactly("/finance/loan-records/"+individual);
        for(var reviewer:List.of(first,second,manager))
            service.decide(reviewer,individual,request,UUID.randomUUID(),"APPROVE","Synthetic independent review");
        assertThat(f.jdbc.queryForObject("select status from loan_applications where id=?",String.class,individual)).isEqualTo("READY_FOR_DISBURSEMENT");
        assertThat(count("loan_ledgers")).isZero();assertThat(count("gl_journal")).isZero();
    }
    @Test void requiredGuarantorDocumentsStillBlockSubmissionWithoutConsent(){
        f.jdbc.update("update loan_product_settings set applicant_attachment_required=true where id=?",f.product);
        UUID individual=f.service.save(f.actor,individual());
        f.jdbc.update("update loan_applications set required_guarantors=1 where id=?",individual);
        assertThatThrownBy(()->service.submit(f.actor,individual,UUID.randomUUID())).hasMessage("recording.error.guarantorConsent");
        assertThat(f.jdbc.queryForObject("select count(*) from loan_group_request where loan_id=?",Integer.class,individual)).isZero();
    }
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
    @Test void progressUsesTheLatestRoundAndFrozenRuleWithinTheLiveBranch(){
        UUID request=submit();rule(1,1);
        var original=service.progress(f.actor,UserClaim.LOAN_RECORDING_VIEW,loan);
        assertThat(original.steps().getFirst().approvals()).isEqualTo(2);
        decide(first,request,"REJECT");UUID next=UUID.randomUUID();service.submit(f.actor,loan,next);
        var current=service.progress(f.actor,UserClaim.DISBURSEMENT_QUEUE_VIEW,loan);
        assertThat(current.request().id()).isEqualTo(next);assertThat(current.steps().getFirst().approvals()).isEqualTo(1);
        assertThat(current.decisions()).isEmpty();assertThat(current.canApprove()).isFalse();
        assertThatThrownBy(()->service.progress(f.operator("B2"),UserClaim.DISBURSEMENT_QUEUE_VIEW,loan)).isInstanceOf(AccessDeniedException.class);
        assertThat(count("gl_journal")).isZero();
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
    @Test void aNamedGroupBorrowerCannotAcquireStaffAccessOrApproveTheirOwnLoan(){
        UUID request=submit();
        UUID borrower=s.clients.getFirst();
        assertThatThrownBy(()->f.jdbc.update("update members set position='ACCOUNTANT',staff_access_status='ACTIVE' where id=?",borrower))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->f.jdbc.update("insert into member_staff_roles(member_id,role_name) values(?,'ACCOUNTANT')",borrower))
            .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->f.jdbc.update("insert into loan_group_decision(id,request_id,step,actor_id,decision,reason,evidence,recorded_at) values(?,?,0,?,'APPROVE','Own group','Paper',now())",UUID.randomUUID(),request,borrower))
            .isInstanceOf(DataAccessException.class);
    }
}
