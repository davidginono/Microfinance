package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.loans.repository.LoanRecordingRepository;
import com.sacco.mvp.accounting.loans.service.*;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoanGroupApprovalTest {
    final LoanRecordingRepository records=mock(LoanRecordingRepository.class);
    final GeneralLedgerService access=mock(GeneralLedgerService.class);
    final MemberDirectoryService members=mock(MemberDirectoryService.class);
    final AuditService audit=mock(AuditService.class);
    final ApplicationClock clock=mock(ApplicationClock.class);
    final LoanRecordingService recording=mock(LoanRecordingService.class);
    final SaccoConfigurationService products=mock(SaccoConfigurationService.class);
    final LoanGroupApprovalService service=new LoanGroupApprovalService(records,access,members,audit,clock,JsonMapper.builder().build(),recording,products);
    final UUID loan=UUID.randomUUID(),product=UUID.randomUUID(),client=UUID.randomUUID(),recorder=UUID.randomUUID(),maker=UUID.randomUUID(),rule=UUID.randomUUID();
    final GroupRequest request=new GroupRequest(UUID.randomUUID(),loan,rule,maker,1,"hash","RECORDED");
    final List<GroupDecision> decisions=new ArrayList<>();
    String status="SUBMITTED",hash="hash";
    @BeforeEach void setup(){
        when(clock.now()).thenReturn(OffsetDateTime.parse("2026-11-11T12:00:00+03:00"));
        when(records.groupLoan(any(),eq(loan),anyBoolean())).thenAnswer(i->new GroupLoan(loan,product,client,recorder,status,hash,"LN-1","Client",new BigDecimal("1000.00"),false));
        when(records.groupRequest(any(),eq(loan))).thenReturn(Optional.of(request));
        when(records.groupSteps(any(),eq(rule))).thenReturn("[{\"group\":\"CREDIT_OFFICER\",\"approvals\":2},{\"group\":\"MANAGER\",\"approvals\":1}]");
        when(records.groupDecisions(request)).thenAnswer(i->List.copyOf(decisions));
        doAnswer(i->{decisions.add(i.getArgument(1));return null;}).when(records).groupDecision(eq(request),any());
        doAnswer(i->{status=i.getArgument(2);return null;}).when(records).groupStatus(any(),eq(loan),anyString(),any());
    }
    AppUserPrincipal person(UUID id,Position... groups){
        var m=Member.builder().id(id).saccoId("I1").stationId("B1").memberNo("STAFF-"+id).fullName("Reviewer").position(groups[0])
            .staffRoles(new LinkedHashSet<>(List.of(groups))).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).memberAccount(false).build();
        when(members.find(id)).thenReturn(Optional.of(m));
        return new AppUserPrincipal(m,EnumSet.of(UserClaim.LOAN_RECORDING_VIEW,UserClaim.LOAN_RECORDING_CREATE,UserClaim.LOAN_RECORDING_APPROVE,UserClaim.LOAN_RECORDING_REJECT));
    }
    void decide(AppUserPrincipal p,String decision){service.decide(p,loan,request.id(),UUID.randomUUID(),decision,"Reviewed signed terms","Signed evidence REF-1");}
    @Test void customWorkflowUsesInstitutionReferenceAndExistingVersionChecks(){
        var p=person(UUID.randomUUID(),Position.ICT_MANAGER);
        when(products.ensureCustomApprovalReference("I1")).thenReturn(product);
        when(records.filterProduct(p,product)).thenReturn(Optional.of(mock(Choice.class)));
        var form=new GroupRuleForm();form.setProductId(UUID.randomUUID());form.getGroups().add(Position.CREDIT_OFFICER);form.getApprovals().add(2);
        service.saveCustomRule(p,form);
        assertThat(form.getProductId()).isEqualTo(product);
        verify(records).groupRule(eq(p),argThat(r->r.product().equals(product)&&r.revision()==1),any());
        verify(access).requireActor(p,"APPROVAL_FLOW_VIEW");
        verify(access,never()).requireActor(p,"LOAN_PRODUCTS_CREATE");
        when(records.groupRule(p,product)).thenReturn(Optional.of(new GroupRule(rule,product,1,"[]")));
        assertThatThrownBy(()->service.saveCustomRule(p,form)).hasMessage("loanGroups.error.changed");
    }
    @Test void approvalRequiresEveryDistinctPersonInOrderAndNeverPostsMoney(){
        var first=person(UUID.randomUUID(),Position.CREDIT_OFFICER);var second=person(UUID.randomUUID(),Position.CREDIT_OFFICER);var manager=person(UUID.randomUUID(),Position.MANAGER);
        assertThatThrownBy(()->decide(manager,"APPROVE")).isInstanceOf(AccessDeniedException.class);
        decide(first,"APPROVE");assertThat(status).isEqualTo("SUBMITTED");
        assertThatThrownBy(()->decide(first,"APPROVE")).isInstanceOf(IllegalArgumentException.class).hasMessage("loanGroups.error.independent");
        decide(second,"APPROVE");assertThat(status).isEqualTo("SUBMITTED");
        decide(manager,"APPROVE");assertThat(status).isEqualTo("READY_FOR_DISBURSEMENT");
        assertThat(decisions).hasSize(3);verifyNoInteractions(recording);
    }
    @Test void recorderSubmitterAndBorrowerCannotApprove(){
        for(var id:List.of(recorder,maker,client))assertThatThrownBy(()->decide(person(id,Position.CREDIT_OFFICER),"APPROVE"))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("loanGroups.error.independent");
        assertThat(decisions).isEmpty();
    }
    @Test void currentMembershipIsRecheckedAfterSessionCreation(){
        var p=person(UUID.randomUUID(),Position.CREDIT_OFFICER);
        var changed=Member.builder().id(p.getMemberId()).position(Position.CUSTOMER_CARE).staffRoles(Set.of(Position.CUSTOMER_CARE)).staffAccessStatus(StaffAccessStatus.ACTIVE).status(com.sacco.mvp.domain.MemberStatus.ACTIVE).build();
        when(members.find(p.getMemberId())).thenReturn(Optional.of(changed));
        assertThatThrownBy(()->decide(p,"APPROVE")).isInstanceOf(AccessDeniedException.class);assertThat(decisions).isEmpty();
    }
    @Test void changedTermsAndStaleRoundsNeedFreshReview(){
        var p=person(UUID.randomUUID(),Position.CREDIT_OFFICER);hash="changed";
        assertThatThrownBy(()->decide(p,"APPROVE")).hasMessage("loanGroups.error.changed");hash="hash";
        assertThatThrownBy(()->service.decide(p,loan,UUID.randomUUID(),UUID.randomUUID(),"APPROVE","Reason","Evidence")).hasMessage("loanGroups.error.changed");
        assertThat(decisions).isEmpty();
    }
    @Test void rejectionStopsTheRoundAndRetainsItsEvidence(){
        decide(person(UUID.randomUUID(),Position.CREDIT_OFFICER),"REJECT");assertThat(status).isEqualTo("REJECTED");
        assertThatThrownBy(()->decide(person(UUID.randomUUID(),Position.MANAGER),"APPROVE")).hasMessage("loanGroups.error.changed");
        assertThat(decisions.getFirst().evidence()).isEqualTo("Signed evidence REF-1");
    }
    @Test void uncertainDecisionCanBeRetriedOnceWithoutAnotherApproval(){
        var p=person(UUID.randomUUID(),Position.CREDIT_OFFICER);var key=UUID.randomUUID();
        service.decide(p,loan,request.id(),key,"APPROVE","Reason","Evidence");
        service.decide(p,loan,request.id(),key,"APPROVE","Reason","Evidence");assertThat(decisions).hasSize(1);
        assertThatThrownBy(()->service.decide(p,loan,request.id(),key,"APPROVE","Changed","Evidence")).hasMessage("loanGroups.error.retry");
    }
    @Test void pendingReviewUsesTheRecordedRuleRatherThanTheLatestRule(){
        var p=person(UUID.randomUUID(),Position.CREDIT_OFFICER);var view=service.review(p,loan);
        assertThat(view.steps()).extracting(GroupStep::group).containsExactly(Position.CREDIT_OFFICER,Position.MANAGER);
        verify(records).groupSteps(p,rule);verify(records,never()).groupRule(any(),any());
    }
    @Test void submissionNeedsIndependentStaffCapacityAndRecordsNoApproval(){
        status="RECORDED";when(records.groupRequest(any(),eq(loan))).thenReturn(Optional.empty());
        when(records.groupRule(any(),eq(product))).thenReturn(Optional.of(new GroupRule(rule,product,1,"[{\"group\":\"CREDIT_OFFICER\",\"approvals\":1},{\"group\":\"MANAGER\",\"approvals\":1}]")));
        var p=person(recorder,Position.LOAN_OFFICER);var overlapping=UUID.randomUUID();
        when(records.groupCandidates(any(),any(),anyString())).thenReturn(Map.of(Position.CREDIT_OFFICER,List.of(overlapping),Position.MANAGER,List.of(overlapping)));
        assertThatThrownBy(()->service.submit(p,loan,UUID.randomUUID())).hasMessage("loanGroups.error.staff");
        when(records.groupCandidates(any(),any(),anyString())).thenReturn(Map.of(Position.CREDIT_OFFICER,List.of(overlapping),Position.MANAGER,List.of(UUID.randomUUID())));
        service.submit(p,loan,UUID.randomUUID());assertThat(status).isEqualTo("SUBMITTED");assertThat(decisions).isEmpty();
        verify(recording,times(2)).validateStaffReview(p,loan);
    }
    @Test void newGroupsGrantNoFinancialPermissionAutomatically(){
        for(var group:List.of(Position.FINANCE_MANAGER,Position.ICT_MANAGER,Position.CREDIT_OFFICER,Position.ACCOUNTS_CLERK,Position.CUSTOMER_CARE,Position.ACCOUNTS_CLERK_2))
            assertThat(UserClaim.defaultClaims(List.of(group),false)).doesNotContain(UserClaim.LOAN_RECORDING_APPROVE,UserClaim.LOAN_RECORDING_REJECT,UserClaim.LOAN_RECORDING_POST);
        assertThat(Position.staffAssignableRoles()).hasSize(10).doesNotContain(Position.MEMBER,Position.BOARD,Position.CHAIRPERSON);
    }
    @Test void onlyRecordedOrRejectedApplicationsCanStartANewReviewRound(){
        var recorder=person(this.recorder,Position.LOAN_OFFICER);
        for(var state:List.of("DRAFT","SUBMITTED","READY_FOR_DISBURSEMENT","DISBURSED","PAID","WITHDRAWN","PENDING_MANAGER_REVIEW")) {
            status=state;
            assertThatThrownBy(()->service.submit(recorder,loan,UUID.randomUUID())).hasMessage("loanGroups.error.state");
        }
        verifyNoInteractions(recording);verify(records,never()).groupRule(any(),any());
        verify(records,never()).groupRequest(any(),any(),any());
    }
    @Test void onlyTheRecorderCanSendARecordedApplicationForReview(){
        status="RECORDED";when(records.groupRequest(any(),eq(loan))).thenReturn(Optional.empty());
        var owner=person(recorder,Position.LOAN_OFFICER);var other=person(UUID.randomUUID(),Position.CREDIT_OFFICER);
        assertThat(service.review(owner,loan).canSubmit()).isTrue();
        assertThat(service.review(owner,loan).canApprove()).isFalse();
        assertThat(service.review(other,loan).canSubmit()).isFalse();
        assertThatThrownBy(()->service.submit(other,loan,UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
        verify(recording,never()).validateStaffReview(any(),any());verify(records,never()).groupRequest(any(),any(),any());
    }
    @Test void reviewerNeedsNoRecordingPermissionAndCannotSeeTheRecorderCommand(){
        var staff=person(UUID.randomUUID(),Position.CREDIT_OFFICER);
        var reviewer=new AppUserPrincipal(members.find(staff.getMemberId()).orElseThrow(),EnumSet.of(UserClaim.LOAN_RECORDING_VIEW,UserClaim.LOAN_RECORDING_APPROVE,UserClaim.LOAN_RECORDING_REJECT));
        var view=service.review(reviewer,loan);
        assertThat(view.canSubmit()).isFalse();assertThat(view.canApprove()).isTrue();assertThat(view.canReject()).isTrue();
        service.decide(reviewer,loan,request.id(),UUID.randomUUID(),"APPROVE","Checked signed papers","Paper reference");
        assertThat(decisions).hasSize(1);verifyNoInteractions(recording);
    }
    @Test void rejectionRequiresAReasonBeforeAnyDecisionIsStored(){
        var reviewer=person(UUID.randomUUID(),Position.CREDIT_OFFICER);
        assertThatThrownBy(()->service.decide(reviewer,loan,request.id(),UUID.randomUUID(),"REJECT"," ","Paper reference"))
            .hasMessage("loanGroups.error.reason");
        assertThat(decisions).isEmpty();verify(records,never()).groupDecision(any(),any());
    }
}
