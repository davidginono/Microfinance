package com.sacco.mvp.accounting.controller;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.accounting.dto.VoucherDtos.Mapping;
import com.sacco.mvp.accounting.service.VoucherService;
import com.sacco.mvp.accounting.service.VoucherPdfService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
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
    @ExceptionHandler({Invalid.class, org.springframework.beans.InvalidPropertyException.class})
    public ResponseEntity<String> invalidRequest(RuntimeException invalid){return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body("Invalid voucher request");}
    public static Type type(String kind){return switch(kind){case "receipts"->Type.RECEIPT;case "payments"->Type.PAYMENT;case "journals"->Type.JOURNAL;default->throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);};}
    @InitBinder("voucherForm") void binder(WebDataBinder binder){
        binder.setAutoGrowCollectionLimit(50);
        binder.setAllowedFields("requestKey","effectiveDate","party","reference","description","evidence","moneyAccountId","rows[*].transactionId","rows[*].templateKey","rows[*].component","rows[*].description","rows[*].amount","rows[*].debitAccountId","rows[*].creditAccountId");
    }
    @GetMapping
    public String list(@PathVariable String kind,@AuthenticationPrincipal AppUserPrincipal actor,
                       @RequestParam(defaultValue="") String search,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,@RequestParam(defaultValue="") String state,
                       @RequestParam(defaultValue="newest") String sort,@RequestParam(defaultValue="0") int page,Model model){
        var filter=new Filter(search,from,through,state,sort,page);context(kind,model);model.addAttribute("filter",filter);
        model.addAttribute("vouchers",vouchers.list(actor,type(kind),filter));return "accounting/vouchers/register";
    }
    @GetMapping("/new")
    public String create(@PathVariable String kind,@AuthenticationPrincipal AppUserPrincipal actor,Model model){
        vouchers.authorize(actor,type(kind),"CREATE");var f=new Form();f.setEffectiveDate(clock.today());model.addAttribute("voucherForm",f);context(kind,model);return "accounting/vouchers/form";
    }
    @PostMapping
    public String post(@PathVariable String kind,@AuthenticationPrincipal AppUserPrincipal actor,@ModelAttribute("voucherForm") Form form,
                       BindingResult errors,@RequestParam(defaultValue="post") String action,Model model,RedirectAttributes flash){
        vouchers.authorize(actor,type(kind),"CREATE");context(kind,model);
        if(!errors.hasErrors())try{
            if("preview".equals(action)){model.addAttribute("preview",vouchers.preview(actor,type(kind),form));model.addAttribute("selectedAccounts",vouchers.selectedAccounts(actor,type(kind),form));return "accounting/vouchers/form";}
            if(!"post".equals(action))throw new Invalid("","request");
            var saved=vouchers.post(actor,type(kind),form);flash.addFlashAttribute("voucherPosted",true);return "redirect:/finance/vouchers/"+kind+"/"+saved.id();
        }catch(Invalid invalid){if(invalid.field().isEmpty())errors.reject(invalid.getMessage());else errors.rejectValue(invalid.field(),invalid.getMessage());}
         catch(DataIntegrityViolationException duplicate){errors.reject("voucher.error.duplicate");}
        model.addAttribute("selectedAccounts",vouchers.selectedAccounts(actor,type(kind),form));return "accounting/vouchers/form";
    }
    @GetMapping("/{id}")
    public String details(@PathVariable String kind,@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,Model model){
        context(kind,model);model.addAttribute("voucher",vouchers.view(actor,type(kind),id));model.addAttribute("reverseKey",UUID.randomUUID());model.addAttribute("today",clock.today());return "accounting/vouchers/details";
    }
    @PostMapping("/{id}/reverse")
    public String reverse(@PathVariable String kind,@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID requestKey,
                          @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate effectiveDate,@RequestParam String reason,Model model){
        try{return "redirect:/finance/vouchers/"+kind+"/"+vouchers.reverse(actor,type(kind),id,requestKey,effectiveDate,reason).id();}
        catch(Invalid invalid){model.addAttribute("reverseError",invalid.getMessage());}
        catch(DataIntegrityViolationException duplicate){model.addAttribute("reverseError","voucher.error.duplicate");}
        context(kind,model);model.addAttribute("voucher",vouchers.view(actor,type(kind),id));model.addAttribute("reverseKey",requestKey);model.addAttribute("today",effectiveDate);model.addAttribute("reason",reason);return "accounting/vouchers/details";
    }
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String kind,@PathVariable UUID id,@RequestParam(defaultValue="false") boolean inline,
                                     @AuthenticationPrincipal AppUserPrincipal actor,Locale locale){
        var result=pdf.render(vouchers.export(actor,type(kind),id),locale);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,(inline?"inline":"attachment")+"; filename="+result.filename()).header("X-Content-Type-Options","nosniff").body(result.bytes());
    }
    @GetMapping("/{id}/print")
    public String print(@PathVariable String kind,@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,Model model){context(kind,model);model.addAttribute("voucher",vouchers.export(actor,type(kind),id));return "accounting/vouchers/print";}
    @GetMapping("/accounts") @ResponseBody
    public List<Account> accounts(@PathVariable String kind,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return vouchers.accounts(actor,type(kind),search,page);}
    @GetMapping("/transactions") @ResponseBody
    public List<Mapping> transactions(@PathVariable String kind,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return vouchers.mappings(actor,type(kind),search,page);}
    private void context(String kind,Model model){model.addAttribute("voucherType",type(kind).name());model.addAttribute("voucherKind",kind);model.addAttribute("base","/finance/vouchers/"+kind);model.addAttribute("viewClaim",type(kind)==Type.JOURNAL?"ACCOUNTING_JOURNALS_VIEW":"ACCOUNTING_BUSINESS_VIEW");model.addAttribute("createClaim",type(kind)==Type.JOURNAL?"ACCOUNTING_JOURNALS_CREATE":"ACCOUNTING_BUSINESS_CREATE");model.addAttribute("reverseClaim",type(kind)==Type.JOURNAL?"ACCOUNTING_JOURNALS_REVERSE":"ACCOUNTING_BUSINESS_REVERSE");}
}
