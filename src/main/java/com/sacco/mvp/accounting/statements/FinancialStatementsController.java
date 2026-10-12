package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.reporting.OperationalReportExportService.Format;
import com.sacco.mvp.reporting.execution.dto.StatementOutputDtos.Layout;
import com.sacco.mvp.reporting.execution.service.StatementOutputService;
import com.sacco.mvp.reporting.execution.service.StatementTypedExporter;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.ReportExportLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
import static com.sacco.mvp.accounting.statements.StatementDefinition.*;

@Controller @RequiredArgsConstructor @RequestMapping("/reports/statements")
public class FinancialStatementsController {
    private final StatementDesignerService statements;
    private final StatementOutputService outputs;
    private final StatementTypedExporter exporter;
    private final ReportExportLimiter limiter;
    private final ApplicationClock clock;

    @GetMapping public String index(@AuthenticationPrincipal AppUserPrincipal actor,
            @RequestParam(defaultValue="PROFIT_AND_LOSS") Kind kind,@RequestParam(required=false) UUID version,
            @RequestParam(defaultValue="0") int page,Model model){
        require(kind!=Kind.NOTES,"validation");
        var mappings=statements.versions(actor,kind,0);
        model.addAttribute("mappings",mappings.size()>25?mappings.subList(0,25):mappings);
        model.addAttribute("moreMappings",mappings.size()>25);
        var selected=version==null?mappings.stream().filter(v->v.state().equals("APPROVED")).findFirst().orElse(null):statements.version(actor,version);
        if(selected!=null){require(selected.definition().kind()==kind,"validation");model.addAttribute("selected",selected);}
        var results=statements.results(actor,kind,page);
        model.addAttribute("results",results.size()>25?results.subList(0,25):results);model.addAttribute("hasNext",results.size()>25);
        model.addAttribute("kind",kind);model.addAttribute("page",page);model.addAttribute("today",clock.today());
        model.addAttribute("from",clock.today().withDayOfMonth(1));return "reports/financial-statements";
    }

    @PostMapping("/versions/{id}/pdf") public ResponseEntity<byte[]> previewPdf(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable UUID id,@RequestParam LocalDate from,@RequestParam LocalDate through,
            @RequestParam(required=false) LocalDate comparisonFrom,@RequestParam(required=false) LocalDate comparisonThrough,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime cutoff,
            @RequestParam(defaultValue="false") boolean inline,Locale locale){
        statements.authorizeExport(actor);
        byte[] bytes=limiter.run(()->{var source=statements.preview(actor,id,from,through,comparisonFrom,comparisonThrough,cutoff);return exporter.exportDraft(source,layout(source.definition().kind(),locale));});
        return pdf(bytes,inline,"draft-statement.pdf");
    }

    @PostMapping("/results/{id}/pdf") public ResponseEntity<byte[]> finalizedPdf(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable UUID id,@RequestParam(defaultValue="false") boolean inline,Locale locale){
        statements.authorizeExport(actor);var source=statements.verifiedResult(actor,id);
        UUID output=outputs.capture(actor,id,layout(source.definition().kind(),locale));
        var artifact=outputs.artifacts(actor,output).stream().filter(a->a.format()==Format.PDF).findFirst().orElseThrow();
        return pdf(outputs.download(actor,output,artifact.id()).bytes(),inline,"financial-statement.pdf");
    }
    private static Layout layout(Kind kind,Locale locale){return new Layout(3,locale.getLanguage().equals("sw")?"sw":"en",kind==Kind.CHANGES_IN_EQUITY,false);}
    private static ResponseEntity<byte[]> pdf(byte[] bytes,boolean inline,String filename){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,(inline?"inline":"attachment")+"; filename="+filename).header("X-Content-Type-Options","nosniff").body(bytes);}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    String invalid(IllegalArgumentException failure,Model model){String key=failure.getMessage();model.addAttribute("statementError",key!=null&&(key.startsWith("statement.error.")||key.startsWith("accounting.release.error.")||key.startsWith("accounting.policy.error."))?key:"statement.error.validation");return "reports/statement-error";}
}
