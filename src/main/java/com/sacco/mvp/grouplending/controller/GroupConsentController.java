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
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public String invalid(Model m){m.addAttribute("groupError","group.error.request");return "groups/error";}
    @GetMapping public String list(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="0") int page,Model m){m.addAttribute("packets",consents.list(a,page));return "groups/client-cycles";}
    @GetMapping("/{id}") public String packet(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m){m.addAttribute("packet",consents.packet(a,id));return "groups/consent";}
    @PostMapping("/{id}/accept") public String accept(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="false") boolean accepted,@RequestParam(defaultValue="") String otpCode,RedirectAttributes flash){
        String error=consents.accept(a,id,accepted,otpCode);if(error!=null)flash.addFlashAttribute("consentError",error);return "redirect:/app/group-loans/"+id;
    }
    @PostMapping("/{id}/code") public String code(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Locale locale,RedirectAttributes flash){
        try{codes.send(a,id,locale);flash.addFlashAttribute("consentCodeSent",true);}catch(IllegalArgumentException failure){String key=failure.getMessage();flash.addFlashAttribute("consentError",key!=null && key.matches("group\\.error\\.[A-Za-z]+")?key:"group.error.otpDelivery");}return "redirect:/app/group-loans/"+id;
    }
}
