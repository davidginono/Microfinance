package com.sacco.mvp.accounting.management.service;

import com.sacco.mvp.accounting.management.dto.ManagementReports.Kind;
import com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters;
import com.sacco.mvp.accounting.management.dto.ManagementReports.Result;
import com.sacco.mvp.accounting.reports.AccountingPdfDocument;
import com.sacco.mvp.service.ApplicationTimestamps;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** Captions and column positions shared by the reference reports. */
public final class LoanReportReferenceText {
    private LoanReportReferenceText() {}

    static String date(LocalDate day, Locale locale) {
        return day.format(DateTimeFormatter.ofPattern("dd-MMM-uuuu", locale)).toUpperCase(locale);
    }

    static String period(Parameters parameters, Locale locale, Function<String, String> label) {
        return label.apply("pdf.dateRange") + ": " + date(parameters.from(), locale)
            + "    " + label.apply("pdf.and") + "    " + date(parameters.through(), locale);
    }
    public static String referenceDate(Object value, Locale locale) {
        if (value instanceof java.sql.Date sqlDate) value = sqlDate.toLocalDate();
        return value instanceof LocalDate date ? date(date, locale) : ApplicationTimestamps.display(value);
    }

    public static AccountingPdfDocument.Style style(Kind kind) {
        return switch (kind) {
            case DISBURSED_LOANS_AUDIT -> AccountingPdfDocument.Style.AUDIT;
            case EXPECTED_REPAYMENTS -> AccountingPdfDocument.Style.EXPECTED;
            case LOAN_REPAYMENT_TRANSACTIONS -> AccountingPdfDocument.Style.REPAYMENT;
            default -> AccountingPdfDocument.Style.REFERENCE;
        };
    }

    public static String documentPeriod(Kind kind, Parameters parameters, Locale locale, Function<String, String> label) {
        if (kind == Kind.EXPECTED_REPAYMENTS) {
            return label.apply("pdf.dueDateRange") + "  " + date(parameters.from(), locale)
                + "         " + label.apply("pdf.and").toUpperCase(locale) + "  " + date(parameters.through(), locale);
        }
        if (kind == Kind.DISBURSED_LOANS_AUDIT) {
            return label.apply("pdf.entryDateRange") + "   " + date(parameters.from(), locale).replace('-', ' ')
                + "      " + label.apply("pdf.and") + "   " + date(parameters.through(), locale).replace('-', ' ');
        }
        return period(parameters, locale, label);
    }

    public static List<String> headerDetails(Result report, Locale locale, Function<String, String> label) {
        var details = new ArrayList<String>();
        if (report.kind() == Kind.LOAN_REPAYMENT_TRANSACTIONS) {
            details.add(report.institution() + " | " + report.branch() + " | TZS");
            details.add(documentPeriod(report.kind(), report.parameters(), locale, label));
        }
        details.add(label.apply("cutoff") + ": " + ApplicationTimestamps.display(report.parameters().cutoff()));
        if (report.kind() != Kind.LOAN_REPAYMENT_TRANSACTIONS && !report.clientSelection().isBlank())
            details.add(label.apply("client") + ": " + report.clientSelection());
        return details;
    }

    public static String documentHeading(Kind kind, String key, Function<String, String> label) {
        if (kind == Kind.DISBURSED_LOANS_AUDIT || kind == Kind.EXPECTED_REPAYMENTS) {
            String caption=switch(key) {
                case "netDisbursed", "loanRate" -> key;
                case "disbursed" -> "loanAmount";
                case "totalInterest" -> "totalInterest";
                case "instalment" -> "totalInstallment";
                case "disbursementDate" -> "disbursedDate";
                case "maturity" -> "expiryDate";
                case "expectedTotal" -> "totalDue";
                default -> "";
            };
            if(!caption.isEmpty())return label.apply("pdf."+caption);
        }
        if (kind == Kind.LOAN_REPAYMENT_TRANSACTIONS) {
            return switch (key) {
                case "date", "openingPrincipal", "principalPaid", "interestPaid", "payment", "closingPrincipal", "interestExcluded" ->
                    label.apply("pdf.repayment." + key);
                default -> heading(kind, key, label);
            };
        }
        return heading(kind, key, label);
    }


    public static String printed(Kind kind, OffsetDateTime time, Locale locale, Function<String, String> label) {
        String stamp = time.format(DateTimeFormatter.ofPattern("EEEE MMMM d uuuu h:mm a", locale));
        return kind == Kind.DISBURSED_LOANS_AUDIT ? label.apply("pdf.printedOn") + "  " + stamp : stamp;
    }

    static String heading(Kind kind, String key, Function<String, String> label) {
        if (kind == Kind.DISBURSED_LOANS_AUDIT) {
            if (key.equals("reference")) return label.apply("pdf.auditReference");
            if (key.equals("periods")) return label.apply("pdf.auditPeriods");
        }
        String caption = label.apply("field." + key);
        boolean twoLines = switch (kind) {
            case DISBURSED_LOANS_AUDIT -> Set.of("disbursed", "disbursementDate", "maturity").contains(key);
            case EXPECTED_REPAYMENTS -> Set.of("clientNumber", "phone", "principalBalance").contains(key);
            case LOAN_REPAYMENT_TRANSACTIONS -> true;
            default -> false;
        };
        int split = twoLines ? caption.lastIndexOf(' ') : -1;
        return split < 0 ? caption : caption.substring(0, split) + "\n" + caption.substring(split + 1);
    }
}
