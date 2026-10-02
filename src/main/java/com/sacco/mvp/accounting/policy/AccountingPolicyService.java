package com.sacco.mvp.accounting.policy;

import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AccountingPolicyService {
    public enum AuthoritativeLedger { LOCAL_GL, EXTERNAL_GL }
    public enum Decision { APPROVED, REJECTED }
    public enum PostingPermission { ALLOWED, DISABLED }
    public record PostingRule(PostingPermission permission, String treatment) {}
    public record PolicyCommand(UUID requestKey, AuthoritativeLedger authoritativeLedger, LocalDate openingDate,
        LocalDate effectiveFrom, Map<PolicyDecision, String> decisions, Map<PostingEvent, PostingRule> postingMatrix,
        Map<String, UUID> accountMappings, String evidenceReference) {}
    public record ApprovalCommand(Decision decision, String evidenceReference, String reason, boolean confirmed) {}
    public record PolicySnapshot(UUID id, String institutionId, int version, LocalDate openingDate, LocalDate effectiveFrom,
        AuthoritativeLedger authoritativeLedger, Map<PolicyDecision, String> decisions,
        Map<PostingEvent, PostingRule> postingMatrix, Map<String, UUID> accountMappings, String evidenceReference, UUID makerId,
        Decision approvalDecision, UUID checkerId, String approvalEvidence, String approvalReason, OffsetDateTime approvedAt) {}

    private final AccountingPolicyRepository policies;
    private final AccountingPolicyApprovalRepository approvals;
    private final AccessControlService access;
    private final AuditService audit;
    private final ApplicationClock clock;
    private final ObjectMapper mapper;
    private final UserClaimService userClaims;
    private final MemberDirectoryService directory;

    @Transactional
    public PolicySnapshot create(AppUserPrincipal actor, PolicyCommand command) {
        String institution = requireActor(actor, UserClaim.ACCOUNTING_POLICIES_CREATE);
        validate(command);
        String decisions = mapper.writeValueAsString(command.decisions());
        String matrix = mapper.writeValueAsString(command.postingMatrix());
        String mappings = mapper.writeValueAsString(command.accountMappings());
        var prior = policies.findBySaccoIdAndRequestKey(institution, command.requestKey());
        if (prior.isPresent()) {
            var p = prior.get();
            require(p.getMakerId().equals(actor.getMemberId()) && p.getAuthoritativeLedger() == command.authoritativeLedger()
                && p.getOpeningDate().equals(command.openingDate()) && p.getEffectiveFrom().equals(command.effectiveFrom())
                && readDecisions(p).equals(command.decisions()) && readMatrix(p).equals(command.postingMatrix())
                && readMappings(p).equals(command.accountMappings())
                && p.getEvidenceReference().equals(command.evidenceReference().trim()), "retry");
            return snapshot(p, approvals.findById(p.getId()).orElse(null));
        }
        var p = policies.saveAndFlush(AccountingPolicy.builder().id(UUID.randomUUID()).saccoId(institution)
            .policyVersion(policies.latestVersion(institution) + 1).requestKey(command.requestKey())
            .authoritativeLedger(command.authoritativeLedger()).openingDate(command.openingDate())
            .effectiveFrom(command.effectiveFrom()).decisionsJson(decisions).postingMatrixJson(matrix)
            .accountMappingsJson(mappings)
            .evidenceReference(command.evidenceReference().trim()).makerId(actor.getMemberId()).createdAt(clock.now()).build());
        audit.log("ACCOUNTING_POLICY", p.getId(), "ACCOUNTING_POLICY_CREATED", actor.getMemberId(), null,
            Map.of("saccoId", institution, "policyVersion", p.getPolicyVersion(), "status", "PENDING_REVIEW"));
        return snapshot(p, null);
    }

    @Transactional
    public PolicySnapshot decide(UUID policyId, AppUserPrincipal actor, ApprovalCommand command) {
        String institution = requireActor(actor, UserClaim.ACCOUNTING_POLICIES_APPROVE);
        var p = policies.findByIdAndSaccoId(policyId, institution).orElseThrow(() -> new AccessDeniedException("Policy unavailable"));
        require(!p.getMakerId().equals(actor.getMemberId()), "checker");
        require(command != null && command.confirmed() && command.decision() != null, "validation");
        text(command.evidenceReference(), 1000); text(command.reason(), 2000);
        var existing = approvals.findById(policyId);
        if (existing.isPresent()) {
            var a = existing.get();
            require(a.getCheckerId().equals(actor.getMemberId()) && a.getDecision() == command.decision()
                && a.getEvidenceReference().equals(command.evidenceReference().trim())
                && a.getReason().equals(command.reason().trim()), "decided");
            return snapshot(p, a);
        }
        if (command.decision() == Decision.APPROVED) {
            // A new decision cannot rewrite an earlier policy's effective boundary or switch the official books.
            var previous = policies.approvedAt(institution, LocalDate.of(9999, 12, 31), PageRequest.of(0, 1));
            if (!previous.isEmpty()) {
                var prev = previous.getFirst();
                require(p.getPolicyVersion() > prev.getPolicyVersion() && p.getEffectiveFrom().isAfter(prev.getEffectiveFrom())
                    && !p.getEffectiveFrom().isBefore(clock.today()), "effective");
                require(p.getAuthoritativeLedger() == prev.getAuthoritativeLedger()
                    && p.getOpeningDate().equals(prev.getOpeningDate()), "cutover");
            } else require(p.getOpeningDate().equals(p.getEffectiveFrom()), "initialDate");
        }
        var a = approvals.saveAndFlush(AccountingPolicyApproval.builder().policyId(p.getId()).saccoId(institution)
            .policyVersion(p.getPolicyVersion()).effectiveFrom(p.getEffectiveFrom()).checkerId(actor.getMemberId())
            .decision(command.decision()).evidenceReference(command.evidenceReference().trim())
            .reason(command.reason().trim()).decidedAt(clock.now()).build());
        audit.log("ACCOUNTING_POLICY", p.getId(), "ACCOUNTING_POLICY_" + command.decision().name(), actor.getMemberId(), null,
            Map.of("saccoId", institution, "policyVersion", p.getPolicyVersion(), "makerId", p.getMakerId(),
                "checkerId", actor.getMemberId(), "evidenceReference", command.evidenceReference().trim()));
        return snapshot(p, a);
    }

    @Transactional(readOnly = true)
    public Page<PolicySnapshot> list(AppUserPrincipal actor, int page) {
        String institution = requireActor(actor, UserClaim.ACCOUNTING_POLICIES_VIEW);
        require(page >= 0 && page <= 10000, "validation");
        var result = policies.findBySaccoIdOrderByPolicyVersionDesc(institution, PageRequest.of(page, 25));
        var decisions = new HashMap<UUID, AccountingPolicyApproval>();
        if (!result.isEmpty()) approvals.findBySaccoIdAndPolicyIdIn(institution, result.stream().map(AccountingPolicy::getId).toList())
            .forEach(a -> decisions.put(a.getPolicyId(), a));
        return result.map(p -> snapshot(p, decisions.get(p.getId())));
    }

    @Transactional(readOnly = true)
    public PolicySnapshot view(UUID id, AppUserPrincipal actor) {
        String institution = requireActor(actor, UserClaim.ACCOUNTING_POLICIES_VIEW);
        var p = policies.findByIdAndSaccoId(id, institution).orElseThrow(() -> new AccessDeniedException("Policy unavailable"));
        return snapshot(p, approvals.findById(id).orElse(null));
    }

    /** Internal accounting boundary; callers must first authorize their financial command and institution scope. */
    @Transactional(readOnly = true)
    public PolicySnapshot requireApprovedLocalPolicy(String institutionId, LocalDate effectiveDate) {
        require(institutionId != null && !institutionId.isBlank() && effectiveDate != null, "validation");
        var applicable = policies.approvedAt(institutionId, effectiveDate, PageRequest.of(0, 1));
        require(!applicable.isEmpty(), "unapproved");
        var p = applicable.getFirst();
        require(p.getAuthoritativeLedger() == AuthoritativeLedger.LOCAL_GL, "external");
        require(!effectiveDate.isBefore(p.getOpeningDate()), "opening");
        return snapshot(p, approvals.findById(p.getId()).orElseThrow(() -> new IllegalArgumentException("accounting.policy.error.unapproved")));
    }

    public void requireAllowedPosting(PolicySnapshot policy, PostingEvent event) {
        require(policy != null && policy.approvalDecision() == Decision.APPROVED && event != null, "unapproved");
        var rule = policy.postingMatrix().get(event);
        require(rule != null && rule.permission() == PostingPermission.ALLOWED, "disabled");
    }

    /** Deletion lifecycle guard. Callers enforce their own administration permission/scope first. */
    @Transactional(readOnly = true)
    public boolean hasInstitutionHistory(String institutionId) {
        return institutionId != null && policies.existsBySaccoId(institutionId);
    }

    /** Preserve makers/checkers of even rejected proposals; deactivate access instead of deleting evidence. */
    @Transactional(readOnly = true)
    public boolean hasMemberHistory(UUID memberId) {
        return memberId != null && (policies.existsByMakerId(memberId) || approvals.existsByCheckerId(memberId));
    }

    private String requireActor(AppUserPrincipal actor, UserClaim claim) {
        if (actor == null || !actor.isStaffSession() || actor.isPlatformIdentity() || !access.has(actor, claim)
            || actor.getSaccoId() == null || actor.getSaccoId().isBlank() || actor.getMemberId() == null)
            throw new AccessDeniedException("Accounting policy access denied");
        var current = directory.find(actor.getMemberId()).orElseThrow(() -> new AccessDeniedException("Accounting policy access denied"));
        if (!current.isStaffAccessActive() || !Objects.equals(current.getSaccoId(), actor.getSaccoId())
            || current.getActiveStaffRolesResolved().contains(Position.ADMIN)
            || !userClaims.effectiveClaims(current.getId(), current.getActiveStaffRolesResolved(), current.isMemberAccess()).contains(claim))
            throw new AccessDeniedException("Accounting policy permission unavailable");
        return actor.getSaccoId();
    }

    private void validate(PolicyCommand c) {
        require(c != null && c.requestKey() != null && c.authoritativeLedger() != null && c.openingDate() != null
            && c.effectiveFrom() != null && c.openingDate().getYear() >= 1 && c.effectiveFrom().getYear() <= 9999
            && !c.openingDate().isAfter(c.effectiveFrom()), "validation");
        require(c.decisions() != null && c.decisions().keySet().equals(EnumSet.allOf(PolicyDecision.class)), "incomplete");
        c.decisions().values().forEach(v -> text(v, 6000));
        require(c.postingMatrix() != null && c.postingMatrix().keySet().equals(EnumSet.allOf(PostingEvent.class)), "incomplete");
        c.postingMatrix().values().forEach(r -> { require(r != null && r.permission() != null, "incomplete"); text(r.treatment(), 6000); });
        require(c.accountMappings() != null && c.accountMappings().size() <= 100, "incomplete");
        c.accountMappings().forEach((key, value) -> require(key != null && key.matches("[A-Z][A-Z0-9_]{2,79}") && value != null, "incomplete"));
        text(c.evidenceReference(), 1000);
    }
    private Map<PolicyDecision, String> readDecisions(AccountingPolicy p) {
        return mapper.readValue(p.getDecisionsJson(), new TypeReference<EnumMap<PolicyDecision, String>>() {});
    }
    private Map<PostingEvent, PostingRule> readMatrix(AccountingPolicy p) {
        return mapper.readValue(p.getPostingMatrixJson(), new TypeReference<EnumMap<PostingEvent, PostingRule>>() {});
    }
    private Map<String, UUID> readMappings(AccountingPolicy p) {
        return mapper.readValue(p.getAccountMappingsJson(), new TypeReference<Map<String, UUID>>() {});
    }
    private PolicySnapshot snapshot(AccountingPolicy p, AccountingPolicyApproval a) {
        return new PolicySnapshot(p.getId(), p.getSaccoId(), p.getPolicyVersion(), p.getOpeningDate(), p.getEffectiveFrom(),
            p.getAuthoritativeLedger(), Map.copyOf(readDecisions(p)), Map.copyOf(readMatrix(p)), Map.copyOf(readMappings(p)), p.getEvidenceReference(),
            p.getMakerId(), a == null ? null : a.getDecision(), a == null ? null : a.getCheckerId(),
            a == null ? null : a.getEvidenceReference(), a == null ? null : a.getReason(), a == null ? null : a.getDecidedAt());
    }
    private static void text(String value, int max) { require(value != null && !value.isBlank() && value.length() <= max, "incomplete"); }
    private static void require(boolean condition, String key) {
        if (!condition) throw new IllegalArgumentException("accounting.policy.error." + key);
    }
}
