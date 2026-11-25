package com.sacco.mvp.web;

import com.sacco.mvp.accounting.loans.controller.LoanRecordingController;
import com.sacco.mvp.security.AppUserPrincipal;
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

/** Locates the waiver workspace from funded loan details. */
@ControllerAdvice(assignableTypes={LoanRecordingController.class,LoanRepaymentController.class})
@RequiredArgsConstructor
public class LoanInterestWaiverAdvice {
    private final LoanRepaymentLedgerService ledger;

    @ModelAttribute
    public void details(HttpServletRequest request,@AuthenticationPrincipal AppUserPrincipal actor,Model model){
        String pattern=String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        if(!"GET".equals(request.getMethod()) || actor==null || !actor.isStaffSession()
            || !(pattern.equals("/finance/loan-records/{id}") || pattern.equals("/repayments/loans/{id}")))return;
        var variables=(java.util.Map<?,?>)request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        UUID id=UUID.fromString(String.valueOf(variables.get("id")));
        if(ledger.hasLedger(id)){
            model.addAttribute("waiverPageLink",(pattern.startsWith("/finance/")?"/finance/loan-records/":"/repayments/loans/")+id+"/interest-waivers");
        }
    }
}
