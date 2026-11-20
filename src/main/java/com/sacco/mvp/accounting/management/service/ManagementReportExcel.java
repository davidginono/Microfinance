package com.sacco.mvp.accounting.management.service;

import com.sacco.mvp.accounting.management.dto.ManagementReports;
import com.sacco.mvp.accounting.management.dto.ManagementReports.*;
import com.sacco.mvp.accounting.reports.AccountingReportWorkbook;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.ReportExportLimiter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ManagementReportExcel {
    private final MessageSource messages;
    private final ReportExportLimiter limiter;
    private final ApplicationClock clock;

    public static boolean supports(Kind kind) {
        return ManagementReports.spreadsheetSupported(kind);
    }

    public byte[] build(Result report, Locale locale) {
        if (!supports(report.kind())) throw new IllegalArgumentException("management.error.parameters");
        if (report.rows().size() != report.count() || report.count() > 5000 || report.groups().size() > 1000)
            throw new IllegalArgumentException("management.error.large");
        return limiter.run(() -> render(report, locale));
    }

    private byte[] render(Result result, Locale locale) {
        boolean repayment = result.kind() == Kind.LOAN_REPAYMENT_TRANSACTIONS;
        String family = repayment ? "Arial" : "Courier New";
        try (var book = new AccountingReportWorkbook(family, result.isReferenceLayout() && !repayment ? "Times New Roman" : family)) {
            book.headings(repayment ? "Arial" : "Times New Roman", repayment);
            var report = reportSheet(book, result, locale);
            var data = dataSheet(book, result, locale);
            if (book.hasExactText()) {
                book.line(report, label("exactAmounts", locale), reportColumns(result), false);
                var dataHeader = data.getRow(data.getPaneInformation().getHorizontalSplitPosition() - 1);
                book.line(data, label("exactAmounts", locale), dataHeader.getLastCellNum(), false);
            }
            return book.bytes();
        }
    }

    private Sheet reportSheet(AccountingReportWorkbook book, Result result, Locale locale) {
        Kind kind = result.kind();
        boolean audit = kind == Kind.DISBURSED_LOANS_AUDIT;
        boolean repayment = kind == Kind.LOAN_REPAYMENT_TRANSACTIONS;
        int columns = reportColumns(result);
        short paper = repayment ? PrintSetup.LETTER_PAPERSIZE
            : result.isReferenceLayout() ? PrintSetup.LEGAL_PAPERSIZE : PrintSetup.A3_PAPERSIZE;
        var sheet = book.sheet(label("sheet.report", locale), columns, !repayment, paper);
        reportWidths(sheet, result);
        reportContext(book, sheet, result, locale);
        int contextEnd = sheet.getLastRowNum() + 1;
        var headings = new ArrayList<String>();
        if (audit) headings.add(label("pdf.recordNumber", locale));
        var documentColumns = result.getDocumentColumns();
        for (var column : documentColumns)
            headings.add(LoanReportReferenceText.documentHeading(kind, column.key(), key -> label(key, locale)));
        Set<Integer> amounts = monetaryColumns(documentColumns, audit ? 1 : 0);
        var grouped = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (var row : result.rows())
            grouped.computeIfAbsent(Objects.toString(row.get("groupName"), ""), ignored -> new ArrayList<>()).add(row);

        var monthlyTotals = audit ? result.getMonthlyTotals() : Map.<String, Map<String, BigDecimal>>of();
        var subtotals=result.getSubtotalLayout();
        String month = null;
        int record = 0;
        for (var group : result.groups()) {
            String nextMonth = audit ? group.name().substring(0, Math.min(7, group.name().length())) : null;
            if (audit && month != null && !month.equals(nextMonth) && subtotals.periods().contains(month))
                total(book, sheet, result, monthlyTotals.get(month), label("monthTotal", locale), amounts);
            book.space(sheet);
            var rows = grouped.getOrDefault(group.name(), List.of());
            groupFacts(book, sheet, result, group.name(), rows, locale);
            book.headers(sheet, headings, repayment ? columns - 1 : -1);
            for (var row : rows) {
                var cells = new ArrayList<Object>();
                if (audit) cells.add(++record);
                for (var column : documentColumns)
                    cells.add(repayment && column.key().equals("date") ? repaymentDate(row, locale)
                        : result.isReferenceLayout() && row.get(column.key()) == null ? "—"
                        : value(row.get(column.key()), column.key(), locale));
                book.row(sheet, cells, amounts, false);
            }
            if(subtotals.groups().contains(group.name()))total(book, sheet, result, group.totals(), label("groupTotal", locale), amounts);
            month = nextMonth;
        }
        if (audit && month != null && subtotals.periods().contains(month))
            total(book, sheet, result, monthlyTotals.get(month), label("monthTotal", locale), amounts);
        if (result.groups().isEmpty()) {
            if (result.isReferenceLayout()) {
                book.space(sheet);
                groupFacts(book, sheet, result, "", List.of(), locale);
            }
            book.headers(sheet, headings, repayment ? columns - 1 : -1);
            book.line(sheet, label("empty", locale), columns, false);
        }
        if (result.count()>0)
            total(book, sheet, result, result.totals(), label("total", locale), amounts);
        sheet.createFreezePane(0, contextEnd);
        reportNotes(book, sheet, result, locale);
        return sheet;
    }

    private void reportWidths(Sheet sheet, Result result) {
        int[] widths = switch (result.kind()) {
            case DISBURSED_LOANS_AUDIT -> new int[]{7, 15, 25, 13, 20, 21, 20, 21, 20, 17, 17, 19, 12, 11};
            case EXPECTED_REPAYMENTS -> new int[]{13, 14, 30, 20, 25, 21, 20, 19, 21};
            case LOAN_REPAYMENT_TRANSACTIONS -> new int[]{27, 20, 21, 24, 21, 23, 20};
            default -> result.columns().stream()
                .mapToInt(column -> (int) Math.max(16, Math.min(40, column.width() * 20))).toArray();
        };
        for (int column = 0; column < widths.length; column++) sheet.setColumnWidth(column, widths[column] * 256);
    }

    private void reportContext(AccountingReportWorkbook book, Sheet sheet, Result result, Locale locale) {
        int columns = reportColumns(result);
        if (!result.isReferenceLayout()) {
            fullContext(book, sheet, result, columns, locale);
            return;
        }
        Kind kind = result.kind();
        int titleColumns = kind == Kind.DISBURSED_LOANS_AUDIT ? 6 : 4;
        var title = title(book, sheet, result, titleColumns, locale);
        String printed = LoanReportReferenceText.printed(kind, clock.now(), locale, key -> label(key, locale));
        switch (kind) {
            case EXPECTED_REPAYMENTS, DISBURSED_LOANS_AUDIT -> {
                var range = sheet.createRow(sheet.getLastRowNum() + 1);
                String from = LoanReportReferenceText.date(result.parameters().from(), locale);
                String through = LoanReportReferenceText.date(result.parameters().through(), locale);
                String and = label("pdf.and", locale);
                if (kind == Kind.DISBURSED_LOANS_AUDIT) {
                    book.caption(range, 1, label("pdf.entryDateRange", locale), false);
                    book.caption(range, 2, from, false);
                    book.caption(range, 3, and, false);
                    book.caption(range, 4, through, false);
                    book.caption(range, 5, printed, false);
                } else {
                    book.caption(title, 6, printed, false);
                    book.caption(range, 0, scope(result), false);
                    book.caption(range, 4, label("pdf.dueDateRange", locale), false);
                    book.caption(range, 5, from, false);
                    book.caption(range, 6, and.toUpperCase(locale), false);
                    book.caption(range, 7, through, false);
                }
            }
            case LOAN_REPAYMENT_TRANSACTIONS -> book.caption(title, titleColumns, printed, false);
            default -> throw new IllegalArgumentException("Reference layout required");
        }
        for (String detail : LoanReportReferenceText.headerDetails(result, locale, key -> label(key, locale)))
            book.line(sheet, detail, columns, false);
        if (result.excluded() > 0)
            book.line(sheet, label("excluded", locale) + ": " + result.excluded(), columns, false);
        String footer = label(kind == Kind.LOAN_REPAYMENT_TRANSACTIONS ? "pdf.page" : "pdf.pageOf", locale);
        sheet.getFooter().setCenter(MessageFormat.format(footer, "&P", "&N"));
    }

    private void groupFacts(AccountingReportWorkbook book, Sheet sheet, Result result, String group,
                            List<Map<String, Object>> rows, Locale locale) {
        if (!result.isReferenceLayout()) {
            book.line(sheet, group, reportColumns(result), true);
            return;
        }
        var first = rows.isEmpty() ? result.emptyGroupFacts() : rows.getFirst();
        switch (result.kind()) {
            case DISBURSED_LOANS_AUDIT -> {
                book.facts(sheet, Arrays.asList(null, label("month", locale), first.get("month"), null,
                    label("productSearch", locale), first.get("product")), Set.of(), Set.of(1, 4));
                book.emphasizeFact(sheet, 2);
                book.space(sheet);
                book.facts(sheet, Arrays.asList(null, label("pdf.institutionName", locale), result.institution()),
                    Set.of(), Set.of(1));
                book.space(sheet);
                // The posting stores a code/name label. Older name-only snapshots stay name-only.
                String[] account = Objects.toString(first.get("disbursementAccount"), "").split(" - ", 2);
                book.facts(sheet, Arrays.asList(null, label("pdf.branchName", locale), result.branch(),
                    null, null, label("field.disbursementAccount", locale),
                    account.length == 2 ? account[0] : null, account[account.length - 1]), Set.of(), Set.of(1, 5));
            }
            case EXPECTED_REPAYMENTS -> book.facts(sheet, Arrays.asList(label("pdf.loanTypeName", locale).toUpperCase(locale),
                null, first.getOrDefault("groupName", first.get("product"))), Set.of(), Set.of(0));
            case LOAN_REPAYMENT_TRANSACTIONS -> {
                book.facts(sheet, Arrays.asList(field("clientNumber", locale).toUpperCase(locale), first.get("clientNumber"),
                    label("sheet.borrowerName", locale).toUpperCase(locale), first.get("name"), null,
                    field("startDate", locale).toUpperCase(locale), value(first.get("startDate"), "startDate", locale)),
                    Set.of(), Set.of(0, 2, 5));
                book.space(sheet);
                book.facts(sheet, Arrays.asList(field("loanId", locale).toUpperCase(locale), label("pdf.loanType", locale).toUpperCase(locale),
                    null, label("pdf.disbursedLoanAmount", locale).toUpperCase(locale), null, field("principalBalance", locale).toUpperCase(locale)),
                    Set.of(), Set.of(0, 1, 3, 5));
                book.facts(sheet, Arrays.asList(first.get("loanId"), first.get("product"), null, first.get("disbursed"),
                    null, first.get("principalBalance")), Set.of(3, 5), Set.of());
            }
            default -> throw new IllegalArgumentException("Reference layout required");
        }
    }

    private void reportNotes(AccountingReportWorkbook book, Sheet sheet, Result result, Locale locale) {
        String noteKey = result.getNoteKey();
        if (noteKey.isEmpty()) return;
        book.space(sheet);
        if (noteKey.equals("unknownAmounts")) noteKey = "sheet.unknown";
        book.line(sheet, label(noteKey, locale), reportColumns(result), false);
    }

    /** The flat sheet keeps native dates, numbers and identifiers for filtering. */
    private Sheet dataSheet(AccountingReportWorkbook book, Result result, Locale locale) {
        var keys = new ArrayList<>(result.columns().stream().map(Column::key).toList());
        var extra = new ArrayList<>(List.of("groupName", "product"));
        if (result.kind() == Kind.LOAN_REPAYMENT_TRANSACTIONS)
            extra.addAll(List.of("clientNumber", "name", "loanId", "startDate", "disbursed", "principalBalance",
                "reference", "entryKind", "reversesReference"));
        if (result.kind() == Kind.DISBURSED_LOANS_AUDIT) extra.addAll(List.of("month", "disbursementAccount"));
        for (var key : extra) if (!keys.contains(key)) keys.add(key);

        var data = book.sheet(label("sheet.data", locale), keys.size(), true, PrintSetup.A3_PAPERSIZE);
        fullContext(book, data, result, keys.size(), locale);
        book.line(data, label("sheet.unknown", locale), keys.size(), false);
        int header = book.headers(data, keys.stream().map(key -> field(key, locale)).toList());
        var amounts = monetaryColumns(result.columns(), 0);
        for (int column = result.columns().size(); column < keys.size(); column++)
            if (Set.of("disbursed", "principalBalance").contains(keys.get(column))) amounts.add(column);
        for (var row : result.rows())
            book.row(data, keys.stream().map(key -> value(row.get(key), key, locale)).toList(), amounts, false);
        book.filter(data, header, keys.size());
        return data;
    }

    private void fullContext(AccountingReportWorkbook book, Sheet sheet, Result result, int columns, Locale locale) {
        title(book, sheet, result, columns, locale);
        book.line(sheet, scope(result), columns, false);
        book.line(sheet, label("period", locale) + ": " + result.parameters().from() + " — " + result.parameters().through(), columns, false);
        reportSettings(book, sheet, result, columns, locale);
    }

    private Row title(AccountingReportWorkbook book, Sheet sheet, Result result, int columns, Locale locale) {
        Kind kind = result.kind();
        boolean repayment = kind == Kind.LOAN_REPAYMENT_TRANSACTIONS;
        return book.title(sheet, label("title." + kind, locale), columns, repayment ? "Times New Roman" : "Arial",
            kind == Kind.DISBURSED_LOANS_AUDIT ? 11 : repayment ? 12 : 14, kind != Kind.EXPECTED_REPAYMENTS,
            kind == Kind.DISBURSED_LOANS_AUDIT || repayment);
    }

    private void reportSettings(AccountingReportWorkbook book, Sheet sheet, Result result, int columns, Locale locale) {
        book.line(sheet, label("cutoff", locale) + ": " + result.parameters().cutoff(), columns, false);
        book.line(sheet, label("excluded", locale) + ": " + result.excluded(), columns, false);
        if (!result.clientSelection().isBlank())
            book.line(sheet, label("client", locale) + ": " + result.clientSelection(), columns, false);
        if (!result.parameters().product().isBlank())
            book.line(sheet, label("productSearch", locale) + ": " + result.parameters().product(), columns, false);
    }

    private String repaymentDate(Map<String, Object> row, Locale locale) {
        Object date = value(row.get("date"), "date", locale);
        var text = new StringBuilder(date instanceof LocalDate day ? LoanReportReferenceText.date(day, locale) : Objects.toString(date, "—"));
        if (row.get("entryKind") != null)
            text.append('\n').append(value(row.get("entryKind"), "entryKind", locale)).append(' ').append(Objects.toString(row.get("reference"), "—"));
        if (row.get("reversesReference") != null)
            text.append('\n').append(field("reversesReference", locale)).append(": ").append(row.get("reversesReference"));
        return text.toString();
    }

    private String scope(Result result) {
        return result.institution() + " | " + result.branch() + " | TZS";
    }

    private int reportColumns(Result result) {
        return result.getDocumentColumns().size() + (result.kind() == Kind.DISBURSED_LOANS_AUDIT ? 1 : 0);
    }

    private Set<Integer> monetaryColumns(List<Column> columns, int offset) {
        var amounts = new HashSet<Integer>();
        for (int column = 0; column < columns.size(); column++)
            if (columns.get(column).money()) amounts.add(column + offset);
        return amounts;
    }

    private String label(String key, Locale locale) {
        return messages.getMessage("management." + key, null, locale);
    }

    private String field(String key, Locale locale) {
        return label(switch (key) {
            case "product" -> "productSearch";
            case "month", "startDate" -> key;
            case "groupName" -> "sheet.group";
            case "entryKind" -> "sheet.entry";
            default -> "field." + key;
        }, locale);
    }

    private Object value(Object value, String key, Locale locale) {
        if (value instanceof java.sql.Date day) return day.toLocalDate();
        if (value == null) return null;
        if (key.equals("frequency") && Set.of("MONTHLY", "WEEKLY").contains(value.toString()))
            return messages.getMessage("recording.frequency." + value, null, locale);
        if (key.equals("entryKind")) return label("entry." + value, locale);
        return value;
    }

    private void total(AccountingReportWorkbook book, Sheet sheet, Result result,
                       Map<String, BigDecimal> totals, String label, Set<Integer> amounts) {
        var cells = new ArrayList<Object>();
        cells.add(label);
        int first = result.kind() == Kind.DISBURSED_LOANS_AUDIT ? 0 : 1;
        var totalFields = result.getTotalFields();
        var columns=result.getDocumentColumns();
        for (int column = first; column < columns.size(); column++) {
            String key = columns.get(column).key();
            cells.add(totalFields.contains(key) ? totals.get(key) : null);
        }
        book.row(sheet, cells, amounts, true);
    }
}
