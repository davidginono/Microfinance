package com.sacco.mvp.grouplending.controller;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller @RequiredArgsConstructor
public class GroupCoverageDocumentController {
    private final GroupCoverageService coverage;
    private final GroupCoveragePdf pdf;
    @ModelAttribute void privateResponse(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping("/finance/groups/coverage/{id}/{format:pdf|print}") @ResponseBody public ResponseEntity<byte[]> staff(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable String format,Locale locale){return document(a,id,null,false,format,locale);}
    @GetMapping("/finance/groups/cycles/{cycle}/coverage/{id}/{format:pdf|print}") @ResponseBody public ResponseEntity<byte[]> cycle(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID cycle,@PathVariable UUID id,@PathVariable String format,Locale locale){return document(a,id,cycle,false,format,locale);}
    @GetMapping("/app/group-coverage/{id}/{format:pdf|print}") @ResponseBody public ResponseEntity<byte[]> own(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable String format,Locale locale){return document(a,id,null,true,format,locale);}
    @GetMapping("/finance/groups/coverage/{id}/notice") @ResponseBody public ResponseEntity<byte[]> staffNotice(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id){return response(coverage.notice(a,id,false),"attachment","served-notice.pdf");}
    @GetMapping("/app/group-coverage/{id}/notice") @ResponseBody public ResponseEntity<byte[]> ownNotice(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id){return response(coverage.notice(a,id,true),"attachment","served-notice.pdf");}
    private ResponseEntity<byte[]> document(AppUserPrincipal a,UUID id,UUID cycle,boolean own,String format,Locale locale){var detail=coverage.document(a,id,own);if(cycle!=null&&!cycle.equals(detail.header().cycle()))throw new AccessDeniedException("Coverage case unavailable");return response(pdf.render(detail,locale),format.equals("print")?"inline":"attachment","coverage-case.pdf");}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid(IllegalArgumentException e,Model model){model.addAttribute("groupError",GroupCoverageController.key(e));return "groups/error";}
    private static ResponseEntity<byte[]> response(byte[] bytes,String disposition,String name){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header("X-Content-Type-Options","nosniff").header("Content-Disposition",disposition+"; filename="+name).body(bytes);}
}
