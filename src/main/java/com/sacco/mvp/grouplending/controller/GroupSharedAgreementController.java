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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;

@Controller @RequiredArgsConstructor
public class GroupSharedAgreementController {
    private final GroupSharedAgreementService agreements;
    private final GroupSharedAgreementDeliveryService delivery;
    private final GroupCycleService cycles;
    @InitBinder("documentForm") public void binder(WebDataBinder b){b.setAllowedFields("documentRequestKey","documentReference","documentLanguage","institutionAddress","contactPhone","contactEmail","complaintPhone","complaintEmail","complaintAddress","documentChecked");}
    @GetMapping("/finance/groups/cycles/{id}/agreement") public String staff(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m){m.addAttribute("documentForm",new DocumentForm());return staffPage(a,id,m);}
    @PostMapping("/finance/groups/cycles/{id}/agreement") public String record(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@ModelAttribute DocumentForm documentForm,BindingResult errors,@RequestParam(required=false) MultipartFile agreementPdf,Model m){
        agreements.staff(a,"UPDATE");if(!errors.hasErrors())try{agreements.record(a,id,documentForm,agreementPdf);return "redirect:/finance/groups/cycles/"+id+"/agreement";}
        catch(IllegalArgumentException e){errors.reject(error(e));}catch(DataIntegrityViolationException e){errors.reject("group.error.financialValidation");}return staffPage(a,id,m);
    }
    private String staffPage(AppUserPrincipal a,UUID id,Model m){m.addAttribute("cycle",cycles.detail(a,id));m.addAttribute("document",agreements.document(a,id).orElse(null));return "groups/shared-document";}
    @GetMapping("/finance/groups/cycles/{id}/agreement/{document}/pdf") public ResponseEntity<byte[]> original(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable UUID document){return pdf(agreements.original(a,id,document));}
    @GetMapping("/app/group-loans/{id}/payout") public String client(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m){m.addAttribute("packet",agreements.packet(a,id));return "groups/shared-payout";}
    @GetMapping("/app/group-loans/{id}/payout/pdf") public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Locale locale){return pdf(agreements.exportClient(a,id,locale));}
    @PostMapping("/app/group-loans/{id}/payout/code") public String code(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam String packetHash,Locale locale,RedirectAttributes flash){try{delivery.send(a,id,packetHash,locale);flash.addFlashAttribute("consentCodeSent",true);}catch(IllegalArgumentException e){flash.addFlashAttribute("consentError",error(e));}return "redirect:/app/group-loans/"+id+"/payout";}
    @PostMapping("/app/group-loans/{id}/payout/confirm") public String confirm(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam String packetHash,@RequestParam(defaultValue="false") boolean accepted,@RequestParam(defaultValue="") String otpCode,@RequestParam(defaultValue="") String preferredLanguage,RedirectAttributes flash){try{String error=preferredLanguage.isBlank()?agreements.confirm(a,id,packetHash,accepted,otpCode):agreements.confirm(a,id,packetHash,accepted,otpCode,preferredLanguage);flash.addFlashAttribute("preferredLanguage",Set.of("EN","SW").contains(preferredLanguage)?preferredLanguage:"");if(error!=null)flash.addFlashAttribute("consentError",error);}catch(IllegalArgumentException e){flash.addFlashAttribute("consentError",error(e));}return "redirect:/app/group-loans/"+id+"/payout";}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid(IllegalArgumentException e,Model m){m.addAttribute("groupError",error(e));return "groups/error";}
    private ResponseEntity<byte[]> pdf(byte[] bytes){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header("Content-Disposition","attachment; filename=shared-loan-agreement.pdf").body(bytes);}
    private String error(IllegalArgumentException e){return e.getMessage()!=null&&e.getMessage().matches("group\\.error\\.[A-Za-z]+")?e.getMessage():"group.error.request";}
}
