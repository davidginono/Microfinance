package com.sacco.mvp.service;

import com.sacco.mvp.domain.PlatformSmsSettings;
import com.sacco.mvp.domain.SmsUnitStatus;
import com.sacco.mvp.domain.SmsUsageLedger;
import com.sacco.mvp.domain.StationSmsAccount;
import com.sacco.mvp.repository.SmsUsageLedgerRepository;
import com.sacco.mvp.repository.StationSmsAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SmsUsageManagementService {
    private static final DateTimeFormatter USAGE_TIMESTAMP =
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private final SmsUnitTransactionService transactionService;
    private final SmsUsageAlertService alertService;
    private final StationSmsAccountRepository accountRepository;
    private final SmsUsageLedgerRepository ledgerRepository;

    public Page<StationSmsAccount> accounts(String saccoId, String stationId, SmsUnitStatus status, Pageable pageable) {
        String normalizedSaccoId = blankToNull(saccoId);
        String normalizedStationId = blankToNull(stationId);
        Specification<StationSmsAccount> filters = Specification.allOf();
        if (normalizedSaccoId != null) {
            filters = filters.and((root, query, builder) -> builder.equal(root.get("saccoId"), normalizedSaccoId));
        }
        if (normalizedStationId != null) {
            filters = filters.and((root, query, builder) -> builder.equal(root.get("stationId"), normalizedStationId));
        }
        if (status != null) {
            filters = filters.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        Pageable orderedPage = PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            Sort.by("saccoId").ascending().and(Sort.by("stationId").ascending())
        );
        return accountRepository.findAll(filters, orderedPage);
    }

    public StationSmsAccount account(String saccoId, String stationId) {
        return transactionService.ensureAccount(saccoId, stationId);
    }

    public StationSmsAccount account(UUID accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("SMS unit account not found."));
    }

    public Page<SmsUsageLedger> history(String saccoId, String stationId, Pageable pageable) {
        return ledgerRepository.findBySaccoIdAndStationIdOrderByCreatedAtDesc(saccoId, stationId, pageable);
    }

    public Page<SmsUsageLedger> history(UUID accountId, Pageable pageable) {
        return ledgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }

    public Page<SmsUsageRow> historyRows(String saccoId, String stationId, Pageable pageable) {
        return history(saccoId, stationId, pageable).map(this::toUsageRow);
    }

    public Page<SmsUsageRow> historyRows(UUID accountId, Pageable pageable) {
        return history(accountId, pageable).map(this::toUsageRow);
    }

    public PlatformSmsSettings settings() {
        return transactionService.settings();
    }

    public void allocate(String saccoId, String stationId, long units, UUID actorMemberId, String note) {
        transactionService.allocate(saccoId, stationId, units, actorMemberId, note);
    }

    public void updateThresholds(int lowPercent, int criticalPercent, UUID actorMemberId) {
        transactionService.updateThresholds(lowPercent, criticalPercent, actorMemberId)
            .forEach(alert -> alertService.alertStatus(alert.saccoId(), alert.stationId(), alert.status(), alert.availableUnits()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SmsUsageRow toUsageRow(SmsUsageLedger entry) {
        return new SmsUsageRow(
            formatTimestamp(entry.getCreatedAt()),
            formatTimestamp(entry.getLastOccurredAt() == null ? entry.getCreatedAt() : entry.getLastOccurredAt()),
            entry.getEventType(),
            entry.getOutcome(),
            entry.getEventCount(),
            entry.getUnitChange(),
            entry.getProviderReference(),
            entry.getNote()
        );
    }

    private String formatTimestamp(OffsetDateTime timestamp) {
        return timestamp == null ? "-" : timestamp.format(USAGE_TIMESTAMP);
    }

    public record SmsUsageRow(
        String createdAtLabel,
        String lastOccurredAtLabel,
        String eventType,
        com.sacco.mvp.domain.SmsUsageOutcome outcome,
        long eventCount,
        long unitChange,
        String providerReference,
        String note
    ) {
    }
}
