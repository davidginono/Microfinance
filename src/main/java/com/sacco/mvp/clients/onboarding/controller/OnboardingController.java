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
@RequestMapping("/finance/clients/onboarding")
public class OnboardingController {
    private static final String ROOT="/finance/clients/onboarding";
    private final OnboardingService service;

    @InitBinder("createForm") void createBinder(WebDataBinder b) {b.setAllowedFields("requestKey","number","name","phone","email");}
    @InitBinder("profileForm") void profileBinder(WebDataBinder b) {b.setAllowedFields("requestKey","expectedSequence","action","profile.identityType","profile.issuingCountry","profile.identityNumber","profile.birthDate","profile.identityExpiresOn","profile.identityNoExpiry","profile.address","profile.activity","profile.activityAddress","profile.sourceOfFunds","profile.noticeVersion","profile.lawfulBasis","profile.consentReference","profile.consentOn","profile.retentionReference","profile.retentionReviewOn","profile.legalHoldReference");}
    @InitBinder("documentForm") void documentBinder(WebDataBinder b) {b.setAllowedFields("requestKey","revisionId","category","expiresOn");}
    @InitBinder("reviewForm") void reviewBinder(WebDataBinder b) {b.setAllowedFields("requestKey","revisionId","kind","reason","clientMessage","identityChecked","addressChecked","activityChecked","contactChecked","duplicatesChecked","screening","screeningReference","policyReference","checkedOn","reviewDue");}
    @InitBinder("noticeForm") void noticeBinder(WebDataBinder b) {b.setAllowedFields("requestKey","expectedSequence","enabled","version","english","kiswahili","lawfulBasis","retentionReference","retentionReviewOn");}

    @GetMapping("/notice") public String notice(@AuthenticationPrincipal AppUserPrincipal a,Model m) {m.addAttribute("noticeForm",service.noticeForm(a));return "onboarding/notice";}
    @PostMapping("/notice") public String notice(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute("noticeForm") NoticeForm f,BindingResult errors) {
        service.authorize(a,"UPDATE");if(!errors.hasErrors())try{service.saveNotice(a,f);return "redirect:"+ROOT+"/notice";}catch(IllegalArgumentException e){reject(errors,e);}
        return "onboarding/notice";
    }

    @GetMapping public String register(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String state,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="number_asc") String sort,Model m) {
        m.addAttribute("records",service.clients(a,search,state,page,sort));m.addAttribute("search",search);m.addAttribute("state",state);m.addAttribute("sort",sort);return "onboarding/register";
    }
    @GetMapping("/new") public String create(@AuthenticationPrincipal AppUserPrincipal a,Model m) {service.authorize(a,"CREATE");m.addAttribute("createForm",new CreateForm());return form(m,"create");}
    @PostMapping("/new") public String create(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute("createForm") CreateForm f,BindingResult errors,Model m) {
        service.authorize(a,"CREATE");if(!errors.hasErrors())try{return "redirect:"+ROOT+"/"+service.create(a,f);}catch(IllegalArgumentException e){reject(errors,e);}catch(org.springframework.dao.DataIntegrityViolationException e){errors.reject("kyc.error.registration");}
        return form(m,"create");
    }
    @GetMapping("/{id}") public String details(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,Model m) {
        context(a,id,m);m.addAttribute("decisions",service.decisions(a,id));m.addAttribute("history",service.history(a,id,page));m.addAttribute("duplicate",service.duplicate(a,id));return "onboarding/details";
    }
    @GetMapping("/{id}/edit") public String edit(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m) {
        service.authorize(a,"UPDATE");var c=service.client(a,id);var f=new ProfileForm();f.setExpectedSequence(c.sequence());service.latest(a,id).ifPresent(r->f.setProfile(r.profile()));m.addAttribute("profileForm",f);context(a,id,m);return form(m,"profile");
    }
    @GetMapping("/{id}/versions/{sequence}") public String version(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable int sequence,Model m) {
        var r=service.version(a,id,sequence);var decisions=service.versionDecisions(a,id,sequence);
        m.addAttribute("client",service.client(a,id));m.addAttribute("revision",r);m.addAttribute("documents",service.versionDocuments(a,id,sequence));m.addAttribute("decisions",decisions);
        m.addAttribute("history",service.history(a,id,0));m.addAttribute("historical",true);m.addAttribute("viewState",decisions.isEmpty()?r.mode():decisions.getFirst().kind());m.addAttribute("viewReviewDue",decisions.isEmpty()?null:decisions.getFirst().reviewDue());return "onboarding/details";
    }
    @PostMapping("/{id}/edit") public String edit(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@ModelAttribute("profileForm") ProfileForm f,BindingResult errors,Model m) {
        service.authorize(a,"UPDATE");if(!errors.hasErrors())try{service.save(a,id,f);return "redirect:"+ROOT+"/"+id;}catch(IllegalArgumentException e){reject(errors,e);}
        context(a,id,m);return form(m,"profile");
    }
    @GetMapping("/{id}/documents/new") public String document(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m) {
        service.authorize(a,"UPDATE");var f=new DocumentForm();f.setRevisionId(service.client(a,id).revisionId());m.addAttribute("documentForm",f);context(a,id,m);return form(m,"document");
    }
    @PostMapping("/{id}/documents/new") public String document(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@ModelAttribute("documentForm") DocumentForm f,BindingResult errors,@RequestParam(required=false) MultipartFile file,Model m) {
        service.authorize(a,"UPDATE");if(!errors.hasErrors())try{service.attach(a,id,f,file);return "redirect:"+ROOT+"/"+id;}catch(IllegalArgumentException e){reject(errors,e);}
        context(a,id,m);return form(m,"document");
    }
    @GetMapping("/{id}/review") public String review(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m) {
        service.authorize(a,"REVIEW");var c=service.client(a,id);var f=new ReviewForm();f.setRevisionId(c.revisionId());f.setKind(Set.of("VERIFY","EXPIRED","STALE").contains(c.state())?"REVOKE":"VERIFY");f.setCheckedOn(service.today());m.addAttribute("reviewForm",f);context(a,id,m);return form(m,"review");
    }
    @PostMapping("/{id}/review") public String review(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@ModelAttribute("reviewForm") ReviewForm f,BindingResult errors,Model m) {
        service.authorize(a,"REVIEW");if(!errors.hasErrors())try{service.review(a,id,f);return "redirect:"+ROOT+"/"+id;}catch(IllegalArgumentException e){reject(errors,e);}
        context(a,id,m);return form(m,"review");
    }
    @GetMapping("/{id}/documents/{document}") public ResponseEntity<byte[]> download(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable UUID document) {
        var file=service.download(a,id,document);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.name()).build().toString()).contentType(MediaType.parseMediaType(file.contentType())).body(file.bytes());
    }
    private void context(AppUserPrincipal a,UUID id,Model m) {m.addAttribute("client",service.client(a,id));m.addAttribute("revision",service.latest(a,id).orElse(null));m.addAttribute("documents",service.documents(a,id));}
    private String form(Model m,String kind) {m.addAttribute("formKind",kind);return "onboarding/form";}
    private static void reject(BindingResult errors,IllegalArgumentException e) {
        String key=key(e);if(errors.getObjectName().equals("noticeForm")) {
            String field=switch(key) {case "kyc.error.noticeVersion" -> "version";case "kyc.error.noticeEnglish" -> "english";case "kyc.error.noticeKiswahili" -> "kiswahili";case "kyc.error.lawfulBasis" -> "lawfulBasis";case "kyc.error.retentionReference" -> "retentionReference";case "kyc.error.retentionReviewOn" -> "retentionReviewOn";default -> null;};
            if(field!=null){errors.rejectValue(field,key);return;}
        }
        errors.reject(key);
    }
    private static String key(IllegalArgumentException e) {return Objects.toString(e.getMessage(),"").matches("kyc\\.error\\.[A-Za-z]+")?e.getMessage():"kyc.error.invalid";}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid(IllegalArgumentException e,Model m) {m.addAttribute("onboardingError",key(e));return "onboarding/error";}
}
