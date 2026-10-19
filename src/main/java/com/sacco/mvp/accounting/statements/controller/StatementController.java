package com.sacco.mvp.accounting.statements.controller;

import com.sacco.mvp.accounting.statements.dto.StatementModels.*;
import com.sacco.mvp.accounting.statements.dto.StatementModels.Mapping;
import com.sacco.mvp.accounting.statements.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.ui.Model;
import org.springframework.http.*;
import java.time.*;
import java.util.*;

@Controller @RequiredArgsConstructor @RequestMapping("/reports/financial/statements")
public class StatementController {
    private final StatementService statements;
    private final StatementPresentation presentation;
    private final StatementExportService exporter;
    private final ApplicationClock clock;
    @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping public String configure(@AuthenticationPrincipal AppUserPrincipal actor,Model model){statements.authorize(actor,false);defaults(model);return "accounting/statements/configure";}
    private void defaults(Model model){model.addAttribute("from",clock.today().withDayOfYear(1));model.addAttribute("through",clock.today());model.addAttribute("today",clock.today());model.addAttribute("year",clock.today().getYear());model.addAttribute("years",java.util.stream.IntStream.rangeClosed(clock.today().getYear()-10,clock.today().getYear()).boxed().sorted(Comparator.reverseOrder()).toList());model.addAttribute("requestKey",UUID.randomUUID());model.addAttribute("kinds",List.of(Kind.POSITION,Kind.INCOME,Kind.CASH_FLOW,Kind.EQUITY,Kind.NOTES,Kind.TRIAL));}
    @PostMapping public String generate(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,@RequestParam(defaultValue="false") boolean institutionWide,@RequestParam UUID requestKey,@RequestParam(defaultValue="POSITION") Kind kind,Model model){
        try{if(!List.of(Kind.POSITION,Kind.INCOME,Kind.CASH_FLOW,Kind.EQUITY,Kind.NOTES,Kind.TRIAL).contains(kind))throw new IllegalArgumentException("fs.error.selection");return "redirect:/reports/financial/statements/"+statements.generate(actor,new Parameters(from,through,institutionWide),requestKey)+"?kind="+kind;}
        catch(IllegalArgumentException failure){defaults(model);model.addAttribute("from",from);model.addAttribute("through",through);model.addAttribute("institutionWide",institutionWide);model.addAttribute("requestKey",requestKey);model.addAttribute("kind",kind);model.addAttribute("errorKey",key(failure));return "accounting/statements/configure";}
    }
    @GetMapping("/{id}") public String preview(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam(defaultValue="POSITION") Kind kind,Model model,Locale locale){
        var run=statements.get(actor,id);context(run,model);model.addAttribute("kind",kind);model.addAttribute("document",presentation.document(run.snapshot(),kind,run.snapshot().balances(),locale));return "accounting/statements/preview";
    }
    @GetMapping("/{id}/trial") public String trial(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam(defaultValue="POSITION") Kind kind,@RequestParam(required=false) String line,Model model,Locale locale){
        var run=statements.get(actor,id);context(run,model);var accounts=statements.selected(run.snapshot(),kind,line);model.addAttribute("kind",Kind.TRIAL);model.addAttribute("selectionKind",kind);model.addAttribute("line",line);model.addAttribute("filtered",line!=null&&!line.isBlank());model.addAttribute("document",presentation.document(run.snapshot(),Kind.TRIAL,accounts,locale));return "accounting/statements/preview";
    }
    @GetMapping("/{id}/ledger/{account}") public String ledger(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@PathVariable UUID account,@RequestParam(defaultValue="0") int page,Model model,Locale locale){
        var run=statements.get(actor,id);context(run,model);var activity=statements.activity(actor,id,account,page);var b=run.snapshot().balances().stream().filter(a->a.id().equals(account)).findFirst().orElseThrow();
        model.addAttribute("kind",Kind.LEDGER);model.addAttribute("account",account);model.addAttribute("page",page);model.addAttribute("hasNext",activity.size()>30);model.addAttribute("document",presentation.ledger(run.snapshot(),b,activity.stream().limit(30).toList(),locale));return "accounting/statements/preview";
    }
    @PostMapping("/{id}/save") public String save(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam(defaultValue="POSITION") Kind kind){statements.save(actor,id);return "redirect:/reports/financial/statements/"+id+"?kind="+kind;}
    @GetMapping("/history") public String history(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="newest") String sort,Model model){var rows=statements.history(actor,page,sort);model.addAttribute("runs",rows.stream().limit(30).toList());model.addAttribute("page",page);model.addAttribute("sort",sort);model.addAttribute("hasNext",rows.size()>30);return "accounting/statements/history";}
    @GetMapping("/mappings") public String mappings(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="code") String sort,@RequestParam(defaultValue="asc") String direction,Model model){var rows=statements.mappings(actor,search,page,sort,direction);model.addAttribute("accounts",rows.stream().limit(30).toList());model.addAttribute("page",page);model.addAttribute("search",search);model.addAttribute("sort",sort);model.addAttribute("direction",direction);model.addAttribute("hasNext",rows.size()>30);return "accounting/statements/mappings";}
    @GetMapping("/mappings/{account}") public String mapping(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID account,Model model){var row=statements.mapping(actor,account);model.addAttribute("account",row);model.addAttribute("mapping",row.mapping());model.addAttribute("revision",row.revision());return "accounting/statements/mapping";}
    @PostMapping("/mappings/{account}") public String map(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID account,@RequestParam String line,@RequestParam String section,@RequestParam String cashSection,@RequestParam(defaultValue="") String equityComponent,@RequestParam(defaultValue="0") int note,@RequestParam(defaultValue="") String noteText,@RequestParam(required=false) UUID revision,Model model){
        var value=new Mapping(account,line.strip(),section,cashSection,equityComponent.strip(),note,noteText.strip());
        try{statements.map(actor,value,revision);return "redirect:/reports/financial/statements/mappings";}
        catch(IllegalArgumentException failure){mapping(actor,account,model);model.addAttribute("mapping",value);model.addAttribute("revision",revision);model.addAttribute("errorKey",key(failure));return "accounting/statements/mapping";}
    }
    @GetMapping("/{id}/export") public ResponseEntity<byte[]> export(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam(defaultValue="POSITION") Kind kind,@RequestParam(defaultValue="PDF") String format,@RequestParam(defaultValue="false") boolean inline,@RequestParam(defaultValue="POSITION") Kind selectionKind,@RequestParam(required=false) String line,@RequestParam(required=false) UUID account,Locale locale){
        if(!Set.of("PDF","XLSX").contains(format))throw new IllegalArgumentException("fs.error.format");var run=statements.get(actor,id);statements.exportAccess(actor,run);
        boolean filtered=line!=null&&!line.isBlank()||account!=null;String language="sw".equals(locale.getLanguage())?"sw":"en";
        byte[] bytes=filtered?null:statements.artifact(actor,id,kind,format,language).orElse(null);
        if(bytes==null){
            bytes=exporter.export(run.snapshot(),documents(actor,run,kind,selectionKind,line,account,locale),format,locale);if(!filtered)bytes=statements.retain(actor,id,kind,format,language,bytes);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .contentType(format.equals("PDF")?MediaType.APPLICATION_PDF:MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,(inline&&format.equals("PDF")?"inline":"attachment")+"; filename=statements-"+kind+"-"+run.through()+"."+format.toLowerCase(Locale.ROOT)).body(bytes);
    }
    @GetMapping("/{id}/print") public String print(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam(defaultValue="POSITION") Kind kind,@RequestParam(defaultValue="POSITION") Kind selectionKind,@RequestParam(required=false) String line,@RequestParam(required=false) UUID account,Model model,Locale locale,jakarta.servlet.http.HttpServletResponse response){
        var run=statements.get(actor,id);statements.exportAccess(actor,run);context(run,model);
        model.addAttribute("documents",documents(actor,run,kind,selectionKind,line,account,locale));
        response.setHeader("X-Content-Type-Options","nosniff");return "accounting/statements/print";
    }
    private List<StatementPresentation.Document> documents(AppUserPrincipal actor,Run run,Kind kind,Kind selectionKind,String line,UUID account,Locale locale){
        if(kind==Kind.ALL)return List.of(Kind.POSITION,Kind.INCOME,Kind.CASH_FLOW,Kind.EQUITY,Kind.NOTES,Kind.TRIAL).stream().map(k->presentation.document(run.snapshot(),k,run.snapshot().balances(),locale)).toList();
        if(kind==Kind.LEDGER){if(account==null)throw new IllegalArgumentException("fs.error.selection");var activity=statements.activityExport(actor,run.id(),account);var balance=run.snapshot().balances().stream().filter(a->a.id().equals(account)).findFirst().orElseThrow();return List.of(presentation.ledger(run.snapshot(),balance,activity,locale));}
        return List.of(presentation.document(run.snapshot(),kind,kind==Kind.TRIAL?statements.selected(run.snapshot(),selectionKind,line):run.snapshot().balances(),locale));
    }
    private void context(Run run,Model model){model.addAttribute("run",run);model.addAttribute("snapshot",run.snapshot());model.addAttribute("generatedLabel",clock.zoned(run.snapshot().generatedAt()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm z")));model.addAttribute("kinds",List.of(Kind.POSITION,Kind.INCOME,Kind.CASH_FLOW,Kind.EQUITY,Kind.NOTES,Kind.TRIAL));}
    private static String key(IllegalArgumentException failure){return failure.getMessage()!=null&&failure.getMessage().startsWith("fs.error.")?failure.getMessage():"fs.error.request";}
    @ExceptionHandler(IllegalArgumentException.class) public String error(IllegalArgumentException failure,Model model,jakarta.servlet.http.HttpServletResponse response){response.setStatus(400);model.addAttribute("errorKey",key(failure));return "accounting/statements/error";}
    @ExceptionHandler({org.springframework.dao.QueryTimeoutException.class,org.springframework.transaction.TransactionTimedOutException.class}) public String timeout(Model model,jakarta.servlet.http.HttpServletResponse response){response.setStatus(503);model.addAttribute("errorKey","fs.error.limit");return "accounting/statements/error";}
}
