package com.sacco.mvp.accounting.loans.controller;

import com.sacco.mvp.accounting.loans.dto.LoanCancellationDtos.*;
import com.sacco.mvp.accounting.loans.service.LoanCancellationService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;

@Controller @RequiredArgsConstructor @RequestMapping("/finance/loan-records")
public class LoanCancellationController {
    private final LoanCancellationService service;
    private final ApplicationClock clock;

    @ModelAttribute void headers(HttpServletResponse response) {response.setHeader("Cache-Control","no-store");}
    @InitBinder("cancellationForm") void bind(WebDataBinder binder) {
        binder.setAllowedFields("requestKey","state","terms","reason","evidence","effectiveDate","confirmed");
    }
    @GetMapping("/{id}/cancel")
    public String form(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,Model model) {
        var loan=service.view(actor,id);var form=new Form();form.setRequestKey(UUID.randomUUID());
        form.setState(loan.status());form.setTerms(loan.terms());if(loan.funded())form.setEffectiveDate(clock.today());
        pageState(actor,loan,model);model.addAttribute("cancellationForm",form);return "accounting/loans/cancel";
    }
    @PostMapping("/{id}/cancel")
    public String cancel(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,
                         @ModelAttribute("cancellationForm") Form form,BindingResult errors,Model model,
                         RedirectAttributes flash,HttpServletResponse response) {
        try {
            if(errors.hasErrors())throw new IllegalArgumentException("cancellation.error.changed");
            var result=service.cancel(actor,id,form);
            if(result.adjustmentId()!=null)return "redirect:/finance/loan-adjustments/"+result.adjustmentId();
            flash.addFlashAttribute("loanCancelled",true);return "redirect:/finance/loan-records/"+id;
        } catch(IllegalArgumentException e) {
            String key=e.getMessage();
            String field=switch(key==null?"":key) {
                case "cancellation.error.reason"->"reason";
                case "cancellation.error.evidence"->"evidence";
                case "cancellation.error.date","adjustment.error.date"->"effectiveDate";
                case "cancellation.error.confirmation"->"confirmed";
                default->"";
            };
            if(!field.isEmpty())errors.rejectValue(field,key);
            else if(!errors.hasErrors())model.addAttribute("cancellationError",key!=null && (key.startsWith("cancellation.error.") || key.startsWith("adjustment.error.")
                || key.startsWith("charges.error.") || key.equals("voucher.error.period"))?key:"cancellation.error.changed");
        } catch(DataAccessException e) {model.addAttribute("cancellationError","cancellation.error.changed");}
        response.setStatus(400);pageState(actor,service.view(actor,id),model);return "accounting/loans/cancel";
    }

    private void pageState(AppUserPrincipal actor,View loan,Model model) {
        model.addAttribute("cancellation",loan);
        model.addAttribute("cancellationAction",service.action(actor,loan));
    }
}
