package com.sacco.mvp.grouplending.controller;

import com.sacco.mvp.grouplending.dto.GroupPersonalGuaranteeDtos.Form;
import com.sacco.mvp.grouplending.service.GroupPersonalGuaranteeService;
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

@Controller @RequiredArgsConstructor @RequestMapping("/finance/groups/cycles/{cycle}/personal-guarantees")
public class GroupPersonalGuaranteeController {
    private final GroupPersonalGuaranteeService guarantees;
    @InitBinder("personalGuaranteeForm") public void bind(WebDataBinder b){b.setAllowedFields("requestKey","clientId","capacity","capacityEvidence","validThrough","document.documentRequestKey","document.documentReference","document.documentLanguage","document.institutionAddress","document.contactPhone","document.contactEmail","document.complaintPhone","document.complaintEmail","document.complaintAddress","document.documentChecked");}
    @ModelAttribute public void privateResponse(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping public String list(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID cycle,@RequestParam(defaultValue="0") int page,Model m){m.addAttribute("workspace",guarantees.workspace(a,cycle,page,null));return "groups/personal-guarantees";}
    @GetMapping("/new") public String create(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID cycle,Model m){var f=new Form();m.addAttribute("personalGuaranteeForm",f);m.addAttribute("workspace",guarantees.workspace(a,cycle,0,f));return "groups/personal-guarantee-form";}
    @GetMapping("/clients") @ResponseBody public ResponseEntity<?> clients(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID cycle,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(guarantees.choices(a,cycle,search,page));}
    @PostMapping public String record(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID cycle,@ModelAttribute("personalGuaranteeForm") Form f,BindingResult errors,@RequestParam(required=false) MultipartFile termsPdf,Model m){
        if(!errors.hasErrors())try{return "redirect:"+path(cycle)+"/"+guarantees.record(a,cycle,f,termsPdf);}catch(IllegalArgumentException e){errors.reject(key(e));}catch(org.springframework.dao.DataIntegrityViolationException e){errors.reject("group.error.guaranteeCapacity");}
        m.addAttribute("workspace",guarantees.workspace(a,cycle,0,f));return "groups/personal-guarantee-form";
    }
    @GetMapping("/{id}") public String detail(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID cycle,@PathVariable UUID id,Model m){m.addAttribute("packet",guarantees.staffPacket(a,cycle,id));m.addAttribute("personalGuaranteeStaff",true);return "groups/personal-guarantee";}
    @GetMapping("/{id}/document") @ResponseBody public ResponseEntity<byte[]> document(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID cycle,@PathVariable UUID id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header("X-Content-Type-Options","nosniff").header("Content-Disposition","attachment; filename=personal-guarantee.pdf").body(guarantees.staffDocument(a,cycle,id));}
    @PostMapping("/{id}/cancel") public String cancel(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID cycle,@PathVariable UUID id,@RequestParam String reason){guarantees.cancel(a,cycle,id,reason);return "redirect:"+path(cycle)+"/"+id;}
    @PostMapping("/review") public String review(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID cycle){guarantees.startReview(a,cycle);return "redirect:"+path(cycle);}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid(IllegalArgumentException e,Model m){m.addAttribute("groupError",key(e));return "groups/error";}
    private static String path(UUID cycle){return "/finance/groups/cycles/"+cycle+"/personal-guarantees";}
    private static String key(IllegalArgumentException e){return e.getMessage()!=null&&e.getMessage().matches("group\\.error\\.[A-Za-z]+")?e.getMessage():"group.error.request";}
}
