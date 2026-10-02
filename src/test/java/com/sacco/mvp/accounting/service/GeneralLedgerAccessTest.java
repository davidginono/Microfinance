package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.policy.AccountingPolicyService;
import com.sacco.mvp.accounting.repository.GeneralLedgerRepository;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeneralLedgerAccessTest {
    @Mock GeneralLedgerRepository books;
    @Mock AccountingPolicyService policies;
    @Mock AuditService audit;
    @Mock ApplicationClock clock;
    @Mock UserClaimService userClaims;
    @Mock MemberDirectoryService directory;
    @Mock SaccoRegistryService institutions;
    @Spy AccessControlService access = new AccessControlService();
    @InjectMocks GeneralLedgerService service;
    private Member current;
    private AppUserPrincipal actor;

    @BeforeEach void setup() {
        current = Member.builder().id(UUID.randomUUID()).memberNo("SYNTHETIC-STAFF")
            .saccoId("I1").stationId("B1").fullName("Synthetic accountant")
            .position(Position.ACCOUNTANT).memberAccount(false).status(MemberStatus.ACTIVE).build();
        actor = new AppUserPrincipal(current, Set.of(UserClaim.ACCOUNTING_ACCOUNTS_VIEW), true);
        when(directory.find(current.getId())).thenReturn(Optional.of(current));
        lenient().when(institutions.findActiveSacco("I1")).thenReturn(Optional.of(
            RegisteredSacco.builder().saccoId("I1").active(true).build()));
        lenient().when(institutions.findStation("I1", "B1")).thenReturn(Optional.of(
            SaccoStation.builder().saccoId("I1").stationId("B1").active(true)
                .accessStatus(SaccoAccessStatus.ACTIVE).build()));
        lenient().when(userClaims.effectiveClaims(any(), anyCollection(), anyBoolean()))
            .thenReturn(Set.of(UserClaim.ACCOUNTING_ACCOUNTS_VIEW));
    }

    @Test void inactiveInstitutionCannotReadAccountsWithStaleSession() {
        when(institutions.findActiveSacco("I1")).thenReturn(Optional.empty());
        deniedBeforeReadingBooks();
    }

    @Test void inactiveBranchCannotReadAccountsWithStaleSession() {
        when(institutions.findStation("I1", "B1")).thenReturn(Optional.of(
            SaccoStation.builder().saccoId("I1").stationId("B1").active(false)
                .accessStatus(SaccoAccessStatus.ACTIVE).build()));
        deniedBeforeReadingBooks();
    }

    @Test void suspendedBranchCannotReadAccountsWithStaleSession() {
        when(institutions.findStation("I1", "B1")).thenReturn(Optional.of(
            SaccoStation.builder().saccoId("I1").stationId("B1").active(true)
                .accessStatus(SaccoAccessStatus.SUSPENDED).build()));
        deniedBeforeReadingBooks();
    }

    @Test void revokedCurrentPermissionOverridesSessionClaim() {
        when(userClaims.effectiveClaims(any(), anyCollection(), anyBoolean())).thenReturn(Set.of());
        deniedBeforeReadingBooks();
    }

    private void deniedBeforeReadingBooks() {
        assertThatThrownBy(() -> service.accounts(actor, 0)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(books, policies, audit);
    }
}
