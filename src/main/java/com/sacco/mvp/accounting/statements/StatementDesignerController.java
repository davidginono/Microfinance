package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.reconciliation.ReconciliationService;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import static com.sacco.mvp.accounting.statements.StatementDefinition.*;

@Controller @RequiredArgsConstructor @RequestMapping("/reports/statements")
public class StatementDesignerController {
    private final StatementDesignerService service;
    private final ReconciliationService closing;
    private final ApplicationClock clock;
    private final ObjectMapper mapper;
    @GetMapping String index(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(required=false)UUID version,@RequestParam(defaultValue="0")int page,Model model){
        var versions=service.versions(actor,page);model.addAttribute("versions",versions.size()>25?versions.subList(0,25):versions);model.addAttribute("hasNext",versions.size()>25);model.addAttribute("page",page);
        var catalog=service.catalog(actor);model.addAttribute("catalogJson",mapper.writeValueAsString(catalog));model.addAttribute("emptyCatalog",catalog.isEmpty());
        StatementDefinition definition=new StatementDefinition(1,1,Kind.BALANCE_SHEET,"Financial statement","Taarifa ya fedha",List.of(new Row("HEADING","Financial position","Hali ya fedha",RowKind.HEADING,Section.ASSETS,Unit.NONE,List.of(),null,null,null,null,true,false,false,null)),List.of(),false);
        if(version!=null){var selected=service.version(actor,version);model.addAttribute("selected",selected);definition=selected.definition();}
        model.addAttribute("definitionJson",mapper.writeValueAsString(definition));model.addAttribute("today",clock.today());return "reports/statements";
    }
    @PostMapping("/versions") String save(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String definition,@RequestParam(required=false)UUID template,@RequestParam String evidence,RedirectAttributes flash,Model model,jakarta.servlet.http.HttpServletResponse response){
        require(definition.length()<=200000,"size");StatementDefinition parsed=mapper.readValue(definition,StatementDefinition.class);
        try{UUID id=service.save(actor,template,parsed,evidence);flash.addFlashAttribute("statementSuccess","statement.saved");return "redirect:/reports/statements?version="+id;}
        catch(IllegalArgumentException error){index(actor,null,0,model);model.addAttribute("definitionJson",mapper.writeValueAsString(parsed));model.addAttribute("draftTemplate",template);model.addAttribute("draftEvidence",evidence);model.addAttribute("statementError",validationKey(error));response.setStatus(400);return "reports/statements";}
    }
    @PostMapping("/versions/{id}/approve") String approve(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String evidence,RedirectAttributes flash){service.approve(actor,id,evidence);flash.addFlashAttribute("statementSuccess","statement.approved");return "redirect:/reports/statements?version="+id;}
    @PostMapping("/versions/{id}/retire") String retire(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,RedirectAttributes flash){service.retire(actor,id);flash.addFlashAttribute("statementSuccess","statement.retired");return "redirect:/reports/statements?version="+id;}
    @PostMapping("/versions/{id}/preview") String preview(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam LocalDate from,@RequestParam LocalDate through,@RequestParam(required=false)LocalDate comparisonFrom,@RequestParam(required=false)LocalDate comparisonThrough,Model model){
        model.addAttribute("result",service.preview(actor,id,from,through,comparisonFrom,comparisonThrough,clock.now()));return "reports/statement-result";
    }
    @PostMapping("/versions/{id}/finalize") String finalizeStatement(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam UUID closeReview,@RequestParam(required=false)UUID comparisonClose,@RequestParam(required=false)UUID priorResult,RedirectAttributes flash){UUID result=service.finalize(actor,id,closeReview,comparisonClose,priorResult);flash.addFlashAttribute("statementSuccess","statement.finalized");return "redirect:/reports/statements/results/"+result;}
    @PostMapping("/versions/{id}/finalize-institution") String finalizeInstitution(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam UUID period,@RequestParam(required=false)UUID comparisonPeriod,@RequestParam(required=false)UUID priorResult,RedirectAttributes flash){UUID result=service.finalizeInstitution(actor,id,period,comparisonPeriod,priorResult);flash.addFlashAttribute("statementSuccess","statement.finalized");return "redirect:/reports/statements/results/"+result;}
    @GetMapping("/results/{id}") String result(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,Model model){model.addAttribute("result",service.verifiedResult(actor,id));return "reports/statement-result";}
    @GetMapping("/versions/{id}/closing") String closeOptions(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam(defaultValue="0")int page,Model model){
        service.authorize(actor,UserClaim.STATEMENT_FINALIZE);model.addAttribute("version",service.version(actor,id));model.addAttribute("closes",closing.closes(actor,page));model.addAttribute("page",page);return "reports/statement-close";
    }
    @GetMapping("/versions/{id}/institution-closing") String institutionCloseOptions(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam(defaultValue="0")int page,Model model){model.addAttribute("institutionPeriods",service.institutionPeriods(actor,page));model.addAttribute("version",service.version(actor,id));model.addAttribute("page",page);return "reports/statement-institution-close";}
    @GetMapping("/regulatory") String regulatory(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0")int page,@RequestParam(required=false)UUID result,Model model){model.addAttribute("formats",service.formats(actor));if(result!=null)model.addAttribute("selectedResult",service.verifiedResult(actor,result));var rows=service.submissions(actor,page);model.addAttribute("submissions",rows.size()>25?rows.subList(0,25):rows);model.addAttribute("hasNext",rows.size()>25);model.addAttribute("page",page);return "reports/regulatory-statements";}
    @PostMapping("/regulatory") String submit(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID format,@RequestParam UUID result,@RequestParam org.springframework.web.multipart.MultipartFile file,@RequestParam String evidence,@RequestParam(required=false)UUID corrects,RedirectAttributes flash)throws java.io.IOException{require(file.getSize()<=2000000,"size");service.submit(actor,format,result,file.getOriginalFilename(),file.getBytes(),evidence,corrects);flash.addFlashAttribute("statementSuccess","statement.submissionSaved");return "redirect:/reports/statements/regulatory";}
    @GetMapping("/regulatory/{id}/file") org.springframework.http.ResponseEntity<byte[]> file(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id){return org.springframework.http.ResponseEntity.ok().header("Content-Disposition","attachment; filename=\"reviewed-submission\"").header("Cache-Control","no-store").contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM).body(service.submissionFile(actor,id));}
    @PostMapping("/regulatory/{id}/review") String review(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String evidence,RedirectAttributes flash){service.reviewSubmission(actor,id,evidence);flash.addFlashAttribute("statementSuccess","statement.submissionReviewed");return "redirect:/reports/statements/regulatory";}
    @ExceptionHandler({IllegalArgumentException.class,tools.jackson.core.JacksonException.class}) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    String validation(RuntimeException error,Model model){model.addAttribute("statementError",validationKey(error));return "reports/statement-error";}
    private static String validationKey(RuntimeException error){String key=error.getMessage();boolean known=key!=null && (key.startsWith("statement.error.") || key.startsWith("accounting.policy.error.") || key.startsWith("accounting.error.") || key.startsWith("reconciliation.error."));return known?key:"statement.error.validation";}
}
