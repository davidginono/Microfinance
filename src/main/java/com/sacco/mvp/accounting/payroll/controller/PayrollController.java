package com.sacco.mvp.accounting.payroll.controller;

import com.sacco.mvp.accounting.payroll.dto.PayrollDtos.*;
import com.sacco.mvp.accounting.payroll.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/finance/payroll")
@PreAuthorize("@access.has(principal,'PAYROLL_DEDUCTIONS_VIEW')")
public class PayrollController {
    private final PayrollService payroll;
    private final PayrollPdfService pdf;
    private final ApplicationClock clock;
    private final MessageSource messages;
    @ModelAttribute void noCache(HttpServletResponse r) {r.setHeader("Cache-Control","no-store");}
    @InitBinder("employerForm") void employerBinder(WebDataBinder b) {b.setAllowedFields("requestKey","name","agreementReference");}
    @InitBinder("instructionForm") void instructionBinder(WebDataBinder b) {b.setAllowedFields("requestKey","employerId","loanId","evidence","monthlyLimit","effectiveFrom","effectiveTo");}
    @InitBinder("batchForm") void batchBinder(WebDataBinder b) {b.setAllowedFields("requestKey","employerId","month","deductionDate","includeArrears","acceptExceptions","previewHash","replacesId");}
    @GetMapping({"","/employers","/instructions"})
    public String register(@AuthenticationPrincipal AppUserPrincipal a,jakarta.servlet.http.HttpServletRequest request,
        @RequestParam(defaultValue="")String search,@RequestParam(defaultValue="")String state,@RequestParam(defaultValue="")String month,
        @RequestParam(defaultValue="0")int page,@RequestParam(required=false)String sort,@RequestParam(defaultValue="asc")String direction,Model m) {
        String path=request.getRequestURI();String kind=path.endsWith("/employers")?"employers":path.endsWith("/instructions")?"instructions":"batches";
        String sorting=sort==null?(kind.equals("employers")?"name":kind.equals("instructions")?"employer":"month"):sort;
        m.addAttribute("records",switch(kind){case "employers"->payroll.employers(a,search,page,sorting,direction);case "instructions"->payroll.instructions(a,search,page,sorting,direction);default->payroll.batches(a,search,state,month,page,sorting,direction);});
        m.addAttribute("kind",kind);m.addAttribute("base",base(kind));m.addAttribute("search",search);m.addAttribute("state",state);m.addAttribute("month",month);m.addAttribute("sort",sorting);m.addAttribute("direction",direction);return "accounting/payroll/register";
    }
    private String base(String kind) {return "/finance/payroll"+(kind.equals("batches")?"":"/"+kind);}
    @GetMapping("/employers/new")
    public String newEmployer(@AuthenticationPrincipal AppUserPrincipal a,Model m) {payroll.authorize(a,"CREATE");if(!m.containsAttribute("employerForm"))m.addAttribute("employerForm",new EmployerForm());m.addAttribute("kind","employers");return "accounting/payroll/setup";}
    @PostMapping("/employers/new")
    public String createEmployer(@AuthenticationPrincipal AppUserPrincipal a,@Valid @ModelAttribute("employerForm")EmployerForm f,BindingResult binding,Model m) {
        try{input(binding);return "redirect:/finance/payroll/employers/"+payroll.createEmployer(a,f);}catch(IllegalArgumentException|DataIntegrityViolationException e){error(m,e);return newEmployer(a,m);}
    }
    @GetMapping("/employers/{id}")
    public String employer(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m) {m.addAttribute("employer",payroll.employer(a,id));m.addAttribute("kind","employers");return "accounting/payroll/setup-details";}
    @GetMapping("/instructions/new")
    public String newInstruction(@AuthenticationPrincipal AppUserPrincipal a,Model m) {
        payroll.authorize(a,"CREATE");if(!m.containsAttribute("instructionForm")){var f=new InstructionForm();f.setEffectiveFrom(clock.today());f.setEffectiveTo(clock.today().plusYears(1));m.addAttribute("instructionForm",f);}
        var f=(InstructionForm)m.getAttribute("instructionForm");m.addAttribute("selectedEmployer",f.getEmployerId()==null?null:payroll.employer(a,f.getEmployerId()));m.addAttribute("selectedLoan",payroll.selectedLoan(a,f.getLoanId()));m.addAttribute("kind","instructions");return "accounting/payroll/setup";
    }
    @PostMapping("/instructions/new")
    public String createInstruction(@AuthenticationPrincipal AppUserPrincipal a,@Valid @ModelAttribute("instructionForm")InstructionForm f,BindingResult binding,Model m) {
        try{input(binding);return "redirect:/finance/payroll/instructions/"+payroll.createInstruction(a,f);}catch(IllegalArgumentException|DataIntegrityViolationException e){error(m,e);if(e instanceof IllegalArgumentException && "payroll.error.loan".equals(e.getMessage()))f.setLoanId(null);return newInstruction(a,m);}
    }
    @GetMapping("/instructions/{id}")
    public String instruction(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model m) {m.addAttribute("instruction",payroll.instruction(a,id));m.addAttribute("kind","instructions");return "accounting/payroll/setup-details";}
    @PostMapping("/instructions/{id}/stop")
    public String stop(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam String reason,RedirectAttributes flash) {
        try{payroll.stop(a,id,reason);}catch(IllegalArgumentException e){flash.addFlashAttribute("payrollError",safe(e));}return "redirect:/finance/payroll/instructions/"+id;
    }
    @GetMapping("/new")
    public String newBatch(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(required=false)UUID replacesId,Model m) {
        payroll.authorize(a,"CREATE");
        if(!m.containsAttribute("batchForm")) {
            var f=new BatchForm();f.setMonth(clock.currentYearMonth().toString());f.setDeductionDate(clock.currentYearMonth().atEndOfMonth());
            if(replacesId!=null){var old=payroll.batch(a,replacesId);if(!old.state().equals("CANCELLED"))throw new IllegalArgumentException("payroll.error.replacement");f.setReplacesId(replacesId);f.setMonth(old.month().toString().substring(0,7));f.setDeductionDate(old.deductionDate());}
            m.addAttribute("batchForm",f);
        }
        var f=(BatchForm)m.getAttribute("batchForm");m.addAttribute("selectedEmployer",f.getEmployerId()==null?null:payroll.employer(a,f.getEmployerId()));return "accounting/payroll/new";
    }
    @PostMapping({"/preview","/freeze"})
    public String batchCommand(@AuthenticationPrincipal AppUserPrincipal a,@Valid @ModelAttribute("batchForm")BatchForm f,BindingResult binding,
        jakarta.servlet.http.HttpServletRequest request,@RequestParam(defaultValue="0")int previewPage,Model m) {
        try {
            input(binding);
            if(request.getRequestURI().endsWith("/freeze"))return "redirect:/finance/payroll/"+payroll.freeze(a,f);
            var p=payroll.preview(a,f);f.setPreviewHash(p.hash());m.addAttribute("preview",p);
            if(previewPage<0 || previewPage>39)throw new IllegalArgumentException("payroll.error.input");
            m.addAttribute("previewPage",previewPage);m.addAttribute("previewRows",p.rows().stream().skip(previewPage*25L).limit(25).toList());m.addAttribute("previewHasNext",p.rows().size()>(previewPage+1)*25);
        }catch(IllegalArgumentException|DataIntegrityViolationException e){error(m,e);f.setPreviewHash("");}
        return newBatch(a,null,m);
    }
    @GetMapping("/{id}")
    public String details(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="0")int historyPage,Model m) {
        m.addAttribute("batch",payroll.batch(a,id));m.addAttribute("lines",payroll.lines(a,id,page));m.addAttribute("events",payroll.events(a,id,historyPage));return "accounting/payroll/details";
    }
    @PostMapping("/{id}/state")
    public String state(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam String kind,@RequestParam String evidence,
        @RequestParam(defaultValue="false")boolean withdrawalConfirmed,RedirectAttributes flash) {
        try{payroll.changeState(a,id,kind,evidence,withdrawalConfirmed);}catch(IllegalArgumentException e){flash.addFlashAttribute("payrollError",safe(e));}return "redirect:/finance/payroll/"+id;
    }
    @GetMapping("/choices/{kind}") @ResponseBody
    public List<Choice> choices(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable String kind,@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="0")int page) {
        payroll.authorize(a,"CREATE");return switch(kind){case "employers"->payroll.employerChoices(a,search,page);case "loans"->payroll.loanChoices(a,search,page).stream().map(l->new Choice(l.id(),l.label())).toList();default->throw new IllegalArgumentException("payroll.error.input");};
    }
    @GetMapping({"/{id}/pdf","/{id}/print"})
    public ResponseEntity<byte[]> document(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Locale locale,jakarta.servlet.http.HttpServletRequest request) {
        payroll.authorize(a,"EXPORT");String language=locale.getLanguage().equals("sw")?"sw":"en";var batch=payroll.batch(a,id);
        var document=payroll.document(a,id,language).orElseGet(()->{var bytes=pdf.render(batch,payroll.exportLines(a,id),Locale.forLanguageTag(language));return new Document(bytes,PayrollService.hash(bytes));});
        var saved=payroll.downloaded(a,id,language,document);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,(request.getRequestURI().endsWith("/print")?"inline":"attachment")+"; filename=payroll-"+id+".pdf")
            .header("X-Content-Type-Options","nosniff").header("X-Document-SHA256",saved.checksum()).body(saved.content());
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalid(IllegalArgumentException e,Locale locale) {return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body(messages.getMessage(safe(e),null,locale));}
    private void input(BindingResult result) {if(result.hasErrors())throw new IllegalArgumentException("payroll.error.input");}
    private void error(Model m,Exception e) {m.addAttribute("payrollError",safe(e));}
    private String safe(Exception e) {return e instanceof IllegalArgumentException && e.getMessage()!=null && e.getMessage().matches("payroll\\.error\\.[A-Za-z]+")?e.getMessage():"payroll.error.input";}
}
