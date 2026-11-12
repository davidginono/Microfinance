package com.sacco.mvp.accounting.statements.controller;

import com.sacco.mvp.accounting.statements.dto.StatementModels.Parameters;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.*;
import com.sacco.mvp.accounting.statements.service.StatementSetupService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.BindingResult;
import org.springframework.ui.Model;
import java.time.LocalDate;
import java.util.*;

@Controller @RequiredArgsConstructor @RequestMapping("/reports/financial/statements")
public class StatementSetupController {
    private final StatementSetupService setup;
    private final ApplicationClock clock;
    @InitBinder("form") void bind(WebDataBinder binder){binder.setAutoGrowCollectionLimit(32);binder.setAllowedFields("version","requestKey","reason","components[*].name","components[*].nameSw","components[*].accounts","components[*].retained","notes[*].number","notes[*].title","notes[*].titleSw","notes[*].basis","notes[*].text","notes[*].textSw","notes[*].accounts","notes[*].includeProfit","categories[*].category","categories[*].name","categories[*].nameSw");}
    @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping("/setup") public String edit(@AuthenticationPrincipal AppUserPrincipal a,Model model){model.addAttribute("form",setup.edit(a));return editor(a,model);}
    @PostMapping("/setup") public String save(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute("form") Form form,BindingResult binding,Model model){
        try{if(binding.hasErrors())throw new IllegalArgumentException("fs.error.setup");setup.save(a,form);return "redirect:/reports/financial/statements/setup?saved=true";}
        catch(IllegalArgumentException e){model.addAttribute("errorKey",key(e));return editor(a,model);}
    }
    private String editor(AppUserPrincipal a,Model model){model.addAttribute("accounts",setup.accounts(a));return "accounting/statements/setup";}
    @GetMapping("/movements") public String movements(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,@RequestParam(defaultValue="0") int page,Model model){
        var p=new Parameters(from==null?clock.today().withDayOfYear(1):from,through==null?clock.today():through,false);var rows=setup.journals(a,p,page);model.addAttribute("from",p.from());model.addAttribute("through",p.through());model.addAttribute("page",page);model.addAttribute("journals",rows.stream().limit(30).toList());model.addAttribute("hasNext",rows.size()>30);model.addAttribute("categories",Category.values());return "accounting/statements/movements";
    }
    @PostMapping("/movements") public String classify(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam UUID journal,@RequestParam int version,@RequestParam Category category,@RequestParam String reason,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,@RequestParam(defaultValue="0") int page,Model model){
        try{setup.classify(a,journal,version,category,reason);model.addAttribute("saved",true);}
        catch(IllegalArgumentException e){model.addAttribute("errorKey",key(e));model.addAttribute("selectedJournal",journal);model.addAttribute("selectedVersion",version);model.addAttribute("selectedCategory",category);model.addAttribute("reason",reason);}
        return movements(a,from,through,page,model);
    }
    private static String key(IllegalArgumentException e){return e.getMessage()!=null&&e.getMessage().startsWith("fs.error.")?e.getMessage():"fs.error.setup";}
    @ExceptionHandler(IllegalArgumentException.class) public String error(IllegalArgumentException e,Model model,jakarta.servlet.http.HttpServletResponse response){response.setStatus(400);model.addAttribute("errorKey",key(e));return "accounting/statements/error";}
}
