package com.sacco.mvp.service;

import com.sacco.mvp.domain.AuditLog;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.OutboxStatus;
import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.domain.SaccoAccessStatus;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.domain.SavingsAccount;
import com.sacco.mvp.repository.AuditLogRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.OutboxEventRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.repository.SavingsAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformAdminService {
    private static final long RECENT_WINDOW_DAYS = 30L;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal GREEN_LIQUIDITY_THRESHOLD = new BigDecimal("1.20");
    private static final BigDecimal AMBER_LIQUIDITY_THRESHOLD = new BigDecimal("1.00");
    private static final BigDecimal GREEN_DEFAULT_THRESHOLD = new BigDecimal("8.00");
    private static final BigDecimal RED_DEFAULT_THRESHOLD = new BigDecimal("12.00");
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final RegisteredSaccoRepository registeredSaccoRepository;
    private final SaccoStationRepository saccoStationRepository;
    private final MemberRepository memberRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final SavingsAccountRepository savingsAccountRepository;
    private final AuditLogRepository auditLogRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final SaccoLogoStorageService saccoLogoStorageService;

    public PlatformDashboard dashboard() {
        PortfolioSnapshot snapshot = buildSnapshot();
        long healthyCount = snapshot.summaries().stream().filter(summary -> "green".equals(summary.healthTone())).count();
        long atRiskCount = snapshot.summaries().stream()
            .filter(summary -> !"green".equals(summary.healthTone()) && !summary.newPortfolio())
            .count();
        long newCount = snapshot.summaries().stream().filter(SaccoSummary::newPortfolio).count();
        BigDecimal totalDisbursed = snapshot.summaries().stream()
            .map(SaccoSummary::totalDisbursedPrincipal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PlatformDashboard(
            snapshot.summaries(),
            snapshot.summaries().size(),
            snapshot.totalMembers(),
            totalDisbursed,
            healthyCount,
            atRiskCount,
            newCount,
            outboxEventRepository.countByStatus(OutboxStatus.FAILED),
            recentAuditItems(auditLogRepository.findTop100ByOrderByCreatedAtDesc(), 10)
        );
    }

    public SaccoDetailView saccoDetail(String saccoId) {
        return saccoDetail(saccoId, null);
    }

    public SaccoDetailView saccoDetail(String saccoId, String stationId) {
        String normalizedSaccoId = normalizeSaccoId(saccoId);
        String normalizedStationId = normalizeOptional(stationId);
        // Deliberately no platform-wide snapshot here: this view only renders one SACCO, and
        // the active-registration lookup above is the same existence check the snapshot gave.
        RegisteredSacco sacco = registeredSaccoRepository.findById(normalizedSaccoId)
            .filter(RegisteredSacco::isActive)
            .orElseThrow(() -> new IllegalArgumentException("SACCO not found."));

        List<SaccoStation> activeStations = saccoStationRepository.findBySaccoIdAndActiveTrueOrderByStationIdAsc(normalizedSaccoId);
        List<String> stationOptions = activeStations.stream()
            .map(station -> station.getStationId())
            .toList();
        if (normalizedStationId != null && !stationOptions.contains(normalizedStationId)) {
            throw new IllegalArgumentException("Station not found for this SACCO.");
        }

        Map<com.sacco.mvp.domain.MemberStatus, Long> memberStatusCounts = memberRepository
            .countByStatusForScope(normalizedSaccoId, normalizedStationId).stream()
            .collect(Collectors.toMap(MemberRepository.StatusCountProjection::getStatus,
                MemberRepository.StatusCountProjection::getTotal));
        MemberStats memberStats = new MemberStats(
            memberStatusCounts.values().stream().mapToLong(Long::longValue).sum(),
            memberStatusCounts.getOrDefault(com.sacco.mvp.domain.MemberStatus.ACTIVE, 0L),
            memberStatusCounts.getOrDefault(com.sacco.mvp.domain.MemberStatus.INACTIVE, 0L)
        );
        LoanStats loanStats = loanApplicationRepository.summarizeLoansForScope(normalizedSaccoId, normalizedStationId)
            .map(this::toLoanStats)
            .orElse(LoanStats.empty());
        List<LoanStatusCount> loanStatusCounts = loanStatusCountsFor(normalizedSaccoId, normalizedStationId);
        long totalLoanApplicationCount = loanStatusCounts.stream()
            .mapToLong(LoanStatusCount::count)
            .sum();
        List<LoanApplication> recentLoanEntities = loanApplicationRepository.findRecentForScope(
            normalizedSaccoId, normalizedStationId, PageRequest.of(0, 10));
        Map<UUID, Member> recentApplicantsById = memberRepository.findAllById(recentLoanEntities.stream()
                .map(LoanApplication::getApplicantMemberId)
                .collect(Collectors.toSet())).stream()
            .collect(Collectors.toMap(Member::getId, member -> member));
        SaccoSettings settings = saccoSettingsRepository.findById(normalizedSaccoId).orElse(null);
        SaccoSummary summary = buildSummary(
            sacco,
            normalizedStationId == null ? stationOptions : List.of(normalizedStationId),
            memberStats,
            loanStats,
            safeAmount(savingsAccountRepository.sumSavingsForScope(normalizedSaccoId, normalizedStationId)),
            settings,
            resolveStationAccess(activeStations, normalizedStationId)
        );

        List<LoanItem> recentLoans = recentLoanEntities.stream()
            .map(loan -> new LoanItem(
                loan.getId(),
                resolveApplicantName(loan.getApplicantMemberId(), recentApplicantsById),
                loan.getStatus(),
                safeAmount(loan.getAmount()),
                loan.getUpdatedAt(),
                loan.getFinalDueDate()
            ))
            .toList();

        List<AuditItem> relatedAudit = auditLogRepository.searchEventLogViewScoped(
                null, null, null, normalizedSaccoId, normalizedStationId, PageRequest.of(0, 12))
            .getContent().stream()
            .map(this::toAuditItem)
            .toList();

        return new SaccoDetailView(
            sacco.getSaccoId(),
            summary,
            recentLoans,
            loanStatusCounts,
            totalLoanApplicationCount,
            relatedAudit,
            loanStats.paidLoanCount(),
            loanStats.overdueLoanCount(),
            stationOptions,
            normalizedStationId
        );
    }

    public String normalizeSection(String section) {
        if (section == null || section.isBlank()) {
            return "overview";
        }
        String normalized = section.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "overview", "loans", "members", "financials", "sms", "audit" -> normalized;
            default -> "overview";
        };
    }

    private PortfolioSnapshot buildSnapshot() {
        List<RegisteredSacco> registeredSaccos = registeredSaccoRepository.findByActiveTrueOrderBySaccoNameAsc();
        if (registeredSaccos.isEmpty()) {
            return new PortfolioSnapshot(List.of(), 0);
        }

        Set<String> saccoIds = registeredSaccos.stream().map(RegisteredSacco::getSaccoId).collect(Collectors.toSet());
        Map<String, MemberStats> memberStatsBySacco = memberRepository.summarizeMembersBySacco(saccoIds).stream()
            .collect(Collectors.toMap(
                MemberRepository.SaccoMemberStatsProjection::getSaccoId,
                row -> new MemberStats(row.getTotalMembers(), row.getActiveMembers(), row.getInactiveMembers()),
                (left, right) -> left,
                LinkedHashMap::new
            ));
        Map<String, LoanStats> loanStatsBySacco = loanApplicationRepository.summarizeLoansBySacco(saccoIds).stream()
            .collect(Collectors.toMap(
                LoanApplicationRepository.SaccoLoanStatsProjection::getSaccoId,
                row -> new LoanStats(
                    row.getActiveLoanCount(),
                    row.getPaidLoanCount(),
                    row.getOverdueLoanCount(),
                    safeAmount(row.getTotalDisbursedPrincipal()),
                    safeAmount(row.getPaidPrincipal()),
                    safeAmount(row.getActiveExposure()),
                    safeAmount(row.getOverduePrincipal())
                ),
                (left, right) -> left,
                LinkedHashMap::new
            ));
        Map<String, BigDecimal> savingsBySacco = savingsAccountRepository.summarizeSavingsBySacco(saccoIds).stream()
            .collect(Collectors.toMap(
                SavingsAccountRepository.SaccoSavingsProjection::getSaccoId,
                row -> safeAmount(row.getTotalSavings()),
                (left, right) -> left,
                LinkedHashMap::new
            ));

        List<SaccoSummary> summaries = new ArrayList<>();
        Map<String, SaccoSettings> settingsBySacco = saccoSettingsRepository.findAllById(saccoIds).stream()
            .collect(Collectors.toMap(SaccoSettings::getSaccoId, settings -> settings, (left, right) -> left, LinkedHashMap::new));
        Map<String, List<SaccoStation>> stationsBySacco = saccoStationRepository
            .findBySaccoIdInAndActiveTrueOrderBySaccoIdAscStationIdAsc(saccoIds).stream()
            .collect(Collectors.groupingBy(SaccoStation::getSaccoId, LinkedHashMap::new, Collectors.toList()));
        for (RegisteredSacco sacco : registeredSaccos) {
            List<SaccoStation> activeStations = stationsBySacco.getOrDefault(sacco.getSaccoId(), List.of());
            List<String> stationIds = activeStations.stream()
                .map(station -> station.getStationId())
                .toList();
            SaccoSummary summary = buildSummary(
                sacco,
                stationIds,
                memberStatsBySacco.getOrDefault(sacco.getSaccoId(), MemberStats.empty()),
                loanStatsBySacco.getOrDefault(sacco.getSaccoId(), LoanStats.empty()),
                savingsBySacco.getOrDefault(sacco.getSaccoId(), BigDecimal.ZERO),
                settingsBySacco.get(sacco.getSaccoId()),
                resolveStationAccess(activeStations, null)
            );
            summaries.add(summary);
        }

        int totalMembers = memberStatsBySacco.values().stream()
            .mapToInt(stats -> Math.toIntExact(stats.totalMembers()))
            .sum();
        return new PortfolioSnapshot(summaries, totalMembers);
    }

    private SaccoSummary buildSummary(RegisteredSacco sacco,
                                      List<String> stationIds,
                                      MemberStats memberStats,
                                      LoanStats loanStats,
                                      BigDecimal totalSavings,
                                      SaccoSettings settings,
                                      StationAccessSnapshot stationAccess) {
        BigDecimal totalDisbursed = safeAmount(loanStats.totalDisbursedPrincipal());
        BigDecimal paidPrincipal = safeAmount(loanStats.paidPrincipal());
        BigDecimal activeExposure = safeAmount(loanStats.activeExposure());
        BigDecimal overduePrincipal = safeAmount(loanStats.overduePrincipal());

        BigDecimal repaymentPercent = totalDisbursed.signum() == 0 ? null : ratioAsPercent(paidPrincipal, totalDisbursed);
        BigDecimal defaultPercent = totalDisbursed.signum() == 0 ? null : ratioAsPercent(overduePrincipal, totalDisbursed);
        BigDecimal liquidityRatio = (totalDisbursed.signum() == 0 || activeExposure.signum() == 0) ? null : ratio(totalSavings, activeExposure);

        StatusMeta statusMeta = resolveStatus(totalDisbursed, defaultPercent, liquidityRatio);

        return new SaccoSummary(
            sacco.getSaccoId(),
            sacco.getSaccoName(),
            stationIds,
            saccoLogoStorageService.hasLogo(sacco.getSaccoId()),
            saccoLogoStorageService.publicLogoUrl(sacco.getSaccoId(), sacco.getUpdatedAt()),
            Math.toIntExact(memberStats.totalMembers()),
            memberStats.activeMembers(),
            memberStats.inactiveMembers(),
            Math.toIntExact(loanStats.activeLoanCount()),
            totalDisbursed,
            totalSavings,
            activeExposure,
            repaymentPercent,
            defaultPercent,
            liquidityRatio,
            statusMeta.label(),
            statusMeta.tone(),
            statusMeta.note(),
            totalDisbursed.signum() == 0,
            stationAccess.accessStatus(),
            stationAccess.paymentDueDate(),
            stationAccess.accessSuspendedAt(),
            stationAccess.accessRestrictionReason(),
            settings == null ? "en" : settings.getDefaultLanguage()
        );
    }

    private StationAccessSnapshot resolveStationAccess(List<SaccoStation> stations, String stationId) {
        List<SaccoStation> candidates = stations == null ? List.of() : stations;
        if (stationId != null && !stationId.isBlank()) {
            return candidates.stream()
                .filter(station -> stationId.equalsIgnoreCase(station.getStationId()))
                .findFirst()
                .map(station -> new StationAccessSnapshot(
                    station.getResolvedAccessStatus(),
                    station.getPaymentDueDate(),
                    station.getAccessSuspendedAt(),
                    station.getAccessRestrictionReason()
                ))
                .orElse(StationAccessSnapshot.active());
        }
        List<SaccoStation> suspended = candidates.stream()
            .filter(SaccoStation::isAccessSuspended)
            .toList();
        if (!suspended.isEmpty()) {
            OffsetDateTime suspendedAt = suspended.stream()
                .map(SaccoStation::getAccessSuspendedAt)
                .filter(value -> value != null)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
            return new StationAccessSnapshot(
                SaccoAccessStatus.SUSPENDED,
                suspended.stream().map(SaccoStation::getPaymentDueDate).filter(value -> value != null).min(LocalDate::compareTo).orElse(null),
                suspendedAt,
                suspended.size() == 1
                    ? suspended.getFirst().getStationId() + ": " + nullSafeReason(suspended.getFirst().getAccessRestrictionReason())
                    : suspended.size() + " stations suspended"
            );
        }
        boolean paymentDue = candidates.stream()
            .anyMatch(station -> station.getResolvedAccessStatus() == SaccoAccessStatus.PAYMENT_DUE);
        if (paymentDue) {
            return new StationAccessSnapshot(
                SaccoAccessStatus.PAYMENT_DUE,
                candidates.stream().map(SaccoStation::getPaymentDueDate).filter(value -> value != null).min(LocalDate::compareTo).orElse(null),
                null,
                null
            );
        }
        return StationAccessSnapshot.active();
    }

    private String nullSafeReason(String reason) {
        return reason == null || reason.isBlank() ? "Access suspended" : reason.trim();
    }

    private List<AuditItem> recentAuditItems(List<AuditLog> auditLogs, int limit) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(RECENT_WINDOW_DAYS);
        return auditLogs.stream()
            .filter(entry -> entry.getCreatedAt() != null && !entry.getCreatedAt().isBefore(cutoff))
            .limit(limit)
            .map(this::toAuditItem)
            .toList();
    }

    private AuditItem toAuditItem(AuditLog entry) {
        return new AuditItem(
            entry.getDisplayAction(),
            entry.getDisplayEntityType(),
            entry.getActorReferenceLabel(),
            entry.getCreatedAtLabel()
        );
    }

    private String resolveApplicantName(UUID applicantMemberId, Map<UUID, Member> membersById) {
        Member applicant = membersById.get(applicantMemberId);
        return applicant == null ? "Member " + shortId(applicantMemberId) : applicant.getFullName();
    }

    private StatusMeta resolveStatus(BigDecimal totalDisbursed, BigDecimal defaultPercent, BigDecimal liquidityRatio) {
        if (totalDisbursed == null || totalDisbursed.signum() == 0) {
            return new StatusMeta("New", "amber", "No disbursed portfolio yet.");
        }
        if ((liquidityRatio != null && liquidityRatio.compareTo(AMBER_LIQUIDITY_THRESHOLD) < 0)
            || (defaultPercent != null && defaultPercent.compareTo(RED_DEFAULT_THRESHOLD) >= 0)) {
            return new StatusMeta("At Risk", "red", "Weak liquidity or high defaults need attention.");
        }
        if ((liquidityRatio != null && liquidityRatio.compareTo(GREEN_LIQUIDITY_THRESHOLD) < 0)
            || (defaultPercent != null && defaultPercent.compareTo(GREEN_DEFAULT_THRESHOLD) >= 0)) {
            return new StatusMeta("Watch", "amber", "Monitor liquidity and repayment performance.");
        }
        return new StatusMeta("Active", "green", "Portfolio is within the healthy operating range.");
    }

    private static BigDecimal ratioAsPercent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return null;
        }
        return safeAmount(numerator).multiply(ONE_HUNDRED).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return null;
        }
        return safeAmount(numerator).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumLoanAmounts(List<LoanApplication> loans) {
        return loans.stream()
            .map(LoanApplication::getAmount)
            .map(PlatformAdminService::safeAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LoanStats toLoanStats(LoanApplicationRepository.SaccoLoanStatsProjection row) {
        return new LoanStats(
            row.getActiveLoanCount(),
            row.getPaidLoanCount(),
            row.getOverdueLoanCount(),
            safeAmount(row.getTotalDisbursedPrincipal()),
            safeAmount(row.getPaidPrincipal()),
            safeAmount(row.getActiveExposure()),
            safeAmount(row.getOverduePrincipal())
        );
    }

    private List<LoanStatusCount> loanStatusCountsFor(String saccoId, String stationId) {
        Map<LoanStatus, Long> counts = new EnumMap<>(LoanStatus.class);
        loanApplicationRepository.countByStatusForScope(saccoId, stationId).forEach(row -> {
            if (row.getStatus() != null) {
                counts.put(row.getStatus(), row.getTotal());
            }
        });
        List<LoanStatusCount> rows = new ArrayList<>();
        for (LoanStatus status : LoanStatus.values()) {
            long count = counts.getOrDefault(status, 0L);
            if (count > 0) {
                rows.add(new LoanStatusCount(status, formatLoanStatusLabel(status), count));
            }
        }
        return rows;
    }

    private static BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private static String normalizeSaccoId(String saccoId) {
        return saccoId == null ? "" : saccoId.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String shortId(UUID id) {
        return id == null ? "N/A" : id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static String formatCompactMoney(BigDecimal amount) {
        BigDecimal safeAmount = safeAmount(amount);
        BigDecimal absolute = safeAmount.abs();
        BigDecimal divisor = BigDecimal.ONE;
        String suffix = "";
        if (absolute.compareTo(new BigDecimal("1000000000")) >= 0) {
            divisor = new BigDecimal("1000000000");
            suffix = "B";
        } else if (absolute.compareTo(new BigDecimal("1000000")) >= 0) {
            divisor = new BigDecimal("1000000");
            suffix = "M";
        } else if (absolute.compareTo(new BigDecimal("1000")) >= 0) {
            divisor = new BigDecimal("1000");
            suffix = "K";
        }
        BigDecimal display = safeAmount.divide(divisor, suffix.isEmpty() ? 0 : 1, RoundingMode.HALF_UP);
        return "TZS " + display.toPlainString() + suffix;
    }

    private static String formatFullMoney(BigDecimal amount) {
        java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0.##", new java.text.DecimalFormatSymbols(Locale.US));
        return "TZS " + format.format(safeAmount(amount).setScale(2, RoundingMode.HALF_UP));
    }

    private static String formatPercent(BigDecimal percent) {
        if (percent == null) {
            return "—";
        }
        return percent.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String formatRatio(BigDecimal ratio) {
        if (ratio == null) {
            return "—";
        }
        return ratio.setScale(2, RoundingMode.HALF_UP).toPlainString() + "x";
    }

    private static String formatLoanStatusLabel(LoanStatus status) {
        if (status == null) {
            return "Unknown";
        }
        return switch (status) {
            case SUBMITTED -> "Submitted";
            case READY_FOR_MANAGER -> "On Review By Manager";
            case AWAITING_LOAN_OFFICER -> "On Review By Loan Officer";
            case AWAITING_CHAIRPERSON -> "On Review By Chairperson";
            case AWAITING_BOARD -> "On Review By Board";
            case AWAITING_CREDIT_COMMITTEE -> "On Review By Credit Committee";
            case AWAITING_ACCOUNTANT -> "On Review By Accountant";
            case READY_FOR_DISBURSEMENT, MANAGER_ACCEPTED -> "Ready for Disbursement";
            case DISBURSED -> "Disbursed";
            case PAR -> "Portfolio At Risk";
            case DEFAULTED -> "Defaulted / Not Paid";
            case PAID -> "Paid";
            case MANAGER_REJECTED -> "Manager Rejected";
            case LOAN_OFFICER_REJECTED -> "Loan Officer Rejected";
            case CHAIRPERSON_REJECTED -> "Chairperson Rejected";
            case BOARD_REJECTED -> "Board Rejected";
            case CREDIT_COMMITTEE_REJECTED -> "Credit Committee Rejected";
            case ACCOUNTANT_REJECTED -> "Accountant Rejected";
            case REJECTED -> "Rejected";
            case ALL_GUARANTORS_APPROVED -> "All Guarantors Approved";
            case AWAITING_GUARANTORS -> "Awaiting Guarantors";
            case BOARD_APPROVED -> "Reviewed By Board";
            case CREDIT_COMMITTEE_APPROVED -> "Reviewed By Credit Committee";
            case CHAIRPERSON_APPROVED -> "Reviewed By Chairperson";
            case LOAN_OFFICER_APPROVED -> "Reviewed By Loan Officer";
            case ACCOUNTANT_APPROVED -> "Reviewed By Accountant";
            case DRAFT -> "Draft";
        };
    }

    private static String initialsForName(String name) {
        if (name == null || name.isBlank()) {
            return "SC";
        }
        List<String> letters = new ArrayList<>();
        for (String part : name.trim().split("\\s+")) {
            if (part.isBlank()) {
                continue;
            }
            char initial = Character.toUpperCase(part.charAt(0));
            if (Character.isLetterOrDigit(initial)) {
                letters.add(String.valueOf(initial));
            }
            if (letters.size() == 2) {
                break;
            }
        }
        if (letters.isEmpty()) {
            return "SC";
        }
        if (letters.size() == 1) {
            return letters.get(0) + "S";
        }
        return String.join("", letters);
    }

    public record PlatformDashboard(
        List<SaccoSummary> saccos,
        int totalSaccos,
        int totalMembers,
        BigDecimal totalDisbursedPrincipal,
        long healthySaccos,
        long atRiskSaccos,
        long newSaccos,
        long failedOutboxCount,
        List<AuditItem> recentAuditEntries
    ) {
        public List<SaccoSummary> getSaccos() {
            return saccos;
        }

        public int getTotalSaccos() {
            return totalSaccos;
        }

        public int getTotalMembers() {
            return totalMembers;
        }

        public BigDecimal getTotalDisbursedPrincipal() {
            return totalDisbursedPrincipal;
        }

        public long getHealthySaccos() {
            return healthySaccos;
        }

        public long getAtRiskSaccos() {
            return atRiskSaccos;
        }

        public long getNewSaccos() {
            return newSaccos;
        }

        public long getFailedOutboxCount() {
            return failedOutboxCount;
        }

        public List<AuditItem> getRecentAuditEntries() {
            return recentAuditEntries;
        }

        public String getTotalDisbursedPrincipalLabel() {
            return PlatformAdminService.formatCompactMoney(totalDisbursedPrincipal);
        }

        public String totalDisbursedPrincipalLabel() {
            return getTotalDisbursedPrincipalLabel();
        }
    }

    public record SaccoDetailView(
        String saccoId,
        SaccoSummary summary,
        List<LoanItem> recentLoans,
        List<LoanStatusCount> loanStatusCounts,
        long totalLoanApplicationCount,
        List<AuditItem> recentAuditEntries,
        long paidLoanCount,
        long overdueLoanCount,
        List<String> stationOptions,
        String selectedStationId
    ) {
        public String getSaccoId() {
            return saccoId;
        }

        public SaccoSummary getSummary() {
            return summary;
        }

        public List<LoanItem> getRecentLoans() {
            return recentLoans;
        }

        public List<LoanStatusCount> getLoanStatusCounts() {
            return loanStatusCounts;
        }

        public long getTotalLoanApplicationCount() {
            return totalLoanApplicationCount;
        }

        public long totalLoanApplicationCount() {
            return getTotalLoanApplicationCount();
        }

        public List<AuditItem> getRecentAuditEntries() {
            return recentAuditEntries;
        }

        public long getPaidLoanCount() {
            return paidLoanCount;
        }

        public long getOverdueLoanCount() {
            return overdueLoanCount;
        }

        public List<String> getStationOptions() {
            return stationOptions;
        }

        public String getSelectedStationId() {
            return selectedStationId;
        }

        public boolean isStationScoped() {
            return selectedStationId != null && !selectedStationId.isBlank();
        }

        public boolean stationScoped() {
            return isStationScoped();
        }
    }

    public record LoanStatusCount(
        LoanStatus status,
        String statusLabel,
        long count
    ) {
        public LoanStatus getStatus() {
            return status;
        }

        public String getStatusLabel() {
            return statusLabel;
        }

        public long getCount() {
            return count;
        }
    }

    private record StationAccessSnapshot(
        SaccoAccessStatus accessStatus,
        LocalDate paymentDueDate,
        OffsetDateTime accessSuspendedAt,
        String accessRestrictionReason
    ) {
        static StationAccessSnapshot active() {
            return new StationAccessSnapshot(SaccoAccessStatus.ACTIVE, null, null, null);
        }
    }

    public record SaccoSummary(
        String saccoId,
        String saccoName,
        List<String> stationIds,
        boolean hasLogo,
        String logoUrl,
        int totalMembers,
        long activeMembers,
        long inactiveMembers,
        int activeLoanCount,
        BigDecimal totalDisbursedPrincipal,
        BigDecimal totalSavings,
        BigDecimal activeExposure,
        BigDecimal repaymentPercent,
        BigDecimal defaultPercent,
        BigDecimal liquidityRatio,
        String healthStatus,
        String healthTone,
        String healthNote,
        boolean newPortfolio,
        SaccoAccessStatus accessStatus,
        LocalDate paymentDueDate,
        OffsetDateTime accessSuspendedAt,
        String accessRestrictionReason,
        String defaultLanguage
    ) {
        public String getSaccoId() {
            return saccoId;
        }

        public String getSaccoName() {
            return saccoName;
        }

        public List<String> getStationIds() {
            return stationIds;
        }

        public boolean isHasLogo() {
            return hasLogo;
        }

        public String getLogoUrl() {
            return logoUrl;
        }

        public int getTotalMembers() {
            return totalMembers;
        }

        public long getActiveMembers() {
            return activeMembers;
        }

        public long getInactiveMembers() {
            return inactiveMembers;
        }

        public int getActiveLoanCount() {
            return activeLoanCount;
        }

        public BigDecimal getTotalDisbursedPrincipal() {
            return totalDisbursedPrincipal;
        }

        public BigDecimal getTotalSavings() {
            return totalSavings;
        }

        public BigDecimal getActiveExposure() {
            return activeExposure;
        }

        public BigDecimal getRepaymentPercent() {
            return repaymentPercent;
        }

        public BigDecimal getDefaultPercent() {
            return defaultPercent;
        }

        public BigDecimal getLiquidityRatio() {
            return liquidityRatio;
        }

        public String getHealthStatus() {
            return healthStatus;
        }

        public String getHealthTone() {
            return healthTone;
        }

        public String getHealthNote() {
            return healthNote;
        }

        public boolean isNewPortfolio() {
            return newPortfolio;
        }

        public SaccoAccessStatus getAccessStatus() {
            return accessStatus == null ? SaccoAccessStatus.ACTIVE : accessStatus;
        }

        public LocalDate getPaymentDueDate() {
            return paymentDueDate;
        }

        public OffsetDateTime getAccessSuspendedAt() {
            return accessSuspendedAt;
        }

        public String getAccessRestrictionReason() {
            return accessRestrictionReason;
        }

        public String getDefaultLanguage() {
            return defaultLanguage == null || defaultLanguage.isBlank() ? "en" : defaultLanguage;
        }

        public String getDefaultLanguageLabel() {
            return "sw".equalsIgnoreCase(getDefaultLanguage()) ? "Kiswahili" : "English";
        }

        public String defaultLanguageLabel() {
            return getDefaultLanguageLabel();
        }

        public boolean isAccessSuspended() {
            return getAccessStatus() == SaccoAccessStatus.SUSPENDED;
        }

        public boolean accessSuspended() {
            return isAccessSuspended();
        }

        public String getAccessStatusLabel() {
            return switch (getAccessStatus()) {
                case ACTIVE -> "Station Access Active";
                case PAYMENT_DUE -> "Station Payment Due";
                case SUSPENDED -> "Station Access Suspended";
            };
        }

        public String accessStatusLabel() {
            return getAccessStatusLabel();
        }

        public String getPaymentDueDateLabel() {
            return paymentDueDate == null ? "Not set" : paymentDueDate.format(DATE_LABEL);
        }

        public String paymentDueDateLabel() {
            return getPaymentDueDateLabel();
        }

        public String getAccessSuspendedAtLabel() {
            return accessSuspendedAt == null ? "Not suspended" : ApplicationTimestamps.zoned(accessSuspendedAt).format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"));
        }

        public String accessSuspendedAtLabel() {
            return getAccessSuspendedAtLabel();
        }

        public String getAccessBadgeClass() {
            return switch (getAccessStatus()) {
                case ACTIVE -> "border-emerald-200 bg-emerald-50 text-emerald-700";
                case PAYMENT_DUE -> "border-amber-200 bg-amber-50 text-amber-700";
                case SUSPENDED -> "border-rose-200 bg-rose-50 text-rose-700";
            };
        }

        public String accessBadgeClass() {
            return getAccessBadgeClass();
        }

        public int getStationCount() {
            return stationIds == null ? 0 : stationIds.size();
        }

        public int stationCount() {
            return getStationCount();
        }

        public String getStationListLabel() {
            return stationIds == null || stationIds.isEmpty() ? "No stations yet" : String.join(", ", stationIds);
        }

        public String stationListLabel() {
            return getStationListLabel();
        }

        public String getTotalDisbursedPrincipalLabel() {
            return PlatformAdminService.formatCompactMoney(totalDisbursedPrincipal);
        }

        public String totalDisbursedPrincipalLabel() {
            return getTotalDisbursedPrincipalLabel();
        }

        public String getTotalDisbursedPrincipalFullLabel() {
            return PlatformAdminService.formatFullMoney(totalDisbursedPrincipal);
        }

        public String totalDisbursedPrincipalFullLabel() {
            return getTotalDisbursedPrincipalFullLabel();
        }

        public String getTotalSavingsLabel() {
            return PlatformAdminService.formatCompactMoney(totalSavings);
        }

        public String totalSavingsLabel() {
            return getTotalSavingsLabel();
        }

        public String getTotalSavingsFullLabel() {
            return PlatformAdminService.formatFullMoney(totalSavings);
        }

        public String totalSavingsFullLabel() {
            return getTotalSavingsFullLabel();
        }

        public String getActiveExposureLabel() {
            return PlatformAdminService.formatCompactMoney(activeExposure);
        }

        public String activeExposureLabel() {
            return getActiveExposureLabel();
        }

        public String getActiveExposureFullLabel() {
            return PlatformAdminService.formatFullMoney(activeExposure);
        }

        public String activeExposureFullLabel() {
            return getActiveExposureFullLabel();
        }

        public String getRepaymentPercentLabel() {
            return PlatformAdminService.formatPercent(repaymentPercent);
        }

        public String repaymentPercentLabel() {
            return getRepaymentPercentLabel();
        }

        public String getDefaultPercentLabel() {
            return PlatformAdminService.formatPercent(defaultPercent);
        }

        public String defaultPercentLabel() {
            return getDefaultPercentLabel();
        }

        public String getLiquidityRatioLabel() {
            return PlatformAdminService.formatRatio(liquidityRatio);
        }

        public String liquidityRatioLabel() {
            return getLiquidityRatioLabel();
        }

        public String getLogoFallbackText() {
            return PlatformAdminService.initialsForName(saccoName);
        }

        public String logoFallbackText() {
            return getLogoFallbackText();
        }

        public String getToneBadgeClass() {
            return switch (healthTone) {
                case "green" -> "border-emerald-200 bg-emerald-50 text-emerald-700";
                case "amber" -> "border-amber-200 bg-amber-50 text-amber-700";
                case "red" -> "border-rose-200 bg-rose-50 text-rose-700";
                default -> "border-slate-200 bg-slate-100 text-slate-700";
            };
        }

        public String toneBadgeClass() {
            return getToneBadgeClass();
        }

        public String getToneDotClass() {
            return switch (healthTone) {
                case "green" -> "bg-emerald-500";
                case "amber" -> "bg-amber-500";
                case "red" -> "bg-rose-500";
                default -> "bg-slate-400";
            };
        }

        public String toneDotClass() {
            return getToneDotClass();
        }

        public String getToneCardClass() {
            return switch (healthTone) {
                case "green" -> "border-emerald-200";
                case "amber" -> "border-amber-200";
                case "red" -> "border-rose-200";
                default -> "border-slate-200";
            };
        }

        public String toneCardClass() {
            return getToneCardClass();
        }
    }

    public record LoanItem(
        UUID loanId,
        String applicantName,
        LoanStatus status,
        BigDecimal amount,
        OffsetDateTime updatedAt,
        LocalDate finalDueDate
    ) {
        public UUID getLoanId() {
            return loanId;
        }

        public String getApplicantName() {
            return applicantName;
        }

        public LoanStatus getStatus() {
            return status;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public OffsetDateTime getUpdatedAt() {
            return updatedAt;
        }

        public LocalDate getFinalDueDate() {
            return finalDueDate;
        }

        public String getLoanReference() {
            return PlatformAdminService.shortId(loanId);
        }

        public String loanReference() {
            return getLoanReference();
        }

        public String getAmountLabel() {
            return PlatformAdminService.formatCompactMoney(amount);
        }

        public String amountLabel() {
            return getAmountLabel();
        }

        public String getStatusLabel() {
            return PlatformAdminService.formatLoanStatusLabel(status);
        }

        public String statusLabel() {
            return getStatusLabel();
        }

        public String getUpdatedAtLabel() {
            return updatedAt == null ? "—" : ApplicationTimestamps.zoned(updatedAt).format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"));
        }

        public String updatedAtLabel() {
            return getUpdatedAtLabel();
        }

        public String getFinalDueDateLabel() {
            return finalDueDate == null ? "—" : finalDueDate.format(DATE_LABEL);
        }

        public String finalDueDateLabel() {
            return getFinalDueDateLabel();
        }
    }

    public record AuditItem(
        String actionLabel,
        String sourceLabel,
        String actorLabel,
        String createdAtLabel
    ) {
        public String getActionLabel() {
            return actionLabel;
        }

        public String getSourceLabel() {
            return sourceLabel;
        }

        public String getActorLabel() {
            return actorLabel;
        }

        public String getCreatedAtLabel() {
            return createdAtLabel;
        }
    }

    private record StatusMeta(String label, String tone, String note) {
    }

    private record MemberStats(long totalMembers, long activeMembers, long inactiveMembers) {
        static MemberStats empty() {
            return new MemberStats(0, 0, 0);
        }
    }

    private record LoanStats(
        long activeLoanCount,
        long paidLoanCount,
        long overdueLoanCount,
        BigDecimal totalDisbursedPrincipal,
        BigDecimal paidPrincipal,
        BigDecimal activeExposure,
        BigDecimal overduePrincipal
    ) {
        static LoanStats empty() {
            return new LoanStats(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    private record PortfolioSnapshot(
        List<SaccoSummary> summaries,
        int totalMembers
    ) {
    }
}
