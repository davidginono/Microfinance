package com.sacco.mvp.web;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.ui.ExtendedModelMap;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class StaffBorrowerLabelsTest {
    @Mock ManagerService manager;@Mock BoardService reviews;@Mock LoanPresentationService presentation;@Mock LoanProductDisplayService products;@Mock MemberDirectoryService members;@Mock MessageSource messages;
    @Spy AccessControlService access=new AccessControlService();
    @InjectMocks ManagerController managerController;@InjectMocks AccountantController accountant;@InjectMocks DisbursementController teller;@InjectMocks BoardController board;@InjectMocks LoanOfficerController officer;
    LoanApplication loan;ExtendedModelMap model;
    @BeforeEach void setup(){loan=LoanApplication.builder().id(UUID.randomUUID()).borrowerPartyId(UUID.randomUUID()).saccoId("I1").stationId("B1").applicationNumber(12345L).status(LoanStatus.READY_FOR_MANAGER).build();model=new ExtendedModelMap();}
    AppUserPrincipal actor(Position role,UserClaim claim){return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(role).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),EnumSet.of(claim),true);}
    void label(AppUserPrincipal a,UserClaim claim){when(presentation.borrowerNames(a,claim,Set.of(loan.getId()))).thenReturn(Map.of(loan.getId(),"Synthetic facility <Company>"));}
    void assertLabels(){assertThat(model.getAttribute("applicantNames")).isEqualTo(Map.of(loan.getId(),"Synthetic facility <Company>"));assertThat(loan.getApplicantMemberId()).isNull();verifyNoInteractions(members);}
    @Test void managerQueueUsesLoanIds(){var a=actor(Position.MANAGER,UserClaim.MANAGER_QUEUE_VIEW);when(manager.queue(eq("I1"),anyList(),eq(""),eq(false),eq("B1"))).thenReturn(List.of(loan));label(a,UserClaim.MANAGER_QUEUE_VIEW);assertThat(managerController.queue(a,null,null,null,model)).isEqualTo("manager/queue");assertLabels();}
    @Test void accountantQueueUsesLoanIds(){var a=actor(Position.ACCOUNTANT,UserClaim.ACCOUNTANT_QUEUE_VIEW);when(manager.queue(eq("I1"),anyList(),eq(""),eq(false),eq("B1"))).thenReturn(List.of(loan));label(a,UserClaim.ACCOUNTANT_QUEUE_VIEW);assertThat(accountant.queue(a,null,null,model)).isEqualTo("accountant/queue");assertLabels();}
    @Test void existingDisburseOnlyAccessCanStillReadItsQueue(){var a=actor(Position.DISBURSEMENT_OFFICER,UserClaim.DISBURSEMENT_QUEUE_DISBURSE);when(manager.queue(eq("I1"),anyList(),eq(""),eq(false),eq("B1"))).thenReturn(List.of(loan));label(a,UserClaim.DISBURSEMENT_QUEUE_DISBURSE);assertThat(teller.queue(a,null,null,model)).isEqualTo("disbursement/queue");assertLabels();}
    @ParameterizedTest @EnumSource(value=ApprovalWorkflowStage.class,names={"BOARD","CHAIRPERSON","CREDIT_COMMITTEE","LOAN_OFFICER"})
    void assignedQueuesUseLoanNamesWithoutChangingAssignmentStages(ApprovalWorkflowStage stage){
        var claim=UserClaim.valueOf(stage.name()+"_QUEUE_VIEW");var a=actor(Position.valueOf(stage.name()),claim);loan.setStatus(LoanStatus.valueOf("AWAITING_"+stage.name()));
        var review=BoardReview.builder().id(UUID.randomUUID()).loanApplicationId(loan.getId()).boardMemberId(a.getMemberId()).reviewStage(stage).decision(BoardDecision.PENDING).build();
        when(presentation.loansById(List.of(loan.getId()))).thenReturn(Map.of(loan.getId(),loan));label(a,claim);
        if(stage==ApprovalWorkflowStage.LOAN_OFFICER){when(reviews.assignedAll(a.getMemberId(),stage)).thenReturn(List.of(review));assertThat(officer.queue(a,null,model)).isEqualTo("board/assigned");}
        else{when(reviews.queue(eq(a.getMemberId()),anyList(),eq("I1"),eq("B1"),eq(""))).thenReturn(List.of(review));assertThat(board.queue(a,null,model)).isEqualTo("board/assigned");}
        assertLabels();assertThat(model.getAttribute("apps")).isEqualTo(List.of(loan));
    }
}
