package com.sacco.mvp.web;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class LoanNotificationOpenController {
    private final AccessControlService access;

    @GetMapping("/loan-notifications/{loanId}/open")
    public String openLoan(@PathVariable UUID loanId, @AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        String suffix = "/loan-applications/" + loanId;
        if (access.canAccessDisbursementArea(principal)) {
            return "redirect:/disbursement" + suffix;
        }
        if (access.canAccessManagerArea(principal)) {
            return "redirect:/manager" + suffix;
        }
        if (access.canAccessLoanOfficerArea(principal)) {
            return "redirect:/loan-officer" + suffix;
        }
        if (access.canAccessAccountantArea(principal)) {
            return "redirect:/accountant" + suffix;
        }
        if (access.canAccessBoardArea(principal)
            || access.canAccessChairpersonArea(principal)
            || access.canAccessCreditCommitteeArea(principal)) {
            return "redirect:/board" + suffix;
        }
        if (access.canAccessMemberArea(principal)) {
            return "redirect:/app" + suffix;
        }
        return "redirect:/login";
    }
}
