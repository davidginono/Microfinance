package com.sacco.mvp.accounting.management.controller;

import com.sacco.mvp.accounting.management.dto.ManagementReports.*;
import com.sacco.mvp.accounting.management.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.AuditService;
import lombok.*;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletResponse;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Controller @RequiredArgsConstructor @RequestMapping("/reports/financial/operations/{reportName}")
public class ManagementReportController {
    private final ManagementReportService service;
    private final ManagementReportPdf pdf;
    private final ApplicationClock clock;
    private final AuditService audit;
    @ModelAttribute void headers(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @Data public static class Filter {
        private String from,through,cutoff;
        private UUID startAccount,endAccount;
        private String client="",product="";
        private int page;
        private boolean run;
    }
    @InitBinder("filter") void bind(WebDataBinder binder){binder.setAllowedFields("from","through","cutoff","startAccount","endAccount","client","product","page","run");}
    @GetMapping public String screen(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable String reportName,@ModelAttribute("filter") Filter f,org.springframework.validation.BindingResult binding,Model m,HttpServletResponse response){
        Kind kind=kind(reportName);service.authorize(a,kind,false);defaults(f,kind);context(a,kind,reportName,f,m);response.setHeader("Cache-Control","no-store");
        try{if(binding.hasErrors())throw new IllegalArgumentException("management.error.parameters");if(f.isRun()){var report=service.report(a,kind,parameters(f),false);m.addAttribute("report",report);record(a,report,"PREVIEW");}}
        catch(IllegalArgumentException|DateTimeException e){response.setStatus(400);m.addAttribute("formError",e instanceof IllegalArgumentException i?BudgetController.safeKey(i):"management.error.parameters");}
        return "accounting/management/report";
    }
    @GetMapping("/pdf") @ResponseBody public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable String reportName,@ModelAttribute("filter") Filter f,Locale locale){
        Kind kind=kind(reportName);defaults(f,kind);var report=service.report(a,kind,parameters(f),true);
        byte[] bytes=pdf.build(report,locale);record(a,report,"PDF");return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header("Cache-Control","no-store").header("Content-Disposition","attachment; filename=\""+reportName+".pdf\"").body(bytes);
    }
    @GetMapping("/print") public String print(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable String reportName,@ModelAttribute("filter") Filter f,Model m,HttpServletResponse response){
        Kind kind=kind(reportName);defaults(f,kind);var report=service.report(a,kind,parameters(f),true);m.addAttribute("report",report);record(a,report,"PRINT");m.addAttribute("kind",kind);response.setHeader("Cache-Control","no-store");return "accounting/management/print";
    }
    @GetMapping("/accounts") @ResponseBody public List<Choice> accounts(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable String reportName,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return service.accounts(a,kind(reportName),search,page);}
    private void defaults(Filter f,Kind kind){if(f.getThrough()==null)f.setThrough(clock.today().toString());if(f.getFrom()==null){java.time.LocalDate end;try{end=LocalDate.parse(f.getThrough());}catch(DateTimeException e){end=clock.today();}f.setFrom(end.withDayOfMonth(1).toString());}if(f.getCutoff()==null)f.setCutoff(clock.now().truncatedTo(ChronoUnit.SECONDS).toString());}
    private Parameters parameters(Filter f){return new Parameters(LocalDate.parse(f.getFrom()),LocalDate.parse(f.getThrough()),OffsetDateTime.parse(f.getCutoff()),f.getStartAccount(),f.getEndAccount(),f.getClient(),f.getProduct(),f.getPage());}
    private void context(AppUserPrincipal a,Kind kind,String name,Filter f,Model m){m.addAttribute("kind",kind);m.addAttribute("reportName",name);m.addAttribute("today",clock.today());m.addAttribute("startChoice",service.selected(a,kind,f.getStartAccount()));m.addAttribute("endChoice",service.selected(a,kind,f.getEndAccount()));}
    private void record(AppUserPrincipal a,Result r,String action){audit.log("MANAGEMENT_REPORT",null,action+"_"+r.kind(),a.getMemberId(),null,Map.of("saccoId",a.getSaccoId(),"stationId",a.getStationId(),"from",r.parameters().from(),"through",r.parameters().through(),"recordedThrough",r.parameters().cutoff(),"rows",r.count()));}
    public static Kind kind(String name){return switch(name){case "daily-collections"->Kind.DAILY_COLLECTIONS;case "cashbook"->Kind.CASHBOOK;case "loan-portfolio"->Kind.LOAN_PORTFOLIO;case "portfolio-risk"->Kind.PORTFOLIO_RISK;case "portfolio-analysis"->Kind.PORTFOLIO_ANALYSIS;default->throw new ResponseStatusException(HttpStatus.NOT_FOUND);};}
    @ExceptionHandler({IllegalArgumentException.class,DateTimeException.class}) String invalid(Exception e,Model m,HttpServletResponse response){response.setStatus(400);m.addAttribute("managementError",e instanceof IllegalArgumentException i?BudgetController.safeKey(i):"management.error.parameters");return "accounting/management/error";}
}
