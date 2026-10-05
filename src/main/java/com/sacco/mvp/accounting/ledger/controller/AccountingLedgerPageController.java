package com.sacco.mvp.accounting.ledger.controller;

import com.sacco.mvp.accounting.ledger.dto.LedgerDtos.*;
import com.sacco.mvp.accounting.ledger.service.AccountingLedgerService;
import com.sacco.mvp.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/accounting")
public class AccountingLedgerPageController {
    private final AccountingLedgerService ledger;

    @GetMapping("/accounts")
    @PreAuthorize("@access.has(principal, 'ACCOUNTING_VIEW')")
    public String accounts(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0") int page,Model model) {
        model.addAttribute("rows",ledger.accounts(actor,page)); model.addAttribute("kinds",AccountKind.values());
        model.addAttribute("normalBalances",NormalBalance.values());model.addAttribute("usages",AccountUsage.values());model.addAttribute("categories",AccountCategory.values());
        return "accounting/accounts";
    }
    @PostMapping("/accounts")
    @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_MANAGE')")
    public String account(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String code,@RequestParam String name,
                          @RequestParam AccountKind kind,@RequestParam NormalBalance normalBalance,@RequestParam AccountUsage usage,
                          @RequestParam AccountCategory category,@RequestParam(required=false) String parentCode) {
        ledger.createAccountWithParentCode(actor,new AccountCommand(code,name,kind,normalBalance,null,usage,category),parentCode);return "redirect:/accounting/accounts";
    }
    @PostMapping("/accounts/{id}/deactivate")
    @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_MANAGE')")
    public String deactivate(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id) {ledger.deactivateAccount(actor,id);return "redirect:/accounting/accounts";}

    @GetMapping("/journals")
    @PreAuthorize("@access.has(principal, 'ACCOUNTING_VIEW')")
    public String journals(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0") int page,Model model) {
        model.addAttribute("rows",ledger.journals(actor,page));model.addAttribute("requestKey",UUID.randomUUID());return "accounting/journals";
    }
    @GetMapping("/journals/{id}")
    @PreAuthorize("@access.has(principal, 'ACCOUNTING_VIEW')")
    public String journal(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,Model model) {
        model.addAttribute("journal",ledger.view(actor,id));model.addAttribute("requestKey",UUID.randomUUID());return "accounting/journal";
    }
    @PostMapping("/journals")
    @PreAuthorize("@access.has(principal, 'ACCOUNTING_JOURNAL_DRAFT')")
    public String draft(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID requestKey,
                        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate effectiveDate,
                        @RequestParam String description,@RequestParam String evidenceReference,@RequestParam String lines) {
        UUID id=ledger.draft(actor,new JournalCommand(requestKey,effectiveDate,description,evidenceReference,ledger.parseLines(lines)));
        return "redirect:/accounting/journals/"+id;
    }
    @PostMapping("/journals/{id}/approve")
    @PreAuthorize("@access.has(principal, 'ACCOUNTING_JOURNAL_APPROVE')")
    public String approve(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String evidenceReference) {
        ledger.approve(actor,id,evidenceReference);return "redirect:/accounting/journals/"+id;
    }
    @PostMapping("/journals/{id}/post")
    @PreAuthorize("@access.has(principal, 'ACCOUNTING_JOURNAL_POST')")
    public String post(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id) {ledger.post(actor,id);return "redirect:/accounting/journals/"+id;}
    @PostMapping("/journals/{id}/reverse")
    @PreAuthorize("@access.has(principal, 'ACCOUNTING_JOURNAL_REVERSE')")
    public String reverse(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam UUID requestKey,
                          @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate effectiveDate,
                          @RequestParam String reason,@RequestParam String evidenceReference) {
        UUID reversal=ledger.requestReversal(actor,id,requestKey,effectiveDate,reason,evidenceReference);return "redirect:/accounting/journals/"+reversal;
    }
    @PostMapping("/periods")
    @PreAuthorize("@access.has(principal, 'ACCOUNTING_PERIOD_MANAGE')")
    public String period(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startsOn,
                         @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endsOn,@RequestParam String evidenceReference,
                         @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate openingCutoff) {
        if(openingCutoff==null) ledger.openPeriod(actor,startsOn,endsOn,evidenceReference);
        else ledger.openInitialPeriod(actor,startsOn,endsOn,openingCutoff,evidenceReference);
        return "redirect:/accounting/journals";
    }
    @GetMapping("/openings")
    @PreAuthorize("@access.has(principal, 'ACCOUNTING_VIEW')")
    public String openings(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0") int page,Model model) {
        model.addAttribute("rows",ledger.openings(actor,page));model.addAttribute("coverage",ledger.coverage(actor));model.addAttribute("requestKey",UUID.randomUUID());model.addAttribute("zeroRequestKey",UUID.randomUUID());model.addAttribute("bridgeRequestKey",UUID.randomUUID());return "accounting/openings";
    }
    @PostMapping("/openings")
    @PreAuthorize("@access.has(principal, 'ACCOUNTING_OPENING_MANAGE')")
    public String opening(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID requestKey,
                          @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate cutoff,@RequestParam String evidenceReference,@RequestParam String lines) {
        ledger.previewOpening(actor,new OpeningCommand(requestKey,cutoff,evidenceReference,ledger.parseLines(lines)));return "redirect:/accounting/openings";
    }
    @PostMapping("/openings/zero")
    @PreAuthorize("@access.has(principal, 'ACCOUNTING_OPENING_MANAGE')")
    public String zeroOpening(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID requestKey,
                              @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate cutoff,@RequestParam String evidenceReference) {
        ledger.previewZeroOpening(actor,requestKey,cutoff,evidenceReference);return "redirect:/accounting/openings";
    }
    @PostMapping("/openings/{id}/approve")
    @PreAuthorize("@access.has(principal, 'ACCOUNTING_OPENING_APPROVE')")
    public String openingApprove(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String evidenceReference) {
        ledger.approveOpening(actor,id,evidenceReference);return "redirect:/accounting/openings";
    }
    @PostMapping("/cutover")
    @PreAuthorize("@access.has(principal, 'ACCOUNTING_CUTOVER_APPROVE')")
    public String cutover(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID batchId,@RequestParam String evidenceReference) {
        ledger.approveCutover(actor,batchId,evidenceReference);return "redirect:/accounting/openings";
    }
    @PostMapping("/bridge")
    @PreAuthorize("@access.has(principal, 'ACCOUNTING_JOURNAL_DRAFT')")
    public String bridge(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID voucher,@RequestParam UUID requestKey,
                         @RequestParam String mapping,@RequestParam String evidenceReference) {
        UUID id=ledger.bridgeText(actor,voucher,requestKey,mapping,evidenceReference);return "redirect:/accounting/journals/"+id;
    }
    @ExceptionHandler({IllegalArgumentException.class,DataAccessException.class})
    public String failure(RuntimeException exception,HttpServletRequest request,Model model) {
        String message=exception.getMessage();
        model.addAttribute("ledgerError",exception instanceof DataAccessException?"accounting.ledger.error.database":
            message!=null && (message.startsWith("accounting.ledger.error.")||message.startsWith("policy.error."))?message:"accounting.ledger.error.request");
        // Retain bounded form values for a safe retry; never reflect arbitrary parameter keys or tenant fields.
        Map<String,String> retained=new LinkedHashMap<>();
        for(String key:new String[]{"effectiveDate","description","evidenceReference","lines","cutoff","code","name","reason","mapping","voucher","startsOn","endsOn","kind","normalBalance","usage","category","parentCode"}) {
            String value=request.getParameter(key);if(value!=null && value.length()<=64000) retained.put(key,value);
        }
        model.addAttribute("retained",retained);return "accounting/error";
    }
}
