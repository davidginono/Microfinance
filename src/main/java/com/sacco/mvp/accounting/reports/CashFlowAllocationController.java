package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.util.*;
import static com.sacco.mvp.accounting.reports.CashFlowAllocation.*;

@Controller @RequiredArgsConstructor @RequestMapping("/reports/financial/cash-flow")
public class CashFlowAllocationController {
    private final CashFlowAllocationService service;
    public record Candidate(UUID moneyLineId,String moneyCode,BigDecimal postedAmount,UUID counterpartAccountId,String counterpartCode,boolean transfer) { }
    @GetMapping public String index(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(required=false)UUID journal,@RequestParam(defaultValue="0")int page,Model model){
        var versions=service.versions(actor,page);var choices=service.journalChoices(actor,page);var shown=versions.stream().limit(25).toList();model.addAttribute("versions",shown);model.addAttribute("supersededVersions",service.supersededVersions(actor,shown.stream().map(Version::id).toList()));model.addAttribute("choices",choices.stream().limit(25).toList());model.addAttribute("page",page);model.addAttribute("hasNext",versions.size()>25 || choices.size()>25);model.addAttribute("requestKey",UUID.randomUUID());
        if(journal!=null){Source source=service.source(actor,journal);model.addAttribute("source",source);var candidates=new ArrayList<Candidate>();
            for(SourceLine money:source.lines())if(money.money())for(SourceLine other:source.lines())if(!money.accountId().equals(other.accountId()) && money.signedAmount().signum()==-other.signedAmount().signum() && candidates.stream().noneMatch(c->c.moneyLineId().equals(money.id()) && c.counterpartAccountId().equals(other.accountId())))candidates.add(new Candidate(money.id(),money.code(),money.signedAmount(),other.accountId(),other.code(),other.money()));
            require(candidates.size()<=400,"size");model.addAttribute("candidates",candidates);
        }return "reporting/cash-flow-allocation";
    }
    @PostMapping("/draft") public String draft(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID journal,@RequestParam UUID requestKey,
            @RequestParam List<UUID> moneyLineId,@RequestParam List<UUID> counterpartAccountId,@RequestParam List<Activity> activity,@RequestParam List<String> amount,@RequestParam String evidence,@RequestParam(required=false)String noncashEvidence,RedirectAttributes redirect){
        service.authorizeCreate(actor);
        try{require(amount.size()<=400 && moneyLineId.size()==amount.size() && counterpartAccountId.size()==amount.size() && activity.size()==amount.size(),"size");var splits=new ArrayList<Split>();for(int i=0;i<amount.size();i++)if(!amount.get(i).isBlank()){try{splits.add(new Split(moneyLineId.get(i),counterpartAccountId.get(i),activity.get(i),new BigDecimal(amount.get(i))));}catch(NumberFormatException e){throw new IllegalArgumentException("financial.cash.error.amount");}}
            service.draft(actor,journal,requestKey,splits,evidence,noncashEvidence);redirect.addFlashAttribute("cashSuccess","financial.cash.saved");
        }catch(IllegalArgumentException e){redirect.addFlashAttribute("cashError",error(e));redirect.addAttribute("journal",journal);}return "redirect:/reports/financial/cash-flow";
    }
    @PostMapping("/{id}/approve") public String approve(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String evidence,RedirectAttributes redirect){try{service.approve(actor,id,evidence);redirect.addFlashAttribute("cashSuccess","financial.cash.approved");}catch(IllegalArgumentException e){redirect.addFlashAttribute("cashError",error(e));}return "redirect:/reports/financial/cash-flow";}
    private static String error(IllegalArgumentException e){return e.getMessage()!=null && e.getMessage().startsWith("financial.cash.error.")?e.getMessage():"financial.cash.error.source";}
}
