package com.sacco.mvp.web;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.*;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Controller @RequiredArgsConstructor
public class LoanPrepaymentController {
    private final LoanRepaymentLedgerService ledger;
    private final AdminService admin;
    private final AdminScopeService scope;
    @Data public static class PolicyForm {
        private LoanPrepaymentTerms.Schedule schedule;
        private LoanPrepaymentTerms.PartialInterest partialInterest;
        private LoanPrepaymentTerms.DayBasis dayBasis;
        private String reason;
    }
    @Data public static class WaiverForm {
        private UUID requestKey;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate effectiveDate;
        private BigDecimal amount;
        private String reason;
        private UUID reverses;
        private boolean confirmed;
    }
    @InitBinder("policyForm") void policyBinder(WebDataBinder b){b.setAllowedFields("schedule","partialInterest","dayBasis","reason");}
    @InitBinder("waiverForm") void waiverBinder(WebDataBinder b){b.setAllowedFields("requestKey","effectiveDate","amount","reason","reverses","confirmed");}
    @GetMapping("/admin/settings-controls/loan-products/{id}/prepayment")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal,'LOAN_PRODUCTS_VIEW')")
    public String policy(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,Model model){
        var product=admin.loanProduct(scope.currentSaccoId(actor),id);model.addAttribute("product",product);
        if(!model.containsAttribute("policyForm")){var f=new PolicyForm();var terms=LoanPrepaymentTerms.product(product);f.setSchedule(terms.schedule());f.setPartialInterest(terms.partialInterest());f.setDayBasis(terms.dayBasis());model.addAttribute("policyForm",f);}
        return "admin/loan-prepayment";
    }
    @PostMapping("/admin/settings-controls/loan-products/{id}/prepayment")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal,'LOAN_PRODUCTS_CONFIGURE')")
    public String savePolicy(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,@ModelAttribute("policyForm") PolicyForm f,
                             org.springframework.validation.BindingResult errors,Model model,RedirectAttributes redirect){
        if(!errors.hasErrors())try{admin.configurePrepayment(scope.currentSaccoId(actor),actor,id,new LoanPrepaymentTerms(f.getSchedule(),f.getPartialInterest(),f.getDayBasis()),f.getReason());redirect.addFlashAttribute("prepaymentSaved",true);return "redirect:/admin/settings-controls/loan-products/"+id+"/prepayment";}catch(IllegalArgumentException e){model.addAttribute("prepaymentError",error(e));}
        else model.addAttribute("prepaymentError","prepayment.error.input");
        return policy(id,actor,model);
    }
    @GetMapping({"/finance/loan-records/{id}/settlement","/repayments/loans/{id}/settlement"})
    @PreAuthorize("@access.hasAny(principal,'LOAN_RECORDING_VIEW','LOAN_REPAYMENTS_VIEW')")
    public String retiredSettlement(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,jakarta.servlet.http.HttpServletRequest request){
        if(!actor.isStaffSession())throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        return "redirect:"+loanDetails(id,request);
    }
    @PostMapping({"/repayments/loans/{id}/interest-waivers","/finance/loan-records/{id}/interest-waivers"})
    @PreAuthorize("@access.has(principal,'LOAN_INTEREST_WAIVE')")
    public String waiver(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,@ModelAttribute("waiverForm") WaiverForm f,
        org.springframework.validation.BindingResult errors,Model model,RedirectAttributes redirect,jakarta.servlet.http.HttpServletRequest request){
        if(!f.isConfirmed())model.addAttribute("prepaymentError","prepayment.error.confirmation");
        else if(!errors.hasErrors())try{ledger.waiveInterest(id,actor,f.getRequestKey(),f.getEffectiveDate(),f.getAmount(),f.getReason(),f.getReverses());redirect.addFlashAttribute("prepaymentSaved",true);return "redirect:"+loanDetails(id,request)+"#interest-waivers";}
        catch(IllegalArgumentException e){model.addAttribute("prepaymentError",error(e));}
        catch(org.springframework.dao.DataAccessException e){model.addAttribute("prepaymentError","prepayment.error.conflict");}
        else model.addAttribute("prepaymentError","prepayment.error.input");
        redirect.addFlashAttribute("prepaymentError",model.getAttribute("prepaymentError"));
        redirect.addFlashAttribute("waiverForm",f);
        redirect.addFlashAttribute(org.springframework.validation.BindingResult.MODEL_KEY_PREFIX+"waiverForm",errors);
        return "redirect:"+loanDetails(id,request)+(f.getReverses()==null?"":"?waiverId="+f.getReverses())+"#interest-waivers";
    }
    @PostMapping({"/repayments/loans/{id}/prepayment-terms","/finance/loan-records/{id}/prepayment-terms"})
    @PreAuthorize("@access.has(principal,'LOAN_INTEREST_WAIVE')")
    public String adopt(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String reason,@RequestParam(defaultValue="false") boolean confirmed,RedirectAttributes redirect,jakarta.servlet.http.HttpServletRequest request){
        try{if(!confirmed)throw new IllegalArgumentException("prepayment.error.confirmation");ledger.adoptPrepaymentTerms(id,actor,reason);redirect.addFlashAttribute("prepaymentSaved",true);}catch(IllegalArgumentException e){redirect.addFlashAttribute("prepaymentError",error(e));}
        return "redirect:"+loanDetails(id,request)+"#interest-waivers";
    }
    private static String loanDetails(UUID id,jakarta.servlet.http.HttpServletRequest request){
        return request.getRequestURI().startsWith(request.getContextPath()+"/finance/")?"/finance/loan-records/"+id:"/repayments/loans/"+id;
    }
    private static String error(IllegalArgumentException e){String s=e.getMessage();if("voucher.error.period".equals(s))return s;return s!=null && s.matches("(?:prepayment|repayment)\\.error\\.[A-Za-z]+")?s:"prepayment.error.input";}
}
