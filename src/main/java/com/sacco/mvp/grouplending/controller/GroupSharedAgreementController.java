package com.sacco.mvp.grouplending.controller;

import com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.DocumentForm;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Controller @RequiredArgsConstructor
public class GroupSharedAgreementController {
    private final GroupSharedAgreementService agreements;
    private final GroupCycleService cycles;
    @InitBinder("documentForm") public void binder(WebDataBinder b){b.setAllowedFields("documentRequestKey","documentReference","documentLanguage","institutionAddress","contactPhone","contactEmail","complaintPhone","complaintEmail","complaintAddress","documentChecked");}
    @GetMapping("/finance/groups/cycles/{id}/agreement") public String staff(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m){m.addAttribute("documentForm",new DocumentForm());return staffPage(a,id,m);}
    @PostMapping("/finance/groups/cycles/{id}/agreement") public String record(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@ModelAttribute DocumentForm documentForm,BindingResult errors,@RequestParam(required=false) MultipartFile agreementPdf,Model m){
        agreements.staff(a,"UPDATE");if(!errors.hasErrors())try{agreements.record(a,id,documentForm,agreementPdf);return "redirect:/finance/groups/cycles/"+id+"/agreement";}
        catch(IllegalArgumentException e){errors.reject(error(e));}catch(DataIntegrityViolationException e){errors.reject("group.error.financialValidation");}return staffPage(a,id,m);
    }
    private String staffPage(AppUserPrincipal a,UUID id,Model m){m.addAttribute("cycle",cycles.detail(a,id));m.addAttribute("document",agreements.document(a,id).orElse(null));return "groups/shared-document";}

    @GetMapping("/finance/groups/cycles/{id}/agreement/{document}/pdf") public ResponseEntity<byte[]> original(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable UUID document){return pdf(agreements.original(a,id,document));}
    private ResponseEntity<byte[]> pdf(byte[] bytes){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header("Content-Disposition","attachment; filename=shared-loan-agreement.pdf").body(bytes);}
    private String error(IllegalArgumentException e){return e.getMessage()!=null&&e.getMessage().matches("group\\.error\\.[A-Za-z]+")?e.getMessage():"group.error.request";}
}
