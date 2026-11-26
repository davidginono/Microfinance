package com.sacco.mvp.accounting.reports;

/** Shared read-side narration rules. SQL identifiers are fixed by the report repositories. */
public final class PostingDescriptionSql {
    private PostingDescriptionSql() {}

    public static String repaymentActivity(String journal) {
        return """
            left join gl_activity repayment_activity on repayment_activity.sacco_id=%1$s.sacco_id
                and repayment_activity.system_key='LOAN_REPAYMENT'
                and %1$s.source_type in ('LOAN_RECORD_PAYMENT','LOAN_CHARGE_RECEIPT','LOAN_REPAYMENT')
            """.formatted(journal);
    }

    public static String withoutLoanPickerLabel(String note, String loanNumber, String product) {
        return "nullif(btrim(" + note + ")," + loanNumber + "||' - '||" + product + ")";
    }

    public static String withoutTransactionPickerLabel(String voucherRow) {
        return "nullif(nullif(btrim(" + voucherRow + ".description),'')," + voucherRow + ".transaction_name)";
    }

    public static String transactionName(String voucherRow, String definition) {
        // The immutable snapshot is the picker label (code followed by the name).
        return "coalesce(nullif(substring(" + voucherRow + ".transaction_name from '^[^ ]+ - (.+)$'),''),"
            + "nullif(" + voucherRow + ".transaction_name,'')," + definition + ".name)";
    }

    public static String transactionNameSw(String voucherRow, String definition) {
        return "coalesce(nullif(" + definition + ".name_sw,'')," + transactionName(voucherRow,definition) + ")";
    }

    public static String singleValue(String expression) {
        return "case when count(distinct " + expression + ")=1 then min(" + expression + ") end";
    }
}
