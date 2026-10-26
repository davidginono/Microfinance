package com.sacco.mvp.grouplending.controller;

import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@Controller @RequiredArgsConstructor
public class GroupMemberStatementController {
    private final GroupMemberStatementService statements;
    private final GroupMemberStatementPdf pdf;
    @GetMapping("/app/group-loans/{id}/statement") public String client(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate through,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="0") int schedulePage,Model m){m.addAttribute("statement",statements.client(a,id,from,through,page,schedulePage,false));m.addAttribute("statementPath","/app/group-loans/"+id+"/statement");return "groups/member-statement";}
    @GetMapping("/finance/groups/cycles/{id}/allocations/{client}/statement") public String staff(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable UUID client,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate through,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="0") int schedulePage,Model m){m.addAttribute("statement",statements.staff(a,id,client,from,through,page,schedulePage,false));m.addAttribute("statementPath","/finance/groups/cycles/"+id+"/allocations/"+client+"/statement");return "groups/member-statement";}
    @GetMapping("/app/group-loans/{id}/statement/{format:pdf|print}") public ResponseEntity<byte[]> clientExport(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable String format,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate through,Locale locale){return document(pdf.render(statements.client(a,id,from,through,0,0,true),locale),format);}
    @GetMapping("/finance/groups/cycles/{id}/allocations/{client}/statement/{format:pdf|print}") public ResponseEntity<byte[]> staffExport(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable UUID client,@PathVariable String format,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate through,Locale locale){return document(pdf.render(statements.staff(a,id,client,from,through,0,0,true),locale),format);}
    private ResponseEntity<byte[]> document(byte[] bytes,String format){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header("Content-Disposition",(format.equals("print")?"inline":"attachment")+"; filename=group-member-statement.pdf").header("X-Content-Type-Options","nosniff").body(bytes);}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid(IllegalArgumentException e,Model m){m.addAttribute("groupError",e.getMessage()!=null&&e.getMessage().matches("group\\.error\\.[A-Za-z]+")?e.getMessage():"group.error.query");return "groups/error";}
}
