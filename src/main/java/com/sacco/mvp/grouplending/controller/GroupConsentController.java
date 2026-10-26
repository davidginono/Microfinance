package com.sacco.mvp.grouplending.controller;

import com.sacco.mvp.grouplending.service.GroupConsentService;
import com.sacco.mvp.grouplending.service.GroupConsentDeliveryService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Locale;
import java.util.UUID;

@Controller @RequestMapping("/app/group-loans") @RequiredArgsConstructor
public class GroupConsentController {
    private final GroupConsentService consents;
    private final GroupConsentDeliveryService codes;
    private final com.sacco.mvp.grouplending.service.GroupOrganizationAccountService accounts;
    @InitBinder("terminationForm") public void terminationBinder(org.springframework.web.bind.WebDataBinder b){b.setAllowedFields("reason");}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public String invalid(Model m){m.addAttribute("groupError","group.error.request");return "groups/error";}
    @GetMapping public String list(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="0") int accountPage,Model m){m.addAttribute("packets",consents.list(a,page));m.addAttribute("organizationAccounts",accounts.list(a,accountPage));return "groups/client-cycles";}
    @GetMapping("/{id}") public String packet(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m){m.addAttribute("packet",consents.packet(a,id));return "groups/consent";}
    @GetMapping("/{id}/refuse") public String refusal(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m){
        var packet=consents.packet(a,id);if(packet.termination()!=null || packet.accepted())return "redirect:/app/group-loans/"+id;
        m.addAttribute("terminationGroup",packet.group());m.addAttribute("terminationForm",new com.sacco.mvp.grouplending.dto.GroupCycleDtos.TerminationForm());refusalPage(id,m);return "groups/terminate";
    }
    @PostMapping("/{id}/refuse") public String refuse(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@ModelAttribute com.sacco.mvp.grouplending.dto.GroupCycleDtos.TerminationForm terminationForm,org.springframework.validation.BindingResult errors,@RequestParam(defaultValue="false") boolean confirmed,Model m){
        if(!errors.hasErrors())try{consents.refuse(a,id,terminationForm.getReason(),confirmed);return "redirect:/app/group-loans/"+id;}
        catch(IllegalArgumentException e){String key=e.getMessage();errors.reject(key!=null && key.matches("group\\.error\\.[A-Za-z]+")?key:"group.error.refusal");}
        m.addAttribute("terminationGroup",consents.packet(a,id).group());refusalPage(id,m);return "groups/terminate";
    }
    private void refusalPage(UUID id,Model m){m.addAttribute("personalRefusal",true);m.addAttribute("terminationAction","/app/group-loans/"+id+"/refuse");m.addAttribute("terminationBack","/app/group-loans/"+id);m.addAttribute("terminationTitle","group.refuse");}
    @PostMapping("/{id}/accept") public String accept(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="false") boolean accepted,@RequestParam(defaultValue="") String otpCode,RedirectAttributes flash){
        String error=consents.accept(a,id,accepted,otpCode);if(error!=null)flash.addFlashAttribute("consentError",error);return "redirect:/app/group-loans/"+id;
    }
    @PostMapping("/{id}/code") public String code(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Locale locale,RedirectAttributes flash){
        try{codes.send(a,id,locale);flash.addFlashAttribute("consentCodeSent",true);}catch(IllegalArgumentException failure){String key=failure.getMessage();flash.addFlashAttribute("consentError",key!=null && key.matches("group\\.error\\.[A-Za-z]+")?key:"group.error.otpDelivery");}return "redirect:/app/group-loans/"+id;
    }
}
