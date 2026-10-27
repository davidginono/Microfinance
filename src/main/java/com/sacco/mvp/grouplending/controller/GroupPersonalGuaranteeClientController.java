package com.sacco.mvp.grouplending.controller;

import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;

@Controller @RequiredArgsConstructor @RequestMapping("/app/group-guarantees")
public class GroupPersonalGuaranteeClientController {
    private final GroupPersonalGuaranteeService guarantees;
    private final GroupPersonalGuaranteeDeliveryService delivery;
    @ModelAttribute public void privateResponse(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping public String list(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="0") int page,Model m){m.addAttribute("guarantees",guarantees.list(a,page));return "groups/personal-guarantees-client";}
    @GetMapping("/{id}") public String packet(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m){m.addAttribute("packet",guarantees.packet(a,id));return "groups/personal-guarantee";}
    @PostMapping("/{id}/document") @ResponseBody public ResponseEntity<byte[]> document(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header("X-Content-Type-Options","nosniff").header("Content-Disposition","attachment; filename=personal-guarantee.pdf").body(guarantees.clientDocument(a,id));}
    @PostMapping("/{id}/accept") public String accept(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam String packetHash,@RequestParam(defaultValue="false") boolean accepted,@RequestParam(defaultValue="") String otpCode,RedirectAttributes flash){String error=guarantees.accept(a,id,packetHash,accepted,otpCode);if(error!=null)flash.addFlashAttribute("consentError",error);return redirect(id);}
    @PostMapping("/{id}/refuse") public String refuse(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam String packetHash,@RequestParam String reason){guarantees.refuse(a,id,packetHash,reason);return redirect(id);}
    @PostMapping("/{id}/code") public String code(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam String packetHash,Locale locale,RedirectAttributes flash){try{delivery.send(a,id,packetHash,locale);flash.addFlashAttribute("consentCodeSent",true);}catch(IllegalArgumentException e){flash.addFlashAttribute("consentError",key(e));}return redirect(id);}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid(IllegalArgumentException e,Model m){m.addAttribute("groupError",key(e));return "groups/error";}
    private static String redirect(UUID id){return "redirect:/app/group-guarantees/"+id;}
    private static String key(IllegalArgumentException e){return e.getMessage()!=null&&e.getMessage().matches("group\\.error\\.[A-Za-z]+")?e.getMessage():"group.error.request";}
}
