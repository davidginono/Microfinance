package com.sacco.mvp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.InterestMethod;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
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
    private static final BigDecimal CHAPCHAP_EXTENDED_INTEREST_RATE = new BigDecimal("0.1200");

    private final ObjectMapper objectMapper;
    private final LoanProductSettingRepository loanProductSettingRepository;

    public ScheduleResult buildSchedule(LoanApplication app,
                                        LocalDate disbursementDate,
                                        LocalDate firstRepaymentDate,
                                        RepaymentFrequency frequency,
                                        BigDecimal installmentOverride,
                                        String disbursementReference,
                                        String disbursementNotes) {
        int installments = frequency == RepaymentFrequency.WEEKLY
            ? Math.max(app.getTenorMonths() * 4, 1)
            : Math.max(app.getTenorMonths(), 1);
        LoanProductSetting product = loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue(app.getSaccoId(), app.getLoanType())
            .orElse(null);
        BigDecimal configuredRate = resolveAnnualInterestRate(app, product);
        InterestMethod interestMethod = resolveInterestMethod(app, product);
        BigDecimal baseInstallment = installmentOverride != null && installmentOverride.compareTo(BigDecimal.ZERO) > 0
            ? installmentOverride.setScale(2, RoundingMode.HALF_UP)
            : defaultInstallment(app.getAmount(), installments, configuredRate, interestMethod, frequency, app.getTenorMonths());

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal runningTotal = BigDecimal.ZERO;
        BigDecimal runningPrincipal = BigDecimal.ZERO;
        BigDecimal remainingPrincipal = app.getAmount().setScale(2, RoundingMode.HALF_UP);
        LocalDate dueDate = firstRepaymentDate;
        BigDecimal periodicRate = periodicRate(configuredRate, frequency);
        BigDecimal totalFlatInterest = totalFlatInterest(app.getAmount(), configuredRate, app.getTenorMonths());
        BigDecimal runningInterest = BigDecimal.ZERO;
        for (int index = 1; index <= installments; index++) {
            BigDecimal installmentAmount;
            BigDecimal principalComponent;
            BigDecimal interestComponent;

            if (interestMethod == InterestMethod.REDUCING_BALANCE) {
                interestComponent = remainingPrincipal.multiply(periodicRate).setScale(2, RoundingMode.HALF_UP);
                principalComponent = baseInstallment.subtract(interestComponent).setScale(2, RoundingMode.HALF_UP);
                if (principalComponent.compareTo(BigDecimal.ZERO) < 0) {
                    principalComponent = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }
                if (index == installments) {
                    principalComponent = app.getAmount().subtract(runningPrincipal).setScale(2, RoundingMode.HALF_UP);
                    installmentAmount = principalComponent.add(interestComponent).setScale(2, RoundingMode.HALF_UP);
                } else {
                    installmentAmount = baseInstallment;
                }
                remainingPrincipal = remainingPrincipal.subtract(principalComponent).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                runningPrincipal = runningPrincipal.add(principalComponent).setScale(2, RoundingMode.HALF_UP);
            } else {
                BigDecimal principalBase = app.getAmount().divide(BigDecimal.valueOf(installments), 2, RoundingMode.HALF_UP);
                BigDecimal interestBase = totalFlatInterest.divide(BigDecimal.valueOf(installments), 2, RoundingMode.HALF_UP);
                principalComponent = index == installments
                    ? app.getAmount().subtract(runningPrincipal).setScale(2, RoundingMode.HALF_UP)
                    : principalBase;
                interestComponent = index == installments
                    ? totalFlatInterest.subtract(runningInterest).setScale(2, RoundingMode.HALF_UP)
                    : interestBase;
                installmentAmount = installmentOverride != null && installmentOverride.compareTo(BigDecimal.ZERO) > 0
                    ? (index == installments
                        ? app.getAmount().add(totalFlatInterest).subtract(runningTotal).setScale(2, RoundingMode.HALF_UP)
                        : baseInstallment)
                    : principalComponent.add(interestComponent).setScale(2, RoundingMode.HALF_UP);
                if (installmentOverride != null && installmentOverride.compareTo(BigDecimal.ZERO) > 0) {
                    interestComponent = installmentAmount.subtract(principalComponent).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                }
                runningPrincipal = runningPrincipal.add(principalComponent).setScale(2, RoundingMode.HALF_UP);
                runningInterest = runningInterest.add(interestComponent).setScale(2, RoundingMode.HALF_UP);
                remainingPrincipal = app.getAmount().subtract(runningPrincipal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            }

            runningTotal = runningTotal.add(installmentAmount);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("installmentNumber", index);
            row.put("dueDate", dueDate.toString());
            row.put("amount", installmentAmount);
            row.put("principalComponent", principalComponent);
            row.put("interestComponent", interestComponent);
            row.put("outstandingBalance", remainingPrincipal);
            row.put("status", "UPCOMING");
            rows.add(row);
            dueDate = frequency == RepaymentFrequency.WEEKLY ? dueDate.plusWeeks(1) : dueDate.plusMonths(1);
        }

        LocalDate finalDueDate = rows.isEmpty()
            ? firstRepaymentDate
            : LocalDate.parse(String.valueOf(rows.get(rows.size() - 1).get("dueDate")));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("disbursedPrincipal", app.getAmount());
        summary.put("disbursementDate", disbursementDate.toString());
        summary.put("firstRepaymentDate", firstRepaymentDate.toString());
        summary.put("finalDueDate", finalDueDate.toString());
        summary.put("repaymentFrequency", frequency.name());
        summary.put("installmentAmount", baseInstallment);
        summary.put("installments", installments);
        summary.put("interestMethod", interestMethod.name());
        summary.put("interestRate", configuredRate);
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

    private BigDecimal defaultInstallment(BigDecimal principal,
                                          int installments,
                                          BigDecimal annualRate,
                                          InterestMethod interestMethod,
                                          RepaymentFrequency frequency,
                                          int tenorMonths) {
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (interestMethod == InterestMethod.REDUCING_BALANCE) {
            BigDecimal ratePerPeriod = periodicRate(annualRate, frequency);
            if (ratePerPeriod.compareTo(BigDecimal.ZERO) <= 0) {
                return principal.divide(BigDecimal.valueOf(installments), 2, RoundingMode.HALF_UP);
            }
            double rateDouble = ratePerPeriod.doubleValue();
            double factor = 1d - Math.pow(1d + rateDouble, -installments);
            return BigDecimal.valueOf(principal.doubleValue() * rateDouble / factor)
                .setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal totalDue = principal.add(totalFlatInterest(principal, annualRate, tenorMonths));
        return totalDue.divide(BigDecimal.valueOf(installments), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal totalFlatInterest(BigDecimal principal, BigDecimal annualRate, int tenorMonths) {
        if (principal == null || annualRate == null || annualRate.compareTo(BigDecimal.ZERO) <= 0 || tenorMonths <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return principal
            .multiply(annualRate)
            .multiply(BigDecimal.valueOf(tenorMonths))
            .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal periodicRate(BigDecimal annualRate, RepaymentFrequency frequency) {
        if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(12, RoundingMode.HALF_UP);
        }
        return switch (frequency) {
            case WEEKLY -> annualRate.divide(BigDecimal.valueOf(52), 12, RoundingMode.HALF_UP);
            case MONTHLY -> annualRate.divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);
        };
    }

    private BigDecimal resolveAnnualInterestRate(LoanApplication app, LoanProductSetting product) {
        LoanType loanType = app.getLoanType();
        int tenorMonths = app.getTenorMonths();
        if (loanType == LoanType.LOAN_ADVANCE) {
            return tenorMonths <= 1 ? BigDecimal.ZERO : CHAPCHAP_EXTENDED_INTEREST_RATE;
        }
        Map<String, Object> snapshot = parseSummary(app.getFinancialSnapshot());
        Object snapshotRate = snapshot.get("interestRate");
        if (snapshotRate instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(4, RoundingMode.HALF_UP);
        }
        if (snapshotRate != null) {
            try {
                return new BigDecimal(String.valueOf(snapshotRate)).setScale(4, RoundingMode.HALF_UP);
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
