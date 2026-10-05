package com.sacco.mvp.reporting.operational.service;

import com.sacco.mvp.reporting.operational.dto.ReportDefinition;
import com.sacco.mvp.reporting.operational.dto.ReportDefinition.*;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;

@Service
public class OperationalReportCatalog {
    public record FieldSpec(Field id, String messageKey, Format format, boolean groupable,
                            boolean summable, String lineage, String nullTreatment,
                            String sensitivity, boolean sortable, List<String> filterOperators,
                            String unit, int decimalPrecision, List<String> aggregations, int version) { }
    public record DatasetSpec(Dataset id, String messageKey, String source, String grain,
                              String dateSemantics, String requiredClaim, int metricVersion,
                              List<FieldSpec> fields) { }
    public DatasetSpec dataset(Dataset id) {
        return switch (id) {
            case COLLECTIONS -> new DatasetSpec(id, "opreport.dataset.collections", "loan_repayment_transactions; loan_ledgers",
                "Posted payment or linked reversal", "Effective payment date; reversal is negative", "LOAN_REPAYMENTS_VIEW", 1,
                fields(Field.LOAN_ID, Field.PAYMENT_DATE, Field.RECEIPT, Field.CHANNEL, Field.KIND, Field.PRINCIPAL, Field.INTEREST, Field.AMOUNT));
            case DISBURSEMENTS -> new DatasetSpec(id, "opreport.dataset.disbursements", "loan_ledgers; loan_applications",
                "Verified local-ledger opening per loan", "Contract disbursement date", "LOAN_REPORTS_VIEW", 1,
                fields(Field.LOAN_ID, Field.DISBURSEMENT_DATE, Field.PRINCIPAL));
            case LOAN_PORTFOLIO -> new DatasetSpec(id, "opreport.dataset.portfolio", "loan_applications; loan_ledgers; loan_repayment_transactions; loan_ledger_installments",
                "Loan; payment aggregates are reduced to one row before joining", "All loans disbursed by end date; balances as of end date; current workflow status", "LOAN_REPORTS_VIEW", 1,
                fields(Field.LOAN_ID, Field.DISBURSEMENT_DATE, Field.STATUS, Field.COVERAGE, Field.PRINCIPAL, Field.OUTSTANDING_PRINCIPAL, Field.DUE_INTEREST));
        };
    }
    public FieldSpec field(Dataset dataset, Field field) {
        return dataset(dataset).fields().stream().filter(spec -> spec.id() == field).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("opreport.error.definition"));
    }
    private List<FieldSpec> fields(Field... fields) {
        return Arrays.stream(fields).map(field -> new FieldSpec(field, "opreport.field." + field.name(),
            switch (field) { case PAYMENT_DATE, DISBURSEMENT_DATE -> Format.DATE;
                case PRINCIPAL, INTEREST, AMOUNT, OUTSTANDING_PRINCIPAL, DUE_INTEREST -> Format.MONEY;
                default -> Format.TEXT; },
            Set.of(Field.CHANNEL, Field.KIND, Field.STATUS, Field.COVERAGE).contains(field),
            Set.of(Field.PRINCIPAL, Field.INTEREST, Field.AMOUNT, Field.OUTSTANDING_PRINCIPAL, Field.DUE_INTEREST).contains(field),
            switch(field) {
                case LOAN_ID -> "Client-facing loan number; no internal UUID or direct client identity/contact fields";
                case PRINCIPAL -> "Signed principal collected for payment rows; verified opening principal for loan rows";
                case INTEREST -> "Signed posted interest collections; future scheduled interest is excluded";
                case AMOUNT -> "Signed posted principal plus interest collections; a reversal is negative";
                case OUTSTANDING_PRINCIPAL -> "Verified opening principal less signed posted principal through the as-of date and recorded cutoff";
                case DUE_INTEREST -> "Original contractual interest due through the as-of date less signed posted interest; excludes future interest and is not a settlement quote";
                case PAYMENT_DATE -> "Actual effective payment date, distinct from recording timestamp";
                case DISBURSEMENT_DATE -> "Contractual disbursement date";
                case STATUS -> "Current workflow status, not historical accounting status";
                case COVERAGE -> "Presence of a verified local-ledger opening";
                case RECEIPT -> "Immutable posted repayment receipt reference";
                case CHANNEL -> "Approved repayment channel";
                case KIND -> "Posted payment or linked reversal";
            },
            "Missing ledger values stay null; totals disclose missing rows", "FINANCIAL_RECORD", true,
            switch(field) { case LOAN_ID,CHANNEL,STATUS -> List.of("EQUALS"); case PAYMENT_DATE,DISBURSEMENT_DATE -> List.of("DATE_RANGE"); default -> List.of(); },
            Set.of(Field.PRINCIPAL,Field.INTEREST,Field.AMOUNT,Field.OUTSTANDING_PRINCIPAL,Field.DUE_INTEREST).contains(field) ? "TZS" : "",
            Set.of(Field.PRINCIPAL,Field.INTEREST,Field.AMOUNT,Field.OUTSTANDING_PRINCIPAL,Field.DUE_INTEREST).contains(field) ? 2 : 0,
            Set.of(Field.PRINCIPAL,Field.INTEREST,Field.AMOUNT,Field.OUTSTANDING_PRINCIPAL,Field.DUE_INTEREST).contains(field) ? List.of("SUM","AVAILABLE_COUNT","UNAVAILABLE_COUNT") : List.of("COUNT"),1)).toList();
    }
    public ReportDefinition system(Dataset dataset, LocalDate today) {
        var spec = dataset(dataset);
        return new ReportDefinition(1, spec.metricVersion(), dataset, spec.fields().stream().map(f -> new Column(f.id(), "", 140, f.format())).toList(),
            dataset == Dataset.LOAN_PORTFOLIO ? today : today.minusDays(29), today, "", "", "", List.of(), spec.fields().stream().filter(FieldSpec::summable).map(FieldSpec::id).toList(),
            dataset == Dataset.COLLECTIONS ? Field.PAYMENT_DATE : Field.DISBURSEMENT_DATE, Direction.DESC,
            "", "", Language.EN, Orientation.LANDSCAPE, Paper.A4, true);
    }
}
