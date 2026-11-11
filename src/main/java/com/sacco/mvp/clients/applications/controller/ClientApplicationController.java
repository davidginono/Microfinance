package com.sacco.mvp.clients.applications.controller;

import com.sacco.mvp.clients.applications.service.ClientApplicationService;
import com.sacco.mvp.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller @RequiredArgsConstructor
@RequestMapping({"/finance/client-applications","/finance/loan-records/applications"})
public class ClientApplicationController {
    private final ClientApplicationService applications;
    private final org.springframework.context.MessageSource messages;
    private AppUserPrincipal actor(Authentication authentication,HttpServletRequest r,Model m){
        Object p=authentication==null?null:authentication.getPrincipal();
        AppUserPrincipal a=p instanceof AppUserPrincipal u&&u.isStaffSession()?u:null;
        applications.authorize(a,false);m.addAttribute("applicationRoot","/finance/loan-records/applications");return a;
    }

    @GetMapping("/{id}/documents/{file}") public ResponseEntity<byte[]> download(Authentication auth,HttpServletRequest r,@PathVariable UUID id,@PathVariable String file,Model m){var f=applications.download(actor(auth,r,m),id,file);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox").header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(f.name(),java.nio.charset.StandardCharsets.UTF_8).build().toString()).contentType(MediaType.parseMediaType(f.contentType())).body(f.bytes());}
    @PostMapping("/{id}/documents/{file}/remove")
    public String removeDraftDocument(Authentication auth, HttpServletRequest request, @PathVariable UUID id,
                                      @PathVariable String file, @RequestParam UUID requestKey,
                                      @RequestParam int expectedVersion, Model model,
                                      org.springframework.web.servlet.mvc.support.RedirectAttributes flash, Locale locale) {
        var actor = actor(auth, request, model);
        try {
            applications.removeDraftDocument(actor, id, file, requestKey, expectedVersion);
            flash.addFlashAttribute("message", messages.getMessage("applications.documentRemoved", null, locale));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            flash.addFlashAttribute("error", messages.getMessage(ex.getMessage(), null,
                messages.getMessage("applications.error.attachments", null, locale), locale));
        }
        return "redirect:" + model.getAttribute("applicationRoot") + "/" + id + "/edit?step=4";
    }

    @GetMapping("/{id}/edit") public String edit(Authentication auth,HttpServletRequest r,@PathVariable UUID id,Model m){var a=actor(auth,r,m);applications.details(a,id);return "redirect:/finance/loan-records/new?applicationId="+id;}

    @GetMapping("/{id}") public String details(Authentication auth,HttpServletRequest r,@PathVariable UUID id,Model m){
        var a=actor(auth,r,m);m.addAttribute("applicationWorkspace",true);m.addAttribute("detail",applications.details(a,id));m.addAttribute("documents",applications.documents(a,id));
        var recorded=applications.recorded(a,id);m.addAttribute("recordedForm",recorded);m.addAttribute("guarantorLabels",applications.guarantorLabels(a,recorded));return "accounting/loans/details";
    }

}
