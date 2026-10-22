package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.AccountingLibraryDtos.*;
import com.sacco.mvp.accounting.repository.AccountingCodeLibraryRepository;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AccountingActivityEditingTest {
    final AccountingCodeLibraryRepository repository=mock(AccountingCodeLibraryRepository.class);
    final GeneralLedgerService ledger=mock(GeneralLedgerService.class);
    final AuditService audit=mock(AuditService.class);
    final AccountingCodeLibraryService service=new AccountingCodeLibraryService(repository,ledger,audit,mock(ApplicationClock.class));
    final UUID id=UUID.randomUUID();
    final AppUserPrincipal actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("staff").position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).build(),Set.of(),true);
    CodeForm form(String name){var form=new CodeForm();form.setCode("FORGED");form.setName(name);form.setDescription(" Updated description ");form.setNameSw("Untrusted translation");return form;}

    @Test void metadataSaveLocksTheScopedActivityAndPreservesItsIdentityAndStoredTranslation() {
        when(repository.activity("I1",id,true)).thenReturn(Optional.of(new Activity(id,"OPS","Operations","Shughuli","Old description",false)));
        var form=form(" Updated name ");service.updateActivity(actor,id,form);
        verify(ledger).requireActor(actor,"ACCOUNTING_ACCOUNTS_UPDATE");
        verify(repository).updateActivity("I1",id,"Updated name","Updated description");assertThat(form.getCode()).isEqualTo("OPS");
        verify(audit).logEvent(eq("ACCOUNTING"),eq(id),eq("ACTIVITY_UPDATED"),eq(actor.getMemberId()),eq(AuditEventStatus.SUCCESS),anyString(),eq("ACCOUNTING_LIBRARY"),eq("OPS"),eq("I1"),eq("B1"),argThat(facts->facts instanceof Map<?,?> values && values.get("previousName").equals("Operations") && values.get("name").equals("Updated name")));
    }
    @Test void missingForeignSystemAndUnauthorizedActivitiesCannotBeEdited() {
        when(repository.activity("I1",id,true)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.updateActivity(actor,id,form("Name"))).isInstanceOf(AccessDeniedException.class);
        when(repository.activity("I1",id,true)).thenReturn(Optional.of(new Activity(id,"SYSTEM.LOAN_REPAYMENT","Repayment",null,null,true)));
        assertThatThrownBy(()->service.updateActivity(actor,id,form("Name"))).hasMessage("library.error.systemManaged");
        doThrow(new AccessDeniedException("Denied")).when(ledger).requireActor(actor,"ACCOUNTING_ACCOUNTS_UPDATE");
        assertThatThrownBy(()->service.updateActivity(actor,id,form("Name"))).isInstanceOf(AccessDeniedException.class);
        verify(repository,never()).updateActivity(any(),any(),any(),any());verifyNoInteractions(audit);
    }
    @Test void invalidNamesAndRepeatedIdenticalSavesDoNotWrite() {
        when(repository.activity("I1",id,true)).thenReturn(Optional.of(new Activity(id,"OPS","Operations","Shughuli","Updated description",true)));
        assertThatThrownBy(()->service.updateActivity(actor,id,form(" "))).hasMessage("library.error.name");
        service.updateActivity(actor,id,form("Operations"));verify(repository,never()).updateActivity(any(),any(),any(),any());verifyNoInteractions(audit);
    }
}
