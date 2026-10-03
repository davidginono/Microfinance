package com.sacco.mvp.accounting.reconciliation;

import com.sacco.mvp.accounting.reconciliation.ReconciliationDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/finance")
public class ReconciliationController {
    private final ReconciliationService service;
    @GetMapping("/reconciliation")
    String index(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0") int page,
                 @RequestParam(required=false) LocalDate asOf,Model m) {
        LocalDate day=asOf==null?LocalDate.now():asOf;
        m.addAttribute("formats",service.formats(actor));m.addAttribute("statements",service.statements(actor,page));
        m.addAttribute("matches",service.matches(actor,page));m.addAttribute("exceptions",service.exceptions(actor,page));
        m.addAttribute("certificates",service.certificates(actor,page));m.addAttribute("balances",service.balances(actor,day,page));
        m.addAttribute("asOf",day);m.addAttribute("page",page);if(!m.containsAttribute("requestKey"))m.addAttribute("requestKey",UUID.randomUUID());return "accounting/reconciliation/index";
    }
    @PostMapping("/reconciliation/formats")
    String format(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String name,@RequestParam String evidence) {service.proposeFormat(actor,name,evidence);return "redirect:/finance/reconciliation";}
    @PostMapping("/reconciliation/formats/{id}/approve")
    String approveFormat(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String evidence) {service.approveFormat(actor,id,evidence);return "redirect:/finance/reconciliation";}
    @PostMapping("/reconciliation/import")
    String imported(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID requestKey,@RequestParam UUID account,
        @RequestParam UUID format,@RequestParam LocalDate from,@RequestParam LocalDate through,@RequestParam BigDecimal opening,
        @RequestParam BigDecimal closing,@RequestParam(defaultValue="") String filename,@RequestParam String evidence,@RequestParam(defaultValue="") String content,
        @RequestParam(required=false) org.springframework.web.multipart.MultipartFile statementFile,@RequestParam String action,Model m) {
        var c=new StatementCommand(requestKey,account,format,from,through,opening,closing,filename,evidence,content);
        boolean uploaded=statementFile!=null&&!statementFile.isEmpty();
        if("preview".equals(action)) {
            if(uploaded){var preview=service.previewUploadedStatement(actor,c,statementFile);m.addAttribute("preview",preview.rows());m.addAttribute("draft",preview.command());m.addAttribute("uploadedPreview",true);}
            else {m.addAttribute("preview",service.preview(actor,c));m.addAttribute("draft",c);}
            m.addAttribute("previewReady",true);m.addAttribute("requestKey",requestKey);return index(actor,0,through,m);
        }
        if(!"import".equals(action))throw new IllegalArgumentException("reconciliation.error.statement");return "redirect:/finance/reconciliation/statements/"+(uploaded?service.importUploadedStatement(actor,c,statementFile):service.importStatement(actor,c));
    }
    @GetMapping("/reconciliation/statements/{id}")
    String statement(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="0") int candidatePage,Model m) {
        m.addAttribute("statement",service.statement(actor,id));m.addAttribute("rows",service.rows(actor,id,page));m.addAttribute("candidates",service.candidates(actor,id,candidatePage));m.addAttribute("allocationIndexes",List.of(0,1,2,3,4,5,6,7));return "accounting/reconciliation/statement";
    }
    @GetMapping("/reconciliation/statements/{id}/evidence")
    org.springframework.http.ResponseEntity<byte[]> evidence(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id) {
        var file=service.statementFile(actor,id);return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,org.springframework.http.ContentDisposition.attachment().filename(file.filename(),java.nio.charset.StandardCharsets.UTF_8).build().toString())
            .eTag("\""+file.checksum()+"\"").body(file.content().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @PostMapping("/reconciliation/statements/{id}/match")
    String match(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String kind,@RequestParam String evidence,
                 @RequestParam List<String> statementLine,@RequestParam List<String> journalLine,@RequestParam List<String> amount) {
        service.statement(actor,id);if(statementLine.size()!=journalLine.size()||amount.size()!=journalLine.size())throw new IllegalArgumentException("reconciliation.error.allocations");
        var parts=new ArrayList<Allocation>();for(int i=0;i<statementLine.size();i++) {if(statementLine.get(i).isBlank()&&journalLine.get(i).isBlank()&&amount.get(i).isBlank())continue;
            try {parts.add(new Allocation(UUID.fromString(statementLine.get(i)),UUID.fromString(journalLine.get(i)),new BigDecimal(amount.get(i))));}catch(RuntimeException e){throw new IllegalArgumentException("reconciliation.error.allocations");}}
        service.proposeMatch(actor,kind,evidence,parts);return "redirect:/finance/reconciliation/statements/"+id;
    }
    @PostMapping("/reconciliation/matches/{id}/review")
    String reviewMatch(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam boolean approved,@RequestParam String evidence) {service.reviewMatch(actor,id,approved,evidence);return "redirect:/finance/reconciliation";}
    @PostMapping("/reconciliation/matches/{id}/reverse")
    String reverseMatch(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String evidence) {service.reverseMatch(actor,id,evidence);return "redirect:/finance/reconciliation";}
    @PostMapping("/reconciliation/exceptions")
    String exception(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID statementLine,@RequestParam String kind,@RequestParam String assigned,@RequestParam String evidence,@RequestParam(required=false) UUID journalLine) {if(journalLine!=null){if(!"TIMING".equals(kind))throw new IllegalArgumentException("reconciliation.error.kind");service.assignTimingExceptionToStaff(actor,statementLine,journalLine,assigned,evidence);}else service.assignExceptionToStaff(actor,statementLine,kind,assigned,evidence);return "redirect:/finance/reconciliation";}
    @PostMapping("/reconciliation/exceptions/{id}/review")
    String reviewException(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String evidence) {service.reviewException(actor,id,evidence);return "redirect:/finance/reconciliation";}
    @PostMapping("/reconciliation/certificates")
    String certificate(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID account,@RequestParam LocalDate asOf,@RequestParam String kind,@RequestParam BigDecimal source,@RequestParam String evidence) {service.certify(actor,account,asOf,kind,source,evidence);return "redirect:/finance/reconciliation?asOf="+asOf;}
    @PostMapping("/reconciliation/certificates/{id}/review")
    String reviewCertificate(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String evidence) {service.reviewCertificate(actor,id,evidence);return "redirect:/finance/reconciliation";}
    @GetMapping("/closing")
    String closing(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0") int page,@RequestParam(required=false) UUID period,Model m) {
        m.addAttribute("periods",service.periods(actor,page));m.addAttribute("reviews",service.closes(actor,page));m.addAttribute("selectedPeriod",period);
        if(period!=null)m.addAttribute("checks",service.closeChecks(actor,period));m.addAttribute("page",page);return "accounting/reconciliation/closing";
    }
    @PostMapping("/closing/reviews")
    String proposeClose(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID period,@RequestParam String evidence,@RequestParam(defaultValue="false") boolean reopen) {UUID id=service.proposeClose(actor,period,evidence,reopen);return "redirect:/finance/closing/reviews/"+id;}
    @GetMapping("/closing/reviews/{id}")
    String closeReview(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,Model m) {m.addAttribute("review",service.close(actor,id));return "accounting/reconciliation/review";}
    @PostMapping("/closing/reviews/{id}/approve")
    String approveClose(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String evidence,@RequestParam boolean confirmed) {confirm(confirmed);service.approveClose(actor,id,evidence);return "redirect:/finance/closing/reviews/"+id;}
    @PostMapping("/closing/complete")
    String completeClose(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID period,@RequestParam String evidence,@RequestParam boolean confirmed) {confirm(confirmed);service.completeInstitutionClose(actor,period,evidence);return "redirect:/finance/closing?period="+period;}
    private static void confirm(boolean value) {if(!value)throw new IllegalArgumentException("reconciliation.error.confirmation");}
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.dao.DataIntegrityViolationException.class})
    String error(Exception failure,Model m,HttpServletRequest request) {
        String key=failure.getMessage();m.addAttribute("reconciliationError",key!=null&&key.startsWith("reconciliation.error.")?key:"reconciliation.error.conflict");
        // Retain safe import values so failed validation can be corrected; no sensitive query parameters in redirects.
        if(request.getRequestURI().endsWith("/reconciliation/import"))for(String field:List.of("requestKey","account","format","from","through","opening","closing","filename","evidence","content"))m.addAttribute(field,request.getParameter(field));
        return "accounting/reconciliation/error";
    }
}
