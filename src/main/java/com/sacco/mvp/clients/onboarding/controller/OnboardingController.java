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

    @InitBinder("createForm") void createBinder(WebDataBinder b) {b.setAutoGrowCollectionLimit(DocumentForm.MAX_FILES);b.setAllowedFields(fields("requestKey","profile.firstName","profile.middleName","profile.lastName","profile.phone","profile.email","documents[*].category","documents[*].expiresOn","documents[*].file"));}
    @InitBinder("profileForm") void profileBinder(WebDataBinder b) {b.setAllowedFields(fields("requestKey","expectedSequence","action","profile.firstName","profile.middleName","profile.lastName","profile.phone","profile.email","profile.legalHoldReference"));}
    @InitBinder("documentForm") void documentBinder(WebDataBinder b) {b.setAllowedFields("requestKey","revisionId","category","expiresOn");}
    @InitBinder("reviewForm") void reviewBinder(WebDataBinder b) {b.setAllowedFields("requestKey","revisionId","kind","reason","clientMessage","checksConfirmed","identityChecked","addressChecked","activityChecked","contactChecked","duplicatesChecked","checkedOn");}

    private static String[] fields(String... base) {return java.util.stream.Stream.concat(Arrays.stream(base),Arrays.stream(OnboardingService.PERSONAL_FIELDS).map(f->"profile."+f)).toArray(String[]::new);}
    @PostMapping("/contact-check") @ResponseBody public ResponseEntity<ContactCheck> contactCheck(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(required=false) UUID clientId,@RequestParam String field,@RequestParam String value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.checkContact(a,clientId,field,value));
    }
    @GetMapping("/groups") @ResponseBody public ResponseEntity<List<GroupChoice>> groups(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(required=false) UUID clientId,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.groups(a,clientId,search,page));}
    @GetMapping("/{id}/photos/{document}") public ResponseEntity<byte[]> photo(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable UUID document) {var f=service.photo(a,id,document);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox").contentType(MediaType.parseMediaType(f.contentType())).body(f.bytes());}
    @GetMapping("/notice") public String retiredNotice(@AuthenticationPrincipal AppUserPrincipal a) {service.authorize(a,"VIEW");return "redirect:"+ROOT;}

    @GetMapping public String register(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String state,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="number_asc") String sort,Model m) {
        m.addAttribute("records",service.clients(a,search,state,page,sort));m.addAttribute("search",search);m.addAttribute("state",state);m.addAttribute("sort",sort);return "onboarding/register";
    }
    @GetMapping("/new") public String create(@AuthenticationPrincipal AppUserPrincipal a,Model m) {
        service.authorize(a,"CREATE");m.addAttribute("createForm",new CreateForm());return form(m,"create");
    }
    @PostMapping("/new") public String create(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute("createForm") CreateForm f,BindingResult errors,@RequestParam(defaultValue="CREATE") String action,Model m) {
        service.authorize(a,"CREATE");if(!errors.hasErrors())try{
            if(!action.equals("CREATE"))throw new IllegalArgumentException("kyc.error.action");
            return "redirect:"+ROOT+"/"+service.create(a,f);
        }catch(IllegalArgumentException e){reject(errors,e);}catch(org.springframework.dao.DataIntegrityViolationException e){errors.reject("kyc.error.registration");}
        return form(m,"create");
    }
    @GetMapping("/{id}") public String details(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,Model m) {
        context(a,id,m);m.addAttribute("decisions",service.decisions(a,id));m.addAttribute("history",service.history(a,id,page));m.addAttribute("duplicate",service.duplicate(a,id));
        var c=(Client)m.getAttribute("client");
        if(a.getClaims().contains("CLIENT_ONBOARDING_REVIEW") && Set.of("SUBMITTED","VERIFY","EXPIRED","STALE").contains(c.state()) && !m.containsAttribute("reviewForm")) {
            var f=new ReviewForm();f.setRevisionId(c.revisionId());f.setKind(c.state().equals("SUBMITTED")?"VERIFY":"REVOKE");f.setCheckedOn(service.today());m.addAttribute("reviewForm",f);
        }
        return "onboarding/details";
    }
    @GetMapping({"/{id}/loan","/{id}/loan/edit","/{id}/loan/products","/{id}/loan/continue"}) public String retiredLoan(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id) {
        service.client(a,id);return "redirect:"+ROOT+"/"+id;
    }
    @GetMapping("/{id}/edit") public String edit(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m) {
        var f=service.profileForm(a,id);m.addAttribute("profileForm",f);m.addAttribute("notice",service.staffNotice(a));context(a,id,m);return form(m,"profile");
    }
    @GetMapping("/{id}/versions/{sequence}") public String version(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable int sequence,Model m) {
        var r=service.version(a,id,sequence);var decisions=service.versionDecisions(a,id,sequence);
        m.addAttribute("client",service.client(a,id));m.addAttribute("revision",r);m.addAttribute("documents",service.versionDocuments(a,id,sequence));m.addAttribute("decisions",decisions);
        m.addAttribute("history",service.history(a,id,0));m.addAttribute("historical",true);m.addAttribute("viewState",decisions.isEmpty()?r.mode():decisions.getFirst().kind());m.addAttribute("viewReviewDue",decisions.isEmpty()?null:decisions.getFirst().reviewDue());return "onboarding/details";
    }
    @PostMapping("/{id}/edit") public String edit(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@ModelAttribute("profileForm") ProfileForm f,BindingResult errors,Model m) {
        service.authorize(a,"UPDATE");if(!errors.hasErrors())try{service.save(a,id,f);return "redirect:"+ROOT+"/"+id;}catch(IllegalArgumentException e){reject(errors,e);}
        m.addAttribute("notice",service.staffNotice(a));context(a,id,m);return form(m,"profile");
    }
    @GetMapping("/{id}/documents/new") public String document(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="IDENTITY") String category,Model m) {
        var f=service.documentForm(a,id);if(OnboardingService.DOCUMENT_CATEGORIES.contains(category))f.setCategory(category);m.addAttribute("documentForm",f);context(a,id,m);return form(m,"document");
    }
    @PostMapping("/{id}/documents/new") public String document(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@ModelAttribute("documentForm") DocumentForm f,BindingResult errors,@RequestParam(required=false) MultipartFile file,Model m) {
        service.authorize(a,"UPDATE");if(!errors.hasErrors())try{service.attach(a,id,f,file);return "redirect:"+ROOT+"/"+id;}catch(IllegalArgumentException e){reject(errors,e);}
        context(a,id,m);return form(m,"document");
    }
    @PostMapping("/{id}/review") public String review(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@ModelAttribute("reviewForm") ReviewForm f,BindingResult errors,Model m) {
        service.authorize(a,"REVIEW");if(!errors.hasErrors())try{service.review(a,id,f);return "redirect:"+ROOT+"/"+id;}catch(IllegalArgumentException e){reject(errors,e);}
        return details(a,id,0,m);
    }
    @GetMapping("/{id}/documents/{document}") public ResponseEntity<byte[]> download(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable UUID document) {
        var file=service.download(a,id,document);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.name()).build().toString()).contentType(MediaType.parseMediaType(file.contentType())).body(file.bytes());
    }
    private void context(AppUserPrincipal a,UUID id,Model m) {m.addAttribute("client",service.client(a,id));m.addAttribute("revision",service.latest(a,id).orElse(null));m.addAttribute("documents",service.documents(a,id));}
    private String form(Model m,String kind) {
        m.addAttribute("formKind",kind);m.addAttribute("profileValidationDate",service.today());
        if (kind.equals("create")) m.addAttribute("creationDocumentLimits",new DocumentForm());
        return "onboarding/form";
    }
    private static void reject(BindingResult errors,IllegalArgumentException e) {
        String key=key(e);
        if (errors.getObjectName().equals("reviewForm") && key.equals("kyc.error.checks")) {
            errors.rejectValue("checksConfirmed",key);
            return;
        }
        if(Set.of("createForm","profileForm").contains(errors.getObjectName())) {
            String field=key.substring("kyc.error.".length());if(field.equals("identityExpiry"))field="identityExpiresOn";if(field.equals("nameParts")||field.equals("name"))field="firstName";
            if(Arrays.asList(fields("profile.firstName","profile.middleName","profile.lastName","profile.phone","profile.email")).contains("profile."+field)){errors.rejectValue("profile."+field,key);return;}
        }
        errors.reject(key);
    }
    private static String key(IllegalArgumentException e) {return Objects.toString(e.getMessage(),"").matches("kyc\\.error\\.[A-Za-z]+")?e.getMessage():"kyc.error.invalid";}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid(IllegalArgumentException e,Model m) {m.addAttribute("onboardingError",key(e));return "onboarding/error";}
}
