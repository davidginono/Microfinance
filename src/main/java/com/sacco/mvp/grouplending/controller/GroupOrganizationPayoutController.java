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

@Controller @RequiredArgsConstructor @RequestMapping("/app/group-loans/{id}/organization-payout")
public class GroupOrganizationPayoutController {
    private final GroupOrganizationPayoutService payouts;
    private final GroupOrganizationPayoutDeliveryService delivery;
    @GetMapping public String packet(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model model){model.addAttribute("packet",payouts.packet(a,id));return "groups/organization-payout";}
    @PostMapping("/confirm") public String confirm(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam String packetHash,@RequestParam(defaultValue="false") boolean accepted,@RequestParam(defaultValue="") String otpCode,RedirectAttributes flash){
        String error=payouts.confirm(a,id,packetHash,accepted,otpCode);if(error!=null)flash.addFlashAttribute("payoutError",error);return redirect(id);
    }
    @PostMapping("/code") public String code(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam String packetHash,Locale locale,RedirectAttributes flash){
        try{delivery.send(a,id,packetHash,locale);flash.addFlashAttribute("payoutCodeSent",true);}catch(IllegalArgumentException error){flash.addFlashAttribute("payoutError",key(error));}return redirect(id);
    }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid(IllegalArgumentException error,Model model){model.addAttribute("groupError",key(error));return "groups/error";}
    private static String redirect(UUID id){return "redirect:/app/group-loans/"+id+"/organization-payout";}
    private static String key(IllegalArgumentException e){return e.getMessage()!=null&&e.getMessage().matches("group\\.error\\.[A-Za-z]+")?e.getMessage():"group.error.request";}
}
