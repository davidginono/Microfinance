package com.sacco.mvp.clients.applications.controller;

import com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.*;
import com.sacco.mvp.clients.applications.service.ClientApplicationService;
import com.sacco.mvp.clients.portal.dto.ClientPortalDtos.Session;
import com.sacco.mvp.clients.portal.service.ClientPortalService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.FinancialDetailsService;
import com.sacco.mvp.service.LoanTenure;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Controller @RequiredArgsConstructor
@RequestMapping({"/finance/client-applications","/finance/loan-records/applications","/client-portal/workspace/applications"})
public class ClientApplicationController {
    private final ClientApplicationService applications;
    private final ClientPortalService portal;
    private final org.springframework.context.MessageSource messages;
    @InitBinder("applicationForm") void binder(WebDataBinder b){
        b.setAutoGrowCollectionLimit(100);
        b.setAllowedFields("requestKey","expectedVersion","clientId","applicationId","productId","loanOfficerId","command","applicationDate","tenureUnit","tenureValue","amount","purpose","business","useOfFunds","income","expenses","otherDebt","declarationAccepted","fields[*]","guarantors[*].clientId","guarantors[*].relationship","guarantors[*].business","guarantors[*].location","guarantors[*].phone","collateral[*].itemName","collateral[*].itemNumber","collateral[*].itemType","collateral[*].description","collateral[*].owner","collateral[*].phone","collateral[*].location","collateral[*].estimatedValue","documentNames[*]","documentTypes[*]","requiredAttachmentIds[*]");
    }
    private AppUserPrincipal actor(Authentication authentication,HttpServletRequest r,Model m){
        boolean privatePortal=r.getServletPath().startsWith("/client-portal/");
        Object p=authentication==null?null:authentication.getPrincipal();
        AppUserPrincipal a=privatePortal&&p instanceof Session s?portal.actor(s,true):!privatePortal&&p instanceof AppUserPrincipal u&&u.isStaffSession()?u:null;
        applications.authorize(a,false);m.addAttribute("portalWorkspace",privatePortal);m.addAttribute("applicationRoot",privatePortal?"/client-portal/workspace/applications":"/finance/loan-records/applications");return a;
    }
    @GetMapping public String list(Authentication auth,HttpServletRequest r,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,Model m){var a=actor(auth,r,m);if(a.isStaffSession()&&a.getClaims().contains("LOAN_RECORDING_VIEW"))return "redirect:/finance/loan-records";m.addAttribute("applicationWorkspace",true);m.addAttribute("records",applications.list(a,search,page));m.addAttribute("search",search);return "accounting/loans/register";}
    @GetMapping("/new") public String create(Authentication auth,HttpServletRequest r,@RequestParam(required=false) UUID clientId,Model m){var a=actor(auth,r,m);if(a.isStaffSession()&&a.getClaims().contains("LOAN_RECORDING_CREATE"))return "redirect:/finance/loan-records/new"+(clientId==null?"":"?clientId="+clientId);m.addAttribute("applicationForm",applications.form(a,clientId,null));return form(a,m);}
    @GetMapping("/{id}/edit") public String edit(Authentication auth,HttpServletRequest r,@PathVariable UUID id,Model m){
        var a=actor(auth,r,m);
        if(a.isStaffSession()&&a.getClaims().contains("LOAN_RECORDING_CREATE")){applications.details(a,id);return "redirect:/finance/loan-records/new?applicationId="+id;}
        m.addAttribute("applicationForm",applications.form(a,null,id));return form(a,m);
    }
    @GetMapping("/{id}") public String details(Authentication auth,HttpServletRequest r,@PathVariable UUID id,Model m){var a=actor(auth,r,m);if(a.isStaffSession()&&r.getServletPath().startsWith("/finance/client-applications")){applications.details(a,id);return "redirect:/finance/loan-records/applications/"+id;}m.addAttribute("applicationWorkspace",true);m.addAttribute("reviewKey",UUID.randomUUID());m.addAttribute("detail",applications.details(a,id));m.addAttribute("documents",applications.documents(a,id));var recorded=applications.recorded(a,id);m.addAttribute("recordedForm",recorded);m.addAttribute("guarantorLabels",applications.guarantorLabels(a,recorded));return "accounting/loans/details";}
    @PostMapping("/{id}/submit-review") public String sendToReview(Authentication auth,HttpServletRequest r,@PathVariable UUID id,@RequestParam UUID requestKey,@RequestParam int expectedVersion,@RequestParam(defaultValue="false") boolean declarationAccepted,Model m){var a=actor(auth,r,m);applications.sendToReview(a,id,requestKey,expectedVersion,declarationAccepted);return "redirect:"+m.getAttribute("applicationRoot")+"/"+id;}
    @GetMapping("/choices/{kind}") @ResponseBody public ResponseEntity<List<Choice>> choices(Authentication auth,HttpServletRequest r,@PathVariable String kind,@RequestParam(required=false) UUID clientId,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,Model m){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(applications.choices(actor(auth,r,m),kind,clientId,search,page));}
    @GetMapping("/product/{id}") @ResponseBody public ResponseEntity<ProductFormView> product(Authentication auth,HttpServletRequest r,@PathVariable UUID id,Model m,Locale locale){var p=applications.product(actor(auth,r,m),id);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ProductFormView(p.product(),p.fields().stream().map(f->new Field(f.getName(),f.getType(),f.isRequired(),f.getMin(),f.getMax(),f.getEnumOptions(),messages.getMessage(f.getLabelKey(),null,f.getName(),locale))).toList(),p.requirements()));}
    @PostMapping("/quote") @ResponseBody public ResponseEntity<Map<String,Object>> quote(Authentication auth,HttpServletRequest r,@ModelAttribute("applicationForm") Form f,BindingResult errors,Model m){var a=actor(auth,r,m);if(errors.hasErrors())throw new IllegalArgumentException("applications.error.details");var snapshot = new LinkedHashMap<>(applications.quote(a,f));
        var tenure = new LoanTenure(f.getTenureUnit(), f.getTenureValue());
        snapshot.put("repaymentSchedule", FinancialDetailsService.previewSchedule(f.getAmount(), tenure.compatibilityMonths(), snapshot, messages));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(snapshot);}
    @PostMapping("/save") public String save(Authentication auth,HttpServletRequest r,@ModelAttribute("applicationForm") Form f,BindingResult errors,@RequestParam(required=false) List<MultipartFile> files,Model m){var a=actor(auth,r,m);if(!errors.hasErrors())try{var id=applications.save(a,f,files);return "redirect:"+m.getAttribute("applicationRoot")+"/"+id;}catch(IllegalArgumentException|IllegalStateException e){String key=Objects.toString(e.getMessage(),"");errors.reject(key.matches("(applications|recording|kyc)\\.error\\.[A-Za-z]+")?key:"applications.validation",key.isBlank()?messages.getMessage("applications.error.details",null,java.util.Locale.getDefault()):key);}return form(a,m);}
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

    private String form(AppUserPrincipal a,Model m){var f=(Form)m.getAttribute("applicationForm");var products=new ArrayList<>(applications.choices(a,"products",null,"",0));if(f.getProductId()!=null&&products.stream().noneMatch(v->v.id().equals(f.getProductId())))products.add(new Choice(f.getProductId(),applications.product(a,f.getProductId()).product().name()));m.addAttribute("products",products);if(a.isStaffSession()){m.addAttribute("clients",applications.choices(a,"clients",null,"",0));var officers=new ArrayList<>(applications.choices(a,"officers",null,"",0));applications.officerChoice(a,f.getLoanOfficerId()).filter(o->officers.stream().noneMatch(v->v.id().equals(o.id()))).ifPresent(officers::add);m.addAttribute("officers",officers);}if(f.getClientId()!=null)m.addAttribute("selectedClient",applications.clientChoice(a,f.getClientId()));if(f.getProductId()!=null)m.addAttribute("productForm",applications.product(a,f.getProductId()));m.addAttribute("guarantorLabels",f.getClientId()==null?Map.of():applications.guarantorLabels(a,f));m.addAttribute("documentTypes",ClientApplicationService.DOCUMENT_TYPES);if(f.getApplicationId()!=null)m.addAttribute("existingApplicationAttachments",applications.documents(a,f.getApplicationId()));return "accounting/loans/record-form";}
}
