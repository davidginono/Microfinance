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

@Controller @RequiredArgsConstructor @RequestMapping("/finance/groups/cycles/{id}/statement")
public class GroupCycleStatementController {
    private final GroupCycleStatementService statements;
    private final GroupCycleStatementPdf pdf;
    @GetMapping public String screen(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate through,
        @RequestParam(defaultValue="0") int loanPage,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="0") int contributionPage,@RequestParam(defaultValue="0") int allocationPage,Model model,jakarta.servlet.http.HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");model.addAttribute("statement",statements.statement(a,id,from,through,loanPage,page,contributionPage,allocationPage,false));model.addAttribute("canExport",statements.canExport(a));return "groups/cycle-statement";
    }
    @GetMapping("/{format:pdf|print}") @ResponseBody public ResponseEntity<byte[]> document(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable String format,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate through,Locale locale){
        var statement=statements.statement(a,id,from,through,0,0,0,0,true);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header("Content-Disposition",(format.equals("print")?"inline":"attachment")+"; filename=\"group-cycle-statement.pdf\"").body(pdf.render(statement,locale));
    }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid(IllegalArgumentException e,Model model){model.addAttribute("groupError",Set.of("group.error.query","group.error.statementRange").contains(Objects.toString(e.getMessage(),""))?e.getMessage():"group.error.request");return "groups/error";}
}
