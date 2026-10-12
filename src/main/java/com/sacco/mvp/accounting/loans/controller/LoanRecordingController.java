package com.sacco.mvp.accounting.loans.controller;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.loans.service.*;
import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.SaccoLogoStorageService;
import com.sacco.mvp.accounting.reports.VoucherPdfDocument;
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
    @InitBinder("recordForm") public void recordBinder(WebDataBinder b){b.setAllowedFields("requestKey","clientId","productId","applicationDate","requestedPrincipal","principal","months","firstPaymentDate","purpose","business","income","expenses","otherDebt","informationSource","guarantors","collateral","evidence","notes");}
    @InitBinder("postForm") public void postBinder(WebDataBinder b){b.setAllowedFields("requestKey","loanId","effectiveDate","amount","moneyAccountId","principalAccountId","interestAccountId","reference","evidence","notes");}
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
    @GetMapping("/loan-records/new") public String create(@AuthenticationPrincipal AppUserPrincipal a,Model model){service.authorize(a,"CREATE");var f=new RecordForm();f.setApplicationDate(clock.today());model.addAttribute("recordForm",f);return "accounting/loans/record-form";}
    @PostMapping("/loan-records") public String save(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute RecordForm recordForm,BindingResult errors,@RequestParam(defaultValue="save") String action,Model model,RedirectAttributes flash){
        service.authorize(a,"CREATE");if(!errors.hasErrors())try{
            if(action.equals("preview")){model.addAttribute("quote",service.preview(a,recordForm));return "accounting/loans/record-form";}
            if(!action.equals("save"))throw new IllegalArgumentException("recording.error.request");UUID id=service.save(a,recordForm);flash.addFlashAttribute("recordingSaved",true);return "redirect:/finance/loan-records/"+id;
        }catch(IllegalArgumentException e){errors.reject(error(e));}catch(DataIntegrityViolationException e){errors.reject("recording.error.duplicate");}
        return "accounting/loans/record-form";
    }
    @GetMapping("/loan-records/{id}") public String detail(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="0") int historyPage,@RequestParam(defaultValue="0") int schedulePage,Model model){if(schedulePage<0 || schedulePage>24)throw new IllegalArgumentException("recording.error.filter");var d=service.detail(a,id);model.addAttribute("detail",d);model.addAttribute("history",service.history(a,id,historyPage));model.addAttribute("schedulePage",schedulePage);model.addAttribute("scheduleRows",d.schedule().stream().skip(schedulePage*25L).limit(25).toList());model.addAttribute("scheduleHasNext",d.schedule().size()>(schedulePage+1)*25);return "accounting/loans/details";}
    @GetMapping({"/loan-disbursements/new","/loan-repayments/new"}) public String postingForm(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(required=false) UUID loanId,jakarta.servlet.http.HttpServletRequest request,Model model){
        String kind=kind(request);service.authorize(a,kind.equals("DISBURSEMENT")?"DISBURSE":"POST");var f=new PostForm();f.setEffectiveDate(clock.today());f.setLoanId(loanId);
        if(loanId!=null){var detail=service.detail(a,loanId);model.addAttribute("detail",detail);if(kind.equals("DISBURSEMENT"))f.setAmount(detail.loan().principal());}
        context(kind,model);model.addAttribute("postForm",f);return "accounting/loans/post-form";
    }
    @PostMapping({"/loan-disbursements","/loan-repayments"}) public String post(@AuthenticationPrincipal AppUserPrincipal a,@ModelAttribute PostForm postForm,BindingResult errors,@RequestParam(defaultValue="post") String action,jakarta.servlet.http.HttpServletRequest request,Model model,RedirectAttributes flash){
        String kind=kind(request);service.authorize(a,kind.equals("DISBURSEMENT")?"DISBURSE":"POST");if(!errors.hasErrors())try{
            if(action.equals("preview")){model.addAttribute("allocationPreview",service.postingPreview(a,postForm,kind));context(kind,model);model.addAttribute("detail",service.detail(a,postForm.getLoanId()));return "accounting/loans/post-form";}if(!action.equals("post"))throw new IllegalArgumentException("recording.error.request");var saved=kind.equals("DISBURSEMENT")?service.disburse(a,postForm):service.repay(a,postForm);flash.addFlashAttribute("recordingSaved",true);return "redirect:/finance/loan-postings/"+saved.id();
        }catch(IllegalArgumentException e){errors.reject(error(e));}catch(DataIntegrityViolationException e){errors.reject("recording.error.duplicate");}
        context(kind,model);if(postForm.getLoanId()!=null)model.addAttribute("detail",service.detail(a,postForm.getLoanId()));return "accounting/loans/post-form";
    }
    @GetMapping({"/loan-postings/{id}","/loan-disbursements/{id}","/loan-repayments/{id}"}) public String posting(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,Model model){postingModel(a,id,model);return "accounting/loans/posting";}
    private void postingModel(AppUserPrincipal a,UUID id,Model model){var p=service.posting(a,id);model.addAttribute("posting",p);model.addAttribute("detail",service.detail(a,p.loanId()));model.addAttribute("canReverse",!p.actorId().equals(a.getMemberId()));model.addAttribute("reverseKey",UUID.randomUUID());model.addAttribute("today",clock.today());}
    @PostMapping("/loan-postings/{id}/reverse") public String reverse(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam UUID requestKey,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate effectiveDate,@RequestParam String reason,Model model){
        try{return "redirect:/finance/loan-postings/"+service.reverse(a,id,requestKey,effectiveDate,reason).id();}catch(IllegalArgumentException e){model.addAttribute("reverseError",error(e));}catch(DataIntegrityViolationException e){model.addAttribute("reverseError","recording.error.duplicate");}
        postingModel(a,id,model);model.addAttribute("reverseKey",requestKey);model.addAttribute("reason",reason);model.addAttribute("today",effectiveDate);return "accounting/loans/posting";
    }
    @GetMapping("/loan-recording/choices/{category}") @ResponseBody public List<Choice> choices(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable String category,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return service.choices(a,category,search,page);}
    @GetMapping("/client-ledgers") public String clients(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,Model model){model.addAttribute("clients",service.choices(a,"ledger-clients",search,page));model.addAttribute("search",search);model.addAttribute("page",page);return "accounting/loans/clients";}
    @GetMapping("/client-ledgers/{id}") public String statement(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="0") int movementPage,Model model){model.addAttribute("movements",service.movements(a,id,movementPage));model.addAttribute("statement",service.statement(a,id,page));model.addAttribute("clientId",id);return "accounting/loans/statement";}
    @GetMapping({"/loan-records/{id}/pdf","/loan-records/{id}/print"}) public Object loanExport(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,jakarta.servlet.http.HttpServletRequest request,Locale locale,Model model){service.authorize(a,"EXPORT");return output(loanDocument(service.detail(a,id),locale),request,model);}
    @GetMapping({"/loan-postings/{id}/pdf","/loan-postings/{id}/print","/loan-disbursements/{id}/pdf","/loan-disbursements/{id}/print","/loan-repayments/{id}/pdf","/loan-repayments/{id}/print"})
    public Object postingExport(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,jakarta.servlet.http.HttpServletRequest request,Locale locale,Model model){
        service.authorize(a,"EXPORT");var p=service.posting(a,id);var d=service.detail(a,p.loanId());
        byte[] logo=logos.hasLogo(a.getSaccoId())?logos.load(a.getSaccoId()).content():null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,(request.getRequestURI().endsWith("/print")?"inline":"attachment")+"; filename=loan-posting-"+p.id()+".pdf")
            .header("X-Content-Type-Options","nosniff").body(pdf.renderPosting(postDocument(p,d,locale,logo)));
    }
    @GetMapping({"/client-ledgers/{id}/pdf","/client-ledgers/{id}/print"}) public Object statementExport(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,jakarta.servlet.http.HttpServletRequest request,Locale locale,Model model){
        var document=statementDocument(service.exportStatement(a,id),locale);
        if(request.getRequestURI().endsWith("/print")){model.addAttribute("document",document);return "accounting/loans/statement-print";}
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=client-statement.pdf")
            .header("X-Content-Type-Options","nosniff").body(pdf.renderStatement(document));
    }
    private StatementDocument statementDocument(LoanRecordingService.StatementExport source,Locale locale){
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
            var disbursement=movements.stream().filter(p->"DISBURSEMENT".equals(p.kind())).findFirst().orElse(null);
            var fields=new ArrayList<StatementField>();
            statementField(fields,"loan",loan.number(),locale);statementField(fields,"product",loan.product(),locale);
            statementField(fields,"disbursementDate",disbursement==null?unavailable:statementDate(disbursement.date(),locale),locale);
            statementField(fields,"fund",unavailable,locale);statementField(fields,"principal",statementMoney(loan.principal(),locale),locale);
            statementField(fields,"months",term==null?unavailable:Integer.toString(term.months()),locale);
            statementField(fields,"installments",term==null || term.installments()<1?unavailable:Integer.toString(term.installments()),locale);
            statementField(fields,"rateAnnual",term==null || term.rate()==null?unavailable:statementMoney(term.rate().multiply(new BigDecimal("100")),locale),locale);
            statementField(fields,"firstDate",term==null?unavailable:statementDate(term.firstPaymentDate(),locale),locale);
            statementField(fields,"lastDate",term==null?unavailable:statementDate(term.lastPaymentDate(),locale),locale);
            statementField(fields,"installment",term==null || term.frequency()==null?unavailable:statementMoney(term.installment(),locale)+" / "+label("recording.frequency."+term.frequency(),locale),locale);
            statementField(fields,"opening",disbursement==null?unavailable:statementMoney(disbursement.principalBalance(),locale),locale);
            statementField(fields,"disbursed",disbursement==null?unavailable:statementMoney(disbursement.amount(),locale),locale);
            statementField(fields,"outstanding",disbursement==null?unavailable:statementMoney(loan.outstandingPrincipal(),locale),locale);
            statementField(fields,"dueInterest",disbursement==null?unavailable:statementMoney(loan.dueInterest(),locale),locale);
            statementField(fields,"futureInterest",disbursement==null?unavailable:statementMoney(loan.futureInterest(),locale),locale);
            statementField(fields,"arrears",disbursement==null?unavailable:statementMoney(loan.arrears(),locale),locale);
            statementField(fields,"branch",term==null?unavailable:term.institution()+" / "+term.branch(),locale);
            var rows=new ArrayList<List<String>>();
            for(var post:movements){
                boolean credit="PAYMENT".equals(post.kind());String account=Objects.toString(post.moneyAccount(),"");
                String code=account.contains(" - ")?account.split(" - ",2)[0]:unavailable;
                String description=post.notes()==null || post.notes().isBlank()?account:post.notes();
                rows.add(List.of(statementDate(post.date(),locale),post.reference(),unavailable,label("recording.document."+post.kind(),locale),code,description,
                    credit?"":statementMoney(post.principal(),locale),credit?statementMoney(post.principal(),locale):"",
                    statementMoney("REVERSAL".equals(post.kind())?post.interest().negate():post.interest(),locale),statementMoney(post.principalBalance(),locale)));
            }
            sections.add(new StatementSection(fields,columns,rows));
        }
        return new StatementDocument(label("recording.statement",locale).toUpperCase(locale),identity,sections,
            label("recording.statement.empty",locale),label("recording.statement.note",locale));
    }
    private void statementField(List<StatementField> fields,String key,String value,Locale locale){fields.add(new StatementField(label("recording.statement."+key,locale),value));}
    private String statementDate(LocalDate value,Locale locale){return value==null?label("recording.unavailable",locale):value.format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-uuuu",locale)).toUpperCase(locale);}
    private String statementMoney(BigDecimal value,Locale locale){if(value==null)return label("recording.unavailable",locale);var format=java.text.NumberFormat.getNumberInstance(locale);format.setMinimumFractionDigits(2);format.setMaximumFractionDigits(2);return format.format(value);}
    @GetMapping({"/loan-disbursements/report/pdf","/loan-disbursements/report/print","/loan-repayments/report/pdf","/loan-repayments/report/print"})
    public Object report(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam(defaultValue="") String search,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,@RequestParam(defaultValue="") String state,@RequestParam(defaultValue="newest") String sort,@RequestParam(required=false) UUID productId,jakarta.servlet.http.HttpServletRequest request,Locale locale,Model model){
        String kind=kind(request);Choice selectedProduct=productId==null?null:service.filterProduct(a,productId);var rows=service.report(a,kind,new Filter(search,from,through,state,sort,0,null,productId));var lines=new ArrayList<String>();lines.add(label("recording.asOf",locale)+": "+clock.today());lines.add(label("voucher.from",locale)+": "+Objects.toString(from,"")+" | "+label("voucher.through",locale)+": "+Objects.toString(through,""));
        if(selectedProduct!=null)lines.add(label("recording.product",locale)+": "+selectedProduct.label());
        BigDecimal total=BigDecimal.ZERO;for(var p:rows){total=total.add(p.kind().equals("REVERSAL")?p.amount().negate():p.amount());lines.add(p.date()+" | "+p.loanNumber()+" | "+p.client()+" | "+p.reference()+" | TZS "+(p.kind().equals("REVERSAL")?p.amount().negate():p.amount())+" | "+label("recording.principal",locale)+": "+p.principal()+" | "+label("recording.interest",locale)+": "+p.interest()+" | "+label(p.kind().equals("REVERSAL")?"voucher.status.REVERSAL":p.reversedBy()==null?"voucher.status.POSTED":"voucher.status.REVERSED",locale));}
        lines.add(label("voucher.records",locale)+": "+rows.size()+" | "+label("voucher.total",locale)+": TZS "+total);String institution="",branch=a.getStationId();if(!rows.isEmpty()){var d=service.detail(a,rows.getFirst().loanId());institution=d.institution();branch=d.branch();}
        return output(new Document(label("recording.register."+kind,locale),institution,branch,"loan-"+kind.toLowerCase(Locale.ROOT)+"-report.pdf",lines),request,model);
    }
    private Object output(Document doc,jakarta.servlet.http.HttpServletRequest request,Model model){if(request.getRequestURI().endsWith("/print")){model.addAttribute("document",doc);return "accounting/loans/print";}return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename="+doc.filename()).header("X-Content-Type-Options","nosniff").body(pdf.render(doc));}
    private Document loanDocument(Detail d,Locale locale){var lines=new ArrayList<String>();lines.add(d.loan().number()+" | "+d.loan().clientNumber()+" | "+d.loan().client());lines.add(label("voucher.status",locale)+": "+label("recording.status."+d.loan().status(),locale));
        pair(lines,"recording.applicationDate",d.loan().applicationDate(),locale);pair(lines,"recording.product",d.loan().product(),locale);pair(lines,"recording.purpose",d.purpose(),locale);pair(lines,"recording.requestedPrincipal",d.loan().requestedPrincipal(),locale);pair(lines,"recording.principal",d.loan().principal(),locale);
        pair(lines,"recording.rate",d.rate().multiply(new BigDecimal("100"))+"%",locale);pair(lines,"recording.method",label("recording.method."+d.method(),locale),locale);pair(lines,"recording.frequency",label("recording.frequency."+d.frequency(),locale),locale);pair(lines,"recording.months",d.months(),locale);pair(lines,"recording.installment",d.installment(),locale);pair(lines,"recording.contractInterest",d.contractualInterest(),locale);
        pair(lines,"recording.business",d.business(),locale);pair(lines,"recording.information",label("recording.source."+d.informationSource(),locale),locale);pair(lines,"recording.income",d.income(),locale);pair(lines,"recording.expenses",d.expenses(),locale);pair(lines,"recording.otherDebt",d.otherDebt(),locale);pair(lines,"recording.guarantors",d.guarantors(),locale);pair(lines,"recording.collateral",d.collateral(),locale);pair(lines,"voucher.evidence",d.evidence(),locale);pair(lines,"voucher.description",d.notes(),locale);
        lines.add(label("recording.schedule",locale)+" (TZS)");lines.add(label("recording.scheduleHeader",locale));for(var s:d.schedule())lines.add(s.number()+" | "+s.date()+" | "+s.principal()+" | "+s.interest()+" | "+s.amount());
        pair(lines,"recording.outstandingPrincipal",d.loan().outstandingPrincipal(),locale);pair(lines,"recording.dueInterest",d.loan().dueInterest(),locale);pair(lines,"recording.arrears",d.loan().arrears(),locale);pair(lines,"recording.futureInterest",d.loan().futureInterest(),locale);pair(lines,"recording.recordedBy",d.recordedBy(),locale);pair(lines,"voucher.postedAt",d.recordedAt(),locale);
        return new Document(label("recording.loanDetails",locale)+" "+d.loan().number(),d.institution(),d.branch(),"loan-"+d.loan().number()+".pdf",lines);}
    private VoucherPdfDocument.Source postDocument(Posting p,Detail d,Locale locale,byte[] logo){
        boolean payment="DISBURSEMENT".equals(p.kind());
        String status=label(p.reversedBy()!=null?"voucher.status.REVERSED":p.reversesId()!=null?"voucher.status.REVERSAL":"voucher.status.POSTED",locale);
        String date=p.date().format(java.time.format.DateTimeFormatter.ofPattern("dd / MM / uuuu"));
        var rows=new ArrayList<List<String>>();
        String reference=label("loan.loanId",locale)+": "+p.loanNumber()+"\n"+p.reference();
        String accounts=Objects.toString(p.moneyAccount(),"")+"\n"+Objects.toString(p.principalAccount(),"");
        if(p.interestAccount()!=null && !p.interestAccount().isBlank())accounts+="\n"+p.interestAccount();
        rows.add(payment?List.of("1",label("recording.principal",locale),accounts,statementMoney(p.principal(),locale),statementMoney(p.principal(),locale),statementMoney(p.principal(),locale)):
            List.of("1",label("recording.principal",locale),reference+"\n"+accounts,statementMoney(p.principal(),locale)));
        if(p.interest().signum()!=0)rows.add(List.of("2",label("recording.interest",locale),reference+"\n"+Objects.toString(p.interestAccount(),""),statementMoney(p.interest(),locale)));
        var totals=new ArrayList<Field>();
        if(payment){totals.add(printField("voucher.print.totalDebit",statementMoney(p.amount(),locale),locale));totals.add(printField("voucher.print.totalCredit",statementMoney(p.amount(),locale),locale));}
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
            payment?List.of(label("voucher.print.no",locale),label("voucher.print.particulars",locale),label("voucher.print.ledger",locale),label("accounting.debit",locale)+" (TZS)",label("accounting.credit",locale)+" (TZS)",label("voucher.amount",locale)+" (TZS)"):
                List.of(label("voucher.print.no",locale),label("voucher.print.particulars",locale),label("voucher.print.ledger",locale),label("voucher.amount",locale)+" (TZS)"),
            payment?new float[]{.055f,.26f,.265f,.14f,.14f,.14f}:new float[]{.06f,.44f,.30f,.20f},rows,
            label("voucher.print.amountWords",locale),VoucherPdfService.amountInWords(p.amount(),locale),totals,label("voucher.print.narration",locale),label("recording.document."+p.kind(),locale)+"\n"+Objects.toString(p.notes(),"")+"\n"+label("voucher.evidence",locale)+": "+Objects.toString(p.evidence(),"")+"\n"+label("recording.balanceAfter",locale)+": "+statementMoney(p.principalBalance(),locale)+" TZS",
            label("voucher.print.postingRecord",locale),posting,label("voucher.page",locale),label("voucher.continued",locale),logo,
            label("voucher.print.method",locale),List.of(printField("voucher.print.method.CASH","false",locale),printField("voucher.print.method.BANK","false",locale),printField("voucher.print.method.CHEQUE","false",locale),printField("voucher.print.method.MOBILE_MONEY","false",locale)));
    }
    private Field printField(String key,String value,Locale locale){return new Field(label(key,locale),Objects.toString(value,""));}
    private void pair(List<String> lines,String key,Object value,Locale locale){lines.add(label(key,locale)+": "+Objects.toString(value,label("recording.unavailable",locale)));}
    private String label(String key,Locale locale){return messages.getMessage(key,null,locale);}
    private String kind(jakarta.servlet.http.HttpServletRequest r){return r.getRequestURI().contains("loan-disbursements")?"DISBURSEMENT":r.getRequestURI().contains("loan-repayments")?"PAYMENT":"LOAN";}
    private void context(String kind,Model model){model.addAttribute("kind",kind);model.addAttribute("createClaim",kind.equals("LOAN")?"LOAN_RECORDING_CREATE":kind.equals("DISBURSEMENT")?"LOAN_RECORDING_DISBURSE":"LOAN_RECORDING_POST");model.addAttribute("base",kind.equals("LOAN")?"/finance/loan-records":kind.equals("DISBURSEMENT")?"/finance/loan-disbursements":"/finance/loan-repayments");}
    private String error(IllegalArgumentException e){return e.getMessage()!=null && (e.getMessage().startsWith("recording.error.") || e.getMessage().startsWith("repayment.error.") || e.getMessage().startsWith("voucher.error."))?e.getMessage():"recording.error.terms";}
}
