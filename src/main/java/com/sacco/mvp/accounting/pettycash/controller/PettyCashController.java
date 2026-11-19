package com.sacco.mvp.accounting.pettycash.controller;

import com.sacco.mvp.accounting.pettycash.dto.PettyCashDtos.*;
import com.sacco.mvp.accounting.pettycash.service.PettyCashService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.util.*;

@Controller @RequiredArgsConstructor @RequestMapping("/finance/petty-cash")
public class PettyCashController {
    private final PettyCashService pettyCash;
    private final ApplicationClock clock;
    @ExceptionHandler(IllegalArgumentException.class)
    public org.springframework.http.ResponseEntity<Void> invalid(){return org.springframework.http.ResponseEntity.badRequest().build();}
    @GetMapping
    public String workspace(@AuthenticationPrincipal AppUserPrincipal actor,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,
        @RequestParam(defaultValue="expenses") String view,@RequestParam(defaultValue="") String search,
        @RequestParam(defaultValue="date_desc") String sort,@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="30") int size,Model model){
        var query=new Query(from==null?clock.today().withDayOfMonth(1):from,through==null?clock.today():through,view,search,sort,page,size);
        model.addAttribute("cashbook",pettyCash.workspace(actor,query));return "accounting/pettycash/workspace";
    }
    @GetMapping("/pending")
    public String pending(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="30") int size,@RequestParam(defaultValue="number_desc") String sort,@RequestParam(defaultValue="") String search,Model model){
        var rows=pettyCash.pending(actor,page,size,sort,search);model.addAttribute("sort",sort);model.addAttribute("search",search);model.addAttribute("pending",rows.stream().limit(size).toList());
        model.addAttribute("page",page);model.addAttribute("size",size);model.addAttribute("hasNext",rows.size()>size);return "accounting/pettycash/pending";
    }
    @GetMapping("/setup")
    public String setup(@AuthenticationPrincipal AppUserPrincipal actor,Model model){
        model.addAttribute("fund",pettyCash.setup(actor).orElse(null));model.addAttribute("enabled",false);model.addAttribute("accountId","");model.addAttribute("evidence","");return "accounting/pettycash/setup";
    }
    @GetMapping("/accounts") @ResponseBody
    public List<Choice> accounts(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return pettyCash.choices(actor,search,page);}
    @PostMapping("/setup")
    public String configure(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(required=false) UUID accountId,@RequestParam(defaultValue="") String evidence,@RequestParam(defaultValue="false") boolean enabled,Model model,RedirectAttributes flash){
        try{pettyCash.configure(actor,new Setup(accountId,evidence,enabled));flash.addFlashAttribute("successMessageCode","petty.saved");return "redirect:/finance/petty-cash/setup";}
        catch(IllegalArgumentException e){model.addAttribute("setupError",e.getMessage());model.addAttribute("enabled",enabled);model.addAttribute("accountId",accountId);model.addAttribute("evidence",evidence);model.addAttribute("selectedAccountName",pettyCash.selectedAccount(actor,accountId));model.addAttribute("fund",pettyCash.setup(actor).orElse(null));return "accounting/pettycash/setup";}
    }
    @GetMapping("/expense")
    public String expense(@AuthenticationPrincipal AppUserPrincipal actor){pettyCash.expenseFund(actor);return "redirect:/finance/vouchers/payments/new?pettyCash=true";}
}
