package com.sacco.mvp.accounting.policy.dto;

import com.sacco.mvp.accounting.policy.model.AccountRole;
import java.util.Map;
import java.util.Collections;
import java.util.EnumMap;

public record PostingRule(boolean enabled, Map<AccountRole, String> accountCodes,
                          String treatment, String evidenceReference) {
    public PostingRule {
        EnumMap<AccountRole,String> ordered = new EnumMap<>(AccountRole.class);
        if (accountCodes != null) ordered.putAll(accountCodes);
        accountCodes = Collections.unmodifiableMap(ordered);
    }
}
