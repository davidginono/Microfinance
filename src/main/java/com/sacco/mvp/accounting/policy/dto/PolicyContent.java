package com.sacco.mvp.accounting.policy.dto;

import com.sacco.mvp.accounting.policy.model.*;
import java.time.LocalDate;
import java.util.Map;
import java.util.Collections;
import java.util.EnumMap;

public record PolicyContent(GlAuthority authority, LocalDate effectiveFrom, LocalDate openingDate,
                            String authorityEvidence, Map<PolicyDecision, String> decisions,
                            Map<AccountingEvent, PostingRule> postingRules) {
    public PolicyContent {
        EnumMap<PolicyDecision,String> orderedDecisions = new EnumMap<>(PolicyDecision.class);
        if (decisions != null) orderedDecisions.putAll(decisions);
        decisions = Collections.unmodifiableMap(orderedDecisions);
        EnumMap<AccountingEvent,PostingRule> orderedRules = new EnumMap<>(AccountingEvent.class);
        if (postingRules != null) orderedRules.putAll(postingRules);
        postingRules = Collections.unmodifiableMap(orderedRules);
    }
}
