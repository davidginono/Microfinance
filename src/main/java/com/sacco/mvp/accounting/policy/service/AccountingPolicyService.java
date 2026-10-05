package com.sacco.mvp.accounting.policy.service;

import com.sacco.mvp.accounting.policy.dto.*;
import com.sacco.mvp.accounting.policy.model.*;
import com.sacco.mvp.accounting.policy.repository.AccountingPolicyRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AccountingPolicyService {
    private final AccountingPolicyRepository policies;
    private final ObjectMapper mapper;
    private final AccessControlService access;
    private final ApplicationClock clock;

    @Transactional(readOnly = true)
    public PolicyPage list(AppUserPrincipal actor, int page) {
        requireActor(actor, "ACCOUNTING_POLICY_VIEW");
        int number = Math.max(0, Math.min(page, 10000));
        var rows = policies.list(actor.getSaccoId(), number * 25);
        return new PolicyPage(rows.stream().limit(25).toList(), number, rows.size() > 25);
    }

    @Transactional(readOnly = true)
    public PolicyView view(AppUserPrincipal actor, UUID id) {
        requireActor(actor, "ACCOUNTING_POLICY_VIEW");
        return view(scoped(actor, id, false));
    }

    @Transactional
    public PolicyView create(AppUserPrincipal actor, UUID requestKey, PolicyContent content) {
        requireActor(actor, "ACCOUNTING_POLICY_CREATE");
        require(requestKey != null, "invalid");
        validate(content, false);
        String json = canonicalJson(content);
        require(json.getBytes(StandardCharsets.UTF_8).length <= 131072, "invalid");
        String hash = hash(json);
        policies.lockGovernance(actor.getSaccoId());
        var retry = policies.retry(actor.getSaccoId(), requestKey);
        if (retry.isPresent()) {
            var previous = retry.get();
            require(previous.createdBy().equals(actor.getMemberId()) && previous.stationId().equals(actor.getStationId())
                && previous.contentHash().equals(hash), "conflict");
            return view(previous);
        }
        var record = new AccountingPolicyRecord(UUID.randomUUID(), actor.getSaccoId(), actor.getStationId(),
            policies.nextVersion(actor.getSaccoId()), "DRAFT", content.authority(), content.effectiveFrom(),
            json, hash, actor.getMemberId(), clock.now(), null, null, null, null, requestKey);
        policies.insert(record);
        policies.audit(record,"CREATED",actor.getMemberId(),actor.getStationId(),clock.now(),content.authorityEvidence(),"Draft submitted for independent review");
        return view(record);
    }

    @Transactional
    public PolicyView approve(AppUserPrincipal actor, UUID id, String expectedHash, String evidence, String reason) {
        return decide(actor,id,expectedHash,evidence,reason,true);
    }

    @Transactional
    public PolicyView reject(AppUserPrincipal actor, UUID id, String expectedHash, String evidence, String reason) {
        return decide(actor,id,expectedHash,evidence,reason,false);
    }

    private PolicyView decide(AppUserPrincipal actor, UUID id, String expectedHash, String evidence, String reason, boolean approved) {
        requireActor(actor, approved ? "ACCOUNTING_POLICY_APPROVE" : "ACCOUNTING_POLICY_REJECT");
        policies.lockGovernance(actor.getSaccoId());
        var record = scoped(actor,id,true);
        require(!record.createdBy().equals(actor.getMemberId()), "independent");
        require(record.contentHash().equals(expectedHash), "conflict");
        String reviewEvidence = text(evidence,500,true);
        String reviewReason = text(reason,2000,true);
        String state = approved ? "APPROVED" : "REJECTED";
        if (!record.state().equals("DRAFT")) {
            require(record.state().equals(state) && actor.getMemberId().equals(record.checkedBy())
                && reviewEvidence.equals(record.reviewEvidence()) && reviewReason.equals(record.reviewReason()), "conflict");
            return view(record);
        }
        if (approved) {
            validate(content(record),true);
            policies.latestApproved(actor.getSaccoId()).ifPresent(previous -> {
                require(previous.authority() == record.authority(), "authority");
                require(content(previous).openingDate().equals(content(record).openingDate()), "openingDate");
                require(record.effectiveFrom().isAfter(previous.effectiveFrom()) && record.effectiveFrom().isAfter(clock.today()), "future");
            });
        }
        policies.decide(actor.getSaccoId(),id,state,actor.getMemberId(),clock.now(),reviewEvidence,reviewReason);
        policies.audit(record,state,actor.getMemberId(),actor.getStationId(),clock.now(),reviewEvidence,reviewReason);
        return view(scoped(actor,id,false));
    }

    /** Internal accounting API: callers authorize their own action and pin this immutable version. */
    @Transactional(readOnly = true)
    public ApprovedAccountingPolicy requireApproved(String saccoId, LocalDate effectiveDate) {
        require(saccoId != null && !saccoId.isBlank() && effectiveDate != null,"unavailable");
        var p = policies.applicable(saccoId,effectiveDate).orElseThrow(() -> new IllegalArgumentException("policy.error.unavailable"));
        var content = content(p);
        validate(content,true);
        return new ApprovedAccountingPolicy(p.id(),p.version(),p.effectiveFrom(),p.authority(),p.contentHash(),content.decisions(),content.postingRules());
    }

    @Transactional(readOnly = true)
    public ApprovedAccountingPolicy requireLocalPolicy(String saccoId, LocalDate effectiveDate) {
        var policy = requireApproved(saccoId,effectiveDate);
        require(policy.authority() == GlAuthority.LOCAL,"external");
        return policy;
    }

    @Transactional(readOnly = true)
    public ApprovedAccountingPolicy requireApprovedLocal(String saccoId, UUID id, int version, LocalDate effectiveDate) {
        var policy = requireLocalPolicy(saccoId,effectiveDate);
        require(policy.id().equals(id) && policy.version() == version,"conflict");
        return policy;
    }

    @Transactional(readOnly = true)
    public PostingRule requirePostingRule(String saccoId, LocalDate effectiveDate, AccountingEvent event) {
        var policy = requireLocalPolicy(saccoId,effectiveDate);
        var rule = event == null ? null : policy.postingRules().get(event);
        require(rule != null && rule.enabled(),"disabled");
        return rule;
    }

    private AccountingPolicyRecord scoped(AppUserPrincipal actor, UUID id, boolean lock) {
        return policies.find(actor.getSaccoId(),id,lock).orElseThrow(() -> new AccessDeniedException("Forbidden"));
    }

    /** End-of-date initial opening only; never authorizes arbitrary historical source events. */
    @Transactional(readOnly = true)
    public ApprovedAccountingPolicy requireOpeningPolicy(String saccoId, LocalDate cutoff) {
        require(saccoId != null && !saccoId.isBlank() && cutoff != null,"unavailable");
        var p = policies.firstApproved(saccoId).orElseThrow(() -> new IllegalArgumentException("policy.error.unavailable"));
        require(p.authority() == GlAuthority.LOCAL,"external");
        var c = content(p); validate(c,true);
        require(c.openingDate().equals(cutoff),"openingDate");
        var rule = c.postingRules().get(AccountingEvent.OPENING_BALANCE);
        require(rule != null && rule.enabled(),"disabled");
        return new ApprovedAccountingPolicy(p.id(),p.version(),p.effectiveFrom(),p.authority(),p.contentHash(),c.decisions(),c.postingRules());
    }

    @Transactional(readOnly = true)
    public ApprovedAccountingPolicy requireOpeningPolicy(String saccoId, UUID policyId, int policyVersion, LocalDate cutoff) {
        var policy = requireOpeningPolicy(saccoId,cutoff);
        require(policy.id().equals(policyId) && policy.version() == policyVersion,"conflict");
        return policy;
    }

    @Transactional(readOnly = true)
    public LocalDate approvedOpeningDate(String saccoId, LocalDate effectiveDate) {
        require(saccoId != null && !saccoId.isBlank() && effectiveDate != null,"unavailable");
        var p = policies.applicable(saccoId,effectiveDate).orElseThrow(() -> new IllegalArgumentException("policy.error.unavailable"));
        require(p.authority() == GlAuthority.LOCAL,"external");
        var c = content(p); validate(c,true);
        return c.openingDate();
    }

    private void requireActor(AppUserPrincipal actor, String claim) {
        if (actor == null || !actor.isStaffSession() || actor.isPlatformIdentity() || actor.getMemberId() == null
            || actor.getSaccoId() == null || actor.getSaccoId().isBlank() || actor.getStationId() == null
            || actor.getStationId().isBlank() || !access.has(actor,claim)) throw new AccessDeniedException("Forbidden");
    }

    private void validate(PolicyContent p, boolean complete) {
        require(p != null && p.authority() != null && p.effectiveFrom() != null && p.openingDate() != null
            && !p.openingDate().isAfter(p.effectiveFrom()),"invalid");
        text(p.authorityEvidence(),500,true);
        require(p.decisions().size() <= PolicyDecision.values().length && p.postingRules().size() <= AccountingEvent.values().length,"invalid");
        if (complete) require(p.decisions().keySet().equals(EnumSet.allOf(PolicyDecision.class))
            && p.postingRules().keySet().equals(EnumSet.allOf(AccountingEvent.class)),"incomplete");
        p.decisions().forEach((key,value) -> text(value,2000,complete));
        p.postingRules().forEach((event,rule) -> {
            require(event != null && rule != null && rule.accountCodes().size() <= AccountRole.values().length,"invalid");
            text(rule.treatment(),2000,complete);
            text(rule.evidenceReference(),500,complete);
            rule.accountCodes().forEach((role,code) -> require(role != null && code != null && code.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,39}"),"invalid"));
            if (complete && rule.enabled()) require(rule.accountCodes().keySet().containsAll(event.requiredRoles()),"mapping");
        });
    }

    private PolicyContent content(AccountingPolicyRecord p) {
        require(hash(p.contentJson()).equals(p.contentHash()),"conflict");
        return mapper.readValue(p.contentJson(),PolicyContent.class);
    }

    private String canonicalJson(PolicyContent p) {
        Map<String,Object> value = new LinkedHashMap<>();
        value.put("authority",p.authority()); value.put("effectiveFrom",p.effectiveFrom());
        value.put("openingDate",p.openingDate()); value.put("authorityEvidence",p.authorityEvidence());
        Map<String,String> decisions = new TreeMap<>();
        p.decisions().forEach((key,text) -> decisions.put(key.name(),text));
        value.put("decisions",decisions);
        Map<String,Object> rules = new TreeMap<>();
        p.postingRules().forEach((event,rule) -> {
            Map<String,String> codes = new TreeMap<>();
            rule.accountCodes().forEach((role,code) -> codes.put(role.name(),code));
            Map<String,Object> decision = new LinkedHashMap<>();
            decision.put("enabled",rule.enabled()); decision.put("accountCodes",codes);
            decision.put("treatment",rule.treatment()); decision.put("evidenceReference",rule.evidenceReference());
            rules.put(event.name(),decision);
        });
        value.put("postingRules",rules);
        return mapper.writeValueAsString(value);
    }

    private PolicyView view(AccountingPolicyRecord p) {
        return new PolicyView(p.id(),p.version(),p.state(),p.stationId(),p.createdBy(),p.createdAt(),p.checkedBy(),
            p.checkedAt(),p.reviewEvidence(),p.reviewReason(),p.contentHash(),content(p));
    }

    private String text(String value, int maximum, boolean required) {
        require(value == null ? !required : value.length() <= maximum && (!required || !value.isBlank())
            && value.chars().noneMatch(c -> c < 32 && c != '\n' && c != '\r' && c != '\t'), required ? "incomplete" : "invalid");
        return value == null ? "" : value.trim();
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static void require(boolean valid, String code) {
        if (!valid) throw new IllegalArgumentException("policy.error."+code);
    }
}
