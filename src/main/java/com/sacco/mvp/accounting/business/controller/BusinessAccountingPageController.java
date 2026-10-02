package com.sacco.mvp.accounting.business.controller;

import com.sacco.mvp.accounting.business.dto.BusinessAccountingForm;
import com.sacco.mvp.accounting.business.dto.BusinessAccountingDtos.Kind;
import com.sacco.mvp.accounting.business.service.BusinessAccountingService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Controller @RequiredArgsConstructor @RequestMapping("/finance/business")
public class BusinessAccountingPageController {
    private final BusinessAccountingService accounting;
    @InitBinder("sourceForm") void bind(WebDataBinder b){b.setAllowedFields("requestKey","kind","effectiveDate","amount","loanId","relatedDocumentId","supplierId","description","evidenceReference","channelReference","moneyAccountKey","destinationBranch","loanNumber","firstRepaymentDate","frequency","installmentAmount");}
    @GetMapping @PreAuthorize("@access.has(principal,'ACCOUNTING_BUSINESS_VIEW')")
    public String index(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0") int page,Model model){model.addAttribute("documents",accounting.list(actor,page));return "accounting/business/index";}
    @GetMapping("/new") @PreAuthorize("@access.has(principal,'ACCOUNTING_BUSINESS_CREATE')")
    public String form(Model model){model.addAttribute("sourceForm",new BusinessAccountingForm());model.addAttribute("sourceKinds",Kind.values());return "accounting/business/new";}
    @PostMapping @PreAuthorize("@access.has(principal,'ACCOUNTING_BUSINESS_CREATE')")
    public String create(@AuthenticationPrincipal AppUserPrincipal actor,@ModelAttribute("sourceForm") BusinessAccountingForm form,BindingResult errors,Model model){
        if(!errors.hasErrors())try{return "redirect:/finance/business/"+accounting.create(actor,form.command()).id();}catch(IllegalArgumentException e){model.addAttribute("sourceError",error(e));}catch(DataAccessException e){model.addAttribute("sourceError","finance.business.error.conflict");}
        else model.addAttribute("sourceError","finance.business.error.validation");model.addAttribute("sourceKinds",Kind.values());return "accounting/business/new";
    }
    @GetMapping("/{id}") @PreAuthorize("@access.has(principal,'ACCOUNTING_BUSINESS_VIEW')")
    public String view(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,Model model){model.addAttribute("document",accounting.view(actor,id));return "accounting/business/view";}
    @PostMapping("/{id}/submit") @PreAuthorize("@access.has(principal,'ACCOUNTING_BUSINESS_CREATE')")
    public String submit(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,Model model){try{accounting.submit(actor,id);return "redirect:/finance/business/"+id;}catch(IllegalArgumentException e){model.addAttribute("sourceError",error(e));}catch(DataAccessException e){model.addAttribute("sourceError","finance.business.error.conflict");}return view(actor,id,model);}
    @PostMapping("/{id}/post") @PreAuthorize("@access.has(principal,'ACCOUNTING_BUSINESS_APPROVE')")
    public String post(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String approvalEvidence,@RequestParam(defaultValue="false") boolean confirmed,Model model){try{accounting.approveAndPost(actor,id,approvalEvidence,confirmed);return "redirect:/finance/business/"+id;}catch(IllegalArgumentException e){model.addAttribute("sourceError",error(e));}catch(DataAccessException e){model.addAttribute("sourceError","finance.business.error.conflict");}model.addAttribute("approvalEvidence",approvalEvidence);return view(actor,id,model);}
    @PostMapping("/{id}/reject") @PreAuthorize("@access.has(principal,'ACCOUNTING_BUSINESS_APPROVE')")
    public String reject(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String approvalEvidence,Model model){try{accounting.reject(actor,id,approvalEvidence);return "redirect:/finance/business/"+id;}catch(IllegalArgumentException e){model.addAttribute("sourceError",error(e));}return view(actor,id,model);}
    @GetMapping("/suppliers") @PreAuthorize("@access.has(principal,'ACCOUNTING_BUSINESS_VIEW')")
    public String suppliers(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0") int page,Model model){model.addAttribute("suppliers",accounting.suppliers(actor,page));return "accounting/business/suppliers";}
    @PostMapping("/suppliers") @PreAuthorize("@access.has(principal,'ACCOUNTING_BUSINESS_CREATE')")
    public String supplier(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String name,@RequestParam String evidence,Model model){try{accounting.supplier(actor,name,evidence);return "redirect:/finance/business/suppliers";}catch(IllegalArgumentException e){model.addAttribute("sourceError",error(e));}return suppliers(actor,0,model);}
    @GetMapping("/assets") @PreAuthorize("@access.has(principal,'ACCOUNTING_BUSINESS_VIEW')")
    public String assets(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0") int page,Model model){model.addAttribute("assets",accounting.assets(actor,page));return "accounting/business/assets";}
    private String error(IllegalArgumentException e){return e.getMessage()!=null && (e.getMessage().startsWith("finance.business.error.") || e.getMessage().startsWith("accounting.policy.error.") || e.getMessage().startsWith("accounting.gl.error.") || e.getMessage().startsWith("repayment.error."))?e.getMessage():"finance.business.error.validation";}
}
