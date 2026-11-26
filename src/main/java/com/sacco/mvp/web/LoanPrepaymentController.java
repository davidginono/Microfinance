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
    private final ApplicationClock clock;
    private final com.sacco.mvp.accounting.loans.service.LoanRecordingService recording;
    @Data public static class WaiverForm {
        private UUID requestKey;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate effectiveDate;
        private Long sequence;
        private List<Selection> selections=new ArrayList<>();
        private String reason;
        private UUID reverses;
        private boolean confirmed;
    }
    @Data public static class Selection {
        private UUID installmentId;
        private boolean selected;
        private BigDecimal amount;
    }

    @InitBinder("waiverForm") void waiverBinder(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(600);
        binder.setAllowedFields("requestKey","effectiveDate","sequence","reason","reverses","confirmed",
            "selections[*].installmentId","selections[*].selected","selections[*].amount");
    }

    @GetMapping({"/finance/loan-records/{id}/interest-waivers","/repayments/loans/{id}/interest-waivers"})
    @PreAuthorize("@access.hasAny(principal,'LOAN_RECORDING_VIEW','LOAN_REPAYMENTS_VIEW')")
    public String page(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,
                       @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
                       @RequestParam(defaultValue="0") int waiverPage,@RequestParam(required=false) UUID waiverId,
                       Model model,jakarta.servlet.http.HttpServletRequest request) {
        if(!model.containsAttribute("waiverForm")){
            var form=new WaiverForm();
            form.setRequestKey(UUID.randomUUID());
            form.setEffectiveDate(date==null?clock.today():date);
            model.addAttribute("waiverForm",form);
        }
        var form=(WaiverForm)model.getAttribute("waiverForm");
        if(form.getEffectiveDate()==null)form.setEffectiveDate(clock.today());
        var schedule=ledger.waiverSchedule(id,actor,form.getEffectiveDate());
        var history=ledger.interestWaivers(id,actor,waiverPage);
        var entered=new LinkedHashMap<UUID,Selection>();
        for(var selection:form.getSelections()){
            if(selection!=null && selection.getInstallmentId()!=null)entered.put(selection.getInstallmentId(),selection);
        }
        model.addAttribute("waiverEntered",entered);
        model.addAttribute("waiverSchedule",schedule);
        model.addAttribute("waiverToday",clock.today());
        model.addAttribute("waiverBase",loanDetails(id,request));
        model.addAttribute("waiverPage",waiverPage);
        var visible=history.stream().limit(25).toList();
        model.addAttribute("waivers",visible);
        model.addAttribute("waiverHasNext",history.size()>25);
        model.addAttribute("selectedWaiver",visible.stream().filter(w->w.id().equals(waiverId)).findFirst().orElse(null));
        return "repayments/interest-waivers";
    }
    @GetMapping({"/finance/loan-records/{id}/settlement","/repayments/loans/{id}/settlement"})
    @PreAuthorize("@access.hasAny(principal,'LOAN_RECORDING_VIEW','LOAN_REPAYMENTS_VIEW')")
    public String retiredSettlement(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,jakarta.servlet.http.HttpServletRequest request){
        if(!actor.isStaffSession())throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        return "redirect:"+loanDetails(id,request);
    }
    @GetMapping("/finance/loan-records/{id}/full-payoff")
    @PreAuthorize("@access.has(principal,'LOAN_RECORDING_VIEW')")
    public String fullPayoff(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue="") String moneySearch,@RequestParam(defaultValue="") String payableSearch,Model model) {
        var today=clock.today();
        model.addAttribute("payoffDate",date==null?today:date);
        model.addAttribute("payoffToday",today);
        model.addAttribute("payoffLatest",today.plusYears(1));
        model.addAttribute("payoffLoanUrl","/finance/loan-records/"+id);
        model.addAttribute("payoffPosting",recording.latestPayoff(actor,id).orElse(null));
        if (!model.containsAttribute("payoffRequestKey")) model.addAttribute("payoffRequestKey",UUID.randomUUID());
        try {
            var quote=ledger.settlementQuote(id,actor,date==null?today:date);
            model.addAttribute("payoff",quote);
            if (quote!=null && quote.monthly() && actor.getClaims().containsAll(Set.of("ACCOUNTING_BUSINESS_CREATE","LOAN_RECORDING_POST"))) {
                if (quote.total().signum()>0) model.addAttribute("payoffMoneyAccounts",recording.payoffAccounts(actor,"PAYOFF_MONEY",moneySearch,0));
                if (quote.refundDue().signum()>0) model.addAttribute("payoffPayableAccounts",recording.payoffAccounts(actor,"PAYOFF_PAYABLE",payableSearch,0));
            }
        } catch(IllegalArgumentException e) {
            if (!model.containsAttribute("payoffError") && model.getAttribute("payoffPosting")==null) model.addAttribute("payoffError",error(e));
        }
        return "repayments/full-payoff";
    }
    @PostMapping("/finance/loan-records/{id}/full-payoff")
    @PreAuthorize("@access.has(principal,'LOAN_RECORDING_POST') and @access.has(principal,'ACCOUNTING_BUSINESS_CREATE')")
    public String postPayoff(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID requestKey,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam long sequence,
            @RequestParam BigDecimal cash,@RequestParam BigDecimal refund,@RequestParam(required=false) UUID moneyAccount,
            @RequestParam(required=false) UUID payableAccount,@RequestParam(defaultValue="") String reference,
            @RequestParam(defaultValue="false") boolean confirmed,RedirectAttributes flash) {
        try {
            if (!confirmed) throw new IllegalArgumentException("prepayment.error.confirmation");
            recording.postMonthlyPayoff(actor,id,requestKey,date,sequence,cash,refund,moneyAccount,payableAccount,reference);
            flash.addFlashAttribute("payoffSaved",true);
        } catch(IllegalArgumentException | org.springframework.dao.DataIntegrityViolationException e) {
            flash.addFlashAttribute("payoffError",payoffError(e));
            flash.addFlashAttribute("payoffRequestKey",requestKey);
            flash.addFlashAttribute("payoffReference",reference);
            flash.addFlashAttribute("selectedPayoffMoney",moneyAccount);
            flash.addFlashAttribute("selectedPayoffPayable",payableAccount);
            flash.addAttribute("date",date);
        }
        return "redirect:/finance/loan-records/"+id+"/full-payoff";
    }
    @PostMapping("/finance/loan-records/{id}/full-payoff/reverse")
    @PreAuthorize("@access.has(principal,'LOAN_RECORDING_REVERSE') and @access.has(principal,'ACCOUNTING_BUSINESS_REVERSE')")
    public String reversePayoff(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID original,
            @RequestParam UUID requestKey,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String reason,RedirectAttributes flash) {
        try {
            recording.reverseMonthlyPayoff(actor,id,original,requestKey,date,reason);
            flash.addFlashAttribute("payoffReversed",true);
        } catch(IllegalArgumentException | org.springframework.dao.DataIntegrityViolationException e) {
            flash.addFlashAttribute("payoffError",payoffError(e));
            flash.addFlashAttribute("payoffRequestKey",requestKey);
        }
        return "redirect:/finance/loan-records/"+id+"/full-payoff";
    }
    private static String payoffError(RuntimeException e) {
        if (e instanceof IllegalArgumentException invalid) {
            var message=invalid.getMessage();
            return message!=null && message.matches("recording\\.error\\.[A-Za-z]+")?message:error(invalid);
        }
        return "prepayment.error.conflict";
    }
    @PostMapping({"/repayments/loans/{id}/interest-waivers","/finance/loan-records/{id}/interest-waivers"})
    @PreAuthorize("@access.has(principal,'LOAN_INTEREST_WAIVE')")
    public String waiver(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,@ModelAttribute("waiverForm") WaiverForm f,
        org.springframework.validation.BindingResult errors,@RequestParam(defaultValue="0") int waiverPage,
        Model model,RedirectAttributes redirect,jakarta.servlet.http.HttpServletRequest request){
        String destination=loanDetails(id,request)+"/interest-waivers";
        if(!f.isConfirmed())model.addAttribute("prepaymentError","prepayment.error.confirmation");
        else if(!errors.hasErrors())try{
            if(f.getReverses()!=null)ledger.reverseInterestWaiver(id,actor,f.getRequestKey(),f.getEffectiveDate(),f.getReason(),f.getReverses());
            else {
                Map<UUID,BigDecimal> selected=new LinkedHashMap<>();
                for(var row:f.getSelections()){
                    if(row==null || !row.isSelected())continue;
                    if(row.getInstallmentId()==null || row.getAmount()==null || selected.putIfAbsent(row.getInstallmentId(),row.getAmount())!=null)
                        throw new IllegalArgumentException("prepayment.error.amount");
                }
                ledger.waiveInstallments(id,actor,f.getRequestKey(),f.getEffectiveDate(),f.getReason(),f.getSequence(),selected);
            }
            redirect.addFlashAttribute("prepaymentSaved",true);
            return "redirect:"+destination;
        } catch(IllegalArgumentException e){model.addAttribute("prepaymentError",error(e));}
          catch(org.springframework.dao.DataAccessException e){model.addAttribute("prepaymentError","prepayment.error.conflict");}
        else model.addAttribute("prepaymentError","prepayment.error.input");
        redirect.addFlashAttribute("prepaymentError",model.getAttribute("prepaymentError"));
        redirect.addFlashAttribute("waiverForm",f);
        if(f.getReverses()!=null){
            redirect.addAttribute("waiverId",f.getReverses());
            if(waiverPage>0)redirect.addAttribute("waiverPage",waiverPage);
            destination+="#waiver-activity";
        }
        return "redirect:"+destination;
    }
    @PostMapping({"/repayments/loans/{id}/prepayment-terms","/finance/loan-records/{id}/prepayment-terms"})
    @PreAuthorize("@access.has(principal,'LOAN_INTEREST_WAIVE')")
    public String adopt(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String reason,@RequestParam(defaultValue="false") boolean confirmed,RedirectAttributes redirect,jakarta.servlet.http.HttpServletRequest request){
        try{if(!confirmed)throw new IllegalArgumentException("prepayment.error.confirmation");ledger.adoptPrepaymentTerms(id,actor,reason);redirect.addFlashAttribute("prepaymentSaved",true);}catch(IllegalArgumentException e){redirect.addFlashAttribute("prepaymentError",error(e));}
        return "redirect:"+loanDetails(id,request)+"/interest-waivers";
    }
    private static String loanDetails(UUID id,jakarta.servlet.http.HttpServletRequest request){
        return request.getRequestURI().startsWith(request.getContextPath()+"/finance/")?"/finance/loan-records/"+id:"/repayments/loans/"+id;
    }
    private static String error(IllegalArgumentException e){String s=e.getMessage();if("voucher.error.period".equals(s)||"recording.error.sharedWaiverHistory".equals(s)||"recording.error.sharedAttributionHistory".equals(s))return s;return s!=null && s.matches("(?:prepayment|repayment)\\.error\\.[A-Za-z]+")?s:"prepayment.error.input";}
}
