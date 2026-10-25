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
}
