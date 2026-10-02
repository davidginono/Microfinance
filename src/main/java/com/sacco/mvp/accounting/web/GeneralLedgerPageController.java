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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequiredArgsConstructor
@RequestMapping("/finance")
public class GeneralLedgerPageController {
    private final GeneralLedgerService ledger;
    @GetMapping("/accounts")
    String accounts(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0") int page,Model model) {
        model.addAttribute("accounts",ledger.accounts(actor,page));
        model.addAttribute("accountTypes",List.of("ASSET","LIABILITY","EQUITY","INCOME","EXPENSE"));
        model.addAttribute("accountKinds",List.of("HEADING","POSTING","CONTROL"));
        model.addAttribute("accountPurposes",List.of("CASH","BANK","MOBILE_MONEY","CLEARING","SUSPENSE","LOAN_PRINCIPAL","INTEREST_RECEIVABLE","FEE_RECEIVABLE","ALLOWANCE","PAYABLE","FUNDING","CAPITAL","INCOME","EXPENSE","FIXED_ASSET","PREPAYMENT","TAX","INTERNAL_TRANSFER","OTHER"));
        return "accounting/accounts";
    }
    @PostMapping("/accounts")
    String createAccount(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String code,@RequestParam String name,@RequestParam String type,@RequestParam String normalBalance,@RequestParam String kind,@RequestParam String purpose,@RequestParam(required=false) UUID parentId,RedirectAttributes flash) {
        ledger.createAccount(actor,new AccountCommand(code,name,type,normalBalance,kind,purpose,parentId));flash.addFlashAttribute("accountingSuccess","accounting.saved");return "redirect:/finance/accounts";
    }
    @PostMapping("/accounts/{id}/deactivate")
    String deactivate(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id) {ledger.deactivateAccount(actor,id);return "redirect:/finance/accounts";}
    @PostMapping("/periods")
    String period(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam LocalDate start,@RequestParam LocalDate end,RedirectAttributes flash) {
        ledger.createPeriod(actor,start,end);flash.addFlashAttribute("accountingSuccess","accounting.saved");return "redirect:/finance/accounts";
    }
    @GetMapping("/journals")
    String journals(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0") int page,Model model) {
        model.addAttribute("journals",ledger.journals(actor,page));model.addAttribute("coverage",ledger.coverage(actor));model.addAttribute("requestKey",UUID.randomUUID());return "accounting/journals";
    }
    @PostMapping("/journals/import")
    String imported(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID requestKey,@RequestParam String sourceReference,@RequestParam LocalDate effectiveDate,@RequestParam String evidenceReference,@RequestParam(required=false) String reason,@RequestParam String content,@RequestParam(defaultValue="false") boolean opening,@RequestParam String action,Model model) {
        var lines=ledger.previewImport(actor,content,opening);
        if("preview".equals(action)) {
            model.addAttribute("preview",lines);model.addAttribute("content",content);model.addAttribute("sourceReference",sourceReference);model.addAttribute("effectiveDate",effectiveDate);model.addAttribute("evidenceReference",evidenceReference);model.addAttribute("reason",reason);model.addAttribute("opening",opening);
            journals(actor,0,model);model.addAttribute("requestKey",requestKey);return "accounting/journals";
        }
        if(!"save".equals(action)) throw new IllegalArgumentException("accounting.error.command");
        JournalCommand c=new JournalCommand(requestKey,sourceReference,effectiveDate,evidenceReference,reason,lines);
        Journal j=opening?ledger.importOpening(actor,c):ledger.draftManual(actor,c);
        return "redirect:/finance/journals/"+j.id();
    }
    @GetMapping("/journals/{id}")
    String journal(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,Model model) {
        model.addAttribute("journal",ledger.journal(actor,id));model.addAttribute("lines",ledger.displayLines(actor,id));model.addAttribute("requestKey",UUID.randomUUID());return "accounting/journal";
    }
    @PostMapping("/journals/{id}/approve")
    String approve(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String evidence) {ledger.approve(actor,id,evidence);return "redirect:/finance/journals/"+id;}
    @PostMapping("/journals/{id}/post")
    String post(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam(defaultValue="false") boolean openingReconciled,@RequestParam boolean confirmed) {
        if(!confirmed)throw new IllegalArgumentException("accounting.error.confirmation");ledger.post(actor,id,openingReconciled);return "redirect:/finance/journals/"+id;
    }
    @PostMapping("/journals/{id}/reverse")
    String reverse(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam UUID requestKey,@RequestParam LocalDate effectiveDate,@RequestParam String reason,@RequestParam String evidence,@RequestParam boolean confirmed) {
        if(!confirmed)throw new IllegalArgumentException("accounting.error.confirmation");return "redirect:/finance/journals/"+ledger.reverse(actor,id,requestKey,effectiveDate,reason,evidence).id();
    }
    @PostMapping("/journals/bridge")
    String bridge(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID voucher,@RequestParam UUID requestKey,@RequestParam String evidence) {return "redirect:/finance/journals/"+ledger.bridgeOperationalVoucher(actor,voucher,requestKey,evidence).id();}
    @ExceptionHandler(IllegalArgumentException.class)
    String error(IllegalArgumentException failure,Model model,HttpServletRequest request,@AuthenticationPrincipal AppUserPrincipal actor) {
        String key=failure.getMessage();model.addAttribute("accountingError",key!=null && key.startsWith("accounting.error.")?key:"accounting.error.validation");
        if(request.getRequestURI().endsWith("/journals/import")) {
            for(String field:List.of("requestKey","sourceReference","effectiveDate","evidenceReference","reason","content"))model.addAttribute(field,request.getParameter(field));
            model.addAttribute("opening","true".equals(request.getParameter("opening")));
            String requestKey=request.getParameter("requestKey");journals(actor,0,model);model.addAttribute("requestKey",requestKey);return "accounting/journals";
        }
        if(request.getRequestURI().matches(".*/journals/[0-9a-fA-F-]{36}/(approve|post|reverse)")) {
            String[] path=request.getRequestURI().split("/");journal(actor,UUID.fromString(path[path.length-2]),model);return "accounting/journal";
        }
        return "accounting/error";
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    String conflict(Model model) {model.addAttribute("accountingError","accounting.error.duplicate");return "accounting/error";}
}
