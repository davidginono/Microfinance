package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Member;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActiveLoanDisplayService {
    private static final String STATUS_LOCAL_ONLY = "LOCAL_ONLY";
    private static final String ACTIVE_STATE_CLASSES = "border-emerald-200 bg-emerald-50 text-emerald-700";

    private final LoanPresentationService loanPresentationService;
    private final LoanProductDisplayService loanProductDisplayService;
    private final ObjectMapper objectMapper;

    public ActiveLoanDisplay localMemberDashboardRows(String saccoId,
                                                      List<LoanApplication> localLoans,
                                                      Set<UUID> dismissedChartIds) {
        return display(memberRows(saccoId, localLoans, dismissedChartIds), "Showing local active loans.");
    }

    public ActiveLoanDisplay memberDashboardRows(Member member,
                                                 String saccoId,
                                                 List<LoanApplication> localLoans,
                                                 Set<UUID> dismissedChartIds) {
        return display(memberRows(saccoId, localLoans, dismissedChartIds), "Showing local active loans.");
    }

    public ActiveLoanDisplay localStaffReviewRows(String saccoId,
                                                  LoanApplication currentApplication,
                                                  List<LoanApplication> localLoans) {
        return display(staffRows(saccoId, currentApplication, localLoans), "Showing local active loans.");
    }

    public ActiveLoanDisplay staffReviewRows(Member applicant,
                                             String saccoId,
                                             LoanApplication currentApplication,
                                             List<LoanApplication> localLoans) {
        return display(staffRows(saccoId, currentApplication, localLoans), "Showing local active loans.");
    }

    private List<ResolvedRow> memberRows(String saccoId, List<LoanApplication> localLoans, Set<UUID> dismissedChartIds) {
        LocalDate today = LocalDate.now();
        Map<LoanType, String> productNames = loanProductDisplayService.namesForSacco(saccoId);
        Set<UUID> dismissedIds = dismissedChartIds == null ? Set.of() : dismissedChartIds;
        return safeLoans(localLoans).stream()
            .filter(this::isActiveLocalLoan)
            .sorted(Comparator.comparing(LoanApplication::getFinalDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .filter(app -> !hasRepaymentTimeframeEnded(app) || !dismissedIds.contains(app.getId()))
            .map(app -> localMemberRow(app, productNames, today))
            .toList();
    }

    private List<ResolvedRow> staffRows(String saccoId, LoanApplication currentApplication, List<LoanApplication> localLoans) {
        Map<LoanType, String> productNames = loanProductDisplayService.namesForSacco(saccoId);
        UUID excludedApplicationId = currentApplication == null ? null : currentApplication.getId();
        return safeLoans(localLoans).stream()
            .filter(this::isActiveLocalLoan)
            .filter(loan -> excludedApplicationId == null || !excludedApplicationId.equals(loan.getId()))
            .sorted(Comparator.comparing(
                LoanApplication::getDisbursementDate,
                Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(LoanApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(loan -> localStaffRow(loan, productNames, currentApplication))
            .toList();
    }

    private ResolvedRow localMemberRow(LoanApplication app,
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
        BigDecimal balance = loanPresentationService.activeLoanOutstandingBalance(app);
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
        row.put("outstandingPrincipal", money(loanPresentationService.activeLoanOutstandingPrincipal(app)));
        row.put("outstandingInterest", money(loanPresentationService.activeLoanOutstandingInterest(app)));
        row.put("totalPrincipalPaid", money(loanPresentationService.activeLoanTotalPrincipalPaid(app)));
        row.put("totalInterestPaid", money(loanPresentationService.activeLoanTotalInterestPaid(app)));
        row.put("scheduleAvailable", true);
        row.put("scheduleUrl", "/app/loan-applications/" + app.getId() + "#repayment-plan");
        row.put("scheduleDataUrl", "/app/loan-applications/" + app.getId() + "/repayment-schedule");
        row.put("externalOnly", false);
        return new ResolvedRow(row, balance);
    }

    private ResolvedRow localStaffRow(LoanApplication loan,
                                      Map<LoanType, String> productNames,
                                      LoanApplication currentApplication) {
        BigDecimal balance = loanPresentationService.activeLoanOutstandingBalance(loan);
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
        row.put("scheduleLoanId", loan.getId() == null ? "" : loan.getId().toString());
        row.put("scheduleUrl", "");
        row.put("externalOnly", false);
        return new ResolvedRow(row, balance);
    }

    private ActiveLoanDisplay display(List<ResolvedRow> resolvedRows, String message) {
        BigDecimal totalExposure = resolvedRows.stream()
            .map(ResolvedRow::balance)
            .filter(balance -> balance != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        List<Map<String, Object>> rows = resolvedRows.stream()
            .map(ResolvedRow::row)
            .toList();
        return new ActiveLoanDisplay(rows, rows.size(), money(totalExposure), STATUS_LOCAL_ONLY, message);
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

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String dateLabel(LocalDate date) {
        return date == null ? "-" : date.toString();
    }

    private String money(BigDecimal value) {
        return loanPresentationService.formatMoneyDisplay(value);
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
}
