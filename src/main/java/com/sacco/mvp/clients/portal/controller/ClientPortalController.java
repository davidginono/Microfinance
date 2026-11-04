package com.sacco.mvp.clients.portal.controller;

import com.sacco.mvp.clients.portal.dto.ClientPortalDtos.*;
import com.sacco.mvp.clients.portal.service.ClientPortalService;
import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.*;
import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;

@Controller @RequiredArgsConstructor @RequestMapping("/client-portal")
public class ClientPortalController {
    private static final String ROOT="/client-portal",PROFILE=ROOT+"/workspace/profile";
    private final ClientPortalService portal;
    private final OnboardingService onboarding;
    private static String[] entryFields(){return java.util.stream.Stream.concat(java.util.stream.Stream.of("clientNumber","intent","institution","branch","registration.profile.firstName","registration.profile.middleName","registration.profile.lastName","registration.profile.phone","registration.profile.email"),Arrays.stream(OnboardingService.PERSONAL_FIELDS).filter(f->!f.equals("groupId")).map(f->"registration.profile."+f)).toArray(String[]::new);}
    @InitBinder("entryForm") void entryBinder(WebDataBinder b){b.setAllowedFields(entryFields());}
    @InitBinder("selfForm") void selfBinder(WebDataBinder b){b.setAllowedFields(java.util.stream.Stream.concat(java.util.stream.Stream.of("requestKey","expectedSequence","action","noticeId","noticeAcknowledged","declarationAccepted"),Arrays.stream(OnboardingService.PERSONAL_FIELDS).map(f->"profile."+f)).toArray(String[]::new));}
    @InitBinder("documentForm") void documentBinder(WebDataBinder b){b.setAllowedFields("requestKey","revisionId","category","expiresOn");}
    @GetMapping public String entry(Model m){m.addAttribute("entryForm",new EntryForm());return "portal/entry";}
    @GetMapping("/register") public String register(Model m){var f=new EntryForm();f.setIntent("REGISTER");m.addAttribute("entryForm",f);return "portal/entry";}
    @GetMapping("/choices") @ResponseBody public ResponseEntity<List<Choice>> choices(@RequestParam(required=false) String institution,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(portal.choices(institution,search,page));}
    @PostMapping("/request-code") public String request(@ModelAttribute("entryForm") EntryForm f,BindingResult errors,HttpServletRequest request,Model m){
        if(!errors.hasErrors())try{UUID id=portal.request(f,request.getRemoteAddr());request.getSession().setAttribute("clientPortalChallenge",id);return "redirect:"+ROOT+"/verify";}catch(IllegalArgumentException e){
            String key=Objects.toString(e.getMessage(),"");String field=key.startsWith("kyc.error.")?key.substring("kyc.error.".length()):"";
            if(field.equals("nameParts"))field="firstName";if(field.equals("identityExpiry"))field="identityExpiresOn";
            if(f.getIntent().equals("REGISTER")&&Arrays.asList(entryFields()).contains("registration.profile."+field))errors.rejectValue("registration.profile."+field,key);else errors.reject("portal.error.invalid");
        }
        return "portal/entry";
    }
    @GetMapping("/verify") public String code(HttpSession session){return session.getAttribute("clientPortalChallenge") instanceof UUID?"portal/verify":"redirect:"+ROOT;}
    @PostMapping("/verify") public String verify(@RequestParam(defaultValue="") String code,HttpServletRequest request,HttpServletResponse response,Model m){
        Object challenge=request.getSession().getAttribute("clientPortalChallenge");var result=portal.verify(challenge instanceof UUID id?id:null,code,request.getRemoteAddr());
        if(result.isEmpty()){m.addAttribute("portalError","portal.error.code");return "portal/verify";}
        request.changeSessionId();request.getSession().removeAttribute("clientPortalChallenge");
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(result.get(),null,List.of()));SecurityContextHolder.setContext(context);new HttpSessionSecurityContextRepository().saveContext(context,request,response);
        response.setHeader("Cache-Control","no-store");return "redirect:"+(result.get().loanAccess()?ROOT+"/workspace/applications":PROFILE);
    }
    @GetMapping("/workspace/profile") public String profile(@AuthenticationPrincipal Session session,Model m){var actor=portal.actor(session,false);m.addAttribute("selfForm",onboarding.ownForm(actor));return profilePage(session,m);}
    @PostMapping("/workspace/profile/edit") public String profile(@AuthenticationPrincipal Session session,@ModelAttribute("selfForm") SelfForm f,BindingResult errors,Model m,RedirectAttributes flash){
        var actor=portal.actor(session,false);if(!errors.hasErrors())try{onboarding.saveOwn(actor,f);flash.addFlashAttribute("onboardingSaved",true);return "redirect:"+PROFILE;}catch(IllegalArgumentException e){reject(errors,e);}
        return profilePage(session,m);
    }
    @GetMapping("/workspace/profile/groups") @ResponseBody public ResponseEntity<List<GroupChoice>> groups(@AuthenticationPrincipal Session session,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){var a=portal.actor(session,false);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(onboarding.groups(a,a.getMemberId(),search,page));}
    @GetMapping("/workspace/profile/documents/new") public String upload(@AuthenticationPrincipal Session session,@RequestParam(defaultValue="IDENTITY") String category,Model m){var f=onboarding.ownDocumentForm(portal.actor(session,false));if(OnboardingService.DOCUMENT_CATEGORIES.contains(category))f.setCategory(category);m.addAttribute("documentForm",f);workspace(session,m);m.addAttribute("formKind","document");return "onboarding/self-form";}
    @PostMapping("/workspace/profile/documents/new") public String upload(@AuthenticationPrincipal Session session,@ModelAttribute("documentForm") DocumentForm f,BindingResult errors,@RequestParam(required=false) MultipartFile file,Model m){var a=portal.actor(session,false);if(!errors.hasErrors())try{onboarding.attachOwn(a,f,file);return "redirect:"+PROFILE;}catch(IllegalArgumentException e){reject(errors,e);}workspace(session,m);m.addAttribute("formKind","document");return "onboarding/self-form";}
    @PostMapping("/workspace/profile/documents/{id}/remove")
    public String removeDocument(@AuthenticationPrincipal Session session,@PathVariable UUID id,@RequestParam UUID revisionId) {
        var actor=portal.actor(session,false);
        try {
            onboarding.removeOwnAttachment(actor,revisionId,id);
        } catch (IllegalArgumentException stale) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT);
        }
        return "redirect:"+PROFILE;
    }
    @GetMapping("/workspace/profile/documents/{id}") public ResponseEntity<byte[]> document(@AuthenticationPrincipal Session session,@PathVariable UUID id,
                                                                                           @RequestParam(defaultValue="false") boolean preview) {
        var document=onboarding.downloadOwn(portal.actor(session,false),id);
        return file(document,!preview||!document.isImage());
    }
    @GetMapping("/workspace/profile/photos/{id}") public ResponseEntity<byte[]> photo(@AuthenticationPrincipal Session session,@PathVariable UUID id){var f=onboarding.ownPhoto(portal.actor(session,false),id);return file(f,false);}
    private ResponseEntity<byte[]> file(Download f,boolean attachment){var response=ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox");if(attachment)response.header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(f.name()).build().toString());return response.contentType(MediaType.parseMediaType(f.contentType())).body(f.bytes());}
    private String profilePage(Session session,Model m){workspace(session,m);var a=portal.actor(session,false);var w=(OwnWorkspace)m.getAttribute("workspace");boolean readOnly=w.client().state().equals("SUBMITTED");m.addAttribute("profileReadOnly",readOnly);var notice=readOnly?null:onboarding.ownNotice(a);var form=(SelfForm)m.getAttribute("selfForm");if(form!=null&&notice!=null&&!notice.id().equals(form.getNoticeId())){form.setNoticeId(notice.id());form.setNoticeAcknowledged(false);form.setDeclarationAccepted(false);}m.addAttribute("notice",notice);m.addAttribute("formKind","self");return "onboarding/self-form";}
    private void workspace(Session s,Model m){m.addAttribute("workspace",onboarding.ownWorkspace(portal.actor(s,false),0,null));m.addAttribute("portalWorkspace",true);m.addAttribute("onboardingRoot",PROFILE);}
    private void reject(BindingResult errors,IllegalArgumentException e){String key=Objects.toString(e.getMessage(),"");errors.reject(key.matches("kyc\\.error\\.[A-Za-z]+")?key:"kyc.error.invalid");}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid(Model m){m.addAttribute("portalError","portal.error.invalid");return "portal/verify";}
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class) @ResponseStatus(HttpStatus.FORBIDDEN) public String denied(Model m){m.addAttribute("portalError","portal.error.session");return "portal/expired";}
}
