package com.sacco.mvp.accounting.controller;

import com.sacco.mvp.accounting.dto.AccountVersionDtos.*;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Set;
import java.util.UUID;

@Controller @RequiredArgsConstructor @RequestMapping("/finance/accounts")
@PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_VIEW')")
public class AccountVersionController {
    private final GeneralLedgerService ledger;
    @InitBinder("versionForm") void bindVersion(WebDataBinder b) {b.setAllowedFields("name","nameSw","description","reason","revision");}
    @InitBinder("policyForm") void bindPolicy(WebDataBinder b) {b.setAllowedFields("version","createAutomatic","updateAutomatic","deactivateAutomatic","reactivateAutomatic","reason");}
    @GetMapping("/versions")
    String register(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="") String state,@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="created") String sort,@RequestParam(defaultValue="desc") String direction,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="30") int size,Model m) {
        m.addAttribute("versions",ledger.accountVersionRegister(a,state,search,sort,direction,page,size));m.addAttribute("state",state);m.addAttribute("search",search);m.addAttribute("sort",sort);m.addAttribute("direction",direction);m.addAttribute("size",size);
        return "accounting/account-versions";
    }
    @GetMapping("/{account}/history")
    String history(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID account) {return redirect(ledger.accountCurrent(a,account).id());}
    @PostMapping("/{account}/{operation:deactivate|reactivate}") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')")
    String lifecycleEntry(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID account,@PathVariable String operation) {
        ledger.requireActor(a,"ACCOUNTING_ACCOUNTS_UPDATE");ledger.accountCurrent(a,account);
        return "redirect:/finance/accounts/"+account+"/versions/new?operation="+operation.toUpperCase(java.util.Locale.ROOT);
    }
    @GetMapping("/{account}/versions/new") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')")
    String propose(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID account,@RequestParam(defaultValue="UPDATE") String operation,Model m) {
        if(!Set.of("UPDATE","DEACTIVATE","REACTIVATE").contains(operation))throw new IllegalArgumentException("accounting.error.versionInput");
        var v=ledger.accountCurrent(a,account);m.addAttribute("versionForm",GeneralLedgerService.metadataForm(v.proposed()));newContext(v,operation,UUID.randomUUID(),m);return "accounting/account-version-form";
    }
    @PostMapping("/{account}/versions") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')")
    String create(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID account,@RequestParam UUID baseId,@RequestParam UUID requestKey,
            @RequestParam String operation,@RequestParam(defaultValue="SAVE") String command,@ModelAttribute("versionForm") EditForm form,BindingResult errors,Model m) {
        if(!errors.hasErrors())try {
            requireCommand(command);return redirect(ledger.proposeAccountVersion(a,account,baseId,operation,requestKey,form,command.equals("SAVE")));
        }catch(IllegalArgumentException e){errors.reject(key(e));}catch(org.springframework.dao.DataIntegrityViolationException e){errors.reject("accounting.error.versionOpen");}
        var v=ledger.accountCurrent(a,account);newContext(v,operation,requestKey,m);m.addAttribute("baseId",baseId);return "accounting/account-version-form";
    }
    @GetMapping("/versions/{id}")
    String details(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="0") int historyPage,@RequestParam(defaultValue="0") int eventPage,Model m) {
        m.addAttribute("details",ledger.accountVersionDetails(a,id,historyPage,eventPage));return "accounting/account-version-details";
    }
    @GetMapping("/versions/{id}/edit")
    String edit(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m) {
        var d=ledger.accountVersionDetails(a,id,0,0);if(!d.actions().contains("EDIT"))throw new org.springframework.security.access.AccessDeniedException("Draft editing unavailable");
        var f=GeneralLedgerService.metadataForm(d.version().proposed());f.setReason(d.version().reason());f.setRevision(d.version().revision());m.addAttribute("versionForm",f);editContext(d.version(),m);return "accounting/account-version-form";
    }
    @PostMapping("/versions/{id}/edit")
    String save(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="SAVE") String command,@ModelAttribute("versionForm") EditForm form,BindingResult errors,Model m) {
        if(!errors.hasErrors())try {requireCommand(command);return redirect(ledger.saveAccountVersion(a,id,form,command.equals("SAVE")));}
        catch(IllegalArgumentException e){errors.reject(key(e));}
        editContext(ledger.accountVersionDetails(a,id,0,0).version(),m);return "accounting/account-version-form";
    }
    @PostMapping("/versions/{id}/action")
    String action(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam int revision,@RequestParam String action,@RequestParam String reason,RedirectAttributes flash) {
        try {ledger.accountVersionAction(a,id,revision,action,reason);}catch(IllegalArgumentException e){flash.addFlashAttribute("accountingError",key(e));}
        return redirect(id);
    }
    @GetMapping("/versions/{id}/revise")
    String revise(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m) {
        var v=ledger.accountVersionDetails(a,id,0,0).version();
        if(!v.creator().equals(a.getMemberId()) || !java.util.Objects.equals(v.branch(),a.getStationId()) || !Set.of("REJECTED","WITHDRAWN").contains(v.state()))throw new org.springframework.security.access.AccessDeniedException("Version revision unavailable");
        m.addAttribute("versionForm",GeneralLedgerService.metadataForm(v.proposed()));editContext(v,m);m.addAttribute("requestKey",UUID.randomUUID());m.addAttribute("savePath","/finance/accounts/versions/"+id+"/revise");return "accounting/account-version-form";
    }
    @PostMapping("/versions/{id}/revise")
    String resubmit(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam UUID requestKey,@RequestParam(defaultValue="SAVE") String command,@ModelAttribute("versionForm") EditForm f,BindingResult errors,Model m) {
        if(!errors.hasErrors())try {requireCommand(command);return redirect(ledger.reviseAccountVersion(a,id,requestKey,f,command.equals("SAVE")));}catch(IllegalArgumentException e){errors.reject(key(e));}
        editContext(ledger.accountVersionDetails(a,id,0,0).version(),m);m.addAttribute("requestKey",requestKey);m.addAttribute("savePath","/finance/accounts/versions/"+id+"/revise");return "accounting/account-version-form";
    }
    @GetMapping("/approval-configuration") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNT_RULES_VIEW')")
    String configuration(@AuthenticationPrincipal AppUserPrincipal a,Model m) {
        var p=ledger.accountApprovalPolicy(a);var f=new PolicyForm();f.setVersion(p.version());f.setCreateAutomatic(p.createAutomatic());f.setUpdateAutomatic(p.updateAutomatic());f.setDeactivateAutomatic(p.deactivateAutomatic());f.setReactivateAutomatic(p.reactivateAutomatic());m.addAttribute("policyForm",f);return "accounting/account-approval-configuration";
    }
    @PostMapping("/approval-configuration") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNT_RULES_VIEW') and @access.has(principal, 'ACCOUNTING_ACCOUNT_RULES_CONFIGURE')")
    String configure(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute("policyForm") PolicyForm f,BindingResult errors,Model m,RedirectAttributes flash) {
        if(!errors.hasErrors())try {ledger.configureAccountApproval(a,f);flash.addFlashAttribute("accountingSuccess","coav.policySaved");return "redirect:/finance/accounts/approval-configuration";}
        catch(IllegalArgumentException e){errors.reject(key(e));}
        ledger.accountApprovalPolicy(a);return "accounting/account-approval-configuration";
    }
    private void newContext(Version v,String op,UUID key,Model m) {
        m.addAttribute("account",v.proposed());m.addAttribute("operation",op);m.addAttribute("baseId",v.id());m.addAttribute("requestKey",key);m.addAttribute("savePath","/finance/accounts/"+v.accountId()+"/versions");
    }
    private void editContext(Version v,Model m) {
        m.addAttribute("account",v.proposed());m.addAttribute("operation",v.operation());m.addAttribute("versionId",v.id());m.addAttribute("savePath","/finance/accounts/versions/"+v.id()+"/edit");
    }
    private static void requireCommand(String command){if(!Set.of("SAVE","DRAFT").contains(command))throw new IllegalArgumentException("accounting.error.versionInput");}
    private static String key(IllegalArgumentException e){return e.getMessage()!=null && e.getMessage().startsWith("accounting.error.")?e.getMessage():"accounting.error.versionInput";}
    private static String redirect(UUID id){return "redirect:/finance/accounts/versions/"+id;}
    @ExceptionHandler(IllegalArgumentException.class)
    String invalid(IllegalArgumentException e,RedirectAttributes flash){flash.addFlashAttribute("accountingError",key(e));return "redirect:/finance/accounts/versions";}
}
