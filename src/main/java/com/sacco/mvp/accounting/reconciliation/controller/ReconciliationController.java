package com.sacco.mvp.accounting.reconciliation.controller;

import com.sacco.mvp.accounting.reconciliation.dto.ReconciliationReport.*;
import com.sacco.mvp.accounting.reconciliation.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Controller @RequiredArgsConstructor @RequestMapping("/reports/financial/sl-gl-reconciliation")
public class ReconciliationController {
    private final ReconciliationService service;
    private final ReconciliationPdf pdf;
    private final ApplicationClock clock;
    private final AuditService audit;
    @Data public static class Filter {
        private String from,through,cutoff,component="ALL";
        private UUID activityId;
        private boolean unassigned;
        private int page;
        private boolean run;
        public String getActivityFilter() {return unassigned?"UNASSIGNED":Objects.toString(activityId,"");}
    }
    @InitBinder("filter") void bind(WebDataBinder binder) {
        binder.setAllowedFields("from","through","cutoff","component","activityId","page","run");
        binder.registerCustomEditor(UUID.class,"activityId",new java.beans.PropertyEditorSupport(){
            @Override public void setAsText(String text) {
                boolean manual="UNASSIGNED".equals(text);((Filter)binder.getTarget()).setUnassigned(manual);
                setValue(manual || text==null || text.isBlank()?null:UUID.fromString(text));
            }
        });
    }
    @ModelAttribute void headers(HttpServletResponse response) { response.setHeader("Cache-Control","no-store"); }
    @GetMapping public String screen(@AuthenticationPrincipal AppUserPrincipal actor,@ModelAttribute("filter") Filter f,
            org.springframework.validation.BindingResult binding,Model model,HttpServletResponse response) {
        service.authorize(actor,false);defaults(f);model.addAttribute("today",clock.today());
        model.addAttribute("activities",service.activities(actor,"",0));
        try {
            if(binding.hasErrors())throw new IllegalArgumentException("reconciliation.error.parameters");
            if(f.getActivityId()!=null)model.addAttribute("selectedActivity",service.activity(actor,f.getActivityId()));
            if(f.isRun())model.addAttribute("report",read(actor,f,false,"PREVIEW"));
        } catch(IllegalArgumentException|DateTimeException e) {
            response.setStatus(400);model.addAttribute("formError",safe(e));
        }
        return "accounting/reconciliation/report";
    }
    @GetMapping(value="/activities",produces="application/json") @ResponseBody
    public List<ActivityChoice> activities(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="0") int page,Locale locale) {
        return service.activities(actor,search,page).stream().map(a->new ActivityChoice(a.id(),a.label(locale))).toList();
    }
    @GetMapping("/pdf") @ResponseBody public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AppUserPrincipal actor,
            @ModelAttribute("filter") Filter f,Locale locale) {
        defaults(f);var report=read(actor,f,true,"PDF");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header("Cache-Control","no-store")
            .header("Content-Disposition","attachment; filename=\"sl-gl-reconciliation.pdf\"").body(pdf.build(report,locale));
    }
    @GetMapping("/print") public String print(@AuthenticationPrincipal AppUserPrincipal actor,@ModelAttribute("filter") Filter f,Model model) {
        defaults(f);model.addAttribute("report",read(actor,f,true,"PRINT"));return "accounting/reconciliation/print";
    }
    private Result read(AppUserPrincipal actor,Filter f,boolean export,String action) {
        var result=service.report(actor,new Parameters(LocalDate.parse(f.getFrom()),LocalDate.parse(f.getThrough()),OffsetDateTime.parse(f.getCutoff()),f.getComponent(),f.getPage(),f.getActivityId(),f.isUnassigned()),export);
        audit.log("SL_GL_RECONCILIATION",null,action,actor.getMemberId(),null,Map.of("saccoId",actor.getSaccoId(),"stationId",actor.getStationId(),
            "from",result.parameters().from(),"through",result.parameters().through(),"cutoff",result.parameters().cutoff(),"component",result.parameters().component(),"activityId",f.getActivityFilter(),"exceptions",result.count()));
        return result;
    }
    private void defaults(Filter f) {
        if(f.getFrom()==null)f.setFrom(clock.today().withDayOfMonth(1).toString());
        if(f.getThrough()==null)f.setThrough(clock.today().toString());
        if(f.getCutoff()==null)f.setCutoff(clock.now().truncatedTo(ChronoUnit.SECONDS).toString());
    }
    private String safe(Exception e) { return "reconciliation.error.large".equals(e.getMessage())?e.getMessage():"reconciliation.error.parameters"; }
    @ExceptionHandler({IllegalArgumentException.class,DateTimeException.class}) String invalid(Exception e,Model model,HttpServletResponse response) {
        response.setStatus(400);model.addAttribute("formError",safe(e));return "accounting/reconciliation/error";
    }
}
