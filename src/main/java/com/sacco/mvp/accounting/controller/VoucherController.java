package com.sacco.mvp.accounting.controller;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.accounting.dto.VoucherDtos.Mapping;
import com.sacco.mvp.accounting.service.VoucherService;
import com.sacco.mvp.accounting.service.VoucherPdfService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.SaccoLogoStorageService;
import lombok.RequiredArgsConstructor;
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
import java.time.LocalDate;
import java.util.*;

@Controller @RequiredArgsConstructor
@RequestMapping("/finance/vouchers/{kind}")
public class VoucherController {
    private final VoucherService vouchers;
    private final VoucherPdfService pdf;
    private final ApplicationClock clock;
    private final SaccoLogoStorageService logos;
    private final com.sacco.mvp.accounting.service.LoanReceiptService loanReceipts;
    private final com.sacco.mvp.accounting.service.LoanReceiptPdfService loanPdf;
    private final com.sacco.mvp.accounting.approval.service.VoucherApprovalGate approvalGate;
    private final com.sacco.mvp.accounting.approval.service.VoucherWorkflowService approvalWorkflows;
    @ExceptionHandler({Invalid.class, org.springframework.beans.InvalidPropertyException.class})
    public ResponseEntity<String> invalidRequest(RuntimeException invalid){return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body("Invalid voucher request");}
    public static Type type(String kind){return switch(kind){case "receipts"->Type.RECEIPT;case "payments"->Type.PAYMENT;case "journals"->Type.JOURNAL;default->throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);};}
    @InitBinder("voucherForm") void binder(WebDataBinder binder){
        binder.setAutoGrowCollectionLimit(50);
        binder.setAllowedFields("requestKey","effectiveDate","party","reference","description","evidence","moneyAccountId","activityId","receiptPurpose","loanPayer","clientId","interestAccountId","rows[*].loanId","rows[*].transactionId","rows[*].templateKey","rows[*].component","rows[*].description","rows[*].amount","rows[*].debitAccountId","rows[*].creditAccountId");
    }
    @GetMapping
    public String list(@PathVariable String kind,@AuthenticationPrincipal AppUserPrincipal actor,
                       @RequestParam(defaultValue="") String search,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,@RequestParam(defaultValue="") String state,
                       @RequestParam(defaultValue="newest") String sort,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="30") int size,Model model){
        vouchers.authorize(actor,type(kind),"VIEW");var filter=new Filter(search,from,through,state,sort,page,size);context(kind,model);model.addAttribute("filter",filter);model.addAttribute("tablePageSize",size);
        model.addAttribute("batchEnabled",approvalWorkflows.batchAvailable(actor,type(kind)));model.addAttribute("batchRequestKey",UUID.randomUUID());
        model.addAttribute("vouchers",type(kind)==Type.RECEIPT?loanReceipts.list(actor,filter):vouchers.list(actor,type(kind),filter));return "accounting/vouchers/register";
    }
    @GetMapping("/new")
    public String create(@PathVariable String kind,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="OTHER") String purpose,@RequestParam(required=false) UUID loanId,Model model){
        vouchers.authorize(actor,type(kind),"CREATE");var f=new Form();f.setEffectiveDate(clock.today());
        if(!Set.of("OTHER","LOAN_REPAYMENT").contains(purpose) || (type(kind)!=Type.RECEIPT && !"OTHER".equals(purpose)))throw new Invalid("","request");
        f.setReceiptPurpose(purpose);if(loanId!=null){if(!"LOAN_REPAYMENT".equals(purpose))throw new Invalid("","request");loanReceipts.prefill(actor,f,loanId);}
        model.addAttribute("voucherForm",f);model.addAttribute("newVoucherForm",true);formContext(kind,actor,f,model);return "accounting/vouchers/form";
    }
    @PostMapping("/number") @ResponseBody
    public ResponseEntity<NumberReservation> number(@PathVariable String kind,@AuthenticationPrincipal AppUserPrincipal actor,
            @RequestParam UUID requestKey,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate effectiveDate){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(vouchers.reserveNumber(actor,type(kind),effectiveDate,requestKey));
    }
    @PostMapping
    public String post(@PathVariable String kind,@AuthenticationPrincipal AppUserPrincipal actor,@ModelAttribute("voucherForm") Form form,
                       BindingResult errors,@RequestParam(defaultValue="post") String action,Model model,RedirectAttributes flash){
        vouchers.authorize(actor,type(kind),"CREATE");context(kind,model);boolean loan=type(kind)==Type.RECEIPT && "LOAN_REPAYMENT".equals(form.getReceiptPurpose());
        if(!errors.hasErrors())try{
            if("preview".equals(action)){if(loan)model.addAttribute("loanPreview",loanReceipts.preview(actor,form));else model.addAttribute("preview",vouchers.preview(actor,type(kind),form));formContext(kind,actor,form,model);return "accounting/vouchers/form";}
            if("draft".equals(action) && !loan && approvalGate.required(actor,type(kind)))return "redirect:/finance/approval/"+approvalWorkflows.capture(actor,type(kind),form,false);
            if(!"post".equals(action))throw new Invalid("","request");
            if(!loan && approvalGate.required(actor,type(kind)))return "redirect:/finance/approval/"+approvalWorkflows.capture(actor,type(kind),form);
            var saved=loan?loanReceipts.post(actor,form).header():vouchers.post(actor,type(kind),form);flash.addFlashAttribute("voucherPosted",true);return "redirect:/finance/vouchers/"+kind+"/"+saved.id();
        }catch(Invalid invalid){if(invalid.field().isEmpty())errors.reject(invalid.getMessage());else errors.rejectValue(invalid.field(),invalid.getMessage());}
         catch(DataIntegrityViolationException duplicate){errors.reject("voucher.error.duplicate");}
         catch(IllegalArgumentException invalid){errors.reject(accountingError(invalid));}
        formContext(kind,actor,form,model);return "accounting/vouchers/form";
    }
    @PostMapping("/batches") public String batch(@PathVariable String kind,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID requestKey){
        var id=approvalWorkflows.batch(actor,type(kind),requestKey);var details=approvalWorkflows.details(actor,id);
        return "redirect:/finance/approval/"+id+"/vouchers/"+details.items().getFirst().id();
    }
    @GetMapping("/{id}")
    public String details(@PathVariable String kind,@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,Model model){
        context(kind,model);if(type(kind)==Type.RECEIPT){var loan=loanReceipts.find(actor,id);if(loan.isPresent()){loanDetails(actor,loan.get(),model);return "accounting/vouchers/loan-details";}}
        model.addAttribute("voucher",vouchers.view(actor,type(kind),id));model.addAttribute("reverseKey",UUID.randomUUID());model.addAttribute("today",clock.today());return "accounting/vouchers/details";
    }
    @PostMapping("/{id}/reverse")
    public String reverse(@PathVariable String kind,@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID requestKey,
                          @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate effectiveDate,@RequestParam String reason,Model model){
        var loan=type(kind)==Type.RECEIPT?loanReceipts.find(actor,id):Optional.<LoanReceipt>empty();
        try{return "redirect:/finance/vouchers/"+kind+"/"+(loan.isPresent()?loanReceipts.reverse(actor,id,requestKey,effectiveDate,reason).header().id():vouchers.reverse(actor,type(kind),id,requestKey,effectiveDate,reason).id());}
        catch(Invalid invalid){model.addAttribute("reverseError",invalid.getMessage());}
        catch(DataIntegrityViolationException duplicate){model.addAttribute("reverseError","voucher.error.duplicate");}
        catch(IllegalArgumentException invalid){model.addAttribute("reverseError",accountingError(invalid));}
        if(loan.isPresent()){context(kind,model);loanDetails(actor,loanReceipts.find(actor,id).orElse(loan.get()),model);model.addAttribute("reverseKey",requestKey);model.addAttribute("today",effectiveDate);model.addAttribute("reason",reason);return "accounting/vouchers/loan-details";}
        context(kind,model);model.addAttribute("voucher",vouchers.view(actor,type(kind),id));model.addAttribute("reverseKey",requestKey);model.addAttribute("today",effectiveDate);model.addAttribute("reason",reason);return "accounting/vouchers/details";
    }
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String kind,@PathVariable UUID id,@RequestParam(defaultValue="false") boolean inline,
                                     @AuthenticationPrincipal AppUserPrincipal actor,Locale locale){
        if(type(kind)==Type.RECEIPT){var loan=loanReceipts.find(actor,id);if(loan.isPresent()){var receipt=loanReceipts.export(actor,id);vouchers.authorize(actor,Type.RECEIPT,"VIEW");
            var result=loanPdf.render(receipt,locale,logos.hasLogo(actor.getSaccoId())?logos.load(actor.getSaccoId()).content():null);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,(inline?"inline":"attachment")+"; filename="+receipt.header().number()+".pdf").header("X-Content-Type-Options","nosniff").body(result);}}
        var voucher=vouchers.export(actor,type(kind),id);
        byte[] logo=logos.hasLogo(actor.getSaccoId())?logos.load(actor.getSaccoId()).content():null;
        var result=pdf.render(voucher,locale,logo);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,(inline?"inline":"attachment")+"; filename="+result.filename()).header("X-Content-Type-Options","nosniff").body(result.bytes());
    }
    @GetMapping("/{id}/print")
    public ResponseEntity<byte[]> print(@PathVariable String kind,@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,Locale locale){return pdf(kind,id,true,actor,locale);}
    @GetMapping("/accounts") @ResponseBody
    public List<Account> accounts(@PathVariable String kind,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return vouchers.accounts(actor,type(kind),search,page);}
    @GetMapping("/transactions") @ResponseBody
    public List<Mapping> transactions(@PathVariable String kind,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(required=false) UUID activityId,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return vouchers.mappings(actor,type(kind),activityId,search,page);}
    @GetMapping("/activities") @ResponseBody
    public List<Activity> activities(@PathVariable String kind,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return vouchers.activities(actor,type(kind),search,page);}
    @GetMapping("/clients") @ResponseBody
    public List<Client> clients(@PathVariable String kind,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return vouchers.clients(actor,type(kind),search,page);}
    @GetMapping("/loan-choices/{category}") @ResponseBody
    public List<com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice> loanChoices(@PathVariable String kind,@PathVariable String category,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(required=false) UUID clientId,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){if(type(kind)!=Type.RECEIPT)throw new Invalid("","request");return loanReceipts.choices(actor,category,clientId,search,page);}
    private void formContext(String kind,AppUserPrincipal actor,Form form,Model model){context(kind,model);model.addAttribute("voucherActivity",vouchers.selectedActivity(actor,type(kind),form.getActivityId()).orElse(null));model.addAttribute("approvalRequired",!"LOAN_REPAYMENT".equals(form.getReceiptPurpose()) && approvalGate.required(actor,type(kind)));model.addAttribute("selectedAccounts",vouchers.selectedAccounts(actor,type(kind),form));if(!"LOAN_REPAYMENT".equals(form.getReceiptPurpose()))model.addAttribute("voucherClient",vouchers.selectedClient(actor,type(kind),form.getClientId()).orElse(null));if(type(kind)==Type.RECEIPT){model.addAttribute("canLoanReceipt",loanReceipts.available(actor,"POST") && loanReceipts.available(actor,"VIEW"));if("LOAN_REPAYMENT".equals(form.getReceiptPurpose())){model.addAttribute("repaymentPolicy",loanReceipts.defaults(actor,form));model.addAttribute("loanLabels",loanReceipts.selected(actor,form));}}}
    private void loanDetails(AppUserPrincipal actor,LoanReceipt receipt,Model model){model.addAttribute("receipt",receipt);model.addAttribute("voucher",receipt.header());model.addAttribute("canReverseLoanReceipt",!receipt.header().postedBy().equals(actor.getMemberId()) && loanReceipts.canReverse(actor,receipt));model.addAttribute("reverseKey",UUID.randomUUID());model.addAttribute("today",clock.today());}
    private String accountingError(IllegalArgumentException ex){return ex.getMessage()!=null && (ex.getMessage().startsWith("approval.error.") || ex.getMessage().startsWith("recording.error.") || ex.getMessage().startsWith("repayment.error.") || ex.getMessage().startsWith("voucher.error.") || ex.getMessage().startsWith("charges.error.") || ex.getMessage().startsWith("prepayment.error.") || ex.getMessage().startsWith("group.error."))?ex.getMessage():"voucher.error.request";}
    private void context(String kind,Model model){model.addAttribute("voucherType",type(kind).name());model.addAttribute("voucherKind",kind);model.addAttribute("base","/finance/vouchers/"+kind);model.addAttribute("viewClaim",type(kind)==Type.JOURNAL?"ACCOUNTING_JOURNALS_VIEW":"ACCOUNTING_BUSINESS_VIEW");model.addAttribute("createClaim",type(kind)==Type.JOURNAL?"ACCOUNTING_JOURNALS_CREATE":"ACCOUNTING_BUSINESS_CREATE");model.addAttribute("reverseClaim",type(kind)==Type.JOURNAL?"ACCOUNTING_JOURNALS_REVERSE":"ACCOUNTING_BUSINESS_REVERSE");}
}
