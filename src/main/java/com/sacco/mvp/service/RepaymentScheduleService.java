package com.sacco.mvp.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.InterestMethod;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.RepaymentFrequency;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RepaymentScheduleService {
    private final ObjectMapper objectMapper;
    private final LoanProductSettingRepository loanProductSettingRepository;

    public ScheduleResult buildSchedule(LoanApplication app,
                                        LocalDate disbursementDate,
                                        LocalDate firstRepaymentDate,
                                        RepaymentFrequency frequency,
                                        BigDecimal installmentOverride,
                                        String disbursementReference,
                                        String disbursementNotes) {
        if (app == null || app.getTenorMonths() == null || app.getTenorMonths() < 1
            || app.getTenorMonths() > LoanAmortizationCalculator.MAX_PAYMENTS || frequency == null
            || disbursementDate == null || firstRepaymentDate == null || !firstRepaymentDate.isAfter(disbursementDate)) {
            throw new IllegalArgumentException("Select valid terms and a first repayment date after disbursement.");
        }
        Map<String, Object> snapshot = parseSummary(app.getFinancialSnapshot());
        boolean decimalQuote = LoanAmortizationCalculator.VERSION.equals(snapshot.get("calculationVersion"));
        if (decimalQuote && !frequency.name().equals(snapshot.get("repaymentFrequency"))) {
            throw new IllegalArgumentException("Repayment frequency differs from the assessed terms. Return the application for reassessment.");
        }
        int installments = decimalQuote ? LoanAmortizationCalculator.numberOfPayments(app.getTenorMonths(), frequency)
            : frequency == RepaymentFrequency.WEEKLY ? app.getTenorMonths() * 4 : app.getTenorMonths();
        LoanProductSetting product = app.getLoanProductSettingId() == null
            ? loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue(app.getSaccoId(), app.getLoanType()).orElse(null)
            : loanProductSettingRepository.findByIdAndSaccoId(app.getLoanProductSettingId(), app.getSaccoId()).orElse(null);
        BigDecimal configuredRate = resolveAnnualInterestRate(app, product);
        InterestMethod interestMethod = resolveInterestMethod(app, product);
        LoanAmortizationCalculator.Result calculation = LoanAmortizationCalculator.calculate(
            app.getAmount(), installments, configuredRate, interestMethod, frequency, installmentOverride,
            resolveFlatInterestAmount(app, configuredRate));
        BigDecimal baseInstallment = calculation.installment();
        if (decimalQuote && (readBigDecimal(snapshot.get("tenorMonths")) == null
            || BigDecimal.valueOf(app.getTenorMonths()).compareTo(readBigDecimal(snapshot.get("tenorMonths"))) != 0
            || readBigDecimal(snapshot.get("numberOfPayments")) == null
            || BigDecimal.valueOf(installments).compareTo(readBigDecimal(snapshot.get("numberOfPayments"))) != 0
            || readBigDecimal(snapshot.get("maximumInstallmentAmount")) == null
            || calculation.maximumInstallment().compareTo(readBigDecimal(snapshot.get("maximumInstallmentAmount"))) != 0
            || readBigDecimal(snapshot.get("periodicRepaymentAmount")) == null
            || baseInstallment.compareTo(readBigDecimal(snapshot.get("periodicRepaymentAmount"))) != 0
            || readBigDecimal(snapshot.get("interestAmount")) == null
            || calculation.totalInterest().compareTo(readBigDecimal(snapshot.get("interestAmount"))) != 0
            || readBigDecimal(snapshot.get("principalAmount")) == null
            || app.getAmount().compareTo(readBigDecimal(snapshot.get("principalAmount"))) != 0)) {
            throw new IllegalArgumentException("Repayment amounts differ from the assessed terms. Return the application for reassessment.");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LoanAmortizationCalculator.Installment installment : calculation.rows()) {
            LocalDate dueDate = frequency == RepaymentFrequency.WEEKLY
                ? firstRepaymentDate.plusWeeks(installment.number() - 1L)
                : firstRepaymentDate.plusMonths(installment.number() - 1L);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("installmentNumber", installment.number());
            row.put("dueDate", dueDate.toString());
            row.put("amount", installment.amount());
            row.put("principalComponent", installment.principal());
            row.put("interestComponent", installment.interest());
            row.put("outstandingBalance", installment.remainingPrincipal());
            row.put("status", "UPCOMING");
            rows.add(row);
        }
        applyInterestInclusiveOutstandingBalances(rows);

        LocalDate finalDueDate = rows.isEmpty()
            ? firstRepaymentDate
            : LocalDate.parse(String.valueOf(rows.get(rows.size() - 1).get("dueDate")));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("disbursedPrincipal", app.getAmount());
        summary.put("depositAmount", app.getDepositAmount());
        summary.put("disbursementDate", disbursementDate.toString());
        summary.put("firstRepaymentDate", firstRepaymentDate.toString());
        summary.put("finalDueDate", finalDueDate.toString());
        summary.put("repaymentFrequency", frequency.name());
        summary.put("installmentAmount", baseInstallment);
        summary.put("installments", installments);
        summary.put("interestMethod", interestMethod.name());
        summary.put("interestRate", configuredRate);
        summary.put("calculationVersion", decimalQuote ? LoanAmortizationCalculator.VERSION : "LEGACY_PERIOD_COUNT_DECIMAL");
        summary.put("disbursementReference", disbursementReference == null ? "" : disbursementReference);
        summary.put("disbursementNotes", disbursementNotes == null ? "" : disbursementNotes);
        summary.put("schedule", rows);

        return new ScheduleResult(
            toJson(summary),
            finalDueDate,
            baseInstallment,
            installments
        );
    }

    public Map<String, Object> parseSummary(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> parseRows(String json) {
        Map<String, Object> summary = parseSummary(json);
        Object schedule = summary.get("schedule");
        if (schedule instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> raw) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    raw.forEach((key, value) -> row.put(String.valueOf(key), value));
                    rows.add(row);
                }
            }
            return rows;
        }
        return Collections.emptyList();
    }

    private void applyInterestInclusiveOutstandingBalances(List<Map<String, Object>> rows) {
        BigDecimal totalScheduled = rows.stream()
            .map(row -> readBigDecimal(row.get("amount")))
            .map(amount -> amount == null ? BigDecimal.ZERO : amount)
            .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal runningPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (Map<String, Object> row : rows) {
            BigDecimal amount = readBigDecimal(row.get("amount"));
            runningPaid = runningPaid.add(amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
            row.put("outstandingBalance", totalScheduled.subtract(runningPaid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        }
    }

    public long daysLeft(LocalDate finalDueDate) {
        if (finalDueDate == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), finalDueDate);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to build repayment schedule");
        }
    }

    private BigDecimal resolveFlatInterestAmount(LoanApplication app, BigDecimal annualRate) {
        Map<String, Object> snapshot = parseSummary(app.getFinancialSnapshot());
        BigDecimal snapshotInterest = readBigDecimal(snapshot.get("interestAmount"));
        if (snapshotInterest != null) {
            return snapshotInterest.setScale(2, RoundingMode.HALF_UP);
        }
        return LoanAmortizationCalculator.flatInterest(app.getAmount(), annualRate, app.getTenorMonths());
    }

    private BigDecimal readBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal resolveAnnualInterestRate(LoanApplication app, LoanProductSetting product) {
        Map<String, Object> snapshot = parseSummary(app.getFinancialSnapshot());
        Object snapshotRate = snapshot.get("interestRate");
        if (snapshotRate != null) {
            try {
                return new BigDecimal(String.valueOf(snapshotRate));
            } catch (NumberFormatException ignored) {
                // Fall back to current product below.
            }
        }
        return product == null || product.getInterestRate() == null
            ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
            : product.getInterestRate();
    }

    private InterestMethod resolveInterestMethod(LoanApplication app, LoanProductSetting product) {
        Map<String, Object> snapshot = parseSummary(app.getFinancialSnapshot());
        Object snapshotMethod = snapshot.get("interestMethod");
        if (snapshotMethod != null) {
            try {
                return InterestMethod.valueOf(String.valueOf(snapshotMethod));
            } catch (IllegalArgumentException ignored) {
                // Fall back to current product below.
            }
        }
        return product == null || product.getInterestMethod() == null
            ? InterestMethod.FLAT_RATE
            : product.getInterestMethod();
    }

    public record ScheduleResult(
        String scheduleJson,
        LocalDate finalDueDate,
        BigDecimal installmentAmount,
        int installments
    ) {
    }
}
