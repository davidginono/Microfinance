package com.sacco.mvp.web;

import com.sacco.mvp.accounting.loans.controller.LoanRecordingController;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.LoanRepaymentLedgerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;
import java.util.UUID;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** Bounded, scoped waiver data for the two existing loan detail pages only. */
@ControllerAdvice(assignableTypes={LoanRecordingController.class,LoanRepaymentController.class})
@RequiredArgsConstructor
public class LoanInterestWaiverAdvice {
    private final LoanRepaymentLedgerService ledger;
    private final ApplicationClock clock;

    @ModelAttribute
    public void details(HttpServletRequest request,@AuthenticationPrincipal AppUserPrincipal actor,Model model){
        String pattern=String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        if(!"GET".equals(request.getMethod()) || actor==null || !actor.isStaffSession()
            || !(pattern.equals("/finance/loan-records/{id}") || pattern.equals("/repayments/loans/{id}")))return;
        var variables=(java.util.Map<?,?>)request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        UUID id=UUID.fromString(String.valueOf(variables.get("id")));int page=0;UUID selected=null;
        try{
            if(request.getParameter("waiverPage")!=null)page=Integer.parseInt(request.getParameter("waiverPage"));
            if(page<0 || page>10000)throw new IllegalArgumentException();
            if(request.getParameter("waiverId")!=null)selected=UUID.fromString(request.getParameter("waiverId"));
        }catch(IllegalArgumentException e){throw new ResponseStatusException(BAD_REQUEST);}
        if(!ledger.hasLedger(id))return;
        var history=ledger.interestWaivers(id,actor,page);
        final UUID selection=selected;
        model.addAttribute("waiversLoaded",true);model.addAttribute("waiverLoanId",id);
        model.addAttribute("waiverBase",pattern.startsWith("/finance/")?"/finance/loan-records/"+id:"/repayments/loans/"+id);
        model.addAttribute("waivers",history.stream().limit(25).toList());model.addAttribute("waiverHasNext",history.size()>25);
        model.addAttribute("waiverPage",page);model.addAttribute("waiverToday",clock.today());
        model.addAttribute("selectedWaiver",history.stream().filter(w->w.id().equals(selection)).findFirst().orElse(null));
        if(actor.getClaims().contains(com.sacco.mvp.domain.UserClaim.LOAN_INTEREST_WAIVE.name())){
            try{model.addAttribute("waiverQuote",ledger.settlementQuote(id,actor,clock.today()));}
            catch(IllegalArgumentException e){if(!"repayment.error.state".equals(e.getMessage()))throw e;}
        }
        if(!model.containsAttribute("waiverForm")){
            var form=new LoanPrepaymentController.WaiverForm();form.setRequestKey(UUID.randomUUID());form.setEffectiveDate(clock.today());model.addAttribute("waiverForm",form);
        }
    }
}
