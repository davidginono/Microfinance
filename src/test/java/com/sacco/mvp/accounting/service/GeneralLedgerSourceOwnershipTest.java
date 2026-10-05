package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.Journal;
import com.sacco.mvp.accounting.policy.AccountingPolicyService;
import com.sacco.mvp.accounting.repository.GeneralLedgerRepository;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.reporting.execution.service.AccountingReleaseGateService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeneralLedgerSourceOwnershipTest {
    @Test void sourceOwnedVoucherNeverDraftsOrDuplicatesAGeneralLedgerJournal() {
        UUID id=UUID.randomUUID(),voucher=UUID.randomUUID(),journal=UUID.randomUUID();
        var member=Member.builder().id(id).saccoId("I").stationId("B").position(Position.MANAGER).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build();
        var actor=new AppUserPrincipal(member,Set.of(UserClaim.ACCOUNTING_JOURNALS_CREATE),true);
        var books=mock(GeneralLedgerRepository.class);var policies=mock(AccountingPolicyService.class);var claims=mock(UserClaimService.class);var directory=mock(MemberDirectoryService.class);var institutions=mock(SaccoRegistryService.class);
        when(directory.find(id)).thenReturn(Optional.of(member));when(claims.effectiveClaims(id,member.getActiveStaffRolesResolved(),member.isMemberAccess())).thenReturn(Set.of(UserClaim.ACCOUNTING_JOURNALS_CREATE));
        when(institutions.findActiveSacco("I")).thenReturn(Optional.of(RegisteredSacco.builder().saccoId("I").active(true).build()));when(institutions.findStation("I","B")).thenReturn(Optional.of(SaccoStation.builder().saccoId("I").stationId("B").active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        when(books.operationalOwner("I","B",voucher)).thenReturn(Optional.of(journal));
        when(books.journal("I","B",journal,false)).thenReturn(Optional.of(new Journal(journal,"I","B",UUID.randomUUID(),1,UUID.randomUUID(),"DISBURSEMENT","SOURCE",UUID.randomUUID(),"hash","POSTED","Evidence",null,LocalDate.of(2026,10,1),UUID.randomUUID(),UUID.randomUUID(),OffsetDateTime.now(),OffsetDateTime.now(),null,List.of(),false)));
        var service=new GeneralLedgerService(books,policies,new AccessControlService(),mock(AuditService.class),mock(ApplicationClock.class),claims,directory,institutions,mock(AccountingReleaseGateService.class));
        assertThatThrownBy(()->service.bridgeOperationalVoucher(actor,voucher,UUID.randomUUID(),"Second GL attempt")).hasMessage("accounting.error.sourceAlreadyOwned");
        verify(books,never()).createJournal(any());verifyNoInteractions(policies);
    }
}
