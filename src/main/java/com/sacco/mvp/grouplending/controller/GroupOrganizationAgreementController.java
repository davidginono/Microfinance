package com.sacco.mvp.grouplending.controller;

import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;

@Controller @RequiredArgsConstructor @RequestMapping("/app/group-loans/{id}/organization-agreement")
public class GroupOrganizationAgreementController {
    private final GroupOrganizationAgreementService agreements;
    private final GroupOrganizationAgreementDeliveryService delivery;
    private final GroupOrganizationAgreementExportService exports;
    @GetMapping public String packet(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,Model model){model.addAttribute("packet",agreements.packet(actor,id,page));return "groups/organization-agreement";}
    @PostMapping("/pdf") public org.springframework.http.ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,Locale locale){return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_PDF).cacheControl(org.springframework.http.CacheControl.noStore()).header("Content-Disposition","attachment; filename=organization-agreement.pdf").body(exports.client(actor,id,locale));}
    @PostMapping("/sign") public String sign(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String packetHash,
        @RequestParam(defaultValue="false") boolean accepted,@RequestParam(defaultValue="") String otpCode,@RequestParam(defaultValue="") String preferredLanguage,RedirectAttributes flash){
        String error=preferredLanguage.isBlank()?agreements.sign(actor,id,packetHash,accepted,otpCode):agreements.sign(actor,id,packetHash,accepted,otpCode,preferredLanguage);flash.addFlashAttribute("preferredLanguage",Set.of("EN","SW").contains(preferredLanguage)?preferredLanguage:"");if(error!=null)flash.addFlashAttribute("consentError",error);return redirect(id);
    }
    @PostMapping("/code") public String code(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String packetHash,Locale locale,RedirectAttributes flash){
        try{delivery.send(actor,id,packetHash,locale);flash.addFlashAttribute("consentCodeSent",true);}catch(IllegalArgumentException failure){flash.addFlashAttribute("consentError",key(failure));}return redirect(id);
    }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalid(IllegalArgumentException error,Model model){model.addAttribute("groupError",key(error));return "groups/error";}
    private static String redirect(UUID id){return "redirect:/app/group-loans/"+id+"/organization-agreement";}
    private static String key(IllegalArgumentException error){return error.getMessage()!=null && error.getMessage().matches("group\\.error\\.[A-Za-z]+")?error.getMessage():"group.error.request";}
}
