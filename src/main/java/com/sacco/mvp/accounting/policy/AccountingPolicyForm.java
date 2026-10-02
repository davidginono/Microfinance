package com.sacco.mvp.accounting.policy;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.*;

@Data
public class AccountingPolicyForm {
    private UUID requestKey;
    private AccountingPolicyService.AuthoritativeLedger authoritativeLedger;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate openingDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate effectiveFrom;
    private String evidenceReference;
    private String accountMappings;
    private Map<PolicyDecision, String> decisions = new EnumMap<>(PolicyDecision.class);
    private Map<PostingEvent, AccountingPolicyService.PostingPermission> permissions = new EnumMap<>(PostingEvent.class);
    private Map<PostingEvent, String> treatments = new EnumMap<>(PostingEvent.class);

    AccountingPolicyService.PolicyCommand command() {
        var matrix = new EnumMap<PostingEvent, AccountingPolicyService.PostingRule>(PostingEvent.class);
        for (var event : PostingEvent.values()) matrix.put(event, new AccountingPolicyService.PostingRule(permissions.get(event), treatments.get(event)));
        var mappings = new LinkedHashMap<String, UUID>();
        if (accountMappings != null && !accountMappings.isBlank()) {
            if (accountMappings.length() > 12000) throw new IllegalArgumentException("accounting.policy.error.validation");
            for (String line : accountMappings.split("\\R")) {
                if (line.isBlank()) continue;
                String[] pair = line.trim().split("=", -1);
                if (pair.length != 2 || !pair[0].matches("[A-Z][A-Z0-9_]{2,79}") || mappings.putIfAbsent(pair[0], UUID.fromString(pair[1].trim())) != null)
                    throw new IllegalArgumentException("accounting.policy.error.validation");
            }
        }
        return new AccountingPolicyService.PolicyCommand(requestKey, authoritativeLedger, openingDate, effectiveFrom,
            decisions, matrix, mappings, evidenceReference);
    }
}
