package com.sacco.mvp.accounting.loans.controller;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.loans.service.*;
import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.SaccoLogoStorageService;
import com.sacco.mvp.accounting.reports.VoucherPdfDocument;
import com.sacco.mvp.accounting.reports.LoanAmortizationPdfDocument;
import com.sacco.mvp.accounting.reports.VoucherPdfDocument.Field;
import com.sacco.mvp.accounting.service.VoucherPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Controller @RequestMapping("/finance") @RequiredArgsConstructor
public class LoanRecordingController {
    private final LoanRecordingService service;
    private final LoanRecordingPdfService pdf;
    private final ApplicationClock clock;
    private final MessageSource messages;
    private final SaccoLogoStorageService logos;
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalidRequest(IllegalArgumentException exception,Model model){model.addAttribute("recordingError",error(exception));return "accounting/loans/error";}
    @InitBinder("recordForm") public void recordBinder(WebDataBinder b){b.setAllowedFields("requestKey","clientId","topUpSourceLoanId","productId","applicationDate","principal","months","tenureUnit","tenureValue","loanMode","interestEntry","interestBasis","interestPercent","interestAmount","customFrequency","customMethod","purpose","business","income","expenses","otherDebt","informationSource","guarantors","collateral","evidence","notes","customApplicationCharge","customProcessingCharge","insurancePremium","insuranceEvidence");}
    @GetMapping({"/loan-records","/loan-disbursements","/loan-repayments"})
    public String register(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="") String search,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,
        @RequestParam(defaultValue="") String state,@RequestParam(defaultValue="newest") String sort,@RequestParam(defaultValue="0") int page,@RequestParam(required=false) UUID productId,
        jakarta.servlet.http.HttpServletRequest request,Model model){
        String kind=kind(request);var f=new Filter(search,from,through,state,sort,page,null,productId);context(kind,model);model.addAttribute("filter",f);
        if(productId!=null)model.addAttribute("selectedProduct",service.filterProduct(a,productId));
        if(kind.equals("LOAN"))model.addAttribute("records",service.list(a,f));else model.addAttribute("records",service.register(a,kind,f));return "accounting/loans/register";
    }
    @GetMapping("/loan-records/new") public String create(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(required=false) UUID topUpLoanId,Model model){service.authorize(a,"CREATE");var f=new RecordForm();f.setApplicationDate(clock.today());f.setTenureUnit("");
        if(topUpLoanId!=null){var q=service.topUpQuote(a,topUpLoanId,clock.today(),null);f.setTopUpSourceLoanId(topUpLoanId);f.setClientId(q.clientId());model.addAttribute("topUpQuote",q);}
        model.addAttribute("recordForm",f);return "accounting/loans/record-form";}
    @PostMapping("/loan-records") public String save(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute RecordForm recordForm,BindingResult errors,@RequestParam(required=false) List<org.springframework.web.multipart.MultipartFile> attachments,@RequestParam(defaultValue="save") String action,Model model,RedirectAttributes flash){
        service.authorize(a,"CREATE");if(!errors.hasErrors())try{
            if(action.equals("preview")){model.addAttribute("quote",service.preview(a,recordForm));recordProductContext(a,recordForm,model);return "accounting/loans/record-form";}
            if(!action.equals("save"))throw new IllegalArgumentException("recording.error.request");UUID id=service.save(a,recordForm,attachments==null?List.of():attachments);flash.addFlashAttribute("recordingSaved",true);return "redirect:/finance/loan-records/"+id;
        }catch(IllegalArgumentException e){errors.reject(error(e));}catch(DataIntegrityViolationException e){errors.reject("recording.error.duplicate");}
        if(recordForm.getTopUpSourceLoanId()!=null)try{model.addAttribute("topUpQuote",service.topUpQuote(a,recordForm.getTopUpSourceLoanId(),recordForm.getApplicationDate(),null));}catch(IllegalArgumentException ex){model.addAttribute("topUpError",error(ex));}
        recordProductContext(a,recordForm,model);
        return "accounting/loans/record-form";
    }
    private void recordProductContext(AppUserPrincipal a,RecordForm form,Model model){
        if("CONFIGURED".equals(form.getLoanMode()) && form.getProductId()!=null)
            try{model.addAttribute("selectedProduct",service.recordProduct(a,form.getProductId()));}catch(IllegalArgumentException ignored){/* Keep the original validation error for an unavailable product. */}
    }
    @GetMapping("/loan-recording/top-up-quote") @ResponseBody
    public ResponseEntity<?> topUpQuote(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam UUID source,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam(required=false) BigDecimal principal,Locale locale){
        service.authorize(a,"CREATE");try{var q=service.topUpQuote(a,source,date,null);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("principal",q.principal().toPlainString(),"interest",q.interest().toPlainString(),"excludedInterest",q.excludedInterest().toPlainString(),"total",q.total().toPlainString(),"charges",q.charges().toPlainString(),"chargeSequence",q.chargeSequence(),"cash",(principal==null?BigDecimal.ZERO:principal.subtract(q.total())).toPlainString()));}
        catch(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("error",label(error(e),locale)));}
    }
    @PostMapping("/loan-records/{id}/reverse-top-up") public String reverseTopUp(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam UUID requestKey,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam String reason,@RequestParam String evidence,RedirectAttributes flash){
        try{service.reverseTopUp(a,id,requestKey,date,reason,evidence);flash.addFlashAttribute("recordingSaved",true);}catch(IllegalArgumentException e){flash.addFlashAttribute("topUpCorrectionError",error(e));flash.addFlashAttribute("topUpCorrectionDate",date);flash.addFlashAttribute("topUpReason",reason);flash.addFlashAttribute("topUpEvidence",evidence);flash.addFlashAttribute("topUpReverseKey",requestKey);}return "redirect:/finance/loan-records/"+id;
    }
    @PostMapping("/loan-recording/quote") @ResponseBody
    public ResponseEntity<?> quote(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute RecordForm recordForm,BindingResult errors,Locale locale){
        service.authorize(a,"CREATE");if(errors.hasErrors())return ResponseEntity.badRequest().body(Map.of("error",label("recording.error.terms",locale)));
        try{var q=service.interestQuote(a,recordForm);var response=new LinkedHashMap<String,Object>(Map.of("product",q.product(),"rate",q.rate().toPlainString(),"ratePercent",q.rate().multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString(),"method",q.method(),"frequency",q.frequency(),"installment",q.installment().toPlainString(),"interest",q.interest().toPlainString(),"tenureUnit",q.tenureUnit(),"tenureValue",q.tenureValue(),"schedule",q.schedule().stream().map(r->Map.of("number",r.number(),"principal",r.principal().toPlainString(),"interest",r.interest().toPlainString(),"amount",r.amount().toPlainString(),"beginningBalance",r.beginningBalance().toPlainString(),"endingBalance",r.endingBalance().toPlainString())).toList()));var chargePlan=service.chargeQuote(a,recordForm);if(chargePlan!=null)response.put("charges",chargeView(a,recordForm,chargePlan,locale));return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);}
        catch(IllegalArgumentException ex){return ResponseEntity.badRequest().body(Map.of("error",label(error(ex),locale)));}
    }
    @GetMapping("/loan-records/{id}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> attachment(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@PathVariable String attachmentId){var file=service.attachment(a,id,attachmentId);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(file.getContentType())).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.getOriginalName(),java.nio.charset.StandardCharsets.UTF_8).build().toString()).header("X-Content-Type-Options","nosniff").body(file.getContent());}
    @GetMapping("/loan-records/{id}") public String detail(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="0") int historyPage,@RequestParam(defaultValue="0") int schedulePage,@RequestParam(defaultValue="0") int chargePage,Model model){if(schedulePage<0 || schedulePage>24)throw new IllegalArgumentException("recording.error.filter");var d=service.detail(a,id);model.addAttribute("detail",d);model.addAttribute("chargeSummary",service.chargeSummary(a,id,chargePage));model.addAttribute("chargeRequestKey",UUID.randomUUID());model.addAttribute("topUpPosting",service.topUp(a,id).orElse(null));model.addAttribute("sourceTopUp",service.sourceTopUp(a,id).orElse(null));model.addAttribute("topUpReverseKey",model.containsAttribute("topUpReverseKey")?model.getAttribute("topUpReverseKey"):UUID.randomUUID());model.addAttribute("today",clock.today());if(model.getAttribute("topUpPosting")==null)try{model.addAttribute("topUpQuote",service.recordedTopUpQuote(a,id).orElse(null));}catch(IllegalArgumentException e){model.addAttribute("topUpError",error(e));}model.addAttribute("attachments",service.attachments(a,id));model.addAttribute("history",service.history(a,id,historyPage));model.addAttribute("schedulePage",schedulePage);model.addAttribute("scheduleRows",d.schedule().stream().skip(schedulePage*25L).limit(25).toList());model.addAttribute("scheduleHasNext",d.schedule().size()>(schedulePage+1)*25);return "accounting/loans/details";}
    @GetMapping({"/loan-disbursements/new","/loan-repayments/new"}) public String postingForm(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(required=false) UUID loanId,jakarta.servlet.http.HttpServletRequest request,Model model) {
        if(kind(request).equals("PAYMENT")){service.authorize(actor,"POST");if(loanId!=null)service.receiptLoan(actor,loanId);return "redirect:/finance/vouchers/receipts/new?purpose=LOAN_REPAYMENT"+(loanId==null?"":"&loanId="+loanId);}
        service.authorize(actor,"VIEW");if(loanId!=null)service.detail(actor,loanId);
        return "redirect:/disbursement/loan-applications"+(loanId==null?"":"/"+loanId);
    }
    @PostMapping({"/loan-disbursements","/loan-repayments"}) public String post(jakarta.servlet.http.HttpServletRequest request) {
        throw new org.springframework.web.server.ResponseStatusException(HttpStatus.GONE,"Use the staff disbursement review or receipt voucher form");
    }
    @GetMapping({"/loan-postings/{id}","/loan-disbursements/{id}","/loan-repayments/{id}"}) public String posting(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="0") int schedulePage,Model model){var receipt=service.receiptForPosting(a,id);if(receipt.isPresent())return "redirect:/finance/vouchers/receipts/"+receipt.get();postingModel(a,id,schedulePage,model);return "accounting/loans/posting";}
    private void postingModel(AppUserPrincipal a,UUID id,int schedulePage,Model model){
        if(schedulePage<0 || schedulePage>=24)throw new IllegalArgumentException("recording.error.filter");
        var p=service.posting(a,id);model.addAttribute("posting",p);model.addAttribute("chargeSummary",service.chargeSummary(a,p.loanId(),0));model.addAttribute("canReverse",!p.actorId().equals(a.getMemberId()));model.addAttribute("reverseKey",UUID.randomUUID());model.addAttribute("today",clock.today());
        if("DISBURSEMENT".equals(p.kind())){model.addAttribute("actualSchedule",service.actualRepaymentSchedule(a,p.loanId(),schedulePage).orElse(null));model.addAttribute("disbursementCorrection",service.disbursementCorrection(a,id));}
    }
    @PostMapping("/loan-postings/{id}/reverse") public String reverse(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam UUID requestKey,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate effectiveDate,@RequestParam String reason,Model model){
        var receipt=service.receiptForPosting(a,id);if(receipt.isPresent())return "redirect:/finance/vouchers/receipts/"+receipt.get();
        try{return "redirect:/finance/loan-postings/"+service.reverse(a,id,requestKey,effectiveDate,reason).id();}catch(IllegalArgumentException e){model.addAttribute("reverseError",error(e));}catch(DataIntegrityViolationException e){model.addAttribute("reverseError","recording.error.duplicate");}
        postingModel(a,id,0,model);model.addAttribute("reverseKey",requestKey);model.addAttribute("reason",reason);model.addAttribute("today",effectiveDate);return "accounting/loans/posting";
    }
    @GetMapping("/loan-recording/choices/{category}") @ResponseBody public List<Choice> choices(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable String category,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return service.choices(a,category,search,page);}
    @GetMapping("/client-ledgers") public String clients(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,Model model){model.addAttribute("clients",service.choices(a,"ledger-clients",search,page));model.addAttribute("search",search);model.addAttribute("page",page);return "accounting/loans/clients";}
    @GetMapping("/client-ledgers/{id}") public String statement(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="0") int movementPage,Model model){model.addAttribute("movements",service.movements(a,id,movementPage));model.addAttribute("statement",service.statement(a,id,page));model.addAttribute("clientId",id);return "accounting/loans/statement";}
    @GetMapping({"/loan-records/{id}/pdf","/loan-records/{id}/print"}) public Object loanExport(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,jakarta.servlet.http.HttpServletRequest request,Locale locale,Model model){
        var data=service.exportLoan(a,id);var document=amortizationDocument(data,locale);
        var topUp=service.topUp(a,id);if(topUp.isPresent())return output(topUpDocument(topUp.get(),data.detail(),locale,null,data.charges()),request,model);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,(request.getRequestURI().endsWith("/print")?"inline":"attachment")+"; filename=loan-"+data.detail().loan().number()+".pdf")
            .header("X-Content-Type-Options","nosniff").body(pdf.renderAmortization(document));
    }
    @GetMapping({"/loan-postings/{id}/pdf","/loan-postings/{id}/print","/loan-disbursements/{id}/pdf","/loan-disbursements/{id}/print","/loan-repayments/{id}/pdf","/loan-repayments/{id}/print"})
    public Object postingExport(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,jakarta.servlet.http.HttpServletRequest request,Locale locale,Model model){
        var receipt=service.receiptForPosting(a,id);
        if(receipt.isPresent())return "redirect:/finance/vouchers/receipts/"+receipt.get()+(request.getRequestURI().endsWith("/print")?"/print":"/pdf");
        service.authorize(a,"EXPORT");var p=service.posting(a,id);var d=service.detail(a,p.loanId());
        var topUp=Set.of("DISBURSEMENT","FUNDING_UNDO").contains(p.kind())?service.topUp(a,p.loanId()):Optional.<TopUpPosting>empty();
        if(topUp.isEmpty() && p.transactionId()!=null)topUp=service.transactionTopUp(a,p.transactionId());
        if(topUp.isPresent())return output(topUpDocument(topUp.get(),service.detail(a,topUp.get().loanId()),locale,p,service.exportLoan(a,topUp.get().loanId()).charges()),request,model);
        if(Set.of("CHARGE_ASSESS","CHARGE_UNDO").contains(p.kind())){var lines=new ArrayList<String>();lines.add(label("recording.document."+p.kind(),locale)+" | TZS "+statementMoney(p.amount(),locale));lines.add(label("loan.loanId",locale)+": "+p.loanNumber());lines.add(label("voucher.date",locale)+": "+p.date());lines.add(label("voucher.evidence",locale)+": "+p.evidence());lines.add(label("repayment.reason",locale)+": "+p.notes());for(var field:chargeFields(service.exportLoan(a,p.loanId()).charges(),locale))lines.add(field.label()+": "+field.value());return output(new Document(label("charges.title",locale),d.institution(),d.branch(),"loan-charge-assessment.pdf",lines),request,model);}
        byte[] logo=logos.hasLogo(a.getSaccoId())?logos.load(a.getSaccoId()).content():null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,(request.getRequestURI().endsWith("/print")?"inline":"attachment")+"; filename=loan-posting-"+p.id()+".pdf")
            .header("X-Content-Type-Options","nosniff").body(pdf.renderPosting(postDocument(p,d,locale,logo,service.postingLines(a,id))));
    }
    @GetMapping({"/client-ledgers/{id}/pdf","/client-ledgers/{id}/print"}) public Object statementExport(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,jakarta.servlet.http.HttpServletRequest request,Locale locale,Model model){
        var document=statementDocument(service.exportStatement(a,id),locale);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,(request.getRequestURI().endsWith("/print")?"inline":"attachment")+"; filename=client-statement.pdf")
            .header("X-Content-Type-Options","nosniff").body(pdf.renderStatement(document));
    }
    @GetMapping("/client-ledgers/{client}/loans/{loan}/statement") public String loanStatement(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID client,@PathVariable UUID loan,@RequestParam(defaultValue="0") int page,Locale locale,Model model){
        var source=service.loanStatement(actor,client,loan,page,false);
        model.addAttribute("document",statementDocument(source.source(),locale,source.opening()));model.addAttribute("statementPage",source);
        model.addAttribute("statementBase","/finance/client-ledgers/"+client+"/loans/"+loan+"/statement");return "accounting/loans/loan-statement";
    }
    @GetMapping({"/client-ledgers/{client}/loans/{loan}/statement/pdf","/client-ledgers/{client}/loans/{loan}/statement/print"}) public ResponseEntity<byte[]> loanStatementPdf(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID client,@PathVariable UUID loan,jakarta.servlet.http.HttpServletRequest request,Locale locale){
        var source=service.loanStatement(actor,client,loan,0,true);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,(request.getRequestURI().endsWith("/print")?"inline":"attachment")+"; filename=loan-statement.pdf")
            .header("X-Content-Type-Options","nosniff").body(pdf.renderStatement(statementDocument(source.source(),locale,source.opening())));
    }
    private StatementDocument statementDocument(LoanRecordingService.StatementExport source,Locale locale){return statementDocument(source,locale,null);}
    private StatementDocument statementDocument(LoanRecordingService.StatementExport source,Locale locale,Posting opening){
        String unavailable=label("recording.unavailable",locale);String[] client=source.client().split(" - ",2);
        var identity=List.of(new StatementField(label("recording.statement.clientNumber",locale),client[0]),
            new StatementField(label("recording.statement.name",locale),client.length>1?client[1]:source.client()),
            new StatementField(label("recording.asOf",locale),statementDate(clock.today(),locale)),
            new StatementField(label("recording.statement.currency",locale),"TZS"));
        var terms=source.terms().stream().collect(java.util.stream.Collectors.toMap(StatementTerms::loanId,t->t));
        var posts=source.movements().stream().collect(java.util.stream.Collectors.groupingBy(Posting::loanId,LinkedHashMap::new,java.util.stream.Collectors.toList()));
        var columns=List.of("date","reference","documentNumber","activity","accountCode","description","debit","credit","interest","balance")
            .stream().map(key->label("recording.statement."+key,locale)).toList();
        var sections=new ArrayList<StatementSection>();
        for(var loan:source.loans()){
            var term=terms.get(loan.id());var movements=posts.getOrDefault(loan.id(),List.of());
            var disbursement=opening!=null?opening:movements.stream().filter(p->"DISBURSEMENT".equals(p.kind())).findFirst().orElse(null);
            var fields=new ArrayList<StatementField>();
            statementField(fields,"loan",loan.number(),locale);statementField(fields,"product",loan.product(),locale);
            statementField(fields,"disbursementDate",disbursement==null?unavailable:statementDate(disbursement.date(),locale),locale);
            statementField(fields,"fund",unavailable,locale);statementField(fields,"principal",statementMoney(loan.principal(),locale),locale);
            statementField(fields,"months",term==null?unavailable:term.tenureValue()+" "+label("recording.tenure."+term.tenureUnit(),locale),locale);
            statementField(fields,"installments",term==null || term.installments()<1?unavailable:Integer.toString(term.installments()),locale);
            statementField(fields,"rateAnnual",term==null || term.rate()==null || "AMOUNT".equals(term.rateBasis())?unavailable:statementMoney(term.rate().multiply(new BigDecimal("100")),locale)+"% "+label("recording.basis."+term.rateBasis(),locale),locale);
            statementField(fields,"firstDate",term==null?unavailable:statementDate(term.firstPaymentDate(),locale),locale);
            statementField(fields,"lastDate",term==null?unavailable:statementDate(term.lastPaymentDate(),locale),locale);
            statementField(fields,"installment",term==null || term.frequency()==null?unavailable:statementMoney(term.installment(),locale)+" / "+label("recording.frequency."+term.frequency(),locale),locale);
            statementField(fields,"opening",disbursement==null?unavailable:statementMoney(disbursement.principalBalance(),locale),locale);
            statementField(fields,"disbursed",disbursement==null?unavailable:statementMoney(disbursement.amount(),locale),locale);
            statementField(fields,"outstanding",disbursement==null?unavailable:statementMoney(loan.outstandingPrincipal(),locale),locale);
            statementField(fields,"dueInterest",disbursement==null?unavailable:statementMoney(loan.dueInterest(),locale),locale);
            statementField(fields,"futureInterest",disbursement==null?unavailable:statementMoney(loan.futureInterest(),locale),locale);
            fields.add(new StatementField(label("charges.outstanding",locale),statementMoney(loan.charges(),locale)));
            if(term!=null && !term.chargeSnapshot().isBlank())try{var plan=new tools.jackson.databind.json.JsonMapper().readValue(term.chargeSnapshot(),com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Plan.class);for(var field:chargeFields(plan,locale))fields.add(new StatementField(field.label(),field.value()));}catch(tools.jackson.core.JacksonException ex){throw new IllegalStateException("Invalid frozen charge terms",ex);}
            statementField(fields,"arrears",disbursement==null?unavailable:statementMoney(loan.arrears(),locale),locale);
            statementField(fields,"branch",term==null?unavailable:term.institution()+" / "+term.branch(),locale);
            var rows=new ArrayList<List<String>>();
            for(var post:movements){
                boolean credit=Set.of("PAYMENT","SETTLEMENT","FUNDING_UNDO").contains(post.kind());String account=Objects.toString(post.moneyAccount(),"");
                String code=account.contains(" - ")?account.split(" - ",2)[0]:unavailable;
                String description=post.notes()==null || post.notes().isBlank()?account:post.notes();if(post.charges().signum()>0)description+=" / "+label("charges.title",locale)+": "+statementMoney(post.charges(),locale);if(Set.of("CHARGE_ASSESS","CHARGE_UNDO").contains(post.kind()))description+=" / TZS "+statementMoney(post.amount(),locale);
                rows.add(List.of(statementDate(post.date(),locale),post.reference(),unavailable,label("recording.document."+post.kind(),locale),code,description,
                    credit?"":statementMoney(post.principal(),locale),credit?statementMoney(post.principal(),locale):"",
                    statementMoney(Set.of("REVERSAL","SETTLEMENT_UNDO").contains(post.kind())?post.interest().negate():post.interest(),locale),statementMoney(post.principalBalance(),locale)));
            }
            sections.add(new StatementSection(fields,columns,rows));
        }
        return new StatementDocument(label("recording.statement",locale).toUpperCase(locale),identity,sections,
            label("recording.statement.empty",locale),label("recording.statement.note",locale)+" "+label("charges.notPaid",locale));
    }
    private void statementField(List<StatementField> fields,String key,String value,Locale locale){fields.add(new StatementField(label("recording.statement."+key,locale),value));}
    private String statementDate(LocalDate value,Locale locale){return value==null?label("recording.unavailable",locale):value.format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-uuuu",locale)).toUpperCase(locale);}
    private String statementMoney(BigDecimal value,Locale locale){if(value==null)return label("recording.unavailable",locale);var format=java.text.NumberFormat.getNumberInstance(locale);format.setMinimumFractionDigits(2);format.setMaximumFractionDigits(2);return format.format(value);}
    @GetMapping({"/loan-disbursements/report/pdf","/loan-disbursements/report/print","/loan-repayments/report/pdf","/loan-repayments/report/print"})
    public Object report(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="") String search,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,@RequestParam(defaultValue="") String state,@RequestParam(defaultValue="newest") String sort,@RequestParam(required=false) UUID productId,jakarta.servlet.http.HttpServletRequest request,Locale locale,Model model){
        String kind=kind(request);Choice selectedProduct=productId==null?null:service.filterProduct(a,productId);var rows=service.report(a,kind,new Filter(search,from,through,state,sort,0,null,productId));var lines=new ArrayList<String>();lines.add(label("recording.asOf",locale)+": "+clock.today());lines.add(label("voucher.from",locale)+": "+Objects.toString(from,"")+" | "+label("voucher.through",locale)+": "+Objects.toString(through,""));
        if(selectedProduct!=null)lines.add(label("recording.product",locale)+": "+selectedProduct.label());
        BigDecimal total=BigDecimal.ZERO;for(var p:rows){total=total.add(Set.of("REVERSAL","FUNDING_UNDO").contains(p.kind())?p.amount().negate():p.amount());lines.add(p.date()+" | "+p.loanNumber()+" | "+p.client()+" | "+p.reference()+" | TZS "+(Set.of("REVERSAL","FUNDING_UNDO").contains(p.kind())?p.amount().negate():p.amount())+" | "+label("recording.principal",locale)+": "+p.principal()+" | "+label("recording.interest",locale)+": "+p.interest()+" | "+label("charges.title",locale)+": "+p.charges()+" | "+label(Set.of("REVERSAL","FUNDING_UNDO").contains(p.kind())?"voucher.status.REVERSAL":p.reversedBy()==null?"voucher.status.POSTED":"voucher.status.REVERSED",locale));}
        lines.add(label("voucher.records",locale)+": "+rows.size()+" | "+label("voucher.total",locale)+": TZS "+total);String institution="",branch=a.getStationId();if(!rows.isEmpty()){var d=service.detail(a,rows.getFirst().loanId());institution=d.institution();branch=d.branch();}
        return output(new Document(label("recording.register."+kind,locale),institution,branch,"loan-"+kind.toLowerCase(Locale.ROOT)+"-report.pdf",lines),request,model);
    }
    private Object output(Document doc,jakarta.servlet.http.HttpServletRequest request,Model model){if(request.getRequestURI().endsWith("/print")){model.addAttribute("document",doc);return "accounting/loans/print";}return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename="+doc.filename()).header("X-Content-Type-Options","nosniff").body(pdf.render(doc));}
    private LoanAmortizationPdfDocument.Source amortizationDocument(LoanRecordingService.LoanExport export,Locale locale){
        var d=export.detail();var loan=d.loan();
        var identity=List.of(printField("recording.amortization.lender",d.institution(),locale),printField("voucher.print.branch",d.branch(),locale),
            printField("recording.client",loan.client(),locale),printField("voucher.print.clientNumber",loan.clientNumber(),locale),printField("loan.loanId",loan.number(),locale),
            printField("recording.product",loan.product(),locale),printField("recording.applicationDate",statementDate(loan.applicationDate(),locale),locale),printField("voucher.status",label("recording.status."+loan.status(),locale),locale));
        var terms=new ArrayList<Field>();terms.add(printField("recording.amortization.loanAmount",statementMoney(loan.principal(),locale),locale));
        if("AMOUNT".equals(d.interestEntry()))terms.add(printField("recording.interestEntry",label("recording.entry.AMOUNT",locale),locale));
        else {var percent=java.text.NumberFormat.getPercentInstance(locale);percent.setMaximumFractionDigits(4);
            terms.add(printField("recording.rate",percent.format(d.rate())+" "+label("CUSTOM".equals(d.loanMode())?"recording.basis."+d.interestBasis():"recording.annual",locale),locale));}
        terms.add(printField("recording.method",label("recording.method."+d.method(),locale),locale));terms.add(printField("recording.tenureUnit",d.tenureValue()+" "+label("recording.tenure."+d.tenureUnit(),locale),locale));
        terms.add(printField("recording.frequency",label("recording.frequency."+d.frequency(),locale),locale));terms.add(printField("recording.firstPaymentDate",statementDate(d.firstPaymentDate(),locale),locale));
        terms.add(printField("recording.amortization.paymentCount",Integer.toString(d.schedule().size()),locale));terms.add(printField("recording.amortization.installment",statementMoney(d.installment(),locale),locale));terms.add(printField("recording.amortization.interestTotal",statementMoney(d.contractualInterest(),locale),locale));
        terms.addAll(chargeFields(export.charges(),locale));if(!export.charges().terms().isEmpty())terms.add(printField("charges.signedEvidence",d.evidence(),locale));terms.add(printField("charges.outstanding",statementMoney(loan.charges(),locale),locale));
        var payments=new ArrayList<List<String>>();
        for(var p:export.payments()){
            boolean reverse=p.reversesId()!=null;payments.add(List.of(statementDate(p.date(),locale),p.reference()+(p.charges().signum()>0?" / "+label("charges.title",locale)+": "+statementMoney(reverse?p.charges().negate():p.charges(),locale):""),statementMoney(reverse?p.principal().negate():p.principal(),locale),
                statementMoney(reverse?p.interest().negate():p.interest(),locale),statementMoney(reverse?p.amount().negate():p.amount(),locale),statementMoney(p.principalBalance(),locale),label("voucher.status."+(reverse?"REVERSAL":p.reversedBy()!=null?"REVERSED":"POSTED"),locale)));
        }
        var schedule=d.schedule().stream().map(s->List.of(Integer.toString(s.number()),s.date()==null?label("repayment.period."+d.frequency(),locale).replace("{0}",Integer.toString(s.number())):statementDate(s.date(),locale),
            statementMoney(s.beginningBalance(),locale),statementMoney(s.amount(),locale),statementMoney(s.principal(),locale),statementMoney(s.interest(),locale),statementMoney(s.endingBalance(),locale))).toList();
        return new LoanAmortizationPdfDocument.Source(label("recording.amortization.title",locale),loan.number(),identity,terms,
            new LoanAmortizationPdfDocument.Table(label("recording.amortization.payments",locale)+" (TZS)",List.of(label("voucher.date",locale),label("voucher.reference",locale),label("recording.principal",locale),label("recording.interest",locale),label("voucher.amount",locale),label("recording.balanceAfter",locale),label("voucher.status",locale)),new float[]{.12f,.22f,.12f,.12f,.13f,.16f,.13f},new boolean[]{false,false,true,true,true,true,false},payments,label("recording.amortization.noPayments",locale)),
            new LoanAmortizationPdfDocument.Table(label(d.firstPaymentDate()==null?"repayment.estimatedSchedule":"recording.schedule",locale)+" (TZS)",List.of(label("voucher.print.no",locale),label("recording.dueDate",locale),label("repayment.beginningBalance",locale),label("repayment.scheduledPayment",locale),label("recording.principal",locale),label("recording.interest",locale),label("repayment.endingBalance",locale)),new float[]{.055f,.14f,.16f,.17f,.14f,.15f,.185f},new boolean[]{false,false,true,true,true,true,true},schedule,label("recording.scheduleUnavailable",locale)),
            (d.firstPaymentDate()==null?label("recording.beforeDisbursement",locale):"")+(export.charges().terms().isEmpty()?"":" "+label("charges.notPaid",locale)+" "+label("charges.scheduleHelp",locale)),label("voucher.page",locale));
    }
    private VoucherPdfDocument.Source postDocument(Posting p,Detail d,Locale locale,byte[] logo){return postDocument(p,d,locale,logo,List.of());}
    private VoucherPdfDocument.Source postDocument(Posting p,Detail d,Locale locale,byte[] logo,List<JournalLine> actualLines){
        boolean payment="DISBURSEMENT".equals(p.kind());
        String status=label(p.reversedBy()!=null || (payment && d.loan()!=null && "DISBURSEMENT_REVERSED".equals(d.loan().status()))?"voucher.status.REVERSED":p.reversesId()!=null?"voucher.status.REVERSAL":"voucher.status.POSTED",locale);
        String date=p.date().format(java.time.format.DateTimeFormatter.ofPattern("dd / MM / uuuu"));
        var rows=new ArrayList<List<String>>();
        String reference=label("loan.loanId",locale)+": "+p.loanNumber()+"\n"+p.reference();
        boolean moneyDebit=!payment && p.reversesId()==null;
        String amount=statementMoney(p.amount(),locale);
        rows.add(List.of("1",reference,Objects.toString(p.moneyAccount(),""),moneyDebit?amount:"",moneyDebit?"":amount,amount));
        if(p.principal().signum()!=0){String principal=statementMoney(p.principal(),locale);
            rows.add(List.of(Integer.toString(rows.size()+1),label("recording.principal",locale),Objects.toString(p.principalAccount(),""),moneyDebit?"":principal,moneyDebit?principal:"",principal));}
        if(p.interest().signum()!=0){String interest=statementMoney(p.interest(),locale);
            rows.add(List.of(Integer.toString(rows.size()+1),label("recording.interest",locale),Objects.toString(p.interestAccount(),""),moneyDebit?"":interest,moneyDebit?interest:"",interest));}
        if(!actualLines.isEmpty()){rows.clear();for(var line:actualLines)rows.add(List.of(Integer.toString(rows.size()+1),reference,line.account(),line.debit().signum()==0?"":statementMoney(line.debit(),locale),line.credit().signum()==0?"":statementMoney(line.credit(),locale),statementMoney(line.debit().add(line.credit()),locale)));}
        if(payment)rows.replaceAll(row->row.subList(0,5));
        var totals=new ArrayList<Field>();
        totals.add(printField("voucher.print.totalDebit",statementMoney(actualLines.isEmpty()?p.amount():actualLines.stream().map(JournalLine::debit).reduce(BigDecimal.ZERO,BigDecimal::add),locale),locale));totals.add(printField("voucher.print.totalCredit",statementMoney(actualLines.isEmpty()?p.amount():actualLines.stream().map(JournalLine::credit).reduce(BigDecimal.ZERO,BigDecimal::add),locale),locale));
        totals.add(printField("voucher.print.totalAmount",statementMoney(p.amount(),locale),locale));
        var posting=new ArrayList<Field>(List.of(printField("voucher.postedBy",p.actor(),locale),printField("voucher.postedAt",
            p.recordedAt().atZoneSameInstant(java.time.ZoneId.of("Africa/Dar_es_Salaam")).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss z")),locale),printField("voucher.status",status,locale)));
        if(p.reversesId()!=null)posting.add(printField("voucher.reversal",p.reversesId().toString(),locale));
        if(p.reversedBy()!=null)posting.add(printField("voucher.reversal",p.reversedBy().toString(),locale));
        var details=List.of(printField("voucher.print.moneyAccount",p.moneyAccount(),locale),printField("voucher.print.reference",p.reference(),locale),printField("voucher.date",date,locale));
        return new VoucherPdfDocument.Source(label(payment?"voucher.title.PAYMENT":"voucher.title.RECEIPT",locale),d.institution(),d.branch(),p.reference(),status,
            List.of(printField("voucher.print.number",p.reference(),locale),printField(payment?"voucher.print.paymentDate":"voucher.print.receiptDate",date,locale),printField("voucher.print.branch",d.branch(),locale)),
            label(payment?"voucher.print.payeeDetails":"voucher.print.payerDetails",locale),
            List.of(printField("voucher.print.name",p.client(),locale),printField("voucher.print.clientNumber",p.clientNumber(),locale),printField("loan.loanId",p.loanNumber(),locale),printField("voucher.print.phone","",locale),printField("voucher.print.email","",locale)),
            label(payment?"voucher.print.paymentHeading":"voucher.print.receiptDetails",locale),details,
            List.of(label("voucher.print.no",locale),label("voucher.print.particulars",locale),label("voucher.print.ledger",locale),label("accounting.debit",locale)+" (TZS)",label("accounting.credit",locale)+" (TZS)",label("voucher.amount",locale)+" (TZS)").subList(0,payment?5:6),
            payment?new float[]{.055f,.26f,.325f,.18f,.18f}:new float[]{.055f,.26f,.265f,.14f,.14f,.14f},rows,
            label("voucher.print.amountWords",locale),VoucherPdfService.amountInWords(p.amount(),locale),totals,label("voucher.print.narration",locale),label("recording.document."+p.kind(),locale)+"\n"+Objects.toString(p.notes(),"")+"\n"+label("voucher.evidence",locale)+": "+Objects.toString(p.evidence(),"")+"\n"+label("recording.balanceAfter",locale)+": "+statementMoney(p.principalBalance(),locale)+" TZS",
            label("voucher.print.postingRecord",locale),posting,label("voucher.page",locale),label("voucher.continued",locale),logo,
            label("voucher.print.method",locale),List.of(printField("voucher.print.method.CASH","false",locale),printField("voucher.print.method.BANK","false",locale),printField("voucher.print.method.CHEQUE","false",locale),printField("voucher.print.method.MOBILE_MONEY","false",locale)));
    }
    private Field printField(String key,String value,Locale locale){return new Field(label(key,locale),Objects.toString(value,""));}
    private Document topUpDocument(TopUpPosting p,Detail d,Locale locale){
        return topUpDocument(p,d,locale,null,new com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Plan(null,"","",List.of(),true,""));
    }
    private Document topUpDocument(TopUpPosting p,Detail d,Locale locale,Posting movement,com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Plan chargePlan){
        var lines=new ArrayList<String>();lines.add(label("recording.loan",locale)+": "+p.loanNumber());lines.add(label("recording.topUp.source",locale)+": "+p.sourceNumber());
        lines.add(label("recording.client",locale)+": "+d.loan().client()+" / "+d.loan().clientNumber());lines.add(label("voucher.date",locale)+": "+p.date());
        lines.add(label("recording.topUp.newPrincipal",locale)+": TZS "+statementMoney(d.loan().principal(),locale));
        lines.add(label("recording.outstandingPrincipal",locale)+": TZS "+statementMoney(p.principal(),locale));lines.add(label("recording.dueInterest",locale)+": TZS "+statementMoney(p.interest(),locale));
        lines.add(label("recording.topUp.settlement",locale)+": TZS "+statementMoney(p.principal().add(p.interest()).add(p.sourceCharges()),locale));lines.add(label("recording.topUp.excluded",locale)+": TZS "+statementMoney(p.excludedInterest(),locale));
        lines.add(label("charges.sourceSettlement",locale)+": TZS "+statementMoney(p.sourceCharges(),locale));lines.add(label("charges.deductions",locale)+": TZS "+statementMoney(p.newDeductions(),locale));
        chargeFields(chargePlan,locale).forEach(f->lines.add(f.label()+": "+f.value()));
        lines.add(label("recording.topUp.cash",locale)+": TZS "+statementMoney(p.cash(),locale));lines.add(label("recording.topUp.internal",locale));
        lines.add(label("recording.topUp.agreement",locale)+": "+p.evidence());if(p.reversedBy()!=null)lines.add(label("recording.topUp.reversed",locale));
        if(movement!=null){lines.add(label("voucher.reference",locale)+": "+movement.reference());lines.add(label("recording.recordedBy",locale)+": "+movement.actor());
            if(movement.reversesId()!=null){lines.add(label("voucher.date",locale)+": "+movement.date());lines.add(label("repayment.reason",locale)+": "+movement.notes());lines.add(label("recording.topUp.correctionEvidence",locale)+": "+movement.evidence());}}
        lines.add(label("recording.installment",locale)+": TZS "+statementMoney(d.installment(),locale)+" / "+label("recording.frequency."+d.frequency(),locale));
        lines.add(label("recording.contractInterest",locale)+": TZS "+statementMoney(d.contractualInterest(),locale));
        if(!"AMOUNT".equals(d.interestEntry())){var percent=java.text.NumberFormat.getPercentInstance(locale);percent.setMaximumFractionDigits(4);lines.add(label("recording.rate",locale)+": "+percent.format(d.rate())+" "+label("CUSTOM".equals(d.loanMode())?"recording.basis."+d.interestBasis():"recording.annual",locale));}
        lines.add(label("recording.method",locale)+": "+label("recording.method."+d.method(),locale));lines.add(label("recording.tenureUnit",locale)+": "+d.tenureValue()+" "+label("recording.tenure."+d.tenureUnit(),locale));
        for(var row:d.schedule())lines.add(row.number()+" | "+Objects.toString(row.date(),"")+" | "+label("recording.principal",locale)+": "+row.principal()+" | "+label("recording.interest",locale)+": "+row.interest()+" | TZS "+row.amount());
        return new Document(label(movement!=null && movement.reversesId()!=null?"recording.document."+movement.kind():"recording.topUp.breakdown",locale),d.institution(),d.branch(),"loan-top-up-"+p.loanNumber()+".pdf",lines);
    }
    private List<Field> chargeFields(com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Plan plan,Locale locale){
        if(plan.terms().isEmpty())return List.of();var fields=new ArrayList<Field>();
        fields.add(printField("charges.policyReference",plan.policyReference(),locale));fields.add(printField("charges.agreementReference",plan.agreementReference(),locale));
        for(var term:plan.terms()){
            String basis=term.basis().equals("PRINCIPAL_RATE")?term.basisValue().movePointRight(2).stripTrailingZeros().toPlainString()+"%":"TZS "+term.basisValue().toPlainString();
            fields.add(printField("charges.kind."+term.kind(),label("charges.basis."+term.basis(),locale)+" "+basis+" / TZS "+statementMoney(term.amount(),locale)+" / "+label("charges.timing."+term.timing(),locale),locale));
            if(!term.provider().isBlank())fields.add(printField("charges.provider",term.provider()+" / "+term.verification(),locale));
            if(!term.evidence().isBlank())fields.add(printField("charges.supportingEvidence",term.evidence(),locale));
        }
        fields.add(printField("charges.managementTotal",statementMoney(plan.managementTotal(),locale),locale));fields.add(printField("charges.total",statementMoney(plan.total(),locale),locale));fields.add(printField("charges.deductions",statementMoney(plan.deductions(),locale),locale));return fields;
    }
    private Map<String,Object> chargeView(AppUserPrincipal a,RecordForm f,com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Plan p,Locale locale){
        var result=new LinkedHashMap<String,Object>();result.put("total",p.total().toPlainString());result.put("managementTotal",p.managementTotal().toPlainString());result.put("deductions",p.deductions().toPlainString());result.put("separate",p.separate().toPlainString());result.put("supported",p.supported());result.put("insuranceRequired",p.terms().stream().anyMatch(t->t.kind().equals("INSURANCE")));result.put("error",p.error().isBlank()?"":label(p.error(),locale));result.put("policyReference",p.policyReference());
        var source=f.getTopUpSourceLoanId()==null?BigDecimal.ZERO:service.topUpQuote(a,f.getTopUpSourceLoanId(),f.getApplicationDate(),null).total();result.put("netCash",f.getPrincipal().subtract(source).subtract(p.deductions()).toPlainString());
        result.put("terms",p.terms().stream().map(t->Map.of("kind",label("charges.kind."+t.kind(),locale),"basis",label("charges.basis."+t.basis(),locale),"basisValue",t.basis().equals("PRINCIPAL_RATE")?t.basisValue().movePointRight(2).stripTrailingZeros().toPlainString()+"%":"TZS "+t.basisValue().toPlainString(),"amount",t.amount().toPlainString(),"timing",label("charges.timing."+t.timing(),locale))).toList());return result;
    }
    private String label(String key,Locale locale){return messages.getMessage(key,null,locale);}
    private String kind(jakarta.servlet.http.HttpServletRequest r){return r.getRequestURI().contains("loan-disbursements")?"DISBURSEMENT":r.getRequestURI().contains("loan-repayments")?"PAYMENT":"LOAN";}
    private void context(String kind,Model model){model.addAttribute("kind",kind);model.addAttribute("createClaim",kind.equals("LOAN")?"LOAN_RECORDING_CREATE":kind.equals("DISBURSEMENT")?"LOAN_RECORDING_DISBURSE":"LOAN_RECORDING_POST");model.addAttribute("base",kind.equals("LOAN")?"/finance/loan-records":kind.equals("DISBURSEMENT")?"/finance/loan-disbursements":"/finance/loan-repayments");}
    private String error(IllegalArgumentException e){return e.getMessage()!=null && (e.getMessage().startsWith("recording.error.") || e.getMessage().startsWith("repayment.error.") || e.getMessage().startsWith("voucher.error.") || e.getMessage().startsWith("charges.error."))?e.getMessage():"recording.error.terms";}

    @PostMapping("/loan-records/{id}/submit-for-review")
    public String submitForReview(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,RedirectAttributes flash) {
        service.submitRecordedApplication(actor,id);flash.addFlashAttribute("recordingSaved",true);return "redirect:/finance/loan-records/"+id;
    }
}
