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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SmsUnitTransactionService {
    public static final long ALERT_RESERVE_TARGET = 3;

    private final StationSmsAccountRepository accountRepository;
    private final SmsUsageLedgerRepository ledgerRepository;
    private final PlatformSmsSettingsRepository settingsRepository;
    private final SaccoStationRepository stationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationResult reserve(String saccoId, String stationId, UUID notificationId, String eventType) {
        String normalizedSaccoId = normalize(saccoId);
        String normalizedStationId = normalize(stationId);
        if (normalizedSaccoId == null || normalizedStationId == null) {
            writeBlocked(null, normalizedSaccoId, normalizedStationId, notificationId, eventType, "Missing originating SACCO-station scope");
            return ReservationResult.blocked("Missing originating SACCO-station scope", true, null, 0);
        }

        SaccoStation station = stationRepository.findBySaccoIdAndStationIdAndActiveTrue(normalizedSaccoId, normalizedStationId).orElse(null);
        if (station == null || station.isAccessSuspended()) {
            writeBlocked(null, normalizedSaccoId, normalizedStationId, notificationId, eventType, "Originating station is missing or inactive");
            return ReservationResult.blocked("Originating station is missing or inactive", true, null, 0);
        }

        StationSmsAccount account = accountRepository.findForUpdate(normalizedSaccoId, normalizedStationId)
            .orElseGet(() -> createAccount(normalizedSaccoId, normalizedStationId));
        if (account.getAvailableUnits() <= 0) {
            account.setStatus(SmsUnitStatus.DEPLETED);
            SmsUnitStatus alertStatus = markAlertIfNeeded(account, SmsUnitStatus.DEPLETED);
            account.setUpdatedAt(OffsetDateTime.now());
            accountRepository.save(account);
            writeBlocked(account.getId(), normalizedSaccoId, normalizedStationId, notificationId, eventType, "SMS units depleted");
            return ReservationResult.blocked("SMS units depleted", false, alertStatus, account.getAvailableUnits());
        }

        account.setAvailableUnits(account.getAvailableUnits() - 1);
        SmsUnitStatus status = resolveStatus(account.getAvailableUnits(), account.getWarningBaseline(), settings());
        account.setStatus(status);
        account.setUpdatedAt(OffsetDateTime.now());
        accountRepository.save(account);

        SmsUsageLedger ledger = ledgerRepository.save(SmsUsageLedger.builder()
            .id(UUID.randomUUID())
            .accountId(account.getId())
            .saccoId(account.getSaccoId())
            .stationId(account.getStationId())
            .notificationId(notificationId)
            .eventType(eventType)
            .unitChange(-1)
            .outcome(SmsUsageOutcome.RESERVED)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build());
        return ReservationResult.reserved(account.getId(), ledger.getId(), null, account.getAvailableUnits());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletionResult complete(UUID accountId, UUID ledgerId, SmsSendResult result) {
        StationSmsAccount account = accountRepository.findByIdForUpdate(accountId)
            .orElseThrow(() -> new IllegalStateException("SMS unit account not found"));
        SmsUsageLedger ledger = ledgerRepository.findByIdAndAccountId(ledgerId, accountId)
            .orElseThrow(() -> new IllegalStateException("SMS usage reservation not found"));
        if (ledger.getOutcome() != SmsUsageOutcome.RESERVED) {
            return new CompletionResult(account.getStatus(), account.getAvailableUnits(), null);
        }

        ledger.setProviderReference(trim(result.providerReference(), 500));
        ledger.setNote(trim(result.message(), 500));
        ledger.setUpdatedAt(OffsetDateTime.now());
        SmsUnitStatus alertStatus = null;
        if (result.consumesUnit()) {
            ledger.setOutcome(result.outcome() == SmsSendOutcome.ACCEPTED
                ? SmsUsageOutcome.ACCEPTED
                : SmsUsageOutcome.ACCEPTANCE_UNKNOWN);
            alertStatus = markAlertIfNeeded(account, account.getStatus());
            if (alertStatus != null) {
                account.setUpdatedAt(OffsetDateTime.now());
                accountRepository.save(account);
            }
        } else {
            ledger.setOutcome(SmsUsageOutcome.RESTORED);
            ledger.setUnitChange(0);
            account.setAvailableUnits(account.getAvailableUnits() + 1);
            account.setStatus(resolveStatus(account.getAvailableUnits(), account.getWarningBaseline(), settings()));
            account.setUpdatedAt(OffsetDateTime.now());
            accountRepository.save(account);
        }
        ledgerRepository.save(ledger);
        return new CompletionResult(account.getStatus(), account.getAvailableUnits(), alertStatus);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationResult reserveAlert(String saccoId, String stationId, SmsUnitStatus status) {
        String normalizedSaccoId = normalize(saccoId);
        String normalizedStationId = normalize(stationId);
        StationSmsAccount account = accountRepository.findForUpdate(normalizedSaccoId, normalizedStationId)
            .orElseThrow(() -> new IllegalStateException("SMS unit account not found"));
        if (account.getAlertReservedUnits() <= 0) {
            return ReservationResult.blocked("SMS alert reserve depleted", false, null, account.getAvailableUnits());
        }
        account.setAlertReservedUnits(account.getAlertReservedUnits() - 1);
        account.setUpdatedAt(OffsetDateTime.now());
        accountRepository.save(account);
        SmsUsageLedger ledger = ledgerRepository.save(SmsUsageLedger.builder()
            .id(UUID.randomUUID())
            .accountId(account.getId())
            .saccoId(account.getSaccoId())
            .stationId(account.getStationId())
            .eventType("SMS_USAGE_" + status.name() + "_ALERT")
            .unitChange(-1)
            .outcome(SmsUsageOutcome.RESERVED)
            .note("Station alert reserve")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build());
        return ReservationResult.reserved(account.getId(), ledger.getId(), null, account.getAvailableUnits());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeAlert(UUID accountId, UUID ledgerId, SmsSendResult result) {
        StationSmsAccount account = accountRepository.findByIdForUpdate(accountId)
            .orElseThrow(() -> new IllegalStateException("SMS unit account not found"));
        SmsUsageLedger ledger = ledgerRepository.findByIdAndAccountId(ledgerId, accountId)
            .orElseThrow(() -> new IllegalStateException("SMS usage reservation not found"));
        if (ledger.getOutcome() != SmsUsageOutcome.RESERVED) {
            return;
        }
        ledger.setProviderReference(trim(result.providerReference(), 500));
        ledger.setNote(trim(result.message(), 500));
        ledger.setUpdatedAt(OffsetDateTime.now());
        if (result.consumesUnit()) {
            ledger.setOutcome(result.outcome() == SmsSendOutcome.ACCEPTED
                ? SmsUsageOutcome.ACCEPTED
                : SmsUsageOutcome.ACCEPTANCE_UNKNOWN);
        } else {
            ledger.setOutcome(SmsUsageOutcome.RESTORED);
            ledger.setUnitChange(0);
            account.setAlertReservedUnits(account.getAlertReservedUnits() + 1);
            account.setUpdatedAt(OffsetDateTime.now());
            accountRepository.save(account);
        }
        ledgerRepository.save(ledger);
    }

    @Transactional
    public StationSmsAccount allocate(String saccoId, String stationId, long units, UUID actorMemberId, String note) {
        if (units <= 0) {
            throw new IllegalArgumentException("Enter a unit amount greater than zero.");
        }
        String normalizedNote = trim(note, 500);
        if (normalizedNote == null || normalizedNote.isBlank()) {
            throw new IllegalArgumentException("Enter an allocation note.");
        }
        String normalizedSaccoId = normalize(saccoId);
        String normalizedStationId = normalize(stationId);
        stationRepository.findBySaccoIdAndStationIdAndActiveTrue(normalizedSaccoId, normalizedStationId)
            .orElseThrow(() -> new IllegalArgumentException("Select a valid active SACCO-station."));
        StationSmsAccount account = accountRepository.findForUpdate(normalizedSaccoId, normalizedStationId)
            .orElseGet(() -> createAccount(normalizedSaccoId, normalizedStationId));
        long reserveDeficit = Math.max(0, ALERT_RESERVE_TARGET - account.getAlertReservedUnits());
        long reserveAddition = Math.min(units, reserveDeficit);
        long usableAddition = units - reserveAddition;
        long newBalance = Math.addExact(account.getAvailableUnits(), usableAddition);
        account.setAlertReservedUnits(account.getAlertReservedUnits() + reserveAddition);
        account.setAvailableUnits(newBalance);
        account.setWarningBaseline(newBalance);
        account.setStatus(resolveStatus(newBalance, newBalance, settings()));
        account.setLowAlertSent(false);
        account.setCriticalAlertSent(false);
        account.setDepletedAlertSent(false);
        account.setUpdatedAt(OffsetDateTime.now());
        accountRepository.save(account);
        ledgerRepository.save(SmsUsageLedger.builder()
            .id(UUID.randomUUID())
            .accountId(account.getId())
            .saccoId(account.getSaccoId())
            .stationId(account.getStationId())
            .unitChange(units)
            .outcome(SmsUsageOutcome.ALLOCATION)
            .actorMemberId(actorMemberId)
            .note(normalizedNote)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build());
        return account;
    }

    @Transactional
    public List<StatusAlert> updateThresholds(int lowPercent, int criticalPercent, UUID actorMemberId) {
        validateThresholds(lowPercent, criticalPercent);
        OffsetDateTime now = OffsetDateTime.now();
        PlatformSmsSettings settings = settingsRepository.findById(PlatformSmsSettings.DEFAULT_ID)
            .orElseGet(() -> defaultSettings(now));
        settings.setLowPercent(lowPercent);
        settings.setCriticalPercent(criticalPercent);
        settings.setUpdatedByMemberId(actorMemberId);
        settings.setUpdatedAt(now);
        settingsRepository.save(settings);

        List<StatusAlert> alerts = new ArrayList<>();
        for (StationSmsAccount account : accountRepository.findAllByOrderBySaccoIdAscStationIdAsc()) {
            SmsUnitStatus status = resolveStatus(account.getAvailableUnits(), account.getWarningBaseline(), settings);
            account.setStatus(status);
            SmsUnitStatus alertStatus = markAlertIfNeeded(account, status);
            account.setUpdatedAt(now);
            accountRepository.save(account);
            if (alertStatus != null) {
                alerts.add(new StatusAlert(account.getSaccoId(), account.getStationId(), alertStatus, account.getAvailableUnits()));
            }
        }
        return alerts;
    }

    @Transactional
    public StationSmsAccount ensureAccount(String saccoId, String stationId) {
        String normalizedSaccoId = normalize(saccoId);
        String normalizedStationId = normalize(stationId);
        return accountRepository.findBySaccoIdAndStationId(normalizedSaccoId, normalizedStationId)
            .orElseGet(() -> createAccount(normalizedSaccoId, normalizedStationId));
    }

    public PlatformSmsSettings settings() {
        OffsetDateTime now = OffsetDateTime.now();
        return settingsRepository.findById(PlatformSmsSettings.DEFAULT_ID)
            .orElseGet(() -> settingsRepository.save(defaultSettings(now)));
    }

    private StationSmsAccount createAccount(String saccoId, String stationId) {
        OffsetDateTime now = OffsetDateTime.now();
        return accountRepository.save(StationSmsAccount.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .stationId(stationId)
            .availableUnits(0)
            .alertReservedUnits(0)
            .warningBaseline(0)
            .status(SmsUnitStatus.DEPLETED)
            .createdAt(now)
            .updatedAt(now)
            .build());
    }

    private void writeBlocked(UUID accountId, String saccoId, String stationId, UUID notificationId, String eventType, String reason) {
        ledgerRepository.save(SmsUsageLedger.builder()
            .id(UUID.randomUUID())
            .accountId(accountId)
            .saccoId(saccoId)
            .stationId(stationId)
            .notificationId(notificationId)
            .eventType(eventType)
            .unitChange(0)
            .outcome(SmsUsageOutcome.BLOCKED)
            .note(reason)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build());
    }

    private SmsUnitStatus markAlertIfNeeded(StationSmsAccount account, SmsUnitStatus status) {
        return switch (status) {
            case HEALTHY -> null;
            case LOW -> {
                if (account.isLowAlertSent()) {
                    yield null;
                }
                account.setLowAlertSent(true);
                yield SmsUnitStatus.LOW;
            }
            case CRITICAL -> {
                if (account.isCriticalAlertSent()) {
                    yield null;
                }
                account.setCriticalAlertSent(true);
                yield SmsUnitStatus.CRITICAL;
            }
            case DEPLETED -> {
                if (account.isDepletedAlertSent()) {
                    yield null;
                }
                account.setDepletedAlertSent(true);
                yield SmsUnitStatus.DEPLETED;
            }
        };
    }

    private SmsUnitStatus resolveStatus(long available, long baseline, PlatformSmsSettings settings) {
        if (available <= 0 || baseline <= 0) {
            return SmsUnitStatus.DEPLETED;
        }
        double remainingPercent = (available * 100.0d) / baseline;
        if (remainingPercent <= settings.getCriticalPercent()) {
            return SmsUnitStatus.CRITICAL;
        }
        if (remainingPercent <= settings.getLowPercent()) {
            return SmsUnitStatus.LOW;
        }
        return SmsUnitStatus.HEALTHY;
    }

    private void validateThresholds(int lowPercent, int criticalPercent) {
        if (lowPercent < 1 || lowPercent > 99) {
            throw new IllegalArgumentException("Low-balance percentage must be between 1 and 99.");
        }
        if (criticalPercent < 1 || criticalPercent >= lowPercent) {
            throw new IllegalArgumentException("Critical percentage must be at least 1 and lower than the low-balance percentage.");
        }
    }

    private PlatformSmsSettings defaultSettings(OffsetDateTime now) {
        return PlatformSmsSettings.builder()
            .id(PlatformSmsSettings.DEFAULT_ID)
            .lowPercent(20)
            .criticalPercent(10)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    public record ReservationResult(
        boolean reserved,
        UUID accountId,
        UUID ledgerId,
        String reason,
        boolean invalidScope,
        SmsUnitStatus alertStatus,
        long availableUnits
    ) {
        static ReservationResult reserved(UUID accountId, UUID ledgerId, SmsUnitStatus alertStatus, long availableUnits) {
            return new ReservationResult(true, accountId, ledgerId, null, false, alertStatus, availableUnits);
        }

        static ReservationResult blocked(String reason, boolean invalidScope, SmsUnitStatus alertStatus, long availableUnits) {
            return new ReservationResult(false, null, null, reason, invalidScope, alertStatus, availableUnits);
        }
    }

    public record CompletionResult(SmsUnitStatus status, long availableUnits, SmsUnitStatus alertStatus) {
    }

    public record StatusAlert(String saccoId, String stationId, SmsUnitStatus status, long availableUnits) {
    }
}
