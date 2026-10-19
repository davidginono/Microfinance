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
        b.setAutoGrowCollectionLimit(50);b.setAllowedFields("requestKey","effectiveDate","party","reference","description","evidence","moneyAccountId","clientId",
            "rows[*].description","rows[*].amount","rows[*].debitAccountId","rows[*].creditAccountId","rows[*].transactionId","rows[*].templateKey","rows[*].component");
    }
    @InitBinder("ruleForm") void ruleBinder(WebDataBinder b) {b.setAutoGrowCollectionLimit(20);b.setAllowedFields("type","version","individuals","batches","forceAllowed","rules[*].name","rules[*].minimum","rules[*].maximum","rules[*].automatic","rules[*].firstClaim","rules[*].secondClaim","rules[*].firstMember","rules[*].secondMember");}
    @ExceptionHandler({org.springframework.beans.InvalidPropertyException.class,Invalid.class})
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST) @ResponseBody String invalid() {return "Invalid voucher workflow request";}
    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(required=false) Type type,
                       @RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String category,
                       @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="30") int size,@RequestParam(defaultValue="updated_desc") String sort,@RequestParam(defaultValue="0") int days,Model m) {
        var rows=workflows.register(a,type,search,category,sort,days,page,size);m.addAttribute("records",rows);m.addAttribute("days",days);m.addAttribute("ownerNames",workflows.ownerNames(a,rows.rows()));m.addAttribute("transactionDates",workflows.transactionDates(a,rows.rows()));m.addAttribute("reviewActions",workflows.reviewActions(a,rows.rows()));
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
        @RequestParam int version,@RequestParam int itemVersion,@RequestParam(defaultValue="CLOSE") String command,@ModelAttribute("voucherForm") Form f,BindingResult errors,Model m,RedirectAttributes flash) {
        var before=workflows.details(a,id);existingItem(before,item);
        boolean saved=false;
        if(!errors.hasErrors())try{
            VoucherApprovalGate.require(Set.of("SAVE","CLOSE","VALIDATE","SUBMIT").contains(command),"input");
            if(command.equals("VALIDATE") || command.equals("SUBMIT"))workflows.authorize(a,"VOUCHER_WORKSPACE_VALIDATE");if(command.equals("SUBMIT"))workflows.authorize(a,"VOUCHER_APPROVAL_INITIATE");
            if(command.equals("SUBMIT"))VoucherApprovalGate.require(before.workflow().scope().equals("SINGLE"),"transition");
            workflows.save(a,id,item,version,itemVersion,f);saved=true;
            if(command.equals("VALIDATE") || command.equals("SUBMIT")) {
                var latest=workflows.details(a,id);workflows.validate(a,id,latest.workflow().version());
                if(command.equals("SUBMIT")){latest=workflows.details(a,id);VoucherApprovalGate.require(latest.workflow().validation().equals("VALIDATED"),"validate");if(!latest.workflow().approval().approved())workflows.action(a,id,latest.workflow().version(),latest.workflow().approval()==Approval.REQUIRED?"INITIATE":"RESUBMIT","");}
            }
            return command.equals("CLOSE") || command.equals("SUBMIT")?redirect(id):"redirect:/finance/approval/"+id+"/vouchers/"+item;
        }catch(IllegalArgumentException e){if(saved){flash.addFlashAttribute("workflowError",VoucherWorkflowService.safeError(e));return "redirect:/finance/approval/"+id+"/vouchers/"+item;}errors.reject(VoucherWorkflowService.safeError(e));}
        var d=workflows.details(a,id);editor(a,d,item,itemVersion,m);return "accounting/approval/editor";
    }
    private Item existingItem(Details details,UUID item) {
        return details.items().stream().filter(row->row.id().equals(item)).findFirst()
            .orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
    }
    private void editor(AppUserPrincipal a,Details d,UUID item,int itemVersion,Model m) {
        support(a,d.workflow().id(),m);m.addAttribute("readOnly",!d.actions().contains("EDIT"));m.addAttribute("details",d);m.addAttribute("itemId",item);m.addAttribute("itemVersion",itemVersion);
        var f=(Form)m.getAttribute("voucherForm");m.addAttribute("accounts",workflows.selectedAccounts(a,d.workflow().id(),f));
        m.addAttribute("voucherClient",workflows.selectedClient(a,d.workflow().id(),f));
        m.addAttribute("itemNumber",java.util.stream.IntStream.range(0,d.items().size()).filter(n->d.items().get(n).id().equals(item)).findFirst().orElse(d.items().size())+1);m.addAttribute("issues",d.items().stream().filter(i->i.id().equals(item)).findFirst().map(Item::issues).orElse(List.of()));
    }
    @GetMapping("/{id}/accounts") @ResponseBody public Object accounts(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page) {return workflows.accounts(a,id,search,page);}
    @GetMapping("/{id}/clients") @ResponseBody public Object clients(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page) {return workflows.clients(a,id,search,page);}
    @GetMapping("/configuration/reviewers") @ResponseBody public Object reviewers(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam String claim,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page) {return workflows.reviewers(a,claim,search,page);}
    @GetMapping("/configuration") public String configuration(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="JOURNAL") Type type,@RequestParam(required=false) java.math.BigDecimal example,Model m) {
        var c=workflows.configuration(a,type);
        if(example!=null){m.addAttribute("example",example);try{VoucherApprovalGate.require(example.signum()>=0 && example.scale()<=2 && example.precision()<=18,"amount");m.addAttribute("matchedRule",VoucherWorkflowService.match(c,example));}catch(Invalid e){m.addAttribute("exampleError",e.getMessage());}}
        var f=new RuleForm();f.setType(type);f.setVersion(c.version());f.setIndividuals(c.individuals());f.setBatches(c.batches());f.setForceAllowed(c.forceAllowed());
        var rows=new ArrayList<RuleRow>();for(var r:c.rules()) {var row=new RuleRow();row.setName(r.name());row.setMinimum(r.minimum());row.setMaximum(r.maximum());row.setAutomatic(r.automatic());
            if(!r.stages().isEmpty()){row.setFirstClaim(r.stages().getFirst().claim());row.setFirstMember(r.stages().getFirst().memberId());}
            if(r.stages().size()>1){row.setSecondClaim(r.stages().get(1).claim());row.setSecondMember(r.stages().get(1).memberId());}rows.add(row);}
        if(!rows.isEmpty())f.setRules(rows);m.addAttribute("ruleForm",f);return "accounting/approval/configuration";
    }
    @PostMapping("/configuration") public String configure(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute("ruleForm") RuleForm f,BindingResult errors,@RequestParam(defaultValue="SAVE") String command,Model m,RedirectAttributes flash) {
        if(!errors.hasErrors())try{VoucherApprovalGate.require(Set.of("SAVE","CLOSE","VALIDATE").contains(command),"input");if(command.equals("VALIDATE")){workflows.validateConfiguration(a,f);m.addAttribute("configurationValid",true);return "accounting/approval/configuration";}workflows.configure(a,f);flash.addFlashAttribute("configurationSaved",true);return command.equals("CLOSE") && a.getClaims().contains("VOUCHER_WORKSPACE_VIEW")?"redirect:/finance/approval":"redirect:/finance/approval/configuration?type="+f.getType();}catch(IllegalArgumentException e){errors.reject(VoucherWorkflowService.safeError(e));}
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
