package com.sacco.mvp.grouplending.controller;

import com.sacco.mvp.grouplending.service.GroupConsentService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Controller @RequestMapping("/app/group-loans") @RequiredArgsConstructor
public class GroupConsentController {
    private final GroupConsentService consents;
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public String invalid(Model m){m.addAttribute("groupError","group.error.request");return "groups/error";}
    @GetMapping public String list(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="0") int page,Model m){m.addAttribute("packets",consents.list(a,page));return "groups/client-cycles";}
    @GetMapping("/{id}") public String packet(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m){m.addAttribute("packet",consents.packet(a,id));return "groups/consent";}
    @PostMapping("/{id}/accept") public String accept(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="false") boolean accepted){consents.accept(a,id,accepted);return "redirect:/app/group-loans/"+id;}
}
