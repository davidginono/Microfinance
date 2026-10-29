package com.sacco.mvp.clients.onboarding.controller;

import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.*;
import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/app/onboarding")
public class ClientOnboardingController {
    private static final String ROOT="/app/onboarding";
    private final OnboardingService service;
    @InitBinder("selfForm") void profileBinder(WebDataBinder b) {
        b.setAllowedFields("requestKey","expectedSequence","action","noticeId","noticeAcknowledged","declarationAccepted","profile.identityType","profile.issuingCountry","profile.identityNumber","profile.birthDate","profile.identityExpiresOn","profile.identityNoExpiry","profile.address","profile.activity","profile.activityAddress","profile.sourceOfFunds");
    }
    @InitBinder("documentForm") void documentBinder(WebDataBinder b) {b.setAllowedFields("requestKey","revisionId","category","expiresOn");}
    @GetMapping public String status(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="0") int page,Model m) {
        m.addAttribute("workspace",service.ownWorkspace(a,page,null));return "onboarding/status";
    }
    @GetMapping("/versions/{sequence}") public String version(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable int sequence,Model m) {
        m.addAttribute("workspace",service.ownWorkspace(a,0,sequence));return "onboarding/status";
    }
    @GetMapping("/edit") public String edit(@AuthenticationPrincipal AppUserPrincipal a,Model m) {
        m.addAttribute("selfForm",service.ownForm(a));return profile(a,m);
    }
    @PostMapping("/edit") public String edit(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute("selfForm") SelfForm f,BindingResult errors,Model m) {
        if(!errors.hasErrors())try{service.saveOwn(a,f);return "redirect:"+ROOT;}catch(IllegalArgumentException e){reject(errors,e);}
        return profile(a,m);
    }
    @GetMapping("/documents/new") public String document(@AuthenticationPrincipal AppUserPrincipal a,Model m) {
        m.addAttribute("documentForm",service.ownDocumentForm(a));return documentPage(a,m);
    }
    @PostMapping("/documents/new") public String document(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute("documentForm") DocumentForm f,BindingResult errors,@RequestParam(required=false) MultipartFile file,Model m) {
        if(!errors.hasErrors())try{service.attachOwn(a,f,file);return "redirect:"+ROOT;}catch(IllegalArgumentException e){reject(errors,e);}
        return documentPage(a,m);
    }
    @GetMapping("/documents/{id}") public ResponseEntity<byte[]> download(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id) {
        var file=service.downloadOwn(a,id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.name()).build().toString()).contentType(MediaType.parseMediaType(file.contentType())).body(file.bytes());
    }
    private String profile(AppUserPrincipal a,Model m) {
        m.addAttribute("workspace",service.ownWorkspace(a,0,null));var notice=service.ownNotice(a).orElse(null);var form=(SelfForm)m.getAttribute("selfForm");
        if(form!=null&&notice!=null&&!notice.id().equals(form.getNoticeId())) {form.setNoticeId(notice.id());form.setNoticeAcknowledged(false);form.setDeclarationAccepted(false);}
        m.addAttribute("notice",notice);m.addAttribute("formKind","self");return "onboarding/self-form";
    }
    private String documentPage(AppUserPrincipal a,Model m) {m.addAttribute("workspace",service.ownWorkspace(a,0,null));m.addAttribute("formKind","document");return "onboarding/self-form";}
    private static void reject(BindingResult errors,IllegalArgumentException e) {
        String key=key(e),field=switch(key) {
            case "kyc.error.identityType" -> "profile.identityType";
            case "kyc.error.issuingCountry" -> "profile.issuingCountry";
            case "kyc.error.identityNumber" -> "profile.identityNumber";
            case "kyc.error.birthDate" -> "profile.birthDate";
            case "kyc.error.identityExpiry" -> "profile.identityExpiresOn";
            case "kyc.error.address" -> "profile.address";
            case "kyc.error.activity" -> "profile.activity";
            case "kyc.error.activityAddress" -> "profile.activityAddress";
            case "kyc.error.sourceOfFunds" -> "profile.sourceOfFunds";
            case "kyc.error.acknowledgement" -> "noticeAcknowledged";
            default -> null;
        };
        if(field==null)errors.reject(key);else errors.rejectValue(field,key);
    }
    private static String key(IllegalArgumentException e) {return Objects.toString(e.getMessage(),"").matches("kyc\\.error\\.[A-Za-z]+")?e.getMessage():"kyc.error.invalid";}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid(IllegalArgumentException e,Model m) {m.addAttribute("onboardingError",key(e));return "onboarding/error";}
}
