package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.reports.LedgerReportExporter;
import com.sacco.mvp.accounting.reports.LedgerReportService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ReportExportLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.*;
import java.util.*;

/** Contextual financial-statement details; no separate report registry or client-supplied mapping. */
@Controller @RequiredArgsConstructor
@RequestMapping("/reports/statements/{source:versions|results}/{id}/lines/{line}")
public class StatementDrilldownController {
    private final StatementDesignerService statements;
    private final LedgerReportService ledger;
    private final LedgerReportExporter exporter;
    private final ReportExportLimiter limiter;

    public record Query(@DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,
            @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate comparisonFrom,
            @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate comparisonThrough,
            @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime cutoff,Integer page) { int pageNumber(){return page==null?0:page;} }

    @GetMapping("/trial-balance") public String trial(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable String source,@PathVariable UUID id,@PathVariable String line,@ModelAttribute Query query,Model model){
        var detail=resolve(actor,source,id,line,query,false);context(model,detail,source,id,line);
        return "reports/statement-trial";
    }
    @GetMapping("/trial-balance/pdf") public ResponseEntity<byte[]> trialPdf(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable String source,@PathVariable UUID id,@PathVariable String line,@ModelAttribute Query query,
            @RequestParam(defaultValue="false") boolean inline,Locale locale){
        byte[] bytes=limiter.run(()->exporter.renderTrial(resolve(actor,source,id,line,query,true).trial(),locale));
        return pdf(bytes,inline,"trial-balance.pdf");
    }
    @GetMapping("/accounts/{account}") public String account(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable String source,@PathVariable UUID id,@PathVariable String line,@PathVariable UUID account,
            @ModelAttribute Query query,Model model){
        var detail=resolve(actor,source,id,line,query,false);checkAccount(detail,account);context(model,detail,source,id,line);
        model.addAttribute("activity",ledger.accountActivity(actor,account,detail.trial().parameters(),false));
        return "reports/statement-account";
    }
    @GetMapping("/accounts/{account}/pdf") public ResponseEntity<byte[]> accountPdf(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable String source,@PathVariable UUID id,@PathVariable String line,@PathVariable UUID account,
            @ModelAttribute Query query,@RequestParam(defaultValue="false") boolean inline,Locale locale){
        var detail=resolve(actor,source,id,line,query,true);checkAccount(detail,account);
        return pdf(exporter.activity(actor,account,detail.trial().parameters(),false,LedgerReportExporter.Format.PDF,locale).bytes(),inline,"account-statement.pdf");
    }
    private StatementDesignerService.LineTrial resolve(AppUserPrincipal actor,String source,UUID id,String line,Query q,boolean export){
        return statements.lineTrial(actor,source,id,line,q.from(),q.through(),q.comparisonFrom(),q.comparisonThrough(),q.cutoff(),q.pageNumber(),export);
    }
    private void checkAccount(StatementDesignerService.LineTrial detail,UUID account){
        if(detail.trial().scope().institutionWide() || !detail.row().accounts().contains(account))throw new AccessDeniedException("Account outside statement branch or line");
    }
    private void context(Model model,StatementDesignerService.LineTrial detail,String source,UUID id,String line){
        var r=detail.result();model.addAttribute("detail",detail);model.addAttribute("result",r);model.addAttribute("trial",detail.trial());
        model.addAttribute("detailBase","/reports/statements/"+source+"/"+id+"/lines/"+line);
        model.addAttribute("detailQuery",UriComponentsBuilder.newInstance().queryParam("from",r.from()).queryParam("through",r.through())
            .queryParam("cutoff",r.recordedCutoff().toInstant()).queryParamIfPresent("comparisonFrom",Optional.ofNullable(r.comparisonFrom()))
            .queryParamIfPresent("comparisonThrough",Optional.ofNullable(r.comparisonThrough())).build().encode().toUriString());
    }
    private ResponseEntity<byte[]> pdf(byte[] bytes,boolean inline,String name){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION,(inline?"inline":"attachment")+"; filename="+name).header("X-Content-Type-Options","nosniff").body(bytes);}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    String invalid(Model model){model.addAttribute("statementError","statement.error.validation");return "reports/statement-error";}
}
