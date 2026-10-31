package com.sacco.mvp.clients.onboarding.controller;

import com.sacco.mvp.clients.onboarding.dto.LoanIntakeDtos.*;
import com.sacco.mvp.clients.onboarding.service.*;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller @RequiredArgsConstructor
@RequestMapping({"/app/onboarding/loan","/finance/clients/onboarding/{clientId}/loan"})
public class LoanIntakeController {
    private final LoanIntakeService service;
    private final OnboardingService onboarding;
    private final LoanRecordingService recording;
    @InitBinder("loanRequestForm") public void binder(WebDataBinder b) {
        b.setAllowedFields("requestKey","expectedSequence","action","details.productId","details.principal","details.tenureValue","details.purpose","details.business","details.income","details.expenses","details.otherDebt");
    }
    private UUID client(AppUserPrincipal a,UUID path) {
        if(a==null||a.isStaffSession()&&path==null||!a.isStaffSession()&&path!=null)throw new org.springframework.security.access.AccessDeniedException("Client workspace required");
        return path==null?a.getMemberId():path;
    }
    private String root(AppUserPrincipal a,UUID id) {return a.isStaffSession()?"/finance/clients/onboarding/"+id+"/loan":"/app/onboarding/loan";}
    private void context(AppUserPrincipal a,UUID id,Model m) {
        var client=service.authorize(a,id,false);m.addAttribute("loanRoot",root(a,id));m.addAttribute("staffIntake",a.isStaffSession());
        m.addAttribute("intakeClient",client);
    }
    @GetMapping public String details(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable(required=false) UUID clientId,Model m) {
        UUID id=client(a,clientId);context(a,id,m);var r=service.latest(a,id).orElse(null);m.addAttribute("loanRequest",r);
        m.addAttribute("selectedProduct",r==null?null:service.product(a,id,r.details().getProductId()).orElse(null));return "onboarding/loan-request";
    }
    @GetMapping("/edit") public String edit(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable(required=false) UUID clientId,Model m) {
        UUID id=client(a,clientId);service.authorize(a,id,true);m.addAttribute("loanRequestForm",service.form(a,id));return form(a,id,m);
    }
    @PostMapping("/edit") public String save(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable(required=false) UUID clientId,@ModelAttribute("loanRequestForm") Form f,BindingResult errors,Model m,org.springframework.web.servlet.mvc.support.RedirectAttributes flash) {
        UUID id=client(a,clientId);service.authorize(a,id,true);
        if(!errors.hasErrors())try {service.save(a,id,f);flash.addFlashAttribute("loanRequestSaved",true);return "redirect:"+root(a,id);}catch(IllegalArgumentException e){errors.reject(key(e));}
        return form(a,id,m);
    }
    private String form(AppUserPrincipal a,UUID id,Model m) {
        context(a,id,m);var f=(Form)m.getAttribute("loanRequestForm");m.addAttribute("selectedProduct",service.product(a,id,f.getDetails().getProductId()).orElse(null));return "onboarding/loan-request-form";
    }
    @GetMapping("/products") @ResponseBody public ResponseEntity<?> products(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable(required=false) UUID clientId,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.products(a,client(a,clientId),search,page).stream().map(p->new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice(p.id(),p.label(),"",p.unit(),p.minimum()==null?null:p.minimum().toPlainString(),p.maximum()==null?null:p.maximum().toPlainString(),p.minTenure(),p.maxTenure())).toList());
    }
    @GetMapping("/continue") public String continueApplication(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable(required=false) UUID clientId,Model m) {
        UUID id=client(a,clientId);if(!a.isStaffSession())throw new org.springframework.security.access.AccessDeniedException("Staff workspace required");
        recording.authorize(a,"CREATE");var f=service.recordingForm(a,id);m.addAttribute("recordForm",f);
        m.addAttribute("selectedProduct",recording.recordProduct(a,f.getProductId()));m.addAttribute("selectedClientLabel",onboarding.client(a,id).number()+" - "+onboarding.client(a,id).name());return "accounting/loans/record-form";
    }
    private static String key(IllegalArgumentException e){return Objects.toString(e.getMessage(),"").matches("kyc\\.error\\.[A-Za-z]+")?e.getMessage():"kyc.error.invalid";}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid(IllegalArgumentException e,Model m){m.addAttribute("onboardingError",key(e));return "onboarding/error";}
}
