package com.sacco.mvp.grouplending.controller;

import com.sacco.mvp.grouplending.dto.GroupAuthorityDtos.*;
import com.sacco.mvp.grouplending.service.GroupAuthorityService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Controller @RequestMapping("/finance/groups/{group}/authority") @RequiredArgsConstructor
public class GroupAuthorityController {
    private final GroupAuthorityService service;
    @InitBinder("authorityForm") public void authorityBinder(WebDataBinder binder){binder.setAutoGrowCollectionLimit(50);binder.setAllowedFields("requestKey","expectedPreviousId","validFrom","validThrough","maxPrincipal","requiredSignatures","capacityEvidence","resolutionEvidence","signatureEvidence","payoutType","payoutProvider","payoutHolder","payoutIdentifier","payoutEvidence","verified","signers[*].clientId","signers[*].mayApply","signers[*].maySign","signers[*].mayReceive","signers[*].mayViewAccount");}
    @InitBinder("revocationForm") public void revocationBinder(WebDataBinder binder){binder.setAllowedFields("reason");}
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.beans.InvalidPropertyException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalid(RuntimeException error,Model model){model.addAttribute("groupError",error instanceof IllegalArgumentException e?key(e):"group.error.request");return "groups/error";}
    @GetMapping public String history(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID group,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="revision_desc")String sort,Model model){var result=service.history(actor,group,page,sort);model.addAttribute("group",result.group());model.addAttribute("history",result.page());model.addAttribute("sort",sort);return "groups/authority-history";}
    @GetMapping("/clients") @ResponseBody public ResponseEntity<?> clients(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID group,@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="0")int page){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.clients(actor,group,search,page));}
    @GetMapping("/new") public String create(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID group,Model model){editor(actor,group,null,model);return "groups/authority-form";}
    @PostMapping public String record(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID group,@ModelAttribute AuthorityForm authorityForm,BindingResult errors,@RequestParam(defaultValue="record")String action,Model model){
        service.authorize(actor,"CREATE");
        if(action.equals("addSigner")){if(authorityForm.getSigners()==null || authorityForm.getSigners().size()>=50)throw new IllegalArgumentException("group.error.authoritySigners");authorityForm.getSigners().add(new SignerForm());}
        else if(action.startsWith("removeSigner:")){int index;try{index=Integer.parseInt(action.substring(13));}catch(NumberFormatException e){throw new IllegalArgumentException("group.error.request");}if(authorityForm.getSigners()==null || index<0 || index>=authorityForm.getSigners().size())throw new IllegalArgumentException("group.error.request");authorityForm.getSigners().remove(index);}
        else if(action.equals("record")){if(!errors.hasErrors())try{return "redirect:/finance/groups/"+group+"/authority/"+service.record(actor,group,authorityForm);}catch(IllegalArgumentException e){errors.reject(key(e));}catch(DataIntegrityViolationException e){errors.reject("group.error.authorityValidation");}}
        else throw new IllegalArgumentException("group.error.request");
        editor(actor,group,authorityForm,model);return "groups/authority-form";
    }
    @GetMapping("/{id}") public String detail(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID group,@PathVariable UUID id,Model model){model.addAttribute("authority",service.detail(actor,group,id));return "groups/authority-details";}
    @GetMapping("/{id}/revoke") public String revoke(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID group,@PathVariable UUID id,Model model){service.authorize(actor,"UPDATE");model.addAttribute("authority",service.detail(actor,group,id));model.addAttribute("revocationForm",new RevocationForm());return "groups/authority-revoke";}
    @PostMapping("/{id}/revoke") public String revoke(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID group,@PathVariable UUID id,@ModelAttribute RevocationForm revocationForm,BindingResult errors,Model model){service.authorize(actor,"UPDATE");if(!errors.hasErrors())try{service.revoke(actor,group,id,revocationForm.getReason());return "redirect:/finance/groups/"+group+"/authority/"+id;}catch(IllegalArgumentException e){errors.reject(key(e));}catch(DataIntegrityViolationException e){errors.reject("group.error.authorityValidation");}model.addAttribute("authority",service.detail(actor,group,id));return "groups/authority-revoke";}
    private void editor(AppUserPrincipal actor,UUID group,AuthorityForm form,Model model){var editor=service.editor(actor,group,form);model.addAttribute("group",editor.group());model.addAttribute("authorityForm",editor.form());model.addAttribute("selectedSigners",editor.selected());}
    private String key(IllegalArgumentException e){return e.getMessage()!=null && e.getMessage().matches("group\\.error\\.[A-Za-z]+")?e.getMessage():"group.error.form";}
}
