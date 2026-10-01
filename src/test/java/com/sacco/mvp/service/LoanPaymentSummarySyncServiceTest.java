package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.repository.LoanApplicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanPaymentSummarySyncServiceTest {
    @Mock LoanApplicationRepository loans;
    @Mock LoanRepaymentLedgerService ledger;
    @InjectMocks LoanPaymentSummarySyncService service;

    @Test
    void trackedLoanRefreshUsesAuthoritativeLocalProjection() {
        LoanApplication loan = loan();
        when(loans.findById(loan.getId())).thenReturn(Optional.of(loan));
        when(ledger.refreshProjection(loan.getId())).thenReturn(true);
        assertThat(service.refreshLoanPaymentSummary(loan.getId()).status())
            .isEqualTo(LoanPaymentSummarySyncService.RefreshStatus.UPDATED);
        verify(ledger).refreshProjection(loan.getId());
        verify(loans, never()).save(any());
    }

    @Test
    void legacyLoanDoesNotClaimAnOpeningBalanceOrSuccessfulRefresh() {
        LoanApplication loan = loan();
        when(loans.findById(loan.getId())).thenReturn(Optional.of(loan));
        var result = service.refreshLoanPaymentSummary(loan.getId());
        assertThat(result.status()).isEqualTo(LoanPaymentSummarySyncService.RefreshStatus.NO_DATA);
        assertThat(result.message()).contains("opening-balance reconciliation");
        verify(loans, never()).save(any());
    }

    @Test
    void mixedMemberRefreshIsPartialAndBounded() {
        UUID client = UUID.randomUUID();
        LoanApplication tracked = loan(), legacy = loan();
        when(loans.findByApplicantMemberIdAndStatusInAndLoanIdIsNotNull(eq(client), any(), any()))
            .thenReturn(new PageImpl<>(List.of(tracked, legacy)));
        when(loans.findById(tracked.getId())).thenReturn(Optional.of(tracked));
        when(loans.findById(legacy.getId())).thenReturn(Optional.of(legacy));
        when(ledger.refreshProjection(tracked.getId())).thenReturn(true);
        assertThat(service.refreshMemberActiveLoanPaymentSummaries(client, 10000).status())
            .isEqualTo(LoanPaymentSummarySyncService.RefreshStatus.PARTIAL);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(loans).findByApplicantMemberIdAndStatusInAndLoanIdIsNotNull(eq(client), any(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(50);
    }

    private LoanApplication loan() {
        return LoanApplication.builder().id(UUID.randomUUID()).loanId("100001").build();
    }
}
