package com.sacco.mvp.accounting.controller;

import com.sacco.mvp.accounting.dto.AccountingLibraryDtos.*;
import com.sacco.mvp.accounting.policy.PostingEvent;
import com.sacco.mvp.accounting.service.AccountingCodeLibraryService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Arrays;
import java.util.UUID;

@Controller @RequiredArgsConstructor @RequestMapping("/finance/library")
@PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_VIEW')")
public class AccountingCodeLibraryController {
    private final AccountingCodeLibraryService library;
    @InitBinder("codeForm") void bindCode(WebDataBinder binder) {binder.setAllowedFields("code","name","nameSw","description","sourceEvent");}
    @InitBinder("templateForm") void bindTemplate(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(5);binder.setAllowedFields("requestKey","expectedRevision","reason","rules[*].component","rules[*].debitCode","rules[*].creditCode");
    }
    @InitBinder("transactionForm") void bindTransaction(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(5);binder.setAllowedFields("activityCode","code","name","description","sourceEvent","template.requestKey","template.expectedRevision","template.reason","template.rules[*].component","template.rules[*].debitCode","template.rules[*].creditCode");
    }
    @GetMapping
    String activities(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="") String state,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="false") boolean create,Model model) {
        model.addAttribute("modalOpen",create);return register(actor,false,null,search,state,page,model);
    }
    @GetMapping("/transactions")
    String transactions(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(required=false) UUID activityId,
            @RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String state,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="false") boolean create,Model model) {
        model.addAttribute("modalOpen",create);return register(actor,true,activityId,search,state,page,model);
    }
    @GetMapping("/activities/{id}")
    String activity(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="") String state,@RequestParam(defaultValue="0") int page,Model model) {
        return register(actor,true,id,search,state,page,model);
    }
    @GetMapping("/activities/new") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_CREATE')")
    String newActivity() {return "redirect:/finance/library?create=true";}
    @GetMapping("/activities/{id}/transactions/new") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_CREATE')")
    String newTransaction(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@ModelAttribute("codeForm") CodeForm form,Model model) {
        library.activity(actor,id);return "redirect:/finance/library/transactions?create=true&activityId="+id;
    }
    @PostMapping("/activities") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_CREATE')")
    String saveActivity(@AuthenticationPrincipal AppUserPrincipal actor,@ModelAttribute("codeForm") CodeForm form,BindingResult errors,Model model,RedirectAttributes flash) {
        if(!errors.hasErrors())try {library.createActivity(actor,form);created(flash,form);return "redirect:/finance/library";}
        catch(IllegalArgumentException failure){codeError(errors,key(failure));}catch(DataIntegrityViolationException failure){errors.rejectValue("code","library.error.duplicate");}
        model.addAttribute("modalOpen",true);return register(actor,false,null,"","",0,model);
    }
    @PostMapping("/transactions") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_CREATE') and @access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')")
    String onboardTransaction(@AuthenticationPrincipal AppUserPrincipal actor,@ModelAttribute("transactionForm") TransactionForm form,
            BindingResult errors,Model model,RedirectAttributes flash) {
        if(!errors.hasErrors())try {library.onboardTransaction(actor,form);created(flash,form);return "redirect:/finance/library/transactions";}
        catch(IllegalArgumentException failure){errors.reject(key(failure));}catch(DataIntegrityViolationException failure){errors.reject("library.error.conflict");}
        model.addAttribute("modalOpen",true);return register(actor,true,null,"","",0,model);
    }
    @GetMapping(value="/lookup/activities",produces="application/json") @ResponseBody
    java.util.List<Activity> activityChoices(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="") String search) {
        return library.activities(actor,search,"ACTIVE",0).rows();
    }
    @GetMapping(value="/lookup/accounts",produces="application/json") @ResponseBody
    java.util.List<AccountChoice> accountChoices(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="") String search) {
        return library.accounts(actor,search,0).rows();
    }
    @PostMapping("/activities/{id}/transactions") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_CREATE')")
    String saveTransaction(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@ModelAttribute("codeForm") CodeForm form,BindingResult errors,Model model,RedirectAttributes flash) {
        form.setActivityId(id);
        if(!errors.hasErrors())try {UUID transaction=library.createTransaction(actor,form);success(flash);return "redirect:/finance/library/transactions/"+transaction;}
        catch(IllegalArgumentException failure){codeError(errors,key(failure));}catch(DataIntegrityViolationException failure){errors.rejectValue("code","library.error.duplicate");}
        return codeForm(model,library.activity(actor,id));
    }
    @GetMapping("/transactions/{id}")
    String transaction(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam(required=false) UUID version,
            @RequestParam(defaultValue="0") int page,Model model) {
        model.addAttribute("transaction",library.transaction(actor,id));model.addAttribute("template",library.template(actor,id,version));
        model.addAttribute("versions",library.versions(actor,id,page));return "accounting/library-transaction";
    }
    @GetMapping("/transactions/{id}/template") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')")
    String newTemplate(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,
            @RequestParam(defaultValue="") String accountSearch,@RequestParam(defaultValue="0") int accountPage,Model model) {
        var code=library.transaction(actor,id);var form=new TemplateForm();form.setExpectedRevision(code.revision());
        var current=library.template(actor,id,null);
        if(current!=null)for(var rule:current.rules()) {var r=new RuleForm();r.setComponent(rule.component());r.setDebitCode(rule.debitCode());r.setCreditCode(rule.creditCode());form.getRules().add(r);}
        if(form.getRules().isEmpty())form.getRules().add(new RuleForm());
        model.addAttribute("templateForm",form);return templateForm(actor,id,accountSearch,accountPage,model);
    }
    @PostMapping("/transactions/{id}/templates") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')")
    String saveTemplate(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@ModelAttribute("templateForm") TemplateForm form,
            BindingResult errors,Model model,RedirectAttributes flash) {
        if(!errors.hasErrors())try {UUID version=library.saveTemplate(actor,id,form);success(flash);return "redirect:/finance/library/transactions/"+id+"?version="+version;}
        catch(IllegalArgumentException failure){errors.reject(key(failure));}catch(DataIntegrityViolationException failure){errors.reject("library.error.conflict");}
        return templateForm(actor,id,"",0,model);
    }
    @PostMapping("/activities/{id}/{action:deactivate|reactivate}") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')")
    String activityState(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@PathVariable String action,RedirectAttributes flash) {
        library.activityState(actor,id,action.equals("reactivate"));success(flash);return "redirect:/finance/library/activities/"+id;
    }
    @PostMapping("/transactions/{id}/{action:deactivate|reactivate}") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')")
    String transactionState(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@PathVariable String action,RedirectAttributes flash) {
        library.transactionState(actor,id,action.equals("reactivate"));success(flash);return "redirect:/finance/library/transactions/"+id;
    }
    private String templateForm(AppUserPrincipal actor,UUID id,String search,int page,Model model) {
        if(model.getAttribute("templateForm") instanceof TemplateForm form && (form.getRules()==null || form.getRules().isEmpty()))
            form.setRules(new java.util.ArrayList<>(java.util.List.of(new RuleForm())));
        model.addAttribute("transaction",library.transaction(actor,id));model.addAttribute("components",AccountingCodeLibraryService.COMPONENTS);
        model.addAttribute("accountChoices",library.accounts(actor,search,page));model.addAttribute("accountSearch",search);return "accounting/library-template-form";
    }
    private String codeForm(Model model,Activity activity) {
        model.addAttribute("activity",activity);model.addAttribute("sourceEvents",Arrays.stream(PostingEvent.values()).map(Enum::name).toList());return "accounting/library-code-form";
    }
    private String register(AppUserPrincipal actor,boolean transactions,UUID activityId,String search,String state,int page,Model model) {
        model.addAttribute("isTransactions",transactions);filters(model,search,state);
        if(transactions) {
            Activity parent=activityId==null?null:library.activity(actor,activityId);model.addAttribute("activity",parent);
            model.addAttribute("records",library.transactionRegister(actor,activityId,search,state,page));
            if(!model.containsAttribute("transactionForm")) {var form=new TransactionForm();if(parent!=null)form.setActivityCode(parent.code());model.addAttribute("transactionForm",form);}
            var form=(TransactionForm)model.getAttribute("transactionForm");
            if(form.getTemplate().getRules()==null)form.getTemplate().setRules(new java.util.ArrayList<>());
            if(form.getTemplate().getRules().isEmpty())form.getTemplate().getRules().add(new RuleForm());
            model.addAttribute("activityChoices",library.activities(actor,"","ACTIVE",0));model.addAttribute("accountChoices",library.accounts(actor,"",0));
            model.addAttribute("components",AccountingCodeLibraryService.COMPONENTS);model.addAttribute("sourceEvents",Arrays.stream(PostingEvent.values()).map(Enum::name).toList());
        } else {
            model.addAttribute("records",library.activities(actor,search,state,page));
            if(!model.containsAttribute("codeForm"))model.addAttribute("codeForm",new CodeForm());
        }
        return "accounting/library";
    }
    private static void created(RedirectAttributes flash,CodeForm form) {
        success(flash);flash.addFlashAttribute("createdCode",form.getCode());flash.addFlashAttribute("createdName",form.getName());flash.addAttribute("search",form.getCode());
    }
    private static void filters(Model model,String search,String state) {model.addAttribute("search",search);model.addAttribute("state",state);}
    private static void success(RedirectAttributes flash) {flash.addFlashAttribute("librarySuccess","library.saved");}
    private static void codeError(BindingResult errors,String key) {
        String field=switch(key) {case "library.error.code","library.error.name","library.error.nameSw","library.error.description","library.error.sourceEvent"->key.substring("library.error.".length());default->null;};
        if(field==null)errors.reject(key);else errors.rejectValue(field,key);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    String error(IllegalArgumentException failure,RedirectAttributes flash) {flash.addFlashAttribute("libraryError",key(failure));return "redirect:/finance/library";}
    @ExceptionHandler(org.springframework.beans.InvalidPropertyException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    String invalidFields(Model model) {model.addAttribute("accountingError","library.error.rules");return "accounting/error";}
    private static String key(IllegalArgumentException failure) {return failure.getMessage()!=null && failure.getMessage().startsWith("library.error.")?failure.getMessage():"library.error.validation";}
}
