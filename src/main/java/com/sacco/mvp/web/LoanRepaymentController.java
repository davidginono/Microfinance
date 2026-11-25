package com.sacco.mvp.web;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.LoanRepaymentLedgerService;
import com.sacco.mvp.service.LoanRepaymentReceiptPdfService;
import org.springframework.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.Locale;

@Controller
@RequiredArgsConstructor
@RequestMapping("/repayments")
public class LoanRepaymentController {
    private final LoanRepaymentLedgerService ledger;
    private final ApplicationClock clock;
    private final LoanRepaymentReceiptPdfService receiptPdf;

    @GetMapping("/loans/{id}")
    @PreAuthorize("@access.has(principal, 'LOAN_REPAYMENTS_VIEW')")
    public String loan(@PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal actor,
                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "0") int schedulePage, Model model) {
        populate(id, actor, page, schedulePage, model);
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
    @PreAuthorize("@access.has(principal, 'LOAN_REPAYMENTS_VIEW')")
    public String receipt(@PathVariable UUID id, @PathVariable UUID receiptId,
                          @AuthenticationPrincipal AppUserPrincipal actor, Model model) {
        model.addAttribute("receipt", ledger.receipt(id, receiptId, actor));
        return "repayments/receipt";
    }

    @GetMapping({"/loans/{id}/receipts/{receiptId}/pdf","/loans/{id}/receipts/{receiptId}/print"})
    @PreAuthorize("@access.has(principal, 'LOAN_REPAYMENTS_VIEW')")
    public ResponseEntity<byte[]> receiptExport(@PathVariable UUID id,@PathVariable UUID receiptId,
        @AuthenticationPrincipal AppUserPrincipal actor,Locale locale,jakarta.servlet.http.HttpServletRequest request){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,(request.getRequestURI().endsWith("/print")?"inline":"attachment")+"; filename=receipt-"+receiptId+".pdf")
            .header("X-Content-Type-Options","nosniff").body(receiptPdf.render(id,receiptId,actor,locale));
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
