package com.sacco.mvp.accounting.reconciliation.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Discrepancy projections only. Null money means unavailable, never an assumed zero opening. */
public final class ReconciliationReport {
    private ReconciliationReport() {}
    public record Parameters(LocalDate from, LocalDate through, OffsetDateTime cutoff, String component, int page) {}
    public record Row(String component, String section, String client, String loan, String account,
            String reference, LocalDate date, String activity, String issue, BigDecimal slDebit, BigDecimal slCredit,
            BigDecimal glDebit, BigDecimal glCredit, BigDecimal debitVariance, BigDecimal creditVariance, String identity) {
        public String group() { return component + "/" + section; }
        public String getGroup() { return group(); }
        public List<BigDecimal> amounts() { return Arrays.asList(slDebit,slCredit,glDebit,glCredit,debitVariance,creditVariance); }
        public List<BigDecimal> getAmounts() { return amounts(); }
    }
    public record Group(String component, String section, long count, List<BigDecimal> totals) {
        public String key() { return component + "/" + section; }
        public String getKey() { return key(); }
    }
    public record Result(Parameters parameters, String institution, String branch, List<Row> rows,
            List<Group> groups, long count, boolean hasNext) {}
}
