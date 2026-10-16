package com.sacco.mvp.accounting.adjustments.controller;

import com.sacco.mvp.accounting.adjustments.dto.LoanAdjustmentDtos.*;
import com.sacco.mvp.accounting.adjustments.service.LoanAdjustmentService;
import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;

@Controller @RequiredArgsConstructor @RequestMapping("/finance/loan-adjustments")
public class LoanAdjustmentController {
    private final LoanAdjustmentService service;
    private final ApplicationClock clock;
    private final LoanRecordingPdfService pdf;
    private final MessageSource messages;
    @ModelAttribute void headers(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @InitBinder("adjustmentForm") void bind(WebDataBinder b){b.setAllowedFields("requestKey","loanId","kind","date","targetAllowance","allowanceAccountId","expenseAccountId","reason","evidence");}
    @GetMapping public String list(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="") String state,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="date") String sort,@RequestParam(defaultValue="desc") String direction,Model m){m.addAttribute("records",service.list(a,state,search,page,sort,direction));m.addAttribute("state",state);m.addAttribute("search",search);m.addAttribute("sort",sort);m.addAttribute("direction",direction);return "accounting/adjustments/register";}
    @GetMapping("/new") public String create(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(required=false) UUID loanId,@RequestParam(defaultValue="PROVISION") Kind kind,Model m){service.authorize(a,"CREATE");var f=new Form();f.setLoanId(loanId);f.setKind(kind);f.setDate(clock.today());m.addAttribute("adjustmentForm",f);return form(a,f,m);}
    private String form(AppUserPrincipal a,Form f,Model m){m.addAttribute("today",clock.today());m.addAttribute("kinds",Kind.values());if(f.getLoanId()!=null)m.addAttribute("loan",service.context(a,f.getLoanId()));m.addAttribute("selectedAllowance",service.selectedAccount(a,f.getAllowanceAccountId()));m.addAttribute("selectedExpense",service.selectedAccount(a,f.getExpenseAccountId()));return "accounting/adjustments/form";}
    @GetMapping("/choices/{category}") @ResponseBody public List<Choice> choices(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable String category,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return service.choices(a,category,search,page);}
    @PostMapping public String request(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute("adjustmentForm") Form f,BindingResult errors,@RequestParam(defaultValue="false") boolean confirmed,Model m,HttpServletResponse response){
        service.authorize(a,"CREATE");try{if(errors.hasErrors() || !confirmed)throw new IllegalArgumentException("adjustment.error.parameters");return "redirect:/finance/loan-adjustments/"+service.request(a,f);}
        catch(IllegalArgumentException e){response.setStatus(400);m.addAttribute("formError",key(e));return form(a,f,m);}
    }
    @GetMapping("/{id}") public String view(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m){details(a,id,m);return "accounting/adjustments/details";}
    private void details(AppUserPrincipal a,UUID id,Model m){var q=service.view(a,id);m.addAttribute("adjustment",q);m.addAttribute("loan",service.context(a,q.loanId()));m.addAttribute("decisionKey",UUID.randomUUID());m.addAttribute("independent",!q.requestedBy().equals(a.getMemberId()));}
    @PostMapping("/{id}/decision") public String decide(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam UUID requestKey,@RequestParam boolean approve,@RequestParam String reason,@RequestParam(defaultValue="false") boolean confirmed,Model m,HttpServletResponse response){
        service.authorize(a,"APPROVE");try{if(!confirmed)throw new IllegalArgumentException("adjustment.error.parameters");return "redirect:/finance/loan-adjustments/"+service.decide(a,id,requestKey,approve,reason);}
        catch(IllegalArgumentException e){response.setStatus(400);details(a,id,m);m.addAttribute("decisionKey",requestKey);m.addAttribute("decisionReason",reason);m.addAttribute("formError",key(e));return "accounting/adjustments/details";}
    }
    @GetMapping({"/{id}/pdf","/{id}/print"}) public ResponseEntity<byte[]> export(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Locale locale,jakarta.servlet.http.HttpServletRequest request){
        var q=service.export(a,id);var rows=new ArrayList<String>();
        rows.add(label("recording.loan",locale)+": "+q.number());rows.add(label("recording.client",locale)+": "+q.client());
        rows.add(label("voucher.status",locale)+": "+label("adjustment.state."+q.state(),locale));rows.add(label("voucher.date",locale)+": "+q.date());
        rows.add(label("voucher.amount",locale)+": TZS "+q.amount());rows.add(label("accounting.debit",locale)+": "+q.debitName());rows.add(label("accounting.credit",locale)+": "+q.creditName());
        rows.add(label("adjustment.principalChange",locale)+": TZS "+q.principalChange());rows.add(label("adjustment.allowanceChange",locale)+": TZS "+q.allowanceChange());
        rows.add(label("recording.reason",locale)+": "+q.reason());rows.add(label("voucher.evidence",locale)+": "+q.evidence());rows.add(label("adjustment.requestedBy",locale)+": "+q.requester()+" / "+q.requestedAt());
        if(q.decidedAt()!=null){rows.add(label("adjustment.reviewedBy",locale)+": "+q.reviewer()+" / "+q.decidedAt());rows.add(label("recording.reason",locale)+": "+q.decisionReason());}
        if(q.journalId()!=null)rows.add(label("adjustment.journal",locale)+": "+q.journalId());
        rows.add(label("adjustment.effect."+q.kind(),locale));
        var bytes=pdf.render(new LoanRecordingPdfService.Document(label("adjustment.kind."+q.kind(),locale),q.institution(),q.branch(),"loan-adjustment.pdf",rows));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,(request.getRequestURI().endsWith("/print")?"inline":"attachment")+"; filename=loan-adjustment-"+q.id()+".pdf").body(bytes);
    }
    private String label(String key,Locale l){return messages.getMessage(key,null,l);}
    @ExceptionHandler(IllegalArgumentException.class) String invalid(IllegalArgumentException e,Model m,HttpServletResponse r){r.setStatus(400);m.addAttribute("formError",key(e));return "accounting/adjustments/error";}
    private static String key(IllegalArgumentException e){if("voucher.error.period".equals(e.getMessage()))return "adjustment.error.period";return e.getMessage()!=null && e.getMessage().startsWith("adjustment.error.")?e.getMessage():"adjustment.error.parameters";}
}
