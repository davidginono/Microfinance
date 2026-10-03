package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.accounting.policy.AccountingPolicyService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerReportServiceTest {
    @Mock LedgerReportRepository repository;
    @Mock AccountingPolicyService policies;
    @Mock MemberDirectoryService members;
    @Mock UserClaimService claims;
    @Mock SaccoRegistryService institutions;
    @Mock ApplicationClock clock;
    @InjectMocks LedgerReportService service;
    Member current; AppUserPrincipal actor;
    final LocalDate day=LocalDate.of(2026,10,2);
    final OffsetDateTime now=OffsetDateTime.parse("2026-10-02T10:00:00+03:00");
    LedgerReportService.Parameters parameters;

    @BeforeEach void setup() {
        current=Member.builder().id(UUID.randomUUID()).memberNo("SYNTHETIC")
            .saccoId("I1").stationId("B1").fullName("Synthetic accountant")
            .position(Position.ACCOUNTANT).memberAccount(false).status(MemberStatus.ACTIVE).build();
        actor=new AppUserPrincipal(current,Set.of(UserClaim.FINANCIAL_REPORTS_VIEW),true);
        parameters=new LedgerReportService.Parameters(day.minusDays(1),day,now,0);
        lenient().when(members.find(current.getId())).thenReturn(Optional.of(current));
        lenient().when(institutions.findActiveSacco("I1")).thenReturn(Optional.of(RegisteredSacco.builder().saccoId("I1").active(true).build()));
        lenient().when(institutions.findStation("I1","B1")).thenReturn(Optional.of(SaccoStation.builder().saccoId("I1").stationId("B1").active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        lenient().when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.FINANCIAL_REPORTS_VIEW));
        lenient().when(clock.today()).thenReturn(day);lenient().when(clock.now()).thenReturn(now);
    }
    @Test void revokedPermissionRejectsBeforePostedBookRead() {
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(Set.of());
        denied();
    }
    @Test void changedBranchRejectsStaleSession() { current.setStationId("B2");denied(); }
    @Test void inactiveInstitutionRejectsRead() {when(institutions.findActiveSacco("I1")).thenReturn(Optional.empty());denied();}
    @Test void suspendedBranchRejectsRead() {
        when(institutions.findStation("I1","B1")).thenReturn(Optional.of(SaccoStation.builder().saccoId("I1").stationId("B1").active(true).accessStatus(SaccoAccessStatus.SUSPENDED).build()));denied();
    }
    @Test void institutionAggregationRequiresSeparateCurrentPermission() {
        assertThatThrownBy(()->service.trialBalance(actor,parameters,true)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository,policies);
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.FINANCIAL_REPORTS_VIEW,UserClaim.FINANCIAL_REPORTS_INSTITUTION));
        assertThat(service.authorize(actor,true)).isEqualTo(new LedgerReportService.Scope("I1","B1",true));
    }
    @Test void unboundedDatesFutureCutoffAndNegativePageRejectBeforeBookRead() {
        for(var bad:List.of(new LedgerReportService.Parameters(day.minusDays(366),day,now,0),
                new LedgerReportService.Parameters(day,day.plusDays(1),now,0),
                new LedgerReportService.Parameters(day,day,now.plusNanos(1),0),
                new LedgerReportService.Parameters(day,day,now,-1))) {
            assertThatThrownBy(()->service.trialBalance(actor,bad,false)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(repository,policies);
    }
    private void denied() {
        assertThatThrownBy(()->service.trialBalance(actor,parameters,false)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository,policies);
    }
    @Test void allocationCreateAndApproveHaveIndependentFreshClaims() {
        assertThatThrownBy(()->service.authorizeAllocation(actor,UserClaim.ACCOUNTING_CASH_FLOW_CREATE)).isInstanceOf(AccessDeniedException.class);
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.FINANCIAL_REPORTS_VIEW,UserClaim.ACCOUNTING_CASH_FLOW_CREATE));
        assertThat(service.authorizeAllocation(actor,UserClaim.ACCOUNTING_CASH_FLOW_CREATE).branch()).isEqualTo("B1");
        assertThatThrownBy(()->service.authorizeAllocation(actor,UserClaim.ACCOUNTING_CASH_FLOW_APPROVE)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.authorizeAllocation(actor,UserClaim.STATEMENT_APPROVE)).isInstanceOf(AccessDeniedException.class);
    }
}
