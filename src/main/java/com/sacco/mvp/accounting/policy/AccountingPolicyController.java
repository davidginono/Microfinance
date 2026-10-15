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
import java.util.List;

@Controller @RequiredArgsConstructor @RequestMapping("/finance/policies")
public class AccountingPolicyController {
    private final AccountingPolicyService policies;

    @InitBinder("policyForm") void bind(WebDataBinder binder) {
        binder.setAllowedFields("requestKey", "sourcePolicyId", "authoritativeLedger", "openingDate", "effectiveFrom", "evidenceReference",
            "interestRecording", "accounts[*]", "permissions[*]");
        binder.setAutoGrowCollectionLimit(100);
    }
    @GetMapping @PreAuthorize("@access.has(principal, 'ACCOUNTING_POLICIES_VIEW')")
    public String list(@AuthenticationPrincipal AppUserPrincipal actor, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("policies", policies.list(actor, page)); return "accounting/policies/index";
    }
    @GetMapping("/new") @PreAuthorize("@access.has(principal, 'ACCOUNTING_POLICIES_CREATE')")
    public String createForm(@AuthenticationPrincipal AppUserPrincipal actor, Model model) {
        var form = policies.settingsForm(actor);
        model.addAttribute("policyForm", form); fields(model);
        model.addAttribute("accountLabels", policies.accountLabels(actor, form.getAccounts().values()));
        return "accounting/policies/new";
    }
    @GetMapping("/{id}/change") @PreAuthorize("@access.has(principal, 'ACCOUNTING_POLICIES_CREATE') and @access.has(principal, 'ACCOUNTING_POLICIES_VIEW')")
    public String change(@PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal actor, Model model) {
        var form = AccountingPolicyForm.from(policies.view(id, actor));
        model.addAttribute("policyForm", form); fields(model);
        model.addAttribute("accountLabels", policies.accountLabels(actor, form.getAccounts().values()));
        return "accounting/policies/new";
    }
    @GetMapping("/accounts") @ResponseBody @PreAuthorize("@access.has(principal, 'ACCOUNTING_POLICIES_CREATE')")
    public List<AccountingPolicyService.AccountChoice> accounts(@AuthenticationPrincipal AppUserPrincipal actor,
        @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") int page) {
        return policies.accountChoices(actor, search, page);
    }
    @PostMapping @PreAuthorize("@access.has(principal, 'ACCOUNTING_POLICIES_CREATE')")
    public String create(@AuthenticationPrincipal AppUserPrincipal actor, @ModelAttribute("policyForm") AccountingPolicyForm form,
                         BindingResult errors, Model model) {
        if (!errors.hasErrors()) {
            try { return "redirect:/finance/policies/" + policies.saveSettings(actor, form).id(); }
            catch (IllegalArgumentException e) { model.addAttribute("policyError", error(e)); }
            catch (DataAccessException e) { model.addAttribute("policyError", "accounting.policy.error.conflict"); }
        } else model.addAttribute("policyError", "accounting.policy.error.validation");
        fields(model);
        model.addAttribute("accountLabels", policies.accountLabels(actor, form.getAccounts().values().stream().filter(java.util.Objects::nonNull).toList()));
        return "accounting/policies/new";
    }
    @GetMapping("/{id}") @PreAuthorize("@access.has(principal, 'ACCOUNTING_POLICIES_VIEW')")
    public String view(@PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal actor, Model model) {
        var policy = policies.view(id, actor);
        model.addAttribute("policy", policy); fields(model);
        model.addAttribute("accountLabels", policies.accountLabels(actor, policy.accountMappings().values()));
        return "accounting/policies/view";
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
        model.addAttribute("postingEvents", AccountingPolicyForm.EVENTS);
        model.addAttribute("accountKeys", AccountingPolicyForm.ACCOUNT_KEYS);
        model.addAttribute("interestModes", AccountingPolicyForm.InterestRecording.values());
    }
    private String error(IllegalArgumentException e) {
        return e.getMessage() != null && e.getMessage().startsWith("accounting.policy.error.") ? e.getMessage() : "accounting.policy.error.validation";
    }
}
