package com.sacco.mvp.accounting.payroll.controller;

import com.sacco.mvp.accounting.payroll.dto.PayrollFeedbackDtos.*;
import com.sacco.mvp.accounting.payroll.service.*;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.MessageSource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Controller @RequiredArgsConstructor
@RequestMapping("/finance/payroll/{batchId}/feedback")
@PreAuthorize("@access.has(principal,'PAYROLL_DEDUCTIONS_VIEW')")
public class PayrollFeedbackController {
    private final PayrollFeedbackService feedback;
    private final PayrollService payroll;
    private final LoanRecordingService loans;
    private final ApplicationClock clock;
    private final MessageSource messages;
    @ModelAttribute void cache(HttpServletResponse r){r.setHeader("Cache-Control","no-store");}
    @InitBinder("remittanceForm") void bind(WebDataBinder b){b.setAllowedFields("moneyAccountId","receivedDate","receivedAmount","reference","evidence","verified");}
    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID batchId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="date")String sort,@RequestParam(defaultValue="desc")String direction,Model m){m.addAttribute("batch",payroll.batch(a,batchId));m.addAttribute("records",feedback.list(a,batchId,page,sort,direction));m.addAttribute("sort",sort);m.addAttribute("direction",direction);return "accounting/payroll/feedback-register";}
    @GetMapping("/new")
    public String uploadForm(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID batchId,Model m){payroll.authorize(a,"IMPORT");m.addAttribute("batch",payroll.batch(a,batchId));m.addAttribute("feedbackPageTitle","payroll.feedback.import");m.addAttribute("columns",PayrollFeedbackWorkbook.HEADERS);return "accounting/payroll/feedback-new";}
    @PostMapping("/new")
    public String upload(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID batchId,@RequestParam MultipartFile file,@RequestParam String evidence,Model m){
        payroll.authorize(a,"IMPORT");
        try{if(file.getSize()>PayrollFeedbackWorkbook.MAX_BYTES)throw new IllegalArgumentException("payroll.feedback.error.file");return "redirect:/finance/payroll/"+batchId+"/feedback/"+feedback.upload(a,batchId,file.getOriginalFilename(),file.getBytes(),evidence);}
        catch(java.io.IOException|IllegalArgumentException|DataIntegrityViolationException e){m.addAttribute("payrollError",safe(e));if(e instanceof PayrollFeedbackWorkbook.RowError row)m.addAttribute("feedbackErrorLine",row.line());m.addAttribute("evidence",evidence==null?"":evidence.substring(0,Math.min(500,evidence.length())));return uploadForm(a,batchId,m);}
    }
    @GetMapping("/{id}")
    public String details(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID batchId,@PathVariable UUID id,@RequestParam(defaultValue="0")int page,Model m){
        m.addAttribute("batch",payroll.batch(a,batchId));var f=feedback.get(a,batchId,id);m.addAttribute("feedback",f);m.addAttribute("remittance",feedback.remittance(a,batchId,id).orElse(null));m.addAttribute("records",feedback.rows(a,batchId,id,page));m.addAttribute("today",clock.today());
        if(!m.containsAttribute("remittanceForm")){var form=new RemittanceForm();form.setReceivedAmount(f.total());form.setReceivedDate(clock.today());m.addAttribute("remittanceForm",form);}
        var form=(RemittanceForm)m.getAttribute("remittanceForm");
        if(form.getMoneyAccountId()!=null){feedback.authorizeRemittance(a);m.addAttribute("selectedMoney",loans.receiptMoneyAccount(a,form.getMoneyAccountId()));}
        return "accounting/payroll/feedback-details";
    }
    @PostMapping("/{id}/prepare")
    public String prepare(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID batchId,@PathVariable UUID id,@ModelAttribute("remittanceForm")RemittanceForm form,BindingResult binding,Model m){
        feedback.authorizeRemittance(a);
        try{if(binding.hasErrors())throw new IllegalArgumentException("payroll.feedback.error.input");UUID receiptBatch=feedback.prepare(a,batchId,id,form);return "redirect:/finance/batches/receipts/"+receiptBatch;}
        catch(IllegalArgumentException|DataIntegrityViolationException e){m.addAttribute("payrollError",safe(e));return details(a,batchId,id,0,m);}
    }
    @GetMapping("/money") @ResponseBody
    public List<com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice> money(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID batchId,@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="0")int page){feedback.authorizeRemittance(a);payroll.batch(a,batchId);return loans.choices(a,"money",search,page);}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseBody
    public ResponseEntity<String> invalid(IllegalArgumentException e,Locale locale){return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body(messages.getMessage(safe(e),null,locale));}
    private String safe(Exception e){return e.getMessage()!=null && e.getMessage().matches("payroll\\.feedback\\.error\\.[A-Za-z]+")?e.getMessage():"payroll.feedback.error.input";}
}
