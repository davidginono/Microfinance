package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.integration.foresight.ForesightAccountSummary;
import com.sacco.mvp.integration.foresight.ForesightActiveLoan;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import com.sacco.mvp.integration.foresight.UpstreamAvailabilityException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActiveLoanDisplayService {
    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_LOCAL_ONLY = "LOCAL_ONLY";
    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String ACTIVE_STATE_CLASSES = "border-emerald-200 bg-emerald-50 text-emerald-700";

    private final ForesightDirectoryService foresightDirectoryService;
    private final LoanPresentationService loanPresentationService;
    private final LoanProductDisplayService loanProductDisplayService;
    private final ObjectMapper objectMapper;

    public ActiveLoanDisplay localMemberDashboardRows(String saccoId,
                                                      List<LoanApplication> localLoans,
                                                      Set<UUID> dismissedChartIds) {
        List<ResolvedRow> rows = memberRows(
            safeLoans(localLoans),
            Map.of(),
            Map.of(),
            safeIds(dismissedChartIds),
            saccoId
        );
        return display(rows, STATUS_LOCAL_ONLY, "Showing LMS active loans.");
    }

    public ActiveLoanDisplay memberDashboardRows(Member member,
                                                 String saccoId,
                                                 List<LoanApplication> localLoans,
                                                 Set<UUID> dismissedChartIds) {
        ExternalLoanData external = fetchExternalLoans(member, firstStationId(localLoans), null);
        List<ResolvedRow> rows = memberRows(
            safeLoans(localLoans),
            external.activeByLoanId(),
            external.outstandingByLoanId(),
            safeIds(dismissedChartIds),
            saccoId
        );
        return display(rows, external.status(), external.message());
    }

    public ActiveLoanDisplay localStaffReviewRows(String saccoId,
                                                  LoanApplication currentApplication,
                                                  List<LoanApplication> localLoans) {
        List<ResolvedRow> rows = staffRows(
            safeLoans(localLoans),
            Map.of(),
            Map.of(),
            currentApplication,
            saccoId
        );
        return display(rows, STATUS_LOCAL_ONLY, "Showing LMS active loans.");
    }

    public ActiveLoanDisplay staffReviewRows(Member applicant,
                                             String saccoId,
                                             LoanApplication currentApplication,
                                             List<LoanApplication> localLoans) {
        ExternalLoanData external = fetchExternalLoans(applicant, firstStationId(localLoans), currentApplication);
        List<ResolvedRow> rows = staffRows(
            safeLoans(localLoans),
            external.activeByLoanId(),
            external.outstandingByLoanId(),
            currentApplication,
            saccoId
        );
        return display(rows, external.status(), external.message());
    }

    private List<ResolvedRow> memberRows(List<LoanApplication> localLoans,
                                         Map<String, ForesightActiveLoan> activeByLoanId,
                                         Map<String, ForesightAccountSummary.ForesightOutstandingLoan> outstandingByLoanId,
                                         Set<UUID> dismissedChartIds,
                                         String saccoId) {
        LocalDate today = LocalDate.now();
        Map<LoanType, String> productNames = loanProductDisplayService.namesForSacco(saccoId);
        Set<String> renderedLoanIds = new LinkedHashSet<>();
        List<ResolvedRow> rows = localLoans.stream()
            .filter(this::isActiveLocalLoan)
            .sorted(Comparator.comparing(LoanApplication::getFinalDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .filter(app -> !hasRepaymentTimeframeEnded(app) || !dismissedChartIds.contains(app.getId()))
            .map(app -> {
                String normalizedLoanId = normalizeLoanId(app.getLoanId());
                if (!normalizedLoanId.isBlank()) {
                    renderedLoanIds.add(normalizedLoanId);
                }
                return localMemberRow(app, outstandingByLoanId.get(normalizedLoanId), productNames, today);
            })
            .collect(Collectors.toCollection(ArrayList::new));

        activeByLoanId.entrySet().stream()
            .filter(entry -> !renderedLoanIds.contains(entry.getKey()))
            .sorted(Comparator.comparing(
                entry -> entry.getValue().disbursedDate(),
                Comparator.nullsLast(Comparator.reverseOrder())))
            .map(entry -> externalMemberRow(entry.getValue(), outstandingByLoanId.get(entry.getKey())))
            .forEach(rows::add);
        return rows;
    }

    private List<ResolvedRow> staffRows(List<LoanApplication> localLoans,
                                        Map<String, ForesightActiveLoan> activeByLoanId,
                                        Map<String, ForesightAccountSummary.ForesightOutstandingLoan> outstandingByLoanId,
                                        LoanApplication currentApplication,
                                        String saccoId) {
        Map<LoanType, String> productNames = loanProductDisplayService.namesForSacco(saccoId);
        String excludedLoanId = normalizeLoanId(currentApplication == null ? null : currentApplication.getLoanId());
        UUID excludedApplicationId = currentApplication == null ? null : currentApplication.getId();
        Set<String> renderedLoanIds = new LinkedHashSet<>();
        List<ResolvedRow> rows = localLoans.stream()
            .filter(this::isActiveLocalLoan)
            .filter(loan -> excludedApplicationId == null || !excludedApplicationId.equals(loan.getId()))
            .filter(loan -> {
                String normalizedLoanId = normalizeLoanId(loan.getLoanId());
                return normalizedLoanId.isBlank() || !normalizedLoanId.equals(excludedLoanId);
            })
            .sorted(Comparator.comparing(
                LoanApplication::getDisbursementDate,
                Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(LoanApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(loan -> {
                String normalizedLoanId = normalizeLoanId(loan.getLoanId());
                if (!normalizedLoanId.isBlank()) {
                    renderedLoanIds.add(normalizedLoanId);
                }
                return localStaffRow(loan, outstandingByLoanId.get(normalizedLoanId), productNames, currentApplication);
            })
            .collect(Collectors.toCollection(ArrayList::new));

        activeByLoanId.entrySet().stream()
            .filter(entry -> !renderedLoanIds.contains(entry.getKey()))
            .filter(entry -> excludedLoanId.isBlank() || !excludedLoanId.equals(entry.getKey()))
            .sorted(Comparator.comparing(
                entry -> entry.getValue().disbursedDate(),
                Comparator.nullsLast(Comparator.reverseOrder())))
            .map(entry -> externalStaffRow(entry.getValue(), outstandingByLoanId.get(entry.getKey())))
            .forEach(rows::add);
        return rows;
    }

    private ResolvedRow localMemberRow(LoanApplication app,
                                       ForesightAccountSummary.ForesightOutstandingLoan outstandingLoan,
                                       Map<LoanType, String> productNames,
                                       LocalDate today) {
        long daysLeft = app.getFinalDueDate() == null
            ? 0
            : Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(today, app.getFinalDueDate()));
        long totalDays = totalRepaymentDays(app);
        long elapsedDays = Math.max(0, totalDays - daysLeft);
        long remainingPercent = totalDays <= 0
            ? (daysLeft > 0 ? 100 : 0)
            : Math.max(0, Math.min(100, Math.round((daysLeft * 100.0d) / totalDays)));
        BigDecimal balance = balanceForLocalLoan(app, outstandingLoan);
        BigDecimal paidAmount = loanPresentationService.activeLoanPaidAmount(app);
        String productName = loanProductDisplayService.displayName(app, productNames);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("fullId", stringValue(app.getId()));
        row.put("applicationId", stringValue(app.getId()));
        row.put("loanId", valueOrDash(app.getLoanId()));
        row.put("loanProductName", productName);
        row.put("applicantReason", applicantReason(app));
        row.put("amountLabel", money(app.getAmount()));
        row.put("disbursementDate", dateLabel(app.getDisbursementDate()));
        row.put("startDate", dateLabel(resolveRepaymentTimerStartDate(app)));
        row.put("daysLeft", daysLeft);
        row.put("elapsedDays", elapsedDays);
        row.put("totalDays", totalDays);
        row.put("remainingPercent", remainingPercent);
        row.put("finalDueDate", dateLabel(app.getFinalDueDate()));
        row.put("countdown", loanPresentationService.countdownLabel(app.getFinalDueDate()));
        row.put("canDismiss", hasRepaymentTimeframeEnded(app));
        row.put("repaymentStateCode", app.getStatus() == null ? "" : app.getStatus().name());
        row.put("repaymentStateLabel", repaymentStateLabel(app, today));
        row.put("repaymentStateClasses", repaymentStateClasses(app, today));
        row.put("loanDescription", productName);
        row.put("lastPaymentDate", loanPresentationService.activeLoanLastPaymentDateLabel(app));
        row.put("totalOutstanding", money(balance));
        row.put("paidAmount", money(paidAmount));
        row.put("currentBalance", money(balance));
        row.put("outstandingPrincipal", money(localOutstandingPrincipal(app, outstandingLoan)));
        row.put("outstandingInterest", money(localOutstandingInterest(app, outstandingLoan)));
        row.put("totalPrincipalPaid", money(loanPresentationService.activeLoanTotalPrincipalPaid(app)));
        row.put("totalInterestPaid", money(loanPresentationService.activeLoanTotalInterestPaid(app)));
        row.put("scheduleAvailable", true);
        row.put("scheduleUrl", "/app/loan-applications/" + app.getId() + "#repayment-plan");
        row.put("externalOnly", false);
        return new ResolvedRow(row, balance);
    }

    private ResolvedRow externalMemberRow(ForesightActiveLoan loan,
                                          ForesightAccountSummary.ForesightOutstandingLoan outstandingLoan) {
        BigDecimal balance = balanceForExternalLoan(outstandingLoan);
        Map<String, Object> row = new LinkedHashMap<>();
        String loanId = valueOrDash(loan.loanIdText());
        row.put("fullId", "");
        row.put("applicationId", "foresight-" + loanId);
        row.put("loanId", loanId);
        row.put("loanProductName", valueOrDash(loan.loanDescription()));
        row.put("applicantReason", "-");
        row.put("amountLabel", moneyOrDash(firstNonNull(loan.requestedAmount(), loan.disbursedAmount())));
        row.put("disbursementDate", dateLabel(loan.disbursedDate()));
        row.put("startDate", dateLabel(loan.disbursedDate()));
        row.put("daysLeft", 0);
        row.put("elapsedDays", 0);
        row.put("totalDays", 0);
        row.put("remainingPercent", 0);
        row.put("finalDueDate", "-");
        row.put("countdown", "");
        row.put("canDismiss", false);
        row.put("repaymentStateCode", "ACTIVE");
        row.put("repaymentStateLabel", "Active");
        row.put("repaymentStateClasses", ACTIVE_STATE_CLASSES);
        row.put("loanDescription", valueOrDash(loan.loanDescription()));
        row.put("lastPaymentDate", "-");
        row.put("totalOutstanding", moneyOrDash(balance));
        row.put("paidAmount", "-");
        row.put("currentBalance", moneyOrDash(balance));
        row.put("outstandingPrincipal", moneyOrDash(outstandingLoan == null ? null : outstandingLoan.outstandingPrincipal()));
        row.put("outstandingInterest", moneyOrDash(outstandingLoan == null ? null : outstandingLoan.outstandingInterest()));
        row.put("totalPrincipalPaid", "-");
        row.put("totalInterestPaid", "-");
        row.put("scheduleAvailable", false);
        row.put("scheduleUrl", "");
        row.put("externalOnly", true);
        return new ResolvedRow(row, balance);
    }

    private ResolvedRow localStaffRow(LoanApplication loan,
                                      ForesightAccountSummary.ForesightOutstandingLoan outstandingLoan,
                                      Map<LoanType, String> productNames,
                                      LoanApplication currentApplication) {
        BigDecimal balance = balanceForLocalLoan(loan, outstandingLoan);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", stringValue(loan.getId()));
        row.put("shortId", loanLabel(loan));
        row.put("loanId", valueOrDash(loan.getLoanId()));
        row.put("loanTypeLabel", loanProductDisplayService.displayName(loan, productNames));
        row.put("amount", money(loan.getAmount()));
        row.put("outstandingBalance", money(balance));
        row.put("installmentAmount", money(loan.getInstallmentAmount()));
        row.put("repaymentFrequency", loan.getRepaymentFrequency() == null
            ? "Standard schedule"
            : humanizeEnum(loan.getRepaymentFrequency().name()));
        row.put("disbursedAt", dateLabel(loan.getDisbursementDate()));
        row.put("finalDueDate", dateLabel(loan.getFinalDueDate()));
        row.put("countdown", loanPresentationService.countdownLabel(loan.getFinalDueDate()));
        row.put("isTopUpSource", String.valueOf(currentApplication != null
            && currentApplication.getTopUpSourceLoanId() != null
            && currentApplication.getTopUpSourceLoanId().equals(loan.getId())));
        row.put("scheduleAvailable", true);
        row.put("externalOnly", false);
        return new ResolvedRow(row, balance);
    }

    private ResolvedRow externalStaffRow(ForesightActiveLoan loan,
                                         ForesightAccountSummary.ForesightOutstandingLoan outstandingLoan) {
        BigDecimal balance = balanceForExternalLoan(outstandingLoan);
        String loanId = valueOrDash(loan.loanIdText());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "foresight-" + loanId);
        row.put("shortId", loanId);
        row.put("loanId", loanId);
        row.put("loanTypeLabel", valueOrDash(loan.loanDescription()));
        row.put("amount", moneyOrDash(firstNonNull(loan.requestedAmount(), loan.disbursedAmount())));
        row.put("outstandingBalance", moneyOrDash(balance));
        row.put("installmentAmount", "-");
        row.put("repaymentFrequency", "");
        row.put("disbursedAt", dateLabel(loan.disbursedDate()));
        row.put("finalDueDate", "-");
        row.put("countdown", "");
        row.put("isTopUpSource", "false");
        row.put("scheduleAvailable", false);
        row.put("externalOnly", true);
        return new ResolvedRow(row, balance);
    }

    private ExternalLoanData fetchExternalLoans(Member member, String fallbackStationId, LoanApplication currentApplication) {
        String memberNumber = member == null ? null : blankToNull(member.getMemberNo());
        String stationId = member == null ? null : blankToNull(member.getStationId());
        if (stationId == null && currentApplication != null) {
            stationId = blankToNull(currentApplication.getStationId());
        }
        if (stationId == null) {
            stationId = blankToNull(fallbackStationId);
        }
        if (memberNumber == null || stationId == null) {
            return new ExternalLoanData(Map.of(), Map.of(), STATUS_LOCAL_ONLY, "Showing LMS active loans.");
        }
        try {
            Map<String, ForesightActiveLoan> activeByLoanId = foresightDirectoryService.fetchActiveLoans(memberNumber, stationId).stream()
                .filter(loan -> !normalizeLoanId(loan.loanIdText()).isBlank())
                .collect(Collectors.toMap(
                    loan -> normalizeLoanId(loan.loanIdText()),
                    loan -> loan,
                    (first, ignored) -> first,
                    LinkedHashMap::new
                ));
            ForesightAccountSummary summary = foresightDirectoryService.fetchAccountSummary(memberNumber, stationId);
            Map<String, ForesightAccountSummary.ForesightOutstandingLoan> outstandingByLoanId =
                summary == null || summary.outstandingLoans() == null
                    ? Map.of()
                    : summary.outstandingLoans().stream()
                        .filter(loan -> !normalizeLoanId(loan.loanIdText()).isBlank())
                        .collect(Collectors.toMap(
                            loan -> normalizeLoanId(loan.loanIdText()),
                            loan -> loan,
                            (first, ignored) -> first,
                            LinkedHashMap::new
                        ));
            return new ExternalLoanData(activeByLoanId, outstandingByLoanId, STATUS_AVAILABLE, "Active loans loaded.");
        } catch (UpstreamAvailabilityException ex) {
            return new ExternalLoanData(
                Map.of(),
                Map.of(),
                STATUS_UNAVAILABLE,
                "Live active loans are unavailable right now. Showing LMS active loans."
            );
        } catch (IllegalStateException ex) {
            return new ExternalLoanData(
                Map.of(),
                Map.of(),
                STATUS_UNAVAILABLE,
                "Live active loans are unavailable right now. Showing LMS active loans."
            );
        }
    }

    private ActiveLoanDisplay display(List<ResolvedRow> resolvedRows, String status, String message) {
        BigDecimal totalExposure = resolvedRows.stream()
            .map(ResolvedRow::balance)
            .filter(balance -> balance != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        List<Map<String, Object>> rows = resolvedRows.stream()
            .map(ResolvedRow::row)
            .toList();
        return new ActiveLoanDisplay(rows, rows.size(), money(totalExposure), status, message);
    }

    private BigDecimal balanceForLocalLoan(LoanApplication app,
                                           ForesightAccountSummary.ForesightOutstandingLoan outstandingLoan) {
        BigDecimal liveBalance = balanceForExternalLoan(outstandingLoan);
        return liveBalance == null ? loanPresentationService.activeLoanOutstandingBalance(app) : nonNegative(liveBalance);
    }

    private BigDecimal balanceForExternalLoan(ForesightAccountSummary.ForesightOutstandingLoan outstandingLoan) {
        if (outstandingLoan == null) {
            return null;
        }
        BigDecimal balance = outstandingLoan.balanceIncludingInterest();
        return balance == null ? null : nonNegative(balance);
    }

    private BigDecimal localOutstandingPrincipal(LoanApplication app,
                                                 ForesightAccountSummary.ForesightOutstandingLoan outstandingLoan) {
        if (outstandingLoan != null && outstandingLoan.outstandingPrincipal() != null) {
            return nonNegative(outstandingLoan.outstandingPrincipal());
        }
        return loanPresentationService.activeLoanOutstandingPrincipal(app);
    }

    private BigDecimal localOutstandingInterest(LoanApplication app,
                                                ForesightAccountSummary.ForesightOutstandingLoan outstandingLoan) {
        if (outstandingLoan != null && outstandingLoan.outstandingInterest() != null) {
            return nonNegative(outstandingLoan.outstandingInterest());
        }
        return loanPresentationService.activeLoanOutstandingInterest(app);
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) < 0
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String applicantReason(LoanApplication app) {
        if (app == null || app.getFormData() == null || app.getFormData().isBlank()) {
            return "";
        }
        try {
            Map<String, Object> formData = objectMapper.readValue(app.getFormData(), new TypeReference<>() {});
            Object purpose = formData.get("purpose");
            return purpose == null ? "" : String.valueOf(purpose).trim();
        } catch (Exception ex) {
            return "";
        }
    }

    private String repaymentStateLabel(LoanApplication app, LocalDate today) {
        if (app != null && app.getStatus() == LoanStatus.PAR) {
            return "Portfolio At Risk";
        }
        if (app != null && app.getStatus() == LoanStatus.DEFAULTED) {
            return "Defaulted";
        }
        if (app != null && app.getFinalDueDate() != null && app.getFinalDueDate().isBefore(today)) {
            return "Due";
        }
        return "Active";
    }

    private String repaymentStateClasses(LoanApplication app, LocalDate today) {
        if (app != null && app.getStatus() == LoanStatus.PAR) {
            return "border-amber-200 bg-amber-50 text-amber-700";
        }
        if (app != null && app.getStatus() == LoanStatus.DEFAULTED) {
            return "border-rose-200 bg-rose-50 text-rose-700";
        }
        if (app != null && app.getFinalDueDate() != null && app.getFinalDueDate().isBefore(today)) {
            return "border-amber-200 bg-amber-50 text-amber-700";
        }
        return ACTIVE_STATE_CLASSES;
    }

    private boolean hasRepaymentTimeframeEnded(LoanApplication app) {
        return app != null && app.getFinalDueDate() != null && app.getFinalDueDate().isBefore(LocalDate.now());
    }

    private long totalRepaymentDays(LoanApplication app) {
        if (app == null || app.getFinalDueDate() == null) {
            return 0;
        }
        LocalDate startDate = resolveRepaymentTimerStartDate(app);
        return startDate == null ? 0 : Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(startDate, app.getFinalDueDate()));
    }

    private LocalDate resolveRepaymentTimerStartDate(LoanApplication app) {
        if (app == null) {
            return null;
        }
        return app.getDisbursementDate() == null ? app.getFirstRepaymentDate() : app.getDisbursementDate();
    }

    private boolean isActiveLocalLoan(LoanApplication loan) {
        return loan != null && (loan.getStatus() == LoanStatus.DISBURSED
            || loan.getStatus() == LoanStatus.PAR
            || loan.getStatus() == LoanStatus.DEFAULTED);
    }

    private String loanLabel(LoanApplication loan) {
        if (loan == null) {
            return "-";
        }
        if (loan.getApplicationNumber() != null) {
            return loan.getApplicationNumber().toString();
        }
        return valueOrDash(loan.getLoanId());
    }

    private List<LoanApplication> safeLoans(List<LoanApplication> loans) {
        return loans == null ? List.of() : loans;
    }

    private Set<UUID> safeIds(Set<UUID> ids) {
        return ids == null ? Set.of() : ids;
    }

    private String firstStationId(List<LoanApplication> loans) {
        if (loans == null) {
            return null;
        }
        return loans.stream()
            .map(LoanApplication::getStationId)
            .filter(stationId -> stationId != null && !stationId.isBlank())
            .findFirst()
            .orElse(null);
    }

    private String normalizeLoanId(String loanId) {
        return loanId == null ? "" : loanId.trim().toLowerCase(Locale.ENGLISH);
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String dateLabel(LocalDate date) {
        return date == null ? "-" : date.toString();
    }

    private String money(BigDecimal value) {
        return loanPresentationService.formatMoneyDisplay(value);
    }

    private String moneyOrDash(BigDecimal value) {
        return value == null ? "-" : money(value);
    }

    private BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
        return first == null ? second : first;
    }

    private String humanizeEnum(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] parts = value.toLowerCase(Locale.ENGLISH).split("_");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            words.add(part.substring(0, 1).toUpperCase(Locale.ENGLISH) + part.substring(1));
        }
        return String.join(" ", words);
    }

    public record ActiveLoanDisplay(
        List<Map<String, Object>> rows,
        int count,
        String totalExposure,
        String status,
        String message
    ) {
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("rows", rows);
            payload.put("count", count);
            payload.put("totalExposure", totalExposure);
            payload.put("status", status);
            payload.put("message", message);
            return payload;
        }
    }

    private record ResolvedRow(Map<String, Object> row, BigDecimal balance) {
    }

    private record ExternalLoanData(
        Map<String, ForesightActiveLoan> activeByLoanId,
        Map<String, ForesightAccountSummary.ForesightOutstandingLoan> outstandingByLoanId,
        String status,
        String message
    ) {
    }
}
