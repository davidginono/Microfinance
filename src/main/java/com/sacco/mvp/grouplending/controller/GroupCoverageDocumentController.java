package com.sacco.mvp.grouplending.controller;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller @RequiredArgsConstructor
public class GroupCoverageDocumentController {
    private final GroupCoverageService coverage;
    private final GroupCoveragePdf pdf;
    @GetMapping("/finance/groups/coverage/{id}/{format:pdf|print}") @ResponseBody public ResponseEntity<byte[]> staff(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable String format,Locale locale){return document(a,id,false,format,locale);}
    @GetMapping("/app/group-coverage/{id}/{format:pdf|print}") @ResponseBody public ResponseEntity<byte[]> own(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable String format,Locale locale){return document(a,id,true,format,locale);}
    @GetMapping("/finance/groups/coverage/{id}/notice") @ResponseBody public ResponseEntity<byte[]> staffNotice(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id){return response(coverage.notice(a,id,false),"attachment","served-notice.pdf");}
    @GetMapping("/app/group-coverage/{id}/notice") @ResponseBody public ResponseEntity<byte[]> ownNotice(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id){return response(coverage.notice(a,id,true),"attachment","served-notice.pdf");}
    private ResponseEntity<byte[]> document(AppUserPrincipal a,UUID id,boolean own,String format,Locale locale){return response(pdf.render(coverage.document(a,id,own),locale),format.equals("print")?"inline":"attachment","coverage-case.pdf");}
    private static ResponseEntity<byte[]> response(byte[] bytes,String disposition,String name){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header("X-Content-Type-Options","nosniff").header("Content-Disposition",disposition+"; filename="+name).body(bytes);}
}
