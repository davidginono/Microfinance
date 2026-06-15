package com.sacco.mvp.service;

import com.sacco.mvp.domain.PlatformSmsSettings;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.domain.SmsUnitStatus;
import com.sacco.mvp.domain.SmsUsageLedger;
import com.sacco.mvp.domain.SmsUsageOutcome;
import com.sacco.mvp.domain.StationSmsAccount;
import com.sacco.mvp.repository.PlatformSmsSettingsRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.repository.SmsUsageLedgerRepository;
import com.sacco.mvp.repository.StationSmsAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmsUnitTransactionServiceTest {

    @Test
    void finalAvailableUnitCanBeReservedOnlyFromLockedAccount() {
        Fixture fixture = new Fixture(1);
        UUID notificationId = UUID.randomUUID();

        SmsUnitTransactionService.ReservationResult result =
            fixture.service.reserve("SACCO-1", "STN001", notificationId, "LOAN_STATUS");

        assertTrue(result.reserved());
        assertEquals(0, result.availableUnits());
        assertNull(result.alertStatus());
        assertEquals(0, fixture.account.getAvailableUnits());
        assertEquals(SmsUnitStatus.DEPLETED, fixture.account.getStatus());
        verify(fixture.accountRepository).findForUpdate("SACCO-1", "STN001");
    }

    @Test
    void explicitProviderRejectionRestoresReservedUnit() {
        Fixture fixture = new Fixture(0);
        UUID ledgerId = UUID.randomUUID();
        SmsUsageLedger ledger = fixture.ledger(ledgerId);
        when(fixture.accountRepository.findByIdForUpdate(fixture.account.getId())).thenReturn(Optional.of(fixture.account));
        when(fixture.ledgerRepository.findByIdAndAccountId(ledgerId, fixture.account.getId())).thenReturn(Optional.of(ledger));

        fixture.service.complete(fixture.account.getId(), ledgerId, SmsSendResult.rejected("Rejected"));

        assertEquals(1, fixture.account.getAvailableUnits());
        assertEquals(SmsUsageOutcome.RESTORED, ledger.getOutcome());
        assertEquals(0, ledger.getUnitChange());
    }

    @Test
    void acceptanceUnknownKeepsUnitConsumed() {
        Fixture fixture = new Fixture(0);
        UUID ledgerId = UUID.randomUUID();
        SmsUsageLedger ledger = fixture.ledger(ledgerId);
        when(fixture.accountRepository.findByIdForUpdate(fixture.account.getId())).thenReturn(Optional.of(fixture.account));
        when(fixture.ledgerRepository.findByIdAndAccountId(ledgerId, fixture.account.getId())).thenReturn(Optional.of(ledger));

        fixture.service.complete(fixture.account.getId(), ledgerId, SmsSendResult.acceptanceUnknown("Timed out"));

        assertEquals(0, fixture.account.getAvailableUnits());
        assertEquals(SmsUsageOutcome.ACCEPTANCE_UNKNOWN, ledger.getOutcome());
        assertEquals(-1, ledger.getUnitChange());
    }

    @Test
    void zeroBalanceWritesBlockedEntryWithoutReservation() {
        Fixture fixture = new Fixture(0);

        SmsUnitTransactionService.ReservationResult result =
            fixture.service.reserve("SACCO-1", "STN001", UUID.randomUUID(), "LOAN_STATUS");

        assertFalse(result.reserved());
        assertEquals("SMS units depleted", result.reason());
        verify(fixture.accountRepository, never()).findByIdForUpdate(any());
        ArgumentCaptor<SmsUsageLedger> ledgerCaptor = ArgumentCaptor.forClass(SmsUsageLedger.class);
        verify(fixture.ledgerRepository).save(ledgerCaptor.capture());
        assertEquals(SmsUsageOutcome.BLOCKED, ledgerCaptor.getValue().getOutcome());
    }

    @Test
    void allocationReplenishesThreeAlertUnitsBeforeUsableBalance() {
        Fixture fixture = new Fixture(0);

        fixture.service.allocate("SACCO-1", "STN001", 10, UUID.randomUUID(), "Top up");

        assertEquals(3, fixture.account.getAlertReservedUnits());
        assertEquals(7, fixture.account.getAvailableUnits());
        assertEquals(7, fixture.account.getWarningBaseline());
    }

    @Test
    void rejectedAlertRestoresReservedAlertUnit() {
        Fixture fixture = new Fixture(0);
        fixture.account.setAlertReservedUnits(1);
        SmsUnitTransactionService.ReservationResult reservation =
            fixture.service.reserveAlert("SACCO-1", "STN001", SmsUnitStatus.DEPLETED);
        SmsUsageLedger ledger = fixture.ledger(reservation.ledgerId());
        when(fixture.accountRepository.findByIdForUpdate(fixture.account.getId())).thenReturn(Optional.of(fixture.account));
        when(fixture.ledgerRepository.findByIdAndAccountId(reservation.ledgerId(), fixture.account.getId()))
            .thenReturn(Optional.of(ledger));

        fixture.service.completeAlert(fixture.account.getId(), reservation.ledgerId(), SmsSendResult.rejected("Rejected"));

        assertEquals(1, fixture.account.getAlertReservedUnits());
        assertEquals(SmsUsageOutcome.RESTORED, ledger.getOutcome());
    }

    private static class Fixture {
        final StationSmsAccountRepository accountRepository = mock(StationSmsAccountRepository.class);
        final SmsUsageLedgerRepository ledgerRepository = mock(SmsUsageLedgerRepository.class);
        final PlatformSmsSettingsRepository settingsRepository = mock(PlatformSmsSettingsRepository.class);
        final SaccoStationRepository stationRepository = mock(SaccoStationRepository.class);
        final StationSmsAccount account;
        final SmsUnitTransactionService service;

        Fixture(long availableUnits) {
            account = StationSmsAccount.builder()
                .id(UUID.randomUUID())
                .saccoId("SACCO-1")
                .stationId("STN001")
                .availableUnits(availableUnits)
                .warningBaseline(1)
                .status(availableUnits == 0 ? SmsUnitStatus.DEPLETED : SmsUnitStatus.HEALTHY)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
            service = new SmsUnitTransactionService(accountRepository, ledgerRepository, settingsRepository, stationRepository);
            when(stationRepository.findBySaccoIdAndStationIdAndActiveTrue("SACCO-1", "STN001"))
                .thenReturn(Optional.of(SaccoStation.builder().saccoId("SACCO-1").stationId("STN001").active(true).build()));
            when(accountRepository.findForUpdate("SACCO-1", "STN001")).thenReturn(Optional.of(account));
            when(settingsRepository.findById(PlatformSmsSettings.DEFAULT_ID)).thenReturn(Optional.of(PlatformSmsSettings.builder()
                .id(PlatformSmsSettings.DEFAULT_ID)
                .lowPercent(20)
                .criticalPercent(10)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build()));
            when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(ledgerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        }

        SmsUsageLedger ledger(UUID id) {
            return SmsUsageLedger.builder()
                .id(id)
                .accountId(account.getId())
                .saccoId(account.getSaccoId())
                .stationId(account.getStationId())
                .unitChange(-1)
                .outcome(SmsUsageOutcome.RESERVED)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        }
    }
}
