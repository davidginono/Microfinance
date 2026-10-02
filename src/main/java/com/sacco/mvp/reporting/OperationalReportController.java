package com.sacco.mvp.reporting;

import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.AuditService;
import com.sacco.mvp.service.ReportExportLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

@Controller
@RequestMapping("/reports/builder")
@RequiredArgsConstructor
public class OperationalReportController {
    private final OperationalReportService reports;
    private final OperationalReportTemplateService templates;
    private final OperationalReportExportService exports;
    private final ObjectMapper mapper;
    private final ApplicationClock clock;
    private final AccessControlService access;
    private final AuditService audit;
    private final ReportExportLimiter limiter;

    @GetMapping
    @PreAuthorize("@access.hasAny(principal,'REPORT_TEMPLATE_DESIGN','REPORT_TEMPLATE_PUBLISH','REPORT_RUN')")
    public String index(@AuthenticationPrincipal AppUserPrincipal actor, @RequestParam(required=false) UUID version,
            @RequestParam(defaultValue="0") int page, Model model) {
        OperationalReportDefinition definition = version == null ? OperationalReportDefinition.standard(
            access.has(actor,UserClaim.LOAN_REPAYMENTS_VIEW) ? OperationalReportDefinition.Dataset.COLLECTIONS : OperationalReportDefinition.Dataset.LOAN_PORTFOLIO,"en")
            : templates.get(version,actor,false).definition();
        if(version!=null)model.addAttribute("sourceTemplate",templates.get(version,actor,false));
        populate(actor,definition,page,model);
        return "reports/builder";
    }
    @PostMapping("/templates")
    @PreAuthorize("@access.has(principal,'REPORT_TEMPLATE_DESIGN')")
    public String save(@AuthenticationPrincipal AppUserPrincipal actor, @RequestParam String definition,
            @RequestParam(required=false) UUID templateId, @RequestParam(defaultValue="false") boolean shared,
            RedirectAttributes redirect, Model model) {
        try {
            UUID id=templates.save(decode(definition),templateId,shared,actor);
            redirect.addFlashAttribute("reportSuccess","report.saved");
            return "redirect:/reports/builder?version="+id;
        } catch(IllegalArgumentException ex) { model.addAttribute("reportError",errorKey(ex)); }
        populate(actor, fallback(definition),0,model); return "reports/builder";
    }
    @PostMapping("/templates/{id}/publish")
    @PreAuthorize("@access.has(principal,'REPORT_TEMPLATE_PUBLISH')")
    public String publish(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,RedirectAttributes redirect) {
        try { templates.publish(id,actor); redirect.addFlashAttribute("reportSuccess","report.published"); }
        catch(IllegalArgumentException ex){redirect.addFlashAttribute("reportError",errorKey(ex));}
        return "redirect:/reports/builder";
    }
    @PostMapping("/templates/{id}/retire")
    @PreAuthorize("@access.has(principal,'REPORT_TEMPLATE_PUBLISH')")
    public String retire(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,RedirectAttributes redirect) {
        try { templates.retire(id,actor); redirect.addFlashAttribute("reportSuccess","report.retired"); }
        catch(IllegalArgumentException ex){redirect.addFlashAttribute("reportError",errorKey(ex));}
        return "redirect:/reports/builder";
    }
    @PostMapping("/preview")
    @PreAuthorize("@access.has(principal,'REPORT_TEMPLATE_DESIGN') and @access.has(principal,'REPORT_RUN')")
    public String preview(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String definition,
            @RequestParam LocalDate from,@RequestParam LocalDate through,@RequestParam(required=false) OffsetDateTime cutoff,
            @RequestParam(defaultValue="0") int page,Model model) {
        OperationalReportDefinition validated = fallback(definition);
        try {
            validated=decode(definition);
            reports.authorize(actor,validated.dataset(),UserClaim.REPORT_TEMPLATE_DESIGN);
            model.addAttribute("result", reports.execute(validated,actor,from,through,cutoff==null?clock.now():cutoff,page,25));
            log(actor,"PREVIEW",null,validated.dataset().name());
        } catch(IllegalArgumentException ex){model.addAttribute("reportError",errorKey(ex));}
        model.addAttribute("from",from);model.addAttribute("through",through);
        populate(actor,validated,0,model);return "reports/builder";
    }
    @GetMapping("/templates/{id}/run")
    @PreAuthorize("@access.has(principal,'REPORT_RUN')")
    public String run(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam LocalDate from,
            @RequestParam LocalDate through,@RequestParam(required=false) OffsetDateTime cutoff,@RequestParam(defaultValue="0") int page,Model model) {
        var template=templates.get(id,actor,true);
        try {
            model.addAttribute("result",reports.execute(template.definition(),actor,from,through,cutoff==null?clock.now():cutoff,page,25));
            model.addAttribute("runVersion",id); log(actor,"RUN",id,template.definition().dataset().name());
        }catch(IllegalArgumentException ex){model.addAttribute("reportError",errorKey(ex));}
        model.addAttribute("from",from); model.addAttribute("through",through);populate(actor,template.definition(),0,model);
        return "reports/builder";
    }
    @PostMapping("/preview/export")
    @PreAuthorize("@access.has(principal,'REPORT_TEMPLATE_DESIGN') and @access.has(principal,'REPORT_RUN') and @access.has(principal,'REPORT_EXPORT')")
    public ResponseEntity<byte[]> previewExport(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String definition,
            @RequestParam LocalDate from,@RequestParam LocalDate through,@RequestParam OffsetDateTime cutoff,
            @RequestParam OperationalReportExportService.Format format) {
        var validated=decode(definition);reports.authorize(actor,validated.dataset(),UserClaim.REPORT_TEMPLATE_DESIGN);
        return export(validated,null,actor,from,through,cutoff,format);
    }
    @GetMapping("/templates/{id}/export")
    @PreAuthorize("@access.has(principal,'REPORT_RUN') and @access.has(principal,'REPORT_EXPORT')")
    public ResponseEntity<byte[]> exportVersion(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam LocalDate from,
            @RequestParam LocalDate through,@RequestParam OffsetDateTime cutoff,@RequestParam OperationalReportExportService.Format format) {
        return export(templates.get(id,actor,true).definition(),id,actor,from,through,cutoff,format);
    }
    private ResponseEntity<byte[]> export(OperationalReportDefinition definition,UUID version,AppUserPrincipal actor,
            LocalDate from,LocalDate through,OffsetDateTime cutoff,OperationalReportExportService.Format format) {
        reports.authorize(actor,definition.dataset(),UserClaim.REPORT_EXPORT);
        byte[] bytes=limiter.run(()->exports.export(reports.execute(definition,actor,from,through,cutoff,0,2000),format));
        log(actor,"EXPORT",version,definition.dataset().name());
        MediaType type=switch(format){case CSV->MediaType.parseMediaType("text/csv;charset=UTF-8");case XLSX->MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");case PDF->MediaType.APPLICATION_PDF;};
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=operational-report."+format.name().toLowerCase(Locale.ROOT)).body(bytes);
    }
    private void populate(AppUserPrincipal actor,OperationalReportDefinition definition,int page,Model model) {
        model.addAttribute("reportActorId",actor.getMemberId());
        model.addAttribute("templates",templates.list(actor,page));model.addAttribute("templatePage",page);
        model.addAttribute("definition",definition);model.addAttribute("definitionJson",mapper.writeValueAsString(definition));
        model.addAttribute("catalogJson",mapper.writeValueAsString(OperationalReportDefinition.catalog()));
        model.addAttribute("catalog",OperationalReportDefinition.catalog());
        if(!model.containsAttribute("from"))model.addAttribute("from",clock.today().withDayOfMonth(1));
        if(!model.containsAttribute("through"))model.addAttribute("through",clock.today());
        Map<String,String> headings=new LinkedHashMap<>();
        for(var field:OperationalReportDefinition.Field.values())headings.put(field.name(),exports.message(field.getKey(),definition.language()));
        model.addAttribute("fieldLabels",headings);
    }
    private OperationalReportDefinition decode(String json) {
        if(json==null||json.length()>12000)throw new IllegalArgumentException("report.error.definition");
        try{OperationalReportDefinition definition=mapper.readerFor(OperationalReportDefinition.class).with(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);definition.validate();return definition;}
        catch(RuntimeException ex){throw new IllegalArgumentException("report.error.definition");}
    }
    private OperationalReportDefinition fallback(String json){try{return decode(json);}catch(IllegalArgumentException ex){return OperationalReportDefinition.standard(OperationalReportDefinition.Dataset.COLLECTIONS,"en");}}
    private void log(AppUserPrincipal actor,String action,UUID version,String dataset){audit.log("OPERATIONAL_REPORT",version,action,actor.getMemberId(),null,Map.of("saccoId",actor.getSaccoId(),"stationId",actor.getStationId(),"dataset",dataset));}
    private String errorKey(IllegalArgumentException ex){return ex.getMessage()!=null&&ex.getMessage().startsWith("report.error.")?ex.getMessage():"report.error.definition";}
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalidExport(IllegalArgumentException ex,Locale locale){return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(exports.message(errorKey(ex),locale.getLanguage().equals("sw")?"sw":"en"));}
}
