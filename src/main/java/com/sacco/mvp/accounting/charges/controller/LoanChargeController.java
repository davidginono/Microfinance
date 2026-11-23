package com.sacco.mvp.accounting.charges.controller;

import com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.*;
import com.sacco.mvp.accounting.charges.service.LoanChargeService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.util.*;

@Controller @RequiredArgsConstructor
public class LoanChargeController {
    private final LoanChargeService charges;
    @InitBinder("chargeAccounts") public void accountsBinder(WebDataBinder b){b.setAllowedFields("requestKey","expectedVersion","receivableAccountId","applicationAccountId","processingAccountId","insuranceAccountId");}
    @GetMapping("/admin/settings-controls/loan-products/{productId}/charge-accounts") public String policy(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID productId){
        charges.policy(a,productId);return "redirect:/admin/settings-controls/loan-products/"+productId+"/edit?tab=accounts";
    }
    @GetMapping("/admin/settings-controls/loan-charge-accounts/{kind}") @ResponseBody public List<com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice> accounts(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable String kind,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return charges.accounts(a,kind,search,page);}
    @PostMapping("/admin/settings-controls/loan-products/{productId}/charge-accounts") public String saveAccounts(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute("chargeAccounts") AccountMappingForm f,org.springframework.validation.BindingResult errors,@PathVariable UUID productId,RedirectAttributes flash){
        f.setProductId(productId);
        try{if(errors.hasErrors())throw new IllegalArgumentException("charges.error.input");charges.saveAccountMappings(a,f);flash.addFlashAttribute("chargeAccountsSaved",true);}
        catch(IllegalArgumentException ex){flash.addFlashAttribute("chargeError",key(ex));flash.addFlashAttribute("chargeAccountDraft",f);}
        return "redirect:/admin/settings-controls/loan-products/"+productId+"/edit?tab=accounts";
    }
    @InitBinder("chargePolicySetup")
    public void policySetupBinder(WebDataBinder binder){
        binder.setAllowedFields("requestKey","expectedVersion","effectiveFrom","policyReference","agreementReference",
            "managementTiming","insuranceTiming","feeRecoveryAccountId","insurer","insurerVerification",
            "customManagementLimit","allowCustom","enabled");
    }

    @PostMapping("/admin/settings-controls/loan-products/{productId}/charge-policy")
    public String savePolicySetup(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID productId,
            @ModelAttribute("chargePolicySetup") PolicyForm form,BindingResult errors,RedirectAttributes flash){
        try{
            if(errors.hasErrors())throw new IllegalArgumentException("charges.error.input");
            charges.revisePolicy(actor,productId,form);
            flash.addFlashAttribute("chargePolicySaved",true);
        }catch(IllegalArgumentException ex){
            flash.addFlashAttribute("chargePolicyError",key(ex));
            flash.addFlashAttribute("chargePolicyDraft",form);
        }
        return "redirect:/admin/settings-controls/loan-products/"+productId+"/edit?tab=pricing";
    }

    @PostMapping("/finance/loan-records/{id}/charges/assess") public String assess(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam UUID requestKey,@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam String evidence,@RequestParam(defaultValue="") String reason,RedirectAttributes flash){var f=new AssessmentForm();f.setRequestKey(requestKey);f.setDate(date);f.setEvidence(evidence);f.setReason(reason);try{charges.assess(a,id,f);flash.addFlashAttribute("recordingSaved",true);}catch(IllegalArgumentException ex){flash.addFlashAttribute("chargeError",key(ex));flash.addFlashAttribute("assessmentForm",f);}return "redirect:/finance/loan-records/"+id;}
    @PostMapping("/finance/loan-records/{id}/charges/reverse") public String reverse(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam UUID journalId,@RequestParam UUID requestKey,@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam String evidence,@RequestParam String reason,RedirectAttributes flash){try{charges.reverseAssessment(a,id,journalId,requestKey,date,evidence,reason);flash.addFlashAttribute("recordingSaved",true);}catch(IllegalArgumentException ex){flash.addFlashAttribute("chargeError",key(ex));var f=new AssessmentForm();f.setRequestKey(requestKey);f.setDate(date);f.setEvidence(evidence);f.setReason(reason);flash.addFlashAttribute("chargeReverseForm",f);}return "redirect:/finance/loan-records/"+id;}
    @InitBinder("recoveryForm")
    public void recoveryBinder(WebDataBinder binder){
        binder.setAllowedFields("requestKey","holdingAccountId","application","processing","evidence");
    }

    @GetMapping("/finance/loan-records/{id}/fee-recovery")
    public String recovery(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,Model model){
        model.addAttribute("recoveryReview",charges.recoveryReview(actor,id).orElse(null));
        model.addAttribute("loanId",id);
        model.addAttribute("recoveryActor",actor.getMemberId());
        if(!model.containsAttribute("recoveryForm"))model.addAttribute("recoveryForm",new RecoveryReviewForm());
        if(actor.getClaims().contains("LOAN_FEE_RECOVERY_SUBMIT")){
            var form=(RecoveryReviewForm)model.getAttribute("recoveryForm");
            var accounts=charges.recoveryAccountChoices(actor,"",0).stream().limit(25).toList();
            model.addAttribute("holdingAccounts",accounts);
            if(form.getHoldingAccountId()!=null && accounts.stream().noneMatch(c->c.id().equals(form.getHoldingAccountId())))
                model.addAttribute("selectedHolding",charges.recoveryHolding(actor,form.getHoldingAccountId()));
        }
        return "accounting/loans/fee-recovery";
    }

    @GetMapping("/finance/loan-records/{id}/fee-recovery/accounts") @ResponseBody
    public List<com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice> recoveryAccounts(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable UUID id,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){
        charges.recoveryReview(actor,id);
        return charges.recoveryAccountChoices(actor,search,page);
    }

    @PostMapping("/finance/loan-records/{id}/fee-recovery")
    public String submitRecovery(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,
            @ModelAttribute("recoveryForm") RecoveryReviewForm form,BindingResult errors,RedirectAttributes flash){
        try{
            if(errors.hasErrors())throw new IllegalArgumentException("charges.error.input");
            charges.submitRecoveryReview(actor,id,form);
        }catch(IllegalArgumentException ex){
            flash.addFlashAttribute("recoveryError",key(ex));flash.addFlashAttribute("recoveryForm",form);
        }catch(org.springframework.dao.DataIntegrityViolationException ex){
            flash.addFlashAttribute("recoveryError","charges.error.stale");flash.addFlashAttribute("recoveryForm",form);
        }
        return "redirect:/finance/loan-records/"+id+"/fee-recovery";
    }

    @PostMapping("/finance/loan-records/{id}/fee-recovery/{review}/approve")
    public String approveRecovery(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@PathVariable UUID review,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam String reason,RedirectAttributes flash){
        try{
            charges.approveRecoveryReview(actor,id,review,date,reason);
        }catch(IllegalArgumentException ex){
            flash.addFlashAttribute("recoveryError",key(ex));flash.addFlashAttribute("recoveryDate",date);flash.addFlashAttribute("recoveryReason",reason);
        }catch(org.springframework.dao.DataIntegrityViolationException ex){
            flash.addFlashAttribute("recoveryError","charges.error.stale");flash.addFlashAttribute("recoveryDate",date);flash.addFlashAttribute("recoveryReason",reason);
        }
        return "redirect:/finance/loan-records/"+id+"/fee-recovery";
    }

    private String key(IllegalArgumentException ex){return ex.getMessage()!=null && (ex.getMessage().startsWith("charges.error.") || ex.getMessage().startsWith("voucher.error.") || ex.getMessage().startsWith("accounting.error."))?ex.getMessage():"charges.error.input";}
}
