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
        b.setAllowedFields(java.util.stream.Stream.concat(java.util.stream.Stream.of("requestKey","expectedSequence","action","noticeId","noticeAcknowledged","declarationAccepted"),Arrays.stream(OnboardingService.PERSONAL_FIELDS).map(f->"profile."+f)).toArray(String[]::new));
    }
    @InitBinder("documentForm") void documentBinder(WebDataBinder b) {b.setAllowedFields("requestKey","revisionId","category","expiresOn");}
    @GetMapping("/groups") @ResponseBody public ResponseEntity<List<GroupChoice>> groups(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.groups(a,a.getMemberId(),search,page));}
    @GetMapping("/photos/{id}") public ResponseEntity<byte[]> photo(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id) {var f=service.ownPhoto(a,id);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox").contentType(MediaType.parseMediaType(f.contentType())).body(f.bytes());}
    @GetMapping public String status(@AuthenticationPrincipal AppUserPrincipal a,Model m) {
        return edit(a,m);
    }
    @GetMapping("/versions/{sequence}") public String version(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable int sequence,Model m) {
        service.ownStatus(a);return "redirect:"+ROOT;
    }
    @GetMapping({"/loan","/loan/edit","/loan/products","/loan/continue"}) public String retiredLoan(@AuthenticationPrincipal AppUserPrincipal a) {
        service.ownStatus(a);return "redirect:"+ROOT;
    }
    @GetMapping("/edit") public String edit(@AuthenticationPrincipal AppUserPrincipal a,Model m) {
        m.addAttribute("selfForm",service.ownForm(a));return profile(a,m);
    }
    @PostMapping("/edit") public String edit(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute("selfForm") SelfForm f,BindingResult errors,Model m,org.springframework.web.servlet.mvc.support.RedirectAttributes flash) {
        if(!errors.hasErrors())try{service.saveOwn(a,f);flash.addFlashAttribute("onboardingSaved",true);return "redirect:"+ROOT;}catch(IllegalArgumentException e){reject(errors,e);}
        return profile(a,m);
    }
    @GetMapping("/documents/new") public String document(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="IDENTITY") String category,Model m) {
        var f=service.ownDocumentForm(a);if(OnboardingService.DOCUMENT_CATEGORIES.contains(category))f.setCategory(category);m.addAttribute("documentForm",f);return documentPage(a,m);
    }
    @PostMapping("/documents/new") public String document(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute("documentForm") DocumentForm f,BindingResult errors,@RequestParam(required=false) MultipartFile file,Model m) {
        if(!errors.hasErrors())try{service.attachOwn(a,f,file);return "redirect:"+ROOT;}catch(IllegalArgumentException e){reject(errors,e);}
        return documentPage(a,m);
    }
    @PostMapping("/documents/{id}/remove")
    public String removeDocument(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam UUID revisionId) {
        try {
            service.removeOwnAttachment(actor,revisionId,id);
        } catch (IllegalArgumentException stale) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT);
        }
        return "redirect:"+ROOT;
    }
    @GetMapping("/documents/{id}") public ResponseEntity<byte[]> download(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,
                                                                           @RequestParam(defaultValue="false") boolean preview) {
        var file=service.downloadOwn(a,id);
        var response=ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox");
        if (!preview || !file.isImage()) {
            response.header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.name()).build().toString());
        }
        return response.contentType(MediaType.parseMediaType(file.contentType())).body(file.bytes());
    }
    private String profile(AppUserPrincipal a,Model m) {
        var workspace=service.ownWorkspace(a,0,null);m.addAttribute("workspace",workspace);
        boolean readOnly=workspace!=null&&"SUBMITTED".equals(workspace.client().state());m.addAttribute("profileReadOnly",readOnly);
        var notice=readOnly?null:service.ownNotice(a);var form=(SelfForm)m.getAttribute("selfForm");
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
        if(field==null&&Arrays.asList(OnboardingService.PERSONAL_FIELDS).contains(key.substring("kyc.error.".length())))field="profile."+key.substring("kyc.error.".length());
        if(field==null)errors.reject(key);else errors.rejectValue(field,key);
    }
    private static String key(IllegalArgumentException e) {return Objects.toString(e.getMessage(),"").matches("kyc\\.error\\.[A-Za-z]+")?e.getMessage():"kyc.error.invalid";}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid(IllegalArgumentException e,Model m) {m.addAttribute("onboardingError",key(e));return "onboarding/error";}
}
