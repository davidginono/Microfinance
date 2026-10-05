package com.sacco.mvp.accounting.policy;

import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Controller @RequiredArgsConstructor @RequestMapping("/finance/policies")
public class AccountingPolicyController {
    private final AccountingPolicyService policies;

    @InitBinder("policyForm") void bind(WebDataBinder binder) {
        binder.setAllowedFields("requestKey", "authoritativeLedger", "openingDate", "effectiveFrom", "evidenceReference",
            "accountMappings", "decisions[*]", "permissions[*]", "treatments[*]");
        binder.setAutoGrowCollectionLimit(100);
    }
    @GetMapping @PreAuthorize("@access.has(principal, 'ACCOUNTING_POLICIES_VIEW')")
    public String list(@AuthenticationPrincipal AppUserPrincipal actor, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("policies", policies.list(actor, page)); return "accounting/policies/index";
    }
    @GetMapping("/new") @PreAuthorize("@access.has(principal, 'ACCOUNTING_POLICIES_CREATE')")
    public String createForm(Model model) {
        var form = new AccountingPolicyForm(); form.setRequestKey(UUID.randomUUID());
        model.addAttribute("policyForm", form); fields(model); return "accounting/policies/new";
    }
    @PostMapping @PreAuthorize("@access.has(principal, 'ACCOUNTING_POLICIES_CREATE')")
    public String create(@AuthenticationPrincipal AppUserPrincipal actor, @ModelAttribute("policyForm") AccountingPolicyForm form,
                         BindingResult errors, Model model) {
        if (!errors.hasErrors()) {
            try { return "redirect:/finance/policies/" + policies.create(actor, form.command()).id(); }
            catch (IllegalArgumentException e) { model.addAttribute("policyError", error(e)); }
            catch (DataAccessException e) { model.addAttribute("policyError", "accounting.policy.error.conflict"); }
        } else model.addAttribute("policyError", "accounting.policy.error.validation");
        fields(model); return "accounting/policies/new";
    }
    @GetMapping("/{id}") @PreAuthorize("@access.has(principal, 'ACCOUNTING_POLICIES_VIEW')")
    public String view(@PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal actor, Model model) {
        model.addAttribute("policy", policies.view(id, actor)); fields(model); return "accounting/policies/view";
    }
    @PostMapping("/{id}/decision") @PreAuthorize("@access.has(principal, 'ACCOUNTING_POLICIES_APPROVE')")
    public String decide(@PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal actor,
        @RequestParam AccountingPolicyService.Decision decision, @RequestParam String evidenceReference,
        @RequestParam String reason, @RequestParam(defaultValue = "false") boolean confirmed, Model model) {
        try { policies.decide(id, actor, new AccountingPolicyService.ApprovalCommand(decision, evidenceReference, reason, confirmed));
            return "redirect:/finance/policies/" + id; }
        catch (IllegalArgumentException e) { model.addAttribute("policyError", error(e)); }
        catch (DataAccessException e) { model.addAttribute("policyError", "accounting.policy.error.conflict"); }
        model.addAttribute("decisionEvidence", evidenceReference); model.addAttribute("decisionReason", reason);
        return view(id, actor, model);
    }
    private void fields(Model model) {
        model.addAttribute("decisionFields", PolicyDecision.values()); model.addAttribute("postingEvents", PostingEvent.values());
    }
    private String error(IllegalArgumentException e) {
        return e.getMessage() != null && e.getMessage().startsWith("accounting.policy.error.") ? e.getMessage() : "accounting.policy.error.validation";
    }
}
