package com.sacco.mvp.accounting.charges.controller;

import com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.*;
import com.sacco.mvp.accounting.charges.service.LoanChargeService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
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
    @PostMapping("/finance/loan-records/{id}/charges/assess") public String assess(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam UUID requestKey,@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam String evidence,@RequestParam(defaultValue="") String reason,RedirectAttributes flash){var f=new AssessmentForm();f.setRequestKey(requestKey);f.setDate(date);f.setEvidence(evidence);f.setReason(reason);try{charges.assess(a,id,f);flash.addFlashAttribute("recordingSaved",true);}catch(IllegalArgumentException ex){flash.addFlashAttribute("chargeError",key(ex));flash.addFlashAttribute("assessmentForm",f);}return "redirect:/finance/loan-records/"+id;}
    @PostMapping("/finance/loan-records/{id}/charges/reverse") public String reverse(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam UUID journalId,@RequestParam UUID requestKey,@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam String evidence,@RequestParam String reason,RedirectAttributes flash){try{charges.reverseAssessment(a,id,journalId,requestKey,date,evidence,reason);flash.addFlashAttribute("recordingSaved",true);}catch(IllegalArgumentException ex){flash.addFlashAttribute("chargeError",key(ex));var f=new AssessmentForm();f.setRequestKey(requestKey);f.setDate(date);f.setEvidence(evidence);f.setReason(reason);flash.addFlashAttribute("chargeReverseForm",f);}return "redirect:/finance/loan-records/"+id;}
    private String key(IllegalArgumentException ex){return ex.getMessage()!=null && (ex.getMessage().startsWith("charges.error.") || ex.getMessage().startsWith("voucher.error.") || ex.getMessage().startsWith("accounting.error."))?ex.getMessage():"charges.error.input";}
}
