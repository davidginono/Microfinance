package com.sacco.mvp.reporting.operational.dto;

import java.time.LocalDate;
import java.util.List;

/** Closed, versioned report language. It deliberately contains no expressions or query fragments. */
public record ReportDefinition(int schemaVersion, int metricVersion, Dataset dataset, List<Column> columns,
        LocalDate dateFrom, LocalDate dateTo, String loanId, String channel, String status,
        List<Field> groupBy, List<Field> totals, Field sortBy, Direction direction,
        String title, String footer, Language language, Orientation orientation,
        Paper paper, boolean showInstitutionBranding) {
    public enum Dataset { COLLECTIONS, DISBURSEMENTS, LOAN_PORTFOLIO }
    public enum Field { LOAN_ID, PAYMENT_DATE, DISBURSEMENT_DATE, RECEIPT, CHANNEL, KIND,
        PRINCIPAL, INTEREST, AMOUNT, OUTSTANDING_PRINCIPAL, DUE_INTEREST, STATUS, COVERAGE }
    public enum Direction { ASC, DESC }
    public enum Language { EN, SW }
    public enum Orientation { PORTRAIT, LANDSCAPE }
    public enum Paper { A4, LETTER }
    public enum Format { TEXT, DATE, MONEY }
    public record Column(Field field, String label, int width, Format format) { }
}
