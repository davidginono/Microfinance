package com.sacco.mvp.accounting.policy.dto;

import com.sacco.mvp.accounting.policy.model.*;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record ApprovedAccountingPolicy(UUID id, int version, LocalDate effectiveFrom,
                                       GlAuthority authority, String contentHash,
                                       Map<PolicyDecision, String> decisions,
                                       Map<AccountingEvent, PostingRule> postingRules) {
    public ApprovedAccountingPolicy {
        decisions = Map.copyOf(decisions);
        postingRules = Map.copyOf(postingRules);
    }
}
