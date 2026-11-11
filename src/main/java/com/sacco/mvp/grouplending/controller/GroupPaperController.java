package com.sacco.mvp.grouplending.controller;

import com.sacco.mvp.grouplending.dto.GroupPaperDtos.PaperForm;
import com.sacco.mvp.grouplending.service.GroupPaperService;
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
import java.util.UUID;

@Controller @RequiredArgsConstructor @RequestMapping("/finance/groups/cycles/{cycle}/signed-papers")
public class GroupPaperController {
    private final GroupPaperService papers;
    @InitBinder("paperForm") public void binder(WebDataBinder b){b.setAllowedFields("requestKey","target","expectedHash","reference","language","destinationType","provider","accountHolderName","accountIdentifier","signedAt","checked");}
    @GetMapping public String page(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID cycle,@RequestParam(defaultValue="0") int page,Model m){m.addAttribute("paperForm",new PaperForm());return view(a,cycle,page,m);}
    @PostMapping public String record(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID cycle,@ModelAttribute PaperForm paperForm,BindingResult errors,@RequestParam(defaultValue="preview") String action,@RequestParam(required=false) MultipartFile signedPdf,Model m){
        papers.authorize(a,true);if(!errors.hasErrors())try{if(action.equals("preview")){paperForm.setExpectedHash(papers.preview(a,cycle,paperForm).hash());m.addAttribute("paperPreview",true);}else if(action.equals("record")){papers.record(a,cycle,paperForm,signedPdf);return "redirect:/finance/groups/cycles/"+cycle+"/signed-papers";}else errors.reject("group.error.request");}
        catch(IllegalArgumentException e){errors.reject(e.getMessage()!=null&&e.getMessage().matches("group\\.error\\.[A-Za-z]+")?e.getMessage():"group.error.request");}
        catch(org.springframework.dao.DataAccessException e){errors.reject("group.error.financialValidation");}
        return view(a,cycle,0,m);
    }
    private String view(AppUserPrincipal a,UUID cycle,int page,Model m){m.addAttribute("cycleId",cycle);m.addAttribute("paperTargets",papers.targets(a,cycle));m.addAttribute("papers",papers.history(a,cycle,page));return "groups/signed-papers";}
    @GetMapping("/{id}/pdf") @ResponseBody public ResponseEntity<byte[]> original(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID cycle,@PathVariable UUID id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header("X-Content-Type-Options","nosniff").header("Content-Disposition","attachment; filename=signed-paper.pdf").body(papers.original(a,cycle,id));}
}
