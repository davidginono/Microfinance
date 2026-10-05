package com.sacco.mvp.accounting.policy.dto;

import com.sacco.mvp.accounting.policy.model.*;
import java.time.LocalDate;
import java.util.*;

/** Transport decoder for allowlisted form fields. Never accepts tenant, branch, maker, or approval state. */
public final class PolicyForm {
    private PolicyForm() {}
    public static Map<String,String> from(PolicyContent content) {
        Map<String,String> fields = new HashMap<>();
        fields.put("requestKey",UUID.randomUUID().toString());
        fields.put("authority",content.authority().name()); fields.put("openingDate",content.openingDate().toString());
        fields.put("authorityEvidence",content.authorityEvidence());
        content.decisions().forEach((key,value) -> fields.put("decision."+key.name(),value));
        content.postingRules().forEach((event,rule) -> {
            String prefix="rule."+event.name()+".";
            fields.put(prefix+"enabled",Boolean.toString(rule.enabled())); fields.put(prefix+"treatment",rule.treatment());
            fields.put(prefix+"evidence",rule.evidenceReference());
            rule.accountCodes().forEach((role,code) -> fields.put(prefix+"account."+role.name(),code));
        });
        return fields;
    }
    public static PolicyContent decode(Map<String,String> fields) {
        try {
            Set<String> allowed = new HashSet<>(Set.of("requestKey","authority","effectiveFrom","openingDate","authorityEvidence","_csrf"));
            for (var key : PolicyDecision.values()) allowed.add("decision."+key.name());
            for (var event : AccountingEvent.values()) {
                String prefix = "rule."+event.name()+".";
                allowed.addAll(Set.of(prefix+"enabled",prefix+"treatment",prefix+"evidence"));
                for (var role : AccountRole.values()) allowed.add(prefix+"account."+role.name());
            }
            if (!allowed.containsAll(fields.keySet()) || fields.values().stream().anyMatch(value -> value != null && value.length() > 2000))
                throw new IllegalArgumentException();
            EnumMap<PolicyDecision,String> decisions = new EnumMap<>(PolicyDecision.class);
            for (var key : PolicyDecision.values()) {
                String value = fields.get("decision."+key.name());
                if (value != null && !value.isBlank()) decisions.put(key,value);
            }
            EnumMap<AccountingEvent,PostingRule> rules = new EnumMap<>(AccountingEvent.class);
            for (var event : AccountingEvent.values()) {
                String prefix = "rule."+event.name()+".";
                String enabled = fields.get(prefix+"enabled");
                if (enabled == null || enabled.isBlank()) continue;
                if (!Set.of("true","false").contains(enabled)) throw new IllegalArgumentException();
                EnumMap<AccountRole,String> codes = new EnumMap<>(AccountRole.class);
                for (var role : AccountRole.values()) {
                    String code = fields.get(prefix+"account."+role.name());
                    if (code != null && !code.isBlank()) codes.put(role,code.trim());
                }
                rules.put(event,new PostingRule(Boolean.parseBoolean(enabled),codes,
                    fields.getOrDefault(prefix+"treatment",""),fields.getOrDefault(prefix+"evidence","")));
            }
            return new PolicyContent(GlAuthority.valueOf(fields.get("authority")),LocalDate.parse(fields.get("effectiveFrom")),
                LocalDate.parse(fields.get("openingDate")),fields.get("authorityEvidence"),decisions,rules);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("policy.error.invalid");
        }
    }
}
