package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.repository.LoanApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanPaymentTransactionSyncSchedulerTest {

    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private LoanPaymentTransactionSyncService syncService;

    private LoanPaymentTransactionSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new LoanPaymentTransactionSyncScheduler(loanApplicationRepository, syncService);
        ReflectionTestUtils.setField(scheduler, "syncPageSize", 1);
    }

    @Test
    void recentSyncPagesThroughDisbursedLoansWithoutUserInteraction() {
        LoanApplication first = loan("LN-1001");
        LoanApplication second = loan("LN-1002");
        when(loanApplicationRepository.findByStatusInAndLoanIdIsNotNull(anyCollection(), any()))
            .thenReturn(new PageImpl<>(List.of(first), PageRequest.of(0, 1), 2))
            .thenReturn(new PageImpl<>(List.of(second), PageRequest.of(1, 1), 2));
        when(syncService.syncAllAndRefreshSummary(first)).thenReturn(1);
        when(syncService.syncAllAndRefreshSummary(second)).thenReturn(0);

        scheduler.syncRecentPayments();

        verify(syncService).syncAllAndRefreshSummary(first);
        verify(syncService).syncAllAndRefreshSummary(second);
    }

    private LoanApplication loan(String loanId) {
        return LoanApplication.builder()
            .id(UUID.randomUUID())
            .status(LoanStatus.DISBURSED)
            .loanId(loanId)
            .build();
    }
}
