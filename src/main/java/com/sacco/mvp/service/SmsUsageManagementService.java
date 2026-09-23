package com.sacco.mvp.service;

import com.sacco.mvp.domain.PlatformSmsSettings;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.SmsUsageOutcome;
import com.sacco.mvp.domain.SmsUnitStatus;
import com.sacco.mvp.domain.SmsUsageLedger;
import com.sacco.mvp.domain.StationSmsAccount;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.SmsUsageLedgerRepository;
import com.sacco.mvp.repository.StationSmsAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SmsUsageManagementService {
    private static final DateTimeFormatter USAGE_TIMESTAMP =
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final int MAX_APPLICANT_FILTERS = 50;
    private static final int MAX_APPLICANT_SEARCH_RESULTS = 10;
    private static final UUID UNUSED_APPLICANT_ID = new UUID(0L, 0L);

    private final SmsUnitTransactionService transactionService;
    private final SmsUsageAlertService alertService;
    private final StationSmsAccountRepository accountRepository;
    private final SmsUsageLedgerRepository ledgerRepository;
    private final MemberRepository memberRepository;
    private final ApplicationClock applicationClock;
    private final WorkflowStatusPresentationService statusPresentationService;
    @Value("${app.sms-usage.export-max-rows:5000}")
    private int exportMaxRows;

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

    public Page<LoanSmsUsageRow> loanUsageRows(String saccoId, String stationId, Pageable pageable) {
        return loanUsageRows(loanUsageCriteria(saccoId, stationId, null, null, null, List.of()), pageable);
    }

    public Page<LoanSmsUsageRow> loanUsageRows(LoanSmsUsageCriteria criteria, Pageable pageable) {
        NormalizedLoanSmsUsageCriteria normalized = normalizeLoanUsageCriteria(criteria);
        return ledgerRepository.summarizeLoanSmsUsage(
            normalized.saccoId(),
            normalized.stationId(),
            normalized.fromAt(),
            normalized.toAt(),
            normalized.loanStatus(),
            normalized.applicantIds(),
            normalized.applicantFilterActive(),
            List.of(SmsUsageOutcome.ACCEPTED, SmsUsageOutcome.ACCEPTANCE_UNKNOWN),
            pageable
        ).map(this::toLoanUsageRow);
    }

    public LoanSmsUsageCriteria loanUsageCriteria(String saccoId,
                                                  String stationId,
                                                  LocalDate fromDate,
                                                  LocalDate toDate,
                                                  LoanStatus loanStatus,
                                                  Collection<UUID> applicantIds) {
        return new LoanSmsUsageCriteria(
            blankToNull(saccoId),
            blankToNull(stationId),
            fromDate,
            toDate,
            loanStatus,
            normalizeApplicantIds(applicantIds)
        );
    }

    public List<LoanStatusOption> loanStatusOptions() {
        List<LoanStatusOption> options = new ArrayList<>();
        for (LoanStatus status : LoanStatus.values()) {
            options.add(new LoanStatusOption(status.name(), statusPresentationService.dashboardStatusLabel(status)));
        }
        return options;
    }

    public List<ApplicantFilterOption> selectedLoanUsageApplicants(LoanSmsUsageCriteria criteria) {
        NormalizedLoanSmsUsageCriteria normalized = normalizeLoanUsageCriteria(criteria);
        if (!normalized.applicantFilterActive()) {
            return List.of();
        }
        Map<UUID, Integer> order = new LinkedHashMap<>();
        for (int i = 0; i < normalized.originalApplicantIds().size(); i++) {
            order.putIfAbsent(normalized.originalApplicantIds().get(i), i);
        }
        return memberRepository.findAllById(order.keySet()).stream()
            .filter(member -> matchesScope(member, normalized.saccoId(), normalized.stationId()))
            .sorted(Comparator.comparingInt(member -> order.getOrDefault(member.getId(), Integer.MAX_VALUE)))
            .map(this::toApplicantFilterOption)
            .toList();
    }

    public List<ApplicantFilterOption> searchLoanUsageApplicants(String saccoId, String stationId, String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.length() < 2) {
            return List.of();
        }
        return ledgerRepository.searchLoanSmsUsageApplicants(
                blankToNull(saccoId),
                blankToNull(stationId),
                normalizedQuery,
                List.of(SmsUsageOutcome.ACCEPTED, SmsUsageOutcome.ACCEPTANCE_UNKNOWN),
                PageRequest.of(0, MAX_APPLICANT_SEARCH_RESULTS)
            ).stream()
            .map(this::toApplicantFilterOption)
            .toList();
    }

    public LoanSmsUsageExportReport loanUsageExport(LoanSmsUsageCriteria criteria, String preparedBy) {
        NormalizedLoanSmsUsageCriteria normalized = normalizeLoanUsageCriteria(criteria);
        int safeMaxRows = Math.max(1, exportMaxRows);
        List<LoanSmsUsageRow> rows = ledgerRepository.exportLoanSmsUsage(
                normalized.saccoId(),
                normalized.stationId(),
                normalized.fromAt(),
                normalized.toAt(),
                normalized.loanStatus(),
                normalized.applicantIds(),
                normalized.applicantFilterActive(),
                List.of(SmsUsageOutcome.ACCEPTED, SmsUsageOutcome.ACCEPTANCE_UNKNOWN),
                PageRequest.of(0, safeMaxRows + 1)
            ).stream()
            .map(this::toLoanUsageRow)
            .toList();
        if (rows.size() > safeMaxRows) {
            throw new IllegalStateException("The SMS usage export is too large. Narrow the filters and try again.");
        }
        long totalEvents = rows.stream().mapToLong(LoanSmsUsageRow::smsEventCount).sum();
        long totalUnits = rows.stream().mapToLong(LoanSmsUsageRow::unitsUsed).sum();
        return new LoanSmsUsageExportReport(
            normalized.saccoId(),
            normalized.stationId(),
            criteria == null ? null : criteria.fromDate(),
            criteria == null ? null : criteria.toDate(),
            normalized.loanStatus() == null ? "All loan statuses" : statusPresentationService.dashboardStatusLabel(normalized.loanStatus()),
            applicantSummary(selectedLoanUsageApplicants(criteria)),
            preparedBy == null || preparedBy.isBlank() ? "System" : preparedBy,
            applicationClock.today(),
            rows,
            totalEvents,
            totalUnits
        );
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

    private NormalizedLoanSmsUsageCriteria normalizeLoanUsageCriteria(LoanSmsUsageCriteria criteria) {
        LoanSmsUsageCriteria safeCriteria = criteria == null
            ? loanUsageCriteria(null, null, null, null, null, List.of())
            : criteria;
        if (safeCriteria.fromDate() != null && safeCriteria.toDate() != null && safeCriteria.fromDate().isAfter(safeCriteria.toDate())) {
            throw new IllegalArgumentException("Filter From cannot be later than Filter To.");
        }
        List<UUID> applicantIds = normalizeApplicantIds(safeCriteria.applicantIds());
        boolean applicantFilterActive = !applicantIds.isEmpty();
        return new NormalizedLoanSmsUsageCriteria(
            blankToNull(safeCriteria.saccoId()),
            blankToNull(safeCriteria.stationId()),
            applicationClock.startOfDay(safeCriteria.fromDate()),
            applicationClock.dayAfter(safeCriteria.toDate()),
            safeCriteria.loanStatus(),
            applicantFilterActive ? applicantIds : List.of(UNUSED_APPLICANT_ID),
            applicantIds,
            applicantFilterActive
        );
    }

    private List<UUID> normalizeApplicantIds(Collection<UUID> applicantIds) {
        if (applicantIds == null || applicantIds.isEmpty()) {
            return List.of();
        }
        return applicantIds.stream()
            .filter(Objects::nonNull)
            .distinct()
            .limit(MAX_APPLICANT_FILTERS)
            .toList();
    }

    private boolean matchesScope(Member member, String saccoId, String stationId) {
        if (member == null) {
            return false;
        }
        if (saccoId != null && !saccoId.equals(member.getSaccoId())) {
            return false;
        }
        return stationId == null || (member.getStationId() != null && member.getStationId().equalsIgnoreCase(stationId));
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
        return timestamp == null ? "-" : applicationClock.zoned(timestamp).format(USAGE_TIMESTAMP);
    }

    private LoanSmsUsageRow toLoanUsageRow(SmsUsageLedgerRepository.LoanSmsUsageProjection entry) {
        return new LoanSmsUsageRow(
            entry.getLoanApplicationId(),
            entry.getApplicationNumber(),
            blankToDash(entry.getLoanId()),
            entry.getLoanStatus() == null ? "-" : statusPresentationService.dashboardStatusLabel(entry.getLoanStatus()),
            entry.getLoanStatus() == null ? "" : entry.getLoanStatus().name(),
            blankToDash(entry.getSaccoId()),
            blankToDash(entry.getStationId()),
            entry.getApplicantMemberId(),
            blankToDash(entry.getApplicantName()),
            blankToDash(entry.getApplicantMemberNo()),
            entry.getSmsEventCount(),
            entry.getUnitsUsed(),
            formatTimestamp(entry.getLastSmsAt())
        );
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private ApplicantFilterOption toApplicantFilterOption(Member member) {
        return new ApplicantFilterOption(
            member.getId(),
            applicantLabel(member.getFullName(), member.getMemberNo(), member.getStaffNo()),
            blankToDash(member.getMemberNo()),
            blankToDash(member.getStaffNo()),
            blankToDash(member.getSaccoId()),
            blankToDash(member.getStationId())
        );
    }

    private ApplicantFilterOption toApplicantFilterOption(SmsUsageLedgerRepository.LoanSmsApplicantProjection applicant) {
        return new ApplicantFilterOption(
            applicant.getApplicantMemberId(),
            applicantLabel(applicant.getApplicantName(), applicant.getApplicantMemberNo(), applicant.getApplicantStaffNo()),
            blankToDash(applicant.getApplicantMemberNo()),
            blankToDash(applicant.getApplicantStaffNo()),
            blankToDash(applicant.getSaccoId()),
            blankToDash(applicant.getStationId())
        );
    }

    private String applicantLabel(String name, String memberNo, String staffNo) {
        String displayName = blankToDash(name);
        String identifier = !blankToDash(memberNo).equals("-") ? memberNo : staffNo;
        return identifier == null || identifier.isBlank() ? displayName : displayName + " (" + identifier + ")";
    }

    private String applicantSummary(List<ApplicantFilterOption> applicants) {
        if (applicants == null || applicants.isEmpty()) {
            return "All applicants";
        }
        if (applicants.size() == 1) {
            return applicants.get(0).label();
        }
        return applicants.size() + " selected applicants";
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

    public record LoanSmsUsageRow(
        UUID loanApplicationId,
        Long applicationNumber,
        String loanId,
        String loanStatusLabel,
        String loanStatus,
        String saccoId,
        String stationId,
        UUID applicantMemberId,
        String applicantName,
        String applicantMemberNo,
        long smsEventCount,
        long unitsUsed,
        String lastSmsAtLabel
    ) {
    }

    public record LoanSmsUsageCriteria(
        String saccoId,
        String stationId,
        LocalDate fromDate,
        LocalDate toDate,
        LoanStatus loanStatus,
        List<UUID> applicantIds
    ) {
    }

    private record NormalizedLoanSmsUsageCriteria(
        String saccoId,
        String stationId,
        OffsetDateTime fromAt,
        OffsetDateTime toAt,
        LoanStatus loanStatus,
        List<UUID> applicantIds,
        List<UUID> originalApplicantIds,
        boolean applicantFilterActive
    ) {
    }

    public record LoanStatusOption(String value, String label) {
    }

    public record ApplicantFilterOption(
        UUID id,
        String label,
        String memberNo,
        String staffNo,
        String saccoId,
        String stationId
    ) {
    }

    public record LoanSmsUsageExportReport(
        String saccoId,
        String stationId,
        LocalDate fromDate,
        LocalDate toDate,
        String loanStatusLabel,
        String applicantSummary,
        String preparedBy,
        LocalDate generatedDate,
        List<LoanSmsUsageRow> rows,
        long totalSmsEvents,
        long totalUnitsUsed
    ) {
    }
}
