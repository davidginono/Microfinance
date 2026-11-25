package com.sacco.mvp.accounting.reports;

import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Descriptions shared by vouchers and reports. Recorded narration takes precedence over a translated default. */
public final class PostingDescription {
    private static final String UUID = "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}";
    private static final String SOURCE_ID = "(?:(?:CHG-|TU-)?" + UUID + "|BATCH/" + UUID + "(?:/[0-9]+)?)";
    private static final Pattern INTERNAL_SOURCE = Pattern.compile(SOURCE_ID,Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERNAL_REFERENCE = Pattern.compile(
        "(?:" + SOURCE_ID + "|(?:RV|PV|JV)-[0-9]{4}-[0-9]+(?:/[0-9]+)?)",Pattern.CASE_INSENSITIVE);
    private static final Map<String,String> KEYS = Map.ofEntries(
        Map.entry("LOAN_RECORD_DISBURSEMENT","disbursement"), Map.entry("LOAN_CHARGE_FUNDING","disbursement"),
        Map.entry("LOAN_DISBURSEMENT","disbursement"), Map.entry("LOAN_RECORD_PAYMENT","repayment"),
        Map.entry("LOAN_REPAYMENT","repayment"), Map.entry("LOAN_RECORD_REVERSAL","repaymentCorrection"),
        Map.entry("LOAN_REPAYMENT_REVERSAL","repaymentCorrection"), Map.entry("LOAN_CHARGE_ASSESS","chargesAssessed"),
        Map.entry("LOAN_CHARGE_UNDO","chargeCorrection"), Map.entry("LOAN_CHARGE_RECEIPT","chargeReceipt"),
        Map.entry("LOAN_CHARGE_REVERSAL","chargeReceiptCorrection"), Map.entry("LOAN_TOPUP","topup"),
        Map.entry("LOAN_TOPUP_UNDO","topupCorrection"), Map.entry("LOAN_FEE_CUTOVER","feeRecovery"),
        Map.entry("LOAN_ADJUSTMENT","adjustment"), Map.entry("CORE_RECEIPT","receipt"),
        Map.entry("CORE_PAYMENT","payment"), Map.entry("CORE_REVERSAL","correction"),
        Map.entry("OPENING","opening"), Map.entry("OPENING_BALANCE","opening"));

    private PostingDescription() {}

    public static String resolve(String sourceType, String sourceReference, String voucherNumber, String... candidates) {
        return resolve(LocaleContextHolder.getLocale(), sourceType, sourceReference, voucherNumber, candidates);
    }

    public static String resolve(Locale locale, String sourceType, String sourceReference, String voucherNumber, String... candidates) {
        for (String candidate : candidates) {
            if (candidate == null) continue;
            String value = candidate.strip();
            if (!INTERNAL_REFERENCE.matcher(value).matches() && externalReference(value, sourceReference, voucherNumber) != null) return value;
        }
        return bundle(locale).getString("posting.description." + KEYS.getOrDefault(sourceType == null ? "" : sourceType,"journal"));
    }

    /** Used by registers that must sort and search the translated descriptions before pagination. */
    public static Map<String,String> localizedDefaults() {
        var messages = bundle(LocaleContextHolder.getLocale());
        return KEYS.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
            entry -> messages.getString("posting.description." + entry.getValue())));
    }

    public static String internalReferencePattern() {
        return "^" + INTERNAL_REFERENCE.pattern() + "$";
    }

    private static ResourceBundle bundle(Locale locale) {
        Locale language = "sw".equals(locale.getLanguage()) ? Locale.forLanguageTag("sw") : Locale.ENGLISH;
        return ResourceBundle.getBundle("messages", language);
    }

    static String externalReference(String value, String sourceReference, String voucherNumber) {
        if (value == null) return null;
        String reference=value.strip();
        if (reference.isEmpty() || reference.equalsIgnoreCase(sourceReference) || reference.equalsIgnoreCase(voucherNumber)
            || INTERNAL_SOURCE.matcher(reference).matches()) return null;
        int suffix=reference.lastIndexOf('/');
        if (suffix>0 && reference.substring(0,suffix).equalsIgnoreCase(voucherNumber)
            && reference.substring(suffix+1).matches("[0-9]+")) return null;
        return reference;
    }
}
