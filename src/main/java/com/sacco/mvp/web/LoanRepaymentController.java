package com.sacco.mvp.web;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.LoanRepaymentLedgerService;
import com.sacco.mvp.web.form.LoanRepaymentForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/repayments")
public class LoanRepaymentController {
    private final LoanRepaymentLedgerService ledger;
    private final ApplicationClock clock;

    @GetMapping
    @PreAuthorize("@access.has(principal, 'LOAN_REPAYMENTS_VIEW')")
    public String list(@AuthenticationPrincipal AppUserPrincipal actor, @RequestParam(required = false) String loanNumber,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        try {
            model.addAttribute("loans", ledger.list(actor, loanNumber, page));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("loans", Page.empty());
            model.addAttribute("repaymentError", errorKey(ex));
        }
        model.addAttribute("loanNumber", loanNumber);
        return "repayments/index";
    }

    @GetMapping("/loans/{id}")
    @PreAuthorize("@access.hasAny(principal, 'LOAN_REPAYMENTS_VIEW', 'MEMBER_LOANS_VIEW')")
    public String loan(@PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal actor,
                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "0") int schedulePage, Model model) {
        LoanRepaymentForm form = new LoanRepaymentForm();
        form.setPaymentDate(clock.today());
        form.setRequestKey(UUID.randomUUID());
        model.addAttribute("paymentForm", form);
        populate(id, actor, page, schedulePage, model);
        return "repayments/loan";
    }

    @PostMapping("/loans/{id}/payments")
    @PreAuthorize("@access.has(principal, 'LOAN_REPAYMENTS_CREATE')")
    public String post(@PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal actor,
                       @Valid @ModelAttribute("paymentForm") LoanRepaymentForm form, BindingResult errors, Model model) {
        if (!errors.hasErrors()) {
            try {
                var receipt = ledger.post(id, actor, new LoanRepaymentLedgerService.PaymentCommand(form.getAmount(),
                    form.getPaymentDate(), form.getChannel(), form.getReference(), form.getRequestKey()));
                return receiptRedirect(id, receipt.id());
            } catch (IllegalArgumentException ex) {
                model.addAttribute("repaymentError", errorKey(ex));
            } catch (DataAccessException ex) {
                model.addAttribute("repaymentError", "repayment.error.conflict");
            }
        } else model.addAttribute("repaymentError", "repayment.error.validation");
        populate(id, actor, 0, 0, model);
        return "repayments/loan";
    }

    @PostMapping("/loans/{id}/payments/{paymentId}/reverse")
    @PreAuthorize("@access.has(principal, 'LOAN_REPAYMENTS_REVERSE')")
    public String reverse(@PathVariable UUID id, @PathVariable UUID paymentId,
                          @AuthenticationPrincipal AppUserPrincipal actor, @RequestParam UUID requestKey,
                          @RequestParam String reason, @RequestParam(defaultValue = "false") boolean confirmed, Model model) {
        try {
            if (!confirmed) throw new IllegalArgumentException("repayment.error.validation");
            var receipt = ledger.reverse(id, paymentId, actor, requestKey, reason);
            return receiptRedirect(id, receipt.id());
        } catch (IllegalArgumentException ex) {
            model.addAttribute("repaymentError", errorKey(ex));
        } catch (DataAccessException ex) {
            model.addAttribute("repaymentError", "repayment.error.conflict");
        }
        model.addAttribute("reversalReason", reason);
        model.addAttribute("reversalRequestKey", requestKey);
        return loan(id, actor, 0, 0, model);
    }

    @GetMapping("/loans/{id}/receipts/{receiptId}")
    @PreAuthorize("@access.hasAny(principal, 'LOAN_REPAYMENTS_VIEW', 'MEMBER_LOANS_VIEW')")
    public String receipt(@PathVariable UUID id, @PathVariable UUID receiptId,
                          @AuthenticationPrincipal AppUserPrincipal actor, Model model) {
        model.addAttribute("receipt", ledger.receipt(id, receiptId, actor));
        return "repayments/receipt";
    }

    private void populate(UUID id, AppUserPrincipal actor, int page, int schedulePage, Model model) {
        model.addAttribute("ledger", ledger.view(id, actor, page, schedulePage));
        model.addAttribute("today", clock.today());
        if (!model.containsAttribute("reversalRequestKey")) model.addAttribute("reversalRequestKey", UUID.randomUUID());
    }

    private String errorKey(IllegalArgumentException ex) {
        return ex.getMessage() != null && ex.getMessage().startsWith("repayment.error.")
            ? ex.getMessage() : "repayment.error.validation";
    }

    private String receiptRedirect(UUID loan, UUID receipt) {
        return "redirect:/repayments/loans/" + loan + "/receipts/" + receipt;
    }
}
