package com.sacco.mvp.accounting.policy;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.*;

@Data
public class AccountingPolicyForm {
    public enum InterestRecording { CASH_DUE_INTEREST_V1, ACCRUAL_DUE_INTEREST_V1 }
    public static final List<PostingEvent> EVENTS = List.of(PostingEvent.OPENING_BALANCE, PostingEvent.DISBURSEMENT,
        PostingEvent.REPAYMENT, PostingEvent.INTEREST_ACCRUAL, PostingEvent.ADVANCE, PostingEvent.REFUND,
        PostingEvent.EXPENSE, PostingEvent.FUNDING, PostingEvent.CAPITAL, PostingEvent.REVERSAL, PostingEvent.OPERATIONAL_BRIDGE);
    public static final List<String> ACCOUNT_KEYS = List.of("CASH", "BANK", "MOBILE_MONEY", "LOAN_PRINCIPAL",
        "INTEREST_INCOME", "INTEREST_RECEIVABLE", "DISBURSEMENT_CLEARING", "CASH_CLEARING", "BANK_CLEARING",
        "MOBILE_MONEY_CLEARING", "INTEREST_COLLECTIONS_CLEARING", "UNAPPLIED_FUNDS", "OPERATING_EXPENSE",
        "SUPPLIER_PAYABLE", "OWNER_CAPITAL", "OWNER_DISTRIBUTIONS", "FUNDING_PRINCIPAL", "FUNDING_INTEREST_EXPENSE",
        "FIXED_ASSET", "DEPRECIATION_EXPENSE", "ACCUMULATED_DEPRECIATION", "DISPOSAL_GAIN", "DISPOSAL_LOSS",
        "PREPAYMENT_ASSET", "ACCRUED_LIABILITY", "TAX_EXPENSE", "TAX_PAYABLE", "INTERNAL_DUE_FROM", "INTERNAL_DUE_TO");
    private UUID requestKey;
    private UUID sourcePolicyId;
    private AccountingPolicyService.AuthoritativeLedger authoritativeLedger;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate openingDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate effectiveFrom;
    private String evidenceReference;
    private InterestRecording interestRecording;
    private Map<PostingEvent, AccountingPolicyService.PostingPermission> permissions = new EnumMap<>(PostingEvent.class);
    private Map<String, UUID> accounts = new LinkedHashMap<>();

    static AccountingPolicyForm from(AccountingPolicyService.PolicySnapshot source) {
        var form = new AccountingPolicyForm();
        form.setRequestKey(UUID.randomUUID()); form.setSourcePolicyId(source.id());
        form.setAuthoritativeLedger(source.authoritativeLedger()); form.setOpeningDate(source.openingDate());
        String interest = source.decisions().get(PolicyDecision.INTEREST_RECOGNITION);
        for (var mode : InterestRecording.values()) if (mode.name().equals(interest)) form.setInterestRecording(mode);
        EVENTS.forEach(event -> form.permissions.put(event, source.postingMatrix().get(event).permission()));
        source.accountMappings().forEach((key, value) -> {if (ACCOUNT_KEYS.contains(key)) form.accounts.put(key, value);});
        return form;
    }
}
