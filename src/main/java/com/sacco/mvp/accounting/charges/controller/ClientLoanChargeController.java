package com.sacco.mvp.accounting.charges.controller;

import com.sacco.mvp.accounting.charges.service.LoanChargeService;
import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;

@Controller @RequiredArgsConstructor
@PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal,'MEMBER_LOANS_VIEW') and @authz.isLoanOwner(#id,principal)")
public class ClientLoanChargeController {
    private final LoanChargeService charges;
    private final LoanRecordingPdfService pdf;
    private final MessageSource messages;
    @GetMapping("/app/loan-applications/{id}/charges")
    public String view(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam(defaultValue="0") int chargePage,Model model){model.addAttribute("chargeSummary",charges.clientSummary(actor,id,chargePage));model.addAttribute("loanId",id);return "accounting/loans/client-charges";}
    @GetMapping({"/app/loan-applications/{id}/charges/pdf","/app/loan-applications/{id}/charges/print"})
    public Object export(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,Locale locale,jakarta.servlet.http.HttpServletRequest request,Model model){
        var summary=charges.clientExport(actor,id);var lines=new ArrayList<String>();
        lines.add(label("loan.loanId",locale)+": "+summary.loanNumber());lines.add(label("charges.notPaid",locale));lines.add(label("charges.policyReference",locale)+": "+summary.plan().policyReference());lines.add(label("charges.agreementReference",locale)+": "+summary.plan().agreementReference());
        for(var t:summary.plan().terms()){String basis=t.basis().equals("PRINCIPAL_RATE")?t.basisValue().movePointRight(2).stripTrailingZeros().toPlainString()+"%":"TZS "+t.basisValue();lines.add(label("charges.kind."+t.kind(),locale)+" | "+label("charges.basis."+t.basis(),locale)+" "+basis+" | TZS "+t.amount()+" | "+label("charges.timing."+t.timing(),locale));if(!t.provider().isBlank())lines.add(label("charges.provider",locale)+": "+t.provider()+" / "+t.verification());if(!t.evidence().isBlank())lines.add(label("charges.supportingEvidence",locale)+": "+t.evidence());}
        lines.add(label("charges.outstanding",locale)+": TZS "+summary.balance().outstanding());
        for(var e:summary.history())lines.add(e.date()+" | "+label("charges.kind."+e.chargeKind(),locale)+" | "+label("charges.event."+e.kind(),locale)+" | TZS "+e.amount()+" | "+e.evidence()+" | "+e.reason());
        var document=new LoanRecordingPdfService.Document(label("charges.title",locale),summary.institution(),summary.branch(),"loan-charges.pdf",lines);
        if(request.getRequestURI().endsWith("/print")){model.addAttribute("document",document);return "accounting/loans/print";}
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=loan-charges.pdf").header("X-Content-Type-Options","nosniff").body(pdf.render(document));
    }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalid(IllegalArgumentException failure,Model model){model.addAttribute("recordingError",failure.getMessage()!=null && failure.getMessage().startsWith("charges.error.")?failure.getMessage():"charges.error.input");return "accounting/loans/error";}
    private String label(String key,Locale locale){return messages.getMessage(key,null,locale);}
}
