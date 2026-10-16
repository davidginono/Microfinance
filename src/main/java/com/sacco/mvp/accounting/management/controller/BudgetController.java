package com.sacco.mvp.accounting.management.controller;

import com.sacco.mvp.accounting.management.dto.ManagementReports.*;
import com.sacco.mvp.accounting.management.service.BudgetService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;

@Controller @RequiredArgsConstructor @RequestMapping("/finance/budgets")
public class BudgetController {
    private final BudgetService service;
    private final ApplicationClock clock;
    @ModelAttribute void headers(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @InitBinder("budgetForm") void bind(WebDataBinder binder){binder.setAllowedFields("requestKey","accountId","date","year","amounts[*]","replacesId");binder.setAutoGrowCollectionLimit(12);}
    @GetMapping public String list(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(required=false) Integer year,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="code") String sort,@RequestParam(defaultValue="asc") String direction,Model model){
        int y=year==null?clock.today().getYear():year;model.addAttribute("budgets",service.list(a,y,page,sort,direction));model.addAttribute("year",y);model.addAttribute("sort",sort);model.addAttribute("direction",direction);return "accounting/budgets/register";
    }
    @GetMapping("/new") public String create(@AuthenticationPrincipal AppUserPrincipal a,Model model){
        service.authorize(a,true);var f=new BudgetForm();f.setDate(clock.today());f.setYear(clock.today().getYear());model.addAttribute("budgetForm",f);return form(a,f,model);
    }
    @GetMapping("/{id}/revise") public String revise(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model model){
        service.authorize(a,true);var b=service.view(a,id);var f=new BudgetForm();f.setAccountId(b.accountId());f.setYear(b.year());f.setDate(clock.today());f.setReplacesId(id);f.setAmounts(new ArrayList<>(b.amounts()));model.addAttribute("budgetForm",f);return form(a,f,model);
    }
    @PostMapping public String save(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute("budgetForm") BudgetForm f,BindingResult errors,Model model,HttpServletResponse response){
        service.authorize(a,true);
        try{if(errors.hasErrors())throw new IllegalArgumentException("management.error.parameters");return "redirect:/finance/budgets/"+service.save(a,f);}
        catch(IllegalArgumentException failure){response.setStatus(400);model.addAttribute("formError",failure.getMessage());return form(a,f,model);}
    }
    private String form(AppUserPrincipal a,BudgetForm f,Model model){model.addAttribute("selectedAccount",service.selected(a,f.getAccountId()));model.addAttribute("today",clock.today());return "accounting/budgets/form";}
    @GetMapping("/{id}") public String view(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model model){model.addAttribute("budget",service.view(a,id));return "accounting/budgets/details";}
    @GetMapping("/accounts") @ResponseBody public List<Choice> accounts(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return service.accounts(a,search,page);}
    @ExceptionHandler(IllegalArgumentException.class) String invalid(IllegalArgumentException e,Model m,HttpServletResponse r){r.setStatus(400);m.addAttribute("managementError",safeKey(e));return "accounting/management/error";}
    static String safeKey(IllegalArgumentException e){return e.getMessage()!=null && e.getMessage().startsWith("management.error.")?e.getMessage():"management.error.parameters";}
}
