package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.ManagerReview;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class LoanAnalyticsService {
    private static final EnumSet<LoanStatus> REJECTED_STATUSES = EnumSet.of(
        LoanStatus.MANAGER_REJECTED,
        LoanStatus.LOAN_OFFICER_REJECTED,
        LoanStatus.BOARD_REJECTED,
        LoanStatus.ACCOUNTANT_REJECTED,
        LoanStatus.FINAL_REJECTED
    );
    private static final EnumSet<LoanStatus> DISBURSED_STATUSES = EnumSet.of(
        LoanStatus.FINAL_APPROVED,
        LoanStatus.DEFAULTED,
        LoanStatus.PAID
    );
    private static final EnumSet<LoanStatus> ACTIVE_STATUSES = EnumSet.of(
        LoanStatus.FINAL_APPROVED,
        LoanStatus.DEFAULTED
    );

    private final LoanApplicationRepository loanApplicationRepository;
    private final ManagerReviewRepository managerReviewRepository;
    private final BoardReviewRepository boardReviewRepository;
    private final LoanProductSettingRepository loanProductSettingRepository;
    private final ApplicationClock applicationClock;

    public MemberLoanAnalytics forMember(UUID memberId, LocalDate fromDate, LocalDate toDate) {
        return forMember(memberId, fromDate, toDate, null, null);
    }

    public MemberLoanAnalytics forMember(UUID memberId,
                                         LocalDate fromDate,
                                         LocalDate toDate,
                                         LoanType loanType,
                                         LoanStatus loanStatus) {
        return summarize(memberLoans(memberId, fromDate, toDate, loanType, loanStatus));
    }

    public MemberLoanAnalytics forStaff(AppUserPrincipal principal,
                                        LocalDate fromDate,
                                        LocalDate toDate,
                                        LoanType loanType,
                                        LoanStatus loanStatus) {
        return summarize(staffLoans(principal, fromDate, toDate, loanType, loanStatus));
    }

    public List<LoanApplication> loansForStaffAnalytics(AppUserPrincipal principal,
                                                        LocalDate fromDate,
                                                        LocalDate toDate,
                                                        LoanType loanType,
                                                        LoanStatus loanStatus) {
        return staffLoans(principal, fromDate, toDate, loanType, loanStatus);
    }

    public MemberLoanAnalytics forStation(String saccoId,
                                          String stationId,
                                          LocalDate fromDate,
                                          LocalDate toDate,
                                          LoanType loanType,
                                          LoanStatus loanStatus) {
        return summarize(stationLoans(saccoId, stationId, fromDate, toDate, loanType, loanStatus));
    }

    public List<MetricTrendSeries> statusTrendForMember(UUID memberId,
                                                        LocalDate fromDate,
                                                        LocalDate toDate,
                                                        LoanType loanType,
                                                        LoanStatus loanStatus) {
        LocalDate end = toDate == null ? applicationClock.today() : toDate;
        LocalDate start = fromDate == null ? end.minusMonths(11).withDayOfMonth(1) : fromDate.withDayOfMonth(1);
        List<YearMonth> months = new ArrayList<>();
        YearMonth cursor = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        while (!cursor.isAfter(last)) {
            months.add(cursor);
            cursor = cursor.plusMonths(1);
        }

        List<LoanApplication> loans = memberLoans(memberId, start, end, loanType, loanStatus);

        return List.of(
            trendSeries("Applied", "#2563eb", months, loans, app -> app.getStatus() != LoanStatus.DRAFT),
            trendSeries("Disbursed", "#059669", months, loans, app -> DISBURSED_STATUSES.contains(app.getStatus())),
            trendSeries("Paid", "#7c3aed", months, loans, app -> app.getStatus() == LoanStatus.PAID),
            trendSeries("Defaulted", "#dc2626", months, loans, app -> app.getStatus() == LoanStatus.DEFAULTED),
            trendSeries("Forfeited", "#f97316", months, loans, app -> app.getStatus() == LoanStatus.FORFEITED),
            trendSeries("Rejected", "#475569", months, loans, app -> REJECTED_STATUSES.contains(app.getStatus()))
        );
    }

    public List<MetricTrendSeries> statusTrendForStaff(AppUserPrincipal principal,
                                                       LocalDate fromDate,
                                                       LocalDate toDate,
                                                       LoanType loanType,
                                                       LoanStatus loanStatus) {
        LocalDate end = toDate == null ? applicationClock.today() : toDate;
        LocalDate start = fromDate == null ? end.minusMonths(11).withDayOfMonth(1) : fromDate.withDayOfMonth(1);
        List<YearMonth> months = monthsBetween(start, end);
        List<StaffLoanEvent> events = staffLoanEvents(principal, start, end, loanType, loanStatus);

        return List.of(
            staffTrendSeries("Applied", "#2563eb", months, events, event -> true),
            staffTrendSeries("Active", "#059669", months, events, event -> ACTIVE_STATUSES.contains(event.loan().getStatus())),
            staffTrendSeries("Disbursed", "#059669", months, events, event -> event.disbursed() || DISBURSED_STATUSES.contains(event.loan().getStatus())),
            staffTrendSeries("Paid", "#7c3aed", months, events, event -> event.loan().getStatus() == LoanStatus.PAID),
            staffTrendSeries("Defaulted", "#dc2626", months, events, event -> event.loan().getStatus() == LoanStatus.DEFAULTED),
            staffTrendSeries("Forfeited", "#f97316", months, events, event -> event.loan().getStatus() == LoanStatus.FORFEITED),
            staffTrendSeries("Rejected", "#475569", months, events, event -> event.rejected() || REJECTED_STATUSES.contains(event.loan().getStatus()))
        );
    }

    public List<MetricTrendSeries> statusTrendForStation(String saccoId,
                                                         String stationId,
                                                         LocalDate fromDate,
                                                         LocalDate toDate,
                                                         LoanType loanType,
                                                         LoanStatus loanStatus) {
        LocalDate end = toDate == null ? applicationClock.today() : toDate;
        LocalDate start = fromDate == null ? end.minusMonths(11).withDayOfMonth(1) : fromDate.withDayOfMonth(1);
        List<YearMonth> months = monthsBetween(start, end);
        List<LoanApplication> loans = stationLoans(saccoId, stationId, start, end, loanType, loanStatus);

        return List.of(
            trendSeries("Applied", "#2563eb", months, loans, app -> app.getStatus() != LoanStatus.DRAFT),
            trendSeries("Active", "#059669", months, loans, app -> ACTIVE_STATUSES.contains(app.getStatus())),
            trendSeries("Disbursed", "#059669", months, loans, app -> DISBURSED_STATUSES.contains(app.getStatus())),
            trendSeries("Paid", "#7c3aed", months, loans, app -> app.getStatus() == LoanStatus.PAID),
            trendSeries("Defaulted", "#dc2626", months, loans, app -> app.getStatus() == LoanStatus.DEFAULTED),
            trendSeries("Forfeited", "#f97316", months, loans, app -> app.getStatus() == LoanStatus.FORFEITED),
            trendSeries("Rejected", "#475569", months, loans, app -> REJECTED_STATUSES.contains(app.getStatus()))
        );
    }

    public List<LoanProductPerformance> productPerformanceForMember(String saccoId,
                                                                    String stationId,
                                                                    UUID memberId,
                                                                    LocalDate fromDate,
                                                                    LocalDate toDate,
                                                                    LoanStatus loanStatus) {
        return productPerformanceForMember(saccoId, stationId, memberId, fromDate, toDate, null, loanStatus);
    }

    public List<LoanProductPerformance> productPerformanceForMember(String saccoId,
                                                                    String stationId,
                                                                    UUID memberId,
                                                                    LocalDate fromDate,
                                                                    LocalDate toDate,
                                                                    LoanType loanType,
                                                                    LoanStatus loanStatus) {
        return productPerformance(saccoId, memberLoans(memberId, fromDate, toDate, loanType, loanStatus).stream()
            .filter(app -> matchesScope(app, saccoId, stationId))
            .toList());
    }

    public List<LoanProductPerformance> productPerformanceForStaff(AppUserPrincipal principal,
                                                                   LocalDate fromDate,
                                                                   LocalDate toDate,
                                                                   LoanStatus loanStatus) {
        return productPerformanceForStaff(principal, fromDate, toDate, null, loanStatus);
    }

    public List<LoanProductPerformance> productPerformanceForStaff(AppUserPrincipal principal,
                                                                   LocalDate fromDate,
                                                                   LocalDate toDate,
                                                                   LoanType loanType,
                                                                   LoanStatus loanStatus) {
        return productPerformance(principal == null ? null : principal.getSaccoId(),
            staffLoans(principal, fromDate, toDate, loanType, loanStatus));
    }

    public List<LoanProductPerformance> productPerformanceForStation(String saccoId,
                                                                     String stationId,
                                                                     LocalDate fromDate,
                                                                     LocalDate toDate,
                                                                     LoanStatus loanStatus) {
        return productPerformanceForStation(saccoId, stationId, fromDate, toDate, null, loanStatus);
    }

    public List<LoanProductPerformance> productPerformanceForStation(String saccoId,
                                                                     String stationId,
                                                                     LocalDate fromDate,
                                                                     LocalDate toDate,
                                                                     LoanType loanType,
                                                                     LoanStatus loanStatus) {
        return productPerformance(saccoId, stationLoans(saccoId, stationId, fromDate, toDate, loanType, loanStatus));
    }

    public List<MetricDelta> metricDeltas(MemberLoanAnalytics current, MemberLoanAnalytics previous) {
        return List.of(
            new MetricDelta("applied", percentChange(current.appliedLoans(), previous.appliedLoans()), false),
            new MetricDelta("active", percentChange(current.activeLoans(), previous.activeLoans()), false),
            new MetricDelta("disbursed", percentChange(current.disbursedLoans(), previous.disbursedLoans()), false),
            new MetricDelta("paid", percentChange(current.paidLoans(), previous.paidLoans()), false),
            new MetricDelta("defaulted", percentChange(current.defaultedLoans(), previous.defaultedLoans()), true),
            new MetricDelta("forfeited", percentChange(current.forfeitedLoans(), previous.forfeitedLoans()), true),
            new MetricDelta("rejected", percentChange(current.rejectedLoans(), previous.rejectedLoans()), true)
        );
    }

    public StaffPortfolioSummary staffPortfolio(AppUserPrincipal principal,
                                                LocalDate fromDate,
                                                LocalDate toDate,
                                                LoanType loanType,
                                                LoanStatus loanStatus) {
        List<StaffLoanEvent> events = staffLoanEvents(principal, fromDate, toDate, loanType, loanStatus);
        long approved = events.stream().filter(StaffLoanEvent::approved).count();
        long rejected = events.stream().filter(StaffLoanEvent::rejected).count();
        long disbursed = events.stream()
            .filter(event -> event.disbursed() || DISBURSED_STATUSES.contains(event.loan().getStatus()))
            .count();
        long defaultedAfterApproval = events.stream()
            .filter(StaffLoanEvent::approved)
            .filter(event -> event.loan().getStatus() == LoanStatus.DEFAULTED)
            .count();
        BigDecimal defaultedRate = approved == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(defaultedAfterApproval)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(approved), 2, RoundingMode.HALF_UP);
        String riskLevel = defaultedRate.compareTo(BigDecimal.valueOf(10)) >= 0
            ? "High"
            : defaultedRate.compareTo(BigDecimal.valueOf(5)) >= 0 ? "Moderate" : "Low";
        return new StaffPortfolioSummary(events.size(), approved, rejected, disbursed, defaultedAfterApproval, defaultedRate, riskLevel);
    }

    public StaffReviewAnalytics staffReviewAnalytics(AppUserPrincipal principal,
                                                     LocalDate fromDate,
                                                     LocalDate toDate,
                                                     LoanType loanType,
                                                     LoanStatus loanStatus) {
        LocalDate end = toDate == null ? applicationClock.today() : toDate;
        LocalDate start = fromDate == null ? end.minusMonths(11).withDayOfMonth(1) : fromDate.withDayOfMonth(1);
        List<YearMonth> months = monthsBetween(start, end);
        List<StaffLoanEvent> events = staffLoanEvents(principal, fromDate, toDate, loanType, loanStatus);
        List<StaffReviewProductPerformance> productRows = staffReviewProductPerformance(
            principal == null ? null : principal.getSaccoId(),
            events
        );
        List<MetricTrendSeries> trendSeries = List.of(
            staffTrendSeries("Reviewed", "#111827", months, events, event -> true),
            staffTrendSeries("Approved", "#65a30d", months, events, StaffLoanEvent::approved),
            staffTrendSeries("Rejected", "#ef4444", months, events, StaffLoanEvent::rejected),
            staffTrendSeries("Pending", "#7e22ce", months, events, StaffLoanEvent::pending)
        );
        return new StaffReviewAnalytics(
            events.size(),
            events.stream().filter(StaffLoanEvent::approved).count(),
            events.stream().filter(StaffLoanEvent::rejected).count(),
            events.stream().filter(StaffLoanEvent::pending).count(),
            events.stream().filter(event -> event.disbursed() || DISBURSED_STATUSES.contains(event.loan().getStatus())).count(),
            events.stream().filter(StaffLoanEvent::approved).filter(event -> event.loan().getStatus() == LoanStatus.DEFAULTED).count(),
            productRows,
            trendSeries
        );
    }

    public StaffPortfolioSummary stationPortfolio(String saccoId,
                                                  String stationId,
                                                  LocalDate fromDate,
                                                  LocalDate toDate,
                                                  LoanType loanType,
                                                  LoanStatus loanStatus) {
        MemberLoanAnalytics analytics = forStation(saccoId, stationId, fromDate, toDate, loanType, loanStatus);
        BigDecimal defaultedRate = analytics.disbursedLoans() == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(analytics.defaultedLoans())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(analytics.disbursedLoans()), 2, RoundingMode.HALF_UP);
        String riskLevel = defaultedRate.compareTo(BigDecimal.valueOf(10)) >= 0
            ? "High"
            : defaultedRate.compareTo(BigDecimal.valueOf(5)) >= 0 ? "Moderate" : "Low";
        return new StaffPortfolioSummary(
            analytics.appliedLoans(),
            analytics.disbursedLoans(),
            analytics.rejectedLoans(),
            analytics.disbursedLoans(),
            analytics.defaultedLoans(),
            defaultedRate,
            riskLevel
        );
    }

    public List<Map<String, Object>> productChartSeries(List<LoanProductPerformance> performance) {
        return List.of(
            productChartSeries("Total Loans", "#2563eb", performance, LoanProductPerformance::totalLoans),
            productChartSeries("Paid Loans", "#059669", performance, LoanProductPerformance::paidLoans),
            productChartSeries("Defaulted Loans", "#f97316", performance, LoanProductPerformance::defaultedLoans),
            productChartSeries("Rejected Loans", "#ef4444", performance, LoanProductPerformance::rejectedLoans)
        );
    }

    public List<Map<String, Object>> staffReviewProductChartSeries(List<StaffReviewProductPerformance> performance) {
        return List.of(
            staffReviewProductChartSeries("Reviewed", "#111827", performance, StaffReviewProductPerformance::reviewed),
            staffReviewProductChartSeries("Approved", "#65a30d", performance, StaffReviewProductPerformance::approved),
            staffReviewProductChartSeries("Rejected", "#ef4444", performance, StaffReviewProductPerformance::rejected),
            staffReviewProductChartSeries("Pending", "#7e22ce", performance, StaffReviewProductPerformance::pending)
        );
    }

    public MemberLoanAnalytics summarizeAllTime(UUID memberId, String saccoId, String stationId) {
        Map<LoanStatus, Long> counts = new java.util.EnumMap<>(LoanStatus.class);
        loanApplicationRepository.countByStatusForApplicantScope(memberId, saccoId, stationId)
            .forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        long defaulted = counts.getOrDefault(LoanStatus.DEFAULTED, 0L);
        long active = ACTIVE_STATUSES.stream().mapToLong(status -> counts.getOrDefault(status, 0L)).sum();
        long paid = counts.getOrDefault(LoanStatus.PAID, 0L);
        long forfeited = counts.getOrDefault(LoanStatus.FORFEITED, 0L);
        long applied = counts.entrySet().stream()
            .filter(entry -> entry.getKey() != LoanStatus.DRAFT)
            .mapToLong(Map.Entry::getValue)
            .sum();
        long disbursed = DISBURSED_STATUSES.stream().mapToLong(status -> counts.getOrDefault(status, 0L)).sum();
        long rejected = REJECTED_STATUSES.stream().mapToLong(status -> counts.getOrDefault(status, 0L)).sum();
        BigDecimal activeAmount = loanApplicationRepository.sumAmountForApplicantScopeAndStatuses(
            memberId, saccoId, stationId, ACTIVE_STATUSES);
        return new MemberLoanAnalytics(defaulted, active, paid, forfeited, applied, disbursed, rejected, activeAmount);
    }

    public BigDecimal activeLoanAmount(UUID memberId, String saccoId, String stationId) {
        return loanApplicationRepository.sumAmountForApplicantScopeAndStatuses(
            memberId, saccoId, stationId, ACTIVE_STATUSES);
    }

    private boolean matchesStation(LoanApplication app, String stationId) {
        if (stationId == null || stationId.isBlank()) {
            return true;
        }
        return app != null && app.getStationId() != null && stationId.trim().equalsIgnoreCase(app.getStationId());
    }

    private boolean matchesScope(LoanApplication app, String saccoId, String stationId) {
        if (app == null) {
            return false;
        }
        if (saccoId != null && !saccoId.isBlank() && !saccoId.equalsIgnoreCase(app.getSaccoId())) {
            return false;
        }
        return matchesStation(app, stationId);
    }

    private List<LoanApplication> memberLoans(UUID memberId,
                                              LocalDate fromDate,
                                              LocalDate toDate,
                                              LoanType loanType,
                                              LoanStatus loanStatus) {
        OffsetDateTime createdFrom = fromDate == null ? null : fromDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime createdToExclusive = toDate == null ? null : toDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return loanApplicationRepository.findMemberLoansForAnalytics(
            memberId,
            createdFrom,
            createdToExclusive,
            loanType,
            loanStatus
        );
    }

    private List<LoanApplication> stationLoans(String saccoId,
                                               String stationId,
                                               LocalDate fromDate,
                                               LocalDate toDate,
                                               LoanType loanType,
                                               LoanStatus loanStatus) {
        if (saccoId == null || saccoId.isBlank()) {
            return List.of();
        }
        return loanApplicationRepository.findScopeLoansForAnalytics(
            saccoId,
            stationId,
            startOfDay(fromDate),
            dayAfter(toDate),
            loanType,
            loanStatus
        );
    }

    private List<LoanApplication> staffLoans(AppUserPrincipal principal,
                                             LocalDate fromDate,
                                             LocalDate toDate,
                                             LoanType loanType,
                                             LoanStatus loanStatus) {
        return staffLoanEvents(principal, fromDate, toDate, loanType, loanStatus).stream()
            .map(StaffLoanEvent::loan)
            .filter(distinctById())
            .toList();
    }

    private List<StaffLoanEvent> staffLoanEvents(AppUserPrincipal principal,
                                                 LocalDate fromDate,
                                                 LocalDate toDate,
                                                 LoanType loanType,
                                                 LoanStatus loanStatus) {
        if (principal == null) {
            return List.of();
        }
        Map<UUID, LoanApplication> loanMap = new LinkedHashMap<>();
        List<ReviewRef> reviewRefs = new ArrayList<>();
        OffsetDateTime createdFrom = startOfDay(fromDate);
        OffsetDateTime createdToExclusive = dayAfter(toDate);
        for (ApprovalWorkflowStage stage : managerStagesFor(principal)) {
            managerReviewRepository.findForAnalytics(principal.getMemberId(), stage, createdFrom, createdToExclusive)
                .stream()
                .forEach(review -> reviewRefs.add(new ReviewRef(review.getLoanApplicationId(), review.getCreatedAt(),
                    review.getDecision() == ManagerDecision.ACCEPT,
                    review.getDecision() == ManagerDecision.REJECT,
                    stage == ApprovalWorkflowStage.DISBURSEMENT_OFFICER)));
        }
        for (ApprovalWorkflowStage stage : boardStagesFor(principal)) {
            boardReviewRepository.findForAnalytics(principal.getMemberId(), stage, createdFrom, createdToExclusive)
                .stream()
                .forEach(review -> reviewRefs.add(new ReviewRef(review.getLoanApplicationId(), resolveBoardReviewDate(review),
                    review.getDecision() == BoardDecision.APPROVED,
                    review.getDecision() == BoardDecision.REJECTED,
                    false)));
        }
        if (reviewRefs.isEmpty()) {
            return List.of();
        }
        loanApplicationRepository.findAllById(reviewRefs.stream().map(ReviewRef::loanId).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)))
            .forEach(app -> loanMap.put(app.getId(), app));

        return reviewRefs.stream()
            .map(ref -> {
                LoanApplication loan = loanMap.get(ref.loanId());
                if (loan == null) {
                    return null;
                }
                return new StaffLoanEvent(loan, ref.reviewedAt(), ref.approved(), ref.rejected(), ref.disbursed());
            })
            .filter(Objects::nonNull)
            .filter(event -> event.loan().getSaccoId().equals(principal.getSaccoId()))
            .filter(event -> matchesStation(event.loan(), principal.getStationId()))
            .filter(event -> loanType == null || event.loan().getLoanType() == loanType)
            .filter(event -> loanStatus == null || event.loan().getStatus() == loanStatus)
            .sorted(Comparator.comparing(StaffLoanEvent::reviewedAt).reversed())
            .toList();
    }

    private List<ApprovalWorkflowStage> managerStagesFor(AppUserPrincipal principal) {
        List<ApprovalWorkflowStage> stages = new ArrayList<>();
        if (principal.hasRole(Position.MANAGER)) {
            stages.add(ApprovalWorkflowStage.MANAGER);
        }
        if (principal.hasRole(Position.ACCOUNTANT)) {
            stages.add(ApprovalWorkflowStage.ACCOUNTANT);
        }
        if (principal.hasRole(Position.DISBURSEMENT_OFFICER)) {
            stages.add(ApprovalWorkflowStage.DISBURSEMENT_OFFICER);
        }
        return stages;
    }

    private List<ApprovalWorkflowStage> boardStagesFor(AppUserPrincipal principal) {
        List<ApprovalWorkflowStage> stages = new ArrayList<>();
        if (principal.hasRole(Position.LOAN_OFFICER)) {
            stages.add(ApprovalWorkflowStage.LOAN_OFFICER);
        }
        if (principal.hasRole(Position.BOARD)) {
            stages.add(ApprovalWorkflowStage.BOARD);
        }
        return stages;
    }

    private OffsetDateTime resolveBoardReviewDate(BoardReview review) {
        return review.getDecidedAt() == null ? review.getCreatedAt() : review.getDecidedAt();
    }

    private Predicate<LoanApplication> distinctById() {
        Set<UUID> seen = new HashSet<>();
        return loan -> loan != null && seen.add(loan.getId());
    }

    private List<LoanProductPerformance> productPerformance(String saccoId, List<LoanApplication> loans) {
        List<ProductRef> productRefs = configuredProductRefs(saccoId);
        if (productRefs.isEmpty()) {
            productRefs = java.util.Arrays.stream(LoanType.values())
                .sorted(Comparator.comparingInt(LoanType::getDisplayOrder))
                .map(type -> new ProductRef(type, shortProductLabel(type)))
                .toList();
        }
        return productRefs.stream()
            .filter(product -> product.loanType() != LoanType.CUSTOMIZED_LOAN)
            .map(product -> {
                List<LoanApplication> typedLoans = loans.stream()
                    .filter(app -> app.getLoanType() == product.loanType())
                    .toList();
                return new LoanProductPerformance(
                    product.label(),
                    typedLoans.size(),
                    count(typedLoans, LoanStatus.PAID),
                    count(typedLoans, LoanStatus.DEFAULTED),
                    typedLoans.stream().filter(app -> REJECTED_STATUSES.contains(app.getStatus())).count()
                );
            })
            .toList();
    }

    private List<StaffReviewProductPerformance> staffReviewProductPerformance(String saccoId, List<StaffLoanEvent> events) {
        List<ProductRef> productRefs = configuredProductRefs(saccoId);
        if (productRefs.isEmpty()) {
            productRefs = java.util.Arrays.stream(LoanType.values())
                .sorted(Comparator.comparingInt(LoanType::getDisplayOrder))
                .map(type -> new ProductRef(type, shortProductLabel(type)))
                .toList();
        }
        return productRefs.stream()
            .filter(product -> product.loanType() != LoanType.CUSTOMIZED_LOAN)
            .map(product -> {
                List<StaffLoanEvent> productEvents = events.stream()
                    .filter(event -> event.loan().getLoanType() == product.loanType())
                    .toList();
                long reviewed = productEvents.size();
                long approved = productEvents.stream().filter(StaffLoanEvent::approved).count();
                long rejected = productEvents.stream().filter(StaffLoanEvent::rejected).count();
                long pending = productEvents.stream().filter(StaffLoanEvent::pending).count();
                BigDecimal approvalRate = reviewed == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(approved)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(reviewed), 2, RoundingMode.HALF_UP);
                return new StaffReviewProductPerformance(product.label(), reviewed, approved, rejected, pending, approvalRate);
            })
            .toList();
    }

    private List<ProductRef> configuredProductRefs(String saccoId) {
        if (saccoId == null || saccoId.isBlank()) {
            return List.of();
        }
        return loanProductSettingRepository.findBySaccoIdAndActiveTrue(saccoId).stream()
            .sorted(Comparator.comparingInt(com.sacco.mvp.domain.LoanProductSetting::getResolvedDisplayOrder))
            .map(product -> new ProductRef(product.getLoanType(), product.getDisplayName()))
            .filter(product -> product.loanType() != null)
            .toList();
    }

    private Map<String, Object> productChartSeries(String name,
                                                   String color,
                                                   List<LoanProductPerformance> performance,
                                                   java.util.function.ToLongFunction<LoanProductPerformance> valueExtractor) {
        Map<String, Object> series = new LinkedHashMap<>();
        series.put("name", name);
        series.put("color", color);
        series.put("dataPoints", performance.stream().map(item -> {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("label", item.label());
            point.put("y", valueExtractor.applyAsLong(item));
            return point;
        }).toList());
        return series;
    }

    private Map<String, Object> staffReviewProductChartSeries(String name,
                                                              String color,
                                                              List<StaffReviewProductPerformance> performance,
                                                              java.util.function.ToLongFunction<StaffReviewProductPerformance> valueExtractor) {
        Map<String, Object> series = new LinkedHashMap<>();
        series.put("name", name);
        series.put("color", color);
        series.put("dataPoints", performance.stream().map(item -> {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("label", item.label());
            point.put("y", valueExtractor.applyAsLong(item));
            return point;
        }).toList());
        return series;
    }

    private List<YearMonth> monthsBetween(LocalDate start, LocalDate end) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth cursor = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        while (!cursor.isAfter(last)) {
            months.add(cursor);
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    private BigDecimal percentChange(long current, long previous) {
        if (previous == 0) {
            return current == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
        }
        return BigDecimal.valueOf(current - previous)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(previous), 2, RoundingMode.HALF_UP);
    }

    private String shortProductLabel(LoanType type) {
        return switch (type) {
            case LOAN_ADVANCE -> "Salary Advance";
            case EDUCATION_LOAN -> "Education Loan";
            case EMERGENCY_LOAN -> "Emergency Loan";
            case DEVELOPMENT_LOAN -> "Development Loan";
            case CUSTOMIZED_LOAN -> "Other Loans";
        };
    }

    private MemberLoanAnalytics summarize(List<LoanApplication> loans) {
        long defaulted = count(loans, LoanStatus.DEFAULTED);
        long active = loans.stream().filter(app -> ACTIVE_STATUSES.contains(app.getStatus())).count();
        long paid = count(loans, LoanStatus.PAID);
        long forfeited = count(loans, LoanStatus.FORFEITED);
        long applied = loans.stream().filter(app -> app.getStatus() != LoanStatus.DRAFT).count();
        long disbursed = loans.stream().filter(app -> DISBURSED_STATUSES.contains(app.getStatus())).count();
        long rejected = loans.stream().filter(app -> REJECTED_STATUSES.contains(app.getStatus())).count();
        BigDecimal activeAmount = loans.stream()
            .filter(app -> ACTIVE_STATUSES.contains(app.getStatus()))
            .map(LoanApplication::getAmount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MemberLoanAnalytics(defaulted, active, paid, forfeited, applied, disbursed, rejected, activeAmount);
    }

    private long count(List<LoanApplication> loans, LoanStatus status) {
        return loans.stream().filter(app -> app.getStatus() == status).count();
    }

    private MetricTrendSeries trendSeries(String name,
                                          String color,
                                          List<YearMonth> months,
                                          List<LoanApplication> loans,
                                          java.util.function.Predicate<LoanApplication> predicate) {
        Map<YearMonth, Long> counts = new LinkedHashMap<>();
        for (YearMonth month : months) {
            counts.put(month, 0L);
        }
        loans.stream()
            .filter(predicate)
            .filter(app -> app.getCreatedAt() != null)
            .forEach(app -> {
                YearMonth month = YearMonth.from(app.getCreatedAt());
                if (counts.containsKey(month)) {
                    counts.put(month, counts.get(month) + 1);
                }
            });
        List<Map<String, Object>> dataPoints = counts.entrySet().stream()
            .map(entry -> {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("x", entry.getKey().atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli());
                point.put("y", entry.getValue());
                return point;
            })
            .toList();
        return new MetricTrendSeries(name, color, dataPoints);
    }

    private MetricTrendSeries staffTrendSeries(String name,
                                               String color,
                                               List<YearMonth> months,
                                               List<StaffLoanEvent> events,
                                               java.util.function.Predicate<StaffLoanEvent> predicate) {
        Map<YearMonth, Long> counts = new LinkedHashMap<>();
        for (YearMonth month : months) {
            counts.put(month, 0L);
        }
        events.stream()
            .filter(predicate)
            .filter(event -> event.reviewedAt() != null)
            .forEach(event -> {
                YearMonth month = YearMonth.from(event.reviewedAt());
                if (counts.containsKey(month)) {
                    counts.put(month, counts.get(month) + 1);
                }
            });
        List<Map<String, Object>> dataPoints = counts.entrySet().stream()
            .map(entry -> {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("x", entry.getKey().atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli());
                point.put("y", entry.getValue());
                return point;
            })
            .toList();
        return new MetricTrendSeries(name, color, dataPoints);
    }

    private boolean withinRange(OffsetDateTime value, LocalDate fromDate, LocalDate toDate) {
        if (value == null) {
            return false;
        }
        OffsetDateTime from = fromDate == null ? null : fromDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = toDate == null ? null : toDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return (from == null || !value.isBefore(from)) && (to == null || value.isBefore(to));
    }

    private OffsetDateTime startOfDay(LocalDate value) {
        return value == null ? null : value.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private OffsetDateTime dayAfter(LocalDate value) {
        return value == null ? null : value.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    public record MemberLoanAnalytics(
        long defaultedLoans,
        long activeLoans,
        long paidLoans,
        long forfeitedLoans,
        long appliedLoans,
        long disbursedLoans,
        long rejectedLoans,
        BigDecimal activeLoanAmount
    ) {
        public long getDefaultedLoans() {
            return defaultedLoans;
        }

        public long getActiveLoans() {
            return activeLoans;
        }

        public long getPaidLoans() {
            return paidLoans;
        }

        public long getForfeitedLoans() {
            return forfeitedLoans;
        }

        public long getAppliedLoans() {
            return appliedLoans;
        }

        public long getDisbursedLoans() {
            return disbursedLoans;
        }

        public long getRejectedLoans() {
            return rejectedLoans;
        }

        public BigDecimal getActiveLoanAmount() {
            return activeLoanAmount;
        }
    }

    public record MetricTrendSeries(
        String name,
        String color,
        List<Map<String, Object>> dataPoints
    ) {
        public String getName() {
            return name;
        }

        public String getColor() {
            return color;
        }

        public List<Map<String, Object>> getDataPoints() {
            return dataPoints;
        }
    }

    public record MetricDelta(String key, BigDecimal percent, boolean riskMetric) {
        public String getKey() {
            return key;
        }

        public BigDecimal getPercent() {
            return percent;
        }

        public boolean isRiskMetric() {
            return riskMetric;
        }
    }

    public record StaffPortfolioSummary(
        long handledLoans,
        long approvedLoans,
        long rejectedLoans,
        long disbursedLoans,
        long defaultedAfterApproval,
        BigDecimal defaultedAfterApprovalRate,
        String riskLevel
    ) {
        public long getHandledLoans() {
            return handledLoans;
        }

        public long getApprovedLoans() {
            return approvedLoans;
        }

        public long getRejectedLoans() {
            return rejectedLoans;
        }

        public long getDisbursedLoans() {
            return disbursedLoans;
        }

        public long getDefaultedAfterApproval() {
            return defaultedAfterApproval;
        }

        public BigDecimal getDefaultedAfterApprovalRate() {
            return defaultedAfterApprovalRate;
        }

        public String getRiskLevel() {
            return riskLevel;
        }
    }

    public record LoanProductPerformance(
        String label,
        long totalLoans,
        long paidLoans,
        long defaultedLoans,
        long rejectedLoans
    ) {
        public String getLabel() {
            return label;
        }

        public long getTotalLoans() {
            return totalLoans;
        }

        public long getPaidLoans() {
            return paidLoans;
        }

        public long getDefaultedLoans() {
            return defaultedLoans;
        }

        public long getRejectedLoans() {
            return rejectedLoans;
        }
    }

    public record StaffReviewAnalytics(
        long reviewedLoans,
        long approvedLoans,
        long rejectedLoans,
        long pendingLoans,
        long disbursedLoans,
        long defaultedAfterApproval,
        List<StaffReviewProductPerformance> productRows,
        List<MetricTrendSeries> trendSeries
    ) {
        public long getReviewedLoans() {
            return reviewedLoans;
        }

        public long getApprovedLoans() {
            return approvedLoans;
        }

        public long getRejectedLoans() {
            return rejectedLoans;
        }

        public long getPendingLoans() {
            return pendingLoans;
        }

        public long getDisbursedLoans() {
            return disbursedLoans;
        }

        public long getDefaultedAfterApproval() {
            return defaultedAfterApproval;
        }

        public List<StaffReviewProductPerformance> getProductRows() {
            return productRows;
        }

        public List<MetricTrendSeries> getTrendSeries() {
            return trendSeries;
        }
    }

    public record StaffReviewProductPerformance(
        String label,
        long reviewed,
        long approved,
        long rejected,
        long pending,
        BigDecimal approvalRate
    ) {
        public String getLabel() {
            return label;
        }

        public long getReviewed() {
            return reviewed;
        }

        public long getApproved() {
            return approved;
        }

        public long getRejected() {
            return rejected;
        }

        public long getPending() {
            return pending;
        }

        public BigDecimal getApprovalRate() {
            return approvalRate;
        }
    }

    private record ReviewRef(UUID loanId, OffsetDateTime reviewedAt, boolean approved, boolean rejected, boolean disbursed) {}

    private record StaffLoanEvent(LoanApplication loan, OffsetDateTime reviewedAt, boolean approved, boolean rejected, boolean disbursed) {
        private boolean pending() {
            return !approved && !rejected;
        }
    }

    private record ProductRef(LoanType loanType, String label) {}
}
