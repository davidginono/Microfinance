package com.sacco.mvp.accounting.charges.controller;

import com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.*;
import com.sacco.mvp.accounting.charges.service.LoanChargeService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.util.*;

@Controller @RequiredArgsConstructor @RequestMapping("/finance")
public class LoanChargeController {
    private final LoanChargeService charges;
    private final ApplicationClock clock;
    @InitBinder("chargePolicy") void policyBinder(WebDataBinder b){b.setAllowedFields("requestKey","expectedVersion","productId","effectiveFrom","enabled","managementTiming","insuranceTiming","receivableAccountId","managementAccountId","insuranceAccountId","policyReference","agreementReference","insurer","insurerVerification","allowCustom","customManagementLimit");}
    @GetMapping("/loan-recording/charge-policy") public String policy(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam UUID productId,Model model){
        var prior=charges.policy(a,productId);var f=new PolicyForm();f.setProductId(productId);f.setExpectedVersion(prior.map(Policy::id).orElse(null));f.setEffectiveFrom(prior.map(p->p.effectiveFrom().plusDays(1)).orElse(clock.today()));
        prior.ifPresent(p->{f.setEnabled(p.enabled());f.setManagementTiming(p.managementTiming());f.setInsuranceTiming(p.insuranceTiming());f.setReceivableAccountId(p.receivableAccountId());f.setManagementAccountId(p.managementAccountId());f.setInsuranceAccountId(p.insuranceAccountId());f.setPolicyReference(p.policyReference());f.setAgreementReference(p.agreementReference());f.setInsurer(p.insurer());f.setInsurerVerification(p.insurerVerification());f.setAllowCustom(p.allowCustom());f.setCustomManagementLimit(p.customManagementLimit());});
        model.addAttribute("chargePolicy",f);model.addAttribute("currentChargePolicy",prior.orElse(null));choices(a,f,model);return "accounting/loans/charge-policy";
    }
    @GetMapping("/loan-recording/charge-accounts/{kind}") @ResponseBody public List<com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice> accounts(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable String kind,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return charges.accounts(a,kind,search,page);}
    @PostMapping("/loan-recording/charge-policy") public String savePolicy(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute("chargePolicy") PolicyForm f,org.springframework.validation.BindingResult errors,Model model,RedirectAttributes flash){
        try{if(errors.hasErrors())throw new IllegalArgumentException("charges.error.input");charges.savePolicy(a,f);flash.addFlashAttribute("recordingSaved",true);return "redirect:/finance/loan-recording/charge-policy?productId="+f.getProductId();}
        catch(IllegalArgumentException ex){model.addAttribute("chargeError",key(ex));model.addAttribute("currentChargePolicy",charges.policy(a,f.getProductId()).orElse(null));choices(a,f,model);return "accounting/loans/charge-policy";}
    }
    @PostMapping("/loan-records/{id}/charges/assess") public String assess(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam UUID requestKey,@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam String evidence,@RequestParam(defaultValue="") String reason,RedirectAttributes flash){var f=new AssessmentForm();f.setRequestKey(requestKey);f.setDate(date);f.setEvidence(evidence);f.setReason(reason);try{charges.assess(a,id,f);flash.addFlashAttribute("recordingSaved",true);}catch(IllegalArgumentException ex){flash.addFlashAttribute("chargeError",key(ex));flash.addFlashAttribute("assessmentForm",f);}return "redirect:/finance/loan-records/"+id;}
    @PostMapping("/loan-records/{id}/charges/reverse") public String reverse(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam UUID journalId,@RequestParam UUID requestKey,@RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam String evidence,@RequestParam String reason,RedirectAttributes flash){try{charges.reverseAssessment(a,id,journalId,requestKey,date,evidence,reason);flash.addFlashAttribute("recordingSaved",true);}catch(IllegalArgumentException ex){flash.addFlashAttribute("chargeError",key(ex));var f=new AssessmentForm();f.setRequestKey(requestKey);f.setDate(date);f.setEvidence(evidence);f.setReason(reason);flash.addFlashAttribute("chargeReverseForm",f);}return "redirect:/finance/loan-records/"+id;}
    private void choices(AppUserPrincipal a,PolicyForm f,Model model){
        model.addAttribute("selectedChargeProduct",charges.productChoice(a,f.getProductId()));
        var choices=new LinkedHashMap<String,List<com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice>>();
        for(var kind:List.of("RECEIVABLE","MANAGEMENT","INSURANCE")){
            var values=new ArrayList<>(charges.accounts(a,kind,"",0).stream().limit(25).toList());var id=switch(kind){case "RECEIVABLE"->f.getReceivableAccountId();case "MANAGEMENT"->f.getManagementAccountId();default->f.getInsuranceAccountId();};
            if(id!=null && values.stream().noneMatch(v->v.id().equals(id))){var selected=charges.selectedAccount(a,id,kind);if(selected!=null)values.add(selected);}choices.put(kind,values);
        }
        model.addAttribute("chargeAccounts",choices);
    }
    private String key(IllegalArgumentException ex){return ex.getMessage()!=null && (ex.getMessage().startsWith("charges.error.") || ex.getMessage().startsWith("voucher.error."))?ex.getMessage():"charges.error.input";}
}
