package com.sacco.mvp.accounting.statements.controller;

import com.sacco.mvp.accounting.statements.dto.StatementModels.*;
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
    private static final String FRAGMENT_HEADER = "X-Statement-Fragment";
    private static final String PREVIEW = "accounting/statements/preview";

    private static String previewView(String fragment) {
        return "report".equals(fragment) ? PREVIEW+"-fragment" : PREVIEW;
    }
    private final StatementService statements;
    private final StatementPresentation presentation;
    private final StatementExportService exporter;
    private final ApplicationClock clock;
    @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping public String configure(@AuthenticationPrincipal AppUserPrincipal actor,Model model){statements.authorize(actor,false);defaults(model);return "accounting/statements/configure";}
    @GetMapping("/ledger") public String configureLedger(@AuthenticationPrincipal AppUserPrincipal actor,Model model){
        statements.authorize(actor,false);defaults(model);model.addAttribute("ledgerForm",true);
        return "accounting/statements/configure";
    }
    @GetMapping("/ledger/accounts") @ResponseBody
    public List<LedgerAccount> ledgerAccounts(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){
        return statements.ledgerAccounts(actor,search,page,null);
    }
    @PostMapping("/ledger") public String generateLedger(@AuthenticationPrincipal AppUserPrincipal actor,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,
            @RequestParam(required=false) UUID account,@RequestParam UUID requestKey,Model model){
        try{
            UUID run=statements.generateLedger(actor,from,through,account,requestKey);
            return "redirect:/reports/financial/statements/"+run+"/ledger"+(account==null?"":"/"+account);
        }catch(IllegalArgumentException failure){
            configureLedger(actor,model);
            model.addAttribute("from",from);model.addAttribute("through",through);model.addAttribute("requestKey",requestKey);
            model.addAttribute("year",null);model.addAttribute("errorKey",key(failure));
            if(account!=null)statements.ledgerAccounts(actor,"",0,account).stream().findFirst().ifPresent(choice->model.addAttribute("ledgerAccount",choice));
            return "accounting/statements/configure";
        }
    }
    private void defaults(Model model){model.addAttribute("from",clock.today().withDayOfYear(1));model.addAttribute("through",clock.today());model.addAttribute("today",clock.today());model.addAttribute("year",clock.today().getYear());model.addAttribute("years",java.util.stream.IntStream.rangeClosed(clock.today().getYear()-10,clock.today().getYear()).boxed().sorted(Comparator.reverseOrder()).toList());model.addAttribute("requestKey",UUID.randomUUID());model.addAttribute("kinds",List.of(Kind.POSITION,Kind.INCOME,Kind.CASH_FLOW,Kind.EQUITY,Kind.NOTES,Kind.TRIAL));}
    @PostMapping public String generate(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,@RequestParam(defaultValue="false") boolean institutionWide,@RequestParam UUID requestKey,@RequestParam(defaultValue="POSITION") Kind kind,Model model){
        try{if(!List.of(Kind.POSITION,Kind.INCOME,Kind.CASH_FLOW,Kind.EQUITY,Kind.NOTES,Kind.TRIAL).contains(kind))throw new IllegalArgumentException("fs.error.selection");return "redirect:/reports/financial/statements/"+statements.generate(actor,new Parameters(from,through,institutionWide),requestKey)+"?kind="+kind;}
        catch(IllegalArgumentException failure){defaults(model);model.addAttribute("from",from);model.addAttribute("through",through);model.addAttribute("institutionWide",institutionWide);model.addAttribute("requestKey",requestKey);model.addAttribute("kind",kind);model.addAttribute("errorKey",key(failure));return "accounting/statements/configure";}
    }
    @GetMapping("/{id}")
    public String preview(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,
                          @RequestParam(defaultValue="POSITION") Kind kind,@RequestParam(defaultValue="") String previewSearch,
                          @RequestHeader(name=FRAGMENT_HEADER,required=false) String fragment,Model model,Locale locale){
        var run=statements.get(actor,id);
        return statementPreview(run,kind,run.snapshot().balances(),previewSearch,fragment,model,locale);
    }
    @GetMapping("/{id}/trial")
    public String trial(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,
                        @RequestParam(defaultValue="POSITION") Kind kind,@RequestParam(required=false) String line,
                        @RequestParam(defaultValue="") String previewSearch,
                        @RequestHeader(name=FRAGMENT_HEADER,required=false) String fragment,Model model,Locale locale){
        var run=statements.get(actor,id);
        var accounts=statements.selected(run.snapshot(),kind,line);
        model.addAttribute("selectionKind",kind);model.addAttribute("line",line);
        model.addAttribute("filtered",line!=null&&!line.isBlank());
        return statementPreview(run,Kind.TRIAL,accounts,previewSearch,fragment,model,locale);
    }

    private String statementPreview(Run run,Kind kind,List<Balance> accounts,String search,String fragment,Model model,Locale locale){
        context(run,model);
        model.addAttribute("kind",kind);
        model.addAttribute("previewSearch",search);
        var document=presentation.document(run.snapshot(),kind,accounts,locale);
        if(!search.isBlank()){
            document=presentation.searchPreview(document,run.snapshot(),kind,statements.matchingAccounts(run,search),search);
            model.addAttribute("searchEmpty",document.rows().stream().noneMatch(row->!row.total() && !row.heading()));
        }
        model.addAttribute("document",document);
        return previewView(fragment);
    }

    @GetMapping({"/{id}/ledger","/{id}/ledger/{account}"})
    public String ledger(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,
                         @PathVariable(required=false) UUID account,@RequestParam(defaultValue="0") int page,
                         @RequestParam(defaultValue="") String previewSearch,
                         @RequestHeader(name=FRAGMENT_HEADER,required=false) String fragment,Model model,Locale locale){
        var run=statements.get(actor,id);context(run,model);
        var activity=statements.ledgerRows(actor,run,account,page,previewSearch);
        var rows=activity.stream().limit(30).toList();
        model.addAttribute("kind",Kind.LEDGER);model.addAttribute("account",account);model.addAttribute("page",page);
        model.addAttribute("hasNext",activity.size()>30);
        model.addAttribute("previewSearch",previewSearch);
        var document=account==null?presentation.ledger(run.snapshot(),rows,locale):
            presentation.ledger(run.snapshot(),run.snapshot().balances().stream().filter(b->b.id().equals(account)).findFirst().orElseThrow(),
                rows.stream().map(LedgerRow::activity).filter(Objects::nonNull).toList(),locale);
        model.addAttribute("document",document);
        model.addAttribute("searchEmpty",!previewSearch.isBlank() && rows.isEmpty());
        return previewView(fragment);
    }
    @PostMapping("/{id}/save")
    public String save(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,
            @RequestParam(defaultValue="POSITION") Kind kind,@RequestParam(required=false) Kind selectionKind,
            @RequestParam(required=false) String line,@RequestParam(required=false) UUID account,
            @RequestParam(defaultValue="0") int page){
        statements.save(actor,id);
        String base="/reports/financial/statements/"+id;
        var destination=org.springframework.web.util.UriComponentsBuilder.fromPath(base);
        if(kind==Kind.LEDGER){
            destination.path(account==null?"/ledger":"/ledger/{account}").queryParam("page",Math.max(0,page));
            return "redirect:"+(account==null?destination.build():destination.buildAndExpand(account)).encode().toUriString();
        }
        if(kind==Kind.TRIAL && selectionKind!=null){
            destination.path("/trial").queryParam("kind",selectionKind);
            if(line!=null&&!line.isBlank())destination.queryParam("line","{line}");
            return "redirect:"+destination.encode().buildAndExpand(line==null?"":line).toUriString();
        }
        return "redirect:"+destination.queryParam("kind",kind==Kind.LEDGER?Kind.POSITION:kind).build().toUriString();
    }
    @GetMapping("/history") public String history(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="newest") String sort,Model model){var rows=statements.history(actor,page,sort);model.addAttribute("runs",rows.stream().limit(30).toList());model.addAttribute("page",page);model.addAttribute("sort",sort);model.addAttribute("hasNext",rows.size()>30);return "accounting/statements/history";}
    @GetMapping("/{id}/export") public ResponseEntity<byte[]> export(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam(defaultValue="POSITION") Kind kind,@RequestParam(defaultValue="PDF") String format,@RequestParam(defaultValue="false") boolean inline,@RequestParam(defaultValue="POSITION") Kind selectionKind,@RequestParam(required=false) String line,@RequestParam(required=false) UUID account,Locale locale){
        if(!Set.of("PDF","XLSX").contains(format))throw new IllegalArgumentException("fs.error.format");var run=statements.get(actor,id);statements.exportAccess(actor,run);
        boolean filtered=kind==Kind.LEDGER||line!=null&&!line.isBlank()||account!=null;String language="sw".equals(locale.getLanguage())?"sw":"en";
        byte[] bytes=filtered?null:statements.artifact(actor,id,kind,format,language).orElse(null);
        if(bytes==null){
            bytes=exporter.export(run.snapshot(),document(actor,run,kind,selectionKind,line,account,locale),format,locale);if(!filtered)bytes=statements.retain(actor,id,kind,format,language,bytes);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .contentType(format.equals("PDF")?MediaType.APPLICATION_PDF:MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,(inline&&format.equals("PDF")?"inline":"attachment")+"; filename=statements-"+kind+"-"+run.through()+"."+format.toLowerCase(Locale.ROOT)).body(bytes);
    }
    @GetMapping("/{id}/print") public String print(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam(defaultValue="POSITION") Kind kind,@RequestParam(defaultValue="POSITION") Kind selectionKind,@RequestParam(required=false) String line,@RequestParam(required=false) UUID account,Model model,Locale locale,jakarta.servlet.http.HttpServletResponse response){
        var run=statements.get(actor,id);statements.exportAccess(actor,run);context(run,model);
        model.addAttribute("document",document(actor,run,kind,selectionKind,line,account,locale));
        response.setHeader("X-Content-Type-Options","nosniff");return "accounting/statements/print";
    }
    private StatementPresentation.Document document(AppUserPrincipal actor,Run run,Kind kind,Kind selectionKind,String line,UUID account,Locale locale){
        if(kind==Kind.LEDGER){
            var rows=statements.ledgerExport(actor,run,account);
            if(account==null)return presentation.ledger(run.snapshot(),rows,locale);
            var balance=run.snapshot().balances().stream().filter(a->a.id().equals(account)).findFirst().orElseThrow();
            return presentation.ledger(run.snapshot(),balance,rows.stream().map(LedgerRow::activity).filter(Objects::nonNull).toList(),locale);
        }
        return presentation.document(run.snapshot(),kind,kind==Kind.TRIAL?statements.selected(run.snapshot(),selectionKind,line):run.snapshot().balances(),locale);
    }
    private void context(Run run,Model model){model.addAttribute("equityPeriod",presentation.equityPeriod(run.snapshot(),org.springframework.context.i18n.LocaleContextHolder.getLocale()));model.addAttribute("run",run);model.addAttribute("snapshot",run.snapshot());model.addAttribute("generatedLabel",clock.zoned(run.snapshot().generatedAt()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm z")));model.addAttribute("kinds",List.of(Kind.POSITION,Kind.INCOME,Kind.CASH_FLOW,Kind.EQUITY,Kind.NOTES,Kind.TRIAL));}
    private static String key(IllegalArgumentException failure){return failure.getMessage()!=null&&failure.getMessage().startsWith("fs.error.")?failure.getMessage():"fs.error.request";}
    @ExceptionHandler(IllegalArgumentException.class) public String error(IllegalArgumentException failure,Model model,jakarta.servlet.http.HttpServletResponse response){response.setStatus(400);model.addAttribute("errorKey",key(failure));return "accounting/statements/error";}
    @ExceptionHandler({org.springframework.dao.QueryTimeoutException.class,org.springframework.transaction.TransactionTimedOutException.class}) public String timeout(Model model,jakarta.servlet.http.HttpServletResponse response){response.setStatus(503);model.addAttribute("errorKey","fs.error.limit");return "accounting/statements/error";}
}
