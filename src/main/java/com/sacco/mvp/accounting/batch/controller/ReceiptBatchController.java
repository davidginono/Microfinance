package com.sacco.mvp.accounting.batch.controller;

import com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.*;
import com.sacco.mvp.accounting.batch.service.*;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.repayment.dto.RepaymentPolicyDtos.*;
import com.sacco.mvp.accounting.repayment.service.RepaymentPolicyService;
import com.sacco.mvp.accounting.service.LoanReceiptService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller @RequiredArgsConstructor @RequestMapping("/finance/batches/receipts")
@PreAuthorize("@access.has(principal,'ACCOUNTING_BUSINESS_VIEW') and @access.has(principal,'LOAN_RECORDING_VIEW')")
public class ReceiptBatchController {
    private final ReceiptBatchService batches;
    private final LoanReceiptService receipts;
    private final LoanRecordingService loans;
    private final RepaymentPolicyService policies;
    private final ApplicationClock clock;
    private final MessageSource messages;
    @ModelAttribute void cache(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping public String register(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="0")int page,Model m){m.addAttribute("batches",batches.list(a,search,page));m.addAttribute("search",search);return "accounting/batches/register";}
    @GetMapping("/new") public String createForm(@AuthenticationPrincipal AppUserPrincipal a){batches.authorize(a,true);return "accounting/batches/new";}
    @PostMapping("/new") public String create(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam String title){return "redirect:/finance/batches/receipts/"+batches.create(a,title);}
    @GetMapping("/{id}") public String editor(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m){m.addAttribute("summary",batches.get(a,id));m.addAttribute("payrollLinked",batches.payrollLinked(a,id));m.addAttribute("today",clock.today());return "accounting/batches/editor";}
    @GetMapping("/{id}/summary") @ResponseBody public Summary summary(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id){return batches.get(a,id);}
    @GetMapping("/{id}/items") @ResponseBody public Page<Item> items(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="")String state,Locale locale){var result=batches.items(a,id,page,state);return new Page<>(result.rows().stream().map(i->i.withFailureMessage(i.failureKey()==null?"":messages.getMessage(i.failureKey(),null,messages.getMessage("batch.error.failed",null,locale),locale))).toList(),result.page(),result.hasNext());}
    @PutMapping("/{id}/items/{item}") @ResponseBody public Item save(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable UUID item,@RequestBody SaveItem form){return batches.save(a,id,item,form);}
    @DeleteMapping("/{id}/items/{item}") @ResponseBody public Map<String,Boolean> delete(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable UUID item,@RequestParam int version){batches.delete(a,id,item,version);return Map.of("deleted",true);}
    @PostMapping("/{id}/items/{item}/estimate") @ResponseBody public Plan estimate(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable UUID item){return batches.estimate(a,id,item);}
    @PostMapping("/{id}/validate") @ResponseBody public Summary validate(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam int version){batches.start(a,id,version,false);return batches.get(a,id);}
    @PostMapping("/{id}/post") @ResponseBody public Summary post(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam int version){batches.start(a,id,version,true);return batches.get(a,id);}
    @PostMapping("/{id}/stop") @ResponseBody public Summary stop(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id){batches.cancel(a,id);return batches.get(a,id);}
    @GetMapping("/choices/{category}") @ResponseBody public List<Choice> choices(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable String category,@RequestParam(required=false)UUID clientId,@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="0")int page){batches.authorize(a,true);return switch(category){case "clients","loans","interest"->receipts.choices(a,category,clientId,search,page);case "money"->loans.choices(a,"money",search,page);default->throw new IllegalArgumentException("batch.error.input");};}
    @GetMapping("/loan-context") @ResponseBody public List<Balances> loanContext(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam UUID loanId,@RequestParam java.time.LocalDate date){batches.authorize(a,true);return loans.receiptLoans(a,List.of(loanId),date).stream().map(l->new Balances(l.id(),l.number(),l.outstandingPrincipal(),l.duePrincipal(),l.dueInterest())).toList();}
    @GetMapping("/effective-policy") @ResponseBody public Policy effectivePolicy(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam java.time.LocalDate date){batches.authorize(a,true);return policies.effective(a,date,false);}
    @GetMapping("/settings") @PreAuthorize("@access.has(principal,'ACCOUNTING_ACCOUNTS_VIEW')")
    public String settings(@AuthenticationPrincipal AppUserPrincipal a,Model m){m.addAttribute("policy",policies.latest(a).orElse(null));m.addAttribute("requestKey",UUID.randomUUID());m.addAttribute("today",clock.today());return "accounting/batches/settings";}
    @GetMapping("/settings/accounts") @ResponseBody @PreAuthorize("@access.has(principal,'ACCOUNTING_ACCOUNTS_VIEW')")
    public List<Choice> settingAccounts(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="0")int page){return policies.accounts(a,search,page);}
    @PostMapping("/settings") @PreAuthorize("@access.has(principal,'ACCOUNTING_ACCOUNTS_UPDATE')")
    public String saveSettings(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute("policyForm") PolicyForm form,Model m){try{policies.save(a,form);return "redirect:/finance/batches/receipts/settings?saved=true";}catch(IllegalArgumentException failure){settings(a,m);m.addAttribute("policyForm",form);m.addAttribute("errorKey",ReceiptBatchWorkerService.safeError(failure));return "accounting/batches/settings";}}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseBody
    public ResponseEntity<Map<String,String>> invalid(IllegalArgumentException failure,Locale locale){String key=ReceiptBatchWorkerService.safeError(failure);return ResponseEntity.badRequest().body(Map.of("code",key,"message",messages.getMessage(key,null,messages.getMessage("batch.error.failed",null,locale),locale)));}
}
