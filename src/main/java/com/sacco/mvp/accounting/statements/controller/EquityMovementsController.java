package com.sacco.mvp.accounting.statements.controller;

import com.sacco.mvp.accounting.statements.dto.StatementModels.Parameters;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.Category;
import com.sacco.mvp.accounting.statements.service.EquityMovementService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.ui.Model;
import java.time.LocalDate;
import java.util.UUID;

@Controller @RequiredArgsConstructor @RequestMapping("/reports/financial/statements/movements")
public class EquityMovementsController {
    private final EquityMovementService movements;
    private final ApplicationClock clock;
    @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping public String list(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,@RequestParam(defaultValue="0") int page,Model model){
        var parameters=new Parameters(from==null?clock.today().withDayOfYear(1):from,through==null?clock.today():through,false);
        var rows=movements.journals(actor,parameters,page);model.addAttribute("from",parameters.from());model.addAttribute("through",parameters.through());model.addAttribute("page",page);model.addAttribute("journals",rows.stream().limit(30).toList());model.addAttribute("hasNext",rows.size()>30);model.addAttribute("categories",Category.values());return "accounting/statements/movements";
    }
    @PostMapping public String classify(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID journal,@RequestParam int version,@RequestParam Category category,@RequestParam String reason,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,@RequestParam(defaultValue="0") int page,Model model){
        try{movements.classify(actor,journal,version,category,reason);model.addAttribute("saved",true);}
        catch(IllegalArgumentException error){model.addAttribute("errorKey",key(error));model.addAttribute("selectedJournal",journal);model.addAttribute("selectedVersion",version);model.addAttribute("selectedCategory",category);model.addAttribute("reason",reason);}
        return list(actor,from,through,page,model);
    }
    private static String key(IllegalArgumentException error){return error.getMessage()!=null&&error.getMessage().startsWith("fs.error.")?error.getMessage():"fs.error.setup";}
    @ExceptionHandler(IllegalArgumentException.class) public String error(IllegalArgumentException error,Model model,jakarta.servlet.http.HttpServletResponse response){response.setStatus(400);model.addAttribute("errorKey",key(error));return "accounting/statements/error";}
}
