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

@Controller @RequiredArgsConstructor @RequestMapping("/app/group-loans/{id}/organization-account")
public class GroupOrganizationAccountController {
    private final GroupOrganizationAccountService accounts;
    private final GroupCycleStatementPdf pdf;
    @GetMapping public String screen(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate through,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="0") int coveragePage,Model m,jakarta.servlet.http.HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");m.addAttribute("statement",accounts.statement(a,id,from,through,page,coveragePage,false));m.addAttribute("canExport",accounts.canExport(a));m.addAttribute("statementPath","/app/group-loans/"+id+"/organization-account");m.addAttribute("organizationAccount",true);return "groups/cycle-statement";
    }
    @GetMapping("/{format:pdf|print}") @ResponseBody public ResponseEntity<byte[]> document(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable String format,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate through,Locale locale){
        var statement=accounts.statement(a,id,from,through,0,true);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header("X-Content-Type-Options","nosniff").header("Content-Disposition",(format.equals("print")?"inline":"attachment")+"; filename=organization-account-statement.pdf").body(pdf.render(statement,locale));
    }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid(IllegalArgumentException e,Model m){m.addAttribute("groupError",Set.of("group.error.query","group.error.statementRange","coverage.error.documentRange").contains(Objects.toString(e.getMessage(),""))?e.getMessage():"group.error.query");return "groups/error";}
}
