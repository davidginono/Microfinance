package com.sacco.mvp.accounting.web;

import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.*;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.sacco.mvp.security.AppUserPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/finance")
public class GeneralLedgerPageController {
    private final GeneralLedgerService ledger;
    /** Compatibility endpoint; the onboarding forms use /accounts/groups and /accounts/posting. */
    @PostMapping("/accounts")
    String createAccount(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String code,@RequestParam String name,@RequestParam String type,@RequestParam String normalBalance,@RequestParam String kind,@RequestParam String purpose,@RequestParam(required=false) UUID parentId,RedirectAttributes flash) {
        ledger.createAccount(actor,new AccountCommand(code,name,type,normalBalance,kind,purpose,parentId));flash.addFlashAttribute("accountingSuccess","accounting.saved");return "redirect:/finance/accounts";
    }
    @ExceptionHandler(IllegalArgumentException.class)
    String error(IllegalArgumentException failure, Model model) {
        String key = failure.getMessage();
        model.addAttribute("accountingError", key != null && key.startsWith("accounting.error.") ? key : "accounting.error.validation");
        return "accounting/error";
    }
}
