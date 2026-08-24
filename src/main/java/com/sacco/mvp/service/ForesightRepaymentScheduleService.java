package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.RepaymentFrequency;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import com.sacco.mvp.integration.foresight.ForesightRepaymentScheduleRow;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForesightRepaymentScheduleService {
    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_NO_DATA = "NO_DATA";
    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String NO_DATA_MESSAGE = "Repayment schedule is not available from Foresight yet.";
    private static final String UNAVAILABLE_MESSAGE = "Repayment schedule is unavailable right now. Please retry again later.";
    private static final List<LoanStatus> SYNCABLE_STATUSES = List.of(LoanStatus.DISBURSED, LoanStatus.DEFAULTED);

    private final ForesightDirectoryService foresightDirectoryService;
    private final LoanApplicationRepository loanApplicationRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    public RepaymentScheduleDisplay loadLocalLoanSchedule(LoanApplication loan, Member member) {
        if (loan == null) {
            return unavailable("Loan application was not found.");
        }
        if (!isActualScheduleStatus(loan.getStatus())) {
            return unavailable("Actual repayment schedule is available after disbursement.");
        }
        String stationId = stationId(loan, member);
        return loadSchedule(
            member == null ? null : member.getMemberNo(),
            stationId,
            loan.getLoanId(),
            loan
        );
    }

    public RepaymentScheduleDisplay loadExternalLoanSchedule(Member member, String fallbackStationId, String loanId) {
        return loadSchedule(
            member == null ? null : member.getMemberNo(),
            firstText(member == null ? null : member.getStationId(), fallbackStationId),
            loanId,
            null
        );
    }

    public RefreshResult refreshLocalLoanSchedule(UUID loanApplicationId) {
        if (loanApplicationId == null) {
            return RefreshResult.skipped(null, "", "Loan application ID is missing.");
        }
        LoanApplication loan = loanApplicationRepository.findById(loanApplicationId).orElse(null);
        if (loan == null) {
            return RefreshResult.skipped(loanApplicationId, "", "Loan application was not found.");
        }
        if (!isSyncable(loan)) {
            return RefreshResult.skipped(loan.getId(), loanId(loan), "Loan is not disbursed or has no LMS loan ID.");
        }
        Member member = memberRepository.findById(loan.getApplicantMemberId()).orElse(null);
        RepaymentScheduleDisplay display = loadLocalLoanSchedule(loan, member);
        if (!STATUS_AVAILABLE.equals(display.status())) {
            return new RefreshResult(loan.getId(), loanId(loan), RefreshStatus.NO_DATA, display.message());
        }
        persistSnapshot(loan.getId(), display.snapshot());
        return new RefreshResult(loan.getId(), loanId(loan), RefreshStatus.UPDATED, "Repayment schedule updated.");
    }

    public RefreshResult tryRefreshLocalLoanSchedule(UUID loanApplicationId) {
        try {
            return refreshLocalLoanSchedule(loanApplicationId);
        } catch (RuntimeException ex) {
            log.warn("Unable to refresh Foresight repayment schedule for application {}: {}",
                loanApplicationId, ex.getMessage());
            return new RefreshResult(loanApplicationId, "", RefreshStatus.ERROR, UNAVAILABLE_MESSAGE);
        }
    }

    public ScheduledSyncResult syncActiveLoanSchedules(int batchSize) {
        int safeBatchSize = batchSize <= 0 ? 100 : Math.min(batchSize, 500);
        int updated = 0;
        int noData = 0;
        int errors = 0;
        int skipped = 0;
        PageRequest pageRequest = PageRequest.of(0, safeBatchSize, Sort.by(Sort.Order.asc("id")));
        while (true) {
            Page<LoanApplication> page = loanApplicationRepository.findByStatusInAndLoanIdIsNotNull(
                SYNCABLE_STATUSES,
                pageRequest
            );
            for (LoanApplication loan : page.getContent()) {
                RefreshResult result = tryRefreshLocalLoanSchedule(loan.getId());
                switch (result.status()) {
                    case UPDATED -> updated++;
                    case NO_DATA -> noData++;
                    case ERROR -> errors++;
                    case SKIPPED -> skipped++;
                }
            }
            if (!page.hasNext()) {
                break;
            }
            pageRequest = PageRequest.of(page.nextPageable().getPageNumber(), safeBatchSize, Sort.by(Sort.Order.asc("id")));
        }
        return new ScheduledSyncResult(updated, noData, errors, skipped);
    }

    private RepaymentScheduleDisplay loadSchedule(String memberNumber,
                                                  String stationId,
                                                  String loanId,
                                                  LoanApplication localLoan) {
        String normalizedMemberNumber = blankToNull(memberNumber);
        String normalizedStationId = blankToNull(stationId);
        String normalizedLoanId = blankToNull(loanId);
        if (normalizedMemberNumber == null || normalizedStationId == null || normalizedLoanId == null) {
            return unavailable("Foresight repayment schedule needs member number, station ID, and loan ID.");
        }
        try {
            List<ForesightRepaymentScheduleRow> rows = foresightDirectoryService.fetchRepaymentSchedule(
                normalizedMemberNumber,
                normalizedStationId,
                normalizedLoanId
            );
            List<ForesightRepaymentScheduleRow> usableRows = usableRows(rows);
            if (usableRows.isEmpty()) {
                return new RepaymentScheduleDisplay(
                    List.of(),
                    Map.of(),
                    List.of(),
                    STATUS_NO_DATA,
                    NO_DATA_MESSAGE,
                    null
                );
            }
            ScheduleSnapshot snapshot = toSnapshot(usableRows, localLoan, normalizedLoanId);
            return new RepaymentScheduleDisplay(
                snapshot.displayRows(),
                snapshot.displaySummary(),
                summaryEntries(snapshot.displaySummary()),
                STATUS_AVAILABLE,
                "Repayment schedule loaded.",
                snapshot
            );
        } catch (IllegalStateException ex) {
            log.warn("Foresight repayment schedule unavailable for loan {}: {}", normalizedLoanId, ex.getMessage());
            return unavailable(UNAVAILABLE_MESSAGE);
        }
    }

    private List<ForesightRepaymentScheduleRow> usableRows(List<ForesightRepaymentScheduleRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
            .filter(Objects::nonNull)
            .filter(row -> row.paymentNumber() != null || row.month() != null)
            .sorted(Comparator
                .comparing(ForesightRepaymentScheduleRow::paymentNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ForesightRepaymentScheduleRow::month, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    private ScheduleSnapshot toSnapshot(List<ForesightRepaymentScheduleRow> rows,
                                        LoanApplication localLoan,
                                        String loanId) {
        BigDecimal totalPrincipal = rows.stream()
            .map(ForesightRepaymentScheduleRow::principal)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalInterest = rows.stream()
            .map(ForesightRepaymentScheduleRow::interest)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = rows.stream()
            .map(ForesightRepaymentScheduleRow::amountToPay)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal initialPrincipal = firstNonNull(rows.getFirst().beginningBalance(), totalPrincipal);
        BigDecimal installmentAmount = rows.getFirst().amountToPay() == null
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : rows.getFirst().amountToPay().setScale(2, RoundingMode.HALF_UP);
        LocalDate firstDueDate = rows.getFirst().month();
        LocalDate finalDueDate = rows.getLast().month();
        String interestMethod = inferInterestMethod(rows);

        List<Map<String, Object>> displayRows = new ArrayList<>();
        List<Map<String, Object>> cacheRows = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            ForesightRepaymentScheduleRow item = rows.get(index);
            int paymentNumber = item.paymentNumber() == null ? index + 1 : item.paymentNumber();
            Map<String, Object> display = new LinkedHashMap<>();
            display.put("installment", "Installment " + paymentNumber);
            display.put("installmentNumber", String.valueOf(paymentNumber));
            display.put("pmtNo", String.valueOf(paymentNumber));
            display.put("month", dateLabel(item.month()));
            display.put("dueDate", dateLabel(item.month()));
            display.put("beginningBalance", moneyOrDash(item.beginningBalance()));
            display.put("amount", moneyOrDash(item.amountToPay()));
            display.put("payment", moneyOrDash(item.amountToPay()));
            display.put("loanAmount", moneyOrDash(item.principal()));
            display.put("principal", moneyOrDash(item.principal()));
            display.put("interest", moneyOrDash(item.interest()));
            display.put("endingBalance", moneyOrDash(item.endingBalance()));
            display.put("outstandingBalance", moneyOrDash(item.endingBalance()));
            displayRows.add(display);

            Map<String, Object> cache = new LinkedHashMap<>();
            cache.put("installmentNumber", paymentNumber);
            cache.put("dueDate", item.month() == null ? null : item.month().toString());
            cache.put("month", item.month() == null ? null : item.month().toString());
            cache.put("beginningBalance", scaled(item.beginningBalance()));
            cache.put("amount", scaled(item.amountToPay()));
            cache.put("principalComponent", scaled(item.principal()));
            cache.put("interestComponent", scaled(item.interest()));
            cache.put("outstandingBalance", scaled(item.endingBalance()));
            cache.put("status", "UPCOMING");
            cacheRows.add(cache);
        }

        Map<String, Object> displaySummary = new LinkedHashMap<>();
        displaySummary.put("Installment Amount", moneyOrDash(installmentAmount));
        displaySummary.put("Total Interest", moneyOrDash(totalInterest));
        displaySummary.put("Total Principal", moneyOrDash(initialPrincipal));
        displaySummary.put("Total Amount", moneyOrDash(totalAmount));
        displaySummary.put("Repayment Frequency", "Monthly");
        displaySummary.put("First Repayment Date", dateLabel(firstDueDate));
        displaySummary.put("Final Due Date", dateLabel(finalDueDate));
        displaySummary.put("Installments", rows.size());
        displaySummary.put("Interest Method", interestMethod);

        Map<String, Object> cacheSummary = new LinkedHashMap<>();
        cacheSummary.put("source", "FORESIGHT");
        cacheSummary.put("foresightLoanId", loanId);
        cacheSummary.put("foresightRepaymentScheduleFetchedAt", OffsetDateTime.now().toString());
        cacheSummary.put("disbursedPrincipal", scaled(initialPrincipal));
        cacheSummary.put("depositAmount", localLoan == null ? null : scaled(localLoan.getDepositAmount()));
        cacheSummary.put("disbursementDate", localLoan == null || localLoan.getDisbursementDate() == null
            ? null
            : localLoan.getDisbursementDate().toString());
        cacheSummary.put("firstRepaymentDate", firstDueDate == null ? null : firstDueDate.toString());
        cacheSummary.put("finalDueDate", finalDueDate == null ? null : finalDueDate.toString());
        cacheSummary.put("repaymentFrequency", RepaymentFrequency.MONTHLY.name());
        cacheSummary.put("installmentAmount", scaled(installmentAmount));
        cacheSummary.put("installments", rows.size());
        cacheSummary.put("interestMethod", interestMethod);
        cacheSummary.put("totalInterest", scaled(totalInterest));
        cacheSummary.put("totalPrincipal", scaled(initialPrincipal));
        cacheSummary.put("totalAmount", scaled(totalAmount));
        cacheSummary.put("schedule", cacheRows);

        return new ScheduleSnapshot(
            displayRows,
            displaySummary,
            cacheSummary,
            firstDueDate,
            finalDueDate,
            installmentAmount
        );
    }

    private void persistSnapshot(UUID loanApplicationId, ScheduleSnapshot snapshot) {
        if (loanApplicationId == null || snapshot == null) {
            return;
        }
        LoanApplication loan = loanApplicationRepository.findById(loanApplicationId).orElse(null);
        if (loan == null) {
            return;
        }
        loan.setRepaymentScheduleJson(toJson(snapshot.cacheSummary()));
        loan.setFirstRepaymentDate(snapshot.firstDueDate());
        loan.setFinalDueDate(snapshot.finalDueDate());
        loan.setInstallmentAmount(snapshot.installmentAmount());
        loan.setRepaymentFrequency(RepaymentFrequency.MONTHLY);
        loanApplicationRepository.save(loan);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to write Foresight repayment schedule cache.", ex);
        }
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

    private String inferInterestMethod(List<ForesightRepaymentScheduleRow> rows) {
        List<BigDecimal> interestValues = rows.stream()
            .map(ForesightRepaymentScheduleRow::interest)
            .filter(Objects::nonNull)
            .map(value -> value.setScale(2, RoundingMode.HALF_UP))
            .toList();
        if (interestValues.size() < 2) {
            return "Foresight schedule";
        }
        boolean allEqual = interestValues.stream().distinct().count() == 1;
        if (allEqual) {
            return "Flat schedule";
        }
        boolean nonIncreasing = true;
        for (int index = 1; index < interestValues.size(); index++) {
            if (interestValues.get(index).compareTo(interestValues.get(index - 1)) > 0) {
                nonIncreasing = false;
                break;
            }
        }
        return nonIncreasing ? "Reducing balance" : "Foresight schedule";
    }

    private boolean isSyncable(LoanApplication loan) {
        return loan != null
            && SYNCABLE_STATUSES.contains(loan.getStatus())
            && blankToNull(loan.getLoanId()) != null;
    }

    private boolean isActualScheduleStatus(LoanStatus status) {
        return status == LoanStatus.DISBURSED || status == LoanStatus.DEFAULTED || status == LoanStatus.PAID;
    }

    private String stationId(LoanApplication loan, Member member) {
        return firstText(
            loan == null ? null : loan.getStationId(),
            member == null ? null : member.getStationId()
        );
    }

    private String loanId(LoanApplication loan) {
        return loan == null || loan.getLoanId() == null ? "" : loan.getLoanId().trim();
    }

    private RepaymentScheduleDisplay unavailable(String message) {
        return new RepaymentScheduleDisplay(List.of(), Map.of(), List.of(), STATUS_UNAVAILABLE, message, null);
    }

    private String firstText(String first, String second) {
        return Optional.ofNullable(blankToNull(first)).orElseGet(() -> blankToNull(second));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
        return first == null ? second : first;
    }

    private BigDecimal scaled(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String dateLabel(LocalDate value) {
        return value == null ? "-" : value.toString();
    }

    private String moneyOrDash(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        DecimalFormat format = new DecimalFormat("#,##0.##", new DecimalFormatSymbols(Locale.US));
        return "TSh " + format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    public record RepaymentScheduleDisplay(
        List<Map<String, Object>> rows,
        Map<String, Object> summary,
        List<Map<String, Object>> summaryEntries,
        String status,
        String message,
        ScheduleSnapshot snapshot
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

    public record ScheduleSnapshot(
        List<Map<String, Object>> displayRows,
        Map<String, Object> displaySummary,
        Map<String, Object> cacheSummary,
        LocalDate firstDueDate,
        LocalDate finalDueDate,
        BigDecimal installmentAmount
    ) {
    }

    public enum RefreshStatus {
        UPDATED,
        NO_DATA,
        ERROR,
        SKIPPED
    }

    public record RefreshResult(UUID applicationId, String loanId, RefreshStatus status, String message) {
        static RefreshResult skipped(UUID applicationId, String loanId, String message) {
            return new RefreshResult(applicationId, loanId, RefreshStatus.SKIPPED, message);
        }
    }

    public record ScheduledSyncResult(int updated, int noData, int errors, int skipped) {
    }
}
