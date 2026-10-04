package com.sacco.mvp.accounting.policy.dto;

import com.sacco.mvp.accounting.policy.model.*;
import java.time.LocalDate;
import java.util.Map;

public record PolicyContent(GlAuthority authority, LocalDate effectiveFrom, LocalDate openingDate,
                            String authorityEvidence, Map<PolicyDecision, String> decisions,
                            Map<AccountingEvent, PostingRule> postingRules) {
    public PolicyContent {
        decisions = decisions == null ? Map.of() : Map.copyOf(decisions);
        postingRules = postingRules == null ? Map.of() : Map.copyOf(postingRules);
    }
}
