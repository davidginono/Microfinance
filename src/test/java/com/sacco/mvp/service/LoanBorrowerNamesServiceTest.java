package com.sacco.mvp.service;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanBorrowerNamesServiceTest {
    @Mock LoanApplicationRepository loans;@Mock GeneralLedgerService scope;@InjectMocks LoanPresentationService presentation;
    final AppUserPrincipal actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.MANAGER).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),EnumSet.of(UserClaim.MANAGER_QUEUE_VIEW),true);
    @Test void labelsAreKeyedByLoanAndTheQueryUsesCurrentActorScope(){
        UUID id=UUID.randomUUID();var row=mock(LoanApplicationRepository.BorrowerNameRow.class);when(row.getLoanId()).thenReturn(id);when(row.getName()).thenReturn("Synthetic organization <Limited>");when(loans.findBorrowerNamesForLoans("I1","B1",List.of(id))).thenReturn(List.of(row));
        var names=presentation.borrowerNames(actor,UserClaim.MANAGER_QUEUE_VIEW,List.of(id));assertThat(names).containsExactly(Map.entry(id,"Synthetic organization <Limited>"));assertThatThrownBy(()->names.clear()).isInstanceOf(UnsupportedOperationException.class);
        verify(scope).requireActor(actor,"MANAGER_QUEUE_VIEW");
    }
    @Test void revokedCurrentPermissionStopsTheRead(){
        doThrow(new AccessDeniedException("Revoked")).when(scope).requireActor(actor,"MANAGER_QUEUE_VIEW");
        assertThatThrownBy(()->presentation.borrowerNames(actor,UserClaim.MANAGER_QUEUE_VIEW,List.of(UUID.randomUUID()))).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(loans);
    }
    @Test void selectionsAreBoundedAndEmptySelectionsNeverQueryTheDatabase(){
        assertThat(presentation.borrowerNames(actor,UserClaim.MANAGER_QUEUE_VIEW,List.of())).isEmpty();
        assertThatThrownBy(()->presentation.borrowerNames(actor,UserClaim.MANAGER_QUEUE_VIEW,java.util.stream.IntStream.range(0,101).mapToObj(n->UUID.randomUUID()).toList())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->presentation.borrowerNames(actor,UserClaim.MANAGER_QUEUE_VIEW,Arrays.asList((UUID)null))).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(loans);
    }
    @Test void unrelatedAndMemberClaimsCannotSelectAStaffLoanAudience(){
        assertThatThrownBy(()->presentation.borrowerNames(actor,UserClaim.MEMBER_LOANS_VIEW,List.of(UUID.randomUUID()))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->presentation.borrowerNames(actor,UserClaim.ACCOUNTING_BUSINESS_CREATE,List.of(UUID.randomUUID()))).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(loans,scope);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"JOINT_GROUP","ORGANIZATION"})
    void groupProfilesHaveNoPersonalIdentityOrContactDetails(String kind){
        var id=UUID.randomUUID();var party=UUID.randomUUID();var group=UUID.randomUUID();var row=mock(LoanApplicationRepository.BorrowerProfileRow.class);
        when(row.getPartyId()).thenReturn(party);when(row.getKind()).thenReturn(kind);when(row.getGroupId()).thenReturn(group);
        when(row.getName()).thenReturn("Synthetic group <Limited>");when(row.getNumber()).thenReturn("GR-001");when(row.getBranch()).thenReturn("B1");when(row.getLegalForm()).thenReturn("REGISTERED_GROUP");
        when(loans.findBorrowerProfile("I1","B1",id,actor.getMemberId(),null)).thenReturn(Optional.of(row));
        var profile=presentation.borrowerProfile(actor,UserClaim.MANAGER_QUEUE_VIEW,id);
        assertThat(profile.isPerson()).isFalse();assertThat(profile.partyId()).isEqualTo(party);assertThat(profile.groupId()).isEqualTo(group);
        assertThat(profile.clientId()).isNull();assertThat(profile.email()).isNull();assertThat(profile.phone()).isNull();assertThat(profile.name()).isEqualTo("Synthetic group <Limited>");
        verify(row,never()).getClientId();verify(row,never()).getEmail();verify(row,never()).getPhone();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=UserClaim.class,names={"BOARD_QUEUE_VIEW","CHAIRPERSON_QUEUE_VIEW","CREDIT_COMMITTEE_QUEUE_VIEW","LOAN_OFFICER_QUEUE_VIEW"})
    void reviewerProfilesRequireTheExactCurrentAssignmentStage(UserClaim claim){
        var id=UUID.randomUUID();String stage=claim.name().replace("_QUEUE_VIEW","");
        when(loans.findBorrowerProfile("I1","B1",id,actor.getMemberId(),stage)).thenReturn(Optional.empty());
        assertThatThrownBy(()->presentation.borrowerProfile(actor,claim,id)).isInstanceOf(AccessDeniedException.class);verify(scope).requireActor(actor,claim.name());
    }
    @Test void profileReadRejectsRevokedPermissionsAndClientAudienceBeforePersistence(){
        var id=UUID.randomUUID();doThrow(new AccessDeniedException("Revoked")).when(scope).requireActor(actor,"MANAGER_QUEUE_VIEW");
        assertThatThrownBy(()->presentation.borrowerProfile(actor,UserClaim.MANAGER_QUEUE_VIEW,id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->presentation.borrowerProfile(actor,UserClaim.MEMBER_LOANS_VIEW,id)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(loans);
    }
}
