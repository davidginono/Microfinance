package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LoanRepaymentScheduleDisplayService {
    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_NO_DATA = "NO_DATA";
    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    private final RepaymentScheduleService repaymentScheduleService;
    private final LoanApplicationRepository loanApplicationRepository;

    public RepaymentScheduleDisplay loadLocalLoanSchedule(LoanApplication loan, Member member) {
        if (loan == null) {
            return unavailable("Loan application was not found.");
        }
        if (!isActualScheduleStatus(loan.getStatus())) {
            return unavailable("Actual repayment schedule is available after disbursement.");
        }
        if (loan.getRepaymentScheduleJson() == null || loan.getRepaymentScheduleJson().isBlank()) {
            return new RepaymentScheduleDisplay(
                List.of(),
                Map.of(),
                List.of(),
                STATUS_NO_DATA,
                "Local repayment schedule is not available yet."
            );
        }
        Map<String, Object> summary = repaymentScheduleService.parseSummary(loan.getRepaymentScheduleJson());
        List<Map<String, Object>> rows = repaymentScheduleService.parseRows(loan.getRepaymentScheduleJson())
            .stream()
            .map(this::displayRow)
            .toList();
        Map<String, Object> displaySummary = displaySummary(summary);
        return new RepaymentScheduleDisplay(
            rows,
            displaySummary,
            summaryEntries(displaySummary),
            STATUS_AVAILABLE,
            "Repayment schedule loaded."
        );
    }

    public RepaymentScheduleDisplay loadExternalLoanSchedule(Member member, String fallbackStationId, String loanId) {
        if (member == null || loanId == null || loanId.isBlank()) {
            return unavailable("Local repayment schedule needs a client and loan ID.");
        }
        LoanApplication loan = loanApplicationRepository
            .findFirstByApplicantMemberIdAndLoanIdOrderByCreatedAtDesc(member.getId(), loanId.trim())
            .orElse(null);
        return loadLocalLoanSchedule(loan, member);
    }

    public RefreshResult tryRefreshLocalLoanSchedule(java.util.UUID loanApplicationId) {
        if (loanApplicationId == null) {
            return RefreshResult.skipped(null, "", "Loan application ID is missing.");
        }
        LoanApplication loan = loanApplicationRepository.findById(loanApplicationId).orElse(null);
        if (loan == null) {
            return RefreshResult.skipped(loanApplicationId, "", "Loan application was not found.");
        }
        if (loan.getRepaymentScheduleJson() == null || loan.getRepaymentScheduleJson().isBlank()) {
            return new RefreshResult(loan.getId(), loanId(loan), RefreshStatus.NO_DATA, "Local repayment schedule is not available yet.");
        }
        return new RefreshResult(loan.getId(), loanId(loan), RefreshStatus.UPDATED, "Local repayment schedule is available.");
    }

    private Map<String, Object> displayRow(Map<String, Object> row) {
        Map<String, Object> display = new LinkedHashMap<>();
        Object installmentNumber = row.get("installmentNumber");
        display.put("installment", "Installment " + valueOrDash(installmentNumber));
        display.put("installmentNumber", valueOrDash(installmentNumber));
        display.put("pmtNo", valueOrDash(installmentNumber));
        display.put("month", valueOrDash(row.get("dueDate")));
        display.put("dueDate", valueOrDash(row.get("dueDate")));
        display.put("beginningBalance", "-");
        display.put("amount", moneyOrDash(readBigDecimal(row.get("amount"))));
        display.put("payment", moneyOrDash(readBigDecimal(row.get("amount"))));
        display.put("loanAmount", moneyOrDash(readBigDecimal(row.get("principalComponent"))));
        display.put("principal", moneyOrDash(readBigDecimal(row.get("principalComponent"))));
        display.put("interest", moneyOrDash(readBigDecimal(row.get("interestComponent"))));
        display.put("endingBalance", moneyOrDash(readBigDecimal(row.get("outstandingBalance"))));
        display.put("outstandingBalance", moneyOrDash(readBigDecimal(row.get("outstandingBalance"))));
        return display;
    }

    private Map<String, Object> displaySummary(Map<String, Object> raw) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("Installment Amount", moneyOrDash(readBigDecimal(raw.get("installmentAmount"))));
        summary.put("Total Interest", moneyOrDash(totalInterest(raw)));
        summary.put("Total Principal", moneyOrDash(readBigDecimal(raw.get("disbursedPrincipal"))));
        summary.put("Total Amount", moneyOrDash(totalAmount(raw)));
        summary.put("Repayment Frequency", humanize(String.valueOf(raw.getOrDefault("repaymentFrequency", "MONTHLY"))));
        summary.put("First Repayment Date", valueOrDash(raw.get("firstRepaymentDate")));
        summary.put("Final Due Date", valueOrDash(raw.get("finalDueDate")));
        summary.put("Installments", valueOrDash(raw.get("installments")));
        summary.put("Interest Method", humanize(String.valueOf(raw.getOrDefault("interestMethod", ""))));
        return summary;
    }

    private BigDecimal totalInterest(Map<String, Object> summary) {
        Object schedule = summary.get("schedule");
        if (!(schedule instanceof List<?> rows)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return rows.stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .map(row -> readBigDecimal(row.get("interestComponent")))
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal totalAmount(Map<String, Object> summary) {
        Object schedule = summary.get("schedule");
        if (!(schedule instanceof List<?> rows)) {
            return readBigDecimal(summary.get("disbursedPrincipal"));
        }
        return rows.stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .map(row -> readBigDecimal(row.get("amount")))
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private List<Map<String, Object>> summaryEntries(Map<String, Object> summary) {
        return summary.entrySet().stream()
            .map(entry -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", entry.getKey());
                row.put("value", entry.getValue());
                return row;
            })
            .toList();
    }

    private boolean isActualScheduleStatus(LoanStatus status) {
        return status == LoanStatus.DISBURSED || status == LoanStatus.DEFAULTED || status == LoanStatus.PAR || status == LoanStatus.PAID;
    }

    private String loanId(LoanApplication loan) {
        return loan == null || loan.getLoanId() == null ? "" : loan.getLoanId().trim();
    }

    private RepaymentScheduleDisplay unavailable(String message) {
        return new RepaymentScheduleDisplay(List.of(), Map.of(), List.of(), STATUS_UNAVAILABLE, message);
    }

    private BigDecimal readBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String moneyOrDash(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        DecimalFormat format = new DecimalFormat("#,##0.##", new DecimalFormatSymbols(Locale.US));
        return "TSh " + format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    private String valueOrDash(Object value) {
        return value == null || String.valueOf(value).isBlank() ? "-" : String.valueOf(value).trim();
    }

    private String humanize(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String[] parts = value.toLowerCase(Locale.ENGLISH).split("_");
        StringBuilder label = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(part.substring(0, 1).toUpperCase(Locale.ENGLISH)).append(part.substring(1));
        }
        return label.isEmpty() ? "-" : label.toString();
    }

    public record RepaymentScheduleDisplay(
        List<Map<String, Object>> rows,
        Map<String, Object> summary,
        List<Map<String, Object>> summaryEntries,
        String status,
        String message
    ) {
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("rows", rows == null ? List.of() : rows);
            payload.put("summary", summary == null ? Map.of() : summary);
            payload.put("summaryEntries", summaryEntries == null ? List.of() : summaryEntries);
            payload.put("count", rows == null ? 0 : rows.size());
            payload.put("status", status);
            payload.put("message", message);
            payload.put("scheduleAvailable", STATUS_AVAILABLE.equals(status));
            return payload;
        }
    }

    public enum RefreshStatus {
        UPDATED,
        NO_DATA,
        SKIPPED
    }

    public record RefreshResult(java.util.UUID applicationId, String loanId, RefreshStatus status, String message) {
        static RefreshResult skipped(java.util.UUID applicationId, String loanId, String message) {
            return new RefreshResult(applicationId, loanId, RefreshStatus.SKIPPED, message);
        }
    }
}
