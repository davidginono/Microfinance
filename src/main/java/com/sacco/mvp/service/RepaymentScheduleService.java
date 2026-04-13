package com.sacco.mvp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.RepaymentFrequency;
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
        BigDecimal baseInstallment = installmentOverride != null && installmentOverride.compareTo(BigDecimal.ZERO) > 0
            ? installmentOverride.setScale(2, RoundingMode.HALF_UP)
            : app.getAmount().divide(BigDecimal.valueOf(installments), 2, RoundingMode.HALF_UP);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal runningTotal = BigDecimal.ZERO;
        LocalDate dueDate = firstRepaymentDate;
        for (int index = 1; index <= installments; index++) {
            BigDecimal installmentAmount = index == installments
                ? app.getAmount().subtract(runningTotal).setScale(2, RoundingMode.HALF_UP)
                : baseInstallment;
            runningTotal = runningTotal.add(installmentAmount);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("installmentNumber", index);
            row.put("dueDate", dueDate.toString());
            row.put("amount", installmentAmount);
            row.put("status", "UPCOMING");
            rows.add(row);
            dueDate = frequency == RepaymentFrequency.WEEKLY ? dueDate.plusWeeks(1) : dueDate.plusMonths(1);
        }

        LocalDate finalDueDate = rows.isEmpty()
            ? firstRepaymentDate
            : LocalDate.parse(String.valueOf(rows.get(rows.size() - 1).get("dueDate")));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("disbursementDate", disbursementDate.toString());
        summary.put("firstRepaymentDate", firstRepaymentDate.toString());
        summary.put("finalDueDate", finalDueDate.toString());
        summary.put("repaymentFrequency", frequency.name());
        summary.put("installmentAmount", baseInstallment);
        summary.put("installments", installments);
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

    public record ScheduleResult(
        String scheduleJson,
        LocalDate finalDueDate,
        BigDecimal installmentAmount,
        int installments
    ) {
    }
}
