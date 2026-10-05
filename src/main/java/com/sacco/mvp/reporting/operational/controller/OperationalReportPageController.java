package com.sacco.mvp.reporting.operational.controller;

import com.sacco.mvp.reporting.operational.dto.*;
import com.sacco.mvp.reporting.operational.dto.ReportDefinition.Dataset;
import com.sacco.mvp.reporting.operational.service.OperationalReportService;
import com.sacco.mvp.security.AppUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.UUID;

@Controller @RequiredArgsConstructor @RequestMapping("/reports/operational")
public class OperationalReportPageController {
    private final OperationalReportService service;
    @GetMapping @PreAuthorize("@access.has(principal,'REPORT_TEMPLATES_VIEW')")
    public String index(@AuthenticationPrincipal AppUserPrincipal actor, @RequestParam(defaultValue="0") int page, Model model) {
        model.addAttribute("templates", service.templates(actor, page)); model.addAttribute("catalog", service.catalog(actor));
        return "reports/operational/index";
    }
    @GetMapping("/design") @PreAuthorize("@access.has(principal,'REPORT_TEMPLATES_VIEW')")
    public String design(@AuthenticationPrincipal AppUserPrincipal actor, @RequestParam(required=false) UUID id,
                         @RequestParam(defaultValue="COLLECTIONS") Dataset dataset, Model model) {
        var form = new OperationalDesignerForm(); ReportDefinition d;
        if (id == null) { d=service.system(actor,dataset); form.setName(""); }
        else { var view=service.definition(actor,id); d=view.definition(); form.setId(id); form.setExpectedVersion(view.lockVersion());
            form.setName(view.name()); model.addAttribute("template",view); }
        form.setDefinitionJson(service.encode(d)); model.addAttribute("designerForm",form); populate(actor,d,model);
        return "reports/operational/designer";
    }
    @PostMapping("/draft") @PreAuthorize("@access.hasAny(principal,'REPORT_TEMPLATES_CREATE','REPORT_TEMPLATES_UPDATE')")
    public String save(@AuthenticationPrincipal AppUserPrincipal actor, @Valid @ModelAttribute("designerForm") OperationalDesignerForm form,
                       BindingResult errors, Model model) {
        ReportDefinition d=null;
        try {
            d=service.parse(form.getDefinitionJson());
            if (!errors.hasErrors()) { var result=service.saveDraft(actor,form.getId(),form.getExpectedVersion(),form.getName(),d);
                return "redirect:/reports/operational/design?id="+result.id(); }
            model.addAttribute("reportError","opreport.error.definition");
        } catch (IllegalArgumentException ex) { model.addAttribute("reportError",error(ex)); }
          catch (DataAccessException ex) { model.addAttribute("reportError","opreport.error.conflict"); }
        if (d==null) {
            var allowed=service.catalog(actor);
            if (allowed.isEmpty()) throw new org.springframework.security.access.AccessDeniedException("Dataset unavailable");
            d=service.system(actor,allowed.getFirst().id());
            form.setDefinitionJson(service.encode(d));
        }
        populate(actor,d,model); return "reports/operational/designer";
    }
    @PostMapping("/{id}/publish") @PreAuthorize("@access.has(principal,'REPORT_TEMPLATES_PUBLISH')")
    public String publish(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam Long expectedVersion) {
        service.publish(actor,id,expectedVersion); return "redirect:/reports/operational/design?id="+id;
    }
    @PostMapping("/{id}/clone") @PreAuthorize("@access.has(principal,'REPORT_TEMPLATES_CREATE')")
    public String clone(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id) {
        return "redirect:/reports/operational/design?id="+service.cloneVersion(actor,id).id();
    }
    @PostMapping("/{id}/retire") @PreAuthorize("@access.has(principal,'REPORT_TEMPLATES_PUBLISH')")
    public String retire(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam Long expectedVersion) {
        service.retire(actor,id,expectedVersion); return "redirect:/reports/operational";
    }
    @PostMapping("/{id}/share") @PreAuthorize("@access.has(principal,'REPORT_TEMPLATES_SHARE')")
    public String share(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam Long expectedVersion,
                        @RequestParam boolean institutionVisible) {
        service.share(actor,id,expectedVersion,institutionVisible); return "redirect:/reports/operational/design?id="+id;
    }
    @GetMapping("/{id}/run") @PreAuthorize("@access.has(principal,'REPORTS_RUN')")
    public String run(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,
                      @RequestParam(defaultValue="0") int page,Model model) {
        model.addAttribute("result",service.run(actor,id,page,25));
        return "reports/operational/run";
    }
    private void populate(AppUserPrincipal actor,ReportDefinition d,Model model) {
        model.addAttribute("definition",d); model.addAttribute("datasetSpec",service.catalog(actor).stream().filter(s -> s.id()==d.dataset()).findFirst().orElseThrow());
    }
    private String error(IllegalArgumentException ex) { return ex.getMessage()!=null && ex.getMessage().startsWith("opreport.error.") ? ex.getMessage() : "opreport.error.definition"; }
    @ExceptionHandler({IllegalArgumentException.class,DataAccessException.class})
    public String failed(Exception ex,RedirectAttributes redirect) {
        redirect.addFlashAttribute("reportError",ex instanceof IllegalArgumentException invalid ? error(invalid) : "opreport.error.conflict");
        return "redirect:/reports/operational";
    }
}
