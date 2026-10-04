package com.sacco.mvp.accounting.business.controller;

import com.sacco.mvp.accounting.business.dto.BusinessOpeningForm;
import com.sacco.mvp.accounting.business.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller @RequiredArgsConstructor @RequestMapping("/finance/business/openings")
public class BusinessOpeningPageController {
    private final BusinessOpeningService openings;
    @InitBinder("openingForm") void bind(WebDataBinder binder){binder.setAllowedFields("requestKey","accountCode","through","purpose","evidence","completeCoverage");}
    @GetMapping @PreAuthorize("@access.has(principal,'ACCOUNTING_BUSINESS_VIEW')")
    public String index(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0") int page,Model model){model.addAttribute("openings",openings.list(actor,page));return "accounting/business/openings/index";}
    @GetMapping("/new") @PreAuthorize("@access.has(principal,'ACCOUNTING_BUSINESS_CREATE')")
    public String form(Model model){if(!model.containsAttribute("openingForm"))model.addAttribute("openingForm",new BusinessOpeningForm());model.addAttribute("purposes",BusinessOpeningImport.PURPOSES.stream().sorted().toList());return "accounting/business/openings/new";}
    @PostMapping @PreAuthorize("@access.has(principal,'ACCOUNTING_BUSINESS_CREATE')")
    public String upload(@AuthenticationPrincipal AppUserPrincipal actor,@ModelAttribute("openingForm") BusinessOpeningForm form,BindingResult errors,
            @RequestParam MultipartFile file,@RequestParam(defaultValue="false") boolean preview,Model model){
        if(!errors.hasErrors())try{
            var c=openings.command(actor,form.getRequestKey(),form.getAccountCode(),form.getThrough(),form.getPurpose(),form.getEvidence(),form.isCompleteCoverage());
            if(preview){model.addAttribute("preview",openings.preview(actor,c,file));return form(model);}
            return "redirect:/finance/business/openings/"+openings.importFile(actor,c,file).id();
        }catch(IllegalArgumentException failure){model.addAttribute("openingError",error(failure));}
        catch(DataAccessException failure){model.addAttribute("openingError","finance.business.opening.error.import");}
        else model.addAttribute("openingError","finance.business.opening.error.validation");return form(model);
    }
    @GetMapping("/{id}") @PreAuthorize("@access.has(principal,'ACCOUNTING_BUSINESS_VIEW')")
    public String view(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,Model model){
        if(page<0||page>40)throw new IllegalArgumentException("finance.business.opening.error.validation");
        var o=openings.view(actor,id);model.addAttribute("opening",o);model.addAttribute("accountLabel",openings.accountLabel(actor,id));model.addAttribute("rows",o.preview().rows().stream().skip(page*25L).limit(25).toList());model.addAttribute("rowPage",page);model.addAttribute("hasNext",o.preview().rows().size()>(page+1)*25);model.addAttribute("independentReviewer",!o.maker().equals(actor.getMemberId()));return "accounting/business/openings/view";
    }
    @PostMapping("/{id}/review") @PreAuthorize("@access.has(principal,'ACCOUNTING_BUSINESS_APPROVE')")
    public String review(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String decision,@RequestParam String evidence,
            @RequestParam(defaultValue="false") boolean confirmed,Model model){
        try{openings.review(actor,id,decision,evidence,confirmed);return "redirect:/finance/business/openings/"+id;}
        catch(IllegalArgumentException failure){model.addAttribute("openingError",error(failure));}
        catch(DataAccessException failure){model.addAttribute("openingError","finance.business.opening.error.review");}
        model.addAttribute("reviewEvidence",evidence);return view(actor,id,0,model);
    }
    @GetMapping("/{id}/file") @PreAuthorize("@access.has(principal,'ACCOUNTING_BUSINESS_VIEW')")
    public ResponseEntity<byte[]> file(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id){var file=openings.file(actor,id);return ResponseEntity.ok()
        .contentType(MediaType.TEXT_PLAIN).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.filename(),StandardCharsets.UTF_8).build().toString()).body(file.bytes());}
    private static String error(IllegalArgumentException failure){return failure.getMessage()!=null&&failure.getMessage().startsWith("finance.business.opening.error.")?failure.getMessage():"finance.business.opening.error.validation";}
}
