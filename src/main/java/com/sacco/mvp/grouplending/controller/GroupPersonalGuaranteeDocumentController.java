package com.sacco.mvp.grouplending.controller;

import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ReportExportLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller @RequiredArgsConstructor
public class GroupPersonalGuaranteeDocumentController {
    private final GroupPersonalGuaranteeService guarantees;
    private final GroupPersonalGuaranteePdf pdf;
    private final ReportExportLimiter limiter;
    @GetMapping("/app/group-guarantees/{id}/evidence/{format:pdf|print}") @ResponseBody public ResponseEntity<byte[]> client(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable String format,Locale locale){return document(limiter.run(()->pdf.render(guarantees.evidence(a,null,id,true),locale)),format);}
    @GetMapping("/finance/groups/cycles/{cycle}/personal-guarantees/{id}/evidence/{format:pdf|print}") @ResponseBody public ResponseEntity<byte[]> staff(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID cycle,@PathVariable UUID id,@PathVariable String format,Locale locale){return document(limiter.run(()->pdf.render(guarantees.evidence(a,cycle,id,false),locale)),format);}
    private ResponseEntity<byte[]> document(byte[] data,String format){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header("X-Content-Type-Options","nosniff").header("Content-Disposition",(format.equals("print")?"inline":"attachment")+"; filename=signed-personal-guarantee.pdf").body(data);}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid(Model m){m.addAttribute("groupError","group.error.personalGuaranteesClosed");return "groups/error";}
}
