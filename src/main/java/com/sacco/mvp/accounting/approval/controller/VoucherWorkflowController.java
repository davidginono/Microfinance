package com.sacco.mvp.accounting.approval.controller;

import com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.*;
import com.sacco.mvp.accounting.approval.service.*;
import com.sacco.mvp.accounting.dto.VoucherDtos.Form;
import com.sacco.mvp.accounting.dto.VoucherDtos.Type;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;

@Controller @RequiredArgsConstructor @RequestMapping("/finance/approval")
public class VoucherWorkflowController {
    private final VoucherWorkflowService workflows;
    private final VoucherWorkflowPostingService posting;
    private final VoucherDocumentService documents;
    @InitBinder("voucherForm") void formBinder(WebDataBinder b) {
        b.setAutoGrowCollectionLimit(50);b.setAllowedFields("requestKey","effectiveDate","party","reference","description","evidence","moneyAccountId",
            "rows[*].description","rows[*].amount","rows[*].debitAccountId","rows[*].creditAccountId","rows[*].transactionId","rows[*].templateKey","rows[*].component");
    }
    @InitBinder("ruleForm") void ruleBinder(WebDataBinder b) {b.setAutoGrowCollectionLimit(20);b.setAllowedFields("type","version","individuals","batches","forceAllowed","rules[*].name","rules[*].minimum","rules[*].maximum","rules[*].automatic","rules[*].firstClaim","rules[*].secondClaim","rules[*].firstMember","rules[*].secondMember");}
    @ExceptionHandler({org.springframework.beans.InvalidPropertyException.class,Invalid.class})
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST) @ResponseBody String invalid() {return "Invalid voucher workflow request";}
    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(required=false) Type type,
                       @RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String category,
                       @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="30") int size,@RequestParam(defaultValue="updated_desc") String sort,Model m) {
        if(type==null)type=a.getClaims().contains("ACCOUNTING_JOURNALS_VIEW")?Type.JOURNAL:Type.RECEIPT;
        var rows=workflows.list(a,type,search,category,sort,page,size);m.addAttribute("records",rows);
        m.addAttribute("sort",sort);m.addAttribute("type",type);m.addAttribute("search",search);m.addAttribute("category",category);m.addAttribute("approvalStates",Approval.values());return "accounting/approval/register";
    }
    @GetMapping("/{id}") public String details(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m) {
        var d=workflows.details(a,id);m.addAttribute("details",d);support(a,id,m);m.addAttribute("ownerNames",workflows.ownerNames(a,List.of(d.workflow())));return "accounting/approval/details";
    }
    @PostMapping("/{id}/actions") public String action(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam int version,@RequestParam String action,
                                                      @RequestParam(defaultValue="") String comments,RedirectAttributes flash) {
        try {
            if(action.equals("VALIDATE"))workflows.validate(a,id,version);
            else if(action.equals("POST")) {
                try{posting.post(a,id,version);}catch(RuntimeException e){posting.failed(a,id,version,e);throw e;}
            } else workflows.action(a,id,version,action,comments);
        }catch(IllegalArgumentException e){flash.addFlashAttribute("workflowError",VoucherWorkflowService.safeError(e));}
         catch(org.springframework.dao.DataIntegrityViolationException e){flash.addFlashAttribute("workflowError","approval.error.duplicate");}
        return redirect(id);
    }
    @GetMapping("/{id}/vouchers/{item}") public String edit(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable UUID item,Model m) {
        var d=workflows.details(a,id);var row=existingItem(d,item);
        m.addAttribute("voucherForm",row.form());editor(a,d,item,row.version(),m);return "accounting/approval/editor";
    }
    @PostMapping("/{id}/vouchers/{item}") public String save(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable UUID item,
        @RequestParam int version,@RequestParam int itemVersion,@ModelAttribute("voucherForm") Form f,BindingResult errors,Model m) {
        var before=workflows.details(a,id);existingItem(before,item);
        if(!errors.hasErrors())try{workflows.save(a,id,item,version,itemVersion,f);return redirect(id);}
        catch(IllegalArgumentException e){errors.reject(VoucherWorkflowService.safeError(e));}
        var d=workflows.details(a,id);editor(a,d,item,itemVersion,m);return "accounting/approval/editor";
    }
    private Item existingItem(Details details,UUID item) {
        return details.items().stream().filter(row->row.id().equals(item)).findFirst()
            .orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
    }
    private void editor(AppUserPrincipal a,Details d,UUID item,int itemVersion,Model m) {
        support(a,d.workflow().id(),m);m.addAttribute("readOnly",!d.actions().contains("EDIT"));m.addAttribute("details",d);m.addAttribute("itemId",item);m.addAttribute("itemVersion",itemVersion);
        var f=(Form)m.getAttribute("voucherForm");m.addAttribute("accounts",workflows.selectedAccounts(a,d.workflow().id(),f));
        m.addAttribute("issues",d.items().stream().filter(i->i.id().equals(item)).findFirst().map(Item::issues).orElse(List.of()));
    }
    @GetMapping("/{id}/accounts") @ResponseBody public Object accounts(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page) {return workflows.accounts(a,id,search,page);}
    @GetMapping("/configuration") public String configuration(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="JOURNAL") Type type,@RequestParam(required=false) java.math.BigDecimal example,Model m) {
        var c=workflows.configuration(a,type);
        if(example!=null){m.addAttribute("example",example);try{VoucherApprovalGate.require(example.signum()>=0 && example.scale()<=2 && example.precision()<=18,"amount");m.addAttribute("matchedRule",VoucherWorkflowService.match(c,example));}catch(Invalid e){m.addAttribute("exampleError",e.getMessage());}}
        var f=new RuleForm();f.setType(type);f.setVersion(c.version());f.setIndividuals(c.individuals());f.setBatches(c.batches());f.setForceAllowed(c.forceAllowed());
        var rows=new ArrayList<RuleRow>();for(var r:c.rules()) {var row=new RuleRow();row.setName(r.name());row.setMinimum(r.minimum());row.setMaximum(r.maximum());row.setAutomatic(r.automatic());
            if(!r.stages().isEmpty()){row.setFirstClaim(r.stages().getFirst().claim());row.setFirstMember(r.stages().getFirst().memberId());}
            if(r.stages().size()>1){row.setSecondClaim(r.stages().get(1).claim());row.setSecondMember(r.stages().get(1).memberId());}rows.add(row);}
        if(!rows.isEmpty())f.setRules(rows);m.addAttribute("ruleForm",f);return "accounting/approval/configuration";
    }
    @PostMapping("/configuration") public String configure(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute("ruleForm") RuleForm f,BindingResult errors,RedirectAttributes flash) {
        if(!errors.hasErrors())try{workflows.configure(a,f);flash.addFlashAttribute("configurationSaved",true);return "redirect:/finance/approval/configuration?type="+f.getType();}catch(IllegalArgumentException e){errors.reject(VoucherWorkflowService.safeError(e));}
        return "accounting/approval/configuration";
    }
    private void support(AppUserPrincipal a,UUID id,Model m) {
        if(a.getClaims().contains("VOUCHER_DOCUMENT_VIEW"))m.addAttribute("documents",documents.list(a,id));
    }
    @GetMapping("/{id}/documents/{document}") @ResponseBody
    public org.springframework.http.ResponseEntity<byte[]> document(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable UUID document) {
        var d=documents.get(a,id,document);
        return org.springframework.http.ResponseEntity.ok().header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff")
            .header("Content-Security-Policy","sandbox; default-src 'none'; frame-ancestors 'self'")
            .header("Content-Disposition",org.springframework.http.ContentDisposition.inline().filename(d.filename()).build().toString())
            .contentType(org.springframework.http.MediaType.parseMediaType(d.mediaType())).body(d.content());
    }
    @PostMapping("/{id}/documents") public String upload(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,
        @RequestParam org.springframework.web.multipart.MultipartFile file,RedirectAttributes flash) throws java.io.IOException {
        try {if(file.getSize()>5242880)throw new Invalid("document");documents.add(a,id,file.getOriginalFilename(),file.getContentType(),file.getBytes());}
        catch(IllegalArgumentException e){flash.addFlashAttribute("workflowError",VoucherWorkflowService.safeError(e));}
        return redirect(id);
    }
    private static String redirect(UUID id){return "redirect:/finance/approval/"+id;}
}
