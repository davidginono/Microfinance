package com.sacco.mvp.accounting.policy.dto;

import com.sacco.mvp.accounting.policy.model.AccountRole;
import java.util.Map;

public record PostingRule(boolean enabled, Map<AccountRole, String> accountCodes,
                          String treatment, String evidenceReference) {
    public PostingRule { accountCodes = accountCodes == null ? Map.of() : Map.copyOf(accountCodes); }
}
