package com.sacco.mvp.web;

import com.sacco.mvp.domain.Position;
import com.sacco.mvp.security.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@Controller
public class LoanNotificationOpenController {
    private static final String DISBURSEMENT_ACCESS_CLAIM = "ACCESS_DISBURSEMENT_QUEUE";
    private static final String DISBURSE_LOAN_CLAIM = "DISBURSE_LOAN";

    @GetMapping("/loan-notifications/{loanId}/open")
    public String openLoan(@PathVariable UUID loanId, @AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        String suffix = "/loan-applications/" + loanId;
        if (principal.getClaims().contains(DISBURSEMENT_ACCESS_CLAIM) || principal.getClaims().contains(DISBURSE_LOAN_CLAIM)) {
            return "redirect:/disbursement" + suffix;
        }
        if (principal.hasRole(Position.MANAGER)) {
            return "redirect:/manager" + suffix;
        }
        if (principal.hasRole(Position.LOAN_OFFICER)) {
            return "redirect:/loan-officer" + suffix;
        }
        if (principal.hasRole(Position.ACCOUNTANT)) {
            return "redirect:/accountant" + suffix;
        }
        if (principal.hasRole(Position.BOARD) || principal.hasRole(Position.CHAIRPERSON) || principal.hasRole(Position.CREDIT_COMMITTEE)) {
            return "redirect:/board" + suffix;
        }
        if (principal.hasRole(Position.MEMBER)) {
            return "redirect:/app" + suffix;
        }
        return "redirect:/login";
    }
}
