package com.sacco.mvp.accounting.policy.controller;

import com.sacco.mvp.accounting.policy.dto.*;
import com.sacco.mvp.accounting.policy.model.*;
import com.sacco.mvp.accounting.policy.service.AccountingPolicyService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/accounting/policies")
public class AccountingPolicyController {
    private final AccountingPolicyService policies;

    @GetMapping
    @PreAuthorize("@access.has(principal,'ACCOUNTING_POLICY_VIEW')")
    public String index(@AuthenticationPrincipal AppUserPrincipal actor, @RequestParam(defaultValue="0") int page, Model model) {
        model.addAttribute("policies",policies.list(actor,page));
        return "accounting/policies/index";
    }

    @GetMapping("/new")
    @PreAuthorize("@access.has(principal,'ACCOUNTING_POLICY_CREATE')")
    public String newPolicy(@AuthenticationPrincipal AppUserPrincipal actor, @RequestParam(required=false) UUID source, Model model) {
        // Service authorization is also enforced before any policy data is returned or accepted.
        model.addAttribute("form",source == null ? Map.of("requestKey",UUID.randomUUID().toString()) : PolicyForm.from(policies.view(actor,source).content()));
        return prepareForm(model);
    }

    @PostMapping
    @PreAuthorize("@access.has(principal,'ACCOUNTING_POLICY_CREATE')")
    public String create(@AuthenticationPrincipal AppUserPrincipal actor, @RequestParam Map<String,String> form, Model model) {
        try {
            var result = policies.create(actor,UUID.fromString(Objects.requireNonNullElse(form.get("requestKey"),"")),PolicyForm.decode(form));
            return "redirect:/accounting/policies/"+result.id();
        } catch (IllegalArgumentException ex) {
            model.addAttribute("form",form);
            model.addAttribute("policyError",error(ex));
            return prepareForm(model);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("@access.has(principal,'ACCOUNTING_POLICY_VIEW')")
    public String view(@AuthenticationPrincipal AppUserPrincipal actor, @PathVariable UUID id, Model model) {
        model.addAttribute("policy",policies.view(actor,id));
        return "accounting/policies/view";
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("@access.has(principal,'ACCOUNTING_POLICY_APPROVE')")
    public String approve(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String expectedHash,
                          @RequestParam String evidence,@RequestParam String reason,RedirectAttributes flash) {
        try { policies.approve(actor,id,expectedHash,evidence,reason); flash.addFlashAttribute("policySuccess","policy.approved"); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("policyError",error(ex)); flash.addFlashAttribute("reviewEvidence",bounded(evidence,500)); flash.addFlashAttribute("reviewReason",bounded(reason,2000)); }
        return "redirect:/accounting/policies/"+id;
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("@access.has(principal,'ACCOUNTING_POLICY_REJECT')")
    public String reject(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String expectedHash,
                         @RequestParam String evidence,@RequestParam String reason,RedirectAttributes flash) {
        try { policies.reject(actor,id,expectedHash,evidence,reason); flash.addFlashAttribute("policySuccess","policy.rejected"); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("policyError",error(ex)); flash.addFlashAttribute("reviewEvidence",bounded(evidence,500)); flash.addFlashAttribute("reviewReason",bounded(reason,2000)); }
        return "redirect:/accounting/policies/"+id;
    }

    private String prepareForm(Model model) {
        model.addAttribute("decisionKeys",PolicyDecision.values());
        model.addAttribute("eventKeys",AccountingEvent.values());
        model.addAttribute("accountRoles",AccountRole.values());
        return "accounting/policies/new";
    }
    private String error(IllegalArgumentException ex) {
        String message = ex.getMessage();
        return message != null && message.startsWith("policy.error.") ? message : "policy.error.invalid";
    }
    private String bounded(String value, int limit) { return value == null ? "" : value.substring(0,Math.min(value.length(),limit)); }
}
