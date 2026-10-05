package com.sacco.mvp.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Stable schema 1: a controlled operational report, never executable user content. */
public record OperationalReportDefinition(int schemaVersion, Dataset dataset, String title, String footer,
        String language, boolean landscape, List<Column> columns, List<Filter> filters,
        List<Sort> sorts, List<Field> groups, List<Field> totals, boolean institutionLogo) {
    public OperationalReportDefinition(int schemaVersion,Dataset dataset,String title,String footer,String language,boolean landscape,
            List<Column> columns,List<Filter> filters,List<Sort> sorts,List<Field> groups,List<Field> totals){
        this(schemaVersion,dataset,title,footer,language,landscape,columns,filters,sorts,groups,totals,false);
    }
    public enum Dataset { COLLECTIONS, DISBURSEMENTS, LOAN_PORTFOLIO }
    public enum Type { TEXT, DATE, MONEY }
    public enum Operator { EQ, GE, LE }
    public enum Field {
        LOAN_ID(Type.TEXT, false), EFFECTIVE_DATE(Type.DATE, false), RECEIPT(Type.TEXT, false),
        CHANNEL(Type.TEXT, true), KIND(Type.TEXT, true), AMOUNT(Type.MONEY, false),
        PRINCIPAL(Type.MONEY, false), INTEREST(Type.MONEY, false), OUTSTANDING_PRINCIPAL(Type.MONEY, false);
        private final Type type;
        private final boolean groupable;
        Field(Type type, boolean groupable) { this.type = type; this.groupable = groupable; }
        public Type getType() { return type(); }
        public Type type() { return type; }
        public boolean isGroupable() { return groupable; }
        public String getKey() { return key(); }
        public String key() { return "report.field." + name(); }
        public String getDescriptionKey() { return getKey() + ".description"; }
        public boolean isSensitive() { return this == LOAN_ID || this == RECEIPT; }
        public int getPrecision() { return type == Type.MONEY ? 2 : 0; }
        public String getUnit() { return unit(); }
        public String unit() { return type == Type.MONEY ? "TZS" : ""; }
        public List<Operator> getOperators() { return type == Type.TEXT ? List.of(Operator.EQ) : List.of(Operator.values()); }
    }
    public record Column(Field field, String label, int width, boolean visible) { }
    public record Filter(Field field, Operator operator, String value) { }
    public record Sort(Field field, boolean descending) { }
    public record Catalog(Dataset dataset, int version, String source, String grain, String dateBasis,
            String coverage, List<Field> fields, String requiredClaim) { }

    public static List<Field> fields(Dataset dataset) {
        return switch (Objects.requireNonNull(dataset)) {
            case COLLECTIONS -> List.of(Field.LOAN_ID, Field.EFFECTIVE_DATE, Field.RECEIPT, Field.CHANNEL,
                Field.KIND, Field.AMOUNT, Field.PRINCIPAL, Field.INTEREST);
            case DISBURSEMENTS -> List.of(Field.LOAN_ID, Field.EFFECTIVE_DATE, Field.PRINCIPAL);
            case LOAN_PORTFOLIO -> List.of(Field.LOAN_ID, Field.EFFECTIVE_DATE, Field.PRINCIPAL,
                Field.OUTSTANDING_PRINCIPAL);
        };
    }
    public static List<Catalog> catalog() {
        return List.of(
            new Catalog(Dataset.COLLECTIONS, 1, "loan_repayment_transactions + loan_ledgers", "payment or linked reversal",
                "actual payment date; reversals are negative", "verified local ledger receipts only; channel reconciliation unavailable",
                fields(Dataset.COLLECTIONS), "LOAN_REPAYMENTS_VIEW"),
            new Catalog(Dataset.DISBURSEMENTS, 1, "loan_ledgers", "confirmed ordinary loan ledger origin",
                "confirmed disbursement date", "ordinary ledger origins only; untracked and top-up loans excluded",
                fields(Dataset.DISBURSEMENTS), "LOAN_REPORTS_VIEW"),
            new Catalog(Dataset.LOAN_PORTFOLIO, 1, "loan_ledgers + immutable repayment transactions", "one verified loan",
                "disbursed by effective cutoff; principal less signed payments through cutoff",
                "untracked loans excluded and counted separately; fees and interest receivable unavailable",
                fields(Dataset.LOAN_PORTFOLIO), "LOAN_REPORTS_VIEW"));
    }
    public void validate() {
        if (schemaVersion != 1 || dataset == null || !Set.of("en", "sw").contains(language)) invalid();
        safeText(title, 100, false); safeText(footer, 160, true);
        if (columns == null || columns.isEmpty() || columns.size() > 9 || filters == null || filters.size() > 8
            || sorts == null || sorts.size() > 3 || groups == null || groups.size() > 2 || totals == null || totals.size() > 4) invalid();
        Set<Field> allowed = new HashSet<>(fields(dataset));
        Set<Field> selected = new HashSet<>();
        for (Column column : columns) {
            if (column == null || !allowed.contains(column.field()) || !selected.add(column.field())
                || column.width() < 60 || column.width() > 300) invalid();
            safeText(column.label(), 50, true);
        }
        if (columns.stream().noneMatch(Column::visible)) invalid();
        Set<Field> visible = new HashSet<>();
        columns.stream().filter(Column::visible).forEach(c -> visible.add(c.field()));
        for (Filter filter : filters) {
            if (filter == null || !allowed.contains(filter.field()) || filter.operator() == null
                || !filter.field().getOperators().contains(filter.operator())) invalid();
            value(filter);
        }
        for (Field group : groups) if (!allowed.contains(group) || !group.isGroupable() || !visible.contains(group)) invalid();
        if (new HashSet<>(groups).size() != groups.size() || new HashSet<>(totals).size() != totals.size()) invalid();
        for (Field total : totals) if (!allowed.contains(total) || total.getType() != Type.MONEY || !visible.contains(total)) invalid();
        if (!groups.isEmpty() && columns.stream().filter(Column::visible).anyMatch(c -> !groups.contains(c.field()) && !totals.contains(c.field()))) invalid();
        for (Sort sort : sorts) if (sort == null || !visible.contains(sort.field()) || (!groups.isEmpty() && !groups.contains(sort.field()) && !totals.contains(sort.field()))) invalid();
    }
    public static Object value(Filter filter) {
        safeText(filter.value(), 64, false);
        try {
            return switch (filter.field().getType()) {
                case TEXT -> filter.value();
                case DATE -> LocalDate.parse(filter.value());
                case MONEY -> {
                    BigDecimal value = new BigDecimal(filter.value());
                    if (value.scale() > 2 || value.precision() - value.scale() > 16) invalid();
                    yield value;
                }
            };
        } catch (RuntimeException ex) { throw new IllegalArgumentException("report.error.definition"); }
    }
    public static void validateDates(LocalDate from, LocalDate through) {
        if (from == null || through == null || through.isBefore(from) || ChronoUnit.DAYS.between(from, through) > 366) invalid();
    }
    public static void safeText(String text, int max, boolean optional) {
        if (text == null || text.length() > max || (!optional && text.isBlank())
            || text.chars().anyMatch(c -> Character.isISOControl(c) || Character.getType(c)==Character.FORMAT || "<>`".indexOf(c) >= 0)) invalid();
    }
    private static void invalid() { throw new IllegalArgumentException("report.error.definition"); }
    public static OperationalReportDefinition standard(Dataset dataset, String language) {
        List<Column> columns = fields(dataset).stream().map(f -> new Column(f, "", 130, true)).toList();
        String title=switch(dataset){case COLLECTIONS->language.equals("sw")?"Makusanyo na marejesho":"Collections and reversals";case DISBURSEMENTS->language.equals("sw")?"Utoaji wa mikopo uliothibitishwa":"Verified disbursements";case LOAN_PORTFOLIO->language.equals("sw")?"Mikopo iliyothibitishwa":"Verified loan portfolio";};
        return new OperationalReportDefinition(1, dataset, title, "", language,
            dataset == Dataset.COLLECTIONS, columns, List.of(), List.of(new Sort(Field.LOAN_ID, false)), List.of(),
            fields(dataset).stream().filter(f -> f.getType() == Type.MONEY).toList());
    }
}
